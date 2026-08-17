using HeartRate.Helpers;

namespace HeartRate.Models;

/// <summary>扫描发现的 BLE 设备信息。</summary>
public sealed class BleDeviceInfo
{
    public required ulong Address { get; init; }
    public required string Name { get; init; }
    public required bool HasHeartRateService { get; init; }

    /// <summary>信号强度 dBm（负数，越大越强）；int.MinValue 表示信号不可用。</summary>
    public required int Rssi { get; init; }

    /// <summary>缓存占位项：本次会话尚未被扫描到，仅来自历史缓存。</summary>
    public bool IsCachedOnly { get; init; }

    public string DisplayName => string.IsNullOrEmpty(Name) ? L.DeviceList_UnknownDevice : Name;
    public string AddressText => Address.ToString("X12");

    /// <summary>信号强度显示文本，不可用时显示 "--"。</summary>
    public string RssiText => Rssi == int.MinValue ? "--" : Rssi.ToString();
}
