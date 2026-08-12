package com.github.heartratemonitor_compose.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 将图标包裹在圆形彩色背景中，
 * 提升视觉层级和功能区分度。
 *
 * 典型用法：40dp 圆形 primaryContainer 背景 + 24dp onPrimaryContainer 图标。
 *
 * @param icon       Painter 图标
 * @param modifier   外部修饰符（注意：size 会被 containerSize 覆盖）
 * @param containerSize 容器直径，默认 40dp
 * @param iconSize   图标尺寸，默认 24dp
 * @param containerColor 容器背景色，默认 primaryContainer
 * @param iconTint   图标着色，默认 onPrimaryContainer
 */
@Composable
fun IconContainer(
    icon: Painter,
    modifier: Modifier = Modifier,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier.size(containerSize),
        shape = CircleShape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconTint
            )
        }
    }
}

/**
 * [ImageVector] 重载，支持 Material Icons 等矢量图标。
 */
@Composable
fun IconContainer(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier.size(containerSize),
        shape = CircleShape,
        color = containerColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconTint
            )
        }
    }
}
