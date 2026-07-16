package com.github.heartratemonitor_compose.ui.main

import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.db.FavoriteDeviceEntity
import com.github.heartratemonitor_compose.service.BleService
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import com.juul.kable.Advertisement
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
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

    private val sharedPrefs = application.getSharedPreferences("app_settings", Application.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val favoriteDeviceDao = AppDatabase.getDatabase(application).favoriteDeviceDao()

    private var bleServiceRef: WeakReference<BleService>? = null

    private var serviceDataJob: Job? = null

    // --- StateFlow for UI ---
    private val _statusMessage = MutableStateFlow("Click button below to scan")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _appStatus = MutableStateFlow(AppStatus.DISCONNECTED)
    val appStatus: StateFlow<AppStatus> = _appStatus.asStateFlow()

    // --- Favorite device (cached StateFlow，避免每次重组读 SharedPreferences) ---
    private val _favoriteDeviceId = MutableStateFlow<String?>(null)
    val favoriteDeviceId: StateFlow<String?> = _favoriteDeviceId.asStateFlow()

    // --- Chart State Management ---
    private var chartStartTime = 0L
    private val chartDataPoints = ArrayDeque<HeartRatePoint>()
    private val _newChartEntries = MutableStateFlow<List<HeartRatePoint>?>(null)
    val newChartEntries: StateFlow<List<HeartRatePoint>?> = _newChartEntries.asStateFlow()

    // RR-Interval 累加时间戳:逐拍数据按 RR 秒数累加,得到每个心跳的相对时间 (秒)
    private var lastChartTimeSec = 0f

    private val MAX_CHART_POINTS = 10000

    /**
     * TRIM 内存预警时图表降采样后保留的最近点数。
     * 心率原始数据已持久化到 Room，内存中的图表缓存可安全降采样。
     */
    private val TRIM_KEEP_POINTS = 500

    val chartHistory: List<HeartRatePoint> get() = chartDataPoints

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "favorite_device_id") {
            _favoriteDeviceId.value = prefs.getString("favorite_device_id", null)
        }
    }

    init {
        // 初始化收藏设备缓存
        _favoriteDeviceId.value = sharedPrefs.getString("favorite_device_id", null)

        // 监听 SharedPreferences 变化（来自 FavoriteDevicesScreen 等外部修改），同步 StateFlow
        sharedPrefs.registerOnSharedPreferenceChangeListener(prefsListener)

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
        viewModelScope.launch { migrateFavoriteHistoryIfNeeded() }
    }

    /**
     * 一次性迁移：将 SharedPreferences JSON 数组中的收藏历史导入 Room。
     * 迁移完成后写入标志位，后续不再执行。
     */
    private suspend fun migrateFavoriteHistoryIfNeeded() {
        if (sharedPrefs.getBoolean("favorite_history_migrated_to_room", false)) return
        val json = sharedPrefs.getString("favorite_device_history", null) ?: "[]"
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                favoriteDeviceDao.insert(FavoriteDeviceEntity(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                ))
            }
        } catch (e: Exception) {
            Log.w("MainViewModel", "收藏历史迁移到 Room 失败", e)
        }
        sharedPrefs.edit { putBoolean("favorite_history_migrated_to_room", true) }
    }

    // --- Service Data Flows ---
    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _scanResults = MutableStateFlow<List<Advertisement>>(emptyList())
    val scanResults: StateFlow<List<Advertisement>> = _scanResults.asStateFlow()

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
                service.bleState.collectLatest { state ->
                    _statusMessage.value = state.message
                    val newStatus = when (state) {
                        is BleState.Scanning -> AppStatus.SCANNING
                        is BleState.AutoConnecting, is BleState.Connecting, is BleState.AutoReconnecting -> AppStatus.CONNECTING
                        is BleState.Connected -> AppStatus.CONNECTED
                        else -> AppStatus.DISCONNECTED
                    }

                    if (_appStatus.value != AppStatus.CONNECTED && newStatus == AppStatus.CONNECTED) {
                        initializeChart()
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
    }

    /**
     * 处理一帧心率测量,生成图表数据点。
     *
     * 逐拍算法:
     * - 当设备上报 RR-Interval 时,每个 RR 对应一个心跳,瞬时心率 = 60 / rr(秒)。
     *   时间戳按 RR 累加,使 X 轴反映真实的心拍节律 (分辨率比 1Hz 平均 BPM 高)。
     * - 当设备不支持 RR 时,回退到平均 BPM,用墙钟时间戳 (与旧实现一致)。
     *
     * 异常剔除:
     * - RR 超出 (0, 3] 秒视为噪声 (对应 <20 或 >∞ bpm);
     * - 瞬时心率超出 [30, 220] bpm 视为生理范围外的伪迹,丢弃。
     */
    private fun processHeartRateMeasurement(measurement: HeartRateMeasurement) {
        if (appStatus.value != AppStatus.CONNECTED) return

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

        // 1. 图表数据释放
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

        // 2. 非扫描态清空扫描结果
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
        bleServiceRef?.get()?.startAutoConnectScan(identifier)
    }

    fun connectToDevice(identifier: String) {
        bleServiceRef?.get()?.connectToDevice(identifier)
    }

    fun disconnectDevice() {
        bleServiceRef?.get()?.disconnectDevice()
    }

    // --- Favorite device logic ---
    fun isDeviceFavorite(identifier: String): Boolean {
        return _favoriteDeviceId.value == identifier
    }

    fun toggleFavoriteDevice(ad: Advertisement) {
        val id = ad.identifier
        val currentFavorite = _favoriteDeviceId.value
        if (currentFavorite == id) {
            // 取消收藏：删除 Room 记录，并从剩余收藏中恢复最近的一个
            viewModelScope.launch {
                favoriteDeviceDao.deleteById(id)
                // 从 Room 中查找剩余收藏中最近的一个，恢复为当前收藏设备
                val remaining = favoriteDeviceDao.getAllRaw()
                val latestFavorite = remaining.firstOrNull()
                if (latestFavorite != null) {
                    sharedPrefs.edit {
                        putString("favorite_device_id", latestFavorite.id)
                    }
                    _favoriteDeviceId.value = latestFavorite.id
                } else {
                    sharedPrefs.edit {
                        putString("favorite_device_id", null)
                    }
                    _favoriteDeviceId.value = null
                }
            }
        } else {
            // 收藏设备（替换旧收藏）
            sharedPrefs.edit {
                putString("favorite_device_id", id)
            }
            _favoriteDeviceId.value = id
            addToFavoriteHistory(id, ad.name ?: "未知设备")
            // 删除旧收藏的 Room 记录，确保设置页收藏列表实时同步
            if (currentFavorite != null) {
                viewModelScope.launch {
                    favoriteDeviceDao.deleteById(currentFavorite)
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
            favoriteDeviceDao.insert(FavoriteDeviceEntity(
                id = id,
                name = name,
                timestamp = System.currentTimeMillis()
            ))
        }
    }

    override fun onCleared() {
        super.onCleared()
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        FairMemoryReceiver.getInstance().setMemoryListener(null)
        serviceDataJob?.cancel()
        bleServiceRef = null
    }
}