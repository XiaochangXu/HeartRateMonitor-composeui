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

/**
 * [killStateSaver] 由 MainActivity 注入后下发（Phase 7 起不再经 AppContainerExt）。
 *
 * 进程恢复逻辑（KillStateSaver.read → pagerState.scrollToPage）已随 pagerState
 * 移入 TabRoot 场景（AppTabHost），此处只负责快照上报。
 */
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
    // rememberUpdatedState 确保 LaunchedEffect(Unit) 内的 collect 始终读取最新值，
    // 避免闭包捕获首次参数值导致快照数据过时
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

    // 从单一 uiState 派生 + distinctUntilChanged，保持原 StateFlow 只在变化时发射的语义
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
