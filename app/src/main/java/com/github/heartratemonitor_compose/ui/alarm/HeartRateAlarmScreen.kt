package com.github.heartratemonitor_compose.ui.alarm

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.service.HeartRateAlarmService
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
    sharedPreferences: SharedPreferences,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val postureDetector = remember { PostureDetector() }

    // 校准状态
    val calibration = remember {
        PostureCalibration.fromJson(sharedPreferences.getString("posture_calibration_data", null))
    }
    var currentCalibration by remember { mutableStateOf(calibration) }
    var isCalibrating by remember { mutableStateOf(false) }
    // 使用普通 MutableList 避免每次追加时 O(n) 不可变列表拷贝
    val calibrationBuffer = remember { mutableListOf<FloatArray>() }
    var calibrationProgress by remember { mutableIntStateOf(0) }
    var calibratingPostureName by remember { mutableStateOf("") }

    // 姿态显示
    var currentPosture by remember { mutableStateOf(PostureType.UNKNOWN) }

    // 预警设置
    var alarmEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("heart_rate_alarm_enabled", false)) }
    var excludePostureDetection by remember { mutableStateOf(sharedPreferences.getBoolean("heart_rate_alarm_exclude_posture_detection", false)) }
    var highThreshold by remember { mutableIntStateOf(sharedPreferences.getInt("heart_rate_alarm_high_threshold", 100)) }
    var lowThreshold by remember { mutableIntStateOf(sharedPreferences.getInt("heart_rate_alarm_low_threshold", 50)) }
    var durationSeconds by remember { mutableIntStateOf(sharedPreferences.getInt("heart_rate_alarm_duration_seconds", 10)) }
    var repeatEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("heart_rate_alarm_repeat_enabled", false)) }
    var repeatInterval by remember { mutableIntStateOf(sharedPreferences.getInt("heart_rate_alarm_repeat_interval_minutes", 5)) }

    // 阈值互相约束：高阈值至少比低阈值大 1，修正历史无效值
    LaunchedEffect(Unit) {
        if (highThreshold <= lowThreshold) {
            val newHigh = lowThreshold + 1
            highThreshold = newHigh
            sharedPreferences.edit().putInt("heart_rate_alarm_high_threshold", newHigh).apply()
        }
    }

    // 弹出动画
    val popAnim = remember { Animatable(0.7f) }
    LaunchedEffect(currentPosture) {
        popAnim.snapTo(0.7f)
        popAnim.animateTo(1f, animationSpec = tween(200, easing = FastOutSlowInEasing))
    }

    // 运动弹跳动画
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

    // 传感器注册 + 姿态分类（排除姿态检测时跳过）
    DisposableEffect(context, excludePostureDetection) {
        if (excludePostureDetection) {
            // 排除姿态检测：不注册传感器，姿态显示置为未检测
            currentPosture = PostureType.UNKNOWN
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            postureDetector.setCalibration(currentCalibration)

            val sensorListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    postureDetector.onSensorSample(event.values[0], event.values[1], event.values[2])
                    if (isCalibrating) {
                        calibrationBuffer.add(floatArrayOf(event.values[0], event.values[1], event.values[2]))
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME)

            val classifyHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val classifyRunnable = object : Runnable {
                override fun run() {
                    currentPosture = postureDetector.classify()
                    classifyHandler.postDelayed(this, CLASSIFY_INTERVAL_MS)
                }
            }
            classifyHandler.post(classifyRunnable)

            onDispose {
                sensorManager.unregisterListener(sensorListener)
                classifyHandler.removeCallbacks(classifyRunnable)
            }
        }
    }

    // 校准倒计时
    LaunchedEffect(isCalibrating) {
        if (isCalibrating) {
            calibrationProgress = 0
            calibrationBuffer.clear()
            for (i in 1..CALIBRATION_DURATION_SECONDS) {
                kotlinx.coroutines.delay(1000L)
                calibrationProgress = i
            }
            isCalibrating = false
            finishCalibration(sharedPreferences, currentCalibration, calibratingPostureName, calibrationBuffer) { newCal ->
                currentCalibration = newCal
                postureDetector.setCalibration(newCal)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("心率预警") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
            // 1. 姿态展示卡片（排除姿态检测时隐藏）
            if (!excludePostureDetection) {
                PostureCard(
                    posture = currentPosture,
                    scale = popAnim.value,
                    bounceOffset = if (currentPosture == PostureType.EXERCISE) bounceOffset else 0f
                )
            }

            // 2. 姿态校准区（排除姿态检测时隐藏）
            if (!excludePostureDetection) {
                CalibrationCard(
                    calibration = currentCalibration,
                    isCalibrating = isCalibrating,
                    calibratingPostureName = calibratingPostureName,
                    calibrationProgress = calibrationProgress,
                    onCalibrateSitting = {
                        calibratingPostureName = "静坐"
                        isCalibrating = true
                    },
                    onCalibrateStanding = {
                        calibratingPostureName = "站立"
                        isCalibrating = true
                    },
                    onClearCalibration = {
                        sharedPreferences.edit().remove("posture_calibration_data").apply()
                        currentCalibration = null
                        postureDetector.setCalibration(null)
                    }
                )
            }

            // 3. 预警设置区
            AlarmSettingsCard(
                sharedPreferences = sharedPreferences,
                alarmEnabled = alarmEnabled,
                onAlarmEnabledChange = { enabled ->
                    alarmEnabled = enabled
                    sharedPreferences.edit().putBoolean("heart_rate_alarm_enabled", enabled).apply()
                    val intent = Intent(context, HeartRateAlarmService::class.java)
                    if (enabled) context.startService(intent) else context.stopService(intent)
                },
                excludePostureDetection = excludePostureDetection,
                onExcludePostureDetectionChange = { enabled ->
                    excludePostureDetection = enabled
                    sharedPreferences.edit().putBoolean("heart_rate_alarm_exclude_posture_detection", enabled).apply()
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
                    sharedPreferences.edit().putInt("heart_rate_alarm_high_threshold", clamped).apply()
                },
                lowThreshold = lowThreshold,
                onLowThresholdChange = { value ->
                    val clamped = minOf(value, highThreshold - 1)
                    lowThreshold = clamped
                    sharedPreferences.edit().putInt("heart_rate_alarm_low_threshold", clamped).apply()
                },
                durationSeconds = durationSeconds,
                onDurationChange = { value ->
                    durationSeconds = value
                    sharedPreferences.edit().putInt("heart_rate_alarm_duration_seconds", value).apply()
                },
                repeatEnabled = repeatEnabled,
                onRepeatEnabledChange = { enabled ->
                    repeatEnabled = enabled
                    sharedPreferences.edit().putBoolean("heart_rate_alarm_repeat_enabled", enabled).apply()
                },
                repeatInterval = repeatInterval,
                onRepeatIntervalChange = { value ->
                    repeatInterval = value
                    sharedPreferences.edit().putInt("heart_rate_alarm_repeat_interval_minutes", value).apply()
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
        shape = RoundedCornerShape(20.dp),
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
                text = posture.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Normal
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PostureIndicator("静坐", PostureType.SITTING.emoji, posture == PostureType.SITTING)
                PostureIndicator("站立", PostureType.STANDING.emoji, posture == PostureType.STANDING)
                PostureIndicator("运动", PostureType.EXERCISE.emoji, posture == PostureType.EXERCISE)
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
    val sitStatus = if (calibration?.sittingSamples?.isNotEmpty() == true)
        "已校准 ✓（${calibration.sittingSamples.size} 个样本）" else "未校准"
    val standStatus = if (calibration?.standingSamples?.isNotEmpty() == true)
        "已校准 ✓（${calibration.standingSamples.size} 个样本）" else "未校准"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "姿态校准",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            if (isCalibrating) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "正在校准${calibratingPostureName}…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { calibrationProgress.toFloat() / CALIBRATION_DURATION_SECONDS },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "剩余 ${CALIBRATION_DURATION_SECONDS - calibrationProgress} 秒",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCalibrateSitting, modifier = Modifier.weight(1f)) {
                        Text("校准静坐")
                    }
                    OutlinedButton(onClick = onCalibrateStanding, modifier = Modifier.weight(1f)) {
                        Text("校准站立")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "静坐：$sitStatus\n站立：$standStatus",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (calibration?.isComplete() == true) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onClearCalibration) {
                        Text("清除校准", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// ────────────────── 预警设置卡片 ──────────────────

@Composable
private fun AlarmSettingsCard(
    sharedPreferences: SharedPreferences,
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
            text = "预警设置",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        AlarmGroupCard {
            // 启用开关
            AlarmItem {
                AlarmSwitch(
                    checked = alarmEnabled,
                    onCheckedChange = onAlarmEnabledChange,
                    title = "启用心率预警",
                    leadingIcon = painterResource(R.drawable.ic_enable_alarm)
                )
            }

            AlarmDivider()
            // 排除姿态检测开关（位于启用心率预警下方）
            AlarmItem {
                AlarmSwitch(
                    checked = excludePostureDetection,
                    onCheckedChange = onExcludePostureDetectionChange,
                    title = "排除姿态检测",
                    leadingIcon = painterResource(R.drawable.ic_hide_source)
                )
            }

            AlarmDivider()
            // 超过阈值（动态下限：至少比低阈值大 1）
            AlarmItem {
                AlarmDragSlider(
                    label = "超过",
                    value = highThreshold,
                    onValueChange = onHighThresholdChange,
                    range = maxOf(HIGH_THRESHOLD_MIN, lowThreshold + 1)..HIGH_THRESHOLD_MAX,
                    suffix = " BPM",
                    leadingIcon = painterResource(R.drawable.ic_trending_up)
                )
            }

            AlarmDivider()
            // 低于阈值（动态上限：至多比高阈值小 1）
            AlarmItem {
                AlarmDragSlider(
                    label = "低于",
                    value = lowThreshold,
                    onValueChange = onLowThresholdChange,
                    range = LOW_THRESHOLD_MIN..minOf(LOW_THRESHOLD_MAX, highThreshold - 1),
                    suffix = " BPM",
                    leadingIcon = painterResource(R.drawable.ic_trending_down)
                )
            }

            AlarmDivider()
            // 持续时长
            AlarmItem {
                AlarmDragSlider(
                    label = "持续",
                    value = durationSeconds,
                    onValueChange = onDurationChange,
                    range = DURATION_MIN..60,
                    suffix = " 秒",
                    leadingIcon = painterResource(R.drawable.ic_hourglass)
                )
            }

            AlarmDivider()
            // 重复报警开关
            AlarmItem {
                AlarmSwitch(
                    checked = repeatEnabled,
                    onCheckedChange = onRepeatEnabledChange,
                    title = "重复报警",
                    leadingIcon = painterResource(R.drawable.ic_repeat_alarm)
                )
            }

            if (repeatEnabled) {
                AlarmDivider()
                AlarmItem {
                    AlarmDragSlider(
                        label = "报警间隔",
                        value = repeatInterval,
                        onValueChange = onRepeatIntervalChange,
                        range = REPEAT_INTERVAL_MIN..30,
                        suffix = " 分钟",
                        leadingIcon = painterResource(R.drawable.ic_alarm_interval)
                    )
                }
            }
        }
    }
}

/**
 * 分组卡片容器：用一张圆角卡片包裹同组的多个设置项，
 * 项之间用 [AlarmDivider] 分隔。与设置页风格统一为 Material 3 grouped list。
 */
@Composable
private fun AlarmGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(content = content)
    }
}

/**
 * 分组卡片内的单个设置项行（不再包裹独立 Card）。
 * - [onClick] 非空时整行可点击，ripple 覆盖整行（含圆角由外层 Surface clip）。
 * - 最小高度 56dp，与 MD3 列表规范一致。
 */
@Composable
private fun AlarmItem(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        content()
    }
}

/** 分组卡片内项之间的分隔线。左右留 16dp 与行内容对齐。 */
@Composable
private fun AlarmDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
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
    prefs: SharedPreferences,
    currentCalibration: PostureCalibration?,
    postureName: String,
    samples: List<FloatArray>,
    onUpdated: (PostureCalibration) -> Unit
) {
    if (samples.isEmpty()) return
    val isSitting = postureName == "静坐"

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
    prefs.edit().putString("posture_calibration_data", updated.toJson()).apply()
    onUpdated(updated)
}
