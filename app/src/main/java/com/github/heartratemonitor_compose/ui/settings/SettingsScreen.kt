package com.github.heartratemonitor_compose.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.system.OverlayPermissionProvider
import com.github.heartratemonitor_compose.service.ServiceController
import com.github.heartratemonitor_compose.ui.main.MainActivity


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    onNavigate: (String) -> Unit,
    onOpenExternal: (Intent) -> Unit,
    showToast: (String) -> Unit = {},
    isActive: Boolean = true
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Compose ColorPickerDialog 状态。null 表示不显示；非 null 时渲染 Dialog。
    var colorPickerRequest by remember { mutableStateOf<ColorPickerRequest?>(null) }

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
            GeneralSection(settings, onNavigate, showToast)
            Spacer(Modifier.height(24.dp))
            BluetoothSection(settings)
            Spacer(Modifier.height(24.dp))
            IntegrationSection(onNavigate, settings)
            Spacer(Modifier.height(24.dp))
            StatusBarSection(
                settings = settings,
                onShowColorPicker = { prefKey, title, defaultColor ->
                    colorPickerRequest = ColorPickerRequest(prefKey, title, defaultColor)
                }
            )
            Spacer(Modifier.height(24.dp))
            FloatingWindowSection(
                settings = settings,
                onShowColorPicker = { prefKey, title, defaultColor ->
                    colorPickerRequest = ColorPickerRequest(prefKey, title, defaultColor)
                }
            )
            Spacer(Modifier.height(24.dp))
            AboutSection(onNavigate)
            // 内容延伸到屏幕底部（iOS 风格），末尾留出胶囊+系统导航栏空间
            Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }

    // Compose ColorPickerDialog（替代原 ColorPickerView 依赖）
    colorPickerRequest?.let { request ->
        ColorPickerDialog(
            title = request.title,
            initialColor = settings.getInt(request.prefKey, request.defaultColor),
            onConfirm = { color ->
                settings.setInt(request.prefKey, color)
                colorPickerRequest = null
            },
            onDismiss = { colorPickerRequest = null }
        )
    }
}

/**
 * 颜色选择请求。非 null 时触发 [ColorPickerDialog] 显示。
 */
private data class ColorPickerRequest(
    val prefKey: String,
    val title: String,
    val defaultColor: Int
)

// ──────────────────────────────────────────────
// 设置项组件
// ──────────────────────────────────────────────

// ──────────────────────────────────────────────
// 各分组实现
// ──────────────────────────────────────────────

@Composable
private fun GeneralSection(
    settings: SettingsRepository,
    onNavigate: (String) -> Unit,
    showToast: (String) -> Unit
) {
    var isHistoryEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false)) }
    var isAnimationEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HEARTBEAT_ANIMATION_ENABLED, true)) }
    var isSpeedEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, false)) }
    var isHideFromRecents by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HIDE_FROM_RECENTS_ENABLED, false)) }

    val showWarningDialog = remember { mutableStateOf(false) }
    val showSpeedDialog = remember { mutableStateOf(false) }

    // Icon Container: 常规功能使用蓝色系
    val containerColor = lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer, 0.4f)
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SectionTitle(stringResource(R.string.general))
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

        if (showWarningDialog.value) {
            AlertDialog(
                onDismissRequest = { showWarningDialog.value = false },
                title = { Text(stringResource(R.string.performance_warning)) },
                text = { Text(stringResource(R.string.history_warning_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        settings.setBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, true)
                        isHistoryEnabled = true
                        showWarningDialog.value = false
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showWarningDialog.value = false }) { Text(stringResource(R.string.cancel)) }
                }
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

        SettingsItem {
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

        if (showSpeedDialog.value) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog.value = false },
                title = { Text(stringResource(R.string.enable_speed_title)) },
                text = { Text(stringResource(R.string.speed_gps_warning)) },
                confirmButton = {
                    TextButton(onClick = {
                        settings.setBoolean(PrefsKeys.SPEED_DISPLAY_ENABLED, true)
                        isSpeedEnabled = true
                        showSpeedDialog.value = false
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSpeedDialog.value = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        SettingsItem {
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

        SettingsItem(onClick = { onNavigate("fullscreen_sound") }) {
            SettingsLink(
                title = stringResource(R.string.fullscreen_sound),
                subtitle = stringResource(R.string.subtitle_fullscreen_sound),
                leadingIcon = painterResource(R.drawable.ic_fullscreen_sound),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(onClick = { onNavigate("theme") }) {
            SettingsLink(title = stringResource(R.string.theme_settings), subtitle = stringResource(R.string.subtitle_theme_settings),
                leadingIcon = painterResource(R.drawable.ic_text_color),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }

        SettingsItem(onClick = { onNavigate("favorite") }) {
            SettingsLink(title = stringResource(R.string.favorite_devices), subtitle = stringResource(R.string.subtitle_favorite_devices),
                leadingIcon = painterResource(R.drawable.ic_star),
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
private fun BluetoothSection(settings: SettingsRepository) {
    var isAutoConnectEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.AUTO_CONNECT_ENABLED, false)) }
    var isAutoReconnectEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.AUTO_RECONNECT_ENABLED, true)) }

    // Icon Container: 蓝牙使用蓝色系（与常规功能统一）
    val containerColor = lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer, 0.4f)
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SectionTitle(stringResource(R.string.bluetooth))
    SettingsGroupCard {
        SettingsItem(isFirst = true) {
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

        SettingsItem(isLast = true) {
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
    }
}

@Composable
private fun IntegrationSection(
    onNavigate: (String) -> Unit,
    settings: SettingsRepository
) {
    // Icon Container: 集成功能使用蓝色系（与常规功能统一）
    val containerColor = lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer, 0.4f)
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SectionTitle(stringResource(R.string.integration))
    SettingsGroupCard {
        SettingsItem(isFirst = true, onClick = { onNavigate("server") }) {
            SettingsLink(title = stringResource(R.string.http_websocket_server), subtitle = stringResource(R.string.subtitle_http_websocket_server),
                leadingIcon = painterResource(R.drawable.ic_http_websocket),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }

        SettingsItem(isLast = true, onClick = { onNavigate("webhook") }) {
            SettingsLink(title = stringResource(R.string.webhook_settings), subtitle = stringResource(R.string.subtitle_webhook_settings),
                leadingIcon = painterResource(R.drawable.ic_webhook),
                leadingIconContainerColor = containerColor, leadingIconTint = iconTint)
        }
    }
}

@Composable
private fun StatusBarSection(
    settings: SettingsRepository,
    onShowColorPicker: (prefKey: String, title: String, defaultColor: Int) -> Unit
) {
    val context = LocalContext.current
    val statusBarTextColor by settings.observeInt(PrefsKeys.STATUS_BAR_TEXT_COLOR, android.graphics.Color.BLACK)
        .collectAsState()

    // Icon Container: 状态栏使用紫蓝色系
    val containerColor = lerp(
        lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer, 0.6f),
        MaterialTheme.colorScheme.surfaceContainer,
        0.4f
    )
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SectionTitle(stringResource(R.string.status_bar_resident))
    SettingsGroupCard {
        StatusBarResidentItem(settings, isFirst = true, containerColor = containerColor, iconTint = iconTint)

        SettingsItem {
            var isBpmTextEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.STATUS_BAR_BPM_TEXT_ENABLED, true)) }
            SettingsSwitch(
                checked = isBpmTextEnabled,
                onCheckedChange = {
                    isBpmTextEnabled = it
                    settings.setBoolean(PrefsKeys.STATUS_BAR_BPM_TEXT_ENABLED, it)
                },
                title = stringResource(R.string.display_bpm_unit),
                subtitle = stringResource(R.string.subtitle_display_bpm_unit),
                leadingIcon = painterResource(R.drawable.ic_bpm_unit),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.horizontal_position),
                value = settings.getInt(PrefsKeys.STATUS_BAR_X_POSITION, 0),
                onValueChange = { settings.setInt(PrefsKeys.STATUS_BAR_X_POSITION, it) },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_horizontal_position),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.vertical_adjust),
                value = settings.getInt(PrefsKeys.STATUS_BAR_Y_OFFSET, 10),
                onValueChange = { settings.setInt(PrefsKeys.STATUS_BAR_Y_OFFSET, it) },
                range = 0..20,
                suffix = "dp",
                leadingIcon = painterResource(R.drawable.ic_vertical_adjust),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.overall_size),
                value = settings.getInt(PrefsKeys.STATUS_BAR_SIZE, 100),
                onValueChange = { settings.setInt(PrefsKeys.STATUS_BAR_SIZE, it) },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_resize),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.text_thickness),
                value = settings.getInt(PrefsKeys.STATUS_BAR_TEXT_THICKNESS, 0),
                onValueChange = { settings.setInt(PrefsKeys.STATUS_BAR_TEXT_THICKNESS, it) },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_text_thickness),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        // 文字颜色：黑/自定义/白
        SettingsItem(isLast = true) {
            val context = LocalContext.current
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = containerColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_text_color),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = iconTint
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.text_color),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ColorPreviewButton(
                        label = stringResource(R.string.black),
                        color = android.graphics.Color.BLACK,
                        onClick = {
                            settings.setInt(PrefsKeys.STATUS_BAR_TEXT_COLOR, android.graphics.Color.BLACK)
                        }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.custom_color),
                        color = statusBarTextColor,
                        onClick = {
                            onShowColorPicker(
                                PrefsKeys.STATUS_BAR_TEXT_COLOR,
                                context.getString(R.string.text_color_picker),
                                android.graphics.Color.BLACK
                            )
                        }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.white),
                        color = android.graphics.Color.WHITE,
                        onClick = {
                            settings.setInt(PrefsKeys.STATUS_BAR_TEXT_COLOR, android.graphics.Color.WHITE)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBarResidentItem(
    settings: SettingsRepository,
    isFirst: Boolean = false,
    containerColor: Color = Color.Transparent,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val context = LocalContext.current
    val overlayProvider = remember { context.applicationContext.appContainer.overlayPermissionProvider }
    var residentChecked by remember { mutableStateOf(settings.getBoolean(PrefsKeys.STATUS_BAR_RESIDENT_ENABLED, false)) }

    fun setResidentEnabled(enabled: Boolean) {
        if (enabled && !overlayProvider.canDrawOverlays()) {
            (context as? Activity)?.let {
                MainActivity.suppressHideForExternalLaunch = true
                it.startActivity(overlayProvider.createManageOverlayIntent())
            }
            return
        }
        settings.setBoolean(PrefsKeys.STATUS_BAR_RESIDENT_ENABLED, enabled)
        if (enabled) {
            ServiceController.startStatusBarResidentService(context)
        } else {
            ServiceController.stopStatusBarResidentService(context)
        }
        residentChecked = enabled
    }

    SettingsItem(isFirst = isFirst) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (containerColor != Color.Transparent) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = containerColor
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.ic_status_bar_heart),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = iconTint
                        )
                    }
                }
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_status_bar_heart),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { setResidentEnabled(!residentChecked) }
            ) {
                Text(
                    text = stringResource(R.string.status_bar_heart_rate),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.subtitle_status_bar_heart_rate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = residentChecked,
                onCheckedChange = ::setResidentEnabled
            )
        }
    }
}

@Composable
private fun FloatingWindowSection(
    settings: SettingsRepository,
    onShowColorPicker: (prefKey: String, title: String, defaultColor: Int) -> Unit
) {
    val context = LocalContext.current
    val floatingTextColor by settings.observeInt(PrefsKeys.FLOATING_TEXT_COLOR, android.graphics.Color.BLACK)
        .collectAsState()
    val floatingBgColor by settings.observeInt(PrefsKeys.FLOATING_BG_COLOR, android.graphics.Color.BLACK)
        .collectAsState()
    val floatingBorderColor by settings.observeInt(PrefsKeys.FLOATING_BORDER_COLOR, android.graphics.Color.GRAY)
        .collectAsState()
    // Icon Container: 悬浮窗使用蓝色系（与常规功能统一）
    val containerColor = lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer, 0.4f)
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SectionTitle(stringResource(R.string.floating_window_style))
    SettingsGroupCard {
        SettingsItem(isFirst = true) {
            Text(
                text = stringResource(R.string.floating_window_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsItem {
            var isBpmTextEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.BPM_TEXT_ENABLED, true)) }
            SettingsSwitch(
                checked = isBpmTextEnabled,
                onCheckedChange = {
                    isBpmTextEnabled = it
                    settings.setBoolean(PrefsKeys.BPM_TEXT_ENABLED, it)
                },
                title = stringResource(R.string.display_bpm_text),
                subtitle = stringResource(R.string.subtitle_display_bpm_text),
                leadingIcon = painterResource(R.drawable.ic_bpm_text),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            var isHeartIconEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.HEART_ICON_ENABLED, true)) }
            SettingsSwitch(
                checked = isHeartIconEnabled,
                onCheckedChange = {
                    isHeartIconEnabled = it
                    settings.setBoolean(PrefsKeys.HEART_ICON_ENABLED, it)
                },
                title = stringResource(R.string.display_heart_icon),
                subtitle = stringResource(R.string.subtitle_display_heart_icon),
                leadingIcon = painterResource(R.drawable.ic_heart_icon),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.overall_size),
                value = settings.getInt(PrefsKeys.FLOATING_SIZE, 100),
                onValueChange = { settings.setInt(PrefsKeys.FLOATING_SIZE, it) },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_resize),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.icon_size),
                value = settings.getInt(PrefsKeys.FLOATING_ICON_SIZE, 100),
                onValueChange = { settings.setInt(PrefsKeys.FLOATING_ICON_SIZE, it) },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_icon_size),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.corner_radius),
                value = settings.getInt(PrefsKeys.FLOATING_CORNER_RADIUS, 50),
                onValueChange = { settings.setInt(PrefsKeys.FLOATING_CORNER_RADIUS, it) },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_corner_radius),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.bg_opacity),
                value = settings.getInt(PrefsKeys.FLOATING_BG_ALPHA, 80),
                onValueChange = { settings.setInt(PrefsKeys.FLOATING_BG_ALPHA, it) },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_bg_opacity),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.border_opacity),
                value = settings.getInt(PrefsKeys.FLOATING_BORDER_ALPHA, 100),
                onValueChange = { settings.setInt(PrefsKeys.FLOATING_BORDER_ALPHA, it) },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_border_opacity),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

        SettingsItem(isLast = true) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = containerColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_color_palette),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = iconTint
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.color_picker),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ColorPreviewButton(
                        label = stringResource(R.string.text_label),
                        color = floatingTextColor,
                        onClick = { onShowColorPicker(PrefsKeys.FLOATING_TEXT_COLOR, context.getString(R.string.text_color_picker), android.graphics.Color.BLACK) }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.background_label),
                        color = floatingBgColor,
                        onClick = { onShowColorPicker(PrefsKeys.FLOATING_BG_COLOR, context.getString(R.string.bg_color_picker), android.graphics.Color.BLACK) }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.border_label),
                        color = floatingBorderColor,
                        onClick = { onShowColorPicker(PrefsKeys.FLOATING_BORDER_COLOR, context.getString(R.string.border_color_picker), android.graphics.Color.GRAY) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    onNavigate: (String) -> Unit
) {
    // Icon Container: 关于使用蓝色系（与常规功能统一）
    val containerColor = lerp(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainer, 0.4f)
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SectionTitle(stringResource(R.string.about))
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

