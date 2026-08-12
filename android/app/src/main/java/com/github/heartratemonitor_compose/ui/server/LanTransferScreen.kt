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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.github.heartratemonitor_compose.ui.widgets.IconContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LanTransferScreen(
    onNavigateBack: () -> Unit,
    settings: SettingsRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val appContainer = remember { context.applicationContext.appContainer }
    val nsdDiscoverer = remember { NsdDiscoverer(context) }
    val pairClient = remember { PairClient() }
    val ipAddressProvider: IpAddressProvider = remember { appContainer.ipAddressProvider }

    var isScanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<NsdDiscoverer.DiscoveredPc>>(emptyList()) }
    var pairingPc by remember { mutableStateOf<NsdDiscoverer.DiscoveredPc?>(null) }
    var pairResult by remember { mutableStateOf<PairClient.PairResponse?>(null) }
    var pairError by remember { mutableStateOf<String?>(null) }
    var scanJob: Job? by remember { mutableStateOf(null) }
    var pairJob: Job? by remember { mutableStateOf(null) }

    val wsClientCount by appContainer.webSocketClientCount.collectAsState()
    val isConnected = wsClientCount > 0

    val wsEnabled = remember { mutableStateOf(settings.getBoolean(PrefsKeys.WEBSOCKET_SERVER_ENABLED, false)) }
    LaunchedEffect(Unit) {
        settings.observeBoolean(PrefsKeys.WEBSOCKET_SERVER_ENABLED, false).collectLatest {
            wsEnabled.value = it
        }
    }

    fun startScan() {
        if (isScanning || isConnected) return
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

    LaunchedEffect(isConnected) {
        if (isConnected) {
            stopScan()
        } else {
            stopScan()
            devices = emptyList()
        }
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

    fun disconnect() {
        pairJob?.cancel()
        pairJob = null
        pairingPc = null
        pairResult = null
        pairError = null
        stopScan()
        devices = emptyList()
       
        appContainer.disconnectWebSocketClients?.invoke()
    }

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
                    actions = {
                    if (isConnected) {
                        IconButton(onClick = { disconnect() }) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.LinkOff,
                                        contentDescription = stringResource(R.string.lan_disconnect),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = { if (isScanning) stopScan() else startScan() }) {
                        if (isScanning) {
                            // 扫描中：顶栏搜索按钮切换为 ContainedLoadingIndicator，再点可停止
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
                                        contentDescription = stringResource(R.string.lan_scan),
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding()))

                TipCard()

            
            WebSocketStatusCard(
                enabled = wsEnabled.value,
                onOpenServerSettings = { onNavigateBack() }
            )

             ScanStateCard(
                isConnected = isConnected,
                isScanning = isScanning,
                foundCount = devices.size,
                onClick = { if (isScanning) stopScan() else startScan() }
            )

           if (!isConnected) {
                devices.forEach { pc ->
                    DeviceCard(
                        pc = pc,
                        pairing = pairingPc?.serviceName == pc.serviceName,
                        onPair = { startPairing(pc) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
            StatusBarScrim()
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
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconContainer(icon = painterResource(R.drawable.ic_lan_transfer))
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
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
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


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanStateCard(
    isConnected: Boolean,
    isScanning: Boolean,
    foundCount: Int,
    onClick: () -> Unit
) {
    val containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceCard(
    pc: NsdDiscoverer.DiscoveredPc,
    pairing: Boolean,
    onPair: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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
