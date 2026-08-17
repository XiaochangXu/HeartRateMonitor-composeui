package com.github.heartratemonitor_compose.service

import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [BleSettingsListener] 单元测试。
 *
 * 验证：
 * - 服务器相关设置变更触发 onServerSettingsChanged
 * - 速度开关变更触发 onSpeedSettingsChanged
 * - 历史记录关闭触发 onHistoryRecordingDisabled
 * - 历史记录开启不触发 onHistoryRecordingDisabled
 * - 无关键 change 不触发任何回调
 * - unregister 后不再收到回调
 * - register 后恢复接收回调
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BleSettingsListenerTest {

    private lateinit var context: android.app.Application
    private lateinit var repo: SettingsRepository

    private var serverChangedCount = 0
    private var speedChangedCount = 0
    private var historyDisabledCount = 0

    private lateinit var listener: BleSettingsListener

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // DataStore 单例跨测试用例存活，需显式清空，避免残留键影响断言；
        // SharedPreferences 一并清空，避免 SharedPreferencesMigration 迁入残留键
        context.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }

        serverChangedCount = 0
        speedChangedCount = 0
        historyDisabledCount = 0

        // Unconfined 作用域：写入乐观更新 StateFlow 时收集协程在写入线程同步恢复，
        // 保持原 SharedPreferences listener 的同步回调语义，断言无需等待调度。
        repo = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
        listener = BleSettingsListener(
            settingsRepository = repo,
            scope = CoroutineScope(Dispatchers.Unconfined),
            onServerSettingsChanged = { serverChangedCount++ },
            onSpeedSettingsChanged = { speedChangedCount++ },
            onHistoryRecordingDisabled = { historyDisabledCount++ }
        )
        listener.register()
    }

    // ── 服务器设置 ──

    @Test
    fun `HTTP server enabled change triggers server callback`() {
        repo.set(SettingsKeys.HTTP_SERVER_ENABLED, true)
        assertThat(serverChangedCount).isEqualTo(1)
    }

    @Test
    fun `HTTP server port change triggers server callback`() {
        repo.set(SettingsKeys.HTTP_SERVER_PORT, 9000)
        assertThat(serverChangedCount).isEqualTo(1)
    }

    @Test
    fun `WebSocket server enabled change triggers server callback`() {
        repo.set(SettingsKeys.WEBSOCKET_SERVER_ENABLED, true)
        assertThat(serverChangedCount).isEqualTo(1)
    }

    @Test
    fun `WebSocket server port change triggers server callback`() {
        repo.set(SettingsKeys.WEBSOCKET_SERVER_PORT, 9001)
        assertThat(serverChangedCount).isEqualTo(1)
    }

    @Test
    fun `access token change triggers server callback`() {
        repo.set(SettingsKeys.SERVER_ACCESS_TOKEN, "new-token")
        assertThat(serverChangedCount).isEqualTo(1)
    }

    @Test
    fun `multiple server setting changes each trigger callback`() {
        repo.set(SettingsKeys.HTTP_SERVER_ENABLED, true)
        repo.set(SettingsKeys.HTTP_SERVER_PORT, 9000)
        repo.set(SettingsKeys.SERVER_ACCESS_TOKEN, "token")
        assertThat(serverChangedCount).isEqualTo(3)
    }

    // ── 速度设置 ──

    @Test
    fun `speed display enabled change triggers speed callback`() {
        repo.set(SettingsKeys.SPEED_DISPLAY_ENABLED, true)
        assertThat(speedChangedCount).isEqualTo(1)
    }

    @Test
    fun `speed callback not triggered by other changes`() {
        repo.set(SettingsKeys.SPEED_DISPLAY_ENABLED, true)
        repo.set(SettingsKeys.HTTP_SERVER_ENABLED, true)
        assertThat(speedChangedCount).isEqualTo(1)
    }

    // ── 历史记录设置 ──

    @Test
    fun `history recording disabled triggers history callback`() {
        // 先设为 true，再设为 false → 触发回调
        repo.set(SettingsKeys.HISTORY_RECORDING_ENABLED, true)
        assertThat(historyDisabledCount).isEqualTo(0)

        repo.set(SettingsKeys.HISTORY_RECORDING_ENABLED, false)
        assertThat(historyDisabledCount).isEqualTo(1)
    }

    @Test
    fun `history recording enabled does NOT trigger history callback`() {
        repo.set(SettingsKeys.HISTORY_RECORDING_ENABLED, true)
        assertThat(historyDisabledCount).isEqualTo(0)
    }

    @Test
    fun `history recording disabled multiple times triggers each time`() {
        repo.set(SettingsKeys.HISTORY_RECORDING_ENABLED, true)
        repo.set(SettingsKeys.HISTORY_RECORDING_ENABLED, false)
        repo.set(SettingsKeys.HISTORY_RECORDING_ENABLED, true)
        repo.set(SettingsKeys.HISTORY_RECORDING_ENABLED, false)
        assertThat(historyDisabledCount).isEqualTo(2)
    }

    // ── 无关键变更 ──

    @Test
    fun `unrelated key change does not trigger any callback`() {
        repo.set(SettingsKeys.AUTO_RECONNECT_ENABLED, false)
        assertThat(serverChangedCount).isEqualTo(0)
        assertThat(speedChangedCount).isEqualTo(0)
        assertThat(historyDisabledCount).isEqualTo(0)
    }

    @Test
    fun `theme key change does not trigger any callback`() {
        // 用液态玻璃开关代表「无关键」；不能写 THEME_SOURCE（生产代码以 Int 存储，
        // 写 String 会污染跨测试共享的 DataStore 单例，导致后续测试 ClassCastException）
        repo.set(SettingsKeys.LIQUID_GLASS_ENABLED, true)
        assertThat(serverChangedCount).isEqualTo(0)
        assertThat(speedChangedCount).isEqualTo(0)
        assertThat(historyDisabledCount).isEqualTo(0)
    }

    // ── unregister / register ──

    @Test
    fun `unregister stops receiving callbacks`() {
        listener.unregister()
        repo.set(SettingsKeys.HTTP_SERVER_ENABLED, true)
        repo.set(SettingsKeys.SPEED_DISPLAY_ENABLED, true)
        assertThat(serverChangedCount).isEqualTo(0)
        assertThat(speedChangedCount).isEqualTo(0)
    }

    @Test
    fun `register after unregister resumes callbacks`() {
        listener.unregister()
        repo.set(SettingsKeys.HTTP_SERVER_ENABLED, true)
        assertThat(serverChangedCount).isEqualTo(0)

        listener.register()
        repo.set(SettingsKeys.HTTP_SERVER_ENABLED, false)
        assertThat(serverChangedCount).isEqualTo(1)
    }
}
