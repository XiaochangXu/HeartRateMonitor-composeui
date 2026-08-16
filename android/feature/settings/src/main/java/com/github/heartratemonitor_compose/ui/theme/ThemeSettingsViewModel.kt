package com.github.heartratemonitor_compose.ui.theme

import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import com.materialkolor.PaletteStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 主题设置页面的 ViewModel（教科书式 MVI）。
 *
 * 主题配置经 [ThemeState]（Hilt 单例，持久化真源仍为 SettingsRepository）
 * 投影进单一 [ThemeSettingsUiState]；来源/模式/种子色/variant 经 Intent 上行。
 * 替代原页面经 SettingsDependencies EntryPoint 直取单例的写法。
 *
 * [themePreviewCache] 为色卡预览配色查询的一次性展示依赖（只读缓存，非业务状态），
 * 经 VM 下发给 PresetSeedsRow，避免 Composable 走 EntryPoint。
 */
@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    private val themeState: ThemeState,
    val themePreviewCache: ThemePreviewCache
) : MviViewModel<ThemeSettingsUiState, ThemeSettingsIntent>(
    ThemeSettingsUiState(config = themeState.config.value)
) {

    init {
        viewModelScope.launch {
            themeState.config.collect { config ->
                setState { it.copy(config = config) }
            }
        }
    }

    override suspend fun handleIntent(intent: ThemeSettingsIntent) {
        when (intent) {
            is ThemeSettingsIntent.SetSource -> themeState.setSource(intent.source)
            is ThemeSettingsIntent.SetMode -> themeState.setMode(intent.mode)
            is ThemeSettingsIntent.SetSeed -> themeState.setSeed(intent.argb)
            is ThemeSettingsIntent.SetStyle -> themeState.setStyle(intent.style)
        }
    }
}

/** 主题设置页用户意图。 */
sealed interface ThemeSettingsIntent {
    data class SetSource(val source: Int) : ThemeSettingsIntent
    data class SetMode(val mode: Int) : ThemeSettingsIntent
    data class SetSeed(val argb: Int) : ThemeSettingsIntent
    data class SetStyle(val style: PaletteStyle) : ThemeSettingsIntent
}

/** 主题设置页 UI 状态（只读快照）。 */
data class ThemeSettingsUiState(
    val config: ThemeConfig
)
