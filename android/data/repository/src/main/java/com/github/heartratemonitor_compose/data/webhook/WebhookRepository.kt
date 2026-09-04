package com.github.heartratemonitor_compose.data.webhook

import android.app.Application
import android.util.Log
import com.github.heartratemonitor_compose.data.repository.R
import com.github.heartratemonitor_compose.data.Webhook
import com.github.heartratemonitor_compose.data.WebhookTrigger
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// WebhookManager 下沉到数据层；DataStore 持久化 + 旧版 JSON 一次性迁移。
@Singleton
class WebhookRepository @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) {

    private val appContext = application.applicationContext
    private val legacyWebhookFile = File(application.filesDir, "config_webhook.json")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 仅运行时解析 headers，非持久化。
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var webhooksCache: List<Webhook> = emptyList()
    private val cacheLock = Any()

    // 节流：name|url 作标识，窗口内不重复发送，避免高频心率包导致网络拥塞。
    private val lastSentAtMs = ConcurrentHashMap<String, Long>()

    @Volatile
    private var migrated = false

    init {
        refreshCache()
    }

    // 首次启动时迁移旧 config_webhook.json 到 DataStore，完成后删除旧文件。
    private fun migrateLegacyFileIfNeeded() {
        if (migrated) return
        val data = settingsRepository.getNullable(SettingsKeys.WEBHOOKS_JSON)
        if (data != null) {
            migrated = true
            return
        }
        if (!legacyWebhookFile.exists()) {
            migrated = true
            return
        }
        try {
            val jsonString = legacyWebhookFile.readText()
            val webhooks = Webhook.listFromJson(jsonString)
            settingsRepository.set(SettingsKeys.WEBHOOKS_JSON, Webhook.listToJson(webhooks))
            Log.i("WebhookRepository", "旧 config_webhook.json 已迁移到 DataStore")
        } catch (e: Exception) {
            Log.e("WebhookRepository", "迁移旧 webhook 文件失败", e)
        } finally {
            // ⚠️ 反直觉设计：无论成功与否都删除旧文件，避免反复尝试失败的迁移。
            legacyWebhookFile.delete()
            migrated = true
        }
    }

    private fun refreshCache() {
        migrateLegacyFileIfNeeded()
        synchronized(cacheLock) {
            webhooksCache = loadWebhooksFromDataStore()
        }
    }

    private fun loadWebhooksFromDataStore(): List<Webhook> {
        val jsonString = settingsRepository.getNullable(SettingsKeys.WEBHOOKS_JSON)
            ?: return emptyList()
        return Webhook.listFromJson(jsonString)
    }

    fun triggerWebhooks(trigger: WebhookTrigger, heartRate: Int = 0, speed: Float = 0f) {
        val webhooks = synchronized(cacheLock) { webhooksCache }
        webhooks.filter { it.enabled && it.triggers.contains(trigger) }.forEach { webhook ->
            if (!tryAcquireThrottle(webhook)) return@forEach
            scope.launch {
                sendRequest(webhook, heartRate, speed, trigger)
            }
        }
    }

    // ⚠️ 反直觉设计：以 name|url 作节流标识（body/headers 变更不影响）；testWebhook 不经过此方法，不受节流。
    private fun tryAcquireThrottle(webhook: Webhook): Boolean {
        val now = System.currentTimeMillis()
        var acquired = false
        lastSentAtMs.compute("${webhook.name}|${webhook.url}") { _, last ->
            if (last == null || now - last >= THROTTLE_WINDOW_MS) {
                acquired = true
                now
            } else {
                last
            }
        }
        return acquired
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
        val speedStr = String.format(Locale.US, "%.1f", speed) // Locale.US 防止默认 Locale 格式问题

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
                val headersJson = json.parseToJsonElement(headersString) as JsonObject
                headersJson.forEach { (key, value) ->
                    connection.setRequestProperty(key, value.jsonPrimitive.content)
                }
            } catch (e: Exception) {
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
            if (e is kotlinx.coroutines.CancellationException) throw e
            appContext.getString(R.string.webhook_send_error, e.message)
        } finally {
            connection?.disconnect()
        }
    }

    fun getWebhooks(): List<Webhook> {
        return synchronized(cacheLock) { webhooksCache.toList() }
    }

    fun saveWebhooks(webhooks: ImmutableList<Webhook>) {
        try {
            settingsRepository.set(SettingsKeys.WEBHOOKS_JSON, Webhook.listToJson(webhooks))
            refreshCache()
        } catch (e: Exception) {
            Log.e("WebhookRepository", "保存Webhooks失败", e)
        }
    }

    // ⚠️ 反直觉设计：仅进程结束时调用；Service 不应调用，否则影响配置页等持有者。
    fun shutdown() {
        scope.cancel()
    }

    companion object {
        private const val THROTTLE_WINDOW_MS = 5_000L
    }
}
