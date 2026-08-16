package com.github.heartratemonitor_compose.ui.alarm

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.github.heartratemonitor_compose.ui.util.cardShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 姿态检测区：
 * 当前姿态展示卡片 + 姿态校准卡片。
 */

/** 当前姿态展示卡片：姿态图标弹跳动画 + 三姿态指示器 */
@Composable
internal fun PostureCard(
    posture: PostureType,
    scale: Float,
    bounceOffset: Float
) {
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
                    .scale(scale)
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
 * 胶囊 filled 按钮（按压收缩圆角 + 阴影反馈）：
 * 默认胶囊圆角（40dp 半高 20dp）+ primary 底 + onPrimary 文字；
 * 按压时圆角以 fastSpatial spring（damping 0.6 / stiffness 800）平滑收缩到 8dp，
 * 同时浮现 1dp 阴影。
 */
@Composable
private fun CalibrationButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) 8.dp else 20.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "calibrationButtonCorner"
    )
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = cardShape(cornerRadius),
        interactionSource = interactionSource,
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 1.dp
        )
    ) {
        Text(label)
    }
}

/** 姿态校准卡片：校准中显示波浪进度，空闲时显示校准按钮与状态 */
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
                    var smoothProgress by remember { mutableFloatStateOf(0f) }
                    LaunchedEffect(isCalibrating) {
                        if (isCalibrating) {
                            smoothProgress = 0f
                            val startTime = System.currentTimeMillis()
                            while (isActive) {
                                val elapsed = (System.currentTimeMillis() - startTime) / 1000f
                                smoothProgress = (elapsed / HeartRateAlarmViewModel.CALIBRATION_DURATION_SECONDS).coerceIn(0f, 1f)
                                if (smoothProgress >= 1f) break
                                delay(16)
                            }
                        }
                    }
                    LinearWavyProgressIndicator(
                        progress = { smoothProgress },
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
                    CalibrationButton(
                        onClick = onCalibrateSitting,
                        label = stringResource(R.string.calibrate_sitting),
                        modifier = Modifier.weight(1f)
                    )
                    CalibrationButton(
                        onClick = onCalibrateStanding,
                        label = stringResource(R.string.calibrate_standing),
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
                    TextButton(onClick = onClearCalibration) {
                        Text(stringResource(R.string.clear_calibration), color = MaterialTheme.colorScheme.error)

                    }
                }
            }
        }
    }
}
