package com.github.heartratemonitor_compose.ui.alarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope.PlaceholderSize.Companion.AnimatedSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.service.posture.PostureCalibration
import com.github.heartratemonitor_compose.service.posture.PostureType
import com.github.heartratemonitor_compose.ui.settings.DragSlider
import com.github.heartratemonitor_compose.ui.settings.SettingsGroupCard
import com.github.heartratemonitor_compose.ui.settings.SettingsItem
import com.github.heartratemonitor_compose.ui.settings.SettingsSwitch
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val HIGH_THRESHOLD_MIN = 80
private const val HIGH_THRESHOLD_MAX = 180
private const val LOW_THRESHOLD_MIN = 30
private const val LOW_THRESHOLD_MAX = 80
private const val DURATION_MIN = 5
private const val REPEAT_INTERVAL_MIN = 1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateAlarmScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: HeartRateAlarmViewModel = viewModel()

    val alarmEnabled by viewModel.alarmEnabled.collectAsStateWithLifecycle()
    val excludePostureDetection by viewModel.excludePostureDetection.collectAsStateWithLifecycle()
    val highThreshold by viewModel.highThreshold.collectAsStateWithLifecycle()
    val lowThreshold by viewModel.lowThreshold.collectAsStateWithLifecycle()
    val durationSeconds by viewModel.durationSeconds.collectAsStateWithLifecycle()
    val repeatEnabled by viewModel.repeatEnabled.collectAsStateWithLifecycle()
    val repeatInterval by viewModel.repeatInterval.collectAsStateWithLifecycle()

    val currentPosture by viewModel.currentPosture.collectAsStateWithLifecycle()
    val currentCalibration by viewModel.currentCalibration.collectAsStateWithLifecycle()
    val isCalibrating by viewModel.isCalibrating.collectAsStateWithLifecycle()
    val calibrationProgress by viewModel.calibrationProgress.collectAsStateWithLifecycle()
    val calibratingIsSitting by viewModel.calibratingIsSitting.collectAsStateWithLifecycle()

    val sittingLabel = stringResource(R.string.sitting)
    val standingLabel = stringResource(R.string.standing)
    val calibratingPostureName = if (calibratingIsSitting) sittingLabel else standingLabel

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

    DisposableEffect(excludePostureDetection) {
        if (excludePostureDetection) {
            viewModel.stopPostureDetection()
            onDispose { }
        } else {
            viewModel.startPostureDetection()
            onDispose { viewModel.stopPostureDetection() }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                title = { Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + 24.dp))

            AnimatedVisibility(
                visible = !excludePostureDetection,
                enter = expandVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(250)),
                exit = shrinkVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(250))
            ) {
                Column {
                    PostureCard(
                        posture = currentPosture,
                        scale = popAnim.value,
                        bounceOffset = if (currentPosture == PostureType.EXERCISE) bounceOffset else 0f
                    )
                    Spacer(Modifier.height(24.dp))
                    CalibrationCard(
                        calibration = currentCalibration,
                        isCalibrating = isCalibrating,
                        calibratingPostureName = calibratingPostureName,
                        calibrationProgress = calibrationProgress,
                        onCalibrateSitting = { viewModel.startCalibration(isSitting = true) },
                        onCalibrateStanding = { viewModel.startCalibration(isSitting = false) },
                        onClearCalibration = { viewModel.clearCalibration() }
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }

            AlarmSettingsCard(
                alarmEnabled = alarmEnabled,
                onAlarmEnabledChange = viewModel::setAlarmEnabled,
                excludePostureDetection = excludePostureDetection,
                onExcludePostureDetectionChange = viewModel::setExcludePostureDetection,
                highThreshold = highThreshold,
                onHighThresholdChange = viewModel::setHighThreshold,
                lowThreshold = lowThreshold,
                onLowThresholdChange = viewModel::setLowThreshold,
                durationSeconds = durationSeconds,
                onDurationChange = viewModel::setDurationSeconds,
                repeatEnabled = repeatEnabled,
                onRepeatEnabledChange = viewModel::setRepeatEnabled,
                repeatInterval = repeatInterval,
                onRepeatIntervalChange = viewModel::setRepeatInterval
            )
            // 底部留出系统导航栏空间，避免内容被手势条遮挡
            Spacer(Modifier.height(40.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
            StatusBarScrim()
        }
    }
}

@Composable
private fun PostureCard(
    posture: PostureType,
    scale: Float,
    bounceOffset: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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
                PostureIndicator(stringResource(R.string.sitting), PostureType.SITTING.iconRes, posture == PostureType.SITTING)
                PostureIndicator(stringResource(R.string.standing), PostureType.STANDING.iconRes, posture == PostureType.STANDING)
                PostureIndicator(stringResource(R.string.exercise), PostureType.EXERCISE.iconRes, posture == PostureType.EXERCISE)
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


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AlarmSettingsCard(
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
    val iconContainerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    // 重复报警开关行底部圆角动画
    val repeatSwitchBottomCorner by animateDpAsState(
        targetValue = if (repeatEnabled) 0.dp else 28.dp,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "repeatSwitchBottomCorner"
    )
    val repeatSwitchShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = repeatSwitchBottomCorner,
        bottomEnd = repeatSwitchBottomCorner
    )

    SharedTransitionLayout {
        Column {
            SettingsGroupCard {
                SettingsItem(isFirst = true) {
                    SettingsSwitch(
                        checked = alarmEnabled,
                        onCheckedChange = onAlarmEnabledChange,
                        title = stringResource(R.string.enable_alarm),
                        leadingIcon = painterResource(R.drawable.ic_enable_alarm),
                        leadingIconContainerColor = iconContainerColor,
                        leadingIconTint = iconTint
                    )
                }

                SettingsItem {
                    SettingsSwitch(
                        checked = excludePostureDetection,
                        onCheckedChange = onExcludePostureDetectionChange,
                        title = stringResource(R.string.exclude_posture_detection),
                        leadingIcon = painterResource(R.drawable.ic_hide_source),
                        leadingIconContainerColor = iconContainerColor,
                        leadingIconTint = iconTint
                    )
                }

                SettingsItem {
                    DragSlider(
                        label = stringResource(R.string.above_threshold),
                        value = highThreshold,
                        onValueChange = onHighThresholdChange,
                        range = maxOf(HIGH_THRESHOLD_MIN, lowThreshold + 1)..HIGH_THRESHOLD_MAX,
                        suffix = " bpm",
                        leadingIcon = painterResource(R.drawable.ic_trending_up),
                        leadingIconContainerColor = iconContainerColor,
                        leadingIconTint = iconTint
                    )
                }

                SettingsItem {
                    DragSlider(
                        label = stringResource(R.string.below_threshold),
                        value = lowThreshold,
                        onValueChange = onLowThresholdChange,
                        range = LOW_THRESHOLD_MIN..minOf(LOW_THRESHOLD_MAX, highThreshold - 1),
                        suffix = " bpm",
                        leadingIcon = painterResource(R.drawable.ic_trending_down),
                        leadingIconContainerColor = iconContainerColor,
                        leadingIconTint = iconTint
                    )
                }

                SettingsItem {
                    DragSlider(
                        label = stringResource(R.string.duration_label),
                        value = durationSeconds,
                        onValueChange = onDurationChange,
                        range = DURATION_MIN..60,
                        suffix = stringResource(R.string.seconds_suffix),
                        leadingIcon = painterResource(R.drawable.ic_hourglass),
                        leadingIconContainerColor = iconContainerColor,
                        leadingIconTint = iconTint
                    )
                }

                // 重复报警开关行：使用动画驱动 shape 替代静态 isLast
                SettingsItem(shape = repeatSwitchShape) {
                    SettingsSwitch(
                        checked = repeatEnabled,
                        onCheckedChange = onRepeatEnabledChange,
                        title = stringResource(R.string.repeat_alarm),
                        leadingIcon = painterResource(R.drawable.ic_repeat_alarm),
                        leadingIconContainerColor = iconContainerColor,
                        leadingIconTint = iconTint
                    )
                }

                // 报警间隔滑块：AnimatedVisibility + sharedBounds 平滑展开
                AnimatedVisibility(
                    visible = repeatEnabled,
                    enter = expandVertically(
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top
                    ) + fadeIn(animationSpec = tween(250)),
                    exit = shrinkVertically(
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        shrinkTowards = Alignment.Top
                    ) + fadeOut(animationSpec = tween(250))
                ) {
                    val sliderBounds = rememberSharedContentState(key = "repeat_interval_slider")
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sharedBounds(
                                sharedContentState = sliderBounds,
                                animatedVisibilityScope = this,
                                boundsTransform = { _, _ ->
                                    tween(250, easing = FastOutSlowInEasing)
                                },
                                placeholderSize = AnimatedSize,
                                enter = fadeIn(tween(250)),
                                exit = fadeOut(tween(250))
                            )
                    ) {
                        SettingsItem(isLast = true) {
                            DragSlider(
                                label = stringResource(R.string.alarm_interval),
                                value = repeatInterval,
                                onValueChange = onRepeatIntervalChange,
                                range = REPEAT_INTERVAL_MIN..30,
                                suffix = stringResource(R.string.minutes_suffix),
                                leadingIcon = painterResource(R.drawable.ic_alarm_interval),
                                leadingIconContainerColor = iconContainerColor,
                                leadingIconTint = iconTint
                            )
                        }
                    }
                }
            }
        }
    }
}
