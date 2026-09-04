package com.github.heartratemonitor_compose.service.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * 电脑端（C#+WinUI3）需以相同 serviceType 注册服务，TXT 记录至少包含 name、pair_port。
 */
object LanTransferProtocol {
    const val NSD_SERVICE_TYPE = "_heartrate._tcp."

    const val PAIR_PATH = "/pair-request"

    const val TXT_KEY_NAME = "name"

    const val TXT_KEY_PAIR_PORT = "pair_port"
}

/**
 * 通过 [NsdManager] 扫描局域网由电脑端广播的 `_heartrate._tcp.` 服务。
 * Flow 取消时自动停止 NSD 发现；部分 ROM 上发现回调不一定立即触发。
 */
// open + discover 可重写：供 LanTransferViewModel 单测以 Fake 替换
open class NsdDiscoverer @Inject constructor(@ApplicationContext private val context: Context) {

    data class DiscoveredPc(
        val name: String,
        val host: String,
        val pairPort: Int,
        val serviceName: String
    )

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    open fun discover(): Flow<List<DiscoveredPc>> = callbackFlow {
        val manager = nsdManager
        if (manager == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }

        val found = linkedMapOf<String, DiscoveredPc>()
        val pendingRetries = mutableMapOf<String, Int>()
        val retryHandler = Handler(Looper.getMainLooper())

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
                if (info.serviceType != LanTransferProtocol.NSD_SERVICE_TYPE) return
                resolveServiceWithRetry(manager, info, MAX_RESOLVE_RETRIES, found, pendingRetries, retryHandler) { pc ->
                    if (pc != null) {
                        found[pc.serviceName] = pc
                        trySend(found.values.toList())
                    }
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                found.remove(info.serviceName)
                pendingRetries.remove(info.serviceName)
                trySend(found.values.toList())
            }
        }

        manager.discoverServices(LanTransferProtocol.NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            retryHandler.removeCallbacksAndMessages(null)
            try {
                manager.stopServiceDiscovery(listener)
            } catch (_: Exception) { /* 取消时已停止则忽略 */ }
        }
    }

    private fun resolveService(
        manager: NsdManager,
        info: NsdServiceInfo,
        onResult: (DiscoveredPc?) -> Unit
    ) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                // Android 12+ 推荐 hostAddresses，旧版本用 host
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
                Log.w("NsdDiscoverer", "resolve failed: $errorCode for ${info.serviceName}")
                onResult(null)
            }
        }
        try {
            @Suppress("DEPRECATION")
            manager.resolveService(info, resolveListener)
        } catch (e: Exception) {
            // ⚠️ 反直觉设计：Android 11+ 同一时刻只允许一个 resolve，并发时抛 IllegalStateException
            Log.w("NsdDiscoverer", "resolveService exception: ${e.message}")
            onResult(null)
        }
    }

    /**
     * ⚠️ 反直觉设计：Android 11+ 同一时刻只允许一个 resolve，并发 IllegalStateException 是瞬态错误——
     * 带重试，延迟后重试通常能成功。直接吞掉异常会导致设备永久从列表缺失。
     */
    private fun resolveServiceWithRetry(
        manager: NsdManager,
        info: NsdServiceInfo,
        maxRetries: Int,
        found: MutableMap<String, DiscoveredPc>,
        pendingRetries: MutableMap<String, Int>,
        retryHandler: Handler,
        onResult: (DiscoveredPc?) -> Unit
    ) {
        resolveService(manager, info) { pc ->
            if (pc != null) {
                pendingRetries.remove(info.serviceName)
                onResult(pc)
            } else {
                val remaining = pendingRetries.getOrPut(info.serviceName) { maxRetries } - 1
                if (remaining >= 0) {
                    pendingRetries[info.serviceName] = remaining
                    Log.d("NsdDiscoverer", "resolve retry for ${info.serviceName}, remaining=$remaining")
                    retryHandler.postDelayed({
                        if (!found.containsKey(info.serviceName)) {
                            resolveServiceWithRetry(manager, info, 0, found, pendingRetries, retryHandler, onResult)
                        }
                    }, RESOLVE_RETRY_DELAY_MS)
                } else {
                    pendingRetries.remove(info.serviceName)
                    onResult(null)
                }
            }
        }
    }

    companion object {
        private const val MAX_RESOLVE_RETRIES = 3
        private const val RESOLVE_RETRY_DELAY_MS = 1000L
    }
}
