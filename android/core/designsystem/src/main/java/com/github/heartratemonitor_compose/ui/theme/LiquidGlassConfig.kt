package com.github.heartratemonitor_compose.ui.theme

/**
 * 液态玻璃配置快照，由 [LiquidGlassState] 持有并通过 StateFlow 暴露给 Compose 层。
 *
 * - [blurDp] 模糊半径，仅 Android 12 (API 31+) 生效。
 * - [distortionDp] 扭曲半径，仅 Android 13 (API 33+) 生效。
 */
data class LiquidGlassConfig(
    val enabled: Boolean,
    val blurDp: Float,
    val distortionDp: Float
)
