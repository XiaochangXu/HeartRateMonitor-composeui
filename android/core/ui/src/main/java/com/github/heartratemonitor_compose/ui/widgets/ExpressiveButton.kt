package com.github.heartratemonitor_compose.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.ui.util.cardShape

/**
 * 表现力按钮：按压时弹簧缩放 + 微阴影浮起 + 涟漪反馈。
 *
 * 与 CapsuleSegmentedButton 同一设计语言：shape 保持静态（20dp 胶囊圆角），
 * 弹性形变通过 [Modifier.scale] + spring 实现，涟漪由 Button 内部 Surface 的
 * ripple() 正常渲染（读取全局 LocalRippleConfiguration）。
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "expressiveButtonScale"
    )
    val (containerColor, contentColor) = when (style) {
        ExpressiveButtonStyle.Primary -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        ExpressiveButtonStyle.Danger -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
    }
    Button(
        onClick = onClick,
        modifier = modifier.scale(scale),
        shape = cardShape(20.dp),
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
 * 表现力文字按钮：按压时弹簧缩放 + 涟漪反馈。
 *
 * 与 [ExpressiveButton] 配对使用，用于 BottomSheet 弹窗的取消/关闭等次级操作。
 * - 默认透明底 + 文字色
 * - 按压时弹簧缩放到 0.94，涟漪由 TextButton 内部 Surface 的 ripple() 正常渲染
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
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "expressiveTextButtonScale"
    )
    TextButton(
        onClick = onClick,
        modifier = modifier.scale(scale),
        shape = cardShape(20.dp),
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
