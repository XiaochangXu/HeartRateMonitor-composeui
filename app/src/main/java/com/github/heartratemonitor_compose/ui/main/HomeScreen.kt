package com.github.heartratemonitor_compose.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.heartratemonitor_compose.R
import com.juul.kable.Advertisement
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.cartesian.AutoScrollCondition
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.common.Fill

/**
 * 主页 Composable。替代原 [MainActivity] 的 XML 布局。
 *
 * 内部实时图表用 Vico [CartesianChartHost]（阶段 4 已从 AndroidView{LineChart} 迁移）。
 *
 * @param onOpenHistory 跳转历史页（仍是独立 Activity，阶段6后改 nav route）
 * @param onEnterFullScreen 进入全屏心率模式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenHistory: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onEnterFullScreen: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    val heartRate by viewModel.heartRate.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val appStatus by viewModel.appStatus.collectAsStateWithLifecycle()
    val newChartEntries by viewModel.newChartEntries.collectAsStateWithLifecycle()
    val sessionMaxHr by viewModel.sessionMaxHr.collectAsStateWithLifecycle()
    val sessionMinHr by viewModel.sessionMinHr.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()

    var isHistoryEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("history_recording_enabled", false)) }
    var isSpeedEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("speed_display_enabled", false)) }
    var isAnimationEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("heartbeat_animation_enabled", true)) }

    // 监听 SharedPreferences 变化（悬浮窗/历史/速度/动画开关可能从设置页修改）
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "history_recording_enabled" -> isHistoryEnabled = sharedPreferences.getBoolean("history_recording_enabled", false)
                "speed_display_enabled" -> isSpeedEnabled = sharedPreferences.getBoolean("speed_display_enabled", false)
                "heartbeat_animation_enabled" -> isAnimationEnabled = sharedPreferences.getBoolean("heartbeat_animation_enabled", true)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isConnected = appStatus == AppStatus.CONNECTED

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = context.getString(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = stringResource(R.string.cd_view_history),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        HomeContent(
            modifier = Modifier
                .fillMaxSize()
                // 仅应用顶部 padding（TopAppBar 高度），底部不应用 padding 让内容延伸到屏幕底部
                .padding(top = padding.calculateTopPadding()),
            viewModel = viewModel,
            heartRate = heartRate,
            speed = speed,
            appStatus = appStatus,
            newChartEntries = newChartEntries,
            isConnected = isConnected,
            isHistoryEnabled = isHistoryEnabled,
            isSpeedEnabled = isSpeedEnabled,
            isAnimationEnabled = isAnimationEnabled,
            sessionMaxHr = sessionMaxHr,
            sessionMinHr = sessionMinHr,
            connectedDeviceName = connectedDevice?.name,
            onNavigateToDevices = onNavigateToDevices,
            onEnterFullScreen = onEnterFullScreen
        )
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier,
    viewModel: MainViewModel,
    heartRate: Int,
    speed: Float,
    appStatus: AppStatus,
    newChartEntries: List<HeartRatePoint>?,
    isConnected: Boolean,
    isHistoryEnabled: Boolean,
    isSpeedEnabled: Boolean,
    isAnimationEnabled: Boolean,
    sessionMaxHr: Int,
    sessionMinHr: Int,
    connectedDeviceName: String?,
    onNavigateToDevices: () -> Unit,
    onEnterFullScreen: () -> Unit
) {
    // 内容延伸到屏幕底部（iOS 风格），底部 contentPadding 留出胶囊+系统导航栏空间
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 16.dp + 64.dp + 8.dp + navBarInset // 胶囊高度+边距+系统导航栏
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeartRateCard(
                    modifier = Modifier.weight(1f),
                    heartRate = heartRate,
                    appStatus = appStatus,
                    isAnimationEnabled = isAnimationEnabled,
                    sessionMaxHr = sessionMaxHr,
                    sessionMinHr = sessionMinHr
                )
                val isSpeedActive = isSpeedEnabled && isConnected
                SpeedCard(
                    modifier = Modifier.width(120.dp),
                    speed = speed,
                    isActive = isSpeedActive
                )
            }
        }

        item {
            when {
                isConnected && isHistoryEnabled -> {
                    RealtimeChart(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        viewModel = viewModel,
                        newChartEntries = newChartEntries,
                        appStatus = appStatus
                    )
                }
                isConnected && !isHistoryEnabled -> {
                    ChartPlaceholder(R.string.history_not_enabled)
                }
                else -> {
                    ChartPlaceholder(R.string.device_not_connected)
                }
            }
        }

        item {
            val availableDevicesText = stringResource(R.string.available_devices)
            val connectedDeviceText = stringResource(R.string.connected_device)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = onNavigateToDevices
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (isConnected && !connectedDeviceName.isNullOrEmpty()) {
                            "$connectedDeviceText $connectedDeviceName"
                        } else {
                            availableDevicesText
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isConnected) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onEnterFullScreen
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fullscreen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.enter_fullscreen),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            item {
                // 断开连接（与全屏模式按钮形状统一）
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = { viewModel.disconnectDevice() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BluetoothDisabled,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.disconnect),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * 图表占位卡片：未连接设备 / 已连接但未开启历史记录时显示。
 * 与 RealtimeChart 等高（200dp）+ surfaceContainerHigh 背景，保持视觉一致性。
 * 居中文字使用 alpha 0.45 含蓄提示，不抢视觉焦点。
 */
@Composable
private fun ChartPlaceholder(messageRes: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.alpha(0.45f)
        ) {
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 心率卡片：
 * 使用 surfaceContainerHigh 背景 + onSurface 文字（不跟随 seed 色，仅跟随亮/暗模式）。
 * 心跳动画作用于背景爱心 emoji，文字不跳动。
 * 右上角显示本次连接的心率最大值/最小值（断开即清零）。
 */
@Composable
private fun HeartRateCard(
    modifier: Modifier,
    heartRate: Int,
    appStatus: AppStatus,
    isAnimationEnabled: Boolean,
    sessionMaxHr: Int,
    sessionMinHr: Int
) {
    val isConnected = appStatus == AppStatus.CONNECTED

    // 中性色令牌：不跟随 seed 色，仅跟随亮/暗模式（亮色黑、暗色白）
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface

    // 心跳动画：bpm > 30 且开启动画且已连接时缩放（作用于背景爱心，文字不跳动）
    // 缩放范围 1.0 → 1.15，配合 96dp 图标尺寸：最大 ~110dp，在 150dp 高的卡片内有充足余量，不会被 Surface 圆角裁剪
    val heartScale = remember { Animatable(1f) }
    val shouldAnimate = isAnimationEnabled && heartRate > 30 && isConnected
    // 量化 bpm 到 5 步长，减少动画重启频率
    val animBpm = if (shouldAnimate) (heartRate / 5) * 5 else 0
    LaunchedEffect(animBpm) {
        if (animBpm > 0) {
            val cycleMs = (60000f / animBpm).toLong()
            while (true) {
                heartScale.animateTo(1.15f, tween((cycleMs / 2).toInt(), easing = FastOutSlowInEasing))
                heartScale.animateTo(1f, tween((cycleMs / 2).toInt(), easing = FastOutSlowInEasing))
            }
        } else {
            heartScale.animateTo(1f, tween(200))
        }
    }

    Surface(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(28.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box {
            // 背景爱心图标（纯红 + 心跳缩放动画）
            // 使用矢量图标：精确控制尺寸 96dp，缩放 1.15x ≈ 110dp，在 150dp 卡片内不会被圆角裁剪
            // 已连接：Icons.Filled.Favorite（完整爱心）；断开：Icons.Filled.HeartBroken（裂成两半的实心爱心）
            // tint 使用纯红 Color(0xFFFF0000)，alpha 0.35 保留含蓄感
            Icon(
                imageVector = if (isConnected) Icons.Filled.Favorite
                              else Icons.Filled.HeartBroken,
                contentDescription = null,
                tint = ComposeColor(0xFFFF0000),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(96.dp)
                    .alpha(if (isConnected) 0.35f else 0.25f)
                    .scale(heartScale.value)
            )

            if (isConnected && sessionMaxHr > 0) {
                Text(
                    text = "MAX ${sessionMaxHr}",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    color = contentColor.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            if (isConnected && sessionMaxHr > 0) {
                Text(
                    text = "MIN ${sessionMinHr}",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    color = contentColor.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            // BPM 数值（文字不跳动）
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (isConnected && heartRate > 0) "$heartRate" else "--",
                    color = contentColor,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "BPM",
                    color = contentColor.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

/**
 * 速度卡片：与 HeartRateCard 视觉风格一致。
 * - Surface 容器 + surfaceContainerHigh 背景 + onSurface 内容色（不跟随 seed 色）
 * - 20dp 圆角，与首页其他卡片统一（项目规范：一级卡片 20dp 圆角 + 0dp 阴影）
 * - [isActive]=false 时仅显示 "--"，颜色与已激活时一致（不做颜色区分）
 */
@Composable
private fun SpeedCard(
    modifier: Modifier,
    speed: Float,
    isActive: Boolean
) {
    Surface(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                text = if (isActive) "%.1f".format(speed) else "--",
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

/**
 * 实时心率图表（Vico CartesianChartHost）。
 *
 * 数据源:
 * - [MainViewModel.chartHistory] (List<HeartRatePoint>) 用于回填历史
 * - [newChartEntries] (List<HeartRatePoint>?) 用于逐拍增量追加
 *
 * 渲染特点（向心电图风格靠拢）:
 * - 逐拍数据:RR-Interval 累加时间戳 + 瞬时心率,分辨率高于 1Hz 平均 BPM
 * - 三次贝塞尔插值 (cubic) + 心率红渐变填充,曲线平滑有节律感
 * - 固定 Y 轴生理范围 (40–180 bpm),配合默认网格线,类似 ECG 刻度网格
 *
 * 可视窗口:最近 300 秒（scroll 到末尾实现自动跟随）
 */
@Composable
private fun RealtimeChart(
    modifier: Modifier,
    viewModel: MainViewModel,
    newChartEntries: List<HeartRatePoint>?,
    appStatus: AppStatus
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val history = viewModel.chartHistory

    // x 值用「整数毫秒」((timeOffsetSec * 1000).toLong())：浮点秒的辗转相除 GCD 会被
    // 浮点误差磨成 0 触发 Vico "x-values are too precise" 异常；整数毫秒 GCD 永不归零。
    LaunchedEffect(history.size, appStatus) {
        if (appStatus == AppStatus.CONNECTED && history.isNotEmpty()) {
            // 事务外先拷贝数据：runTransaction 是 suspend，若在事务内读 history，
            // 挂起期间 chartDataPoints 可能被新到达的心率修改，导致 ConcurrentModificationException
            val xValues = history.map { (it.timeOffsetSec * 1000).toLong().toDouble() }
            val yValues = history.map { it.heartRate.toDouble() }
            modelProducer.runTransaction {
                lineSeries {
                    series(x = xValues, y = yValues)
                }
            }
        }
    }

    // 逐拍增量追加（每帧测量到达时触发,一帧可能含多个 RR 心拍）
    LaunchedEffect(newChartEntries) {
        val points = newChartEntries ?: return@LaunchedEffect
        if (appStatus != AppStatus.CONNECTED || points.isEmpty()) return@LaunchedEffect
        val lastPoint = points.last()
        // 维护最近 300 秒可视窗口:截取 [lastPoint.timeOffsetSec - 300, lastPoint.timeOffsetSec] 范围
        val windowStart = lastPoint.timeOffsetSec - 300f
        // history 按 timeOffsetSec 单调递增，用 indexOfFirst 定位起点 + subList 视图，避免 filter 每次分配新列表
        val startIndex = history.indexOfFirst { it.timeOffsetSec >= windowStart }
        // 事务外拷贝可视窗口数据，避免 subList 视图在 runTransaction 挂起期间被并发修改
        val visible = if (startIndex >= 0) history.subList(startIndex, history.size).toList() else emptyList()
        // history 已由 ViewModel 的 appendPoint 写入本批 points,无需再追加
        if (visible.isNotEmpty()) {
            val xValues = visible.map { (it.timeOffsetSec * 1000).toLong().toDouble() }
            val yValues = visible.map { it.heartRate.toDouble() }
            modelProducer.runTransaction {
                lineSeries {
                    series(x = xValues, y = yValues)
                }
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
    val lineColor = ComposeColor(0xFFE53935)

    // 圆角卡片容器包裹心率图表，使用主题色适配深色/浅色模式，与 SpeedCard 视觉风格保持一致
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
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
                    // 固定生理范围 Y 轴,稳定刻度便于读数 (类似 ECG 固定网格)
                    rangeProvider = CartesianLayerRangeProvider.fixed(minY = 40.0, maxY = 180.0)
                ),
                startAxis = VerticalAxis.rememberStart(
                    // 固定每 20 bpm 一条网格线 (40/60/80/100/120/140/160/180),ECG 风格刻度
                    itemPlacer = VerticalAxis.ItemPlacer.step({ 20.0 }),
                    valueFormatter = CartesianValueFormatter { _, value, _ ->
                        value.toInt().toString()
                    }
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = CartesianValueFormatter { _, value, _ ->
                        // value 是整数毫秒（x 值已量化），转回分:秒显示
                        val totalSec = (value / 1000.0).toLong()
                        val minutes = totalSec / 60
                        val seconds = totalSec % 60
                        String.format("%02d:%02d", minutes, seconds)
                    }
                )
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            // 实时图表自动跟随最新数据:模型增长时自动滚动到末尾
            scrollState = rememberVicoScrollState(
                scrollEnabled = true,
                autoScrollCondition = AutoScrollCondition.OnModelGrowth
            ),
            zoomState = rememberVicoZoomState(
                zoomEnabled = true,
                initialZoom = Zoom.Content
            )
        )
    }
}

/**
 * 设备列表项：替代原 list_item_device.xml + DeviceAdapter。
 */
@Composable
internal fun DeviceItem(
    advertisement: Advertisement,
    isFavorite: Boolean,
    onDeviceClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val rssi = advertisement.rssi
    val strongColor = ComposeColor(0xFF00668B) // primary_light
    val mediumColor = ComposeColor(0xFFF59E0B)
    val weakColor = ComposeColor(0xFFB00020)   // red_error

    // 信号强度统一使用 WiFi 信号图标，通过颜色区分强/中/弱
    val signalTint: ComposeColor
    val rssiColor: ComposeColor
    when {
        rssi > -65 -> {
            signalTint = strongColor
            rssiColor = strongColor
        }
        rssi > -80 -> {
            signalTint = mediumColor
            rssiColor = mediumColor
        }
        else -> {
            signalTint = weakColor
            rssiColor = weakColor
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable { onDeviceClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = advertisement.name ?: "Unknown Device",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = advertisement.identifier,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_signal_wifi),
            contentDescription = null,
            tint = signalTint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${rssi}dBm",
            style = MaterialTheme.typography.labelSmall,
            color = rssiColor
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onFavoriteClick) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star
                               else Icons.Filled.StarBorder,
                contentDescription = stringResource(R.string.cd_favorite),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 全屏心率模式覆盖层。
 *
 * - 纯黑背景，横屏全屏显示
 * - 静态爱心 + 心率数值，按屏幕高度自适应放到最大
 * - ECG 滚动波形：屏幕底部持续左滚的心电波形，QRS 与实际心率同步
 * - 爱心在 QRS 波峰时产生微妙光晕脉冲（非缩放动画）
 * - 颜色读取设置页悬浮窗「文本颜色」选项（floating_text_color）
 * - 点击屏幕或按返回键退出
 */
@Composable
internal fun FullScreenHeartRate(
    heartRate: Int,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }
    val heartColor = remember {
        ComposeColor(sharedPreferences.getInt("floating_text_color", android.graphics.Color.RED))
    }
    val isAnimationEnabled = remember { sharedPreferences.getBoolean("heartbeat_animation_enabled", true) }

    // ECG 滚动动画：ecgPhase 在 0..1 之间循环，每个周期 = 一个心动周期（60_000/bpm ms）
    val effectiveBpm = if (isAnimationEnabled && heartRate > 30) heartRate else 0
    val ecgPhase = remember { Animatable(0f) }
    LaunchedEffect(effectiveBpm) {
        if (effectiveBpm > 0) {
            val cycleMs = (60_000f / effectiveBpm).toInt().coerceAtLeast(200)
            ecgPhase.snapTo(0f)
            ecgPhase.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = cycleMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    // 爱心光晕：QRS 波峰时最亮
    val rPeakPhase = 0.2f
    val phaseFraction = ecgPhase.value % 1f
    val rawDist = abs(phaseFraction - rPeakPhase)
    val distToPeak = min(rawDist, 1f - rawDist)
    val heartGlow = if (effectiveBpm > 0) (1f - distToPeak / 0.06f).coerceIn(0f, 1f) else 0f
    val heartAlpha = 0.75f + 0.25f * heartGlow

    BackHandler { onExit() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black)
            .drawBehind {
                val canvasW = size.width
                val canvasH = size.height
                val baseline = canvasH * 0.82f
                val amplitude = canvasH * 0.12f
                val cyclesOnScreen = 4f
                val currentPhase = ecgPhase.value

                val gridColor = heartColor.copy(alpha = 0.1f)
                val gridStep = canvasW / 20f
                var gx = 0f
                while (gx <= canvasW) {
                    drawLine(
                        color = gridColor,
                        start = Offset(gx, baseline - amplitude * 1.5f),
                        end = Offset(gx, baseline + amplitude * 1.5f),
                        strokeWidth = 2f
                    )
                    gx += gridStep
                }
                var gy = baseline - amplitude * 1.5f
                while (gy <= baseline + amplitude * 1.5f) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, gy),
                        end = Offset(canvasW, gy),
                        strokeWidth = 2f
                    )
                    gy += amplitude * 0.5f
                }

                val path = Path()
                var first = true
                var x = 0f
                while (x <= canvasW) {
                    val phase = (x / canvasW * cyclesOnScreen + currentPhase) % 1f
                    val y = if (effectiveBpm > 0) {
                        baseline - ecgWaveformValue(phase, amplitude)
                    } else {
                        baseline
                    }
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                    x += 2f
                }
                drawPath(
                    path = path,
                    color = heartColor,
                    style = Stroke(
                        width = 5f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onExit() }
    ) {
        val horizontalMargin = maxWidth * 0.05f
        val halfWidth = (maxWidth - horizontalMargin * 2) / 2
        val heartSize = minOf(halfWidth, maxHeight) * 0.9f
        val maxFontSizeByWidth = halfWidth / 2.0f
        val maxFontSizeByHeight = maxHeight * 0.85f
        val bpmFontSize = minOf(maxFontSizeByWidth, maxFontSizeByHeight).value.toInt().sp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalMargin),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = heartColor.copy(alpha = heartAlpha),
                    modifier = Modifier.size(heartSize)
                )
            }

            Text(
                text = ":",
                color = heartColor.copy(alpha = heartAlpha),
                fontSize = bpmFontSize,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (heartRate > 0) "$heartRate" else "--",
                        color = heartColor,
                        fontSize = bpmFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = stringResource(R.string.bpm_unit),
                        color = heartColor.copy(alpha = 0.7f),
                        fontSize = (bpmFontSize.value * 0.3f).toInt().sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.fullscreen_exit_hint),
            color = ComposeColor.White.copy(alpha = 0.35f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

/**
 * ECG 波形函数：返回给定相位 [phase]（0..1）处的 Y 偏移量（正值向上）。
 * 包含标准 P-QRS-T 波形：
 * - P 波（0.05~0.12）：小凸起
 * - PR 段（0.12~0.17）：基线
 * - Q 波（0.17~0.19）：小下凹
 * - R 波（0.19~0.23）：尖锐主峰
 * - S 波（0.23~0.26）：下凹
 * - ST 段（0.26~0.35）：基线
 * - T 波（0.35~0.52）：中等凸起
 * - 基线（0.52~1.0）：平线
 */
private fun ecgWaveformValue(phase: Float, amplitude: Float): Float {
    val t = phase
    return when {
        // P 波
        t < 0.05f -> 0f
        t < 0.12f -> {
            val pt = (t - 0.05f) / 0.07f
            sin(pt * PI).toFloat() * amplitude * 0.15f
        }
        // PR 段
        t < 0.17f -> 0f
        // Q 波（小下凹）
        t < 0.19f -> {
            -((t - 0.17f) / 0.02f) * amplitude * 0.1f
        }
        // R 波（尖锐主峰）
        t < 0.21f -> {
            val rt = (t - 0.19f) / 0.02f
            rt * amplitude
        }
        t < 0.23f -> {
            val rt = (t - 0.21f) / 0.02f
            (1f - rt) * amplitude
        }
        // S 波（下凹）
        t < 0.26f -> {
            val st = (t - 0.23f) / 0.03f
            -(1f - st) * amplitude * 0.25f
        }
        // ST 段
        t < 0.35f -> 0f
        // T 波
        t < 0.52f -> {
            val tt = (t - 0.35f) / 0.17f
            sin(tt * PI).toFloat() * amplitude * 0.3f
        }
        // 基线
        else -> 0f
    }
}
