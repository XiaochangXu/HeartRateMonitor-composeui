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
 * [FloatingWindowSettingsViewModel] 单元测试（Intent dispatch → 写入 → observe 回流往返一致性）。
 *
 * 覆盖开关、滑块每拍写入与三色选择器确认回写；悬浮窗服务的热更新由
 * FloatingWindowService 经 observe().drop(1) 响应，键与写入时序未变。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FloatingWindowSettingsViewModelTest {

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

    private fun createViewModel(): FloatingWindowSettingsViewModel =
        FloatingWindowSettingsViewModel(settings)

    /**
     * 轮询等待 uiState 与磁盘值双重收敛。
     *
     * 仅等流值不够：前序写入的迟到发射可能短暂回退乐观快照（已文档化瞬态限制），
     * 磁盘值与流值同时命中后，后续发射只含最后一次写入，断言不再竞态。
     */
    private suspend fun awaitUiState(
        viewModel: FloatingWindowSettingsViewModel,
        disk: Map<Preferences.Key<*>, Any?> = emptyMap(),
        predicate: (FloatingWindowSettingsUiState) -> Boolean
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
            FloatingWindowSettingsUiState(
                bpmTextEnabled = true,
                heartIconEnabled = true,
                size = 100,
                iconSize = 100,
                cornerRadius = 100,
                bgAlpha = 10,
                borderAlpha = 100,
                textColor = android.graphics.Color.BLACK,
                bgColor = android.graphics.Color.BLACK,
                borderColor = android.graphics.Color.GRAY
            )
        )
    }

    @Test
    fun `slider changes write every tick and flow back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(FloatingWindowSettingsIntent.SetSize(150))
        viewModel.dispatch(FloatingWindowSettingsIntent.SetIconSize(80))
        viewModel.dispatch(FloatingWindowSettingsIntent.SetCornerRadius(50))
        viewModel.dispatch(FloatingWindowSettingsIntent.SetBgAlpha(30))
        viewModel.dispatch(FloatingWindowSettingsIntent.SetBorderAlpha(60))
        awaitUiState(
            viewModel,
            mapOf(
                Pair(SettingsKeys.FLOATING_SIZE, 150),
                Pair(SettingsKeys.FLOATING_ICON_SIZE, 80),
                Pair(SettingsKeys.FLOATING_CORNER_RADIUS, 50),
                Pair(SettingsKeys.FLOATING_BG_ALPHA, 30),
                Pair(SettingsKeys.FLOATING_BORDER_ALPHA, 60)
            )
        ) {
            it.size == 150 && it.iconSize == 80 && it.cornerRadius == 50 &&
                it.bgAlpha == 30 && it.borderAlpha == 60
        }

        assertThat(settings.get(SettingsKeys.FLOATING_SIZE)).isEqualTo(150)
        assertThat(settings.get(SettingsKeys.FLOATING_ICON_SIZE)).isEqualTo(80)
        assertThat(settings.get(SettingsKeys.FLOATING_CORNER_RADIUS)).isEqualTo(50)
        assertThat(settings.get(SettingsKeys.FLOATING_BG_ALPHA)).isEqualTo(30)
        assertThat(settings.get(SettingsKeys.FLOATING_BORDER_ALPHA)).isEqualTo(60)
    }

    @Test
    fun `color picker confirm writes all three keys and flows back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(
            FloatingWindowSettingsIntent.ConfirmColor(SettingsKeys.FLOATING_TEXT_COLOR, android.graphics.Color.WHITE)
        )
        awaitUiState(
            viewModel,
            mapOf(Pair(SettingsKeys.FLOATING_TEXT_COLOR, android.graphics.Color.WHITE))
        ) { it.textColor == android.graphics.Color.WHITE }

        viewModel.dispatch(
            FloatingWindowSettingsIntent.ConfirmColor(SettingsKeys.FLOATING_BG_COLOR, android.graphics.Color.BLUE)
        )
        awaitUiState(
            viewModel,
            mapOf(Pair(SettingsKeys.FLOATING_BG_COLOR, android.graphics.Color.BLUE))
        ) { it.bgColor == android.graphics.Color.BLUE }

        viewModel.dispatch(
            FloatingWindowSettingsIntent.ConfirmColor(SettingsKeys.FLOATING_BORDER_COLOR, android.graphics.Color.RED)
        )
        awaitUiState(
            viewModel,
            mapOf(Pair(SettingsKeys.FLOATING_BORDER_COLOR, android.graphics.Color.RED))
        ) { it.borderColor == android.graphics.Color.RED }

        assertThat(settings.get(SettingsKeys.FLOATING_TEXT_COLOR)).isEqualTo(android.graphics.Color.WHITE)
        assertThat(settings.get(SettingsKeys.FLOATING_BG_COLOR)).isEqualTo(android.graphics.Color.BLUE)
        assertThat(settings.get(SettingsKeys.FLOATING_BORDER_COLOR)).isEqualTo(android.graphics.Color.RED)
    }

    @Test
    fun `icon switches write and flow back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(FloatingWindowSettingsIntent.SetBpmText(false))
        viewModel.dispatch(FloatingWindowSettingsIntent.SetHeartIcon(false))
        awaitUiState(
            viewModel,
            mapOf(
                Pair(SettingsKeys.BPM_TEXT_ENABLED, false),
                Pair(SettingsKeys.HEART_ICON_ENABLED, false)
            )
        ) { !it.bpmTextEnabled && !it.heartIconEnabled }

        assertThat(settings.get(SettingsKeys.BPM_TEXT_ENABLED)).isFalse()
        assertThat(settings.get(SettingsKeys.HEART_ICON_ENABLED)).isFalse()
    }
}
