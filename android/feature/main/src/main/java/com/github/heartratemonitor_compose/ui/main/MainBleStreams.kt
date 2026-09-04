package com.github.heartratemonitor_compose.ui.main

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.service.HeartRateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.collections.immutable.toImmutableList

/**
 * 按域拆分 MainViewModel 的 BLE 数据管道订阅。敏感语义保留：
 * supervisorScope 隔离各订阅异常；CancellationException 重 throw；
 * manualConnectionPending 防竞态；drop(1) 防 StateFlow 首次重放。
 */
internal fun MainViewModel.bindRepositoryStreams(repository: HeartRateRepository): Job {
    return viewModelScope.launch {
        // supervisorScope：任一订阅异常只终止自身，不级联取消其余数据管道，
        // 避免单个 collector 抛异常导致心率/状态/扫描结果全部永久停更。
        supervisorScope {
            launch {
                try {
                    repository.heartRateMeasurement.collect { measurement ->
                        reduceState { it.copy(heartRate = measurement.bpm) }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "心率数据订阅异常终止", e)
                }
            }

            launch {
                try {
                    repository.speed.collect { reduceState { s -> s.copy(speed = it) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "速度数据订阅异常终止", e)
                }
            }

            launch {
                try {
                    repository.scanResults.collect { reduceState { s -> s.copy(scanResults = it.toImmutableList()) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "扫描结果订阅异常终止", e)
                }
            }

            launch {
                try {
                    repository.connectedDevice.collect { reduceState { s -> s.copy(connectedDevice = it) } }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "已连接设备订阅异常终止", e)
                }
            }

            // 图表流：StateFlow 重放实现「重进即恢复」
            launch {
                try {
                    combine(
                        repository.chartDataSnapshot,
                        repository.sessionMaxHr,
                        repository.sessionMinHr
                    ) { snapshot, maxHr, minHr -> Triple(snapshot, maxHr, minHr) }
                        .collect { (snapshot, maxHr, minHr) ->
                            reduceState {
                                it.copy(chartDataSnapshot = snapshot, sessionMaxHr = maxHr, sessionMinHr = minHr)
                            }
                        }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "图表数据订阅异常终止", e)
                }
            }

            launch {
                try {
                    // drop(1) 跳过 StateFlow 首次重放：避免应用重进时 bleState 当前值（状态恢复而非新事件）触发图表 reset 或 Toast。
                    repository.bleState.drop(1).collectLatest { state -> handleBleState(state) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainViewModel", "连接状态订阅异常终止", e)
                }
            }
        }
    }
}

/** BLE 状态机归约：文案/状态映射、连接中设备防竞态、Toast 联动。 */
private fun MainViewModel.handleBleState(state: BleState) {
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

    // 图表 reset/clear/极值清零已由服务层 SessionChartTracker 在连接成功处和 cleanupConnection 处理

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
