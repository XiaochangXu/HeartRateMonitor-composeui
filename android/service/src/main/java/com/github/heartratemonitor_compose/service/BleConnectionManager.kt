package com.github.heartratemonitor_compose.service

import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.data.model.ChartDataSnapshot
import com.github.heartratemonitor_compose.data.model.ScannedDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * 连接设备信息（设备 id + 名称）。
 */
data class ConnectedDevice(val id: String, val name: String)

/**
 * 将扫描/连接/断开/状态查询能力从 [BleService] 具体类中抽象出来，
 * ViewModel 依赖本接口而非 [BleService]，从而：
 * - 可脱离 Android Service 对 ViewModel 进行单元测试
 * - 未来替换 BLE 底层实现不需改 ViewModel
 *
 * 绑定机制不变：[BleService] 仍通过 Binder 绑定。
 */
interface BleConnectionManager {

    val bleState: StateFlow<BleState>

    val heartRate: StateFlow<Int>

    val heartRateMeasurement: StateFlow<HeartRateMeasurement>

    val speed: StateFlow<Float>

    val scanResults: StateFlow<List<ScannedDevice>>

    val connectedDevice: StateFlow<ConnectedDevice?>

    /**
     * 当前会话的图表快照（60秒滑动窗口）。
     *
     * 生命周期随 BLE 连接生灭，由服务层 [SessionChartTracker] 维护。
     * StateFlow 重放实现「重进即恢复」——退出应用再重进后，
     * UI 订阅本流立即获得当前会话的图表缓存，不再归零。
     */
    val chartDataSnapshot: StateFlow<ChartDataSnapshot?>

    /** 当前会话心率最大值（随连接生灭，跨重进连续） */
    val sessionMaxHr: StateFlow<Int>

    /** 当前会话心率最小值（随连接生灭，跨重进连续） */
    val sessionMinHr: StateFlow<Int>

    fun isDeviceConnected(): Boolean

    fun startScan(durationMillis: Long = DEFAULT_SCAN_DURATION_MS)

    fun stopScan()

    fun startAutoConnectScan(favoriteDeviceId: String, durationMillis: Long = DEFAULT_SCAN_DURATION_MS)

    fun connectToDevice(identifier: String)

    fun disconnectDevice()

    companion object {
        /** 默认扫描时长（毫秒） */
        const val DEFAULT_SCAN_DURATION_MS = 15_000L
    }
}
