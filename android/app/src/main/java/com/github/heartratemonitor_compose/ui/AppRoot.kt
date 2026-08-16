package com.github.heartratemonitor_compose.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
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
    // navigation3：返回栈永不为空，栈底固定 TabRoot 占位（Tab 页 = 栈大小 1），
    // 二级页面在其上压栈/出栈
    val navBackStack = rememberNavBackStack(AppNavKey.TabRoot)
    val navGuard = rememberNavGuard()
    val safeNavigateInner = rememberSafeNavigate(navBackStack, navGuard)
    val safePopBackInner = rememberSafePopBack(navBackStack)

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

    // Tab 页在 NavDisplay 外部管理：栈底仅剩 TabRoot 占位 = 在 Tab 页
    val isOnTab = navBackStack.size == 1

    val safeNavigate = remember(safeNavigateInner) {
        { key: AppNavKey -> safeNavigateInner(key) }
    }
    val safePopBack = remember(safePopBackInner) {
        { safePopBackInner() }
    }

    AppLifecycleEffects(
        mainViewModel = mainViewModel,
        killStateSaver = killStateSaver,
        pagerState = pagerState,
        isFullScreenMode = isFullScreenMode,
        onFullScreenChange = { isFullScreenMode = it },
        isOnTab = isOnTab,
        currentTab = currentTab,
        currentRoute = navBackStack.lastOrNull()?.toString()
    )

    // 在二级页面拦截返回键，Tab 页让系统处理（退出应用）
    BackHandler(enabled = !isOnTab) {
        safePopBack()
    }

    val navigateToTab = remember(navBackStack, currentTab, isOnTab, scope) {
        tab@{ pageIndex: Int ->
            val newTab = tabScreenAt(pageIndex)
            if (currentTab == newTab && isOnTab) return@tab
            if (!isOnTab) {
                // 清空二级页返回栈（保留栈底 TabRoot 占位，等价于原 popBackStack(TAB_PLACEHOLDER)）
                navBackStack.removeAll { it != AppNavKey.TabRoot }
            }
            scope.launch { pagerState.animateScrollToPage(pageIndex) }
        }
    }

    val onOpenExternalStable = remember(onOpenExternal) { onOpenExternal }

    // 稳定化 lambda 引用：AppRoot 每次重组（导航/路由变化）若新建 lambda，
    // 会触发 AppBottomNavBar 内部 remember(selectedTabIndex) 重建与 LaunchedEffect 重启
    //（值相同，行为不变，纯多余重组）
    val selectedPage = remember(pagerState) { { pagerState.targetPage } }
    val onTabSelected = remember(navigateToTab) { { index: Int -> navigateToTab(index) } }

    val changelogNotice by changelogNotifier.notice.collectAsStateWithLifecycle()

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
    val liquidBackdropBgColor = MaterialTheme.colorScheme.surfaceContainer
    val liquidBackdropOnDraw: androidx.compose.ui.graphics.drawscope.ContentDrawScope.() -> Unit =
        remember(liquidBackdropBgColor) {
            { drawRect(liquidBackdropBgColor); drawContent() }
        }
    val liquidBackdrop = rememberLayerBackdrop(onDraw = liquidBackdropOnDraw)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // 玻璃层：录制 NavDisplay 内容（Tab 页 + 二级页）供玻璃导航条采样
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (liquidGlassEnabled) Modifier.layerBackdrop(liquidBackdrop) else Modifier)
        ) {
            AppNavHost(
                navBackStack = navBackStack,
                pagerState = pagerState,
                isOnTab = isOnTab,
                safePopBack = safePopBack,
                safeNavigate = safeNavigate,
                mainViewModel = mainViewModel,
                onToggleFloatingWindow = onToggleFloatingWindow,
                onEnterFullScreen = { isFullScreenMode = true },
                onOpenExternal = onOpenExternalStable
            )
        }

        AppBottomNavBar(
            liquidGlassEnabled = liquidGlassEnabled,
            liquidBackdrop = liquidBackdrop,
            liquidGlassConfig = liquidGlassConfig,
            isFullScreenMode = isFullScreenMode,
            isOnTab = isOnTab,
            selectedPage = selectedPage,
            onTabSelected = onTabSelected,
            navBarBottomInset = navBarBottomInset,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 底部：仅覆盖系统导航条 inset 区域，二级页面底部渐变由本层统一提供
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarTopInset * 1.2f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
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
                        1f to MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
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
