package com.github.heartratemonitor_compose.service

import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.service.server.ServerHost
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [BleBroadcastManager] 单元测试。
 *
 * 验证：
 * - broadcast() 在各种 BleState 下不抛异常
 * - 不同心率/速度/连接状态下不抛异常
 * - 200ms 节流：连续快速调用不抛异常
 * - 节流后间隔 >200ms 的调用正常工作
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BleBroadcastManagerTest {

    private lateinit var context: android.app.Application
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var serverHost: ServerHost

    private val heartRateFlow = MutableStateFlow(0)
    private val speedFlow = MutableStateFlow(0f)
    private val bleStateFlow = MutableStateFlow<BleState>(BleState.Idle)
    private val clientCountFlow = MutableStateFlow(0)
    private var connected = false

    private lateinit var broadcastManager: BleBroadcastManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences(PrefsKeys.FILE_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().clear().apply()

        serverHost = ServerHost(
            prefs = prefs,
            heartRate = heartRateFlow,
            speed = speedFlow,
            isDeviceConnected = { connected },
            getStatusMessage = { bleStateFlow.value.getMessage(context) },
            webSocketClientCount = clientCountFlow
        )

        broadcastManager = BleBroadcastManager(
            serverHost = serverHost,
            heartRate = heartRateFlow,
            speed = speedFlow,
            bleState = bleStateFlow,
            isDeviceConnected = { connected },
            context = context
        )
    }

    // ── 基本 broadcast ──

    @Test
    fun `broadcast with Idle state does not throw`() {
        bleStateFlow.value = BleState.Idle
        broadcastManager.broadcast()
        // 不抛异常即通过
    }

    @Test
    fun `broadcast with Scanning state does not throw`() {
        bleStateFlow.value = BleState.Scanning
        broadcastManager.broadcast()
    }

    @Test
    fun `broadcast with Connected state does not throw`() {
        bleStateFlow.value = BleState.Connected("Connected to Device")
        connected = true
        broadcastManager.broadcast()
    }

    @Test
    fun `broadcast with Disconnected state does not throw`() {
        bleStateFlow.value = BleState.Disconnected("Device disconnected")
        connected = false
        broadcastManager.broadcast()
    }

    @Test
    fun `broadcast with ScanFailed state does not throw`() {
        bleStateFlow.value = BleState.ScanFailed("No devices found")
        broadcastManager.broadcast()
    }

    @Test
    fun `broadcast with AutoReconnecting state does not throw`() {
        bleStateFlow.value = BleState.AutoReconnecting
        broadcastManager.broadcast()
    }

    // ── 不同数据值 ──

    @Test
    fun `broadcast with high heart rate does not throw`() {
        heartRateFlow.value = 220
        speedFlow.value = 30.5f
        bleStateFlow.value = BleState.Connected("Connected")
        connected = true
        broadcastManager.broadcast()
    }

    @Test
    fun `broadcast with zero heart rate does not throw`() {
        heartRateFlow.value = 0
        speedFlow.value = 0f
        bleStateFlow.value = BleState.Idle
        connected = false
        broadcastManager.broadcast()
    }

    // ── 节流 ──

    @Test
    fun `multiple rapid broadcasts do not throw`() {
        // 连续快速调用 10 次，节流应跳过大部分，但不抛异常
        repeat(10) {
            broadcastManager.broadcast()
        }
    }

    @Test
    fun `broadcast after 200ms interval works without throwing`() {
        broadcastManager.broadcast()
        // 等待节流窗口过期
        Thread.sleep(250)
        broadcastManager.broadcast()
        // 不抛异常即通过
    }

    @Test
    fun `broadcast with changing states over time does not throw`() {
        bleStateFlow.value = BleState.Idle
        broadcastManager.broadcast()

        Thread.sleep(250)
        bleStateFlow.value = BleState.Scanning
        broadcastManager.broadcast()

        Thread.sleep(250)
        bleStateFlow.value = BleState.Connecting
        broadcastManager.broadcast()

        Thread.sleep(250)
        bleStateFlow.value = BleState.Connected("Connected to HR Monitor")
        connected = true
        heartRateFlow.value = 75
        broadcastManager.broadcast()

        Thread.sleep(250)
        bleStateFlow.value = BleState.Disconnected("Disconnected")
        connected = false
        heartRateFlow.value = 0
        broadcastManager.broadcast()
    }
}
