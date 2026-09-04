package com.github.heartratemonitor_compose.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val ExpressLightColorScheme = lightColorScheme(
    primary = ExpressPrimaryLight,
    onPrimary = ExpressOnPrimaryLight,
    primaryContainer = ExpressPrimaryContainerLight,
    onPrimaryContainer = ExpressOnPrimaryContainerLight,
    secondary = ExpressSecondaryLight,
    onSecondary = ExpressOnSecondaryLight,
    secondaryContainer = ExpressSecondaryContainerLight,
    onSecondaryContainer = ExpressOnSecondaryContainerLight,
    surface = ExpressSurfaceLight,
    onSurface = ExpressOnSurfaceLight,
    surfaceVariant = ExpressSurfaceVariantLight,
    onSurfaceVariant = ExpressOnSurfaceVariantLight,
    surfaceBright = ExpressSurfaceBrightLight,
    surfaceDim = ExpressSurfaceDimLight,
    surfaceContainerLowest = ExpressSurfaceContainerLowestLight,
    surfaceContainerLow = ExpressSurfaceContainerLowLight,
    surfaceContainer = ExpressSurfaceContainerLight,
    surfaceContainerHigh = ExpressSurfaceContainerHighLight,
    surfaceContainerHighest = ExpressSurfaceContainerHighestLight,
    background = ExpressBackgroundLight,
    onBackground = ExpressOnBackgroundLight,
    error = ExpressErrorLight,
    outline = ExpressOutlineLight,
    outlineVariant = ExpressOutlineVariantLight
)

private val ExpressDarkColorScheme = darkColorScheme(
    primary = ExpressPrimaryDark,
    onPrimary = ExpressOnPrimaryDark,
    primaryContainer = ExpressPrimaryContainerDark,
    onPrimaryContainer = ExpressOnPrimaryContainerDark,
    secondary = ExpressSecondaryDark,
    onSecondary = ExpressOnSecondaryDark,
    secondaryContainer = ExpressSecondaryContainerDark,
    onSecondaryContainer = ExpressOnSecondaryContainerDark,
    surface = ExpressSurfaceDark,
    onSurface = ExpressOnSurfaceDark,
    surfaceVariant = ExpressSurfaceVariantDark,
    onSurfaceVariant = ExpressOnSurfaceVariantDark,
    surfaceBright = ExpressSurfaceBrightDark,
    surfaceDim = ExpressSurfaceDimDark,
    surfaceContainerLowest = ExpressSurfaceContainerLowestDark,
    surfaceContainerLow = ExpressSurfaceContainerLowDark,
    surfaceContainer = ExpressSurfaceContainerDark,
    surfaceContainerHigh = ExpressSurfaceContainerHighDark,
    surfaceContainerHighest = ExpressSurfaceContainerHighestDark,
    background = ExpressBackgroundDark,
    onBackground = ExpressOnBackgroundDark,
    error = ExpressErrorDark,
    outline = ExpressOutlineDark,
    outlineVariant = ExpressOutlineVariantDark
)

// M3 Expressive Shapes：标准圆弧 RoundedCornerShape；底部导航栏仍用 ContinuousCapsule。
val ExpressShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

// 无状态 M3 Expressive 主题：config 由调用方提供，按 source 走 CUSTOM/动态/静态三路径。
@Composable
fun HeartRateMonitorMobileTheme(
    config: ThemeConfig,
    customSchemeCache: CustomSchemeCache,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (config.mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> systemDark
    }

    val context = LocalContext.current

    // 同步计算 + 进程级缓存（同 seed/明暗/style 仅一次 HCT 转换），避免旧实现蓝色闪屏。
    val colorScheme = when {
        config.source == ThemeSource.CUSTOM -> remember(config.seedArgb, darkTheme, config.style) {
            customSchemeCache.get(config.seedArgb, darkTheme, config.style)
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ExpressDarkColorScheme
        else -> ExpressLightColorScheme
    }

    // ⚠️ 反直觉设计：仅 Activity 可操作 Window；Service context 返回 null → 跳过 Window 装饰。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val window = activity.window
                // ⚠️ 反直觉设计：API 35 弃用 statusBarColor/navigationBarColor，但旧版本仍需透明色实现沉浸式。
                @Suppress("DEPRECATION")
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                @Suppress("DEPRECATION")
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val isLight = colorScheme.surface.brightness() > 0.5
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = isLight
                    isAppearanceLightNavigationBars = isLight
                }
            }
        }
    }

    // 波纹透明度为 M3 默认 2x（pressed 20% / hovered 16% / focused 20% / dragged 32%），全局生效。
    val rippleAlpha = RippleAlpha(
        hoveredAlpha = 2f * 0.08f,
        focusedAlpha = 2f * 0.10f,
        pressedAlpha = 2f * 0.10f,
        draggedAlpha = 2f * 0.16f
    )

    // ⚠️ 反直觉设计：RippleConfiguration deprecated 但无替代——internal 构造函数 + RippleThemeConfiguration 未覆盖 rippleAlpha。
    @Suppress("DEPRECATION")
    val rippleConfig = RippleConfiguration(rippleAlpha = rippleAlpha)

    CompositionLocalProvider(
        LocalRippleConfiguration provides rippleConfig
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ExpressTypography,
            shapes = ExpressShapes,
            content = content
        )
    }
}

@Singleton
class CustomSchemeCache @Inject constructor() {
    private val cache = ConcurrentHashMap<Triple<Int, Boolean, PaletteStyle>, ColorScheme>()

    fun get(seedArgb: Int, isDark: Boolean, style: PaletteStyle): ColorScheme =
        cache.getOrPut(Triple(seedArgb, isDark, style)) {
            dynamicColorScheme(
                seedColor = Color(seedArgb),
                isDark = isDark,
                style = style
            )
        }
}

private fun androidx.compose.ui.graphics.Color.brightness(): Double {
    val r = red.toDouble()
    val g = green.toDouble()
    val b = blue.toDouble()
    return (0.299 * r + 0.587 * g + 0.114 * b)
}

// 沿 ContextWrapper 链找 Activity；Service context 返回 null，跳过 Window 装饰。
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
