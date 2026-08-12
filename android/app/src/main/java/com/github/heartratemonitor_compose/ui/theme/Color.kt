package com.github.heartratemonitor_compose.ui.theme

import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color

// M3强调色（当动态取色不可用时） ──
// 默认使用预设色卡中的蓝色（#1B6EF3）通过 MaterialKolor TonalSpot 生成，
// 保证 Android 11 及以下无 Monet 设备仍有一致的蓝色主题，且不会开启自定义主题模式。
val ExpressPrimaryLight = Color(0xFF0057CC)
val ExpressOnPrimaryLight = Color(0xFFFFFFFF)
val ExpressPrimaryContainerLight = Color(0xFFD9E2FF)
val ExpressOnPrimaryContainerLight = Color(0xFF00419C)
val ExpressSecondaryLight = Color(0xFF575E71)
val ExpressOnSecondaryLight = Color(0xFFFFFFFF)
val ExpressSecondaryContainerLight = Color(0xFFDCE2F9)
val ExpressOnSecondaryContainerLight = Color(0xFF404659)
val ExpressSurfaceLight = Color(0xFFFAF8FF)
val ExpressOnSurfaceLight = Color(0xFF1A1B20)
val ExpressSurfaceVariantLight = Color(0xFFE1E2EC)
val ExpressOnSurfaceVariantLight = Color(0xFF44464F)
val ExpressBackgroundLight = Color(0xFFFAF8FF)
val ExpressOnBackgroundLight = Color(0xFF1A1B20)
// M3 surfaceDim：app 大背景，中性色略暗于 surface（与卡片形成色阶对比，不偏色相）
val ExpressSurfaceDimLight = Color(0xFFDAD9E0)
val ExpressErrorLight = Color(0xFFBA1A1A)
val ExpressOutlineLight = Color(0xFF757780)
val ExpressOutlineVariantLight = Color(0xFFC5C6D0)
// M3 Surface Container 色调层级（中性灰色阶，与 surfaceDim 形成由暗到亮的递进）
// 顺序：surfaceDim(暗) → surfaceContainerLow → surfaceContainer → surfaceContainerHigh → surfaceContainerHighest(亮) → surface(最亮)
val ExpressSurfaceContainerLowestLight = Color(0xFFFFFFFF)
val ExpressSurfaceContainerLowLight = Color(0xFFF4F3FA)
val ExpressSurfaceContainerLight = Color(0xFFEEEDF4)
val ExpressSurfaceContainerHighLight = Color(0xFFE8E7EF)
val ExpressSurfaceContainerHighestLight = Color(0xFFE2E2E9)

val ExpressPrimaryDark = Color(0xFFB0C6FF)
val ExpressOnPrimaryDark = Color(0xFF002C6F)
val ExpressPrimaryContainerDark = Color(0xFF00419C)
val ExpressOnPrimaryContainerDark = Color(0xFFD9E2FF)
val ExpressSecondaryDark = Color(0xFFC0C6DC)
val ExpressOnSecondaryDark = Color(0xFF2A3042)
val ExpressSecondaryContainerDark = Color(0xFF404659)
val ExpressOnSecondaryContainerDark = Color(0xFFDCE2F9)
val ExpressSurfaceDark = Color(0xFF121318)
val ExpressOnSurfaceDark = Color(0xFFE2E2E9)
val ExpressSurfaceVariantDark = Color(0xFF44464F)
val ExpressOnSurfaceVariantDark = Color(0xFFC5C6D0)
val ExpressBackgroundDark = Color(0xFF121318)
val ExpressOnBackgroundDark = Color(0xFFE2E2E9)
// M3 surfaceDim：app 大背景，中性深色（与卡片形成色阶对比，不偏色相）
val ExpressSurfaceDimDark = Color(0xFF121318)
val ExpressErrorDark = Color(0xFFFFB4AB)
val ExpressOutlineDark = Color(0xFF8F9099)
val ExpressOutlineVariantDark = Color(0xFF44464F)
// M3 Surface Container 色调层级（中性灰色阶）
val ExpressSurfaceContainerLowestDark = Color(0xFF0C0E13)
val ExpressSurfaceContainerLowDark = Color(0xFF1A1B20)
val ExpressSurfaceContainerDark = Color(0xFF1E1F25)
val ExpressSurfaceContainerHighDark = Color(0xFF282A2F)
val ExpressSurfaceContainerHighestDark = Color(0xFF33343A)

// ── 语义化功能色（不跟随主题，固定值） ──

// 心率图表/卡片专用颜色
val HeartRateLineColor = Color(0xFFE53935)
val HeartIconColor = Color(0xFFFF0000)

// 蓝牙信号强度颜色
val SignalStrongColor = Color(0xFF00668B)
val SignalMediumColor = Color(0xFFF59E0B)
val SignalWeakColor = Color(0xFFB00020)
