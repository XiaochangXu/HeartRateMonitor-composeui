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
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.feature.server.R
import com.github.heartratemonitor_compose.service.server.NsdDiscoverer
import com.github.heartratemonitor_compose.ui.util.SegmentBottomShape
import com.github.heartratemonitor_compose.ui.util.SegmentMiddleShape
import com.github.heartratemonitor_compose.ui.util.SegmentTopShape
import com.github.heartratemonitor_compose.ui.util.SheetTopShape
import com.github.heartratemonitor_compose.ui.util.rememberExpandedSheetState
import com.github.heartratemonitor_compose.ui.util.rememberSheetDismissHandler
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButton
import com.github.heartratemonitor_compose.ui.widgets.ExpressiveButtonStyle
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

/**
 * 已连接设备分组卡片（参考 DevicesScreen 的 ConnectedDeviceCard）。
 *
 * 上方：标题行（电脑 icon + "已连接设备" + check 图标）
 * 下方：设备名称（如 Windows）+ 局域网 IP + 断开连接按钮
 */
@Composable
internal fun ConnectedDeviceSection(
    deviceName: String,
    deviceIp: String,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 标题行
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SegmentTopShape,
            color = MaterialTheme.colorScheme.surfaceBright,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconContainer(
                    icon = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_computer),
                    containerSize = 36.dp,
                    iconSize = 20.dp,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.lan_connected_device),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    painter = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // 内容行：设备名 + IP + 断开按钮
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SegmentBottomShape,
            color = MaterialTheme.colorScheme.surfaceBright,
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
                        text = deviceName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = deviceIp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                ExpressiveButton(
                    label = stringResource(R.string.lan_disconnect),
                    onClick = onDisconnect,
                    style = ExpressiveButtonStyle.Danger
                )
            }
        }
    }
}

/**
 * 可用设备分组卡片（参考 DevicesScreen 的 available_header + 列表）。
 *
 * 上方：标题行（搜索 icon + "可用设备"）
 * 下方：空状态（"暂无可用设备，请点击右上角搜索"）或设备列表
 */
@Composable
internal fun AvailableDevicesSection(
    isScanning: Boolean,
    devices: List<NsdDiscoverer.DiscoveredPc>,
    pairingPc: NsdDiscoverer.DiscoveredPc?,
    onPair: (NsdDiscoverer.DiscoveredPc) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 标题行
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SegmentTopShape,
            color = MaterialTheme.colorScheme.surfaceBright,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconContainer(
                    icon = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_computer),
                    containerSize = 36.dp,
                    iconSize = 20.dp,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconTint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.lan_available_devices),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (devices.isEmpty()) {
            // 空状态
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = SegmentBottomShape,
                color = MaterialTheme.colorScheme.surfaceBright,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Text(
                    text = stringResource(R.string.lan_no_devices_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                )
            }
        } else {
            // 设备列表
            devices.forEachIndexed { index, pc ->
                val isLast = index == devices.lastIndex
                val shape = if (isLast) SegmentBottomShape else SegmentMiddleShape
                val pairing = pairingPc?.serviceName == pc.serviceName
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceBright,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    DeviceItem(
                        pc = pc,
                        pairing = pairing,
                        onPair = { onPair(pc) }
                    )
                }
            }
        }
    }
}

/** 发现的 PC 设备行：显示名称/地址与配对按钮 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceItem(
    pc: NsdDiscoverer.DiscoveredPc,
    pairing: Boolean,
    onPair: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconContainer(icon = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_computer))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pc.name.ifBlank { stringResource(R.string.lan_device_unknown) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${pc.host}:${pc.pairPort}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        if (pairing) {
            ContainedLoadingIndicator(
                modifier = Modifier.size(40.dp)
            )
        } else {
            ExpressiveButton(
                label = stringResource(R.string.lan_pair_action),
                onClick = onPair
            )
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
    val dismissWithAnimation = rememberSheetDismissHandler(sheetState, onDismiss)
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
                    onClick = dismissWithAnimation
                )
            }
        }
    }
}
