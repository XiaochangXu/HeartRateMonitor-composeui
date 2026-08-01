using System.Net.Http;
using System.Text;
using System.Text.Json;
using HeartRate.Models;
using Windows.Devices.Bluetooth;

namespace HeartRate.Services;

/// <summary>
/// Webhook 分发：订阅 <see cref="HeartRateService"/> 连接/断开/心率更新事件，
/// 按各 Webhook 的触发条件过滤后异步 POST。对应示例项目 WebhookRepository。
/// 占位符 {bpm} {speed} 运行时替换；PC 无速度源，{speed} 替换为 "0"。
/// </summary>
public sealed class WebhookService : IDisposable
{
    private static readonly HttpClient _http = new() { Timeout = TimeSpan.FromSeconds(10) };

    private readonly HeartRateService _heartRate;
    private readonly Func<List<Webhook>> _getWebhooks;
    private int _lastBpm; // 断开触发时使用最后已知心率

    public WebhookService(HeartRateService heartRate, Func<List<Webhook>> getWebhooks)
    {
        _heartRate = heartRate;
        _getWebhooks = getWebhooks;
        _heartRate.HeartRateReceived += OnHeartRateReceived;
        _heartRate.ConnectionChanged += OnConnectionChanged;
    }

    private void OnHeartRateReceived(object? sender, int bpm)
    {
        _lastBpm = bpm;
        Trigger(WebhookTrigger.HeartRateUpdated, bpm);
    }

    private void OnConnectionChanged(object? sender, BluetoothConnectionStatus status)
    {
        // 示例：连接时传默认心率 0；断开时传最后已知心率
        if (status == BluetoothConnectionStatus.Connected)
            Trigger(WebhookTrigger.Connected, 0);
        else
            Trigger(WebhookTrigger.Disconnected, _lastBpm);
    }

    private void Trigger(WebhookTrigger trigger, int bpm)
    {
        List<Webhook> snapshot;
        try { snapshot = _getWebhooks(); }
        catch { return; }

        foreach (var wh in snapshot)
        {
            if (!wh.Enabled || !wh.Triggers.Contains(trigger)) continue;
            _ = SendAsync(wh, trigger, bpm);
        }
    }

    /// <summary>测试单个 Webhook（使用示例值 bpm=88）。</summary>
    public async Task<string> TestAsync(Webhook webhook)
    {
        return await SendAsync(webhook, WebhookTrigger.HeartRateUpdated, 88, isTest: true);
    }

    private static async Task<string> SendAsync(Webhook webhook, WebhookTrigger trigger, int bpm, bool isTest = false)
    {
        var bpmStr = bpm.ToString();
        var speedStr = "0"; // PC 无速度源

        var url = webhook.Url.Replace("{bpm}", bpmStr).Replace("{speed}", speedStr);
        var body = webhook.Body.Replace("{bpm}", bpmStr).Replace("{speed}", speedStr);
        var headersStr = webhook.Headers.Replace("{bpm}", bpmStr).Replace("{speed}", speedStr);

        try
        {
            using var req = new HttpRequestMessage(HttpMethod.Post, url)
            {
                Content = new StringContent(body, Encoding.UTF8),
            };

            // 解析 Headers JSON 字符串
            try
            {
                using var headersDoc = JsonDocument.Parse(string.IsNullOrWhiteSpace(headersStr) ? "{}" : headersStr);
                foreach (var prop in headersDoc.RootElement.EnumerateObject())
                    req.Headers.TryAddWithoutValidation(prop.Name, prop.Value.GetString());
            }
            catch (Exception ex)
            {
                return $"Headers 解析失败：{ex.Message}";
            }

            if (req.Content.Headers.ContentType is null)
                req.Content.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/json");

            using var resp = await _http.SendAsync(req);
            var respBody = await resp.Content.ReadAsStringAsync();
            var title = isTest ? "--- 测试响应 ---" : "--- 已发送 ---";
            return $"{title}\n{webhook.Name} [{trigger}]\n状态：{(int)resp.StatusCode} {resp.ReasonPhrase}\n响应：{respBody}";
        }
        catch (Exception ex)
        {
            return $"发送失败：{ex.Message}";
        }
    }

    public void Dispose()
    {
        _heartRate.HeartRateReceived -= OnHeartRateReceived;
        _heartRate.ConnectionChanged -= OnConnectionChanged;
    }
}
