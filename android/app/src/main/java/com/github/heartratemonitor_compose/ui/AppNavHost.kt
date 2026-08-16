package com.github.heartratemonitor_compose.ui

import android.graphics.Path
import android.view.animation.PathInterpolator
import android.widget.Toast
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
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
import com.github.heartratemonitor_compose.ui.theme.ThemeSettingsScreen
import com.github.heartratemonitor_compose.ui.webhook.WebhookScreen

/**
 * navigation3 二级页面容器：
 * - [NavDisplay] + [NavEntry] 承载返回栈，栈底 TabRoot 渲染 Tab 页；
 * - 转场动画：700ms 半屏叠层 slide + 延迟 fade + M3 emphasized 缓动，
 *   含预测性返回手势动画（predictivePopTransitionSpec）。
 */
@Composable
fun AppNavHost(
    navBackStack: NavBackStack<NavKey>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    isOnTab: Boolean,
    safePopBack: () -> Unit,
    safeNavigate: (AppNavKey) -> Unit,
    mainViewModel: MainViewModel,
    onToggleFloatingWindow: () -> Unit,
    onEnterFullScreen: () -> Unit,
    onOpenExternal: (android.content.Intent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val transitionSpec: AnimatedContentTransitionScope<*>.() -> ContentTransform = remember {
        {
            ContentTransform(
                targetContentEnter =
                    slideInHorizontally(
                        tween(700, 0, EaseEmphasized)
                    ) { it / 2 } + fadeIn(
                        tween(500, 200, EaseEmphasizedDecelerate)
                    ),
                initialContentExit =
                    slideOutHorizontally(
                        tween(700, 50, EaseEmphasized)
                    ) { -it / 2 } + fadeOut(
                        tween(200, 0, EaseEmphasizedAccelerate)
                    )
            )
        }
    }

    val predictivePopTransitionSpec: AnimatedContentTransitionScope<*>.(Int) -> ContentTransform = remember {
        { _ ->
            ContentTransform(
                targetContentEnter =
                    slideInHorizontally(
                        tween(700, 0, EaseEmphasized)
                    ) { -it / 2 } + fadeIn(
                        tween(500, 200, EaseEmphasizedDecelerate)
                    ),
                initialContentExit =
                    slideOutHorizontally(
                        tween(700, 0, EaseEmphasized)
                    ) { it / 2 } + fadeOut(
                        tween(200, 0, EaseEmphasizedAccelerate)
                    )
            )
        }
    }

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
        // 会盖住下方的 Tab 页（AppTabPager）与底部导航；TabRoot 占位场景透明，
        // 露出 AppRoot 的 surfaceContainer 背景；二级页面自带 Scaffold 背景
        modifier = modifier.fillMaxSize(),
        onBack = { safePopBack() },
        entryDecorators = listOfNotNull(
            rememberSaveableStateHolderNavEntryDecorator(),
            viewModelStoreDecorator
        ),
        transitionSpec = transitionSpec,
        popTransitionSpec = transitionSpec,
        predictivePopTransitionSpec = predictivePopTransitionSpec
    ) { key ->
        when (key) {
            // 栈底占位 = Tab 页（Tab 页在 NavDisplay 栈内，
            // 转场时作为旧场景参与半屏滑出/滑入动画）
            AppNavKey.TabRoot -> NavEntry(key) {
                AppTabPager(
                    viewModel = mainViewModel,
                    pagerState = pagerState,
                    isOnTab = isOnTab,
                    onToggleFloatingWindow = onToggleFloatingWindow,
                    onEnterFullScreen = onEnterFullScreen,
                    safeNavigate = safeNavigate,
                    onOpenExternal = onOpenExternal
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

// ──────────────────────────────────────────────
// M3 emphasized 缓动
// ──────────────────────────────────────────────

private val EaseEmphasized: Easing = Easing { t -> EaseEmphasizedInterpolator.getInterpolation(t) }
private val EaseEmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EaseEmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private val EaseEmphasizedInterpolator =
    PathInterpolator(
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        }
    )
