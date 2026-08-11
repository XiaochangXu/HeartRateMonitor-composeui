package com.github.heartratemonitor_compose.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.theme.HeartIconColor

/**
 * 心率卡片：
 * 使用 surfaceContainerHigh 背景 + onSurface 文字（不跟随 seed 色，仅跟随亮/暗模式）。
 * 心跳动画作用于背景爱心 emoji，文字不跳动。
 * 右上角显示本次连接的心率最大值/最小值（断开即清零）。
 */
@Composable
internal fun HeartRateCard(
    modifier: Modifier,
    heartRate: Int,
    appStatus: AppStatus,
    isAnimationEnabled: Boolean,
    sessionMaxHr: Int,
    sessionMinHr: Int
) {
    val isConnected = appStatus == AppStatus.CONNECTED

    // 中性色令牌：不跟随 seed 色，仅跟随亮/暗模式（亮色黑、暗色白）
    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val contentColor = MaterialTheme.colorScheme.onSurface

    // 心跳动画：bpm > 30 且开启动画且已连接时缩放（作用于背景爱心，文字不跳动）
    // 缩放范围 1.0 → 1.15，配合 96dp 图标尺寸：最大 ~110dp，在 150dp 高的卡片内有充足余量，不会被 Surface 圆角裁剪
    val heartScale = remember { Animatable(1f) }
    val shouldAnimate = isAnimationEnabled && heartRate > 30 && isConnected
    // 量化 bpm 到 5 步长，减少动画重启频率
    val animBpm = if (shouldAnimate) (heartRate / 5) * 5 else 0
    LaunchedEffect(animBpm) {
        if (animBpm > 0) {
            val cycleMs = (60000f / animBpm).toLong()
            while (true) {
                heartScale.animateTo(1.15f, tween((cycleMs / 2).toInt(), easing = FastOutSlowInEasing))
                heartScale.animateTo(1f, tween((cycleMs / 2).toInt(), easing = FastOutSlowInEasing))
            }
        } else {
            heartScale.animateTo(1f, tween(200))
        }
    }

    Surface(
        modifier = modifier.height(150.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor
    ) {
        Box {
            // 背景爱心图标（纯红 + 心跳缩放动画）
            // 使用矢量图标：精确控制尺寸 96dp，缩放 1.15x ≈ 110dp，在 150dp 卡片内不会被圆角裁剪
            // 已连接：Icons.Filled.Favorite（完整爱心）；断开：Icons.Filled.HeartBroken（裂成两半的实心爱心）
            // tint 使用纯红 Color(0xFFFF0000)，alpha 0.35 保留含蓄感
            Icon(
                imageVector = if (isConnected) Icons.Filled.Favorite
                              else Icons.Filled.HeartBroken,
                contentDescription = null,
                tint = HeartIconColor,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .alpha(if (isConnected) 0.35f else 0.25f)
                    .graphicsLayer {
                        scaleX = heartScale.value
                        scaleY = heartScale.value
                    }
            )

            if (isConnected && sessionMaxHr > 0) {
                Text(
                    text = "MAX ${sessionMaxHr}",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    color = contentColor.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            if (isConnected && sessionMinHr > 0) {
                Text(
                    text = "MIN ${sessionMinHr}",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    color = contentColor.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            // bpm 数值（文字不跳动）
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (isConnected && heartRate > 0) "$heartRate" else "--",
                    color = contentColor,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "bpm",
                    color = contentColor.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

/**
 * 速度卡片：与 HeartRateCard 视觉风格一致。
 * - Surface 容器 + surfaceContainerHigh 背景 + onSurface 内容色（不跟随 seed 色）
 * - 20dp 圆角，与首页其他卡片统一（项目规范：一级卡片 20dp 圆角 + 0dp 阴影）
 * - [isActive]=false 时仅显示 "--"，颜色与已激活时一致（不做颜色区分）
 */
@Composable
internal fun SpeedCard(
    modifier: Modifier,
    speed: Float,
    isActive: Boolean
) {
    Surface(
        modifier = modifier.height(150.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.speed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isActive) "%.1f".format(speed) else "--",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.End)
            )
        }
    }
}
