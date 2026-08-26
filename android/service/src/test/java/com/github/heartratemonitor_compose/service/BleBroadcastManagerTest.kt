package com.github.heartratemonitor_compose.service

import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.ble.BleState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [BleBroadcastManager] 行为测试（升级自原"不抛异常"烟雾测试）。
 *
 * 验证：
 * - 200ms 节流仅作用于高频心率包：相同状态连续广播只发一条
 * - 连接/断开迁移、状态文案变化、心率清零等终态事件不被节流（回归：
 *   旧实现直接丢弃节流窗口内的断开广播，WS 客户端停留在陈旧连接态）
 * - 节流窗口过后恢复广播
 * - 广播 JSON 字段正确性
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BleBroadcastManagerTest {

    private lateinit var context: android.app.Application

    private val heartRateFlow = MutableStateFlow(0)
    private val speedFlow = MutableStateFlow(0f)
    private val bleStateFlow = MutableStateFlow<BleState>(BleState.Idle)
    private var connected = false

    private val emitted = mutableListOf<String>()

    private lateinit var broadcastManager: BleBroadcastManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        emitted.clear()
        connected = false
        heartRateFlow.value = 0
        speedFlow.value = 0f
        bleStateFlow.value = BleState.Idle

        broadcastManager = BleBroadcastManager(
            emitState = { json -> emitted.add(json) },
            heartRate = heartRateFlow,
            speed = speedFlow,
            bleState = bleStateFlow,
            isDeviceConnected = { connected },
            context = context
        )
    }

    private fun connectedState(rate: Int = 75) {
        connected = true
        heartRateFlow.value = rate
        bleStateFlow.value = BleState.Connected("Connected to Device")
    }

    private fun disconnectedState() {
        connected = false
        heartRateFlow.value = 0
        bleStateFlow.value = BleState.Disconnected("Device disconnected")
    }

    // ── 节流语义 ──

    @Test
    fun `rapid same-state broadcasts are throttled to one`() {
        connectedState()
        repeat(5) { broadcastManager.broadcast() }
        assertThat(emitted).hasSize(1)
    }

    @Test
    fun `disconnect transition within throttle window is never dropped`() {
        connectedState()
        broadcastManager.broadcast()

        // 紧接（<200ms）断开：断开广播不得被节流丢弃（回归测试）
        disconnectedState()
        broadcastManager.broadcast()

        assertThat(emitted).hasSize(2)
    }

    @Test
    fun `connect transition within throttle window is never dropped`() {
        disconnectedState()
        broadcastManager.broadcast()

        connectedState()
        broadcastManager.broadcast()

        assertThat(emitted).hasSize(2)
    }

    @Test
    fun `heart rate clear is never throttled`() {
        connectedState(rate = 75)
        broadcastManager.broadcast()

        // 心率清零（新鲜度 STALE 联动）也是终态事件：不得被节流吞掉
        heartRateFlow.value = 0
        broadcastManager.broadcast()

        assertThat(emitted).hasSize(2)
    }

    @Test
    fun `throttling resumes after window elapses`() {
        connectedState()
        broadcastManager.broadcast()
        assertThat(emitted).hasSize(1)

        Thread.sleep(250)
        broadcastManager.broadcast()
        assertThat(emitted).hasSize(2)

        // 窗口内再次节流
        repeat(3) { broadcastManager.broadcast() }
        assertThat(emitted).hasSize(2)
    }

    @Test
    fun `status text change is never throttled`() {
        connectedState()
        broadcastManager.broadcast()

        // 状态文案变化（如 Connected → Disconnected 文案）视为迁移，不节流
        bleStateFlow.value = BleState.Disconnected("Disconnected")
        broadcastManager.broadcast()

        assertThat(emitted).hasSize(2)
    }

    // ── 广播内容 ──

    @Test
    fun `broadcast json contains expected fields`() {
        connectedState(rate = 88)
        speedFlow.value = 1.5f
        broadcastManager.broadcast()

        assertThat(emitted).hasSize(1)
        val json = JSONObject(emitted.single())
        assertThat(json.getInt("heart_rate")).isEqualTo(88)
        assertThat(json.getBoolean("connected")).isTrue()
        assertThat(json.getDouble("speed")).isWithin(0.001).of(1.5)
        assertThat(json.getString("status")).isNotEmpty()
        assertThat(json.has("timestamp")).isTrue()
    }

    @Test
    fun `broadcast json reflects disconnected state`() {
        disconnectedState()
        broadcastManager.broadcast()

        val json = JSONObject(emitted.single())
        assertThat(json.getInt("heart_rate")).isEqualTo(0)
        assertThat(json.getBoolean("connected")).isFalse()
    }
}
