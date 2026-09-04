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
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun listToJson(webhooks: List<Webhook>): String =
            json.encodeToString(webhooks)

        // 解析失败返回空列表。
        fun listFromJson(jsonString: String): List<Webhook> = try {
            json.decodeFromString<List<Webhook>>(jsonString)
        } catch (e: Exception) {
            Log.e("Webhook", "反序列化 Webhook 列表失败", e)
            emptyList()
        }
    }
}

// 输出普通 JSON 数组（小写 trigger 名），反序列化还原 ImmutableList 保持 Compose stable。
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

// ⚠️ 反直觉设计：持久化使用小写名称，向后兼容旧 org.json 格式。
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
                if (raw.equals("heart_rate_updated", ignoreCase = true)) {
                    WebhookTrigger.HEART_RATE_UPDATED
                } else {
                    Log.w("WebhookTriggerSerializer", "Ignoring unknown trigger: $raw")
                    WebhookTrigger.HEART_RATE_UPDATED
                }
            }
    }
}
