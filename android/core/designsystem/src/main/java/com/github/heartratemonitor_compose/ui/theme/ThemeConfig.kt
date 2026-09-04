package com.github.heartratemonitor_compose.ui.theme

import com.materialkolor.PaletteStyle

object ThemeSource {
    const val SYSTEM_MONET = 0
    const val CUSTOM = 1
}

object ThemeMode {
    const val FOLLOW_SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

// @param seedArgb/style 仅 CUSTOM 生效
data class ThemeConfig(
    val source: Int,
    val mode: Int,
    val seedArgb: Int,
    val style: PaletteStyle
)
