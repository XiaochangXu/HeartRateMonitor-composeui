package com.github.heartratemonitor_compose.ui.alarm

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.alarm.R
import com.github.heartratemonitor_compose.service.posture.PostureCalibration
import com.github.heartratemonitor_compose.service.posture.PostureType
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButton
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButtonStyle

/**
 * 姿态检测区：
 * 当前姿态展示卡片 + 姿态校准卡片。
 * 动画全部在卡片内部管理，页面关闭/隐藏时随 Composable 生命周期自动停止。
 */

/**
 * 当前姿态展示卡片：姿态图标弹跳动画 + 三姿态指示器。
 *
 * popAnim：姿态变化时图标从 0.7 缩放到 1.0 的弹跳效果。
 * bounceOffset：运动姿态时图标上下弹跳的无限动画，仅在 EXERCISE 时运行。
 */
@Composable
internal fun PostureCard(
    posture: PostureType
) {
    // popAnim：姿态变化时弹跳，由 LaunchedEffect(posture) 驱动
    val popAnim = remember { Animatable(0.7f) }
    LaunchedEffect(posture) {
        popAnim.snapTo(0.7f)
        popAnim.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
    }

    // bounceOffset：仅运动姿态时才创建无限弹跳动画
    val isExercise = posture == PostureType.EXERCISE
    val bounceOffset: Float
    if (isExercise) {
        val infiniteTransition = rememberInfiniteTransition(label = "bounce")
        bounceOffset = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -20f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bounceOffset"
        ).value
    } else {
        bounceOffset = 0f
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(posture.iconRes),
                contentDescription = stringResource(posture.labelRes),
                modifier = Modifier
                    .size(64.dp)
                    .scale(popAnim.value)
                    .offset(y = bounceOffset.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(posture.labelRes),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PostureIndicator(stringResource(com.github.heartratemonitor_compose.service.R.string.sitting), PostureType.SITTING.iconRes, posture == PostureType.SITTING)
                PostureIndicator(stringResource(com.github.heartratemonitor_compose.service.R.string.standing), PostureType.STANDING.iconRes, posture == PostureType.STANDING)
                PostureIndicator(stringResource(com.github.heartratemonitor_compose.service.R.string.exercise), PostureType.EXERCISE.iconRes, posture == PostureType.EXERCISE)
            }
        }
    }
}

@Composable
private fun PostureIndicator(label: String, iconRes: Int, isActive: Boolean) {
    val alphaValue = if (isActive) 1f else 0.3f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = label,
            modifier = Modifier
                .size(24.dp)
                .alpha(alphaValue),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(alphaValue)
        )
    }
}


/**
 * 姿态校准卡片：校准中显示波浪进度，空闲时显示校准按钮与状态。
 *
 * 进度条用 [Animatable] 平滑追赶服务端每秒递增的 calibrationProgress，
 * 替代原 16ms 手搓轮询：服务端 setState 频率从 60fps 降到 1fps，
 * 动画由 Compose 自动插值，无额外状态写入。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CalibrationCard(
    calibration: PostureCalibration?,
    isCalibrating: Boolean,
    calibratingPostureName: String,
    calibrationProgress: Int,
    onCalibrateSitting: () -> Unit,
    onCalibrateStanding: () -> Unit,
    onClearCalibration: () -> Unit
) {
    val context = LocalContext.current
    val sitStatus = if (calibration?.sittingSamples?.isNotEmpty() == true)
        context.getString(R.string.calibrated_samples, calibration.sittingSamples.size) else context.getString(R.string.not_calibrated)
    val standStatus = if (calibration?.standingSamples?.isNotEmpty() == true)
        context.getString(R.string.calibrated_samples, calibration.standingSamples.size) else context.getString(R.string.not_calibrated)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isCalibrating) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = context.getString(R.string.calibrating_format, calibratingPostureName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    // 平滑进度：服务端每秒 +1，Animatable 用 1 秒 tween 追赶
                    val smoothProgress = remember { Animatable(0f) }
                    val target = calibrationProgress / HeartRateAlarmViewModel.CALIBRATION_DURATION_SECONDS.toFloat()
                    LaunchedEffect(calibrationProgress) {
                        smoothProgress.animateTo(
                            targetValue = target.coerceIn(0f, 1f),
                            animationSpec = tween(1000, easing = FastOutSlowInEasing)
                        )
                    }
                    LinearWavyProgressIndicator(
                        progress = { smoothProgress.value },
                        modifier = Modifier.fillMaxWidth(),
                        amplitude = { 1f }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.remaining_seconds, HeartRateAlarmViewModel.CALIBRATION_DURATION_SECONDS - calibrationProgress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton(
                        label = stringResource(R.string.calibrate_sitting),
                        onClick = onCalibrateSitting,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveButton(
                        label = stringResource(R.string.calibrate_standing),
                        onClick = onCalibrateStanding,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = context.getString(R.string.calibration_status_format, sitStatus, standStatus),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (calibration?.isComplete() == true) {
                    Spacer(Modifier.height(8.dp))
                    ExpressiveButton(
                        label = stringResource(R.string.clear_calibration),
                        onClick = onClearCalibration,
                        style = ExpressiveButtonStyle.Danger
                    )
                }
            }
        }
    }
}
