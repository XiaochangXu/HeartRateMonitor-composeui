using System.Collections.ObjectModel;
using System.Collections.Specialized;
using System.ComponentModel;
using HeartRate.Helpers;
using HeartRate.Models;
using HeartRate.Services;
using Microsoft.UI.Dispatching;

namespace HeartRate.ViewModels
{
    /// <summary>
    /// 局域网传输设置页：开关 / 电脑名 / 配对端口 / 本地 IP / 运行状态展示，
    /// 以及已配对手机列表（实时心率推送）。所有后台事件均封送到 UI 线程后再更新绑定属性。
    /// 配对弹窗回调由本 VM 通过 <see cref="SetApprovalHandler"/> 转发到 Service。
    /// </summary>
    public partial class LanTransferViewModel : BaseViewModel
    {
        private readonly LanTransferService _service;
        private readonly DispatcherQueue _uiDispatcher;
        private readonly string _lanIp;

        public LanTransferSettings Settings { get; }

        /// <summary>已配对的手机列表（心率字段实时刷新）。</summary>
        public ObservableCollection<LanPhone> Phones { get; } = new();

        public LanTransferViewModel(
            LanTransferSettings settings,
            LanTransferService service,
            DispatcherQueue uiDispatcher)
        {
            Title = "LAN Transfer";
            Settings = settings;
            _service = service;
            _uiDispatcher = uiDispatcher;
            _lanIp = NetworkIPHelper.GetLanIPv4Addresses().FirstOrDefault()?.ToString() ?? "localhost";

            Settings.PropertyChanged += OnSettingsPropertyChanged;
            _service.StatusChanged += OnServiceStatusChanged;
            _service.PhoneConnected += OnPhoneConnected;
            _service.PhoneDisconnected += OnPhoneDisconnected;
            _service.HeartRateUpdated += OnHeartRateUpdated;
            Phones.CollectionChanged += OnPhonesCollectionChanged;
        }

        /// <summary>Phones 为空时显示「暂无已配对设备」提示。</summary>
        public Visibility NoDevicesHintVisibility
            => Phones.Count == 0 ? Visibility.Visible : Visibility.Collapsed;

        // ── 派生展示属性 ────────────────────────────────────────────────────

        /// <summary>本机局域网 IPv4，用于 UI 提示。</summary>
        public string LocalIp => _lanIp;

        /// <summary>配对端口输入：字符串往返，解析失败时忽略，由 Settings 钳制到合法范围。</summary>
        public string PairPortText
        {
            get => Settings.PairPort.ToString();
            set { if (int.TryParse(value, out var p) && p != Settings.PairPort) Settings.PairPort = p; }
        }

        /// <summary>电脑名输入：双向字符串绑定（空时 Service 退化到 MachineName）。</summary>
        public string ComputerNameText
        {
            get => Settings.ComputerName;
            set { if (value != Settings.ComputerName) Settings.ComputerName = value; }
        }

        /// <summary>状态文案：未启用 / 广播中 / 已停止 / 错误。</summary>
        public string StatusText
        {
            get
            {
                if (!Settings.Enabled) return L.Lan_Disabled;
                if (_service.IsRunning) return Loc.Format("Lan_RunningOn", Settings.PairPort);
                if (_service.Error is not null) return Loc.Format("Network_Error", _service.Error);
                return L.Lan_Stopped;
            }
        }

        // ── 配对弹窗回调（View 在 Loaded 时注入）─────────────────────────────

        /// <summary>由 LanTransferControl 在 Loaded 时调用，注入需要 XamlRoot 的弹窗回调。</summary>
        public void SetApprovalHandler(Func<LanPairRequest, Task<bool>> handler)
            => _service.SetApprovalHandler(handler);

        // ── 事件封送到 UI 线程 ──────────────────────────────────────────────

        private void OnSettingsPropertyChanged(object? sender, PropertyChangedEventArgs e)
        {
            _uiDispatcher.TryEnqueue(() =>
            {
                if (e.PropertyName is nameof(LanTransferSettings.Enabled)
                    or nameof(LanTransferSettings.PairPort))
                {
                    OnPropertyChanged(nameof(StatusText));
                    OnPropertyChanged(nameof(PairPortText));
                }
                if (e.PropertyName == nameof(LanTransferSettings.ComputerName))
                    OnPropertyChanged(nameof(ComputerNameText));
            });
        }

        private void OnServiceStatusChanged()
        {
            _uiDispatcher.TryEnqueue(() => OnPropertyChanged(nameof(StatusText)));
        }

        private void OnPhoneConnected(LanPhone phone)
        {
            // 已通过 UI dispatcher 封送
            var existing = Phones.FirstOrDefault(p => p.DeviceId == phone.DeviceId);
            if (existing is not null) Phones.Remove(existing);
            Phones.Add(phone);
        }

        private void OnPhoneDisconnected(string deviceId)
        {
            // 已通过 UI dispatcher 封送
            var phone = Phones.FirstOrDefault(p => p.DeviceId == deviceId);
            if (phone is not null) Phones.Remove(phone);
        }

        private void OnHeartRateUpdated(LanPhone phone, int hr, bool connected, string status)
        {
            // 已通过 UI dispatcher 封送；LanPhone 的 HeartRate/BleConnected/StatusText
            // 均为 ObservableProperty，UI 自动刷新。
        }

        private void OnPhonesCollectionChanged(object? sender, NotifyCollectionChangedEventArgs e)
            => OnPropertyChanged(nameof(NoDevicesHintVisibility));
    }
}
