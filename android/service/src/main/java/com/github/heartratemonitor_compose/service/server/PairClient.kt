package com.github.heartratemonitor_compose.service.server

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

/**
 * 局域网传输：手机端配对客户端。
 *
 * HTTP POST 向电脑端 PairingServer 发起配对请求（协议与 C# 实现一致）。
 * 电脑端弹窗确认，长时间不操作请求挂起——调用方应设超时（[withTimeoutOrNull]）。
 */
// open + request 可重写：供 LanTransferViewModel 单测以 Fake 替换
open class PairClient @Inject constructor() {

    data class PairRequest(
        val deviceName: String,
        val deviceId: String,
        val platform: String = "android",
        val wsIp: String,
        val wsPort: Int,
        val wsToken: String
    )

    sealed class PairResponse {
        data class Approved(val sessionId: String) : PairResponse()
        object Rejected : PairResponse()
        data class Failed(val message: String) : PairResponse()
    }

    open suspend fun request(
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
                readTimeout = 30_000   // ⚠️ 反直觉设计：留足时间给电脑端弹窗等待用户操作
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
