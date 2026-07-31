package com.github.heartratemonitor_compose.ui.server

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.network.IpAddressProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.service.server.NsdDiscoverer
import com.github.heartratemonitor_compose.service.server.PairClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LanTransferScreen(
    onNavigateBack: () -> Unit,
    settings: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val nsdDiscoverer = remember { NsdDiscoverer(context) }
    val pairClient = remember { PairClient() }
    val ipAddressProvider: IpAddressProvider = remember { context.applicationContext.appContainer.ipAddressProvider }

    var isScanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<NsdDiscoverer.DiscoveredPc>>(emptyList()) }
    var pairingPc by remember { mutableStateOf<NsdDiscoverer.DiscoveredPc?>(null) }
    var pairResult by remember { mutableStateOf<PairClient.PairResponse?>(null) }
    var pairError by remember { mutableStateOf<String?>(null) }
    var scanJob: Job? by remember { mutableStateOf(null) }
    var pairJob: Job? by remember { mutableStateOf(null) }

    val wsEnabled = remember { mutableStateOf(settings.getBoolean(PrefsKeys.WEBSOCKET_SERVER_ENABLED, false)) }
    LaunchedEffect(Unit) {
        settings.observeBoolean(PrefsKeys.WEBSOCKET_SERVER_ENABLED, false).collectLatest {
            wsEnabled.value = it
        }
    }

    fun startScan() {
        if (isScanning) return
        isScanning = true
        devices = emptyList()
        scanJob = scope.launch {
            try {
                nsdDiscoverer.discover().collectLatest { list ->
                    devices = list
                }
            } finally {
                isScanning = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        isScanning = false
    }

    fun startPairing(pc: NsdDiscoverer.DiscoveredPc) {
        if (pairingPc != null) return
        if (!wsEnabled.value) {
            pairError = context.getString(R.string.lan_ws_not_enabled)
            return
        }
        pairingPc = pc
        pairResult = null
        pairError = null

        val wsPort = settings.getInt(PrefsKeys.WEBSOCKET_SERVER_PORT, 8001)
        val wsToken = settings.getString(PrefsKeys.SERVER_ACCESS_TOKEN, "")
        val wsIp = ipAddressProvider.getLocalIpAddress() ?: ""
        val deviceName = buildString {
            append(context.getString(R.string.app_name))
            append("-")
            append(Build.MODEL)
        }
        val deviceId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (_: Exception) { "" }

        pairJob = scope.launch {
            val resp = withTimeoutOrNull(35_000L) {
                pairClient.request(
                    pcHost = pc.host,
                    pcPairPort = pc.pairPort,
                    request = PairClient.PairRequest(
                        deviceName = deviceName,
                        deviceId = deviceId,
                        wsIp = wsIp,
                        wsPort = wsPort,
                        wsToken = wsToken
                    )
                )
            } ?: PairClient.PairResponse.Failed(context.getString(R.string.lan_pair_timeout))

            pairResult = resp
            pairingPc = null

            when (resp) {
                is PairClient.PairResponse.Approved -> {
                    settings.setString(PrefsKeys.LAN_LAST_PAIRED_PC_NAME, pc.name)
                }
                is PairClient.PairResponse.Rejected -> {
                    pairError = context.getString(R.string.lan_pair_rejected)
                }
                is PairClient.PairResponse.Failed -> {
                    pairError = context.getString(R.string.lan_pair_failed, resp.message)
                }
            }
        }
    }

    fun dismissResult() {
        pairResult = null
        pairError = null
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lan_transfer_title), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(R.string.cd_back))
                            }
                        }
                    }
                },
                // 右上角搜索按钮：与「可用设备」页面完全一致（Surface 圆形 + onSurface tint）
                actions = {
                    IconButton(onClick = { startScan() }) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(R.string.lan_scan),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TipCard()

            // WebSocket 服务器状态卡（icon 颜色已统一为 primaryContainer，与其他页面一致）
            WebSocketStatusCard(
                enabled = wsEnabled.value,
                onOpenServerSettings = { onNavigateBack() }
            )

            // 搜索设备卡：参照首页「搜索蓝牙设备」的卡片设计（28dp 圆角 + 行内布局 + 末尾进度条/箭头）
            ScanStateCard(
                isScanning = isScanning,
                foundCount = devices.size,
                onClick = { if (isScanning) stopScan() else startScan() }
            )

            // 设备列表（未发现设备时不重复显示提示，ScanStateCard 已有文案）
            devices.forEach { pc ->
                DeviceCard(
                    pc = pc,
                    pairing = pairingPc?.serviceName == pc.serviceName,
                    onPair = { startPairing(pc) }
                )
            }

            val lastPcName = remember { settings.getString(PrefsKeys.LAN_LAST_PAIRED_PC_NAME, "") }
            if (lastPcName.isNotEmpty() && pairResult == null) {
                LastPairedCard(name = lastPcName)
            }

            Spacer(Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }

    val result = pairResult
    if (result is PairClient.PairResponse.Approved) {
        PairResultDialog(
            title = stringResource(R.string.lan_pair_approved),
            onDismiss = { dismissResult() }
        )
    }
    val error = pairError
    if (error != null) {
        PairResultDialog(
            title = error,
            onDismiss = { dismissResult() }
        )
    }
}

@Composable
private fun TipCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_lan_transfer),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
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

@Composable
private fun WebSocketStatusCard(
    enabled: Boolean,
    onOpenServerSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // icon 颜色统一为 primaryContainer，与其他页面一致（不再用 errorContainer 黄色）
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(
                                if (enabled) R.drawable.ic_websocket_server_enabled
                                else R.drawable.ic_websocket_server_disabled
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
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
 * 搜索设备卡：参照首页「搜索蓝牙设备」入口卡片设计。
 * - 28dp 圆角 surfaceContainer
 * - 行内：Computer 图标 + 状态文本(weight 1f) + 末尾进度条/数字
 * - 整卡可点击切换扫描/停止
 */
@Composable
private fun ScanStateCard(
    isScanning: Boolean,
    foundCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = when {
                    isScanning -> stringResource(R.string.lan_scanning)
                    foundCount == 0 -> stringResource(R.string.lan_no_devices)
                    else -> "$foundCount PC"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            } else if (foundCount > 0) {
                Text(
                    text = stringResource(R.string.lan_status_pushing),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    pc: NsdDiscoverer.DiscoveredPc,
    pairing: Boolean,
    onPair: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Computer,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
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
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(onClick = onPair) {
                    Text(stringResource(R.string.lan_pair_action))
                }
            }
        }
    }
}

@Composable
private fun LastPairedCard(name: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_signal_wifi),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.lan_connected_format, name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PairResultDialog(
    title: String,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}
