package com.github.heartratemonitor_compose.ui.history

import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import com.github.heartratemonitor_compose.ui.theme.findActivity
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val db = remember { AppDatabase.getDatabase(context) }

    var records by remember { mutableStateOf<List<HeartRateRecord>>(emptyList()) }
    var startTime by remember { mutableStateOf(0L) }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // 异步加载会话记录
    LaunchedEffect(sessionId) {
        val loaded = withContext(Dispatchers.IO) {
            db.heartRateDao().getRecordsForSession(sessionId)
        }
        records = loaded
        startTime = loaded.firstOrNull()?.timestamp ?: 0L
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("心率详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
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
                            contentDescription = "切换方向"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            if (records.isEmpty()) {
                Text(
                    text = "没有心率数据可显示",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                HeartRateChart(
                    records = records,
                    startTime = startTime,
                    timeFormat = timeFormat,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                )
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
    val modelProducer = remember { CartesianChartModelProducer() }

    // 数据填充:把记录转成 Vico 的 lineSeries（X=整数索引，Y=心率）
    // Vico 的 getXDeltaGcd 会计算相邻 x 差值的 GCD 来确定 X 轴刻度间隔，
    // 对时间戳（毫秒大数）或浮点秒做 GCD 会触发 "x-values are too precise" 异常。
    // 用整数索引（0,1,2,...）彻底规避：GCD=1，精度安全。
    // X 轴显示的时间由 bottomAxisFormatter 根据索引查 records[index].timestamp 还原。
    LaunchedEffect(records) {
        if (records.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
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

    // Marker:显示「心率: X bpm / 时间: HH:mm:ss」
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
            "心率: ${entry.y.toInt()} bpm\n时间: $timeString"
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
        modifier = modifier
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
