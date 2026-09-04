package com.github.heartratemonitor_compose.ui.util

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 胶囊形状（圆角 50%）；底部导航栏仍用 ContinuousCapsule。
val CapsuleShape: Shape = RoundedCornerShape(50)

val SheetTopShape: Shape = RoundedCornerShape(
    topStart = 28.dp,
    topEnd = 28.dp
)

val SheetBottomShape: Shape = RoundedCornerShape(
    bottomEnd = 28.dp,
    bottomStart = 28.dp
)

// 分段列表形状：首末项大圆角 28dp、中间项 4dp；2dp 间距露出背景形成分段观感。
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

fun cardShape(radius: Dp): Shape = RoundedCornerShape(radius)

fun segmentedItemShape(isFirst: Boolean, isLast: Boolean): Shape = when {
    isFirst && isLast -> RoundedCornerShape(28.dp)
    isFirst -> SegmentTopShape
    isLast -> SegmentBottomShape
    else -> SegmentMiddleShape
}
