package com.github.heartratemonitor_compose.ui.theme

import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// ⚠️ 反直觉设计：必须 Singleton 作用域——Activity/设置页/Services 共享同一实例；去 Singleton 会导致「主题设置失效」。
@Singleton
class ThemeState @Inject constructor(private val settings: SettingsRepository) {

    private val _config = MutableStateFlow(
        settings.settings.value.let { s ->
            ThemeConfig(
                source = s.themeSource,
                mode = s.themeMode,
                seedArgb = s.themeCustomSeed,
                style = runCatching {
                    PaletteStyle.valueOf(s.themePaletteStyle)
                }.getOrDefault(PaletteStyle.TonalSpot)
            )
        }
    )
    val config: StateFlow<ThemeConfig> = _config.asStateFlow()

    fun setSource(source: Int) {
        settings.set(SettingsKeys.THEME_SOURCE, source)
        _config.value = _config.value.copy(source = source)
    }

    fun setMode(mode: Int) {
        settings.set(SettingsKeys.THEME_MODE, mode)
        _config.value = _config.value.copy(mode = mode)
    }

    fun setSeed(argb: Int) {
        settings.set(SettingsKeys.THEME_CUSTOM_SEED, argb)
        _config.value = _config.value.copy(seedArgb = argb)
    }

    fun setStyle(style: PaletteStyle) {
        settings.set(SettingsKeys.THEME_PALETTE_STYLE, style.name)
        _config.value = _config.value.copy(style = style)
    }
}
