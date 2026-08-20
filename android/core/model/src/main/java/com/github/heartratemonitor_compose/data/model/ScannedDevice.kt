package com.github.heartratemonitor_compose.data.model

/**
 * 扫描设备 UI 模型。
 *
 * 从 com.juul.kable.Advertisement 映射而来的稳定 data class，
 * 只提取 UI 需要的三个字段（identifier / name / rssi）。
 * 全部由 stable 基本类型组成，Compose 编译器可自动推断为 stable，
 * 避免第三方 Advertisement 类型不稳定导致的无效重组。
 *
 * @param identifier 设备唯一标识（MAC 地址或系统分配的 ID）
 * @param name       设备广播名称，可能为 null
 * @param rssi       信号强度（dBm）
 */
data class ScannedDevice(
    val identifier: String,
    val name: String?,
    val rssi: Int
)
