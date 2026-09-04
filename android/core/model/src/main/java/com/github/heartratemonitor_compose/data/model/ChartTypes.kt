package com.github.heartratemonitor_compose.data.model

import kotlinx.collections.immutable.ImmutableList

data class HeartRatePoint(
    val timeOffsetSec: Float,
    val heartRate: Float
)

// 图表快照：已格式化坐标 + 窗口极值，避免 UI 层每次心跳全量转换与重复遍历。
data class ChartDataSnapshot(
    val xValues: ImmutableList<Double>,
    val yValues: ImmutableList<Double>,
    val windowMaxY: Double = 0.0,
    val windowMinY: Double = 0.0
)
