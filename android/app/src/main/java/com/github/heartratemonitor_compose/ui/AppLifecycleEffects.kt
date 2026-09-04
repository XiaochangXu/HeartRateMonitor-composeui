package com.github.heartratemonitor_compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.main.AppStatus
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun AppLifecycleEffects(
    mainViewModel: MainViewModel,
    killStateSaver: KillStateSaver,
    isFullScreenMode: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    isOnTab: Boolean,
    currentTab: Screen,
    currentRoute: String?
) {
    // rememberUpdatedState 确保 LaunchedEffect(Unit) 内的 collect 始终读取最新值。
    val currentFullScreen by rememberUpdatedState(isFullScreenMode)
    val currentRouteState by rememberUpdatedState(currentRoute)
    val currentTabState by rememberUpdatedState(currentTab)
    val currentOnFullScreenChange by rememberUpdatedState(onFullScreenChange)

    val pushSnapshot = remember(mainViewModel) {
        { route: String, tab: String, fullscreen: Boolean ->
            val device = mainViewModel.uiState.value.connectedDevice
            killStateSaver.updateSnapshot(
                KillStateSaver.Snapshot(
                    route = route,
                    tab = tab,
                    isFullScreen = fullscreen,
                    connectedDeviceId = device?.id,
                    connectedDeviceName = device?.name
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.uiState.map { it.connectedDevice }.distinctUntilChanged().collect {
            pushSnapshot(currentRouteState ?: "", currentTabState.route, currentFullScreen)
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.uiState.map { it.appStatus }.distinctUntilChanged().collect { status ->
            if (status != AppStatus.CONNECTED && currentFullScreen) {
                currentOnFullScreenChange(false)
            }
        }
    }

    LaunchedEffect(currentTab, currentRoute, isFullScreenMode) {
        pushSnapshot(currentRoute ?: "", currentTab.route, isFullScreenMode)
    }
}
