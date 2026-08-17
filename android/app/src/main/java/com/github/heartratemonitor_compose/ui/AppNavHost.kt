package com.github.heartratemonitor_compose.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.alarm.HeartRateAlarmScreen
import com.github.heartratemonitor_compose.ui.history.ChartScreen
import com.github.heartratemonitor_compose.ui.main.DevicesScreen
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.server.LanTransferScreen
import com.github.heartratemonitor_compose.ui.server.ServerScreen
import com.github.heartratemonitor_compose.ui.settings.AboutDetailsScreen
import com.github.heartratemonitor_compose.ui.settings.FairMemoryScreen
import com.github.heartratemonitor_compose.ui.settings.FloatingWindowSettingsScreen
import com.github.heartratemonitor_compose.ui.settings.FullscreenSoundScreen
import com.github.heartratemonitor_compose.ui.settings.FunctionSettingsScreen
import com.github.heartratemonitor_compose.ui.settings.LicenseScreen
import com.github.heartratemonitor_compose.ui.settings.NavStyleScreen
import com.github.heartratemonitor_compose.ui.settings.PrivacyScreen
import com.github.heartratemonitor_compose.ui.settings.StatusBarSettingsScreen
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassConfig
import com.github.heartratemonitor_compose.ui.theme.ThemeSettingsScreen
import com.github.heartratemonitor_compose.ui.webhook.WebhookScreen

/**
 * navigation3 二级页面容器（HorizontalPager + Navigation3 架构）：
 * - 栈底 [AppNavKey.TabRoot] 渲染 Tab 页宿主 [AppTabHost]（AppTabPager + 底部导航条），
 *   二级页面压栈在其上；二级页面完全展示后 Tab 场景按 SinglePaneScene 语义离开组合
 *   （页面状态经 SaveableStateHolder / entry 级 ViewModelStore / 场景内 pagerState 恢复）；
 * - 转场动画：
 *   进入——新卡片从右侧整屏滑入（从右往左），旧场景向右让位 1/4 并淡出；
 *   退出——下层场景从左侧 1/4 处滑入（从左往右），被弹出的卡片缩放至 0.8 并淡出；
 *   位移 480ms（FastOutSlowInEasing），淡入淡出 360ms（LinearOutSlowInEasing）；
 *   预测性返回与 pop 同向（手势驱动，默认时长）。
 */
@Composable
fun AppNavHost(
    navBackStack: NavBackStack<NavKey>,
    isOnTab: Boolean,
    safePopBack: () -> Unit,
    safeNavigate: (AppNavKey) -> Unit,
    mainViewModel: MainViewModel,
    onToggleFloatingWindow: () -> Unit,
    onEnterFullScreen: () -> Unit,
    onOpenExternal: (android.content.Intent) -> Unit,
    liquidGlassConfig: LiquidGlassConfig,
    isFullScreenMode: Boolean,
    onCurrentTabChange: (Screen) -> Unit,
    killStateSaver: KillStateSaver,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // NavEntry 级 ViewModelStore：让 hiltViewModel() 在 navigation3 目的地内可用
    val viewModelStoreOwner = LocalViewModelStoreOwner.current
    val viewModelStoreDecorator = if (viewModelStoreOwner != null) {
        rememberViewModelStoreNavEntryDecorator<NavKey>(viewModelStoreOwner)
    } else {
        null
    }

    NavDisplay(
        backStack = navBackStack,
        // 注意：不能给 NavDisplay 加不透明背景——它在 AppRoot 的 Z 序最上层，
        // 会盖住下方的底部导航；TabRoot 场景透明，露出 AppRoot 背景
        modifier = modifier.fillMaxSize(),
        onBack = { safePopBack() },
        entryDecorators = listOfNotNull(
            rememberSaveableStateHolderNavEntryDecorator(),
            viewModelStoreDecorator
        ),
        // ── 转场动画 ──
        transitionSpec = {
            (slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> fullWidth }
            ) + fadeIn(
                animationSpec = tween(durationMillis = 360, easing = LinearOutSlowInEasing)
            )) togetherWith (slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                targetOffset = { fullWidth -> fullWidth / 4 }
            ) + fadeOut(
                animationSpec = tween(durationMillis = 360, easing = LinearOutSlowInEasing)
            ))
        },
        popTransitionSpec = {
            (slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> -fullWidth / 4 }
            ) + fadeIn(
                animationSpec = tween(durationMillis = 360, easing = LinearOutSlowInEasing)
            )) togetherWith (scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 360)))
        },
        predictivePopTransitionSpec = { _ ->
            (slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> -fullWidth / 4 }
            ) + fadeIn(animationSpec = tween(easing = LinearOutSlowInEasing))) togetherWith (scaleOut(
                targetScale = 0.8f,
                animationSpec = tween(easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween()))
        },
        // ──────────────────────────────────────────────────────────────────────────────
    ) { key ->
        when (key) {
            // 栈底 = Tab 页宿主：Pager + 底部导航条由 NavDisplay 渲染（随场景生灭）
            AppNavKey.TabRoot -> NavEntry(key) {
                AppTabHost(
                    viewModel = mainViewModel,
                    isOnTab = isOnTab,
                    onToggleFloatingWindow = onToggleFloatingWindow,
                    onEnterFullScreen = onEnterFullScreen,
                    safeNavigate = safeNavigate,
                    onOpenExternal = onOpenExternal,
                    liquidGlassConfig = liquidGlassConfig,
                    isFullScreenMode = isFullScreenMode,
                    onCurrentTabChange = onCurrentTabChange,
                    killStateSaver = killStateSaver
                )
            }
            is AppNavKey.Chart -> NavEntry(key) {
                ChartScreen(
                    sessionId = key.sessionId,
                    onNavigateBack = { safePopBack() }
                )
            }
            AppNavKey.Alarm -> NavEntry(key) { HeartRateAlarmScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.Server -> NavEntry(key) { ServerScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.Webhook -> NavEntry(key) { WebhookScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.LanTransfer -> NavEntry(key) { LanTransferScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.FairMemory -> NavEntry(key) { FairMemoryScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.Theme -> NavEntry(key) { ThemeSettingsScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.NavStyle -> NavEntry(key) { NavStyleScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.Devices -> NavEntry(key) {
                DevicesScreen(viewModel = mainViewModel, onNavigateBack = { safePopBack() })
            }
            AppNavKey.FullscreenSound -> NavEntry(key) { FullscreenSoundScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.License -> NavEntry(key) { LicenseScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.Privacy -> NavEntry(key) { PrivacyScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.AboutDetails -> NavEntry(key) {
                val onDetailsNavigate = remember(safeNavigate) { { route: String -> safeNavigate(appNavKeyOf(route.toScreenRoute())) } }
                val showToast = remember(context) { { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() } }
                AboutDetailsScreen(
                    onNavigate = onDetailsNavigate,
                    onNavigateBack = { safePopBack() },
                    onOpenExternal = onOpenExternal,
                    showToast = showToast
                )
            }
            AppNavKey.FunctionSettings -> NavEntry(key) { FunctionSettingsScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.StatusBarSettings -> NavEntry(key) { StatusBarSettingsScreen(onNavigateBack = { safePopBack() }) }
            AppNavKey.FloatingWindowSettings -> NavEntry(key) { FloatingWindowSettingsScreen(onNavigateBack = { safePopBack() }) }
            else -> NavEntry(key) { /* 未知键不应出现 */ }
        }
    }
}
