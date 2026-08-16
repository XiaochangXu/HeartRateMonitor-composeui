package com.github.heartratemonitor_compose.ui

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.github.heartratemonitor_compose.ui.favorite.FavoriteDevicesScreen
import com.github.heartratemonitor_compose.ui.history.HistoryScreen
import com.github.heartratemonitor_compose.ui.main.HomeScreen
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import com.github.heartratemonitor_compose.ui.settings.SettingsScreen

@Composable
fun AppTabPager(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    pagerState: PagerState,
    isOnTab: Boolean,
    onToggleFloatingWindow: () -> Unit,
    onEnterFullScreen: () -> Unit,
    safeNavigate: (String) -> Unit,
    onOpenExternal: (android.content.Intent) -> Unit
) {
    val context = LocalContext.current

    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        beyondViewportPageCount = 3,
        userScrollEnabled = isOnTab
    ) { page ->
        val isActive = isOnTab && pagerState.currentPage == page
        when (page) {
            0 -> {
                val onToggleFloatingWindowStable = remember(onToggleFloatingWindow) { onToggleFloatingWindow }
                val onNavigateToDevices = remember(safeNavigate) { { safeNavigate(Screen.Devices.route) } }
                val onEnterFullScreenStable = remember { onEnterFullScreen }
                HomeScreen(
                    viewModel = viewModel,
                    isActive = isActive,
                    onToggleFloatingWindow = onToggleFloatingWindowStable,
                    onNavigateToDevices = onNavigateToDevices,
                    onEnterFullScreen = onEnterFullScreenStable
                )
            }
            1 -> {
                val onChart = remember(safeNavigate) { { sessionId: Long -> safeNavigate(Screen.Chart.createRoute(sessionId)) } }
                HistoryScreen(
                    onNavigateBack = {},
                    onNavigateToChart = onChart,
                    isInTab = true
                )
            }
            2 -> {
                FavoriteDevicesScreen(
                    onNavigateBack = {},
                    isInTab = true
                )
            }
            3 -> {
                val onSettingsNavigate = remember(safeNavigate) { { route: String -> safeNavigate(route.toScreenRoute()) } }
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
