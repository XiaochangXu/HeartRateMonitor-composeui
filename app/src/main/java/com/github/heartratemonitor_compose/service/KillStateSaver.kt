package com.github.heartratemonitor_compose.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.github.heartratemonitor_compose.data.PrefsKeys

/**
 * KILL 广播现场状态保存与恢复。
 *
 * 当 [FairMemoryReceiver] 收到 ACTION_KILL 时，系统留给应用的时间窗口很短，
 * 必须立即把当前关键 UI 状态写入 SharedPreferences，以便下次启动时恢复体验。
 *
 * 保存内容：当前路由、Tab、全屏状态、已连接设备信息。
 */
object KillStateSaver {

    private const val TAG = "KillStateSaver"

    /** 当前应用状态快照，由 AppRoot 在组合生命周期内持续更新。 */
    @Volatile
    var currentSnapshot: Snapshot = Snapshot()
        private set

    data class Snapshot(
        val route: String = "",
        val tab: String = "",
        val isFullScreen: Boolean = false,
        val connectedDeviceId: String? = null,
        val connectedDeviceName: String? = null
    )

    /**
     * 更新当前快照。由 AppRoot 在关键状态变化时调用。
     */
    fun updateSnapshot(snapshot: Snapshot) {
        currentSnapshot = snapshot
    }

    /**
     * 收到 KILL 广播时立即持久化当前快照。
     */
    fun save(context: Context) {
        try {
            val snapshot = currentSnapshot
            val prefs = context.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean(PrefsKeys.KILL_STATE_SAVED, true)
                putString(PrefsKeys.KILL_STATE_ROUTE, snapshot.route)
                putString(PrefsKeys.KILL_STATE_TAB, snapshot.tab)
                putBoolean(PrefsKeys.KILL_STATE_FULLSCREEN, snapshot.isFullScreen)
                putString(PrefsKeys.KILL_STATE_CONNECTED_DEVICE_ID, snapshot.connectedDeviceId)
                putString(PrefsKeys.KILL_STATE_CONNECTED_DEVICE_NAME, snapshot.connectedDeviceName)
                putLong(PrefsKeys.KILL_STATE_TIMESTAMP, System.currentTimeMillis())
                apply()
            }
            Log.i(TAG, "KILL 现场已保存: route=${snapshot.route}, tab=${snapshot.tab}, " +
                    "fullscreen=${snapshot.isFullScreen}, device=${snapshot.connectedDeviceId}")
        } catch (e: Exception) {
            Log.e(TAG, "保存 KILL 现场失败", e)
        }
    }

    /**
     * 应用启动时读取上次 KILL 保存的现场。
     * 返回 null 表示没有待恢复的现场或已过期（超过 5 分钟）。
     */
    fun read(context: Context): Snapshot? {
        val prefs = context.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PrefsKeys.KILL_STATE_SAVED, false)) return null

        val timestamp = prefs.getLong(PrefsKeys.KILL_STATE_TIMESTAMP, 0L)
        val elapsed = System.currentTimeMillis() - timestamp
        if (elapsed > KILL_STATE_VALIDITY_MS) {
            clear(prefs)
            return null
        }

        return Snapshot(
            route = prefs.getString(PrefsKeys.KILL_STATE_ROUTE, "") ?: "",
            tab = prefs.getString(PrefsKeys.KILL_STATE_TAB, "") ?: "",
            isFullScreen = prefs.getBoolean(PrefsKeys.KILL_STATE_FULLSCREEN, false),
            connectedDeviceId = prefs.getString(PrefsKeys.KILL_STATE_CONNECTED_DEVICE_ID, null),
            connectedDeviceName = prefs.getString(PrefsKeys.KILL_STATE_CONNECTED_DEVICE_NAME, null)
        )
    }

    /**
     * 现场恢复后清除，避免重复恢复。
     */
    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
        clear(prefs)
    }

    private fun clear(prefs: SharedPreferences) {
        try {
            prefs.edit().apply {
                putBoolean(PrefsKeys.KILL_STATE_SAVED, false)
                remove(PrefsKeys.KILL_STATE_ROUTE)
                remove(PrefsKeys.KILL_STATE_TAB)
                remove(PrefsKeys.KILL_STATE_FULLSCREEN)
                remove(PrefsKeys.KILL_STATE_CONNECTED_DEVICE_ID)
                remove(PrefsKeys.KILL_STATE_CONNECTED_DEVICE_NAME)
                remove(PrefsKeys.KILL_STATE_TIMESTAMP)
                apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除 KILL 现场失败", e)
        }
    }

    /** KILL 现场有效时间：5 分钟 */
    private const val KILL_STATE_VALIDITY_MS = 5 * 60 * 1000L
}
