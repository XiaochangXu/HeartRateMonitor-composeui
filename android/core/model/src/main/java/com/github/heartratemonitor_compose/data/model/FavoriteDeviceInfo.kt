package com.github.heartratemonitor_compose.data.model

// FavoriteDeviceEntity 的 UI/ViewModel 层投影，隔离 Room schema 变更。
data class FavoriteDeviceInfo(
    val id: String,
    val name: String,
    val timestamp: Long
)
