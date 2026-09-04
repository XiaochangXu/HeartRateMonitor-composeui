package com.github.heartratemonitor_compose.service.server

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException

class WebSocketServerManager(
    private val context: Context,
    private val port: Int,
    private val authToken: String,
    private val stateFlow: SharedFlow<String>,
    private val clientCountFlow: MutableStateFlow<Int>,
    private val connectedClientInfoFlow: MutableStateFlow<com.github.heartratemonitor_compose.service.ConnectedClientInfo?> = MutableStateFlow(null)
) {
    private var server: AppWebSocketServer? = null

    /**
     * @return true 表示启动成功；false 表示端口被占用等错误，调用方据此向 UI 传递实际运行状态。
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
        connectedClientInfoFlow.value = null
        Log.d("WebSocketServerManager", "WebSocket Server stopped")
    }

    /**
     * 主动断开所有已连接的 WebSocket 客户端（PC），用于「断开连接」按钮。
     */
    fun disconnectAllClients() {
        server?.disconnectAllClients()
    }

    @Volatile
    private var cachedObsTemplate: String? = null

    /**
     * 读取 assets/obs_heartrate.html 注入 WS 连接信息；WS 服务器自身提供 WS 服务，wsUrl 直接指向同端口。
     */
    private fun getObsHtml(host: String): String {
        val template = cachedObsTemplate ?: run {
            val html = try {
                context.assets.open("obs_heartrate.html").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e("WebSocketServerManager", "Failed to read obs_heartrate.html from assets", e)
                "<html><body><h1>Heart Rate Monitor</h1><p>HTML file not found.</p></body></html>"
            }
            cachedObsTemplate = html
            html
        }
        // ⚠️ 反直觉设计：escape 防 XSS（host 来自客户端可控）
        val wsUrl = if (host.isNotEmpty()) "ws://$host:$port" else ""
        val safeWsUrl = escapeForJs(wsUrl)
        val safeToken = escapeForJs(authToken)
        val versionStamp = System.currentTimeMillis()
        val injection = "<script>window.__WS_CONFIG__={wsUrl:\"$safeWsUrl\",token:\"$safeToken\"};" +
            "if(!new URLSearchParams(location.search).has('_v')){var p=new URLSearchParams(location.search);p.set('_v','$versionStamp');" +
            "location.replace(location.pathname+(p.toString()?'?'+p.toString():''));}" +
            "</script>"
        val parts = template.split("<script>", limit = 2)
        return if (parts.size == 2) {
            parts[0] + injection + "\n<script>" + parts[1]
        } else {
            template + injection
        }
    }

    private fun escapeForJs(value: String): String {
        return value
            .replace("</", "<\\/")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
    }

    private inner class AppWebSocketServer : NanoWSD(port) {

        private val activeScopes = java.util.Collections.synchronizedSet(mutableSetOf<CoroutineScope>())
        private val activeSockets = java.util.Collections.synchronizedSet(mutableSetOf<AppWebSocket>())

        /**
         * ⚠️ 反直觉设计：重写 serve()（非 serveHttp）统一拦截鉴权——
         * NanoWSD.serve() 中 WebSocket 升级走 openWebSocket() 不经 serveHttp()，若只重写 serveHttp 将绕过 token。
         */
        override fun serve(session: IHTTPSession?): Response {
            if (!isAuthorized(session)) {
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
            }
            return super.serve(session)
        }

        override fun serveHttp(session: IHTTPSession?): Response {
            if (session?.method == Method.GET && (session.uri == "/" || session.uri == "/obs" || session.uri == "/obs_heartrate")) {
                val hostHeader = session.headers?.get("host")
                val host = extractHost(hostHeader) ?: session.remoteIpAddress ?: ""
                val resp = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", getObsHtml(host))
                resp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                resp.addHeader("Pragma", "no-cache")
                resp.addHeader("Expires", "0")
                return resp
            }
            return super.serveHttp(session)
        }

        /**
         * ⚠️ 反直觉设计：MessageDigest.isEqual 恒定时间比较防时序攻击，与 HttpServerManager 一致。
         */
        private fun isAuthorized(session: IHTTPSession?): Boolean {
            if (authToken.isEmpty()) return true
            val queryToken = session?.parameters?.get("token")?.firstOrNull()
            return queryToken != null &&
                java.security.MessageDigest.isEqual(queryToken.toByteArray(), authToken.toByteArray())
        }

        private fun extractHost(hostHeader: String?): String? {
            if (hostHeader.isNullOrEmpty()) return null
            return if (hostHeader.startsWith("[")) {
                hostHeader.substringBeforeLast("]:").removeSurrounding("[", "")
                    .ifEmpty { null }
            } else {
                hostHeader.substringBeforeLast(":")
            }
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
            synchronized(activeScopes) {
                activeScopes.forEach { it.cancel() }
                activeScopes.clear()
            }
            synchronized(activeSockets) { activeSockets.clear() }
            super.stop()
        }

        private fun updateClientCount() {
            clientCountFlow.value = synchronized(activeSockets) { activeSockets.size }
            updateConnectedClientInfo()
        }

        private fun updateConnectedClientInfo() {
            val socket = synchronized(activeSockets) { activeSockets.firstOrNull() }
            if (socket == null) {
                connectedClientInfoFlow.value = null
            } else {
                val ip = socket.handshakeRequest.remoteIpAddress ?: ""
                val ua = socket.handshakeRequest.headers?.get("user-agent")
                connectedClientInfoFlow.value = com.github.heartratemonitor_compose.service.ConnectedClientInfo(
                    name = parseOsNameFromUserAgent(ua),
                    ip = ip
                )
            }
        }

        private fun parseOsNameFromUserAgent(ua: String?): String {
            if (ua.isNullOrBlank()) return "PC"
            val lower = ua.lowercase()
            return when {
                lower.contains("windows") -> "Windows"
                lower.contains("macintosh") || lower.contains("mac os") -> "macOS"
                lower.contains("linux") -> "Linux"
                lower.contains("android") -> "Android"
                lower.contains("iphone") || lower.contains("ipad") || lower.contains("ios") -> "iOS"
                else -> "PC"
            }
        }

        inner class AppWebSocket(handshakeRequest: IHTTPSession) : WebSocket(handshakeRequest) {
            private val webSocketScope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also {
                activeScopes.add(it)
            }

            // ⚠️ 反直觉设计：send/ping 失败后 close() 异步，onClose 回调触发前 collect 可能再次 send()，此标志防重复
            @Volatile
            private var isClosing = false

            init {
                activeSockets.add(this)
            }

            override fun onOpen() {
                updateClientCount()
                Log.d("AppWebSocket", "WebSocket opened for: ${handshakeRequest.remoteIpAddress}")

                webSocketScope.launch {
                    try {
                        while (isOpen && !isClosing) {
                            delay(4000)
                            ping(byteArrayOf())
                        }
                    } catch (e: CancellationException) {
                        // expected
                    } catch (e: IOException) {
                        if (!isClosing) {
                            isClosing = true
                            Log.e("AppWebSocket", "Error sending ping, closing connection.", e)
                            close(CloseCode.GoingAway, "Ping failed", false)
                        }
                    }
                }

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
