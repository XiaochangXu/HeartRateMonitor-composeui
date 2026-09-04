package com.github.heartratemonitor_compose.data.model

// 从 kable.Advertisement 映射的稳定 data class（全 stable 基本类型 → Compose 自动推断 stable），避免无效重组。
data class ScannedDevice(
    val identifier: String,
    val name: String?,
    val rssi: Int
)
