package com.github.heartratemonitor_compose.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.data.model.ScannedDevice
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
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
 * [HeartRateRepository] 服务重建对账回归测试（幽灵连接缺陷，见 spec 修订记录）。
 *
 * 场景：BleService 被系统杀死后 START_STICKY 重建时 Repository 进程级存活，
 * 保留上一实例的连接态；新 Handler 无任何活动连接，若不对账，UI 将展示
 * 幽灵连接（设备页显示已连接、断开命令静默落空）。
 *
 * 验证 resetForNewServiceInstance 将全部瞬态状态清零回归初始值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HeartRateRepositoryTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var repository: HeartRateRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // DataStore 单例跨测试用例存活，需显式清空；SharedPreferences 一并清空，
        // 避免 SharedPreferencesMigration 迁入残留键
        context.getSharedPreferences(SETTINGS_FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settingsRepository = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
        repository = HeartRateRepository(settingsRepository)
    }

    @Test
    fun `resetForNewServiceInstance clears stale state left by previous service instance`() {
        // 模拟上一 Service 实例被杀时残留的连接态
        repository.setBleState(BleState.Connected("已连接到 Test HRM"))
        repository.setHeartRate(75)
        repository.setHeartRateMeasurement(
            HeartRateMeasurement(
                bpm = 75,
                rrIntervals = listOf(400f, 410f),
                sensorContactSupported = true,
                sensorContact = true,
                energyExpended = null
            )
        )
        repository.setScanResults(
            listOf(ScannedDevice(identifier = "AA:BB:CC:DD:EE:FF", name = "Test HRM", rssi = -52))
        )
        repository.setConnectedDevice(ConnectedDevice(id = "AA:BB:CC:DD:EE:FF", name = "Test HRM"))
        repository.updateSpeed(12.5f)

        repository.resetForNewServiceInstance()

        assertThat(repository.bleState.value).isEqualTo(BleState.Idle)
        assertThat(repository.heartRate.value).isEqualTo(0)
        assertThat(repository.heartRateMeasurement.value).isEqualTo(HeartRateMeasurement.EMPTY)
        assertThat(repository.scanResults.value).isEmpty()
        assertThat(repository.connectedDevice.value).isNull()
        assertThat(repository.speed.value).isEqualTo(0f)
    }

    @Test
    fun `resetForNewServiceInstance is idempotent on fresh repository`() {
        repository.resetForNewServiceInstance()

        assertThat(repository.bleState.value).isEqualTo(BleState.Idle)
        assertThat(repository.heartRate.value).isEqualTo(0)
        assertThat(repository.connectedDevice.value).isNull()
    }
}
