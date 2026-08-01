namespace HeartRate.Views
{
    public sealed partial class DeviceListControl
    {
        public DeviceListViewModel ViewModel { get; set; } = null!;

        public DeviceListControl()
        {
            this.InitializeComponent();
        }
    }
}
