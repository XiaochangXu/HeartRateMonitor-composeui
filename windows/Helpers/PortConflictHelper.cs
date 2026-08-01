using System.Net;
using System.Net.Sockets;
using HeartRate.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace HeartRate.Helpers;

/// <summary>
/// 端口冲突相关工具：内置服务间的端口冲突提示弹窗 + 对外部进程占用端口的探测/换端口引导。
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

    /// <summary>端口是否已被本机其他进程绑定占用。</summary>
    public static bool IsPortInUse(int port)
    {
        try
        {
            var listener = new TcpListener(IPAddress.Any, port);
            listener.Start();
            listener.Stop();
            return false;
        }
        catch
        {
            return true;
        }
    }

    /// <summary>
    /// 从 startPort 起向后找一个可用端口（跳过内置已启用服务端口与外部占用端口）。
    /// 找不到返回 -1。
    /// </summary>
    public static int FindAvailablePort(int startPort)
    {
        for (int i = 0; i < 2000; i++)
        {
            var candidate = ((startPort - 1 + i) % 65535) + 1;
            if (SettingsService.PortUsedByOtherService(candidate)) continue;
            if (!IsPortInUse(candidate)) return candidate;
        }
        return -1;
    }

    /// <summary>端口被外部程序占用：询问是否自动换到可用端口。返回 true 表示用户选择切换。</summary>
    public static async Task<bool> ShowPortInUseAsync(XamlRoot root, int port, int newPort)
    {
        var dlg = new ContentDialog
        {
            Title = L.Lan_PortInUse_Title,
            Content = Loc.Format("Lan_PortInUse_Body", port, newPort),
            PrimaryButtonText = Loc.Format("Lan_PortInUse_Switch", newPort),
            CloseButtonText = L.Lan_PortInUse_Keep,
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = root,
        };
        return await dlg.ShowAsync() == ContentDialogResult.Primary;
    }

    /// <summary>端口被占用且找不到可用端口：提示手动修改。</summary>
    public static async Task ShowPortUnavailableAsync(XamlRoot root, int port)
    {
        var dlg = new ContentDialog
        {
            Title = L.Lan_PortUnavailable_Title,
            Content = Loc.Format("Lan_PortUnavailable_Body", port),
            PrimaryButtonText = L.Dialog_Ok,
            DefaultButton = ContentDialogButton.Primary,
            XamlRoot = root,
        };
        await dlg.ShowAsync();
    }
}
