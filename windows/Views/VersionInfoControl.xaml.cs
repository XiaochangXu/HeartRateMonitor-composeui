using System.Reflection;
using Microsoft.UI.Xaml;

namespace HeartRate.Views
{
    public sealed partial class VersionInfoControl
    {
        public VersionInfoControl()
        {
            this.InitializeComponent();
            Loaded += OnLoaded;
        }

        private void OnLoaded(object sender, RoutedEventArgs e)
        {
            // 读取程序集版本（AssemblyVersion），取主.次.修订三段显示。
            var v = Assembly.GetExecutingAssembly().GetName().Version;
            VersionText.Text = v is null ? "1.0.0" : v.ToString(3);
        }
    }
}
