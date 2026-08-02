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

            HeartRate.GetSelectedDevice = () => DeviceList.SelectedDevice;
            DeviceList.PropertyChanged += OnDeviceListPropertyChanged;
        }

        /// <summary>窗口关闭时停止网络服务、解除事件订阅。</summary>
        public void Shutdown()
        {
            // 各 ViewModel 对 Service 长生命周期对象的事件订阅统一退订
            DeviceList.Unsubscribe();
            HeartRate.Unsubscribe();
            DeviceList.PropertyChanged -= OnDeviceListPropertyChanged;

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
    }
}
