using HeartRate.Helpers;
using HeartRate.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace HeartRate.Views
{
    public sealed partial class LanTransferControl
    {
        public ViewModels.LanTransferViewModel ViewModel { get; set; } = null!;

        public LanTransferControl()
        {
            InitializeComponent();
            // 配对弹窗 handler 由 MainWindow.Loaded 统一注入：
            // MainWindow 的 Content.XamlRoot 一定先于本控件的 Loaded 就绪，
            // 且 Visibility=Collapsed 时本控件的 Loaded 可能不触发。
        }

        // ── 端口冲突检测：开启时若配对端口与其他已启用服务相同，回退开关并弹提示 ──
        private async void LanToggle_Toggled(object sender, RoutedEventArgs e)
        {
            if (sender is not ToggleSwitch ts || !ts.IsOn) return;
            if (SettingsService.PortConflict("lan"))
            {
                ts.IsOn = false; // 回退：TwoWay 写回 false
                await PortConflictHelper.ShowAsync(this.XamlRoot, ViewModel.Settings.PairPort);
            }
        }
    }
}
