package com.github.heartratemonitor_compose.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

/**
 * 分段选择器的单个选项数据。
 *
 * @param label   显示文本
 * @param icon    可选的前置图标
 * @param value   该选项对应的值（用于比较选中态）
 */
data class SegmentOption<T>(
    val label: String,
    val icon: ImageVector? = null,
    val value: T
)

/**
 * 独立胶囊式分段选择器 —— 使用 MD3E ToggleButton，
 * 取消硬分割线，靠 ConnectedSpaceBetween 间距分隔，
 * 每个选项使用 connectedShapes 形成连接胶囊，点击时带弹簧物理回弹动效。
 *
 * @param options          选项列表
 * @param selectedValue    当前选中的值
 * @param onOptionSelected 选中回调
 * @param modifier         外层 Row 修饰
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> CapsuleSegmentedButton(
    options: List<SegmentOption<T>>,
    selectedValue: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = options.indexOfFirst { it.value == selectedValue }
        .coerceAtLeast(0)

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            CapsuleToggleButton(
                option = option,
                checked = selectedIndex == index,
                onCheckedChange = { onOptionSelected(option.value) },
                index = index,
                count = options.size,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/* ── 单个 ToggleButton ─────────────────────────── */

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> CapsuleToggleButton(
    option: SegmentOption<T>,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    /* 弹簧缩放：按下 → 0.94f，松开 → 1f（dampingRatio 0.45 产生回弹） */
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.45f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "capsuleScale"
    )

    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
            .semantics { role = Role.RadioButton }
            .scale(scale),
        shapes = when (index) {
            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
            count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
        },
        interactionSource = interactionSource
    ) {
        if (option.icon != null) {
            Icon(imageVector = option.icon, contentDescription = null)
            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
        }
        Text(text = option.label, overflow = TextOverflow.Ellipsis, maxLines = 1)
    }
}
