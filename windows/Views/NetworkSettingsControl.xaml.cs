using HeartRate.Helpers;
using HeartRate.Models;
using HeartRate.Services;

namespace HeartRate.Views
{
    public sealed partial class NetworkSettingsControl
    {
        public ViewModels.NetworkSettingsViewModel ViewModel { get; set; } = null!;

        public NetworkSettingsControl()
        {
            InitializeComponent();
        }

        // ── 端口冲突检测：开启时若端口与其他已启用服务相同，回退开关并弹提示 ──
        private async void HttpToggle_Toggled(object sender, RoutedEventArgs e)
        {
            try
            {
                if (sender is not ToggleSwitch ts || !ts.IsOn) return;
                if (this.XamlRoot is null) return;
                if (SettingsService.PortConflict("http"))
                {
                    ts.IsOn = false; // 回退：TwoWay 写回 false
                    await PortConflictHelper.ShowAsync(this.XamlRoot, ViewModel.Network.HttpServerPort);
                }
            }
            catch { /* async void 处理器：弹窗异常不得崩溃进程 */ }
        }

        private async void WsToggle_Toggled(object sender, RoutedEventArgs e)
        {
            try
            {
                if (sender is not ToggleSwitch ts || !ts.IsOn) return;
                if (this.XamlRoot is null) return;
                if (SettingsService.PortConflict("ws"))
                {
                    ts.IsOn = false;
                    await PortConflictHelper.ShowAsync(this.XamlRoot, ViewModel.Network.WebSocketServerPort);
                }
            }
            catch { /* async void 处理器：弹窗异常不得崩溃进程 */ }
        }

        private async void AddWebhook_Click(object sender, RoutedEventArgs e)
        {
            var dlg = new WebhookEditDialog(edit: null, testFunc: wh => ViewModel.TestAsync(wh))
            {
                XamlRoot = this.XamlRoot,
            };
            if (await dlg.ShowAsync() == ContentDialogResult.Primary && dlg.Result is not null)
                ViewModel.Add(dlg.Result);
        }

        private async void EditWebhook_Click(object sender, RoutedEventArgs e)
        {
            if (sender is FrameworkElement fe && fe.DataContext is Webhook wh)
            {
                var dlg = new WebhookEditDialog(wh, testFunc: w => ViewModel.TestAsync(w))
                {
                    XamlRoot = this.XamlRoot,
                };
                if (await dlg.ShowAsync() == ContentDialogResult.Primary && dlg.Result is not null)
                    ViewModel.Update(wh, dlg.Result);
            }
        }

        private async void DeleteWebhook_Click(object sender, RoutedEventArgs e)
        {
            if (sender is not FrameworkElement fe || fe.DataContext is not Webhook wh) return;

            var confirm = new ContentDialog
            {
                Title = L.Webhook_DeleteConfirmTitle,
                Content = Loc.Format("Webhook_DeleteConfirmBody", wh.Name),
                PrimaryButtonText = L.Webhook_Delete,
                CloseButtonText = L.Webhook_Cancel,
                DefaultButton = ContentDialogButton.Close,
                XamlRoot = this.XamlRoot,
            };
            if (await confirm.ShowAsync() == ContentDialogResult.Primary)
                ViewModel.Delete(wh);
        }

        private void OnWebhookEnabledToggled(object sender, RoutedEventArgs e)
            => ViewModel.NotifyWebhookChanged();
    }
}
