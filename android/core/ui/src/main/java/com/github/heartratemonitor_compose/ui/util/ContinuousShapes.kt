package com.github.heartratemonitor_compose.ui.util

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 胶囊形状（普通圆角 50% 实现胶囊效果）。
 * 底部导航栏（FloatingBottomBar / AppBottomNavBar）仍直接使用 com.kyant.capsule.ContinuousCapsule，
 * 不受此处影响。
 */
val CapsuleShape: Shape = RoundedCornerShape(50)

val SheetTopShape: Shape = RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 28.dp
)

val SheetBottomShape: Shape = RoundedCornerShape(
    bottomEnd = 28.dp,
    bottomStart = 28.dp
)

/**
 * 分段列表（组首项大圆角、组内项小圆角）：
 * - 组首项：顶部 28dp 大圆角 + 底部 4dp 小圆角
 * - 组内项：四角 4dp 小圆角
 * - 组末项：顶部 4dp 小圆角 + 底部 28dp 大圆角
 * 项与项之间 2dp 间距露出页面背景，形成首末大圆角、中间小圆角的分段观感。
 */
val SegmentTopShape: Shape = RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 28.dp,
    bottomEnd = 4.dp,
    bottomStart = 4.dp
)

val SegmentMiddleShape: Shape = RoundedCornerShape(4.dp)

val SegmentBottomShape: Shape = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 4.dp,
    bottomEnd = 28.dp,
    bottomStart = 28.dp
)

fun bottomCornerShape(radius: Dp): Shape = RoundedCornerShape(
    topStart = 4.dp,
    topEnd = 4.dp,
    bottomEnd = radius,
    bottomStart = radius
)

/** 统一圆角卡片形状（普通圆角，替代原 ContinuousRoundedRectangle）。 */
fun cardShape(radius: Dp): Shape = RoundedCornerShape(radius)

fun segmentedItemShape(isFirst: Boolean, isLast: Boolean): Shape = when {
    isFirst && isLast -> RoundedCornerShape(28.dp)
    isFirst -> SegmentTopShape
    isLast -> SegmentBottomShape
    else -> SegmentMiddleShape
}
