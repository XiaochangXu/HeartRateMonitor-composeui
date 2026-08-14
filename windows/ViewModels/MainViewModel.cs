using System.ComponentModel;
using HeartRate.Models;
using HeartRate.Services;
using Microsoft.UI.Dispatching;

namespace HeartRate.ViewModels
{
    public partial class MainViewModel : BaseViewModel
    {
        private readonly NetworkServerService _networkServer;
        private readonly WebhookService _webhookService;
        private readonly LanTransferService _lanService;
        private readonly HeartRateService _heartRateService;

        public DeviceListViewModel DeviceList { get; }
        public HeartRateViewModel HeartRate { get; }
        public FloatWindowSettingsViewModel FloatSettings { get; }
        public NetworkSettingsViewModel NetworkSettings { get; }
        public AppearanceSettingsViewModel Appearance { get; }
        public LanTransferViewModel LanTransfer { get; }
        public AppSettingsViewModel AppSettings { get; }

        public MainViewModel(HeartRateService service)
        {
            // 构造于 UI 线程（MainWindow ctor 在 InitializeComponent 之前），
            // 捕获 dispatcher 供后台事件封送。
            var uiDispatcher = DispatcherQueue.GetForCurrentThread();

            Title = "Heart Rate";

            _heartRateService = service;
            DeviceList = new DeviceListViewModel(service, uiDispatcher);
            HeartRate = new HeartRateViewModel(service, uiDispatcher);
            FloatSettings = new FloatWindowSettingsViewModel();

            // 网络传输：HTTP/WebSocket 服务 + Webhook，按设置自动启停
            var network = SettingsService.Current.Network;
            _networkServer = new NetworkServerService(service, network);
            _webhookService = new WebhookService(service, () => network.Webhooks.ToList());
            NetworkSettings = new NetworkSettingsViewModel(network, _networkServer, _webhookService, uiDispatcher);
            _ = _networkServer.ApplyAsync();

            // 局域网传输：mDNS 广播 + 配对 HTTP 服务 + WS 客户端，按设置自动启停
            var lan = SettingsService.Current.LanTransfer;
            _lanService = new LanTransferService(lan, uiDispatcher);
            LanTransfer = new LanTransferViewModel(lan, _lanService, uiDispatcher);
            // 手机推送的心率转发到 HeartRateService，走和 BLE 同一条链路刷新首页和悬浮窗
            _lanService.HeartRateUpdated += OnLanHeartRateUpdated;
            _lanService.PhoneDisconnected += OnLanPhoneDisconnected;
            _ = _lanService.ApplyAsync();

            Appearance = new AppearanceSettingsViewModel();
            AppSettings = new AppSettingsViewModel();

            HeartRate.GetSelectedDevice = () => DeviceList.SelectedDevice;
            // 连接时名称兜底：按地址查历史缓存名，避免自动连接命中首包缺名时卡片显示"未知设备"
            HeartRate.GetCachedDeviceName = addr => DeviceList.GetCachedDeviceName(addr);
            // 左侧"强制断开"按钮 → 右侧心率面板的强制断开逻辑
            DeviceList.ForceDisconnectRequested = () => HeartRate.ForceDisconnectCommand.Execute(null);
            DeviceList.PropertyChanged += OnDeviceListPropertyChanged;
            HeartRate.PropertyChanged += OnHeartRatePropertyChanged;
            // 自动连接上一次连接的设备：启动扫描发现目标设备时自动连接
            DeviceList.AutoConnectTargetFound += OnAutoConnectTargetFound;
            if (SettingsService.Current.AutoConnectLastDevice
                && SettingsService.Current.LastConnectedAddress is ulong lastAddr)
            {
                DeviceList.SetAutoConnectTarget(lastAddr);
            }
        }

        /// <summary>窗口关闭时停止网络服务、解除事件订阅。</summary>
        public void Shutdown()
        {
            // 各 ViewModel 对 Service 长生命周期对象的事件订阅统一退订
            DeviceList.Unsubscribe();
            HeartRate.Unsubscribe();
            DeviceList.PropertyChanged -= OnDeviceListPropertyChanged;
            HeartRate.PropertyChanged -= OnHeartRatePropertyChanged;
            DeviceList.AutoConnectTargetFound -= OnAutoConnectTargetFound;

            _webhookService.Dispose();
            _networkServer.Dispose();
            _lanService.HeartRateUpdated -= OnLanHeartRateUpdated;
            _lanService.PhoneDisconnected -= OnLanPhoneDisconnected;
            _lanService.Dispose();
        }

        /// <summary>局域网收到手机心率推送 → 更新首页数据源信息 + 转发 bpm 到刷新链路。</summary>
        private void OnLanHeartRateUpdated(LanPhone phone, int bpm, bool connected, string status)
        {
            // 手机端 BLE 断开时（connected=false）只同步状态文案，不更新数据源：
            // 若无条件 UpdateLanSource 会把电脑端数据源污染为局域网并锁死蓝牙连接。
            if (!connected)
            {
                HeartRate.NotifyLanDisconnectedStatus(status);
                return;
            }
            HeartRate.UpdateLanSource(phone.DeviceName, phone.WsHost);
            _heartRateService.RaiseHeartRateReceived(bpm);
        }

        /// <summary>局域网手机断开 → 清除首页数据源。</summary>
        private void OnLanPhoneDisconnected(string deviceId)
            => HeartRate.ClearLanSource();

        private void OnDeviceListPropertyChanged(object? sender, PropertyChangedEventArgs e)
        {
            if (e.PropertyName == nameof(DeviceListViewModel.SelectedDevice))
                HeartRate.NotifySelectedDeviceChanged();
        }

        /// <summary>用户点击"连接"即停止扫描；自动重连成功时兜底再停一次。</summary>
        private void OnHeartRatePropertyChanged(object? sender, PropertyChangedEventArgs e)
        {
            if ((e.PropertyName == nameof(HeartRateViewModel.IsConnecting) && HeartRate.IsConnecting)
                || (e.PropertyName == nameof(HeartRateViewModel.IsConnected) && HeartRate.IsConnected))
            {
                DeviceList.StopScan();
            }
        }

        /// <summary>启动扫描发现上一次连接的设备 → 自动连接（IsConnecting 会触发扫描停止）。</summary>
        private void OnAutoConnectTargetFound(object? sender, BleDeviceInfo device)
            => _ = HeartRate.AutoConnectLastDeviceAsync(device);
    }
}
