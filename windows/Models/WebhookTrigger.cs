namespace HeartRate.Models;

/// <summary>
/// Webhook 触发条件（多选，存于 <see cref="Webhook.Triggers"/> 列表）。
/// 序列化为 snake_case 字符串持久化到设置文件。对应示例项目 WebhookTrigger 枚举。
/// </summary>
public enum WebhookTrigger
{
    HeartRateUpdated = 1,
    Connected = 2,
    Disconnected = 4,
}
