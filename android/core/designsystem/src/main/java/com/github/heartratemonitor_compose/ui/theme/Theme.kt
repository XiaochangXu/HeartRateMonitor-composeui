package com.github.heartratemonitor_compose.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.kyant.capsule.ContinuousRoundedRectangle
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
 *
 * 全部采用 ContinuousRoundedRectangle（G2 连续曲率，iOS 风），
 * 与底部导航栏 ContinuousCapsule 同源，圆角曲率过渡全 App 统一。
 */
val ExpressShapes = Shapes(
    extraSmall = ContinuousRoundedRectangle(4.dp),
    small = ContinuousRoundedRectangle(8.dp),
    medium = ContinuousRoundedRectangle(16.dp),
    large = ContinuousRoundedRectangle(24.dp),
    extraLarge = ContinuousRoundedRectangle(28.dp)
)

/**
 * HeartRateMonitorMobile M3 Expressive 主题（无状态版本，:core:designsystem）。
 *
 * 主题决策由调用方（:app 组合根侧的 AppTheme 薄包装）提供：
 * - **config**（[ThemeConfig]）：色彩来源 / 明暗模式 / 自定义 seed 色与 MaterialKolor variant；
 * - **customSchemeCache**：进程级 ColorScheme 缓存，同 seed/明暗/style 只算一次 HCT 转换。
 *
 * 计算规则（与迁移前逐字等价）：
 * - source == CUSTOM：经 customSchemeCache 从 seed 生成 ColorScheme（切断系统壁纸联动）；
 * - Android 12+：跟随系统 `dynamicLight/DarkColorScheme` 取壁纸色；
 * - 低版本：回退到预设的 Expressive 静态方案。
 *
 * Activity 与 Services（FloatingWindowService / StatusBarResidentService）共用同一
 * ThemeState 实例（同进程），任一调用方修改主题后全 App 即时重配色。
 */
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

    // 自定义配色同步计算 + 进程级缓存：同 seed/明暗/style 只算一次 HCT 转换（数毫秒），
    // 冷启动首帧即得自定义色。旧实现用 produceState 异步计算，空窗期回退到蓝色 Express
    // 方案，造成"先进页面闪一下蓝色再变自定义色"的观感。
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

    // 仅 Activity 上下文可操作 Window；Service 内托管的 ComposeView 上下文为 Service，跳过
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val window = activity.window
                // statusBarColor/navigationBarColor 在 API 35 被弃用（edge-to-edge 默认强制），
                // 但旧版本仍需设置透明色实现沉浸式效果
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExpressTypography,
        shapes = ExpressShapes,
        content = content
    )
}

/**
 * 自定义 ColorScheme 进程级缓存。
 *
 * 以 (seed, 明暗, style) 为键缓存 MaterialKolor 生成结果：Activity、悬浮窗、
 * 状态栏服务同进程共享，同配置只算一次 HCT 转换，切换主题后再切回也零开销。
 *
 * Hilt 单例（Phase 2 起由 Hilt 装配，替代 AppContainer）。
 */
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

/** 计算颜色的感知亮度 (relative luminance approximation) */
private fun androidx.compose.ui.graphics.Color.brightness(): Double {
    val r = red.toDouble()
    val g = green.toDouble()
    val b = blue.toDouble()
    return (0.299 * r + 0.587 * g + 0.114 * b)
}

/**
 * 沿 ContextWrapper 链向上查找真正的 Activity。
 * Service 内托管的 ComposeView，context 为 Service，返回 null → 跳过 Window 装饰。
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}