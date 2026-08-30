package com.github.heartratemonitor_compose.data.model

import kotlinx.collections.immutable.ImmutableList

/**
 * 心率图表中的一个数据点：RR-Interval 累加时间戳 + 瞬时心率。
 *
 * 从 :feature/main 的 ChartDataManager 下沉至 :core:model，
 * 使服务层 SessionChartTracker 可持有该类型而无需依赖 feature 模块（契约 9.2）。
 */
data class HeartRatePoint(
    val timeOffsetSec: Float,
    val heartRate: Float
)

/**
 * 图表快照：已格式化的 Vico 坐标列表 + 窗口极值。
 *
 * 避免 UI 层每次心跳都执行 timeOffsetSec→ms 与 Float→Double 的全量转换。
 * [windowMaxY] / [windowMinY] 为当前 60 秒可视窗口内的极值，
 * 由 SessionChartTracker（原 ChartDataManager）在发布快照时一并计算，
 * 避免 UI 层重复遍历 yValues。
 *
 * 从 :feature/main 下沉至 :core:model，供服务层和 UI 层共享。
 */
data class ChartDataSnapshot(
    val xValues: ImmutableList<Double>,
    val yValues: ImmutableList<Double>,
    val windowMaxY: Double = 0.0,
    val windowMinY: Double = 0.0
)
