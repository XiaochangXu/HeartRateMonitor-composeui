package com.github.heartratemonitor_compose.service

import android.app.Application
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 当 [FairMemoryReceiver] 收到 ACTION_KILL 时，系统留给应用的时间窗口很短，
 * 必须立即把当前关键 UI 状态写入 DataStore，以便下次启动时恢复体验。
 *
 * 写入路径直连 [settingsDataStore] 而非 SettingsRepository 的即发即忘 setter：
 * KILL 场景进程随时被杀，异步 launch 可能来不及落盘，必须 runBlocking 同步等待写完。
 *
 * Hilt 单例（Phase 2 起由 Hilt 装配，替代 AppContainer）。
 * 必须保持 [Singleton]：AppRoot（MainActivity 注入）写入 currentSnapshot，
 * MainViewModel 调用 save() 持久化——若两者拿到不同实例，save() 会持久化空快照，KILL 现场恢复失效。
 */
@Singleton
class KillStateSaver @Inject constructor(
    private val application: Application,
    private val settings: SettingsRepository
) {

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

    fun updateSnapshot(snapshot: Snapshot) {
        currentSnapshot = snapshot
    }

    fun save() {
        try {
            val snapshot = currentSnapshot
            runBlocking(Dispatchers.IO) {
                application.settingsDataStore.edit { prefs ->
                    prefs[SettingsKeys.KILL_STATE_SAVED] = true
                    prefs[SettingsKeys.KILL_STATE_ROUTE] = snapshot.route
                    prefs[SettingsKeys.KILL_STATE_TAB] = snapshot.tab
                    prefs[SettingsKeys.KILL_STATE_FULLSCREEN] = snapshot.isFullScreen
                    prefs.putOrRemove(
                        SettingsKeys.KILL_STATE_CONNECTED_DEVICE_ID,
                        snapshot.connectedDeviceId
                    )
                    prefs.putOrRemove(
                        SettingsKeys.KILL_STATE_CONNECTED_DEVICE_NAME,
                        snapshot.connectedDeviceName
                    )
                    prefs[SettingsKeys.KILL_STATE_TIMESTAMP] = System.currentTimeMillis()
                }
            }
            Log.i(TAG, "KILL 现场已保存: route=${snapshot.route}, tab=${snapshot.tab}, " +
                    "fullscreen=${snapshot.isFullScreen}, device=${snapshot.connectedDeviceId}")
        } catch (e: Exception) {
            Log.e(TAG, "保存 KILL 现场失败", e)
        }
    }

    /** null 值不能写入 DataStore，等价旧 putString(key, null) → remove(key) 语义。 */
    private fun androidx.datastore.preferences.core.MutablePreferences.putOrRemove(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        value: String?
    ) {
        if (value != null) this[key] = value else remove(key)
    }

    fun read(): Snapshot? {
        if (!settings.get(SettingsKeys.KILL_STATE_SAVED)) return null

        val timestamp = settings.get(SettingsKeys.KILL_STATE_TIMESTAMP)
        val elapsed = System.currentTimeMillis() - timestamp
        if (elapsed > KILL_STATE_VALIDITY_MS) {
            clear()
            return null
        }

        return Snapshot(
            route = settings.get(SettingsKeys.KILL_STATE_ROUTE),
            tab = settings.get(SettingsKeys.KILL_STATE_TAB),
            isFullScreen = settings.get(SettingsKeys.KILL_STATE_FULLSCREEN),
            connectedDeviceId = settings.getNullable(SettingsKeys.KILL_STATE_CONNECTED_DEVICE_ID),
            connectedDeviceName = settings.getNullable(SettingsKeys.KILL_STATE_CONNECTED_DEVICE_NAME)
        )
    }

    /**
     * 现场恢复后清除，避免重复恢复（非时间敏感路径，走 Repository 即发即忘写即可）。
     */
    fun clear() {
        try {
            settings.set(SettingsKeys.KILL_STATE_SAVED, false)
            settings.remove(SettingsKeys.KILL_STATE_ROUTE)
            settings.remove(SettingsKeys.KILL_STATE_TAB)
            settings.remove(SettingsKeys.KILL_STATE_FULLSCREEN)
            settings.remove(SettingsKeys.KILL_STATE_CONNECTED_DEVICE_ID)
            settings.remove(SettingsKeys.KILL_STATE_CONNECTED_DEVICE_NAME)
            settings.remove(SettingsKeys.KILL_STATE_TIMESTAMP)
        } catch (e: Exception) {
            Log.e(TAG, "清除 KILL 现场失败", e)
        }
    }

    companion object {
        private const val TAG = "KillStateSaver"

        private const val KILL_STATE_VALIDITY_MS = 5 * 60 * 1000L
    }
}
