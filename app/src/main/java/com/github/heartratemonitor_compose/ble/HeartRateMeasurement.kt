package com.github.heartratemonitor_compose.ble

/**
 * 心率测量特征值 (0x2A37) 解析结果。
 *
 * @param bpm 设备上报的平均心率 (次/分)
 * @param rrIntervals 相邻 R 波峰时间间隔序列,单位秒;空表示设备不支持 RR-Interval
 * @param sensorContactSupported 传感器是否支持接触检测
 * @param sensorContact 传感器是否与皮肤接触 (仅当 [sensorContactSupported] 为 true 时有意义)
 * @param energyExpended 自会话开始累计能耗 (千卡);null 表示设备未上报
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
