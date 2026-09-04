package com.github.heartratemonitor_compose.ble

import android.content.Context
import androidx.annotation.StringRes
import com.github.heartratemonitor_compose.service.R

/**
 * 蓝牙连接状态，「单一事实来源」核心。
 */
sealed class BleState(@param:StringRes val messageRes: Int) {
    object Idle : BleState(R.string.ble_idle)

    object Scanning : BleState(R.string.ble_scanning)
    class ScanResults : BleState(R.string.ble_scan_results)
    class ScanFailed(val displayMessage: String) : BleState(0)

    /** 蓝牙未开启，kable 抛出 UnmetRequirementException。 */
    object BluetoothDisabled : BleState(R.string.ble_bluetooth_disabled)

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
     * 向后兼容：仅适用于动态消息状态，推荐使用 [getMessage]。
     */
    val message: String
        get() = when {
            messageRes != 0 -> ""
            this is ScanFailed -> displayMessage
            this is Connected -> displayMessage
            this is Disconnected -> displayMessage
            else -> ""
        }
}
