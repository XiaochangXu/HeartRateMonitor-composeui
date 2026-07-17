package com.github.heartratemonitor_compose.ui.main

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.data.db.AppDatabase
import com.github.heartratemonitor_compose.service.BleService
import com.github.heartratemonitor_compose.service.FloatingWindowService
import com.github.heartratemonitor_compose.service.HeartRateAlarmService
import com.github.heartratemonitor_compose.service.StatusBarResidentService
import com.github.heartratemonitor_compose.ui.AppRoot
import com.github.heartratemonitor_compose.ui.theme.HeartRateMonitorMobileTheme
import com.permissionx.guolindev.PermissionX
import kotlinx.coroutines.launch

/**
 * 单 Activity 宿主。
 *
 * 职责：
 * - 持有 NavHost 根 Composable（[AppRoot]）
 * - 绑定/启动 [BleService]、[FloatingWindowService]
 * - 处理权限请求、悬浮窗开关、MediaProjection 授权（launcher 吸收自 SettingsActivity）
 * - 应用启动时恢复 [StatusBarResidentService] / [HeartRateAlarmService]（修复原仅 SettingsActivity 触发的时序 bug）
 * - 「退出应用隐藏后台」逻辑（吸收自原 BaseActivity）
 * - 莫奈取色切换重建
 *
 * 原 XML 布局 logic（chart、device list、heartbeat anim）已下沉到 [HomeScreen]。
 */
class MainActivity : FragmentActivity() {

    companion object {
        /**
         * 启动外部 Activity（系统设置、浏览器等）前设为 true，阻止 onStop 中的 hide 误触发。
         * 在下次 onStart 自动复位。吸收自原 BaseActivity，供 SettingsScreen 等引用。
         */
        @JvmStatic
        var suppressHideForExternalLaunch = false
    }

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: AppDatabase

    /** 跟踪当前 Activity 是否处于 started 状态，用于「退出应用隐藏后台」 */
    private var isStarted = false

    /**
     * 记录 MainActivity 创建时莫奈取色的开关状态。
     * Compose 主题的 dynamicColor 参数仅在 Activity 创建时读取；若用户在设置页切换，
     * 返回首页时需重建 MainActivity 才能应用/移除动态取色。
     */
    private var monetEnabledAtCreate = true

    // ─────────── Service Bindings ───────────
    private var bleService: BleService? = null
    private var isBleServiceBound = false

    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            isBleServiceBound = true
            // 主页 ViewModel 通过 viewModel() 自行拿 BleService 引用，此处仅做 service 通知
            // 直接通知 ViewModel：通过 androidx.lifecycle ViewModelProvider 拿到
            val viewModel = androidx.lifecycle.ViewModelProvider(this@MainActivity)[MainViewModel::class.java]
            viewModel.setBleService(bleService!!)
            observeBleState()
            checkAndStartAutoConnectScan()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isBleServiceBound = false
        }
    }

    private var floatingService: FloatingWindowService? = null
    private var isFloatingServiceBound = false

    private val floatingServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FloatingWindowService.LocalBinder
            floatingService = binder.getService()
            isFloatingServiceBound = true
            updateFloatingWindowUi(sharedPreferences.getBoolean("floating_window_enabled", false))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            floatingService = null
            isFloatingServiceBound = false
        }
    }

    /**
     * MediaProjection 权限请求 launcher（吸收自 SettingsActivity）。
     * 仅 ComponentActivity 可注册 registerForActivityResult。
     *
     * 授权成功：写入 status_bar_auto_color=true 并向 StatusBarResidentService
     * 派发 ACTION_START_MEDIA_PROJECTION + resultCode/data。
     * 授权失败/取消：写入 status_bar_auto_color=false 并派发 ACTION_STOP_MEDIA_PROJECTION。
     */
    private val mediaProjectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            sharedPreferences.edit().putBoolean("status_bar_auto_color", true).apply()
            val intent = Intent(this, StatusBarResidentService::class.java).apply {
                action = StatusBarResidentService.ACTION_START_MEDIA_PROJECTION
                putExtra(StatusBarResidentService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(StatusBarResidentService.EXTRA_RESULT_DATA, result.data)
            }
            startService(intent)
        } else {
            sharedPreferences.edit().putBoolean("status_bar_auto_color", false).apply()
            val intent = Intent(this, StatusBarResidentService::class.java).apply {
                action = StatusBarResidentService.ACTION_STOP_MEDIA_PROJECTION
            }
            startService(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 显式传入透明 scrim：默认 SystemBarStyle.auto 使用 90%白/50%黑遮罩，
        // 三大金刚键模式下系统会在导航按钮区域叠该遮罩，覆盖 NavigationBar 背景色导致无法沉浸。
        // 透明 scrim 让系统不叠遮罩，由 NavigationBar 的 surfaceContainer 背景提供对比度；
        // 系统仍会按内容明暗自动切换按钮图标颜色（白/黑）保证可见性。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        // 三大金刚键模式下系统默认 isNavigationBarContrastEnforced=true，
        // 即使 navigationBarColor=TRANSPARENT，系统仍会强制绘制白色/黑色背景保证按钮可见性，
        // 该背景覆盖在 NavigationBar 之上导致无法沉浸。显式关闭对比度强制，
        // 让 NavigationBar 的 surfaceContainer 背景直接透出，系统仅在上方绘制按钮图标。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        sharedPreferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        db = AppDatabase.getDatabase(this)

        monetEnabledAtCreate = sharedPreferences.getBoolean("monet_color_enabled", true)

        cleanupOpenSessions()
        startAndBindServices()
        recoverServices()
        requestPermissions()

        setContent {
            HeartRateMonitorMobileTheme(
                dynamicColor = sharedPreferences.getBoolean("monet_color_enabled", true)
            ) {
                AppRoot(
                    onToggleFloatingWindow = { toggleFloatingWindow() },
                    onMediaProjectionRequest = { screenCaptureIntent ->
                        mediaProjectionLauncher.launch(screenCaptureIntent)
                    },
                    onOpenExternal = { intent ->
                        suppressHideForExternalLaunch = true
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isStarted = true
        suppressHideForExternalLaunch = false
        setExcludeFromRecentsFlag(false)
    }

    override fun onStop() {
        super.onStop()
        isStarted = false
        if (!suppressHideForExternalLaunch && isHideFromRecentsEnabled()) {
            setExcludeFromRecentsFlag(true)
        }
    }

    override fun onResume() {
        super.onResume()
        // 莫奈取色状态若在离开首页期间被切换，需重启 MainActivity 以应用/移除动态取色。
        // 使用 startActivity + finish 而非 recreate()：后者在新实例上 setDecorFitsSystemWindows(false)
        // 可能未及时生效，导致底部导航栏 edge-to-edge 失效、系统手势条无法沉浸。
        val monetEnabledNow = sharedPreferences.getBoolean("monet_color_enabled", true)
        if (monetEnabledNow != monetEnabledAtCreate) {
            monetEnabledAtCreate = monetEnabledNow
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(0, 0)
            finish()
            return
        }
        updateFloatingWindowUi(sharedPreferences.getBoolean("floating_window_enabled", false))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBleServiceBound) {
            unbindService(bleServiceConnection)
            isBleServiceBound = false
        }
        if (isFloatingServiceBound) {
            unbindService(floatingServiceConnection)
        }
    }

    // ─────────── Services ───────────

    private fun cleanupOpenSessions() {
        lifecycleScope.launch {
            val openSessions = db.heartRateDao().getOpenSessions()
            for (session in openSessions) {
                val lastTimestamp = db.heartRateDao().getLastRecordTimestampForSession(session.id)
                db.heartRateDao().endSession(session.id, lastTimestamp ?: session.startTime)
            }
        }
    }

    private fun startAndBindServices() {
        Intent(this, BleService::class.java).also { intent ->
            startService(intent)
            bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
        }
        Intent(this, FloatingWindowService::class.java).also { intent ->
            bindService(intent, floatingServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /**
     * 恢复用户上次启用但被系统回收的服务。吸收自 SettingsActivity.recoverServices()。
     *
     * 修复点：原 SettingsActivity 仅在用户打开设置页时才恢复服务，导致用户重启应用后
     * StatusBarResidentService / HeartRateAlarmService 不会自动恢复。改为在 MainActivity
     * onCreate 中调用，确保应用启动即恢复。
     */
    private fun recoverServices() {
        // 恢复状态栏常驻服务
        val residentEnabled = sharedPreferences.getBoolean("status_bar_resident_enabled", false)
        if (residentEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && Settings.canDrawOverlays(this)
        ) {
            startService(Intent(this, StatusBarResidentService::class.java))
        }

        // 恢复心率预警服务
        val alarmEnabled = sharedPreferences.getBoolean("heart_rate_alarm_enabled", false)
        if (alarmEnabled) {
            startService(Intent(this, HeartRateAlarmService::class.java))
        }
    }

    private fun checkAndStartAutoConnectScan() {
        val isAutoConnectEnabled = sharedPreferences.getBoolean("auto_connect_enabled", false)
        val favoriteDeviceId = sharedPreferences.getString("favorite_device_id", null)
        if (isAutoConnectEnabled && favoriteDeviceId != null) {
            val viewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
            viewModel.startAutoConnectScan(favoriteDeviceId)
        }
    }

    private fun observeBleState() {
        val viewModel = androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
        var previousBleState: BleState? = null
        lifecycleScope.launch {
            bleService?.bleState?.collect { state ->
                when (state) {
                    is BleState.Connected -> showToast(getString(R.string.toast_connected))
                    is BleState.AutoReconnecting -> showToast(getString(R.string.toast_auto_reconnecting))
                    is BleState.ScanFailed -> {
                        if (previousBleState is BleState.AutoReconnecting) {
                            showToast(getString(R.string.toast_reconnect_failed))
                        }
                    }
                    else -> { /* 在其他状态下不显示 Toast */ }
                }
                previousBleState = state
            }
        }
    }

    // ─────────── Floating Window ───────────

    private fun toggleFloatingWindow() {
        val shouldBeEnabled = !sharedPreferences.getBoolean("floating_window_enabled", false)
        if (shouldBeEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            suppressHideForExternalLaunch = true
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        sharedPreferences.edit().putBoolean("floating_window_enabled", shouldBeEnabled).apply()
        updateFloatingWindowUi(shouldBeEnabled)
    }

    private fun updateFloatingWindowUi(isEnabled: Boolean) {
        if (!isFloatingServiceBound) return
        if (isEnabled) floatingService?.showWindow() else floatingService?.hideWindow()
    }

    // ─────────── Permissions ───────────

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        PermissionX.init(this)
            .permissions(permissionsToRequest)
            .onExplainRequestReason { scope, deniedList ->
                scope.showRequestReasonDialog(
                    deniedList,
                    "App requires these permissions to find devices and calculate speed.",
                    "OK", "Cancel"
                )
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    "You need to grant these permissions manually in settings.",
                    "OK", "Cancel"
                )
            }
            .request { allGranted, _, _ ->
                if (!allGranted) {
                    showToast("Some permissions were denied. Features may be limited!")
                } else {
                    val intent = Intent(this, BleService::class.java)
                    startService(intent)
                }
            }
    }

    // ─────────── Hide From Recents ───────────

    private fun isHideFromRecentsEnabled(): Boolean {
        return sharedPreferences.getBoolean("hide_from_recents_enabled", false)
    }

    private fun setExcludeFromRecentsFlag(exclude: Boolean) {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val myTaskId = taskId
            for (task in am.appTasks) {
                if (task.taskInfo?.id == myTaskId) {
                    task.setExcludeFromRecents(exclude)
                    break
                }
            }
        } catch (_: Exception) { }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
