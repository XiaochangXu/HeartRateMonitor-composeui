using System.Collections.ObjectModel;
using HeartRate.Helpers;
using HeartRate.Models;
using HeartRate.Services;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;

namespace HeartRate.ViewModels
{
    /// <summary>左侧设备列表：BLE 广播扫描、设备集合与选择。</summary>
    public partial class DeviceListViewModel : ObservableObject
    {
        private readonly HeartRateService _service;
        private readonly DispatcherQueue _uiDispatcher;
        // 历史设备缓存（MAC → 信息），启动时载入展示，扫描到新数据即覆盖
        private readonly Dictionary<ulong, CachedDevice> _cache;
        // 扫描自动停止计时器：1 分钟未连接则停止，避免持续占用蓝牙/系统资源
        private readonly DispatcherQueueTimer _scanTimer;
        // 自动连接上一次设备的目标与 30 秒超时计时器
        private readonly DispatcherQueueTimer _autoConnectTimer;
        private ulong? _autoConnectTarget;

        /// <summary>扫描到自动连接目标设备时触发（已发现且未超时）。</summary>
        public event EventHandler<BleDeviceInfo>? AutoConnectTargetFound;

        public ObservableCollection<BleDeviceInfo> Devices { get; } = new();

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(ScanProgressActive))]
        private bool _isScanning;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(HasDevices))]
        private BleDeviceInfo? _selectedDevice;

        /// <summary>扫描中且尚无结果时显示圆形进度指示器。</summary>
        public bool ScanProgressActive => IsScanning && Devices.Count == 0;

        /// <summary>扫描状态提示（发现设备/蓝牙不可用），为空时不显示。</summary>
        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(ScanStatusVisibility))]
        private string? _scanStatusMessage;

        public Visibility ScanStatusVisibility => string.IsNullOrEmpty(ScanStatusMessage) ? Visibility.Collapsed : Visibility.Visible;

        public DeviceListViewModel(HeartRateService service, DispatcherQueue uiDispatcher)
        {
            _service = service;
            _uiDispatcher = uiDispatcher;
            _service.DeviceDiscovered += OnDeviceDiscovered;
            _service.DeviceUpdated += OnDeviceUpdated;
            _service.ScanStateChanged += OnScanStateChanged;

            _scanTimer = uiDispatcher.CreateTimer();
            _scanTimer.Interval = TimeSpan.FromMinutes(1);
            _scanTimer.Tick += OnScanTimerTick;

            _autoConnectTimer = uiDispatcher.CreateTimer();
            _autoConnectTimer.Interval = TimeSpan.FromSeconds(30);
            _autoConnectTimer.Tick += OnAutoConnectTimerTick;

            // 启动时载入历史设备缓存：只展示含心率服务的设备，过滤无关广播设备；
            // 以"缓存占位项"展示（信号 --），本次扫描发现后再就地替换为实时项
            _cache = DeviceCacheService.Load();
            foreach (var c in _cache.Values
                .Where(d => d.HasHeartRateService)
                .OrderByDescending(c => c.LastSeen))
            {
                Devices.Add(new BleDeviceInfo
                {
                    Address = c.Address,
                    Name = c.Name,
                    HasHeartRateService = c.HasHeartRateService,
                    Rssi = int.MinValue,
                    IsCachedOnly = true,
                });
            }
            OnPropertyChanged(nameof(EmptyHintVisibility));
            OnPropertyChanged(nameof(HasDevices));
            OnPropertyChanged(nameof(ScanProgressActive));
        }

        /// <summary>窗口关闭时退订服务事件，并落盘挂起的缓存写入。</summary>
        public void Unsubscribe()
        {
            _scanTimer.Tick -= OnScanTimerTick;
            _scanTimer.Stop();
            _autoConnectTimer.Tick -= OnAutoConnectTimerTick;
            _autoConnectTimer.Stop();
            _service.DeviceDiscovered -= OnDeviceDiscovered;
            _service.DeviceUpdated -= OnDeviceUpdated;
            _service.ScanStateChanged -= OnScanStateChanged;
            DeviceCacheService.Flush();
        }

        public string ScanButtonContent => IsScanning ? L.DeviceList_StopScan : L.DeviceList_StartScan;

        public Visibility EmptyHintVisibility => Devices.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

        public bool HasDevices => Devices.Count > 0;

        partial void OnIsScanningChanged(bool value)
        {
            OnPropertyChanged(nameof(ScanButtonContent));
        }

        private void OnDeviceDiscovered(object? sender, BleDeviceInfo device)
        {
            _uiDispatcher.TryEnqueue(() =>
            {
                // 若列表已有同地址的缓存占位项，就地替换为本次发现的实时项
                bool replaced = false;
                for (int i = 0; i < Devices.Count; i++)
                {
                    if (Devices[i].Address == device.Address)
                    {
                        Devices[i] = device;
                        replaced = true;
                        break;
                    }
                }
                if (!replaced)
                    Devices.Add(device);

                // 自动连接上一次设备：发现目标且未超时则触发一次
                if (_autoConnectTarget is not null && device.Address == _autoConnectTarget.Value)
                {
                    _autoConnectTarget = null;
                    _autoConnectTimer.Stop();
                    AutoConnectTargetFound?.Invoke(this, device);
                }

                // 发现提示 + 同步到本地缓存
                ScanStatusMessage = Loc.Format("DeviceList_FoundDevice", device.DisplayName);
                UpdateCache(device);

                OnPropertyChanged(nameof(EmptyHintVisibility));
                OnPropertyChanged(nameof(HasDevices));
                OnPropertyChanged(nameof(ScanProgressActive));
            });
        }

        /// <summary>设备信息被补齐（名称/心率服务）时，就地替换列表条目并同步缓存。</summary>
        private void OnDeviceUpdated(object? sender, BleDeviceInfo device)
        {
            _uiDispatcher.TryEnqueue(() =>
            {
                for (int i = 0; i < Devices.Count; i++)
                {
                    if (Devices[i].Address == device.Address)
                    {
                        Devices[i] = device;
                        // 被替换的若是当前选中项，同步更新选中引用，保证连接按钮与右侧面板一致
                        if (SelectedDevice?.Address == device.Address)
                            SelectedDevice = device;
                        UpdateCache(device);
                        return;
                    }
                }
            });
        }

        /// <summary>把设备最新信息写入内存缓存并触发防抖落盘。
        /// 首包广播常缺名称：新名称为空时保留旧缓存名，避免空名覆盖历史真实名。</summary>
        private void UpdateCache(BleDeviceInfo device)
        {
            var name = device.Name;
            if (string.IsNullOrEmpty(name)
                && _cache.TryGetValue(device.Address, out var old)
                && !string.IsNullOrEmpty(old.Name))
            {
                name = old.Name;
            }
            _cache[device.Address] = new CachedDevice(
                device.Address, name, device.HasHeartRateService, DateTime.UtcNow);
            DeviceCacheService.Save(_cache.Values);
        }

        /// <summary>按地址查历史缓存名（连接时首包缺名的兜底），无缓存时返回 null。</summary>
        public string? GetCachedDeviceName(ulong address)
            => _cache.TryGetValue(address, out var cached) ? cached.Name : null;

        [RelayCommand]
        private void ToggleScan()
        {
            if (IsScanning)
            {
                StopScan();
            }
            else
            {
                StartScanning();
            }
        }

        /// <summary>启动扫描：不清空列表（保留缓存设备），失败时提示蓝牙不可用。
        /// 成功启动即置 IsScanning=true（不依赖 watcher.Status 的即时读取，
        /// 避免按钮激活状态与真实扫描不一致）。</summary>
        private void StartScanning()
        {
            ScanStatusMessage = null;
            try
            {
                _service.StartScan();
                IsScanning = true;
                _scanTimer.Start();
            }
            catch
            {
                IsScanning = false;
                ScanStatusMessage = L.DeviceList_BluetoothUnavailable;
            }
            OnPropertyChanged(nameof(EmptyHintVisibility));
            OnPropertyChanged(nameof(HasDevices));
            OnPropertyChanged(nameof(ScanProgressActive));
        }

        /// <summary>watcher 状态变化（后台线程触发，封送到 UI）：保持按钮与真实扫描状态同步。</summary>
        private void OnScanStateChanged(object? sender, bool scanning)
        {
            _uiDispatcher.TryEnqueue(() => IsScanning = scanning);
        }

        /// <summary>扫描 1 分钟未连接则自动停止并提示（避免长时间占用蓝牙/系统资源）。</summary>
        private void OnScanTimerTick(DispatcherQueueTimer sender, object args)
        {
            _scanTimer.Stop();
            if (!IsScanning) return;
            _service.StopScan();
            IsScanning = false;
            ScanStatusMessage = L.DeviceList_ScanAutoStopped;
        }

        /// <summary>应用启动时自动开始持续扫描（用户手动停止/连接成功后不再自动开启）。</summary>
        public void AutoStartScan()
        {
            if (IsScanning) return;
            StartScanning();
        }

        /// <summary>设置启动自动连接目标：30 秒内扫描到该设备即触发 AutoConnectTargetFound。</summary>
        public void SetAutoConnectTarget(ulong address)
        {
            _autoConnectTarget = address;
            _autoConnectTimer.Start();
        }

        /// <summary>自动连接目标 30 秒超时未发现：取消并提示。</summary>
        private void OnAutoConnectTimerTick(DispatcherQueueTimer sender, object args)
        {
            _autoConnectTimer.Stop();
            if (_autoConnectTarget is null) return;
            _autoConnectTarget = null;
            ScanStatusMessage = L.DeviceList_AutoConnectTimeout;
        }

        /// <summary>停止扫描（用户手动、点击连接、连接成功、超时自动停止时共用）。</summary>
        public void StopScan()
        {
            _scanTimer.Stop();
            if (!IsScanning) return;
            _service.StopScan();
            IsScanning = false;
        }

        [RelayCommand]
        private void Clear()
        {
            Devices.Clear();
            SelectedDevice = null;
            _cache.Clear();
            DeviceCacheService.Save(_cache.Values);
            OnPropertyChanged(nameof(EmptyHintVisibility));
            OnPropertyChanged(nameof(HasDevices));
            OnPropertyChanged(nameof(ScanProgressActive));
        }

        /// <summary>强制断开请求（由 MainViewModel 注入到右侧面板的强制断开逻辑）。</summary>
        public Action? ForceDisconnectRequested { get; set; }

        /// <summary>强制断开：无论何种状态都断开连接并停止重连（保底按钮）。</summary>
        [RelayCommand]
        private void ForceDisconnect() => ForceDisconnectRequested?.Invoke();
    }
}
