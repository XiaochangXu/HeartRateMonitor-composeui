package com.github.heartratemonitor_compose.ui.util

import androidx.compose.foundation.shape.ZeroCornerSize
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

fun bottomCornerShape(radius: Dp): Shape = ContinuousRoundedRectangle(
    bottomEnd = radius,
    bottomStart = radius
)

/** 统一圆角卡片形状（continuous 圆角，替代原 RoundedCornerShape）。 */
fun cardShape(radius: Dp): Shape = ContinuousRoundedRectangle(radius)

fun segmentedItemShape(isFirst: Boolean, isLast: Boolean): Shape = when {
    isFirst && isLast -> ContinuousRoundedRectangle(28.dp)
    isFirst -> SheetTopShape
    isLast -> SheetBottomShape
    else -> ContinuousRoundedRectangle(
        topStart = ZeroCornerSize,
        topEnd = ZeroCornerSize,
        bottomEnd = ZeroCornerSize,
        bottomStart = ZeroCornerSize
    )
}
