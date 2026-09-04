package com.github.heartratemonitor_compose.ui.theme

// blurDp 仅 API 31+，distortionDp 仅 API 33+
data class LiquidGlassConfig(
    val enabled: Boolean,
    val blurDp: Float,
    val distortionDp: Float
)
