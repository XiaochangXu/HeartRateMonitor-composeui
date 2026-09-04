package com.github.heartratemonitor_compose.service

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.service.R

/**
 * 悬浮窗静态视觉参数。[heartScale] 在 graphicsLayer draw-phase 读取，
 * scale 变化只触发 draw layer 更新，不触发 recomposition + relayout。
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

@Composable
fun FloatingWindowContent(
    heartRate: String,
    speed: String,
    heartScale: () -> Float,
    appearance: FloatingWindowAppearance
) {
    val iconSizeDp = with(LocalDensity.current) { appearance.iconSize.toDp() }

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
                        .graphicsLayer { scaleX = heartScale(); scaleY = heartScale() }
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
