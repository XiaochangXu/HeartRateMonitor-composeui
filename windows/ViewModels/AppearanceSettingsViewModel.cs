using System.ComponentModel;
using HeartRate.Helpers;
using HeartRate.Models;
using HeartRate.Services;
using Microsoft.UI.Xaml;

namespace HeartRate.ViewModels
{
    /// <summary>
    /// 主题与语言设置页：RadioButtons 双向绑定 <see cref="Settings"/>.
    /// ThemeMode 即时应用（ThemeHelper.ApplyTheme）；Language 仅持久化，下次启动由
    /// App.OnLaunched 通过 PrimaryLanguageOverride 应用（resw 在首次访问时冻结）。
    /// </summary>
    public partial class AppearanceSettingsViewModel : BaseViewModel
    {
        public FloatWindowSettings Settings { get; } = SettingsService.Current;

        public AppearanceSettingsViewModel()
        {
            Title = "Appearance";
            Settings.PropertyChanged += OnSettingsPropertyChanged;
        }

        // ── RadioButtons.SelectedIndex 绑定（int 与 ElementTheme 互转）─────────
        // 顺序对应 UI: 0=跟随系统 / 1=亮色 / 2=暗色
        public int ThemeModeIndex
        {
            get => Settings.ThemeMode switch
            {
                ElementTheme.Dark  => 2,
                ElementTheme.Light => 1,
                _                  => 0,
            };
            set
            {
                var theme = value switch
                {
                    2 => ElementTheme.Dark,
                    1 => ElementTheme.Light,
                    _ => ElementTheme.Default,
                };
                if (Settings.ThemeMode == theme) return;
                Settings.ThemeMode = theme; // → OnThemeModeChanged → Save + ApplyTheme
            }
        }

        // 0=zh-CN / 1=en-US
        public int LanguageIndex
        {
            get => Settings.Language == "en-US" ? 1 : 0;
            set
            {
                var lang = value == 1 ? "en-US" : "zh-CN";
                if (Settings.Language == lang) return;
                Settings.Language = lang; // → OnLanguageChanged → Save
                OnPropertyChanged(nameof(LanguageRestartHint));
            }
        }

        /// <summary>语言切换后需要重启才生效的提示文案。</summary>
        public string LanguageRestartHint => L.Appearance_RestartHint;

        // ── 背景效果：云母 / 亚克力 二选一 ──────────────────────────────────

        /// <summary>背景效果下标：0=云母(Mica) / 1=亚克力(Acrylic)。变更即保存并即时切换。</summary>
        public int BackdropIndex
        {
            get => Settings.BackdropMode == "Acrylic" ? 1 : 0;
            set
            {
                var mode = value switch { 1 => "Acrylic", _ => "Mica" };
                if (Settings.BackdropMode == mode) return;
                Settings.BackdropMode = mode; // → OnBackdropModeChanged → Save + ApplyBackdrop
            }
        }

        /// <summary>Mica 材料仅 Windows 11 (22000+) 支持；Acrylic 在 Win10 17763+ 即可用。</summary>
        public bool IsMicaSupported => ThemeHelper.IsMicaSupported;

        /// <summary>Mica 不可用时（Win10）显示「云母需 Windows 11」提示。</summary>
        public Visibility MicaUnsupportedVisibility
            => IsMicaSupported ? Visibility.Collapsed : Visibility.Visible;

        private void OnSettingsPropertyChanged(object? sender, PropertyChangedEventArgs e)
        {
            if (e.PropertyName == nameof(FloatWindowSettings.ThemeMode))
                OnPropertyChanged(nameof(ThemeModeIndex));
            if (e.PropertyName == nameof(FloatWindowSettings.Language))
                OnPropertyChanged(nameof(LanguageIndex));
            if (e.PropertyName == nameof(FloatWindowSettings.BackdropMode))
                OnPropertyChanged(nameof(BackdropIndex));
        }
    }
}
