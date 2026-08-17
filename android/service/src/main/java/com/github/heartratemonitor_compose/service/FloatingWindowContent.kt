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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.service.R
import kotlin.math.roundToInt

/**
 * 复刻原 layout_floating_window.xml 的 MaterialCardView + LinearLayout 样式。
 * 由 [FloatingWindowService.updateWindowAppearance] 从设置读取并计算后赋值，
 * 变更触发 [FloatingWindowContent] 重组。
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
 * 心跳动画在内部用 [Animatable] + [LaunchedEffect] 驱动，替代原 [android.animation.ValueAnimator]。
 * 仅 [isAnimationEnabled] / [isConnected] 变化时重启 [LaunchedEffect]；bpm 从 key 中移除，
 * 避免心率每秒变化导致协程反复取消重启（拖拽悬浮窗时与 updateViewLayout 争抢主线程造成卡顿）。
 * bpm 经 [androidx.compose.runtime.rememberUpdatedState] 包装后在循环内部读取，每周期自动用最新值
 * 计算动画时长；循环条件含 bpm 判断，bpm 降至 30 以下时当前周期结束后退出循环并平滑回归 1f。
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
    val iconSizeDp = with(LocalDensity.current) { appearance.iconSize.toDp() }
    // rememberUpdatedState：LaunchedEffect 不以 bpm 为 key，协程不重启，
    // 但闭包内直接读 bpm 会捕获首次组合时的值（Int 值类型），后续心率变化读不到。
    // 经 rememberUpdatedState 包装后，每次重组都更新 State<Int>，循环内读 .value 始终为最新心率。
    val currentBpm by rememberUpdatedState(bpm)
    LaunchedEffect(isAnimationEnabled, isConnected) {
        if (isAnimationEnabled && currentBpm > 30 && isConnected) {
            while (isAnimationEnabled && currentBpm > 30 && isConnected) {
                val durationMs = (60000f / currentBpm).roundToInt()
                val halfDuration = (durationMs / 2).coerceAtLeast(1)
                heartScale.animateTo(1.2f, tween(halfDuration, easing = FastOutSlowInEasing))
                heartScale.animateTo(1f, tween(halfDuration, easing = FastOutSlowInEasing))
            }
            heartScale.animateTo(1f, tween(200))
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
                Icon(
                    painter = painterResource(R.drawable.ic_heart),
                    contentDescription = null,
                    tint = appearance.textColor,
                    modifier = Modifier
                        .size(iconSizeDp)
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
