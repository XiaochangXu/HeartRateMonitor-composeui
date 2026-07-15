package com.github.heartratemonitor_compose.ui.webhook

import android.content.Context
import android.util.Log
import com.github.heartratemonitor_compose.data.Webhook
import com.github.heartratemonitor_compose.data.WebhookTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

// 修复：移除 'private val'，使 context 仅作为构造参数，不作为属性
class WebhookManager(context: Context) {

    private val webhookFile = File(context.filesDir, "config_webhook.json")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val githubUrl = "https://raw.githubusercontent.com/ccc007ccc/HeartRateMonitor/main/config_webhook.json"

    // 内存缓存：避免每次 triggerWebhooks（每个心率包）都读盘解析 JSON
    @Volatile
    private var webhooksCache: List<Webhook> = emptyList()
    private val cacheLock = Any()

    init {
        // 初始化时预加载缓存
        refreshCache()
    }

    private fun refreshCache() {
        synchronized(cacheLock) {
            webhooksCache = loadWebhooksFromDisk()
        }
    }

    private fun loadWebhooksFromDisk(): List<Webhook> {
        if (!webhookFile.exists()) return emptyList()
        return try {
            val jsonString = webhookFile.readText()
            val jsonArray = JSONArray(jsonString)
            val webhooks = mutableListOf<Webhook>()
            for (i in 0 until jsonArray.length()) {
                webhooks.add(Webhook.Companion.fromJson(jsonArray.getJSONObject(i)))
            }
            webhooks
        } catch (e: Exception) {
            Log.e("WebhookManager", "获取Webhooks失败", e)
            emptyList()
        }
    }

    fun triggerWebhooks(trigger: WebhookTrigger, heartRate: Int = 0, speed: Float = 0f) {
        val webhooks = synchronized(cacheLock) { webhooksCache }
        webhooks.filter { it.enabled && it.triggers.contains(trigger) }.forEach { webhook ->
            scope.launch {
                sendRequest(webhook, heartRate, speed, trigger)
            }
        }
    }

    fun testWebhook(webhook: Webhook, onResult: (String) -> Unit) {
        scope.launch {
            val result = sendRequest(webhook, 88, 15.5f, WebhookTrigger.HEART_RATE_UPDATED, true)
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    private suspend fun sendRequest(webhook: Webhook, heartRate: Int, speed: Float, trigger: WebhookTrigger, isTest: Boolean = false): String {
        return withContext(Dispatchers.IO) {
            val bpm = heartRate.toString()
            // 修复：指定 Locale.US 以防止隐式使用默认 Locale 导致的格式问题
            val speedStr = String.format(Locale.US, "%.1f", speed)

            val shouldReplacePlaceholders = trigger == WebhookTrigger.HEART_RATE_UPDATED || trigger == WebhookTrigger.DISCONNECTED || trigger == WebhookTrigger.CONNECTED

            var urlString = webhook.url
            var bodyString = webhook.body
            var headersString = webhook.headers

            if (shouldReplacePlaceholders) {
                urlString = urlString.replace("{bpm}", bpm).replace("{speed}", speedStr)
                bodyString = bodyString.replace("{bpm}", bpm).replace("{speed}", speedStr)
                headersString = headersString.replace("{bpm}", bpm).replace("{speed}", speedStr)
            }

            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                try {
                    val headersJson = JSONObject(headersString)
                    headersJson.keys().forEach { key ->
                        connection.setRequestProperty(key, headersJson.getString(key))
                    }
                } catch (e: JSONException) {
                    return@withContext "发送失败: Headers不是有效的JSON格式: ${e.message}"
                }
                if(connection.getRequestProperty("Content-Type") == null){
                    connection.setRequestProperty("Content-Type", "application/json")
                }
                if(connection.getRequestProperty("User-Agent") == null){
                    connection.setRequestProperty("User-Agent", "HeartRateMonitorMobile-Webhook")
                }

                connection.doOutput = true
                connection.outputStream.use { os ->
                    OutputStreamWriter(os).use { writer ->
                        writer.write(bodyString)
                        writer.flush()
                    }
                }

                val responseCode = connection.responseCode
                val responseMessage = connection.responseMessage
                val inputStream = if (responseCode < HttpURLConnection.HTTP_BAD_REQUEST) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

                val responseBody = inputStream?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).use { reader ->
                        reader.readText()
                    }
                } ?: ""

                val responseTitle = if (isTest) "Webhook 测试响应" else "Webhook 已发送"
                """
                --- $responseTitle ---
                名称: ${webhook.name}
                触发于: ${trigger.name}
                状态码: $responseCode $responseMessage
                响应体:
                $responseBody
                ----------------------
                """.trimIndent()

            } catch (e: Exception) {
                "发送时发生未知错误: ${e.message}"
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun getWebhooks(): MutableList<Webhook> {
        return synchronized(cacheLock) { webhooksCache.toMutableList() }
    }

    fun saveWebhooks(webhooks: List<Webhook>) {
        try {
            val jsonArray = JSONArray()
            webhooks.forEach { jsonArray.put(it.toJson()) }
            webhookFile.writeText(jsonArray.toString(4))
            refreshCache()
        } catch (e: Exception) {
            Log.e("WebhookManager", "保存Webhooks失败", e)
        }
    }

    fun syncFromGithub(onComplete: (Boolean, String) -> Unit) {
        scope.launch {
            try {
                val url = URL(githubUrl)
                val response = withContext(Dispatchers.IO) {
                    (url.openConnection() as HttpURLConnection).run {
                        connectTimeout = 15000
                        readTimeout = 15000
                        inputStream.bufferedReader().use { it.readText() }
                    }
                }

                JSONArray(response)
                webhookFile.writeText(response)
                refreshCache()

                withContext(Dispatchers.Main) {
                    onComplete(true, "同步成功！已从GitHub获取最新的官方预设。")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onComplete(false, "同步过程中发生错误: ${e.message}")
                }
            }
        }
    }

    /**
     * 关闭 WebhookManager，取消所有挂起的网络请求。
     * 应在 BleService.onDestroy 中调用。
     */
    fun shutdown() {
        scope.cancel()
    }
}