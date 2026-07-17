package com.github.heartratemonitor_compose.ble

import android.content.Context
import androidx.annotation.StringRes
import com.github.heartratemonitor_compose.R
import com.juul.kable.Advertisement

/**
 * 新增的状态管理类，用于统一表示蓝牙连接的各种状态及其对应的UI信息。
 * 这是实现"单一事实来源"架构的核心。
 *
 * @param messageRes 要在UI上显示给用户的状态文本资源 ID（0 表示使用动态消息）。
 */
sealed class BleState(@StringRes val messageRes: Int) {
    // 空闲/初始状态
    object Idle : BleState(R.string.ble_idle)

    // 扫描状态
    object Scanning : BleState(R.string.ble_scanning)
    class ScanResults : BleState(R.string.ble_scan_results)
    class ScanFailed(val displayMessage: String) : BleState(0)

    // 连接状态
    object Connecting : BleState(R.string.ble_connecting)
    // **【修改前为AutoConnecting】** 应用启动时的自动连接
    object AutoConnecting: BleState(R.string.ble_auto_connecting)
    // **【新增状态】** 意外断开后的自动重连
    object AutoReconnecting: BleState(R.string.ble_auto_reconnecting)
    class Connected(val displayMessage: String) : BleState(0)
    class Disconnected(val displayMessage: String) : BleState(0)

    /**
     * 获取用于显示的消息字符串。静态消息使用 context 解析资源 ID，动态消息直接返回。
     */
    fun getMessage(context: Context): String {
        return if (messageRes != 0) context.getString(messageRes)
        else when (this) {
            is ScanFailed -> displayMessage
            is Connected -> displayMessage
            is Disconnected -> displayMessage
            else -> ""
        }
    }

    /**
     * 向后兼容：直接获取消息（仅适用于动态消息状态，静态状态返回空字符串）。
     * 推荐使用 [getMessage] 传入 Context。
     */
    val message: String
        get() = when {
            messageRes != 0 -> "" // 静态消息需要 Context 解析
            this is ScanFailed -> displayMessage
            this is Connected -> displayMessage
            this is Disconnected -> displayMessage
            else -> ""
        }
}
