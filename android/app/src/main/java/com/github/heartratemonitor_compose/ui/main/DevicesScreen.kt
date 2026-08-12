package com.github.heartratemonitor_compose.ui.main

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.service.BleService
import com.github.heartratemonitor_compose.data.di.settingsRepository
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevicesScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { context.settingsRepository }

    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val appStatus by viewModel.appStatus.collectAsStateWithLifecycle()
    val connectingDeviceId by viewModel.connectingDeviceId.collectAsStateWithLifecycle()
    val favoriteDeviceId by viewModel.favoriteDeviceId.collectAsStateWithLifecycle()
    val connectedDevice by viewModel.connectedDevice.collectAsStateWithLifecycle()

    var showSearchTipDialog by remember { mutableStateOf(false) }

    val sortedScanResults = remember(scanResults, favoriteDeviceId) {
        scanResults.sortedWith(
            compareByDescending<com.juul.kable.Advertisement> { it.identifier == favoriteDeviceId }
                .thenByDescending { it.rssi }
        )
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                        stringResource(R.string.available_devices),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
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
                    IconButton(
                        onClick = {
                            if (appStatus == AppStatus.SCANNING) {
                                // 再次点击：停止扫描，收起 ContainedLoadingIndicator，恢复搜索按钮
                                viewModel.stopScan()
                            } else if (appStatus == AppStatus.CONNECTED || appStatus == AppStatus.CONNECTING) {
                                Toast.makeText(context, R.string.please_disconnect_first, Toast.LENGTH_SHORT).show()
                            } else if (!settings.getBoolean(PrefsKeys.SEARCH_TIP_SHOWN, false)) {
                                showSearchTipDialog = true
                            } else {
                                viewModel.startScan()
                            }
                        }
                    ) {
                        if (appStatus == AppStatus.SCANNING) {
                            // 扫描中：Contained（填充式）加载指示器，40dp 与普通按钮圆同尺寸，
                            // 形成「扫描按钮 → 加载圆」的形态切换（M3 Expressive 模式）
                            ContainedLoadingIndicator(
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = stringResource(R.string.cd_search_bluetooth),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        val isConnectingDevice = remember(appStatus, connectingDeviceId) {
            { id: String -> id == connectingDeviceId && appStatus == AppStatus.CONNECTING }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                connectedDevice?.let { device ->
                item(key = "connected") {
                    val onDisconnect = remember(viewModel) { { viewModel.disconnectDevice() } }
                    ConnectedDeviceCard(
                        device = device,
                        onDisconnect = onDisconnect
                    )
                }
            item(key = "gap_connected_available") { Spacer(Modifier.height(14.dp)) }
            }

            item(key = "available_header") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.available_devices),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (sortedScanResults.isEmpty()) {
                item(key = "available_empty") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        Text(
                            text = stringResource(R.string.no_available_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp)
                        )
                    }
                }
            } else {
                items(
                    items = sortedScanResults,
                    key = { it.identifier }
                ) { advertisement ->
                    val isLast = advertisement == sortedScanResults.last()
                    val shape = if (isLast) {
                        RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                    } else {
                        RoundedCornerShape(0.dp)
                    }
                    val isFavorite = advertisement.identifier == favoriteDeviceId
                    val isConnecting = isConnectingDevice(advertisement.identifier)
                    val onDeviceClick = remember(advertisement.identifier) {
                        { viewModel.connectToDevice(advertisement.identifier) }
                    }
                    val onFavoriteClick = remember(advertisement.identifier) {
                        { viewModel.toggleFavoriteDevice(advertisement) }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = shape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ) {
                        DeviceItem(
                            advertisement = advertisement,
                            isFavorite = isFavorite,
                            isConnecting = isConnecting,
                            onDeviceClick = onDeviceClick,
                            onFavoriteClick = onFavoriteClick
                        )
                    }
                }
            }
            }
            StatusBarScrim()
        }
    }

    if (showSearchTipDialog) {
        AlertDialog(
            onDismissRequest = { showSearchTipDialog = false },
            title = { Text(stringResource(R.string.search_tip_title)) },
            text = { Text(stringResource(R.string.search_tip_message)) },
            confirmButton = {
                TextButton(onClick = {
                    settings.setBoolean(PrefsKeys.SEARCH_TIP_SHOWN, true)
                    showSearchTipDialog = false
                    viewModel.startScan()
                }) { Text(stringResource(R.string.got_it)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    settings.setBoolean(PrefsKeys.SEARCH_TIP_SHOWN, true)
                    showSearchTipDialog = false
                }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun ConnectedDeviceCard(
    device: BleService.ConnectedDevice,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.connected_device),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name.ifBlank { stringResource(R.string.unknown_device) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = device.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(
                    onClick = onDisconnect,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.disconnect),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}


