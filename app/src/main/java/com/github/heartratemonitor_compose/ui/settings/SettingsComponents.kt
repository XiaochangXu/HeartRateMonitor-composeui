package com.github.heartratemonitor_compose.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Material 3 Icon Container：当 [containerColor] 非透明时，将图标包裹在 40dp 彩色圆形背景中，
 * 提升视觉层级和功能区分度。图标颜色使用 [tint]（默认跟随 onSurfaceVariant）。
 */
@Composable
private fun LeadingIcon(
    icon: Painter,
    containerColor: Color = Color.Transparent,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    if (containerColor != Color.Transparent) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = containerColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = tint
                )
            }
        }
    } else {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = tint
        )
    }
    Spacer(Modifier.width(16.dp))
}

/**
 * 分组标题。
 */
@Composable
internal fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

/**
 * 分组容器：用 Column 包裹同组的多个独立设置项卡片，
 * 项之间用 2dp 间隔分隔（露出背景），取代传统的分割线设计。
 * 这是 Material 3 分段列表（Segmented List）模式的变体，
 * 每个设置项是独立的 Surface 卡片，视觉上既分组又分离。
 */
@Composable
internal fun SettingsGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content
    )
}

/**
 * 独立设置项卡片：根据在分组中的位置应用不同的圆角形状。
 * - [isFirst] && [isLast]：四角全圆角（单独一项）
 * - [isFirst]：顶部圆角，底部直角（首项）
 * - [isLast]：底部圆角，顶部直角（末项）
 * - 都不传：四角直角（中间项，长方形）
 * 卡片之间有 2dp 间隙，背景透过间隙显示，形成"分段式卡片组"视觉效果。
 * - [onClick] 非空时整张卡片可点击，ripple 被 Surface 的 clip 裁剪到圆角内。
 * - 最小高度 56dp，与 MD3 列表规范一致。
 */
@Composable
internal fun SettingsItem(
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = when {
        isFirst && isLast -> RoundedCornerShape(28.dp)
        isFirst -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        isLast -> RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
        else -> RoundedCornerShape(0.dp)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    // 先 clip 成卡片形状，再挂 clickable，
                    // 否则 ripple / 长按激活范围会超出圆角变成矩形
                    Modifier.clip(shape).clickable(onClick = onClick)
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

@Composable
internal fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String? = null,
    leadingIcon: Painter? = null,
    leadingIconContainerColor: Color = Color.Transparent,
    leadingIconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            LeadingIcon(leadingIcon, leadingIconContainerColor, leadingIconTint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
internal fun SettingsLink(
    title: String,
    icon: ImageVector? = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    leadingIcon: Painter? = null,
    leadingIconContainerColor: Color = Color.Transparent,
    leadingIconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    subtitle: String? = null
) {
    // 纯展示组件：不在此处处理点击。
    // 点击由外层 SettingsItem(onClick = ...) 承担，使 ripple 覆盖整行
    // （含水平 16dp 边距与圆角处），而非仅覆盖被 Column padding 包裹的 Row 中间区域。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            LeadingIcon(leadingIcon, leadingIconContainerColor, leadingIconTint)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun DragSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    suffix: String = "",
    enabled: Boolean = true,
    leadingIcon: Painter? = null,
    leadingIconContainerColor: Color = Color.Transparent,
    leadingIconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    var internalValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var sliderSize by remember { mutableStateOf(IntSize.Zero) }

    val fraction = if (range.last > range.first)
        (internalValue - range.first) / (range.last - range.first) else 0f

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            if (leadingIcon != null) {
                LeadingIcon(leadingIcon, leadingIconContainerColor, leadingIconTint)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .onGloballyPositioned { sliderSize = it.size }
        ) {
            // 拖拽时浮现在手柄上方的数值气泡
            if (isDragging && sliderSize.width > 0) {
                val density = LocalDensity.current
                val thumbCenterX = with(density) {
                    val trackStart = 16.dp.toPx()
                    val trackWidth = sliderSize.width.toFloat() - 32.dp.toPx()
                    trackStart + trackWidth * fraction
                }
                Surface(
                    modifier = Modifier
                        .offset(
                            x = with(density) { thumbCenterX.toDp() } - 22.dp,
                            y = (-12).dp
                        ),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "${internalValue.toInt()}$suffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Slider(
                modifier = Modifier.align(Alignment.BottomCenter),
                value = internalValue,
                onValueChange = {
                    internalValue = it
                    isDragging = true
                    onValueChange(it.toInt())
                },
                onValueChangeFinished = { isDragging = false },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = 0,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledThumbColor = MaterialTheme.colorScheme.outline,
                    disabledActiveTrackColor = MaterialTheme.colorScheme.outline,
                    disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
internal fun ColorPreviewButton(
    label: String,
    color: Int,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(color),
            onClick = onClick
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
