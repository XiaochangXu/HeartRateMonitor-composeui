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
        connectedClientInfoFlow.value = null
        Log.d("WebSocketServerManager", "WebSocket Server stopped")
    }

    /**
     * 主动断开所有已连接的 WebSocket 客户端（PC）。
     * 用于局域网传输页面「断开连接」按钮。
     */
    fun disconnectAllClients() {
        server?.disconnectAllClients()
    }

    @Volatile
    private var cachedObsTemplate: String? = null

    /**
     * 读取 assets/obs_heartrate.html 并注入 WebSocket 连接信息。
     * WS 服务器返回的页面，其 wsUrl 直接指向自身（同端口），不需要额外配置 WS 地址。
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
        // WS 服务器自身就提供 WebSocket 服务，wsUrl 直接指向同端口
        val wsUrl = if (host.isNotEmpty()) "ws://$host:$port" else ""
        // 转义注入值中的 </script> 和双引号/反斜杠，防止 XSS（host 来自客户端可控的 HTTP Host 头）
        val safeWsUrl = escapeForJs(wsUrl)
        val safeToken = escapeForJs(authToken)
        // 版本戳：每次请求都不同，用于打破 OBS 浏览器源缓存。
        // 页面加载时若 URL 无 _v 参数，则保留原有 query 参数（如 token）并追加 _v=时间戳后跳转，
        // URL 变化后浏览器无法使用缓存，确保每次连接都拉取最新 HTML。
        val versionStamp = System.currentTimeMillis()
        val injection = "<script>window.__WS_CONFIG__={wsUrl:\"$safeWsUrl\",token:\"$safeToken\"};" +
            "if(!new URLSearchParams(location.search).has('_v')){var p=new URLSearchParams(location.search);p.set('_v','$versionStamp');" +
            "location.replace(location.pathname+(p.toString()?'?'+p.toString():''));}" +
            "</script>"
        // 使用 split + join 避免 replaceFirst 的 replacement 中 $ 和 \ 被解释为特殊字符
        val parts = template.split("<script>", limit = 2)
        return if (parts.size == 2) {
            parts[0] + injection + "\n<script>" + parts[1]
        } else {
            template + injection
        }
    }

    /**
     * 转义注入到 JS 字符串字面量中的值，防止 XSS。
     * - 替换 </script> 序列，防止提前关闭 <script> 标签
     * - 转义反斜杠、双引号、单引号
     */
    private fun escapeForJs(value: String): String {
        return value
            .replace("</", "<\\/")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
    }

    private inner class AppWebSocketServer : NanoWSD(port) {

        // 跟踪所有活跃连接的 scope，确保 stop() 时全部取消，防止泄漏
        private val activeScopes = java.util.Collections.synchronizedSet(mutableSetOf<CoroutineScope>())
        // 跟踪所有活跃连接的 WebSocket 实例，用于主动断开
        private val activeSockets = java.util.Collections.synchronizedSet(mutableSetOf<AppWebSocket>())

        /**
         * 重写 serve() 拦截所有请求做鉴权，再委托给 NanoWSD.serve()。
         *
         * NanoWSD.serve() 的逻辑：
         * - WebSocket 升级请求 → 调用 openWebSocket() 握手（不经过 serveHttp）
         * - 普通 HTTP 请求 → 调用 serveHttp()
         *
         * 若只重写 serveHttp()，则 WebSocket 升级请求会绕过 token 鉴权。
         * 因此在 serve() 入口处统一拦截，鉴权通过后再 super.serve() 让 NanoWSD 分流。
         */
        override fun serve(session: IHTTPSession?): Response {
            if (!isAuthorized(session)) {
                return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
            }
            return super.serve(session)
        }

        override fun serveHttp(session: IHTTPSession?): Response {
            // 普通 HTTP GET 请求：根路径 / 返回 OBS 用的 HTML 页面
            // 浏览器源填 http://<IP>:<WS端口>/ 即可直接使用
            if (session?.method == Method.GET && (session.uri == "/" || session.uri == "/obs" || session.uri == "/obs_heartrate")) {
                val hostHeader = session.headers?.get("host")
                val host = extractHost(hostHeader) ?: session.remoteIpAddress ?: ""
                val resp = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", getObsHtml(host))
                // 禁止 OBS 浏览器源缓存 HTML 页面，确保每次连接都拉取最新内容
                resp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                resp.addHeader("Pragma", "no-cache")
                resp.addHeader("Expires", "0")
                return resp
            }
            return super.serveHttp(session)
        }

        /**
         * 鉴权：若配置了 token，则校验 ?token= 查询参数。
         * 使用 MessageDigest.isEqual 进行恒定时间比较，防止时序攻击。
         * 与 HttpServerManager 保持一致。
         */
        private fun isAuthorized(session: IHTTPSession?): Boolean {
            if (authToken.isEmpty()) return true
            val queryToken = session?.parameters?.get("token")?.firstOrNull()
            return queryToken != null &&
                java.security.MessageDigest.isEqual(queryToken.toByteArray(), authToken.toByteArray())
        }

        /**
         * 从 Host 头提取主机名/IP，去掉端口部分。
         * Host 头格式 "192.168.1.100:8000" → "192.168.1.100"
         * IPv6 格式 "[::1]:8000" → "::1"（去掉方括号和端口）
         */
        private fun extractHost(hostHeader: String?): String? {
            if (hostHeader.isNullOrEmpty()) return null
            return if (hostHeader.startsWith("[")) {
                // IPv6: [::1]:8000 → ::1
                hostHeader.substringBeforeLast("]:").removeSurrounding("[", "")
                    .ifEmpty { null }
            } else {
                // IPv4: 192.168.1.100:8000 → 192.168.1.100
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
            updateConnectedClientInfo()
        }

        /**
         * 从活跃连接中取第一个客户端的 OS 名称 + IP 写入 [connectedClientInfoFlow]。
         * 无连接时置 null。UI 据此展示「已连接设备」卡片。
         */
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

        /**
         * 从 User-Agent 字符串中粗略解析操作系统名称。
         * 常见 PC 客户端（C# WebView2 / Edge / Chrome）UA 含 "Windows NT"、"Macintosh" 等。
         * 解析失败回退为 "PC"。
         */
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
