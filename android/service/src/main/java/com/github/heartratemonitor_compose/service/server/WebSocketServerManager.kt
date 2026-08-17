package com.github.heartratemonitor_compose.service.server

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException

class WebSocketServerManager(
    private val port: Int,
    private val authToken: String,
    private val stateFlow: SharedFlow<String>,
    private val clientCountFlow: MutableStateFlow<Int>
) {
    private var server: AppWebSocketServer? = null

    /**
     * 启动 WebSocket 服务器。
     * @return true 表示启动成功；false 表示端口被占用或其它错误导致启动失败。
     *         调用方（ServerHost）据此向 UI 传递实际运行状态，避免「设置已启用但服务器未运行」。
     */
    fun start(): Boolean {
        if (server != null) return true
        return try {
            server = AppWebSocketServer()
            server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d("WebSocketServerManager", "WebSocket Server started on port $port")
            true
        } catch (e: Exception) {
            Log.e("WebSocketServerManager", "WebSocket Server start failed", e)
            server = null
            false
        }
    }

    fun stop() {
        server?.stop()
        server = null
        clientCountFlow.value = 0
        Log.d("WebSocketServerManager", "WebSocket Server stopped")
    }

    /**
     * 主动断开所有已连接的 WebSocket 客户端（PC）。
     * 用于局域网传输页面「断开连接」按钮。
     */
    fun disconnectAllClients() {
        server?.disconnectAllClients()
    }

    private inner class AppWebSocketServer : NanoWSD(port) {

        // 跟踪所有活跃连接的 scope，确保 stop() 时全部取消，防止泄漏
        private val activeScopes = java.util.Collections.synchronizedSet(mutableSetOf<CoroutineScope>())
        // 跟踪所有活跃连接的 WebSocket 实例，用于主动断开
        private val activeSockets = java.util.Collections.synchronizedSet(mutableSetOf<AppWebSocket>())

        override fun serve(session: IHTTPSession?): Response {
            // 鉴权：若配置了 token，则校验 ?token= 查询参数
            // 使用 MessageDigest.isEqual 进行恒定时间比较，防止时序攻击（与 HttpServerManager 保持一致）
            if (authToken.isNotEmpty()) {
                val queryToken = session?.parameters?.get("token")?.firstOrNull()
                val tokenMatch = queryToken != null &&
                    java.security.MessageDigest.isEqual(queryToken.toByteArray(), authToken.toByteArray())
                if (!tokenMatch) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
                }
            }
            return super.serve(session)
        }

        override fun openWebSocket(handshake: IHTTPSession): WebSocket {
            return AppWebSocket(handshake)
        }

        fun disconnectAllClients() {
            synchronized(activeSockets) {
                activeSockets.toList().forEach { socket ->
                    try {
                        socket.close(CloseCode.GoingAway, "Disconnected by user", false)
                    } catch (e: Exception) {
                        Log.w("AppWebSocketServer", "Failed to close socket", e)
                    }
                }
            }
        }

        override fun stop() {
            // 先取消所有活跃连接的 scope，再停止服务器
            synchronized(activeScopes) {
                activeScopes.forEach { it.cancel() }
                activeScopes.clear()
            }
            synchronized(activeSockets) { activeSockets.clear() }
            super.stop()
        }

        private fun updateClientCount() {
            clientCountFlow.value = synchronized(activeSockets) { activeSockets.size }
        }

        inner class AppWebSocket(handshakeRequest: IHTTPSession) : WebSocket(handshakeRequest) {
            private val webSocketScope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also {
                activeScopes.add(it)
            }

            // 标记正在关闭中：send/ping 失败后调用 close()（异步），
            // 在 onClose 回调触发前 collect 可能再次执行 send()，此标志防止重复 send 与冗余日志
            @Volatile
            private var isClosing = false

            init {
                activeSockets.add(this)
            }

            override fun onOpen() {
                updateClientCount()
                Log.d("AppWebSocket", "WebSocket opened for: ${handshakeRequest.remoteIpAddress}")

                // Coroutine for handling heartbeats (Ping/Pong)
                webSocketScope.launch {
                    try {
                        while (isOpen && !isClosing) {
                            delay(4000)
                            ping(byteArrayOf())
                        }
                    } catch (e: CancellationException) {
                        // This is expected when the scope is cancelled
                    } catch (e: IOException) {
                        if (!isClosing) {
                            isClosing = true
                            Log.e("AppWebSocket", "Error sending ping, closing connection.", e)
                            close(CloseCode.GoingAway, "Ping failed", false)
                        }
                    }
                }

                // Coroutine for listening to state updates and sending them to the client
                webSocketScope.launch {
                    stateFlow.collect { stateJson ->
                        if (isClosing) return@collect
                        try {
                            send(stateJson)
                        } catch (e: IOException) {
                            if (!isClosing) {
                                isClosing = true
                                Log.e("AppWebSocket", "Failed to send state update, closing connection.", e)
                                close(CloseCode.GoingAway, "Send failed", false)
                            }
                        }
                    }
                }
            }

            override fun onClose(code: CloseCode?, reason: String?, initiatedByRemote: Boolean) {
                isClosing = true
                webSocketScope.cancel()
                activeScopes.remove(webSocketScope)
                activeSockets.remove(this)
                updateClientCount()
                Log.d("AppWebSocket", "WebSocket closed. Reason: $reason, Remote: $initiatedByRemote")
            }

            override fun onMessage(message: WebSocketFrame) {
                // Not used
            }

            override fun onPong(pong: WebSocketFrame?) {
                // Pong received, connection is alive
            }

            override fun onException(exception: IOException) {
                isClosing = true
                webSocketScope.cancel()
                activeScopes.remove(webSocketScope)
                activeSockets.remove(this)
                updateClientCount()
                Log.e("AppWebSocket", "WebSocket exception", exception)
            }
        }
    }
}