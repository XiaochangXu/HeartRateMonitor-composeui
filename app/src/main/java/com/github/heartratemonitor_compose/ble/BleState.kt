package com.github.heartratemonitor_compose.ble

import com.juul.kable.Advertisement

/**
 * 新增的状态管理类，用于统一表示蓝牙连接的各种状态及其对应的UI信息。
 * 这是实现“单一事实来源”架构的核心。
 *
 * @param message 要在UI上显示给用户的状态文本。
 */
sealed class BleState(val message: String) {
    // 空闲/初始状态
    object Idle : BleState("点击右上角按钮扫描设备")

    // 扫描状态
    object Scanning : BleState("正在扫描设备...")
    class ScanResults(val advertisements: List<Advertisement>) : BleState("请选择要连接的设备")
    class ScanFailed(message: String) : BleState(message)

    // 连接状态
    object Connecting : BleState("正在连接...")
    // **【修改前为AutoConnecting】** 应用启动时的自动连接
    object AutoConnecting: BleState("正在尝试连接收藏的设备...")
    // **【新增状态】** 意外断开后的自动重连
    object AutoReconnecting: BleState("正在自动重连...")
    class Connected(message: String) : BleState(message)
    class Disconnected(message: String) : BleState(message)
}