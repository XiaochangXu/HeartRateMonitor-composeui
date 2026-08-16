package com.github.heartratemonitor_compose.ui.server

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.network.IpAddressProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.feature.server.R
import com.github.heartratemonitor_compose.service.LanTransferSharedState
import com.github.heartratemonitor_compose.service.server.NsdDiscoverer
import com.github.heartratemonitor_compose.service.server.PairClient
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * 局域网传输页面的 ViewModel（教科书式 MVI，Phase 3）。
 *
 * 职责（D1 迁移：业务逻辑出 UI 层）：
 * - NSD 扫描生命周期（StartScan/StopScan）与发现列表；
 * - 配对流程与结果状态机（pairingPc / pairResult / pairError），
 *   scanJob/pairJob 由 [viewModelScope] 管理，页面退出自动取消；
 * - WebSocket 连接数经 [LanTransferSharedState]（Hilt 单例，保持原样）下行，
 *   连接建立时停止扫描、断开时清空发现列表（与原页面 LaunchedEffect(isConnected) 语义一致）。
 * - pairResult/pairError 采用迁移方案 §3.4 方案 2：状态内可空字段 + ConsumePairResult Intent。
 *
 * 设置读写仍统一走 [SettingsRepository]（契约 2）。
 */
@HiltViewModel
class LanTransferViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val ipAddressProvider: IpAddressProvider,
    private val lanTransferSharedState: LanTransferSharedState,
    private val nsdDiscoverer: NsdDiscoverer,
    private val pairClient: PairClient,
    @ApplicationContext private val appContext: Context
) : MviViewModel<LanTransferUiState, LanTransferIntent>(
    LanTransferUiState(
        // 构造期同步读真值（SettingsRepository 已预热快照），observe/collect 回流后续变化
        wsEnabled = settings.get(SettingsKeys.WEBSOCKET_SERVER_ENABLED),
        isConnected = lanTransferSharedState.webSocketClientCount.value > 0
    )
) {

    private var scanJob: Job? = null
    private var pairJob: Job? = null

    init {
        viewModelScope.launch {
            settings.observe(SettingsKeys.WEBSOCKET_SERVER_ENABLED).collect { enabled ->
                setState { it.copy(wsEnabled = enabled) }
            }
        }
        // 连接状态联动（等价原页面 LaunchedEffect(isConnected)）：
        // 建立连接停扫描；断开连接额外清空发现列表
        viewModelScope.launch {
            lanTransferSharedState.webSocketClientCount.collectLatest { count ->
                val connected = count > 0
                setState { it.copy(isConnected = connected) }
                stopScanInternal()
                if (!connected) {
                    setState { it.copy(devices = emptyList()) }
                }
            }
        }
    }

    override suspend fun handleIntent(intent: LanTransferIntent) {
        when (intent) {
            LanTransferIntent.StartScan -> startScanInternal()
            LanTransferIntent.StopScan -> stopScanInternal()
            is LanTransferIntent.StartPairing -> startPairingInternal(intent.pc)
            LanTransferIntent.ConsumePairResult ->
                setState { it.copy(pairResult = null, pairError = null) }
            LanTransferIntent.Disconnect -> disconnectInternal()
        }
    }

    private fun startScanInternal() {
        if (currentState.isScanning || currentState.isConnected) return
        setState { it.copy(isScanning = true, devices = emptyList()) }
        scanJob = viewModelScope.launch {
            try {
                nsdDiscoverer.discover().collectLatest { list ->
                    setState { it.copy(devices = list) }
                }
            } finally {
                setState { it.copy(isScanning = false) }
            }
        }
    }

    private fun stopScanInternal() {
        scanJob?.cancel()
        scanJob = null
        setState { it.copy(isScanning = false) }
    }

    /**
     * 发起配对。前置条件：无进行中的配对、WebSocket 服务器已开启。
     * 请求参数（ws 端口/token、本机 IP、设备名与 ID）在此组装，属业务流程而非 UI 职责。
     */
    private fun startPairingInternal(pc: NsdDiscoverer.DiscoveredPc) {
        if (currentState.pairingPc != null) return
        if (!currentState.wsEnabled) {
            setState { it.copy(pairError = appContext.getString(R.string.lan_ws_not_enabled)) }
            return
        }
        setState { it.copy(pairingPc = pc, pairResult = null, pairError = null) }

        val wsPort = settings.get(SettingsKeys.WEBSOCKET_SERVER_PORT)
        val wsToken = settings.get(SettingsKeys.SERVER_ACCESS_TOKEN)
        val wsIp = ipAddressProvider.getLocalIpAddress() ?: ""
        val deviceName = buildString {
            append(appContext.getString(com.github.heartratemonitor_compose.service.R.string.app_name))
            append("-")
            append(Build.MODEL)
        }
        val deviceId = try {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (_: Exception) { "" }

        pairJob = viewModelScope.launch {
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
            } ?: PairClient.PairResponse.Failed(appContext.getString(R.string.lan_pair_timeout))

            // 联动字段一次归约：结果 + 清除进行中 + 错误文案
            val errorMsg = when (resp) {
                is PairClient.PairResponse.Approved -> null
                is PairClient.PairResponse.Rejected ->
                    appContext.getString(R.string.lan_pair_rejected)
                is PairClient.PairResponse.Failed ->
                    appContext.getString(R.string.lan_pair_failed, resp.message)
            }
            setState { it.copy(pairResult = resp, pairingPc = null, pairError = errorMsg) }
        }
    }

    /** 断开：取消配对/扫描、清空状态与发现列表、断开全部 WS 客户端。 */
    private fun disconnectInternal() {
        pairJob?.cancel()
        pairJob = null
        setState { it.copy(pairingPc = null, pairResult = null, pairError = null) }
        stopScanInternal()
        setState { it.copy(devices = emptyList()) }

        lanTransferSharedState.disconnectWebSocketClients?.invoke()
    }
}

/** 局域网传输页用户意图。 */
sealed interface LanTransferIntent {
    data object StartScan : LanTransferIntent
    data object StopScan : LanTransferIntent
    data class StartPairing(val pc: NsdDiscoverer.DiscoveredPc) : LanTransferIntent

    /** 关闭结果弹窗后消费结果/错误（弹窗显隐为 UI 瞬时态，结果数据归 VM，§3.4 方案 2）。 */
    data object ConsumePairResult : LanTransferIntent

    data object Disconnect : LanTransferIntent
}

/** 局域网传输页 UI 状态（只读快照）。 */
data class LanTransferUiState(
    val wsEnabled: Boolean = false,
    val isConnected: Boolean = false,
    val isScanning: Boolean = false,
    val devices: List<NsdDiscoverer.DiscoveredPc> = emptyList(),
    val pairingPc: NsdDiscoverer.DiscoveredPc? = null,
    val pairResult: PairClient.PairResponse? = null,
    val pairError: String? = null
)
