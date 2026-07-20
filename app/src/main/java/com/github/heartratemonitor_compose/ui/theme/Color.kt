package com.github.heartratemonitor_compose.ui.theme

import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color

// ── M3 Expressive 强调色（当动态取色不可用时） ──
val ExpressPrimaryLight = Color(0xFF006B5E)
val ExpressOnPrimaryLight = Color(0xFFFFFFFF)
val ExpressPrimaryContainerLight = Color(0xFF57F7E0)
val ExpressOnPrimaryContainerLight = Color(0xFF00201B)
val ExpressSecondaryLight = Color(0xFF4A635D)
val ExpressOnSecondaryLight = Color(0xFFFFFFFF)
val ExpressSecondaryContainerLight = Color(0xFFCCE9E1)
val ExpressOnSecondaryContainerLight = Color(0xFF06201B)
val ExpressSurfaceLight = Color(0xFFFCFCFD)
val ExpressOnSurfaceLight = Color(0xFF1C1B1F)
val ExpressSurfaceVariantLight = Color(0xFFE0E2EA)
val ExpressOnSurfaceVariantLight = Color(0xFF44474E)
val ExpressBackgroundLight = Color(0xFFFCFCFD)
val ExpressOnBackgroundLight = Color(0xFF1C1B1F)
// M3 surfaceDim：app 大背景，中性色略暗于 surface（与卡片形成色阶对比，不偏色相）
val ExpressSurfaceDimLight = Color(0xFFDEDCE0)
val ExpressErrorLight = Color(0xFFBA1A1A)
val ExpressOutlineLight = Color(0xFF6E7C79)
val ExpressOutlineVariantLight = Color(0xFFBECDCA)
// M3 Surface Container 色调层级（中性灰色阶，与 surfaceDim 形成由暗到亮的递进）
// 顺序：surfaceDim(暗) → surfaceContainerLow → surfaceContainer → surfaceContainerHigh → surfaceContainerHighest(亮) → surface(最亮)
val ExpressSurfaceContainerLowestLight = Color(0xFFFFFFFF)
val ExpressSurfaceContainerLowLight = Color(0xFFF0F1F4)
val ExpressSurfaceContainerLight = Color(0xFFE9E9EC)
val ExpressSurfaceContainerHighLight = Color(0xFFE3E2E6)
val ExpressSurfaceContainerHighestLight = Color(0xFFDEDDDF)

val ExpressPrimaryDark = Color(0xFF38DAC4)
val ExpressOnPrimaryDark = Color(0xFF00382F)
val ExpressPrimaryContainerDark = Color(0xFF005046)
val ExpressOnPrimaryContainerDark = Color(0xFF57F7E0)
val ExpressSecondaryDark = Color(0xFFB1CCC5)
val ExpressOnSecondaryDark = Color(0xFF1C3530)
val ExpressSecondaryContainerDark = Color(0xFF324B46)
val ExpressOnSecondaryContainerDark = Color(0xFFCCE9E1)
val ExpressSurfaceDark = Color(0xFF131316)
val ExpressOnSurfaceDark = Color(0xFFE6E1E9)
val ExpressSurfaceVariantDark = Color(0xFF47464F)
val ExpressOnSurfaceVariantDark = Color(0xFFCAC4D0)
val ExpressBackgroundDark = Color(0xFF131316)
val ExpressOnBackgroundDark = Color(0xFFE6E1E9)
// M3 surfaceDim：app 大背景，中性深色（与卡片形成色阶对比，不偏色相）
val ExpressSurfaceDimDark = Color(0xFF0E0E11)
val ExpressErrorDark = Color(0xFFFFB4AB)
val ExpressOutlineDark = Color(0xFF889A94)
val ExpressOutlineVariantDark = Color(0xFF424F4B)
// M3 Surface Container 色调层级（中性灰色阶）
val ExpressSurfaceContainerLowestDark = Color(0xFF0E0E11)
val ExpressSurfaceContainerLowDark = Color(0xFF1B1B1F)
val ExpressSurfaceContainerDark = Color(0xFF1F1F23)
val ExpressSurfaceContainerHighDark = Color(0xFF2A2A2E)
val ExpressSurfaceContainerHighestDark = Color(0xFF353539)
