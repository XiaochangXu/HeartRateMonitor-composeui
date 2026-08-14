package com.github.heartratemonitor_compose.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.heartratemonitor_compose.data.di.settingsRepository
import com.github.heartratemonitor_compose.ui.main.FullScreenHeartRate
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.settings.ChangelogBottomSheet
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

/**
 * 应用根 Composable — 主编排器。
 *
 * 组装所有拆分后的子组件：
 * - [AppTabPager]：Tab 页 HorizontalPager
 * - [AppBottomNavBar]：底部导航栏（液态玻璃 / 回退）
 * - [AppNavHost]：二级页面路由表
 * - [AppLifecycleEffects]：KILL 现场状态快照
 * - [rememberChangelogState]：更新日志检测
 */
@Composable
fun AppRoot(
    onToggleFloatingWindow: () -> Unit,
    onOpenExternal: (Intent) -> Unit
) {
    val context = LocalContext.current
    val settings = remember { context.settingsRepository }
    val navController = rememberNavController()
    val navGuard = rememberNavGuard()
    val safeNavigate = rememberSafeNavigate(navController, navGuard)
    val safePopBack = rememberSafePopBack(navController, navGuard)

    val mainViewModel: MainViewModel = viewModel()
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val currentTab: Screen = tabScreenAt(pagerState.currentPage)
    val scope = rememberCoroutineScope()

    // ── 全屏心率模式 ──
    // 心率订阅下放到 FullScreenHeartRate 内部，避免 AppRoot 根层级随每次心跳重组整棵树
    var isFullScreenMode by remember { mutableStateOf(false) }

    // 全屏时锁定横屏
    DisposableEffect(isFullScreenMode) {
        val activity = context as? Activity
        if (isFullScreenMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // 禁用 NavController 自带的返回回调，所有返回键由 BackHandler 统一处理
    // 防止转场动画期间 BackHandler 被短暂禁用时 NavController 绕过防抖直接 pop
    DisposableEffect(navController) {
        navController.enableOnBackPressed(false)
        onDispose {
            navController.enableOnBackPressed(true)
        }
    }

    // ── 路由追踪 ──
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

    // ── 生命周期副作用：KILL 快照管理 + 断连退出全屏 ──
    AppLifecycleEffects(
        mainViewModel = mainViewModel,
        pagerState = pagerState,
        isFullScreenMode = isFullScreenMode,
        onFullScreenChange = { isFullScreenMode = it },
        isOnTab = isOnTab,
        currentTab = currentTab,
        lastKnownRoute = lastKnownRoute
    )

    // ── 底层视差位移：进入二级页面时 Tab 层向左移，退出时向右移回 ──
    // 仅由 isOnTab 触发：Tab→二级 时位移，二级→二级 时保持当前状态不变
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

    // ── Tab 切换导航 ──
    val navigateToTab = remember(navController, currentTab, isOnTab, scope, navGuard) {
        tab@{ pageIndex: Int ->
            val newTab = tabScreenAt(pageIndex)
            if (currentTab == newTab && isOnTab) return@tab
            if (!isOnTab) {
                val now = System.currentTimeMillis()
                if (now - navGuard.lastNavTimeMs < TRANSITION_DEBOUNCE_MS) {
                    Log.w("AppRoot", "tab switch blocked by debounce: ${now - navGuard.lastNavTimeMs}ms since last")
                    return@tab
                }
                navGuard.lastNavTimeMs = now
                navController.popBackStack(TAB_PLACEHOLDER, inclusive = false)
            }
            scope.launch { pagerState.animateScrollToPage(pageIndex) }
        }
    }

    val onOpenExternalStable = remember(onOpenExternal) { onOpenExternal }

    // ── 更新日志状态 ──
    val changelogState = rememberChangelogState(settings)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim)
    ) {
        // ── 底层：Tab 页（HorizontalPager 管理 4 Tab）──
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
                AppTabPager(
                    viewModel = mainViewModel,
                    pagerState = pagerState,
                    isOnTab = isOnTab,
                    onToggleFloatingWindow = onToggleFloatingWindow,
                    onEnterFullScreen = { isFullScreenMode = true },
                    safeNavigate = safeNavigate,
                    settings = settings,
                    onOpenExternal = onOpenExternalStable
                )
            }
        }

        // ── 底部导航栏（渐变遮罩 + 胶囊导航）──
        AppBottomNavBar(
            liquidGlassEnabled = liquidGlassEnabled,
            liquidBackdrop = liquidBackdrop,
            liquidGlassConfig = liquidGlassConfig,
            isFullScreenMode = isFullScreenMode,
            selectedPage = { pagerState.targetPage },
            onTabSelected = { index -> navigateToTab(index) },
            navBarBottomInset = navBarBottomInset,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // ── 中层：NavHost 管理二级页面（覆盖在 Tab 层和导航栏之上）──
        AppNavHost(
            navController = navController,
            safePopBack = safePopBack,
            safeNavigate = safeNavigate,
            settings = settings,
            mainViewModel = mainViewModel,
            onOpenExternal = onOpenExternalStable
        )

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

        // 更新日志 BottomSheet（首次安装/更新后自动弹出）
        if (changelogState.showChangelog) {
            ChangelogBottomSheet(
                changelogContent = changelogState.changelogContent,
                currentVersion = changelogState.changelogVersion,
                onDismiss = { changelogState.showChangelog = false }
            )
        }
    }
}
