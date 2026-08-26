package com.github.heartratemonitor_compose.ui.main

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.service.BleConnectionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.collections.immutable.toImmutableList

/**
 * Phase 5 按域拆分，契约 4。自原 MainViewModel.initializeDataStreams 逐行迁出，
 * 敏感语义原样保留：
 * supervisorScope 隔离各订阅异常；CancellationException 重throw；
 * manualConnectionPending 防竞态；断开时立即清零极值 + 清空图表；
 * BLE 状态 → 一次性 Toast 经 bleToastListener 回调（§3.4 方案 1）。
 */
internal fun MainViewModel.bindBleDataStreams(manager: BleConnectionManager): Job {
    return viewModelScope.launch {
        // supervisorScope：任一订阅异常只终止自身，不级联取消其余数据管道，
        // 避免单个 collector 抛异常导致心率/状态/扫描结果全部永久停更。
        supervisorScope {
            launch {
                try {
                    manager.heartRateMeasurement.collect { measurement ->
                        reduceState { it.copy(heartRate = measurement.bpm) }
                        if (measurement.bpm > 0 && stateSnapshot.appStatus == AppStatus.CONNECTED) {
                            chartDataManager.onMeasurement(measurement)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "心率数据订阅异常终止", e)
                }
            }

            launch {
                try {
                    manager.speed.collect { reduceState { s -> s.copy(speed = it) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "速度数据订阅异常终止", e)
                }
            }

            launch {
                try {
                    manager.scanResults.collect { reduceState { s -> s.copy(scanResults = it.toImmutableList()) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "扫描结果订阅异常终止", e)
                }
            }

            launch {
                try {
                    manager.connectedDevice.collect { reduceState { s -> s.copy(connectedDevice = it) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "已连接设备订阅异常终止", e)
                }
            }

            launch {
                try {
                    // drop(1) 跳过 StateFlow 首次重放：应用重进时 bleState 的当前值
                    // （如 Connected）会被新订阅者立即收到，但这是状态恢复而非新事件，
                    // 不应触发图表 reset 或「已连接」Toast。与项目中 BleSettingsListener /
                    // StatusBarResidentService 等的 drop(1) 模式一致。
                    manager.bleState.drop(1).collectLatest { state -> handleBleState(state) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "连接状态订阅异常终止", e)
                }
            }
        }
    }
}

/** BLE 状态机归约：文案/状态映射、图表联动、连接中设备防竞态、Toast 联动。 */
private fun MainViewModel.handleBleState(state: BleState) {
    val oldStatus = stateSnapshot.appStatus
    Log.d(
        "MainViewModel",
        "bleState: ${state.javaClass.simpleName}, manualPending=$manualConnectionPending, " +
            "connectingId=${stateSnapshot.connectingDeviceId}"
    )
    val statusMessage = state.getMessage(appContext)
    val newStatus = when (state) {
        is BleState.Scanning -> AppStatus.SCANNING
        is BleState.AutoConnecting, is BleState.Connecting, is BleState.AutoReconnecting -> AppStatus.CONNECTING
        is BleState.Connected -> AppStatus.CONNECTED
        is BleState.BluetoothDisabled -> AppStatus.DISCONNECTED
        else -> AppStatus.DISCONNECTED
    }

    if (oldStatus != AppStatus.CONNECTED && newStatus == AppStatus.CONNECTED) {
        chartDataManager.isConnected = true
        chartDataManager.reset()
    }

    if (oldStatus == AppStatus.CONNECTED && newStatus != AppStatus.CONNECTED) {
        chartDataManager.isConnected = false
        // 断开时立即清零极值 + 清空图表数据/snapshot，避免重连时残留旧曲线
        chartDataManager.resetSessionExtremes()
        chartDataManager.clear()
    }

    // 手动连接中途可能收到自动重连扫描的 ScanFailed（DISCONNECTED），
    // 此时 manualConnectionPending=true，不能清空 connectingDeviceId，
    // 否则后续 Connecting 到达时已丢失设备信息，动画不会显示。
    val keepConnectingId = newStatus == AppStatus.CONNECTING || manualConnectionPending
    if (newStatus == AppStatus.CONNECTING) {
        Log.d(
            "MainViewModel",
            "CONNECTING reached, clearing manualPending, connectingId=${stateSnapshot.connectingDeviceId}"
        )
        manualConnectionPending = false
    } else if (manualConnectionPending) {
        Log.d("MainViewModel", "keeping connectingDeviceId=${stateSnapshot.connectingDeviceId} (manualPending=true)")
    } else {
        Log.d("MainViewModel", "clearing connectingDeviceId (newStatus=$newStatus)")
    }

    // 联动字段一次归约，UI 不收到中间态
    reduceState {
        it.copy(
            statusMessage = statusMessage,
            appStatus = newStatus,
            connectingDeviceId = if (keepConnectingId) it.connectingDeviceId else null
        )
    }

    // BLE 状态 → 一次性 Toast 联动（原 MainActivity.observeBleState 迁入）
    // AutoConnecting 仅由「启动自动连接收藏设备」流程设置（自动重连路径维持 AutoReconnecting，
    // 不会落到 AutoConnecting），因此 prev=AutoConnecting 即可精确判定启动自动连接失败。
    when (state) {
        is BleState.Connected -> bleToastListener?.invoke(BleToastEvent.CONNECTED)
        is BleState.AutoReconnecting -> bleToastListener?.invoke(BleToastEvent.AUTO_RECONNECTING)
        is BleState.BluetoothDisabled ->
            bleToastListener?.invoke(BleToastEvent.BLUETOOTH_DISABLED)
        is BleState.ScanFailed -> {
            when {
                previousBleState is BleState.AutoReconnecting ->
                    bleToastListener?.invoke(BleToastEvent.RECONNECT_FAILED)
                // 启动自动连接扫描窗口内未找到收藏设备
                previousBleState is BleState.AutoConnecting ->
                    bleToastListener?.invoke(BleToastEvent.AUTO_CONNECT_FAILED)
            }
        }
        // 启动自动连接已找到设备但连接失败/超时：connectToDevice 走 Disconnected 分支
        is BleState.Disconnected -> {
            if (previousBleState is BleState.AutoConnecting) {
                bleToastListener?.invoke(BleToastEvent.AUTO_CONNECT_FAILED)
            }
        }
        else -> { /* 在其他状态下不显示 Toast */ }
    }
    previousBleState = state
}
