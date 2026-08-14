package com.github.heartratemonitor_compose.ui

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
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
import androidx.navigation.NavHostController

/**
 * NavHost 二级页面路由表。
 *
 * 覆盖在 Tab 层和导航栏之上，管理所有二级页面的导航与转场动画。
 * 转场动画分两类：
 * 1. Tab→二级 / 二级→Tab：placeholder 透明，旧页面 slideOut 整页滑出（被新页面覆盖）
 * 2. 二级→二级：旧页面作为"原背景"小幅左移，新页面从右滑入覆盖；
 *    返回时旧页面小幅右移，新页面从左滑入
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    safePopBack: () -> Unit,
    safeNavigate: (String) -> Unit,
    settings: SettingsRepository,
    mainViewModel: MainViewModel,
    onOpenExternal: (android.content.Intent) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = TAB_PLACEHOLDER,
        modifier = modifier.fillMaxSize(),
        enterTransition = {
            slideInHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth }
        },
        exitTransition = {
            val fromSecondary = initialState.destination.route != TAB_PLACEHOLDER
            val toSecondary = targetState.destination.route != TAB_PLACEHOLDER
            if (fromSecondary && toSecondary) {
                // 二级→二级：旧页面小幅左移
                slideOutHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> -(fullWidth * BACKGROUND_PARALLAX_RATIO).toInt() }
            } else {
                slideOutHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth }
            }
        },
        popEnterTransition = {
            val fromSecondary = initialState.destination.route != TAB_PLACEHOLDER
            val toSecondary = targetState.destination.route != TAB_PLACEHOLDER
            if (fromSecondary && toSecondary) {
                // 二级→二级返回：旧页面从左侧视差位置移回
                slideInHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> -(fullWidth * BACKGROUND_PARALLAX_RATIO).toInt() }
            } else {
                slideInHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> -fullWidth }
            }
        },
        popExitTransition = {
            // 退出时统一整页右滑出（揭开覆盖，露出下方页面），与 Tab→二级 返回一致
            slideOutHorizontally(tween(SECONDARY_SLIDE_DURATION, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth }
        }
    ) {
        composable(TAB_PLACEHOLDER) { /* 透明占位，Tab 层在下方可见 */ }
        composable(
            route = Screen.Chart.route,
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            val onBack = remember(safePopBack) { { safePopBack() } }
            ChartScreen(
                sessionId = sessionId,
                onNavigateBack = onBack
            )
        }
        composable(Screen.Alarm.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            HeartRateAlarmScreen(onNavigateBack = onBack)
        }
        composable(Screen.Server.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            ServerScreen(onNavigateBack = onBack, settings = settings)
        }
        composable(Screen.Webhook.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            WebhookScreen(onNavigateBack = onBack)
        }
        composable(Screen.LanTransfer.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            LanTransferScreen(onNavigateBack = onBack, settings = settings)
        }
        composable(Screen.FairMemory.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            FairMemoryScreen(onNavigateBack = onBack)
        }
        composable(Screen.Theme.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            ThemeSettingsScreen(onNavigateBack = onBack)
        }
        composable(Screen.NavStyle.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            NavStyleScreen(onNavigateBack = onBack)
        }
        composable(Screen.Devices.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            DevicesScreen(viewModel = mainViewModel, onNavigateBack = onBack)
        }
        composable(Screen.FullscreenSound.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            FullscreenSoundScreen(settings = settings, onNavigateBack = onBack)
        }
        composable(Screen.License.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            LicenseScreen(onNavigateBack = onBack)
        }
        composable(Screen.Privacy.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            PrivacyScreen(onNavigateBack = onBack)
        }
        composable(Screen.AboutDetails.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            val onDetailsNavigate = remember(safeNavigate) { { route: String -> safeNavigate(route.toScreenRoute()) } }
            val showToast = remember(context) { { message: String -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() } }
            AboutDetailsScreen(
                onNavigate = onDetailsNavigate,
                onNavigateBack = onBack,
                onOpenExternal = onOpenExternal,
                showToast = showToast
            )
        }
        composable(Screen.FunctionSettings.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            FunctionSettingsScreen(settings = settings, onNavigateBack = onBack)
        }
        composable(Screen.StatusBarSettings.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            StatusBarSettingsScreen(settings = settings, onNavigateBack = onBack)
        }
        composable(Screen.FloatingWindowSettings.route) {
            val onBack = remember(safePopBack) { { safePopBack() } }
            FloatingWindowSettingsScreen(settings = settings, onNavigateBack = onBack)
        }
    }
}
