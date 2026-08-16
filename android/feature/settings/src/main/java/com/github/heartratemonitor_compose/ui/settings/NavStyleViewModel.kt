package com.github.heartratemonitor_compose.ui.settings

import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassConfig
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 导航效果设置页面的 ViewModel（MVI 架构）。
 *
 * 液态玻璃配置经 [LiquidGlassState]（Hilt 单例，持久化真源仍为 SettingsRepository）
 * 投影进单一 [NavStyleUiState]；开关/滑块/恢复默认经 Intent 上行。
 * 替代原页面经 SettingsDependencies EntryPoint 直取单例的写法。
 */
@HiltViewModel
class NavStyleViewModel @Inject constructor(
    private val liquidGlassState: LiquidGlassState
) : MviViewModel<NavStyleUiState, NavStyleIntent>(
    NavStyleUiState(config = liquidGlassState.config.value)
) {

    init {
        viewModelScope.launch {
            liquidGlassState.config.collect { config ->
                setState { it.copy(config = config) }
            }
        }
    }

    override suspend fun handleIntent(intent: NavStyleIntent) {
        when (intent) {
            is NavStyleIntent.SetEnabled -> liquidGlassState.setEnabled(intent.enabled)
            is NavStyleIntent.SetBlurDp -> liquidGlassState.setBlurDp(intent.dp)
            is NavStyleIntent.SetDistortionDp -> liquidGlassState.setDistortionDp(intent.dp)
            NavStyleIntent.RestoreDefaults -> liquidGlassState.restoreDefaults()
        }
    }
}

/** 导航效果设置页用户意图。 */
sealed interface NavStyleIntent {
    data class SetEnabled(val enabled: Boolean) : NavStyleIntent
    data class SetBlurDp(val dp: Float) : NavStyleIntent
    data class SetDistortionDp(val dp: Float) : NavStyleIntent
    data object RestoreDefaults : NavStyleIntent
}

/** 导航效果设置页 UI 状态（只读快照）。 */
data class NavStyleUiState(
    val config: LiquidGlassConfig
)
