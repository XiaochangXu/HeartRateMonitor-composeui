package com.github.heartratemonitor_compose.service.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 局域网传输：手机端配对客户端。
 *
 * 通过 HTTP POST 向电脑端的 PairingServer 发起配对请求。
 *
 * 协议（与电脑端 C# 实现保持一致）：
 *
 * 请求：POST http://{pcHost}:{pcPairPort}/pair-request
 * Body JSON：
 * ```
 * {
 *   "device_name": "心率监测-小米14",
 *   "device_id": "xxx",
 *   "platform": "android",
 *   "ws_ip": "192.168.1.100",   // 手机端 WS Server 监听 IP
 *   "ws_port": 8001,            // 手机端 WS Server 端口
 *   "ws_token": "abc123"        // 手机端 WS Server 鉴权 token，可能为空字符串
 * }
 * ```
 *
 * 响应：HTTP 200
 * ```
 * { "approved": true, "session_id": "xxx" }
 * ```
 * 或
 * ```
 * { "approved": false }
 * ```
 *
 * 电脑端收到请求后会弹窗让用户确认；用户长时间不操作时此请求会一直挂起，
 * 调用方应通过 [withTimeoutOrNull] 等方式设置超时。
 */
class PairClient {

    /** 配对请求参数 */
    data class PairRequest(
        val deviceName: String,
        val deviceId: String,
        val platform: String = "android",
        val wsIp: String,
        val wsPort: Int,
        val wsToken: String
    )

    /** 配对响应 */
    sealed class PairResponse {
        /** 用户允许；[sessionId] 由电脑端生成，用于后续日志/断开追溯 */
        data class Approved(val sessionId: String) : PairResponse()
        /** 用户主动拒绝 */
        object Rejected : PairResponse()
        /** 网络/协议错误；[message] 用于 UI 展示 */
        data class Failed(val message: String) : PairResponse()
    }

    /**
     * 发起一次配对请求。
     *
     * @param pcHost 电脑 IP
     * @param pcPairPort 电脑 PairingServer 端口（来自 mDNS TXT 记录）
     * @param request 手机端信息
     */
    suspend fun request(
        pcHost: String,
        pcPairPort: Int,
        request: PairRequest
    ): PairResponse = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("http://$pcHost:$pcPairPort${LanTransferProtocol.PAIR_PATH}")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5_000
                readTimeout = 30_000   // 留足时间给电脑端弹窗等待用户操作
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                doOutput = true
            }

            val body = JSONObject().apply {
                put("device_name", request.deviceName)
                put("device_id", request.deviceId)
                put("platform", request.platform)
                put("ws_ip", request.wsIp)
                put("ws_port", request.wsPort)
                put("ws_token", request.wsToken)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val raw = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (code !in 200..299) {
                return@withContext PairResponse.Failed("HTTP $code: $raw")
            }

            val json = JSONObject(raw.takeIf { it.isNotBlank() } ?: "{}")
            when {
                json.optBoolean("approved", false) ->
                    PairResponse.Approved(json.optString("session_id", ""))
                else -> PairResponse.Rejected
            }
        } catch (e: Exception) {
            Log.w("PairClient", "pair request failed", e)
            PairResponse.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            conn?.disconnect()
        }
    }
}
