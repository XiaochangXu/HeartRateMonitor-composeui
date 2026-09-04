package com.github.heartratemonitor_compose.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.feature.history.R
import com.github.heartratemonitor_compose.data.model.HeartRateRecordInfo
import com.github.heartratemonitor_compose.ui.widgets.IconContainer
import kotlinx.collections.immutable.ImmutableList
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.compose.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerDimensions
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.LineCartesianLayerMarkerTarget
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.LayeredComponent
import com.patrykandpatrick.vico.compose.common.MarkerCornerBasedShape
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.TextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

private const val MAX_POINTS = 300

/**
 * 图表实际绘制用的数据点：时间戳 + 平滑后的心率值
 */
private data class DisplayPoint(val timestamp: Long, val y: Double)

/** 心率历史详情图表（Vico）：逐拍心率折线 + 时间轴 + 触摸标记 */
@Composable
internal fun HeartRateChart(
    records: ImmutableList<HeartRateRecordInfo>,
    startTime: Long,
    timeFormat: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val modelProducer = remember { CartesianChartModelProducer() }

    // 5 点滑动平均平滑逐拍心率（端点收缩窗口），抑制测量噪声锯齿。
    val smoothedY = remember(records) {
        val raw = records.map { it.heartRate.toDouble() }
        when {
            records.size < 5 -> raw
            else -> raw.mapIndexed { i, _ ->
                val from = maxOf(0, i - 2)
                val to = minOf(raw.lastIndex, i + 2)
                raw.subList(from, to + 1).average()
            }
        }
    }

    // 均匀降采样：逐拍心率点数多（长会话可达数千点），平滑后抽取最多 MAX_POINTS 个点保留首尾。
    val displayPoints = remember(records, smoothedY) {
        if (smoothedY.size <= MAX_POINTS) {
            smoothedY.mapIndexed { i, y -> DisplayPoint(records[i].timestamp, y) }
        } else {
            val step = (smoothedY.size - 1).toDouble() / (MAX_POINTS - 1)
            List(MAX_POINTS) { i ->
                val index = (i * step).toInt().coerceAtMost(smoothedY.lastIndex)
                DisplayPoint(records[index].timestamp, smoothedY[index])
            }
        }
    }

    LaunchedEffect(displayPoints) {
        if (displayPoints.isNotEmpty()) {
            modelProducer.runTransaction {
                lineModel {
                    series(
                        x = displayPoints.indices.map { it.toDouble() },
                        y = displayPoints.map { it.y }
                    )
                }
            }
        }
    }

    val bottomAxisFormatter = remember(startTime, timeFormat, displayPoints) {
        CartesianValueFormatter { _, value, _ ->
            val index = value.toInt()
            if (index in displayPoints.indices) {
                timeFormat.format(Date(displayPoints[index].timestamp))
            } else {
                ""
            }
        }
    }

    val markerFormatter = remember(startTime, timeFormat, displayPoints) {
        DefaultCartesianMarker.ValueFormatter { _, targets ->
            val target = targets.firstOrNull() ?: return@ValueFormatter ""
            val lineTarget = target as? LineCartesianLayerMarkerTarget
                ?: return@ValueFormatter ""
            val point = lineTarget.points.firstOrNull() ?: return@ValueFormatter ""
            val entry = point.entry
            val index = entry.x.toInt()
            val timeString = if (index in displayPoints.indices) {
                timeFormat.format(Date(displayPoints[index].timestamp))
            } else {
                ""
            }
            // ⚠️ 反直觉设计：数值以 String 传入（%1$s），规避小语种 locale 整数格式化输出本地数字
            context.getString(R.string.marker_heart_rate, entry.y.toInt().toString(), timeString)
        }
    }

    val marker = rememberMarker(valueFormatter = markerFormatter)

    // ⚠️ 反直觉设计：Y 轴 min=hrMin-20, max=hrMax+20（不足 40 时补足），上界对齐 20bpm 刻度网格——否则顶部无刻度观感如"绘制不完整"。
    val rangeMinY = remember(records) {
        if (records.isEmpty()) 0.0 else records.minOf { it.heartRate }.toDouble() - 20.0
    }
    val rangeMaxY = remember(records) {
        if (records.isEmpty()) 40.0
        else {
            val target = maxOf(records.maxOf { it.heartRate }.toDouble() + 20.0, rangeMinY + 40.0)
            rangeMinY + ceil((target - rangeMinY) / 20.0) * 20.0
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconContainer(
                    icon = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_heart_rate_chart),
                    containerSize = 36.dp,
                    iconSize = 20.dp,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.heart_rate_chart_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            CartesianChartHost(
                chart = rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = LineCartesianLayer.LineProvider.series(
                            LineCartesianLayer.rememberLine(
                                interpolator = LineCartesianLayer.Interpolator.cubic()
                            )
                        ),
                        // Y 轴最低值锁定为心率最小值，配合下方 step(20) 刻度，
                        // 网格线从最低值起每格相差 20 bpm
                        rangeProvider = remember(rangeMinY, rangeMaxY) {
                            object : CartesianLayerRangeProvider {
                                override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                                    rangeMinY

                                override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
                                    rangeMaxY
                            }
                        }
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        // ⚠️ 反直觉设计：显式 Int.toString() 输出 ASCII 数字，规避 Vico 默认 DecimalValueFormatter 走 Locale 渲染本地数字。
                        itemPlacer = VerticalAxis.ItemPlacer.step({ 20.0 }),
                        valueFormatter = CartesianValueFormatter { _, value, _ -> value.toInt().toString() }
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        // ⚠️ 反直觉设计：首尾端点必出刻度（默认 aligned 跳过端点，数据非间距倍数时观感如"绘制不完整"）。
                        valueFormatter = bottomAxisFormatter,
                        itemPlacer = remember { EndpointsAlignedItemPlacer() }
                    ),
                    marker = marker
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp),
                scrollState = rememberVicoScrollState(scrollEnabled = true),
                zoomState = rememberVicoZoomState(
                    zoomEnabled = true,
                    initialZoom = Zoom.Content
                )
            )
        }
    }
}

/**
 * 底部时间轴刻度选择器：首尾（数据起点/终点）必出刻度，中间按“相邻时间标签不重叠”的
 * 最大密度在首尾间等分。
 *
 * 默认的 aligned 刻度只落在 minX + k×spacing 的等差网格上，且刻意跳过范围两端
 * （AlignedHorizontalAxisItemPlacer.getLabelValues 中 value == fullXRange.start/end 会被跳过）：
 * 间距按标签宽度自动抽稀（spacing × ceil(maxLabelWidth / (xSpacing × spacing))），
 * 数据点数凑巧是间距整数倍时末刻度恰好落在数据终点（看起来完整），
 * 否则最后一段数据（长会话可达数分钟）无竖网格线与时间标签，跨会话看呈概率性。
 */
private class EndpointsAlignedItemPlacer : HorizontalAxis.ItemPlacer {
    override fun getShiftExtremeLines(context: CartesianDrawingContext) = true

    // 与 aligned(addExtremeLabelPadding = true) 一致：返回数据两端作为端点标签位置，
    // 绘图区两侧各留半个标签宽内边距，保证首尾时间标签完整可见不被裁剪
    override fun getFirstLabelValue(context: CartesianMeasuringContext, maxLabelWidth: Float) =
        context.ranges.minX

    override fun getLastLabelValue(context: CartesianMeasuringContext, maxLabelWidth: Float) =
        context.ranges.maxX

    override fun getLabelValues(
        context: CartesianDrawingContext,
        visibleXRange: ClosedFloatingPointRange<Double>,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> {
        val minX = context.ranges.minX
        val maxX = context.ranges.maxX
        val xStep = context.ranges.xStep
        val stepsInRange = ((maxX - minX) / xStep).takeIf { it > 0.0 } ?: return listOf(minX)
        // 无重叠前提下最密的刻度间距（单位：xStep）：相邻刻度像素距离 ≥ 最大标签宽度
        val step = if (maxLabelWidth != 0f) {
            ceil(maxLabelWidth / context.layerDimensions.xSpacing).toInt().coerceAtLeast(1)
        } else {
            1
        }
        // 在 [minX, maxX] 间等分 count 段：首、尾必为刻度，中间取整到整数索引
        val count = floor(stepsInRange / step).toInt().coerceIn(1, 60)
        return buildList {
            add(minX)
            for (k in 1 until count) {
                val value = minX + (maxX - minX) * k / count
                add(minX + ((value - minX) / xStep).roundToInt() * xStep)
            }
            add(maxX)
        }
            .distinct()
            .filter { it in visibleXRange }
    }

    override fun getWidthMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
    ): List<Double> = listOf(context.ranges.minX, context.ranges.maxX)

    override fun getHeightMeasurementLabelValues(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        fullXRange: ClosedFloatingPointRange<Double>,
        maxLabelWidth: Float,
    ): List<Double> =
        listOf(context.ranges.minX, (context.ranges.minX + context.ranges.maxX) / 2.0, context.ranges.maxX)

    override fun getStartLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = (tickThickness / 2f - layerDimensions.unscalableStartPadding).coerceAtLeast(0f)

    override fun getEndLayerMargin(
        context: CartesianMeasuringContext,
        layerDimensions: CartesianLayerDimensions,
        tickThickness: Float,
        maxLabelWidth: Float,
    ): Float = (tickThickness / 2f - layerDimensions.unscalableEndPadding).coerceAtLeast(0f)
}

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

/** 图表延迟加载（350ms 转场错峰）期间的骨架屏，容器与 HeartRateChart 卡片保持一致 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChartSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            ContainedLoadingIndicator(
                modifier = Modifier.size(64.dp)
            )
        }
    }
}
