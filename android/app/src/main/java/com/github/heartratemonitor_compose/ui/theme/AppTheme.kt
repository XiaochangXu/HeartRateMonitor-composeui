package com.github.heartratemonitor_compose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.github.heartratemonitor_compose.ui.theme.HeartRateMonitorMobileTheme

// Activity 与 Services 共用同一 ThemeState 实例（同进程），改主题后全 App 即时重配色。
// 依赖由 MainActivity 注入后下发。
@Composable
fun AppTheme(
    themeState: ThemeState,
    customSchemeCache: CustomSchemeCache,
    content: @Composable () -> Unit
) {
    val config by themeState.config.collectAsState()
    HeartRateMonitorMobileTheme(
        config = config,
        customSchemeCache = customSchemeCache,
        content = content
    )
}
