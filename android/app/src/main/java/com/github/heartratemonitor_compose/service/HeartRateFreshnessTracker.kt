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
 * 心率数据新鲜度状态。
 *
 * 手表测量失败（屏幕显示 --）时大多只是停止推送通知，BLE 连接仍保持，
 * App 侧若无过期机制会一直停留在失败前的旧值。本跟踪器按"包到达时间"
 * 判定新鲜度（与数值是否变化无关：静坐时心率恒定但包持续到达，不会误判）。
 */
enum class HeartRateFreshness {
    /** 数据正常到达 */
    FRESH,

    /** 一级超时：疑似停发。预警应暂停判定，UI 仍显示最后值（不闪 --） */
    SUSPECT,

    /** 二级超时：基本确认测量失败。BleService 将心率清零，全链路显示 -- */
    STALE
}

/**
 * 心率数据新鲜度跟踪器（自适应超时 + 分级降级）。
 *
 * 用 EWMA 平滑估算包到达间隔，超时阈值随设备实际推送频率自适应：
 * - 一级（SUSPECT）：max(EWMA × [STAGE1_MULTIPLIER], 5s)，上限 30s。
 *   1Hz 手表约 5s，2~3s 一包的省电型手表自动放宽。
 * - 二级（STALE）：一级阈值 × [STAGE2_MULTIPLIER]，确认失败后清零降级。
 *
 * 分级目的：少数低端设备固件存在"值不变就不发包"的省电行为，
 * 一级只暂停预警判定而不清零显示，避免此类设备被误判后数字消失。
 *
 * @param scope 看门狗协程运行作用域（传 BleService 的 serviceScope）
 * @param clock 单调时钟源，默认 SystemClock.elapsedRealtime，可注入便于单测
 */
class HeartRateFreshnessTracker(
    private val scope: CoroutineScope,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
) {

    private val _freshness = MutableStateFlow(HeartRateFreshness.FRESH)
    val freshness: StateFlow<HeartRateFreshness> = _freshness.asStateFlow()

    @Volatile
    private var lastPacketRealtimeMs = 0L

    // onPacket（IO 线程池 collect 循环）与 reset（cleanupConnection 所在线程）可能跨线程
    // 读写以下字段，@Volatile 保证可见性
    @Volatile
    private var intervalEwmaMs = INITIAL_INTERVAL_MS
    @Volatile
    private var watchdogJob: Job? = null

    /**
     * 每收到一包心率通知时调用：刷新间隔估算、恢复 FRESH 并重启看门狗。
     * 从后台挂起恢复后的首包间隔可能异常大，clamp 后再进 EWMA 防止估算被单次毛刺污染。
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

    /** 断开连接时调用：取消看门狗，状态回到 FRESH 等待下次连接 */
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

    /** 一级超时 = EWMA 间隔 × 5，clamp 到 [5s, 30s] */
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
