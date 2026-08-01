using System.ComponentModel;
using HeartRate.Helpers;
using HeartRate.Models;
using HeartRate.Services;

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

        private void OnSettingsPropertyChanged(object? sender, PropertyChangedEventArgs e)
        {
            if (e.PropertyName == nameof(FloatWindowSettings.ThemeMode))
                OnPropertyChanged(nameof(ThemeModeIndex));
            if (e.PropertyName == nameof(FloatWindowSettings.Language))
                OnPropertyChanged(nameof(LanguageIndex));
        }
    }
}
