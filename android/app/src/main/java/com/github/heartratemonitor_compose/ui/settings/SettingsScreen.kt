package com.github.heartratemonitor_compose.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.repository.SettingsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    onNavigate: (String) -> Unit,
    onOpenExternal: (Intent) -> Unit,
    showToast: (String) -> Unit = {},
    isActive: Boolean = true
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 仅应用顶部 padding（TopAppBar 高度），底部不应用 padding 让内容延伸到屏幕底部
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            GeneralSection(settings, onNavigate)
            Spacer(Modifier.height(32.dp))
            IntegrationSection(onNavigate, settings)
            Spacer(Modifier.height(32.dp))
            OverlaySettingsSection(onNavigate)
            Spacer(Modifier.height(32.dp))
            AboutSection(onNavigate)
            // 内容延伸到屏幕底部
            Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

/**
 * 常规分组卡片：功能设置 + 全屏模式 + 主题设置 + 导航样式 + 心率预警。
 * 其中功能设置点击进入二级页面，其余为原有二级页面入口。
 */
@Composable
private fun GeneralSection(
    settings: SettingsRepository,
    onNavigate: (String) -> Unit
) {
    // Icon Container
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SettingsGroupCard {
        // 功能设置（点击进入二级页面）
        SettingsItem(isFirst = true, onClick = { onNavigate("function_settings") }) {
            SettingsLink(
                title = stringResource(R.string.function_settings),
                subtitle = stringResource(R.string.subtitle_function_settings),
                leadingIcon = painterResource(R.drawable.ic_function_settings),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(onClick = { onNavigate("theme") }) {
            SettingsLink(title = stringResource(R.string.theme_settings), subtitle = stringResource(R.string.subtitle_theme_settings),
                leadingIcon = painterResource(R.drawable.ic_text_color),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }

        SettingsItem(onClick = { onNavigate("fullscreen_sound") }) {
            SettingsLink(
                title = stringResource(R.string.fullscreen_sound),
                subtitle = stringResource(R.string.subtitle_fullscreen_sound),
                leadingIcon = painterResource(R.drawable.ic_fullscreen_sound),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(onClick = { onNavigate("nav_style") }) {
            SettingsLink(title = stringResource(R.string.nav_style), subtitle = stringResource(R.string.subtitle_nav_style),
                leadingIcon = painterResource(R.drawable.ic_nav_options),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }

        SettingsItem(isLast = true, onClick = { onNavigate("alarm") }) {
            SettingsLink(title = stringResource(R.string.heart_rate_alarm), subtitle = stringResource(R.string.subtitle_heart_rate_alarm),
                leadingIcon = painterResource(R.drawable.ic_warning),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }
    }
}

@Composable
private fun IntegrationSection(
    onNavigate: (String) -> Unit,
    settings: SettingsRepository
) {
    // Icon Container: 集成功能使用蓝色系（与常规功能统一）
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SettingsGroupCard {
        SettingsItem(isFirst = true, onClick = { onNavigate("server") }) {
            SettingsLink(title = stringResource(R.string.http_websocket_server), subtitle = stringResource(R.string.subtitle_http_websocket_server),
                leadingIcon = painterResource(R.drawable.ic_http_websocket),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }

        SettingsItem(onClick = { onNavigate("webhook") }) {
            SettingsLink(title = stringResource(R.string.webhook_settings), subtitle = stringResource(R.string.subtitle_webhook_settings),
                leadingIcon = painterResource(R.drawable.ic_webhook),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }

        SettingsItem(isLast = true, onClick = { onNavigate("lan_transfer") }) {
            SettingsLink(title = stringResource(R.string.lan_transfer), subtitle = stringResource(R.string.subtitle_lan_transfer),
                leadingIcon = painterResource(R.drawable.ic_lan_transfer),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }
    }
}

/**
 * 覆盖层设置分组卡片：状态栏设置 + 悬浮窗设置，均点击进入二级页面。
 */
@Composable
private fun OverlaySettingsSection(
    onNavigate: (String) -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SettingsGroupCard {
        SettingsItem(isFirst = true, onClick = { onNavigate("status_bar_settings") }) {
            SettingsLink(
                title = stringResource(R.string.status_bar_settings),
                subtitle = stringResource(R.string.subtitle_status_bar_settings),
                leadingIcon = painterResource(R.drawable.ic_status_bar_settings),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(isLast = true, onClick = { onNavigate("floating_window_settings") }) {
            SettingsLink(
                title = stringResource(R.string.floating_window_settings),
                subtitle = stringResource(R.string.subtitle_floating_window_settings),
                leadingIcon = painterResource(R.drawable.ic_floating_window_settings),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }
    }
}

@Composable
private fun AboutSection(
    onNavigate: (String) -> Unit
) {
    // Icon Container: 关于使用蓝色系（与常规功能统一）
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SettingsGroupCard {
        SettingsItem(isFirst = true, onClick = { onNavigate("about_details") }) {
            SettingsLink(
                title = stringResource(R.string.about_details),
                subtitle = stringResource(R.string.subtitle_about_details),
                leadingIcon = painterResource(R.drawable.ic_version),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(isLast = true, onClick = { onNavigate("fair_memory") }) {
            SettingsLink(title = stringResource(R.string.fair_memory), subtitle = stringResource(R.string.subtitle_fair_memory),
                leadingIcon = painterResource(R.drawable.ic_fair_memory),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }
    }
}
