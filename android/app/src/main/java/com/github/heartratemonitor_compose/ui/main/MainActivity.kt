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
        
        @JvmStatic
        var suppressHideForExternalLaunch = false
    }

    @Inject lateinit var overlayPermissionProvider: OverlayPermissionProvider
    @Inject lateinit var serviceLauncher: ServiceLauncher
    @Inject lateinit var changelogNotifier: ChangelogNotifier
    @Inject lateinit var themeState: ThemeState
    @Inject lateinit var customSchemeCache: CustomSchemeCache
    @Inject lateinit var liquidGlassState: LiquidGlassState
    @Inject lateinit var killStateSaver: KillStateSaver

    // 业务决策与设置写入归 MainViewModel（D2 收敛），Activity 仅保留
    // Service 绑定机制、权限跳转与 suppressHideForExternalLaunch
    private val mainViewModel: MainViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[MainViewModel::class.java]
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private var isStarted = false

    private var bleService: BleService? = null
    private var isBleServiceBound = false

    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BleService.LocalBinder
            bleService = binder.getService()
            isBleServiceBound = true
            // 绑定机制保留（契约 3 例外）；BLE 状态订阅/自动连接判定已归 MainViewModel
            mainViewModel.setConnectionManager(bleService!!)
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

        // BLE 状态 → Toast 一次性回调：Activity 存活期注册，onDestroy 注销避免泄漏
        mainViewModel.bleToastListener = { event ->
            showToast(
                getString(
                    when (event) {
                        BleToastEvent.CONNECTED -> R.string.toast_connected
                        BleToastEvent.AUTO_RECONNECTING -> R.string.toast_auto_reconnecting
                        BleToastEvent.RECONNECT_FAILED -> R.string.toast_reconnect_failed
                        BleToastEvent.AUTO_CONNECT_FAILED -> R.string.toast_auto_connect_failed
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
        if (!suppressHideForExternalLaunch && mainViewModel.uiState.value.hideFromRecentsEnabled) {
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

    /** 服务恢复经 MainViewModel（业务决策归 VM）；僵尸会话清理已移至 BleService.onCreate。 */
    private fun cleanupAndRecover() {
        mainViewModel.recoverServices()
    }

    private fun startAndBindServices() {
        Intent(this, BleService::class.java).also { intent ->
            serviceLauncher.startBleService()
            // bindService 前置位标志：避免 bind→onServiceConnected 窗口内 Activity 被销毁时
            // onDestroy 认为"未绑定"而跳过 unbindService，导致连接泄漏、服务滞留
            isBleServiceBound = true
            bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
        }
        Intent(this, FloatingWindowService::class.java).also { intent ->
            isFloatingServiceBound = true
            bindService(intent, floatingServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun toggleFloatingWindow() {
        // 业务判定与设置写入归 VM；权限跳转依赖 Activity 上下文，留在此处
        if (mainViewModel.toggleFloatingWindow()) {
            suppressHideForExternalLaunch = true
            startActivity(overlayPermissionProvider.createManageOverlayIntent())
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
