package com.github.heartratemonitor_compose.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.system.OverlayPermissionProvider
import com.github.heartratemonitor_compose.service.ServiceController
import com.github.heartratemonitor_compose.ui.main.MainActivity
import kotlinx.coroutines.launch

// ──────────────────────────────────────────────
// 主屏幕
// ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    onNavigate: (String) -> Unit,
    onOpenExternal: (Intent) -> Unit,
    showToast: (String) -> Unit = {}
) {
    val context = LocalContext.current

    // Compose ColorPickerDialog 状态。null 表示不显示；非 null 时渲染 Dialog。
    var colorPickerRequest by remember { mutableStateOf<ColorPickerRequest?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Normal
                    )
                }
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
            AboutSection(settings, onNavigate, onOpenExternal)
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
                leadingIcon = painterResource(R.drawable.ic_deployed_code_history)
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
                leadingIcon = painterResource(R.drawable.ic_animation)
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
                leadingIcon = painterResource(R.drawable.ic_speed)
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
                leadingIcon = painterResource(R.drawable.ic_hide_source)
            )
        }

        SettingsItem(onClick = { onNavigate("fullscreen_sound") }) {
            SettingsLink(
                title = stringResource(R.string.fullscreen_sound),
                leadingIcon = painterResource(R.drawable.ic_fullscreen_sound)
            )
        }

        SettingsItem(onClick = { onNavigate("theme") }) {
            SettingsLink(title = stringResource(R.string.theme_settings), leadingIcon = painterResource(R.drawable.ic_text_color))
        }

        SettingsItem(onClick = { onNavigate("favorite") }) {
            SettingsLink(title = stringResource(R.string.favorite_devices), leadingIcon = painterResource(R.drawable.ic_star))
        }

        SettingsItem(isLast = true, onClick = { onNavigate("alarm") }) {
            SettingsLink(title = stringResource(R.string.heart_rate_alarm), leadingIcon = painterResource(R.drawable.ic_warning))
        }
    }
}

@Composable
private fun BluetoothSection(settings: SettingsRepository) {
    var isAutoConnectEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.AUTO_CONNECT_ENABLED, false)) }
    var isAutoReconnectEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.AUTO_RECONNECT_ENABLED, true)) }

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
                leadingIcon = painterResource(R.drawable.ic_bluetooth_connected_symbol)
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
                leadingIcon = painterResource(R.drawable.ic_plug_connect)
            )
        }
    }
}

@Composable
private fun IntegrationSection(
    onNavigate: (String) -> Unit,
    settings: SettingsRepository
) {
    SectionTitle(stringResource(R.string.integration))
    SettingsGroupCard {
        SettingsItem(isFirst = true, onClick = { onNavigate("server") }) {
            SettingsLink(title = stringResource(R.string.http_websocket_server), leadingIcon = painterResource(R.drawable.ic_http_websocket))
        }

        SettingsItem(isLast = true, onClick = { onNavigate("webhook") }) {
            SettingsLink(title = stringResource(R.string.webhook_settings), leadingIcon = painterResource(R.drawable.ic_webhook))
        }
    }
}

@Composable
private fun StatusBarSection(
    settings: SettingsRepository,
    onShowColorPicker: (prefKey: String, title: String, defaultColor: Int) -> Unit
) {
    SectionTitle(stringResource(R.string.status_bar_resident))
    SettingsGroupCard {
        StatusBarResidentItem(settings, isFirst = true)

        SettingsItem {
            var isBpmTextEnabled by remember { mutableStateOf(settings.getBoolean(PrefsKeys.STATUS_BAR_BPM_TEXT_ENABLED, true)) }
            SettingsSwitch(
                checked = isBpmTextEnabled,
                onCheckedChange = {
                    isBpmTextEnabled = it
                    settings.setBoolean(PrefsKeys.STATUS_BAR_BPM_TEXT_ENABLED, it)
                },
                title = stringResource(R.string.display_bpm_unit),
                leadingIcon = painterResource(R.drawable.ic_bpm_unit)
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.horizontal_position),
                value = settings.getInt(PrefsKeys.STATUS_BAR_X_POSITION, 0),
                onValueChange = { settings.setInt(PrefsKeys.STATUS_BAR_X_POSITION, it) },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_horizontal_position)
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.vertical_adjust),
                value = settings.getInt(PrefsKeys.STATUS_BAR_Y_OFFSET, 10),
                onValueChange = { settings.setInt(PrefsKeys.STATUS_BAR_Y_OFFSET, it) },
                range = 0..20,
                suffix = "dp",
                leadingIcon = painterResource(R.drawable.ic_vertical_adjust)
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.overall_size),
                value = settings.getInt(PrefsKeys.STATUS_BAR_SIZE, 100),
                onValueChange = { settings.setInt(PrefsKeys.STATUS_BAR_SIZE, it) },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_resize)
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.text_thickness),
                value = settings.getInt(PrefsKeys.STATUS_BAR_TEXT_THICKNESS, 0),
                onValueChange = { settings.setInt(PrefsKeys.STATUS_BAR_TEXT_THICKNESS, it) },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_text_thickness)
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
                    Icon(
                        painter = painterResource(R.drawable.ic_text_color),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.text_color),
                        style = MaterialTheme.typography.bodyMedium,
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
                        color = settings.getInt(PrefsKeys.STATUS_BAR_TEXT_COLOR, android.graphics.Color.BLACK),
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
    isFirst: Boolean = false
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
            Icon(
                painter = painterResource(R.drawable.ic_status_bar_heart),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { setResidentEnabled(!residentChecked) }
            ) {
                Text(
                    text = stringResource(R.string.status_bar_heart_rate),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
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
                leadingIcon = painterResource(R.drawable.ic_bpm_text)
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
                leadingIcon = painterResource(R.drawable.ic_heart_icon)
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.overall_size),
                value = settings.getInt(PrefsKeys.FLOATING_SIZE, 100),
                onValueChange = { settings.setInt(PrefsKeys.FLOATING_SIZE, it) },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_resize)
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.icon_size),
                value = settings.getInt(PrefsKeys.FLOATING_ICON_SIZE, 100),
                onValueChange = { settings.setInt(PrefsKeys.FLOATING_ICON_SIZE, it) },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_icon_size)
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.corner_radius),
                value = settings.getInt(PrefsKeys.FLOATING_CORNER_RADIUS, 50),
                onValueChange = { settings.setInt(PrefsKeys.FLOATING_CORNER_RADIUS, it) },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_corner_radius)
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.bg_opacity),
                value = settings.getInt(PrefsKeys.FLOATING_BG_ALPHA, 80),
                onValueChange = { settings.setInt(PrefsKeys.FLOATING_BG_ALPHA, it) },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_bg_opacity)
            )
        }

        SettingsItem {
            DragSlider(
                label = stringResource(R.string.border_opacity),
                value = settings.getInt(PrefsKeys.FLOATING_BORDER_ALPHA, 100),
                onValueChange = { settings.setInt(PrefsKeys.FLOATING_BORDER_ALPHA, it) },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_border_opacity)
            )
        }

        SettingsItem(isLast = true) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_color_palette),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.color_picker),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ColorPreviewButton(
                        label = stringResource(R.string.text_label),
                        color = settings.getInt(PrefsKeys.FLOATING_TEXT_COLOR, android.graphics.Color.BLACK),
                        onClick = { onShowColorPicker(PrefsKeys.FLOATING_TEXT_COLOR, context.getString(R.string.text_color_picker), android.graphics.Color.BLACK) }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.background_label),
                        color = settings.getInt(PrefsKeys.FLOATING_BG_COLOR, android.graphics.Color.BLACK),
                        onClick = { onShowColorPicker(PrefsKeys.FLOATING_BG_COLOR, context.getString(R.string.bg_color_picker), android.graphics.Color.BLACK) }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.border_label),
                        color = settings.getInt(PrefsKeys.FLOATING_BORDER_COLOR, android.graphics.Color.GRAY),
                        onClick = { onShowColorPicker(PrefsKeys.FLOATING_BORDER_COLOR, context.getString(R.string.border_color_picker), android.graphics.Color.GRAY) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    settings: SettingsRepository,
    onNavigate: (String) -> Unit,
    onOpenExternal: (Intent) -> Unit
) {
    val aboutContext = LocalContext.current
    val scope = rememberCoroutineScope()

    // 当前版本号（去除 'v' 前缀，用于与 GitHub Release tag 对比）
    val currentVersion = remember {
        try {
            val raw = aboutContext.packageManager
                .getPackageInfo(aboutContext.packageName, 0).versionName
            if (raw != null) raw.removePrefix("v").removePrefix("V") else aboutContext.getString(R.string.unknown_version)
        } catch (e: Exception) {
            aboutContext.getString(R.string.unknown_version)
        }
    }

    // 检查更新状态：null=未触发；Checking=检查中；Result=已返回结果
    var updateState by remember { mutableStateOf<Any?>(null) }
    // 更新提示弹窗：UpdateChecker.Result.UpdateAvailable 时显示
    var updateDialog by remember { mutableStateOf<UpdateChecker.Result.UpdateAvailable?>(null) }
    // 错误/已是最新版提示弹窗
    var messageDialog by remember { mutableStateOf<String?>(null) }

    SectionTitle(stringResource(R.string.about))
    SettingsGroupCard {
        SettingsItem(isFirst = true) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_version),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.version),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = currentVersion,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SettingsItem(
            onClick = {
                if (updateState is UpdateChecker.Result.UpdateAvailable) {
                    updateDialog = updateState as UpdateChecker.Result.UpdateAvailable
                } else {
                    updateState = UpdateChecker.Result.Error(aboutContext.getString(R.string.checking_update)) // 占位，避免重复点击
                    scope.launch {
                        val result = UpdateChecker.check(aboutContext, currentVersion)
                        updateState = result
                        when (result) {
                            is UpdateChecker.Result.UpdateAvailable -> updateDialog = result
                            is UpdateChecker.Result.UpToDate ->
                                messageDialog = aboutContext.getString(R.string.up_to_date, result.currentVersion)
                            is UpdateChecker.Result.Error ->
                                messageDialog = result.message
                        }
                    }
                }
            }
        ) {
            SettingsLink(
                title = if (updateState is UpdateChecker.Result.Error &&
                            (updateState as UpdateChecker.Result.Error).message == aboutContext.getString(R.string.checking_update))
                            aboutContext.getString(R.string.checking_update) else aboutContext.getString(R.string.check_update),
                leadingIcon = painterResource(R.drawable.ic_check_update)
            )
        }

        SettingsItem(onClick = { onNavigate("fair_memory") }) {
            SettingsLink(title = stringResource(R.string.fair_memory), leadingIcon = painterResource(R.drawable.ic_fair_memory))
        }

        SettingsItem(
            isLast = true,
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/XiaochangXu/HeartRateMonitor-composeui")
                )
                MainActivity.suppressHideForExternalLaunch = true
                onOpenExternal(intent)
            }
        ) {
            SettingsLink(title = stringResource(R.string.github_repo), leadingIcon = painterResource(R.drawable.ic_github_repo))
        }
    }

    // ── 发现新版本弹窗 ──
    // 左下角"确认"关闭弹窗；右下角"跳转更新"打开 GitHub Release 页
    updateDialog?.let { info ->
        AlertDialog(
            onDismissRequest = { updateDialog = null },
            title = { Text(aboutContext.getString(R.string.new_version_found, info.newVersion)) },
            text = {
                Column {
                    Text(
                        text = aboutContext.getString(R.string.current_version_label, currentVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    if (info.releaseNotes.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.release_notes_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = info.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.no_release_notes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { updateDialog = null }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    updateDialog = null
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(info.htmlUrl)
                    )
                    MainActivity.suppressHideForExternalLaunch = true
                    onOpenExternal(intent)
                }) { Text(stringResource(R.string.go_update)) }
            }
        )
    }

    // ── 通用消息弹窗（已是最新版 / 错误）──
    messageDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageDialog = null },
            title = { Text(stringResource(R.string.update_check_title)) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { messageDialog = null }) { Text(stringResource(R.string.confirm)) }
            }
        )
    }
}

