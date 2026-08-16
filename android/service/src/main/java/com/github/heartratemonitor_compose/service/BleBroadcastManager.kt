package com.github.heartratemonitor_compose.service

import android.content.Context
import com.github.heartratemonitor_compose.ble.BleState
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * 将 JSON 构造与 200ms 节流逻辑从 [BleService] 中剥离，
 * 注入函数便于单元测试捕获广播内容。
 */
class BleBroadcastManager(
    private val emitState: (String) -> Unit,
    private val heartRate: StateFlow<Int>,
    private val speed: StateFlow<Float>,
    private val bleState: StateFlow<BleState>,
    private val isDeviceConnected: () -> Boolean,
    private val context: Context
) {
    @Volatile
    private var lastBroadcastTime = 0L
    @Volatile
    private var lastSentConnected: Boolean? = null
    @Volatile
    private var lastSentStatus: String? = null
    private val broadcastMinIntervalMs = 200L

    /**
     * 200ms 节流仅针对高频心率包（~1Hz）；连接/断开、状态文案变化、心率清零等
     * 终态/迁移事件不节流——否则断开广播落在节流窗口内会被直接丢弃，
     * WS 客户端将永久停留在 connected=true 的陈旧状态。
     */
    fun broadcast() {
        val now = System.currentTimeMillis()
        val connected = isDeviceConnected()
        val status = bleState.value.getMessage(context)
        val isTransition = lastSentConnected != connected || lastSentStatus != status || heartRate.value == 0
        if (!isTransition && now - lastBroadcastTime < broadcastMinIntervalMs) return
        lastBroadcastTime = now
        lastSentConnected = connected
        lastSentStatus = status

        val json = JSONObject().apply {
            put("heart_rate", heartRate.value)
            put("connected", connected)
            put("status", status)
            put("timestamp", System.currentTimeMillis())
            put("speed", speed.value)
        }
        emitState(json.toString())
    }
}
