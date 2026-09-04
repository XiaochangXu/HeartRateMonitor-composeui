package com.github.heartratemonitor_compose.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.rememberNavBackStack
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.animation.LocalReducedMotion
import com.github.heartratemonitor_compose.ui.animation.rememberSystemReducedMotion
import com.github.heartratemonitor_compose.ui.main.FullScreenHeartRate
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.settings.ChangelogBottomSheet
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState

@Composable
fun AppRoot(
    changelogNotifier: ChangelogNotifier,
    liquidGlassState: LiquidGlassState,
    killStateSaver: KillStateSaver,
    onToggleFloatingWindow: () -> Unit,
    onOpenExternal: (Intent) -> Unit
) {
    val context = LocalContext.current
    val navBackStack = rememberNavBackStack(AppNavKey.TabRoot)
    val navGuard = rememberNavGuard()
    val safeNavigateInner = rememberSafeNavigate(navBackStack, navGuard)
    val safePopBackInner = rememberSafePopBack(navBackStack)

    val mainViewModel: MainViewModel = hiltViewModel()
    var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }

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

    val changelogNotice by changelogNotifier.notice.collectAsStateWithLifecycle()

    val reducedMotion = rememberSystemReducedMotion()

    val liquidGlassConfig by liquidGlassState.config.collectAsStateWithLifecycle()

    val navAnimationDisabled by liquidGlassState.navAnimationDisabledFlow.collectAsStateWithLifecycle()

    // 底部渐变需覆盖：系统导航条 inset + 悬浮应用导航条（FLOATING_NAV_HEIGHT + 边距）。
    // windowInsetsBottomHeight 仅等于系统导航条高度，无法覆盖应用导航条悬浮区域。
    val density = LocalDensity.current
    val navBarInsetPx = WindowInsets.navigationBars.getBottom(density)
    val bottomGradientHeightDp = with(density) {
        (navBarInsetPx + FLOATING_NAV_HEIGHT + FLOATING_NAV_BOTTOM_MARGIN).toDp()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
            AppNavHost(
                navBackStack = navBackStack,
                isOnTab = isOnTab,
                safePopBack = safePopBack,
                safeNavigate = safeNavigate,
                mainViewModel = mainViewModel,
                onToggleFloatingWindow = onToggleFloatingWindow,
                onEnterFullScreen = { isFullScreenMode = true },
                onOpenExternal = onOpenExternal,
                liquidGlassConfig = liquidGlassConfig,
                isFullScreenMode = isFullScreenMode,
                onCurrentTabChange = { currentTab = it },
                killStateSaver = killStateSaver,
                navAnimationDisabled = navAnimationDisabled
            )

            // 顶部渐变：状态栏区域
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
            // 底部渐变：覆盖系统导航条 inset + 悬浮应用导航条区域（仅 Tab 页有悬浮导航条需覆盖）。
            if (isOnTab) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomGradientHeightDp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f)
                            )
                        )
                )
            }

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
}
