package com.github.heartratemonitor_compose.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.github.heartratemonitor_compose.service.R
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.service.posture.PostureCalibration
import com.github.heartratemonitor_compose.service.posture.PostureDetector
import com.github.heartratemonitor_compose.service.posture.PostureFeatures
import com.github.heartratemonitor_compose.service.posture.PostureType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import javax.inject.Inject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * specialUse 前台服务，绑定 BleService 获取实时心率，注册加速度传感器运行 PostureDetector，
 * 仅静坐/站立姿态下触发通知 + 震动报警。60 秒冷却避免反复报警。
 *
 * 冷启动经 ServiceBootInitializer（ContentProvider）自动恢复。
 * 用户主动冷启动时进程处于前台，startService 不会被后台启动限制拒绝；
 * 极端情况下后台 startService 可能被拒，try-catch 降级为普通服务；
 * 用户进入应用时 recoverServices 兜底恢复。
 */
@AndroidEntryPoint
class HeartRateAlarmService : Service() {

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): HeartRateAlarmService = this@HeartRateAlarmService
    }

    private val _posture = MutableStateFlow(PostureType.UNKNOWN)
    val posture = _posture.asStateFlow()

    // ── 校准状态（供 UI 绑定读取） ──

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating = _isCalibrating.asStateFlow()

    private val _calibrationProgress = MutableStateFlow(0)
    val calibrationProgress = _calibrationProgress.asStateFlow()

    private val _calibratingIsSitting = MutableStateFlow(false)
    val calibratingIsSitting = _calibratingIsSitting.asStateFlow()

    // 延迟到 onCreate 注入完成后初始化（settingsRepository 是 @Inject lateinit）
    private val _currentCalibration = MutableStateFlow<PostureCalibration?>(null)
    val currentCalibration = _currentCalibration.asStateFlow()

    /** 校准计数协程句柄：打断/重入时须取消旧协程，防止并发校准竞态 */
    private var calibrationJob: Job? = null
    private val calibrationBuffer = mutableListOf<FloatArray>()

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var reopenAppIntent: @JvmSuppressWildcards () -> Intent
    private lateinit var sensorManager: SensorManager
    private lateinit var postureDetector: PostureDetector
    private var bleService: BleService? = null
    private var isBleBound = false
    private var isResidentForeground = false
    private var alarmMachine: AlarmStateMachine? = null

    private var excludePostureDetection = false
    private var isSensorRegistered = false

    private val classifyHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** 心率订阅协程，重连/断开时需取消旧订阅避免泄漏 */
    private var heartRateJob: Job? = null

    private var freshnessJob: Job? = null

    /** 心率数据是否新鲜：一级超时（SUSPECT）起暂停预警判定，避免基于陈旧值误报 */
    @Volatile
    private var heartRateFresh = true

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        postureDetector = PostureDetector()
        _currentCalibration.value = PostureCalibration.fromJson(
            settingsRepository.getNullable(SettingsKeys.POSTURE_CALIBRATION_DATA)
        )
        postureDetector.setCalibration(_currentCalibration.value)

        val high = settingsRepository.get(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD)
        val low = settingsRepository.get(SettingsKeys.HEART_RATE_ALARM_LOW_THRESHOLD)
        val dur = settingsRepository.get(SettingsKeys.HEART_RATE_ALARM_DURATION_SECONDS)
        alarmMachine = AlarmStateMachine(
            high, low, dur.toLong() * 1000L, computeEffectiveCooldown(),
            onAlarmTriggered = ::triggerAlarm
        )

        createNotificationChannels()
        ensureResidentForeground()
        startAndBindBleService()
        excludePostureDetection = settingsRepository.get(SettingsKeys.HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION)
        applyPostureDetectionState()
        observeSettingsChanges()
    }

    /**
     * 排除时：注销传感器、停止分类任务，将姿态置为静坐（视为静止）使预警状态机正常判定。
     */
    private fun applyPostureDetectionState() {
        if (excludePostureDetection) {
            classifyHandler.removeCallbacks(classifyRunnable)
            if (isSensorRegistered) {
                sensorManager.unregisterListener(sensorListener)
                isSensorRegistered = false
            }
            // 视为静止姿态，确保预警不因姿态被跳过
            _posture.value = PostureType.SITTING
        } else {
            if (!isSensorRegistered) {
                registerAccelerometer()
                isSensorRegistered = true
            }
            classifyHandler.removeCallbacks(classifyRunnable)
            classifyHandler.post(classifyRunnable)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val alarmChannel = NotificationChannel(
                ALARM_CHANNEL_ID,
                getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.alarm_channel_desc)
                enableVibration(true)
                vibrationPattern = VIBRATION_PATTERN
                setShowBadge(true)
            }

            val residentChannel = NotificationChannel(
                RESIDENT_CHANNEL_ID,
                getString(R.string.alarm_resident_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.alarm_resident_channel_desc)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(alarmChannel)
            notificationManager.createNotificationChannel(residentChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureResidentForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        classifyHandler.removeCallbacks(classifyRunnable)
        if (isSensorRegistered) {
            sensorManager.unregisterListener(sensorListener)
            isSensorRegistered = false
        }
        if (isBleBound) {
            try {
                unbindService(bleServiceConnection)
            } catch (_: Exception) {
            }
            isBleBound = false
        }
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        isResidentForeground = false
    }

    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bleService = (service as BleService.LocalBinder).getService()
            isBleBound = true
            observeHeartRate()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // 取消旧的心率订阅协程，避免 collect 旧 BleService 的 StateFlow 永不退出导致泄漏
            heartRateJob?.cancel()
            heartRateJob = null
            freshnessJob?.cancel()
            freshnessJob = null
            heartRateFresh = true
            bleService = null
            isBleBound = false
        }
    }

    private fun startAndBindBleService() {
        Intent(this, BleService::class.java).also { intent ->
            // startService 确保 BleService 进入前台模式（onStartCommand → startForegroundService）。
            // START_STICKY 后台重启时若 ensureResidentForeground 失败，本服务未处于前台状态，
            // startService 可能被系统拒绝，捕获后仍尝试 bindService 获取数据。
            try {
                startService(intent)
            } catch (_: Exception) {
            }
            bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeHeartRate() {
        // 取消上一次订阅（防御性：重连时旧协程可能仍在 collect 旧 BleService 的 StateFlow）
        heartRateJob?.cancel()
        freshnessJob?.cancel()
        freshnessJob = serviceScope.launch {
            bleService?.heartRateFreshness?.collect { freshness ->
                val fresh = freshness == HeartRateFreshness.FRESH
                // 断流后恢复新鲜：重置越界计时，空窗期不计入越界时长
                if (fresh && !heartRateFresh) alarmMachine?.resetBreachTimers()
                heartRateFresh = fresh
            }
        }
        heartRateJob = serviceScope.launch {
            bleService?.heartRate?.collect { rate ->
                // 蓝牙断开时心率为 0，忽略以避免误触发低限报警
                if (rate <= 0) return@collect
                // 一级超时（疑似停发）期间暂停判定，避免基于陈旧值误报
                if (!heartRateFresh) return@collect
                // 排除姿态检测时视为静止姿态，否则使用姿态分类结果
                val currentPosture = if (excludePostureDetection) {
                    PostureType.SITTING
                } else {
                    postureDetector.currentStablePosture()
                }
                alarmMachine?.onHeartRate(rate, currentPosture)
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            postureDetector.onSensorSample(x, y, z)
            // 校准期间收集样本，供 finishCalibration 计算特征
            if (_isCalibrating.value) {
                calibrationBuffer.add(floatArrayOf(x, y, z))
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun registerAccelerometer() {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    /** 每 200ms 分类一次姿态并更新 StateFlow */
    private val classifyRunnable = object : Runnable {
        override fun run() {
            val posture = postureDetector.classify()
            _posture.value = posture
            classifyHandler.postDelayed(this, CLASSIFY_INTERVAL_MS)
        }
    }

    // ── 校准（供 UI 通过 Binder 调用） ──

    fun startCalibration(isSitting: Boolean) {
        calibrationJob?.cancel()
        calibrationJob = null
        _calibratingIsSitting.value = isSitting
        _isCalibrating.value = true
        _calibrationProgress.value = 0
        calibrationBuffer.clear()

        calibrationJob = serviceScope.launch {
            for (i in 1..CALIBRATION_DURATION_SECONDS) {
                delay(1000L)
                _calibrationProgress.value = i
            }
            _isCalibrating.value = false
            finishCalibration()
            calibrationJob = null
        }
    }

    fun cancelCalibration() {
        calibrationJob?.cancel()
        calibrationJob = null
        _isCalibrating.value = false
        calibrationBuffer.clear()
    }

    fun clearCalibration() {
        cancelCalibration()
        settingsRepository.remove(SettingsKeys.POSTURE_CALIBRATION_DATA)
        _currentCalibration.value = null
        postureDetector.setCalibration(null)
    }

    /**
     * 计算校准缓冲区特征并持久化。
     * 计算三轴均值与加速度模长标准差，生成 [PostureFeatures]，
     * 追加到对应姿态的样本列表中。
     */
    private fun finishCalibration() {
        if (calibrationBuffer.isEmpty()) return
        val isSitting = _calibratingIsSitting.value
        val samples = calibrationBuffer.toList()
        calibrationBuffer.clear()

        val n = samples.size
        val meanX = samples.map { it[0] }.average().toFloat()
        val meanY = samples.map { it[1] }.average().toFloat()
        val meanZ = samples.map { it[2] }.average().toFloat()
        val magnitudes = samples.map { sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) }
        val stdMag = sqrt(magnitudes.map { (it - magnitudes.average()) * (it - magnitudes.average()) }.average()).toFloat()

        val features = PostureFeatures(meanX, meanY, meanZ, stdMag, n)
        val existing = _currentCalibration.value
        val sitSamples = existing?.sittingSamples ?: persistentListOf()
        val standSamples = existing?.standingSamples ?: persistentListOf()
        val updated = if (isSitting) {
            PostureCalibration(
                sittingSamples = (sitSamples + features).toImmutableList(),
                standingSamples = standSamples,
                motionThreshold = existing?.motionThreshold ?: 1.5f,
                calibratedAt = System.currentTimeMillis()
            )
        } else {
            PostureCalibration(
                sittingSamples = sitSamples,
                standingSamples = (standSamples + features).toImmutableList(),
                motionThreshold = existing?.motionThreshold ?: 1.5f,
                calibratedAt = System.currentTimeMillis()
            )
        }
        settingsRepository.set(SettingsKeys.POSTURE_CALIBRATION_DATA, updated.toJson())
        _currentCalibration.value = updated
        postureDetector.setCalibration(updated)
    }

    private fun triggerAlarm(rate: Int, isHigh: Boolean, posture: PostureType, threshold: Int) {
        val direction = if (isHigh) getString(R.string.alarm_exceeded_high) else getString(R.string.alarm_below_low)
        // 数值以 String 传入（%1$s/%3$s），规避小语种（ne/bn/ar）locale 整数格式化输出本地数字（如 Devanagari १२०）
        val body = getString(R.string.alarm_notification_body, rate.toString(), direction, threshold.toString(), getString(posture.labelRes))
        showAlarmNotification(body)
        vibrate()
    }

    private fun showAlarmNotification(body: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle(getString(R.string.alarm_notification_title))
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_heart)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(ALARM_NOTIFICATION_ID, notification)
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATION_PATTERN, -1)
        }
    }

    // ========== 前台保活（复用 StatusBarResidentService 模式） ==========

    /**
     * 以 specialUse 类型提升为前台服务，防止系统在锁屏/内存压力下杀死服务。
     * - 首次由 Activity（前台）启动时：startForeground 成功，持续保活。
     * - START_STICKY 重启时若 app 在后台：startForeground 可能抛
     *   ForegroundServiceStartNotAllowedException，捕获后降级为普通服务；
     *   用户下次打开 App 时会重新建立前台状态。
     */
    private fun ensureResidentForeground() {
        if (isResidentForeground) return
        try {
            val notification = createResidentNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isResidentForeground = true
        } catch (_: Exception) {
            // 后台 START_STICKY 重启时可能被拒绝，降级为普通服务
            isResidentForeground = false
        }
    }

    /**
     * 常驻前台通知（低重要性：不在状态栏显示，仅在通知栏可见）。
     * Android 13+ 前台服务通知默认延迟显示，对用户无干扰。
     */
    private fun createResidentNotification(): Notification {
        return NotificationCompat.Builder(this, RESIDENT_CHANNEL_ID)
            .setContentTitle(getString(R.string.alarm_resident_notification_title))
            .setContentText(getString(R.string.alarm_resident_notification_text))
            .setSmallIcon(R.drawable.ic_heart)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, reopenAppIntent(),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
    }

    /**
     * 通过 SettingsRepository 的 StateFlow 监听设置变更，替代原
     * SharedPreferences.OnSharedPreferenceChangeListener。
     * 各流 drop(1) 跳过订阅时的初始发射，保持原 listener「仅响应注册后变化」的语义；
     * 收集协程随 [serviceScope] 在 onDestroy 中统一取消。
     */
    private fun observeSettingsChanges() {
        serviceScope.launch {
            merge(
                settingsRepository.observe(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD).drop(1),
                settingsRepository.observe(SettingsKeys.HEART_RATE_ALARM_LOW_THRESHOLD).drop(1),
                settingsRepository.observe(SettingsKeys.HEART_RATE_ALARM_DURATION_SECONDS).drop(1),
                settingsRepository.observe(SettingsKeys.HEART_RATE_ALARM_REPEAT_ENABLED).drop(1),
                settingsRepository.observe(SettingsKeys.HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES).drop(1)
            ).collect { reloadAlarmConfig() }
        }
        serviceScope.launch {
            settingsRepository.observe(SettingsKeys.HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION)
                .drop(1)
                .collect { enabled ->
                    excludePostureDetection = enabled
                    applyPostureDetectionState()
                }
        }
        serviceScope.launch {
            settingsRepository.observeNullable(SettingsKeys.POSTURE_CALIBRATION_DATA)
                .drop(1)
                .collect { json ->
                    val cal = PostureCalibration.fromJson(json)
                    _currentCalibration.value = cal
                    postureDetector.setCalibration(cal)
                }
        }
    }

    private fun computeEffectiveCooldown(): Long {
        val isRepeatEnabled = settingsRepository.get(SettingsKeys.HEART_RATE_ALARM_REPEAT_ENABLED)
        val intervalMinutes = settingsRepository.get(SettingsKeys.HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES)
        return if (isRepeatEnabled) intervalMinutes * 60_000L else AlarmStateMachine.DEFAULT_COOLDOWN_MS
    }

    private fun reloadAlarmConfig() {
        val high = settingsRepository.get(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD)
        val low = settingsRepository.get(SettingsKeys.HEART_RATE_ALARM_LOW_THRESHOLD)
        val dur = settingsRepository.get(SettingsKeys.HEART_RATE_ALARM_DURATION_SECONDS)
        alarmMachine?.updateThresholds(high, low, dur)
        alarmMachine?.updateCooldown(computeEffectiveCooldown())
    }

    companion object {
        private const val NOTIFICATION_ID = 0x5B02
        private const val ALARM_NOTIFICATION_ID = 0x5B03
        private const val RESIDENT_CHANNEL_ID = "heart_rate_alarm_resident"
        private const val ALARM_CHANNEL_ID = "heart_rate_alarm"
        private const val CLASSIFY_INTERVAL_MS = 200L
        const val CALIBRATION_DURATION_SECONDS = 10
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 300, 500)
    }
}
