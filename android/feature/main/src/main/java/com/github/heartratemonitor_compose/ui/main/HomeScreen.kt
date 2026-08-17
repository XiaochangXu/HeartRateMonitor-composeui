package com.github.heartratemonitor_compose.ui.main

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.main.R

import com.github.heartratemonitor_compose.ui.util.collectWhenActive
import com.github.heartratemonitor_compose.ui.widgets.IconContainer

/**
 * 首页：
 * - Speed Dial FAB 组见 HomeSpeedDialFab.kt
 * - 统计卡片区见 SessionStatsRow.kt
 * - 设备列表项见 DeviceItem.kt
 *
 * 内部实时图表用 Vico [CartesianChartHost]。
 *
 * 状态下行只收集 [MainViewModel.uiState]（MVI，Phase 5）；
 * 设置派生字段由 VM 投影，首页不再直读 SettingsRepository。
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
    // 不在前台 Tab 时暂停高频状态订阅，避免二级页面转场期间后台 Home 页持续重组抢主线程
    val uiState by viewModel.uiState.collectWhenActive(isActive)

    val appStatus = uiState.appStatus
    val speed = uiState.speed
    val isHistoryEnabled = uiState.isHistoryEnabled
    val isSpeedEnabled = uiState.isSpeedEnabled
    val floatingWindowEnabled = uiState.floatingWindowEnabled

    val isConnected = appStatus == AppStatus.CONNECTED

    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val onDisconnect = remember(viewModel) { { viewModel.dispatch(MainIntent.DisconnectDevice) } }

    Scaffold(
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
                title = {
                    Text(
                        text = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.nav_home),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                actions = {                  
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
                            else MaterialTheme.colorScheme.surfaceBright
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(
                                        if (floatingWindowEnabled) com.github.heartratemonitor_compose.service.R.drawable.ic_floating_window_on
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
        Box(modifier = Modifier.fillMaxSize()) {
            HomeContent(
                modifier = Modifier
                    .fillMaxSize()
                    // 仅应用顶部 padding（TopAppBar 高度），底部不应用 padding 让内容延伸到屏幕底部
                    .padding(top = padding.calculateTopPadding()),
                uiState = uiState,
                onNavigateToDevices = onNavigateToDevices,
                onRingMaxChange = remember(viewModel) {
                    { v -> viewModel.dispatch(MainIntent.SetHeartRateRingMax(v)) }
                }
            )

            // Speed Dial FAB：仅在已连接时显示
            HomeSpeedDialFab(
                isConnected = isConnected,
                navBarInset = navBarInset,
                onEnterFullScreen = onEnterFullScreen,
                onDisconnect = onDisconnect,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeContent(
    modifier: Modifier,
    uiState: MainUiState,
    onNavigateToDevices: () -> Unit,
    onRingMaxChange: (Int) -> Unit
) {
    val heartRate = uiState.heartRate
    val appStatus = uiState.appStatus
    val statusMessage = uiState.statusMessage
    val chartDataSnapshot = uiState.chartDataSnapshot
    val isConnected = uiState.appStatus == AppStatus.CONNECTED
    val isHistoryEnabled = uiState.isHistoryEnabled
    val ringMaxHr = uiState.ringMaxHr
    val sessionMaxHr = uiState.sessionMaxHr
    val sessionMinHr = uiState.sessionMinHr
    val connectedDeviceName = uiState.connectedDevice?.name

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
            
            HeartRateCard(
                modifier = Modifier.fillMaxWidth(),
                heartRate = heartRate,
                appStatus = appStatus,
                ringMaxHr = ringMaxHr,
                onRingMaxChange = onRingMaxChange
            )
        }

        item {
        
            SessionStatsRow(
                sessionMaxHr = sessionMaxHr,
                sessionMinHr = sessionMinHr
            )
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
                color = MaterialTheme.colorScheme.surfaceBright,
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
