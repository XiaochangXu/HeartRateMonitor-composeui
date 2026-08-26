package com.github.heartratemonitor_compose.ui.util

import androidx.core.os.LocaleListCompat
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys

/**
 * Phase 7 从 ui/settings/FullscreenSoundScreen.kt 迁出，
 * :feature:main 的 FullScreenHeartRate 与 :feature:settings 的 FullscreenSoundScreen 共用，
 * 按方案规则 2 下沉 :core:ui（feature 之间禁止互依）。
 */
fun defaultFullscreenSoundMode(): String {
    return if (LocaleListCompat.getDefault()[0]?.language == "zh") "cn" else "en"
}

fun resolveSoundMode(settings: SettingsRepository): String {
    val existing = settings.getNullable(SettingsKeys.FULLSCREEN_SOUND_MODE)
    if (existing != null) return existing


    val oldEnabled = settings.get(SettingsKeys.FULLSCREEN_SOUND_ENABLED)
    val mode = if (!oldEnabled) "off" else defaultFullscreenSoundMode()
    settings.set(SettingsKeys.FULLSCREEN_SOUND_MODE, mode)
    return mode
}
