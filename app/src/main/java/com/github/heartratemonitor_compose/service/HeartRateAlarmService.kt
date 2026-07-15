package com.github.heartratemonitor_compose.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
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
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.service.posture.PostureCalibration
import com.github.heartratemonitor_compose.service.posture.PostureDetector
import com.github.heartratemonitor_compose.service.posture.PostureType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 心率预警服务。
 *
 * specialUse 前台服务，绑定 BleService 获取实时心率，注册加速度传感器运行 PostureDetector，
 * 驱动 AlarmStateMachine 判定：仅静坐/站立姿态下，心率连续超过高限或低于低限达设定秒数时
 * 触发通知（IMPORTANCE_HIGH）+ 震动报警。60 秒冷却避免反复报警。
 *
 * 冷启动经 HeartRateAlarmInitializer（ContentProvider）自动恢复。
 * - 用户主动冷启动时进程处于前台，startService 不会被后台启动限制拒绝；
 *   服务在 onStartCommand 中自行调用 startForeground 提升为前台保活。
 * - 极端情况下后台 startService 可能被拒，try-catch 降级为普通服务；
 *   用户进入设置页时 recoverHeartRateAlarmIfNeeded 兜底恢复。
 */
class HeartRateAlarmService : Service() {

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): HeartRateAlarmService = this@HeartRateAlarmService
    }

    /** 当前姿态（供外部绑定观察，预留扩展） */
    private val _posture = MutableStateFlow(PostureType.UNKNOWN)
    val posture = _posture.asStateFlow()

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var sensorManager: SensorManager
    private lateinit var postureDetector: PostureDetector
    private var bleService: BleService? = null
    private var isBleBound = false
    private var isResidentForeground = false
    private var alarmMachine: AlarmStateMachine? = null

    private val classifyHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ========== 生命周期 ==========

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        postureDetector = PostureDetector()
        postureDetector.setCalibration(
            PostureCalibration.fromJson(sharedPreferences.getString("posture_calibration_data", null))
        )

        val high = sharedPreferences.getInt("heart_rate_alarm_high_threshold", 100)
        val low = sharedPreferences.getInt("heart_rate_alarm_low_threshold", 50)
        val dur = sharedPreferences.getInt("heart_rate_alarm_duration_seconds", 10)
        alarmMachine = AlarmStateMachine(high, low, dur.toLong() * 1000L, computeEffectiveCooldown())

        createNotificationChannels()
        ensureResidentForeground()
        startAndBindBleService()
        registerAccelerometer()
        classifyHandler.post(classifyRunnable)
        sharedPreferences.registerOnSharedPreferenceChangeListener(settingsListener)
    }

    /**
     * 一次性创建所有通知通道（系统幂等,但避免每次报警重复构造 NotificationChannel 对象）
     */
    private fun createNotificationChannels() {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "心率预警",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "心率超出设定区间时发出预警通知"
            enableVibration(true)
            vibrationPattern = VIBRATION_PATTERN
            setShowBadge(true)
        }

        val residentChannel = NotificationChannel(
            RESIDENT_CHANNEL_ID,
            "心率预警常驻",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持心率预警服务存活"
            setShowBadge(false)
        }

        notificationManager.createNotificationChannel(alarmChannel)
        notificationManager.createNotificationChannel(residentChannel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureResidentForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        classifyHandler.removeCallbacks(classifyRunnable)
        sensorManager.unregisterListener(sensorListener)
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
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(settingsListener)
    }

    // ========== BleService 绑定 ==========

    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            bleService = (service as BleService.LocalBinder).getService()
            isBleBound = true
            observeHeartRate()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isBleBound = false
        }
    }

    private fun startAndBindBleService() {
        Intent(this, BleService::class.java).also { intent ->
            // startService 确保 BleService 进入前台模式（onStartCommand → startForegroundService）
            startService(intent)
            bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeHeartRate() {
        serviceScope.launch {
            bleService?.heartRate?.collect { rate ->
                // 蓝牙断开时心率为 0，忽略以避免误触发低限报警
                if (rate <= 0) return@collect
                val currentPosture = postureDetector.currentStablePosture()
                alarmMachine?.onHeartRate(rate, currentPosture)
            }
        }
    }

    // ========== 加速度传感器 ==========

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            postureDetector.onSensorSample(event.values[0], event.values[1], event.values[2])
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

    // ========== AlarmStateMachine（无定时器，由心率 Flow 驱动） ==========

    /**
     * 心率预警状态机。
     *
     * 用时间戳记录高/低限越界起始时刻，每次心率更新时增量判定。
     * 仅静坐/站立姿态（isStationary）触发检测；运动/未知姿态跳过。
     * 报警后进入冷却期（默认 60 秒，可由重复报警设置覆盖），期间不重复判定。
     */
    inner class AlarmStateMachine(
        var highThreshold: Int,
        var lowThreshold: Int,
        private var durationMs: Long,
        private var cooldownMs: Long = COOLDOWN_MS
    ) {
        private var highBreachStart = 0L
        private var lowBreachStart = 0L
        private var lastAlarmTime = 0L

        fun onHeartRate(rate: Int, posture: PostureType, now: Long = System.currentTimeMillis()) {
            // 阈值倒置校验：highThreshold 必须 > lowThreshold，否则配置无效，跳过检测
            if (highThreshold <= lowThreshold) {
                highBreachStart = 0L
                lowBreachStart = 0L
                return
            }
            // 冷却期内不判定
            if (lastAlarmTime > 0 && now - lastAlarmTime < cooldownMs) {
                highBreachStart = 0L
                lowBreachStart = 0L
                return
            }
            // 仅静止姿态（静坐/站立）触发检测
            if (!posture.isStationary) {
                highBreachStart = 0L
                lowBreachStart = 0L
                return
            }
            // 高限检测
            if (rate > highThreshold) {
                if (highBreachStart == 0L) highBreachStart = now
                if (now - highBreachStart >= durationMs) {
                    triggerAlarm(rate, isHigh = true, posture, highThreshold)
                    lastAlarmTime = now
                    highBreachStart = 0L
                    lowBreachStart = 0L
                }
            } else {
                highBreachStart = 0L
            }
            // 低限检测
            if (rate < lowThreshold) {
                if (lowBreachStart == 0L) lowBreachStart = now
                if (now - lowBreachStart >= durationMs) {
                    triggerAlarm(rate, isHigh = false, posture, lowThreshold)
                    lastAlarmTime = now
                    highBreachStart = 0L
                    lowBreachStart = 0L
                }
            } else {
                lowBreachStart = 0L
            }
        }

        fun updateThresholds(high: Int, low: Int, durationSec: Int) {
            highThreshold = high
            lowThreshold = low
            durationMs = durationSec.toLong() * 1000L
        }

        fun updateCooldown(cooldownMs: Long) {
            this.cooldownMs = cooldownMs
        }
    }

    // ========== 触发报警 ==========

    private fun triggerAlarm(rate: Int, isHigh: Boolean, posture: PostureType, threshold: Int) {
        val direction = if (isHigh) "超过高限" else "低于低限"
        val body = "心率 ${rate} BPM ${direction} ${threshold}（${posture.label}状态），请关注"
        showAlarmNotification(body)
        vibrate()
    }

    private fun showAlarmNotification(body: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle("心率预警")
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
            .setContentTitle("心率预警")
            .setContentText("心率预警服务运行中")
            .setSmallIcon(R.drawable.ic_heart)
            .setOngoing(true)
            .build()
    }

    // ========== SharedPreferences 热更新 ==========

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "heart_rate_alarm_high_threshold",
            "heart_rate_alarm_low_threshold",
            "heart_rate_alarm_duration_seconds",
            "heart_rate_alarm_repeat_enabled",
            "heart_rate_alarm_repeat_interval_minutes" -> reloadAlarmConfig()
            "posture_calibration_data" -> {
                postureDetector.setCalibration(
                    PostureCalibration.fromJson(
                        sharedPreferences.getString("posture_calibration_data", null)
                    )
                )
            }
        }
    }

    private fun computeEffectiveCooldown(): Long {
        val isRepeatEnabled = sharedPreferences.getBoolean("heart_rate_alarm_repeat_enabled", false)
        val intervalMinutes = sharedPreferences.getInt("heart_rate_alarm_repeat_interval_minutes", 5)
        return if (isRepeatEnabled) intervalMinutes * 60_000L else COOLDOWN_MS
    }

    private fun reloadAlarmConfig() {
        val high = sharedPreferences.getInt("heart_rate_alarm_high_threshold", 100)
        val low = sharedPreferences.getInt("heart_rate_alarm_low_threshold", 50)
        val dur = sharedPreferences.getInt("heart_rate_alarm_duration_seconds", 10)
        alarmMachine?.updateThresholds(high, low, dur)
        alarmMachine?.updateCooldown(computeEffectiveCooldown())
    }

    companion object {
        private const val NOTIFICATION_ID = 0x5B02
        private const val ALARM_NOTIFICATION_ID = 0x5B03
        private const val RESIDENT_CHANNEL_ID = "heart_rate_alarm_resident"
        private const val ALARM_CHANNEL_ID = "heart_rate_alarm"
        private const val CLASSIFY_INTERVAL_MS = 200L
        private const val COOLDOWN_MS = 60_000L
        private val VIBRATION_PATTERN = longArrayOf(0, 500, 300, 500)
    }
}
