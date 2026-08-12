package com.github.heartratemonitor_compose.service

import android.content.Context
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.service.server.ServerHost
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * 负责 WebSocket 状态广播与节流。
 *
 * 将 JSON 构造与 200ms 节流逻辑从 [BleService] 中剥离，
 * [BleService] 只需在心率更新、连接/断开等时机调用 [broadcast]。
 *
 * @param serverHost 用于向 WebSocket 客户端推送状态
 * @param heartRate 当前心率 StateFlow
 * @param speed 当前速度 StateFlow
 * @param bleState 当前 BLE 状态 StateFlow
 * @param isDeviceConnected 返回设备是否已连接的回调
 * @param context 用于解析 BleState 的消息资源 ID
 */
class BleBroadcastManager(
    private val serverHost: ServerHost,
    private val heartRate: StateFlow<Int>,
    private val speed: StateFlow<Float>,
    private val bleState: StateFlow<BleState>,
    private val isDeviceConnected: () -> Boolean,
    private val context: Context
) {
    @Volatile
    private var lastBroadcastTime = 0L
    private val broadcastMinIntervalMs = 200L

    /**
     * 广播当前状态给所有已连接的 WebSocket 客户端。
     *
     * 内置 200ms 节流，避免心率（~1s）和位置（~1s）同时触发导致重复推送。
     */
    fun broadcast() {
        val now = System.currentTimeMillis()
        if (now - lastBroadcastTime < broadcastMinIntervalMs) return
        lastBroadcastTime = now

        val json = JSONObject().apply {
            put("heart_rate", heartRate.value)
            put("connected", isDeviceConnected())
            put("status", bleState.value.getMessage(context))
            put("timestamp", System.currentTimeMillis())
            put("speed", speed.value)
        }
        serverHost.emitState(json.toString())
    }
}
