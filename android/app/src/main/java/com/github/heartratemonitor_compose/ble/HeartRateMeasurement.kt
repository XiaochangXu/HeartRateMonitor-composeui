package com.github.heartratemonitor_compose.ble

/**
 * 心率测量特征值 (0x2A37) 解析结果。
 *
 * @param bpm 
 * @param rrIntervals 
 * @param sensorContactSupported 
 * @param sensorContact 
 * @param energyExpended 
 */
data class HeartRateMeasurement(
    val bpm: Int,
    val rrIntervals: List<Float>,
    val sensorContactSupported: Boolean,
    val sensorContact: Boolean,
    val energyExpended: Int?
) {
    companion object {
        val EMPTY = HeartRateMeasurement(
            bpm = 0,
            rrIntervals = emptyList(),
            sensorContactSupported = false,
            sensorContact = false,
            energyExpended = null
        )
    }
}
