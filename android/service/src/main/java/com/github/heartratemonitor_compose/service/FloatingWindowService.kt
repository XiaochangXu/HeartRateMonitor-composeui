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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import com.github.heartratemonitor_compose.service.R
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.ui.theme.CustomSchemeCache
import com.github.heartratemonitor_compose.ui.theme.HeartRateMonitorMobileTheme
import com.github.heartratemonitor_compose.ui.theme.ThemeState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import javax.inject.Inject
import java.util.Locale

/**
 * FrameLayout 子类：拦截所有触摸事件，使其不会分发给子 View（ComposeView 内部的
 * AndroidComposeView 会消费触摸事件，导致 ViewGroup 的 OnTouchListener 永远不被调用）。
 * onInterceptTouchEvent 返回 true 后，触摸事件交给 FrameLayout 自身的 onTouchEvent →
 * OnTouchListener 处理拖动和长按穿透逻辑。
 */
private class TouchInterceptFrameLayout(context: android.content.Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true
}

@AndroidEntryPoint
class FloatingWindowService : Service() {

    companion object {
        const val ACTION_DISABLE_TOUCH_THROUGH = "com.github.heartratemonitor_compose.DISABLE_TOUCH_THROUGH"
        private const val TOUCH_THROUGH_CHANNEL_ID = "floating_touch_through"
        private const val TOUCH_THROUGH_NOTIFICATION_ID = 1001
        private const val LONG_PRESS_THRESHOLD = 500L
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
     * 3. START_STICKY 系统重建（无 action）：进程被回收后服务被系统重建，此时没有 Activity
     *    绑定来调用 showWindow()，需按设置与权限自愈恢复悬浮窗，否则窗口永久丢失。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISABLE_TOUCH_THROUGH -> {
                disableTouchThrough()
                stopSelf(startId)
            }
            // 无 action：showWindow 保活 start 或 START_STICKY 重启自愈，不释放
            null -> {
                if (!isWindowShown && settingsRepository.get(SettingsKeys.FLOATING_WINDOW_ENABLED)) {
                    // showWindow 内部自检 overlay 权限；无权限时静默返回，权限授予后由下次启动恢复
                    showWindow()
                }
            }
        }
        return START_STICKY
    }

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var touchContainer: TouchInterceptFrameLayout
    private lateinit var viewTreeOwners: ServiceViewTreeOwners
    private lateinit var layoutParams: WindowManager.LayoutParams

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var customSchemeCache: CustomSchemeCache
    @Inject lateinit var reopenAppIntent: @JvmSuppressWildcards () -> Intent
    // 数据面 SSOT（审查修订）：悬浮窗心率/速度/连接标志改由进程级 Repository 直订，
    // 不再经 Binder 持有的 BleService 实例读取
    @Inject lateinit var heartRateRepository: HeartRateRepository

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isServiceBound = false
    /** 当前生效的数据订阅（Repository 直出），重新订阅前先取消，避免叠加重复收集 */
    private var bleDataJobs: List<Job> = emptyList()

    private var isWindowShown = false
    /** 拖拽中暂停心率/速度刷新，避免重组与 updateViewLayout 争抢主线程造成卡顿 */
    private var isDragging = false

    private var heartRateText by mutableStateOf("--")
    private var speedText by mutableStateOf("0.0")
    private var isAnimationEnabled by mutableStateOf(true)
    private var isConnected by mutableStateOf(false)
    private var appearance by mutableStateOf(FloatingWindowAppearance())
    private val heartbeatAnimator = HeartbeatAnimator()

    private var initialX = 0; private var initialY = 0
    private var initialTouchX = 0f; private var initialTouchY = 0f

    private var isTouchThroughEnabled = false
    private val touchThroughHandler = Handler(Looper.getMainLooper())
    private var touchThroughRunnable: Runnable? = null

    private var settingsJobs: List<Job> = emptyList()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            // 数据面已改由 HeartRateRepository 直订，无需持有 Service 实例；
            // 绑定保留为前台服务拉起锚点（BIND_AUTO_CREATE 副作用）
            isServiceBound = true
            isConnected = heartRateRepository.bleState.value is BleState.Connected
            observeBleData()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            // 取消旧订阅并复位显示，防止 onServiceConnected 重入时叠加重复收集
            // （对齐 StatusBarResidentService 的处理）
            bleDataJobs.forEach { it.cancel() }
            bleDataJobs = emptyList()
            isServiceBound = false
            isDragging = false
            isConnected = false
            heartRateText = "--"
            speedText = "0.0"
            heartbeatAnimator.stop()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // ComposeView 需要主题上下文以解析 Material3 资源；保留 ContextThemeWrapper 兼容 Phase 7 主题迁移
        val contextWithTheme = ContextThemeWrapper(this, R.style.Theme_HeartRateMonitorMobile)
        viewTreeOwners = ServiceViewTreeOwners()
        composeView = ComposeView(contextWithTheme).apply {
            setContent {
                val config by themeState.config.collectAsState()
                HeartRateMonitorMobileTheme(config = config, customSchemeCache = customSchemeCache) {
                    FloatingWindowContent(
                        heartRate = heartRateText,
                        speed = speedText,
                        heartScale = { heartbeatAnimator.scaleState.floatValue },
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
        observeSettingsChanges()

        Intent(this, BleService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * 替代原 SharedPreferences.OnSharedPreferenceChangeListener，
     * 收窄为仅监听实际影响外观的 key。各流 drop(1) 跳过初始发射，
     * 保持原 listener「仅响应注册后变化」的语义。
     */
    private fun observeSettingsChanges() {
        settingsJobs = listOf(
            serviceScope.launch {
                merge(
                    settingsRepository.observe(SettingsKeys.FLOATING_TEXT_COLOR).drop(1),
                    settingsRepository.observe(SettingsKeys.FLOATING_BG_COLOR).drop(1),
                    settingsRepository.observe(SettingsKeys.FLOATING_BORDER_COLOR).drop(1),
                    settingsRepository.observe(SettingsKeys.FLOATING_BG_ALPHA).drop(1),
                    settingsRepository.observe(SettingsKeys.FLOATING_BORDER_ALPHA).drop(1),
                    settingsRepository.observe(SettingsKeys.FLOATING_CORNER_RADIUS).drop(1),
                    settingsRepository.observe(SettingsKeys.FLOATING_SIZE).drop(1),
                    settingsRepository.observe(SettingsKeys.FLOATING_ICON_SIZE).drop(1),
                    settingsRepository.observe(SettingsKeys.BPM_TEXT_ENABLED).drop(1),
                    settingsRepository.observe(SettingsKeys.HEART_ICON_ENABLED).drop(1),
                    settingsRepository.observe(SettingsKeys.SPEED_DISPLAY_ENABLED).drop(1),
                    settingsRepository.observe(SettingsKeys.HEARTBEAT_ANIMATION_ENABLED).drop(1)
                ).collect { if (isWindowShown) updateWindowAppearance() }
            }
        )
    }

    private fun observeBleData() {
        // 取消旧订阅：BleService 重建后重新 onServiceConnected 会再次调用本方法，
        // 必须先取消旧协程，避免叠加重复收集。
        bleDataJobs.forEach { it.cancel() }
        bleDataJobs = listOf(
            serviceScope.launch {
                // 使用 collect 而非 collectLatest：lambda 体内无挂起操作，不需要取消上一个收集。
                // 拖拽期间跳过 UI 刷新；恢复刷新由 applyPendingBleData 直读 StateFlow.value，
                // 无需拖拽期间缓存（.value 始终是采集引擎写入的最新值）。
                heartRateRepository.heartRate.collect { rate ->
                    if (!isDragging) {
                        heartRateText = if (rate > 0) "$rate" else "--"
                        // 心率变更时同步连接状态，确保断开后心跳动画立即停止。
                        // 连接标志由 Repository 的 bleState 推导（与流同源，不依赖 Binder）
                        isConnected = heartRateRepository.bleState.value is BleState.Connected
                        heartbeatAnimator.update(rate, isAnimationEnabled, isConnected)
                    }
                }
            },
            serviceScope.launch {
                heartRateRepository.speed.collect { speed ->
                    if (!isDragging) {
                        speedText = String.format(Locale.US, "%.1f", speed)
                    }
                }
            }
        )
    }

    /**
     * 一次性应用拖拽期间的最新心率/速度，恢复正常刷新。
     * 调用时机：ACTION_UP / ACTION_CANCEL / enableTouchThrough()。
     *
     * 直接从 StateFlow 同步读取当前值（.value）：.value 始终是采集引擎写入的
     * 最新心率，不依赖收集协程的调度时序，拖拽期间的跳帧不会造成陈旧显示；
     * Repository 进程级存活，不存在旧实现“服务已解绑”的回退场景。
     */
    private fun applyPendingBleData() {
        val rate = heartRateRepository.heartRate.value
        if (rate > 0) {
            heartRateText = "$rate"
        } else {
            heartRateText = "--"
        }
        isConnected = heartRateRepository.bleState.value is BleState.Connected
        heartbeatAnimator.update(rate, isAnimationEnabled, isConnected)
        val currentSpeed = heartRateRepository.speed.value
        speedText = String.format(Locale.US, "%.1f", currentSpeed)
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
        }
    }

    fun hideWindow() {
        if (!isWindowShown) return
        isDragging = false
        if (isTouchThroughEnabled) {
            isTouchThroughEnabled = false
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            cancelTouchThroughNotification()
        }
        touchThroughRunnable?.let { touchThroughHandler.removeCallbacks(it) }
        touchThroughRunnable = null
        try {
            windowManager.removeView(touchContainer)
        } catch (e: Exception) {
            // 窗口可能已被系统移除（如进程被杀后重建），忽略
        }
        // 状态复位必须移出 try：removeView 异常时若 isWindowShown 保持 true，
        // 之后 showWindow() 会因 isWindowShown 直接 return，悬浮窗永久无法再显示
        isWindowShown = false
        // 释放 showWindow 时的 start 保活；若仍被绑定则服务继续存活
        stopSelf()
    }

    private fun initLayoutParams() {
        // TYPE_PHONE 在 API 26 已被 TYPE_APPLICATION_OVERLAY 取代，旧设备仍需 fallback
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settingsRepository.get(SettingsKeys.FLOATING_X)
            y = settingsRepository.get(SettingsKeys.FLOATING_Y)
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
                    isDragging = true
                    touchThroughRunnable = Runnable {
                        if (!isTouchThroughEnabled) enableTouchThrough()
                    }
                    touchThroughHandler.postDelayed(touchThroughRunnable!!, LONG_PRESS_THRESHOLD)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (dx.absoluteValue > TOUCH_SLOP || dy.absoluteValue > TOUCH_SLOP) {
                        touchThroughRunnable?.let { touchThroughHandler.removeCallbacks(it) }
                    }
                    if (!isTouchThroughEnabled) {
                        // 边界检查：限制窗口至少 25% 可见，避免被拖出屏幕无法拖回
                        val dm = resources.displayMetrics
                        val w = touchContainer.width.coerceAtLeast(1)
                        val h = touchContainer.height.coerceAtLeast(1)
                        val minVisibleW = w / 4
                        val minVisibleH = h / 4
                        val minX = -(w - minVisibleW)
                        val maxX = dm.widthPixels - minVisibleW
                        val minY = -(h - minVisibleH)
                        val maxY = dm.heightPixels - minVisibleH
                        layoutParams.x = (initialX + dx.toInt()).coerceIn(minX, maxX)
                        layoutParams.y = (initialY + dy.toInt()).coerceIn(minY, maxY)
                        if (isWindowShown) windowManager.updateViewLayout(touchContainer, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchThroughRunnable?.let { touchThroughHandler.removeCallbacks(it) }
                    touchThroughRunnable = null
                    isDragging = false
                    applyPendingBleData()
                    if (layoutParams.x != initialX || layoutParams.y != initialY) {
                        settingsRepository.set(SettingsKeys.FLOATING_X, layoutParams.x)
                        settingsRepository.set(SettingsKeys.FLOATING_Y, layoutParams.y)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun enableTouchThrough() {
        if (isTouchThroughEnabled || !isWindowShown) return
        isTouchThroughEnabled = true
        // 穿透后窗口不再收到 ACTION_UP，必须在此复位 isDragging 并直读 StateFlow
        // 当前值刷新显示，否则心率更新会永远卡在拖拽暂停状态
        isDragging = false
        applyPendingBleData()
        layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(touchContainer, layoutParams)
        } catch (e: Exception) {
            // 更新失败则回退状态
            isTouchThroughEnabled = false
            layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            return
        }
        showTouchThroughNotification()
        Toast.makeText(this, R.string.toast_touch_through_enabled, Toast.LENGTH_LONG).show()
    }

    private fun disableTouchThrough() {
        val wasEnabled = isTouchThroughEnabled
        isTouchThroughEnabled = false
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
            Toast.makeText(this, R.string.toast_touch_through_disabled, Toast.LENGTH_SHORT).show()
        }
    }

    private fun createTouchThroughNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TOUCH_THROUGH_CHANNEL_ID,
                getString(R.string.touch_through_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.touch_through_channel_desc)
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
            .setContentTitle(getString(R.string.touch_through_notification_title))
            .setContentText(getString(R.string.touch_through_notification_text))
            .addAction(R.drawable.ic_floating_window_on, getString(R.string.touch_through_disable_action), disablePendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, reopenAppIntent(),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
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
        val textColor = settingsRepository.get(SettingsKeys.FLOATING_TEXT_COLOR)
        val bgColor = settingsRepository.get(SettingsKeys.FLOATING_BG_COLOR)
        val borderColor = settingsRepository.get(SettingsKeys.FLOATING_BORDER_COLOR)
        val bgAlpha = settingsRepository.get(SettingsKeys.FLOATING_BG_ALPHA) / 100f
        val borderAlpha = settingsRepository.get(SettingsKeys.FLOATING_BORDER_ALPHA) / 100f
        val cornerRadius = settingsRepository.get(SettingsKeys.FLOATING_CORNER_RADIUS).toFloat()
        val sizePercent = settingsRepository.get(SettingsKeys.FLOATING_SIZE)
        val iconSizePercent = settingsRepository.get(SettingsKeys.FLOATING_ICON_SIZE)
        val isBpmTextEnabled = settingsRepository.get(SettingsKeys.BPM_TEXT_ENABLED)
        val isHeartIconEnabled = settingsRepository.get(SettingsKeys.HEART_ICON_ENABLED)
        val isSpeedEnabled = settingsRepository.get(SettingsKeys.SPEED_DISPLAY_ENABLED)
        isAnimationEnabled = settingsRepository.get(SettingsKeys.HEARTBEAT_ANIMATION_ENABLED)
        // 同步更新心跳动画驱动器
        val rate = heartRateRepository.heartRate.value
        heartbeatAnimator.update(rate, isAnimationEnabled, isConnected)

        val finalBgColor = Color.argb((255 * bgAlpha).roundToInt(), Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        val finalBorderColor = Color.argb((255 * borderAlpha).roundToInt(), Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
        val scaleFactor = sizePercent / 100f
        val iconScaleFactor = iconSizePercent / 100f
        val baseIconSizeSp = 22f
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
            iconSize = (baseIconSizeSp * scaleFactor * iconScaleFactor).sp,
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
        // 服务销毁时再保存一次最终位置，防止被系统回收时未触发 ACTION_UP/CANCEL
        if (::layoutParams.isInitialized) {
            settingsRepository.set(SettingsKeys.FLOATING_X, layoutParams.x)
            settingsRepository.set(SettingsKeys.FLOATING_Y, layoutParams.y)
        }
        touchThroughRunnable?.let { touchThroughHandler.removeCallbacks(it) }
        cancelTouchThroughNotification()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        serviceScope.cancel()
        settingsJobs.forEach { it.cancel() }
        settingsJobs = emptyList()
        heartbeatAnimator.stop()
        // 释放组合：派发 ON_DESTROY 让 ComposeView 清理 composition
        viewTreeOwners.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
