namespace HeartRate.ViewModels
{
    public partial class BaseViewModel : ObservableObject
    {
        protected BaseViewModel()
        {
            _title = string.Empty;
        }

        [ObservableProperty]
        private string _title = string.Empty;
    }
}
