package com.github.heartratemonitor_compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.github.heartratemonitor_compose.service.KillStateSaver
import com.github.heartratemonitor_compose.ui.main.MainViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun AppLifecycleEffects(
    mainViewModel: MainViewModel,
    killStateSaver: KillStateSaver,
    currentTab: Screen
) {
    // rememberUpdatedState 确保 LaunchedEffect(Unit) 内的 collect 始终读取最新值。
    val currentTabState by rememberUpdatedState(currentTab)

    val pushSnapshot = remember(mainViewModel) {
        { tab: String ->
            val device = mainViewModel.uiState.value.connectedDevice
            // ⚠️ 反直觉设计：copy 保留 isFullScreen——全屏标志由 FullscreenActivity 维护，
            // 此处后台 collect 若整包覆写会把 true 打回 false 导致冷启动恢复失效。
            killStateSaver.updateSnapshot(
                killStateSaver.currentSnapshot.copy(
                    tab = tab,
                    connectedDeviceId = device?.id,
                    connectedDeviceName = device?.name
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.uiState.map { it.connectedDevice }.distinctUntilChanged().collect {
            pushSnapshot(currentTabState.route)
        }
    }

    LaunchedEffect(currentTab) {
        pushSnapshot(currentTab.route)
    }
}
