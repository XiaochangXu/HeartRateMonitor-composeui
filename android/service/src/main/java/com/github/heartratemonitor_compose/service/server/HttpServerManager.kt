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
     * @return true 表示启动成功；false 表示端口被占用等 IO 错误，调用方据此向 UI 传递实际运行状态。
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
     * 读取 assets/obs_heartrate.html 返回；首次读取后缓存模板，每次请求注入动态参数（WS 端口/token）。
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
        // ⚠️ 反直觉设计：escape 防 XSS（host 来自客户端可控的 Host 头）
        val wsPort = wsPortProvider()
        val wsEnabled = wsEnabledProvider()
        val wsUrl = if (wsEnabled && host.isNotEmpty()) "ws://$host:$wsPort" else ""
        val safeWsUrl = escapeForJs(wsUrl)
        val safeToken = escapeForJs(authToken)
        // 版本戳打破 OBS 浏览器源缓存
        val versionStamp = System.currentTimeMillis()
        val injection = "<script>window.__WS_CONFIG__={wsUrl:\"$safeWsUrl\",token:\"$safeToken\"};" +
            "if(!new URLSearchParams(location.search).has('_v')){var p=new URLSearchParams(location.search);p.set('_v','$versionStamp');" +
            "location.replace(location.pathname+(p.toString()?'?'+p.toString():''));}" +
            "</script>"
        // ⚠️ 反直觉设计：split + join 避 replaceFirst 中 $ 和 \ 被解释为特殊字符
        val parts = template.split("<script>", limit = 2)
        return if (parts.size == 2) {
            parts[0] + injection + "\n<script>" + parts[1]
        } else {
            template + injection
        }
    }

    @Volatile
    private var cachedObsTemplate: String? = null

    private fun escapeForJs(value: String): String {
        return value
            .replace("</", "<\\/")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
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

    private inner class HttpServer : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession?): Response {
            // ⚠️ 反直觉设计：MessageDigest.isEqual 恒定时间比较防时序攻击
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

            if (session?.method == Method.GET && (session.uri == "/" || session.uri == "/obs" || session.uri == "/obs_heartrate")) {
                val hostHeader = session.headers?.get("host")
                val host = extractHost(hostHeader) ?: session.remoteIpAddress ?: ""
                val resp = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", getObsHtml(host))
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
