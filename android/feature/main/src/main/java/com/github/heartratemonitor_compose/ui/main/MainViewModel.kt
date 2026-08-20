package com.github.heartratemonitor_compose.ui.main

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.settings.AppSettings
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.data.repository.FavoriteDeviceRepository
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.system.OverlayPermissionProvider
import com.github.heartratemonitor_compose.service.BleConnectionManager
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.service.ServiceLauncher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import com.juul.kable.Advertisement
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import java.lang.ref.WeakReference
import javax.inject.Inject

/**
 * MVI 架构，Phase 5。仅 BLE 状态订阅 + 组件编排 + 对外单一 [uiState]；
 * 图表数据管道归 [ChartDataManager]，BLE 数据管道订阅与状态机归约见 MainBleStreams.kt。
 *
 * 契约 6 红线原样保留：manualConnectionPending 防竞态、
 * bleToastListener 回调（§3.4 方案 1）、toggleFloatingWindow(): Boolean 返回值语义。
 *
 * Activity 生命周期编排方法非 UI 用户意图，保持公开方法形态由 MainActivity 调用。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val favoriteDeviceRepository: FavoriteDeviceRepository,
    private val fairMemoryReceiver: FairMemoryReceiver,
    private val killStateSaver: KillStateSaver,
    private val serviceLauncher: ServiceLauncher,
    private val overlayPermissionProvider: OverlayPermissionProvider,
    @ApplicationContext internal val appContext: Context
) : MviViewModel<MainUiState, MainIntent>(initialMainUiState(settings, appContext)),
    FairMemoryReceiver.MemoryListener {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var bleServiceRef: WeakReference<BleConnectionManager>? = null

    private var serviceDataJob: Job? = null

    // 防止自动重连扫描的 ScanFailed（DISCONNECTED）在手动连接的 Connecting 到达之前清空 connectingDeviceId
    @Volatile
    internal var manualConnectionPending = false

    /** 上一次 BLE 状态：ScanFailed 紧跟 AutoReconnecting 才提示重连失败 */
    internal var previousBleState: BleState? = null

    internal val chartDataManager = ChartDataManager(viewModelScope).apply {
        // 构造期同步历史记录开关，确保首个心率包到达时 onMeasurement 不会被
        // isHistoryEnabled=false 拦截（collect 的首次重放被 drop(1) 跳过，不再同步此字段）
        isHistoryEnabled = settings.get(SettingsKeys.HISTORY_RECORDING_ENABLED)
    }

    /**
     * 设备页专用精简状态流：从 [uiState] map 出设备页需要的字段 + distinctUntilChanged 去重。
     *
     * 避免设备页全量订阅 [uiState]：heartRate / speed / chartDataSnapshot 等高频字段每秒更新，
     * 设备页完全不需要，但会导致整个 DevicesScreen 无效重组。此流只在设备页相关字段
     * （appStatus / scanResults / connectingDeviceId / connectedDevice / favoriteDeviceId /
     * scanFilterEnabled / searchTipShown）真正变化时才发射新值。
     */
    val devicesUiState: StateFlow<DevicesUiState> = uiState
        .map { state: MainUiState -> state.toDevicesUiState() }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = uiState.value.toDevicesUiState()
        )

    /**
     * BLE 状态转换触发的一次性 Toast 回调：Activity 注册/注销，
     * 避免一次性事件进 StateFlow 产生重放（§3.4 方案 1 之 VM 回调）。
     */
    @Volatile
    var bleToastListener: ((BleToastEvent) -> Unit)? = null

    /** 构造期解析旧开关迁移结果（initialMainUiState 已执行过一次，幂等仅读）。 */
    private val resolveSoundModeFallback: String = currentState.fullscreenSoundMode

    init {
        // 历史记录开关：投影 + 图表管道联动（开关热更新语义原样保留）
        // drop(1) 跳过 StateFlow 首次重放：VM 重建时 settings.observe 会立即重放当前值，
        // 但这是状态恢复而非用户切换——reset()/clear() 不应在此时执行，否则与心率包到达
        // 存在竞态，概率性清空刚产出的图表数据导致转圈圈。isHistoryEnabled 的初始同步
        // 已在 chartDataManager 构造期完成，首次重放跳过后不影响后续心率包的图表累积。
        // 与项目中 bleState / BleSettingsListener / StatusBarResidentService 等的 drop(1) 模式一致。
        viewModelScope.launch {
            settings.observe(SettingsKeys.HISTORY_RECORDING_ENABLED).drop(1).collect { enabled ->
                setState { it.copy(isHistoryEnabled = enabled) }
                chartDataManager.isHistoryEnabled = enabled
                if (enabled) {
                    mainHandler.post { chartDataManager.reset() }
                } else {
                    chartDataManager.clear()
                }
            }
        }

        // 纯状态投影订阅合并为 combine 单流：任一设置变化只触发一次 setState，
        // 避免多个独立 collector 各自 setState 的交错与冗余归约。
        // settings.settings 是 AppSettings 聚合快照 StateFlow，覆盖
        // speedDisplayEnabled / scanFilterEnabled / heartRateRingMax /
        // floatingWindowEnabled / hideFromRecentsEnabled / searchTipShown /
        // heartbeatAnimationEnabled / favoriteDeviceId / fullscreenSoundMode 九字段；
        // FLOATING_TEXT_COLOR 因全屏页默认 RED（历史分歧点）单独 observe 后并入 combine。
        viewModelScope.launch {
            combine(
                settings.settings,
                settings.observe(SettingsKeys.FLOATING_TEXT_COLOR, android.graphics.Color.RED)
            ) { s, textColor ->
                // fullscreenSoundMode：键缺失时回退构造期解析的旧开关迁移结果（幂等）
                SettingsSnapshot(
                    isSpeedEnabled = s.speedDisplayEnabled,
                    scanFilterEnabled = s.scanFilterEnabled,
                    ringMaxHr = s.heartRateRingMax,
                    floatingWindowEnabled = s.floatingWindowEnabled,
                    hideFromRecentsEnabled = s.hideFromRecentsEnabled,
                    searchTipShown = s.searchTipShown,
                    heartbeatAnimationEnabled = s.heartbeatAnimationEnabled,
                    favoriteDeviceId = s.favoriteDeviceId,
                    fullscreenHeartTextColor = textColor,
                    fullscreenSoundMode = s.fullscreenSoundMode ?: resolveSoundModeFallback
                )
            }.collect { snap ->
                setState {
                    it.copy(
                        isSpeedEnabled = snap.isSpeedEnabled,
                        scanFilterEnabled = snap.scanFilterEnabled,
                        ringMaxHr = snap.ringMaxHr,
                        floatingWindowEnabled = snap.floatingWindowEnabled,
                        hideFromRecentsEnabled = snap.hideFromRecentsEnabled,
                        searchTipShown = snap.searchTipShown,
                        heartbeatAnimationEnabled = snap.heartbeatAnimationEnabled,
                        favoriteDeviceId = snap.favoriteDeviceId,
                        fullscreenHeartTextColor = snap.fullscreenHeartTextColor,
                        fullscreenSoundMode = snap.fullscreenSoundMode
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                chartDataManager.chartDataSnapshot,
                chartDataManager.sessionMaxHr,
                chartDataManager.sessionMinHr
            ) { snapshot, maxHr, minHr -> Triple(snapshot, maxHr, minHr) }.collect { (snapshot, maxHr, minHr) ->
                setState {
                    it.copy(chartDataSnapshot = snapshot, sessionMaxHr = maxHr, sessionMinHr = minHr)
                }
            }
        }

        fairMemoryReceiver.addMemoryListener(this)

        viewModelScope.launch { favoriteDeviceRepository.migrateLegacyFavoritesIfNeeded() }
    }

    override suspend fun handleIntent(intent: MainIntent) {
        when (intent) {
            MainIntent.StartScan -> bleServiceRef?.get()?.startScan()
            MainIntent.StopScan -> bleServiceRef?.get()?.stopScan()
            is MainIntent.ConnectToDevice -> {
                Log.d("MainViewModel", "connectToDevice: ${intent.identifier}, setting manualPending=true")
                setState { it.copy(connectingDeviceId = intent.identifier) }
                manualConnectionPending = true
                bleServiceRef?.get()?.connectToDevice(intent.identifier)
            }
            MainIntent.DisconnectDevice -> bleServiceRef?.get()?.disconnectDevice()
            is MainIntent.ToggleFavoriteDevice -> toggleFavoriteDevice(intent.advertisement)
            MainIntent.MarkSearchTipShown -> settings.set(SettingsKeys.SEARCH_TIP_SHOWN, true)
            is MainIntent.SetHeartRateRingMax ->
                settings.set(SettingsKeys.HEART_RATE_RING_MAX, intent.value)
        }
    }

    /**
     * 恢复用户上次启用但被系统回收的服务（原 MainActivity.recoverServices）。
     *
     * 修复点：原 SettingsActivity 仅在用户打开设置页时才恢复服务，导致用户重启应用后
     * StatusBarResidentService / HeartRateAlarmService 不会自动恢复。改为在 MainActivity
     * onCreate 时经本 VM 调用，确保应用启动即恢复。
     */
    fun recoverServices() {
        val residentEnabled = settings.get(SettingsKeys.STATUS_BAR_RESIDENT_ENABLED)
        if (residentEnabled && overlayPermissionProvider.canDrawOverlays()) {
            serviceLauncher.startStatusBarResidentService()
        }

        // 恢复心率预警服务
        val alarmEnabled = settings.get(SettingsKeys.HEART_RATE_ALARM_ENABLED)
        if (alarmEnabled) {
            serviceLauncher.startHeartRateAlarmService()
        }
    }

    /** 自动连接判定与触发（原 MainActivity.checkAndStartAutoConnectScan）。 */
    fun checkAndStartAutoConnectScan() {
        val isAutoConnectEnabled = settings.get(SettingsKeys.AUTO_CONNECT_ENABLED)
        val favorite = settings.getNullable(SettingsKeys.FAVORITE_DEVICE_ID)
        if (isAutoConnectEnabled && favorite != null) {
            startAutoConnectScan(favorite)
        }
    }

    private fun startAutoConnectScan(identifier: String) {
        setState { it.copy(connectingDeviceId = identifier) }
        bleServiceRef?.get()?.startAutoConnectScan(identifier)
    }

    /**
     * 一次性行为的返回值机制保留（§3.4 方案 1）：需 Activity 上下文跳转权限页时
     * 由 Activity 同步取得判定结果。
     */
    fun toggleFloatingWindow(): Boolean {
        val shouldBeEnabled = !settings.get(SettingsKeys.FLOATING_WINDOW_ENABLED)
        if (shouldBeEnabled && !overlayPermissionProvider.canDrawOverlays()) {
            return true
        }
        settings.set(SettingsKeys.FLOATING_WINDOW_ENABLED, shouldBeEnabled)
        return false
    }

    /**
     * 绑定机制不变：仍由 MainActivity 通过 Binder 绑定 BleService 后注入实例，
     * WeakReference 持有避免 ViewModel 泄潏 Service。
     */
    fun setConnectionManager(manager: BleConnectionManager) {
        if (bleServiceRef?.get() === manager && serviceDataJob?.isActive == true) return

        this.bleServiceRef = WeakReference(manager)
        serviceDataJob?.cancel()
        serviceDataJob = bindBleDataStreams(manager)

        // 状态恢复：bleState 订阅的 drop(1) 会跳过当前值的首次重放（避免图表 reset / Toast），
        // 但 appStatus 必须同步——否则 Activity 重建后重新绑定时，首页会显示未连接，
        // 而设备实际仍连接着（BleService 前台服务未被回收）。此处仅同步状态与图表连接标志位，
        // 不调用 handleBleState，不触发图表 reset，不触发 Toast。
        val currentBleState = manager.bleState.value
        val restoredStatus = when (currentBleState) {
            is BleState.Scanning -> AppStatus.SCANNING
            is BleState.AutoConnecting, is BleState.Connecting, is BleState.AutoReconnecting -> AppStatus.CONNECTING
            is BleState.Connected -> AppStatus.CONNECTED
            else -> AppStatus.DISCONNECTED
        }
        if (restoredStatus == AppStatus.CONNECTED) {
            chartDataManager.isConnected = true
        }
        reduceState {
            it.copy(
                appStatus = restoredStatus,
                statusMessage = currentBleState.getMessage(appContext)
            )
        }
    }

    internal fun reduceState(reducer: (MainUiState) -> MainUiState) {
        setState(reducer)
    }

    internal val stateSnapshot: MainUiState get() = currentState

    private fun toggleFavoriteDevice(ad: Advertisement) {
        val id = ad.identifier
        val currentFavorite = currentState.favoriteDeviceId
        if (currentFavorite == id) {
            viewModelScope.launch {
                favoriteDeviceRepository.deleteAndRestoreLatest(id)
            }
        } else {
            favoriteDeviceRepository.setFavoriteDeviceId(id)
            addToFavoriteHistory(id, ad.name ?: appContext.getString(com.github.heartratemonitor_compose.service.R.string.unknown_device))
            if (currentFavorite != null) {
                viewModelScope.launch {
                    favoriteDeviceRepository.deleteFavoriteDevice(currentFavorite)
                }
            }
        }
    }

    private fun addToFavoriteHistory(id: String, name: String) {
        viewModelScope.launch {
            favoriteDeviceRepository.addFavoriteDevice(id, name)
        }
    }

    // TRIM/KILL 回调顺序不得调整（契约 6）

    /**
     * 图表缓存释放委派给 ChartDataManager.releaseOnTrim，
     * 本方法额外负责清空扫描结果缓存。
     */
    fun releaseNonCriticalMemory(notifyType: Int) {
        val isPss = notifyType == FairMemoryReceiver.NOTIFY_TYPE_PSS

        chartDataManager.releaseOnTrim(notifyType)

        if (currentState.appStatus != AppStatus.SCANNING) {
            setState { it.copy(scanResults = emptyList()) }
            Log.i("MainViewModel", "TRIM(${if (isPss) "PSS" else "HEAP"}): 已清空扫描结果缓存")
        }
    }

    override fun onTrimMemory(notifyType: Int) {
        mainHandler.post { releaseNonCriticalMemory(notifyType) }
    }

    override fun onKillMemory() {
        killStateSaver.save()
    }

    override fun onCleared() {
        super.onCleared()
        fairMemoryReceiver.removeMemoryListener(this)
        serviceDataJob?.cancel()
        bleServiceRef = null
    }
}

/**
 * [MainViewModel] init 中 combine 单流的中间快照：将 AppSettings 聚合快照 + 全屏文字色
 * 投影为 MainUiState 需要的字段集合，避免在 collect 内逐字段 copy 产生多次 setState。
 */
private data class SettingsSnapshot(
    val isSpeedEnabled: Boolean,
    val scanFilterEnabled: Boolean,
    val ringMaxHr: Int,
    val floatingWindowEnabled: Boolean,
    val hideFromRecentsEnabled: Boolean,
    val searchTipShown: Boolean,
    val heartbeatAnimationEnabled: Boolean,
    val favoriteDeviceId: String?,
    val fullscreenHeartTextColor: Int,
    val fullscreenSoundMode: String
)
