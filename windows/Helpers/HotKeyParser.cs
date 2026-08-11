using Windows.System;

namespace HeartRate.Helpers;

/// <summary>
/// 热键字符串与 (修饰键掩码, 虚拟键码) 之间的双向转换。
/// 字符串格式形如 "Ctrl+Shift+T"，与 Win32 RegisterHotKey 的参数对应。
/// </summary>
public static class HotKeyParser
{
    public const uint MOD_ALT = 0x0001;
    public const uint MOD_CONTROL = 0x0002;
    public const uint MOD_SHIFT = 0x0004;
    public const uint MOD_WIN = 0x0008;
    public const uint MOD_NOREPEAT = 0x4000;

    /// <summary>解析 "Ctrl+Shift+T" → (mods, vk)。失败返回 false。</summary>
    public static bool TryParse(string? s, out uint mods, out uint vk)
    {
        mods = 0; vk = 0;
        if (string.IsNullOrWhiteSpace(s)) return false;
        var parts = s.Split('+');
        for (int i = 0; i < parts.Length; i++)
        {
            var p = parts[i].Trim();
            if (p.Length == 0) return false;
            if (i < parts.Length - 1)
            {
                switch (p.ToUpperInvariant())
                {
                    case "CTRL": case "CONTROL": mods |= MOD_CONTROL; break;
                    case "ALT": case "MENU": mods |= MOD_ALT; break;
                    case "SHIFT": mods |= MOD_SHIFT; break;
                    case "WIN": case "WINDOWS": mods |= MOD_WIN; break;
                    default: return false;
                }
            }
            else
            {
                vk = KeyToVk(p);
                if (vk == 0) return false;
            }
        }
        return vk != 0;
    }

    /// <summary>(key + 修饰键状态) → "Ctrl+Shift+T"。</summary>
    public static string Format(VirtualKey key, bool ctrl, bool alt, bool shift, bool win)
    {
        var sb = new System.Text.StringBuilder();
        if (ctrl) sb.Append("Ctrl+");
        if (alt) sb.Append("Alt+");
        if (shift) sb.Append("Shift+");
        if (win) sb.Append("Win+");
        sb.Append(VkToLabel(key));
        return sb.ToString();
    }

    /// <summary>是否为单独的修饰键（录制时单独按修饰键忽略）。</summary>
    public static bool IsModifier(VirtualKey key) =>
        key == VirtualKey.Control || key == VirtualKey.LeftControl || key == VirtualKey.RightControl ||
        key == VirtualKey.Menu || key == VirtualKey.LeftMenu || key == VirtualKey.RightMenu ||
        key == VirtualKey.Shift || key == VirtualKey.LeftShift || key == VirtualKey.RightShift ||
        key == VirtualKey.LeftWindows || key == VirtualKey.RightWindows;

    private static uint KeyToVk(string s)
    {
        if (s.Length == 1)
        {
            char c = s.ToUpperInvariant()[0];
            if (c >= 'A' && c <= 'Z') return (uint)c;
            if (c >= '0' && c <= '9') return (uint)c;
        }
        if (s.Length > 1 && (s[0] == 'F' || s[0] == 'f'))
        {
            if (int.TryParse(s.AsSpan(1), out int n) && n >= 1 && n <= 24)
                return (uint)(0x6F + n);
        }
        // 非字母数字键：与 VkToLabel 的 key.ToString() 输出保持对称，
        // 否则录制 Space/Enter 等键存入设置后 TryParse 失败、热键静默失效。
        return s.ToUpperInvariant() switch
        {
            "SPACE" => 0x20,
            "ENTER" or "RETURN" => 0x0D,
            "TAB" => 0x09,
            "ESCAPE" or "ESC" => 0x1B,
            "LEFT" => 0x25,
            "UP" => 0x26,
            "RIGHT" => 0x27,
            "DOWN" => 0x28,
            "INSERT" => 0x2D,
            "DELETE" or "DEL" => 0x2E,
            "BACK" or "BACKSPACE" => 0x08,
            "HOME" => 0x24,
            "END" => 0x23,
            "PAGEUP" => 0x21,
            "PAGEDOWN" => 0x22,
            "CAPSLOCK" => 0x14,
            "NUMLOCK" => 0x90,
            "SCROLL" => 0x91,
            "PAUSE" or "PAUSEBREAK" => 0x13,
            "PRINT" or "PRINTSCREEN" => 0x2C,
            _ => 0,
        };
    }

    private static string VkToLabel(VirtualKey key)
    {
        int v = (int)key;
        if (v >= 0x30 && v <= 0x39) return ((char)v).ToString();
        if (v >= 0x41 && v <= 0x5A) return ((char)v).ToString();
        if (v >= 0x70 && v <= 0x87) return "F" + (v - 0x6F);
        return key.ToString();
    }
}
