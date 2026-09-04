using System.ComponentModel;
using HeartRate.Helpers;
using HeartRate.Models;
using HeartRate.Services;
using Windows.UI;
using Windows.System;

namespace HeartRate.ViewModels
{
    /// <summary>悬浮窗设置页：直接双向绑定 <see cref="Models.FloatWindowSettings"/>。</summary>
    public partial class FloatWindowSettingsViewModel : BaseViewModel
    {
        public FloatWindowSettings Settings { get; } = SettingsService.Current;

        [ObservableProperty]
        private Color _heartColor;

        public FloatWindowSettingsViewModel()
        {
            Title = "Float Window Settings";
            HeartColor = ColorUtil.Parse(Settings.HeartColor);
            Settings.PropertyChanged += OnSettingsPropertyChanged;
        }

        partial void OnHeartColorChanged(Color value)
        {
            Settings.HeartColor = ColorUtil.ToHex(value);
            SettingsService.Save();
        }

        public string ScaleText => $"{Settings.FloatingSize}%";
        public string IconSizeText => $"{Settings.FloatingIconSize}%";
        public string HotKeyDisplay => Settings.ClickThroughHotKey;

        /// <summary>录制热键：由设置页 TextBox.KeyDown 调用。</summary>
        public void RecordHotKey(VirtualKey key, bool ctrl, bool alt, bool shift, bool win)
        {
            if (HotKeyParser.IsModifier(key)) return;
            var str = HotKeyParser.Format(key, ctrl, alt, shift, win);
            if (str == Settings.ClickThroughHotKey) return;
            Settings.ClickThroughHotKey = str;
            SettingsService.Save();
            OnPropertyChanged(nameof(HotKeyDisplay));
        }

        public void ClearHotKey()
        {
            if (string.IsNullOrEmpty(Settings.ClickThroughHotKey)) return;
            Settings.ClickThroughHotKey = string.Empty;
            SettingsService.Save();
            OnPropertyChanged(nameof(HotKeyDisplay));
        }

        /// <summary>重置外观为默认值并刷新绑定文本/颜色。</summary>
        public void ResetToDefaults()
        {
            Settings.ResetAppearanceToDefaults();
            SettingsService.Save();
            OnPropertyChanged(nameof(ScaleText));
            OnPropertyChanged(nameof(IconSizeText));
            HeartColor = ColorUtil.Parse(Settings.HeartColor);
        }

        private void OnSettingsPropertyChanged(object? sender, PropertyChangedEventArgs e)
        {
            OnPropertyChanged(nameof(ScaleText));
            OnPropertyChanged(nameof(IconSizeText));
            if (e.PropertyName == nameof(FloatWindowSettings.HeartColor))
                HeartColor = ColorUtil.Parse(Settings.HeartColor);
            if (e.PropertyName == nameof(FloatWindowSettings.ClickThroughHotKey))
                OnPropertyChanged(nameof(HotKeyDisplay));
        }
    }
}
