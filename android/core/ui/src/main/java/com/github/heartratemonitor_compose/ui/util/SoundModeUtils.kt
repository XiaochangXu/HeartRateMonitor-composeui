package com.github.heartratemonitor_compose.ui.util

import androidx.core.os.LocaleListCompat
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys

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
