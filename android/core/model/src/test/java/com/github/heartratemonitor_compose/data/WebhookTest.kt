package com.github.heartratemonitor_compose.data

import com.google.common.truth.Truth.assertThat
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

/**
 * [Webhook] 序列化/反序列化单元测试。
 *
 * 覆盖：
 * - kotlinx.serialization round-trip（新格式）
 * - 旧格式 JSON 字符串兼容（键名一致，listFromJson 可直接解析）
 * - 空列表序列化
 * - triggers 序列化为小写枚举名
 */
class WebhookTest {

    @Test
    fun `listToJson and listFromJson round trip preserves all fields`() {
        val original = listOf(
            Webhook(
                name = "test-hook",
                url = "https://example.com/webhook",
                enabled = true,
                body = "{\"bpm\":\"{bpm}\"}",
                headers = "{\"Content-Type\":\"application/json\"}",
                triggers = persistentListOf(
                    WebhookTrigger.HEART_RATE_UPDATED,
                    WebhookTrigger.DISCONNECTED
                )
            ),
            Webhook(
                name = "disabled-hook",
                url = "https://example.com/other",
                enabled = false,
                body = "",
                headers = "",
                triggers = persistentListOf(WebhookTrigger.CONNECTED)
            )
        )

        val json = Webhook.listToJson(original)
        val restored = Webhook.listFromJson(json)

        assertThat(restored).hasSize(2)

        val first = restored[0]
        assertThat(first.name).isEqualTo("test-hook")
        assertThat(first.url).isEqualTo("https://example.com/webhook")
        assertThat(first.enabled).isTrue()
        assertThat(first.body).isEqualTo("{\"bpm\":\"{bpm}\"}")
        assertThat(first.headers).isEqualTo("{\"Content-Type\":\"application/json\"}")
        assertThat(first.triggers).containsExactly(
            WebhookTrigger.HEART_RATE_UPDATED,
            WebhookTrigger.DISCONNECTED
        ).inOrder()

        val second = restored[1]
        assertThat(second.name).isEqualTo("disabled-hook")
        assertThat(second.enabled).isFalse()
        assertThat(second.triggers).containsExactly(WebhookTrigger.CONNECTED)
    }

    @Test
    fun `listFromJson empty array returns empty list`() {
        val json = Webhook.listToJson(emptyList())
        assertThat(Webhook.listFromJson(json)).isEmpty()
    }

    @Test
    fun `listFromJson invalid json returns empty list`() {
        assertThat(Webhook.listFromJson("not a json")).isEmpty()
        assertThat(Webhook.listFromJson("")).isEmpty()
    }

    @Test
    fun `triggers are serialized as lowercase names`() {
        val webhook = Webhook(
            name = "test",
            url = "",
            triggers = persistentListOf(WebhookTrigger.HEART_RATE_UPDATED)
        )
        val json = Webhook.listToJson(listOf(webhook))

        // 验证 JSON 字符串中 triggers 值为小写
        assertThat(json).contains("\"heart_rate_updated\"")
    }

    @Test
    fun `listFromJson parses old format JSON string with triggers array`() {
        // 旧格式 JSON 字符串（键名与新格式一致，triggers 为小写枚举名）
        val legacyJson = """[{"name":"hook","url":"https://example.com","enabled":true,"body":"","headers":"","triggers":["heart_rate_updated","connected"]}]"""

        val restored = Webhook.listFromJson(legacyJson)
        assertThat(restored).hasSize(1)
        assertThat(restored[0].name).isEqualTo("hook")
        assertThat(restored[0].url).isEqualTo("https://example.com")
        assertThat(restored[0].enabled).isTrue()
        assertThat(restored[0].triggers).containsExactly(
            WebhookTrigger.HEART_RATE_UPDATED,
            WebhookTrigger.CONNECTED
        ).inOrder()
    }

    @Test
    fun `listFromJson defaults triggers when missing in old format`() {
        // 旧格式无 triggers 字段：kotlinx.serialization 使用默认值（HEART_RATE_UPDATED）
        val legacyJson = """[{"name":"no-trigger-hook","url":"https://example.com","enabled":true}]"""

        val restored = Webhook.listFromJson(legacyJson)
        assertThat(restored).hasSize(1)
        assertThat(restored[0].triggers).containsExactly(WebhookTrigger.HEART_RATE_UPDATED)
    }

    @Test
    fun `listFromJson ignores legacy single trigger field and uses default`() {
        // 最老格式：只有单数 trigger 字段（非 triggers 数组）
        // kotlinx.serialization 的 ignoreUnknownKeys 会忽略 trigger 字段，
        // triggers 使用默认值 HEART_RATE_UPDATED
        val legacyJson = """[{"name":"old-hook","url":"https://example.com","enabled":true,"trigger":"connected"}]"""

        val restored = Webhook.listFromJson(legacyJson)
        assertThat(restored).hasSize(1)
        assertThat(restored[0].name).isEqualTo("old-hook")
        // trigger 字段被忽略，triggers 回退到默认值
        assertThat(restored[0].triggers).containsExactly(WebhookTrigger.HEART_RATE_UPDATED)
    }

    @Test
    fun `default values match old Webhook defaults`() {
        val webhook = Webhook(name = "test", url = "")
        assertThat(webhook.enabled).isTrue()
        assertThat(webhook.body).isEqualTo("{\n  \"bpm\": \"{bpm}\"\n}")
        assertThat(webhook.headers).isEqualTo("{\n  \"Content-Type\": \"application/json\"\n}")
        assertThat(webhook.triggers).containsExactly(WebhookTrigger.HEART_RATE_UPDATED)
    }
}
