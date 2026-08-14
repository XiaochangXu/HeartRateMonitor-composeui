package com.github.heartratemonitor_compose.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.di.settingsRepository
import com.github.heartratemonitor_compose.ui.theme.SignalMediumColor
import com.github.heartratemonitor_compose.ui.theme.SignalStrongColor
import com.github.heartratemonitor_compose.ui.theme.SignalWeakColor
import com.github.heartratemonitor_compose.ui.widgets.IconContainer
import com.juul.kable.Advertisement
import kotlinx.coroutines.flow.Flow

/**
 *
 * 内部实时图表用 Vico [CartesianChartHost]（阶段 4 已从 AndroidView{LineChart} 迁移）。
 *
 * @param onToggleFloatingWindow 切换悬浮窗（顶部按钮，原历史入口位置）
 * @param onEnterFullScreen 进入全屏心率模式
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onToggleFloatingWindow: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onEnterFullScreen: () -> Unit,
    isActive: Boolean = true
) {
    val context = LocalContext.current
    val settings = remember { context.settingsRepository }

    // 不在前台 Tab 时暂停高频状态订阅，避免二级页面转场期间后台 Home 页持续重组抢主线程
    val heartRate by viewModel.heartRate.collectWhenActive(isActive, initial = 0)
    val speed by viewModel.speed.collectWhenActive(isActive, initial = 0f)
    val appStatus by viewModel.appStatus.collectWhenActive(isActive, initial = AppStatus.DISCONNECTED)
    val statusMessage by viewModel.statusMessage.collectWhenActive(isActive, initial = "")
    val sessionMaxHr by viewModel.sessionMaxHr.collectWhenActive(isActive, initial = 0)
    val sessionMinHr by viewModel.sessionMinHr.collectWhenActive(isActive, initial = 0)
    val connectedDevice by viewModel.connectedDevice.collectWhenActive(isActive, initial = null)
    val chartDataSnapshot by viewModel.chartDataSnapshot.collectWhenActive(isActive, initial = null)

    // 设置开关均直接从 SettingsRepository 的 StateFlow 订阅，
    // 避免经 MainViewModel 中转产生额外异步延迟。
    val isHistoryEnabled by settings.observeBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false)
        .collectWhenActive(isActive, initial = false)
    val isSpeedEnabled by settings.observeBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, false)
        .collectWhenActive(isActive, initial = false)
    // 首页心率卡片半圆环满量程（默认 180 bpm）
    val ringMaxHr by settings.observeInt(PrefsKeys.HEART_RATE_RING_MAX, 180)
        .collectWhenActive(isActive, initial = 180)
    // 悬浮窗开关状态（顶部按钮图标切换）
    val floatingWindowEnabled by settings.observeBoolean(PrefsKeys.FLOATING_WINDOW_ENABLED, false)
        .collectWhenActive(isActive, initial = false)

    val isConnected = appStatus == AppStatus.CONNECTED

    // Speed Dial FAB 展开状态
    var speedDialExpanded by rememberSaveable { mutableStateOf(false) }
    // 断开连接时自动收起
    LaunchedEffect(isConnected) {
        if (!isConnected) speedDialExpanded = false
    }
    // 展开/收起时的挤压回弹动画
    val fabSqueeze = remember { Animatable(1f) }
    LaunchedEffect(speedDialExpanded) {
        fabSqueeze.snapTo(0.85f)
        fabSqueeze.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.35f, stiffness = 250f)
        )
    }
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val onDisconnect = remember(viewModel) { { viewModel.disconnectDevice() } }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = stringResource(R.string.nav_home),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
                    // 速度胶囊：放在悬浮窗开关按钮左侧；不活跃时显示占位"--"
                    SpeedPill(
                        speed = speed,
                        isActive = isSpeedEnabled && isConnected
                    )
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onToggleFloatingWindow) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = if (floatingWindowEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(
                                        if (floatingWindowEnabled) R.drawable.ic_floating_window_on
                                        else R.drawable.ic_floating_window_off
                                    ),
                                    contentDescription = stringResource(R.string.cd_toggle_floating_window),
                                    tint = if (floatingWindowEnabled) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        val uiState = HomeUiState(
            heartRate = heartRate,
            speed = speed,
            appStatus = appStatus,
            statusMessage = statusMessage,
            chartDataSnapshot = chartDataSnapshot,
            isConnected = isConnected,
            isHistoryEnabled = isHistoryEnabled,
            isSpeedEnabled = isSpeedEnabled,
            ringMaxHr = ringMaxHr,
            sessionMaxHr = sessionMaxHr,
            sessionMinHr = sessionMinHr,
            connectedDeviceName = connectedDevice?.name
        )

        Box(modifier = Modifier.fillMaxSize()) {
            HomeContent(
                modifier = Modifier
                    .fillMaxSize()
                    // 仅应用顶部 padding（TopAppBar 高度），底部不应用 padding 让内容延伸到屏幕底部
                    .padding(top = padding.calculateTopPadding()),
                uiState = uiState,
                onNavigateToDevices = onNavigateToDevices,
                onRingMaxChange = remember(settings) { { v -> settings.setInt(PrefsKeys.HEART_RATE_RING_MAX, v) } }
            )

            // Speed Dial FAB：仅在已连接时显示
            AnimatedVisibility(
                visible = isConnected,
                enter = scaleIn(
                    initialScale = 0.6f,
                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f)
                ) + fadeIn(),
                exit = scaleOut(
                    targetScale = 0.6f,
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
                ) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                FloatingActionButtonMenu(
                    modifier = Modifier
                        .padding(
                            end = 8.dp,
                            bottom = navBarInset + 12.dp + 64.dp + 8.dp + 16.dp
                        )
                        .graphicsLayer {
                            val s = fabSqueeze.value
                            scaleX = s
                            scaleY = s
                        },
                    expanded = speedDialExpanded,
                    button = {
                        ToggleFloatingActionButton(
                            checked = speedDialExpanded,
                            onCheckedChange = { speedDialExpanded = !speedDialExpanded }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.cd_speed_dial),
                                modifier = Modifier.graphicsLayer {
                                    rotationZ = checkedProgress * 45f
                                }
                            )
                        }
                    }
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            onEnterFullScreen()
                            speedDialExpanded = false
                        },
                        icon = { Icon(Icons.Filled.Fullscreen, contentDescription = null) },
                        text = { Text(stringResource(R.string.enter_fullscreen)) }
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            onDisconnect()
                            speedDialExpanded = false
                        },
                        icon = { Icon(Icons.Filled.BluetoothDisabled, contentDescription = null) },
                        text = { Text(stringResource(R.string.disconnect)) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeContent(
    modifier: Modifier,
    uiState: HomeUiState,
    onNavigateToDevices: () -> Unit,
    onRingMaxChange: (Int) -> Unit
) {
    val heartRate = uiState.heartRate
    val appStatus = uiState.appStatus
    val statusMessage = uiState.statusMessage
    val chartDataSnapshot = uiState.chartDataSnapshot
    val isConnected = uiState.isConnected
    val isHistoryEnabled = uiState.isHistoryEnabled
    val ringMaxHr = uiState.ringMaxHr
    val sessionMaxHr = uiState.sessionMaxHr
    val sessionMinHr = uiState.sessionMinHr
    val connectedDeviceName = uiState.connectedDeviceName

    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 16.dp + 64.dp + 8.dp + navBarInset
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // 速度胶囊已移至顶栏，心率卡片独占整行
            HeartRateCard(
                modifier = Modifier.fillMaxWidth(),
                heartRate = heartRate,
                appStatus = appStatus,
                ringMaxHr = ringMaxHr,
                onRingMaxChange = onRingMaxChange
            )
        }

        item {
            // 最大/最低心率双卡（仿 legado 首页累计阅读/阅读时长双卡布局）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeartRateStatCard(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_heart_rate_max,
                    title = stringResource(R.string.max_heart_rate),
                    value = if (sessionMaxHr > 0) "$sessionMaxHr" else "--",
                    unit = stringResource(R.string.bpm_unit)
                )
                HeartRateStatCard(
                    modifier = Modifier.weight(1f),
                    icon = R.drawable.ic_heart_rate_min,
                    title = stringResource(R.string.min_heart_rate),
                    value = if (sessionMinHr > 0) "$sessionMinHr" else "--",
                    unit = stringResource(R.string.bpm_unit)
                )
            }
        }

        item {
            when {
                isConnected && isHistoryEnabled -> {
                    // 已连接且历史已开启：尚无心率数据时先显示加载指示器，首个数据到达后切换为图表
                    val snapshot = chartDataSnapshot
                    if (snapshot == null || snapshot.xValues.isEmpty()) {
                        ChartLoadingIndicator()
                    } else {
                        RealtimeChart(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            chartDataSnapshot = chartDataSnapshot,
                            appStatus = appStatus
                        )
                    }
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
            val isConnecting = appStatus == AppStatus.CONNECTING
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                onClick = onNavigateToDevices
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconContainer(
                        icon = Icons.Filled.Bluetooth,
                        containerSize = 36.dp,
                        iconSize = 20.dp,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = when {
                            isConnecting -> statusMessage
                            isConnected && !connectedDeviceName.isNullOrEmpty() -> "$connectedDeviceText $connectedDeviceName"
                            else -> availableDevicesText
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isConnecting) {
                        ContainedLoadingIndicator(
                            modifier = Modifier.size(40.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

    }
}

/**
 * 设备列表项：替代原 list_item_device.xml + DeviceAdapter。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeviceItem(
    advertisement: Advertisement,
    isFavorite: Boolean,
    isConnecting: Boolean,
    onDeviceClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val rssi = advertisement.rssi
    val strongColor = SignalStrongColor // primary_light
    val mediumColor = SignalMediumColor
    val weakColor = SignalWeakColor   // red_error

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
        if (isConnecting) {
            ContainedLoadingIndicator(
                modifier = Modifier.size(40.dp)
            )
        } else {
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
        }
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
 * 仅在 [isActive] 为 true 时收集 Flow，暂停期间保留 [initial] 值。
 *
 * 用于 Home 页在切到二级页面时停止订阅心率、图表等高频更新，
 * 避免后台页面持续重组导致转场动画掉帧。
 */
@Composable
private fun <T> Flow<T>.collectWhenActive(
    isActive: Boolean,
    initial: T
): State<T> {
    val state = remember { mutableStateOf(initial) }
    LaunchedEffect(isActive) {
        if (isActive) this@collectWhenActive.collect { state.value = it }
    }
    return state
}
