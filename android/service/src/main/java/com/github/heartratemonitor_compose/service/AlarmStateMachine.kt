package com.github.heartratemonitor_compose.service

import com.github.heartratemonitor_compose.service.posture.PostureType

/**
 * 原为 [HeartRateAlarmService] 的内部类，提取为顶层类以降低耦合，便于单元测试。
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

    fun onHeartRate(rate: Int, posture: PostureType, now: Long = System.currentTimeMillis()) {
        if (highThreshold <= lowThreshold) {
            highBreachStart = -1L
            lowBreachStart = -1L
            return
        }
        if (lastAlarmTime >= 0 && now - lastAlarmTime < cooldownMs) {
            highBreachStart = -1L
            lowBreachStart = -1L
            return
        }
        if (!posture.isStationary) {
            highBreachStart = -1L
            lowBreachStart = -1L
            return
        }
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

    fun updateThresholds(high: Int, low: Int, durationSec: Int) {
        highThreshold = high
        lowThreshold = low
        durationMs = durationSec.toLong() * 1000L
    }

    fun updateCooldown(cooldownMs: Long) {
        this.cooldownMs = cooldownMs
    }

    /**
     * 数据断流（手表测量失败停发包）后恢复新鲜度时调用，
     * 避免把无数据的空窗期计入越界持续时长导致误报。
     */
    fun resetBreachTimers() {
        highBreachStart = -1L
        lowBreachStart = -1L
    }

    companion object {
        const val DEFAULT_COOLDOWN_MS = 60_000L
    }
}
