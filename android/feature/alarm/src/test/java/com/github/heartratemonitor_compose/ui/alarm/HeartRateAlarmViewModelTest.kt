package com.github.heartratemonitor_compose.ui.alarm

import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.github.heartratemonitor_compose.service.ServiceLauncher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * [HeartRateAlarmViewModel] 测试。
 *
 * 姿态检测与校准逻辑已搬至 [HeartRateAlarmService]，VM 仅转发 Intent 给服务。
 * Robolectric 下 bindService 不会真正连接服务，校准流程由服务侧测试覆盖。
 * 本测试覆盖：预警开关启停服务、阈值互相约束纯归约函数。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HeartRateAlarmViewModelTest {

    private lateinit var context: android.app.Application
    private lateinit var settings: SettingsRepository
    private lateinit var serviceLauncher: FakeServiceLauncher

    private class FakeServiceLauncher : ServiceLauncher {
        var alarmStarted = false
            private set
        var alarmStopped = false
            private set

        override fun startBleService() {}
        override fun startStatusBarResidentService() {}
        override fun stopStatusBarResidentService() {}
        override fun startHeartRateAlarmService() { alarmStarted = true }
        override fun stopHeartRateAlarmService() { alarmStopped = true }
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settings = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
        serviceLauncher = FakeServiceLauncher()
    }

    /** Main 调度器替换后创建 VM，保证 init 投影协程落在虚拟时间调度器上。 */
    private fun createViewModel(): HeartRateAlarmViewModel {
        return HeartRateAlarmViewModel(
            settings = settings,
            serviceLauncher = serviceLauncher,
            appContext = context
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `enabling alarm starts service and disabling stops it`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()

        viewModel.dispatch(HeartRateAlarmIntent.SetAlarmEnabled(true))
        runCurrent()
        assertThat(serviceLauncher.alarmStarted).isTrue()
        assertThat(serviceLauncher.alarmStopped).isFalse()

        viewModel.dispatch(HeartRateAlarmIntent.SetAlarmEnabled(false))
        runCurrent()
        assertThat(serviceLauncher.alarmStopped).isTrue()
    }

    // ── 阈值互相约束纯归约函数（高 ≥ 低 + 1）──

    @Test
    fun `clampHighThreshold enforces at least low plus one`() {
        assertThat(clampHighThreshold(value = 100, lowThreshold = 120)).isEqualTo(121)
        assertThat(clampHighThreshold(value = 121, lowThreshold = 120)).isEqualTo(121)
        assertThat(clampHighThreshold(value = 150, lowThreshold = 120)).isEqualTo(150)
    }

    @Test
    fun `clampLowThreshold enforces at most high minus one`() {
        assertThat(clampLowThreshold(value = 150, highThreshold = 120)).isEqualTo(119)
        assertThat(clampLowThreshold(value = 119, highThreshold = 120)).isEqualTo(119)
        assertThat(clampLowThreshold(value = 60, highThreshold = 120)).isEqualTo(60)
    }
}
