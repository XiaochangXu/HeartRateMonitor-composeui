package com.github.heartratemonitor_compose.service.server

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException

class WebSocketServerManager(
    private val port: Int,
    private val authToken: String,
    private val stateFlow: SharedFlow<String>
) {
    private var server: AppWebSocketServer? = null

    fun start() {
        if (server == null) {
            try {
                server = AppWebSocketServer()
                server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                Log.d("WebSocketServerManager", "WebSocket Server started on port $port")
            } catch (e: Exception) {
                Log.e("WebSocketServerManager", "WebSocket Server start failed", e)
            }
        }
    }

    fun stop() {
        server?.stop()
        server = null
        Log.d("WebSocketServerManager", "WebSocket Server stopped")
    }

    private inner class AppWebSocketServer : NanoWSD(port) {

        // 跟踪所有活跃连接的 scope，确保 stop() 时全部取消，防止泄漏
        private val activeScopes = java.util.Collections.synchronizedSet(mutableSetOf<CoroutineScope>())

        override fun serve(session: IHTTPSession?): Response {
            // 鉴权：若配置了 token，则校验 ?token= 查询参数
            if (authToken.isNotEmpty()) {
                val queryToken = session?.parameters?.get("token")?.firstOrNull()
                if (queryToken != authToken) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
                }
            }
            return super.serve(session)
        }

        override fun openWebSocket(handshake: IHTTPSession): WebSocket {
            return AppWebSocket(handshake)
        }

        override fun stop() {
            // 先取消所有活跃连接的 scope，再停止服务器
            synchronized(activeScopes) {
                activeScopes.forEach { it.cancel() }
                activeScopes.clear()
            }
            super.stop()
        }

        inner class AppWebSocket(handshakeRequest: IHTTPSession) : WebSocket(handshakeRequest) {
            private val webSocketScope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also {
                activeScopes.add(it)
            }

            override fun onOpen() {
                Log.d("AppWebSocket", "WebSocket opened for: ${handshakeRequest.remoteIpAddress}")

                // Coroutine for handling heartbeats (Ping/Pong)
                webSocketScope.launch {
                    try {
                        while (isOpen) {
                            delay(4000)
                            ping(byteArrayOf())
                        }
                    } catch (e: CancellationException) {
                        // This is expected when the scope is cancelled
                    } catch (e: IOException) {
                        Log.e("AppWebSocket", "Error sending ping, closing connection.", e)
                        close(CloseCode.GoingAway, "Ping failed", false)
                    }
                }

                // Coroutine for listening to state updates and sending them to the client
                webSocketScope.launch {
                    stateFlow.collect { stateJson ->
                        try {
                            send(stateJson)
                        } catch (e: IOException) {
                            Log.e("AppWebSocket", "Failed to send state update, closing connection.", e)
                            close(CloseCode.GoingAway, "Send failed", false)
                        }
                    }
                }
            }

            override fun onClose(code: CloseCode?, reason: String?, initiatedByRemote: Boolean) {
                webSocketScope.cancel()
                activeScopes.remove(webSocketScope)
                Log.d("AppWebSocket", "WebSocket closed. Reason: $reason, Remote: $initiatedByRemote")
            }

            override fun onMessage(message: WebSocketFrame) {
                // Not used
            }

            override fun onPong(pong: WebSocketFrame?) {
                // Pong received, connection is alive
            }

            override fun onException(exception: IOException) {
                webSocketScope.cancel()
                activeScopes.remove(webSocketScope)
                Log.e("AppWebSocket", "WebSocket exception", exception)
            }
        }
    }
}