package com.github.heartratemonitor_compose.ui.alarm

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.service.ServiceController
import com.github.heartratemonitor_compose.service.posture.PostureCalibration
import com.github.heartratemonitor_compose.service.posture.PostureDetector
import com.github.heartratemonitor_compose.service.posture.PostureType

private const val HIGH_THRESHOLD_MIN = 80
private const val HIGH_THRESHOLD_MAX = 180
private const val LOW_THRESHOLD_MIN = 30
private const val LOW_THRESHOLD_MAX = 80
private const val DURATION_MIN = 5
private const val REPEAT_INTERVAL_MIN = 1
private const val CALIBRATION_DURATION_SECONDS = 10
private const val CLASSIFY_INTERVAL_MS = 200L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateAlarmScreen(
    settings: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val postureDetector = remember { PostureDetector() }

    val calibration = remember {
        PostureCalibration.fromJson(settings.getStringNullable(PrefsKeys.POSTURE_CALIBRATION_DATA))
    }
    var currentCalibration by remember { mutableStateOf(calibration) }
    var isCalibrating by remember { mutableStateOf(false) }
    // 使用普通 MutableList 避免每次追加时 O(n) 不可变列表拷贝
    val calibrationBuffer = remember { mutableListOf<FloatArray>() }
    var calibrationProgress by remember { mutableIntStateOf(0) }
    var calibratingPostureName by remember { mutableStateOf("") }

    val sittingLabel = stringResource(R.string.sitting)
    val standingLabel = stringResource(R.string.standing)

    var currentPosture by remember { mutableStateOf(PostureType.UNKNOWN) }

    var alarmEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HEART_RATE_ALARM_ENABLED, false)) }
    var excludePostureDetection by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION, false)) }
    var highThreshold by remember { mutableIntStateOf(settings.getInt(PrefsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, 100)) }
    var lowThreshold by remember { mutableIntStateOf(settings.getInt(PrefsKeys.HEART_RATE_ALARM_LOW_THRESHOLD, 50)) }
    var durationSeconds by remember { mutableIntStateOf(settings.getInt(PrefsKeys.HEART_RATE_ALARM_DURATION_SECONDS, 10)) }
    var repeatEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HEART_RATE_ALARM_REPEAT_ENABLED, false)) }
    var repeatInterval by remember { mutableIntStateOf(settings.getInt(PrefsKeys.HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES, 5)) }

    // 阈值互相约束：高阈值至少比低阈值大 1，修正历史无效值
    LaunchedEffect(Unit) {
        if (highThreshold <= lowThreshold) {
            val newHigh = lowThreshold + 1
            highThreshold = newHigh
            settings.setInt(PrefsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, newHigh)
        }
    }

    val popAnim = remember { Animatable(0.7f) }
    LaunchedEffect(currentPosture) {
        popAnim.snapTo(0.7f)
        popAnim.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
    }

    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -20f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    val postureSensorProvider = remember { context.applicationContext.appContainer.postureSensorProvider }

    // 传感器注册 + 姿态分类（排除姿态检测时跳过）
    DisposableEffect(postureSensorProvider, excludePostureDetection) {
        if (excludePostureDetection) {
            // 排除姿态检测：不注册传感器，姿态显示置为未检测
            currentPosture = PostureType.UNKNOWN
            onDispose { }
        } else {
            postureDetector.setCalibration(currentCalibration)
            postureSensorProvider.start(
                onSample = { x, y, z ->
                    postureDetector.onSensorSample(x, y, z)
                    if (isCalibrating) {
                        calibrationBuffer.add(floatArrayOf(x, y, z))
                    }
                },
                onClassify = { currentPosture = postureDetector.classify() },
                classifyIntervalMs = CLASSIFY_INTERVAL_MS
            )
            onDispose { postureSensorProvider.stop() }
        }
    }

    LaunchedEffect(isCalibrating) {
        if (isCalibrating) {
            calibrationProgress = 0
            calibrationBuffer.clear()
            for (i in 1..CALIBRATION_DURATION_SECONDS) {
                kotlinx.coroutines.delay(1000L)
                calibrationProgress = i
            }
            isCalibrating = false
            finishCalibration(settings, currentCalibration, calibratingPostureName, calibrationBuffer, sittingLabel) { newCal ->
                currentCalibration = newCal
                postureDetector.setCalibration(newCal)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.alarm_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!excludePostureDetection) {
                PostureCard(
                    posture = currentPosture,
                    scale = popAnim.value,
                    bounceOffset = if (currentPosture == PostureType.EXERCISE) bounceOffset else 0f
                )
            }

            if (!excludePostureDetection) {
                CalibrationCard(
                    calibration = currentCalibration,
                    isCalibrating = isCalibrating,
                    calibratingPostureName = calibratingPostureName,
                    calibrationProgress = calibrationProgress,
                    onCalibrateSitting = {
                        calibratingPostureName = sittingLabel
                        isCalibrating = true
                    },
                    onCalibrateStanding = {
                        calibratingPostureName = standingLabel
                        isCalibrating = true
                    },
                    onClearCalibration = {
                        settings.remove(PrefsKeys.POSTURE_CALIBRATION_DATA)
                        currentCalibration = null
                        postureDetector.setCalibration(null)
                    }
                )
            }

            AlarmSettingsCard(
                settings = settings,
                alarmEnabled = alarmEnabled,
                onAlarmEnabledChange = { enabled ->
                    alarmEnabled = enabled
                    settings.setBoolean(PrefsKeys.HEART_RATE_ALARM_ENABLED, enabled)
                    if (enabled) ServiceController.startHeartRateAlarmService(context) else ServiceController.stopHeartRateAlarmService(context)
                },
                excludePostureDetection = excludePostureDetection,
                onExcludePostureDetectionChange = { enabled ->
                    excludePostureDetection = enabled
                    settings.setBoolean(PrefsKeys.HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION, enabled)
                    // 开启排除时中断正在进行的校准
                    if (enabled) {
                        isCalibrating = false
                        calibrationBuffer.clear()
                    }
                },
                highThreshold = highThreshold,
                onHighThresholdChange = { value ->
                    val clamped = maxOf(value, lowThreshold + 1)
                    highThreshold = clamped
                    settings.setInt(PrefsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, clamped)
                },
                lowThreshold = lowThreshold,
                onLowThresholdChange = { value ->
                    val clamped = minOf(value, highThreshold - 1)
                    lowThreshold = clamped
                    settings.setInt(PrefsKeys.HEART_RATE_ALARM_LOW_THRESHOLD, clamped)
                },
                durationSeconds = durationSeconds,
                onDurationChange = { value ->
                    durationSeconds = value
                    settings.setInt(PrefsKeys.HEART_RATE_ALARM_DURATION_SECONDS, value)
                },
                repeatEnabled = repeatEnabled,
                onRepeatEnabledChange = { enabled ->
                    repeatEnabled = enabled
                    settings.setBoolean(PrefsKeys.HEART_RATE_ALARM_REPEAT_ENABLED, enabled)
                },
                repeatInterval = repeatInterval,
                onRepeatIntervalChange = { value ->
                    repeatInterval = value
                    settings.setInt(PrefsKeys.HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES, value)
                }
            )
            // 底部留出系统导航栏空间，避免内容被手势条遮挡
            Spacer(Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

// ────────────────── 姿态展示卡片 ──────────────────

@Composable
private fun PostureCard(
    posture: PostureType,
    scale: Float,
    bounceOffset: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = posture.emoji,
                fontSize = 64.sp,
                modifier = Modifier
                    .scale(scale)
                    .offset(y = bounceOffset.dp)
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
                PostureIndicator(stringResource(R.string.sitting), PostureType.SITTING.emoji, posture == PostureType.SITTING)
                PostureIndicator(stringResource(R.string.standing), PostureType.STANDING.emoji, posture == PostureType.STANDING)
                PostureIndicator(stringResource(R.string.exercise), PostureType.EXERCISE.emoji, posture == PostureType.EXERCISE)
            }
        }
    }
}

@Composable
private fun PostureIndicator(label: String, emoji: String, isActive: Boolean) {
    val alphaValue = if (isActive) 1f else 0.3f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = emoji,
            fontSize = 24.sp,
            modifier = Modifier.alpha(alphaValue)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(alphaValue)
        )
    }
}

// ────────────────── 姿态校准卡片 ──────────────────

@Composable
private fun CalibrationCard(
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
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.posture_calibration),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            if (isCalibrating) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = context.getString(R.string.calibrating_format, calibratingPostureName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { calibrationProgress.toFloat() / CALIBRATION_DURATION_SECONDS },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = context.getString(R.string.remaining_seconds, CALIBRATION_DURATION_SECONDS - calibrationProgress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCalibrateSitting, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.calibrate_sitting))
                    }
                    OutlinedButton(onClick = onCalibrateStanding, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.calibrate_standing))
                    }
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

// ────────────────── 预警设置卡片 ──────────────────

@Composable
private fun AlarmSettingsCard(
    settings: SettingsRepository,
    alarmEnabled: Boolean,
    onAlarmEnabledChange: (Boolean) -> Unit,
    excludePostureDetection: Boolean,
    onExcludePostureDetectionChange: (Boolean) -> Unit,
    highThreshold: Int,
    onHighThresholdChange: (Int) -> Unit,
    lowThreshold: Int,
    onLowThresholdChange: (Int) -> Unit,
    durationSeconds: Int,
    onDurationChange: (Int) -> Unit,
    repeatEnabled: Boolean,
    onRepeatEnabledChange: (Boolean) -> Unit,
    repeatInterval: Int,
    onRepeatIntervalChange: (Int) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.alarm_settings),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        AlarmGroupCard {
            AlarmItem(isFirst = true) {
                AlarmSwitch(
                    checked = alarmEnabled,
                    onCheckedChange = onAlarmEnabledChange,
                    title = stringResource(R.string.enable_alarm),
                    leadingIcon = painterResource(R.drawable.ic_enable_alarm)
                )
            }

            AlarmItem {
                AlarmSwitch(
                    checked = excludePostureDetection,
                    onCheckedChange = onExcludePostureDetectionChange,
                    title = stringResource(R.string.exclude_posture_detection),
                    leadingIcon = painterResource(R.drawable.ic_hide_source)
                )
            }

            // 超过阈值（动态下限：至少比低阈值大 1）
            AlarmItem {
                AlarmDragSlider(
                    label = stringResource(R.string.above_threshold),
                    value = highThreshold,
                    onValueChange = onHighThresholdChange,
                    range = maxOf(HIGH_THRESHOLD_MIN, lowThreshold + 1)..HIGH_THRESHOLD_MAX,
                    suffix = " BPM",
                    leadingIcon = painterResource(R.drawable.ic_trending_up)
                )
            }

            // 低于阈值（动态上限：至多比高阈值小 1）
            AlarmItem {
                AlarmDragSlider(
                    label = stringResource(R.string.below_threshold),
                    value = lowThreshold,
                    onValueChange = onLowThresholdChange,
                    range = LOW_THRESHOLD_MIN..minOf(LOW_THRESHOLD_MAX, highThreshold - 1),
                    suffix = " BPM",
                    leadingIcon = painterResource(R.drawable.ic_trending_down)
                )
            }

            AlarmItem {
                AlarmDragSlider(
                    label = stringResource(R.string.duration_label),
                    value = durationSeconds,
                    onValueChange = onDurationChange,
                    range = DURATION_MIN..60,
                    suffix = stringResource(R.string.seconds_suffix),
                    leadingIcon = painterResource(R.drawable.ic_hourglass)
                )
            }

            AlarmItem(isLast = !repeatEnabled) {
                AlarmSwitch(
                    checked = repeatEnabled,
                    onCheckedChange = onRepeatEnabledChange,
                    title = stringResource(R.string.repeat_alarm),
                    leadingIcon = painterResource(R.drawable.ic_repeat_alarm)
                )
            }

            if (repeatEnabled) {
                AlarmItem(isLast = true) {
                    AlarmDragSlider(
                        label = stringResource(R.string.alarm_interval),
                        value = repeatInterval,
                        onValueChange = onRepeatIntervalChange,
                        range = REPEAT_INTERVAL_MIN..30,
                        suffix = stringResource(R.string.minutes_suffix),
                        leadingIcon = painterResource(R.drawable.ic_alarm_interval)
                    )
                }
            }
        }
    }
}

/**
 * 分组容器：用 Column 包裹同组的多个独立设置项卡片，
 * 项之间用 2dp 间隔分隔（露出背景），取代传统的分割线设计。
 * 这是 Material 3 分段列表（Segmented List）模式的变体，
 * 每个设置项是独立的 Surface 卡片，视觉上既分组又分离。
 */
@Composable
private fun AlarmGroupCard(
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
private fun AlarmItem(
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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

// ────────────────── M3 Expressive 开关组件 ──────────────────

@Composable
private fun AlarmSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    leadingIcon: Painter? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // MD3 列表项 Leading Icon：24dp + 16dp 间距
        if (leadingIcon != null) {
            Icon(
                painter = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

// ────────────────── M3 Expressive 拖拽滑块组件 ──────────────────

@Composable
private fun AlarmDragSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    suffix: String = "",
    enabled: Boolean = true,
    leadingIcon: Painter? = null
) {
    var internalValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var sliderSize by remember { mutableStateOf(IntSize.Zero) }

    val fraction = if (range.last > range.first)
        (internalValue - range.first) / (range.last - range.first) else 0f

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        // MD3 列表项 Leading Icon：24dp + 16dp 间距
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    painter = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
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

private fun finishCalibration(
    settings: SettingsRepository,
    currentCalibration: PostureCalibration?,
    postureName: String,
    samples: List<FloatArray>,
    sittingLabel: String,
    onUpdated: (PostureCalibration) -> Unit
) {
    if (samples.isEmpty()) return
    val isSitting = postureName == sittingLabel

    val n = samples.size
    val meanX = samples.map { it[0] }.average().toFloat()
    val meanY = samples.map { it[1] }.average().toFloat()
    val meanZ = samples.map { it[2] }.average().toFloat()
    val magnitudes = samples.map { kotlin.math.sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) }
    val stdMag = kotlin.math.sqrt(magnitudes.map { (it - magnitudes.average()) * (it - magnitudes.average()) }.average()).toFloat()

    val features = com.github.heartratemonitor_compose.service.posture.PostureFeatures(meanX, meanY, meanZ, stdMag, n)
    val existing = currentCalibration
    val sitSamples = existing?.sittingSamples ?: emptyList()
    val standSamples = existing?.standingSamples ?: emptyList()
    val updated = if (isSitting) {
        PostureCalibration(
            sittingSamples = sitSamples + features,
            standingSamples = standSamples,
            motionThreshold = existing?.motionThreshold ?: 1.5f,
            calibratedAt = System.currentTimeMillis()
        )
    } else {
        PostureCalibration(
            sittingSamples = sitSamples,
            standingSamples = standSamples + features,
            motionThreshold = existing?.motionThreshold ?: 1.5f,
            calibratedAt = System.currentTimeMillis()
        )
    }
    settings.setString(PrefsKeys.POSTURE_CALIBRATION_DATA, updated.toJson())
    onUpdated(updated)
}
