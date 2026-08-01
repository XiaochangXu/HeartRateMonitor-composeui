using HeartRate.Helpers;

namespace HeartRate.Models;

/// <summary>扫描发现的 BLE 设备信息。</summary>
public sealed class BleDeviceInfo
{
    public required ulong Address { get; init; }
    public required string Name { get; init; }
    public required bool HasHeartRateService { get; init; }

    public string DisplayName => string.IsNullOrEmpty(Name) ? L.DeviceList_UnknownDevice : Name;
    public string AddressText => Address.ToString("X12");
}
