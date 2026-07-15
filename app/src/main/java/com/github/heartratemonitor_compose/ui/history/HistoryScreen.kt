<<<<<<< HEAD
package com.github.heartratemonitor_compose.ui.history
=======
﻿package com.github.heartratemonitor_compose.ui.history
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61

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
<<<<<<< HEAD
import androidx.lifecycle.compose.collectAsStateWithLifecycle
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.db.HeartRateSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class SessionPreviewData(
    val recordCount: Int,
    val avgHeartRate: Double,
    val minHeartRate: Int,
    val maxHeartRate: Int,
    val heartRateSamples: List<Int>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    db: AppDatabase,
    onNavigateBack: () -> Unit,
    onNavigateToChart: (Long) -> Unit
) {
    val context = LocalContext.current
<<<<<<< HEAD
    val sessions by db.heartRateDao().getAllSessions().collectAsStateWithLifecycle(initialValue = emptyList())
=======
    val sessions by db.heartRateDao().getAllSessions().collectAsState(initial = emptyList())
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
    val scope = rememberCoroutineScope()

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var previewDataMap by remember { mutableStateOf(mapOf<Long, SessionPreviewData>()) }

    // 异步加载每个会话的预览数据
<<<<<<< HEAD
    // 优化：用 getAllSessionStats() 批量获取统计信息（1 次查询替代 N 次），
    // 仅对有数据的 session 查询轻量采样数据用于迷你图表。
    LaunchedEffect(sessions) {
        if (sessions.isEmpty()) {
            previewDataMap = emptyMap()
            return@LaunchedEffect
        }
        // 批量获取所有会话的聚合统计
        val statsList = db.heartRateDao().getAllSessionStats()
        val statsMap = statsList.associateBy { it.sessionId }

        val previewMap = mutableMapOf<Long, SessionPreviewData>()
        for (session in sessions) {
            val stats = statsMap[session.id] ?: continue
            if (stats.recordCount <= 0) continue

            // 仅查询心率值用于迷你图表采样（轻量查询，不加载完整记录对象）
            val heartRates = db.heartRateDao().getHeartRatesForSession(session.id)
            val step = maxOf(1, heartRates.size / 50)
            val samples = heartRates.filterIndexed { index, _ -> index % step == 0 }
            previewMap[session.id] = SessionPreviewData(
                recordCount = stats.recordCount,
                avgHeartRate = stats.avgHeartRate?.toDouble() ?: 0.0,
                minHeartRate = stats.minHeartRate ?: 0,
                maxHeartRate = stats.maxHeartRate ?: 0,
                heartRateSamples = samples
            )
=======
    LaunchedEffect(sessions) {
        val previewMap = mutableMapOf<Long, SessionPreviewData>()
        for (session in sessions) {
            val records = db.heartRateDao().getRecordsForSession(session.id)
            if (records.isNotEmpty()) {
                val heartRates = records.map { it.heartRate }
                val avg = heartRates.average()
                val min = heartRates.min()
                val max = heartRates.max()
                // 最多采样 50 个点用于迷你图表
                val step = maxOf(1, records.size / 50)
                val samples = records.filterIndexed { index, _ -> index % step == 0 }
                    .map { it.heartRate }
                previewMap[session.id] = SessionPreviewData(
                    recordCount = records.size,
                    avgHeartRate = avg,
                    minHeartRate = min,
                    maxHeartRate = max,
                    heartRateSamples = samples
                )
            }
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
        }
        previewDataMap = previewMap
    }

    Scaffold(
        contentWindowInsets = WindowInsets.navigationBars,
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
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "还没有任何历史记录",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
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
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 条历史记录吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val deleteCount = selectedIds.size
                    scope.launch {
                        try {
                            db.heartRateDao().deleteSessionsByIds(selectedIds.toList())
                            showDeleteDialog = false
                            isMultiSelectMode = false
                            selectedIds = emptySet()
                            Toast.makeText(context, "已删除 $deleteCount 条记录", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "删除失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
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
                text = if (isMultiSelectMode) "已选择 $selectedCount 项" else "心率历史记录",
                style = MaterialTheme.typography.titleLarge
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            if (isMultiSelectMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "全选",
                        tint = if (selectedCount == totalCount)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
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
    session: HeartRateSession,
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                } ?: "进行中"
                Text(
                    text = "$startTime - $endTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 显示预览统计数据
                if (previewData != null && !isMultiSelectMode) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "平均 ${previewData.avgHeartRate.toInt()} · 最低 ${previewData.minHeartRate} · 最高 ${previewData.maxHeartRate} · ${previewData.recordCount} 条记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isMultiSelectMode) {
                // 迷你折线图预览
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

        // 绘制网格线（水平参考线）
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

        // 绘制折线
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

        // 绘制起点和终点圆点
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
