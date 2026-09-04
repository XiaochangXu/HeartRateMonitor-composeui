package com.github.heartratemonitor_compose.ui.alarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope.PlaceholderSize.Companion.AnimatedSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.alarm.R
import com.github.heartratemonitor_compose.ui.settings.DragSlider
import com.github.heartratemonitor_compose.ui.settings.SettingsGroupCard
import com.github.heartratemonitor_compose.ui.settings.SettingsItem
import com.github.heartratemonitor_compose.ui.settings.SettingsSwitch
import com.github.heartratemonitor_compose.ui.util.bottomCornerShape

internal const val HIGH_THRESHOLD_MIN = 80
internal const val HIGH_THRESHOLD_MAX = 180
internal const val LOW_THRESHOLD_MIN = 30
internal const val LOW_THRESHOLD_MAX = 80
internal const val DURATION_MIN = 5
internal const val REPEAT_INTERVAL_MIN = 1

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AlarmSettingsCard(
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

    val repeatSwitchBottomCorner by animateDpAsState(
        targetValue = if (repeatEnabled) 0.dp else 28.dp,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "repeatSwitchBottomCorner"
    )
    val repeatSwitchShape = bottomCornerShape(repeatSwitchBottomCorner)

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
                        leadingIcon = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_hide_source),
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

                // 动画驱动 shape 替代静态 isLast
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
