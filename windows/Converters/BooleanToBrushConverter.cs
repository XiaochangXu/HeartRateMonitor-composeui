using Microsoft.UI.Xaml.Data;
using Microsoft.UI.Xaml.Media;

namespace HeartRate.Converters
{
    /// <summary>bool → 画笔：true 用 TrueBrush，false 用 FalseBrush。</summary>
    public partial class BooleanToBrushConverter : IValueConverter
    {
        public Brush? TrueBrush { get; set; }
        public Brush? FalseBrush { get; set; }

        public object Convert(object value, Type targetType, object parameter, string language)
            => value is true
                ? (TrueBrush ?? FalseBrush ?? new SolidColorBrush(Microsoft.UI.Colors.Transparent))
                : (FalseBrush ?? TrueBrush ?? new SolidColorBrush(Microsoft.UI.Colors.Transparent));

        public object ConvertBack(object value, Type targetType, object parameter, string language)
            => throw new NotSupportedException();
    }
}
