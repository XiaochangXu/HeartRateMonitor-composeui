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
 *
 * 语言存储为 BCP 47 语言 Tag 字符串（如 `"zh-CN"`、`"en"`、`"de"`），
 * `null` 或空字符串表示自动跟随系统语言。
 */
internal object LocaleHelper {

    /**
     * 读取持久化的语言 Tag，返回应用了该语言的 [Context]。
     *
     * @param base 原始 base Context
     * @return 应用了语言配置的 wrapper Context（语言为 null/空时原样返回）
     */
    fun wrap(base: Context): Context {
        val languageTag = getSavedLanguageTag(base) ?: return base
        if (languageTag.isBlank()) return base

        val localeList = LocaleList.forLanguageTags(languageTag)
        Locale.setDefault(localeList.get(0))
        val config = base.resources.configuration
        config.setLocales(localeList)
        return base.createConfigurationContext(config)
    }

    /**
     * 直连 DataStore 同步读取语言设置 Tag。
     * 首次读取会触发 SharedPreferences → DataStore 迁移（与 SettingsRepository 预热同源）。
     */
    private fun getSavedLanguageTag(context: Context): String? {
        return try {
            val prefs = runBlocking { context.settingsDataStore.data.first() }
            prefs[SettingsKeys.APP_LANGUAGE]
        } catch (_: Exception) {
            null
        }
    }
}
