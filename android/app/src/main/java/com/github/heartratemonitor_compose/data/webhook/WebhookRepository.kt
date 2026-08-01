package com.github.heartratemonitor_compose.data.webhook

import android.app.Application
import android.util.Log
import com.github.heartratemonitor_compose.R
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

/**
 * Webhook 数据仓库。
 *
 * 负责：
 * - 本地 webhook 配置的持久化（filesDir/config_webhook.json）。
 * - 按触发条件筛选并异步发送 HTTP 请求。
 * - 为配置页提供 CRUD 与测试能力。
 *
 * 从原 `ui.webhook.WebhookManager` 下沉到数据层，避免 UI 包直接持有网络管理类。
 */
class WebhookRepository(application: Application) {

    private val appContext = application.applicationContext
    private val webhookFile = File(application.filesDir, "config_webhook.json")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 内存缓存：避免每次 triggerWebhooks（每个心率包）都读盘解析 JSON
    @Volatile
    private var webhooksCache: List<Webhook> = emptyList()
    private val cacheLock = Any()

    init {
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
            Log.e("WebhookRepository", "获取Webhooks失败", e)
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

    private suspend fun sendRequest(
        webhook: Webhook,
        heartRate: Int,
        speed: Float,
        trigger: WebhookTrigger,
        isTest: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val bpm = heartRate.toString()
        // 修复：指定 Locale.US 以防止隐式使用默认 Locale 导致的格式问题
        val speedStr = String.format(Locale.US, "%.1f", speed)

        val shouldReplacePlaceholders = trigger == WebhookTrigger.HEART_RATE_UPDATED
                || trigger == WebhookTrigger.DISCONNECTED
                || trigger == WebhookTrigger.CONNECTED

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
                return@withContext appContext.getString(R.string.webhook_send_failed_headers, e.message)
            }
            if (connection.getRequestProperty("Content-Type") == null) {
                connection.setRequestProperty("Content-Type", "application/json")
            }
            if (connection.getRequestProperty("User-Agent") == null) {
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

            val responseTitle = if (isTest) {
                appContext.getString(R.string.webhook_test_response)
            } else {
                appContext.getString(R.string.webhook_sent)
            }
            val nameLabel = appContext.getString(R.string.webhook_resp_name)
            val triggerLabel = appContext.getString(R.string.webhook_resp_trigger)
            val statusLabel = appContext.getString(R.string.webhook_resp_status)
            val bodyLabel = appContext.getString(R.string.webhook_resp_body)
            """
            --- $responseTitle ---
            $nameLabel: ${webhook.name}
            $triggerLabel: ${trigger.name}
            $statusLabel: $responseCode $responseMessage
            $bodyLabel:
            $responseBody
            ----------------------
            """.trimIndent()

        } catch (e: Exception) {
            appContext.getString(R.string.webhook_send_error, e.message)
        } finally {
            connection?.disconnect()
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
            Log.e("WebhookRepository", "保存Webhooks失败", e)
        }
    }

    /**
     * 关闭 WebhookRepository，取消所有挂起的网络请求。
     * 应用进程结束时调用；Service 不应调用，否则会影响配置页等其它持有者。
     */
    fun shutdown() {
        scope.cancel()
    }
}
