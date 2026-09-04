using System.Runtime.InteropServices;
using HeartRate.Helpers;

namespace HeartRate.Services;

/// <summary>
/// 系统托盘图标（"隐藏到托盘"功能核心）。经典 Shell_NotifyIconW 实现：
/// 回调消息发到主窗口 HWND，由 MainWindow.SubclassProc 转发到 HandleMessage。
/// 左键单击/双击恢复窗口；右键弹出菜单（显示主窗口 / 退出）。
/// </summary>
public static class TrayIconService
{
    private const uint WM_APP_TRAY = 0x8000 + 1; // WM_APP + 1，自定义回调消息
    private const uint WM_LBUTTONUP = 0x0202;
    private const uint WM_LBUTTONDBLCLK = 0x0203;
    private const uint WM_RBUTTONUP = 0x0205;
    private const uint WM_NULL = 0x0000;

    private const uint NIM_ADD = 0, NIM_MODIFY = 1, NIM_DELETE = 2;
    private const uint NIF_MESSAGE = 0x1, NIF_ICON = 0x2, NIF_TIP = 0x4;

    private const uint IMAGE_ICON = 1;
    private const uint LR_LOADFROMFILE = 0x10;

    private const uint MF_STRING = 0x0;
    private const uint TPM_RETURNCMD = 0x0100, TPM_RIGHTBUTTON = 0x0002, TPM_BOTTOMALIGN = 0x0020;
    private const int MI_SHOW = 1, MI_EXIT = 2;
    private const nint IDI_APPLICATION = 32512;

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    private struct NOTIFYICONDATAW
    {
        public uint cbSize;
        public nint hWnd;
        public uint uID;
        public uint uFlags;
        public uint uCallbackMessage;
        public nint hIcon;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 128)] public string szTip = "";
        public uint dwState;
        public uint dwStateMask;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 256)] public string szInfo = "";
        public uint uVersion; // 与 uTimeout 的 union 等长占位
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 64)] public string szInfoTitle = "";
        public uint dwInfoFlags;
        public Guid guidItem;
        public nint hBalloonIcon;

        // 字符串字段带初始值设定项时，C# 要求 struct 显式声明构造函数
        public NOTIFYICONDATAW() { }
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern bool Shell_NotifyIconW(uint dwMessage, ref NOTIFYICONDATAW lpData);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern nint LoadImageW(nint hInst, string lpszName, uint uType, int cx, int cy, uint fuLoad);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern nint LoadIconW(nint hInstance, nint lpIconName);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern bool DestroyIcon(nint hIcon);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern bool AppendMenuW(nint hMenu, uint uFlags, nint uIDNewItem, string lpNewItem);

    [DllImport("user32.dll")]
    private static extern nint CreatePopupMenu();

    [DllImport("user32.dll")]
    private static extern bool DestroyMenu(nint hMenu);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int TrackPopupMenu(nint hMenu, uint uFlags, int x, int y, int nReserved, nint hWnd, nint prcRect);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(nint hWnd);

    [DllImport("user32.dll")]
    private static extern bool PostMessageW(nint hWnd, uint msg, nint wParam, nint lParam);

    [StructLayout(LayoutKind.Sequential)]
    private struct POINT { public int X, Y; }

    [DllImport("user32.dll")]
    private static extern bool GetCursorPos(out POINT lpPoint);

    private static bool _added;
    private static nint _ownerHwnd;
    private static nint _hIcon;
    private static string _iconPath = "";
    private static Action? _onShow;
    private static Action? _onExit;

    public static bool IsVisible => _added;

    /// <summary>确保托盘图标存在；已存在时仅刷新提示文本与动作。</summary>
    public static void EnsureCreated(nint ownerHwnd, string iconPath, string tip, Action onShow, Action onExit)
    {
        _ownerHwnd = ownerHwnd;
        _onShow = onShow;
        _onExit = onExit;

        if (_added)
        {
            var modify = BuildData(tip);
            Shell_NotifyIconW(NIM_MODIFY, ref modify);
            return;
        }

        if (_hIcon == 0 || _iconPath != iconPath)
        {
            if (_hIcon != 0) DestroyIcon(_hIcon);
            _hIcon = LoadImageW(0, iconPath, IMAGE_ICON, 0, 0, LR_LOADFROMFILE);
            if (_hIcon == 0) _hIcon = LoadIconW(0, IDI_APPLICATION); // 图标文件缺失时兜底
            _iconPath = iconPath;
        }
        if (_hIcon == 0) return;

        var nid = BuildData(tip);
        if (Shell_NotifyIconW(NIM_ADD, ref nid)) _added = true;
    }

    /// <summary>移除托盘图标（保留已加载的图标句柄，供下次复用）。</summary>
    public static void Remove()
    {
        if (!_added) return;
        var nid = new NOTIFYICONDATAW { cbSize = (uint)Marshal.SizeOf<NOTIFYICONDATAW>(), hWnd = _ownerHwnd, uID = 1 };
        Shell_NotifyIconW(NIM_DELETE, ref nid);
        _added = false;
    }

    /// <summary>彻底销毁（进程退出前）：移除图标并释放图标句柄。</summary>
    public static void Destroy()
    {
        Remove();
        if (_hIcon != 0)
        {
            DestroyIcon(_hIcon);
            _hIcon = 0;
        }
        _onShow = null;
        _onExit = null;
    }

    /// <summary>主窗口子类化过程转发托盘回调（wParam=uID，lParam=鼠标消息）。</summary>
    public static void HandleMessage(nint wParam, nint lParam)
    {
        if (wParam != (nint)1) return;
        uint msg = (uint)lParam.ToInt64();
        if (msg is WM_LBUTTONUP or WM_LBUTTONDBLCLK)
        {
            _onShow?.Invoke();
        }
        else if (msg == WM_RBUTTONUP)
        {
            ShowContextMenu();
        }
    }

    private static NOTIFYICONDATAW BuildData(string tip) => new()
    {
        cbSize = (uint)Marshal.SizeOf<NOTIFYICONDATAW>(),
        hWnd = _ownerHwnd,
        uID = 1,
        uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP,
        uCallbackMessage = WM_APP_TRAY,
        hIcon = _hIcon,
        szTip = tip.Length > 127 ? tip[..127] : tip,
    };

    private static void ShowContextMenu()
    {
        if (_ownerHwnd == 0) return;
        var menu = CreatePopupMenu();
        if (menu == 0) return;

        AppendMenuW(menu, MF_STRING, (nint)MI_SHOW, Loc.GetString("Tray_ShowMainWindow"));
        AppendMenuW(menu, MF_STRING, (nint)MI_EXIT, Loc.GetString("Tray_Exit"));

        GetCursorPos(out var pt);
        // 必须把 owner 置前台，否则点击菜单外区域菜单不会消失
        SetForegroundWindow(_ownerHwnd);
        int id = TrackPopupMenu(menu, TPM_RETURNCMD | TPM_RIGHTBUTTON | TPM_BOTTOMALIGN,
                                pt.X, pt.Y, 0, _ownerHwnd, 0);
        PostMessageW(_ownerHwnd, WM_NULL, 0, 0);
        DestroyMenu(menu);

        if (id == MI_SHOW) _onShow?.Invoke();
        else if (id == MI_EXIT) _onExit?.Invoke();
    }
}
