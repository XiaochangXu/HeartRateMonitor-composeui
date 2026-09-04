package com.github.heartratemonitor_compose.data.model

// HeartRateSession 的 UI/ViewModel 层投影，隔离 Room schema 变更。
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
