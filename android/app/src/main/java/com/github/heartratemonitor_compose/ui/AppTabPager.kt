package com.github.heartratemonitor_compose.ui

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.favorite.FavoriteDevicesScreen
import com.github.heartratemonitor_compose.ui.history.HistoryScreen
import com.github.heartratemonitor_compose.ui.main.AppStatus
import com.github.heartratemonitor_compose.ui.main.HomeScreen
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.settings.SettingsScreen
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassConfig
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

@Composable
fun AppTabHost(
    viewModel: MainViewModel,
    onToggleFloatingWindow: () -> Unit,
    onOpenExternal: (android.content.Intent) -> Unit,
    liquidGlassConfig: LiquidGlassConfig,
    onCurrentTabChange: (Screen) -> Unit,
    killStateSaver: KillStateSaver,
    navAnimationDisabled: Boolean
) {
    val context = LocalContext.current
    // pagerState 随场景生灭：rememberSaveable 恢复页签
    val pagerState = rememberPagerState(initialPage = 0) { 4 }
    val currentTab = tabScreenAt(pagerState.currentPage)

    // 当前 Tab 上报 AppRoot（KillStateSaver 快照用）
    LaunchedEffect(currentTab) { onCurrentTabChange(currentTab) }

    // 进程恢复（KillStateSaver）：原 AppLifecycleEffects 恢复逻辑，随场景执行
    LaunchedEffect(Unit) {
        val saved = killStateSaver.read() ?: return@LaunchedEffect
        killStateSaver.clear()
        val restoreIndex = when (saved.tab) {
            Screen.History.route -> 1
            Screen.Favorite.route -> 2
            Screen.Settings.route -> 3
            else -> 0
        }
        pagerState.scrollToPage(restoreIndex)
        if (saved.isFullScreen && viewModel.uiState.value.appStatus == AppStatus.CONNECTED) {
            context.launchDestination(Destination.Fullscreen)
        }
    }

    // 切 Tab：二级页面已独立成 Activity，导航条只在 Tab 宿主渲染，恒可交互
    val scope = rememberCoroutineScope()
    val onTabSelected: (Int) -> Unit = { pageIndex ->
        if (tabScreenAt(pageIndex) != currentTab) {
            if (navAnimationDisabled) {
                scope.launch { pagerState.scrollToPage(pageIndex) }
            } else {
                scope.launch { pagerState.animateScrollToPage(pageIndex) }
            }
        }
    }
    val selectedPage: () -> Int = { pagerState.targetPage }
    // 切 Tab 动画检测：currentPage != targetPage 期间为 true，用于通知导航条降级玻璃效果
    val isTabSwitching: () -> Boolean = {
        pagerState.currentPage != pagerState.targetPage
    }

    // blur 需 API 31+，lens 需 API 33+，低版本库内部静默 no-op；
    // 更低版本即使用户开启设置也回退到简单 Surface 模式
    val liquidGlassEnabled = liquidGlassConfig.enabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // 先画背景色再画内容，避免玻璃外区域透明。
    // onDraw 用 remember 稳定化，防止宿主重组时频繁重建 LayerBackdrop。
    val liquidBackdropBgColor = MaterialTheme.colorScheme.surfaceContainer
    val liquidBackdropOnDraw: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit =
        remember(liquidBackdropBgColor) {
            { drawRect(liquidBackdropBgColor); drawContent() }
        }
    val liquidBackdrop = rememberLayerBackdrop(onDraw = liquidBackdropOnDraw)

    Box(modifier = Modifier.fillMaxSize()) {
        // 玻璃采样层只包 Pager 内容：导航条在层外，液态玻璃不会采样到自身
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (liquidGlassEnabled) Modifier.layerBackdrop(liquidBackdrop) else Modifier)
        ) {
            AppTabPager(
                viewModel = viewModel,
                pagerState = pagerState,
                onToggleFloatingWindow = onToggleFloatingWindow,
                onOpenExternal = onOpenExternal
            )
        }

        AppBottomNavBar(
            liquidGlassEnabled = liquidGlassEnabled,
            liquidBackdrop = liquidBackdrop,
            liquidGlassConfig = liquidGlassConfig,
            selectedPage = selectedPage,
            onTabSelected = onTabSelected,
            isTabSwitching = isTabSwitching,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun AppTabPager(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    pagerState: PagerState,
    onToggleFloatingWindow: () -> Unit,
    onOpenExternal: (android.content.Intent) -> Unit
) {
    val context = LocalContext.current

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        // 4 个 Tab 页全部预组合（beyondViewportPageCount = 3 覆盖全部剩余页），保持 Tab 页常驻
        beyondViewportPageCount = 3
    ) { page ->
        val isActive = pagerState.currentPage == page
        // 页面级 LifecycleOwner：非活跃页降至 STARTED，
        // collectAsStateWithLifecycle 自动暂停；collectWhenActive 不受影响（独立于生命周期）
        val pageLifecycleOwner = rememberPageLifecycleOwner(isActive)
        CompositionLocalProvider(LocalLifecycleOwner provides pageLifecycleOwner) {
            when (page) {
                0 -> {
                    val onToggleFloatingWindowStable = remember(onToggleFloatingWindow) { onToggleFloatingWindow }
                    val onNavigateToDevices = remember(context) { { context.launchDestination(Destination.Devices) } }
                    val onEnterFullScreen = remember(context) { { context.launchDestination(Destination.Fullscreen) } }
                    HomeScreen(
                        viewModel = viewModel,
                        isActive = isActive,
                        onToggleFloatingWindow = onToggleFloatingWindowStable,
                        onNavigateToDevices = onNavigateToDevices,
                        onEnterFullScreen = onEnterFullScreen
                    )
                }
                1 -> {
                    val onChart = remember(context) { { sessionId: Long -> context.launchDestination(Destination.Chart(sessionId)) } }
                    HistoryScreen(
                        onNavigateBack = {},
                        onNavigateToChart = onChart,
                        isInTab = true,
                        isActive = isActive
                    )
                }
                2 -> {
                    FavoriteDevicesScreen(
                        onNavigateBack = {},
                        isInTab = true,
                        isActive = isActive
                    )
                }
                3 -> {
                    val onSettingsNavigate = remember(context) {
                        { route: String ->
                            val destination = Destination.of(route)
                            if (destination != null) context.launchDestination(destination)
                        }
                    }
                    val showToast = remember(context) { { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() } }
                    SettingsScreen(
                        isActive = isActive,
                        onNavigate = onSettingsNavigate,
                        onOpenExternal = onOpenExternal,
                        showToast = showToast
                    )
                }
            }
        }
    }
}
