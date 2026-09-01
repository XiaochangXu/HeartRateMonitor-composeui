package com.github.heartratemonitor_compose.service

import androidx.datastore.preferences.core.edit
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.github.heartratemonitor_compose.ble.BleManager
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.db.HeartRateDao
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SETTINGS_FILE_NAME
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.github.heartratemonitor_compose.data.webhook.WebhookRepository
import com.google.common.truth.Truth.assertThat
import com.juul.kable.Characteristic
import com.juul.kable.Descriptor
import com.juul.kable.DiscoveredService
import com.juul.kable.ExperimentalKableApi
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.WriteType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * [BleConnectionHandler] BLE 连接状态机测试。
 *
 * 回归覆盖（本次审查修复的两个 bug）：
 * - 首连失败不得触发自动重连（修复前 lastConnectedDeviceId 在 connect() 成功前登记，
 *   失败的首连会进入退避+反复扫描的重连循环）
 * - 连接成功后的链路丢失才触发自动重连
 * - 手动断开不触发自动重连
 *
 * 说明：连接状态机含 withTimeout / withContext(NonCancellable) / 子协程嵌套，
 * 虚拟时间调度不可靠，测试使用真实时间 + 状态轮询。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BleConnectionHandlerTest {

    private lateinit var context: android.app.Application
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var database: AppDatabase
    private lateinit var dao: HeartRateDao
    private lateinit var webhookRepository: WebhookRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(SETTINGS_FILE_NAME, android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking { context.settingsDataStore.edit { it.clear() } }
        settingsRepository = SettingsRepository(context, CoroutineScope(Dispatchers.Unconfined))
        webhookRepository = WebhookRepository(context, settingsRepository)

        database = Room.inMemoryDatabaseBuilder<AppDatabase>(context)
            .setQueryCoroutineContext(Dispatchers.IO)
            .allowMainThreadQueries()
            .build()
        dao = database.heartRateDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createHandler(
        scope: CoroutineScope,
        peripheral: FakePeripheral
    ): BleConnectionHandler {
        // 生产环境 Hilt 注入同一 @Singleton；测试同样共享单实例，保证写入面互通
        val repository = HeartRateRepository(settingsRepository)
        return BleConnectionHandler(
            context = context,
            bleManager = BleManager(),
            settingsRepository = settingsRepository,
            webhookRepository = webhookRepository,
            heartRateRecorder = HeartRateRecorder(settingsRepository, dao, scope),
            speedProvider = SpeedProvider(context, settingsRepository, repository),
            broadcast = {},
            freshnessTracker = HeartRateFreshnessTracker(scope),
            repository = repository,
            scope = scope,
            peripheralFactory = { _, _ -> peripheral }
        )
    }

    /** 轮询等待 bleState 满足条件（真实时间），超时抛 AssertionError。 */
    private suspend fun awaitBleState(
        handler: BleConnectionHandler,
        condition: (BleState) -> Boolean,
        description: String,
        timeoutMs: Long = 5_000,
        diagnostics: () -> String = { "" }
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition(handler.bleState.value)) return
            delay(50)
        }
        throw AssertionError(
            "等待状态超时: $description（当前 ${handler.bleState.value.javaClass.simpleName}）${diagnostics()}"
        )
    }

    @Test
    fun `first connect failure does not trigger auto reconnect`() = runBlocking {
        settingsRepository.set(SettingsKeys.AUTO_RECONNECT_ENABLED, true)
        val fake = FakePeripheral("AA:BB").apply {
            connectError = IOException("connection refused")
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val handler = createHandler(scope, fake)

        handler.connectToDevice("AA:BB")

        // 连接失败 → Disconnected
        awaitBleState(handler, { it is BleState.Disconnected }, "连接失败进入 Disconnected")
        // 回归：等待超过首轮退避时间（1s），不得进入自动重连状态机
        // （修复前 lastConnectedDeviceId 在 connect() 成功前已登记，此处会变为 AutoReconnecting）
        delay(2_500)
        assertThat(handler.bleState.value).isInstanceOf(BleState.Disconnected::class.java)
        assertThat(handler.bleState.value)
            .isNotInstanceOf(BleState.AutoReconnecting::class.java)
        assertThat(handler.bleState.value)
            .isNotInstanceOf(BleState.AutoConnecting::class.java)

        scope.cancel()
    }

    @Test
    fun `successful connect then link loss triggers auto reconnect`() = runBlocking {
        settingsRepository.set(SettingsKeys.AUTO_RECONNECT_ENABLED, true)
        val fake = FakePeripheral("AA:BB")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val handler = createHandler(scope, fake)

        handler.connectToDevice("AA:BB")

        // 连接成功：状态与已连接设备信息就绪
        awaitBleState(handler, { it is BleState.Connected }, "连接成功")
        assertThat(handler.connectedDevice.value?.id).isEqualTo("AA:BB")
        assertThat(handler.isDeviceConnected()).isTrue()

        // 设备侧链路丢失
        fake.simulateDisconnect()

        // 曾成功连接过 → 退避 1s 后进入自动重连
        awaitBleState(
            handler,
            { it is BleState.Disconnected },
            "链路丢失进入 Disconnected"
        )
        awaitBleState(
            handler,
            { it is BleState.AutoReconnecting },
            "退避后进入 AutoReconnecting",
            timeoutMs = 5_000
        ) { 
            // 诊断：把私有状态带进失败消息
            val lastId = BleConnectionHandler::class.java
                .getDeclaredField("lastConnectedDeviceId").apply { isAccessible = true }
                .get(handler)
            val attempt = BleConnectionHandler::class.java
                .getDeclaredField("autoReconnectAttempt").apply { isAccessible = true }
                .get(handler)
            val epoch = BleConnectionHandler::class.java
                .getDeclaredField("connectEpoch").apply { isAccessible = true }
                .get(handler)
            val manual = BleConnectionHandler::class.java
                .getDeclaredField("isManuallyDisconnected").apply { isAccessible = true }
                .get(handler)
            "lastConnectedDeviceId=$lastId attempt=$attempt epoch=$epoch manual=$manual"
        }

        scope.cancel()
    }

    @Test
    fun `manual disconnect does not auto reconnect`() = runBlocking {
        settingsRepository.set(SettingsKeys.AUTO_RECONNECT_ENABLED, true)
        val fake = FakePeripheral("AA:BB")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val handler = createHandler(scope, fake)

        handler.connectToDevice("AA:BB")
        awaitBleState(handler, { it is BleState.Connected }, "连接成功")

        handler.disconnectDevice()
        awaitBleState(handler, { it is BleState.Disconnected }, "手动断开进入 Disconnected")

        // 手动断开：即使曾连接成功也不自动重连
        delay(2_500)
        assertThat(handler.bleState.value).isInstanceOf(BleState.Disconnected::class.java)
        assertThat(handler.bleState.value)
            .isNotInstanceOf(BleState.AutoReconnecting::class.java)

        scope.cancel()
    }

    @Test
    fun `disconnect clears connected device info and heart rate`() = runBlocking {
        settingsRepository.set(SettingsKeys.AUTO_RECONNECT_ENABLED, false)
        val fake = FakePeripheral("AA:BB")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val handler = createHandler(scope, fake)

        handler.connectToDevice("AA:BB")
        awaitBleState(handler, { it is BleState.Connected }, "连接成功")
        assertThat(handler.connectedDevice.value?.id).isEqualTo("AA:BB")

        fake.simulateDisconnect()
        awaitBleState(handler, { it is BleState.Disconnected }, "链路丢失")

        assertThat(handler.connectedDevice.value).isNull()
        assertThat(handler.heartRate.value).isEqualTo(0)

        scope.cancel()
    }

    // ── kable Peripheral 的测试替身 ──

    /**
     * 可编程的 [Peripheral] fake：
     * - connect() 可选注入异常（模拟连接失败）
     * - state 流可驱动（连接成功 / 设备侧断开）
     * - 其余 GATT 操作返回空实现
     */
    @OptIn(ExperimentalKableApi::class)
    private class FakePeripheral(
        override val identifier: String,
        override val name: String? = "Fake Device"
    ) : Peripheral {

        override val scope: CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        private val _state = MutableStateFlow<State>(State.Disconnected(null))
        override val state: StateFlow<State> = _state.asStateFlow()

        override val services: StateFlow<List<DiscoveredService>> =
            MutableStateFlow(emptyList())

        /** 非空时 connect() 抛出该异常（模拟连接失败）。 */
        var connectError: Exception? = null

        override suspend fun connect(): CoroutineScope {
            connectError?.let { throw it }
            _state.value = State.Connected(scope)
            return scope
        }

        override suspend fun disconnect() {
            _state.value = State.Disconnected(State.Disconnected.Status.Cancelled)
        }

        /** 模拟设备侧链路丢失。 */
        fun simulateDisconnect() {
            _state.value = State.Disconnected(State.Disconnected.Status.PeripheralDisconnected)
        }

        override suspend fun maximumWriteValueLengthForType(type: WriteType): Int = 20
        override suspend fun rssi(): Int = -42
        override suspend fun read(characteristic: Characteristic): ByteArray = byteArrayOf()
        override suspend fun write(characteristic: Characteristic, data: ByteArray, type: WriteType) {}
        override suspend fun read(descriptor: Descriptor): ByteArray = byteArrayOf()
        override suspend fun write(descriptor: Descriptor, data: ByteArray) {}
        // 永不发射的流：collect 挂起直到取消——若返回 emptyFlow()，
        // observeHeartRateData 的 while 循环会立即完成并无限重订阅，造成忙循环/OOM
        override fun observe(characteristic: Characteristic, onWrite: suspend () -> Unit): Flow<ByteArray> =
            MutableSharedFlow()
        override fun close() = scope.cancel()
    }
}
