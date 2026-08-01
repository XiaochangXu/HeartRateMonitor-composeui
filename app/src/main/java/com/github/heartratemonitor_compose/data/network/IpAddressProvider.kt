package com.github.heartratemonitor_compose.data.network

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address


class IpAddressProvider(context: Context) {

    private val applicationContext = context.applicationContext

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
