using System.Diagnostics;
using System.Reflection;
using HeartRate.Helpers;
using HeartRate.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace HeartRate.Views
{
    public sealed partial class VersionInfoControl
    {
        public VersionInfoControl()
        {
            this.InitializeComponent();
            Loaded += OnLoaded;
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            // 读取程序集版本（AssemblyVersion），取主.次.修订三段显示。
            var v = Assembly.GetExecutingAssembly().GetName().Version;
            VersionText.Text = v is null ? "1.0.0" : v.ToString(3);
        }

        /// <summary>点击「检查更新」：从 Gitee 拉取最新 Release 并弹窗展示结果。</summary>
        private async void OnCheckUpdateClick(object sender, RoutedEventArgs e)
        {
            var root = XamlRoot;
            if (root is null)
                return;

            CheckUpdateButton.IsEnabled = false;
            var original = CheckUpdateButtonText.Text;
            CheckUpdateButtonText.Text = L.VersionInfo_CheckingUpdate;
            try
            {
                var result = await UpdateCheckerService.CheckAsync(VersionText.Text);
                switch (result)
                {
                    case UpdateCheckerService.Result.UpdateAvailable info:
                        await ShowUpdateAvailableAsync(root, VersionText.Text, info);
                        break;
                    case UpdateCheckerService.Result.UpToDate up:
                        await ShowMessageAsync(root, L.VersionInfo_UpdateCheckTitle,
                            Loc.Format("VersionInfo_UpToDate", up.CurrentVersion));
                        break;
                    case UpdateCheckerService.Result.Error err:
                        await ShowMessageAsync(root, L.VersionInfo_UpdateCheckTitle, err.Message);
                        break;
                }
            }
            finally
            {
                CheckUpdateButtonText.Text = original;
                CheckUpdateButton.IsEnabled = true;
            }
        }

        /// <summary>简单提示弹窗（已是最新 / 检查失败）。</summary>
        private static async Task ShowMessageAsync(XamlRoot root, string title, string message)
        {
            var dlg = new ContentDialog
            {
                Title = title,
                Content = message,
                CloseButtonText = L.Dialog_Ok,
                DefaultButton = ContentDialogButton.Close,
                XamlRoot = root,
            };
            await dlg.ShowAsync();
        }

        /// <summary>发现新版本弹窗：展示当前版本、更新内容，跳转 Gitee Release 页。</summary>
        private static async Task ShowUpdateAvailableAsync(XamlRoot root, string currentVersion,
            UpdateCheckerService.Result.UpdateAvailable info)
        {
            var secondary = ThemeBrush("TextFillColorSecondaryBrush");
            var notes = new TextBlock
            {
                Text = string.IsNullOrEmpty(info.ReleaseNotes) ? L.VersionInfo_NoReleaseNotes : info.ReleaseNotes,
                Foreground = secondary,
                TextWrapping = TextWrapping.Wrap,
            };

            var content = new StackPanel { Spacing = 10 };
            content.Children.Add(new TextBlock
            {
                Text = Loc.Format("VersionInfo_CurrentVersion", currentVersion),
                Foreground = secondary,
                TextWrapping = TextWrapping.Wrap,
            });
            content.Children.Add(new TextBlock
            {
                Text = L.VersionInfo_ReleaseNotesTitle,
                FontWeight = Microsoft.UI.Text.FontWeights.SemiBold,
                TextWrapping = TextWrapping.Wrap,
            });
            content.Children.Add(notes);

            var dlg = new ContentDialog
            {
                Title = Loc.Format("VersionInfo_NewVersionFound", info.NewVersion),
                Content = content,
                PrimaryButtonText = L.VersionInfo_GoUpdate,
                CloseButtonText = L.VersionInfo_Cancel,
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = root,
            };
            if (await dlg.ShowAsync() == ContentDialogResult.Primary)
            {
                Process.Start(new ProcessStartInfo(info.HtmlUrl) { UseShellExecute = true });
            }
        }

        /// <summary>从应用资源中取主题画刷（不存在时返回 null，回退默认前景色）。</summary>
        private static Brush? ThemeBrush(string key) =>
            Application.Current.Resources.TryGetValue(key, out var value) ? value as Brush : null;
    }
}
