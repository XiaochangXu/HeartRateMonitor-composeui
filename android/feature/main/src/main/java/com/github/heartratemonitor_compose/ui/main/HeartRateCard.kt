package com.github.heartratemonitor_compose.ui.main

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.feature.main.R
import com.github.heartratemonitor_compose.ui.settings.DragSlider
import com.github.heartratemonitor_compose.ui.theme.HealthHighColor
import com.github.heartratemonitor_compose.ui.theme.HealthLowColor
import com.github.heartratemonitor_compose.ui.theme.HealthMidColor
import com.github.heartratemonitor_compose.ui.util.SheetTopShape
import com.github.heartratemonitor_compose.ui.util.rememberExpandedSheetState
import com.github.heartratemonitor_compose.ui.util.rememberSheetDismissHandler
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButton
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveTextButton
import com.github.heartratemonitor_compose.ui.widgets.IconContainer
import java.util.Locale

@Composable
internal fun HeartRateCard(
    modifier: Modifier,
    heartRate: Int,
    appStatus: AppStatus,
    ringMaxHr: Int,
    onRingMaxChange: (Int) -> Unit
) {
    // appStatus（枚举）和 heartRate（Int）都是 stable 参数，直接赋值即可：
    // HeartRateCard 只在参数变化时重组，isConnected / hasData 会随参数正确更新。
    val isConnected = appStatus == AppStatus.CONNECTED
    val hasData = isConnected && heartRate > 0

    val containerColor = MaterialTheme.colorScheme.surfaceBright
    val contentColor = MaterialTheme.colorScheme.onSurface
    val trackColor = MaterialTheme.colorScheme.surfaceContainer

    val zoneColor = when {
        !hasData -> trackColor
        heartRate < 90 -> HealthLowColor
        heartRate <= 120 -> HealthMidColor
        else -> HealthHighColor
    }
    var showMaxDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconContainer(
                    icon = painterResource(R.drawable.ic_heart_rate_data),
                    containerSize = 40.dp,
                    iconSize = 24.dp,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.heart_rate_data),
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showMaxDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.cd_edit_heart_rate_max),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            SemiCircleProgress(
                progress = if (hasData) (heartRate / ringMaxHr.toFloat()).coerceIn(0f, 1f) else 0f,
                progressColor = zoneColor,
                trackColor = trackColor,
                modifier = Modifier
                    .width(240.dp)
                    .padding(vertical = 8.dp)
                    .aspectRatio(2f)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (hasData) "$heartRate" else "--",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = stringResource(R.string.bpm_unit),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showMaxDialog) {
        HeartRateRingMaxDialog(
            currentMax = ringMaxHr,
            onConfirm = { value ->
                onRingMaxChange(value)
                showMaxDialog = false
            },
            onDismiss = { showMaxDialog = false }
        )
    }
}

@Composable
internal fun HeartRateStatCard(
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int,
    title: String,
    value: String,
    unit: String
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconContainer(
                icon = painterResource(icon),
                containerSize = 36.dp,
                iconSize = 20.dp,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                iconTint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}


@Composable
private fun SemiCircleProgress(
    progress: Float,
    progressColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "HrRingProgress"
    )

    Box(
        modifier = modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = animatedProgress.coerceIn(0f, 1f),
                range = 0f..1f
            )
        },
        contentAlignment = Alignment.BottomCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 14.dp.toPx()
            val arcSize = size.width - strokeWidth
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            val boundingSize = Size(arcSize, arcSize)
            drawArc(
                color = trackColor,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = topLeft,
                size = boundingSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = 180f,
                sweepAngle = 180f * animatedProgress,
                useCenter = false,
                topLeft = topLeft,
                size = boundingSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        content()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeartRateRingMaxDialog(
    currentMax: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(currentMax) }
    val sheetState = rememberExpandedSheetState()
    val dismissWithAnimation = rememberSheetDismissHandler(sheetState, onDismiss)
    val confirmWithAnimation = rememberSheetDismissHandler(sheetState) { onConfirm(value) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetTopShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.heart_rate_ring_max),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                ExpressiveButton(
                    label = stringResource(R.string.reset),
                    onClick = { value = 180 }
                )
            }
            DragSlider(
                label = stringResource(R.string.edit_heart_rate_ring_max),
                value = value,
                onValueChange = { value = it },
                range = 120..220,
                suffix = " bpm"
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ExpressiveTextButton(
                    label = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cancel),
                    onClick = dismissWithAnimation
                )
                Spacer(Modifier.width(8.dp))
                ExpressiveButton(
                    label = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.ok),
                    onClick = confirmWithAnimation
                )
            }
        }
    }
}


@Composable
internal fun SpeedCard(
    modifier: Modifier,
    speed: Float,
    isActive: Boolean
) {
    Surface(
        modifier = modifier.height(150.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright,
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
                text = if (isActive) String.format(Locale.US, "%.1f", speed) else "--",
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


@Composable
internal fun SpeedPill(
    modifier: Modifier = Modifier,
    speed: Float,
    isActive: Boolean
) {
    val containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceBright
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.height(40.dp),
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (isActive) String.format(Locale.US, "%.1f", speed) else "--",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f)
            )
        }
    }
}
