package com.github.heartratemonitor_compose.ui.settings

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
 * [FullscreenSoundViewModel] 单元测试（声音模式解析与切换，MVI dispatch 形态）。
 *
 * 试听流程依赖 MediaPlayer，不在单测覆盖范围（真机回归项）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FullscreenSoundViewModelTest {

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

    private fun createViewModel(): FullscreenSoundViewModel =
        FullscreenSoundViewModel(settings, context)

    /**
     * 轮询等待 soundMode、磁盘值与 getNullable 内存快照三重收敛到预期。
     *
     * 仅等流值不够：前序写入的迟到发射可能短暂回退乐观快照（已文档化瞬态限制）；
     * observeNullable 派生流含 ?: 兜底可能先于 prefsState 命中，故同步等待
     * getNullable（断言目标）。三者同时命中后，后续发射只含最后一次写入。
     */
    private suspend fun awaitMode(
        viewModel: FullscreenSoundViewModel,
        timeoutMs: Long = 5000,
        expected: String
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val disk = context.settingsDataStore.data.first()[SettingsKeys.FULLSCREEN_SOUND_MODE]
            val mem = settings.getNullable(SettingsKeys.FULLSCREEN_SOUND_MODE)
            if (viewModel.uiState.value.soundMode == expected && disk == expected && mem == expected) break
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("soundMode 未在 ${timeoutMs}ms 内收敛到 $expected，当前值：${viewModel.uiState.value.soundMode}，磁盘值：$disk，内存快照：$mem")
            }
            delay(20)
        }
    }

    @Test
    fun `persisted mode is exposed as soundMode`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        settings.set(SettingsKeys.FULLSCREEN_SOUND_MODE, "cn")
        val viewModel = createViewModel()
        runCurrent()

        awaitMode(viewModel, expected = "cn")
        assertThat(viewModel.uiState.value.soundMode).isEqualTo("cn")
    }

    @Test
    fun `legacy disabled switch resolves to off and persists`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // 旧开关关闭且模式键缺失：构造期 resolveSoundMode 迁移为 "off" 并落盘
        settings.set(SettingsKeys.FULLSCREEN_SOUND_ENABLED, false)
        val viewModel = createViewModel()
        runCurrent()

        awaitMode(viewModel, expected = "off")
        assertThat(settings.getNullable(SettingsKeys.FULLSCREEN_SOUND_MODE)).isEqualTo("off")
    }

    @Test
    fun `selecting mode writes and flows back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        settings.set(SettingsKeys.FULLSCREEN_SOUND_MODE, "off")
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(FullscreenSoundIntent.SelectSoundMode("en"))
        awaitMode(viewModel, expected = "en")
        assertThat(settings.getNullable(SettingsKeys.FULLSCREEN_SOUND_MODE)).isEqualTo("en")

        viewModel.dispatch(FullscreenSoundIntent.SelectSoundMode("off"))
        awaitMode(viewModel, expected = "off")
        assertThat(settings.getNullable(SettingsKeys.FULLSCREEN_SOUND_MODE)).isEqualTo("off")
    }

    @Test
    fun `preview state idle initially and StopPreview resets it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        assertThat(viewModel.uiState.value.isPreviewing).isFalse()
        assertThat(viewModel.uiState.value.previewProgress).isEqualTo(0f)

        // 无进行中的试听时停止不抛异常、状态保持空闲
        viewModel.dispatch(FullscreenSoundIntent.StopPreview)
        runCurrent()
        assertThat(viewModel.uiState.value.isPreviewing).isFalse()
        assertThat(viewModel.uiState.value.previewProgress).isEqualTo(0f)
    }
}
