namespace HeartRate.Models;

/// <summary>本地缓存的已发现 BLE 设备（以 MAC 地址为唯一标识）。</summary>
public sealed record CachedDevice(
    ulong Address,
    string Name,
    bool HasHeartRateService,
    DateTime LastSeen);
