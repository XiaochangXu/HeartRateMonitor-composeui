package com.github.heartratemonitor_compose.ui.alarm

import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.sensor.PostureSensorProvider
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.github.heartratemonitor_compose.service.ServiceLauncher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
 * [HeartRateAlarmViewModel] 姿态校准流程测试（MVI dispatch 形态）。
 *
 * 回归覆盖：校准协程被「排除姿态检测」打断后重新校准，旧协程不得提前终止
 * 新校准的进度 UI 或重复提交数据（修复前旧协程无句柄、不被取消）。
 * 另覆盖阈值互相约束的纯归约函数（高 ≥ 低 + 1，迁移方案 Phase 4 要点）。
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
        // 注意：VM 须在各测试 setMain 之后创建（见 createViewModel）——
        // 本 VM 的 init 会经 viewModelScope 启动设置投影协程，若在 @Before（setMain 前）
        // 创建，viewModelScope 会捕获真实 Main 调度器，导致校准 delay 不走虚拟时间。
    }

    /** Main 调度器替换后创建 VM，保证 init 投影与校准协程落在虚拟时间调度器上。 */
    private fun createViewModel(): HeartRateAlarmViewModel {
        // Robolectric 下 SensorManager.getDefaultSensor 返回 null，PostureSensorProvider.start 为 no-op，
        // 校准测试只驱动 ViewModel 状态与虚拟时间，不依赖真实传感器采样
        return HeartRateAlarmViewModel(
            settings = settings,
            postureSensorProvider = PostureSensorProvider(context),
            serviceLauncher = serviceLauncher
        )
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    /** 校准时长（秒），与 ViewModel 内部 CALIBRATION_DURATION_SECONDS 一致。 */
    private val calibrationDurationMs = 10_000L

    @Test
    fun `calibration completes after duration`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()

        viewModel.dispatch(HeartRateAlarmIntent.StartCalibration(isSitting = true))
        runCurrent()
        assertThat(viewModel.uiState.value.isCalibrating).isTrue()

        // advanceTimeBy 不执行恰好落在目标时刻的任务，需补 runCurrent()
        advanceTimeBy(calibrationDurationMs)
        runCurrent()

        assertThat(viewModel.uiState.value.isCalibrating).isFalse()
        assertThat(viewModel.uiState.value.calibrationProgress).isEqualTo(10)
    }

    @Test
    fun `excluding posture detection cancels calibration immediately`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()

        viewModel.dispatch(HeartRateAlarmIntent.StartCalibration(isSitting = true))
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        assertThat(viewModel.uiState.value.isCalibrating).isTrue()

        viewModel.dispatch(HeartRateAlarmIntent.SetExcludePostureDetection(true))
        runCurrent()
        assertThat(viewModel.uiState.value.isCalibrating).isFalse()

        // 旧协程已取消：后续时间推进不再改变校准状态
        advanceTimeBy(calibrationDurationMs)
        runCurrent()
        assertThat(viewModel.uiState.value.isCalibrating).isFalse()
    }

    @Test
    fun `restarting calibration after interruption is not preempted by stale coroutine`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()

        // 第一段校准：5 秒后被打断
        viewModel.dispatch(HeartRateAlarmIntent.StartCalibration(isSitting = true))
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        viewModel.dispatch(HeartRateAlarmIntent.SetExcludePostureDetection(true))
        runCurrent()
        assertThat(viewModel.uiState.value.isCalibrating).isFalse()

        // 第二段校准（站立）
        viewModel.dispatch(HeartRateAlarmIntent.StartCalibration(isSitting = false))
        runCurrent()
        assertThat(viewModel.uiState.value.isCalibrating).isTrue()

        // 推进到第一段校准的原定完成时刻（t=10s）：
        // 回归断言——旧协程必须已被取消，不得在此刻提前结束第二段校准
        advanceTimeBy(5_000)
        runCurrent()
        assertThat(viewModel.uiState.value.isCalibrating).isTrue()
        assertThat(viewModel.uiState.value.calibrationProgress).isEqualTo(5)

        // 第二段校准正常走完
        advanceTimeBy(5_000)
        runCurrent()
        assertThat(viewModel.uiState.value.isCalibrating).isFalse()
        assertThat(viewModel.uiState.value.calibrationProgress).isEqualTo(10)
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
