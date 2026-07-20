package com.github.heartratemonitor_compose.ui.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
import android.widget.Toast
import com.github.heartratemonitor_compose.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToChart: (Long) -> Unit
) {
    val context = LocalContext.current
    val viewModel: HistoryViewModel = viewModel()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val previewDataMap by viewModel.previewDataMap.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HistoryTopBar(
                isMultiSelectMode = isMultiSelectMode,
                selectedCount = selectedIds.size,
                totalCount = sessions.size,
                onNavigateBack = {
                    if (isMultiSelectMode) {
                        isMultiSelectMode = false
                        selectedIds = emptySet()
                    } else {
                        onNavigateBack()
                    }
                },
                onSelectAll = {
                    selectedIds = sessions.map { it.id }.toSet()
                },
                onDelete = {
                    if (selectedIds.isNotEmpty()) {
                        showDeleteDialog = true
                    }
                }
            )
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
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
                    .padding(top = padding.calculateTopPadding())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    // 底部留出系统导航栏空间
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
                                toggleSelection(session.id, selectedIds) { selectedIds = it }
                            } else {
                                onNavigateToChart(session.id)
                            }
                        },
                        onLongClick = {
                            if (!isMultiSelectMode) {
                                isMultiSelectMode = true
                                toggleSelection(session.id, selectedIds) { selectedIds = it }
                            }
                        },
                        onCheckToggle = {
                            toggleSelection(session.id, selectedIds) { selectedIds = it }
                            if (selectedIds.isEmpty()) {
                                isMultiSelectMode = false
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.confirm_delete)) },
            text = { Text(stringResource(R.string.delete_history_confirm, selectedIds.size)) },
            confirmButton = {
                TextButton(onClick = {
                    val deleteCount = selectedIds.size
                    scope.launch {
                        try {
                            viewModel.deleteSessions(selectedIds.toList())
                            showDeleteDialog = false
                            isMultiSelectMode = false
                            selectedIds = emptySet()
                            Toast.makeText(context, context.getString(R.string.deleted_records, deleteCount), Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, context.getString(R.string.delete_failed, e.message), Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.confirm_text)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTopBar(
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    onNavigateBack: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = if (isMultiSelectMode) stringResource(R.string.selected_count, selectedCount) else stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
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
                        contentDescription = stringResource(R.string.cd_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionCard(
    session: com.github.heartratemonitor_compose.data.db.HeartRateSession,
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                    shape = RoundedCornerShape(12.dp),
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

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        if (samples.size < 2) return@Canvas

        val minVal = samples.min().toFloat()
        val maxVal = samples.max().toFloat()
        val range = (maxVal - minVal).coerceAtLeast(1f)

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
            val y = canvasHeight - ((value - minVal) / range) * (canvasHeight - 4f) - 2f
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

        val firstY = canvasHeight - ((samples.first() - minVal) / range) * (canvasHeight - 4f) - 2f
        val lastY = canvasHeight - ((samples.last() - minVal) / range) * (canvasHeight - 4f) - 2f
        drawCircle(color = lineColorValue, radius = 2.5f, center = Offset(0f, firstY))
        drawCircle(
            color = lineColorValue,
            radius = 2.5f,
            center = Offset(canvasWidth, lastY)
        )
    }
}

private fun toggleSelection(
    sessionId: Long,
    current: Set<Long>,
    onUpdate: (Set<Long>) -> Unit
) {
    onUpdate(
        if (current.contains(sessionId)) current - sessionId
        else current + sessionId
    )
}
