package com.github.heartratemonitor_compose.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class Webhook(
    var name: String,
    var url: String,
    var enabled: Boolean = true,
    var body: String = "{\n  \"bpm\": \"{bpm}\"\n}",
    var headers: String = "{\n  \"Content-Type\": \"application/json\"\n}",
    var triggers: MutableList<WebhookTrigger> = mutableListOf(WebhookTrigger.HEART_RATE_UPDATED)
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("url", url)
            put("enabled", enabled)
            put("body", body)
            put("headers", headers)
            // 将 triggers 列表转换为小写的字符串，与输入格式保持一致
            put("triggers", JSONArray(triggers.map { it.name.lowercase() }))
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Webhook {
            val triggers = mutableListOf<WebhookTrigger>()
            if (json.has("triggers")) {
                val triggersArray = json.getJSONArray("triggers")
                for (i in 0 until triggersArray.length()) {
                    try {
                        // **【关键修复】** 将JSON中的字符串转为大写再进行匹配，实现大小写不敏感
                        val trigger = WebhookTrigger.valueOf(triggersArray.getString(i).uppercase())
                        triggers.add(trigger)
                    } catch (e: IllegalArgumentException) {
                        // 忽略无法识别的trigger值, 比如 "heart_rate_updated" vs "HEART_RATE_UPDATE"
                        // 让我们尝试修复这个
                        val triggerString = triggersArray.getString(i)
                        if (triggerString.equals("heart_rate_updated", ignoreCase = true)) {
                            triggers.add(WebhookTrigger.HEART_RATE_UPDATED)
                        } else {
                            Log.w("Webhook.fromJson", "Ignoring unknown trigger: $triggerString")
                        }
                    }
                }
            }
            // 向后兼容旧的单 trigger 字段
            else if (json.has("trigger")) {
                try {
                    val trigger = WebhookTrigger.valueOf(json.getString("trigger").uppercase())
                    triggers.add(trigger)
                } catch (e: IllegalArgumentException) {
                    Log.w("Webhook.fromJson", "Ignoring unknown legacy trigger: ${json.getString("trigger")}")
                }
            }

            // 如果解析后一个触发器都没有，默认添加心率更新
            if (triggers.isEmpty()) {
                triggers.add(WebhookTrigger.HEART_RATE_UPDATED)
            }

            return Webhook(
                name = json.getString("name"),
                url = json.getString("url"),
                enabled = json.getBoolean("enabled"),
                body = json.getString("body"),
                headers = json.getString("headers"),
                triggers = triggers
            )
        }
    }
}