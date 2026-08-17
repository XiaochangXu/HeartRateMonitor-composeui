package com.github.heartratemonitor_compose.service.server

import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 把端口/token 变更检测、服务器启停逻辑从 [BleService] 中拆出，
 * [BleService] 只需在设置变化时调用 [update]，并依赖 [emitState] 向 WebSocket 客户端广播状态。
 */
class ServerHost(
    private val settingsRepository: SettingsRepository,
    private val heartRate: StateFlow<Int>,
    private val speed: StateFlow<Float>,
    private val isDeviceConnected: () -> Boolean,
    private val getStatusMessage: () -> String,
    private val webSocketClientCount: MutableStateFlow<Int>
) {

    private var httpServerManager: HttpServerManager? = null
    private var webSocketServerManager: WebSocketServerManager? = null

    private var currentHttpPort: Int = -1
    private var currentWebSocketPort: Int = -1
    private var currentHttpAuthToken: String = ""
    private var currentWebSocketAuthToken: String = ""

    private val webSocketStateFlow = MutableSharedFlow<String>(replay = 1)

    /**
     * 根据当前设置同步 HTTP 与 WebSocket 服务器状态。
     */
    fun update() {
        updateHttpServerState()
        updateWebSocketServerState()
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
        httpServerManager?.stop()
        webSocketServerManager?.stop()
    }

    /**
     * 主动断开所有已连接的 WebSocket 客户端（PC），用于局域网传输「断开连接」。
     */
    fun disconnectAllWebSocketClients() {
        webSocketServerManager?.disconnectAllClients()
    }

    private fun updateHttpServerState() {
        val isEnabled = settingsRepository.get(SettingsKeys.HTTP_SERVER_ENABLED)
        val authToken = settingsRepository.get(SettingsKeys.SERVER_ACCESS_TOKEN)

        if (isEnabled) {
            val port = settingsRepository.get(SettingsKeys.HTTP_SERVER_PORT)
            if (httpServerManager == null || currentHttpPort != port || currentHttpAuthToken != authToken) {
                httpServerManager?.stop()
                httpServerManager = HttpServerManager(
                    port = port,
                    authToken = authToken,
                    heartRateFlow = heartRate,
                    speedFlow = speed,
                    isDeviceConnected = isDeviceConnected,
                    getStatusMessage = getStatusMessage
                )
                httpServerManager?.start()
                currentHttpPort = port
                currentHttpAuthToken = authToken
            }
        } else {
            httpServerManager?.stop()
            httpServerManager = null
            currentHttpPort = -1
            currentHttpAuthToken = ""
        }
    }

    private fun updateWebSocketServerState() {
        val isEnabled = settingsRepository.get(SettingsKeys.WEBSOCKET_SERVER_ENABLED)
        val authToken = settingsRepository.get(SettingsKeys.SERVER_ACCESS_TOKEN)

        if (isEnabled) {
            val port = settingsRepository.get(SettingsKeys.WEBSOCKET_SERVER_PORT)
            if (webSocketServerManager == null || currentWebSocketPort != port || currentWebSocketAuthToken != authToken) {
                webSocketServerManager?.stop()
                webSocketServerManager = WebSocketServerManager(port, authToken, webSocketStateFlow, webSocketClientCount)
                webSocketServerManager?.start()
                currentWebSocketPort = port
                currentWebSocketAuthToken = authToken
            }
        } else {
            webSocketServerManager?.stop()
            webSocketServerManager = null
            currentWebSocketPort = -1
            currentWebSocketAuthToken = ""
        }
    }
}
