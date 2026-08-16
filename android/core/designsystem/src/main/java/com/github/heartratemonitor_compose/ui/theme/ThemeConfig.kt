package com.github.heartratemonitor_compose.ui.theme

import com.materialkolor.PaletteStyle

/**
 * - [SYSTEM_MONET]：跟随系统壁纸 Monet 动态取色（Android 12+），低版本回退到 Expressive 静态方案。
 * - [CUSTOM]：用户自选 seed 色，由 MaterialKolor 生成 ColorScheme，**切断壁纸联动**。
 */
object ThemeSource {
    const val SYSTEM_MONET = 0
    const val CUSTOM = 1
}

object ThemeMode {
    const val FOLLOW_SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2
}

/**
 * 主题配置快照，由 [ThemeState] 持有并通过 StateFlow 暴露给 Compose 层。
 *
 * @param seedArgb 仅 [ThemeSource.CUSTOM] 生效
 * @param style 仅 [ThemeSource.CUSTOM] 生效
 */
data class ThemeConfig(
    val source: Int,
    val mode: Int,
    val seedArgb: Int,
    val style: PaletteStyle
)
