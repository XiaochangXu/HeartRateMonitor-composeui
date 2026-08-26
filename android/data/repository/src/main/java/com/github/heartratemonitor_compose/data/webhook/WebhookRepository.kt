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

/**
 * 从原 `ui.webhook.WebhookManager` 下沉到数据层，避免 UI 包直接持有网络管理类。
 *
 * 持久化由 DataStore（经 [SettingsRepository]）承载，值为 kotlinx.serialization
 * 序列化的 JSON 字符串。旧版 `config_webhook.json` 文件在首次启动时一次性迁移。
 */
@Singleton
class WebhookRepository @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository
) {

    private val appContext = application.applicationContext
    private val legacyWebhookFile = File(application.filesDir, "config_webhook.json")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 用于解析用户输入的 headers JSON 字符串（非持久化，运行时 HTTP 请求设置请求头）
    private val json = Json { ignoreUnknownKeys = true }

    // 内存缓存：避免每次 triggerWebhooks（每个心率包）都读盘
    @Volatile
    private var webhooksCache: List<Webhook> = emptyList()
    private val cacheLock = Any()

    // Webhook 节流：记录每个 webhook（以 name|url 为标识）的上次发送时间戳，
    // 时间窗口内不重复发送，避免高频心率包触发大量 HTTP 请求导致网络拥塞。
    private val lastSentAtMs = ConcurrentHashMap<String, Long>()

    // 标记旧文件是否已迁移
    @Volatile
    private var migrated = false

    init {
        refreshCache()
    }

    /**
     * 首次启动时迁移旧 `config_webhook.json` 文件到 DataStore。
     * 旧文件的 JSON 格式与新格式键名一致，直接用 [Webhook.listFromJson] 解析即可。
     * 迁移完成后删除旧文件，后续启动跳过。
     */
    private fun migrateLegacyFileIfNeeded() {
        if (migrated) return
        val data = settingsRepository.getNullable(SettingsKeys.WEBHOOKS_JSON)
        if (data != null) {
            // DataStore 已有数据，无需迁移
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
            // 无论成功与否都删除旧文件，避免反复尝试失败的迁移
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

    /**
     * 以 name|url 作为 webhook 标识：body/headers 等字段变更不影响节流判定，
     * 用户修改 url 重命名后立即生效。
     * 手动测试（[testWebhook]）不经过本方法，不受节流影响。
     */
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

    /**
     * 应用进程结束时调用；Service 不应调用，否则会影响配置页等其它持有者。
     */
    fun shutdown() {
        scope.cancel()
    }

    companion object {
        /** 节流时间窗口（毫秒） */
        private const val THROTTLE_WINDOW_MS = 5_000L
    }
}
