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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.main.FullScreenHeartRate
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.settings.ChangelogBottomSheet
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

/**
 * ChangelogNotifier 由 Hilt 单例提供（契约 10：设置读写归 VM/单例）。
 */
@Composable
fun AppRoot(
    changelogNotifier: ChangelogNotifier,
    liquidGlassState: LiquidGlassState,
    killStateSaver: KillStateSaver,
    onToggleFloatingWindow: () -> Unit,
    onOpenExternal: (Intent) -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navGuard = rememberNavGuard()
    val safeNavigate = rememberSafeNavigate(navController, navGuard)
    val safePopBack = rememberSafePopBack(navController, navGuard)

    val mainViewModel: MainViewModel = hiltViewModel()
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val currentTab: Screen = tabScreenAt(pagerState.currentPage)
    val scope = rememberCoroutineScope()

    // 心率订阅下放到 FullScreenHeartRate 内部，避免 AppRoot 根层级随每次心跳重组整棵树
    var isFullScreenMode by remember { mutableStateOf(false) }

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

    AppLifecycleEffects(
        mainViewModel = mainViewModel,
        killStateSaver = killStateSaver,
        pagerState = pagerState,
        isFullScreenMode = isFullScreenMode,
        onFullScreenChange = { isFullScreenMode = it },
        isOnTab = isOnTab,
        currentTab = currentTab,
        lastKnownRoute = lastKnownRoute
    )

    // 仅由 isOnTab 触发：Tab→二级 时位移，二级→二级 时保持当前状态不变
    val backgroundOffset = remember { Animatable(0f) }
    LaunchedEffect(isOnTab) {
        backgroundOffset.animateTo(
            targetValue = if (isOnTab) 0f else -BACKGROUND_PARALLAX_RATIO,
            animationSpec = tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)
        )
    }

    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusBarTopInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // blur 需 API 31+，lens 需 API 33+，低版本库内部静默 no-op
    val liquidGlassConfig by liquidGlassState.config.collectAsStateWithLifecycle()
    // blur 需 API 31+ (Android 12)，更低版本即使用户开启设置也回退到简单 Surface 模式，
    // 避免 drawBackdrop 仍以半透明采样但 blur 静默 no-op 导致"半个液态玻璃"的异常外观。
    val liquidGlassEnabled = liquidGlassConfig.enabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // 先画背景色再画内容，避免玻璃外区域透明。
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

    val changelogNotice by changelogNotifier.notice.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceDim)
    ) {
        // layerBackdrop 必须在 graphicsLayer 视差位移的外层，否则视差动画会使 backdrop 采样错位
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                    onOpenExternal = onOpenExternalStable
                )
            }
        }

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

        AppNavHost(
            navController = navController,
            safePopBack = safePopBack,
            safeNavigate = safeNavigate,
            mainViewModel = mainViewModel,
            onOpenExternal = onOpenExternalStable
        )

        // 底部：仅覆盖系统导航条 inset 区域，二级页面底部渐变由本层统一提供
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

        if (isFullScreenMode) {
            val onExitFullScreen = remember { { isFullScreenMode = false } }
            FullScreenHeartRate(
                viewModel = mainViewModel,
                onExit = onExitFullScreen
            )
        }

        val notice = changelogNotice
        if (notice != null) {
            ChangelogBottomSheet(
                changelogContent = notice.content,
                currentVersion = notice.versionName,
                onDismiss = { changelogNotifier.dismiss() }
            )
        }
    }
}
