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
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.webhook.WebhookRepository
import com.github.heartratemonitor_compose.service.server.ServerHost
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.peripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

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

    // --- 自动重连退避 ---
    private var autoReconnectAttempt = 0

    companion object {
        /** 自动重连最大尝试次数，超过后停止并等待用户手动操作 */
        private const val MAX_AUTO_RECONNECT_ATTEMPTS = 5
        /** 自动重连基础退避（毫秒），实际退避 = base * 2^(attempt-1)，上限 60s */
        private const val AUTO_RECONNECT_BASE_DELAY_MS = 1000L
        private const val AUTO_RECONNECT_MAX_DELAY_MS = 60_000L
    }

    // --- WebSocket 广播节流 ---
    @Volatile private var lastBroadcastTime = 0L
    private val BROADCAST_MIN_INTERVAL_MS = 200L

    fun isDeviceConnected(): Boolean = connectedPeripheral?.state?.value is State.Connected

    override fun onCreate() {
        super.onCreate()
        lastConnectedDeviceName = getString(R.string.unknown_device)

        bleManager = BleManager()
        webhookRepository = applicationContext.appContainer.webhookRepository
        sharedPreferences = getSharedPreferences(PrefsKeys.FILE_NAME, MODE_PRIVATE)
        heartRateRecorder = HeartRateRecorder(
            prefs = sharedPreferences,
            dao = AppDatabase.getDatabase(applicationContext).heartRateDao(),
            scope = serviceScope
        )
        speedProvider = SpeedProvider(applicationContext, sharedPreferences)
        serverHost = ServerHost(
            prefs = sharedPreferences,
            heartRate = _heartRate,
            speed = speedProvider.speed,
            isDeviceConnected = ::isDeviceConnected,
            getStatusMessage = { _bleState.value.getMessage(applicationContext) }
        )

        startForegroundService()
        registerSettingsListener()

        // 注册公平运行内存监听：TRIM 时清空扫描缓存，KILL 时立即落盘未写入心率记录
        FairMemoryReceiver.getInstance().addMemoryListener(this)

        // 服务器 start/stop 涉及 Socket bind，移至 IO 线程避免阻塞主线程
        serviceScope.launch { serverHost.update() }
        speedProvider.update()
        broadcastWebSocketState()
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

        // 修复：移除 Android 8.0+ 的冗余检查 (minSdk >= 27)
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(chan)

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

    fun startAutoConnectScan(favoriteDeviceId: String, durationMillis: Long = 15_000L) {
        if (!isScanning.compareAndSet(false, true)) return
        stopAllBleActivities()

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

        connectionJob = serviceScope.launch {
            var peripheral: Peripheral? = null
            try {
                peripheral = serviceScope.peripheral(identifier)
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

            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is TimeoutCancellationException -> getString(R.string.ble_connect_timeout)
                    is CancellationException -> getString(R.string.ble_connect_cancelled, e.message)
                    else -> getString(R.string.ble_connect_failed, e.message)
                }
                Log.e("BleService", "Connection to $identifier failed", e)
                if (_bleState.value !is BleState.AutoReconnecting) {
                    _bleState.value = BleState.Disconnected(errorMessage)
                }
            } finally {
                withContext(NonCancellable) {
                    cleanupConnection(peripheral)
                    checkAutoReconnect()
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
                broadcastWebSocketState()

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

    private suspend fun cleanupConnection(peripheral: Peripheral?) {
        Log.d("BleService", "cleanupConnection: isManuallyDisconnected=$isManuallyDisconnected")
        try {
            peripheral?.disconnect()
        } catch (_: Exception) { /* 修复：未使用变量重命名为 _ */ }

        heartRateRecorder.endSession()

        val message = if (isManuallyDisconnected) getString(R.string.ble_manual_disconnect) else getString(R.string.ble_device_disconnected)
        // 无条件设置断开状态，避免设备从 Connected 直接跳到 Disconnected（未经 Disconnecting）时状态卡在 Connected
        _bleState.value = BleState.Disconnected(message)

        webhookRepository.triggerWebhooks(WebhookTrigger.DISCONNECTED, _heartRate.value, speedProvider.speed.value)
        _heartRate.value = 0
        // 清除已连接设备信息（断开后 DevicesScreen 不再显示已连接卡片）
        _connectedDevice.value = null
        _scanResults.value = emptyList()
        broadcastWebSocketState()
        connectedPeripheral = null
    }

    private suspend fun checkAutoReconnect() {
        val autoReconnectEnabled = sharedPreferences.getBoolean(PrefsKeys.AUTO_RECONNECT_ENABLED, true)
        Log.d("BleService", "checkAutoReconnect: enabled=$autoReconnectEnabled, isManual=$isManuallyDisconnected, lastDeviceId=$lastConnectedDeviceId")
        if (!autoReconnectEnabled || isManuallyDisconnected || lastConnectedDeviceId == null) return

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
        _bleState.value = BleState.AutoReconnecting
        startAutoConnectScan(lastConnectedDeviceId!!)
    }

    private suspend fun observeHeartRateData(peripheral: Peripheral) {
        try {
            bleManager.observeHeartRate(peripheral).collect { measurement ->
                _heartRate.value = measurement.bpm
                _heartRateMeasurement.value = measurement
                webhookRepository.triggerWebhooks(WebhookTrigger.HEART_RATE_UPDATED, measurement.bpm, speedProvider.speed.value)

                heartRateRecorder.record(measurement.bpm, lastConnectedDeviceName)
                broadcastWebSocketState()
            }
        } catch (e: Exception) {
            Log.w("BleService", "Heart rate observation failed", e)
        }
    }

    private fun broadcastWebSocketState() {
        // 节流：心率（~1s）和位置（~1s）可能同时触发，200ms 间隔去重，不丢数据
        val now = System.currentTimeMillis()
        if (now - lastBroadcastTime < BROADCAST_MIN_INTERVAL_MS) return
        lastBroadcastTime = now

        val json = JSONObject().apply {
            put("heart_rate", _heartRate.value)
            put("connected", isDeviceConnected())
            put("status", _bleState.value.getMessage(applicationContext))
            put("timestamp", System.currentTimeMillis())
            put("speed", speedProvider.speed.value)
        }
        serverHost.emitState(json.toString())
    }

    private val settingsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefsKeys.HTTP_SERVER_ENABLED, PrefsKeys.HTTP_SERVER_PORT, PrefsKeys.SERVER_ACCESS_TOKEN ->
                serviceScope.launch { serverHost.update() }
            PrefsKeys.WEBSOCKET_SERVER_ENABLED, PrefsKeys.WEBSOCKET_SERVER_PORT, PrefsKeys.SERVER_ACCESS_TOKEN ->
                serviceScope.launch { serverHost.update() }
            PrefsKeys.SPEED_DISPLAY_ENABLED -> {
                speedProvider.update()
                startForegroundService()
                broadcastWebSocketState()
            }
        }
    }

    private fun registerSettingsListener() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(settingsChangeListener)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户从最近任务列表滑掉时提前刷新，给 onDestroy 留更充裕的时间
        heartRateRecorder.cancelFlushLoop()
        serviceScope.launch { heartRateRecorder.flushPendingRecords() }
    }

    override fun onDestroy() {
        super.onDestroy()
        FairMemoryReceiver.getInstance().removeMemoryListener(this)
        // 刷新未写入的批量心率记录：在现有 IO scope 中以 NonCancellable 启动，
        // 既避免阻塞主线程，也避免创建独立线程导致数据竞争。
        heartRateRecorder.cancelFlushLoop()
        serviceScope.launch(NonCancellable) { heartRateRecorder.flushPendingRecords() }
        serviceScope.cancel()
        speedProvider.stop()
        serverHost.stop()
        // WebhookRepository 是应用级单例，不在 Service 生命周期内 shutdown
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(settingsChangeListener)
    }

    /** 公平运行内存 TRIM：清空蓝牙扫描缓存，释放 Advertisement 对象占用的内存。 */
    override fun onTrimMemory(notifyType: Int) {
        if (!isScanning.get()) {
            _scanResults.value = emptyList()
            Log.i("BleService", "TRIM: 已清空蓝牙扫描缓存")
        }
    }

    /** 公平运行内存 KILL：同步阻塞等待未写入心率记录落盘。 */
    override fun onKillMemory() {
        Log.i("BleService", "KILL: 正在强制落盘未写入心率记录…")
        heartRateRecorder.cancelFlushLoop()
        runBlocking(NonCancellable) { heartRateRecorder.flushPendingRecords() }
        Log.i("BleService", "KILL: 心率记录落盘完成")
    }
}
