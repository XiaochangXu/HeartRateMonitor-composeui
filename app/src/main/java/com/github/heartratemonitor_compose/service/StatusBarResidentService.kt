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
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.theme.HeartRateMonitorMobileTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 状态栏常驻心率服务。
 *
 * 在顶部状态栏区域以 TYPE_APPLICATION_OVERLAY 叠加层绘制紧凑心率条（心形 + BPM）。
 * 独立于 FloatingWindowService，由设置页开关 startService / stopService 控制。
 * 仅 bindService(BleService) 获取心率数据，依赖同进程 BleService 前台档位保持存活。
 *
 * 阶段 5.2 起：覆盖层改用 [ComposeView] 承载 [StatusBarOverlayContent] Composable，
 * 由 [viewTreeOwners] 注入 ViewTree Lifecycle / SavedStateRegistry / ViewModelStore。
 * 文字仍以 [android.graphics.Paint.Style.FILL_AND_STROKE] 绘制（纯黑/纯白无阴影硬约束），
 * 由 [StatusBarOverlayContent] 内的 Canvas + nativeCanvas 实现，本服务不再直接操作 View 属性。
 *
 * 保留不动：MediaProjection 截屏采样、screenReceiver、overlaySafetyCheck（3s）、
 * componentCallbacks、ensureResidentForeground + FGS 类型切换、getStatusBarHeight、updatePosition。
 */
class StatusBarResidentService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var viewTreeOwners: ServiceViewTreeOwners
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var sharedPreferences: SharedPreferences

    // Compose state：变更触发 StatusBarOverlayContent 重组/重绘
    private var heartRateText by mutableStateOf("--")
    private var bpmForAnimation by mutableStateOf(0)
    private var isAnimationEnabled by mutableStateOf(true)
    private var isConnected by mutableStateOf(false)
    private var appearance by mutableStateOf(StatusBarOverlayAppearance())

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bleService: BleService? = null
    private var isServiceBound = false

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

    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val sampleHandler = Handler(Looper.getMainLooper())
    private var isSampling = false
    // 采样结果：true=背景偏深→用白字，false=背景偏浅→用黑字
    private var sampledUseWhiteText = false
    private val sampleRunnable = object : Runnable {
        override fun run() {
            sampleStatusBarBrightness()
            if (isSampling) {
                sampleHandler.postDelayed(this, SAMPLE_INTERVAL_MS)
            }
        }
    }

    // specialUse 前台服务防止系统在锁屏/内存压力下杀死服务，保证 overlay 持续可用。
    private var isResidentForeground = false
    // 标记服务正在销毁，避免 stopMediaProjectionSampling 中误调 ensureResidentForeground
    private var isDestroying = false
    private val safetyHandler = Handler(Looper.getMainLooper())

    private val settingsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!isOverlayShown) return@OnSharedPreferenceChangeListener
        when (key) {
            "status_bar_size" -> applySize()
            "status_bar_x_position", "status_bar_y_offset" -> {
                updatePosition()
                try {
                    windowManager.updateViewLayout(composeView, layoutParams)
                } catch (_: Exception) {
                }
            }
            "status_bar_bpm_text_enabled", "status_bar_text_thickness" -> applyTextStyle()
            "status_bar_white_text", "status_bar_auto_color" -> applyAppearance()
        }
    }

    /**
     * 周期性自愈检查：屏幕亮且已解锁时，若 overlay 未显示或被系统移除则重新添加。
     * 兜底处理广播遗漏、服务被杀后 START_STICKY 重启等场景，确保锁屏解锁后 overlay 自动恢复。
     */
    private val overlaySafetyCheck = object : Runnable {
        override fun run() {
            try {
                if (sharedPreferences.getBoolean("status_bar_resident_enabled", false)) {
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
        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val themedContext = ContextThemeWrapper(this, R.style.Theme_HeartRateMonitorMobile)

        composeView = ComposeView(themedContext)
        viewTreeOwners = ServiceViewTreeOwners().also { it.attachToView(composeView) }
        composeView.setContent {
            HeartRateMonitorMobileTheme {
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
        // API 26+ 支持 3 参重载；RECEIVER_NOT_EXPORTED 保证不暴露接收器
        registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        sharedPreferences.registerOnSharedPreferenceChangeListener(settingsChangeListener)

        // 启动周期性自愈检查，兜底恢复 overlay
        safetyHandler.postDelayed(overlaySafetyCheck, SAFETY_CHECK_INTERVAL_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            // 防御性：无悬浮窗权限则不显示
            stopSelf()
            return START_STICKY
        }
        when (intent?.action) {
            ACTION_START_MEDIA_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                }
                if (resultData != null) {
                    startMediaProjectionSampling(resultCode, resultData)
                }
            }
            ACTION_STOP_MEDIA_PROJECTION -> stopMediaProjectionSampling()
            else -> {
                // 普通启动 / START_STICKY 重启：先提升为前台服务保活，再显示 overlay
                ensureResidentForeground()
                showOverlay()
            }
        }
        return START_STICKY
    }

    /**
     * 以 specialUse 类型提升为前台服务，防止系统在锁屏/内存压力下杀死服务。
     * - 首次由 MainActivity（前台）启动时：startForeground 成功，持续保活。
     * - START_STICKY 重启时若 app 在后台：startForeground 可能抛
     *   ForegroundServiceStartNotAllowedException，捕获后降级为普通服务，
     *   overlay 仍可显示；用户下次打开 App 时会重新建立前台状态。
     * - 采样期间（isSampling）不抢占类型，由 startMediaProjectionSampling 管理。
     */
    private fun ensureResidentForeground() {
        if (isSampling || isResidentForeground) return
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
     * Android 13+ 前台服务通知默认延迟显示，对状态栏 overlay 无干扰。
     */
    private fun createResidentNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            RESIDENT_CHANNEL_ID,
            "状态栏心率常驻",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "保持状态栏心率显示服务存活"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
        return Notification.Builder(this, RESIDENT_CHANNEL_ID)
            .setContentTitle("心率状态栏")
            .setContentText("状态栏心率显示运行中")
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

        // 位置由用户设置控制（水平位置百分比 + 垂直微调）
        updatePosition()
    }

    private fun showOverlay() {
        // 已标记显示且窗口确实 attached：无需重复添加
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
        serviceScope.launch {
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
     * 心跳动画参数更新：实际动画由 [StatusBarOverlayContent] 内部的
     * [androidx.compose.animation.core.Animatable] + [LaunchedEffect] 驱动（1f↔1.2f）。
     * 此处仅刷新 bpm / 动画启用 / 连接 状态，触发 LaunchedEffect 重启。
     */
    private fun updateHeartbeatAnimation(bpm: Int) {
        bpmForAnimation = bpm
        isAnimationEnabled = sharedPreferences.getBoolean("heartbeat_animation_enabled", true)
        isConnected = bleService?.isDeviceConnected() ?: false
    }

    /**
     * 刷新文字/图标颜色（纯色，无阴影/描边）。
     *
     * 颜色判定优先级：
     * 1. status_bar_auto_color = true → 用 MediaProjection 采样结果（sampledUseWhiteText）
     * 2. status_bar_white_text = true → 纯白
     * 3. 否则 → 纯黑（默认）
     */
    private fun applyAppearance() {
        val autoColor = sharedPreferences.getBoolean("status_bar_auto_color", false)
        val useWhite = if (autoColor) {
            sampledUseWhiteText
        } else {
            sharedPreferences.getBoolean("status_bar_white_text", false)
        }
        val textColor = if (useWhite) Color.WHITE else Color.BLACK
        appearance = appearance.copy(textColor = textColor)
    }

    /**
     * 根据用户设置的整体大小缩放图标、文字、内边距与间距。
     *
     * appearance 字段单位为 px（直接喂给 Paint.textSize / Canvas 坐标），故需把原 XML 的
     * sp（文字）与 dp（图标/间距）按 density 转为 px，与原 TextView.setTextSize(COMPLEX_UNIT_SP)
     * / ImageView.layoutParams(dpToPx) 的视觉效果完全一致。
     */
    private fun applySize() {
        val sizePercent = sharedPreferences.getInt("status_bar_size", 100)
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
     * 根据用户设置控制 "bpm" 单位文字显隐与心率数字粗细。
     * - status_bar_bpm_text_enabled：true 显示 "bpm" 单位（图标+80+bpm），
     *   false 隐藏 "bpm" 单位（图标+80），心率数字始终显示
     * - status_bar_text_thickness：0-100，在原有 bold 基础上叠加 stroke 宽度实现可调加粗
     *   （0 = 普通 bold，100 = stroke 宽度 = 文字大小的 25%）
     *
     * 实际 FILL_AND_STROKE 描边由 [StatusBarOverlayContent] 内的 [android.graphics.Paint] 完成。
     */
    private fun applyTextStyle() {
        val textEnabled = sharedPreferences.getBoolean("status_bar_bpm_text_enabled", true)
        val thickness = sharedPreferences.getInt("status_bar_text_thickness", 0)

        appearance = appearance.copy(
            thickness = thickness,
            isBpmTextEnabled = textEnabled
        )
    }

    /**
     * 根据用户设置刷新水平位置和垂直微调。
     * 水平位置：0-100 映射为屏幕宽度的百分比（0=最左，100=最右）
     * 垂直微调：0-20 映射为 -10 到 +10 dp（中值 10 = 0dp 偏移）
     */
    private fun updatePosition() {
        val xPercent = sharedPreferences.getInt("status_bar_x_position", 0)
        val screenWidth = resources.displayMetrics.widthPixels
        layoutParams.x = (screenWidth * xPercent / 100f).toInt()

        val yOffsetProgress = sharedPreferences.getInt("status_bar_y_offset", 10)
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
        isDestroying = true
        safetyHandler.removeCallbacks(overlaySafetyCheck)
        stopMediaProjectionSampling()
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
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(settingsChangeListener)
    }

    companion object {
        const val ACTION_START_MEDIA_PROJECTION =
            "com.github.heartratemonitor_compose.START_MEDIA_PROJECTION"
        const val ACTION_STOP_MEDIA_PROJECTION =
            "com.github.heartratemonitor_compose.STOP_MEDIA_PROJECTION"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val SAMPLE_INTERVAL_MS = 1000L
        private const val NOTIFICATION_ID = 0x5B01
        private const val CHANNEL_ID = "status_bar_sampling"
        private const val RESIDENT_CHANNEL_ID = "status_bar_resident"
        private const val SAFETY_CHECK_INTERVAL_MS = 3000L
        private const val LUMINANCE_THRESHOLD = 128.0
    }

    /**
     * 启动 MediaProjection 截屏采样。
     * Android 14+（API 34）要求 MediaProjection 必须在 mediaProjection 类型的前台服务中运行，
     * 故先调 startForeground；API 29+ 用 3 参版本指定类型，低版本退回 2 参。
     */
    private fun startMediaProjectionSampling(resultCode: Int, data: Intent) {
        try {
            val notification = createSamplingNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            // 采样期间由 mediaProjection 类型接管前台，常驻标记重置
            isResidentForeground = false

            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    isSampling = false
                    sampleHandler.removeCallbacks(sampleRunnable)
                }
            }
            mediaProjectionCallback = callback
            mediaProjection?.registerCallback(callback, sampleHandler)

            val metrics = resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            imageReader = ImageReader.newInstance(
                screenWidth, screenHeight, PixelFormat.RGBA_8888, 2
            )
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "StatusBarSample",
                screenWidth, screenHeight, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface, null, sampleHandler
            )

            isSampling = true
            sampleHandler.post(sampleRunnable)
        } catch (_: Exception) {
            isSampling = false
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    /**
     * 停止 MediaProjection 截屏采样，释放资源并退出前台服务状态。
     */
    private fun stopMediaProjectionSampling() {
        isSampling = false
        sampleHandler.removeCallbacks(sampleRunnable)
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (_: Exception) {}
        imageReader = null
        try {
            mediaProjectionCallback?.let { mediaProjection?.unregisterCallback(it) }
        } catch (_: Exception) {}
        mediaProjectionCallback = null
        try { mediaProjection?.stop() } catch (_: Exception) {}
        mediaProjection = null
        sampledUseWhiteText = false
        // 停止采样后：若状态栏常驻仍开启且服务未在销毁，回退到 specialUse 常驻前台类型保活
        if (!isDestroying && sharedPreferences.getBoolean("status_bar_resident_enabled", false)) {
            isResidentForeground = false  // 重置以允许 ensureResidentForeground 重新提升
            ensureResidentForeground()
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isResidentForeground = false
        }
        applyAppearance()
    }

    /**
     * 采样状态栏区域亮度，更新 sampledUseWhiteText 并刷新外观。
     * 亮度 > 阈值 → 浅色背景 → 黑字；否则 → 白字。
     */
    private fun sampleStatusBarBrightness() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val screenWidth = resources.displayMetrics.widthPixels
            val statusBarHeight = getStatusBarHeight()

            var totalLuminance = 0.0
            var sampleCount = 0
            val xStep = 10
            val yStep = 5
            var y = 0
            while (y < statusBarHeight) {
                var x = 0
                while (x < screenWidth) {
                    val pixelIndex = y * rowStride + x * pixelStride
                    if (pixelIndex + 2 < buffer.capacity()) {
                        val r = buffer.get(pixelIndex).toInt() and 0xFF
                        val g = buffer.get(pixelIndex + 1).toInt() and 0xFF
                        val b = buffer.get(pixelIndex + 2).toInt() and 0xFF
                        totalLuminance += 0.299 * r + 0.587 * g + 0.114 * b
                        sampleCount++
                    }
                    x += xStep
                }
                y += yStep
            }

            if (sampleCount > 0) {
                val avgLuminance = totalLuminance / sampleCount
                val newUseWhite = avgLuminance < LUMINANCE_THRESHOLD
                if (newUseWhite != sampledUseWhiteText) {
                    sampledUseWhiteText = newUseWhite
                    applyAppearance()
                }
            }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }

    private fun createSamplingNotification(): Notification {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "状态栏颜色采样",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用于自动识别屏幕颜色以切换状态栏心率文字颜色"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("心率状态栏")
            .setContentText("正在自动识别屏幕颜色")
            .setSmallIcon(R.drawable.ic_heart)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
