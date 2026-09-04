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

            // 录制动作（改写本 TextBox.Text + 重注册全局热键）不在 KeyDown 处理栈内
            // 同步执行，避免输入事件中途重入修改文本状态；修饰键状态必须在当前
            // 消息内读取，故先取值再入队。
            DispatcherQueue.TryEnqueue(() => ViewModel.RecordHotKey(key, ctrl, alt, shift, win));
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
            // 禁用 HasFlag：WinAppSDK 1.8 投影下返回值的运行时枚举类型是
            // Windows.UI.Core.CoreVirtualKeyStates，与 Microsoft.UI.Input.VirtualKeyStates
            // 不一致，Enum.HasFlag 会抛 ArgumentException(0x80070057)，
            // 经 XAML 输入回调变成 stowed exception → 0xC000027B 闪退。
            // 两枚举底层值相同（Down=1），按位比较始终安全。
            return ((int)state & (int)VirtualKeyStates.Down) != 0;
        }
    }
}
