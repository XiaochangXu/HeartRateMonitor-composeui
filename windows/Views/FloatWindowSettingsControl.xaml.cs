using HeartRate.Helpers;
using Microsoft.UI.Input;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Windows.System;

namespace HeartRate.Views
{
    public sealed partial class FloatWindowSettingsControl
    {
        public FloatWindowSettingsViewModel ViewModel { get; set; } = null!;

        public FloatWindowSettingsControl()
        {
            this.InitializeComponent();
        }

        // 热键录制：按下任意非修饰键 + 修饰键组合即录入
        private void HotKeyTextBox_KeyDown(object sender, KeyRoutedEventArgs e)
        {
            e.Handled = true;
            var key = e.Key;
            if (HotKeyParser.IsModifier(key)) return;

            bool ctrl = IsDown(VirtualKey.Control);
            bool alt = IsDown(VirtualKey.Menu);
            bool shift = IsDown(VirtualKey.Shift);
            bool win = IsDown(VirtualKey.LeftWindows) || IsDown(VirtualKey.RightWindows);

            ViewModel.RecordHotKey(key, ctrl, alt, shift, win);
        }

        private void ClearHotKey_Click(object sender, RoutedEventArgs e)
        {
            ViewModel.ClearHotKey();
        }

        // 重置外观为默认值（不影响触摸穿透/热键）
        private void Reset_Click(object sender, RoutedEventArgs e)
        {
            ViewModel.ResetToDefaults();
        }

        private static bool IsDown(VirtualKey key)
        {
            var state = InputKeyboardSource.GetKeyStateForCurrentThread(key);
            return state.HasFlag(VirtualKeyStates.Down);
        }
    }
}
