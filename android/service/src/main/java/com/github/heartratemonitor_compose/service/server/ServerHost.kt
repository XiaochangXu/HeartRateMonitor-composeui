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
 * 端口/token 变更检测、服务器启停逻辑。[BleService] 只需在设置变化时调用 [update]，
 * 实际运行状态经 [LanTransferSharedState.serverRuntimeStatus] 下行至 UI。
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
     * ⚠️ 反直觉设计：ReentrantLock 防止 BleSettingsListener merge flow 并发调用 update 时
     * 双 update 同时操作 httpServerManager 导致「实际已启动但 UI 显示失败」状态错乱。
     */
    private val updateLock = ReentrantLock()

    fun update() {
        updateLock.withLock {
            updateHttpServerState()
            updateWebSocketServerState()
        }
    }

    fun emitState(stateJson: String) {
        webSocketStateFlow.tryEmit(stateJson)
    }

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

    private fun setHttpRunning(running: Boolean?) {
        serverRuntimeStatus.value = serverRuntimeStatus.value.copy(httpRunning = running)
    }

    private fun setWsRunning(running: Boolean?) {
        serverRuntimeStatus.value = serverRuntimeStatus.value.copy(wsRunning = running)
    }
}
