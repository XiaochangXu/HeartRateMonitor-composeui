package com.github.heartratemonitor_compose.ui.main

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.github.heartratemonitor_compose.util.settingsRepository

/**
 * 设备搜索二级界面。
 *
 * 从首页「可用设备」入口进入，包含：
 * - TopAppBar（标题 + 返回箭头 + 搜索按钮）
 * - 首次搜索提示弹窗
 * - 设备列表（复用 [DeviceItem]），按收藏优先 + 信号强度排序
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    // 首次搜索提示弹窗（只弹出一次）
    var showSearchTipDialog by remember { mutableStateOf(false) }

    val sortedScanResults = remember(scanResults, favoriteDeviceId) {
        scanResults.sortedWith(
            compareByDescending<com.juul.kable.Advertisement> { it.identifier == favoriteDeviceId }
                .thenByDescending { it.rssi }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.available_devices),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Normal
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (appStatus == AppStatus.CONNECTED || appStatus == AppStatus.CONNECTING) {
                                Toast.makeText(context, R.string.please_disconnect_first, Toast.LENGTH_SHORT).show()
                            } else if (!settings.getBoolean(PrefsKeys.SEARCH_TIP_SHOWN, false)) {
                                showSearchTipDialog = true
                            } else {
                                viewModel.startScan()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.cd_search_bluetooth),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 修复：连接后 scanResults 被清空，列表为空。增加此卡片让用户始终能看到当前已连接的设备。
            connectedDevice?.let { device ->
                item {
                    ConnectedDeviceCard(
                        device = device,
                        onDisconnect = { viewModel.disconnectDevice() }
                    )
                }
            }

            item {
                AvailableDevicesList(
                    devices = sortedScanResults,
                    favoriteDeviceId = favoriteDeviceId,
                    isConnecting = { id -> id == connectingDeviceId && appStatus == AppStatus.CONNECTING },
                    onDeviceClick = { viewModel.connectToDevice(it) },
                    onFavoriteClick = { viewModel.toggleFavoriteDevice(it) }
                )
            }
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

/**
 * 已连接设备卡片组。
 *
 * 分段式卡片组：用 Column + 2dp 间隔取代单 Card + Divider。
 */
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
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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

/**
 * 可用设备列表卡片组。
 *
 * 分段式卡片组：用 Column + 2dp 间隔取代单 Card + Divider。
 */
@Composable
private fun AvailableDevicesList(
    devices: List<com.juul.kable.Advertisement>,
    favoriteDeviceId: String?,
    isConnecting: (String) -> Boolean,
    onDeviceClick: (String) -> Unit,
    onFavoriteClick: (com.juul.kable.Advertisement) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Text(
                text = stringResource(R.string.available_devices),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        if (devices.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
        } else {
            devices.forEachIndexed { index, advertisement ->
                val isLast = index == devices.lastIndex
                val shape = if (isLast) {
                    RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                } else {
                    RoundedCornerShape(0.dp)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    DeviceItem(
                        advertisement = advertisement,
                        isFavorite = advertisement.identifier == favoriteDeviceId,
                        isConnecting = isConnecting(advertisement.identifier),
                        onDeviceClick = { onDeviceClick(advertisement.identifier) },
                        onFavoriteClick = { onFavoriteClick(advertisement) }
                    )
                }
            }
        }
    }
}
