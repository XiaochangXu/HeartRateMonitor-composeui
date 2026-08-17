package com.github.heartratemonitor_compose.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.feature.settings.R
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onOpenExternal: (Intent) -> Unit,
    showToast: (String) -> Unit = {},
    isActive: Boolean = true
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = {
            TopAppBar(
                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + 16.dp))
            GeneralSection(onNavigate)
            Spacer(Modifier.height(32.dp))
            IntegrationSection(onNavigate)
            Spacer(Modifier.height(32.dp))
            OverlaySettingsSection(onNavigate)
            Spacer(Modifier.height(32.dp))
            AboutSection(onNavigate)
                        Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
            StatusBarScrim()
        }
    }
}


@Composable
private fun GeneralSection(
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
    onNavigate: (String) -> Unit
) {
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
                leadingIcon = painterResource(com.github.heartratemonitor_compose.ui.widgets.R.drawable.ic_lan_transfer),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }
    }
}


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
                leadingIcon = painterResource(com.github.heartratemonitor_compose.service.R.drawable.ic_fair_memory),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }
    }
}
