package com.github.heartratemonitor_compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.pager.PagerState
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.main.AppStatus
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * [killStateSaver] 由 MainActivity 注入后下发（Phase 7 起不再经 AppContainerExt）。
 */
@Composable
fun AppLifecycleEffects(
    mainViewModel: MainViewModel,
    killStateSaver: KillStateSaver,
    pagerState: PagerState,
    isFullScreenMode: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    isOnTab: Boolean,
    currentTab: Screen,
    lastKnownRoute: String
) {
    val scope = rememberCoroutineScope()

    // rememberUpdatedState 确保 LaunchedEffect(Unit) 内的 collect 始终读取最新值，
    // 避免闭包捕获首次参数值导致快照数据过时
    val currentFullScreen by rememberUpdatedState(isFullScreenMode)
    val currentRoute by rememberUpdatedState(lastKnownRoute)
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

    // 从单一 uiState 派生 + distinctUntilChanged，保持原 StateFlow 只在变化时发射的语义
    LaunchedEffect(Unit) {
        mainViewModel.uiState.map { it.connectedDevice }.distinctUntilChanged().collect {
            pushSnapshot(currentRoute, currentTabState.route, currentFullScreen)
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.uiState.map { it.appStatus }.distinctUntilChanged().collect { status ->
            if (status != AppStatus.CONNECTED && currentFullScreen) {
                currentOnFullScreenChange(false)
            }
        }
    }

    LaunchedEffect(Unit) {
        val saved = killStateSaver.read() ?: return@LaunchedEffect
        killStateSaver.clear()
        if (isOnTab) {
            val restoreIndex = when (saved.tab) {
                Screen.History.route -> 1
                Screen.Favorite.route -> 2
                Screen.Settings.route -> 3
                else -> 0
            }
            scope.launch { pagerState.scrollToPage(restoreIndex) }
            if (saved.isFullScreen && mainViewModel.uiState.value.appStatus == AppStatus.CONNECTED) {
                onFullScreenChange(true)
            }
        }
    }

    LaunchedEffect(currentTab, lastKnownRoute, isFullScreenMode) {
        pushSnapshot(lastKnownRoute, currentTab.route, isFullScreenMode)
    }
}
