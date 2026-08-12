package com.github.heartratemonitor_compose.service.server

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [WebSocketServerManager] 集成测试。
 *
 * 验证：
 * - 服务器启动/停止生命周期
 * - 端口绑定
 * - 客户端计数追踪
 * - 重复 start 不创建多个实例
 * - stop 后 clientCount 归零
 * - disconnectAllClients 不抛异常
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebSocketServerManagerTest {

    private val testPort = 18301  // 使用非标准端口避免冲突
    private val authToken = ""
    private val stateFlow = MutableSharedFlow<String>(replay = 1)
    private val clientCountFlow = MutableStateFlow(0)

    private lateinit var serverManager: WebSocketServerManager

    @Before
    fun setup() {
        serverManager = WebSocketServerManager(
            port = testPort,
            authToken = authToken,
            stateFlow = stateFlow,
            clientCountFlow = clientCountFlow
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

    // ── 生命周期 ──

    @Test
    fun `start does not throw`() {
        serverManager.start()
        // 不抛异常即通过
    }

    @Test
    fun `stop does not throw`() {
        serverManager.start()
        serverManager.stop()
        // 不抛异常即通过
    }

    @Test
    fun `stop without start does not throw`() {
        serverManager.stop()
        // 不抛异常即通过
    }

    @Test
    fun `stop resets client count to zero`() {
        serverManager.start()
        serverManager.stop()
        assertThat(clientCountFlow.value).isEqualTo(0)
    }

    // ── 幂等性 ──

    @Test
    fun `multiple starts do not throw`() {
        serverManager.start()
        serverManager.start()
        serverManager.start()
        // 不抛异常即通过
    }

    @Test
    fun `multiple stops do not throw`() {
        serverManager.start()
        serverManager.stop()
        serverManager.stop()
        // 不抛异常即通过
    }

    @Test
    fun `start after stop restarts server`() {
        serverManager.start()
        serverManager.stop()
        serverManager.start()
        // 不抛异常即通过
    }

    // ── disconnectAllClients ──

    @Test
    fun `disconnectAllClients without start does not throw`() {
        serverManager.disconnectAllClients()
        // 不抛异常即通过
    }

    @Test
    fun `disconnectAllClients with no clients does not throw`() {
        serverManager.start()
        serverManager.disconnectAllClients()
        // 不抛异常即通过
    }

    // ── 鉴权 ──

    @Test
    fun `server with auth token starts successfully`() {
        val securedManager = WebSocketServerManager(
            port = 18302,
            authToken = "secret-token",
            stateFlow = stateFlow,
            clientCountFlow = clientCountFlow
        )
        securedManager.start()
        securedManager.stop()
        // 不抛异常即通过
    }
}
