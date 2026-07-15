package com.github.heartratemonitor_compose.ui.main

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.heartratemonitor_compose.R
import com.juul.kable.Advertisement
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
 * @param onToggleFloatingWindow 切换悬浮窗开关（包含权限检查，由 Activity 处理）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenHistory: () -> Unit,
    onToggleFloatingWindow: () -> Unit
) {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    // 订阅 ViewModel 状态（StateFlow → collectAsStateWithLifecycle）
    val heartRate by viewModel.heartRate.collectAsStateWithLifecycle()
    val speed by viewModel.speed.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val appStatus by viewModel.appStatus.collectAsStateWithLifecycle()
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val newChartEntries by viewModel.newChartEntries.collectAsStateWithLifecycle()

    // 读取本地设置（每次重组读最新）
    var isHistoryEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("history_recording_enabled", false)) }
    var isSpeedEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("speed_display_enabled", false)) }
    var isAnimationEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("heartbeat_animation_enabled", true)) }
    var floatingWindowEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("floating_window_enabled", false)) }

    // 监听 SharedPreferences 变化（悬浮窗/历史/速度/动画开关可能从设置页修改）
    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                "history_recording_enabled" -> isHistoryEnabled = sharedPreferences.getBoolean("history_recording_enabled", false)
                "speed_display_enabled" -> isSpeedEnabled = sharedPreferences.getBoolean("speed_display_enabled", false)
                "heartbeat_animation_enabled" -> isAnimationEnabled = sharedPreferences.getBoolean("heartbeat_animation_enabled", true)
                "floating_window_enabled" -> floatingWindowEnabled = sharedPreferences.getBoolean("floating_window_enabled", false)
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
                    // 悬浮窗开关
                    IconButton(onClick = onToggleFloatingWindow) {
                        Icon(
                            painter = painterResource(
                                if (floatingWindowEnabled) R.drawable.ic_floating_window_on
                                else R.drawable.ic_floating_window_off
                            ),
                            contentDescription = "切换悬浮窗",
                            tint = if (floatingWindowEnabled) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 历史按钮
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "查看历史记录",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // 扫描按钮
                    IconButton(
                        onClick = { viewModel.startScan() },
                        enabled = appStatus == AppStatus.DISCONNECTED
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "搜索蓝牙设备",
                            tint = if (appStatus == AppStatus.DISCONNECTED) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        HomeContent(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            viewModel = viewModel,
            heartRate = heartRate,
            speed = speed,
            statusMessage = statusMessage,
            appStatus = appStatus,
            scanResults = scanResults,
            newChartEntries = newChartEntries,
            isConnected = isConnected,
            isHistoryEnabled = isHistoryEnabled,
            isSpeedEnabled = isSpeedEnabled,
            isAnimationEnabled = isAnimationEnabled
        )
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier,
    viewModel: MainViewModel,
    heartRate: Int,
    speed: Float,
    statusMessage: String,
    appStatus: AppStatus,
    scanResults: List<Advertisement>,
    newChartEntries: List<HeartRatePoint>?,
    isConnected: Boolean,
    isHistoryEnabled: Boolean,
    isSpeedEnabled: Boolean,
    isAnimationEnabled: Boolean
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 顶部卡片行：心率卡 + 速度卡
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeartRateCard(
                    modifier = Modifier.weight(1f),
                    heartRate = heartRate,
                    statusMessage = statusMessage,
                    appStatus = appStatus,
                    isAnimationEnabled = isAnimationEnabled
                )
                if (isSpeedEnabled && isConnected) {
                    SpeedCard(
                        modifier = Modifier.width(120.dp),
                        speed = speed
                    )
                }
            }
        }

        // 实时图表（连接且开启历史记录时显示）
        if (isConnected && isHistoryEnabled) {
            item {
                RealtimeChart(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    viewModel = viewModel,
                    newChartEntries = newChartEntries,
                    appStatus = appStatus
                )
            }
        }

        // 设备列表标题（未连接时）
        if (!isConnected) {
            item {
                Text(
                    text = "可用设备",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            items(
                items = scanResults.sortedWith(
                    compareByDescending<Advertisement> { viewModel.isDeviceFavorite(it.identifier) }
                        .thenByDescending { it.rssi }
                ),
                key = { it.identifier }
            ) { advertisement ->
                DeviceItem(
                    advertisement = advertisement,
                    isFavorite = viewModel.isDeviceFavorite(advertisement.identifier),
                    onDeviceClick = { viewModel.connectToDevice(advertisement.identifier) },
                    onFavoriteClick = {
                        viewModel.toggleFavoriteDevice(advertisement)
                    }
                )
            }
        }

        // 断开连接按钮（已连接时）
        if (isConnected) {
            item {
                Button(
                    onClick = { viewModel.disconnectDevice() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.BluetoothDisabled,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("断开连接")
                }
            }
        }
    }
}

/** 心率卡片：
 * 连接时显示「❤️ + 数值」+ 渐变红背景 + 心跳动画，
 * 未连接时显示「💔 + 未连接」+ 灰色渐变。
 */
@Composable
private fun HeartRateCard(
    modifier: Modifier,
    heartRate: Int,
    statusMessage: String,
    appStatus: AppStatus,
    isAnimationEnabled: Boolean
) {
    val isConnected = appStatus == AppStatus.CONNECTED

    // 渐变背景（对应原 drawable background_heart_rate_connected/disconnected，angle=135）
    val gradientColors = if (isConnected) {
        listOf(ComposeColor(0xFFFCA5A5), ComposeColor(0xFFF87171))
    } else {
        listOf(ComposeColor(0xFFD4D4D8), ComposeColor(0xFFA1A1AA))
    }

    // 心跳动画：bpm > 30 且开启动画且已连接时缩放
    val heartScale = remember { Animatable(1f) }
    val shouldAnimate = isAnimationEnabled && heartRate > 30 && isConnected
    // 量化 bpm 到 5 步长，减少动画重启频率（对应原 (currentDuration - targetDuration).absoluteValue > 50 阈值）
    val animBpm = if (shouldAnimate) (heartRate / 5) * 5 else 0
    LaunchedEffect(animBpm) {
        if (animBpm > 0) {
            val cycleMs = (60000f / animBpm).toLong()
            // 循环：1 → 1.3 → 1，单次完整周期等于 cycleMs
            while (true) {
                heartScale.animateTo(1.3f, tween((cycleMs / 2).toInt(), easing = FastOutSlowInEasing))
                heartScale.animateTo(1f, tween((cycleMs / 2).toInt(), easing = FastOutSlowInEasing))
            }
        } else {
            heartScale.animateTo(1f, tween(200))
        }
    }

    Surface(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(24.dp),
        color = ComposeColor.Transparent
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    colors = gradientColors,
                    start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f)
                )
            )
        ) {
            // 背景心形 emoji（半透明）
            Text(
                text = if (isConnected) "❤️" else "💔",
                fontSize = 100.sp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .alpha(0.2f)
            )

            // BPM 数值（连接时）—— 用 scaled Modifier 应用动画
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (isConnected && heartRate > 0) "$heartRate" else "--",
                    color = ComposeColor.White,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .scale(heartScale.value)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "BPM",
                    color = ComposeColor.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // 状态栏（左下角：图标 + 进度/状态文本）
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (appStatus == AppStatus.SCANNING || appStatus == AppStatus.CONNECTING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = ComposeColor.White
                    )
                } else {
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.Bluetooth
                                      else Icons.Filled.BluetoothDisabled,
                        contentDescription = null,
                        tint = if (isConnected) ComposeColor.White
                               else ComposeColor.White.copy(alpha = 0.87f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = statusMessage,
                    color = ComposeColor.White.copy(alpha = 0.87f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 速度卡片：遵循 Material 3 规范。
 * - 容器使用 MD3 [Card] 组件 (Filled 变体,0dp 阴影,保持与 HeartRateCard 平齐)
 * - 形状/排版均使用 MaterialTheme 令牌 (shapes.extraLarge、typography.*)
 * - 容器色 surfaceContainerLow,onSurfaceVariant 作为辅助文本色,符合 M3 色调层级
 */
@Composable
private fun SpeedCard(
    modifier: Modifier,
    speed: Float
) {
    Card(
        modifier = modifier.height(150.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "速度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "%.1f".format(speed),
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

    // 历史数据回填（连接中 + 有数据 + 模型尚未填充时）
    // 监听 history.size 变化,首次或断开重连后回填
    // x 值用「整数毫秒」((timeOffsetSec * 1000).toLong())：浮点秒的辗转相除 GCD 会被
    // 浮点误差磨成 0 触发 Vico "x-values are too precise" 异常；整数毫秒 GCD 永不归零。
    LaunchedEffect(history.size, appStatus) {
        if (appStatus == AppStatus.CONNECTED && history.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(
                        x = history.map { (it.timeOffsetSec * 1000).toLong().toDouble() },
                        y = history.map { it.heartRate.toDouble() }
                    )
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
        val visible = if (startIndex >= 0) history.subList(startIndex, history.size) else emptyList()
        // history 已由 ViewModel 的 appendPoint 写入本批 points,无需再追加
        if (visible.isNotEmpty()) {
            modelProducer.runTransaction {
                lineSeries {
                    series(
                        x = visible.map { (it.timeOffsetSec * 1000).toLong().toDouble() },
                        y = visible.map { it.heartRate.toDouble() }
                    )
                }
            }
        }
    }

    // 断开连接时清空图表数据
    LaunchedEffect(appStatus) {
        if (appStatus != AppStatus.CONNECTED) {
            modelProducer.runTransaction {
                // 空事务清空所有 series（无 series 调用 = 清空）
            }
        }
    }

    // 心率红主色,ECG 风格
    val lineColor = ComposeColor(0xFFE53935)

    // 纯白色圆角卡片容器包裹心率图表，与 SpeedCard 等卡片视觉风格保持一致
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = ComposeColor.White
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
                        // Y 轴显示整数 BPM
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
private fun DeviceItem(
    advertisement: Advertisement,
    isFavorite: Boolean,
    onDeviceClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val rssi = advertisement.rssi
    val strongColor = ComposeColor(0xFF00668B) // primary_light
    val mediumColor = ComposeColor(0xFFF59E0B)
    val weakColor = ComposeColor(0xFFB00020)   // red_error

    // 强信号用 Material Icons；中弱信号用自定义 1/2 格 drawable（Material Icons 无对应）
    val signalIconVector: androidx.compose.ui.graphics.vector.ImageVector?
    val signalIconDrawableRes: Int?
    val signalTint: ComposeColor
    val rssiColor: ComposeColor
    when {
        rssi > -65 -> {
            signalIconVector = Icons.Filled.SignalCellularAlt
            signalIconDrawableRes = null
            signalTint = strongColor
            rssiColor = strongColor
        }
        rssi > -80 -> {
            signalIconVector = null
            signalIconDrawableRes = R.drawable.ic_signal_cellular_alt_2_bar
            signalTint = mediumColor
            rssiColor = mediumColor
        }
        else -> {
            signalIconVector = null
            signalIconDrawableRes = R.drawable.ic_signal_cellular_alt_1_bar
            signalTint = weakColor
            rssiColor = weakColor
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onDeviceClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
            if (signalIconVector != null) {
                Icon(
                    imageVector = signalIconVector,
                    contentDescription = null,
                    tint = signalTint,
                    modifier = Modifier.size(20.dp)
                )
            } else if (signalIconDrawableRes != null) {
                Icon(
                    painter = painterResource(signalIconDrawableRes),
                    contentDescription = null,
                    tint = signalTint,
                    modifier = Modifier.size(20.dp)
                )
            }
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
                    contentDescription = "收藏",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
