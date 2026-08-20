package com.github.heartratemonitor_compose.ui.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.github.heartratemonitor_compose.data.system.OverlayPermissionProvider
import com.github.heartratemonitor_compose.service.ServiceLauncher
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
 * [StatusBarSettingsViewModel] 单元测试（Intent dispatch → 写入 → observe 回流往返一致性）。
 *
 * 覆盖开关、滑块每拍写入与颜色确认回写；状态栏常驻服务的热更新由
 * StatusBarResidentService 经 observe().drop(1) 响应，键与写入时序未变。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StatusBarSettingsViewModelTest {

    private lateinit var context: android.app.Application
    private lateinit var settings: SettingsRepository
    private lateinit var serviceLauncher: FakeServiceLauncher
    private lateinit var overlayProvider: FakeOverlayPermissionProvider
    private var suppressHideInvoked: Boolean = false

    private class FakeServiceLauncher : ServiceLauncher {
        var residentStarted = false
            private set
        var residentStopped = false
            private set

        override fun startBleService() {}
        override fun startStatusBarResidentService() { residentStarted = true }
        override fun stopStatusBarResidentService() { residentStopped = true }
        override fun startHeartRateAlarmService() {}
        override fun stopHeartRateAlarmService() {}
    }

    /** Robolectric 下系统悬浮窗权限判定不可控，以 Fake 替换（open 化见类注释）。 */
    private class FakeOverlayPermissionProvider(context: android.content.Context) :
        OverlayPermissionProvider(context) {
        var granted = true
        override fun canDrawOverlays(): Boolean = granted
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settings = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
        serviceLauncher = FakeServiceLauncher()
        overlayProvider = FakeOverlayPermissionProvider(context)
        suppressHideInvoked = false
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): StatusBarSettingsViewModel = StatusBarSettingsViewModel(
        settings = settings,
        overlayPermissionProvider = overlayProvider,
        serviceLauncher = serviceLauncher,
        suppressHideForExternalLaunch = { suppressHideInvoked = it }
    )

    /**
     * 轮询等待 uiState 与磁盘值双重收敛。
     *
     * 仅等流值不够：前序写入的迟到发射可能短暂回退乐观快照（已文档化瞬态限制），
     * 磁盘值与流值同时命中后，后续发射只含最后一次写入，断言不再竞态。
     */
    private suspend fun awaitUiState(
        viewModel: StatusBarSettingsViewModel,
        disk: Map<Preferences.Key<*>, Any?> = emptyMap(),
        predicate: (StatusBarSettingsUiState) -> Boolean
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
            StatusBarSettingsUiState(
                residentEnabled = false,
                bpmTextEnabled = true,
                xPosition = 0,
                yOffset = 10,
                size = 100,
                textThickness = 0,
                textColor = android.graphics.Color.BLACK
            )
        )
    }

    @Test
    fun `slider changes write every tick and flow back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(StatusBarSettingsIntent.SetXPosition(30))
        viewModel.dispatch(StatusBarSettingsIntent.SetYOffset(5))
        viewModel.dispatch(StatusBarSettingsIntent.SetSize(120))
        viewModel.dispatch(StatusBarSettingsIntent.SetTextThickness(40))
        awaitUiState(
            viewModel,
            mapOf(
                Pair(SettingsKeys.STATUS_BAR_X_POSITION, 30),
                Pair(SettingsKeys.STATUS_BAR_Y_OFFSET, 5),
                Pair(SettingsKeys.STATUS_BAR_SIZE, 120),
                Pair(SettingsKeys.STATUS_BAR_TEXT_THICKNESS, 40)
            )
        ) {
            it.xPosition == 30 && it.yOffset == 5 && it.size == 120 && it.textThickness == 40
        }

        assertThat(settings.get(SettingsKeys.STATUS_BAR_X_POSITION)).isEqualTo(30)
        assertThat(settings.get(SettingsKeys.STATUS_BAR_Y_OFFSET)).isEqualTo(5)
        assertThat(settings.get(SettingsKeys.STATUS_BAR_SIZE)).isEqualTo(120)
        assertThat(settings.get(SettingsKeys.STATUS_BAR_TEXT_THICKNESS)).isEqualTo(40)
    }

    @Test
    fun `color set and picker confirm write and flow back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(StatusBarSettingsIntent.SetTextColor(android.graphics.Color.WHITE))
        awaitUiState(
            viewModel,
            mapOf(Pair(SettingsKeys.STATUS_BAR_TEXT_COLOR, android.graphics.Color.WHITE))
        ) { it.textColor == android.graphics.Color.WHITE }
        assertThat(settings.get(SettingsKeys.STATUS_BAR_TEXT_COLOR)).isEqualTo(android.graphics.Color.WHITE)

        // 选择器确认回写（ColorPickerRequest 为 UI 瞬时态，键经参数传入）
        viewModel.dispatch(
            StatusBarSettingsIntent.ConfirmColor(SettingsKeys.STATUS_BAR_TEXT_COLOR, android.graphics.Color.RED)
        )
        awaitUiState(
            viewModel,
            mapOf(Pair(SettingsKeys.STATUS_BAR_TEXT_COLOR, android.graphics.Color.RED))
        ) { it.textColor == android.graphics.Color.RED }
        assertThat(settings.get(SettingsKeys.STATUS_BAR_TEXT_COLOR)).isEqualTo(android.graphics.Color.RED)
    }

    @Test
    fun `resident and bpm switches write and flow back`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(StatusBarSettingsIntent.SetResident(true))
        viewModel.dispatch(StatusBarSettingsIntent.SetBpmText(false))
        awaitUiState(
            viewModel,
            mapOf(
                Pair(SettingsKeys.STATUS_BAR_RESIDENT_ENABLED, true),
                Pair(SettingsKeys.STATUS_BAR_BPM_TEXT_ENABLED, false)
            )
        ) { it.residentEnabled && !it.bpmTextEnabled }

        assertThat(settings.get(SettingsKeys.STATUS_BAR_RESIDENT_ENABLED)).isTrue()
        assertThat(settings.get(SettingsKeys.STATUS_BAR_BPM_TEXT_ENABLED)).isFalse()
        // 有权限时开启/关闭联动常驻服务启停
        assertThat(serviceLauncher.residentStarted).isTrue()
        viewModel.dispatch(StatusBarSettingsIntent.SetResident(false))
        awaitUiState(
            viewModel,
            mapOf(Pair(SettingsKeys.STATUS_BAR_RESIDENT_ENABLED, false))
        ) { !it.residentEnabled }
        assertThat(serviceLauncher.residentStopped).isTrue()
    }

    @Test
    fun `enabling resident without overlay permission requests permission and skips write`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        overlayProvider.granted = false
        val viewModel = createViewModel()
        runCurrent()

        var permissionIntentReceived = false
        viewModel.dispatch(
            StatusBarSettingsIntent.SetResident(true) { permissionIntent ->
                permissionIntentReceived = permissionIntent != null
            }
        )
        runCurrent()

        // 无权限：不落盘、不启服务、置外部启动抑制标志、经回调回传权限页 Intent
        assertThat(settings.get(SettingsKeys.STATUS_BAR_RESIDENT_ENABLED)).isFalse()
        assertThat(viewModel.uiState.value.residentEnabled).isFalse()
        assertThat(serviceLauncher.residentStarted).isFalse()
        assertThat(suppressHideInvoked).isTrue()
        assertThat(permissionIntentReceived).isTrue()
    }
}
