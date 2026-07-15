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
    private val speedFlow: StateFlow<Float>, // 新增速度流
    private val isDeviceConnected: () -> Boolean,
    private val getStatusMessage: () -> String // 新增状态消息提供者
) {
    private var server: HttpServer? = null

    fun start() {
        if (server == null) {
            try {
                server = HttpServer()
                server?.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                Log.d("HttpServerManager", "HTTP Server started on port $port")
            } catch (e: IOException) {
                Log.e("HttpServerManager", "HTTP Server start failed", e)
            }
        }
    }

    fun stop() {
        server?.stop()
        server = null
        Log.d("HttpServerManager", "HTTP Server stopped")
    }

    private inner class HttpServer : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession?): Response {
            // 鉴权：若配置了 token，则校验 ?token= 或 Authorization: Bearer
            if (authToken.isNotEmpty()) {
                val queryToken = session?.parameters?.get("token")?.firstOrNull()
                val bearerToken = session?.headers?.get("authorization")
                    ?.removePrefix("Bearer ")?.trim()
                if (queryToken != authToken && bearerToken != authToken) {
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "Unauthorized")
                }
            }

            if (session?.method == Method.GET && session.uri == "/heartrate") {
                val json = JSONObject().apply {
                    put("heart_rate", heartRateFlow.value)
                    put("connected", isDeviceConnected())
                    put("status", getStatusMessage()) // 添加状态
                    put("timestamp", System.currentTimeMillis()) // 添加时间戳
                    put("speed", speedFlow.value) // 添加速度
                }
                return newFixedLengthResponse(Response.Status.OK, "application/json", json.toString())
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }
}