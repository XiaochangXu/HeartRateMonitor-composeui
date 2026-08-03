using System.Text.Json;
using HeartRate.Models;

namespace HeartRate.Services;

/// <summary>
/// 已发现 BLE 设备的本地缓存：%LOCALAPPDATA%\HeartRate\devices.json。
/// 以 MAC 地址为 key（同名不同设备/改名都按地址区分）；名称仅作展示，
/// 扫描到新数据即覆盖。Save() 防抖 500ms，Flush() 立即写盘。
/// </summary>
public static class DeviceCacheService
{
    private static readonly object _sync = new();
    private static List<CachedDevice> _pending = new();
    private static readonly System.Threading.Timer _saveTimer =
        new(_ => Flush(), null, Timeout.Infinite, Timeout.Infinite);

    private static string FilePath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "HeartRate", "devices.json");

    public static Dictionary<ulong, CachedDevice> Load()
    {
        try
        {
            if (!File.Exists(FilePath)) return new();
            var list = JsonSerializer.Deserialize<List<CachedDevice>>(File.ReadAllText(FilePath));
            if (list is null) return new();
            // 同一地址多条（历史残留）时取最近一次
            return list.Where(d => d.Address != 0)
                       .GroupBy(d => d.Address)
                       .ToDictionary(g => g.Key, g => g.OrderByDescending(x => x.LastSeen).First());
        }
        catch
        {
            // 损坏的缓存文件回退为空
            return new();
        }
    }

    /// <summary>记录待写缓存（防抖 500ms 合并写入）。</summary>
    public static void Save(IEnumerable<CachedDevice> devices)
    {
        lock (_sync)
        {
            _pending = devices.ToList();
            _saveTimer.Change(500, Timeout.Infinite);
        }
    }

    /// <summary>立即把挂起的缓存写入磁盘（窗口关闭/退出前调用）。</summary>
    public static void Flush()
    {
        List<CachedDevice> snapshot;
        lock (_sync)
        {
            snapshot = _pending;
            _pending = new();
        }
        if (snapshot.Count == 0) return;
        try
        {
            var dir = Path.GetDirectoryName(FilePath)!;
            Directory.CreateDirectory(dir);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(snapshot, new JsonSerializerOptions { WriteIndented = true }));
        }
        catch
        {
            // 写盘失败不阻塞应用
        }
    }
}
