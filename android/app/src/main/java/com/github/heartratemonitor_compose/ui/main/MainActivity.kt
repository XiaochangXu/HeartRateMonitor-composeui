package com.github.heartratemonitor_compose.ui.main

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.github.heartratemonitor_compose.HeartRateApp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.ble.BleState
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.system.OverlayPermissionProvider
import com.github.heartratemonitor_compose.service.BleService
import com.github.heartratemonitor_compose.service.FloatingWindowService
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.service.ServiceController
import com.github.heartratemonitor_compose.ui.AppRoot
import com.github.heartratemonitor_compose.ui.theme.HeartRateMonitorMobileTheme
import com.permissionx.guolindev.PermissionX
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    companion object {
        
        @JvmStatic
        var suppressHideForExternalLaunch = false
    }

    private val settings: SettingsRepository by lazy { (application as HeartRateApp).settingsRepository }
    private val overlayPermissionProvider: OverlayPermissionProvider by lazy { application.appContainer.overlayPermissionProvider }

    private var isStarted = false

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
            updateFloatingWindowUi(settings.getBoolean(PrefsKeys.FLOATING_WINDOW_ENABLED, false))
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            floatingService = null
            isFloatingServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        cleanupOpenSessions()
        startAndBindServices()
        recoverServices()
        requestPermissions()

        setContent {
            HeartRateMonitorMobileTheme {
                AppRoot(
                    onToggleFloatingWindow = { toggleFloatingWindow() },
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
        updateFloatingWindowUi(settings.getBoolean(PrefsKeys.FLOATING_WINDOW_ENABLED, false))
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
            application.appContainer.sessionRepository.closeOpenSessions()
        }
    }

    private fun startAndBindServices() {
        Intent(this, BleService::class.java).also { intent ->
            ServiceController.startBleService(this)
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
        val residentEnabled = settings.getBoolean(PrefsKeys.STATUS_BAR_RESIDENT_ENABLED, false)
        if (residentEnabled && overlayPermissionProvider.canDrawOverlays()) {
            ServiceController.startStatusBarResidentService(this)
        }

        // 恢复心率预警服务
        val alarmEnabled = settings.getBoolean(PrefsKeys.HEART_RATE_ALARM_ENABLED, false)
        if (alarmEnabled) {
            ServiceController.startHeartRateAlarmService(this)
        }
    }

    private fun checkAndStartAutoConnectScan() {
        val isAutoConnectEnabled = settings.getBoolean(PrefsKeys.AUTO_CONNECT_ENABLED, false)
        val favoriteDeviceId = settings.getStringNullable(PrefsKeys.FAVORITE_DEVICE_ID)
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
        val shouldBeEnabled = !settings.getBoolean(PrefsKeys.FLOATING_WINDOW_ENABLED, false)
        if (shouldBeEnabled && !overlayPermissionProvider.canDrawOverlays()) {
            suppressHideForExternalLaunch = true
            startActivity(overlayPermissionProvider.createManageOverlayIntent())
            return
        }
        settings.setBoolean(PrefsKeys.FLOATING_WINDOW_ENABLED, shouldBeEnabled)
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
                    getString(R.string.permission_request_reason),
                    getString(R.string.confirm), getString(R.string.cancel)
                )
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    getString(R.string.toast_permissions_denied_dialog),
                    getString(R.string.permission_forward_title), getString(R.string.cancel)
                )
            }
            .request { allGranted, _, _ ->
                if (!allGranted) {
                    showToast(getString(R.string.toast_permissions_denied))
                } else {
                    ServiceController.startBleService(this)
                }
            }
    }

    // ─────────── Hide From Recents ───────────

    private fun isHideFromRecentsEnabled(): Boolean {
        return settings.getBoolean(PrefsKeys.HIDE_FROM_RECENTS_ENABLED, false)
    }

    private fun setExcludeFromRecentsFlag(exclude: Boolean) {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            // 用 baseIntent 包名匹配代替 taskInfo.taskId，兼容 Android 7+ 全版本
            // (TaskInfo.taskId 字段从 API 29 起才存在，低版本访问会抛 NoSuchFieldError)
            for (task in am.appTasks) {
                if (task.taskInfo?.baseIntent?.component?.packageName == packageName) {
                    task.setExcludeFromRecents(exclude)
                    break
                }
            }
        } catch (_: Throwable) { }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
