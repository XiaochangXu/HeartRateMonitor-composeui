package com.github.heartratemonitor_compose.service

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 悬浮窗外观参数。所有颜色已应用透明度，所有尺寸已应用缩放因子。
 * 由 [FloatingWindowService.updateWindowAppearance] 从 SharedPreferences 读取并计算后赋值，
 * 变更触发 [FloatingWindowContent] 重组。
 *
 * 复刻原 layout_floating_window.xml 的 MaterialCardView + LinearLayout 样式：
 * - textColor / bgColor / borderColor：已应用 alpha（argb 计算）
 * - cornerRadius：圆角半径（dp）
 * - textSize / smallTextSize / iconSize：已应用 scaleFactor / iconScaleFactor
 * - padding：根内边距（basePaddingDp * scaleFactor）
 * - bpmNumberMarginStart：心率数字左间距（heart icon 显隐时不同）
 * - isBpmTextEnabled / isHeartIconEnabled / isSpeedEnabled：显隐开关
 */
data class FloatingWindowAppearance(
    val textColor: Color = Color.Black,
    val bgColor: Color = Color.Black,
    val borderColor: Color = Color.Gray,
    val cornerRadius: Dp = 100.dp,
    val textSize: TextUnit = 16.sp,
    val smallTextSize: TextUnit = 12.sp,
    val iconSize: TextUnit = 18.sp,
    val padding: Dp = 8.dp,
    val bpmNumberMarginStart: Dp = 4.dp,
    val isBpmTextEnabled: Boolean = true,
    val isHeartIconEnabled: Boolean = true,
    val isSpeedEnabled: Boolean = false
)

/**
 * 悬浮窗内容 Composable。复刻原 [layout_floating_window.xml]：
 * MaterialCardView → [Card]；LinearLayout → [Row]；心形 emoji + bpm 数字 + "bpm" + 速度块。
 *
 * 心跳动画在内部用 [Animatable] + [LaunchedEffect] 驱动，替代原 [android.animation.ValueAnimator]。
 * [bpm] / [isAnimationEnabled] / [isConnected] 任一变化时重启 [LaunchedEffect]：
 * - 满足条件（动画启用 + bpm > 30 + 已连接）：1f ↔ 1.2f 循环，单周期 = 60000 / bpm ms
 * - 否则：平滑回归 1f（200ms）
 *
 * 与原实现一致：当动画启用条件不满足时，scale 平滑回到 1f（对应原 `heartIcon.animate().scaleX(1f)`）。
 */
@Composable
fun FloatingWindowContent(
    heartRate: String,
    speed: String,
    bpm: Int,
    isAnimationEnabled: Boolean,
    isConnected: Boolean,
    appearance: FloatingWindowAppearance
) {
    val heartScale = remember { Animatable(1f) }
    LaunchedEffect(bpm, isAnimationEnabled, isConnected) {
        if (isAnimationEnabled && bpm > 30 && isConnected) {
            val durationMs = (60000f / bpm).roundToInt()
            val halfDuration = (durationMs / 2).coerceAtLeast(1)
            while (true) {
                heartScale.animateTo(1.2f, tween(halfDuration, easing = FastOutSlowInEasing))
                heartScale.animateTo(1f, tween(halfDuration, easing = FastOutSlowInEasing))
            }
        } else {
            heartScale.animateTo(1f, tween(200))
        }
    }

    Card(
        shape = RoundedCornerShape(appearance.cornerRadius),
        colors = CardDefaults.cardColors(containerColor = appearance.bgColor),
        border = BorderStroke(1.dp, appearance.borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(appearance.padding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appearance.isHeartIconEnabled) {
                Text(
                    text = "❤️",
                    fontSize = appearance.iconSize,
                    color = appearance.textColor,
                    modifier = Modifier
                        .scale(heartScale.value)
                        .padding(start = 1.dp)
                )
            }
            Text(
                text = heartRate,
                fontSize = appearance.textSize,
                fontWeight = FontWeight.Bold,
                color = appearance.textColor,
                modifier = Modifier.padding(start = appearance.bpmNumberMarginStart)
            )
            if (appearance.isBpmTextEnabled) {
                Text(
                    text = "bpm",
                    fontSize = appearance.smallTextSize,
                    fontWeight = FontWeight.Bold,
                    color = appearance.textColor,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            // 原 XML 中 speed_layout + speed_divider 共同显隐，此处用 AnimatedVisibility 包裹整块
            AnimatedVisibility(
                visible = appearance.isSpeedEnabled,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "|",
                        fontSize = 14.sp,
                        color = appearance.textColor,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )
                    Text(
                        text = speed,
                        fontSize = appearance.textSize,
                        fontWeight = FontWeight.Bold,
                        color = appearance.textColor
                    )
                    Text(
                        text = "km/h",
                        fontSize = appearance.smallTextSize,
                        fontWeight = FontWeight.Bold,
                        color = appearance.textColor,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
    }
}
