package com.github.heartratemonitor_compose.service

import com.github.heartratemonitor_compose.service.posture.PostureType

/**
 * 心率预警状态机。
 *
 * 用时间戳记录高/低限越界起始时刻，每次心率更新时增量判定。
 * 仅静坐/站立姿态（isStationary）触发检测；运动/未知姿态跳过。
 * 报警后进入冷却期（默认 60 秒，可由重复报警设置覆盖），期间不重复判定。
 *
 * 原为 [HeartRateAlarmService] 的内部类，阶段 3.3 提取为顶层类以降低耦合，
 * 便于单元测试（阶段 5.1）。
 *
 * @param highThreshold 高限阈值（bpm）
 * @param lowThreshold 低限阈值（bpm）
 * @param durationMs 持续越界触发报警所需的时间（毫秒）
 * @param cooldownMs 报警后冷却时间（毫秒），冷却期内不重复判定
 * @param onAlarmTriggered 报警触发回调：(rate, isHigh, posture, threshold) -> Unit
 */
class AlarmStateMachine(
    var highThreshold: Int,
    var lowThreshold: Int,
    private var durationMs: Long,
    private var cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    private val onAlarmTriggered: (rate: Int, isHigh: Boolean, posture: PostureType, threshold: Int) -> Unit = { _, _, _, _ -> }
) {
    private var highBreachStart = -1L
    private var lowBreachStart = -1L
    private var lastAlarmTime = -1L

    /**
     * 每次心率更新时调用，增量判定是否触发报警。
     *
     * @param rate 当前心率（bpm）
     * @param posture 当前姿态
     * @param now 当前时间戳（默认 System.currentTimeMillis()，可注入用于测试）
     */
    fun onHeartRate(rate: Int, posture: PostureType, now: Long = System.currentTimeMillis()) {
        // 阈值倒置校验：highThreshold 必须 > lowThreshold，否则配置无效，跳过检测
        if (highThreshold <= lowThreshold) {
            highBreachStart = -1L
            lowBreachStart = -1L
            return
        }
        // 冷却期内不判定
        if (lastAlarmTime >= 0 && now - lastAlarmTime < cooldownMs) {
            highBreachStart = -1L
            lowBreachStart = -1L
            return
        }
        // 仅静止姿态（静坐/站立）触发检测
        if (!posture.isStationary) {
            highBreachStart = -1L
            lowBreachStart = -1L
            return
        }
        // 高限检测
        if (rate > highThreshold) {
            if (highBreachStart < 0) highBreachStart = now
            if (now - highBreachStart >= durationMs) {
                onAlarmTriggered(rate, true, posture, highThreshold)
                lastAlarmTime = now
                highBreachStart = -1L
                lowBreachStart = -1L
            }
        } else {
            highBreachStart = -1L
        }
        // 低限检测
        if (rate < lowThreshold) {
            if (lowBreachStart < 0) lowBreachStart = now
            if (now - lowBreachStart >= durationMs) {
                onAlarmTriggered(rate, false, posture, lowThreshold)
                lastAlarmTime = now
                highBreachStart = -1L
                lowBreachStart = -1L
            }
        } else {
            lowBreachStart = -1L
        }
    }

    /**
     * 更新阈值与持续时间。
     *
     * @param high 高限阈值（bpm）
     * @param low 低限阈值（bpm）
     * @param durationSec 持续时间（秒）
     */
    fun updateThresholds(high: Int, low: Int, durationSec: Int) {
        highThreshold = high
        lowThreshold = low
        durationMs = durationSec.toLong() * 1000L
    }

    /**
     * 更新冷却时间。
     *
     * @param cooldownMs 冷却时间（毫秒）
     */
    fun updateCooldown(cooldownMs: Long) {
        this.cooldownMs = cooldownMs
    }

    /**
     * 重置越界计时。
     * 数据断流（手表测量失败停发包）后恢复新鲜度时调用，
     * 避免把无数据的空窗期计入越界持续时长导致误报。
     */
    fun resetBreachTimers() {
        highBreachStart = -1L
        lowBreachStart = -1L
    }

    companion object {
        /** 默认冷却时间：60 秒 */
        const val DEFAULT_COOLDOWN_MS = 60_000L
    }
}
