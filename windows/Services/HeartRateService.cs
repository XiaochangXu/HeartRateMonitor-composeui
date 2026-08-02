using System.Collections.Concurrent;
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
    private BluetoothLEAdvertisementWatcher? _watcher;
    // BLE 广播回调在线程池触发，StartScan 的 Clear 在 UI 线程执行，
    // 用 ConcurrentDictionary 保证跨线程去重安全。
    private readonly ConcurrentDictionary<ulong, BleDeviceInfo> _devices = new();
    private BluetoothLEDevice? _device;
    private GattDeviceService? _service;
    private GattCharacteristic? _hrCharacteristic;

    /// <summary>扫描到新设备时触发。</summary>
    public event EventHandler<BleDeviceInfo>? DeviceDiscovered;

    /// <summary>收到新心率值时触发，参数为 bpm。</summary>
    public event EventHandler<int>? HeartRateReceived;

    /// <summary>连接状态变化时触发。</summary>
    public event EventHandler<BluetoothConnectionStatus>? ConnectionChanged;

    public IReadOnlyCollection<BleDeviceInfo> Devices => _devices.Values.ToArray();
    public bool IsScanning => _watcher?.Status == BluetoothLEAdvertisementWatcherStatus.Started;
    public bool IsConnected => _device?.ConnectionStatus == BluetoothConnectionStatus.Connected;

    /// <summary>外部数据源（如局域网传输收到的手机推送）触发心率更新。
    /// 走和 BLE 同一条 HeartRateReceived 链路，首页卡片和悬浮窗自动刷新。</summary>
    public void RaiseHeartRateReceived(int bpm) => HeartRateReceived?.Invoke(this, bpm);

    // ---------------- 扫描 ----------------

    public void StartScan()
    {
        if (_watcher is not null) return;

        // 每次全新扫描前清空去重表：否则 UI 清空列表后重新扫描，
        // 之前见过的设备会被去重拦截而不再上报（列表保持为空）。
        _devices.Clear();

        _watcher = new BluetoothLEAdvertisementWatcher
        {
            ScanningMode = BluetoothLEScanningMode.Active,
        };
        _watcher.Received += OnAdvertisementReceived;
        _watcher.Start();
    }

    public void StopScan()
    {
        if (_watcher is null) return;
        _watcher.Received -= OnAdvertisementReceived;
        _watcher.Stop();
        _watcher = null;
    }

    private void OnAdvertisementReceived(BluetoothLEAdvertisementWatcher sender, BluetoothLEAdvertisementReceivedEventArgs args)
    {
        var info = new BleDeviceInfo
        {
            Address = args.BluetoothAddress,
            Name = args.Advertisement.LocalName,
            HasHeartRateService = args.Advertisement.ServiceUuids.Contains(GattServiceUuids.HeartRate),
        };
        if (!_devices.TryAdd(args.BluetoothAddress, info)) return;
        DeviceDiscovered?.Invoke(this, info);
    }

    // ---------------- 连接与订阅 ----------------

    public async Task<bool> ConnectAsync(ulong address, CancellationToken ct = default)
    {
        Disconnect();
        bool success = false;
        try
        {
            ct.ThrowIfCancellationRequested();

            _device = await BluetoothLEDevice.FromBluetoothAddressAsync(address);
            if (_device is null) return false;

            _device.ConnectionStatusChanged += OnDeviceConnectionStatusChanged;
            ct.ThrowIfCancellationRequested();

            // 优先按已知心率服务 UUID 获取，失败则全量遍历服务列表
            var svcResult = await _device.GetGattServicesForUuidAsync(GattServiceUuids.HeartRate, BluetoothCacheMode.Uncached);
            _service = svcResult.Status == GattCommunicationStatus.Success
                ? svcResult.Services.FirstOrDefault()
                : null;

            if (_service is null)
            {
                var all = await _device.GetGattServicesAsync(BluetoothCacheMode.Uncached);
                if (all.Status == GattCommunicationStatus.Success)
                    _service = all.Services.FirstOrDefault(s => s.Uuid == GattServiceUuids.HeartRate);
            }
            if (_service is null) return false;
            ct.ThrowIfCancellationRequested();

            var chResult = await _service.GetCharacteristicsForUuidAsync(GattCharacteristicUuids.HeartRateMeasurement);
            if (chResult.Status != GattCommunicationStatus.Success)
                return false;
            _hrCharacteristic = chResult.Characteristics.FirstOrDefault();
            if (_hrCharacteristic is null) return false;

            // 开启 CCCD 通知
            var writeResult = await _hrCharacteristic.WriteClientCharacteristicConfigurationDescriptorAsync(
                GattClientCharacteristicConfigurationDescriptorValue.Notify);
            if (writeResult != GattCommunicationStatus.Success) return false;

            _hrCharacteristic.ValueChanged += OnHeartRateValueChanged;
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
