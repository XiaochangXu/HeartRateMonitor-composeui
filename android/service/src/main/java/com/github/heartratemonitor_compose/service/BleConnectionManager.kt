package com.github.heartratemonitor_compose.service

import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.juul.kable.Advertisement
import kotlinx.coroutines.flow.StateFlow

/**
 * 原为 [BleService] 嵌套类，提升为顶层类型，
 * 使消费方不再依赖具体 Service 类。
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

    val scanResults: StateFlow<List<Advertisement>>

    val connectedDevice: StateFlow<ConnectedDevice?>

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
