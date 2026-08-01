using System.Runtime.InteropServices;
using System.Text;
using HeartRate.Services;
using Microsoft.UI.Xaml;
using Microsoft.Windows.Globalization;

namespace HeartRate;

public partial class App : Application
{
    public static Window? MainWindow { get; private set; }

    /// <summary>单实例锁：同名 Mutex 已存在说明已有实例在运行。</summary>
    private static Mutex? _instanceMutex;

    public App()
    {
        InitializeComponent();
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        // ── 单实例保护 ────────────────────────────────────────────────────
        // 局域网传输（mDNS/HTTP/WS）依赖本机固定端口，双开会互相抢占端口导致
        // "address already in use"。第二个实例直接退出并把已有窗口带到前台。
        if (_instanceMutex is null)
        {
            _instanceMutex = new Mutex(true, @"Local\HeartRateMonitor_SingleInstance", out var createdNew);
            if (!createdNew)
            {
                ActivateExistingWindow();
                Environment.Exit(0);
                return;
            }
        }

        // 启动期间的任何未捕获异常都会被 WinUI 转成 STOWED_EXCEPTION(0xC000027B)
        // 导致进程立即崩溃且无窗口弹出，对用户表现为"双击 exe 无反应"。
        // 这里整体 try-catch 把异常落盘到 %LOCALAPPDATA%\HeartRate\startup.log，
        // 便于诊断，同时仍向上抛以保留 WinUI 原始崩溃语义（仅多了一份日志）。
        try
        {
            // 语言：resw 在首次访问时按当前 locale 冻结，需在创建 MainWindow（触发
            // x:Bind/x:Uid 解析）之前设置 PrimaryLanguageOverride，下个进程生效。
            // 必须使用 WinAppSDK 的 Microsoft.Windows.Globalization.ApplicationLanguages；
            // Windows.Globalization 版本在 unpackaged 应用中会抛 InvalidOperationException。
            ApplicationLanguages.PrimaryLanguageOverride = SettingsService.Current.Language;

            MainWindow = new MainWindow();
            MainWindow.Activate();
        }
        catch (Exception ex)
        {
            try
            {
                var dir = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                    "HeartRate");
                Directory.CreateDirectory(dir);
                var logPath = Path.Combine(dir, "startup.log");
                var sb = new StringBuilder();
                sb.AppendLine($"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] OnLaunched 异常:");
                AppendException(sb, ex, depth: 0);
                sb.AppendLine();
                File.AppendAllText(logPath, sb.ToString());
            }
            catch { /* 写日志失败不影响后续行为 */ }
            // 重新抛出，保留 WinUI 的崩溃处理；日志已落盘。
            throw;
        }
    }

    /// <summary>递归打印异常及其 InnerException 链到日志。</summary>
    private static void AppendException(StringBuilder sb, Exception? ex, int depth)
    {
        if (ex is null) return;
        var indent = new string(' ', depth * 2);
        sb.AppendLine($"{indent}Type: {ex.GetType().FullName}");
        sb.AppendLine($"{indent}Message: {ex.Message}");
        sb.AppendLine($"{indent}HResult: 0x{ex.HResult:X8}");
        sb.AppendLine($"{indent}Source: {ex.Source}");
        sb.AppendLine($"{indent}StackTrace:");
        sb.AppendLine(ex.StackTrace?.Replace("\n", "\n" + indent + "  "));
        if (ex.InnerException is not null)
        {
            sb.AppendLine($"{indent}InnerException:");
            AppendException(sb, ex.InnerException, depth + 1);
        }
    }

    /// <summary>把已运行的实例窗口带到前台（WinUI 3 unpackaged 窗口类名）。</summary>
    private static void ActivateExistingWindow()
    {
        try
        {
            var hwnd = FindWindowW("WinUIDesktopWin32WindowClass", null);
            if (hwnd != IntPtr.Zero)
            {
                ShowWindow(hwnd, SW_RESTORE);
                SetForegroundWindow(hwnd);
            }
        }
        catch { /* 激活失败不阻塞退出 */ }
    }

    private const int SW_RESTORE = 9;

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr FindWindowW(string? lpClassName, string? lpWindowName);

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool SetForegroundWindow(IntPtr hWnd);
}
