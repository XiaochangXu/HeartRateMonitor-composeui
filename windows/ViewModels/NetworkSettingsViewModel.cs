using System.Collections.ObjectModel;
using System.ComponentModel;
using HeartRate.Helpers;
using HeartRate.Models;
using HeartRate.Services;
using Microsoft.UI.Dispatching;

namespace HeartRate.ViewModels
{
    /// <summary>
    /// 网络传输设置页：HTTP/WebSocket 开关与端口、访问 URL 展示、Webhook 列表增删改。
    /// ToggleSwitch / TextBox 直接双向绑定 <see cref="Network"/>（NetworkSettings 自动防抖保存）；
    /// Webhook 列表的增删改由本 VM 提交后显式 Save。
    /// </summary>
    public partial class NetworkSettingsViewModel : BaseViewModel
    {
        private readonly NetworkServerService _server;
        private readonly WebhookService _webhooks;
        private readonly DispatcherQueue _uiDispatcher;
        private readonly string _lanIp;

        public NetworkSettings Network { get; }
        public ObservableCollection<Webhook> Webhooks => Network.Webhooks;

        public NetworkSettingsViewModel(
            NetworkSettings network,
            NetworkServerService server,
            WebhookService webhooks,
            DispatcherQueue uiDispatcher)
        {
            Title = "Network Transfer";
            Network = network;
            _server = server;
            _webhooks = webhooks;
            _uiDispatcher = uiDispatcher;
            _lanIp = NetworkIPHelper.GetLanIPv4Addresses().FirstOrDefault()?.ToString() ?? "localhost";

            Network.PropertyChanged += OnNetworkPropertyChanged;
            _server.StatusChanged += OnServerStatusChanged;
        }

        // ── 派生展示属性 ────────────────────────────────────────────────────
        public string HttpUrl => Network.HttpServerEnabled
            ? $"http://{_lanIp}:{Network.HttpServerPort}/heartrate"
            : string.Empty;

        public string WsUrl => Network.WebSocketServerEnabled
            ? $"ws://{_lanIp}:{Network.WebSocketServerPort}/ws"
            : string.Empty;

        /// <summary>HTTP 端口输入：字符串往返，解析失败时忽略，由 Network 钳制到合法范围。</summary>
        public string HttpPortText
        {
            get => Network.HttpServerPort.ToString();
            set { if (int.TryParse(value, out var p) && p != Network.HttpServerPort) Network.HttpServerPort = p; }
        }

        public string WsPortText
        {
            get => Network.WebSocketServerPort.ToString();
            set { if (int.TryParse(value, out var p) && p != Network.WebSocketServerPort) Network.WebSocketServerPort = p; }
        }

        public string HttpStatusText
        {
            get
            {
                if (!Network.HttpServerEnabled) return L.Network_Disabled;
                if (_server.IsHttpRunning) return Loc.Format("Network_RunningOn", Network.HttpServerPort);
                if (_server.HttpError is not null) return Loc.Format("Network_Error", _server.HttpError);
                return L.Network_Stopped;
            }
        }

        public string WsStatusText
        {
            get
            {
                if (!Network.WebSocketServerEnabled) return L.Network_Disabled;
                if (_server.IsWsRunning) return Loc.Format("Network_RunningOn", Network.WebSocketServerPort);
                if (_server.WsError is not null) return Loc.Format("Network_Error", _server.WsError);
                return L.Network_Stopped;
            }
        }

        // ── Webhook 列表增删改 ──────────────────────────────────────────────
        public void Add(Webhook webhook)
        {
            Webhooks.Add(webhook);
            SettingsService.Save();
        }

        public void Update(Webhook original, Webhook edited)
        {
            original.Name = edited.Name;
            original.Url = edited.Url;
            original.Enabled = edited.Enabled;
            original.Body = edited.Body;
            original.Headers = edited.Headers;
            original.Triggers = new List<WebhookTrigger>(edited.Triggers);
            SettingsService.Save();
        }

        public void Delete(Webhook webhook)
        {
            Webhooks.Remove(webhook);
            SettingsService.Save();
        }

        public Task<string> TestAsync(Webhook webhook) => _webhooks.TestAsync(webhook);

        /// <summary>列表中 Webhook 单字段变更（如启停开关）后调用以持久化。</summary>
        public void NotifyWebhookChanged() => SettingsService.Save();

        // ── 事件 ────────────────────────────────────────────────────────────
        private void OnNetworkPropertyChanged(object? sender, PropertyChangedEventArgs e)
        {
            if (e.PropertyName is nameof(NetworkSettings.HttpServerEnabled)
                or nameof(NetworkSettings.HttpServerPort))
            {
                OnPropertyChanged(nameof(HttpUrl));
                OnPropertyChanged(nameof(HttpStatusText));
                OnPropertyChanged(nameof(HttpPortText));
            }
            if (e.PropertyName is nameof(NetworkSettings.WebSocketServerEnabled)
                or nameof(NetworkSettings.WebSocketServerPort))
            {
                OnPropertyChanged(nameof(WsUrl));
                OnPropertyChanged(nameof(WsStatusText));
                OnPropertyChanged(nameof(WsPortText));
            }
        }

        private void OnServerStatusChanged()
        {
            _uiDispatcher.TryEnqueue(() =>
            {
                OnPropertyChanged(nameof(HttpStatusText));
                OnPropertyChanged(nameof(WsStatusText));
            });
        }
    }
}
