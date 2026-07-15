<<<<<<< HEAD
package com.github.heartratemonitor_compose.service
=======
﻿package com.github.heartratemonitor_compose.service
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteConstraintException
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ble.BleManager
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.data.WebhookTrigger
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import com.github.heartratemonitor_compose.data.db.HeartRateSession
import com.github.heartratemonitor_compose.service.server.HttpServerManager
import com.github.heartratemonitor_compose.service.server.WebSocketServerManager
import com.github.heartratemonitor_compose.ui.webhook.WebhookManager
import com.juul.kable.Advertisement
import com.juul.kable.Peripheral
import com.juul.kable.State
import com.juul.kable.peripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
<<<<<<< HEAD
import kotlin.coroutines.coroutineContext
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
import org.json.JSONObject
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

class BleService : Service() {

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var bleManager: BleManager
    private lateinit var webhookManager: WebhookManager
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: AppDatabase
    private var locationManager: LocationManager? = null

    // --- Server Managers ---
    private var httpServerManager: HttpServerManager? = null
    private var webSocketServerManager: WebSocketServerManager? = null

    // --- StateFlow ---
    private val _bleState = MutableStateFlow<BleState>(BleState.Idle)
    val bleState: StateFlow<BleState> = _bleState.asStateFlow()

    private val _heartRate = MutableStateFlow(0)
    val heartRate: StateFlow<Int> = _heartRate.asStateFlow()

    // 完整心率测量 (含 RR-Interval / 传感器接触 / 累计能耗),供图表做逐拍渲染
    private val _heartRateMeasurement = MutableStateFlow(HeartRateMeasurement.EMPTY)
    val heartRateMeasurement: StateFlow<HeartRateMeasurement> = _heartRateMeasurement.asStateFlow()

    private val _speed = MutableStateFlow(0f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _scanResults = MutableStateFlow<List<Advertisement>>(emptyList())
    val scanResults: StateFlow<List<Advertisement>> = _scanResults.asStateFlow()

    private val webSocketStateFlow = MutableSharedFlow<String>(replay = 1)

    // --- BLE State ---
    private var connectedPeripheral: Peripheral? = null
    private var connectionJob: Job? = null
    private var scanJob: Job? = null
    @Volatile private var isManuallyDisconnected = false
    private val isScanning = AtomicBoolean(false)
    private var lastConnectedDeviceId: String? = null
<<<<<<< HEAD
    @Volatile private var currentSessionId: Long? = null
    @Volatile private var lastConnectedDeviceName: String = "未知设备"

    // --- 自动重连退避 ---
    private var autoReconnectAttempt = 0
=======
    private var currentSessionId: Long? = null
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61

    // --- 批量插入缓冲 ---
    private val pendingRecords = mutableListOf<HeartRateRecord>()
    private val pendingRecordsLock = Any()
    private var recordFlushJob: Job? = null

    companion object {
        /** 批量刷新间隔（毫秒）：减少高频心率数据的数据库 I/O 次数 */
        private const val BATCH_FLUSH_INTERVAL_MS = 5000L
        /** onDestroy 中刷新的超时时间（毫秒），超时后放弃未写入记录以避免 ANR */
        private const val DESTROY_FLUSH_TIMEOUT_MS = 1500L
<<<<<<< HEAD
        /** 自动重连最大尝试次数，超过后停止并等待用户手动操作 */
        private const val MAX_AUTO_RECONNECT_ATTEMPTS = 5
        /** 自动重连基础退避（毫秒），实际退避 = base * 2^(attempt-1)，上限 60s */
        private const val AUTO_RECONNECT_BASE_DELAY_MS = 1000L
        private const val AUTO_RECONNECT_MAX_DELAY_MS = 60_000L
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
    }

    // --- 服务器端口跟踪（用于检测端口变更并重启） ---
    private var currentHttpPort: Int = -1
    private var currentWebSocketPort: Int = -1
<<<<<<< HEAD
    private var currentHttpAuthToken: String = ""
    private var currentWebSocketAuthToken: String = ""
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61

    // --- WebSocket 广播节流 ---
    @Volatile private var lastBroadcastTime = 0L
    private val BROADCAST_MIN_INTERVAL_MS = 200L

    // --- Location Listener ---
    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.hasSpeed()) {
                _speed.value = location.speed * 3.6f // m/s to km/h
            } else {
                _speed.value = 0f
            }
            broadcastWebSocketState()
        }
        // 兼容旧 API
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun isDeviceConnected(): Boolean = connectedPeripheral?.state?.value is State.Connected

    override fun onCreate() {
        super.onCreate()
        // 修复：BleManager 不再需要 context 参数
        bleManager = BleManager()
        webhookManager = WebhookManager(applicationContext)
        // 修复：移除冗余的 Context. 前缀
        sharedPreferences = getSharedPreferences("app_settings", MODE_PRIVATE)
        db = AppDatabase.getDatabase(applicationContext)
        // 修复：移除冗余的 Context. 前缀
        locationManager = getSystemService(LOCATION_SERVICE) as? LocationManager

        startForegroundService()
        registerSettingsListener()

        updateHttpServerState()
        updateWebSocketServerState()
        updateLocationUpdates()
        broadcastWebSocketState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BleService", "Service onStartCommand, refreshing state...")
        updateLocationUpdates()
        startForegroundService()
        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "BleServiceChannel"
        val channelName = "BLE 连接状态"

        // 修复：移除 Android 8.0+ 的冗余检查 (minSdk >= 27)
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
        // 修复：移除冗余的 Context. 前缀
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(chan)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("心率监控器")
            .setContentText("服务正在后台运行")
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
            val isSpeedEnabled = sharedPreferences.getBoolean("speed_display_enabled", true)

            if (hasLocationPermission && isSpeedEnabled) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            }

            try {
                ServiceCompat.startForeground(this, 1, notification, type)
            } catch (e: Exception) {
                // 修复：未使用变量重命名为 _
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
                        // Update the map with the latest advertisement for this ID
                        foundDevicesMap[advertisement.identifier] = advertisement
                        _scanResults.value = foundDevicesMap.values.toList()
                    }
                }
            } catch (_: TimeoutCancellationException) {
                // 修复：未使用变量重命名为 _
            } finally {
                withContext(NonCancellable) {
                    val statusMessage = if (foundDevicesMap.isNotEmpty()) "扫描结束" else "未找到任何设备"
                    _bleState.value = BleState.ScanFailed(statusMessage)
                    isScanning.set(false)
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
                        connectToDevice(favoriteDeviceId)
                    } else {
                        if (_bleState.value is BleState.AutoConnecting || _bleState.value is BleState.AutoReconnecting) {
                            _bleState.value = BleState.ScanFailed("自动连接失败: 未找到设备")
                        }
                    }
                }
            }
        }
    }

    fun connectToDevice(identifier: String) {
        stopAllBleActivities()
        isManuallyDisconnected = false
<<<<<<< HEAD
        autoReconnectAttempt = 0  // 手动连接时重置重试计数
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61

        connectionJob = serviceScope.launch {
            var peripheral: Peripheral? = null
            try {
                peripheral = serviceScope.peripheral(identifier)
                connectedPeripheral = peripheral
                lastConnectedDeviceId = identifier

                if (_bleState.value !is BleState.AutoReconnecting) {
                    _bleState.value = BleState.Connecting
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
                    is TimeoutCancellationException -> "连接超时"
                    is CancellationException -> "连接已取消: ${e.message}"
                    else -> "连接失败: ${e.message}"
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
                val deviceName = peripheral.name ?: "未知设备"
<<<<<<< HEAD
                lastConnectedDeviceName = deviceName
                _bleState.value = BleState.Connected("已连接到 $deviceName")
                autoReconnectAttempt = 0  // 连接成功，重置重试计数
=======
                _bleState.value = BleState.Connected("已连接到 $deviceName")
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
                webhookManager.triggerWebhooks(WebhookTrigger.CONNECTED, speed = _speed.value)

                // 先确保 session 写入完成（await），再启动心率监听，避免早期数据因 currentSessionId 为 null 而丢失
                startHistorySession(deviceName)
                broadcastWebSocketState()

<<<<<<< HEAD
                // 作为 connectionJob 的子协程启动：断开连接时随 connectionJob 取消，避免泄漏
                CoroutineScope(coroutineContext).launch { observeHeartRateData(peripheral) }
=======
                serviceScope.launch { observeHeartRateData(peripheral) }
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
            }
            is State.Disconnecting -> _bleState.value = BleState.Disconnected("正在断开...")
            is State.Disconnected -> {
                throw CancellationException("Device disconnected: ${state.status}")
            }
        }
    }

    private suspend fun startHistorySession(deviceName: String) {
        val isHistoryEnabled = sharedPreferences.getBoolean("history_recording_enabled", false)
        if (isHistoryEnabled) {
            val session = HeartRateSession(deviceName = deviceName, startTime = System.currentTimeMillis())
            // 同步等待 session 插入完成，确保 currentSessionId 在 observeHeartRateData 启动前就绪
            currentSessionId = db.heartRateDao().insertSession(session)
            startRecordFlushLoop()
        }
    }

    fun disconnectDevice() {
        isManuallyDisconnected = true
        stopAllBleActivities()
    }

    private fun stopAllBleActivities() {
        scanJob?.cancel()
        connectionJob?.cancel()
        _scanResults.value = emptyList()
    }

    private suspend fun cleanupConnection(peripheral: Peripheral?) {
        try {
            peripheral?.disconnect()
        } catch (_: Exception) { /* 修复：未使用变量重命名为 _ */ }

        // 停止批量刷新循环并刷新剩余记录
        recordFlushJob?.cancel()
        recordFlushJob = null
        flushPendingRecords()

        currentSessionId?.let { id ->
            db.heartRateDao().endSession(id, System.currentTimeMillis())
            currentSessionId = null
        }

        val message = if (isManuallyDisconnected) "已手动断开" else "设备连接已断开"
        // 无条件设置断开状态，避免设备从 Connected 直接跳到 Disconnected（未经 Disconnecting）时状态卡在 Connected
        _bleState.value = BleState.Disconnected(message)

        webhookManager.triggerWebhooks(WebhookTrigger.DISCONNECTED, _heartRate.value, _speed.value)
        _heartRate.value = 0
        broadcastWebSocketState()
        connectedPeripheral = null
    }

    private suspend fun checkAutoReconnect() {
        val autoReconnectEnabled = sharedPreferences.getBoolean("auto_reconnect_enabled", true)
<<<<<<< HEAD
        if (!autoReconnectEnabled || isManuallyDisconnected || lastConnectedDeviceId == null) return

        autoReconnectAttempt++
        if (autoReconnectAttempt > MAX_AUTO_RECONNECT_ATTEMPTS) {
            _bleState.value = BleState.ScanFailed("自动重连已达最大尝试次数（$MAX_AUTO_RECONNECT_ATTEMPTS），请手动重连")
            autoReconnectAttempt = 0
            return
        }

        // 指数退避：1s, 2s, 4s, 8s, 16s... 上限 60s
        val delayMs = (AUTO_RECONNECT_BASE_DELAY_MS shl (autoReconnectAttempt - 1))
            .coerceAtMost(AUTO_RECONNECT_MAX_DELAY_MS)
        delay(delayMs)
        _bleState.value = BleState.AutoReconnecting
        startAutoConnectScan(lastConnectedDeviceId!!)
=======
        if (autoReconnectEnabled && !isManuallyDisconnected && lastConnectedDeviceId != null) {
            delay(1000)
            _bleState.value = BleState.AutoReconnecting
            startAutoConnectScan(lastConnectedDeviceId!!)
        }
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
    }

    private suspend fun observeHeartRateData(peripheral: Peripheral) {
        try {
<<<<<<< HEAD
=======
            // [Fix]: Default value changed to false
            val isHistoryEnabled = sharedPreferences.getBoolean("history_recording_enabled", false)
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
            bleManager.observeHeartRate(peripheral).collect { measurement ->
                _heartRate.value = measurement.bpm
                _heartRateMeasurement.value = measurement
                webhookManager.triggerWebhooks(WebhookTrigger.HEART_RATE_UPDATED, measurement.bpm, _speed.value)

<<<<<<< HEAD
                // 实时读取历史记录开关，支持连接中途切换
                val isHistoryEnabled = sharedPreferences.getBoolean("history_recording_enabled", false)
                if (isHistoryEnabled) {
                    // 中途开启时懒创建 session（连接时历史开关关闭的情况）
                    if (currentSessionId == null) {
                        val session = HeartRateSession(deviceName = lastConnectedDeviceName, startTime = System.currentTimeMillis())
                        currentSessionId = db.heartRateDao().insertSession(session)
                        startRecordFlushLoop()
                    }
=======
                if (isHistoryEnabled && currentSessionId != null) {
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
                    val record = HeartRateRecord(sessionId = currentSessionId!!, timestamp = System.currentTimeMillis(), heartRate = measurement.bpm)
                    synchronized(pendingRecordsLock) {
                        pendingRecords.add(record)
                    }
                }
                broadcastWebSocketState()
            }
        } catch (e: Exception) {
            Log.w("BleService", "Heart rate observation failed", e)
        }
    }

    /**
     * 启动定时批量刷新循环：每 5 秒将缓冲的心率记录批量写入数据库，减少高频 I/O。
     */
    private fun startRecordFlushLoop() {
        recordFlushJob?.cancel()
        recordFlushJob = serviceScope.launch {
            while (true) {
                kotlinx.coroutines.delay(BATCH_FLUSH_INTERVAL_MS)
                flushPendingRecords()
            }
        }
    }

    /**
     * 将缓冲的心率记录批量写入数据库。
     */
    private suspend fun flushPendingRecords() {
        val toFlush: List<HeartRateRecord>
        synchronized(pendingRecordsLock) {
            if (pendingRecords.isEmpty()) return
            toFlush = pendingRecords.toList()
            pendingRecords.clear()
        }
        try {
            db.heartRateDao().insertRecords(toFlush)
        } catch (_: SQLiteConstraintException) {
            currentSessionId = null
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
            put("status", _bleState.value.message)
            put("timestamp", System.currentTimeMillis())
            put("speed", _speed.value)
        }
        webSocketStateFlow.tryEmit(json.toString())
    }

    private val settingsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "http_server_enabled", "http_server_port", "server_access_token" -> updateHttpServerState()
            "websocket_server_enabled", "websocket_server_port", "server_access_token" -> updateWebSocketServerState()
            "speed_display_enabled" -> {
                updateLocationUpdates()
                startForegroundService()
            }
        }
    }

    private fun registerSettingsListener() {
        sharedPreferences.registerOnSharedPreferenceChangeListener(settingsChangeListener)
    }

    private fun updateLocationUpdates() {
        val isEnabled = sharedPreferences.getBoolean("speed_display_enabled", true)
        val hasPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (isEnabled && hasPermission) {
            try {
                // 修复：使用 hasSystemFeature 替代已过时的 getProvider 来检查 GPS 硬件
                if (packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
                    locationManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        1f,
                        locationListener
                    )
                } else {
                    Log.w("BleService", "设备不支持 GPS，无法获取速度信息")
                }
            } catch (e: Exception) {
                Log.e("BleService", "Location update failed", e)
            }
        } else {
            locationManager?.removeUpdates(locationListener)
            _speed.value = 0f
            broadcastWebSocketState()
        }
    }

    private fun updateHttpServerState() {
        val isEnabled = sharedPreferences.getBoolean("http_server_enabled", false)
        val authToken = sharedPreferences.getString("server_access_token", "") ?: ""
        if (isEnabled) {
            val port = sharedPreferences.getInt("http_server_port", 8000)
<<<<<<< HEAD
            // 端口或 token 变更、首次启动时（重新）创建服务器
            if (httpServerManager == null || currentHttpPort != port || currentHttpAuthToken != authToken) {
=======
            // 端口变更或首次启动时（重新）创建服务器
            if (httpServerManager == null || currentHttpPort != port) {
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
                httpServerManager?.stop()
                httpServerManager = HttpServerManager(port, authToken, _heartRate, _speed, ::isDeviceConnected) { _bleState.value.message }
                httpServerManager?.start()
                currentHttpPort = port
<<<<<<< HEAD
                currentHttpAuthToken = authToken
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
            }
        } else {
            httpServerManager?.stop()
            httpServerManager = null
            currentHttpPort = -1
<<<<<<< HEAD
            currentHttpAuthToken = ""
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
        }
    }

    private fun updateWebSocketServerState() {
        val isEnabled = sharedPreferences.getBoolean("websocket_server_enabled", false)
        val authToken = sharedPreferences.getString("server_access_token", "") ?: ""
        if (isEnabled) {
            val port = sharedPreferences.getInt("websocket_server_port", 8001)
<<<<<<< HEAD
            // 端口或 token 变更、首次启动时（重新）创建服务器
            if (webSocketServerManager == null || currentWebSocketPort != port || currentWebSocketAuthToken != authToken) {
=======
            // 端口变更或首次启动时（重新）创建服务器
            if (webSocketServerManager == null || currentWebSocketPort != port) {
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
                webSocketServerManager?.stop()
                webSocketServerManager = WebSocketServerManager(port, authToken, webSocketStateFlow)
                webSocketServerManager?.start()
                currentWebSocketPort = port
<<<<<<< HEAD
                currentWebSocketAuthToken = authToken
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
            }
        } else {
            webSocketServerManager?.stop()
            webSocketServerManager = null
            currentWebSocketPort = -1
<<<<<<< HEAD
            currentWebSocketAuthToken = ""
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
        }
        broadcastWebSocketState()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户从最近任务列表滑掉时提前刷新，给 onDestroy 留更充裕的时间
        recordFlushJob?.cancel()
        serviceScope.launch { flushPendingRecords() }
    }

    override fun onDestroy() {
        super.onDestroy()
<<<<<<< HEAD
        // 刷新未写入的批量心率记录：用独立 daemon 线程异步执行，
        // 避免 runBlocking 阻塞主线程导致 ANR。Room 的 suspend 函数使用内部 dispatcher，不依赖 serviceScope。
        recordFlushJob?.cancel()
        Thread {
            runBlocking {
                withTimeoutOrNull(DESTROY_FLUSH_TIMEOUT_MS) {
                    flushPendingRecords()
                }
            }
        }.apply {
            isDaemon = true
            name = "BleService-FlushOnDestroy"
            start()
=======
        // 刷新未写入的批量心率记录（带超时保护，避免长时间阻塞主线程导致 ANR）
        recordFlushJob?.cancel()
        runBlocking {
            withTimeoutOrNull(DESTROY_FLUSH_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    flushPendingRecords()
                }
            }
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
        }
        serviceScope.cancel()
        locationManager?.removeUpdates(locationListener)
        httpServerManager?.stop()
        webSocketServerManager?.stop()
        webhookManager.shutdown()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(settingsChangeListener)
    }
}