package com.github.heartratemonitor_compose.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "favorite_devices")
data class FavoriteDeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val timestamp: Long
)
