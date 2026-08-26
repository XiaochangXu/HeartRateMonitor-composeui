package com.github.heartratemonitor_compose.ui.theme

import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.AppSettings
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * **必须保持 [Singleton] 作用域**：AppRoot（MainActivity 注入）与 NavStyleScreen
 * （EntryPoint 获取）必须共享同一实例，开关才能即时生效。若去掉 @Singleton，
 * 设置页修改的是自己的副本，AppRoot 侧收不到更新——即「液态玻璃开关失效」。
 *
 * 构造时经 SettingsRepository 预热快照同步加载持久化配置，
 * 由 [com.github.heartratemonitor_compose.HeartRateApp.onCreate] 显式触发注入字段。
 */
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

    /** 暴露 SettingsRepository 的全量设置快照，供 AppRoot 读取非液态玻璃相关的设置项。 */
    val appSettingsFlow: StateFlow<AppSettings> = settings.settings

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
