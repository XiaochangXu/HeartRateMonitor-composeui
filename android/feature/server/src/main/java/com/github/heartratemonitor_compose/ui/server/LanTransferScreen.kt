package com.github.heartratemonitor_compose.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.heartratemonitor_compose.feature.server.R
import com.github.heartratemonitor_compose.service.server.PairClient
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim

/**
 * 局域网传输页：
 * 状态与流程编排（扫描/配对/断开）归 [LanTransferViewModel]，本文件仅渲染 + 事件回调；
 * 子组件见 LanTransferSections.kt（TipCard / WebSocketStatusCard / ScanStateCard / DeviceCard / PairResultDialog）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LanTransferScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: LanTransferViewModel = hiltViewModel()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isScanning = uiState.isScanning
    val devices = uiState.devices
    val pairingPc = uiState.pairingPc
    val pairResult = uiState.pairResult
    val pairError = uiState.pairError
    val wsEnabled = uiState.wsEnabled
    val isConnected = uiState.isConnected

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                title = { Text(stringResource(R.string.lan_transfer_title), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceBright
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Default.ArrowBack, stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_back))
                            }
                        }
                    }
                },
                    actions = {
                    if (isConnected) {
                        IconButton(onClick = { viewModel.dispatch(LanTransferIntent.Disconnect) }) {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceBright
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_link_off),
                                        contentDescription = stringResource(R.string.lan_disconnect),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = {
                        if (isScanning) viewModel.dispatch(LanTransferIntent.StopScan)
                        else viewModel.dispatch(LanTransferIntent.StartScan)
                    }) {
                        if (isScanning) {
                            // 扫描中：顶栏搜索按钮切换为 ContainedLoadingIndicator，再点可停止
                            ContainedLoadingIndicator(
                                modifier = Modifier.size(40.dp)
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceBright
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
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 16.dp)
                    .padding(top = padding.calculateTopPadding() + 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TipCard()

            
            WebSocketStatusCard(
                enabled = wsEnabled,
                onOpenServerSettings = { onNavigateBack() }
            )

             ScanStateCard(
                isConnected = isConnected,
                isScanning = isScanning,
                foundCount = devices.size,
                onClick = {
                    if (isScanning) viewModel.dispatch(LanTransferIntent.StopScan)
                    else viewModel.dispatch(LanTransferIntent.StartScan)
                }
            )

           if (!isConnected) {
                devices.forEach { pc ->
                    DeviceCard(
                        pc = pc,
                        pairing = pairingPc?.serviceName == pc.serviceName,
                        onPair = { viewModel.dispatch(LanTransferIntent.StartPairing(pc)) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            }
            StatusBarScrim()
        }
    }

    val result = pairResult
    if (result is PairClient.PairResponse.Approved) {
        PairResultDialog(
            title = stringResource(R.string.lan_pair_approved),
            onDismiss = { viewModel.dispatch(LanTransferIntent.ConsumePairResult) }
        )
    }
    val error = pairError
    if (error != null) {
        PairResultDialog(
            title = error,
            onDismiss = { viewModel.dispatch(LanTransferIntent.ConsumePairResult) }
        )
    }
}
