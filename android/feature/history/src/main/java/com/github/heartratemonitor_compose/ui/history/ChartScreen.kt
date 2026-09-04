package com.github.heartratemonitor_compose.ui.history

import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier

import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.github.heartratemonitor_compose.feature.history.R
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.heartratemonitor_compose.data.model.HeartRateRecordInfo
import com.github.heartratemonitor_compose.ui.theme.findActivity
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 心率历史详情页：
 * - 图表区见 ChartComponents.kt（HeartRateChart / ChartSkeleton）
 * - 统计摘要区见 SessionStatsCard.kt（ChartStats / SessionStatsCard）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ChartViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val records = uiState.records

    var startTime by remember { mutableStateOf(0L) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LaunchedEffect(sessionId) {
        viewModel.dispatch(ChartIntent.LoadRecords(sessionId))
    }

    LaunchedEffect(records) {
        startTime = records.firstOrNull()?.timestamp ?: 0L
    }

    // SAF 导出：用户选定目标文件后交由 VM 写入（业务流程归 VM，契约 10.2）
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.dispatch(ChartIntent.ExportCsv(sessionId, uri))
        }
    }
    // 导出文件名取会话首条记录的时间；无记录时退化为当前时间
    val exportFileName = remember(records) {
        val format = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val base = records.firstOrNull()?.timestamp ?: System.currentTimeMillis()
        "heart_rate_${format.format(Date(base))}.csv"
    }

    LaunchedEffect(uiState.exportEvent) {
        when (val event = uiState.exportEvent) {
            is ChartExportEvent.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.export_success, event.count.toString()),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.dispatch(ChartIntent.ConsumeExportEvent)
            }
            is ChartExportEvent.Failure -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.export_failed, event.message),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.dispatch(ChartIntent.ConsumeExportEvent)
            }
            null -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity() ?: return@onDispose
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                title = { Text(stringResource(R.string.chart_detail), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = {
                        val activity = context.findActivity()
                        if (activity != null &&
                            activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        ) {
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceBright
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_back)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch(exportFileName) }) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceBright
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_export),
                                    contentDescription = stringResource(R.string.cd_export)
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        val activity = context.findActivity() ?: return@IconButton
                        activity.requestedOrientation =
                            if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                    }) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceBright
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_screen_rotation),
                                    contentDescription = stringResource(R.string.cd_rotate_screen)
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_heart_rate_data),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
             val stats = remember(records) {
                    var sum = 0
                    var min = Int.MAX_VALUE
                    var max = Int.MIN_VALUE
                    for (record in records) {
                        val hr = record.heartRate
                        sum += hr
                        if (hr < min) min = hr
                        if (hr > max) max = hr
                    }
                    ChartStats(
                        avg = if (records.isEmpty()) 0 else sum / records.size,
                        min = min,
                        max = max,
                        startTime = records.first().timestamp,
                        endTime = records.last().timestamp
                    )
                }
                // ⚠️ 反直觉设计：records 非空后延迟 350ms 再显示图表，避免初始化与转场动画争抢主线程。横竖屏切换不触发（key 不变）。
                var chartReady by remember { mutableStateOf(false) }
                LaunchedEffect(records.isNotEmpty()) {
                    if (records.isNotEmpty()) {
                        delay(350)
                        chartReady = true
                    } else {
                        chartReady = false
                    }
                }

                if (maxWidth > maxHeight) {
                    // 横屏：可滚动，图表占满视口高度，下滑显示统计卡片
                    val viewportHeight = maxHeight
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        Spacer(Modifier.height(padding.calculateTopPadding()))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(viewportHeight)
                                .padding(16.dp)
                        ) {
                            Crossfade(
                                targetState = chartReady,
                                animationSpec = tween(200),
                                label = "chart_crossfade"
                            ) { ready ->
                                if (ready) {
                                    HeartRateChart(
                                        records = records,
                                        startTime = startTime,
                                        timeFormat = timeFormat,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    ChartSkeleton(modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                        SessionStatsCard(
                            stats = stats,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        Spacer(Modifier.height(padding.calculateTopPadding()))
                        // 图表卡片：高 300dp，四周留白（左右 16、顶部 16），
                        // 与下方统计卡片的间距由 SessionStatsCard 的 top 内边距（16dp）提供
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 16.dp, end = 16.dp)
                                .height(300.dp)
                        ) {
                            Crossfade(
                                targetState = chartReady,
                                animationSpec = tween(200),
                                label = "chart_crossfade"
                            ) { ready ->
                                if (ready) {
                                    HeartRateChart(
                                        records = records,
                                        startTime = startTime,
                                        timeFormat = timeFormat,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    ChartSkeleton(modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                        SessionStatsCard(
                            stats = stats,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 24.dp)
                                .padding(bottom = 16.dp)
                        )
                    }
                }
            }
            }
            StatusBarScrim()
        }
    }
}
