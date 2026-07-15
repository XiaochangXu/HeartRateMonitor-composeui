package com.github.heartratemonitor_compose.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ui.theme.HeartRateMonitorMobileTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * FrameLayout 子类：拦截所有触摸事件，使其不会分发给子 View（ComposeView 内部的
 * AndroidComposeView 会消费触摸事件，导致 ViewGroup 的 OnTouchListener 永远不被调用）。
 * onInterceptTouchEvent 返回 true 后，触摸事件交给 FrameLayout 自身的 onTouchEvent →
 * OnTouchListener 处理拖动和长按穿透逻辑。
 */
private class TouchInterceptFrameLayout(context: android.content.Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true
}

class FloatingWindowService : Service() {

    companion object {
        /** 通知栏动作：关闭触摸穿透 */
        const val ACTION_DISABLE_TOUCH_THROUGH = "com.github.heartratemonitor_compose.DISABLE_TOUCH_THROUGH"
        private const val TOUCH_THROUGH_CHANNEL_ID = "floating_touch_through"
        private const val TOUCH_THROUGH_NOTIFICATION_ID = 1001
        /** 长按触发阈值（毫秒） */
        private const val LONG_PRESS_THRESHOLD = 500L
        /** 判定为拖动的移动阈值（像素） */
        private const val TOUCH_SLOP = 10f
    }

    private val binder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService(): FloatingWindowService = this@FloatingWindowService }
    override fun onBind(intent: Intent?): IBinder = binder

    /**
     * 处理两类 startService 调用：
     * 1. 通知栏「关闭触摸穿透」按钮（ACTION_DISABLE_TOUCH_THROUGH）：一次性动作，处理完即
     *    stopSelf(startId) 释放本次 start 请求；若 Activity 仍绑定本服务则服务不会被销毁。
     * 2. showWindow() 中的无 action 保活 start：使服务在 Activity 解绑（如开启"退出应用隐藏
     *    后台"后按 HOME 触发 finishAffinity）后仍能存活，悬浮窗持续显示。hideWindow() 时
     *    stopSelf() 释放该保活 start。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISABLE_TOUCH_THROUGH -> {
                disableTouchThrough()
                stopSelf(startId)
            }
            // 无 action：showWindow 保活 start，不释放
        }
        return START_STICKY
    }

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var touchContainer: TouchInterceptFrameLayout
    private lateinit var viewTreeOwners: ServiceViewTreeOwners
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var sharedPreferences: SharedPreferences

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bleService: BleService? = null
    private var isServiceBound = false

    private var isWindowShown = false

    // Compose 状态：collectLatest / settings 变更更新这些字段，触发 FloatingWindowContent 重组
    private var heartRateText by mutableStateOf("--")
    private var speedText by mutableStateOf("0.0")
    /** 心率原始值，驱动心跳动画周期 */
    private var bpmForAnimation by mutableStateOf(0)
    private var isAnimationEnabled by mutableStateOf(true)
    private var isConnected by mutableStateOf(false)
    private var appearance by mutableStateOf(FloatingWindowAppearance())

    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f

    /** 触摸穿透是否已开启：开启后悬浮窗不再接收触摸事件，触摸直接传递给下方应用 */
    private var isTouchThroughEnabled = false
    private val touchThroughHandler = Handler(Looper.getMainLooper())
    private var touchThroughRunnable: Runnable? = null
    /** 触摸穿透开启时覆盖在悬浮窗中心的不可见触摸接收窗口，用于长按关闭穿透 */
    private var touchThroughCatcherView: View? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            isServiceBound = true
            isConnected = bleService?.isDeviceConnected() ?: false
            observeBleData()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isServiceBound = false
            isConnected = false
        }
    }

    private val settingsChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        if (isWindowShown) updateWindowAppearance()
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // ComposeView 需要主题上下文以解析 Material3 资源；保留 ContextThemeWrapper 兼容 Phase 7 主题迁移
        val contextWithTheme = ContextThemeWrapper(this, R.style.Theme_HeartRateMonitorMobile)
        viewTreeOwners = ServiceViewTreeOwners()
        composeView = ComposeView(contextWithTheme).apply {
            setContent {
                HeartRateMonitorMobileTheme {
                    FloatingWindowContent(
                        heartRate = heartRateText,
                        speed = speedText,
                        bpm = bpmForAnimation,
                        isAnimationEnabled = isAnimationEnabled,
                        isConnected = isConnected,
                        appearance = appearance
                    )
                }
            }
        }
        // 用 FrameLayout 包裹 ComposeView：FrameLayout 拦截所有触摸事件，
        // 防止 ComposeView 内部的 AndroidComposeView 消费触摸导致 OnTouchListener 不触发
        touchContainer = TouchInterceptFrameLayout(contextWithTheme).apply {
            addView(composeView)
        }
        // ViewTree owners 必须设置到 touchContainer（WindowManager 的顶层 View），
        // 而非 composeView。因为 ComposeView 在 touchContainer 内部，
        // WindowRecomposer 查找 ViewTreeLifecycleOwner 时从 parent（touchContainer）开始向上查找，
        // 若设置在 composeView 上会查找不到导致闪退。
        viewTreeOwners.attachToView(touchContainer)

        initLayoutParams()
        setupTouchListener()
        createTouchThroughNotificationChannel()
        sharedPreferences.registerOnSharedPreferenceChangeListener(settingsChangeListener)

        // Bind to BleService to get data
        Intent(this, BleService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun observeBleData() {
        serviceScope.launch {
            bleService?.heartRate?.collectLatest { rate ->
                heartRateText = if (rate > 0) "$rate" else "--"
                bpmForAnimation = rate
                // 心率变更时同步连接状态，确保断开后心跳动画立即停止
                isConnected = bleService?.isDeviceConnected() ?: false
            }
        }
        // 监听速度数据
        serviceScope.launch {
            bleService?.speed?.collectLatest { speed ->
                speedText = String.format("%.1f", speed)
            }
        }
    }

    fun showWindow() {
        if (isWindowShown) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return
        try {
            windowManager.addView(touchContainer, layoutParams)
            isWindowShown = true
            updateWindowAppearance()
            // 提升为 started 服务，使悬浮窗在 Activity 解绑（如开启"退出应用隐藏后台"后按 HOME 触发 finishAffinity）后仍能存活
            startService(Intent(this, FloatingWindowService::class.java))
        } catch (e: Exception) {
            // Handle exception
        }
    }

    fun hideWindow() {
        if (!isWindowShown) return
        // 重置触摸穿透状态并清理通知与 catcher（避免隐藏后残留）
        if (isTouchThroughEnabled) {
            isTouchThroughEnabled = false
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            cancelTouchThroughNotification()
        }
        removeTouchThroughCatcher()
        // 取消可能挂起的长按回调
        touchThroughRunnable?.let { touchThroughHandler.removeCallbacks(it) }
        touchThroughRunnable = null
        try {
            windowManager.removeView(touchContainer)
            isWindowShown = false
            // 释放 showWindow 时的 start 保活；若仍被绑定则服务继续存活
            stopSelf()
        } catch (e: Exception) {
            // Handle exception
        }
    }

    private fun initLayoutParams() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener() {
        touchContainer.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    // 启动长按检测：500ms 内未移动超过阈值则开启触摸穿透
                    touchThroughRunnable = Runnable {
                        if (!isTouchThroughEnabled) enableTouchThrough()
                    }
                    touchThroughHandler.postDelayed(touchThroughRunnable!!, LONG_PRESS_THRESHOLD)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    // 超过移动阈值则取消长按检测（判定为拖动）
                    if (dx.absoluteValue > TOUCH_SLOP || dy.absoluteValue > TOUCH_SLOP) {
                        touchThroughRunnable?.let { touchThroughHandler.removeCallbacks(it) }
                    }
                    // 触摸穿透开启后不处理拖动
                    if (!isTouchThroughEnabled) {
                        layoutParams.x = initialX + dx.toInt()
                        layoutParams.y = initialY + dy.toInt()
                        if (isWindowShown) windowManager.updateViewLayout(touchContainer, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchThroughRunnable?.let { touchThroughHandler.removeCallbacks(it) }
                    touchThroughRunnable = null
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 开启触摸穿透：为窗口添加 FLAG_NOT_TOUCHABLE，触摸事件直接传递给下方应用。
     * 同时在悬浮窗中心叠加一个不可见的触摸接收窗口（catcher），用于长按关闭穿透。
     * 通知栏按钮作为关闭的备选方式。
     */
    private fun enableTouchThrough() {
        if (isTouchThroughEnabled || !isWindowShown) return
        isTouchThroughEnabled = true
        layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(touchContainer, layoutParams)
        } catch (e: Exception) {
            // 更新失败则回退状态
            isTouchThroughEnabled = false
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            return
        }
        addTouchThroughCatcher()
        showTouchThroughNotification()
        Toast.makeText(this, "触摸穿透已开启，长按悬浮窗或点击通知关闭", Toast.LENGTH_LONG).show()
    }

    /**
     * 关闭触摸穿透：移除 catcher 窗口和 FLAG_NOT_TOUCHABLE，恢复拖动。
     * 可由 catcher 的长按或通知栏按钮触发。
     */
    private fun disableTouchThrough() {
        val wasEnabled = isTouchThroughEnabled
        isTouchThroughEnabled = false
        removeTouchThroughCatcher()
        if (wasEnabled && isWindowShown) {
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try {
                windowManager.updateViewLayout(touchContainer, layoutParams)
            } catch (e: Exception) {
                // 忽略
            }
        }
        cancelTouchThroughNotification()
        if (wasEnabled) {
            Toast.makeText(this, "触摸穿透已关闭，可拖动悬浮窗", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 在悬浮窗中心叠加一个不可见的触摸接收窗口。
     * 主窗口设置了 FLAG_NOT_TOUCHABLE 后无法接收触摸，
     * catcher 负责接收长按手势以关闭触摸穿透。
     * catcher 仅覆盖中心 48dp×48dp 区域，其余区域触摸直接穿透。
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun addTouchThroughCatcher() {
        if (touchThroughCatcherView != null) return
        val catcherSize = dpToPx(48f)
        val catcher = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        touchThroughRunnable = Runnable { disableTouchThrough() }
                        touchThroughHandler.postDelayed(touchThroughRunnable!!, LONG_PRESS_THRESHOLD)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if ((event.rawX - initialTouchX).absoluteValue > TOUCH_SLOP ||
                            (event.rawY - initialTouchY).absoluteValue > TOUCH_SLOP) {
                            touchThroughRunnable?.let { touchThroughHandler.removeCallbacks(it) }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        touchThroughRunnable?.let { touchThroughHandler.removeCallbacks(it) }
                        touchThroughRunnable = null
                        true
                    }
                    else -> false
                }
            }
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        // 以悬浮窗实际位置和尺寸计算 catcher 居中坐标（touchContainer 未测量完成时兜底为 catcherSize）
        val windowWidth = touchContainer.width.coerceAtLeast(catcherSize)
        val windowHeight = touchContainer.height.coerceAtLeast(catcherSize)
        val catcherX = layoutParams.x + (windowWidth - catcherSize) / 2
        val catcherY = layoutParams.y + (windowHeight - catcherSize) / 2

        val catcherParams = WindowManager.LayoutParams(
            catcherSize, catcherSize,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = catcherX
            y = catcherY
        }

        touchThroughCatcherView = catcher
        try {
            windowManager.addView(catcher, catcherParams)
        } catch (e: Exception) {
            // 添加失败不影响穿透本身，仍可通过通知关闭
            touchThroughCatcherView = null
        }
    }

    private fun removeTouchThroughCatcher() {
        touchThroughCatcherView?.let { catcher ->
            try {
                windowManager.removeView(catcher)
            } catch (e: Exception) {
                // 忽略
            }
        }
        touchThroughCatcherView = null
    }

    private fun createTouchThroughNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TOUCH_THROUGH_CHANNEL_ID,
                "悬浮窗触摸穿透",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮窗触摸穿透状态提醒"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showTouchThroughNotification() {
        val disableIntent = Intent(this, FloatingWindowService::class.java).apply {
            action = ACTION_DISABLE_TOUCH_THROUGH
        }
        val disablePendingIntent = PendingIntent.getService(
            this, 0, disableIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TOUCH_THROUGH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("触摸穿透已开启")
            .setContentText("长按悬浮窗或点击下方按钮关闭")
            .addAction(R.drawable.ic_floating_window_on, "关闭触摸穿透", disablePendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(TOUCH_THROUGH_NOTIFICATION_ID, notification)
    }

    private fun cancelTouchThroughNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(TOUCH_THROUGH_NOTIFICATION_ID)
    }

    /**
     * 读取 11 个外观参数 + 心跳动画开关，计算后写入 [appearance] / [isAnimationEnabled] 触发重组。
     * 替代原 View 属性操作（setTextSize / setTextColor / setPadding / MaterialCardView 样式）。
     */
    private fun updateWindowAppearance() {
        val textColor = sharedPreferences.getInt("floating_text_color", Color.BLACK)
        val bgColor = sharedPreferences.getInt("floating_bg_color", Color.BLACK)
        val borderColor = sharedPreferences.getInt("floating_border_color", Color.GRAY)
        val bgAlpha = sharedPreferences.getInt("floating_bg_alpha", 10) / 100f
        val borderAlpha = sharedPreferences.getInt("floating_border_alpha", 100) / 100f
        val cornerRadius = sharedPreferences.getInt("floating_corner_radius", 100).toFloat()
        val sizePercent = sharedPreferences.getInt("floating_size", 100)
        val iconSizePercent = sharedPreferences.getInt("floating_icon_size", 100)
        val isBpmTextEnabled = sharedPreferences.getBoolean("bpm_text_enabled", true)
        val isHeartIconEnabled = sharedPreferences.getBoolean("heart_icon_enabled", true)
        val isSpeedEnabled = sharedPreferences.getBoolean("speed_display_enabled", false)
        isAnimationEnabled = sharedPreferences.getBoolean("heartbeat_animation_enabled", true)

        val finalBgColor = Color.argb((255 * bgAlpha).roundToInt(), Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        val finalBorderColor = Color.argb((255 * borderAlpha).roundToInt(), Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
        val scaleFactor = sizePercent / 100f
        val iconScaleFactor = iconSizePercent / 100f
        val baseIconSizeSp = 18f
        val baseTextSizeSp = 16f
        val baseSmallTextSizeSp = 12f
        val basePaddingDp = 8f
        val baseMarginDp = 4f

        appearance = FloatingWindowAppearance(
            textColor = ComposeColor(textColor),
            bgColor = ComposeColor(finalBgColor),
            borderColor = ComposeColor(finalBorderColor),
            cornerRadius = cornerRadius.dp,
            textSize = (baseTextSizeSp * scaleFactor).sp,
            smallTextSize = (baseSmallTextSizeSp * scaleFactor).sp,
            iconSize = (baseIconSizeSp * iconScaleFactor).sp,
            padding = (basePaddingDp * scaleFactor).dp,
            bpmNumberMarginStart = (if (isHeartIconEnabled) baseMarginDp * scaleFactor else 0f).dp,
            isBpmTextEnabled = isBpmTextEnabled,
            isHeartIconEnabled = isHeartIconEnabled,
            isSpeedEnabled = isSpeedEnabled
        )
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        hideWindow()
        touchThroughRunnable?.let { touchThroughHandler.removeCallbacks(it) }
        cancelTouchThroughNotification()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        serviceScope.cancel()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(settingsChangeListener)
        // 释放组合：派发 ON_DESTROY 让 ComposeView 清理 composition
        viewTreeOwners.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
