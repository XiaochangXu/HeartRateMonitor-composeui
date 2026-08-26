package com.github.heartratemonitor_compose.ui.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [FunctionSettingsViewModel] 单元测试（Intent dispatch → 开关写入 → 快照回流往返一致性）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FunctionSettingsViewModelTest {

    private lateinit var context: android.app.Application
    private lateinit var settings: SettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settings = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): FunctionSettingsViewModel = FunctionSettingsViewModel(settings)

    /**
     * 轮询等待 uiState 与磁盘值双重收敛。
     *
     * 仅等流值不够：前序写入的迟到发射可能短暂回退乐观快照（已文档化瞬态限制），
     * 磁盘值与流值同时命中后，后续发射只含最后一次写入，断言不再竞态。
     */
    private suspend fun awaitUiState(
        viewModel: FunctionSettingsViewModel,
        disk: Map<Preferences.Key<*>, Any?> = emptyMap(),
        predicate: (FunctionSettingsUiState) -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + 5000
        while (true) {
            val diskPrefs = context.settingsDataStore.data.first().asMap()
            val diskOk = disk.all { (key, value) -> diskPrefs[key] == value }
            if (diskOk && predicate(viewModel.uiState.value)) break
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("uiState 未在 5000ms 内收敛，当前值：${viewModel.uiState.value}")
            }
            delay(20)
        }
    }

    @Test
    fun `uiState reflects defaults when keys absent`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        assertThat(viewModel.uiState.value).isEqualTo(
            FunctionSettingsUiState(
                historyRecordingEnabled = false,
                heartbeatAnimationEnabled = true,
                speedDisplayEnabled = false,
                hideFromRecentsEnabled = false,
                autoConnectEnabled = false,
                autoReconnectEnabled = true,
                scanFilterEnabled = true,
                navAnimationDisabled = true
            )
        )
    }

    @Test
    fun `history recording toggle writes and flows back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()
    
        // 对应"确认弹窗后开启"路径：确认后 dispatch SetHistoryRecording(true)
        viewModel.dispatch(FunctionSettingsIntent.SetHistoryRecording(true))
        awaitUiState(
            viewModel,
            mapOf(Pair(SettingsKeys.HISTORY_RECORDING_ENABLED, true))
        ) { it.historyRecordingEnabled }
        assertThat(settings.get(SettingsKeys.HISTORY_RECORDING_ENABLED)).isTrue()

        // 关闭无需确认，直接 dispatch false
        viewModel.dispatch(FunctionSettingsIntent.SetHistoryRecording(false))
        awaitUiState(
            viewModel,
            mapOf(Pair(SettingsKeys.HISTORY_RECORDING_ENABLED, false))
        ) { !it.historyRecordingEnabled }
        assertThat(settings.get(SettingsKeys.HISTORY_RECORDING_ENABLED)).isFalse()
    }

    @Test
    fun `animation and speed switches write and flow back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(FunctionSettingsIntent.SetHeartbeatAnimation(false))
        viewModel.dispatch(FunctionSettingsIntent.SetSpeedDisplay(true))
        awaitUiState(
            viewModel,
            mapOf(
                Pair(SettingsKeys.HEARTBEAT_ANIMATION_ENABLED, false),
                Pair(SettingsKeys.SPEED_DISPLAY_ENABLED, true)
            )
        ) { !it.heartbeatAnimationEnabled && it.speedDisplayEnabled }

        assertThat(settings.get(SettingsKeys.HEARTBEAT_ANIMATION_ENABLED)).isFalse()
        assertThat(settings.get(SettingsKeys.SPEED_DISPLAY_ENABLED)).isTrue()
    }

    @Test
    fun `connection group switches write and flow back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(FunctionSettingsIntent.SetHideFromRecents(true))
        viewModel.dispatch(FunctionSettingsIntent.SetNavAnimationDisabled(false))
        viewModel.dispatch(FunctionSettingsIntent.SetAutoConnect(true))
        viewModel.dispatch(FunctionSettingsIntent.SetAutoReconnect(false))
        viewModel.dispatch(FunctionSettingsIntent.SetScanFilter(false))
        awaitUiState(
            viewModel,
            mapOf(
                Pair(SettingsKeys.HIDE_FROM_RECENTS_ENABLED, true),
                Pair(SettingsKeys.NAV_ANIMATION_DISABLED, false),
                Pair(SettingsKeys.AUTO_CONNECT_ENABLED, true),
                Pair(SettingsKeys.AUTO_RECONNECT_ENABLED, false),
                Pair(SettingsKeys.SCAN_FILTER_ENABLED, false)
            )
        ) {
            it.hideFromRecentsEnabled && !it.navAnimationDisabled &&
                it.autoConnectEnabled &&
                !it.autoReconnectEnabled && !it.scanFilterEnabled
        }

        assertThat(settings.get(SettingsKeys.HIDE_FROM_RECENTS_ENABLED)).isTrue()
        assertThat(settings.get(SettingsKeys.NAV_ANIMATION_DISABLED)).isFalse()
        assertThat(settings.get(SettingsKeys.AUTO_CONNECT_ENABLED)).isTrue()
        assertThat(settings.get(SettingsKeys.AUTO_RECONNECT_ENABLED)).isFalse()
        assertThat(settings.get(SettingsKeys.SCAN_FILTER_ENABLED)).isFalse()
    }
}
