package com.github.heartratemonitor_compose.ui.server

import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.network.IpAddressProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
 * [ServerSettingsViewModel] 单元测试（Intent dispatch → 设置写入 → observe 回流往返一致性）。
 *
 * 参照 HeartRateAlarmViewModelTest 的 Robolectric + coroutines-test 写法：
 * Main 调度器替换为虚拟时间调度器，驱动 viewModelScope 的真源投影收集。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServerSettingsViewModelTest {

    private lateinit var context: android.app.Application
    private lateinit var settings: SettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // 清空 DataStore 与 SharedPreferences，避免跨用例残留
        context.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settings = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    /** Main 调度器替换后创建 VM，保证真源投影收集协程落在虚拟时间调度器上。 */
    private fun createViewModel(): ServerSettingsViewModel =
        ServerSettingsViewModel(settings, IpAddressProvider(context))

    /**
     * 等待指定键落盘且 uiState 收敛到预期值。
     *
     * DataStore 发射在 IO 线程产生，Unconfined 收集者的对账更新经跨线程派发到
     * 测试调度器，runCurrent() 无法同步看到异步到达的任务，故用真实时间轮询
     * （同时覆盖已文档化的瞬态回退自愈窗口）。
     */
    private suspend fun awaitUiState(
        viewModel: ServerSettingsViewModel,
        timeoutMs: Long = 5000,
        predicate: (ServerUiState) -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate(viewModel.uiState.value)) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError("uiState 未在 ${timeoutMs}ms 内收敛，当前值：${viewModel.uiState.value}")
            }
            delay(20)
        }
    }

    @Test
    fun `uiState reflects defaults when keys absent`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        assertThat(viewModel.uiState.value)
            .isEqualTo(ServerUiState(httpEnabled = false, httpPort = 8000, wsEnabled = false, wsPort = 8001))
    }

    @Test
    fun `enabling http writes settings and flows back to uiState`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(ServerSettingsIntent.SetHttpEnabled(true))
        awaitUiState(viewModel) { it.httpEnabled && it.httpPort == 8000 }

        assertThat(settings.get(SettingsKeys.HTTP_SERVER_ENABLED)).isTrue()
        // 开启即采用默认端口（与原页面开关打开时默认端口立即落盘语义一致）
        assertThat(settings.get(SettingsKeys.HTTP_SERVER_PORT)).isEqualTo(8000)
        assertThat(viewModel.uiState.value)
            .isEqualTo(ServerUiState(httpEnabled = true, httpPort = 8000, wsEnabled = false, wsPort = 8001))

        viewModel.dispatch(ServerSettingsIntent.SetHttpEnabled(false))
        awaitUiState(viewModel) { !it.httpEnabled }
        assertThat(settings.get(SettingsKeys.HTTP_SERVER_ENABLED)).isFalse()
        assertThat(viewModel.uiState.value.httpEnabled).isFalse()
    }

    @Test
    fun `enabling websocket writes settings and flows back to uiState`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(ServerSettingsIntent.SetWsEnabled(true))
        awaitUiState(viewModel) { it.wsEnabled && it.wsPort == 8001 }

        assertThat(settings.get(SettingsKeys.WEBSOCKET_SERVER_ENABLED)).isTrue()
        assertThat(settings.get(SettingsKeys.WEBSOCKET_SERVER_PORT)).isEqualTo(8001)
        assertThat(viewModel.uiState.value.wsEnabled).isTrue()
        assertThat(viewModel.uiState.value.wsPort).isEqualTo(8001)
    }

    @Test
    fun `valid port commit persists and flows back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(ServerSettingsIntent.CommitHttpPort("9000"))
        awaitUiState(viewModel) { it.httpPort == 9000 }

        assertThat(settings.get(SettingsKeys.HTTP_SERVER_PORT)).isEqualTo(9000)
        assertThat(viewModel.uiState.value.httpPort).isEqualTo(9000)
    }

    @Test
    fun `invalid port commit keeps last valid value`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(ServerSettingsIntent.CommitHttpPort("9000"))
        awaitUiState(viewModel) { it.httpPort == 9000 }

        // 超范围 / 空 / 非数字：均不落盘，保持上次有效值
        viewModel.dispatch(ServerSettingsIntent.CommitHttpPort("70000"))
        viewModel.dispatch(ServerSettingsIntent.CommitHttpPort(""))
        viewModel.dispatch(ServerSettingsIntent.CommitHttpPort("abc"))
        viewModel.dispatch(ServerSettingsIntent.CommitWsPort("0"))
        awaitUiState(viewModel) { it.httpPort == 9000 && it.wsPort == 8001 }

        assertThat(settings.get(SettingsKeys.HTTP_SERVER_PORT)).isEqualTo(9000)
        assertThat(settings.get(SettingsKeys.WEBSOCKET_SERVER_PORT)).isEqualTo(8001)
        assertThat(viewModel.uiState.value.httpPort).isEqualTo(9000)
        assertThat(viewModel.uiState.value.wsPort).isEqualTo(8001)
    }
}
