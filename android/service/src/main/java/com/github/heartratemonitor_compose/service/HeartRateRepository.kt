package com.github.heartratemonitor_compose.service

import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.data.model.ChartDataSnapshot
import com.github.heartratemonitor_compose.data.model.ScannedDevice
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 心率实时数据的进程级单一事实来源（SSOT）。
 *
 * ⚠️ 反直觉设计：@Singleton 跨 Service 重建存活，Service 被杀重建时由 onCreate 调用
 * resetForNewServiceInstance 对账清零，否则 UI 展示幽灵连接
 * （首页图表未连接、设备页已连接、断开命令静默落空）。
 */
@Singleton
class HeartRateRepository @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    /** Tracker 节流发布作用域：进程级，Service 销毁后挂起的发布任务无人消费、无副作用。 */
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _bleState = MutableStateFlow<BleState>(BleState.Idle)
    val bleState: StateFlow<BleState> = _bleState.asStateFlow()

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    private val _heartRateMeasurement = MutableStateFlow(HeartRateMeasurement.EMPTY)
    val heartRateMeasurement: StateFlow<HeartRateMeasurement> = _heartRateMeasurement.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scanResults: StateFlow<List<ScannedDevice>> = _scanResults.asStateFlow()

    // ⚠️ 反直觉设计：连接后 scanResults 被清空，单独维护已连接设备防止列表为空
    private val _connectedDevice = MutableStateFlow<ConnectedDevice?>(null)
    val connectedDevice: StateFlow<ConnectedDevice?> = _connectedDevice.asStateFlow()

    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    // 服务层会话图表追踪器：StateFlow 重放实现「重进即恢复」
    // 历史记录开关关闭期间仅跟踪极值、不统计图表点，开启后从零开始绘制
    private val sessionChartTracker = SessionChartTracker(repositoryScope) {
        settingsRepository.get(SettingsKeys.HISTORY_RECORDING_ENABLED)
    }
    val chartDataSnapshot: StateFlow<ChartDataSnapshot?> = sessionChartTracker.chartDataSnapshot
    val sessionMaxHr: StateFlow<Int> = sessionChartTracker.sessionMaxHr
    val sessionMinHr: StateFlow<Int> = sessionChartTracker.sessionMinHr

    // ── 写入面（仅采集引擎 BleConnectionHandler 调用）──

    fun setBleState(state: BleState) {
        _bleState.value = state
    }

    fun setHeartRate(bpm: Int) {
        _heartRate.value = bpm
    }

    fun setHeartRateMeasurement(measurement: HeartRateMeasurement) {
        _heartRateMeasurement.value = measurement
    }

    fun setScanResults(results: List<ScannedDevice>) {
        _scanResults.value = results
    }

    fun setConnectedDevice(device: ConnectedDevice?) {
        _connectedDevice.value = device
    }

    fun updateSpeed(value: Float) {
        _speed.value = value
    }

    // ── 图表追踪代理（原 BleConnectionHandler 调用点）──

    fun onChartMeasurement(measurement: HeartRateMeasurement) =
        sessionChartTracker.onMeasurement(measurement)

    /** 新会话开始：重置图表缓存与极值（连接成功处调用）。 */
    fun resetChartSession() = sessionChartTracker.reset()

    /** 清零本次连接的心率极值（断开/蓝牙关闭处调用）。 */
    fun resetChartExtremes() = sessionChartTracker.resetSessionExtremes()

    /** 清空图表缓存（断开/蓝牙关闭/历史记录开关关闭处调用）。 */
    fun clearChart() = sessionChartTracker.clear()

    /** TRIM 内存预警时释放图表缓存（FairMemoryReceiver 回调链）。 */
    fun releaseChartOnTrim(notifyType: Int) = sessionChartTracker.releaseOnTrim(notifyType)

    // ── 服务重建对账（仅 BleService.onCreate 调用）──

    /**
     * 清零上一个 Service 实例残留的瞬态状态。
     *
     * ⚠️ 反直觉设计：仅可在 onCreate 调用——此刻新 Handler 尚无活动连接，
     * 本类中的连接态必然为残留值，必须显式清零避免幽灵连接。
     */
    fun resetForNewServiceInstance() {
        _bleState.value = BleState.Idle
        _heartRate.value = 0
        _heartRateMeasurement.value = HeartRateMeasurement.EMPTY
        _scanResults.value = emptyList()
        _connectedDevice.value = null
        _speed.value = 0f
        sessionChartTracker.resetSessionExtremes()
        sessionChartTracker.clear()
    }
}
