package com.github.heartratemonitor_compose.ui.main

import android.util.Log
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HeartRatePoint(
    val timeOffsetSec: Float,
    val heartRate: Float
)

/**
 * 避免 UI 层每次心跳都执行 timeOffsetSec->ms 与 Float->Double 的全量转换。
 * [windowMaxY] / [windowMinY] 为当前 60 秒可视窗口内的极值，
 * 由 ChartDataManager 在发布快照时一并计算，避免 UI 层重复遍历 yValues。
 */
data class ChartDataSnapshot(
    val xValues: ImmutableList<Double>,
    val yValues: ImmutableList<Double>,
    val windowMaxY: Double = 0.0,
    val windowMinY: Double = 0.0
)

/**
 * RR-Interval → HeartRatePoint → 滑动窗口管理 → ChartDataSnapshot 发布，
 * 以及本次连接的心率极值跟踪与公平运行内存 TRIM 时的缓存释放。
 * 所有可变状态必须且只能在主线程访问。
 */
class ChartDataManager(private val scope: CoroutineScope) {

    private val _chartDataSnapshot = MutableStateFlow<ChartDataSnapshot?>(null)
    val chartDataSnapshot: StateFlow<ChartDataSnapshot?> = _chartDataSnapshot.asStateFlow()

    // 必须在外部首次回调前构造完成：历史记录开关关闭时 collect 会立即触发 clear()
    private val _sessionMaxHr = MutableStateFlow(0)
    val sessionMaxHr: StateFlow<Int> = _sessionMaxHr.asStateFlow()

    private val _sessionMinHr = MutableStateFlow(0)
    val sessionMinHr: StateFlow<Int> = _sessionMinHr.asStateFlow()

    // --- 内部管道状态 ---
    private var chartStartTime = 0L
    private val chartDataPoints = ArrayDeque<HeartRatePoint>()
    // 与 chartDataPoints 同步维护的已格式化 Vico 坐标列表，避免 UI 层每拍全量转换
    private val chartXValues = ArrayDeque<Double>()
    private val chartYValues = ArrayDeque<Double>()

    // RR-Interval 累加时间戳:逐拍数据按 RR 秒数累加,得到每个心跳的相对时间 (秒)
    private var lastChartTimeSec = 0f

    /** 历史记录开关状态：关闭时不累积图表数据（由 [MainViewModel] 同步设置） */
    var isHistoryEnabled = false

    /** 连接状态：仅 CONNECTED 时处理测量数据（由 [MainViewModel] 同步设置） */
    var isConnected = false

    private companion object {
        const val TAG = "ChartDataManager"
        const val MAX_CHART_POINTS = 10000

        /**
         * 快照发布节流间隔：心率包 ~1Hz，500ms 节流将 UI 重组 + Vico 重建频率减半，
         * 最大延迟 500ms 对用户不可感知（心率数字不受节流，仍即时更新）。
         */
        const val SNAPSHOT_THROTTLE_MS = 500L

        /**
         * 首页实时图表只保留最近 N 秒的数据，避免长时间连接后内存和渲染开销线性增长。
         * 超过该窗口的旧点会随新点到达被移除；完整历史数据仍由 Room 持久化（历史记录开启时）。
         */
        const val MAX_CHART_WINDOW_SECONDS = 60f

        /**
         * TRIM 内存预警时图表降采样后保留的最近点数。
         * 心率原始数据已持久化到 Room，内存中的图表缓存可安全降采样。
         */
        const val TRIM_KEEP_POINTS = 500
    }

    /**
     * 标记自上次快照发布以来是否有新数据追加，配合 [snapshotJob] 做 500ms 节流。
     */
    private var hasPendingSnapshot = false

    /**
     * 节流定时器：500ms 内只发布一次快照，避免每个心率包都触发 UI 全量重组 + Vico 重建 series。
     */
    private var snapshotJob: kotlinx.coroutines.Job? = null

    /**
     * 处理一次完整心率测量：跟踪 MAX/MIN，并在历史记录开启时把 RR/bpm 转为图表点。
     * 仅在主线程调用。
     */
    fun onMeasurement(measurement: HeartRateMeasurement) {
        if (!isConnected) return

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
        if (!isHistoryEnabled) return

        // 防御竞态：状态流通知与数据流到达之间可能存在窗口，确保 chartStartTime 已初始化
        if (chartStartTime == 0L) {
            chartStartTime = System.currentTimeMillis()
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
        } else {
            // 设备不支持 RR:回退到 bpm + 墙钟时间戳,同步 lastChartTimeSec
            val timeDiffSeconds = (System.currentTimeMillis() - chartStartTime) / 1000f
            appendPoint(HeartRatePoint(timeDiffSeconds, measurement.bpm.toFloat()))
            lastChartTimeSec = timeDiffSeconds
            appended = true
        }

        if (appended) {
            // 首次数据到达时立即发布快照，让 UI 从 loading 切到图表显示；
            // 后续更新走 500ms 节流，避免每个心率包都触发 UI 全量重组 + Vico 重建 series。
            if (_chartDataSnapshot.value == null) {
                publishSnapshot()
            } else {
                scheduleSnapshotPublish()
            }
        }
    }

    /**
     * 500ms 节流发布：同一时间窗口内多个心率包只触发一次 UI 更新。
     * 心率数据照常追加到 [chartXValues] / [chartYValues]，定时器到期后发布最新快照。
     */
    private fun scheduleSnapshotPublish() {
        hasPendingSnapshot = true
        if (snapshotJob?.isActive == true) return
        snapshotJob = scope.launch {
            kotlinx.coroutines.delay(SNAPSHOT_THROTTLE_MS)
            if (hasPendingSnapshot) {
                hasPendingSnapshot = false
                publishSnapshot()
            }
        }
    }

    /**
     * 将当前 [chartXValues] / [chartYValues] 发布为 [ChartDataSnapshot]。
     * 窗口极值在发布时一并计算，避免 UI 层每次重组都重复遍历 yValues。
     */
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

    /**
     * 取消待发布的节流定时器。reset / clear / releaseOnTrim 等即时清空场景调用，
     * 防止定时器到期后发布已被清空的陈旧快照。
     */
    private fun cancelSnapshotJob() {
        snapshotJob?.cancel()
        snapshotJob = null
        hasPendingSnapshot = false
    }

    /**
     * 新会话开始：重置时间基准与全部缓存，并清零 MAX/MIN。
     * 在连接建立（状态转为 CONNECTED）或开启历史记录时调用。仅在主线程调用。
     */
    fun reset() {
        cancelSnapshotJob()
        chartStartTime = System.currentTimeMillis()
        chartDataPoints.clear()
        // 兜底：清空 X/Y 双端队列与 snapshot，防止断开事件丢失时重连残留旧数据
        chartXValues.clear()
        chartYValues.clear()
        _chartDataSnapshot.value = null
        lastChartTimeSec = 0f
        _sessionMaxHr.value = 0
        _sessionMinHr.value = 0
    }

    /**
     * 清零本次连接的心率极值。断开连接时调用，使 UI 立即回落为 "--"；
     * 新会话开始时的清零由 [reset] 负责。仅在主线程调用。
     */
    fun resetSessionExtremes() {
        _sessionMaxHr.value = 0
        _sessionMinHr.value = 0
    }

    /**
     * 清空当前会话的图表缓存。
     * 用于断开连接或关闭历史记录时立即重置首页图表，
     * 不重置 MAX/MIN（MAX/MIN 由 [reset] 在新会话开始时独立清零）。
     * 仅在主线程调用。
     */
    fun clear() {
        cancelSnapshotJob()
        chartDataPoints.clear()
        chartXValues.clear()
        chartYValues.clear()
        chartStartTime = 0L
        lastChartTimeSec = 0f
        _chartDataSnapshot.value = null
    }

    /**
     * TRIM 内存预警时释放图表缓存（由公平运行内存 TRIM 广播触发）。仅在主线程调用。
     *
     * - [FairMemoryReceiver.NOTIFY_TYPE_PSS]（物理内存异常）：文档指出"先查杀再通知"，
     *   紧急度最高，清空整个图表缓存（数据已 Room 持久化，可恢复）。
     * - [FairMemoryReceiver.NOTIFY_TYPE_HEAP]（Java 堆异常）：降采样到 [TRIM_KEEP_POINTS]
     *   保留最近图表数据以维持当前会话体验；无论缓存是否为空都在后台线程触发 GC
     *   （与原实现一致，GC 对堆异常直接有效）。
     */
    fun releaseOnTrim(notifyType: Int) {
        val isPss = notifyType == FairMemoryReceiver.NOTIFY_TYPE_PSS
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
        // HEAP 异常时在后台线程触发 GC，避免 System.gc() 暂停主线程
        if (!isPss) {
            scope.launch(Dispatchers.Default) { System.gc() }
        }
    }

    private fun appendPoint(point: HeartRatePoint) {
        // 维护最近 60 秒可视窗口，避免长时间连接后 chartDataPoints 线性膨胀导致
        // 主线程扫描/拷贝开销增长（以及 Vico 全量重建 series 的卡顿）。
        val windowStart = point.timeOffsetSec - MAX_CHART_WINDOW_SECONDS
        while (chartDataPoints.isNotEmpty() && chartDataPoints.first().timeOffsetSec < windowStart) {
            chartDataPoints.removeFirst()
            chartXValues.removeFirst()
            chartYValues.removeFirst()
        }
        // 兜底：异常时间戳/极端频率下仍不突破硬上限
        if (chartDataPoints.size >= MAX_CHART_POINTS) {
            chartDataPoints.removeFirst()
            chartXValues.removeFirst()
            chartYValues.removeFirst()
        }
        chartDataPoints.add(point)
        chartXValues.add((point.timeOffsetSec * 1000).toLong().toDouble())
        chartYValues.add(point.heartRate.toDouble())
    }
}
