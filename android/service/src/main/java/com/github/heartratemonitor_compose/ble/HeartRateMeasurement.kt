package com.github.heartratemonitor_compose.ble

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
