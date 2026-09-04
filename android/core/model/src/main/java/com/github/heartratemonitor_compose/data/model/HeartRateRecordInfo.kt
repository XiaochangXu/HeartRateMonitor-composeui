package com.github.heartratemonitor_compose.data.model

// HeartRateRecord 的 UI/ViewModel 层投影，隔离 Room schema 变更。
data class HeartRateRecordInfo(
    val id: Long,
    val sessionId: Long,
    val timestamp: Long,
    val heartRate: Int
)
