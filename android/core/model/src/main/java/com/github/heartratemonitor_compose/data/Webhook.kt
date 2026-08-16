package com.github.heartratemonitor_compose.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class Webhook(
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val body: String = "{\n  \"bpm\": \"{bpm}\"\n}",
    val headers: String = "{\n  \"Content-Type\": \"application/json\"\n}",
    val triggers: List<WebhookTrigger> = listOf(WebhookTrigger.HEART_RATE_UPDATED)
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("name", name)
            put("url", url)
            put("enabled", enabled)
            put("body", body)
            put("headers", headers)
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
                        val trigger = WebhookTrigger.valueOf(triggersArray.getString(i).uppercase())
                        triggers.add(trigger)
                    } catch (e: IllegalArgumentException) {
                        // 向后兼容：旧版本 trigger 字段名与枚举值不一致（如 "heart_rate_updated" vs "HEART_RATE_UPDATE"）
                        val triggerString = triggersArray.getString(i)
                        if (triggerString.equals("heart_rate_updated", ignoreCase = true)) {
                            triggers.add(WebhookTrigger.HEART_RATE_UPDATED)
                        } else {
                            Log.w("Webhook.fromJson", "Ignoring unknown trigger: $triggerString")
                        }
                    }
                }
            }
            // 向后兼容旧的单 trigger 字段（旧版只存单个 trigger 而非 triggers 数组）
            else if (json.has("trigger")) {
                try {
                    val trigger = WebhookTrigger.valueOf(json.getString("trigger").uppercase())
                    triggers.add(trigger)
                } catch (e: IllegalArgumentException) {
                    Log.w("Webhook.fromJson", "Ignoring unknown legacy trigger: ${json.getString("trigger")}")
                }
            }

            if (triggers.isEmpty()) {
                triggers.add(WebhookTrigger.HEART_RATE_UPDATED)
            }

            return Webhook(
                name = json.optString("name", ""),
                url = json.optString("url", ""),
                enabled = json.optBoolean("enabled", true),
                body = json.optString("body", "{\n  \"bpm\": \"{bpm}\"\n}"),
                headers = json.optString("headers", "{\n  \"Content-Type\": \"application/json\"\n}"),
                triggers = triggers.toList()
            )
        }
    }
}