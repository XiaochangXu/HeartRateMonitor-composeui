package com.github.heartratemonitor_compose.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.feature.main.R
import com.github.heartratemonitor_compose.ui.theme.HeartRateLineColor
import com.github.heartratemonitor_compose.ui.widgets.IconContainer
import com.patrykandpatrick.vico.compose.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.decoration.HorizontalLine
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.Position
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import kotlin.math.ceil
import kotlin.math.floor

/**
 * 图表卡片头部：心率统计图标 + 标题文字。
 * 在 RealtimeChart / ChartPlaceholder / ChartLoadingIndicator 三种状态下保持一致显示。
 */
@Composable
private fun ChartCardHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconContainer(
            icon = painterResource(R.drawable.ic_heart_rate_statistics),
            containerSize = 36.dp,
            iconSize = 20.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = stringResource(R.string.heart_rate_statistics),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 图表占位卡片：未连接设备 / 已连接但未开启历史记录时显示。
 * 与 RealtimeChart 等高（200dp）+ surfaceContainerHigh 背景，保持视觉一致性。
 * 居中文字使用 alpha 0.45 含蓄提示，不抢视觉焦点。
 */
@Composable
internal fun ChartPlaceholder(messageRes: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            ChartCardHeader()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.45f)
            ) {
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 图表加载占位：已连接且历史已开启、但尚未测出心率数据时显示。
 * 与 ChartPlaceholder / RealtimeChart 等高同容器，居中显示 ContainedLoadingIndicator，
 * 首个心率数据到达后由 HomeScreen 切换为 RealtimeChart。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChartLoadingIndicator() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column {
            ChartCardHeader()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}

/**
 * 实时心率图表（Vico CartesianChartHost）。
 *
 * 数据源:
 * - [MainViewModel.chartDataSnapshot] (ChartDataSnapshot?) 由 ViewModel 维护的已格式化坐标快照
 *
 * 渲染特点（向心电图风格靠拢）:
 * - 逐拍数据:RR-Interval 累加时间戳 + 瞬时心率,分辨率高于 1Hz 平均 bpm
 * - 三次贝塞尔插值 (cubic) + 心率红渐变填充,曲线平滑有节律感
 * - 动态 Y 轴范围（数据 min/max ±10，取整到 10 的倍数），配合每 10 bpm 网格线
 * - 最高/最低极值 HorizontalLine 标注（Max/Min + 数值）
 *
 * 可视窗口:最近 60 秒（scroll 到末尾实现自动跟随）
 */
@Composable
internal fun RealtimeChart(
    modifier: Modifier,
    chartDataSnapshot: ChartDataSnapshot?,
    appStatus: AppStatus
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    // 直接使用 MainViewModel 维护的已格式化坐标快照，UI 层不再每拍全量转换/拷贝。
    // 快照列表在 ViewModel 中已复制为不可变 List，避免 runTransaction 挂起期间被并发修改。
    LaunchedEffect(chartDataSnapshot) {
        val snapshot = chartDataSnapshot ?: return@LaunchedEffect
        if (appStatus != AppStatus.CONNECTED || snapshot.xValues.isEmpty()) return@LaunchedEffect
        modelProducer.runTransaction {
            lineModel {
                series(x = snapshot.xValues, y = snapshot.yValues)
            }
        }
    }

    LaunchedEffect(appStatus) {
        if (appStatus != AppStatus.CONNECTED) {
            modelProducer.runTransaction {
                // 空事务清空所有 series（无 series 调用 = 清空）
            }
        }
    }

    // 心率红主色,ECG 风格
    val lineColor = HeartRateLineColor

    // 计算 1 分钟窗口内的极值，用于 HorizontalLine 标注
    val maxY = chartDataSnapshot?.yValues?.maxOrNull() ?: 0.0
    val minY = chartDataSnapshot?.yValues?.minOrNull() ?: 0.0

    // 极值参考线 + 标签组件（微型圆角背景，提升可读性）
    val extremaLineColor = lineColor.copy(alpha = 0.35f)
    val extremaLineComp = rememberLineComponent(fill = Fill(extremaLineColor), thickness = 1.dp)
    val extremaLabelBg = rememberShapeComponent(
        fill = Fill(MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.extraSmall
    )
    val extremaLabelStyle = TextStyle(color = lineColor, fontSize = 10.sp)
    val maxLabelComp = rememberTextComponent(
        extremaLabelStyle,
        margins = Insets(start = 6.dp),
        padding = Insets(start = 6.dp, top = 2.dp, end = 6.dp, bottom = 2.dp),
        background = extremaLabelBg
    )
    val minLabelComp = rememberTextComponent(
        extremaLabelStyle,
        margins = Insets(start = 6.dp),
        padding = Insets(start = 6.dp, top = 2.dp, end = 6.dp, bottom = 2.dp),
        background = extremaLabelBg
    )
    val decorations = remember(maxY, minY) {
        buildList {
            if (maxY > 0) {
                add(
                    HorizontalLine(
                        y = { maxY },
                        line = extremaLineComp,
                        labelComponent = maxLabelComp,
                        label = { "Max ${maxY.toInt()}" },
                        verticalLabelPosition = Position.Vertical.Top
                    )
                )
                if (minY > 0 && minY != maxY) {
                    add(
                        HorizontalLine(
                            y = { minY },
                            line = extremaLineComp,
                            labelComponent = minLabelComp,
                            label = { "Min ${minY.toInt()}" },
                            verticalLabelPosition = Position.Vertical.Bottom
                        )
                    )
                }
            }
        }
    }

    // 圆角卡片容器包裹心率图表，使用主题色适配深色/浅色模式，与 SpeedCard 视觉风格保持一致
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            ChartCardHeader()
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                fill = LineCartesianLayer.LineFill.single(Fill(lineColor)),
                                stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 3.dp),
                                areaFill = LineCartesianLayer.AreaFill.single(
                                    Fill(
                                        Brush.verticalGradient(
                                            listOf(lineColor.copy(alpha = 0.35f), ComposeColor.Transparent)
                                        )
                                    )
                                ),
                                interpolator = LineCartesianLayer.Interpolator.cubic()
                            )
                        ),
                        // 动态 Y 轴范围：数据 min - 10 / max + 10，取整到 10 的倍数，保证网格线落在整数刻度
                        rangeProvider = remember {
                            object : CartesianLayerRangeProvider {
                                override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                                    floor((minY - 10.0) / 10.0) * 10.0
                                override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                                    ceil((maxY + 10.0) / 10.0) * 10.0
                            }
                        }
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        // 每 10 bpm 一条网格线，配合动态范围显示中间刻度
                        itemPlacer = VerticalAxis.ItemPlacer.step({ 10.0 }),
                        valueFormatter = CartesianValueFormatter { _, value, _ ->
                            value.toInt().toString()
                        }
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        // 采样频率低，降低标签密度：每 ~20 个数据点显示一个时间标签（约 3~4 个）
                        itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = { 20 }),
                        valueFormatter = CartesianValueFormatter { _, value, _ ->
                            // value 是整数毫秒（x 值已量化），转回分:秒显示
                            val totalSec = (value / 1000.0).toLong()
                            val minutes = totalSec / 60
                            val seconds = totalSec % 60
                            String.format("%02d:%02d", minutes, seconds)
                        }
                    ),
                    decorations = decorations
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                // 实时图表自动跟随最新数据：每次模型变更都滚动到末尾
                // OnModelGrowth 仅在数据宽度增加时触发，但 60 秒窗口会同步裁剪旧数据，宽度不变 → 不触发
                scrollState = rememberVicoScrollState(
                    scrollEnabled = true,
                    autoScrollCondition = AutoScrollCondition { _, _ -> true }
                ),
                zoomState = rememberVicoZoomState(
                    zoomEnabled = true,
                    initialZoom = Zoom.Content
                ),
                // 禁用 diff 动画与初始生长动画，避免曲线从下往上长
                animationSpec = null,
                animateIn = false
            )
        }
    }
}
