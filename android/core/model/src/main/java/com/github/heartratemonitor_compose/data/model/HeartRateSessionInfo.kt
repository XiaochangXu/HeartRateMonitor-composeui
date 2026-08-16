package com.github.heartratemonitor_compose.data.model

/**
 * 心率会话 Domain Model。
 *
 * 与 Room Entity [HeartRateSession] 字段一一对应，
 * 供 UI/ViewModel 层使用，隔离 Room schema 变更对上层的影响。
 */
data class HeartRateSessionInfo(
    val id: Long,
    val deviceName: String,
    val startTime: Long,
    val endTime: Long?
)

data class SessionStatsInfo(
    val sessionId: Long,
    val recordCount: Int,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val minHeartRate: Int?,
    val firstTimestamp: Long?,
    val lastTimestamp: Long?
)
