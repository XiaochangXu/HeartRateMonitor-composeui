package com.github.heartratemonitor_compose.ui.alarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.heartratemonitor_compose.feature.alarm.R
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim

/**
 * 心率预警设置页：
 * - 姿态检测区见 PostureDetectionSection.kt（PostureCard / CalibrationCard）
 * - 阈值/重复设置区见 AlarmSettingsCard.kt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateAlarmScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: HeartRateAlarmViewModel = hiltViewModel()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val alarmEnabled = uiState.alarmEnabled
    val excludePostureDetection = uiState.excludePostureDetection
    val highThreshold = uiState.highThreshold
    val lowThreshold = uiState.lowThreshold
    val durationSeconds = uiState.durationSeconds
    val repeatEnabled = uiState.repeatEnabled
    val repeatInterval = uiState.repeatInterval

    val currentPosture = uiState.currentPosture
    val currentCalibration = uiState.currentCalibration
    val isCalibrating = uiState.isCalibrating
    val calibrationProgress = uiState.calibrationProgress
    val calibratingIsSitting = uiState.calibratingIsSitting

    val sittingLabel = stringResource(com.github.heartratemonitor_compose.service.R.string.sitting)
    val standingLabel = stringResource(com.github.heartratemonitor_compose.service.R.string.standing)
    val calibratingPostureName = if (calibratingIsSitting) sittingLabel else standingLabel

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 首次组合无展开动画（避免与转场叠加掉帧），首帧后切换开关才播放动画。
    var hasAnimated by remember { mutableStateOf(false) }
    val postureSectionVisible = !excludePostureDetection

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                            color = MaterialTheme.colorScheme.surfaceBright
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_back))
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
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + 16.dp))

            // 首次组合跳过进入动画（EnterTransition.None），避免与转场叠加掉帧。
            val enterTrans = if (hasAnimated) {
                expandVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = tween(250))
            } else {
                EnterTransition.None
            }
            val exitTrans = if (hasAnimated) {
                shrinkVertically(
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = tween(250))
            } else {
                ExitTransition.None
            }
            AnimatedVisibility(
                visible = postureSectionVisible,
                enter = enterTrans,
                exit = exitTrans
            ) {
                Column {
                    PostureCard(
                        posture = currentPosture
                    )
                    Spacer(Modifier.height(24.dp))
                    CalibrationCard(
                        calibration = currentCalibration,
                        isCalibrating = isCalibrating,
                        calibratingPostureName = calibratingPostureName,
                        calibrationProgress = calibrationProgress,
                        onCalibrateSitting = {
                            viewModel.dispatch(HeartRateAlarmIntent.StartCalibration(isSitting = true))
                        },
                        onCalibrateStanding = {
                            viewModel.dispatch(HeartRateAlarmIntent.StartCalibration(isSitting = false))
                        },
                        onClearCalibration = { viewModel.dispatch(HeartRateAlarmIntent.ClearCalibration) }
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
            // 首帧后标记，后续切换走动画分支
            LaunchedEffect(Unit) { hasAnimated = true }

            AlarmSettingsCard(
                alarmEnabled = alarmEnabled,
                onAlarmEnabledChange = {
                    viewModel.dispatch(HeartRateAlarmIntent.SetAlarmEnabled(it))
                },
                excludePostureDetection = excludePostureDetection,
                onExcludePostureDetectionChange = {
                    viewModel.dispatch(HeartRateAlarmIntent.SetExcludePostureDetection(it))
                },
                highThreshold = highThreshold,
                onHighThresholdChange = {
                    viewModel.dispatch(HeartRateAlarmIntent.SetHighThreshold(it))
                },
                lowThreshold = lowThreshold,
                onLowThresholdChange = {
                    viewModel.dispatch(HeartRateAlarmIntent.SetLowThreshold(it))
                },
                durationSeconds = durationSeconds,
                onDurationChange = {
                    viewModel.dispatch(HeartRateAlarmIntent.SetDurationSeconds(it))
                },
                repeatEnabled = repeatEnabled,
                onRepeatEnabledChange = {
                    viewModel.dispatch(HeartRateAlarmIntent.SetRepeatEnabled(it))
                },
                repeatInterval = repeatInterval,
                onRepeatIntervalChange = {
                    viewModel.dispatch(HeartRateAlarmIntent.SetRepeatInterval(it))
                }
            )
            Spacer(Modifier.height(40.dp))
            }
            StatusBarScrim()
        }
    }
}
