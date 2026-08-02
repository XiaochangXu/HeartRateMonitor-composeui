using System.ComponentModel;
using System.Runtime.InteropServices;
using HeartRate.Helpers;
using HeartRate.Services;
using HeartRate.Windows;
using Microsoft.UI;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Windowing;
using Windows.Foundation;
using Windows.Graphics;
using Windows.UI;
using Windows.Win32;
using Windows.Win32.Foundation;
using Windows.Win32.UI.Input.KeyboardAndMouse;
using WinRT.Interop;

namespace HeartRate;

public sealed partial class MainWindow : Window
{
    private readonly FrameworkElement _rootElement;
    private readonly DispatcherQueue _uiDispatcher;
    private FloatWindow? _floatWindow;

    // ── 全局热键（SetWindowSubclass 拦截 WM_HOTKEY + RegisterHotKey）─────
    private const uint WM_HOTKEY = 0x0312;
    private const int HOTKEY_ID = 1;
    private static readonly SUBCLASSPROC _subclassProc = SubclassProc;
    private string? _registeredHotKey;

    private delegate IntPtr SUBCLASSPROC(IntPtr hwnd, uint msg, IntPtr wParam, IntPtr lParam,
                                         UIntPtr uIdSubclass, IntPtr dwRefData);

    [DllImport("comctl32.dll", SetLastError = true)]
    private static extern bool SetWindowSubclass(IntPtr hwnd, SUBCLASSPROC pfnSubclass,
                                                  UIntPtr uIdSubclass, IntPtr dwRefData);

    [DllImport("comctl32.dll", SetLastError = true)]
    private static extern bool RemoveWindowSubclass(IntPtr hwnd, SUBCLASSPROC pfnSubclass,
                                                     UIntPtr uIdSubclass);

    [DllImport("comctl32.dll")]
    private static extern IntPtr DefSubclassProc(IntPtr hwnd, uint msg, IntPtr wParam, IntPtr lParam);

    public MainViewModel ViewModel { get; }

    public MainWindow()
    {
        // Build services before InitializeComponent so ViewModel is ready for x:Bind
        var heartRateService = new HeartRateService();
        ViewModel = new MainViewModel(heartRateService);

        InitializeComponent();

        Title = L.MainWindow_Title;
        ToolTipService.SetToolTip(DockButton, L.MainWindow_ToggleFloat);

        _rootElement = (FrameworkElement)Content;
        ThemeHelper.RootElement = _rootElement;
        ThemeHelper.MainWindow = this;
        ThemeHelper.ApplyBackdrop(SettingsService.Current.BackdropMode);
        // 按设置应用主题（默认 Light）。RootElement 已就绪，ApplyTheme 内部会广播 ThemeChanged
        // 供后续可能打开的悬浮窗等子窗口同步主题。
        ThemeHelper.ApplyTheme(SettingsService.Current.ThemeMode);
        _rootElement.ActualThemeChanged += OnRootElementActualThemeChanged;

        // 捕获 UI 线程 DispatcherQueue：配对请求从 Kestrel 工作线程到达，
        // 需切回 UI 线程才能访问 XamlRoot / 显示 ContentDialog（否则 RPC_E_WRONG_THREAD 0x8001010E）。
        _uiDispatcher = DispatcherQueue.GetForCurrentThread();

        // 局域网传输配对弹窗：立即注入 handler（不依赖 Loaded 事件）。
        InjectLanApprovalHandler();
        // 蓝牙冲突提示弹窗（局域网已连接时阻止蓝牙连接）。
        InjectBluetoothBlockedHandler();

        ExtendsContentIntoTitleBar = true;
        SetTitleBar(AppTitleBar);

        UpdateCaptionButtonColors();
        // 显式固定窗口圆角为 8px（不随系统策略变化）
        ApplyWindowCornerPreference();

        // 任务栏/窗口图标：显式使用 favicon.ico（unpackaged 下 exe 图标资源
        // 不一定被任务栏拾取，SetIcon 保证标题栏与任务栏图标一致）
        var iconPath = Path.Combine(AppContext.BaseDirectory, "winico", "favicon.ico");
        if (File.Exists(iconPath)) AppWindow.SetIcon(iconPath);

        RestoreWindowBounds();

        ViewModel.HeartRate.FloatWindowVisibilityRequested += OnFloatWindowVisibilityRequested;
        ViewModel.HeartRate.PropertyChanged += OnHeartRatePropertyChanged;

        NavView.SelectedItem = NavView.MenuItems[0];

        Closed += OnClosed;

        SetupHotKey();
    }

    private void InjectLanApprovalHandler()
    {
        // 配对请求从 Kestrel 工作线程到达，必须切到 UI 线程才能访问 XamlRoot / 显示 ContentDialog。
        // 用 TaskCompletionSource 把 UI 线程的弹窗结果回传给 HTTP 线程 await。
        ViewModel.LanTransfer.SetApprovalHandler(req =>
        {
            var tcs = new TaskCompletionSource<bool>();
            bool enqueued = _uiDispatcher.TryEnqueue(DispatcherQueuePriority.Normal, () =>
            {
                try
                {
                    var root = _rootElement.XamlRoot;
                    if (root is null) { tcs.SetResult(false); return; }

                    var body = Loc.Format("Lan_PairBody", req.DeviceName, req.Platform);
                    var dlg = new ContentDialog
                    {
                        Title = L.Lan_PairTitle,
                        Content = body,
                        PrimaryButtonText = L.Lan_PairApprove,
                        CloseButtonText = L.Lan_PairReject,
                        DefaultButton = ContentDialogButton.Primary,
                        XamlRoot = root,
                    };
                    var op = dlg.ShowAsync();
                    op.Completed = (info, status) =>
                    {
                        tcs.SetResult(status == AsyncStatus.Completed
                                      && info.GetResults() == ContentDialogResult.Primary);
                    };
                }
                catch
                {
                    tcs.SetResult(false);
                }
            });
            if (!enqueued)
            {
                tcs.SetResult(false);
            }
            return tcs.Task;
        });
    }

    /// <summary>注入蓝牙冲突提示弹窗：局域网已连接时点击「连接」弹出提示。</summary>
    private void InjectBluetoothBlockedHandler()
    {
        // ToggleConnect 由按钮点击触发，通常在 UI 线程；保持与配对弹窗一致的派发模式。
        ViewModel.HeartRate.ShowDialogRequested = (title, body) =>
        {
            var tcs = new TaskCompletionSource<bool>();
            bool enqueued = _uiDispatcher.TryEnqueue(() =>
            {
                try
                {
                    var root = _rootElement.XamlRoot;
                    if (root is null) { tcs.SetResult(false); return; }

                    var dlg = new ContentDialog
                    {
                        Title = title,
                        Content = body,
                        PrimaryButtonText = L.Dialog_Ok,
                        DefaultButton = ContentDialogButton.Primary,
                        XamlRoot = root,
                    };
                    _ = dlg.ShowAsync();
                    tcs.SetResult(true);
                }
                catch
                {
                    tcs.SetResult(false);
                }
            });
            if (!enqueued)
            {
                tcs.SetResult(false);
            }
            return tcs.Task;
        };
    }

    private void NavView_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        var tag = (args.SelectedItem as NavigationViewItem)?.Tag as string;
        HomeContent.Visibility = tag == "home" ? Visibility.Visible : Visibility.Collapsed;
        FloatSettingsContent.Visibility = tag == "floatSettings" ? Visibility.Visible : Visibility.Collapsed;
        NetworkContent.Visibility = tag == "network" ? Visibility.Visible : Visibility.Collapsed;
        AppearanceContent.Visibility = tag == "appearance" ? Visibility.Visible : Visibility.Collapsed;
        LanTransferContent.Visibility = tag == "lanTransfer" ? Visibility.Visible : Visibility.Collapsed;
        VersionInfoContent.Visibility = tag == "versionInfo" ? Visibility.Visible : Visibility.Collapsed;
    }

    private void DockButton_Click(object sender, RoutedEventArgs e)
    {
        ViewModel.HeartRate.ToggleFloatWindowCommand.Execute(null);
    }

    private void OnFloatWindowVisibilityRequested(object? sender, bool visible)
    {
        if (visible)
        {
            // FloatWindow 内部用 D3D11/D2D/DComp + Win32 P/Invoke 创建独立窗口，
            // 任何一步抛 COMException 都会冒泡到 OnLaunched 被 WinUI 转成
            // STOWED_EXCEPTION(0xC000027B) 进程崩溃。这里吞掉异常只影响悬浮窗本身，
            // 主窗口仍能正常启动；用户可从托盘/按钮再次尝试打开。
            try
            {
                if (_floatWindow is null)
                    _floatWindow = new FloatWindow();
                _floatWindow.Show();
                if (ViewModel.HeartRate.HeartRate is int hr)
                    _floatWindow.UpdateHeartRate(hr);
            }
            catch (Exception ex)
            {
                try
                {
                    var dir = Path.Combine(
                        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                        "HeartRate");
                    Directory.CreateDirectory(dir);
                    File.AppendAllText(Path.Combine(dir, "startup.log"),
                        $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] FloatWindow 创建失败: {ex}\n\n");
                }
                catch { }
                _floatWindow = null;
                ViewModel.HeartRate.IsFloatWindowVisible = false;
            }
        }
        else
        {
            _floatWindow?.Close();
        }
    }

    private void OnHeartRatePropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName != nameof(HeartRateViewModel.HeartRate)) return;
        if (_floatWindow is null) return;
        // 数据源断开（HeartRate=null，如 LAN/蓝牙断开）时清除悬浮窗显示，
        // 否则悬浮窗会残留断开前最后一个心率值
        if (ViewModel.HeartRate.HeartRate is int hr)
            _floatWindow.UpdateHeartRate(hr);
        else
            _floatWindow.ClearHeartRate();
    }

    private void OnRootElementActualThemeChanged(FrameworkElement sender, object args)
    {
        UpdateCaptionButtonColors();
    }

    private void UpdateCaptionButtonColors()
    {
        var tb = AppWindow.TitleBar;
        var isDarkTheme = _rootElement.ActualTheme == ElementTheme.Dark;

        var foregroundColor = isDarkTheme
            ? Colors.White
            : Color.FromArgb(230, 0, 0, 0);
        var inactiveForegroundColor = isDarkTheme
            ? Color.FromArgb(153, 255, 255, 255)
            : Color.FromArgb(138, 0, 0, 0);
        var hoverBackgroundColor = isDarkTheme
            ? Color.FromArgb(30, 255, 255, 255)
            : Color.FromArgb(18, 0, 0, 0);
        var pressedBackgroundColor = isDarkTheme
            ? Color.FromArgb(60, 255, 255, 255)
            : Color.FromArgb(36, 0, 0, 0);

        tb.ButtonBackgroundColor = Colors.Transparent;
        tb.ButtonInactiveBackgroundColor = Colors.Transparent;
        tb.ButtonHoverBackgroundColor = hoverBackgroundColor;
        tb.ButtonPressedBackgroundColor = pressedBackgroundColor;
        tb.ButtonForegroundColor = foregroundColor;
        tb.ButtonInactiveForegroundColor = inactiveForegroundColor;
        tb.ButtonHoverForegroundColor = foregroundColor;
        tb.ButtonPressedForegroundColor = foregroundColor;

        // 窗口顶部 1px 边框（DWM 非客户区）默认跟随系统主题，应用内强制暗黑时
        // 会残留一条白色细线。用 DWMWA_BORDER_COLOR 按当前主题同步边框颜色。
        ApplyDwmBorderColor(isDarkTheme);
    }

    // ── 顶部边框/按钮区颜色：消除暗黑模式下 DWM 非客户区残留的白色细线 ──
    private void ApplyDwmBorderColor(bool isDarkTheme)
    {
        try
        {
            var hwnd = WindowNative.GetWindowHandle(this);
            // COLORREF = 0x00BBGGRR：暗黑用 WinUI 深色基底 #202020，浅色用白色
            uint color = isDarkTheme ? 0x00202020u : 0x00FFFFFFu;
            // 顶部 1px 窗口边框
            DwmSetWindowAttribute(hwnd, DWMWA_BORDER_COLOR, ref color, sizeof(uint));
            // 标题栏/最小化最大化关闭按钮区的底色（左侧被 XAML 内容盖住，
            // 右侧按钮区归 DWM 画，暗黑下默认仍偏白，需一并设暗）
            DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, ref color, sizeof(uint));
        }
        catch { /* 旧系统/不支持该属性时忽略 */ }
    }

    // DWMWA_BORDER_COLOR / DWMWA_CAPTION_COLOR（Windows 11 引入；Win10 下 DWM 会忽略）
    private const int DWMWA_BORDER_COLOR = 34;
    private const int DWMWA_CAPTION_COLOR = 35;

    // ── 窗口圆角：显式固定为 8px（DWMWCP_ROUND），不随系统策略变化 ──
    private void ApplyWindowCornerPreference()
    {
        try
        {
            var hwnd = WindowNative.GetWindowHandle(this);
            // DWMWCP_ROUND = 2（8px 圆角）
            uint preference = 2;
            DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, ref preference, sizeof(uint));
        }
        catch { /* 旧系统/不支持该属性时忽略 */ }
    }

    // DWMWA_WINDOW_CORNER_PREFERENCE（Windows 11 引入；Win10 下 DWM 会忽略）
    private const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref uint pvAttribute, uint cbAttribute);

    private void RestoreWindowBounds()
    {
        const int defaultWidth = 980;
        const int defaultHeight = 600;
        var s = SettingsService.Current;
        var work = DisplayArea.GetFromWindowId(AppWindow.Id, DisplayAreaFallback.Nearest).WorkArea;

        int width = defaultWidth;
        int height = defaultHeight;
        int x, y;

        var savedVisible = s.HasWindowBounds && s.WindowWidth > 0 && s.WindowHeight > 0
            && s.WindowX < work.X + work.Width
            && s.WindowX + s.WindowWidth > work.X
            && s.WindowY < work.Y + work.Height
            && s.WindowY + s.WindowHeight > work.Y;

        if (savedVisible)
        {
            // 显示器变小/分辨率降低时收缩并钳制，避免窗口部分移出屏幕难以取回
            width = Math.Min(s.WindowWidth, work.Width);
            height = Math.Min(s.WindowHeight, work.Height);
            x = Math.Clamp(s.WindowX, work.X, Math.Max(work.X, work.X + work.Width - width));
            y = Math.Clamp(s.WindowY, work.Y, Math.Max(work.Y, work.Y + work.Height - 100));
        }
        else
        {
            x = work.X + (work.Width - width) / 2;
            y = work.Y + (work.Height - height) / 2;
        }

        AppWindow.MoveAndResize(new RectInt32(x, y, width, height));
    }

    private void OnClosed(object sender, WindowEventArgs args)
    {
        // 先注销热键与子类化，避免主窗口销毁后仍接收 WM_HOTKEY
        SettingsService.Changed -= OnSettingsChangedForHotKey;
        var hwndCleanup = WindowNative.GetWindowHandle(this);
        PInvoke.UnregisterHotKey(new HWND((nint)hwndCleanup), HOTKEY_ID);
        RemoveWindowSubclass(hwndCleanup, _subclassProc, (UIntPtr)1);

        // 悬浮窗还开着时关主窗口：先收掉悬浮窗（内部会保存位置），再落盘挂起的设置
        _floatWindow?.Close();
        _floatWindow = null;

        var s = SettingsService.Current;
        s.HasWindowBounds = true;
        s.WindowX = AppWindow.Position.X;
        s.WindowY = AppWindow.Position.Y;
        s.WindowWidth = AppWindow.Size.Width;
        s.WindowHeight = AppWindow.Size.Height;
        SettingsService.SaveNow();

        ViewModel.HeartRate.FloatWindowVisibilityRequested -= OnFloatWindowVisibilityRequested;
        ViewModel.HeartRate.PropertyChanged -= OnHeartRatePropertyChanged;
        _rootElement.ActualThemeChanged -= OnRootElementActualThemeChanged;

        // 停止 HTTP/WebSocket 服务、解除心率事件订阅
        ViewModel.Shutdown();
    }

    // ── 全局热键注册与 WM_HOTKEY 处理 ─────────────────────────────────────

    private void SetupHotKey()
    {
        var hwnd = WindowNative.GetWindowHandle(this);
        SetWindowSubclass(hwnd, _subclassProc, (UIntPtr)1, IntPtr.Zero);
        RegisterCurrentHotKey();
        SettingsService.Changed += OnSettingsChangedForHotKey;
    }

    private void OnSettingsChangedForHotKey()
    {
        // 仅当热键字符串变化时重新注册，避免每次任意设置变更都重注册
        if (SettingsService.Current.ClickThroughHotKey != _registeredHotKey)
            RegisterCurrentHotKey();
    }

    private void RegisterCurrentHotKey()
    {
        var hwnd = WindowNative.GetWindowHandle(this);
        var oldKey = _registeredHotKey;
        PInvoke.UnregisterHotKey(new HWND((nint)hwnd), HOTKEY_ID);
        _registeredHotKey = null;

        var s = SettingsService.Current;
        if (string.IsNullOrEmpty(s.ClickThroughHotKey)) return;
        if (!HotKeyParser.TryParse(s.ClickThroughHotKey, out uint mods, out uint vk))
        {
            // 新热键无法解析（录制与注册不对称，如 Space 等键）：恢复旧热键
            RestoreHotKey(hwnd, oldKey);
            return;
        }

        // MOD_NOREPEAT：按住热键只触发一次，避免状态连续翻转
        if (PInvoke.RegisterHotKey(new HWND((nint)hwnd), HOTKEY_ID, (HOT_KEY_MODIFIERS)(mods | HotKeyParser.MOD_NOREPEAT), vk))
        {
            _registeredHotKey = s.ClickThroughHotKey;
        }
        else
        {
            // 新热键注册失败（被其他应用占用等）：恢复旧热键，避免静默丢失
            RestoreHotKey(hwnd, oldKey);
        }
    }

    /// <summary>注册失败/解析失败时恢复之前生效的热键，避免功能静默丢失。</summary>
    private void RestoreHotKey(nint hwnd, string? oldKey)
    {
        if (string.IsNullOrEmpty(oldKey)) return;
        if (!HotKeyParser.TryParse(oldKey, out uint mods, out uint vk)) return;
        if (PInvoke.RegisterHotKey(new HWND((nint)hwnd), HOTKEY_ID, (HOT_KEY_MODIFIERS)(mods | HotKeyParser.MOD_NOREPEAT), vk))
            _registeredHotKey = oldKey;
    }

    private static IntPtr SubclassProc(IntPtr hwnd, uint msg, IntPtr wParam, IntPtr lParam,
                                       UIntPtr uIdSubclass, IntPtr dwRefData)
    {
        if (msg == WM_HOTKEY && wParam == (IntPtr)HOTKEY_ID)
        {
            // 翻转锁定状态 → 触发 OnClickThroughEnabledChanged → Save → Changed
            // → 悬浮窗更新 HTTRANSPARENT 行为 + 蓝色边框；ToggleSwitch 自动同步。
            var s = SettingsService.Current;
            s.ClickThroughEnabled = !s.ClickThroughEnabled;
        }
        return DefSubclassProc(hwnd, msg, wParam, lParam);
    }
}
