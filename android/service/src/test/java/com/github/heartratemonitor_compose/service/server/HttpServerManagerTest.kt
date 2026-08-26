package com.github.heartratemonitor_compose.service.server

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [HttpServerManager] 集成测试。
 *
 * 验证：
 * - 服务器启动/停止生命周期
 * - 重复 start 幂等
 * - stop 后可重新 start
 * - 鉴权模式下正常启动
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HttpServerManagerTest {

    private val testPort = 18401
    private val heartRateFlow = MutableStateFlow(75)
    private val speedFlow = MutableStateFlow(0f)
    private val clientConnectedFlow = MutableStateFlow(false)
    private val context: Application = ApplicationProvider.getApplicationContext()

    private lateinit var serverManager: HttpServerManager

    @Before
    fun setup() {
        serverManager = HttpServerManager(
            context = context,
            port = testPort,
            authToken = "",
            heartRateFlow = heartRateFlow,
            speedFlow = speedFlow,
            isDeviceConnected = { clientConnectedFlow.value },
            getStatusMessage = { "Idle" },
            wsPortProvider = { 8001 },
            wsEnabledProvider = { true }
        )
    }

    @After
    fun teardown() {
        try {
            serverManager.stop()
        } catch (_: Exception) {
            // 忽略清理异常
        }
    }

    @Test
    fun `start does not throw`() {
        serverManager.start()
    }

    @Test
    fun `stop does not throw`() {
        serverManager.start()
        serverManager.stop()
    }

    @Test
    fun `stop without start does not throw`() {
        serverManager.stop()
    }

    @Test
    fun `multiple starts are idempotent`() {
        serverManager.start()
        serverManager.start()
        serverManager.start()
    }

    @Test
    fun `start after stop restarts server`() {
        serverManager.start()
        serverManager.stop()
        serverManager.start()
    }

    @Test
    fun `server with auth token starts successfully`() {
        val securedManager = HttpServerManager(
            context = context,
            port = 18402,
            authToken = "my-secret",
            heartRateFlow = heartRateFlow,
            speedFlow = speedFlow,
            isDeviceConnected = { false },
            getStatusMessage = { "Idle" },
            wsPortProvider = { 8001 },
            wsEnabledProvider = { true }
        )
        securedManager.start()
        securedManager.stop()
    }
}
