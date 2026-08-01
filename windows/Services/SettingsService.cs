using System.Text.Json;
using HeartRate.Models;

namespace HeartRate.Services;

/// <summary>
/// 设置持久化：JSON 文件位于 %LOCALAPPDATA%\HeartRate\settings.json。
/// Save() 防抖 300ms（拖动滑块时避免频繁写盘）；SaveNow()/Flush() 立即写。
/// Changed 事件在每次保存请求时同步触发（实时应用到悬浮窗）。
/// </summary>
public static class SettingsService
{
    private static readonly Lazy<FloatWindowSettings> _settings = new(Load);
    private static readonly System.Threading.Timer _debounceTimer =
        new(_ => WriteToDisk(), null, Timeout.Infinite, Timeout.Infinite);
    private static bool _dirty;

    // WebhookTrigger 等枚举以 snake_case 字符串持久化（与示例项目一致）
    private static readonly JsonSerializerOptions _jsonOptions = new()
    {
        WriteIndented = true,
        Converters = { new System.Text.Json.Serialization.JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower) },
    };

    public static FloatWindowSettings Current => _settings.Value;

    public static event Action? Changed;

    private static string FilePath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "HeartRate", "settings.json");

    /// <summary>防抖保存（界面上的连续调整）。</summary>
    public static void Save()
    {
        _dirty = true;
        Changed?.Invoke();
        _debounceTimer.Change(300, Timeout.Infinite);
    }

    /// <summary>立即保存（位置等关键时机）。</summary>
    public static void SaveNow()
    {
        _dirty = true;
        Changed?.Invoke();
        WriteToDisk();
    }

    /// <summary>
    /// 仅把挂起设置落盘，不触发 Changed 事件。
    /// 用于悬浮窗位置保存：位置变化不影响内容绘制，若触发 Changed 会让
    /// FloatWindow 重绘，而重绘发生在鼠标消息处理中易与 DWM 合成冲突，
    /// 导致 surface 偶发被清成透明（悬浮窗“消失”）。位置只需持久化即可。
    /// </summary>
    public static void SaveWithoutNotify()
    {
        _dirty = true;
        WriteToDisk();
    }

    /// <summary>应用退出前把挂起的防抖写入落盘。</summary>
    public static void Flush() => WriteToDisk();

    /// <summary>
    /// 端口冲突检测：刚开启的服务（svcId: "http"/"ws"/"lan"）端口是否与
    /// 其他<b>已启用</b>服务端口相同。调用时机：ToggleSwitch.Toggled 且 IsOn=true。
    /// </summary>
    public static bool PortConflict(string svcId)
    {
        var net = Current.Network;
        var lan = Current.LanTransfer;
        int myPort = svcId switch
        {
            "http" => net.HttpServerPort,
            "ws"   => net.WebSocketServerPort,
            "lan"  => lan.PairPort,
            _      => 0
        };
        if (svcId != "http" && net.HttpServerEnabled && net.HttpServerPort == myPort) return true;
        if (svcId != "ws" && net.WebSocketServerEnabled && net.WebSocketServerPort == myPort) return true;
        if (svcId != "lan" && lan.Enabled && lan.PairPort == myPort) return true;
        return false;
    }

    /// <summary>指定端口是否已被其他<b>已启用</b>服务占用（不含局域网传输自身）。
    /// 供「自动换端口」挑选空闲端口时排除内置服务的端口。</summary>
    public static bool PortUsedByOtherService(int port)
    {
        var net = Current.Network;
        if (net.HttpServerEnabled && net.HttpServerPort == port) return true;
        if (net.WebSocketServerEnabled && net.WebSocketServerPort == port) return true;
        return false;
    }

    private static void WriteToDisk()
    {
        try
        {
            if (!_dirty) return;
            var dir = Path.GetDirectoryName(FilePath)!;
            Directory.CreateDirectory(dir);
            var json = JsonSerializer.Serialize(Current, _jsonOptions);
            File.WriteAllText(FilePath, json);
            _dirty = false;
        }
        catch
        {
            // 设置写盘失败不阻塞应用
        }
    }

    private static FloatWindowSettings Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<FloatWindowSettings>(File.ReadAllText(FilePath), _jsonOptions) ?? new FloatWindowSettings();
        }
        catch
        {
            // 损坏的配置文件回退默认值
        }
        return new FloatWindowSettings();
    }
}
