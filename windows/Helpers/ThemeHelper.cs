using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml.Media;

namespace HeartRate.Helpers
{
    public static class ThemeHelper
    {
        public static FrameworkElement? RootElement { get; set; }
        public static Window? MainWindow { get; set; }

        private static ElementTheme _currentTheme = ElementTheme.Default;
        public static ElementTheme CurrentTheme => _currentTheme;

        private static event Action<ElementTheme>? ThemeChanged;

        // 与 FloatWindowSettings 的默认 BackdropMode 保持一致（默认亚克力）；启动时 ApplyBackdrop 会覆盖
        private static string _currentBackdrop = "Acrylic";
        public static string CurrentBackdrop => _currentBackdrop;

        // Cache controller instances so switching backdrop doesn't allocate a
        // fresh DComp controller each time.
        private static MicaBackdrop? _micaBackdrop;
        private static DesktopAcrylicBackdrop? _acrylicBackdrop;

        /// <summary>Actual resolved theme (Light or Dark) based on current setting.</summary>
        public static ElementTheme ActualTheme
            => RootElement?.ActualTheme ?? ElementTheme.Default;

        /// <summary>Mica 材料需要 Windows 11 (22000+)；Windows 10 上由 WinAppSDK 自动回退纯色。</summary>
        public static bool IsMicaSupported
            => OperatingSystem.IsWindowsVersionAtLeast(10, 0, 22000);

        public static void ApplyTheme(ElementTheme theme)
        {
            _currentTheme = theme;
            if (RootElement != null)
                RootElement.RequestedTheme = theme;

            ThemeChanged?.Invoke(theme);
        }

        public static void ApplyTitleBarTheme(Window window, ElementTheme theme)
        {
            window.AppWindow.TitleBar.PreferredTheme = theme switch
            {
                ElementTheme.Light => TitleBarTheme.Light,
                ElementTheme.Dark  => TitleBarTheme.Dark,
                _                  => TitleBarTheme.UseDefaultAppMode,
            };
        }

        /// <summary>
        /// Makes a secondary window follow the app's light/dark theme: seeds the
        /// initial theme on <paramref name="root"/> and the title bar, then keeps
        /// both in sync until the window closes (self-unsubscribes on Closed).
        /// </summary>
        public static void FollowAppTheme(Window window, FrameworkElement root)
        {
            root.RequestedTheme = _currentTheme;
            ApplyTitleBarTheme(window, _currentTheme);

            void OnChanged(ElementTheme theme)
            {
                root.RequestedTheme = theme;
                ApplyTitleBarTheme(window, theme);
            }

            ThemeChanged += OnChanged;
            window.Closed += (_, _) => ThemeChanged -= OnChanged;
        }

        public static void ApplyBackdrop(string backdrop)
        {
            if (MainWindow is null) return;

            // Acrylic 支持 Win10 17763+ 与 Win11；Mica 仅 Win11（Win10 下 WinAppSDK
            // 自动回退纯色，不会崩溃），两者由 API 自行回退，无需按版本过滤。
            if (_currentBackdrop == backdrop && MainWindow.SystemBackdrop is not null) return;

            MainWindow.SystemBackdrop = backdrop switch
            {
                "Acrylic" => _acrylicBackdrop ??= new DesktopAcrylicBackdrop(),
                _         => _micaBackdrop ??= new MicaBackdrop(),
            };
            _currentBackdrop = backdrop;
        }
    }
}
