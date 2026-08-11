package com.github.heartratemonitor_compose.ui.main

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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
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
import com.juul.kable.Advertisement
import kotlinx.coroutines.flow.Flow

/**
 *
 * 内部实时图表用 Vico [CartesianChartHost]（阶段 4 已从 AndroidView{LineChart} 迁移）。
 *
 * @param onToggleFloatingWindow 切换悬浮窗（顶部按钮，原历史入口位置）
 * @param onEnterFullScreen 进入全屏心率模式
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // 三个设置开关均直接从 SettingsRepository 的 StateFlow 订阅，
    // 避免经 MainViewModel 中转产生额外异步延迟。
    val isHistoryEnabled by settings.observeBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false)
        .collectWhenActive(isActive, initial = false)
    val isSpeedEnabled by settings.observeBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, false)
        .collectWhenActive(isActive, initial = false)
    val isAnimationEnabled by settings.observeBoolean(PrefsKeys.HEARTBEAT_ANIMATION_ENABLED, true)
        .collectWhenActive(isActive, initial = true)
    // 悬浮窗开关状态（顶部按钮图标切换）
    val floatingWindowEnabled by settings.observeBoolean(PrefsKeys.FLOATING_WINDOW_ENABLED, false)
        .collectWhenActive(isActive, initial = false)

    val isConnected = appStatus == AppStatus.CONNECTED

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        text = context.getString(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {
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
            isAnimationEnabled = isAnimationEnabled,
            sessionMaxHr = sessionMaxHr,
            sessionMinHr = sessionMinHr,
            connectedDeviceName = connectedDevice?.name
        )

        HomeContent(
            modifier = Modifier
                .fillMaxSize()
                // 仅应用顶部 padding（TopAppBar 高度），底部不应用 padding 让内容延伸到屏幕底部
                .padding(top = padding.calculateTopPadding()),
            uiState = uiState,
            onDisconnect = remember(viewModel) { { viewModel.disconnectDevice() } },
            onNavigateToDevices = onNavigateToDevices,
            onEnterFullScreen = onEnterFullScreen
        )
    }
}

@Composable
private fun HomeContent(
    modifier: Modifier,
    uiState: HomeUiState,
    onDisconnect: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onEnterFullScreen: () -> Unit
) {
    val heartRate = uiState.heartRate
    val speed = uiState.speed
    val appStatus = uiState.appStatus
    val statusMessage = uiState.statusMessage
    val chartDataSnapshot = uiState.chartDataSnapshot
    val isConnected = uiState.isConnected
    val isHistoryEnabled = uiState.isHistoryEnabled
    val isSpeedEnabled = uiState.isSpeedEnabled
    val isAnimationEnabled = uiState.isAnimationEnabled
    val sessionMaxHr = uiState.sessionMaxHr
    val sessionMinHr = uiState.sessionMinHr
    val connectedDeviceName = uiState.connectedDeviceName

    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 16.dp + 64.dp + 8.dp + navBarInset
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
                        chartDataSnapshot = chartDataSnapshot,
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
                    Icon(
                        imageVector = Icons.Filled.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
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

        if (isConnected) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
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
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = onDisconnect
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
 * 设备列表项：替代原 list_item_device.xml + DeviceAdapter。
 */
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
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
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
