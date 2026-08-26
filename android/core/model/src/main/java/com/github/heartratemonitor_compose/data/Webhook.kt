package com.github.heartratemonitor_compose.data

import android.util.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

@Serializable
data class Webhook(
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val body: String = "{\n  \"bpm\": \"{bpm}\"\n}",
    val headers: String = "{\n  \"Content-Type\": \"application/json\"\n}",
    @Serializable(with = ImmutableWebhookTriggerListSerializer::class)
    val triggers: ImmutableList<WebhookTrigger> = persistentListOf(WebhookTrigger.HEART_RATE_UPDATED)
) {
    companion object {
        // 包级单例 Json 配置，容忍未知字段（向前兼容）
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** 将 Webhook 列表序列化为 JSON 字符串（用于 DataStore 持久化）。 */
        fun listToJson(webhooks: List<Webhook>): String =
            json.encodeToString(webhooks)

        /** 从 JSON 字符串反序列化 Webhook 列表，解析失败返回空列表。 */
        fun listFromJson(jsonString: String): List<Webhook> = try {
            json.decodeFromString<List<Webhook>>(jsonString)
        } catch (e: Exception) {
            Log.e("Webhook", "反序列化 Webhook 列表失败", e)
            emptyList()
        }
    }
}

/**
 * ImmutableList<WebhookTrigger> 的自定义序列化器：
 * 序列化时输出为普通 JSON 数组（小写 trigger 名），
 * 反序列化时还原为 ImmutableList，保持 Compose 稳定性推断。
 */
object ImmutableWebhookTriggerListSerializer :
    KSerializer<ImmutableList<WebhookTrigger>> {
    private val delegate = ListSerializer(WebhookTriggerSerializer)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: ImmutableList<WebhookTrigger>) {
        delegate.serialize(encoder, value.toList())
    }

    override fun deserialize(decoder: Decoder): ImmutableList<WebhookTrigger> {
        return delegate.deserialize(decoder).toImmutableList()
    }
}

/**
 * WebhookTrigger 枚举的自定义序列化器：持久化时使用小写名称，
 * 保持与旧 org.json 格式的向后兼容（旧格式 trigger 名为小写）。
 */
object WebhookTriggerSerializer :
    KSerializer<WebhookTrigger> {
    override val descriptor =
        PrimitiveSerialDescriptor("WebhookTrigger", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: WebhookTrigger) {
        encoder.encodeString(value.name.lowercase())
    }

    override fun deserialize(decoder: Decoder): WebhookTrigger {
        val raw = decoder.decodeString()
        return runCatching { WebhookTrigger.valueOf(raw.uppercase()) }
            .getOrElse {
                // 向后兼容：旧版本 trigger 字段名与枚举值不一致
                if (raw.equals("heart_rate_updated", ignoreCase = true)) {
                    WebhookTrigger.HEART_RATE_UPDATED
                } else {
                    Log.w("WebhookTriggerSerializer", "Ignoring unknown trigger: $raw")
                    WebhookTrigger.HEART_RATE_UPDATED
                }
            }
    }
}
