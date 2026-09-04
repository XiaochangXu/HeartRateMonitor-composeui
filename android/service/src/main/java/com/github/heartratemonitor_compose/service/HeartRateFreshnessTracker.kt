package com.github.heartratemonitor_compose.service

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 按"包到达时间"判定新鲜度，与数值是否变化无关：静坐时心率恒定但包持续到达，不会误判。
 *
 * ⚠️ 反直觉设计：手表测量失败大多只停推通知，BLE 连接仍保持——无过期机制将停留旧值。
 */
enum class HeartRateFreshness {
    FRESH,

    /** 一级超时：疑似停发。预警应暂停判定，UI 仍显示最后值（不闪 --） */
    SUSPECT,

    /** 二级超时：基本确认测量失败。BleService 将心率清零，全链路显示 -- */
    STALE
}

/**
 * EWMA 平滑估算包到达间隔，超时阈值随设备实际推送频率自适应。
 *
 * ⚠️ 反直觉设计：少数低端设备"值不变就不发包"，一级只暂停预警不清零显示，避免误判后数字消失。
 */
class HeartRateFreshnessTracker(
    private val scope: CoroutineScope,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private val _freshness = MutableStateFlow(HeartRateFreshness.FRESH)
    val freshness: StateFlow<HeartRateFreshness> = _freshness.asStateFlow()

    @Volatile
    private var lastPacketRealtimeMs = 0L

    @Volatile
    private var intervalEwmaMs = INITIAL_INTERVAL_MS
    @Volatile
    private var watchdogJob: Job? = null

    /**
     * ⚠️ 反直觉设计：后台挂起恢复后首包间隔异常大，clamp 后再进 EWMA 防止单次毛刺污染估算。
     */
    fun onPacket() {
        val now = clock()
        val last = lastPacketRealtimeMs
        lastPacketRealtimeMs = now
        if (last > 0L) {
            val interval = (now - last).toFloat().coerceIn(RAW_INTERVAL_MIN_MS, RAW_INTERVAL_MAX_MS)
            intervalEwmaMs = intervalEwmaMs * (1f - EWMA_ALPHA) + interval * EWMA_ALPHA
        }
        _freshness.value = HeartRateFreshness.FRESH
        restartWatchdog()
    }

    fun reset() {
        watchdogJob?.cancel()
        watchdogJob = null
        lastPacketRealtimeMs = 0L
        intervalEwmaMs = INITIAL_INTERVAL_MS
        _freshness.value = HeartRateFreshness.FRESH
    }

    private fun restartWatchdog() {
        watchdogJob?.cancel()
        val stage1 = stage1TimeoutMs()
        watchdogJob = scope.launch {
            delay(stage1)
            _freshness.value = HeartRateFreshness.SUSPECT
            delay(stage1 * (STAGE2_MULTIPLIER - 1))
            _freshness.value = HeartRateFreshness.STALE
        }
    }

    @VisibleForTesting
    internal fun stage1TimeoutMs(): Long =
        (intervalEwmaMs * STAGE1_MULTIPLIER).toLong().coerceIn(STAGE1_MIN_MS, STAGE1_MAX_MS)

    companion object {
        /** EWMA 平滑系数：新样本权重，越小越平滑 */
        private const val EWMA_ALPHA = 0.2f

        /** 初始估算间隔：按主流手表 1Hz 假设 */
        private const val INITIAL_INTERVAL_MS = 1000f

        /** 原始间隔采样钳制范围：滤掉系统挂起恢复后的异常大间隔与毫秒级毛刺 */
        private const val RAW_INTERVAL_MIN_MS = 50f
        private const val RAW_INTERVAL_MAX_MS = 15_000f

        /** 一级超时 = EWMA × 5（容忍连续丢 5 包），下限 5s 兜住首包前与极端抖动 */
        private const val STAGE1_MULTIPLIER = 5f
        private const val STAGE1_MIN_MS = 5_000L
        private const val STAGE1_MAX_MS = 30_000L

        /** 二级超时 = 一级 × 3：再观察两个一级周期仍无包，确认测量失败 */
        private const val STAGE2_MULTIPLIER = 3
    }
}
