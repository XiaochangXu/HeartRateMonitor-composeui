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
import kotlinx.coroutines.launch

/**
 * 应用生命周期副作用：KILL 现场状态快照管理。
 *
 * 职责：
 * 1. 持续将当前路由、Tab、全屏状态、连接设备信息写入内存快照
 * 2. 应用启动时恢复上次 KILL 保存的 Tab 页和全屏状态
 * 3. 断开连接时自动退出全屏模式
 */
@Composable
fun AppLifecycleEffects(
    mainViewModel: MainViewModel,
    pagerState: PagerState,
    isFullScreenMode: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    isOnTab: Boolean,
    currentTab: Screen,
    lastKnownRoute: String
) {
    val scope = rememberCoroutineScope()

    // rememberUpdatedState 确保 LaunchedEffect(Unit) 内的 collect 始终读取最新值，
    // 避免因参数值被首次闭包捕获而导致快照数据过时
    val currentFullScreen by rememberUpdatedState(isFullScreenMode)
    val currentRoute by rememberUpdatedState(lastKnownRoute)
    val currentTabState by rememberUpdatedState(currentTab)
    val currentOnFullScreenChange by rememberUpdatedState(onFullScreenChange)

    val pushSnapshot = remember(mainViewModel) {
        { route: String, tab: String, fullscreen: Boolean ->
            val device = mainViewModel.connectedDevice.value
            KillStateSaver.updateSnapshot(
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

    // connectedDevice 仅用于 KILL 状态快照，用副作用订阅即可，无需在组合中读取
    LaunchedEffect(Unit) {
        mainViewModel.connectedDevice.collect {
            pushSnapshot(currentRoute, currentTabState.route, currentFullScreen)
        }
    }

    // 断开连接时自动退出全屏模式
    LaunchedEffect(Unit) {
        mainViewModel.appStatus.collect { status ->
            if (status != AppStatus.CONNECTED && currentFullScreen) {
                currentOnFullScreenChange(false)
            }
        }
    }

    // 应用启动时尝试恢复上次 KILL 保存的 Tab / 全屏状态（仅在 Tab 页时）
    LaunchedEffect(Unit) {
        val context = mainViewModel.getApplication<android.app.Application>()
        val saved = KillStateSaver.read(context) ?: return@LaunchedEffect
        KillStateSaver.clear(context)
        if (isOnTab) {
            // 恢复 Tab 索引：Home=0, History=1, Favorite=2, Settings=3
            val restoreIndex = when (saved.tab) {
                Screen.History.route -> 1
                Screen.Favorite.route -> 2
                Screen.Settings.route -> 3
                else -> 0
            }
            scope.launch { pagerState.scrollToPage(restoreIndex) }
            if (saved.isFullScreen && mainViewModel.appStatus.value == AppStatus.CONNECTED) {
                onFullScreenChange(true)
            }
        }
    }

    // 关键状态变化时更新内存快照
    LaunchedEffect(currentTab, lastKnownRoute, isFullScreenMode) {
        pushSnapshot(lastKnownRoute, currentTab.route, isFullScreenMode)
    }
}
