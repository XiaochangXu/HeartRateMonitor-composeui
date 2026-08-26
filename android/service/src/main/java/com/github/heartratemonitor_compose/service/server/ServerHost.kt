package com.github.heartratemonitor_compose.service.server

import android.content.Context
import android.util.Log
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.service.LanTransferSharedState
import com.github.heartratemonitor_compose.service.ServerRuntimeStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 把端口/token 变更检测、服务器启停逻辑从 [BleService] 中拆出，
 * [BleService] 只需在设置变化时调用 [update]，并依赖 [emitState] 向 WebSocket 客户端广播状态。
 *
 * 服务器实际运行状态（端口冲突等导致启动失败）经 [LanTransferSharedState.serverRuntimeStatus]
 * 下行至 UI，使设置页能区分「用户已启用」与「服务器实际在运行」。
 */
class ServerHost(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val heartRate: StateFlow<Int>,
    private val speed: StateFlow<Float>,
    private val isDeviceConnected: () -> Boolean,
    private val getStatusMessage: () -> String,
    private val webSocketClientCount: MutableStateFlow<Int>,
    private val serverRuntimeStatus: MutableStateFlow<ServerRuntimeStatus>,
    private val connectedClientInfo: MutableStateFlow<com.github.heartratemonitor_compose.service.ConnectedClientInfo?> = MutableStateFlow(null)
) {

    private var httpServerManager: HttpServerManager? = null
    private var webSocketServerManager: WebSocketServerManager? = null

    private var currentHttpPort: Int = -1
    private var currentWebSocketPort: Int = -1
    private var currentHttpAuthToken: String = ""
    private var currentWebSocketAuthToken: String = ""

    private val webSocketStateFlow = MutableSharedFlow<String>(replay = 1)

    /**
     * 互斥锁：BleSettingsListener 的 merge flow 每个上游变化都会触发一次 [update]，
     * SetHttpEnabled(true) 写 ENABLED+PORT 两个 key 即触发两次 launch。
     * 在 Dispatchers.IO（多线程）上并发执行会导致两个 update 同时操作
     * httpServerManager 等非线程安全字段——例如两个都读到 httpServerManager == null，
     * 各自创建新实例并 start()，第二个因端口被占而失败，覆盖第一个的结果，
     * 造成「服务器实际已启动但 UI 显示启动失败」的状态错乱。
     */
    private val updateLock = ReentrantLock()

    /**
     * 根据当前设置同步 HTTP 与 WebSocket 服务器状态。
     */
    fun update() {
        updateLock.withLock {
            updateHttpServerState()
            updateWebSocketServerState()
        }
    }

    /**
     * 广播一条 JSON 状态给所有已连接的 WebSocket 客户端。
     */
    fun emitState(stateJson: String) {
        webSocketStateFlow.tryEmit(stateJson)
    }

    /**
     * 服务销毁时停止所有服务器。
     */
    fun stop() {
        updateLock.withLock {
            httpServerManager?.stop()
            webSocketServerManager?.stop()
            httpServerManager = null
            webSocketServerManager = null
            currentHttpPort = -1
            currentWebSocketPort = -1
            currentHttpAuthToken = ""
            currentWebSocketAuthToken = ""
            serverRuntimeStatus.value = ServerRuntimeStatus()
        }
    }

    /**
     * 主动断开所有已连接的 WebSocket 客户端（PC），用于局域网传输「断开连接」。
     */
    fun disconnectAllWebSocketClients() {
        updateLock.withLock {
            webSocketServerManager?.disconnectAllClients()
        }
    }

    private fun updateHttpServerState() {
        val isEnabled = settingsRepository.get(SettingsKeys.HTTP_SERVER_ENABLED)
        val authToken = settingsRepository.get(SettingsKeys.SERVER_ACCESS_TOKEN)

        if (isEnabled) {
            val port = settingsRepository.get(SettingsKeys.HTTP_SERVER_PORT)
            if (httpServerManager == null || currentHttpPort != port || currentHttpAuthToken != authToken) {
                httpServerManager?.stop()
                // 先标记为「启动中」(null)，避免 UI 在启动完成前闪烁「启动失败」
                setHttpRunning(null)
                httpServerManager = HttpServerManager(
                    context = context,
                    port = port,
                    authToken = authToken,
                    heartRateFlow = heartRate,
                    speedFlow = speed,
                    isDeviceConnected = isDeviceConnected,
                    getStatusMessage = getStatusMessage,
                    wsPortProvider = { settingsRepository.get(SettingsKeys.WEBSOCKET_SERVER_PORT) },
                    wsEnabledProvider = { settingsRepository.get(SettingsKeys.WEBSOCKET_SERVER_ENABLED) }
                )
                val success = httpServerManager!!.start()
                if (success) {
                    currentHttpPort = port
                    currentHttpAuthToken = authToken
                } else {
                    // 启动失败：清理 manager 引用与端口缓存，下次 update() 会重新尝试
                    httpServerManager = null
                    currentHttpPort = -1
                    currentHttpAuthToken = ""
                }
                setHttpRunning(success)
            }
        } else {
            httpServerManager?.stop()
            httpServerManager = null
            currentHttpPort = -1
            currentHttpAuthToken = ""
            setHttpRunning(false)
        }
    }

    private fun updateWebSocketServerState() {
        val isEnabled = settingsRepository.get(SettingsKeys.WEBSOCKET_SERVER_ENABLED)
        val authToken = settingsRepository.get(SettingsKeys.SERVER_ACCESS_TOKEN)

        if (isEnabled) {
            val port = settingsRepository.get(SettingsKeys.WEBSOCKET_SERVER_PORT)
            if (webSocketServerManager == null || currentWebSocketPort != port || currentWebSocketAuthToken != authToken) {
                webSocketServerManager?.stop()
                // 先标记为「启动中」(null)，避免 UI 在启动完成前闪烁「启动失败」
                setWsRunning(null)
                webSocketServerManager = WebSocketServerManager(context, port, authToken, webSocketStateFlow, webSocketClientCount, connectedClientInfo)
                val success = webSocketServerManager!!.start()
                if (success) {
                    currentWebSocketPort = port
                    currentWebSocketAuthToken = authToken
                } else {
                    webSocketServerManager = null
                    currentWebSocketPort = -1
                    currentWebSocketAuthToken = ""
                }
                setWsRunning(success)
            }
        } else {
            webSocketServerManager?.stop()
            webSocketServerManager = null
            currentWebSocketPort = -1
            currentWebSocketAuthToken = ""
            setWsRunning(false)
        }
    }

    /**
     * 更新 HTTP 服务器运行状态，保留 WS 状态不变。
     * null = 启动中, true = 运行中, false = 启动失败
     */
    private fun setHttpRunning(running: Boolean?) {
        serverRuntimeStatus.value = serverRuntimeStatus.value.copy(httpRunning = running)
    }

    /**
     * 更新 WebSocket 服务器运行状态，保留 HTTP 状态不变。
     * null = 启动中, true = 运行中, false = 启动失败
     */
    private fun setWsRunning(running: Boolean?) {
        serverRuntimeStatus.value = serverRuntimeStatus.value.copy(wsRunning = running)
    }
}
