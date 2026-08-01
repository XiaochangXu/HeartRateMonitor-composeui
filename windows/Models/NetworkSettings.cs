using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using HeartRate.Services;

namespace HeartRate.Models;

/// <summary>
/// 网络传输设置：HTTP / WebSocket 服务开关与端口，以及 Webhook 列表。
/// 由 <see cref="FloatWindowSettings"/> 持有并随 settings.json 一起持久化。
/// 开关/端口变化即触发防抖保存；端口变更时由 NetworkServerService 感知并重启监听。
/// </summary>
public partial class NetworkSettings : ObservableObject
{
    [ObservableProperty]
    private bool _httpServerEnabled = false;

    [ObservableProperty]
    private int _httpServerPort = 8000;

    [ObservableProperty]
    private bool _webSocketServerEnabled = false;

    [ObservableProperty]
    private int _webSocketServerPort = 8001;

    /// <summary>Webhook 列表。增删改由 ViewModel 显式调用 SettingsService.Save() 持久化。</summary>
    public ObservableCollection<Webhook> Webhooks { get; set; } = new();

    partial void OnHttpServerEnabledChanged(bool value) => SettingsService.Save();
    partial void OnWebSocketServerEnabledChanged(bool value) => SettingsService.Save();

    partial void OnHttpServerPortChanged(int value)
    {
        if (value < 1 || value > 65535)
            HttpServerPort = Math.Clamp(value, 1, 65535);
        SettingsService.Save();
    }

    partial void OnWebSocketServerPortChanged(int value)
    {
        if (value < 1 || value > 65535)
            WebSocketServerPort = Math.Clamp(value, 1, 65535);
        SettingsService.Save();
    }
}
