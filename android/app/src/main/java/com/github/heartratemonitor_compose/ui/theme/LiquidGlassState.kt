package com.github.heartratemonitor_compose.ui.theme

import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 液态玻璃配置快照。由 [LiquidGlassState] 持有并通过 [StateFlow] 暴露给 Compose 层。
 *
 * - [blurDp] 模糊半径，仅 Android 12 (API 31+) 生效，更低版本静默失效。
 * - [distortionDp] 扭曲半径（折射高度 = 折射强度），仅 Android 13 (API 33+) 生效。
 *
 * 与 Backdrop 库示例保持一致：blur(8f.dp) / lens(24f.dp, 24f.dp)。
 */
data class LiquidGlassConfig(
    val enabled: Boolean,
    val blurDp: Float,
    val distortionDp: Float
)

/**
 * 全局液态玻璃状态单例。
 *
 * - 在 [com.github.heartratemonitor_compose.HeartRateApp.onCreate] 中调用 [init] 注入
 *   [SettingsRepository]，从 `app_settings` 读取持久化配置。
 * - 设置页通过 [setEnabled]/[setBlurDp]/[setDistortionDp] 修改配置，立即写回
 *   SharedPreferences 并更新 [config] StateFlow。
 * - [com.github.heartratemonitor_compose.ui.AppRoot] 通过 collectAsStateWithLifecycle
 *   读取，决定是否为底部导航栏挂载 backdrop 采样层与玻璃效果。
 */
object LiquidGlassState {

    /** 默认模糊半径。 */
    const val DEFAULT_BLUR_DP = 5f

    /** 默认扭曲半径。 */
    const val DEFAULT_DISTORTION_DP = 30f

    /** 模糊滑块范围。 */
    val BLUR_RANGE_DP = 0f..40f

    /** 扭曲滑块范围。 */
    val DISTORTION_RANGE_DP = 0f..30f

    private val _config = MutableStateFlow(
        LiquidGlassConfig(
            enabled = true,
            blurDp = DEFAULT_BLUR_DP,
            distortionDp = DEFAULT_DISTORTION_DP
        )
    )
    val config: StateFlow<LiquidGlassConfig> = _config.asStateFlow()

    private lateinit var settings: SettingsRepository

    /**
     * 在 Application.onCreate 中调用，注入 [SettingsRepository] 并加载持久化配置。
     * 必须在任何 Composable 读取 [config] 之前完成（同一进程内仅调用一次）。
     */
    fun init(settingsRepository: SettingsRepository) {
        settings = settingsRepository
        _config.value = LiquidGlassConfig(
            enabled = settings.getBoolean(PrefsKeys.LIQUID_GLASS_ENABLED, true),
            blurDp = settings.getFloat(PrefsKeys.LIQUID_GLASS_BLUR, DEFAULT_BLUR_DP),
            distortionDp = settings.getFloat(PrefsKeys.LIQUID_GLASS_DISTORTION, DEFAULT_DISTORTION_DP)
        )
    }

    fun setEnabled(enabled: Boolean) {
        settings.setBoolean(PrefsKeys.LIQUID_GLASS_ENABLED, enabled)
        _config.value = _config.value.copy(enabled = enabled)
    }

    fun setBlurDp(dp: Float) {
        settings.setFloat(PrefsKeys.LIQUID_GLASS_BLUR, dp)
        _config.value = _config.value.copy(blurDp = dp)
    }

    fun setDistortionDp(dp: Float) {
        settings.setFloat(PrefsKeys.LIQUID_GLASS_DISTORTION, dp)
        _config.value = _config.value.copy(distortionDp = dp)
    }

    /** 恢复模糊与扭曲到默认值。 */
    fun restoreDefaults() {
        setBlurDp(DEFAULT_BLUR_DP)
        setDistortionDp(DEFAULT_DISTORTION_DP)
    }
}
