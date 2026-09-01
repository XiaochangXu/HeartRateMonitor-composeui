package com.github.heartratemonitor_compose.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.github.heartratemonitor_compose.service.R
import com.github.heartratemonitor_compose.ble.BleManager
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.ble.HeartRateMeasurement
import com.github.heartratemonitor_compose.data.db.HeartRateDao
import com.github.heartratemonitor_compose.data.model.ChartDataSnapshot
import com.github.heartratemonitor_compose.data.model.ScannedDevice
import com.github.heartratemonitor_compose.data.repository.SessionRepository
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.webhook.WebhookRepository
import com.github.heartratemonitor_compose.service.server.ServerHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 扫描/连接/断开/自动重连状态机见 [BleConnectionHandler]，
 * 前台通知构建见 [BleNotificationManager]。
 * 本类保留 Service 生命周期编排与组件构造。
 * Phase 5 起依赖由 Hilt 字段注入。
 */
@AndroidEntryPoint
class BleService : Service(), FairMemoryReceiver.MemoryListener, BleConnectionManager {

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // 设置变更收集专用主线程作用域：回调涉及位置监听注册（需 Looper 线程）与前台服务启动，
    // 不复用 IO 作用域；onDestroy 中与 settingsListener.unregister() 一同取消。
    private val settingsScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Inject lateinit var webhookRepository: WebhookRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var heartRateDao: HeartRateDao
    @Inject lateinit var heartRateRepository: HeartRateRepository
    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var fairMemoryReceiver: FairMemoryReceiver
    @Inject lateinit var lanTransferSharedState: LanTransferSharedState
    @Inject lateinit var reopenAppIntent: @JvmSuppressWildcards () -> Intent

    private lateinit var bleManager: BleManager
    private lateinit var heartRateRecorder: HeartRateRecorder
    private lateinit var speedProvider: SpeedProvider
    private lateinit var serverHost: ServerHost
    private lateinit var broadcastManager: BleBroadcastManager
    private lateinit var settingsListener: BleSettingsListener
    private lateinit var connectionHandler: BleConnectionHandler
    private lateinit var notificationManager: BleNotificationManager

    // 心率数据新鲜度（自适应超时）：手表测量失败仅停发包不断连时，
    // 一级暂停预警判定，二级清零全链路降级为 --
    private val heartRateFreshnessTracker = HeartRateFreshnessTracker(serviceScope)
    val heartRateFreshness: StateFlow<HeartRateFreshness> = heartRateFreshnessTracker.freshness

    override val bleState: StateFlow<BleState> get() = connectionHandler.bleState
    override val heartRate: StateFlow<Int> get() = connectionHandler.heartRate
    override val heartRateMeasurement: StateFlow<HeartRateMeasurement> get() = connectionHandler.heartRateMeasurement
    override val speed: StateFlow<Float> get() = heartRateRepository.speed
    override val scanResults: StateFlow<List<ScannedDevice>> get() = connectionHandler.scanResults
    override val connectedDevice: StateFlow<ConnectedDevice?> get() = connectionHandler.connectedDevice
    override val chartDataSnapshot: StateFlow<ChartDataSnapshot?> get() = connectionHandler.chartDataSnapshot
    override val sessionMaxHr: StateFlow<Int> get() = connectionHandler.sessionMaxHr
    override val sessionMinHr: StateFlow<Int> get() = connectionHandler.sessionMinHr

    override fun isDeviceConnected(): Boolean = connectionHandler.isDeviceConnected()
    override fun startScan(durationMillis: Long) = connectionHandler.startScan(durationMillis)
    override fun stopScan() = connectionHandler.stopScan()
    override fun startAutoConnectScan(favoriteDeviceId: String, durationMillis: Long) =
        connectionHandler.startAutoConnectScan(favoriteDeviceId, durationMillis)
    override fun connectToDevice(identifier: String) = connectionHandler.connectToDevice(identifier)
    override fun disconnectDevice() = connectionHandler.disconnectDevice()

    override fun onCreate() {
        super.onCreate()

        bleManager = BleManager()
        heartRateRecorder = createHeartRateRecorder()
        speedProvider = createSpeedProvider()
        connectionHandler = createConnectionHandler()
        serverHost = createServerHost()
        broadcastManager = createBroadcastManager()
        settingsListener = createSettingsListener()
        notificationManager = BleNotificationManager(this, settingsRepository, reopenAppIntent)

        // 初始化 Handler 内的本地化兜底设备名
        connectionHandler.initDeviceNameFallback(getString(R.string.unknown_device))

        // 注册断开 WebSocket 客户端的回调，供局域网传输页面「断开连接」按钮调用
        lanTransferSharedState.disconnectWebSocketClients = { serverHost.disconnectAllWebSocketClients() }

        notificationManager.startForeground()
        settingsListener.register()
        // 注册蓝牙适配器状态广播监听：蓝牙关闭时主动清理连接状态
        connectionHandler.registerBluetoothStateReceiver()

        // 注册公平运行内存监听：TRIM 时清空扫描缓存，KILL 时立即落盘未写入心率记录
        fairMemoryReceiver.addMemoryListener(this)

        // 清理上次崩溃/被杀遗留的僵尸会话（endTime 仍为 NULL 的心率会话）。
        // 放在 BleService.onCreate 而非 MainActivity.onCreate：onCreate 仅在服务首次创建或
        // 被杀重启时执行，退到后台再回来时不会执行——从而不会误关后台正在写入的活动会话。
        serviceScope.launch { sessionRepository.closeOpenSessions() }

        // 服务器 start/stop 涉及 Socket bind，移至 IO 线程避免阻塞主线程
        serviceScope.launch { serverHost.update() }
        speedProvider.update()
        broadcastManager.broadcast()

        // 二级超时（STALE）联动：心率清零 + 清空测量源，下游 UI（rate <= 0 显示 --）、
        // 预警服务（rate <= 0 过滤）、局域网广播自动降级，无需逐个打补丁
        serviceScope.launch {
            heartRateFreshnessTracker.freshness.collect { state ->
                // 二次确认当前状态：STALE 发射与本次处理之间可能已有新包到达（已回 FRESH），
                // 此时不能把刚更新的有效心率清零，避免数据恢复瞬间显示闪 --
                if (state == HeartRateFreshness.STALE &&
                    heartRateFreshnessTracker.freshness.value == HeartRateFreshness.STALE
                ) {
                    connectionHandler.clearHeartRateOnStale()
                }
            }
        }
    }

    // ── 组件工厂（构造注入依赖集中于此，便于阅读装配顺序与后续测试替换）──

    private fun createHeartRateRecorder(): HeartRateRecorder = HeartRateRecorder(
        settingsRepository = settingsRepository,
        dao = heartRateDao,
        scope = serviceScope
    )

    private fun createSpeedProvider(): SpeedProvider =
        SpeedProvider(applicationContext, settingsRepository, heartRateRepository)

    private fun createConnectionHandler(): BleConnectionHandler = BleConnectionHandler(
        context = this,
        bleManager = bleManager,
        settingsRepository = settingsRepository,
        webhookRepository = webhookRepository,
        heartRateRecorder = heartRateRecorder,
        speedProvider = speedProvider,
        // 延迟解析：broadcastManager 构造晚于 connectionHandler（依赖其状态流），运行时求值
        broadcast = { broadcastManager.broadcast() },
        freshnessTracker = heartRateFreshnessTracker,
        repository = heartRateRepository,
        scope = serviceScope
    )

    private fun createServerHost(): ServerHost = ServerHost(
        context = applicationContext,
        settingsRepository = settingsRepository,
        heartRate = connectionHandler.heartRate,
        speed = heartRateRepository.speed,
        isDeviceConnected = connectionHandler::isDeviceConnected,
        getStatusMessage = { connectionHandler.bleState.value.getMessage(applicationContext) },
        webSocketClientCount = lanTransferSharedState.webSocketClientCount,
        serverRuntimeStatus = lanTransferSharedState.serverRuntimeStatus,
        connectedClientInfo = lanTransferSharedState.connectedClientInfo
    )

    private fun createBroadcastManager(): BleBroadcastManager = BleBroadcastManager(
        emitState = serverHost::emitState,
        heartRate = connectionHandler.heartRate,
        speed = heartRateRepository.speed,
        bleState = connectionHandler.bleState,
        isDeviceConnected = connectionHandler::isDeviceConnected,
        context = applicationContext
    )

    private fun createSettingsListener(): BleSettingsListener = BleSettingsListener(
        settingsRepository = settingsRepository,
        scope = settingsScope,
        onServerSettingsChanged = { serviceScope.launch { serverHost.update() } },
        onSpeedSettingsChanged = {
            speedProvider.update()
            notificationManager.startForeground()
            broadcastManager.broadcast()
        },
        onHistoryRecordingDisabled = { serviceScope.launch { heartRateRecorder.endSession() } },
        onChartCacheClear = {
            // 关闭历史记录开关时清空图表缓存（原 UI 层 ChartDataManager.clear 联动下移至服务层）
            // SessionChartTracker 方法 @Synchronized 线程安全，无需切线程
            connectionHandler.clearChartCache()
        }
    )

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BleService", "Service onStartCommand, refreshing state...")
        speedProvider.update()
        notificationManager.startForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // 用户从最近任务列表滑掉时提前刷新，给 onDestroy 留更充裕的时间
        heartRateRecorder.cancelFlushLoop()
        serviceScope.launch { heartRateRecorder.flushPendingRecords() }
    }

    override fun onDestroy() {
        super.onDestroy()
        lanTransferSharedState.disconnectWebSocketClients = null
        lanTransferSharedState.webSocketClientCount.value = 0
        lanTransferSharedState.connectedClientInfo.value = null
        fairMemoryReceiver.removeMemoryListener(this)
        // 从缓冲区取出待写入记录并交给 WorkManager 异步落盘。
        // 不在主线程阻塞等待 I/O，消除 ANR 风险；
        // WorkManager 持久化工作请求——即使进程被杀也会在下次启动时补执行，保证数据不丢失。
        heartRateRecorder.cancelFlushLoop()
        val pending = heartRateRecorder.drainPendingRecords()
        if (pending.isNotEmpty()) {
            FlushRecordsWorker.enqueue(applicationContext, pending)
        }
        // 先注销蓝牙状态广播监听，避免 scope.cancel 后 onBluetoothDisabled 的异步清理被吞掉
        connectionHandler.unregisterBluetoothStateReceiver()
        // 连接/扫描协程均运行在 serviceScope，随作用域取消一并终止
        serviceScope.cancel()
        speedProvider.stop()
        serverHost.stop()
        // WebhookRepository 是应用级单例，不在 Service 生命周期内 shutdown
        settingsListener.unregister()
        settingsScope.cancel()
    }

    /** 公平运行内存 TRIM：清空蓝牙扫描缓存 + 释放图表缓存。 */
    override fun onTrimMemory(notifyType: Int) {
        connectionHandler.trimScanCacheIfIdle()
        connectionHandler.releaseChartOnTrim(notifyType)
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
