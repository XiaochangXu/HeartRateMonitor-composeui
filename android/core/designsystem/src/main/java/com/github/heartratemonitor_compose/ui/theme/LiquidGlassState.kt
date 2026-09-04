package com.github.heartratemonitor_compose.ui.theme

import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.AppSettings
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// ⚠️ 反直觉设计：必须 Singleton 作用域——AppRoot 与 NavStyleScreen 共享同一实例，否则「液态玻璃开关失效」。
@Singleton
class LiquidGlassState @Inject constructor(private val settings: SettingsRepository) {

    private val _config = MutableStateFlow(
        settings.settings.value.let { s ->
            LiquidGlassConfig(
                enabled = s.liquidGlassEnabled,
                blurDp = s.liquidGlassBlurDp,
                distortionDp = s.liquidGlassDistortionDp
            )
        }
    )
    val config: StateFlow<LiquidGlassConfig> = _config.asStateFlow()

    // 独立流避免订阅全量 AppSettings（60+ 字段变化触发根节点重组）。
    val navAnimationDisabledFlow: StateFlow<Boolean> =
        settings.observe(SettingsKeys.NAV_ANIMATION_DISABLED)

    fun setEnabled(enabled: Boolean) {
        settings.set(SettingsKeys.LIQUID_GLASS_ENABLED, enabled)
        _config.value = _config.value.copy(enabled = enabled)
    }

    fun setBlurDp(dp: Float) {
        settings.set(SettingsKeys.LIQUID_GLASS_BLUR, dp)
        _config.value = _config.value.copy(blurDp = dp)
    }

    fun setDistortionDp(dp: Float) {
        settings.set(SettingsKeys.LIQUID_GLASS_DISTORTION, dp)
        _config.value = _config.value.copy(distortionDp = dp)
    }

    fun restoreDefaults() {
        setBlurDp(AppSettings.DEFAULT_LIQUID_GLASS_BLUR_DP)
        setDistortionDp(AppSettings.DEFAULT_LIQUID_GLASS_DISTORTION_DP)
    }

    companion object {
        val BLUR_RANGE_DP = 0f..40f

        val DISTORTION_RANGE_DP = 0f..30f
    }
}
