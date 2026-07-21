package com.github.heartratemonitor_compose.ui.main

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.FavoriteDeviceRepository
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.service.BleService
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import com.juul.kable.Advertisement
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

enum class AppStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

/**
 * 心率图表数据点。替代原 MPAndroidChart 的 Entry 类型。
 *
 * @param timeOffsetSec 距离会话开始时间的秒偏移（X 轴）
 * @param heartRate 心率值（Y 轴）
 */
data class HeartRatePoint(
    val timeOffsetSec: Float,
    val heartRate: Float
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val settings: SettingsRepository = container.settingsRepository
    private val favoriteDeviceRepository: FavoriteDeviceRepository = container.favoriteDeviceRepository
    private val mainHandler = Handler(Looper.getMainLooper())

    private var bleServiceRef: WeakReference<BleService>? = null

    private var serviceDataJob: Job? = null

    // --- StateFlow for UI ---
    private val _statusMessage = MutableStateFlow(getApplication<Application>().getString(R.string.ble_idle))
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _appStatus = MutableStateFlow(AppStatus.DISCONNECTED)
    val appStatus: StateFlow<AppStatus> = _appStatus.asStateFlow()

    // --- Currently connecting device (for per-row progress indicator) ---
    private val _connectingDeviceId = MutableStateFlow<String?>(null)
    val connectingDeviceId: StateFlow<String?> = _connectingDeviceId.asStateFlow()

    // 防止自动重连扫描的 ScanFailed（DISCONNECTED）在手动连接的 Connecting 到达之前清空 connectingDeviceId
    @Volatile
    private var manualConnectionPending = false

    // --- Favorite device ---
    // 直接暴露 SettingsRepository 的 StateFlow，SharedPreferences listener 同步更新，
    // 避免 MutableStateFlow + 协程 collect 的异步延迟（FavoriteDevicesScreen 取消收藏后 DevicesScreen 可实时感知）。
    val favoriteDeviceId: StateFlow<String?> = settings.observeStringNullable(PrefsKeys.FAVORITE_DEVICE_ID)

    // --- Chart State Management ---
    private var chartStartTime = 0L
    private val chartDataPoints = ArrayDeque<HeartRatePoint>()
    private val _newChartEntries = MutableStateFlow<List<HeartRatePoint>?>(null)
    val newChartEntries: StateFlow<List<HeartRatePoint>?> = _newChartEntries.asStateFlow()

    // RR-Interval 累加时间戳:逐拍数据按 RR 秒数累加,得到每个心跳的相对时间 (秒)
    private var lastChartTimeSec = 0f

    private val MAX_CHART_POINTS = 10000

    /**
     * 首页实时图表只保留最近 N 秒的数据，避免长时间连接后内存和渲染开销线性增长。
     * 超过该窗口的旧点会随新点到达被移除；完整历史数据仍由 Room 持久化（历史记录开启时）。
     */
    private val MAX_CHART_WINDOW_SECONDS = 300f

    /**
     * TRIM 内存预警时图表降采样后保留的最近点数。
     * 心率原始数据已持久化到 Room，内存中的图表缓存可安全降采样。
     */
    private val TRIM_KEEP_POINTS = 500

    val chartHistory: List<HeartRatePoint> get() = chartDataPoints

    // --- 历史记录开关状态（供 UI 和控制图表/统计使用）---
    private val _isHistoryEnabled = MutableStateFlow(settings.getBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false))
    val isHistoryEnabled: StateFlow<Boolean> = _isHistoryEnabled.asStateFlow()

    // --- 本次连接的心率最大值/最小值（断开或重连时重置，进程死亡自然丢失）---
    // 必须在 init 块之前初始化，因为 init 中 collect 历史记录开关会立即回调 clearChartData()，
    // 若此时 _sessionMaxHr/_sessionMinHr 尚未构造会触发 NullPointerException。
    private val _sessionMaxHr = MutableStateFlow(0)
    val sessionMaxHr: StateFlow<Int> = _sessionMaxHr.asStateFlow()

    private val _sessionMinHr = MutableStateFlow(0)
    val sessionMinHr: StateFlow<Int> = _sessionMinHr.asStateFlow()

    init {
        viewModelScope.launch {
            settings.observeBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false).collect { enabled ->
                _isHistoryEnabled.value = enabled
                if (enabled) {
                    mainHandler.post { initializeChart() }
                } else {
                    clearChartData()
                }
            }
        }

        // 注册公平运行内存监听器，在 TRIM/KILL 时释放内存
        FairMemoryReceiver.getInstance().setMemoryListener(object : FairMemoryReceiver.MemoryListener {
            override fun onTrimMemory(notifyType: Int) {
                // 监听器在 FairMemoryReceiver 的 HandlerThread 上调用，
                // chartDataPoints 与 StateFlow 需在主线程操作，切换到主线程
                mainHandler.post { releaseNonCriticalMemory(notifyType) }
            }

            override fun onKillMemory() {
                // 心率数据已通过 Room 持久化，无需额外保存
            }
        })

        // 一次性迁移：将 SharedPreferences 中的收藏历史迁移到 Room
        viewModelScope.launch { favoriteDeviceRepository.migrateLegacyFavoritesIfNeeded(application) }
    }

    // --- Service Data Flows ---
    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _scanResults = MutableStateFlow<List<Advertisement>>(emptyList())
    val scanResults: StateFlow<List<Advertisement>> = _scanResults.asStateFlow()

    // 当前已连接设备信息（id + name），断开时为 null。供 DevicesScreen 显示。
    // 中转 BleService.connectedDevice：服务未绑定前返回 null，绑定后自动镜像服务端状态。
    private val _connectedDevice = MutableStateFlow<BleService.ConnectedDevice?>(null)
    val connectedDevice: StateFlow<BleService.ConnectedDevice?> = _connectedDevice.asStateFlow()

    fun setBleService(service: BleService) {
        if (bleServiceRef?.get() === service && serviceDataJob?.isActive == true) return

        this.bleServiceRef = WeakReference(service)
        initializeDataStreams(service)
    }

    private fun initializeDataStreams(service: BleService) {
        serviceDataJob?.cancel()

        serviceDataJob = viewModelScope.launch {
            launch {
                service.heartRateMeasurement.collect { measurement ->
                    _heartRate.value = measurement.bpm
                    if (measurement.bpm > 0 && _appStatus.value == AppStatus.CONNECTED) {
                        processHeartRateMeasurement(measurement)
                    }
                }
            }

            launch {
                service.speed.collect { _speed.value = it }
            }

            launch {
                service.scanResults.collect { _scanResults.value = it }
            }

            launch {
                service.connectedDevice.collect { _connectedDevice.value = it }
            }

            launch {
                service.bleState.collectLatest { state ->
                    Log.d("MainViewModel", "bleState: ${state.javaClass.simpleName}, manualPending=$manualConnectionPending, connectingId=${_connectingDeviceId.value}")
                    _statusMessage.value = state.getMessage(getApplication())
                    val newStatus = when (state) {
                        is BleState.Scanning -> AppStatus.SCANNING
                        is BleState.AutoConnecting, is BleState.Connecting, is BleState.AutoReconnecting -> AppStatus.CONNECTING
                        is BleState.Connected -> AppStatus.CONNECTED
                        else -> AppStatus.DISCONNECTED
                    }

                    if (_appStatus.value != AppStatus.CONNECTED && newStatus == AppStatus.CONNECTED) {
                        initializeChart()
                    }

                    if (_appStatus.value == AppStatus.CONNECTED && newStatus != AppStatus.CONNECTED) {
                        _sessionMaxHr.value = 0
                        _sessionMinHr.value = 0
                    }

                    if (newStatus != AppStatus.CONNECTING) {
                        // 手动连接中途可能收到自动重连扫描的 ScanFailed（DISCONNECTED），
                        // 此时 manualConnectionPending=true，不能清空 connectingDeviceId，
                        // 否则后续 Connecting 到达时已丢失设备信息，动画不会显示。
                        if (!manualConnectionPending) {
                            Log.d("MainViewModel", "clearing connectingDeviceId (newStatus=$newStatus)")
                            _connectingDeviceId.value = null
                        } else {
                            Log.d("MainViewModel", "keeping connectingDeviceId=${_connectingDeviceId.value} (manualPending=true)")
                        }
                    } else {
                        Log.d("MainViewModel", "CONNECTING reached, clearing manualPending, connectingId=${_connectingDeviceId.value}")
                        manualConnectionPending = false
                    }

                    _appStatus.value = newStatus
                }
            }
        }
    }

    private fun initializeChart() {
        chartStartTime = System.currentTimeMillis()
        chartDataPoints.clear()
        lastChartTimeSec = 0f
        _sessionMaxHr.value = 0
        _sessionMinHr.value = 0
    }

    /**
     * 清空当前会话的图表缓存。
     * 用于关闭历史记录时立即重置首页图表，不重置 MAX/MIN（MAX/MIN 独立于历史记录）。
     */
    private fun clearChartData() {
        chartDataPoints.clear()
        chartStartTime = 0L
        lastChartTimeSec = 0f
        _newChartEntries.value = null
    }

    
    private fun processHeartRateMeasurement(measurement: HeartRateMeasurement) {
        if (appStatus.value != AppStatus.CONNECTED) return

        // MAX/MIN 独立于历史记录开关：无论是否开启历史记录，始终跟踪当次连接的心率极值
        val bpm = measurement.bpm
        if (bpm > 0) {
            if (_sessionMaxHr.value == 0 || bpm > _sessionMaxHr.value) {
                _sessionMaxHr.value = bpm
            }
            if (_sessionMinHr.value == 0 || bpm < _sessionMinHr.value) {
                _sessionMinHr.value = bpm
            }
        }

        // 历史记录开关关闭时不累积图表数据。
        if (!_isHistoryEnabled.value) return

        // 防御竞态：状态流通知与数据流到达之间可能存在窗口，确保 chartStartTime 已初始化
        if (chartStartTime == 0L) {
            chartStartTime = System.currentTimeMillis()
        }

        val newPoints = mutableListOf<HeartRatePoint>()
        val rrs = measurement.rrIntervals
        if (rrs.isNotEmpty()) {
            for (rr in rrs) {
                if (rr <= 0f || rr > 3f) continue
                val instantHr = 60f / rr
                if (instantHr < 30f || instantHr > 220f) continue
                lastChartTimeSec += rr
                val point = HeartRatePoint(lastChartTimeSec, instantHr)
                appendPoint(point)
                newPoints.add(point)
            }
        } else {
            // 设备不支持 RR:回退到 BPM + 墙钟时间戳,同步 lastChartTimeSec
            val timeDiffSeconds = (System.currentTimeMillis() - chartStartTime) / 1000f
            val point = HeartRatePoint(timeDiffSeconds, measurement.bpm.toFloat())
            appendPoint(point)
            newPoints.add(point)
            lastChartTimeSec = timeDiffSeconds
        }

        if (newPoints.isNotEmpty()) {
            _newChartEntries.value = newPoints
        }
    }

    private fun appendPoint(point: HeartRatePoint) {
        // 维护最近 300 秒可视窗口，避免长时间连接后 chartDataPoints 线性膨胀导致
        // 主线程扫描/拷贝开销增长（以及 Vico 全量重建 series 的卡顿）。
        val windowStart = point.timeOffsetSec - MAX_CHART_WINDOW_SECONDS
        while (chartDataPoints.isNotEmpty() && chartDataPoints.first().timeOffsetSec < windowStart) {
            chartDataPoints.removeFirst()
        }
        // 兜底：异常时间戳/极端频率下仍不突破硬上限
        if (chartDataPoints.size >= MAX_CHART_POINTS) {
            chartDataPoints.removeFirst()
        }
        chartDataPoints.add(point)
    }

    /**
     * 释放非关键内存（由公平运行内存 TRIM 广播触发）。
     *
     * 按 [notifyType] 差异化释放（参考金标联盟文档 §2.2.1 / §2.2.3）：
     *
     * - [FairMemoryReceiver.NOTIFY_TYPE_PSS]（1000，物理内存异常）：
     *   文档 §2.2.3 指出物理内存异常"先查杀再通知"，紧急度最高。
     *   清空整个 [chartDataPoints]（数据已持久化到 Room，可恢复）+ 清空扫描结果，
     *   最大化释放物理内存。
     *
     * - [FairMemoryReceiver.NOTIFY_TYPE_HEAP]（2000，Java 堆内存异常）：
     *   [System.gc] 对 Java 堆直接有效。降采样到 [TRIM_KEEP_POINTS] 即可，
     *   保留最近图表数据以维持当前会话体验。
     *
     * 必须在主线程执行（操作 [chartDataPoints] 与 [_scanResults]）。
     *
     * @param notifyType 异常类型，见 [FairMemoryReceiver.NOTIFY_TYPE_PSS] / [FairMemoryReceiver.NOTIFY_TYPE_HEAP]
     */
    fun releaseNonCriticalMemory(notifyType: Int) {
        val isPss = notifyType == FairMemoryReceiver.NOTIFY_TYPE_PSS

        if (chartDataPoints.isNotEmpty()) {
            val originalSize = chartDataPoints.size
            if (isPss) {
                // 物理内存异常：清空整个图表缓存（数据已 Room 持久化，可恢复）
                chartDataPoints.clear()
                Log.i("MainViewModel", "TRIM(PSS): 清空图表缓存 $originalSize 点")
            } else if (originalSize > TRIM_KEEP_POINTS) {
                // Java 堆异常：降采样保留最近 N 点，gc 对堆直接有效
                val kept = chartDataPoints.takeLast(TRIM_KEEP_POINTS)
                chartDataPoints.clear()
                chartDataPoints.addAll(kept)
                Log.i("MainViewModel", "TRIM(HEAP): 图表降采样 $originalSize -> 保留最近 $TRIM_KEEP_POINTS 点")
            }
        }

        if (_appStatus.value != AppStatus.SCANNING) {
            _scanResults.value = emptyList()
            Log.i("MainViewModel", "TRIM(${if (isPss) "PSS" else "HEAP"}): 已清空扫描结果缓存")
        }
    }

    // --- Actions delegated to the service ---
    fun startScan() {
        bleServiceRef?.get()?.startScan()
    }

    fun startAutoConnectScan(identifier: String) {
        _connectingDeviceId.value = identifier
        bleServiceRef?.get()?.startAutoConnectScan(identifier)
    }

    fun connectToDevice(identifier: String) {
        Log.d("MainViewModel", "connectToDevice: $identifier, setting manualPending=true")
        _connectingDeviceId.value = identifier
        manualConnectionPending = true
        bleServiceRef?.get()?.connectToDevice(identifier)
    }

    fun disconnectDevice() {
        bleServiceRef?.get()?.disconnectDevice()
    }

    // --- Favorite device logic ---
    fun isDeviceFavorite(identifier: String): Boolean {
        return favoriteDeviceId.value == identifier
    }

    fun toggleFavoriteDevice(ad: Advertisement) {
        val id = ad.identifier
        val currentFavorite = favoriteDeviceId.value
        val app = getApplication<Application>()
        if (currentFavorite == id) {
            // 取消收藏：删除 Room 记录，并从剩余收藏中恢复最近的一个
            viewModelScope.launch {
                favoriteDeviceRepository.deleteFavoriteDevice(app, id)
                // 从 Room 中查找剩余收藏中最近的一个，恢复为当前收藏设备
                val latestFavorite = favoriteDeviceRepository.getLatestFavoriteDevice(app)
                if (latestFavorite != null) {
                    favoriteDeviceRepository.setFavoriteDeviceId(app, latestFavorite.id)
                } else {
                    favoriteDeviceRepository.clearFavoriteDeviceId(app)
                }
            }
        } else {
            favoriteDeviceRepository.setFavoriteDeviceId(app, id)
            addToFavoriteHistory(id, ad.name ?: app.getString(R.string.unknown_device))
            // 删除旧收藏的 Room 记录，确保设置页收藏列表实时同步
            if (currentFavorite != null) {
                viewModelScope.launch {
                    favoriteDeviceRepository.deleteFavoriteDevice(app, currentFavorite)
                }
            }
        }
    }

    /**
     * 将设备添加到收藏历史列表（Room 存储）。
     * - 去重：OnConflictStrategy.REPLACE 自动覆盖同 ID 记录
     * - 排序：按 timestamp DESC，新收藏排最前
     */
    private fun addToFavoriteHistory(id: String, name: String) {
        viewModelScope.launch {
            favoriteDeviceRepository.addFavoriteDevice(getApplication(), id, name)
        }
    }

    override fun onCleared() {
        super.onCleared()
        FairMemoryReceiver.getInstance().setMemoryListener(null)
        serviceDataJob?.cancel()
        bleServiceRef = null
    }
}