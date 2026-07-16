package com.github.heartratemonitor_compose.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

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

/**
 * M3 Expressive Shapes
 *
 * Material Design 3 Expressive 使用不对称圆角：
 * - 大圆角 (28dp) 用于卡片/容器
 * - 小圆角 (4dp) 用于按钮/输入框
 * - 创建动态的"有机感"外观
 */
val ExpressShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * HeartRateMonitorMobile M3 Expressive 主题
 *
 * 在 Android 12+ 上使用系统动态取色（Monet），
 * 低版本回退到预设的 M3 Expressive 颜色方案。
 * 总是使用 Expressive 排版和形状。
 */
@Composable
fun HeartRateMonitorMobileTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ExpressDarkColorScheme
        else -> ExpressLightColorScheme
    }

    // 自适应状态栏/导航栏图标颜色
    // 仅 Activity 上下文可操作 Window；Service 内托管的 ComposeView 上下文为 Service，跳过
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val window = activity.window
                val schemeColor = colorScheme.primary.toArgb()
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                val isLight = colorScheme.surface.brightness() > 0.5
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = isLight
                    isAppearanceLightNavigationBars = isLight
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExpressTypography,
        shapes = ExpressShapes,
        content = content
    )
}

/**
 * 计算颜色的感知亮度 (relative luminance approximation)
 * 用于判断状态栏图标的颜色
 */
private fun androidx.compose.ui.graphics.Color.brightness(): Double {
    val r = red.toDouble()
    val g = green.toDouble()
    val b = blue.toDouble()
    return (0.299 * r + 0.587 * g + 0.114 * b)
}

/**
 * 沿 ContextWrapper 链向上查找真正的 Activity。
 * 用于区分 Activity 上下文（可操作 Window）与 Service / ContextThemeWrapper 上下文（不可操作 Window）。
 * - Service 内托管的 ComposeView，context 为 Service，返回 null → 跳过 Window 装饰
 *
 * ChartScreen 等需要操作 Activity.requestedOrientation 的 Composable 复用本函数。
 */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
