package com.github.heartratemonitor_compose.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.feature.server.R
import com.github.heartratemonitor_compose.service.server.NsdDiscoverer
import com.github.heartratemonitor_compose.ui.util.SheetTopShape
import com.github.heartratemonitor_compose.ui.util.rememberExpandedSheetState
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButton
import com.github.heartratemonitor_compose.ui.widgets.IconContainer

/** 局域网传输页顶部提示卡片 */
@Composable
internal fun TipCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconContainer(icon = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_lan_transfer))
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.lan_transfer_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.lan_transfer_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** WebSocket 服务器状态卡片：未开启时引导跳转服务器设置 */
@Composable
internal fun WebSocketStatusCard(
    enabled: Boolean,
    onOpenServerSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconContainer(
                    icon = painterResource(
                        if (enabled) R.drawable.ic_websocket_server_enabled
                        else R.drawable.ic_websocket_server_disabled
                    )
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = if (enabled) stringResource(R.string.ws_enabled_status)
                    else stringResource(R.string.lan_ws_not_enabled),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
            }
            if (!enabled) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onOpenServerSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.lan_open_server_settings))
                }
            }
        }
    }
}

/** 扫描/连接状态卡片：点击切换扫描 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ScanStateCard(
    isConnected: Boolean,
    isScanning: Boolean,
    foundCount: Int,
    onClick: () -> Unit
) {
    val containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceBright
    val onContainerColor = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = onContainerColor,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Computer,
                contentDescription = null,
                tint = onContainerColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = when {
                    isConnected -> stringResource(R.string.lan_connected_status)
                    isScanning -> stringResource(R.string.lan_scanning)
                    foundCount == 0 -> stringResource(R.string.lan_no_devices)
                    else -> "$foundCount PC"
                },
                style = MaterialTheme.typography.titleMedium,
                color = onContainerColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isConnected && isScanning) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(40.dp)
                )
            } else if (!isConnected && foundCount > 0) {
                Text(
                    text = stringResource(R.string.lan_status_pushing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 发现的 PC 设备卡片：显示名称/地址与配对按钮 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DeviceCard(
    pc: NsdDiscoverer.DiscoveredPc,
    pairing: Boolean,
    onPair: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconContainer(icon = Icons.Default.Computer)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pc.name.ifBlank { stringResource(R.string.lan_device_unknown) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "${pc.host}:${pc.pairPort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            if (pairing) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Button(onClick = onPair) {
                    Text(stringResource(R.string.lan_pair_action))
                }
            }
        }
    }
}

/** 配对结果 BottomSheet（成功/失败共用） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PairResultDialog(
    title: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetTopShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ExpressiveButton(
                    label = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.ok),
                    onClick = onDismiss
                )
            }
        }
    }
}
