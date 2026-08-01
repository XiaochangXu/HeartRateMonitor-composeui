using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;

namespace HeartRate.Views
{
    public sealed partial class HeartRateControl
    {
        public HeartRateViewModel ViewModel { get; set; } = null!;

        public HeartRateControl()
        {
            this.InitializeComponent();
        }

        /// <summary>
        /// ToggleSwitch 视觉状态由 VM 单向驱动；这里只在用户操作（开关值
        /// 与 VM 不一致）时执行切换命令，避免 TwoWay 回写与命令相互覆盖。
        /// </summary>
        private void OnFloatToggleToggled(object sender, RoutedEventArgs e)
        {
            if (sender is not ToggleSwitch ts) return;
            if (ts.IsOn == ViewModel.IsFloatWindowVisible) return;
            ViewModel.ToggleFloatWindowCommand.Execute(null);
        }
    }
}
