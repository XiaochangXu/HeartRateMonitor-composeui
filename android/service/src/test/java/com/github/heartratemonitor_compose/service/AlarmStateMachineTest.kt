package com.github.heartratemonitor_compose.service

import com.github.heartratemonitor_compose.service.posture.PostureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [AlarmStateMachine] 单元测试。
 *
 * 验证：
 * - 阈值判定（高限/低限越界）
 * - 持续时间累积（未达持续时间不触发）
 * - 冷却逻辑（报警后冷却期内不重复触发）
 * - 姿态排除（非静止姿态跳过检测）
 * - 阈值倒置保护（highThreshold <= lowThreshold 时跳过）
 * - 阈值与冷却时间动态更新
 */
class AlarmStateMachineTest {

    private data class AlarmEvent(
        val rate: Int,
        val isHigh: Boolean,
        val posture: PostureType,
        val threshold: Int
    )

    private val triggeredAlarms = mutableListOf<AlarmEvent>()
    private lateinit var stateMachine: AlarmStateMachine

    @Before
    fun setup() {
        triggeredAlarms.clear()
        stateMachine = AlarmStateMachine(
            highThreshold = 100,
            lowThreshold = 50,
            durationMs = 10_000L,   // 10 秒持续越界才触发
            cooldownMs = 60_000L,   // 60 秒冷却
            onAlarmTriggered = { rate, isHigh, posture, threshold ->
                triggeredAlarms.add(AlarmEvent(rate, isHigh, posture, threshold))
            }
        )
    }

    // ── 阈值倒置保护 ──

    @Test
    fun `threshold inversion skips detection`() {
        val sm = AlarmStateMachine(
            highThreshold = 50,
            lowThreshold = 100,
            durationMs = 1000L,
            onAlarmTriggered = { _, _, _, _ -> triggeredAlarms.add(AlarmEvent(0, false, PostureType.UNKNOWN, 0)) }
        )
        sm.onHeartRate(200, PostureType.SITTING, now = 0)
        sm.onHeartRate(200, PostureType.SITTING, now = 2000)
        assertTrue(triggeredAlarms.isEmpty())
    }

    @Test
    fun `equal thresholds skip detection`() {
        val sm = AlarmStateMachine(
            highThreshold = 100,
            lowThreshold = 100,
            durationMs = 1000L,
            onAlarmTriggered = { _, _, _, _ -> triggeredAlarms.add(AlarmEvent(0, false, PostureType.UNKNOWN, 0)) }
        )
        sm.onHeartRate(200, PostureType.SITTING, now = 0)
        sm.onHeartRate(200, PostureType.SITTING, now = 2000)
        assertTrue(triggeredAlarms.isEmpty())
    }

    // ── 高限检测 ──

    @Test
    fun `high threshold breach triggers alarm after duration`() {
        // 心率 120 > highThreshold 100，持续 10 秒后触发
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 0)
        // 未达持续时间，不触发
        assertTrue(triggeredAlarms.isEmpty())

        stateMachine.onHeartRate(120, PostureType.SITTING, now = 5_000)
        assertTrue(triggeredAlarms.isEmpty())

        // 达到持续时间，触发
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 10_000)
        assertEquals(1, triggeredAlarms.size)
        val alarm = triggeredAlarms[0]
        assertEquals(120, alarm.rate)
        assertTrue(alarm.isHigh)
        assertEquals(PostureType.SITTING, alarm.posture)
        assertEquals(100, alarm.threshold)
    }

    @Test
    fun `high threshold breach resets when rate returns to normal`() {
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 5_000)
        // 心率恢复正常，重置越界计时
        stateMachine.onHeartRate(80, PostureType.SITTING, now = 6_000)
        // 再次越界，但持续时间从 0 开始
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 7_000)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 17_000)
        // 从 7000 开始计 10 秒 = 17000 触发
        assertEquals(1, triggeredAlarms.size)
        assertTrue(triggeredAlarms[0].isHigh)
    }

    @Test
    fun `rate equal to high threshold does not trigger`() {
        // rate > highThreshold 是严格大于
        stateMachine.onHeartRate(100, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(100, PostureType.SITTING, now = 20_000)
        assertTrue(triggeredAlarms.isEmpty())
    }

    // ── 低限检测 ──

    @Test
    fun `low threshold breach triggers alarm after duration`() {
        stateMachine.onHeartRate(40, PostureType.STANDING, now = 0)
        assertTrue(triggeredAlarms.isEmpty())

        stateMachine.onHeartRate(40, PostureType.STANDING, now = 10_000)
        assertEquals(1, triggeredAlarms.size)
        val alarm = triggeredAlarms[0]
        assertEquals(40, alarm.rate)
        assertFalse(alarm.isHigh)
        assertEquals(PostureType.STANDING, alarm.posture)
        assertEquals(50, alarm.threshold)
    }

    @Test
    fun `rate equal to low threshold does not trigger`() {
        stateMachine.onHeartRate(50, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(50, PostureType.SITTING, now = 20_000)
        assertTrue(triggeredAlarms.isEmpty())
    }

    // ── 冷却逻辑 ──

    @Test
    fun `cooldown prevents repeated alarms`() {
        // 触发第一次报警
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 10_000)
        assertEquals(1, triggeredAlarms.size)

        // 冷却期内（60 秒），即使持续越界也不触发
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 15_000)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 70_000)
        assertEquals(1, triggeredAlarms.size)

        // 冷却期过后，重新触发
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 71_000)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 81_000)
        assertEquals(2, triggeredAlarms.size)
    }

    @Test
    fun `custom cooldown via updateCooldown`() {
        stateMachine.updateCooldown(5_000L)  // 5 秒冷却

        stateMachine.onHeartRate(120, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 10_000)
        assertEquals(1, triggeredAlarms.size)

        // 5 秒冷却过后即可再次触发
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 16_000)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 26_000)
        assertEquals(2, triggeredAlarms.size)
    }

    // ── 姿态排除 ──

    @Test
    fun `exercise posture does not trigger alarm`() {
        stateMachine.onHeartRate(200, PostureType.EXERCISE, now = 0)
        stateMachine.onHeartRate(200, PostureType.EXERCISE, now = 100_000)
        assertTrue(triggeredAlarms.isEmpty())
    }

    @Test
    fun `unknown posture does not trigger alarm`() {
        stateMachine.onHeartRate(200, PostureType.UNKNOWN, now = 0)
        stateMachine.onHeartRate(200, PostureType.UNKNOWN, now = 100_000)
        assertTrue(triggeredAlarms.isEmpty())
    }

    @Test
    fun `sitting posture triggers alarm`() {
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 10_000)
        assertEquals(1, triggeredAlarms.size)
    }

    @Test
    fun `standing posture triggers alarm`() {
        stateMachine.onHeartRate(120, PostureType.STANDING, now = 0)
        stateMachine.onHeartRate(120, PostureType.STANDING, now = 10_000)
        assertEquals(1, triggeredAlarms.size)
    }

    @Test
    fun `posture change resets breach timer`() {
        // 静坐越界 5 秒
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 5_000)
        // 切换到运动姿态，重置越界计时
        stateMachine.onHeartRate(120, PostureType.EXERCISE, now = 6_000)
        // 切回静坐，重新累积
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 7_000)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 17_000)
        assertEquals(1, triggeredAlarms.size)
    }

    // ── 阈值动态更新 ──

    @Test
    fun `updateThresholds changes alarm boundaries`() {
        stateMachine.onHeartRate(90, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(90, PostureType.SITTING, now = 10_000)
        // 90 < 100，不触发
        assertTrue(triggeredAlarms.isEmpty())

        // 更新阈值为 80
        stateMachine.updateThresholds(high = 80, low = 40, durationSec = 5)
        stateMachine.onHeartRate(90, PostureType.SITTING, now = 11_000)
        stateMachine.onHeartRate(90, PostureType.SITTING, now = 16_000)
        // 90 > 80，持续 5 秒后触发
        assertEquals(1, triggeredAlarms.size)
        assertEquals(80, triggeredAlarms[0].threshold)
    }

    @Test
    fun `updateThresholds updates duration`() {
        stateMachine.updateThresholds(high = 100, low = 50, durationSec = 3)

        stateMachine.onHeartRate(120, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 3_000)
        assertEquals(1, triggeredAlarms.size)
    }

    // ── 默认冷却时间常量 ──

    @Test
    fun `DEFAULT_COOLDOWN_MS is 60 seconds`() {
        assertEquals(60_000L, AlarmStateMachine.DEFAULT_COOLDOWN_MS)
    }

    // ── 低限和高限互不干扰 ──

    @Test
    fun `low and high breach do not interfere`() {
        // 低限越界一半时间
        stateMachine.onHeartRate(40, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(40, PostureType.SITTING, now = 5_000)
        // 切换到高限越界
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 6_000)
        stateMachine.onHeartRate(120, PostureType.SITTING, now = 16_000)
        // 高限从 6000 开始累积 10 秒 = 16000 触发
        assertEquals(1, triggeredAlarms.size)
        assertTrue(triggeredAlarms[0].isHigh)
    }

    @Test
    fun `no alarm when rate is within normal range`() {
        stateMachine.onHeartRate(75, PostureType.SITTING, now = 0)
        stateMachine.onHeartRate(80, PostureType.SITTING, now = 5_000)
        stateMachine.onHeartRate(60, PostureType.SITTING, now = 10_000)
        stateMachine.onHeartRate(90, PostureType.SITTING, now = 15_000)
        assertTrue(triggeredAlarms.isEmpty())
    }
}
