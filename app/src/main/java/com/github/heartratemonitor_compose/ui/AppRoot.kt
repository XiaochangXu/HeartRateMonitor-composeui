package com.github.heartratemonitor_compose.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.ui.alarm.HeartRateAlarmScreen
import com.github.heartratemonitor_compose.ui.favorite.FavoriteDevicesScreen
import com.github.heartratemonitor_compose.ui.history.ChartScreen
import com.github.heartratemonitor_compose.ui.history.HistoryScreen
import com.github.heartratemonitor_compose.ui.main.AppStatus
import com.github.heartratemonitor_compose.ui.main.DevicesScreen
import com.github.heartratemonitor_compose.ui.main.FullScreenHeartRate
import com.github.heartratemonitor_compose.ui.main.HomeScreen
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.server.ServerScreen
import com.github.heartratemonitor_compose.ui.settings.FairMemoryScreen
import com.github.heartratemonitor_compose.ui.settings.FullscreenSoundScreen
import com.github.heartratemonitor_compose.ui.settings.SettingsScreen
import com.github.heartratemonitor_compose.ui.theme.ThemeSettingsScreen
import com.github.heartratemonitor_compose.ui.webhook.WebhookScreen
import com.github.heartratemonitor_compose.util.settingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch


private const val FLOATING_NAV_HEIGHT = 64
private const val FLOATING_NAV_BOTTOM_MARGIN = 0
private const val NAV_ITEM_DURATION = 200
private val NAV_INDICATOR_HEIGHT = 40.dp
private val NAV_INDICATOR_CORNER = 20.dp
private val NAV_ICON_SIZE = 24.dp

// Tab 页切换动画时长
private const val TAB_SLIDE_DURATION = 200
// 二级页面 NavHost 转场动画时长
private const val SECONDARY_SLIDE_DURATION = 300
// 进入二级页面时底层 Tab 层向左位移比例（视差效果）
private const val BACKGROUND_PARALLAX_RATIO = 0.2f

// NavHost 占位路由：Tab 页在 NavHost 外部管理，此路由仅作为 startDestination
private const val TAB_PLACEHOLDER = "tab_placeholder"

// ── 悬浮胶囊导航栏动画规格 ──
// spring 物理弹簧：短距离自动快速完成，长距离平滑过渡，方向切换即时响应
private val navBarHideSpec: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = 600f
)
private val navBarShowSpec: AnimationSpec<Float> = spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = 600f
)

/** 导航栏 Channel 消息：串行化 scroll 追踪和 fling 动画，消除竞态 */
private sealed class NavBarMsg {
    /** 滚动跟踪：立即 snapTo 到该位置 */
    data class Track(val progress: Float) : NavBarMsg()
    /** Fling 结束：平滑动画到目标位置 */
    data class AnimateTo(val target: Float, val spec: AnimationSpec<Float>) : NavBarMsg()
}

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
    object FairMemory : Screen("fair_memory")
    object Theme : Screen("theme")
    object Devices : Screen("devices")
    object FullscreenSound : Screen("fullscreen_sound")
}

/** 底部导航 Tab 页 */
private fun Screen.isTab(): Boolean = this is Screen.Home || this is Screen.Settings

/** SettingsScreen 用字符串路由映射到 Navigation Compose 路由 */
private fun String.toScreenRoute(): String = when (this) {
    "favorite" -> Screen.Favorite.route
    "alarm" -> Screen.Alarm.route
    "server" -> Screen.Server.route
    "webhook" -> Screen.Webhook.route
    "fair_memory" -> Screen.FairMemory.route
    "history" -> Screen.History.route
    "theme" -> Screen.Theme.route
    "devices" -> Screen.Devices.route
    "fullscreen_sound" -> Screen.FullscreenSound.route
    else -> Screen.Home.route
}


@Composable
fun AppRoot(
    onToggleFloatingWindow: () -> Unit,
    onOpenExternal: (Intent) -> Unit
) {
    val context = LocalContext.current
    val settings = remember { context.settingsRepository }
    val navController = rememberNavController()

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

    fun safeNavigate(route: String) {
        val now = System.currentTimeMillis()
        if (now - navGuard.lastNavTimeMs < navDebounceMs) {
            Log.w("AppRoot", "navigate blocked by debounce: $route, ${now - navGuard.lastNavTimeMs}ms since last")
            return
        }
        navGuard.lastNavTimeMs = now
        Log.d("AppRoot", "navigate: $route, from=${navController.currentDestination?.route}")
        navController.navigate(route)
    }

    fun safePopBack() {
        val now = System.currentTimeMillis()
        if (now - navGuard.lastNavTimeMs < navDebounceMs) {
            Log.w("AppRoot", "popBack blocked by debounce: ${now - navGuard.lastNavTimeMs}ms since last")
            return
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

    // MainViewModel 绑定到 Activity 级 ViewModelStoreOwner，
    // 与 MainActivity.onServiceConnected 中 ViewModelProvider(this) 获取的是同一实例。
    val mainViewModel: MainViewModel = viewModel()

    // ── Tab 页状态：Home / Settings 始终在组合树中，通过 graphicsLayer 位移切换 ──
    var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }
    val tabOffset = remember { Animatable(0f) } // 0 = Home, 1 = Settings
    LaunchedEffect(currentTab) {
        tabOffset.animateTo(
            targetValue = if (currentTab is Screen.Settings) 1f else 0f,
            animationSpec = tween(TAB_SLIDE_DURATION, easing = FastOutSlowInEasing)
        )
    }

    // ── 全屏心率模式 ──
    var isFullScreenMode by remember { mutableStateOf(false) }
    val heartRate by mainViewModel.heartRate.collectAsStateWithLifecycle()
    val appStatus by mainViewModel.appStatus.collectAsStateWithLifecycle()

    // ── 悬浮窗开关状态（底部圆形按钮）──
    val floatingWindowEnabled by settings.observeBoolean(PrefsKeys.FLOATING_WINDOW_ENABLED, false)
        .collectAsStateWithLifecycle()

    DisposableEffect(isFullScreenMode) {
        val activity = context as? Activity
        if (isFullScreenMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(appStatus) {
        if (appStatus != AppStatus.CONNECTED && isFullScreenMode) {
            isFullScreenMode = false
        }
    }

    // 当前路由：用于底部导航选中态、胶囊隐藏逻辑、返回键拦截
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // 跟踪最后已知的非 null 路由，防止转场动画期间 currentRoute 短暂为 null 导致 isOnTab 误判
    // 从而避免 BackHandler 在转场期间被错误禁用，让 NavController 自带回调绕过防抖
    var lastKnownRoute by remember { mutableStateOf(TAB_PLACEHOLDER) }
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) {
            lastKnownRoute = currentRoute
        }
    }
    // Tab 页在 NavHost 外部管理，NavHost 在 placeholder 时表示当前在 Tab 页
    val isOnTab = lastKnownRoute == TAB_PLACEHOLDER
    val currentScreen = if (isOnTab) currentTab else null

    // ── 底层视差位移：进入二级页面时 Tab 层向左移，退出时向右移回 ──
    val backgroundOffset = remember { Animatable(0f) }
    LaunchedEffect(isOnTab) {
        backgroundOffset.animateTo(
            targetValue = if (isOnTab) 0f else -BACKGROUND_PARALLAX_RATIO,
            animationSpec = tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)
        )
    }

    // ── 系统手势条/导航栏 inset ──
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // ── 胶囊导航栏显隐：向下滚动隐藏，向上滚动显示 ──
    val navBarHideProgress = remember { Animatable(0f) }
    val totalHideDistance = with(LocalDensity.current) {
        (FLOATING_NAV_HEIGHT.dp + FLOATING_NAV_BOTTOM_MARGIN.dp + navBarBottomInset).toPx()
    }
    // 离开 Settings Tab 时立即恢复导航栏（进入二级页面不干预，保持当前隐藏/显示状态）
    LaunchedEffect(currentTab) {
        if (currentTab !is Screen.Settings && navBarHideProgress.value != 0f) {
            navBarHideProgress.snapTo(0f)
        }
    }

    // 使用 CONFLATED Channel 串行化动画请求。
    // animateTo 放入独立协程，新方向到达时可立即取消当前动画。
    val navBarChannel = remember { Channel<NavBarMsg>(Channel.CONFLATED) }
    LaunchedEffect(navBarChannel) {
        var flingJob: Job? = null
        for (msg in navBarChannel) {
            when (msg) {
                is NavBarMsg.AnimateTo -> {
                    flingJob?.cancel()
                    flingJob = launch {
                        Log.d("AppRoot", "animateTo: target=${msg.target.toInt()} from=${"%.2f".format(navBarHideProgress.value)}")
                        navBarHideProgress.animateTo(msg.target, msg.spec)
                        Log.d("AppRoot", "animateTo done: target=${msg.target.toInt()}")
                    }
                }
                else -> {} // Track 消息已废弃，忽略
            }
        }
    }

    val nestedScrollConnection = remember(currentTab, isOnTab) {
        object : NestedScrollConnection {
            // 检测滑动方向，立即触发完整动画（不再渐进式追踪位置）
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (currentTab !is Screen.Settings || !isOnTab) return Offset.Zero
                if (source == NestedScrollSource.SideEffect) return Offset.Zero
                if (available.y > 0f && navBarHideProgress.targetValue != 0f) {
                    navBarChannel.trySend(NavBarMsg.AnimateTo(0f, navBarShowSpec))
                } else if (available.y < 0f && navBarHideProgress.targetValue != 1f) {
                    navBarChannel.trySend(NavBarMsg.AnimateTo(1f, navBarHideSpec))
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (currentTab !is Screen.Settings || !isOnTab) return Offset.Zero
                if (source == NestedScrollSource.SideEffect) return Offset.Zero
                if (available.y > 0f && navBarHideProgress.targetValue != 0f) {
                    navBarChannel.trySend(NavBarMsg.AnimateTo(0f, navBarShowSpec))
                } else if (available.y < 0f && navBarHideProgress.targetValue != 1f) {
                    navBarChannel.trySend(NavBarMsg.AnimateTo(1f, navBarHideSpec))
                }
                return Offset.Zero
            }
        }
    }

    // 在二级页面拦截返回键，Tab 页让系统处理（退出应用）
    BackHandler(enabled = !isOnTab) {
        safePopBack()
    }

    fun navigateToTab(route: String) {
        val newTab = when (route) {
            Screen.Settings.route -> Screen.Settings
            else -> Screen.Home
        }
        if (currentTab == newTab && isOnTab) return
        // 若在二级页面，先 pop 回 placeholder 露出 Tab 层
        if (!isOnTab) {
            navController.popBackStack(TAB_PLACEHOLDER, inclusive = false)
        }
        currentTab = newTab
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim)
            .nestedScroll(nestedScrollConnection)
    ) {
        // ── 底层：Tab 页（始终在组合树中，通过 graphicsLayer 位移切换，零组合开销）──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .graphicsLayer { translationX = backgroundOffset.value * size.width }
        ) {
            // Home 页（offset=0 时可见，向左滑出）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = -tabOffset.value * size.width }
            ) {
                HomeScreen(
                    viewModel = mainViewModel,
                    onOpenHistory = { safeNavigate(Screen.History.route) },
                    onNavigateToDevices = { safeNavigate(Screen.Devices.route) },
                    onEnterFullScreen = { isFullScreenMode = true }
                )
            }
            // Settings 页（offset=1 时可见，从右侧滑入）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = (1f - tabOffset.value) * size.width }
            ) {
                SettingsScreen(
                    settings = settings,
                    onNavigate = { route -> safeNavigate(route.toScreenRoute()) },
                    onOpenExternal = onOpenExternal,
                    showToast = { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // ── 中层：悬浮胶囊式底部导航（始终渲染，保持在原位，由上层 NavHost 滑入覆盖）──
        if (!isFullScreenMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = navBarHideProgress.value
                        translationY = p * totalHideDistance
                        alpha = 1f - p
                        scaleX = 1f - 0.06f * p
                        scaleY = 1f - 0.06f * p
                    }
                    .padding(horizontal = 16.dp)
                    .padding(bottom = navBarBottomInset + FLOATING_NAV_BOTTOM_MARGIN.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f).height(FLOATING_NAV_HEIGHT.dp),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CapsuleNavItem(
                            selected = currentScreen is Screen.Home,
                            onClick = { navigateToTab(Screen.Home.route) },
                            iconRes = R.drawable.ic_home_symbol,
                            label = stringResource(R.string.nav_home),
                            modifier = Modifier.weight(1f)
                        )
                        CapsuleNavItem(
                            selected = currentScreen is Screen.Settings,
                            onClick = { navigateToTab(Screen.Settings.route) },
                            iconRes = R.drawable.ic_settings_symbol,
                            label = stringResource(R.string.nav_settings),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Surface(
                    modifier = Modifier.size(FLOATING_NAV_HEIGHT.dp),
                    shape = CircleShape,
                    color = if (floatingWindowEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainer,
                    onClick = onToggleFloatingWindow
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(
                                if (floatingWindowEnabled) R.drawable.ic_floating_window_on
                                else R.drawable.ic_floating_window_off
                            ),
                            contentDescription = stringResource(R.string.cd_toggle_floating_window),
                            tint = if (floatingWindowEnabled) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // ── 上层：NavHost 管理二级页面（覆盖在 Tab 层和导航栏之上）──
        NavHost(
            navController = navController,
            startDestination = TAB_PLACEHOLDER,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                slideInHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth }
            },
            exitTransition = {
                slideOutHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth }
            },
            popEnterTransition = {
                slideInHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth }
            },
            popExitTransition = {
                slideOutHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth }
            }
        ) {
            composable(TAB_PLACEHOLDER) { /* 透明占位，Tab 层在下方可见 */ }
            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateBack = { safePopBack() },
                    onNavigateToChart = { sessionId ->
                        safeNavigate(Screen.Chart.createRoute(sessionId))
                    }
                )
            }
            composable(
                route = Screen.Chart.route,
                arguments = listOf(
                    navArgument("sessionId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                ChartScreen(
                    sessionId = sessionId,
                    onNavigateBack = { safePopBack() }
                )
            }
            composable(Screen.Favorite.route) {
                FavoriteDevicesScreen(
                    onNavigateBack = { safePopBack() }
                )
            }
            composable(Screen.Alarm.route) {
                HeartRateAlarmScreen(
                    settings = settings,
                    onNavigateBack = { safePopBack() }
                )
            }
            composable(Screen.Server.route) {
                ServerScreen(
                    onNavigateBack = { safePopBack() },
                    settings = settings
                )
            }
            composable(Screen.Webhook.route) {
                WebhookScreen(
                    onNavigateBack = { safePopBack() }
                )
            }
            composable(Screen.FairMemory.route) {
                FairMemoryScreen(
                    onNavigateBack = { safePopBack() }
                )
            }
            composable(Screen.Theme.route) {
                ThemeSettingsScreen(
                    onNavigateBack = { safePopBack() }
                )
            }
            composable(Screen.Devices.route) {
                DevicesScreen(
                    viewModel = mainViewModel,
                    onNavigateBack = { safePopBack() }
                )
            }
            composable(Screen.FullscreenSound.route) {
                FullscreenSoundScreen(
                    settings = settings,
                    onNavigateBack = { safePopBack() }
                )
            }
        }

        // ── 最顶层：全屏心率模式覆盖层 ──
        if (isFullScreenMode) {
            FullScreenHeartRate(
                heartRate = heartRate,
                onExit = { isFullScreenMode = false }
            )
        }
    }
}

/**
 * 悬浮胶囊内导航项（iOS 风格）。
 */
@Composable
private fun CapsuleNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else Color.Transparent,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "capsuleIndicatorColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing),
        label = "capsuleItemColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .height(NAV_INDICATOR_HEIGHT)
                .background(
                    color = indicatorColor,
                    shape = RoundedCornerShape(NAV_INDICATOR_CORNER)
                )
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(NAV_ICON_SIZE)
            )
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing)) +
                    fadeIn(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing)),
                exit = shrinkHorizontally(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing)) +
                    fadeOut(tween(NAV_ITEM_DURATION, easing = FastOutSlowInEasing))
            ) {
                Text(
                    text = " $label",
                    style = MaterialTheme.typography.labelMedium,
                    color = iconColor,
                    maxLines = 1
                )
            }
        }
    }
}
