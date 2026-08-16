package com.github.heartratemonitor_compose.data.model

/**
 * 收藏设备 Domain Model。
 *
 * 与 Room Entity [FavoriteDeviceEntity] 字段一一对应，
 * 供 UI/ViewModel 层使用，隔离 Room schema 变更对上层的影响。
 */
data class FavoriteDeviceInfo(
    val id: String,
    val name: String,
    val timestamp: Long
)
