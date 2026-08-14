using Microsoft.UI.Xaml.Data;

namespace HeartRate.Converters
{
    public partial class InverseBooleanToVisibilityConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, string language)
            => value is true ? Visibility.Collapsed : Visibility.Visible;

        public object ConvertBack(object value, Type targetType, object parameter, string language)
            => value is Visibility.Collapsed;
    }
}
