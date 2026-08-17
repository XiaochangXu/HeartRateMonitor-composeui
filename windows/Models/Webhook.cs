using CommunityToolkit.Mvvm.ComponentModel;

namespace HeartRate.Models;

/// <summary>
/// Webhook 配置。对应示例项目 data/Webhook.kt。
/// Body/Headers 为 JSON 字符串，运行时把 {bpm} {speed} 占位符替换为实参后 POST。
/// </summary>
public partial class Webhook : ObservableObject
{
    // JSON 序列化用，UI 修改后由 ViewModel 调 SettingsService.Save() 持久化
    public string Name { get; set; } = string.Empty;

    public string Url { get; set; } = string.Empty;

    [ObservableProperty]
    private bool _enabled = true;

    public string Body { get; set; } = "{\n  \"bpm\": \"{bpm}\"\n}";

    public string Headers { get; set; } = "{\n  \"Content-Type\": \"application/json\"\n}";

    /// <summary>触发条件集合（多选），默认仅心率更新。</summary>
    public List<WebhookTrigger> Triggers { get; set; } = new() { WebhookTrigger.HeartRateUpdated };

    /// <summary>浅拷贝一份用于编辑对话框（取消编辑不影响原对象）。</summary>
    public Webhook Clone() => new()
    {
        Name = Name,
        Url = Url,
        Enabled = Enabled,
        Body = Body,
        Headers = Headers,
        Triggers = new List<WebhookTrigger>(Triggers),
    };
}
