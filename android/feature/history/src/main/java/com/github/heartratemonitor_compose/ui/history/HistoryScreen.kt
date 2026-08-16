package com.github.heartratemonitor_compose.ui.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import android.widget.Toast
import com.github.heartratemonitor_compose.feature.history.R
import com.github.heartratemonitor_compose.data.model.HeartRateSessionInfo
import com.github.heartratemonitor_compose.ui.util.SheetTopShape
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import com.github.heartratemonitor_compose.ui.util.cardShape
import com.github.heartratemonitor_compose.ui.util.rememberExpandedSheetState
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChart: (Long) -> Unit,
    isInTab: Boolean = false
) {
    val context = LocalContext.current
    val viewModel: HistoryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sessions = uiState.sessions
    val previewDataMap = uiState.previewDataMap
    // 多选态为业务状态（影响删除行为），归 ViewModel；弹窗显隐属纯瞬时态保留 UI
    val isMultiSelectMode = uiState.isMultiSelectMode
    val selectedIds = uiState.selectedIds

    var showDeleteDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HistoryTopBar(
                isMultiSelectMode = isMultiSelectMode,
                selectedCount = selectedIds.size,
                totalCount = sessions.size,
                isInTab = isInTab,
                onNavigateBack = {
                    if (isMultiSelectMode) {
                        viewModel.dispatch(HistoryIntent.ExitMultiSelect)
                    } else {
                        onNavigateBack()
                    }
                },
                onSelectAll = {
                    viewModel.dispatch(HistoryIntent.SelectAll)
                },
                onDelete = {
                    if (selectedIds.isNotEmpty()) {
                        showDeleteDialog = true
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_history),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding() + 16.dp,
                        bottom = 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    )
                ) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        previewData = previewDataMap[session.id],
                        isMultiSelectMode = isMultiSelectMode,
                        isSelected = selectedIds.contains(session.id),
                        onClick = {
                            if (isMultiSelectMode) {
                                viewModel.dispatch(HistoryIntent.ToggleSelection(session.id, exitIfEmpty = false))
                            } else {
                                onNavigateToChart(session.id)
                            }
                        },
                        onLongClick = {
                            if (!isMultiSelectMode) {
                                viewModel.dispatch(HistoryIntent.EnterMultiSelect(session.id))
                            }
                        },
                        onCheckToggle = {
                            viewModel.dispatch(HistoryIntent.ToggleSelection(session.id, exitIfEmpty = true))
                        }
                    )
                }
            }
            }
            StatusBarScrim()
        }
    }

    if (showDeleteDialog) {
        val sheetState = rememberExpandedSheetState()
        ModalBottomSheet(
            onDismissRequest = { showDeleteDialog = false },
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
                Text(
                    text = stringResource(R.string.confirm_delete),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.delete_history_confirm, selectedIds.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text(stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        val deleteCount = selectedIds.size
                        val deleteIds = selectedIds.toList()
                        // dispatch 为即发即忘（与原 deleteSessions 内部 launch 语义一致），
                        // 删除结果由 sessions Flow 回流刷新；Toast 沿用原成功文案
                        try {
                            viewModel.dispatch(HistoryIntent.DeleteSessions(deleteIds))
                            showDeleteDialog = false
                            viewModel.dispatch(HistoryIntent.ExitMultiSelect)
                            Toast.makeText(context, context.getString(R.string.deleted_records, deleteCount), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.delete_failed, e.message), Toast.LENGTH_LONG).show()
                        }
                    }) { Text(stringResource(R.string.confirm_text)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTopBar(
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    isInTab: Boolean,
    onNavigateBack: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    TopAppBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        title = {
            Text(
                text = if (isMultiSelectMode) stringResource(R.string.selected_count, selectedCount) else stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        navigationIcon = {
            // Tab 模式下非多选时不显示返回按钮；多选模式仍显示（用于退出多选）
            if (isMultiSelectMode || !isInTab) {
                IconButton(onClick = onNavigateBack) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_back))
                        }
                    }
                }
            }
        },
        actions = {
            if (isMultiSelectMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.cd_select_all),
                        tint = if (selectedCount == totalCount)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: HeartRateSessionInfo,
    previewData: SessionPreviewData?,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onCheckToggle: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val bgColor by animateColorAsState(
        targetValue = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else
            Color.Transparent,
        label = "cardBg"
    )

    val sessionCardShape = MaterialTheme.shapes.extraLarge
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(sessionCardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = sessionCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onCheckToggle() },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val startTime = dateFormat.format(Date(session.startTime))
                val endTime = session.endTime?.let {
                    dateFormat.format(Date(it)).substring(11)
                } ?: stringResource(R.string.in_progress)
                Text(
                    text = "$startTime - $endTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (previewData != null && !isMultiSelectMode) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.stats_format, previewData.avgHeartRate.toInt(), previewData.minHeartRate, previewData.maxHeartRate, previewData.recordCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isMultiSelectMode) {
                if (previewData != null && previewData.heartRateSamples.size >= 2) {
                    MiniChart(
                        samples = previewData.heartRateSamples,
                        modifier = Modifier
                            .width(72.dp)
                            .height(36.dp)
                            .padding(start = 4.dp),
                        lineColor = MaterialTheme.colorScheme.primary,
                        gridColor = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Surface(
                    modifier = Modifier.size(24.dp),
                    shape = cardShape(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniChart(
    samples: List<Int>,
    modifier: Modifier = Modifier,
    lineColor: Color,
    gridColor: Color
) {
    val lineColorValue = lineColor
    val gridColorValue = gridColor

    val chartData = remember(samples) {
        val minVal = samples.min().toFloat()
        val maxVal = samples.max().toFloat()
        val range = (maxVal - minVal).coerceAtLeast(1f)
        MiniChartData(
            minVal = minVal,
            maxVal = maxVal,
            range = range,
            first = samples.first(),
            last = samples.last()
        )
    }

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        if (samples.size < 2) return@Canvas

        val gridLineColor = gridColorValue
        for (i in 0..2) {
            val y = canvasHeight * i / 2f
            drawLine(
                color = gridLineColor,
                start = Offset(0f, y),
                end = Offset(canvasWidth, y),
                strokeWidth = 0.5f
            )
        }

        val stepX = canvasWidth / (samples.size - 1).coerceAtLeast(1)
        val path = Path()
        samples.forEachIndexed { index, value ->
            val x = index * stepX
            // 翻转Y轴：最大值在底部，最小值在顶部
            val y = canvasHeight - ((value - chartData.minVal) / chartData.range) * (canvasHeight - 4f) - 2f
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColorValue,
            style = Stroke(
                width = 2f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        val firstY = canvasHeight - ((chartData.first - chartData.minVal) / chartData.range) * (canvasHeight - 4f) - 2f
        val lastY = canvasHeight - ((chartData.last - chartData.minVal) / chartData.range) * (canvasHeight - 4f) - 2f
        drawCircle(color = lineColorValue, radius = 2.5f, center = Offset(0f, firstY))
        drawCircle(
            color = lineColorValue,
            radius = 2.5f,
            center = Offset(canvasWidth, lastY)
        )
    }
}

private data class MiniChartData(
    val minVal: Float,
    val maxVal: Float,
    val range: Float,
    val first: Int,
    val last: Int
)
