using CommunityToolkit.Mvvm.ComponentModel;
using HeartRate.Services;

namespace HeartRate.Models;

/// <summary>
/// 局域网传输设置：开关、电脑显示名、配对 HTTP 端口。
/// 由 <see cref="FloatWindowSettings"/> 持有并随 settings.json 持久化。
/// 开关/端口/电脑名变化即触发防抖保存；端口变更由 LanTransferService 感知并重启监听。
/// </summary>
public partial class LanTransferSettings : ObservableObject
{
    /// <summary>是否启用局域网传输（mDNS 广播 + 配对 HTTP 服务）。</summary>
    [ObservableProperty]
    private bool _enabled = false;

    /// <summary>电脑显示名，作为 mDNS 服务名广播给手机端。空则回退到 MachineName。</summary>
    [ObservableProperty]
    private string _computerName = Environment.MachineName;

    /// <summary>配对 HTTP 服务端口（手机 POST /pair-request 的目标端口）。</summary>
    [ObservableProperty]
    private int _pairPort = 7755;

    partial void OnEnabledChanged(bool value) => SettingsService.Save();

    partial void OnComputerNameChanged(string value) => SettingsService.Save();

    partial void OnPairPortChanged(int value)
    {
        if (value < 1 || value > 65535)
            PairPort = Math.Clamp(value, 1, 65535);
        SettingsService.Save();
    }
}

/// <summary>
/// 已配对的手机端设备：在 UI「已连接设备」列表中展示，心率字段为 ObservableProperty
/// 以便 WS 推送新值时 UI 自动刷新。
/// </summary>
public partial class LanPhone : ObservableObject
{
    /// <summary>手机端 ANDROID_ID，作为唯一标识。</summary>
    public string DeviceId { get; init; } = string.Empty;

    /// <summary>手机端构造的 device_name（app_name-机型）。</summary>
    public string DeviceName { get; init; } = string.Empty;

    /// <summary>手机端 WebSocket Server IP（来自配对请求 body 或 HTTP 来源 IP）。</summary>
    public string WsHost { get; init; } = string.Empty;

    /// <summary>手机端 WebSocket Server 端口（默认 8001）。</summary>
    public int WsPort { get; init; }

    /// <summary>配对会话 ID（电脑端生成并回传给手机）。</summary>
    public string SessionId { get; init; } = string.Empty;

    /// <summary>BLE 设备是否已连接（来自手机 WS 推送）。</summary>
    [ObservableProperty]
    private bool _bleConnected;

    /// <summary>当前心率 bpm（来自手机 WS 推送，未连接为 0）。</summary>
    [ObservableProperty]
    private int _heartRate;

    /// <summary>状态文案（来自手机 WS 推送）。</summary>
    [ObservableProperty]
    private string _statusText = string.Empty;

    /// <summary>最后一次收到 WS 推送的时间戳（毫秒）。</summary>
    [ObservableProperty]
    private long _lastUpdated;
}

/// <summary>
/// 手机端发来的配对请求体。JSON 字段命名遵循 <c>snake_case</c>，与手机端
/// <c>PairClient.PairRequest</c> 一致。需 <c>public</c> + 无参构造供 JSON 反序列化。
/// </summary>
public sealed class LanPairRequest
{
    [System.Text.Json.Serialization.JsonPropertyName("device_name")]
    public string DeviceName { get; set; } = string.Empty;

    [System.Text.Json.Serialization.JsonPropertyName("device_id")]
    public string DeviceId { get; set; } = string.Empty;

    [System.Text.Json.Serialization.JsonPropertyName("platform")]
    public string Platform { get; set; } = string.Empty;

    [System.Text.Json.Serialization.JsonPropertyName("ws_ip")]
    public string WsIp { get; set; } = string.Empty;

    [System.Text.Json.Serialization.JsonPropertyName("ws_port")]
    public int WsPort { get; set; }

    [System.Text.Json.Serialization.JsonPropertyName("ws_token")]
    public string WsToken { get; set; } = string.Empty;

    /// <summary>由 HTTP 配对服务端填充：手机 TCP 连接的来源 IP（用于 ws_ip 为空时退化）。</summary>
    [System.Text.Json.Serialization.JsonIgnore]
    public string RemoteIp { get; set; } = string.Empty;
}
