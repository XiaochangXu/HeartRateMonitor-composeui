package com.github.heartratemonitor_compose.ui.server

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.data.network.IpAddressProvider
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.github.heartratemonitor_compose.feature.server.R
import com.github.heartratemonitor_compose.service.LanTransferSharedState
import com.github.heartratemonitor_compose.service.server.NsdDiscoverer
import com.github.heartratemonitor_compose.service.server.PairClient
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LanTransferViewModel] 单元测试（MVI dispatch 形态）：
 * 配对状态机（成功/拒绝/失败/超时分支）与扫描生命周期。
 *
 * PairClient / NsdDiscoverer 以 Fake 子类替换（open 化见各自类注释），不发起真实网络/NSD。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LanTransferViewModelTest {

    private lateinit var context: android.app.Application
    private lateinit var settings: SettingsRepository
    private lateinit var sharedState: LanTransferSharedState
    private lateinit var fakePairClient: FakePairClient
    private lateinit var fakeDiscoverer: FakeNsdDiscoverer

    private class FakePairClient : PairClient() {
        /** 可配置的响应；null 表示挂起（用于超时分支） */
        var scripted: PairResponse? = PairResponse.Approved("session-1")
        var requestCount = 0
            private set

        override suspend fun request(
            pcHost: String,
            pcPairPort: Int,
            request: PairRequest
        ): PairResponse {
            requestCount++
            val resp = scripted
            if (resp == null) {
                delay(60_000)
                return PairResponse.Approved("should-not-reach")
            }
            return resp
        }
    }

    private class FakeNsdDiscoverer(context: Context) : NsdDiscoverer(context) {
        var scripted: Flow<List<DiscoveredPc>> = flowOf(emptyList())
        override fun discover(): Flow<List<DiscoveredPc>> = scripted
    }

    private val testPc = NsdDiscoverer.DiscoveredPc(
        name = "DESKTOP-TEST",
        host = "192.168.1.50",
        pairPort = 52000,
        serviceName = "hr-pc._heartrate._tcp."
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settings = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
        sharedState = LanTransferSharedState()
        fakePairClient = FakePairClient()
        fakeDiscoverer = FakeNsdDiscoverer(context)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): LanTransferViewModel = LanTransferViewModel(
        settings = settings,
        ipAddressProvider = IpAddressProvider(context),
        lanTransferSharedState = sharedState,
        nsdDiscoverer = fakeDiscoverer,
        pairClient = fakePairClient,
        appContext = context
    )

    @Test
    fun `pairing approved sets result without error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        settings.set(SettingsKeys.WEBSOCKET_SERVER_ENABLED, true)
        val viewModel = createViewModel()
        runCurrent()

        fakePairClient.scripted = PairClient.PairResponse.Approved("session-1")
        viewModel.dispatch(LanTransferIntent.StartPairing(testPc))

        runCurrent()
        // dispatch 异步归约：请求瞬时完成，终态为结果已置位、进行中已清除
        assertThat(viewModel.uiState.value.pairResult)
            .isEqualTo(PairClient.PairResponse.Approved("session-1"))
        assertThat(viewModel.uiState.value.pairError).isNull()
        assertThat(viewModel.uiState.value.pairingPc).isNull()
    }

    @Test
    fun `pairing rejected sets rejected error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        settings.set(SettingsKeys.WEBSOCKET_SERVER_ENABLED, true)
        val viewModel = createViewModel()
        runCurrent()

        fakePairClient.scripted = PairClient.PairResponse.Rejected
        viewModel.dispatch(LanTransferIntent.StartPairing(testPc))
        runCurrent()

        assertThat(viewModel.uiState.value.pairResult).isEqualTo(PairClient.PairResponse.Rejected)
        assertThat(viewModel.uiState.value.pairError)
            .isEqualTo(context.getString(R.string.lan_pair_rejected))
    }

    @Test
    fun `pairing network failure sets failed error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        settings.set(SettingsKeys.WEBSOCKET_SERVER_ENABLED, true)
        val viewModel = createViewModel()
        runCurrent()

        fakePairClient.scripted = PairClient.PairResponse.Failed("conn refused")
        viewModel.dispatch(LanTransferIntent.StartPairing(testPc))
        runCurrent()

        val error = viewModel.uiState.value.pairError
        assertThat(error).isNotNull()
        assertThat(error).contains("conn refused")
    }

    @Test
    fun `pairing timeout yields timeout failure`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        settings.set(SettingsKeys.WEBSOCKET_SERVER_ENABLED, true)
        val viewModel = createViewModel()
        runCurrent()

        // null 脚本 = 请求挂起 60s，超过 VM 的 35s 超时上限
        fakePairClient.scripted = null
        viewModel.dispatch(LanTransferIntent.StartPairing(testPc))
        runCurrent()
        assertThat(viewModel.uiState.value.pairingPc).isEqualTo(testPc)

        advanceTimeBy(35_000)
        runCurrent()

        assertThat(viewModel.uiState.value.pairResult)
            .isEqualTo(PairClient.PairResponse.Failed(context.getString(R.string.lan_pair_timeout)))
        assertThat(viewModel.uiState.value.pairingPc).isNull()
    }

    @Test
    fun `pairing blocked when ws server disabled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        // 默认 wsEnabled=false
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(LanTransferIntent.StartPairing(testPc))
        runCurrent()

        assertThat(fakePairClient.requestCount).isEqualTo(0)
        assertThat(viewModel.uiState.value.pairError)
            .isEqualTo(context.getString(R.string.lan_ws_not_enabled))
        assertThat(viewModel.uiState.value.pairResult).isNull()
    }

    @Test
    fun `scan lifecycle updates devices and scanning flag`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fakeDiscoverer.scripted = channelFlow {
            trySend(listOf(testPc))
            awaitClose { }
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(LanTransferIntent.StartScan)
        runCurrent()
        assertThat(viewModel.uiState.value.isScanning).isTrue()
        assertThat(viewModel.uiState.value.devices).containsExactly(testPc)

        viewModel.dispatch(LanTransferIntent.StopScan)
        runCurrent()
        assertThat(viewModel.uiState.value.isScanning).isFalse()
    }

    @Test
    fun `connecting client stops scan and blocks new scan`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        fakeDiscoverer.scripted = channelFlow {
            trySend(listOf(testPc))
            awaitClose { }
        }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(LanTransferIntent.StartScan)
        runCurrent()
        assertThat(viewModel.uiState.value.isScanning).isTrue()

        // PC 连接建立：init 联动停止扫描
        sharedState.webSocketClientCount.value = 1
        runCurrent()
        assertThat(viewModel.uiState.value.isConnected).isTrue()
        assertThat(viewModel.uiState.value.isScanning).isFalse()

        // 已连接时禁止再扫描
        viewModel.dispatch(LanTransferIntent.StartScan)
        runCurrent()
        assertThat(viewModel.uiState.value.isScanning).isFalse()

        // 断开连接：发现列表清空
        sharedState.webSocketClientCount.value = 0
        runCurrent()
        assertThat(viewModel.uiState.value.devices).isEmpty()
    }

    @Test
    fun `disconnect clears state and invokes client disconnect callback`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var callbackInvoked = false
        sharedState.disconnectWebSocketClients = { callbackInvoked = true }
        val viewModel = createViewModel()
        runCurrent()

        viewModel.dispatch(LanTransferIntent.Disconnect)
        runCurrent()

        assertThat(callbackInvoked).isTrue()
        assertThat(viewModel.uiState.value.pairResult).isNull()
        assertThat(viewModel.uiState.value.pairError).isNull()
        assertThat(viewModel.uiState.value.devices).isEmpty()
    }
}
