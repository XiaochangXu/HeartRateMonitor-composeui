package com.github.heartratemonitor_compose.service.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 局域网传输：mDNS / NSD 服务类型。
 *
 * 电脑端（C#+WinUI3）需以相同 serviceType 注册服务，TXT 记录至少包含：
 * - `name`：电脑显示名（如 "DESKTOP-ABC"）
 * - `pair_port`：配对 HTTP 端口（int 字符串）
 *
 * 手机端通过 [NsdDiscoverer] 扫描该服务类型，发现电脑后通过 HTTP 发起配对。
 */
object LanTransferProtocol {
    const val NSD_SERVICE_TYPE = "_heartrate._tcp."

    /** 配对请求的 HTTP 路径（电脑端 PairingServer 监听） */
    const val PAIR_PATH = "/pair-request"

    /** TXT 记录 key：电脑显示名 */
    const val TXT_KEY_NAME = "name"

    /** TXT 记录 key：电脑配对 HTTP 端口 */
    const val TXT_KEY_PAIR_PORT = "pair_port"
}

/**
 * 通过 [NsdManager] 扫描局域网内由电脑端广播的 `_heartrate._tcp.` 服务。
 *
 * 使用 [discover] 返回的 Flow 持续接收已发现的电脑设备列表（自动去重，按 name+host 聚合）。
 * Flow 被取消时自动停止 NSD 发现，无需手动管理生命周期。
 */
class NsdDiscoverer(private val context: Context) {

    /** 一台已发现的电脑设备 */
    data class DiscoveredPc(
        val name: String,
        val host: String,
        val pairPort: Int,
        /** 服务名（含 mDNS 后缀），用于去重 */
        val serviceName: String
    )

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    /**
     * 启动扫描。返回一个 Flow，每次发现/丢失设备时都会发出最新列表。
     *
     * 注意：NsdManager 在部分 ROM 上发现回调不一定立即触发，调用方应同时给用户显示
     * 「扫描中」状态，并在合适时机取消 Flow 以停止扫描。
     */
    fun discover(): Flow<List<DiscoveredPc>> = callbackFlow {
        val manager = nsdManager
        if (manager == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        // 已发现的设备，按 serviceName 去重
        val found = linkedMapOf<String, DiscoveredPc>()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w("NsdDiscoverer", "start discovery failed: $errorCode")
                channel.close()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w("NsdDiscoverer", "stop discovery failed: $errorCode")
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                // 只解析目标类型，忽略其它服务
                if (info.serviceType != LanTransferProtocol.NSD_SERVICE_TYPE) return
                resolveService(manager, info) { pc ->
                    if (pc != null) {
                        found[pc.serviceName] = pc
                        trySend(found.values.toList())
                    }
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                trySend(found.values.toList())
            }
        }

        manager.discoverServices(LanTransferProtocol.NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            try {
                manager.stopServiceDiscovery(listener)
            } catch (_: Exception) { /* 取消时已停止则忽略 */ }
        }
    }

    /**
     * 解析已发现的服务，获取 host + port + TXT 记录。
     * 失败时回调 null（部分 ROM 上 resolve 会偶尔失败，调用方可重试）。
     */
    private fun resolveService(
        manager: NsdManager,
        info: NsdServiceInfo,
        onResult: (DiscoveredPc?) -> Unit
    ) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                // Android 12 (API 31)+ 推荐 hostAddresses；旧版本用 host
                val host = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    serviceInfo.hostAddresses.firstOrNull()?.hostAddress
                } else {
                    @Suppress("DEPRECATION")
                    serviceInfo.host?.hostAddress
                }
                if (host == null) {
                    onResult(null)
                    return
                }
                val attrs = serviceInfo.attributes
                val name = attrs[LanTransferProtocol.TXT_KEY_NAME]
                    ?.let { String(it) }
                    ?: serviceInfo.serviceName
                val pairPort = attrs[LanTransferProtocol.TXT_KEY_PAIR_PORT]
                    ?.let { String(it).toIntOrNull() }
                    ?: serviceInfo.port

                if (pairPort <= 0) {
                    onResult(null)
                    return
                }
                onResult(
                    DiscoveredPc(
                        name = name,
                        host = host,
                        pairPort = pairPort,
                        serviceName = serviceInfo.serviceName
                    )
                )
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w("NsdDiscoverer", "resolve failed: $errorCode for ${serviceInfo.serviceName}")
                onResult(null)
            }
        }
        try {
            @Suppress("DEPRECATION")
            manager.resolveService(info, resolveListener)
        } catch (e: Exception) {
            // Android 11+ 上同一时刻只能 resolve 一个服务，并发 resolve 会抛 IllegalStateException
            // 这里简单吞掉，让上层通过持续扫描自然重试
            Log.w("NsdDiscoverer", "resolveService exception: ${e.message}")
            onResult(null)
        }
    }
}
