using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using System.Text.Json;
using HeartRate.Models;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;
using Microsoft.UI.Dispatching;

namespace HeartRate.Services;

/// <summary>
/// 局域网传输服务：电脑端承担三种角色，与手机端「局域网传输」严格对应
/// （协议见 PC_CLIENT_INTEGRATION.md）：
///   1. mDNS 广播方：广播 <c>_heartrate._tcp.local</c>，TXT 含 <c>name</c>/<c>pair_port</c>。
///   2. HTTP 配对服务端：监听 POST /pair-request，弹窗确认后回 {approved, session_id?}。
///   3. WebSocket 客户端：配对通过后连 <c>ws://{ws_ip}:{ws_port}/?token=</c>，
///      接收手机推送的 {heart_rate,connected,status,timestamp,speed} JSON。
///
/// mDNS 实现为自包含原始 UDP（224.0.0.251:5353 多播），无外部 NuGet 依赖；
/// HTTP 配对复用 Kestrel（与 <see cref="NetworkServerService"/> 同栈，unpackaged 下免 admin）。
/// 开关/端口/电脑名变化触发 ApplyAsync 重启。
/// </summary>
public sealed class LanTransferService : IDisposable
{
    private readonly LanTransferSettings _settings;
    private readonly DispatcherQueue _uiDispatcher;

    private MdnsAdvertiser? _advertiser;
    private WebApplication? _pairApp;
    private readonly SemaphoreSlim _applyLock = new(1, 1);

    // 已配对手机：key = device_id。WS 任务自行管理生命周期。
    private readonly ConcurrentDictionary<string, PhoneSession> _sessions = new();

    // UI 弹窗确认回调：由 LanTransferViewModel 在 View Loaded 时注入。
    private Func<LanPairRequest, Task<bool>>? _approvalHandler;

    public bool IsRunning { get; private set; }
    public string? Error { get; private set; }

    /// <summary>服务运行状态变化时触发（后台线程）。</summary>
    public event Action? StatusChanged;

    /// <summary>收到手机配对请求（弹窗前）。</summary>
    public event Action<LanPairRequest>? PairRequestReceived;

    /// <summary>配对通过、WS 已建立。</summary>
    public event Action<LanPhone>? PhoneConnected;

    /// <summary>WS 关闭或断开。</summary>
    public event Action<string>? PhoneDisconnected;

    /// <summary>收到手机推送的心率数据。</summary>
    public event Action<LanPhone, int, bool, string>? HeartRateUpdated;

    private static readonly JsonSerializerOptions _payloadJsonOptions = new()
    {
        // 字段名已用 [JsonPropertyName] 显式指定，不需要命名策略（避免与策略交互产生边缘 bug）
        PropertyNameCaseInsensitive = true,
    };

    public LanTransferService(LanTransferSettings settings, DispatcherQueue uiDispatcher)
    {
        _settings = settings;
        _uiDispatcher = uiDispatcher;
        _settings.PropertyChanged += OnSettingsPropertyChanged;
    }

    /// <summary>注入 UI 弹窗回调（由 View 提供，XamlRoot 在 View 侧）。</summary>
    public void SetApprovalHandler(Func<LanPairRequest, Task<bool>> handler)
        => _approvalHandler = handler;

    /// <summary>按当前设置应用（停后重启）。可在任何线程调用。</summary>
    public async Task ApplyAsync()
    {
        await _applyLock.WaitAsync();
        try
        {
            await StopCoreAsync();
            await StartCoreAsync();
            RaiseStatusChanged();
        }
        finally
        {
            _applyLock.Release();
        }
    }

    public async Task StopAsync()
    {
        await _applyLock.WaitAsync();
        try
        {
            await StopCoreAsync();
            RaiseStatusChanged();
        }
        finally
        {
            _applyLock.Release();
        }
    }

    private async Task StartCoreAsync()
    {
        IsRunning = false;
        Error = null;

        if (!_settings.Enabled) return;

        var computerName = string.IsNullOrWhiteSpace(_settings.ComputerName)
            ? Environment.MachineName
            : _settings.ComputerName;
        var pairPort = _settings.PairPort;
        var localIps = NetworkIPHelper.GetLanIPv4Addresses();

        // ── 启动 Kestrel 配对 HTTP 服务 ────────────────────────────────────
        try
        {
            var builder = WebApplication.CreateBuilder();
            builder.Logging.ClearProviders();
            builder.WebHost.ConfigureKestrel(o => o.Listen(IPAddress.Any, pairPort));

            var app = builder.Build();
            app.MapPost("/pair-request", ctx => OnPairRequestAsync(ctx));
            app.MapGet("/", () => Results.Ok("Heart Rate Monitor LAN Transfer"));

            await app.StartAsync();
            _pairApp = app;
            // 端口绑定成功即视为运行中（mDNS 随后启动）；IsRunning 提前置位，
            // 供开关预检区分"自身占用"，避免重启后误报端口被占用。
            IsRunning = true;
        }
        catch (Exception ex)
        {
            Error = $"pair server: {ex.Message}";
            IsRunning = false;
        }

        // ── 启动 mDNS 广播（仅当配对 HTTP 服务启动成功）────────────────────
        // 若配对服务绑定失败（如端口被占用），就不广播 _heartrate._tcp.local，
        // 避免手机端能发现电脑却无法配对（表现为"配对上但一直失败"的假死）。
        if (_pairApp is not null)
        {
            try
            {
                _advertiser = new MdnsAdvertiser(computerName, pairPort, localIps);
                _advertiser.Start();
            }
            catch (Exception ex)
            {
                // mDNS 失败不阻塞 HTTP 配对；announce-only 模式由 advertiser 内部退化处理
                if (Error is null) Error = $"mdns: {ex.Message}";
            }
        }
    }

    private async Task StopCoreAsync()
    {
        // ── 关闭所有 WS 客户端会话 ──────────────────────────────────────────
        foreach (var id in _sessions.Keys.ToList())
            await CloseSessionAsync(id);

        // ── 停止 mDNS（发送 goodbye）────────────────────────────────────────
        var advertiser = _advertiser;
        _advertiser = null;
        advertiser?.Dispose();

        // ── 停止 Kestrel ────────────────────────────────────────────────────
        var app = _pairApp;
        _pairApp = null;
        if (app is not null)
        {
            try { await app.StopAsync(); }
            catch { /* 忽略停止异常 */ }
        }

        IsRunning = false;
    }

    // ── HTTP POST /pair-request ────────────────────────────────────────────
    private async Task OnPairRequestAsync(HttpContext ctx)
    {
        LanPairRequest? req;
        try
        {
            using var sr = new StreamReader(ctx.Request.Body);
            var body = await sr.ReadToEndAsync(ctx.RequestAborted);
            req = JsonSerializer.Deserialize<LanPairRequest>(body);
            if (req is null)
            {
                ctx.Response.StatusCode = StatusCodes.Status400BadRequest;
                return;
            }
            req.RemoteIp = ctx.Connection.RemoteIpAddress?.ToString() ?? string.Empty;
        }
        catch
        {
            ctx.Response.StatusCode = StatusCodes.Status400BadRequest;
            return;
        }

        PairRequestReceived?.Invoke(req);

        // 弹窗确认（最长 30s，防手机端 35s 超时）；handler 未注入则拒绝
        bool approved = false;
        if (_approvalHandler is not null)
        {
            using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(30));
            try { approved = await _approvalHandler(req).WaitAsync(timeout.Token); }
            catch { approved = false; }
        }

        var sessionId = approved ? Guid.NewGuid().ToString("N") : null;
        var resp = new
        {
            approved,
            session_id = sessionId,
        };
        ctx.Response.ContentType = "application/json; charset=utf-8";
        await JsonSerializer.SerializeAsync(ctx.Response.Body, resp, _payloadJsonOptions);

        if (approved && sessionId is not null)
            _ = Task.Run(() => ConnectPhoneAsync(req, sessionId));
    }

    // ── 连接手机 WS 并接收心率推送 ─────────────────────────────────────────
    private async Task ConnectPhoneAsync(LanPairRequest req, string sessionId)
    {
        var ip = string.IsNullOrEmpty(req.WsIp) ? req.RemoteIp : req.WsIp;
        if (string.IsNullOrEmpty(ip) || req.WsPort <= 0)
        {
            return;
        }

        var url = $"ws://{ip}:{req.WsPort}/";
        if (!string.IsNullOrEmpty(req.WsToken))
            url += $"?token={Uri.EscapeDataString(req.WsToken)}";

        var phone = new LanPhone
        {
            DeviceId = string.IsNullOrEmpty(req.DeviceId) ? sessionId : req.DeviceId,
            DeviceName = req.DeviceName,
            WsHost = ip,
            WsPort = req.WsPort,
            SessionId = sessionId,
        };

        var session = new PhoneSession { Phone = phone };
        if (!_sessions.TryAdd(phone.DeviceId, session))
        {
            // 同一 device_id 已存在，复用旧会话（替换 WS 连接）
            await CloseSessionAsync(phone.DeviceId);
            _sessions[phone.DeviceId] = session;
        }

        RaisePhoneConnected(phone);

        try
        {
            using var ws = new ClientWebSocket();
            await ws.ConnectAsync(new Uri(url), session.Cts.Token);

            var buffer = new byte[8192];
            while (ws.State == WebSocketState.Open && !session.Cts.IsCancellationRequested)
            {
                WebSocketReceiveResult result;
                using var ms = new MemoryStream();
                do
                {
                    result = await ws.ReceiveAsync(buffer, session.Cts.Token);
                    if (result.MessageType == WebSocketMessageType.Close) goto done;
                    ms.Write(buffer, 0, result.Count);
                } while (!result.EndOfMessage);

                var json = Encoding.UTF8.GetString(ms.ToArray());

                try
                {
                    var payload = JsonSerializer.Deserialize<PhonePayload>(json, _payloadJsonOptions);
                    if (payload is null) { continue; }

                    var hr = payload.HeartRate;
                    var conn = payload.Connected;
                    var st = payload.Status ?? string.Empty;
                    var ts = payload.Timestamp;

                    // phone 是 LanPhone（ObservableObject），其 PropertyChanged 被 x:Bind 订阅。
                    // 在工作线程设置属性会同步触发 x:Bind 更新 TextBlock.Text → RPC_E_WRONG_THREAD。
                    // 必须 marshal 到 UI 线程设置属性。
                    _uiDispatcher.TryEnqueue(() =>
                    {
                        try
                        {
                            phone.HeartRate = hr;
                            phone.BleConnected = conn;
                            phone.StatusText = st;
                            phone.LastUpdated = ts;
                            RaiseHeartRateUpdated(phone, hr, conn, st);
                        }
                        catch { }
                    });
                }
                catch
                {
                }
            }
        done: ;
        }
        catch (OperationCanceledException) { }
        catch
        {
        }
        finally
        {
            await CloseSessionAsync(phone.DeviceId);
            RaisePhoneDisconnected(phone.DeviceId);
        }
    }

    private async Task CloseSessionAsync(string deviceId)
    {
        if (_sessions.TryRemove(deviceId, out var session))
        {
            session.Cts.Cancel();
            session.Cts.Dispose();
        }
        await Task.CompletedTask;
    }

    private void OnSettingsPropertyChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        // 任何相关字段变化都重启服务以应用新配置（电脑名/端口/开关）
        if (e.PropertyName is nameof(LanTransferSettings.Enabled)
            or nameof(LanTransferSettings.ComputerName)
            or nameof(LanTransferSettings.PairPort))
        {
            _ = ApplyAsync();
        }
    }

    private void RaiseStatusChanged() => StatusChanged?.Invoke();

    private void RaisePhoneConnected(LanPhone phone)
        => _uiDispatcher.TryEnqueue(() => PhoneConnected?.Invoke(phone));

    private void RaisePhoneDisconnected(string deviceId)
        => _uiDispatcher.TryEnqueue(() => PhoneDisconnected?.Invoke(deviceId));

    private void RaiseHeartRateUpdated(LanPhone phone, int hr, bool connected, string status)
        => _uiDispatcher.TryEnqueue(() => HeartRateUpdated?.Invoke(phone, hr, connected, status));

    public void Dispose()
    {
        _settings.PropertyChanged -= OnSettingsPropertyChanged;
        _ = StopAsync();
        _applyLock.Dispose();
    }

    // ── 嵌套类型 ────────────────────────────────────────────────────────────

    /// <summary>手机推送的心率 JSON 载荷（snake_case）。</summary>
    private sealed class PhonePayload
    {
        [System.Text.Json.Serialization.JsonPropertyName("heart_rate")]
        public int HeartRate { get; set; }

        [System.Text.Json.Serialization.JsonPropertyName("connected")]
        public bool Connected { get; set; }

        [System.Text.Json.Serialization.JsonPropertyName("status")]
        public string? Status { get; set; }

        [System.Text.Json.Serialization.JsonPropertyName("timestamp")]
        public long Timestamp { get; set; }

        [System.Text.Json.Serialization.JsonPropertyName("speed")]
        public float Speed { get; set; }
    }

    /// <summary>已配对手机的运行时会话。</summary>
    private sealed class PhoneSession
    {
        public LanPhone Phone { get; init; } = null!;
        public CancellationTokenSource Cts { get; } = new();
    }

    // ─── mDNS 广播方：原始 UDP 多播，无外部依赖 ─────────────────────────────

    /// <summary>
    /// 自包含 mDNS 响应器：在 224.0.0.251:5353 多播 socket 上应答 PTR/SRV/A 查询，
    /// 并在启动时主动多播一次通告。停止时发送 goodbye（TTL=0）。
    /// 若 5353 端口绑定失败（被其他 mDNS 客户端占用），则降级为 announce-only：
    /// 不响应查询但仍尝试周期性主动多播通告。
    /// </summary>
    private sealed class MdnsAdvertiser : IDisposable
    {
        private const string ServiceType = "_heartrate._tcp.local";
        private const string MulticastGroup = "224.0.0.251";
        private const int MulticastPort = 5353;
        private const int DefaultTtl = 4500; // mDNS 建议 4500s（与 Bonjour 一致）

        private readonly string _computerName;
        private readonly int _pairPort;
        private readonly IReadOnlyList<IPAddress> _localIps;

        private UdpClient? _responder;
        private UdpClient? _sender;
        private Thread? _listenThread;
        private CancellationTokenSource _cts = new();

        public MdnsAdvertiser(string computerName, int pairPort, IReadOnlyList<IPAddress> localIps)
        {
            _computerName = SanitizeName(computerName);
            _pairPort = pairPort;
            _localIps = localIps.Count > 0 ? localIps : new[] { IPAddress.Loopback };
        }

        public void Start()
        {
            // ── 发送用多播 socket（不需要绑定 5353）───────────────────────────
            try
            {
                _sender = new UdpClient();
                _sender.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                _sender.Client.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.MulticastTimeToLive, 255);
            }
            catch
            {
            }

            // ── 接收/响应 socket（绑定 5353）─────────────────────────────────
            var bound = false;
            try
            {
                _responder = new UdpClient();
                _responder.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
                _responder.Client.Bind(new IPEndPoint(IPAddress.Any, MulticastPort));
                foreach (var ip in _localIps)
                {
                    try
                    {
                        var opt = new MulticastOption(IPAddress.Parse(MulticastGroup), ip);
                        _responder.Client.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.AddMembership, opt);
                    }
                    catch { /* 单个接口加入失败不影响其它 */ }
                }
                bound = true;
            }
            catch
            {
                _responder?.Dispose();
                _responder = null;
            }

            // 主动通告（无论是否绑定成功，多播都能发）
            SendAnnounce(DefaultTtl);

            if (bound)
            {
                _listenThread = new Thread(ListenLoop) { IsBackground = true, Name = "mdns-responder" };
                _listenThread.Start();
            }
        }

        private void ListenLoop()
        {
            var client = _responder;
            if (client is null) return;
            var remote = new IPEndPoint(IPAddress.Any, 0);
            while (!_cts.IsCancellationRequested)
            {
                byte[] data;
                try { data = client.Receive(ref remote); }
                catch
                {
                    // socket 关闭（Dispose）或取消时，Receive 抛异常；
                    // 检查取消状态决定退出还是继续（避免后台线程未处理异常导致进程闪退）
                    if (_cts.IsCancellationRequested) break;
                    continue;
                }
                if (_cts.IsCancellationRequested) break;
                HandleQuery(data, remote);
            }
        }

        private void HandleQuery(byte[] data, IPEndPoint remote)
        {
            // mDNS 查询：header(12) + N×question
            // 我们只关心 QTYPE=12 (PTR) 且 QNAME 含 "_heartrate._tcp.local" 的查询
            try
            {
                if (data.Length < 12) return;
                // 仅处理查询包（flags bit15=0 表示查询；bit15=1 表示响应）
                var flags = (data[2] << 8) | data[3];
                bool isQuery = (flags & 0x8000) == 0;
                if (!isQuery) return;

                var qdCount = (data[4] << 8) | data[5];
                if (qdCount == 0) return;

                int pos = 12;
                for (int i = 0; i < qdCount; i++)
                {
                    var (name, newPos) = ReadName(data, pos);
                    pos = newPos;
                    if (pos + 4 > data.Length) return;
                    var qtype = (data[pos] << 8) | data[pos + 1];
                    pos += 4; // qtype(2) + qclass(2)
                    if (qtype == 12 && name.Contains(ServiceType, StringComparison.OrdinalIgnoreCase))
                    {
                        // 响应 PTR 查询
                        var packet = BuildResponsePacket(DefaultTtl);
                        try { _responder?.Send(packet, packet.Length, remote); }
                        catch { /* 忽略单次发送失败 */ }
                        return; // 一个响应足够
                    }
                }
            }
            catch
            {
            }
        }

        /// <summary>主动多播通告：向 224.0.0.251:5353 发送 PTR+SRV+TXT+A。</summary>
        private void SendAnnounce(int ttl)
        {
            var sender = _sender;
            if (sender is null) return;
            var packet = BuildResponsePacket(ttl);
            try
            {
                sender.Send(packet, packet.Length, new IPEndPoint(IPAddress.Parse(MulticastGroup), MulticastPort));
            }
            catch
            {
            }
        }

        /// <summary>构造 mDNS 响应包：PTR + SRV + TXT + A 四个 answer。</summary>
        private byte[] BuildResponsePacket(int ttl)
        {
            using var ms = new MemoryStream();
            // ── Header（12B）：ID=0, flags=0x8400（响应 + AA）, ANCOUNT=4
            ms.Write(BitConverter.GetBytes((ushort)0).AsSpan()); // ID
            WriteUInt16BE(ms, 0x8400); // flags
            WriteUInt16BE(ms, 0);      // QDCOUNT
            WriteUInt16BE(ms, 4);      // ANCOUNT (PTR, SRV, TXT, A)
            WriteUInt16BE(ms, 0);      // NSCOUNT
            WriteUInt16BE(ms, 0);      // ARCOUNT

            var instance = $"{_computerName}.{ServiceType}";
            var host = $"{_computerName}.local";

            // ── Answer 1: PTR  _heartrate._tcp.local → {instance}
            WriteName(ms, ServiceType);
            WriteUInt16BE(ms, 12);  // type PTR
            WriteUInt16BE(ms, 1);   // class IN
            WriteUInt32BE(ms, (uint)ttl);
            var ptrRdata = BuildNameBytes(instance);
            WriteUInt16BE(ms, (ushort)ptrRdata.Length);
            ms.Write(ptrRdata);

            // ── Answer 2: SRV  {instance} → prio=0, weight=0, port, host
            WriteName(ms, instance);
            WriteUInt16BE(ms, 33);  // type SRV
            WriteUInt16BE(ms, 1);
            WriteUInt32BE(ms, (uint)ttl);
            var srvRdata = new MemoryStream();
            WriteUInt16BE(srvRdata, 0);              // priority
            WriteUInt16BE(srvRdata, 0);              // weight
            WriteUInt16BE(srvRdata, (ushort)_pairPort); // port
            srvRdata.Write(BuildNameBytes(host));    // target
            var srvBytes = srvRdata.ToArray();
            WriteUInt16BE(ms, (ushort)srvBytes.Length);
            ms.Write(srvBytes);

            // ── Answer 3: TXT  {instance} → "name=X", "pair_port=Y"
            WriteName(ms, instance);
            WriteUInt16BE(ms, 16);  // type TXT
            WriteUInt16BE(ms, 1);
            WriteUInt32BE(ms, (uint)ttl);
            var txtRdata = new MemoryStream();
            AppendTxtEntry(txtRdata, "name", _computerName);
            AppendTxtEntry(txtRdata, "pair_port", _pairPort.ToString());
            var txtBytes = txtRdata.ToArray();
            WriteUInt16BE(ms, (ushort)txtBytes.Length);
            ms.Write(txtBytes);

            // ── Answer 4: A  {host} → IP（每个本地 IP 一条，这里只发首个）
            WriteName(ms, host);
            WriteUInt16BE(ms, 1);   // type A
            WriteUInt16BE(ms, 1);
            WriteUInt32BE(ms, (uint)ttl);
            var ipBytes = _localIps[0].GetAddressBytes();
            WriteUInt16BE(ms, (ushort)ipBytes.Length);
            ms.Write(ipBytes);

            return ms.ToArray();
        }

        private static void AppendTxtEntry(MemoryStream ms, string key, string value)
        {
            var entry = $"{key}={value}";
            var bytes = Encoding.UTF8.GetBytes(entry);
            if (bytes.Length > 255) bytes = bytes[..255];
            ms.WriteByte((byte)bytes.Length);
            ms.Write(bytes);
        }

        // ── DNS 名字编解码 ─────────────────────────────────────────────────────
        private static void WriteName(MemoryStream ms, string name)
        {
            foreach (var label in name.Split('.'))
            {
                var bytes = Encoding.UTF8.GetBytes(label);
                if (bytes.Length == 0) continue;
                ms.WriteByte((byte)bytes.Length);
                ms.Write(bytes);
            }
            ms.WriteByte(0); // 终止
        }

        private static byte[] BuildNameBytes(string name)
        {
            using var ms = new MemoryStream();
            WriteName(ms, name);
            return ms.ToArray();
        }

        private static (string name, int newPos) ReadName(byte[] data, int pos)
        {
            var sb = new StringBuilder();
            int safety = 0;
            while (pos < data.Length && safety++ < 64)
            {
                int len = data[pos];
                if (len == 0) { pos++; break; }
                if ((len & 0xC0) == 0xC0)
                {
                    // 压缩指针：忽略，只读原始位置
                    pos += 2;
                    break;
                }
                pos++;
                if (pos + len > data.Length) break;
                var label = Encoding.UTF8.GetString(data, pos, len);
                if (sb.Length > 0) sb.Append('.');
                sb.Append(label);
                pos += len;
            }
            return (sb.ToString(), pos);
        }

        private static void WriteUInt16BE(Stream ms, ushort v)
        {
            ms.WriteByte((byte)(v >> 8));
            ms.WriteByte((byte)(v & 0xFF));
        }

        private static void WriteUInt32BE(Stream ms, uint v)
        {
            ms.WriteByte((byte)(v >> 24));
            ms.WriteByte((byte)(v >> 16));
            ms.WriteByte((byte)(v >> 8));
            ms.WriteByte((byte)(v & 0xFF));
        }

        /// <summary>DNS 标签只允许字母数字与连字符，且不超过 63 字节。</summary>
        private static string SanitizeName(string name)
        {
            var sb = new StringBuilder();
            foreach (var c in name)
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == '-')
                    sb.Append(c);
            var result = sb.ToString();
            if (result.Length == 0) result = "HeartRate-PC";
            if (result.Length > 63) result = result[..63];
            return result;
        }

        public void Dispose()
        {
            try
            {
                // 发送 goodbye（TTL=0）让客户端立即移除条目
                if (_sender is not null) SendAnnounce(0);
            }
            catch { }

            _cts.Cancel();
            try { _responder?.Close(); } catch { }
            try { _sender?.Close(); } catch { }
            try { _cts.Dispose(); } catch { }
        }
    }
}
