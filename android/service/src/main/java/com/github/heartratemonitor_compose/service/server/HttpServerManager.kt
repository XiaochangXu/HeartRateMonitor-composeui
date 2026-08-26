package com.github.heartratemonitor_compose.service.server

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.IOException

class HttpServerManager(
    private val context: Context,
    private val port: Int,
    private val authToken: String,
    private val heartRateFlow: StateFlow<Int>,
    private val speedFlow: StateFlow<Float>,
    private val isDeviceConnected: () -> Boolean,
    private val getStatusMessage: () -> String,
    private val wsPortProvider: () -> Int,
    private val wsEnabledProvider: () -> Boolean
) {
    private var server: HttpServer? = null

    /**
     * 启动 HTTP 服务器。
     * @return true 表示启动成功；false 表示端口被占用或其它 IO 错误导致启动失败。
     *         调用方（ServerHost）据此向 UI 传递实际运行状态，避免「设置已启用但服务器未运行」。
     */
    fun start(): Boolean {
        if (server != null) return true
        return try {
            server = HttpServer()
            server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            Log.d("HttpServerManager", "HTTP Server started on port $port")
            true
        } catch (e: IOException) {
            Log.e("HttpServerManager", "HTTP Server start failed", e)
            server = null
            false
        }
    }

    fun stop() {
        server?.stop()
        server = null
        Log.d("HttpServerManager", "HTTP Server stopped")
    }

    /**
     * 读取 assets/obs_heartrate.html 内容并返回。
     * 该 HTML 页面供 OBS 浏览器源使用，通过 WebSocket 实时显示心率。
     * 首次读取后缓存原始模板到内存，每次请求时注入动态参数（WS 端口、token 等）。
     */
    private fun getObsHtml(host: String): String {
        val template = cachedObsTemplate ?: run {
            val html = try {
                context.assets.open("obs_heartrate.html").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e("HttpServerManager", "Failed to read obs_heartrate.html from assets", e)
                "<html><body><h1>Heart Rate Monitor</h1><p>HTML file not found.</p></body></html>"
            }
            cachedObsTemplate = html
            html
        }
        // 将 WebSocket 连接信息注入到页面中：
        // 页面 JS 读取 window.__WS_CONFIG__ 获取 ws 地址和 token，
        // 避免用户手动拼接 URL 参数
        val wsPort = wsPortProvider()
        val wsEnabled = wsEnabledProvider()
        val wsUrl = if (wsEnabled && host.isNotEmpty()) {
            "ws://$host:$wsPort"
        } else {
            ""  // WebSocket 未启用，页面会显示提示
        }
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

    @Volatile
    private var cachedObsTemplate: String? = null

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

    private inner class HttpServer : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession?): Response {
            // 使用 MessageDigest.isEqual 进行恒定时间比较，防止时序攻击
            if (authToken.isNotEmpty()) {
                val queryToken = session?.parameters?.get("token")?.firstOrNull()
                val bearerToken = session?.headers?.get("authorization")
                    ?.removePrefix("Bearer ")?.trim()
                val queryMatch = queryToken != null &&
                    java.security.MessageDigest.isEqual(queryToken.toByteArray(), authToken.toByteArray())
                val bearerMatch = bearerToken != null &&
                    java.security.MessageDigest.isEqual(bearerToken.toByteArray(), authToken.toByteArray())
                if (!queryMatch && !bearerMatch) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
                }
            }

            // 根路径 / 返回 OBS 用的 HTML 页面（浏览器源直接填此地址即可）
            if (session?.method == Method.GET && (session.uri == "/" || session.uri == "/obs" || session.uri == "/obs_heartrate")) {
                val hostHeader = session?.headers?.get("host")
                val host = extractHost(hostHeader) ?: session?.remoteIpAddress ?: ""
                val resp = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", getObsHtml(host))
                // 禁止 OBS 浏览器源缓存 HTML 页面，确保每次连接都拉取最新内容
                resp.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                resp.addHeader("Pragma", "no-cache")
                resp.addHeader("Expires", "0")
                return resp
            }

            if (session?.method == Method.GET && session.uri == "/heartrate") {
                val json = JSONObject().apply {
                    put("heart_rate", heartRateFlow.value)
                    put("connected", isDeviceConnected())
                    put("status", getStatusMessage())
                    put("timestamp", System.currentTimeMillis())
                    put("speed", speedFlow.value)
                }
                return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }
}
