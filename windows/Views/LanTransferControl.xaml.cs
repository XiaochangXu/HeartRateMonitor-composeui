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

        // ── 端口冲突检测：开启时先查内置服务端口冲突，再查外部进程占用 ──
        private async void LanToggle_Toggled(object sender, RoutedEventArgs e)
        {
            try
            {
                if (sender is not ToggleSwitch ts || !ts.IsOn) return;
                // 控件脱离可视树时 XamlRoot 失效，弹窗会抛异常，直接跳过预检
                if (this.XamlRoot is null) return;

                // 1) 与内置其他已启用服务端口相同：回退开关并提示改端口
                if (SettingsService.PortConflict("lan"))
                {
                    ts.IsOn = false; // 回退：TwoWay 写回 false
                    await PortConflictHelper.ShowAsync(this.XamlRoot, ViewModel.Settings.PairPort);
                    return;
                }

                // 2) 端口被本机其他进程占用：引导自动换一个可用端口。
                //    注意：服务已在运行（如重启后开关随设置自动置开、服务已绑定端口）时，
                //    探测到的"占用"其实是本应用自己，跳过预检避免误报。
                var port = ViewModel.Settings.PairPort;
                if (!ViewModel.IsServiceRunning && PortConflictHelper.IsPortInUse(port))
                {
                    var newPort = PortConflictHelper.FindAvailablePort(port);
                    if (newPort <= 0)
                    {
                        ts.IsOn = false;
                        await PortConflictHelper.ShowPortUnavailableAsync(this.XamlRoot, port);
                        return;
                    }

                    if (await PortConflictHelper.ShowPortInUseAsync(this.XamlRoot, port, newPort))
                    {
                        // 用户确认换端口：改设置触发服务重启（开关保持开启）
                        ViewModel.Settings.PairPort = newPort;
                    }
                    else
                    {
                        ts.IsOn = false; // 用户保持原端口：回退开关
                    }
                }
            }
            catch { /* async void 处理器：弹窗异常不得崩溃进程 */ }
        }
    }
}
