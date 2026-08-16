package com.github.heartratemonitor_compose.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import com.github.heartratemonitor_compose.service.R
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.ui.theme.CustomSchemeCache
import com.github.heartratemonitor_compose.ui.theme.HeartRateMonitorMobileTheme
import com.github.heartratemonitor_compose.ui.theme.ThemeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class StatusBarResidentService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var viewTreeOwners: ServiceViewTreeOwners
    private lateinit var layoutParams: WindowManager.LayoutParams

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var customSchemeCache: CustomSchemeCache

    private var heartRateText by mutableStateOf("--")
    private var bpmForAnimation by mutableStateOf(0)
    private var isAnimationEnabled by mutableStateOf(true)
    private var isConnected by mutableStateOf(false)
    private var appearance by mutableStateOf(StatusBarOverlayAppearance())

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bleService: BleService? = null
    private var isServiceBound = false
    /** 当前生效的 BleService 心率订阅，重新订阅前先取消，避免叠加重复收集 */
    private var bleDataJob: Job? = null

    private var settingsJobs: List<Job> = emptyList()

    private var isOverlayShown = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            isServiceBound = true
            observeBleData()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isServiceBound = false
            updateHeartRateText(0)
        }
    }

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) {
            relayout()
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onLowMemory() {}
        override fun onTrimMemory(level: Int) {}
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> hideOverlay()
                Intent.ACTION_SCREEN_ON -> {
                    // 屏幕亮起：仅在已解锁时恢复 overlay，避免在锁屏界面之上显示
                    val keyguardManager = getSystemService(KeyguardManager::class.java)
                    if (!keyguardManager.isKeyguardLocked) {
                        showOverlay()
                    }
                }
                Intent.ACTION_USER_PRESENT -> showOverlay()
            }
        }
    }

    // specialUse 前台服务防止系统在锁屏/内存压力下杀死服务，保证 overlay 持续可用。
    private var isResidentForeground = false
    private val safetyHandler = Handler(Looper.getMainLooper())

    /**
     * 替代原 SharedPreferences.OnSharedPreferenceChangeListener。
     * 各流 drop(1) 跳过初始发射，保持原 listener「仅响应注册后变化」的语义。
     */
    private fun observeSettingsChanges() {
        settingsJobs = listOf(
            serviceScope.launch {
                settingsRepository.observe(SettingsKeys.STATUS_BAR_SIZE)
                    .drop(1)
                    .collect { if (isOverlayShown) applySize() }
            },
            serviceScope.launch {
                merge(
                    settingsRepository.observe(SettingsKeys.STATUS_BAR_X_POSITION).drop(1),
                    settingsRepository.observe(SettingsKeys.STATUS_BAR_Y_OFFSET).drop(1)
                ).collect {
                    if (!isOverlayShown) return@collect
                    updatePosition()
                    try {
                        windowManager.updateViewLayout(composeView, layoutParams)
                    } catch (_: Exception) {
                    }
                }
            },
            serviceScope.launch {
                merge(
                    settingsRepository.observe(SettingsKeys.STATUS_BAR_BPM_TEXT_ENABLED).drop(1),
                    settingsRepository.observe(SettingsKeys.STATUS_BAR_TEXT_THICKNESS).drop(1)
                ).collect { if (isOverlayShown) applyTextStyle() }
            },
            serviceScope.launch {
                settingsRepository.observe(SettingsKeys.STATUS_BAR_TEXT_COLOR)
                    .drop(1)
                    .collect { if (isOverlayShown) applyAppearance() }
            }
        )
    }

    /**
     * 兜底处理广播遗漏、服务被杀后 START_STICKY 重启等场景，确保锁屏解锁后 overlay 自动恢复。
     */
    private val overlaySafetyCheck = object : Runnable {
        override fun run() {
            try {
                if (settingsRepository.get(SettingsKeys.STATUS_BAR_RESIDENT_ENABLED)) {
                    val powerManager = getSystemService(PowerManager::class.java)
                    val keyguardManager = getSystemService(KeyguardManager::class.java)
                    if (powerManager.isInteractive && !keyguardManager.isKeyguardLocked) {
                        if (!isOverlayShown || (isOverlayShown && !composeView.isAttachedToWindow)) {
                            showOverlay()
                        }
                    }
                }
            } catch (_: Exception) {
            }
            safetyHandler.postDelayed(this, SAFETY_CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HeartRateMonitorMobile)

        composeView = ComposeView(themedContext)
        viewTreeOwners = ServiceViewTreeOwners().also { it.attachToView(composeView) }
        composeView.setContent {
            val config by themeState.config.collectAsState()
            HeartRateMonitorMobileTheme(config = config, customSchemeCache = customSchemeCache) {
                StatusBarOverlayContent(
                    heartRate = heartRateText,
                    bpm = bpmForAnimation,
                    isAnimationEnabled = isAnimationEnabled,
                    isConnected = isConnected,
                    appearance = appearance,
                    statusBarHeightPx = getStatusBarHeight()
                )
            }
        }

        initLayoutParams()
        applyAppearance()

        // 绑定 BleService 获取心率数据（仅 bind，不 start，避免冷重启后台启动限制）
        Intent(this, BleService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        applicationContext.registerComponentCallbacks(componentCallbacks)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        observeSettingsChanges()

        safetyHandler.postDelayed(overlaySafetyCheck, SAFETY_CHECK_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            // 防御性：无悬浮窗权限则不显示
            stopSelf()
            return START_STICKY
        }
        ensureResidentForeground()
        showOverlay()
        return START_STICKY
    }

    /**
     * 首次由 MainActivity（前台）启动时：startForeground 成功，持续保活。
     * START_STICKY 重启时若 app 在后台：startForeground 可能抛
     * ForegroundServiceStartNotAllowedException，捕获后降级为普通服务。
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
     * Android 13+ 前台服务通知默认延迟显示，对状态栏 overlay 无干扰。
     */
    private fun createResidentNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                RESIDENT_CHANNEL_ID,
                getString(R.string.status_bar_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.status_bar_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        // 用 NotificationCompat.Builder 而非 Notification.Builder(Context, String)：
        // 后者是 API 26+ 构造器，minSdk=24 的 Android 7.x 上会抛 NoSuchMethodError
        //（Error 不会被下方 catch (Exception) 捕获，直接导致进程崩溃）。
        return NotificationCompat.Builder(this, RESIDENT_CHANNEL_ID)
            .setContentTitle(getString(R.string.status_bar_notification_title))
            .setContentText(getString(R.string.status_bar_notification_text))
            .setSmallIcon(R.drawable.ic_heart)
            .setOngoing(true)
            .build()
    }

    private fun initLayoutParams() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            getStatusBarHeight(),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        updatePosition()
    }

    private fun showOverlay() {
        if (isOverlayShown && composeView.isAttachedToWindow) return
        // 状态不一致修正：标记显示但窗口已被系统移除（锁屏/内存压力），重置标记
        isOverlayShown = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        try {
            applySize()
            applyTextStyle()
            // 防御性：如果窗口仍 attached（理论不该发生），先移除避免重复添加异常
            if (composeView.isAttachedToWindow) {
                windowManager.removeView(composeView)
            }
            windowManager.addView(composeView, layoutParams)
            isOverlayShown = true
            applyAppearance()
        } catch (_: Exception) {
        }
    }

    private fun hideOverlay() {
        if (!isOverlayShown) return
        try {
            windowManager.removeView(composeView)
        } catch (_: Exception) {
        }
        isOverlayShown = false
    }

    private fun observeBleData() {
        // 取消旧订阅：BleService 重建后重新 onServiceConnected 会再次调用本方法，
        // 旧协程会持有已销毁服务实例的 StateFlow，必须避免叠加重复收集。
        bleDataJob?.cancel()
        bleDataJob = serviceScope.launch {
            bleService?.heartRate?.collectLatest { rate ->
                updateHeartRateText(rate)
                updateHeartbeatAnimation(rate)
            }
        }
    }

    private fun updateHeartRateText(rate: Int) {
        heartRateText = if (rate > 0) "$rate" else "--"
        bpmForAnimation = rate
    }

    /**
     * 实际动画由 StatusBarOverlayContent 内部的 Animatable + LaunchedEffect 驱动，
     * 此处仅刷新 bpm / 动画启用 / 连接状态，触发 LaunchedEffect 重启。
     */
    private fun updateHeartbeatAnimation(bpm: Int) {
        bpmForAnimation = bpm
        isAnimationEnabled = settingsRepository.get(SettingsKeys.HEARTBEAT_ANIMATION_ENABLED)
        isConnected = bleService?.isDeviceConnected() ?: false
    }

    private fun applyAppearance() {
        val textColor = settingsRepository.get(SettingsKeys.STATUS_BAR_TEXT_COLOR)
        appearance = appearance.copy(textColor = textColor)
    }

    /**
     * appearance 字段单位为 px，需把原 XML 的 sp（文字）与 dp（图标/间距）按 density 转为 px，
     * 与原 TextView.setTextSize(COMPLEX_UNIT_SP) / ImageView.layoutParams(dpToPx) 的视觉效果完全一致。
     */
    private fun applySize() {
        val sizePercent = settingsRepository.get(SettingsKeys.STATUS_BAR_SIZE)
        val scaleFactor = sizePercent / 100f

        appearance = appearance.copy(
            textSize = spToPx(12f * scaleFactor),
            unitTextSize = spToPx(9f * scaleFactor),
            iconSize = dpToPx(14f * scaleFactor).toFloat(),
            padding = dpToPx(6f * scaleFactor).toFloat(),
            numberMargin = dpToPx(3f * scaleFactor).toFloat(),
            unitMargin = dpToPx(1f * scaleFactor).toFloat()
        )
    }

    /**
     * status_bar_text_thickness：0-100，在原有 bold 基础上叠加 stroke 宽度实现可调加粗。
     * 实际 FILL_AND_STROKE 描边由 StatusBarOverlayContent 内的 Paint 完成。
     */
    private fun applyTextStyle() {
        val textEnabled = settingsRepository.get(SettingsKeys.STATUS_BAR_BPM_TEXT_ENABLED)
        val thickness = settingsRepository.get(SettingsKeys.STATUS_BAR_TEXT_THICKNESS)

        appearance = appearance.copy(
            thickness = thickness,
            isBpmTextEnabled = textEnabled
        )
    }

    private fun updatePosition() {
        val xPercent = settingsRepository.get(SettingsKeys.STATUS_BAR_X_POSITION)
        val screenWidth = resources.displayMetrics.widthPixels
        layoutParams.x = (screenWidth * xPercent / 100f).toInt()

        val yOffsetProgress = settingsRepository.get(SettingsKeys.STATUS_BAR_Y_OFFSET)
        val yOffsetDp = yOffsetProgress - 10  // 范围 -10 到 +10
        layoutParams.y = dpToPx(yOffsetDp.toFloat())
    }

    private fun relayout() {
        applyAppearance()
        applySize()
        applyTextStyle()
        updatePosition()
        layoutParams.height = getStatusBarHeight()
        if (isOverlayShown) {
            try {
                windowManager.updateViewLayout(composeView, layoutParams)
            } catch (_: Exception) {
            }
        }
    }

    private fun getStatusBarHeight(): Int {
        val res = resources
        val resourceId = res.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            res.getDimensionPixelSize(resourceId)
        } else {
            dpToPx(24f)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
        ).toInt()
    }

    private fun spToPx(sp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        safetyHandler.removeCallbacks(overlaySafetyCheck)
        hideOverlay()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        serviceScope.cancel()
        viewTreeOwners.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        try {
            applicationContext.unregisterComponentCallbacks(componentCallbacks)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        isResidentForeground = false
        settingsJobs.forEach { it.cancel() }
        settingsJobs = emptyList()
    }

    companion object {
        private const val NOTIFICATION_ID = 0x5B01
        private const val RESIDENT_CHANNEL_ID = "status_bar_resident"
        private const val SAFETY_CHECK_INTERVAL_MS = 3000L
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
