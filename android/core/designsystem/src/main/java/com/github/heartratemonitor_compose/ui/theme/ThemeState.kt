package com.github.heartratemonitor_compose.ui.theme

import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.materialkolor.PaletteStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 全局主题状态（Hilt 单例，Phase 2 起由 Hilt 装配，替代 AppContainer）。
 *
 * **必须保持 [Singleton] 作用域**：MainActivity（AppTheme 消费）、设置页
 * （EntryPoint 写入）、FloatingWindowService / StatusBarResidentService 共享同一实例，
 * 任一调用方修改后全 App 即时重配色。若去掉 @Singleton，Hilt 会在每个注入点各建一个实例，
 * 设置页改的是自己的副本，UI 侧收不到更新——即「主题设置失效」。
 *
 * 构造时经 SettingsRepository 预热快照同步加载持久化配置，
 * 由 HeartRateApp.onCreate 显式触发注入字段，保证在任何 Composable 读取前就绪。
 */
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
