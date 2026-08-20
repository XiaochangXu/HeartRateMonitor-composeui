package com.github.heartratemonitor_compose.service.server

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.IOException

class HttpServerManager(
    private val port: Int,
    private val authToken: String,
    private val heartRateFlow: StateFlow<Int>,
    private val speedFlow: StateFlow<Float>,
    private val isDeviceConnected: () -> Boolean,
    private val getStatusMessage: () -> String
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