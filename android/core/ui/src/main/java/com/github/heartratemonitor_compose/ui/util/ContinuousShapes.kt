package com.github.heartratemonitor_compose.ui.util

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle

/** 胶囊形状（G2 连续曲率，与底部导航栏 ContinuousCapsule 同源）。 */
val CapsuleShape: Shape = ContinuousCapsule

val SheetTopShape: Shape = ContinuousRoundedRectangle(
    topStart = 28.dp,
    topEnd = 28.dp
)

val SheetBottomShape: Shape = ContinuousRoundedRectangle(
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
val SegmentTopShape: Shape = ContinuousRoundedRectangle(
    topStart = 28.dp,
    topEnd = 28.dp,
    bottomEnd = 4.dp,
    bottomStart = 4.dp
)

val SegmentMiddleShape: Shape = ContinuousRoundedRectangle(4.dp)

val SegmentBottomShape: Shape = ContinuousRoundedRectangle(
    topStart = 4.dp,
    topEnd = 4.dp,
    bottomEnd = 28.dp,
    bottomStart = 28.dp
)

fun bottomCornerShape(radius: Dp): Shape = ContinuousRoundedRectangle(
    bottomEnd = radius,
    bottomStart = radius
)

/** 统一圆角卡片形状（continuous 圆角，替代原 RoundedCornerShape）。 */
fun cardShape(radius: Dp): Shape = ContinuousRoundedRectangle(radius)

fun segmentedItemShape(isFirst: Boolean, isLast: Boolean): Shape = when {
    isFirst && isLast -> ContinuousRoundedRectangle(28.dp)
    isFirst -> SegmentTopShape
    isLast -> SegmentBottomShape
    else -> SegmentMiddleShape
}
