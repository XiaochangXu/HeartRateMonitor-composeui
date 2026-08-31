package com.github.heartratemonitor_compose.service

import android.util.Log
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.data.model.ChartDataSnapshot
import com.github.heartratemonitor_compose.data.model.HeartRatePoint
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toImmutableList

/**
 * 服务层会话图表状态追踪器：RR-Interval → HeartRatePoint → 滑动窗口管理 → ChartDataSnapshot 发布，
 * 以及本次连接的心率极值跟踪。
 *
 * 与原 [ChartDataManager]（:feature/main）的核心区别：
 * - 生命周期归属 BLE 连接（由 [BleConnectionHandler] 持有），而非 Activity/ViewModel。
 * - StateFlow 重放天然实现「重进即恢复」——退出应用再重进后，UI 订阅服务层图表流
 *   立即获得当前会话的完整图表缓存，不再归零。
 * - 历史记录开关关闭期间不统计图表点、不发布快照（时间基准保持零），开启后从零
 *   开始绘制；但 MAX/MIN 极值不受开关影响，始终跟踪当次连接全程。
 *   连接/断开由 [reset] / [clear] 显式控制；历史开关联动由 [BleSettingsListener] 接管。
 *
 * 所有可变状态通过 @Synchronized 保证线程安全——心率包到达在 IO 线程调用，
 * 历史开关联动在主线程调用，两者不会交错。
 *
 * @param scope 连接协程作用域（connectionJob 的子协程），断开连接时随 connectionJob 取消。
 * @param historyEnabled 历史记录开关读取器（SettingsRepository.get 走内存快照，零 IO，
 * 每个心率包调用一次成本可忽略）。返回 false 时仅跟踪极值，跳过图表统计与发布。
 */
class SessionChartTracker(
    private val scope: CoroutineScope,
    private val historyEnabled: () -> Boolean
) {

    private val _chartDataSnapshot = MutableStateFlow<ChartDataSnapshot?>(null)
    val chartDataSnapshot: StateFlow<ChartDataSnapshot?> = _chartDataSnapshot.asStateFlow()

    private val _sessionMaxHr = MutableStateFlow(0)
    val sessionMaxHr: StateFlow<Int> = _sessionMaxHr.asStateFlow()

    private val _sessionMinHr = MutableStateFlow(0)
    val sessionMinHr: StateFlow<Int> = _sessionMinHr.asStateFlow()

    // --- 内部管道状态 ---
    private var chartStartTime = 0L
    private val chartDataPoints = ArrayDeque<HeartRatePoint>()
    private val chartXValues = ArrayDeque<Double>()
    private val chartYValues = ArrayDeque<Double>()

    // RR-Interval 累加时间戳:逐拍数据按 RR 秒数累加,得到每个心跳的相对时间 (秒)
    private var lastChartTimeSec = 0f

    private companion object {
        const val TAG = "SessionChartTracker"
        const val MAX_CHART_POINTS = 10000

        /**
         * 快照发布节流间隔：心率包 ~1Hz，500ms 节流将 UI 重组 + Vico 重建频率减半，
         * 最大延迟 500ms 对用户不可感知（心率数字不受节流，仍即时更新）。
         */
        const val SNAPSHOT_THROTTLE_MS = 500L

        /**
         * 首页实时图表只保留最近 N 秒的数据，避免长时间连接后内存和渲染开销线性增长。
         */
        const val MAX_CHART_WINDOW_SECONDS = 60f

        /**
         * TRIM 内存预警时图表降采样后保留的最近点数。
         * 心率原始数据已持久化到 Room，内存中的图表缓存可安全降采样。
         */
        const val TRIM_KEEP_POINTS = 500
    }

    private var hasPendingSnapshot = false
    private var snapshotJob: Job? = null

    /**
     * 处理一次完整心率测量：跟踪 MAX/MIN，把 RR/bpm 转为图表点。
     * 由 [BleConnectionHandler.observeHeartRateData] 在心率包到达时调用。
     * @Synchronized 保证线程安全。
     */
    @Synchronized
    fun onMeasurement(measurement: HeartRateMeasurement) {
        // MAX/MIN 跟踪当次连接的心率极值——与历史记录开关无关，
        // 关闭期间仍持续跟踪，中途开启后无需重新积累。
        val bpm = measurement.bpm
        if (bpm > 0) {
            if (_sessionMaxHr.value == 0 || bpm > _sessionMaxHr.value) {
                _sessionMaxHr.value = bpm
            }
            if (_sessionMinHr.value == 0 || bpm < _sessionMinHr.value) {
                _sessionMinHr.value = bpm
            }
        }

        // 未开启历史记录：不统计图表点、不发布快照。
        // 同时清零 chartStartTime——连接时 reset() 会把它定格为连接时刻的墙钟，
        // 若不清零，无 RR 设备的中途开启回退路径会用「现在 - 连接时刻」作首个 x 值，
        // 导致图表从等待时长处（如 02:00）开始而非从零开始。
        // 置零后首个开启的数据包会走到下方惰性初始化，时间基准归零重新起算。
        if (!historyEnabled()) {
            chartStartTime = 0L
            return
        }

        var appended = false
        val rrs = measurement.rrIntervals
        if (rrs.isNotEmpty()) {
            for (rr in rrs) {
                if (rr <= 0f || rr > 3f) continue
                val instantHr = 60f / rr
                if (instantHr < 30f || instantHr > 220f) continue
                lastChartTimeSec += rr
                appendPoint(HeartRatePoint(lastChartTimeSec, instantHr))
                appended = true
            }
        } else if (measurement.bpm > 0) {
            // 设备不支持 RR:回退到 bpm + 墙钟时间戳,同步 lastChartTimeSec。
            // 无效值（传感器接触丢失时 bpm 被置零、RR 已清空）不绘制，
            // 避免曲线出现下探到 0 的尖刺。
            // chartStartTime 在首个有效点处惰性初始化，保证时间基准锚定首个绘制点。
            if (chartStartTime == 0L) {
                chartStartTime = System.currentTimeMillis()
            }
            val timeDiffSeconds = (System.currentTimeMillis() - chartStartTime) / 1000f
            appendPoint(HeartRatePoint(timeDiffSeconds, measurement.bpm.toFloat()))
            lastChartTimeSec = timeDiffSeconds
            appended = true
        }

        if (appended) {
            if (_chartDataSnapshot.value == null) {
                publishSnapshot()
            } else {
                scheduleSnapshotPublish()
            }
        }
    }

    @Synchronized
    private fun scheduleSnapshotPublish() {
        hasPendingSnapshot = true
        if (snapshotJob?.isActive == true) return
        snapshotJob = scope.launch {
            delay(SNAPSHOT_THROTTLE_MS)
            if (hasPendingSnapshot) {
                hasPendingSnapshot = false
                publishSnapshot()
            }
        }
    }

    @Synchronized
    private fun publishSnapshot() {
        if (chartYValues.isEmpty()) {
            _chartDataSnapshot.value = null
            return
        }
        var wMax = 0.0
        var wMin = Double.MAX_VALUE
        for (y in chartYValues) {
            if (y > wMax) wMax = y
            if (y < wMin) wMin = y
        }
        _chartDataSnapshot.value = ChartDataSnapshot(
            xValues = chartXValues.toImmutableList(),
            yValues = chartYValues.toImmutableList(),
            windowMaxY = if (wMax > 0.0) wMax else 0.0,
            windowMinY = if (wMin < Double.MAX_VALUE && wMin > 0.0) wMin else 0.0
        )
    }

    @Synchronized
    private fun cancelSnapshotJob() {
        snapshotJob?.cancel()
        snapshotJob = null
        hasPendingSnapshot = false
    }

    /**
     * 新会话开始：重置时间基准与全部缓存，并清零 MAX/MIN。
     * 在连接建立（[BleConnectionHandler] State.Connected）处调用，与 startSession 同位置。
     * @Synchronized 保证线程安全。
     */
    @Synchronized
    fun reset() {
        cancelSnapshotJob()
        chartStartTime = System.currentTimeMillis()
        chartDataPoints.clear()
        chartXValues.clear()
        chartYValues.clear()
        _chartDataSnapshot.value = null
        lastChartTimeSec = 0f
        _sessionMaxHr.value = 0
        _sessionMinHr.value = 0
    }

    /**
     * 清零本次连接的心率极值。断开连接时调用，使 UI 立即回落为 "--"。
     * @Synchronized 保证线程安全。
     */
    @Synchronized
    fun resetSessionExtremes() {
        _sessionMaxHr.value = 0
        _sessionMinHr.value = 0
    }

    /**
     * 清空当前会话的图表缓存。
     * 用于断开连接（[BleConnectionHandler.cleanupConnection]）或关闭历史记录时立即重置首页图表。
     * 不重置 MAX/MIN（MAX/MIN 由 [reset] 在新会话开始时独立清零）。
     * @Synchronized 保证线程安全。
     */
    @Synchronized
    fun clear() {
        cancelSnapshotJob()
        chartDataPoints.clear()
        chartXValues.clear()
        chartYValues.clear()
        chartStartTime = 0L
        lastChartTimeSec = 0f
        _chartDataSnapshot.value = null
    }

    @Synchronized
    private fun appendPoint(point: HeartRatePoint) {
        val windowStart = point.timeOffsetSec - MAX_CHART_WINDOW_SECONDS
        while (chartDataPoints.isNotEmpty() && chartDataPoints.first().timeOffsetSec < windowStart) {
            chartDataPoints.removeFirst()
            chartXValues.removeFirst()
            chartYValues.removeFirst()
        }
        if (chartDataPoints.size >= MAX_CHART_POINTS) {
            chartDataPoints.removeFirst()
            chartXValues.removeFirst()
            chartYValues.removeFirst()
        }
        chartDataPoints.add(point)
        chartXValues.add((point.timeOffsetSec * 1000).toLong().toDouble())
        chartYValues.add(point.heartRate.toDouble())
    }

    /**
     * TRIM 内存预警时释放图表缓存（由 [BleService] 的 FairMemoryReceiver 回调触发）。
     *
     * - [FairMemoryReceiver.NOTIFY_TYPE_PSS]（物理内存异常）：文档指出"先查杀再通知"，
     *   紧急度最高，清空整个图表缓存（数据已 Room 持久化，可恢复）。
     * - [FairMemoryReceiver.NOTIFY_TYPE_HEAP]（Java 堆异常）：降采样到 [TRIM_KEEP_POINTS]
     *   保留最近图表数据以维持当前会话体验；无论缓存是否为空都在后台线程触发 GC
     *   （GC 对堆异常直接有效）。
     *
     * 数据操作部分通过 @Synchronized 保证线程安全；
     * GC 启动在锁外执行，避免在持锁状态下做协程调度。
     */
    fun releaseOnTrim(notifyType: Int) {
        val isPss = notifyType == FairMemoryReceiver.NOTIFY_TYPE_PSS
        trimData(isPss)
        // HEAP 异常时在后台线程触发 GC，避免 System.gc() 暂停主线程。
        // 在锁外启动：锁只保护数据操作，不护送协程调度。
        if (!isPss) {
            scope.launch(Dispatchers.Default) { System.gc() }
        }
    }

    @Synchronized
    private fun trimData(isPss: Boolean) {
        // PSS 异常时取消待发布的节流定时器，防止清空后定时器又发布陈旧快照
        if (isPss) cancelSnapshotJob()
        if (chartDataPoints.isNotEmpty()) {
            val originalSize = chartDataPoints.size
            if (isPss) {
                // 物理内存异常：清空整个图表缓存（数据已 Room 持久化，可恢复）
                chartDataPoints.clear()
                chartXValues.clear()
                chartYValues.clear()
                _chartDataSnapshot.value = null
                Log.i(TAG, "TRIM(PSS): 清空图表缓存 $originalSize 点")
            } else if (originalSize > TRIM_KEEP_POINTS) {
                // Java 堆异常：降采样保留最近 N 点，gc 对堆直接有效
                val kept = chartDataPoints.takeLast(TRIM_KEEP_POINTS)
                chartDataPoints.clear()
                chartDataPoints.addAll(kept)
                chartXValues.clear()
                chartYValues.clear()
                kept.forEach { p ->
                    chartXValues.add((p.timeOffsetSec * 1000).toLong().toDouble())
                    chartYValues.add(p.heartRate.toDouble())
                }
                var trimMax = 0.0
                var trimMin = Double.MAX_VALUE
                for (y in chartYValues) {
                    if (y > trimMax) trimMax = y
                    if (y < trimMin) trimMin = y
                }
                _chartDataSnapshot.value = ChartDataSnapshot(
                    xValues = chartXValues.toImmutableList(),
                    yValues = chartYValues.toImmutableList(),
                    windowMaxY = if (trimMax > 0.0) trimMax else 0.0,
                    windowMinY = if (trimMin < Double.MAX_VALUE && trimMin > 0.0) trimMin else 0.0
                )
                Log.i(TAG, "TRIM(HEAP): 图表降采样 $originalSize -> 保留最近 $TRIM_KEEP_POINTS 点")
            }
        }
    }
}
