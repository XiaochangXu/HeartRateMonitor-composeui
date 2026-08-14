package com.github.heartratemonitor_compose.service

import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.google.common.truth.Truth.assertThat
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

    private lateinit var prefs: android.content.SharedPreferences

    private var serverChangedCount = 0
    private var speedChangedCount = 0
    private var historyDisabledCount = 0

    private lateinit var listener: BleSettingsListener

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        prefs = context.getSharedPreferences("test_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        serverChangedCount = 0
        speedChangedCount = 0
        historyDisabledCount = 0

        listener = BleSettingsListener(
            sharedPreferences = prefs,
            onServerSettingsChanged = { serverChangedCount++ },
            onSpeedSettingsChanged = { speedChangedCount++ },
            onHistoryRecordingDisabled = { historyDisabledCount++ }
        )
        listener.register()
    }

    // ── 服务器设置 ──

    @Test
    fun `HTTP server enabled change triggers server callback`() {
        prefs.edit().putBoolean(PrefsKeys.HTTP_SERVER_ENABLED, true).apply()
        assertThat(serverChangedCount).isEqualTo(1)
    }

    @Test
    fun `HTTP server port change triggers server callback`() {
        prefs.edit().putInt(PrefsKeys.HTTP_SERVER_PORT, 9000).apply()
        assertThat(serverChangedCount).isEqualTo(1)
    }

    @Test
    fun `WebSocket server enabled change triggers server callback`() {
        prefs.edit().putBoolean(PrefsKeys.WEBSOCKET_SERVER_ENABLED, true).apply()
        assertThat(serverChangedCount).isEqualTo(1)
    }

    @Test
    fun `WebSocket server port change triggers server callback`() {
        prefs.edit().putInt(PrefsKeys.WEBSOCKET_SERVER_PORT, 9001).apply()
        assertThat(serverChangedCount).isEqualTo(1)
    }

    @Test
    fun `access token change triggers server callback`() {
        prefs.edit().putString(PrefsKeys.SERVER_ACCESS_TOKEN, "new-token").apply()
        assertThat(serverChangedCount).isEqualTo(1)
    }

    @Test
    fun `multiple server setting changes each trigger callback`() {
        prefs.edit().putBoolean(PrefsKeys.HTTP_SERVER_ENABLED, true).apply()
        prefs.edit().putInt(PrefsKeys.HTTP_SERVER_PORT, 9000).apply()
        prefs.edit().putString(PrefsKeys.SERVER_ACCESS_TOKEN, "token").apply()
        assertThat(serverChangedCount).isEqualTo(3)
    }

    // ── 速度设置 ──

    @Test
    fun `speed display enabled change triggers speed callback`() {
        prefs.edit().putBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, true).apply()
        assertThat(speedChangedCount).isEqualTo(1)
    }

    @Test
    fun `speed callback not triggered by other changes`() {
        prefs.edit().putBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, true).apply()
        prefs.edit().putBoolean(PrefsKeys.HTTP_SERVER_ENABLED, true).apply()
        assertThat(speedChangedCount).isEqualTo(1)
    }

    // ── 历史记录设置 ──

    @Test
    fun `history recording disabled triggers history callback`() {
        // 先设为 true，再设为 false → 触发回调
        prefs.edit().putBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, true).apply()
        assertThat(historyDisabledCount).isEqualTo(0)

        prefs.edit().putBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false).apply()
        assertThat(historyDisabledCount).isEqualTo(1)
    }

    @Test
    fun `history recording enabled does NOT trigger history callback`() {
        prefs.edit().putBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, true).apply()
        assertThat(historyDisabledCount).isEqualTo(0)
    }

    @Test
    fun `history recording disabled multiple times triggers each time`() {
        prefs.edit().putBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, true).apply()
        prefs.edit().putBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false).apply()
        prefs.edit().putBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, true).apply()
        prefs.edit().putBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false).apply()
        assertThat(historyDisabledCount).isEqualTo(2)
    }

    // ── 无关键变更 ──

    @Test
    fun `unrelated key change does not trigger any callback`() {
        prefs.edit().putBoolean(PrefsKeys.AUTO_RECONNECT_ENABLED, false).apply()
        assertThat(serverChangedCount).isEqualTo(0)
        assertThat(speedChangedCount).isEqualTo(0)
        assertThat(historyDisabledCount).isEqualTo(0)
    }

    @Test
    fun `theme key change does not trigger any callback`() {
        prefs.edit().putString(PrefsKeys.THEME_SOURCE, "system").apply()
        assertThat(serverChangedCount).isEqualTo(0)
        assertThat(speedChangedCount).isEqualTo(0)
        assertThat(historyDisabledCount).isEqualTo(0)
    }

    // ── unregister / register ──

    @Test
    fun `unregister stops receiving callbacks`() {
        listener.unregister()
        prefs.edit().putBoolean(PrefsKeys.HTTP_SERVER_ENABLED, true).apply()
        prefs.edit().putBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, true).apply()
        assertThat(serverChangedCount).isEqualTo(0)
        assertThat(speedChangedCount).isEqualTo(0)
    }

    @Test
    fun `register after unregister resumes callbacks`() {
        listener.unregister()
        prefs.edit().putBoolean(PrefsKeys.HTTP_SERVER_ENABLED, true).apply()
        assertThat(serverChangedCount).isEqualTo(0)

        listener.register()
        prefs.edit().putBoolean(PrefsKeys.HTTP_SERVER_ENABLED, false).apply()
        assertThat(serverChangedCount).isEqualTo(1)
    }
}
