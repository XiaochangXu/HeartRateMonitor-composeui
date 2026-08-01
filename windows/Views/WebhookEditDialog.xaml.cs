using HeartRate.Models;

namespace HeartRate.Views
{
    /// <summary>
    /// Webhook 编辑/新建对话框。字段双向绑定到 <see cref="Editing"/>（克隆副本），
    /// 触发条件由复选框承载（保存时写回）。主按钮返回 <see cref="Result"/>。
    /// </summary>
    public sealed partial class WebhookEditDialog : ContentDialog
    {
        private readonly Func<Webhook, Task<string>>? _testFunc;
        private bool _testing;

        /// <summary>正在编辑的 Webhook（保存按钮后即作为结果）。</summary>
        public Webhook Editing { get; }

        /// <summary>主按钮触发后填入的结果。</summary>
        public Webhook? Result { get; private set; }

        public WebhookEditDialog(Webhook? edit, Func<Webhook, Task<string>>? testFunc = null)
        {
            InitializeComponent();
            Editing = edit?.Clone() ?? new Webhook { Name = string.Empty };
            _testFunc = testFunc;

            TriggerHeartRate.IsChecked = Editing.Triggers.Contains(WebhookTrigger.HeartRateUpdated);
            TriggerConnected.IsChecked = Editing.Triggers.Contains(WebhookTrigger.Connected);
            TriggerDisconnected.IsChecked = Editing.Triggers.Contains(WebhookTrigger.Disconnected);
        }

        private void OnPrimaryClick(ContentDialog sender, ContentDialogButtonClickEventArgs args)
        {
            Editing.Triggers.Clear();
            if (TriggerHeartRate.IsChecked == true) Editing.Triggers.Add(WebhookTrigger.HeartRateUpdated);
            if (TriggerConnected.IsChecked == true) Editing.Triggers.Add(WebhookTrigger.Connected);
            if (TriggerDisconnected.IsChecked == true) Editing.Triggers.Add(WebhookTrigger.Disconnected);
            if (Editing.Triggers.Count == 0) Editing.Triggers.Add(WebhookTrigger.HeartRateUpdated);
            Result = Editing;
        }

        private async void OnTestClick(object sender, RoutedEventArgs e)
        {
            if (_testFunc is null || _testing) return;
            _testing = true;
            TestResultText.Text = "发送中…";
            try
            {
                // 应用当前勾选的触发条件后再测试
                var probe = Editing.Clone();
                probe.Triggers.Clear();
                if (TriggerHeartRate.IsChecked == true) probe.Triggers.Add(WebhookTrigger.HeartRateUpdated);
                if (TriggerConnected.IsChecked == true) probe.Triggers.Add(WebhookTrigger.Connected);
                if (TriggerDisconnected.IsChecked == true) probe.Triggers.Add(WebhookTrigger.Disconnected);
                TestResultText.Text = await _testFunc(probe);
            }
            catch (Exception ex)
            {
                TestResultText.Text = $"发送失败：{ex.Message}";
            }
            finally
            {
                _testing = false;
            }
        }
    }
}
