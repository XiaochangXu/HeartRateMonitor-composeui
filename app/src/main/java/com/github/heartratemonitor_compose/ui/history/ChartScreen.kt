package com.github.heartratemonitor_compose.ui.history

import android.content.pm.ActivityInfo
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ScreenRotation
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.github.heartratemonitor_compose.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import com.github.heartratemonitor_compose.ui.theme.findActivity
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LayeredComponent
import com.patrykandpatrick.vico.compose.common.MarkerCornerBasedShape
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * 心率历史详情图表 Composable。
 *
 * 用 Vico 3.2.3 纯 Compose 图表库绘制:
 * - `CartesianChartHost` + `LineCartesianLayer` 折线图
 * - X 轴格式化为 `HH:mm:ss`（基于会话开始时间偏移）
 * - Marker 显示「心率: X bpm / 时间: HH:mm:ss」
 *
 * 横竖屏切换：TopAppBar 右上角 actions 内嵌 ScreenRotation IconButton，
 * 通过 [findActivity] 拿到 Activity 调 [android.app.Activity.requestedOrientation]。
 * AndroidManifest 中 MainActivity 已声明 configChanges，横竖屏切换不重建 Activity，
 * Compose 通过 LocalConfiguration 自动重组响应新方向，状态完整保留。
 * 离开本页时由 DisposableEffect 重置方向为竖屏。
 *
 * @param sessionId 心率会话 ID
 * @param onNavigateBack 返回按钮回调（由 AppRoot NavController.popBackStack 触发）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    sessionId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ChartViewModel = viewModel()
    val records by viewModel.records.collectAsStateWithLifecycle()

    var startTime by remember { mutableStateOf(0L) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LaunchedEffect(sessionId) {
        viewModel.loadRecords(sessionId)
    }

    LaunchedEffect(records) {
        startTime = records.firstOrNull()?.timestamp ?: 0L
    }

    // 离开 ChartScreen 时重置为竖屏。
    // AndroidManifest 已声明 configChanges，横屏切换不会重建 Activity（状态不丢失），
    // 但 requestedOrientation 会保留——若不重置，返回 History 列表页会停在横屏。
    DisposableEffect(Unit) {
        onDispose {
            val activity = context.findActivity() ?: return@onDispose
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chart_detail), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val activity = context.findActivity() ?: return@IconButton
                        activity.requestedOrientation =
                            if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.ScreenRotation,
                            contentDescription = stringResource(R.string.cd_rotate_screen)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
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
                // 从已加载记录计算统计摘要，无需额外查库。
                // 单次遍历计算 sum/min/max，避免 map+average+min+max 多次遍历开销。
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

                // 图表渲染延迟，避免首次初始化与导航转场动画争抢主线程。
                // records 非空后延迟 350ms（= SECONDary_SLIDE_DURATION 二级页面转场时长）再显示图表，
                // 期间显示骨架屏。横竖屏切换不触发（key 不变），chartReady 保持 true。
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
                    ) {
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
                                .padding(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                        )
                    }
                } else {
                    // 竖屏：图表 weight(1f) + 统计卡片固定底部
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
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
                                .padding(bottom = 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                        )
                    }
                }
            }
        }
    }
}

/**
 * 心率折线图。基于 Vico CartesianChartHost。
 *
 * @param records 心率记录列表（已按时间升序）
 * @param startTime 会话起始时间戳（毫秒）
 * @param timeFormat X 轴时间格式化器
 */
@Composable
private fun HeartRateChart(
    records: List<HeartRateRecord>,
    startTime: Long,
    timeFormat: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val modelProducer = remember { CartesianChartModelProducer() }

    // 数据填充:把记录转成 Vico 的 lineSeries（X=整数索引，Y=心率）
    // Vico 的 getXDeltaGcd 会计算相邻 x 差值的 GCD 来确定 X 轴刻度间隔，
    // 对时间戳（毫秒大数）或浮点秒做 GCD 会触发 "x-values are too precise" 异常。
    // 用整数索引（0,1,2,...）彻底规避：GCD=1，精度安全。
    // X 轴显示的时间由 bottomAxisFormatter 根据索引查 records[index].timestamp 还原。
    LaunchedEffect(records) {
        if (records.isNotEmpty()) {
            modelProducer.runTransaction {
                lineModel {
                    series(
                        x = records.indices.map { it.toDouble() },
                        y = records.map { it.heartRate.toDouble() }
                    )
                }
            }
        }
    }

    // X 轴格式化:索引 → HH:mm:ss（根据索引查 records 对应时间戳）
    val bottomAxisFormatter = remember(startTime, timeFormat, records) {
        CartesianValueFormatter { _, value, _ ->
            val index = value.toInt()
            if (index in records.indices) {
                timeFormat.format(Date(records[index].timestamp))
            } else {
                ""
            }
        }
    }

    val markerFormatter = remember(startTime, timeFormat, records) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.firstOrNull() ?: return@ValueFormatter ""
            // CartesianMarker.Target 接口只有 x/canvasX,需转换为 LineCartesianLayerMarkerTarget 才能访问 entry
            val lineTarget = target as? LineCartesianLayerMarkerTarget
                ?: return@ValueFormatter ""
            val point = lineTarget.points.firstOrNull() ?: return@ValueFormatter ""
            val entry = point.entry  // LineCartesianLayerModel.Entry (有 x: Double, y: Double)
            val index = entry.x.toInt()
            val timeString = if (index in records.indices) {
                timeFormat.format(Date(records[index].timestamp))
            } else {
                ""
            }
            java.util.Locale.getDefault().let { context.getString(R.string.marker_heart_rate, entry.y.toInt(), timeString) }
        }
    }

    val marker = rememberMarker(valueFormatter = markerFormatter)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = bottomAxisFormatter
            ),
            marker = marker
        ),
        modelProducer = modelProducer,
        modifier = modifier,
        // 禁用水平滚动：让图表自动将全部心率数据压缩适配到视口宽度，
        // 进入详情页即可看到完整曲线，无需手动滑动。
        scrollState = rememberVicoScrollState(scrollEnabled = false)
    )
}

/**
 * 自定义 Marker（基于 Vico sample）。
 * 复刻 sample/compose/.../Marker.kt 的样式:圆角背景 + 居中文本 + 指示点 + 垂直辅助线。
 */
@Composable
private fun rememberMarker(
    valueFormatter: DefaultCartesianMarker.ValueFormatter
): CartesianMarker {
    val labelBackgroundShape = MarkerCornerBasedShape(CircleShape)
    val labelBackground = rememberShapeComponent(
        fill = Fill(MaterialTheme.colorScheme.background),
        shape = labelBackgroundShape,
        strokeFill = Fill(MaterialTheme.colorScheme.outline),
        strokeThickness = 1.dp,
    )
    val label = rememberTextComponent(
        style = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
        ),
        padding = Insets(8.dp, 4.dp),
        background = labelBackground,
        minWidth = TextComponent.MinWidth.fixed(40.dp),
    )
    val indicatorFrontComponent =
        rememberShapeComponent(Fill(MaterialTheme.colorScheme.surface), CircleShape)
    val guideline = rememberAxisGuidelineComponent()
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = valueFormatter,
        indicator = { color ->
            LayeredComponent(
                back = ShapeComponent(Fill(color.copy(alpha = 0.15f)), CircleShape),
                front = LayeredComponent(
                    back = ShapeComponent(fill = Fill(color), shape = CircleShape),
                    front = indicatorFrontComponent,
                    padding = Insets(5.dp),
                ),
                padding = Insets(10.dp),
            )
        },
        indicatorSize = 36.dp,
        guideline = guideline,
    )
}

/**
 * 图表页统计摘要（从已加载记录计算，无需额外查库）。
 */
private data class ChartStats(
    val avg: Int,
    val min: Int,
    val max: Int,
    val startTime: Long,
    val endTime: Long
)

/**
 * 统计卡片：2×2 网格显示平均/最低/最高/时间。
 * 28dp 圆角 + surfaceContainer 背景，与设置页卡片风格统一。
 * 时间用 HH:mm 紧凑格式（去掉秒），避免竖屏半屏宽度放不下起止时间。
 */
@Composable
private fun SessionStatsCard(
    stats: ChartStats,
    modifier: Modifier = Modifier
) {
    val compactTimeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val startTimeStr = remember(stats.startTime, compactTimeFormat) {
        compactTimeFormat.format(Date(stats.startTime))
    }
    val endTimeStr = remember(stats.endTime, compactTimeFormat) {
        compactTimeFormat.format(Date(stats.endTime))
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCell(
                    label = stringResource(R.string.stat_avg),
                    value = stringResource(R.string.stat_bpm_value, stats.avg),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.stat_min),
                    value = stringResource(R.string.stat_bpm_value, stats.min),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatCell(
                    label = stringResource(R.string.stat_max),
                    value = stringResource(R.string.stat_bpm_value, stats.max),
                    modifier = Modifier.weight(1f)
                )
                StatCell(
                    label = stringResource(R.string.stat_time),
                    value = stringResource(R.string.stat_time_range, startTimeStr, endTimeStr),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * 图表加载骨架屏。
 *
 * 进入详情页时短暂显示，避免 Vico 图表首次初始化与导航转场动画争抢主线程。
 * 用 surfaceVariant 半透明背景 + 16dp 圆角模拟图表区域外观，无需额外动画依赖。
 * 323 条本地数据查询通常 < 50ms，骨架屏一闪而过，用户基本无感。
 */
@Composable
private fun ChartSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {}
}
