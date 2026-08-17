package com.github.heartratemonitor_compose.ble

import android.content.Context
import androidx.annotation.StringRes
import com.github.heartratemonitor_compose.service.R
import com.juul.kable.Advertisement

/**
 * 统一表示蓝牙连接的各种状态及其对应的 UI 信息，
 * 这是实现“单一事实来源”架构的核心。
 */
sealed class BleState(@param:StringRes val messageRes: Int) {
    object Idle : BleState(R.string.ble_idle)

    object Scanning : BleState(R.string.ble_scanning)
    class ScanResults : BleState(R.string.ble_scan_results)
    class ScanFailed(val displayMessage: String) : BleState(0)

    object Connecting : BleState(R.string.ble_connecting)
    object AutoConnecting: BleState(R.string.ble_auto_connecting)
    object AutoReconnecting: BleState(R.string.ble_auto_reconnecting)
    class Connected(val displayMessage: String) : BleState(0)
    class Disconnected(val displayMessage: String) : BleState(0)

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
