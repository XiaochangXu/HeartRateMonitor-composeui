package com.github.heartratemonitor_compose.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunctionSettingsScreen(
    settings: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                        stringResource(R.string.function_settings),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.cd_back)
                                )
                            }
                        }
                    }
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
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(padding.calculateTopPadding() + 16.dp))

            // ── 分组 1：显示与记录 ──
            DisplayAndRecordGroup(settings)

           
            Spacer(Modifier.height(24.dp))

            // ── 分组 2：连接与后台 ──
            ConnectionAndBackgroundGroup(settings)

            Spacer(Modifier.height(64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
            StatusBarScrim()
        }
    }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DisplayAndRecordGroup(settings: SettingsRepository) {
    var isHistoryEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false)) }
    var isAnimationEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HEARTBEAT_ANIMATION_ENABLED, true)) }
    var isSpeedEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, false)) }

    val showWarningDialog = remember { mutableStateOf(false) }
    val showSpeedDialog = remember { mutableStateOf(false) }

    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SettingsGroupCard {
        SettingsItem(isFirst = true) {
            SettingsSwitch(
                checked = isHistoryEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        showWarningDialog.value = true
                    } else {
                        isHistoryEnabled = false
                        settings.setBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false)
                    }
                },
                title = stringResource(R.string.record_history),
                subtitle = stringResource(R.string.subtitle_record_history),
                leadingIcon = painterResource(R.drawable.ic_deployed_code_history),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            SettingsSwitch(
                checked = isAnimationEnabled,
                onCheckedChange = {
                    isAnimationEnabled = it
                    settings.setBoolean(PrefsKeys.HEARTBEAT_ANIMATION_ENABLED, it)
                },
                title = stringResource(R.string.heartbeat_animation),
                subtitle = stringResource(R.string.subtitle_heartbeat_animation),
                leadingIcon = painterResource(R.drawable.ic_animation),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(isLast = true) {
            SettingsSwitch(
                checked = isSpeedEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        showSpeedDialog.value = true
                    } else {
                        isSpeedEnabled = false
                        settings.setBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, false)
                    }
                },
                title = stringResource(R.string.display_speed_gps),
                subtitle = stringResource(R.string.subtitle_display_speed_gps),
                leadingIcon = painterResource(R.drawable.ic_speed),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }
    }

    if (showWarningDialog.value) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        LaunchedEffect(Unit) { sheetState.expand() }
        ModalBottomSheet(
            onDismissRequest = { showWarningDialog.value = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.performance_warning),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.history_warning_message),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showWarningDialog.value = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        settings.setBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, true)
                        isHistoryEnabled = true
                        showWarningDialog.value = false
                    }) { Text(stringResource(R.string.confirm)) }
                }
            }
        }
    }

    if (showSpeedDialog.value) {
        val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
        LaunchedEffect(Unit) { sheetState.expand() }
        ModalBottomSheet(
            onDismissRequest = { showSpeedDialog.value = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.enable_speed_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = stringResource(R.string.speed_gps_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showSpeedDialog.value = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        settings.setBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, true)
                        isSpeedEnabled = true
                        showSpeedDialog.value = false
                    }) { Text(stringResource(R.string.confirm)) }
                }
            }
        }
    }
}


@Composable
private fun ConnectionAndBackgroundGroup(settings: SettingsRepository) {
    var isHideFromRecents by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HIDE_FROM_RECENTS_ENABLED, false)) }
    var isAutoConnectEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.AUTO_CONNECT_ENABLED, false)) }
    var isAutoReconnectEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.AUTO_RECONNECT_ENABLED, true)) }
    var isScanFilterEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.SCAN_FILTER_ENABLED, false)) }

    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SettingsGroupCard {
        SettingsItem(isFirst = true) {
            SettingsSwitch(
                checked = isHideFromRecents,
                onCheckedChange = {
                    isHideFromRecents = it
                    settings.setBoolean(PrefsKeys.HIDE_FROM_RECENTS_ENABLED, it)
                },
                title = stringResource(R.string.hide_from_recents),
                subtitle = stringResource(R.string.subtitle_hide_from_recents),
                leadingIcon = painterResource(R.drawable.ic_hide_source),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            SettingsSwitch(
                checked = isAutoConnectEnabled,
                onCheckedChange = {
                    isAutoConnectEnabled = it
                    settings.setBoolean(PrefsKeys.AUTO_CONNECT_ENABLED, it)
                },
                title = stringResource(R.string.auto_connect_favorite),
                subtitle = stringResource(R.string.subtitle_auto_connect_favorite),
                leadingIcon = painterResource(R.drawable.ic_bluetooth_connected_symbol),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            SettingsSwitch(
                checked = isAutoReconnectEnabled,
                onCheckedChange = {
                    isAutoReconnectEnabled = it
                    settings.setBoolean(PrefsKeys.AUTO_RECONNECT_ENABLED, it)
                },
                title = stringResource(R.string.auto_reconnect),
                subtitle = stringResource(R.string.subtitle_auto_reconnect),
                leadingIcon = painterResource(R.drawable.ic_plug_connect),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(isLast = true) {
            SettingsSwitch(
                checked = isScanFilterEnabled,
                onCheckedChange = {
                    isScanFilterEnabled = it
                    settings.setBoolean(PrefsKeys.SCAN_FILTER_ENABLED, it)
                },
                title = stringResource(R.string.scan_filter),
                subtitle = stringResource(R.string.subtitle_scan_filter),
                leadingIcon = painterResource(R.drawable.ic_search_filter),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }
    }
}
