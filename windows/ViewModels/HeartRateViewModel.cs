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
        // 自动重连任务句柄（仅意外断开后启动；强制/手动断开时取消并等待退出）
        private CancellationTokenSource? _reconnectCts;
        private Task? _reconnectTask;

        // 局域网数据源信息（手机推送时填充）
        private string? _lanDeviceName;
        private string? _lanAddress;

        /// <summary>当前选中的设备，由 MainViewModel 注入。</summary>
        public Func<BleDeviceInfo?> GetSelectedDevice { get; set; } = () => null;

        /// <summary>按地址查历史缓存名（连接时首包缺名的兜底），由 MainViewModel 注入。</summary>
        public Func<ulong, string?>? GetCachedDeviceName { get; set; }

        /// <summary>请求主窗口显示/隐藏悬浮窗。</summary>
        public event EventHandler<bool>? FloatWindowVisibilityRequested;

        /// <summary>请求主窗口显示信息对话框（标题, 正文），由 MainWindow 注入。</summary>
        public Func<string, string, Task>? ShowDialogRequested { get; set; }

        [ObservableProperty]
        [NotifyCanExecuteChangedFor(nameof(ToggleConnectCommand))]
        private bool _isConnecting;

        [ObservableProperty]
        [NotifyCanExecuteChangedFor(nameof(ToggleConnectCommand))]
        private bool _isConnected;

        /// <summary>意外断开后自动重连进行中（手动/强制断开时停止）。</summary>
        [ObservableProperty]
        [NotifyCanExecuteChangedFor(nameof(ToggleConnectCommand))]
        private bool _isReconnecting;

        /// <summary>自动重连开关（设置持久化，首页连接按钮旁控制）。</summary>
        public bool AutoReconnectEnabled
        {
            get => SettingsService.Current.AutoReconnectEnabled;
            set => SettingsService.Current.AutoReconnectEnabled = value;
        }

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
            _service.DeviceUpdated += OnServiceDeviceUpdated;
        }

        // ── 派生显示属性 ────────────────────────────────────────────────────

        public string ConnectButtonContent => IsConnected ? L.HeartRate_Disconnect : L.HeartRate_Connect;

        // 已连接时也允许点击（切换为断开）；连接中/重连中禁用。
        public bool CanToggleConnect => !IsConnecting && !IsReconnecting && (IsConnected || GetSelectedDevice() is not null);

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
            IsReconnecting    ? L.HeartRate_Reconnecting :
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
                _connectedDevice = null;
                // 仅当数据源确为蓝牙时才清空心率与模式；
                // LAN 手机推送活跃时不得破坏 LAN 数据源（否则 UI 出现矛盾文案）。
                if (ConnectionMode == ConnectionMode.Bluetooth)
                {
                    HeartRate = null;
                    ConnectionMode = ConnectionMode.None;
                }
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
                // 仅当数据源确为局域网时才清空心率与模式；
                // BLE 连接中（ConnectionMode==Bluetooth）不得抹掉正在显示的 BLE 心率。
                if (ConnectionMode == ConnectionMode.Lan)
                {
                    ConnectionMode = ConnectionMode.None;
                    HeartRate = null;
                }
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

        partial void OnIsReconnectingChanged(bool value)
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
            // 命令 CanExecute 依赖所选设备：必须显式通知按钮重新查询，
            // 否则按钮一直停留在启动时的禁用状态，点了也没反应。
            ToggleConnectCommand.NotifyCanExecuteChanged();
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
                // 手动断开：先置 IsConnected=false，使 OnConnectionChanged 的守卫短路，
                // 避免 Disconnect() 触发的断连事件被误判为"意外断开"而启动自动重连。
                IsConnected = false;
                _service.Disconnect();
                return;
            }

            var device = GetSelectedDevice();
            if (device is null) return;

            // 缓存占位设备尚未被本次扫描发现（可能不在范围内）：提示先扫描
            if (device.IsCachedOnly)
            {
                if (ShowDialogRequested is not null)
                    await ShowDialogRequested(L.HeartRate_DeviceNotInRangeTitle, L.HeartRate_DeviceNotInRangeBody);
                return;
            }

            await ConnectCoreAsync(device);
        }

        /// <summary>连接指定设备（手动/自动连接共用核心）。</summary>
        private async Task<bool> ConnectCoreAsync(BleDeviceInfo device)
        {
            if (IsConnected || IsConnecting || IsReconnecting) return false;
            IsConnecting = true;
            try
            {
                var ok = await _service.ConnectAsync(device.Address);
                if (!ok)
                {
                    StatusTextFallback = L.HeartRate_ConnectFailed;
                    OnPropertyChanged(nameof(StatusText));
                    return false;
                }

                // 首包广播常缺设备名：依次用服务内最新解析结果、历史缓存名兜底，
                // 避免连接成功但卡片显示"未知设备"，也避免空名覆盖 LastConnectedName。
                var name = device.Name;
                if (string.IsNullOrEmpty(name))
                    name = _service.Devices.FirstOrDefault(d => d.Address == device.Address)?.Name ?? string.Empty;
                if (string.IsNullOrEmpty(name) && GetCachedDeviceName?.Invoke(device.Address) is { Length: > 0 } cachedName)
                    name = cachedName;

                _connectedDevice = new BleDeviceInfo
                {
                    Address = device.Address,
                    Name = name,
                    HasHeartRateService = device.HasHeartRateService,
                    Rssi = device.Rssi,
                };
                ConnectionMode = ConnectionMode.Bluetooth;
                // 清除上一次失败遗留的 fallback，避免成功连接后仍显示"连接失败"
                StatusTextFallback = null;
                IsConnected = true;
                // 记录为"上一次成功连接的设备"，供下次启动自动连接
                SettingsService.Current.LastConnectedAddress = device.Address;
                SettingsService.Current.LastConnectedName = _connectedDevice.Name;
                OnPropertyChanged(nameof(ConnectedDeviceName));
                OnPropertyChanged(nameof(ConnectedAddressText));
                OnPropertyChanged(nameof(HasHeartRateServiceText));
                return true;
            }
            finally
            {
                // 即使 ConnectAsync 意外抛异常，也保证复位连接中状态，按钮不会永久禁用
                IsConnecting = false;
            }
        }

        /// <summary>自动连接上一次连接的设备（启动扫描发现目标时由 MainViewModel 调用）。
        /// 局域网活跃或正在连接时不自动抢连。</summary>
        public async Task<bool> AutoConnectLastDeviceAsync(BleDeviceInfo device)
        {
            if (IsConnected || IsConnecting || IsReconnecting) return false;
            if (IsLanConnected) return false;
            return await ConnectCoreAsync(device);
        }

        /// <summary>连接失败/断开时的临时状态文本，优先于派生态。</summary>
        private string? StatusTextFallback;

        /// <summary>强制断开（保底）：停止自动重连、断开 BLE、复位状态；保留设备列表。</summary>
        [RelayCommand]
        private async Task ForceDisconnect()
        {
            // 先取消自动重连并等待其彻底退出，避免残留的 ConnectAsync/Disconnect
            // 与随后用户发起的连接操作跨 await 交错（导致偶发"连接失败"）。
            var reconnectTask = _reconnectTask;
            _reconnectCts?.Cancel();
            _reconnectCts = null;
            IsReconnecting = false;
            StatusTextFallback = null;
            // 先置 IsConnected=false 再 Disconnect()，避免断连事件被当作"意外断开"
            IsConnected = false;
            _service.Disconnect();
            if (reconnectTask is not null)
            {
                try { await reconnectTask; } catch { /* 重连任务取消后的异常忽略 */ }
            }
            OnPropertyChanged(nameof(StatusText));
            OnPropertyChanged(nameof(CanToggleConnect));
        }

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
                // 已标记断开（无活跃数据源）时忽略迟到的 GATT 数据
                if (!IsConnected && !IsLanConnected) return;
                StatusTextFallback = null;
                HeartRate = bpm;
                OnPropertyChanged(nameof(StatusText));
            });
        }

        /// <summary>设备信息补全（尤其名称解析成功）时同步当前已连接设备：
        /// 自动连接常命中首包（无名称），补全后若不更新，卡片会一直显示"未知设备"。</summary>
        private void OnServiceDeviceUpdated(object? sender, BleDeviceInfo updated)
        {
            _uiDispatcher.TryEnqueue(() =>
            {
                if (_connectedDevice?.Address != updated.Address) return;
                if (string.IsNullOrEmpty(updated.Name)) return;
                _connectedDevice = updated;
                OnPropertyChanged(nameof(ConnectedDeviceName));
                OnPropertyChanged(nameof(ConnectedAddressText));
                OnPropertyChanged(nameof(HasHeartRateServiceText));
            });
        }

        private void OnConnectionChanged(object? sender, BluetoothConnectionStatus status)
        {
            if (status != BluetoothConnectionStatus.Disconnected) return;
            _uiDispatcher.TryEnqueue(() =>
            {
                if (!IsConnected) return;
                // 记录断开前的设备信息（OnIsConnectedChanged 会清空 _connectedDevice）
                var device = _connectedDevice;
                StatusTextFallback = L.HeartRate_Disconnected;
                IsConnected = false;
                // 设备断电等被动断开时清理 GATT 句柄与事件订阅，避免资源残留
                _service.Disconnect();
                OnPropertyChanged(nameof(StatusText));
                // 仅"意外断开"触发自动重连（手动/强制断开不会走到这里）
                if (device is not null)
                    TryStartAutoReconnect(device);
            });
        }

        // ── 自动重连（仅意外断开后启动）─────────────────────────────────────

        /// <summary>启动自动重连：每 3 秒重试一次，30 秒超时；局域网活跃或开关关闭时不重连。</summary>
        private void TryStartAutoReconnect(BleDeviceInfo device)
        {
            if (!AutoReconnectEnabled || IsLanConnected) return;
            if (_reconnectCts is not null) return;
            var cts = new CancellationTokenSource();
            _reconnectCts = cts;
            _reconnectTask = ReconnectLoopAsync(device, cts.Token);
        }

        private async Task ReconnectLoopAsync(BleDeviceInfo device, CancellationToken ct)
        {
            const int totalTimeoutSeconds = 30;
            const int attemptIntervalSeconds = 3;
            var deadline = DateTimeOffset.UtcNow.AddSeconds(totalTimeoutSeconds);

            // 重连开始后清除"设备已断开"等临时文案，让"自动重连中"提示真正生效
            // （StatusTextFallback 在 StatusText 中优先级最高，不清会被它盖住）
            StatusTextFallback = null;
            OnPropertyChanged(nameof(StatusText));

            while (DateTimeOffset.UtcNow < deadline)
            {
                // 局域网数据源活跃或用户关闭开关时中止重连
                if (IsLanConnected || !AutoReconnectEnabled) break;

                IsReconnecting = true;
                try
                {
                    // 单次连接尝试限时 5 秒，避免一次卡死耗尽整个 30 秒窗口
                    using var attempt = CancellationTokenSource.CreateLinkedTokenSource(ct);
                    attempt.CancelAfter(TimeSpan.FromSeconds(5));
                    var ok = await _service.ConnectAsync(device.Address, attempt.Token);
                    if (ok)
                    {
                        _connectedDevice = device;
                        ConnectionMode = ConnectionMode.Bluetooth;
                        StatusTextFallback = null;
                        _reconnectCts = null;
                        _reconnectTask = null;
                        IsConnected = true;
                        IsReconnecting = false;
                        OnPropertyChanged(nameof(ConnectedDeviceName));
                        OnPropertyChanged(nameof(ConnectedAddressText));
                        OnPropertyChanged(nameof(HasHeartRateServiceText));
                        return;
                    }
                }
                catch (OperationCanceledException)
                {
                    // 强制断开取消 → 直接结束；单次尝试超时 → 进入间隔等待后重试
                    if (ct.IsCancellationRequested) break;
                }
                catch
                {
                    // 连接异常按失败处理，等待后重试
                }

                try
                {
                    await Task.Delay(TimeSpan.FromSeconds(attemptIntervalSeconds), ct);
                }
                catch (OperationCanceledException)
                {
                    break;
                }
            }

            IsReconnecting = false;
            _reconnectCts = null;
            _reconnectTask = null;
            // 被强制断开取消时不提示"重连失败"，避免覆盖强制断开的正常状态
            if (!ct.IsCancellationRequested)
            {
                // 30 秒窗口结束仍未连上：提示用户
                StatusTextFallback = L.HeartRate_ReconnectFailed;
                OnPropertyChanged(nameof(StatusText));
                OnPropertyChanged(nameof(CanToggleConnect));
            }
        }

        /// <summary>手机推送 connected=false 时仅同步状态文案，不污染数据源。</summary>
        public void NotifyLanDisconnectedStatus(string status)
        {
            _uiDispatcher.TryEnqueue(() =>
            {
                StatusTextFallback = status;
                OnPropertyChanged(nameof(StatusText));
            });
        }

        /// <summary>窗口关闭时退订服务事件，避免残留订阅。</summary>
        public void Unsubscribe()
        {
            _service.HeartRateReceived -= OnHeartRateReceived;
            _service.ConnectionChanged -= OnConnectionChanged;
            _service.DeviceUpdated -= OnServiceDeviceUpdated;
        }
    }
}
