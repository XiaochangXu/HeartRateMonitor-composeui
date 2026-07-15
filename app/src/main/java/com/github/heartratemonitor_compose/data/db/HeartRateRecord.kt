package com.github.heartratemonitor_compose.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index // 1. 导入 Index 类
import androidx.room.PrimaryKey

@Entity(
    tableName = "heart_rate_records",
    foreignKeys = [ForeignKey(
        entity = HeartRateSession::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["sessionId"])] // 2. 添加这一行来创建索引
)
data class HeartRateRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val heartRate: Int
)
