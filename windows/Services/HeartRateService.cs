using System.Collections.Concurrent;
using System.Text.RegularExpressions;
using HeartRate.Models;
using Windows.Devices.Bluetooth;
using Windows.Devices.Bluetooth.Advertisement;
using Windows.Devices.Bluetooth.GenericAttributeProfile;

namespace HeartRate.Services;

/// <summary>
/// 心率数据核心服务：BLE 广播扫描 + GATT 连接 + 心率测量特征订阅与解析。
/// 事件回调在线程池线程上触发，UI 侧需通过 DispatcherQueue 封送。
/// </summary>
public sealed class HeartRateService : IDisposable
{
    // 常驻一个 BLE 广播 watcher：反复 Start/Stop 复用，避免每次扫描都重建
    // 导致 Windows BLE 协议栈重新初始化（首次投递延迟会明显拉长）。
    private readonly BluetoothLEAdvertisementWatcher _watcher;
    // BLE 广播回调在线程池触发，StartScan 的 Clear 在 UI 线程执行，
    // 用 ConcurrentDictionary 保证跨线程去重安全。
    private readonly ConcurrentDictionary<ulong, BleDeviceInfo> _devices = new();
    // 异步补查设备名的去重标记，避免同一设备反复发起解析
    private readonly ConcurrentDictionary<ulong, byte> _resolvingNames = new();
    // 名称补查并发闸门：限制同时打向蓝牙控制器的 FromBluetoothAddressAsync 数量
    private readonly SemaphoreSlim _nameResolveGate = new(2, 2);
    // 每个设备最后一次触发 DeviceUpdated 的时间（TickCount64 毫秒），用于 RSSI 更新节流
    private readonly ConcurrentDictionary<ulong, long> _lastRssiUpdate = new();
    private BluetoothLEDevice? _device;
    private GattDeviceService? _service;
    private GattCharacteristic? _hrCharacteristic;

    public HeartRateService()
    {
        // Active 主动扫描：能拿到扫描响应里的真实设备名（Passive 拿不到，
        // 且 Windows 对无名设备只给 "Bluetooth <MAC>" 兜底名）。watcher 已常驻复用，
        // 消除了每次扫描重建协议栈的开销，首次投递不会像之前那样拖到半分钟。
        _watcher = new BluetoothLEAdvertisementWatcher
        {
            ScanningMode = BluetoothLEScanningMode.Active,
        };
        _watcher.Received += OnAdvertisementReceived;
        // watcher 没有 StatusChanged 事件；用 Stopped 事件把"扫描停止/中止"同步给 UI
        // （启动状态由 StartScanning 成功返回后直接置位，无需事件）
        _watcher.Stopped += OnWatcherStopped;
    }

    /// <summary>扫描状态变化（true=扫描中），供 UI 同步扫描按钮激活状态。</summary>
    public event EventHandler<bool>? ScanStateChanged;

    private void OnWatcherStopped(BluetoothLEAdvertisementWatcher sender, BluetoothLEAdvertisementWatcherStoppedEventArgs args)
        => ScanStateChanged?.Invoke(this, false);

    /// <summary>扫描到新设备时触发。</summary>
    public event EventHandler<BleDeviceInfo>? DeviceDiscovered;

    /// <summary>已上报设备的信息被补齐（名称/心率服务）时触发，UI 侧应就地替换列表条目。</summary>
    public event EventHandler<BleDeviceInfo>? DeviceUpdated;

    /// <summary>收到新心率值时触发，参数为 bpm。</summary>
    public event EventHandler<int>? HeartRateReceived;

    /// <summary>连接状态变化时触发。</summary>
    public event EventHandler<BluetoothConnectionStatus>? ConnectionChanged;

    public IReadOnlyCollection<BleDeviceInfo> Devices => _devices.Values.ToArray();
    public bool IsScanning => _watcher.Status == BluetoothLEAdvertisementWatcherStatus.Started;
    public bool IsConnected => _device?.ConnectionStatus == BluetoothConnectionStatus.Connected;

    /// <summary>外部数据源（如局域网传输收到的手机推送）触发心率更新。
    /// 走和 BLE 同一条 HeartRateReceived 链路，首页卡片和悬浮窗自动刷新。</summary>
    public void RaiseHeartRateReceived(int bpm) => HeartRateReceived?.Invoke(this, bpm);

    // ---------------- 扫描 ----------------

    public void StartScan()
    {
        if (IsScanning) return;

        // 每次全新扫描前清空去重表与节流时间表：否则 UI 清空列表后重新扫描，
        // 之前见过的设备会被去重拦截而不再上报（列表保持为空）。
        _devices.Clear();
        _lastRssiUpdate.Clear();
        _watcher.Start();
    }

    public void StopScan()
    {
        if (!IsScanning) return;
        _watcher.Stop();
    }

    private void OnAdvertisementReceived(BluetoothLEAdvertisementWatcher sender, BluetoothLEAdvertisementReceivedEventArgs args)
    {
        // 过滤 Windows 兜底名/纯 MAC 地址等无意义名称，避免其覆盖真实设备名
        var name = IsMeaningfulName(args.Advertisement.LocalName) ? args.Advertisement.LocalName : string.Empty;
        var hasHr = args.Advertisement.ServiceUuids.Contains(GattServiceUuids.HeartRate);
        var rssi = IsValidRssi(args.RawSignalStrengthInDBm) ? args.RawSignalStrengthInDBm : int.MinValue;

        // 同一设备会广播多包（含主动扫描的响应包），名称/心率服务 UUID 常出现在后到的包中：
        // 不能像首包那样直接去重丢弃，否则先到的不含名称的包会把后到的名称包拦截掉，
        // 导致列表里几乎全是"未知设备"。
        if (_devices.TryGetValue(args.BluetoothAddress, out var existing))
        {
            var newName = string.IsNullOrEmpty(existing.Name) ? name : existing.Name;
            var newHasHr = existing.HasHeartRateService || hasHr;
            // 信号强度取扫描期间看到的最强值；RSSI 更新做节流：
            // 提升 ≥3dBm 或距上次刷新 ≥500ms 才替换条目，避免每个广播包都重建列表行导致 UI 抖动
            var newRssi = existing.Rssi;
            if (rssi != int.MinValue && (existing.Rssi == int.MinValue || rssi > existing.Rssi))
                newRssi = rssi;

            var nameChanged = newName != existing.Name;
            var hrChanged = newHasHr != existing.HasHeartRateService;
            var rssiChanged = newRssi != existing.Rssi;
            var rssiAllowed = false;
            if (rssiChanged)
            {
                var last = _lastRssiUpdate.TryGetValue(args.BluetoothAddress, out var t) ? t : 0;
                rssiAllowed = rssi >= existing.Rssi + 3 || Environment.TickCount64 - last >= 500;
            }

            if (nameChanged || hrChanged || (rssiChanged && rssiAllowed))
            {
                var replacement = new BleDeviceInfo
                {
                    Address = existing.Address,
                    Name = newName,
                    HasHeartRateService = newHasHr,
                    Rssi = newRssi,
                };
                if (_devices.TryUpdate(args.BluetoothAddress, replacement, existing))
                {
                    existing = replacement;
                    _lastRssiUpdate[args.BluetoothAddress] = Environment.TickCount64;
                    DeviceUpdated?.Invoke(this, existing);
                }
            }
            // 名称仍未知时继续尝试异步补查
            if (string.IsNullOrEmpty(existing.Name))
                _ = ResolveDeviceNameAsync(existing);
            return;
        }

        var info = new BleDeviceInfo
        {
            Address = args.BluetoothAddress,
            Name = name,
            HasHeartRateService = hasHr,
            Rssi = rssi,
        };
        if (!_devices.TryAdd(args.BluetoothAddress, info)) return;
        DeviceDiscovered?.Invoke(this, info);

        // 广播包（尤其首包）常不含设备名，需经设备对象补查（名称来自系统缓存或设备响应）
        if (string.IsNullOrEmpty(info.Name))
            _ = ResolveDeviceNameAsync(info);
    }

    /// <summary>蓝牙规范有效 RSSI 范围 -127~+20 dBm；0 常见于信号不可用的占位值。</summary>
    private static bool IsValidRssi(int rssi) => rssi != 0 && rssi is >= -127 and <= 20;

    // Windows 对未配对/无名称 BLE 设备自动生成的兜底名（形如 "Bluetooth c8:f0:61:76:c9:03"）
    // 或纯 MAC 地址，都不是真实设备名，过滤后按"未知设备"处理。
    private static readonly Regex FallbackNameRegex = new(
        @"^(Bluetooth(\s+LE)?\s+)?[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}$",
        RegexOptions.Compiled);

    private static bool IsMeaningfulName(string? name) =>
        !string.IsNullOrEmpty(name) && !FallbackNameRegex.IsMatch(name);

    /// <summary>
    /// 广告包未携带名称时，通过设备对象补查名称。BluetoothLEDevice.Name 通常能取到
    /// 广播/扫描响应/系统缓存中的真实设备名，比单包广告里的 LocalName 更可靠。
    /// 通过并发闸门限制同时发起的补查数量，避免扫描初期争用蓝牙控制器。
    /// </summary>
    private async Task ResolveDeviceNameAsync(BleDeviceInfo info)
    {
        if (!_resolvingNames.TryAdd(info.Address, 0)) return;
        try
        {
            await _nameResolveGate.WaitAsync();
            try
            {
                using var device = await BluetoothLEDevice.FromBluetoothAddressAsync(info.Address);
                // 兜底名（"Bluetooth <MAC>"）不是真实设备名，过滤后保持"未知设备"
                if (device is null || !IsMeaningfulName(device.Name)) return;
                var name = device.Name;

                // 以补查结果与最新已知信息合并，避免覆盖并发到达的更新
                var current = _devices.TryGetValue(info.Address, out var latest) ? latest : info;
                var replacement = new BleDeviceInfo
                {
                    Address = info.Address,
                    Name = name,
                    HasHeartRateService = current.HasHeartRateService,
                    Rssi = current.Rssi,
                };
                if (_devices.TryUpdate(info.Address, replacement, current))
                {
                    _lastRssiUpdate[info.Address] = Environment.TickCount64;
                    DeviceUpdated?.Invoke(this, replacement);
                }
            }
            finally
            {
                _nameResolveGate.Release();
            }
        }
        catch
        {
            // 蓝牙关闭/设备不可达时补查失败可忽略，设备名保持"未知设备"
        }
        finally
        {
            _resolvingNames.TryRemove(info.Address, out _);
        }
    }

    // ---------------- 连接与订阅 ----------------

    public async Task<bool> ConnectAsync(ulong address, CancellationToken ct = default)
    {
        Disconnect();
        bool success = false;
        try
        {
            ct.ThrowIfCancellationRequested();

            var device = await BluetoothLEDevice.FromBluetoothAddressAsync(address);
            if (device is null) return false;
            _device = device;
            device.ConnectionStatusChanged += OnDeviceConnectionStatusChanged;
            ct.ThrowIfCancellationRequested();
            // 外部 Disconnect（强制断开/手动断开）可能在 await 间隙清空字段：
            // 用引用校验识别"本连接已被打断"，干净退出而不是操作已释放的对象
            if (!ReferenceEquals(_device, device)) return false;

            // 优先按已知心率服务 UUID 获取，失败则全量遍历服务列表
            var svcResult = await device.GetGattServicesForUuidAsync(GattServiceUuids.HeartRate, BluetoothCacheMode.Uncached);
            if (!ReferenceEquals(_device, device)) return false;
            GattDeviceService? svc = svcResult.Status == GattCommunicationStatus.Success
                ? svcResult.Services.FirstOrDefault()
                : null;

            if (svc is null)
            {
                var all = await device.GetGattServicesAsync(BluetoothCacheMode.Uncached);
                if (!ReferenceEquals(_device, device)) return false;
                svc = all.Status == GattCommunicationStatus.Success
                    ? all.Services.FirstOrDefault(s => s.Uuid == GattServiceUuids.HeartRate)
                    : null;
            }
            if (svc is null) return false;
            _service = svc;
            ct.ThrowIfCancellationRequested();
            if (!ReferenceEquals(_device, device)) return false;

            var chResult = await svc.GetCharacteristicsForUuidAsync(GattCharacteristicUuids.HeartRateMeasurement);
            if (!ReferenceEquals(_device, device) || !ReferenceEquals(_service, svc)) return false;
            if (chResult.Status != GattCommunicationStatus.Success) return false;
            var ch = chResult.Characteristics.FirstOrDefault();
            if (ch is null) return false;
            _hrCharacteristic = ch;

            // 开启 CCCD 通知
            var writeResult = await ch.WriteClientCharacteristicConfigurationDescriptorAsync(
                GattClientCharacteristicConfigurationDescriptorValue.Notify);
            if (!ReferenceEquals(_device, device) || !ReferenceEquals(_hrCharacteristic, ch)) return false;
            if (writeResult != GattCommunicationStatus.Success) return false;

            ch.ValueChanged += OnHeartRateValueChanged;
            success = true;
            return true;
        }
        catch (OperationCanceledException)
        {
            Disconnect();
            throw;
        }
        catch
        {
            // GATT 调用在蓝牙关闭/设备不可达/访问被拒时抛异常，
            // 统一按失败处理（finally 负责清理部分赋值的资源）。
            return false;
        }
        finally
        {
            // 任何失败路径（异常或 return false）都释放已获取的设备/服务，
            // 避免反复失败连接累积未释放的 WinRT 句柄与事件订阅。
            if (!success) Disconnect();
        }
    }

    public void Disconnect()
    {
        if (_hrCharacteristic is not null)
        {
            _hrCharacteristic.ValueChanged -= OnHeartRateValueChanged;
            _hrCharacteristic = null;
        }
        _service?.Dispose();
        _service = null;
        if (_device is not null)
        {
            _device.ConnectionStatusChanged -= OnDeviceConnectionStatusChanged;
            _device.Dispose();
            _device = null;
        }
    }

    private void OnDeviceConnectionStatusChanged(BluetoothLEDevice sender, object args)
        => ConnectionChanged?.Invoke(this, sender.ConnectionStatus);

    private void OnHeartRateValueChanged(GattCharacteristic sender, GattValueChangedEventArgs args)
    {
        var reader = global::Windows.Storage.Streams.DataReader.FromBuffer(args.CharacteristicValue);
        var bytes = new byte[reader.UnconsumedBufferLength];
        reader.ReadBytes(bytes);
        var hr = ParseHeartRate(bytes);
        if (hr is not null)
            HeartRateReceived?.Invoke(this, hr.Value);
    }

    /// <summary>
    /// 按 BLE 心率测量特征 (0x2A37) 规范解析。
    /// byte0 flags: bit0=1 时心率值为 16 位小端，否则为 8 位。
    /// </summary>
    public static int? ParseHeartRate(byte[] data)
    {
        if (data.Length < 2) return null;
        bool isUint16 = (data[0] & 0x01) != 0;
        // 16 位值需要至少 3 字节，畸形包直接视为无效，
        // 否则 ToUInt16(data,1) 会在 GATT 回调线程越界抛异常。
        if (isUint16) return data.Length >= 3 ? BitConverter.ToUInt16(data, 1) : null;
        return data[1];
    }

    public void Dispose()
    {
        // 先停止扫描再断开 GATT，避免 watcher 持续占用 BLE 无线电与事件订阅
        StopScan();
        Disconnect();
    }
}
