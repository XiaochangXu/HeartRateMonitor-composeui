using HeartRate.Helpers;
using HeartRate.Models;
using HeartRate.Services;
using Microsoft.UI.Dispatching;
using Windows.Devices.Bluetooth;

namespace HeartRate.ViewModels
{
    /// <summary>当前心率数据来源：蓝牙 GATT 或局域网手机推送。</summary>
    public enum ConnectionMode { None, Bluetooth, Lan }

    /// <summary>右侧心率面板：GATT 连接、实时心率、悬浮窗开关。</summary>
    public partial class HeartRateViewModel : ObservableObject
    {
        private readonly HeartRateService _service;
        private readonly DispatcherQueue _uiDispatcher;
        private BleDeviceInfo? _connectedDevice;

        // 局域网数据源信息（手机推送时填充）
        private string? _lanDeviceName;
        private string? _lanAddress;

        /// <summary>当前选中的设备，由 MainViewModel 注入。</summary>
        public Func<BleDeviceInfo?> GetSelectedDevice { get; set; } = () => null;

        /// <summary>请求主窗口显示/隐藏悬浮窗。</summary>
        public event EventHandler<bool>? FloatWindowVisibilityRequested;

        /// <summary>请求主窗口显示信息对话框（标题, 正文），由 MainWindow 注入。</summary>
        public Func<string, string, Task>? ShowDialogRequested { get; set; }

        [ObservableProperty]
        private bool _isConnecting;

        [ObservableProperty]
        private bool _isConnected;

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(HeartRateText))]
        [NotifyPropertyChangedFor(nameof(HasHeartRate))]
        private int? _heartRate;

        [ObservableProperty]
        private bool _isFloatWindowVisible;

        /// <summary>当前数据来源（蓝牙/局域网/无）。</summary>
        [ObservableProperty]
        private ConnectionMode _connectionMode = ConnectionMode.None;

        /// <summary>局域网手机推送处于活跃状态（独立于 BLE 的 IsConnected）。</summary>
        [ObservableProperty]
        private bool _isLanConnected;

        public HeartRateViewModel(HeartRateService service, DispatcherQueue uiDispatcher)
        {
            _service = service;
            _uiDispatcher = uiDispatcher;
            _service.HeartRateReceived += OnHeartRateReceived;
            _service.ConnectionChanged += OnConnectionChanged;
        }

        // ── 派生显示属性 ────────────────────────────────────────────────────

        public string ConnectButtonContent => IsConnected ? L.HeartRate_Disconnect : L.HeartRate_Connect;

        public bool CanToggleConnect => !IsConnecting && !IsConnected && GetSelectedDevice() is not null;

        public string HeartRateText => HeartRate?.ToString() ?? "--";

        public bool HasHeartRate => HeartRate is not null;

        /// <summary>有活跃数据源（BLE 或局域网），用于状态指示灯。</summary>
        public bool HasActiveSource => IsConnected || IsLanConnected;

        /// <summary>连接方式显示文本：蓝牙 / 局域网 / --。</summary>
        public string ConnectionModeText => ConnectionMode switch
        {
            ConnectionMode.Bluetooth => L.HeartRate_ConnModeBluetooth,
            ConnectionMode.Lan       => L.HeartRate_ConnModeLan,
            _                        => "--"
        };

        public string StatusText =>
            StatusTextFallback is not null ? StatusTextFallback :
            IsConnecting      ? L.HeartRate_Connecting :
            IsConnected       ? Loc.Format("HeartRate_ConnectedName", ConnectedDeviceName) :
            IsLanConnected    ? Loc.Format("HeartRate_ConnectedName", ConnectedDeviceName) :
            GetSelectedDevice() is null ? L.HeartRate_NoDevice :
            L.HeartRate_NotConnected;

        /// <summary>当前连接的设备名称（蓝牙用 BLE 设备名，局域网用手机设备名）。</summary>
        public string ConnectedDeviceName =>
            ConnectionMode == ConnectionMode.Lan ? (_lanDeviceName ?? "--") :
            _connectedDevice?.DisplayName ?? L.HeartRate_NotConnected;

        /// <summary>当前连接地址：蓝牙显示 MAC（c8:f0:61:xx:xx:xx），局域网显示 IP。</summary>
        public string ConnectedAddressText =>
            ConnectionMode == ConnectionMode.Lan ? (_lanAddress ?? "--") :
            _connectedDevice is not null ? FormatMacAddress(_connectedDevice.AddressText) :
            "--";

        /// <summary>BLE 心率服务可用性（局域网模式下显示 N/A）。</summary>
        public string HasHeartRateServiceText =>
            ConnectionMode == ConnectionMode.Lan ? "N/A" :
            _connectedDevice?.HasHeartRateService == true ? L.HeartRate_Yes : L.HeartRate_No;

        /// <summary>将 BLE AddressText（C8F0617600XX）格式化为 MAC（c8:f0:61:76:00:xx）。</summary>
        private static string FormatMacAddress(string hex)
        {
            if (string.IsNullOrEmpty(hex) || hex.Length < 12) return hex ?? "--";
            var s = hex.ToLowerInvariant();
            return $"{s[0..2]}:{s[2..4]}:{s[4..6]}:{s[6..8]}:{s[8..10]}:{s[10..12]}";
        }

        partial void OnIsConnectedChanged(bool value)
        {
            OnPropertyChanged(nameof(ConnectButtonContent));
            OnPropertyChanged(nameof(CanToggleConnect));
            OnPropertyChanged(nameof(StatusText));
            OnPropertyChanged(nameof(HasActiveSource));
            if (!value)
            {
                HeartRate = null;
                _connectedDevice = null;
                ConnectionMode = ConnectionMode.None;
                OnPropertyChanged(nameof(ConnectedDeviceName));
                OnPropertyChanged(nameof(ConnectedAddressText));
                OnPropertyChanged(nameof(HasHeartRateServiceText));
                OnPropertyChanged(nameof(ConnectionModeText));
            }
        }

        partial void OnIsLanConnectedChanged(bool value)
        {
            OnPropertyChanged(nameof(HasActiveSource));
            OnPropertyChanged(nameof(StatusText));
            if (!value)
            {
                _lanDeviceName = null;
                _lanAddress = null;
                if (ConnectionMode == ConnectionMode.Lan) ConnectionMode = ConnectionMode.None;
                HeartRate = null;
                OnPropertyChanged(nameof(ConnectedDeviceName));
                OnPropertyChanged(nameof(ConnectedAddressText));
                OnPropertyChanged(nameof(HasHeartRateServiceText));
                OnPropertyChanged(nameof(ConnectionModeText));
            }
        }

        partial void OnConnectionModeChanged(ConnectionMode value)
        {
            OnPropertyChanged(nameof(ConnectionModeText));
            OnPropertyChanged(nameof(ConnectedDeviceName));
            OnPropertyChanged(nameof(ConnectedAddressText));
            OnPropertyChanged(nameof(HasHeartRateServiceText));
        }

        partial void OnIsConnectingChanged(bool value)
        {
            OnPropertyChanged(nameof(CanToggleConnect));
            OnPropertyChanged(nameof(StatusText));
        }

        partial void OnHeartRateChanged(int? value)
        {
            OnPropertyChanged(nameof(HeartRateText));
            OnPropertyChanged(nameof(HasHeartRate));
        }

        partial void OnIsFloatWindowVisibleChanged(bool value)
        {
            FloatWindowVisibilityRequested?.Invoke(this, value);
        }

        /// <summary>列表选择变化时刷新连接按钮可用状态。</summary>
        public void NotifySelectedDeviceChanged()
        {
            OnPropertyChanged(nameof(CanToggleConnect));
            OnPropertyChanged(nameof(StatusText));
        }

        // ── 命令 ────────────────────────────────────────────────────────────

        [RelayCommand(CanExecute = nameof(CanToggleConnect))]
        private async Task ToggleConnect()
        {
            // 局域网已连接时禁止蓝牙连接：弹出提示后直接返回。
            if (IsLanConnected)
            {
                if (ShowDialogRequested is not null)
                    await ShowDialogRequested(L.HeartRate_BluetoothBlockedTitle, L.HeartRate_BluetoothBlockedBody);
                return;
            }

            if (IsConnected)
            {
                _service.Disconnect();
                IsConnected = false;
                return;
            }

            var device = GetSelectedDevice();
            if (device is null) return;

            IsConnecting = true;
            var ok = await _service.ConnectAsync(device.Address);
            IsConnecting = false;

            if (!ok)
            {
                StatusTextFallback = L.HeartRate_ConnectFailed;
                OnPropertyChanged(nameof(StatusText));
                return;
            }

            _connectedDevice = device;
            ConnectionMode = ConnectionMode.Bluetooth;
            IsConnected = true;
            OnPropertyChanged(nameof(ConnectedDeviceName));
            OnPropertyChanged(nameof(ConnectedAddressText));
            OnPropertyChanged(nameof(HasHeartRateServiceText));
        }

        /// <summary>连接失败/断开时的临时状态文本，优先于派生态。</summary>
        private string? StatusTextFallback;

        [RelayCommand]
        private void ToggleFloatWindow()
        {
            IsFloatWindowVisible = !IsFloatWindowVisible;
        }

        // ── 局域网数据源（由 MainViewModel 转发，已在 UI 线程）──────────────

        /// <summary>局域网收到手机心率推送时更新数据源信息。</summary>
        public void UpdateLanSource(string deviceName, string address)
        {
            _lanDeviceName = deviceName;
            _lanAddress = address;
            ConnectionMode = ConnectionMode.Lan;
            IsLanConnected = true;
            OnPropertyChanged(nameof(ConnectedDeviceName));
            OnPropertyChanged(nameof(ConnectedAddressText));
        }

        /// <summary>局域网手机断开时清除数据源信息。</summary>
        public void ClearLanSource()
        {
            IsLanConnected = false; // 触发 OnIsLanConnectedChanged 清理其余字段
        }

        // ── 服务事件（后台线程，封送到 UI）──────────────────────────────────

        private void OnHeartRateReceived(object? sender, int bpm)
        {
            _uiDispatcher.TryEnqueue(() =>
            {
                StatusTextFallback = null;
                HeartRate = bpm;
                OnPropertyChanged(nameof(StatusText));
            });
        }

        private void OnConnectionChanged(object? sender, BluetoothConnectionStatus status)
        {
            if (status != BluetoothConnectionStatus.Disconnected) return;
            _uiDispatcher.TryEnqueue(() =>
            {
                if (!IsConnected) return;
                StatusTextFallback = L.HeartRate_Disconnected;
                IsConnected = false;
                OnPropertyChanged(nameof(StatusText));
            });
        }
    }
}
