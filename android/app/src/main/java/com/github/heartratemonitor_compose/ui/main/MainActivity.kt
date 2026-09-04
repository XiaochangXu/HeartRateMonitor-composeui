package com.github.heartratemonitor_compose.ui.main

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.system.OverlayPermissionProvider
import com.github.heartratemonitor_compose.service.BleService
import com.github.heartratemonitor_compose.service.FloatingWindowService
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.service.ServiceLauncher
import com.github.heartratemonitor_compose.ui.AppRoot
import com.github.heartratemonitor_compose.ui.ChangelogNotifier
import com.github.heartratemonitor_compose.ui.theme.AppTheme
import com.github.heartratemonitor_compose.ui.theme.CustomSchemeCache
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState
import com.github.heartratemonitor_compose.ui.theme.ThemeState
import com.permissionx.guolindev.PermissionX
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val SUPPRESS_TIMEOUT_MS = 5000L

        @JvmStatic
        private var suppressHideForExternalLaunch = false
        private val suppressResetHandler = Handler(Looper.getMainLooper())
        private val suppressResetRunnable = Runnable {
            if (suppressHideForExternalLaunch) {
                Log.w(TAG, "suppressHideForExternalLaunch 超时自动复位（用户可能未从外链返回）")
                suppressHideForExternalLaunch = false
            }
        }

        // 置位外部启动抑制标志并启动超时自动复位（防止抑制窗口无限泄漏）。
        @JvmStatic
        fun setSuppressHideForExternalLaunch(value: Boolean) {
            suppressResetHandler.removeCallbacks(suppressResetRunnable)
            suppressHideForExternalLaunch = value
            if (value) {
                suppressResetHandler.postDelayed(suppressResetRunnable, SUPPRESS_TIMEOUT_MS)
            }
        }

        @JvmStatic
        fun isSuppressHideForExternalLaunch(): Boolean = suppressHideForExternalLaunch
    }

    @Inject lateinit var overlayPermissionProvider: OverlayPermissionProvider
    @Inject lateinit var serviceLauncher: ServiceLauncher
    @Inject lateinit var changelogNotifier: ChangelogNotifier
    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var customSchemeCache: CustomSchemeCache
    @Inject lateinit var liquidGlassState: LiquidGlassState
    @Inject lateinit var killStateSaver: KillStateSaver

    // Activity 仅保留 Service 绑定机制、权限跳转与 suppressHideForExternalLaunch。
    private val mainViewModel: MainViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private var bleService: BleService? = null
    private var isBleServiceBound = false

    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            isBleServiceBound = true
            // Binder 仅注入控制命令通道；数据面由 VM 构造期从 HeartRateRepository 订阅。
            mainViewModel.setControlPlane(bleService!!)
            mainViewModel.checkAndStartAutoConnectScan()
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
            updateFloatingWindowUi(mainViewModel.uiState.value.floatingWindowEnabled)
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

        cleanupAndRecover()
        startAndBindServices()
        requestPermissions()

        mainViewModel.bleToastListener = { event ->
            showToast(
                getString(
                    when (event) {
                        BleToastEvent.CONNECTED -> R.string.toast_connected
                        BleToastEvent.AUTO_RECONNECTING -> R.string.toast_auto_reconnecting
                        BleToastEvent.RECONNECT_FAILED -> R.string.toast_reconnect_failed
                        BleToastEvent.AUTO_CONNECT_FAILED -> R.string.toast_auto_connect_failed
                        BleToastEvent.BLUETOOTH_DISABLED -> R.string.toast_bluetooth_disabled
                    }
                )
            )
        }

        setContent {
            AppTheme(themeState = themeState, customSchemeCache = customSchemeCache) {
                AppRoot(
                    changelogNotifier = changelogNotifier,
                    liquidGlassState = liquidGlassState,
                    killStateSaver = killStateSaver,
                    onToggleFloatingWindow = { toggleFloatingWindow() },
                    onOpenExternal = { intent ->
                        try {
                            startActivity(intent)
                            setSuppressHideForExternalLaunch(true)
                        } catch (e: android.content.ActivityNotFoundException) {
                            Log.e(TAG, "外部链接跳转失败：无 Activity 可处理该 Intent", e)
                            showToast(getString(R.string.toast_permissions_denied))
                        } catch (e: Exception) {
                            Log.e(TAG, "外部链接跳转失败", e)
                        }
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setSuppressHideForExternalLaunch(false)
        setExcludeFromRecentsFlag(false)
    }

    override fun onStop() {
        super.onStop()
        if (!isSuppressHideForExternalLaunch() && mainViewModel.uiState.value.hideFromRecentsEnabled) {
            setExcludeFromRecentsFlag(true)
        }
    }

    override fun onResume() {
        super.onResume()
        updateFloatingWindowUi(mainViewModel.uiState.value.floatingWindowEnabled)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainViewModel.bleToastListener = null
        if (isBleServiceBound) {
            unbindService(bleServiceConnection)
            isBleServiceBound = false
        }
        if (isFloatingServiceBound) {
            unbindService(floatingServiceConnection)
        }
    }

    private fun cleanupAndRecover() {
        mainViewModel.recoverServices()
    }

    private fun startAndBindServices() {
        Intent(this, BleService::class.java).also { intent ->
            serviceLauncher.startBleService()
            // ⚠️ 反直觉设计：bindService 前置位标志，避免 bind→onServiceConnected 窗口内 Activity 被销毁时 onDestroy 认为"未绑定"跳过 unbindService 导致连接泄漏。
            isBleServiceBound = true
            bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
        }
        Intent(this, FloatingWindowService::class.java).also { intent ->
            isFloatingServiceBound = true
            bindService(intent, floatingServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun toggleFloatingWindow() {
        if (mainViewModel.toggleFloatingWindow()) {
            try {
                startActivity(overlayPermissionProvider.createManageOverlayIntent())
                setSuppressHideForExternalLaunch(true)
            } catch (e: Exception) {
                Log.e(TAG, "悬浮窗权限页跳转失败", e)
            }
            return
        }
        updateFloatingWindowUi(mainViewModel.uiState.value.floatingWindowEnabled)
    }

    private fun updateFloatingWindowUi(isEnabled: Boolean) {
        if (!isFloatingServiceBound) return
        if (isEnabled) floatingService?.showWindow() else floatingService?.hideWindow()
    }

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
                    getString(com.github.heartratemonitor_compose.ui.widgets.R.string.confirm), getString(com.github.heartratemonitor_compose.ui.widgets.R.string.cancel)
                )
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(
                    deniedList,
                    getString(R.string.toast_permissions_denied_dialog),
                    getString(R.string.permission_forward_title), getString(com.github.heartratemonitor_compose.ui.widgets.R.string.cancel)
                )
            }
            .request { allGranted, _, _ ->
                if (!allGranted) {
                    showToast(getString(R.string.toast_permissions_denied))
                } else {
                    serviceLauncher.startBleService()
                }
            }
    }

    private fun setExcludeFromRecentsFlag(exclude: Boolean) {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            var matched = false
            for (task in am.appTasks) {
                if (task.taskInfo?.baseIntent?.component?.packageName == packageName) {
                    task.setExcludeFromRecents(exclude)
                    matched = true
                    break
                }
            }
            if (!matched) {
                Log.w(TAG, "setExcludeFromRecents($exclude): 未找到包名匹配的任务")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "setExcludeFromRecents($exclude) 失败", e)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
