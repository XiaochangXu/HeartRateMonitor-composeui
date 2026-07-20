package com.github.heartratemonitor_compose.data.network

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

/**
 * 本机 IPv4 地址提供者。
 *
 * 将 [ConnectivityManager] 访问从 UI 层（[ServerScreen]）下沉到数据层，
 * 避免 Composable 直接操作系统服务，同时统一处理 SecurityException 等异常。
 */
class IpAddressProvider(context: Context) {

    private val applicationContext = context.applicationContext

    /**
     * 获取当前连接网络的 IPv4 地址；未连接或异常时返回 null。
     */
    fun getLocalIpAddress(): String? {
        return try {
            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val linkProperties = cm?.getLinkProperties(network)
            linkProperties?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address }
                ?.address?.hostAddress
        } catch (_: SecurityException) {
            null
        }
    }
}
