package com.github.heartratemonitor_compose.service

import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [SessionChartTracker] 单元测试。
 *
 * 验证：
 * - 历史记录开关关闭期间：不统计图表点、不发布快照，但 MAX/MIN 极值持续跟踪
 * - 中途开启开关：图表从零开始绘制（时间基准未被关闭期间的数据污染）
 * - 开关开启时：正常统计并发布快照（首点立即发布，后续 500ms 节流）
 * - clear() 清空图表缓存但不清零极值
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionChartTrackerTest {

    /** 可变开关：模拟用户中途切换历史记录设置 */
    private var historyEnabled = false

    private fun kotlinx.coroutines.test.TestScope.newTracker() =
        SessionChartTracker(backgroundScope) { historyEnabled }

    private fun measurement(bpm: Int, rr: Float = 1f) = HeartRateMeasurement(
        bpm = bpm,
        rrIntervals = listOf(rr),
        sensorContactSupported = false,
        sensorContact = false,
        energyExpended = null
    )

    /** 无 RR-Interval 设备的心率包：走墙钟回退路径 */
    private fun bpmOnlyMeasurement(bpm: Int) = HeartRateMeasurement(
        bpm = bpm,
        rrIntervals = emptyList(),
        sensorContactSupported = false,
        sensorContact = false,
        energyExpended = null
    )

    @Test
    fun `disabled history tracks extremes but not chart`() = runTest {
        val tracker = newTracker()
        tracker.onMeasurement(measurement(bpm = 80))
        tracker.onMeasurement(measurement(bpm = 120))
        tracker.onMeasurement(measurement(bpm = 60))

        assertNull("关闭历史记录时不得发布图表快照", tracker.chartDataSnapshot.value)
        assertEquals(120, tracker.sessionMaxHr.value)
        assertEquals(60, tracker.sessionMinHr.value)
    }

    @Test
    fun `enabling history mid-session starts chart from zero`() = runTest {
        val tracker = newTracker()

        // 关闭期间累积 5 个心跳（若被统计，时间基准应达 5s）
        repeat(5) { tracker.onMeasurement(measurement(bpm = 90, rr = 1f)) }
        assertNull(tracker.chartDataSnapshot.value)

        // 中途开启：首个数据点时间基准从 0 重新累加（1s → 1000ms）
        historyEnabled = true
        tracker.onMeasurement(measurement(bpm = 100, rr = 1f))

        val snapshot = tracker.chartDataSnapshot.value
        assertEquals(1, snapshot?.xValues?.size)
        assertEquals(1000.0, snapshot?.xValues?.first())
        // RR=1s 的逐拍瞬时心率为 60/1=60，图表 Y 轴取逐拍值而非包内 bpm
        assertEquals(60.0, snapshot?.yValues?.first())

        // 极值跨越开关切换保持全程跟踪
        assertEquals(100, tracker.sessionMaxHr.value)
        assertEquals(90, tracker.sessionMinHr.value)
    }

    @Test
    fun `enabled history appends points and throttles publishes`() = runTest {
        val tracker = newTracker()
        historyEnabled = true

        // 首个数据点：快照为 null 时立即发布
        tracker.onMeasurement(measurement(bpm = 70, rr = 1f))
        assertEquals(1, tracker.chartDataSnapshot.value?.xValues?.size)

        // 后续数据点：500ms 节流，虚拟时钟未推进前不发布
        tracker.onMeasurement(measurement(bpm = 75, rr = 1f))
        assertEquals(1, tracker.chartDataSnapshot.value?.xValues?.size)

        // advanceTimeBy 不执行恰好在目标时刻调度的任务，需补 runCurrent()
        advanceTimeBy(500)
        runCurrent()
        assertEquals(2, tracker.chartDataSnapshot.value?.xValues?.size)
        assertEquals(2000.0, tracker.chartDataSnapshot.value?.xValues?.last())
    }

    @Test
    fun `clear resets chart cache but keeps extremes`() = runTest {
        val tracker = newTracker()
        historyEnabled = true
        tracker.onMeasurement(measurement(bpm = 110))
        tracker.clear()

        assertNull(tracker.chartDataSnapshot.value)
        assertEquals("clear 不得清零极值", 110, tracker.sessionMaxHr.value)
        assertEquals("clear 不得清零极值", 110, tracker.sessionMinHr.value)
    }

    @Test
    fun `wall-clock fallback starts from zero after enabling mid-session`() = runTest {
        val tracker = newTracker()

        // 连接建立：reset() 将 chartStartTime 定格为连接时刻的墙钟
        tracker.reset()

        // 历史记录关闭期间持续收包（无 RR 设备）
        repeat(5) { tracker.onMeasurement(bpmOnlyMeasurement(bpm = 85)) }
        assertNull(tracker.chartDataSnapshot.value)

        // 中途开启：首个数据包惰性重建 chartStartTime，首个 x 必须为 0 而非等待时长
        historyEnabled = true
        tracker.onMeasurement(bpmOnlyMeasurement(bpm = 90))

        val snapshot = tracker.chartDataSnapshot.value
        assertEquals(1, snapshot?.xValues?.size)
        assertEquals("无 RR 回退路径首个点必须从 0 秒开始", 0.0, snapshot?.xValues?.first())
        assertEquals(90.0, snapshot?.yValues?.first())
    }

    @Test
    fun `contact-lost zero bpm does not create spike`() = runTest {
        val tracker = newTracker()
        historyEnabled = true
        tracker.reset()

        // 有效包建立基线
        tracker.onMeasurement(bpmOnlyMeasurement(bpm = 80))

        // 传感器接触丢失：包被置为 bpm=0、RR 清空，不得画出 y=0 尖刺
        repeat(3) { tracker.onMeasurement(bpmOnlyMeasurement(bpm = 0)) }

        // 快照不应发布新点（无点追加，不触发首点发布/节流发布）
        val snapshot = tracker.chartDataSnapshot.value
        assertEquals(1, snapshot?.xValues?.size)
        assertEquals(80.0, snapshot?.yValues?.first())

        // 恢复接触后继续追加，无零值点混入
        tracker.onMeasurement(bpmOnlyMeasurement(bpm = 82))
        advanceTimeBy(500)
        runCurrent()
        val recovered = tracker.chartDataSnapshot.value
        assertEquals(2, recovered?.xValues?.size)
        assertEquals(82.0, recovered?.yValues?.last())
    }
}
