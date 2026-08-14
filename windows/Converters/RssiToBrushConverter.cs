using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Data;

namespace HeartRate.Converters
{
    /// <summary>信号强度(dBm) → 分级画笔：绿 ≥ -60，黄 -60~-80，红 &lt; -80；不可用显示中性色。</summary>
    public partial class RssiToBrushConverter : IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, string language)
        {
            var key = value switch
            {
                int.MinValue or 0 => "SignalUnknownBrush",
                int rssi when rssi >= -60 => "SignalStrongBrush",
                int rssi when rssi >= -80 => "SignalMediumBrush",
                _ => "SignalWeakBrush",
            };
            return Application.Current.Resources[key];
        }

        public object ConvertBack(object value, Type targetType, object parameter, string language)
            => throw new NotSupportedException();
    }
}
