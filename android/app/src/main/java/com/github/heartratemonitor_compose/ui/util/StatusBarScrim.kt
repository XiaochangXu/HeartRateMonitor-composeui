package com.github.heartratemonitor_compose.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * 状态栏渐变遮罩（官方 edge-to-edge scrim 方案）。
 *
 * 从表面色渐变到透明，高度约为状态栏的 1.2 倍，作为内容滚动层之上的覆盖层，
 * 使接近顶栏的内容平滑淡出、保证顶栏标题与状态栏图标可读。
 * 颜色跟随 [MaterialTheme] 自动适配亮暗主题。
 */
@Composable
fun StatusBarScrim(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface
) {
    val density = LocalDensity.current
    val height = with(density) {
        (WindowInsets.statusBars.getTop(density) * 1.2f).toDp()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.95f),
                    0.5f to color.copy(alpha = 0.6f),
                    1f to Color.Transparent
                )
            )
    )
}
