using System.Collections.Concurrent;
using System.Net;
using System.Net.WebSockets;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading.Channels;
using HeartRate.Models;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;
using Windows.Devices.Bluetooth;

namespace HeartRate.Services;

/// <summary>
/// 网络传输服务端：单 ASP.NET Core Kestrel 实例同时承载
///   - HTTP GET /heartrate（监听 HttpServerPort，返回 JSON 状态快照）
///   - WebSocket  /ws（监听 WebSocketServerPort，推送状态更新 + 4s 自动 ping）
/// 对应示例项目 HttpServerManager / WebSocketServerManager / ServerHost。
///
/// 端口/开关变化（NetworkSettings.PropertyChanged）触发 ApplyAsync 重启监听。
/// 心率数据来自 <see cref="HeartRateService"/> 事件（后台线程），HTTP 快照与 WS 广播共享同一份状态。
/// </summary>
public sealed class NetworkServerService : IDisposable
{
    private readonly HeartRateService _heartRate;
    private readonly NetworkSettings _settings;

    private WebApplication? _app;
    private readonly SemaphoreSlim _applyLock = new(1, 1);

    // 当前监听端口（-1 表示未启用），用于路由按端口分发
    private int _httpPort = -1;
    private int _wsPort = -1;

    // WS 客户端注册表：每个连接一条消息通道
    private readonly ConcurrentDictionary<WebSocket, Channel<string>> _clients = new();

    // 心率状态快照（多线程读写，需加锁）
    private readonly object _stateLock = new();
    private int _currentBpm;
    private bool _isConnected;
    private string _status = "Disconnected";
    private long _lastTimestamp;

    private static readonly JsonSerializerOptions _stateJsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.SnakeCaseLower,
    };

    public bool IsHttpRunning { get; private set; }
    public bool IsWsRunning { get; private set; }
    public string? HttpError { get; private set; }
    public string? WsError { get; private set; }

    /// <summary>运行状态变化时触发（UI 线程或后台线程）。</summary>
    public event Action? StatusChanged;

    public NetworkServerService(HeartRateService heartRate, NetworkSettings settings)
    {
        _heartRate = heartRate;
        _settings = settings;

        _heartRate.HeartRateReceived += OnHeartRateReceived;
        _heartRate.ConnectionChanged += OnConnectionChanged;
        _settings.PropertyChanged += OnSettingsPropertyChanged;
    }

    /// <summary>按当前设置（停后重启）应用监听。可在任何线程调用。</summary>
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
        IsHttpRunning = false;
        IsWsRunning = false;
        HttpError = null;
        WsError = null;

        _httpPort = _settings.HttpServerEnabled ? _settings.HttpServerPort : -1;
        _wsPort = _settings.WebSocketServerEnabled ? _settings.WebSocketServerPort : -1;
        if (_httpPort < 0 && _wsPort < 0) return;

        try
        {
            // 初始状态快照来自当前心率服务连接情况
            lock (_stateLock)
            {
                _isConnected = _heartRate.IsConnected;
                _status = _isConnected ? "Connected" : "Disconnected";
                _lastTimestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            }

            var builder = WebApplication.CreateBuilder();
            builder.Logging.ClearProviders();
            builder.WebHost.ConfigureKestrel(o =>
            {
                if (_httpPort > 0) o.Listen(IPAddress.Any, _httpPort);
                if (_wsPort > 0) o.Listen(IPAddress.Any, _wsPort);
            });

            var app = builder.Build();
            app.UseWebSockets(new WebSocketOptions
            {
                // 4 秒自动 ping（对应示例 WebSocketServerManager 的 4s 心跳）
                KeepAliveInterval = TimeSpan.FromSeconds(4),
            });

            app.MapGet("/heartrate", OnHttpGet);
            app.MapGet("/ws", OnWsConnect);
            app.MapGet("/", () => Results.Ok("Heart Rate Monitor"));

            await app.StartAsync();
            _app = app;
            if (_httpPort > 0) IsHttpRunning = true;
            if (_wsPort > 0) IsWsRunning = true;
        }
        catch (Exception ex)
        {
            IsHttpRunning = false;
            IsWsRunning = false;
            HttpError = ex.Message;
            WsError = ex.Message;
        }
    }

    private async Task StopCoreAsync()
    {
        var app = _app;
        _app = null;
        if (app is null) return;

        try { await app.StopAsync(); }
        catch { /* 忽略停止异常 */ }

        // 关闭所有 WS 连接
        foreach (var (ws, _) in _clients)
        {
            try
            {
                if (ws.State == WebSocketState.Open)
                    await ws.CloseAsync(WebSocketCloseStatus.EndpointUnavailable, "Server stopping", CancellationToken.None);
            }
            catch { }
        }
        _clients.Clear();
        IsHttpRunning = false;
        IsWsRunning = false;
    }

    // ── HTTP GET /heartrate ────────────────────────────────────────────────
    private IResult OnHttpGet(HttpContext ctx)
    {
        // 仅在 HTTP 端口响应；其它端口（如 WS 端口）访问 /heartrate 返回 404
        if (_httpPort <= 0 || ctx.Connection.LocalPort != _httpPort)
            return Results.NotFound();

        var json = BuildStateJson();
        return Results.Text(json, "application/json");
    }

    // ── WebSocket /ws ─────────────────────────────────────────────────────
    private async Task OnWsConnect(HttpContext ctx)
    {
        if (_wsPort <= 0 || ctx.Connection.LocalPort != _wsPort)
        {
            ctx.Response.StatusCode = StatusCodes.Status404NotFound;
            return;
        }
        if (!ctx.WebSockets.IsWebSocketRequest)
        {
            ctx.Response.StatusCode = StatusCodes.Status400BadRequest;
            return;
        }

        using var ws = await ctx.WebSockets.AcceptWebSocketAsync();
        var channel = Channel.CreateUnbounded<string>();
        _clients[ws] = channel;

        // 连接建立即推送当前快照
        await SendTextAsync(ws, BuildStateJson());

        try
        {
            await foreach (var msg in channel.Reader.ReadAllAsync(ctx.RequestAborted))
            {
                if (ws.State != WebSocketState.Open) break;
                await SendTextAsync(ws, msg);
            }
        }
        catch
        {
            // 连接异常或取消，忽略
        }
        finally
        {
            _clients.TryRemove(ws, out _);
            channel.Writer.TryComplete();
        }
    }

    private static async Task SendTextAsync(WebSocket ws, string text)
    {
        if (ws.State != WebSocketState.Open) return;
        await ws.SendAsync(System.Text.Encoding.UTF8.GetBytes(text),
            WebSocketMessageType.Text, endOfMessage: true, CancellationToken.None);
    }

    // ── 心率事件 → 更新快照 + 广播 ────────────────────────────────────────
    private void OnHeartRateReceived(object? sender, int bpm)
    {
        lock (_stateLock)
        {
            _currentBpm = bpm;
            _isConnected = true;
            _status = "Connected";
            _lastTimestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        }
        BroadcastState();
    }

    private void OnConnectionChanged(object? sender, BluetoothConnectionStatus status)
    {
        var connected = status == BluetoothConnectionStatus.Connected;
        lock (_stateLock)
        {
            _isConnected = connected;
            _status = connected ? "Connected" : "Disconnected";
            _lastTimestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            if (!connected) _currentBpm = 0; // 断开后心率归零（与示例一致）
        }
        BroadcastState();
    }

    private void BroadcastState()
    {
        var json = BuildStateJson();
        foreach (var (ws, channel) in _clients)
        {
            if (ws.State != WebSocketState.Open) continue;
            channel.Writer.TryWrite(json);
        }
    }

    private string BuildStateJson()
    {
        int bpm; bool conn; string status; long ts;
        lock (_stateLock)
        {
            bpm = _currentBpm;
            conn = _isConnected;
            status = _status;
            ts = _lastTimestamp;
        }
        return JsonSerializer.Serialize(new
        {
            heart_rate = bpm,
            connected = conn,
            status,
            timestamp = ts,
            speed = (object?)null,
        }, _stateJsonOptions);
    }

    private void OnSettingsPropertyChanged(object? sender, System.ComponentModel.PropertyChangedEventArgs e)
    {
        // 开关或端口变化 → 重启监听应用新配置
        if (e.PropertyName is nameof(NetworkSettings.HttpServerEnabled)
            or nameof(NetworkSettings.HttpServerPort)
            or nameof(NetworkSettings.WebSocketServerEnabled)
            or nameof(NetworkSettings.WebSocketServerPort))
        {
            _ = ApplyAsync();
        }
    }

    private void RaiseStatusChanged() => StatusChanged?.Invoke();

    public void Dispose()
    {
        _heartRate.HeartRateReceived -= OnHeartRateReceived;
        _heartRate.ConnectionChanged -= OnConnectionChanged;
        _settings.PropertyChanged -= OnSettingsPropertyChanged;
        _ = StopAsync();
        _applyLock.Dispose();
    }
}
