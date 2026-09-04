package com.github.heartratemonitor_compose.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class IpAddressProvider @Inject constructor(@ApplicationContext context: Context) {

    private val applicationContext = context.applicationContext

    fun getLocalIpAddress(): String? {
        return try {
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null

            // 第一优先级：Wi-Fi / 以太网接口（非 VPN）
            val preferredIp = scanAllNetworksForIp(cm) { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps != null &&
                    (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
                    !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
            if (preferredIp != null) return preferredIp

            // 第二优先级：activeNetwork（兼容移动热点、仅蜂窝等场景）
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                val lp = cm.getLinkProperties(activeNetwork)
                extractInet4FromLinkProperties(lp)?.let { return it }
            }

            // 第三优先级：任何非 VPN 网络的 Inet4 地址
            scanAllNetworksForIp(cm) { network ->
                val caps = cm.getNetworkCapabilities(network)
                caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    // ⚠️ 反直觉设计：allNetworks 无替代 API，是获取全部网络的唯一方式。
    private fun scanAllNetworksForIp(
        cm: ConnectivityManager,
        filter: (Network) -> Boolean
    ): String? {
        for (network in cm.allNetworks) {
            if (!filter(network)) continue
            val lp = cm.getLinkProperties(network) ?: continue
            val ip = extractInet4FromLinkProperties(lp)
            if (ip != null) return ip
        }
        return null
    }

    private fun extractInet4FromLinkProperties(lp: LinkProperties?): String? {
        return lp?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address }
            ?.address?.hostAddress
    }
}
