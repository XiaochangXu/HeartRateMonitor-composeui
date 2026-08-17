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
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SinglePaneSceneStrategy
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
        onBack = {
            // 照示例文件模式：NavDisplay 独占返回事件流，不用外层 BackHandler 拦截。
            // 栈底 TabRoot 时让系统接管（finish Activity），二级页面 pop。
            if (navBackStack.size > 1) {
                safePopBack()
            } else {
                (context as? android.app.Activity)?.finish()
            }
        },
        entryDecorators = listOfNotNull(
            rememberSaveableStateHolderNavEntryDecorator(),
            viewModelStoreDecorator
        ),
        // 显式传入 sceneStrategies 命名参数，匹配泛型重载 NavDisplay<T>
        sceneStrategies = listOf(SinglePaneSceneStrategy()),
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
        entryProvider = entryProvider {
            // 栈底 = Tab 页宿主：Pager + 底部导航条由 NavDisplay 渲染（随场景生灭）
            entry<AppNavKey.TabRoot> {
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
            entry<AppNavKey.Chart> { key ->
                ChartScreen(
                    sessionId = key.sessionId,
                    onNavigateBack = { safePopBack() }
                )
            }
            entry<AppNavKey.Alarm> {
                HeartRateAlarmScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.Server> {
                ServerScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.Webhook> {
                WebhookScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.LanTransfer> {
                LanTransferScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.FairMemory> {
                FairMemoryScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.Theme> {
                ThemeSettingsScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.NavStyle> {
                NavStyleScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.Devices> {
                DevicesScreen(viewModel = mainViewModel, onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.FullscreenSound> {
                FullscreenSoundScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.License> {
                LicenseScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.Privacy> {
                PrivacyScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.AboutDetails> {
                val onDetailsNavigate = remember(safeNavigate) {
                    { route: String -> safeNavigate(appNavKeyOf(route.toScreenRoute())) }
                }
                val showToast = remember(context) {
                    { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
                }
                AboutDetailsScreen(
                    onNavigate = onDetailsNavigate,
                    onNavigateBack = { safePopBack() },
                    onOpenExternal = onOpenExternal,
                    showToast = showToast
                )
            }
            entry<AppNavKey.FunctionSettings> {
                FunctionSettingsScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.StatusBarSettings> {
                StatusBarSettingsScreen(onNavigateBack = { safePopBack() })
            }
            entry<AppNavKey.FloatingWindowSettings> {
                FloatingWindowSettingsScreen(onNavigateBack = { safePopBack() })
            }
        }
    )
}
