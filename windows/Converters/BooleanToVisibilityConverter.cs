using Microsoft.UI.Xaml.Data;

namespace HeartRate.Converters
{
    public partial class BooleanToVisibilityConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, string language)
            => value is true ? Visibility.Visible : Visibility.Collapsed;

        public object ConvertBack(object value, Type targetType, object parameter, string language)
            => value is Visibility.Visible;
    }
}
