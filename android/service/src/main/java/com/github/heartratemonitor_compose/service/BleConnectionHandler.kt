package com.github.heartratemonitor_compose.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import com.github.heartratemonitor_compose.service.R
import com.github.heartratemonitor_compose.ble.BleManager
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.data.model.ChartDataSnapshot
import com.github.heartratemonitor_compose.data.model.ScannedDevice
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.WebhookTrigger
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.webhook.WebhookRepository
import com.juul.kable.Advertisement
import com.juul.kable.AndroidPeripheral
import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.Phy
import com.juul.kable.State
import com.juul.kable.UnmetRequirementException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

/**
 * 驱动 [BleConnectionManager] 的连接状态机。
 * 纪元机制（connectEpoch）防止退避中的旧自动重连误取消用户刚发起的新连接。
 * 状态流宿主为进程级 [HeartRateRepository]（SSOT），本类通过 set 系列方法写入。
 */
class BleConnectionHandler(
    private val context: Context,
    private val bleManager: BleManager,
    private val settingsRepository: SettingsRepository,
    private val webhookRepository: WebhookRepository,
    private val heartRateRecorder: HeartRateRecorder,
    private val speedProvider: SpeedProvider,
    private val broadcast: () -> Unit,
    private val freshnessTracker: HeartRateFreshnessTracker,
    private val scope: CoroutineScope,
    /** 进程级状态流 SSOT。 */
    private val repository: HeartRateRepository,
    private val peripheralFactory: (String, PeripheralBuilder.() -> Unit) -> Peripheral =
        { identifier, builder -> Peripheral(identifier, builder) }
) : BleConnectionManager {

    override val bleState: StateFlow<BleState> get() = repository.bleState

    override val heartRate: StateFlow<Int> get() = repository.heartRate

    override val heartRateMeasurement: StateFlow<HeartRateMeasurement> get() = repository.heartRateMeasurement

    override val scanResults: StateFlow<List<ScannedDevice>> get() = repository.scanResults

    override val speed: StateFlow<Float> get() = repository.speed

    override val connectedDevice: StateFlow<ConnectedDevice?> get() = repository.connectedDevice

    override val chartDataSnapshot: StateFlow<ChartDataSnapshot?> get() = repository.chartDataSnapshot
    override val sessionMaxHr: StateFlow<Int> get() = repository.sessionMaxHr
    override val sessionMinHr: StateFlow<Int> get() = repository.sessionMinHr

    @Volatile
    private var connectedPeripheral: Peripheral? = null
    // connectionJob/scanJob 存在跨线程读写：checkAutoReconnect（IO 线程）会调用
    // startAutoConnectScan 写入 scanJob，自动扫描收尾（IO 线程）会调用 connectToDevice
    // 写入 connectionJob，而 stopScan/stopAllBleActivities/onBluetoothDisabled 在主线程读取，
    // 缺少 @Volatile 时无 happens-before 保证，可能读到过期引用导致取消遗漏。
    @Volatile private var connectionJob: Job? = null
    @Volatile private var scanJob: Job? = null
    @Volatile private var isManuallyDisconnected = false
    @Volatile private var isBluetoothTurningOff = false

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF) {
                onBluetoothDisabled()
            }
        }
    }

    private var bluetoothReceiverRegistered = false
    private val isScanning = AtomicBoolean(false)
    // ⚠️ 反直觉设计：仅连接成功时写入（失败/超时首连不触发自动重连）；@Volatile 保证 IO 线程间可见
    @Volatile private var lastConnectedDeviceId: String? = null
    @Volatile private var lastConnectedDeviceName: String = "Unknown Device"
    // ⚠️ 反直觉设计：纪元机制防止旧任务 finally 覆盖新连接状态
    private val connectEpoch = AtomicLong(0L)

    // ⚠️ 反直觉设计：@Volatile 保证跨线程可见；非原子递增的计数偏差由重试上限语义容忍
    @Volatile private var autoReconnectAttempt = 0

    companion object {
        private const val TAG = "BleConnectionHandler"
        private const val MAX_AUTO_RECONNECT_ATTEMPTS = 5
        private const val AUTO_RECONNECT_BASE_DELAY_MS = 1000L
        private const val AUTO_RECONNECT_MAX_DELAY_MS = 60_000L
        private const val MAX_OBSERVE_RETRY_ATTEMPTS = 5
        private const val OBSERVE_RETRY_BASE_DELAY_MS = 1000L
        /** RSSI 节流阈值：变化幅度小于此值时不更新列表，避免扫描时频繁无效重组 */
        private const val RSSI_THROTTLE_THRESHOLD = 3
    }

    fun initDeviceNameFallback(name: String) {
        lastConnectedDeviceName = name
    }

    fun clearChartCache() {
        repository.clearChart()
    }

    fun releaseChartOnTrim(notifyType: Int) {
        repository.releaseChartOnTrim(notifyType)
    }

    // ⚠️ 反直觉设计：蓝牙关闭时 GATT 回调可能不触发，导致状态卡死——监听系统广播作为补充
    fun registerBluetoothStateReceiver() {
        if (bluetoothReceiverRegistered) return
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            bluetoothStateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        bluetoothReceiverRegistered = true
    }

    fun unregisterBluetoothStateReceiver() {
        if (!bluetoothReceiverRegistered) return
        context.unregisterReceiver(bluetoothStateReceiver)
        bluetoothReceiverRegistered = false
    }

    /**
     * 蓝牙关闭时的清理：设置 BluetoothDisabled 状态 + 清空连接信息 + 清零心率，
     * 阻止自动重连（蓝牙已关，重连扫描只会立刻失败）。
     *
     * 与手动断开 [disconnectDevice] 语义一致：isManuallyDisconnected = true 阻止 checkAutoReconnect。
     * stopAllBleActivities 取消 connectionJob 后，finally 块的 cleanupConnection 会执行
     * peripheral.disconnect()/close() 释放底层资源；但纪元守卫确保共享状态只被此处清理一次
     * （onBluetoothDisabled 先于 finally 执行，自增 connectEpoch 使 finally 的纪元失配）。
     *
     * 蓝牙关闭时系统先发 STATE_TURNING_OFF 再发 STATE_OFF，本方法会被调用两次。
     * isBluetoothTurningOff 标志在首次调用时置 true，防止第二次重复清理。
     */
    private fun onBluetoothDisabled() {
        // 重入保护：STATE_TURNING_OFF → STATE_OFF 连续两次广播，第二次直接跳过
        if (isBluetoothTurningOff) {
            // 确保最终状态为 BluetoothDisabled（第二次广播时异步清理可能还没设值）
            if (repository.bleState.value !is BleState.BluetoothDisabled) {
                repository.setBleState(BleState.BluetoothDisabled)
            }
            return
        }

        // 仅在当前有活动连接/扫描时才处理，避免 Idle 状态下无意义清理
        if (connectedPeripheral == null && !isScanning.get() && connectionJob == null && scanJob == null) {
            // 无活动 BLE 任务时仍设置 BluetoothDisabled（如 Idle → 蓝牙关闭），
            // 让 UI 正确反映蓝牙状态而非保持 Idle
            if (repository.bleState.value !is BleState.BluetoothDisabled) {
                repository.setBleState(BleState.BluetoothDisabled)
            }
            return
        }
        Log.w(TAG, "Bluetooth disabled during active connection, cleaning up")
        isBluetoothTurningOff = true
        isManuallyDisconnected = true  // 阻止自动重连
        // 自增纪元使正在运行的 connectionJob/scanJob 的 finally 中纪元守卫失配，
        // 避免 finally 的 cleanupConnection 覆盖此处设置的状态
        val myEpoch = connectEpoch.incrementAndGet()
        stopAllBleActivities()
        isScanning.set(false)

        // 异步清理共享状态，不等待 connectionJob finally（蓝牙关闭后 GATT 回调可能永远不来）。
        // scope.launch 内部 withContext(NonCancellable) 保证清理不被外部取消打断；
        // BleService.onDestroy 中先 unregisterBluetoothStateReceiver 再 serviceScope.cancel，
        // 确保广播注销前不会收到新广播而 scope 已取消。
        scope.launch {
            withContext(NonCancellable) {
                // 纪元守卫：期间若用户已发起新连接/扫描则跳过
                if (connectEpoch.get() != myEpoch) {
                    Log.d(TAG, "onBluetoothDisabled: epoch mismatch, skip cleanup")
                    return@withContext
                }
                heartRateRecorder.endSession()
                repository.setBleState(BleState.BluetoothDisabled)
                webhookRepository.triggerWebhooks(
                    WebhookTrigger.DISCONNECTED,
                    repository.heartRate.value,
                    repository.speed.value
                )
                repository.setHeartRate(0)
                repository.setHeartRateMeasurement(HeartRateMeasurement.EMPTY)
                freshnessTracker.reset()
                repository.setConnectedDevice(null)
                repository.setScanResults(emptyList())
                // 蓝牙关闭时清空图表缓存与极值
                repository.resetChartExtremes()
                repository.clearChart()
                broadcast()
                connectedPeripheral = null
            }
        }
    }

    override fun isDeviceConnected(): Boolean = connectedPeripheral?.state?.value is State.Connected

    // fail-fast：避免先设 Scanning/AutoConnecting 再被 UnmetRequirementException 覆盖（消除 UI 瞬态闪烁）
    private fun isBluetoothEnabled(): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter?.isEnabled == true
    }

    /** 二级超时（STALE）联动清零：心率 + 测量源 + 广播，下游统一降级为 -- */
    fun clearHeartRateOnStale() {
        if (repository.heartRate.value > 0) {
            Log.w(TAG, "心率数据长时间未更新，判定测量失败并清零")
            repository.setHeartRate(0)
            repository.setHeartRateMeasurement(HeartRateMeasurement.EMPTY)
            broadcast()
        }
    }

    fun trimScanCacheIfIdle() {
        if (!isScanning.get()) {
            repository.setScanResults(emptyList())
            Log.i("BleService", "TRIM: 已清空蓝牙扫描缓存")
        }
    }

    override fun startScan(durationMillis: Long) {
        if (!isScanning.compareAndSet(false, true)) return
        // 先自增纪元再取消旧任务（与 onBluetoothDisabled 的顺序一致）：旧任务的 finally
        // 收尾与取消是并发执行的，若旧收尾在自增前读到纪元则守卫误判通过，其
        // isScanning.set(false) 会把此处刚设置的 true 覆盖掉，导致后续扫描被 CAS 拒绝。
        val myEpoch = connectEpoch.incrementAndGet()
        stopAllBleActivities()
        isBluetoothTurningOff = false

        val useFilter = settingsRepository.get(SettingsKeys.SCAN_FILTER_ENABLED)

        scanJob = scope.launch {
            // 前置检查：蓝牙未开启时直接设为 BluetoothDisabled，不经过 Scanning 瞬态，
            // 避免 UI 闪烁加载指示器后被立即覆盖（fail-fast，与 catch UnmetRequirementException 同语义）
            if (!isBluetoothEnabled()) {
                Log.w(TAG, "Scan aborted: bluetooth is disabled (pre-check)")
                withContext(NonCancellable) {
                    if (connectEpoch.get() != myEpoch) return@withContext
                    isScanning.set(false)
                    repository.setBleState(BleState.BluetoothDisabled)
                }
                return@launch
            }

            val foundDevicesMap = mutableMapOf<String, ScannedDevice>()
            try {
                repository.setBleState(BleState.Scanning)
                withTimeout(durationMillis) {
                    bleManager.scan(useServiceFilter = useFilter).collect { advertisement ->
                        val existing = foundDevicesMap[advertisement.identifier]
                        if (shouldUpdateScanResult(existing?.rssi, advertisement.rssi)) {
                            foundDevicesMap[advertisement.identifier] = advertisement.toScannedDevice()
                            repository.setScanResults(foundDevicesMap.values.toList())
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
            } catch (_: UnmetRequirementException) {
                // 蓝牙未开启（kable checkBluetoothIsOn 抛出），提前结束扫描。
                // finally 块的守卫（is BleState.Scanning）不会覆盖此状态。
                Log.w(TAG, "Scan aborted: bluetooth is disabled")
                withContext(NonCancellable) {
                    if (connectEpoch.get() != myEpoch) return@withContext
                    isScanning.set(false)
                    repository.setBleState(BleState.BluetoothDisabled)
                }
            } finally {
                withContext(NonCancellable) {
                    // 纪元守卫：若期间已发起新的扫描/连接，跳过状态写入与 isScanning 复位，
                    // 避免旧扫描的收尾覆盖新活动状态（新活动已接管 isScanning）。
                    if (connectEpoch.get() != myEpoch) return@withContext
                    isScanning.set(false)
                    // 仅当仍在扫描状态时才发出 ScanFailed，避免覆盖正在进行的连接状态
                    if (repository.bleState.value is BleState.Scanning) {
                        val statusMessage = if (foundDevicesMap.isNotEmpty()) context.getString(R.string.ble_scan_finished) else context.getString(R.string.ble_no_devices_found)
                        repository.setBleState(BleState.ScanFailed(statusMessage))
                    }
                }
            }
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    override fun startAutoConnectScan(favoriteDeviceId: String, durationMillis: Long) {
        if (!isScanning.compareAndSet(false, true)) return
        // 先自增纪元再取消旧任务，理由同 startScan：旧任务 finally 收尾的纪元守卫
        // 必须在任何时刻都读到失配值，避免旧收尾复位 isScanning 覆盖此处的新扫描。
        val myEpoch = connectEpoch.incrementAndGet()
        stopAllBleActivities()
        isBluetoothTurningOff = false

        val useFilter = settingsRepository.get(SettingsKeys.SCAN_FILTER_ENABLED)

        scanJob = scope.launch {
            // 前置检查：蓝牙未开启时直接设为 BluetoothDisabled，不经过 AutoConnecting 瞬态，
            // 避免冷启动自动连接时 UI 闪烁（fail-fast，与 catch UnmetRequirementException 同语义）
            if (!isBluetoothEnabled()) {
                Log.w(TAG, "Auto scan aborted: bluetooth is disabled (pre-check)")
                withContext(NonCancellable) {
                    if (connectEpoch.get() != myEpoch) return@withContext
                    isScanning.set(false)
                    repository.setBleState(BleState.BluetoothDisabled)
                }
                return@launch
            }

            val foundDevicesMap = mutableMapOf<String, ScannedDevice>()
            var favoriteFound = false
            if (repository.bleState.value !is BleState.AutoReconnecting) {
                repository.setBleState(BleState.AutoConnecting)
            }

            try {
                withTimeout(durationMillis) {
                    bleManager.scan(useServiceFilter = useFilter).collect { advertisement ->
                        val existing = foundDevicesMap[advertisement.identifier]
                        if (shouldUpdateScanResult(existing?.rssi, advertisement.rssi)) {
                            foundDevicesMap[advertisement.identifier] = advertisement.toScannedDevice()
                            repository.setScanResults(foundDevicesMap.values.toList())
                        }

                        if (advertisement.identifier == favoriteDeviceId) {
                            favoriteFound = true
                            this.cancel()
                        }
                    }
                }
            } catch (_: CancellationException) {
            } catch (_: UnmetRequirementException) {
                // 蓝牙未开启：覆盖 AutoConnecting 为 BluetoothDisabled，
                // finally 守卫（is AutoConnecting/AutoReconnecting）不会覆盖此状态
                Log.w(TAG, "Auto scan aborted: bluetooth is disabled")
                withContext(NonCancellable) {
                    if (connectEpoch.get() != myEpoch) return@withContext
                    isScanning.set(false)
                    repository.setBleState(BleState.BluetoothDisabled)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Auto scan error", e)
            } finally {
                withContext(NonCancellable) {
                    // 纪元守卫：期间用户发起了新的扫描/连接，旧自动扫描的收尾
                    // （连接收藏设备/发 ScanFailed/复位 isScanning）不得覆盖新活动
                    if (connectEpoch.get() != myEpoch) return@withContext
                    isScanning.set(false)
                    if (favoriteFound) {
                        Log.d(TAG, "autoScan finally: favoriteFound=true, calling connectToDevice($favoriteDeviceId)")
                        connectToDevice(favoriteDeviceId)
                    } else {
                        if (repository.bleState.value is BleState.AutoConnecting || repository.bleState.value is BleState.AutoReconnecting) {
                            Log.d(TAG, "autoScan finally: favoriteFound=false, emitting ScanFailed (currentBleState=${repository.bleState.value.javaClass.simpleName})")
                            repository.setBleState(BleState.ScanFailed(context.getString(R.string.ble_auto_connect_failed)))
                        } else {
                            Log.d(TAG, "autoScan finally: favoriteFound=false, NOT emitting ScanFailed (currentBleState=${repository.bleState.value.javaClass.simpleName})")
                        }
                    }
                }
            }
        }
    }

    override fun connectToDevice(identifier: String) {
        // 新连接意图：先自增纪元再取消旧任务（与 onBluetoothDisabled 的顺序一致），
        // 使退避中的旧自动重连检查失效；并确保旧连接/旧扫描的 finally 收尾（可能与本调用
        // 并发执行）在任何时刻读到纪元均已失配，不会覆盖新连接接管的状态。
        val myEpoch = connectEpoch.incrementAndGet()
        stopAllBleActivities()
        isManuallyDisconnected = false
        isBluetoothTurningOff = false
        // 连接是非扫描活动：无论旧扫描的收尾是否完成，扫描标志一律复位，
        // 避免「扫描中被连接打断」后 isScanning 卡死导致后续扫描静默拒绝
        isScanning.set(false)
        autoReconnectAttempt = 0  // 手动连接时重置重试计数

        connectionJob = scope.launch {
            var peripheral: Peripheral? = null
            // 状态监听协程：成功连接路径由下方 join() 收尾；失败/超时路径（withTimeout 抛
            // TimeoutCancellationException / connect 抛异常）不会走到 join()，此时它仍挂起收集
            // 旧 peripheral 的 state 流，必须显式取消，否则泄漏挂起协程与 peripheral 强引用
            var stateMonitor: Job? = null
            try {
                peripheral = peripheralFactory(identifier) {
                    // 优先 LE 2M PHY（BLE 5.0+），设备不支持时系统自动回退 1M
                    phy = Phy.Le2M
                    disconnectTimeout = 10.seconds
                    onServicesDiscovered {
                        // 协商更大 MTU，减少心率通知分包；失败时回退默认 23，不阻断连接
                        try {
                            requestMtu(517)
                        } catch (e: Exception) {
                            Log.w(TAG, "MTU negotiation failed, using default", e)
                        }
                    }
                }
                connectedPeripheral = peripheral

                if (repository.bleState.value !is BleState.AutoReconnecting) {
                    Log.d(TAG, "connectToDevice: setting BleState.Connecting for $identifier")
                    repository.setBleState(BleState.Connecting)
                } else {
                    Log.d(TAG, "connectToDevice: keeping AutoReconnecting, will use existing BleState")
                }

                stateMonitor = launch {
                    peripheral.state
                        .filter { it !is State.Disconnected || it.status != null }
                        .collect { state ->
                            handlePeripheralState(peripheral, state)
                        }
                }

                withTimeout(20_000L) {
                    peripheral.connect()
                }
                stateMonitor.join()

            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Connection to $identifier timed out", e)
                if (repository.bleState.value !is BleState.AutoReconnecting) {
                    repository.setBleState(BleState.Disconnected(context.getString(R.string.ble_connect_timeout)))
                }
            } catch (e: CancellationException) {
                // 结构化并发：真正的取消（外部 cancel / 设备断开）必须向上传播，
                // 不能被当作连接失败吞掉；清理与自动重连仍在 finally 中执行。
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Connection to $identifier failed", e)
                if (repository.bleState.value !is BleState.AutoReconnecting) {
                    repository.setBleState(BleState.Disconnected(context.getString(R.string.ble_connect_failed, e.message)))
                }
            } finally {
                withContext(NonCancellable) {
                    // 取消仍挂起的状态监听（连接失败/超时路径的泄漏源）：
                    // 成功断开路径 stateMonitor 已自行结束（CancellationException），cancel 为无害空操作
                    stateMonitor?.cancel()
                    cleanupConnection(peripheral, myEpoch)
                    checkAutoReconnect(myEpoch)
                }
            }
        }
    }

    private suspend fun handlePeripheralState(peripheral: Peripheral, state: State) {
        when (state) {
            is State.Connecting -> {
                if (repository.bleState.value !is BleState.AutoReconnecting) {
                    repository.setBleState(BleState.Connecting)
                }
            }
            is State.Connected -> {
                @OptIn(com.juul.kable.ExperimentalKableApi::class)
                val deviceName = peripheral.name ?: context.getString(R.string.unknown_device)
                // 请求高优先级连接参数，降低心率通知延迟；失败不影响连接本身
                try {
                    (peripheral as? AndroidPeripheral)?.requestConnectionPriority(AndroidPeripheral.Priority.High)
                } catch (e: Exception) {
                    Log.w(TAG, "requestConnectionPriority failed", e)
                }
                lastConnectedDeviceName = deviceName
                // 仅连接成功后登记设备 id：失败/超时的首连不得触发自动重连
                lastConnectedDeviceId = peripheral.identifier
                // 同步当前已连接设备信息（id + name）供 UI 显示
                repository.setConnectedDevice(ConnectedDevice(lastConnectedDeviceId ?: "", deviceName))
                repository.setScanResults(emptyList())
                repository.setBleState(BleState.Connected(context.getString(R.string.ble_connected_to, deviceName)))
                autoReconnectAttempt = 0  // 连接成功，重置重试计数
                webhookRepository.triggerWebhooks(WebhookTrigger.CONNECTED, speed = repository.speed.value)

                // 先确保 session 写入完成（await），再启动心率监听，避免早期数据因 session 未就绪而丢失
                heartRateRecorder.startSession(deviceName)
                // 新会话开始：重置图表缓存与极值，与 startSession 同位置
                repository.resetChartSession()
                broadcast()

                // 作为 connectionJob 的子协程启动：断开连接时随 connectionJob 取消，避免泄漏
                CoroutineScope(currentCoroutineContext()).launch { observeHeartRateData(peripheral) }
            }
            is State.Disconnecting -> repository.setBleState(BleState.Disconnected(context.getString(R.string.ble_disconnecting)))
            is State.Disconnected -> {
                throw CancellationException("Device disconnected: ${state.status}")
            }
        }
    }

    override fun disconnectDevice() {
        isManuallyDisconnected = true
        isBluetoothTurningOff = false
        stopAllBleActivities()
    }

    private fun stopAllBleActivities() {
        scanJob?.cancel()
        connectionJob?.cancel()
    }

    /**
     * RSSI 节流：仅当设备首次出现或 RSSI 变化幅度 >= [RSSI_THROTTLE_THRESHOLD] 时更新列表。
     * BLE 设备每秒广播数次，RSSI 抖动通常 ±1-2 dBm，过滤这些微小变化可大幅减少无效重组。
     */
    private fun shouldUpdateScanResult(oldRssi: Int?, newRssi: Int): Boolean {
        if (oldRssi == null) return true
        return abs(newRssi - oldRssi) >= RSSI_THROTTLE_THRESHOLD
    }

    private suspend fun cleanupConnection(peripheral: Peripheral?, epoch: Long) {
        Log.d(TAG, "cleanupConnection: isManuallyDisconnected=$isManuallyDisconnected, epoch=$epoch, currentEpoch=${connectEpoch.get()}")
        // 无条件断开旧 peripheral（参数为本次连接的 peripheral，安全）
        try {
            peripheral?.disconnect()
        } catch (_: Exception) { }
        // 释放底层资源：disconnect() 仅断开连接，不调用 close() 会泄漏 BluetoothGatt
        // 直到进程结束（每次连接累积一个），必须显式 close。close 后该 peripheral 不可再用，
        // 新连接/重连走 peripheralFactory 重新创建实例，不受影响。
        try {
            peripheral?.close()
        } catch (_: Exception) { }

        // 纪元守卫：如果期间用户已发起新的连接/扫描（connectEpoch 已变），
        // 则跳过共享状态重置——新连接已经接管了这些状态，旧连接的清理不应破坏它。
        if (connectEpoch.get() != epoch) {
            Log.d(TAG, "cleanupConnection: epoch mismatch, skip shared-state reset (new connection in progress)")
            return
        }

        heartRateRecorder.endSession()
        // 断开连接：清零极值 + 清空图表缓存，与 endSession 同位置
        repository.resetChartExtremes()
        repository.clearChart()

        val message = if (isManuallyDisconnected) context.getString(R.string.ble_manual_disconnect) else context.getString(R.string.ble_device_disconnected)
        // 设置断开状态（仅在当前连接仍为本连接时），避免设备从 Connected 直接跳到 Disconnected 时状态卡在 Connected
        repository.setBleState(BleState.Disconnected(message))

        webhookRepository.triggerWebhooks(WebhookTrigger.DISCONNECTED, repository.heartRate.value, repository.speed.value)
        repository.setHeartRate(0)
        // 同步清空测量源，避免重连后首页沿用上次会话的旧心率值
        repository.setHeartRateMeasurement(HeartRateMeasurement.EMPTY)
        // 新鲜度看门狗复位，避免旧连接的超时任务污染新连接
        freshnessTracker.reset()
        // 清除已连接设备信息（断开后 DevicesScreen 不再显示已连接卡片）
        repository.setConnectedDevice(null)
        repository.setScanResults(emptyList())
        broadcast()
        connectedPeripheral = null
    }

    private suspend fun checkAutoReconnect(epoch: Long) {
        val autoReconnectEnabled = settingsRepository.get(SettingsKeys.AUTO_RECONNECT_ENABLED)
        Log.d(TAG, "checkAutoReconnect: enabled=$autoReconnectEnabled, isManual=$isManuallyDisconnected, lastDeviceId=$lastConnectedDeviceId")
        if (!autoReconnectEnabled || isManuallyDisconnected || lastConnectedDeviceId == null) return
        // 期间用户已发起新的连接/扫描（connectEpoch 变化），旧任务不再自动重连，避免打断新连接
        if (connectEpoch.get() != epoch) return

        autoReconnectAttempt++
        if (autoReconnectAttempt > MAX_AUTO_RECONNECT_ATTEMPTS) {
            // 次数以 String 传入（%1$s），规避小语种（ne/bn/ar）locale 整数格式化输出本地数字（如 Devanagari ५）
            repository.setBleState(BleState.ScanFailed(context.getString(R.string.ble_max_reconnect, MAX_AUTO_RECONNECT_ATTEMPTS.toString())))
            autoReconnectAttempt = 0
            return
        }

        // 指数退避：1s, 2s, 4s, 8s, 16s... 上限 60s
        val delayMs = (AUTO_RECONNECT_BASE_DELAY_MS shl (autoReconnectAttempt - 1))
            .coerceAtMost(AUTO_RECONNECT_MAX_DELAY_MS)
        delay(delayMs)
        // 退避结束后再次校验，覆盖延迟期间用户发起的任何新连接/扫描
        if (connectEpoch.get() != epoch) return
        repository.setBleState(BleState.AutoReconnecting)
        startAutoConnectScan(lastConnectedDeviceId!!)
    }

    private suspend fun observeHeartRateData(peripheral: Peripheral) {
        var consecutiveFailures = 0
        // 订阅失败（如 CCCD 写入失败/瞬时 GATT 错误）后自动重订阅，避免整个连接周期内数据永久静默。
        // 若流正常结束也会回到循环重新订阅，实现连接内的自愈。
        while (currentCoroutineContext().isActive) {
            try {
                // 成功进入收集即重置失败计数；collect 正常返回（流结束）后继续下一轮重新订阅
                consecutiveFailures = 0
                bleManager.observeHeartRate(peripheral).collect { measurement ->
                    freshnessTracker.onPacket()
                    // 传感器接触丢失（规范 bit1-2 = 10：支持接触检测但未接触）：设备仍发包
                    // 但测量已无效，主动置零避免永远显示陈旧值；不支持该特性的设备行为不变。
                    // 新鲜度看门狗仍照常刷新（链路活着），重戴后新包到达即自动恢复。
                    val contactLost = measurement.sensorContactSupported && !measurement.sensorContact
                    val effective = if (contactLost) {
                        measurement.copy(bpm = 0, rrIntervals = emptyList())
                    } else {
                        measurement
                    }
                    repository.setHeartRate(effective.bpm)
                    repository.setHeartRateMeasurement(effective)
                    webhookRepository.triggerWebhooks(WebhookTrigger.HEART_RATE_UPDATED, effective.bpm, repository.speed.value)

                    // 喂点给服务层图表追踪器（@Synchronized 保证线程安全）
                    repository.onChartMeasurement(effective)

                    // 历史记录落盘失败不应中断心率采集（如 DB 瞬时异常），单独隔离。
                    // 无效值（bpm <= 0）不落盘，避免污染历史统计均值
                    try {
                        if (effective.bpm > 0) {
                            heartRateRecorder.record(effective.bpm, lastConnectedDeviceName)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "心率记录写入失败", e)
                    }
                    broadcast()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveFailures++
                if (consecutiveFailures > MAX_OBSERVE_RETRY_ATTEMPTS) {
                    Log.e(TAG, "心率订阅连续失败 $consecutiveFailures 次，本次连接内停止监听", e)
                    break
                }
                Log.w(TAG, "心率订阅失败，第 $consecutiveFailures 次重试", e)
                delay(OBSERVE_RETRY_BASE_DELAY_MS * consecutiveFailures)
            }
        }
    }
}

/**
 * 将 Kable 的 [Advertisement] 映射为项目内部的稳定 [ScannedDevice]。
 *
 * 只提取 UI 需要的三个字段（identifier / name / rssi），
 * 避免 Compose 编译器因第三方类型不稳定而无法跳过重组。
 */
private fun Advertisement.toScannedDevice() = ScannedDevice(
    identifier = identifier,
    name = name,
    rssi = rssi
)
