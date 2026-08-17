package com.github.heartratemonitor_compose.data.model

/**
 * 心率记录 Domain Model。
 *
 * 与 Room Entity [HeartRateRecord] 字段一一对应，
 * 供 UI/ViewModel 层使用，隔离 Room schema 变更对上层的影响。
 */
data class HeartRateRecordInfo(
    val id: Long,
    val sessionId: Long,
    val timestamp: Long,
    val heartRate: Int
)
