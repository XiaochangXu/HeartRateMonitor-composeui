using HeartRate.Models;
using HeartRate.Services;

namespace HeartRate.ViewModels
{
    /// <summary>应用设置页：直接双向绑定 FloatWindowSettings（自动连接/自动重连）。</summary>
    public partial class AppSettingsViewModel : BaseViewModel
    {
        public AppSettingsViewModel()
        {
            Title = "App Settings";
        }

        public FloatWindowSettings Settings { get; } = SettingsService.Current;
    }
}
