package com.github.heartratemonitor_compose.ui.main

import android.content.Context
import android.os.LocaleList
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale

/**
 * 应用语言应用工具。
 *
 * `attachBaseContext` 在 Hilt 组件初始化之前调用，无法走注入获取
 * [SettingsRepository][com.github.heartratemonitor_compose.data.repository.SettingsRepository]，
 * 此处直连全进程唯一 [settingsDataStore] 同步读取语言设置（契约 2 例外，
 * 与 `ServiceBootInitializer` 同模式）。
 */
internal object LocaleHelper {

    fun wrap(base: Context): Context {
        val languageTag = getSavedLanguageTag(base) ?: return base
        if (languageTag.isBlank()) return base

        // ⚠️ 反直觉设计：印尼语 BCP 47 代码 "id" 被 Java Locale 映射为已废弃的 "in"（getLanguage() 返回 "in"），
        // 资源系统按 getLanguage() 匹配 values-xx 目录，故需规范化为 "in"。
        val normalizedTag = if (languageTag == "id") "in" else languageTag

        val localeList = LocaleList.forLanguageTags(normalizedTag)
        Locale.setDefault(localeList.get(0))
        val config = base.resources.configuration
        config.setLocales(localeList)
        return base.createConfigurationContext(config)
    }

    private fun getSavedLanguageTag(context: Context): String? {
        return try {
            val prefs = runBlocking { context.settingsDataStore.data.first() }
            prefs[SettingsKeys.APP_LANGUAGE]
        } catch (_: Exception) {
            null
        }
    }
}
