package com.github.heartratemonitor_compose.ui.widgets

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.ui.util.cardShape

/**
 * 表现力按钮：按压时圆角弹性收缩 + 微阴影浮起。
 *
 * 设计灵感来自心率预警页面的 CalibrationButton：
 * - 默认胶囊圆角（20dp）+ filled primary 底
 * - 按压时圆角以 fastSpatial spring（damping 0.6 / stiffness 800）平滑收缩到 8dp
 * - 同时浮现 1dp 阴影
 *
 * 三种风格变体：
 * - [ExpressiveButtonStyle.Primary]：primary 底 + onPrimary 文字（默认，用于确认/保存等主操作）
 * - [ExpressiveButtonStyle.Danger]：error 底 + onError 文字（用于删除/清除等危险操作）
 *
 * @param label 按钮文字
 * @param onClick 点击回调
 * @param modifier Modifier
 * @param style 按钮风格
 */
@Composable
fun ExpressiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ExpressiveButtonStyle = ExpressiveButtonStyle.Primary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 8.dp else 20.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "expressiveButtonCorner"
    )
    val (containerColor, contentColor) = when (style) {
        ExpressiveButtonStyle.Primary -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        ExpressiveButtonStyle.Danger -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = cardShape(cornerRadius),
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 1.dp
        )
    ) {
        Text(label)
    }
}

/**
 * 表现力文字按钮：按压时圆角弹性收缩 + 背景浮现。
 *
 * 与 [ExpressiveButton] 配对使用，用于 BottomSheet 弹窗的取消/关闭等次级操作。
 * - 默认透明底 + 文字色
 * - 按压时圆角收缩 + 文字色 12% 透明度背景浮现
 *
 * @param label 按钮文字
 * @param onClick 点击回调
 * @param modifier Modifier
 * @param color 文字颜色（默认 onSurfaceVariant，危险操作可传 error）
 */
@Composable
fun ExpressiveTextButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 8.dp else 20.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "expressiveTextButtonCorner"
    )
    TextButton(
        onClick = onClick,
        modifier = modifier,
        shape = cardShape(cornerRadius),
        interactionSource = interactionSource,
        colors = ButtonDefaults.textButtonColors(
            contentColor = color
        )
    ) {
        Text(label)
    }
}

/** [ExpressiveButton] 的风格变体。 */
enum class ExpressiveButtonStyle {
    /** 主操作：primary 底 + onPrimary 文字。 */
    Primary,

    /** 危险操作：error 底 + onError 文字。 */
    Danger
}
