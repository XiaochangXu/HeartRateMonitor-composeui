package com.github.heartratemonitor_compose.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HeartRateFreshnessTracker] 单元测试。
 *
 * 验证：
 * - 分级降级时序（FRESH → SUSPECT → STALE）
 * - 收包重置看门狗（静坐心率恒定但包持续到达时不误判）
 * - reset 取消看门狗（断开连接后不再降级）
 * - 自适应阈值（慢速设备 EWMA 变大 → 一级超时放宽）与下限钳制
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeartRateFreshnessTrackerTest {

    /** 虚拟时钟：与 runTest 虚拟时间同步推进（SystemClock 在纯 JVM 单测中不可用） */
    private var virtualNow = 0L

    private fun kotlinx.coroutines.test.TestScope.newTracker() =
        HeartRateFreshnessTracker(backgroundScope) { virtualNow }

    @Test
    fun `initial freshness is FRESH`() = runTest {
        val tracker = newTracker()
        assertEquals(HeartRateFreshness.FRESH, tracker.freshness.value)
    }

    @Test
    fun `no packet after stage1 becomes SUSPECT then STALE`() = runTest {
        val tracker = newTracker()
        tracker.onPacket()

        // 初始 EWMA = 1000ms → 一级超时 = max(1000×5, 5000) = 5s
        advanceTimeBy(4_999)
        virtualNow += 4_999
        assertEquals(HeartRateFreshness.FRESH, tracker.freshness.value)
        advanceTimeBy(2)
        virtualNow += 2
        assertEquals(HeartRateFreshness.SUSPECT, tracker.freshness.value)

        // 二级 = 一级 × 3 → 第 15s 确认失败
        advanceTimeBy(10_000)
        virtualNow += 10_000
        assertEquals(HeartRateFreshness.STALE, tracker.freshness.value)
    }

    @Test
    fun `packet restarts watchdog and constant value does not trigger`() = runTest {
        val tracker = newTracker()
        tracker.onPacket()

        // 模拟静坐：心率值恒定，但每秒一包持续到达 → 永远保持 FRESH
        repeat(20) {
            advanceTimeBy(1_000)
            virtualNow += 1_000
            tracker.onPacket()
            assertEquals(HeartRateFreshness.FRESH, tracker.freshness.value)
        }

        // 最后一包后停止 → 看门狗从最后一包时刻重新计时
        advanceTimeBy(4_000)
        virtualNow += 4_000
        assertEquals(HeartRateFreshness.FRESH, tracker.freshness.value)
        advanceTimeBy(1_001)
        virtualNow += 1_001
        assertEquals(HeartRateFreshness.SUSPECT, tracker.freshness.value)
    }

    @Test
    fun `STALE recovers to FRESH when packet arrives again`() = runTest {
        val tracker = newTracker()
        tracker.onPacket()

        advanceTimeBy(15_001)
        virtualNow += 15_001
        assertEquals(HeartRateFreshness.STALE, tracker.freshness.value)

        // 手表恢复测量重新发包 → 立即回到 FRESH
        tracker.onPacket()
        assertEquals(HeartRateFreshness.FRESH, tracker.freshness.value)
    }

    @Test
    fun `reset cancels watchdog`() = runTest {
        val tracker = newTracker()
        tracker.onPacket()
        tracker.reset()

        advanceTimeBy(60_000)
        virtualNow += 60_000
        assertEquals(HeartRateFreshness.FRESH, tracker.freshness.value)
    }

    @Test
    fun `stage1 timeout adapts to slow packet interval`() = runTest {
        val tracker = newTracker()

        // 模拟 3s 一包的省电型手表，EWMA 收敛到 ~3000ms → 一级超时 ≈ 15s
        repeat(40) {
            tracker.onPacket()
            virtualNow += 3_000L
        }
        val stage1 = tracker.stage1TimeoutMs()
        assertTrue("stage1=$stage1 应自适应放宽到 ~15s", stage1 in 14_500L..15_000L)
    }

    @Test
    fun `stage1 timeout has 5s floor for fast devices`() = runTest {
        val tracker = newTracker()

        // 模拟 100ms 一包的高频设备（逐拍心率带），一级超时不应低于 5s
        repeat(40) {
            tracker.onPacket()
            virtualNow += 100L
        }
        assertEquals(5_000L, tracker.stage1TimeoutMs())
    }
}
