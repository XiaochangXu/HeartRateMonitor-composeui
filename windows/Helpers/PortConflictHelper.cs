using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace HeartRate.Helpers;

/// <summary>
/// 端口冲突提示弹窗：由网络/局域网设置页的 ToggleSwitch.Toggled 在检测到
/// 端口冲突后调用。集中在此避免两个控件重复构造 ContentDialog。
/// </summary>
public static class PortConflictHelper
{
    /// <summary>显示端口冲突提示（标题 + 带端口号的正文 + 「知道了」按钮）。</summary>
    public static async Task ShowAsync(XamlRoot root, int port)
    {
        var dlg = new ContentDialog
        {
            Title = L.PortConflict_Title,
            Content = Loc.Format("PortConflict_Body", port),
            PrimaryButtonText = L.Dialog_Ok,
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = root,
        };
        await dlg.ShowAsync();
    }
}
