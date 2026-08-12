package com.github.heartratemonitor_compose.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.ble.BleManager
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.data.WebhookTrigger
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.webhook.WebhookRepository
import com.github.heartratemonitor_compose.service.server.ServerHost
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.State
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class BleService : Service(), FairMemoryReceiver.MemoryListener {

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var bleManager: BleManager
    private lateinit var webhookRepository: WebhookRepository
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var heartRateRecorder: HeartRateRecorder
    private lateinit var speedProvider: SpeedProvider
    private lateinit var serverHost: ServerHost
    private lateinit var broadcastManager: BleBroadcastManager
    private lateinit var settingsListener: BleSettingsListener

    private val _bleState = MutableStateFlow<BleState>(BleState.Idle)
    val bleState: StateFlow<BleState> = _bleState.asStateFlow()

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    // 完整心率测量 (含 RR-Interval / 传感器接触 / 累计能耗),供图表做逐拍渲染
    private val _heartRateMeasurement = MutableStateFlow(HeartRateMeasurement.EMPTY)
    val heartRateMeasurement: StateFlow<HeartRateMeasurement> = _heartRateMeasurement.asStateFlow()

    val speed: StateFlow<Float> by lazy { speedProvider.speed }

    private val _scanResults = MutableStateFlow<List<Advertisement>>(emptyList())
    val scanResults: StateFlow<List<Advertisement>> = _scanResults.asStateFlow()

    // 当前已连接设备信息（id + name），断开时为 null。
    // 供 DevicesScreen 在已连接时顶部显示当前设备（修复：连接后 scanResults 被清空导致列表为空）。
    data class ConnectedDevice(val id: String, val name: String)
    private val _connectedDevice = MutableStateFlow<ConnectedDevice?>(null)
    val connectedDevice: StateFlow<ConnectedDevice?> = _connectedDevice.asStateFlow()

    private var connectedPeripheral: Peripheral? = null
    private var connectionJob: Job? = null
    private var scanJob: Job? = null
    @Volatile private var isManuallyDisconnected = false
    private val isScanning = AtomicBoolean(false)
    private var lastConnectedDeviceId: String? = null
    @Volatile private var lastConnectedDeviceName: String = "Unknown Device"
    // 连接/扫描纪元：用户每次发起新的 BLE 活动（扫描/连接）时自增。
    // 被取消的旧连接任务的 finally 用其启动时捕获的纪元做校验，
    // 避免退避中的旧自动重连误取消用户刚发起的新连接。
    private val connectEpoch = AtomicLong(0L)

    // --- 自动重连退避 ---
    private var autoReconnectAttempt = 0

    companion object {
        /** 自动重连最大尝试次数，超过后停止并等待用户手动操作 */
        private const val MAX_AUTO_RECONNECT_ATTEMPTS = 5
        /** 自动重连基础退避（毫秒），实际退避 = base * 2^(attempt-1)，上限 60s */
        private const val AUTO_RECONNECT_BASE_DELAY_MS = 1000L
        private const val AUTO_RECONNECT_MAX_DELAY_MS = 60_000L
        /** 心率订阅失败最大重试次数：连续失败超过该次数后放弃本次连接内的监听 */
        private const val MAX_OBSERVE_RETRY_ATTEMPTS = 5
        /** 心率订阅失败重试基础退避（毫秒），实际退避 = base * 失败次数 */
        private const val OBSERVE_RETRY_BASE_DELAY_MS = 1000L
    }

    fun isDeviceConnected(): Boolean = connectedPeripheral?.state?.value is State.Connected

    override fun onCreate() {
        super.onCreate()
        lastConnectedDeviceName = getString(R.string.unknown_device)

        bleManager = BleManager()
        webhookRepository = applicationContext.appContainer.webhookRepository
        sharedPreferences = getSharedPreferences(PrefsKeys.FILE_NAME, MODE_PRIVATE)
        heartRateRecorder = HeartRateRecorder(
            prefs = sharedPreferences,
            dao = applicationContext.appContainer.appDatabase.heartRateDao(),
            scope = serviceScope
        )
        speedProvider = SpeedProvider(applicationContext, sharedPreferences)
        serverHost = ServerHost(
            prefs = sharedPreferences,
            heartRate = _heartRate,
            speed = speedProvider.speed,
            isDeviceConnected = ::isDeviceConnected,
            getStatusMessage = { _bleState.value.getMessage(applicationContext) },
            webSocketClientCount = applicationContext.appContainer.webSocketClientCount
        )

        // 构造广播管理器（提取自原 broadcastWebSocketState 方法）
        broadcastManager = BleBroadcastManager(
            serverHost = serverHost,
            heartRate = _heartRate,
            speed = speedProvider.speed,
            bleState = _bleState,
            isDeviceConnected = ::isDeviceConnected,
            context = applicationContext
        )

        // 构造设置监听器（提取自原 settingsChangeListener）
        settingsListener = BleSettingsListener(
            sharedPreferences = sharedPreferences,
            onServerSettingsChanged = { serviceScope.launch { serverHost.update() } },
            onSpeedSettingsChanged = {
                speedProvider.update()
                startForegroundService()
                broadcastManager.broadcast()
            },
            onHistoryRecordingDisabled = { serviceScope.launch { heartRateRecorder.endSession() } }
        )

        // 注册断开 WebSocket 客户端的回调，供局域网传输页面「断开连接」按钮调用
        applicationContext.appContainer.disconnectWebSocketClients = { serverHost.disconnectAllWebSocketClients() }

        startForegroundService()
        settingsListener.register()

        // 注册公平运行内存监听：TRIM 时清空扫描缓存，KILL 时立即落盘未写入心率记录
        FairMemoryReceiver.getInstance().addMemoryListener(this)

        // 服务器 start/stop 涉及 Socket bind，移至 IO 线程避免阻塞主线程
        serviceScope.launch { serverHost.update() }
        speedProvider.update()
        broadcastManager.broadcast()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BleService", "Service onStartCommand, refreshing state...")
        speedProvider.update()
        startForegroundService()
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "BleServiceChannel"
        val channelName = getString(R.string.notification_channel_name)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_bluetooth_connected)
            .setOngoing(true)
            .build()

        var type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasLocationPermission = ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val isSpeedEnabled = sharedPreferences.getBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, false)

            if (hasLocationPermission && isSpeedEnabled) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }

            try {
                ServiceCompat.startForeground(this, 1, notification, type)
            } catch (e: Exception) {
                Log.e("BleService", "无法启动带 Location 类型的前台服务，尝试降级启动", e)
                try {
                    val safeType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                    ServiceCompat.startForeground(this, 1, notification, safeType)
                } catch (e2: Exception) {
                    Log.e("BleService", "致命错误：无法启动前台服务", e2)
                }
            }
        } else {
            startForeground(1, notification)
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun startScan(durationMillis: Long = 15_000L) {
        if (!isScanning.compareAndSet(false, true)) return
        stopAllBleActivities()
        connectEpoch.incrementAndGet()

        scanJob = serviceScope.launch {
            // [Fix]: Use Map to prevent duplicates/stacking of same device with updated RSSI
            val foundDevicesMap = mutableMapOf<String, Advertisement>()
            try {
                _bleState.value = BleState.Scanning
                withTimeout(durationMillis) {
                    bleManager.scan().collect { advertisement ->
                        foundDevicesMap[advertisement.identifier] = advertisement
                        _scanResults.value = foundDevicesMap.values.toList()
                    }
                }
            } catch (_: TimeoutCancellationException) {
                // 修复：未使用变量重命名为 _
            } finally {
                withContext(NonCancellable) {
                    isScanning.set(false)
                    // 仅当仍在扫描状态时才发出 ScanFailed，避免覆盖正在进行的连接状态
                    if (_bleState.value is BleState.Scanning) {
                        val statusMessage = if (foundDevicesMap.isNotEmpty()) getString(R.string.ble_scan_finished) else getString(R.string.ble_no_devices_found)
                        _bleState.value = BleState.ScanFailed(statusMessage)
                    }
                }
            }
        }
    }

    /**
     * 手动停止扫描（再次点击搜索按钮时调用）。
     * 取消扫描协程，其 finally 块会复位 isScanning 并发出 ScanFinished 状态，
     * 使搜索按钮的 ContainedLoadingIndicator 结束旋转、恢复搜索图标。
     */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    fun startAutoConnectScan(favoriteDeviceId: String, durationMillis: Long = 15_000L) {
        if (!isScanning.compareAndSet(false, true)) return
        stopAllBleActivities()
        connectEpoch.incrementAndGet()

        scanJob = serviceScope.launch {
            // [Fix]: Use Map here as well for consistency
            val foundDevicesMap = mutableMapOf<String, Advertisement>()
            var favoriteFound = false
            if (_bleState.value !is BleState.AutoReconnecting) {
                _bleState.value = BleState.AutoConnecting
            }

            try {
                withTimeout(durationMillis) {
                    bleManager.scan().collect { advertisement ->
                        foundDevicesMap[advertisement.identifier] = advertisement
                        _scanResults.value = foundDevicesMap.values.toList()

                        if (advertisement.identifier == favoriteDeviceId) {
                            favoriteFound = true
                            this.cancel()
                        }
                    }
                }
            } catch (_: CancellationException) {
                // 修复：未使用变量重命名为 _
            } catch (e: Exception) {
                Log.w("BleService", "Auto scan error", e)
            } finally {
                withContext(NonCancellable) {
                    isScanning.set(false)
                    if (favoriteFound) {
                        Log.d("BleService", "autoScan finally: favoriteFound=true, calling connectToDevice($favoriteDeviceId)")
                        connectToDevice(favoriteDeviceId)
                    } else {
                        if (_bleState.value is BleState.AutoConnecting || _bleState.value is BleState.AutoReconnecting) {
                            Log.d("BleService", "autoScan finally: favoriteFound=false, emitting ScanFailed (currentBleState=${_bleState.value.javaClass.simpleName})")
                            _bleState.value = BleState.ScanFailed(getString(R.string.ble_auto_connect_failed))
                        } else {
                            Log.d("BleService", "autoScan finally: favoriteFound=false, NOT emitting ScanFailed (currentBleState=${_bleState.value.javaClass.simpleName})")
                        }
                    }
                }
            }
        }
    }

    fun connectToDevice(identifier: String) {
        stopAllBleActivities()
        isManuallyDisconnected = false
        autoReconnectAttempt = 0  // 手动连接时重置重试计数
        // 新连接意图：使退避中的旧自动重连检查失效（旧任务捕获的纪元与本值不再相等）
        val myEpoch = connectEpoch.incrementAndGet()

        connectionJob = serviceScope.launch {
            var peripheral: Peripheral? = null
            try {
                peripheral = Peripheral(identifier)
                connectedPeripheral = peripheral
                lastConnectedDeviceId = identifier

                if (_bleState.value !is BleState.AutoReconnecting) {
                    Log.d("BleService", "connectToDevice: setting BleState.Connecting for $identifier")
                    _bleState.value = BleState.Connecting
                } else {
                    Log.d("BleService", "connectToDevice: keeping AutoReconnecting, will use existing BleState")
                }

                val stateMonitor = launch {
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
                Log.e("BleService", "Connection to $identifier timed out", e)
                if (_bleState.value !is BleState.AutoReconnecting) {
                    _bleState.value = BleState.Disconnected(getString(R.string.ble_connect_timeout))
                }
            } catch (e: CancellationException) {
                // 结构化并发：真正的取消（外部 cancel / 设备断开）必须向上传播，
                // 不能被当作连接失败吞掉；清理与自动重连仍在 finally 中执行。
                throw e
            } catch (e: Exception) {
                Log.e("BleService", "Connection to $identifier failed", e)
                if (_bleState.value !is BleState.AutoReconnecting) {
                    _bleState.value = BleState.Disconnected(getString(R.string.ble_connect_failed, e.message))
                }
            } finally {
                withContext(NonCancellable) {
                    cleanupConnection(peripheral, myEpoch)
                    checkAutoReconnect(myEpoch)
                }
            }
        }
    }

    private suspend fun handlePeripheralState(peripheral: Peripheral, state: State) {
        when (state) {
            is State.Connecting -> {
                if (_bleState.value !is BleState.AutoReconnecting) {
                    _bleState.value = BleState.Connecting
                }
            }
            is State.Connected -> {
                @OptIn(com.juul.kable.ExperimentalKableApi::class)
                val deviceName = peripheral.name ?: getString(R.string.unknown_device)
                lastConnectedDeviceName = deviceName
                // 同步当前已连接设备信息（id + name）供 UI 显示
                _connectedDevice.value = ConnectedDevice(lastConnectedDeviceId ?: "", deviceName)
                _scanResults.value = emptyList()
                _bleState.value = BleState.Connected(getString(R.string.ble_connected_to, deviceName))
                autoReconnectAttempt = 0  // 连接成功，重置重试计数
                webhookRepository.triggerWebhooks(WebhookTrigger.CONNECTED, speed = speedProvider.speed.value)

                // 先确保 session 写入完成（await），再启动心率监听，避免早期数据因 session 未就绪而丢失
                heartRateRecorder.startSession(deviceName)
                broadcastManager.broadcast()

                // 作为 connectionJob 的子协程启动：断开连接时随 connectionJob 取消，避免泄漏
                CoroutineScope(currentCoroutineContext()).launch { observeHeartRateData(peripheral) }
            }
            is State.Disconnecting -> _bleState.value = BleState.Disconnected(getString(R.string.ble_disconnecting))
            is State.Disconnected -> {
                throw CancellationException("Device disconnected: ${state.status}")
            }
        }
    }

    fun disconnectDevice() {
        isManuallyDisconnected = true
        stopAllBleActivities()
    }

    private fun stopAllBleActivities() {
        scanJob?.cancel()
        connectionJob?.cancel()
    }

    private suspend fun cleanupConnection(peripheral: Peripheral?, epoch: Long) {
        Log.d("BleService", "cleanupConnection: isManuallyDisconnected=$isManuallyDisconnected, epoch=$epoch, currentEpoch=${connectEpoch.get()}")
        // 无条件断开旧 peripheral（参数为本次连接的 peripheral，安全）
        try {
            peripheral?.disconnect()
        } catch (_: Exception) { /* 修复：未使用变量重命名为 _ */ }

        // 纪元守卫：如果期间用户已发起新的连接/扫描（connectEpoch 已变），
        // 则跳过共享状态重置——新连接已经接管了这些状态，旧连接的清理不应破坏它。
        if (connectEpoch.get() != epoch) {
            Log.d("BleService", "cleanupConnection: epoch mismatch, skip shared-state reset (new connection in progress)")
            return
        }

        heartRateRecorder.endSession()

        val message = if (isManuallyDisconnected) getString(R.string.ble_manual_disconnect) else getString(R.string.ble_device_disconnected)
        // 设置断开状态（仅在当前连接仍为本连接时），避免设备从 Connected 直接跳到 Disconnected 时状态卡在 Connected
        _bleState.value = BleState.Disconnected(message)

        webhookRepository.triggerWebhooks(WebhookTrigger.DISCONNECTED, _heartRate.value, speedProvider.speed.value)
        _heartRate.value = 0
        // 同步清空测量源，避免重连后首页沿用上次会话的旧心率值
        _heartRateMeasurement.value = HeartRateMeasurement.EMPTY
        // 清除已连接设备信息（断开后 DevicesScreen 不再显示已连接卡片）
        _connectedDevice.value = null
        _scanResults.value = emptyList()
        broadcastManager.broadcast()
        connectedPeripheral = null
    }

    private suspend fun checkAutoReconnect(epoch: Long) {
        val autoReconnectEnabled = sharedPreferences.getBoolean(PrefsKeys.AUTO_RECONNECT_ENABLED, true)
        Log.d("BleService", "checkAutoReconnect: enabled=$autoReconnectEnabled, isManual=$isManuallyDisconnected, lastDeviceId=$lastConnectedDeviceId")
        if (!autoReconnectEnabled || isManuallyDisconnected || lastConnectedDeviceId == null) return
        // 期间用户已发起新的连接/扫描（connectEpoch 变化），旧任务不再自动重连，避免打断新连接
        if (connectEpoch.get() != epoch) return

        autoReconnectAttempt++
        if (autoReconnectAttempt > MAX_AUTO_RECONNECT_ATTEMPTS) {
            _bleState.value = BleState.ScanFailed(getString(R.string.ble_max_reconnect, MAX_AUTO_RECONNECT_ATTEMPTS))
            autoReconnectAttempt = 0
            return
        }

        // 指数退避：1s, 2s, 4s, 8s, 16s... 上限 60s
        val delayMs = (AUTO_RECONNECT_BASE_DELAY_MS shl (autoReconnectAttempt - 1))
            .coerceAtMost(AUTO_RECONNECT_MAX_DELAY_MS)
        delay(delayMs)
        // 退避结束后再次校验，覆盖延迟期间用户发起的任何新连接/扫描
        if (connectEpoch.get() != epoch) return
        _bleState.value = BleState.AutoReconnecting
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
                    _heartRate.value = measurement.bpm
                    _heartRateMeasurement.value = measurement
                    webhookRepository.triggerWebhooks(WebhookTrigger.HEART_RATE_UPDATED, measurement.bpm, speedProvider.speed.value)

                    // 历史记录落盘失败不应中断心率采集（如 DB 瞬时异常），单独隔离
                    try {
                        heartRateRecorder.record(measurement.bpm, lastConnectedDeviceName)
                    } catch (e: Exception) {
                        Log.w("BleService", "心率记录写入失败", e)
                    }
                    broadcastManager.broadcast()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                consecutiveFailures++
                if (consecutiveFailures > MAX_OBSERVE_RETRY_ATTEMPTS) {
                    Log.e("BleService", "心率订阅连续失败 $consecutiveFailures 次，本次连接内停止监听", e)
                    break
                }
                Log.w("BleService", "心率订阅失败，第 $consecutiveFailures 次重试", e)
                delay(OBSERVE_RETRY_BASE_DELAY_MS * consecutiveFailures)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户从最近任务列表滑掉时提前刷新，给 onDestroy 留更充裕的时间
        heartRateRecorder.cancelFlushLoop()
        serviceScope.launch { heartRateRecorder.flushPendingRecords() }
    }

    override fun onDestroy() {
        super.onDestroy()
        applicationContext.appContainer.disconnectWebSocketClients = null
        applicationContext.appContainer.webSocketClientCount.value = 0
        FairMemoryReceiver.getInstance().removeMemoryListener(this)
        // 从缓冲区取出待写入记录并交给 WorkManager 异步落盘。
        // 不在主线程阻塞等待 I/O，消除 ANR 风险；
        // WorkManager 持久化工作请求——即使进程被杀也会在下次启动时补执行，保证数据不丢失。
        heartRateRecorder.cancelFlushLoop()
        val pending = heartRateRecorder.drainPendingRecords()
        if (pending.isNotEmpty()) {
            FlushRecordsWorker.enqueue(applicationContext, pending)
        }
        serviceScope.cancel()
        speedProvider.stop()
        serverHost.stop()
        // WebhookRepository 是应用级单例，不在 Service 生命周期内 shutdown
        settingsListener.unregister()
    }

    /** 公平运行内存 TRIM：清空蓝牙扫描缓存，释放 Advertisement 对象占用的内存。 */
    override fun onTrimMemory(notifyType: Int) {
        if (!isScanning.get()) {
            _scanResults.value = emptyList()
            Log.i("BleService", "TRIM: 已清空蓝牙扫描缓存")
        }
    }

    /** 公平运行内存 KILL：将未写入心率记录排入 WorkManager 异步落盘。 */
    override fun onKillMemory() {
        heartRateRecorder.cancelFlushLoop()
        val pending = heartRateRecorder.drainPendingRecords()
        if (pending.isNotEmpty()) {
            FlushRecordsWorker.enqueue(applicationContext, pending)
        }
        Log.i("BleService", "KILL: 已排入 ${pending.size} 条心率记录的落盘任务")
    }
}
