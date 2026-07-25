package com.github.heartratemonitor_compose.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 收藏设备实体（替代原 SharedPreferences JSON 数组存储）。
 */
@Entity(tableName = "favorite_devices")
data class FavoriteDeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val timestamp: Long
)
