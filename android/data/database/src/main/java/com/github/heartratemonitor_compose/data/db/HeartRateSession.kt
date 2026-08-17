package com.github.heartratemonitor_compose.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "heart_rate_sessions")
data class HeartRateSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val deviceName: String,
    val startTime: Long,
    var endTime: Long? = null
)

/**
 * 用于 HistoryScreen 列表预览，避免对每个 session 执行 N+1 查询加载全部记录。
 */
data class SessionStats(
    val sessionId: Long,
    val recordCount: Int,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val minHeartRate: Int?,
    val firstTimestamp: Long?,
    val lastTimestamp: Long?
)