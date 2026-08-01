using Windows.UI;

namespace HeartRate.Helpers;

/// <summary>十六进制颜色字符串与 Windows.UI.Color 互转。#RRGGBB / #AARRGGBB。</summary>
public static class ColorUtil
{
    public static Color Parse(string? hex)
    {
        if (string.IsNullOrWhiteSpace(hex)) return Microsoft.UI.Colors.Red;
        var s = hex.Trim().TrimStart('#');
        try
        {
            return s.Length switch
            {
                6 => Color.FromArgb(255,
                    Convert.ToByte(s.Substring(0, 2), 16),
                    Convert.ToByte(s.Substring(2, 2), 16),
                    Convert.ToByte(s.Substring(4, 2), 16)),
                8 => Color.FromArgb(
                    Convert.ToByte(s.Substring(0, 2), 16),
                    Convert.ToByte(s.Substring(2, 2), 16),
                    Convert.ToByte(s.Substring(4, 2), 16),
                    Convert.ToByte(s.Substring(6, 2), 16)),
                _ => Microsoft.UI.Colors.Red,
            };
        }
        catch
        {
            return Microsoft.UI.Colors.Red;
        }
    }

    public static string ToHex(Color c) => $"#{c.R:X2}{c.G:X2}{c.B:X2}";
}
