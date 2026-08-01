using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace HeartRate.Services;

/// <summary>
/// 局域网 IPv4 地址探测：用于在「网络传输」页展示访问 URL（http://ip:port / ws://ip:port）。
/// 过滤回环、APIPA（169.254）与虚拟网卡常见段，返回可被同局域网设备访问的地址。
/// </summary>
public static class NetworkIPHelper
{
    public static IReadOnlyList<IPAddress> GetLanIPv4Addresses()
    {
        var result = new List<IPAddress>();
        try
        {
            foreach (var nic in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (nic.OperationalStatus != OperationalStatus.Up) continue;
                if (nic.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;

                foreach (var addr in nic.GetIPProperties().UnicastAddresses)
                {
                    var ip = addr.Address;
                    if (ip.AddressFamily != AddressFamily.InterNetwork) continue;
                    if (IPAddress.IsLoopback(ip)) continue;
                    if (IsApiPA(ip)) continue; // 自动配置地址，无网关时不可用
                    result.Add(ip);
                }
            }
        }
        catch
        {
            // 取地址失败不阻塞 UI
        }
        return result;
    }

    private static bool IsApiPA(IPAddress ip)
    {
        var bytes = ip.GetAddressBytes();
        return bytes.Length >= 2 && bytes[0] == 169 && bytes[1] == 254;
    }
}
