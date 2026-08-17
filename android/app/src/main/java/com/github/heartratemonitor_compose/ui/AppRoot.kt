package com.github.heartratemonitor_compose.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.rememberNavBackStack
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.main.FullScreenHeartRate
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.settings.ChangelogBottomSheet
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState

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
    // 当前 Tab 由 TabRoot 场景（AppTabHost）经 onCurrentTabChange 上报：
    // pagerState 在场景内创建，不在 AppRoot 持有
    var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }

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

    // Tab 页由 NavDisplay 栈底 TabRoot 场景渲染：栈大小 1 = 在 Tab 页
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
        isFullScreenMode = isFullScreenMode,
        onFullScreenChange = { isFullScreenMode = it },
        isOnTab = isOnTab,
        currentTab = currentTab,
        currentRoute = navBackStack.lastOrNull()?.toString()
    )

    val onOpenExternalStable = remember(onOpenExternal) { onOpenExternal }

    val changelogNotice by changelogNotifier.notice.collectAsStateWithLifecycle()

    // blur 需 API 31+，lens 需 API 33+，低版本库内部静默 no-op；
    // 液态玻璃采样层与导航条已随 Tab 场景移入 AppTabHost，
    // 二级页面完全展示后随场景销毁——不存在"常驻隐藏导航条"。
    val liquidGlassConfig by liquidGlassState.config.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // 导航架构：栈底 TabRoot 场景渲染 Tab 页（AppTabHost = AppTabPager + 底部导航条），
        // 二级页面压栈在其上
        AppNavHost(
            navBackStack = navBackStack,
            isOnTab = isOnTab,
            safePopBack = safePopBack,
            safeNavigate = safeNavigate,
            mainViewModel = mainViewModel,
            onToggleFloatingWindow = onToggleFloatingWindow,
            onEnterFullScreen = { isFullScreenMode = true },
            onOpenExternal = onOpenExternalStable,
            liquidGlassConfig = liquidGlassConfig,
            isFullScreenMode = isFullScreenMode,
            onCurrentTabChange = { currentTab = it },
            killStateSaver = killStateSaver
        )

        // 顶部渐变：状态栏区域（windowInsets 布局修饰符实时读取，冷启动 insets 就绪后自动重排）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f),
                        1f to Color.Transparent
                    )
                )
        )
        // 底部渐变：仅覆盖系统导航条 inset 区域，二级页面底部渐变由本层统一提供
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
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
