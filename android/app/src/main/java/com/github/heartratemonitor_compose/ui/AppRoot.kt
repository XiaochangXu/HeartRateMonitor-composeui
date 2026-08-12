package com.github.heartratemonitor_compose.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.alarm.HeartRateAlarmScreen
import com.github.heartratemonitor_compose.ui.favorite.FavoriteDevicesScreen
import com.github.heartratemonitor_compose.ui.history.ChartScreen
import com.github.heartratemonitor_compose.ui.history.HistoryScreen
import com.github.heartratemonitor_compose.ui.settings.AboutDetailsScreen
import com.github.heartratemonitor_compose.ui.settings.ChangelogBottomSheet
import com.github.heartratemonitor_compose.ui.settings.FloatingWindowSettingsScreen
import com.github.heartratemonitor_compose.ui.settings.FunctionSettingsScreen
import com.github.heartratemonitor_compose.ui.settings.LicenseScreen
import com.github.heartratemonitor_compose.ui.settings.PrivacyScreen
import com.github.heartratemonitor_compose.ui.settings.StatusBarSettingsScreen
import com.github.heartratemonitor_compose.ui.main.AppStatus
import com.github.heartratemonitor_compose.ui.main.DevicesScreen
import com.github.heartratemonitor_compose.ui.main.FullScreenHeartRate
import com.github.heartratemonitor_compose.ui.main.HomeScreen
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.server.LanTransferScreen
import com.github.heartratemonitor_compose.ui.server.ServerScreen
import com.github.heartratemonitor_compose.ui.settings.FairMemoryScreen
import com.github.heartratemonitor_compose.ui.settings.FullscreenSoundScreen
import com.github.heartratemonitor_compose.ui.settings.NavStyleScreen
import com.github.heartratemonitor_compose.ui.settings.SettingsScreen
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState
import com.github.heartratemonitor_compose.ui.theme.ThemeSettingsScreen
import com.github.heartratemonitor_compose.ui.webhook.WebhookScreen
import com.github.heartratemonitor_compose.ui.widgets.FloatingBottomBar
import com.github.heartratemonitor_compose.ui.widgets.GlassTabItem
import com.github.heartratemonitor_compose.data.di.settingsRepository
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.capsule.ContinuousCapsule
import kotlinx.coroutines.launch


private const val FLOATING_NAV_HEIGHT = 64
private const val FLOATING_NAV_BOTTOM_MARGIN = 12
private const val NAV_ITEM_DURATION = 200
private val NAV_ICON_SIZE = 24.dp

// 二级页面 NavHost 转场动画时长
private const val SECONDARY_SLIDE_DURATION = 350
// 进入二级页面时底层 Tab 层向左位移比例（视差效果）
private const val BACKGROUND_PARALLAX_RATIO = 0.2f

// NavHost 占位路由：Tab 页在 NavHost 外部管理，此路由仅作为 startDestination
private const val TAB_PLACEHOLDER = "tab_placeholder"

/**
 * 路由定义。使用 Navigation Compose 管理页面栈，替代原来的手动 secondaryStack。
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object History : Screen("history")
    object Chart : Screen("chart/{sessionId}") {
        fun createRoute(sessionId: Long) = "chart/$sessionId"
    }
    object Favorite : Screen("favorite")
    object Alarm : Screen("alarm")
    object Server : Screen("server")
    object Webhook : Screen("webhook")
    object LanTransfer : Screen("lan_transfer")
    object FairMemory : Screen("fair_memory")
    object Theme : Screen("theme")
    object NavStyle : Screen("nav_style")
    object Devices : Screen("devices")
    object FullscreenSound : Screen("fullscreen_sound")
    object License : Screen("license")
    object Privacy : Screen("privacy")
    object AboutDetails : Screen("about_details")
    object FunctionSettings : Screen("function_settings")
    object StatusBarSettings : Screen("status_bar_settings")
    object FloatingWindowSettings : Screen("floating_window_settings")
}

/** 底部导航 Tab 页：Home / History / Favorite / Settings 均为 Tab */
private fun Screen.isTab(): Boolean =
    this is Screen.Home || this is Screen.History || this is Screen.Favorite || this is Screen.Settings

/** SettingsScreen 用字符串路由映射到 Navigation Compose 路由 */
private fun String.toScreenRoute(): String = when (this) {
    "alarm" -> Screen.Alarm.route
    "server" -> Screen.Server.route
    "webhook" -> Screen.Webhook.route
    "lan_transfer" -> Screen.LanTransfer.route
    "fair_memory" -> Screen.FairMemory.route
    "theme" -> Screen.Theme.route
    "nav_style" -> Screen.NavStyle.route
    "devices" -> Screen.Devices.route
    "fullscreen_sound" -> Screen.FullscreenSound.route
    "license" -> Screen.License.route
    "privacy" -> Screen.Privacy.route
    "about_details" -> Screen.AboutDetails.route
    "function_settings" -> Screen.FunctionSettings.route
    "status_bar_settings" -> Screen.StatusBarSettings.route
    "floating_window_settings" -> Screen.FloatingWindowSettings.route
    else -> Screen.Home.route
}

/** Tab 索引 → Screen 映射（4 Tab：0=Home, 1=History, 2=Favorite, 3=Settings） */
private fun tabScreenAt(index: Int): Screen = when (index) {
    0 -> Screen.Home
    1 -> Screen.History
    2 -> Screen.Favorite
    3 -> Screen.Settings
    else -> Screen.Home
}

@Composable
fun AppRoot(
    onToggleFloatingWindow: () -> Unit,
    onOpenExternal: (Intent) -> Unit
) {
    val context = LocalContext.current
    val settings = remember { context.settingsRepository }
    val navController = rememberNavController()

    // ── 更新日志：首次安装/更新后自动弹出（仅一次）──
    var showChangelog by remember { mutableStateOf(false) }
    var changelogContent by remember { mutableStateOf("") }
    var changelogVersion by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            val versionName = packageInfo.versionName?.removePrefix("v")?.removePrefix("V") ?: ""
            val lastShown = settings.getInt(PrefsKeys.CHANGELOG_LAST_SHOWN_VERSION, -1)
            if (lastShown != versionCode) {
                changelogContent = context.resources
                    .openRawResource(R.raw.changelog)
                    .bufferedReader()
                    .use { it.readText() }
                changelogVersion = versionName
                showChangelog = true
                settings.setInt(PrefsKeys.CHANGELOG_LAST_SHOWN_VERSION, versionCode)
            }
        } catch (e: Exception) {
            // 读取包信息失败时静默跳过
        }
    }

    // ── 导航防抖：防止转场动画期间快速导航导致 AnimatedContent 状态不同步 ──
    val navGuard = remember { object { var lastNavTimeMs = 0L } }
    val navDebounceMs = SECONDARY_SLIDE_DURATION.toLong()

    // 禁用 NavController 自带的返回回调，所有返回键由 BackHandler 统一处理
    // 防止转场动画期间 BackHandler 被短暂禁用时 NavController 绕过防抖直接 pop
    DisposableEffect(navController) {
        navController.enableOnBackPressed(false)
        onDispose {
            navController.enableOnBackPressed(true)
        }
    }

    val safeNavigate = remember(navController, navGuard, navDebounceMs) {
        nav@{ route: String ->
            val now = System.currentTimeMillis()
            if (now - navGuard.lastNavTimeMs < navDebounceMs) {
                Log.w("AppRoot", "navigate blocked by debounce: $route, ${now - navGuard.lastNavTimeMs}ms since last")
                return@nav
            }
            navGuard.lastNavTimeMs = now
            Log.d("AppRoot", "navigate: $route, from=${navController.currentDestination?.route}")
            navController.navigate(route)
        }
    }

    val safePopBack = remember(navController, navGuard, navDebounceMs) {
        pop@{
            val now = System.currentTimeMillis()
            if (now - navGuard.lastNavTimeMs < navDebounceMs) {
                Log.w("AppRoot", "popBack blocked by debounce: ${now - navGuard.lastNavTimeMs}ms since last")
                return@pop
            }
            navGuard.lastNavTimeMs = now
            val result = navController.popBackStack()
            Log.d("AppRoot", "popBack: result=$result, currentRoute=${navController.currentDestination?.route}")
            if (!result) {
                // popBackStack 失败（BackStack 已在 start destination），强制导航到 placeholder
                Log.w("AppRoot", "popBack failed, navigating to placeholder")
                navController.navigate(TAB_PLACEHOLDER) {
                    popUpTo(TAB_PLACEHOLDER) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    // MainViewModel 绑定到 Activity 级 ViewModelStoreOwner，
    // 与 MainActivity.onServiceConnected 中 ViewModelProvider(this) 获取的是同一实例。
    val mainViewModel: MainViewModel = viewModel()

    // ── Tab 页状态：HorizontalPager 管理 4 Tab（Home / History / Favorite / Settings）──
    // beyondViewportPageCount = 2 预加载左右各 2 页，4 Tab 全部常驻组合树
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val currentTab: Screen = tabScreenAt(pagerState.currentPage)
    val scope = rememberCoroutineScope()

    // ── 全屏心率模式 ──
    // 心率订阅下放到 FullScreenHeartRate 内部，避免 AppRoot 根层级随每次心跳重组整棵树
    var isFullScreenMode by remember { mutableStateOf(false) }

    // appStatus / connectedDevice 不在组合中读取，避免任何状态跳变都触发 AppRoot 重组。
    // 全屏状态判断与 KILL 快照更新分别在副作用中按需订阅。
    LaunchedEffect(Unit) {
        mainViewModel.appStatus.collect { status ->
            if (status != AppStatus.CONNECTED && isFullScreenMode) {
                isFullScreenMode = false
            }
        }
    }

    DisposableEffect(isFullScreenMode) {
        val activity = context as? Activity
        if (isFullScreenMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var lastKnownRoute by remember { mutableStateOf(TAB_PLACEHOLDER) }
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            lastKnownRoute = currentRoute
        }
    }
    // Tab 页在 NavHost 外部管理，NavHost 在 placeholder 时表示当前在 Tab 页
    val isOnTab = lastKnownRoute == TAB_PLACEHOLDER

    // ── KILL 现场状态保存：关键状态变化时更新内存快照 ──
    fun pushKillStateSnapshot() {
        val device = mainViewModel.connectedDevice.value
        KillStateSaver.updateSnapshot(
            KillStateSaver.Snapshot(
                route = lastKnownRoute,
                tab = currentTab.route,
                isFullScreen = isFullScreenMode,
                connectedDeviceId = device?.id,
                connectedDeviceName = device?.name
            )
        )
    }

    // connectedDevice 仅用于 KILL 状态快照，用副作用订阅即可，无需在组合中读取
    LaunchedEffect(Unit) {
        mainViewModel.connectedDevice.collect {
            pushKillStateSnapshot()
        }
    }

    // 应用启动时尝试恢复上次 KILL 保存的 Tab / 全屏状态（仅在 Tab 页时）
    LaunchedEffect(Unit) {
        val saved = KillStateSaver.read(context) ?: return@LaunchedEffect
        KillStateSaver.clear(context)
        if (isOnTab) {
            // 恢复 Tab 索引：Home=0, History=1, Favorite=2, Settings=3
            val restoreIndex = when (saved.tab) {
                Screen.History.route -> 1
                Screen.Favorite.route -> 2
                Screen.Settings.route -> 3
                else -> 0
            }
            scope.launch { pagerState.scrollToPage(restoreIndex) }
            if (saved.isFullScreen && mainViewModel.appStatus.value == AppStatus.CONNECTED) {
                isFullScreenMode = true
            }
        }
    }

    LaunchedEffect(currentTab, lastKnownRoute, isFullScreenMode) {
        pushKillStateSnapshot()
    }

    // ── 底层视差位移：进入二级页面时 Tab 层向左移，退出时向右移回 ──
    // 仅由 isOnTab 触发：Tab→二级 时位移，二级→二级 时保持当前状态不变
    // （二级→二级 的"原背景"平移由 NavHost 的 exitTransition 直接处理旧页面，不需要 Tab 层参与）
    val backgroundOffset = remember { Animatable(0f) }
    LaunchedEffect(isOnTab) {
        backgroundOffset.animateTo(
            targetValue = if (isOnTab) 0f else -BACKGROUND_PARALLAX_RATIO,
            animationSpec = tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)
        )
    }

    // ── 系统手势条/导航栏 inset ──
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // ── 液态玻璃效果（底部导航栏）──
    // blur 需 API 31+，lens（扭曲）需 API 33+，低版本库内部静默 no-op
    val liquidGlassConfig by LiquidGlassState.config.collectAsStateWithLifecycle()
    // blur 需 API 31+ (Android 12)，更低版本即使用户开启设置也回退到简单 Surface 模式，
    // 避免 drawBackdrop 仍以半透明采样但 blur 静默 no-op 导致"半个液态玻璃"的异常外观。
    val liquidGlassEnabled = liquidGlassConfig.enabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // layerBackdrop 录制 Tab 内容层供导航栏采样。
    // 先画背景色再画内容，避免玻璃外区域透明（见 Backdrop glass-bottom-bar 教程）。
    // onDraw 用 remember 稳定化，防止 AppRoot 重组时频繁重建 LayerBackdrop。
    val liquidBackdropBgColor = MaterialTheme.colorScheme.surfaceDim
    val liquidBackdropOnDraw: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit =
        remember(liquidBackdropBgColor) {
            { drawRect(liquidBackdropBgColor); drawContent() }
        }
    val liquidBackdrop = rememberLayerBackdrop(onDraw = liquidBackdropOnDraw)

    // 在二级页面拦截返回键，Tab 页让系统处理（退出应用）
    BackHandler(enabled = !isOnTab) {
        safePopBack()
    }

    // 切换 Tab：若在二级页面先 pop 回 placeholder，再 animateScrollToPage 到目标 Tab
    val navigateToTab = remember(navController, currentTab, isOnTab, scope) {
        tab@{ pageIndex: Int ->
            val newTab = tabScreenAt(pageIndex)
            if (currentTab == newTab && isOnTab) return@tab
            if (!isOnTab) {
                navController.popBackStack(TAB_PLACEHOLDER, inclusive = false)
            }
            scope.launch { pagerState.animateScrollToPage(pageIndex) }
        }
    }

    val onOpenExternalStable = remember(onOpenExternal) { onOpenExternal }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim)
    ) {
        // ── 底层：Tab 页（HorizontalPager 管理 4 Tab，beyondViewportPageCount=3 全部常驻）──
        // layerBackdrop 必须在 graphicsLayer 视差位移的外层，否则进入/退出二级页面时
        // 视差动画会使 backdrop 坐标系紊乱，导致 drawBackdrop 采样错位、玻璃扭曲
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 仅在液态玻璃开启时录制本层，供底部导航栏 drawBackdrop 采样
                .then(if (liquidGlassEnabled) Modifier.layerBackdrop(liquidBackdrop) else Modifier)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .graphicsLayer { translationX = backgroundOffset.value * size.width }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 3,
                    // 二级页面覆盖时禁用 Tab 滑动，避免误触
                    userScrollEnabled = isOnTab
                ) { page ->
                    val isActive = isOnTab && pagerState.currentPage == page
                    when (page) {
                        0 -> {
                            val onToggleFloatingWindowStable = remember(onToggleFloatingWindow) { onToggleFloatingWindow }
                            val onNavigateToDevices = remember(safeNavigate) { { safeNavigate(Screen.Devices.route) } }
                            val onEnterFullScreen = remember { { isFullScreenMode = true } }
                            HomeScreen(
                                viewModel = mainViewModel,
                                isActive = isActive,
                                onToggleFloatingWindow = onToggleFloatingWindowStable,
                                onNavigateToDevices = onNavigateToDevices,
                                onEnterFullScreen = onEnterFullScreen
                            )
                        }
                        1 -> {
                            val onChart = remember(safeNavigate) { { sessionId: Long -> safeNavigate(Screen.Chart.createRoute(sessionId)) } }
                            HistoryScreen(
                                onNavigateBack = {},
                                onNavigateToChart = onChart,
                                isInTab = true
                            )
                        }
                        2 -> {
                            FavoriteDevicesScreen(
                                onNavigateBack = {},
                                isInTab = true
                            )
                        }
                        3 -> {
                            val onSettingsNavigate = remember(safeNavigate) { { route: String -> safeNavigate(route.toScreenRoute()) } }
                            val showToast = remember(context) { { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() } }
                            SettingsScreen(
                                settings = settings,
                                isActive = isActive,
                                onNavigate = onSettingsNavigate,
                                onOpenExternal = onOpenExternalStable,
                                showToast = showToast
                            )
                        }
                    }
                }
            }
        }

        // ── 底部导航条渐变遮罩（悬浮胶囊背后 + 系统导航条区域）──
        // 位于胶囊之下、Tab 内容之上：内容滚动到底部时平滑淡出到表面色。
        // 二级页面被 NavHost 覆盖，其底部渐变由 NavHost 之后的 inset 渐变层统一提供。
        if (!isFullScreenMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(navBarBottomInset + FLOATING_NAV_HEIGHT.dp + FLOATING_NAV_BOTTOM_MARGIN.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                        )
                    )
            )
        }

        // ── 中层：悬浮胶囊式底部导航（液态玻璃三层结构 / 简单回退）──
        if (!isFullScreenMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = navBarBottomInset + FLOATING_NAV_BOTTOM_MARGIN.dp)
            ) {
                if (liquidGlassEnabled) {
                    // 三层液态玻璃：背景 + 透明录制层 + 滑动指示器（含拖拽切换、跟随高光、速度形变）
                    FloatingBottomBar(
                        backdrop = liquidBackdrop,
                        selectedTabIndex = { pagerState.targetPage },
                        onTabSelected = { index -> navigateToTab(index) },
                        tabs = listOf(
                            GlassTabItem(R.drawable.ic_tab_home, stringResource(R.string.nav_home)),
                            GlassTabItem(R.drawable.ic_tab_history, stringResource(R.string.nav_history)),
                            GlassTabItem(R.drawable.ic_tab_favorite, stringResource(R.string.nav_favorite)),
                            GlassTabItem(R.drawable.ic_tab_settings, stringResource(R.string.nav_settings))
                        ),
                        config = liquidGlassConfig
                    )
                } else {
                    // 简单回退：普通 Surface + 常驻 label
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(FLOATING_NAV_HEIGHT.dp),
                        shape = ContinuousCapsule,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 用 targetPage 让选中状态立即响应（与玻璃模式一致），避免 currentPage 延迟导致闪烁
                            val selectedPage = pagerState.targetPage
                            CapsuleNavItem(
                                selected = selectedPage == 0,
                                onClick = remember(navigateToTab) { { navigateToTab(0) } },
                                iconRes = R.drawable.ic_tab_home,
                                label = stringResource(R.string.nav_home),
                                modifier = Modifier.weight(1f)
                            )
                            CapsuleNavItem(
                                selected = selectedPage == 1,
                                onClick = remember(navigateToTab) { { navigateToTab(1) } },
                                iconRes = R.drawable.ic_tab_history,
                                label = stringResource(R.string.nav_history),
                                modifier = Modifier.weight(1f)
                            )
                            CapsuleNavItem(
                                selected = selectedPage == 2,
                                onClick = remember(navigateToTab) { { navigateToTab(2) } },
                                iconRes = R.drawable.ic_tab_favorite,
                                label = stringResource(R.string.nav_favorite),
                                modifier = Modifier.weight(1f)
                            )
                            CapsuleNavItem(
                                selected = selectedPage == 3,
                                onClick = remember(navigateToTab) { { navigateToTab(3) } },
                                iconRes = R.drawable.ic_tab_settings,
                                label = stringResource(R.string.nav_settings),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ── 上层：NavHost 管理二级页面（覆盖在 Tab 层和导航栏之上）──
        // 转场动画分两类：
        // 1. Tab→二级 / 二级→Tab：placeholder 透明，旧页面 slideOut 整页滑出（被新页面覆盖）
        // 2. 二级→二级：旧页面作为"原背景"小幅左移，新页面从右滑入覆盖；
        //    返回时旧页面小幅右移，新页面从左滑入
        NavHost(
            navController = navController,
            startDestination = TAB_PLACEHOLDER,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideInHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth }
            },
            exitTransition = {
                val fromSecondary = initialState.destination.route != TAB_PLACEHOLDER
                val toSecondary = targetState.destination.route != TAB_PLACEHOLDER
                if (fromSecondary && toSecondary) {
                    // 二级→二级：旧页面小幅左移
                    slideOutHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> -(fullWidth * BACKGROUND_PARALLAX_RATIO).toInt() }
                } else {
                    slideOutHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth }
                }
            },
            popEnterTransition = {
                val fromSecondary = initialState.destination.route != TAB_PLACEHOLDER
                val toSecondary = targetState.destination.route != TAB_PLACEHOLDER
                if (fromSecondary && toSecondary) {
                    // 二级→二级返回：旧页面从左侧视差位置移回
                    slideInHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> -(fullWidth * BACKGROUND_PARALLAX_RATIO).toInt() }
                } else {
                    slideInHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth }
                }
            },
            popExitTransition = {
                // 退出时统一整页右滑出（揭开覆盖，露出下方页面），与 Tab→二级 返回一致
                slideOutHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth }
            }
        ) {
            composable(TAB_PLACEHOLDER) { /* 透明占位，Tab 层在下方可见 */ }
            composable(
                route = Screen.Chart.route,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                val onBack = remember(safePopBack) { { safePopBack() } }
                ChartScreen(
                    sessionId = sessionId,
                    onNavigateBack = onBack
                )
            }
            composable(Screen.Alarm.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                HeartRateAlarmScreen(
                    onNavigateBack = onBack
                )
            }
            composable(Screen.Server.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                ServerScreen(
                    onNavigateBack = onBack,
                    settings = settings
                )
            }
            composable(Screen.Webhook.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                WebhookScreen(onNavigateBack = onBack)
            }
            composable(Screen.LanTransfer.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                LanTransferScreen(
                    onNavigateBack = onBack,
                    settings = settings
                )
            }
            composable(Screen.FairMemory.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                FairMemoryScreen(onNavigateBack = onBack)
            }
            composable(Screen.Theme.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                ThemeSettingsScreen(onNavigateBack = onBack)
            }
            composable(Screen.NavStyle.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                NavStyleScreen(onNavigateBack = onBack)
            }
            composable(Screen.Devices.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                DevicesScreen(
                    viewModel = mainViewModel,
                    onNavigateBack = onBack
                )
            }
            composable(Screen.FullscreenSound.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                FullscreenSoundScreen(
                    settings = settings,
                    onNavigateBack = onBack
                )
            }
            composable(Screen.License.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                LicenseScreen(onNavigateBack = onBack)
            }
            composable(Screen.Privacy.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                PrivacyScreen(onNavigateBack = onBack)
            }
            composable(Screen.AboutDetails.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                val onDetailsNavigate = remember(safeNavigate) { { route: String -> safeNavigate(route.toScreenRoute()) } }
                val showToast = remember(context) { { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() } }
                AboutDetailsScreen(
                    onNavigate = onDetailsNavigate,
                    onNavigateBack = onBack,
                    onOpenExternal = onOpenExternalStable,
                    showToast = showToast
                )
            }
            composable(Screen.FunctionSettings.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                FunctionSettingsScreen(
                    settings = settings,
                    onNavigateBack = onBack
                )
            }
            composable(Screen.StatusBarSettings.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                StatusBarSettingsScreen(
                    settings = settings,
                    onNavigateBack = onBack
                )
            }
            composable(Screen.FloatingWindowSettings.route) {
                val onBack = remember(safePopBack) { { safePopBack() } }
                FloatingWindowSettingsScreen(
                    settings = settings,
                    onNavigateBack = onBack
                )
            }
        }

        // ── 系统栏渐变遮罩（覆盖所有页面，含二级页）──
        // 顶部：状态栏区域渐变，柔化状态栏图标下的背景。
        // 底部：仅覆盖系统导航条 inset 区域（胶囊底部 margin 为 navBarBottomInset + 12dp，
        //       此处取 +8dp 不与胶囊重叠），二级页面底部渐变由本层统一提供。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarTopInset * 1.2f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        1f to Color.Transparent
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(navBarBottomInset + 8.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                )
        )

        // ── 最顶层：全屏心率模式覆盖层 ──
        if (isFullScreenMode) {
            val onExitFullScreen = remember { { isFullScreenMode = false } }
            FullScreenHeartRate(
                viewModel = mainViewModel,
                onExit = onExitFullScreen
            )
        }

        // ── 更新日志 BottomSheet（首次安装/更新后自动弹出）──
        if (showChangelog) {
            ChangelogBottomSheet(
                changelogContent = changelogContent,
                currentVersion = changelogVersion,
                onDismiss = { showChangelog = false }
            )
        }
    }
}

@Composable
private fun CapsuleNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "capsuleItemColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "capsuleTextColor"
    )
    // Surface 仅负责 shape + 背景色，clickable 不带 ripple（避免多 Tab 同时显示激活反馈）
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            ),
        shape = ContinuousCapsule,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(NAV_ICON_SIZE)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1
            )
        }
    }
}
