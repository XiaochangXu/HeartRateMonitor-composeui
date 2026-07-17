package com.github.heartratemonitor_compose.ui.settings

import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ──────────────────────────────────────────────
// 主屏幕
// ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    sharedPreferences: SharedPreferences,
    onNavigateBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenExternal: (Intent) -> Unit,
    onRequestMediaProjection: (Intent) -> Unit,
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
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
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
            GeneralSection(sharedPreferences, onNavigate, showToast)
            Spacer(Modifier.height(24.dp))
            BluetoothSection(sharedPreferences)
            Spacer(Modifier.height(24.dp))
            IntegrationSection(onNavigate, sharedPreferences, onRequestMediaProjection)
            Spacer(Modifier.height(24.dp))
            StatusBarSection(sharedPreferences, onRequestMediaProjection)
            Spacer(Modifier.height(24.dp))
            FloatingWindowSection(
                prefs = sharedPreferences,
                onShowColorPicker = { prefKey, title, defaultColor ->
                    colorPickerRequest = ColorPickerRequest(prefKey, title, defaultColor)
                }
            )
            Spacer(Modifier.height(24.dp))
            AboutSection(sharedPreferences, onNavigate, onOpenExternal)
            // 内容延伸到屏幕底部（iOS 风格），末尾留出胶囊+系统导航栏空间
            Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }

    // Compose ColorPickerDialog（替代原 ColorPickerView 依赖）
    colorPickerRequest?.let { request ->
        ColorPickerDialog(
            title = request.title,
            initialColor = sharedPreferences.getInt(request.prefKey, request.defaultColor),
            onConfirm = { color ->
                sharedPreferences.edit().putInt(request.prefKey, color).apply()
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

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

/**
 * 分组卡片容器：用一张圆角卡片包裹同组的多个设置项，
 * 项之间用 [SettingsDivider] 分隔。统一为 Material 3 grouped list 风格。
 */
@Composable
private fun SettingsGroupCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(content = content)
    }
}

/**
 * 分组卡片内的单个设置项行（不再包裹独立 Card）。
 * - [onClick] 非空时整行可点击，ripple 覆盖整行（含圆角由外层 Surface clip）。
 * - 最小高度 56dp，与 MD3 列表规范一致。
 */
@Composable
private fun SettingsItem(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        content()
    }
}

/** 分组卡片内项之间的分隔线。左右留 16dp 与行内容对齐。 */
@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    subtitle: String? = null,
    leadingIcon: Painter? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // MD3 列表项 Leading Icon：24dp + 16dp 间距
        if (leadingIcon != null) {
            Icon(
                painter = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsLink(
    title: String,
    icon: ImageVector? = Icons.AutoMirrored.Filled.KeyboardArrowRight,
    leadingIcon: Painter? = null
) {
    // 纯展示组件：不在此处处理点击。
    // 点击由外层 SettingsItem(onClick = ...) 承担，使 ripple 覆盖整行
    // （含水平 16dp 边距与圆角处），而非仅覆盖被 Column padding 包裹的 Row 中间区域。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // MD3 列表项 Leading Icon：24dp + 16dp 间距
        if (leadingIcon != null) {
            Icon(
                painter = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DragSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    suffix: String = "",
    enabled: Boolean = true,
    leadingIcon: Painter? = null
) {
    var internalValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }
    var sliderSize by remember { mutableStateOf(IntSize.Zero) }

    val fraction = if (range.last > range.first)
        (internalValue - range.first) / (range.last - range.first) else 0f

    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        // MD3 列表项 Leading Icon：24dp + 16dp 间距
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    painter = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .onGloballyPositioned { sliderSize = it.size }
        ) {
            // 拖拽时浮现在手柄上方的数值气泡
            if (isDragging && sliderSize.width > 0) {
                val density = LocalDensity.current
                val thumbCenterX = with(density) {
                    val trackStart = 16.dp.toPx()
                    val trackWidth = sliderSize.width.toFloat() - 32.dp.toPx()
                    trackStart + trackWidth * fraction
                }
                Surface(
                    modifier = Modifier
                        .offset(
                            x = with(density) { thumbCenterX.toDp() } - 22.dp,
                            y = (-12).dp
                        ),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = "${internalValue.toInt()}$suffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Slider(
                modifier = Modifier.align(Alignment.BottomCenter),
                value = internalValue,
                onValueChange = {
                    internalValue = it
                    isDragging = true
                    onValueChange(it.toInt())
                },
                onValueChangeFinished = { isDragging = false },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = 0,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledThumbColor = MaterialTheme.colorScheme.outline,
                    disabledActiveTrackColor = MaterialTheme.colorScheme.outline,
                    disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun ColorPreviewButton(
    label: String,
    color: Int,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(color),
            onClick = onClick
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ──────────────────────────────────────────────
// 各分组实现
// ──────────────────────────────────────────────

@Composable
private fun GeneralSection(
    prefs: SharedPreferences,
    onNavigate: (String) -> Unit,
    showToast: (String) -> Unit
) {
    var isHistoryEnabled by remember { mutableStateOf(prefs.getBoolean("history_recording_enabled", false)) }
    var isAnimationEnabled by remember { mutableStateOf(prefs.getBoolean("heartbeat_animation_enabled", true)) }
    var isSpeedEnabled by remember { mutableStateOf(prefs.getBoolean("speed_display_enabled", false)) }
    var isHideFromRecents by remember { mutableStateOf(prefs.getBoolean("hide_from_recents_enabled", false)) }

    val showWarningDialog = remember { mutableStateOf(false) }
    val showSpeedDialog = remember { mutableStateOf(false) }

    SectionTitle(stringResource(R.string.general))
    SettingsGroupCard {
        // 记录历史数据
        SettingsItem {
            SettingsSwitch(
                checked = isHistoryEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        showWarningDialog.value = true
                    } else {
                        isHistoryEnabled = false
                        prefs.edit().putBoolean("history_recording_enabled", false).apply()
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
                        prefs.edit().putBoolean("history_recording_enabled", true).apply()
                        isHistoryEnabled = true
                        showWarningDialog.value = false
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showWarningDialog.value = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        SettingsDivider()
        // 心跳动画效果
        SettingsItem {
            SettingsSwitch(
                checked = isAnimationEnabled,
                onCheckedChange = {
                    isAnimationEnabled = it
                    prefs.edit().putBoolean("heartbeat_animation_enabled", it).apply()
                },
                title = stringResource(R.string.heartbeat_animation),
                leadingIcon = painterResource(R.drawable.ic_animation)
            )
        }

        SettingsDivider()
        // 显示时速
        SettingsItem {
            SettingsSwitch(
                checked = isSpeedEnabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        showSpeedDialog.value = true
                    } else {
                        isSpeedEnabled = false
                        prefs.edit().putBoolean("speed_display_enabled", false).apply()
                    }
                },
                title = stringResource(R.string.display_speed_gps),
                subtitle = stringResource(R.string.speed_gps_subtitle),
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
                        prefs.edit().putBoolean("speed_display_enabled", true).apply()
                        isSpeedEnabled = true
                        showSpeedDialog.value = false
                    }) { Text(stringResource(R.string.confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSpeedDialog.value = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }

        SettingsDivider()
        // 退出应用隐藏后台
        SettingsItem {
            SettingsSwitch(
                checked = isHideFromRecents,
                onCheckedChange = {
                    isHideFromRecents = it
                    prefs.edit().putBoolean("hide_from_recents_enabled", it).apply()
                },
                title = stringResource(R.string.hide_from_recents),
                subtitle = stringResource(R.string.hide_from_recents_subtitle),
                leadingIcon = painterResource(R.drawable.ic_hide_source)
            )
        }

        SettingsDivider()
        // 收藏设备
        SettingsItem(onClick = { onNavigate("favorite") }) {
            SettingsLink(title = stringResource(R.string.favorite_devices), leadingIcon = painterResource(R.drawable.ic_star))
        }

        SettingsDivider()
        // 心率预警
        SettingsItem(onClick = { onNavigate("alarm") }) {
            SettingsLink(title = stringResource(R.string.heart_rate_alarm), leadingIcon = painterResource(R.drawable.ic_warning))
        }
    }
}

@Composable
private fun BluetoothSection(prefs: SharedPreferences) {
    var isAutoConnectEnabled by remember { mutableStateOf(prefs.getBoolean("auto_connect_enabled", false)) }
    var isAutoReconnectEnabled by remember { mutableStateOf(prefs.getBoolean("auto_reconnect_enabled", true)) }

    SectionTitle(stringResource(R.string.bluetooth))
    SettingsGroupCard {
        SettingsItem {
            SettingsSwitch(
                checked = isAutoConnectEnabled,
                onCheckedChange = {
                    isAutoConnectEnabled = it
                    prefs.edit().putBoolean("auto_connect_enabled", it).apply()
                },
                title = stringResource(R.string.auto_connect_favorite),
                leadingIcon = painterResource(R.drawable.ic_bluetooth_connected_symbol)
            )
        }

        SettingsDivider()
        SettingsItem {
            SettingsSwitch(
                checked = isAutoReconnectEnabled,
                onCheckedChange = {
                    isAutoReconnectEnabled = it
                    prefs.edit().putBoolean("auto_reconnect_enabled", it).apply()
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
    prefs: SharedPreferences,
    onRequestMediaProjection: (Intent) -> Unit
) {
    SectionTitle(stringResource(R.string.integration))
    SettingsGroupCard {
        SettingsItem(onClick = { onNavigate("server") }) {
            SettingsLink(title = stringResource(R.string.http_websocket_server), leadingIcon = painterResource(R.drawable.ic_http_websocket))
        }

        SettingsDivider()
        SettingsItem(onClick = { onNavigate("webhook") }) {
            SettingsLink(title = stringResource(R.string.webhook_settings), leadingIcon = painterResource(R.drawable.ic_webhook))
        }
    }
}

@Composable
private fun StatusBarSection(
    prefs: SharedPreferences,
    onRequestMediaProjection: (Intent) -> Unit
) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.status_bar_resident))
    SettingsGroupCard {
        // 状态栏常驻心率
        SettingsItem {
            var residentChecked by remember { mutableStateOf(prefs.getBoolean("status_bar_resident_enabled", false)) }

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
                        .clickable {
                            val newValue = !residentChecked
                            if (newValue) {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                                    !android.provider.Settings.canDrawOverlays(context)
                                ) {
                                    (context as? android.app.Activity)?.let {
                                        com.github.heartratemonitor_compose.ui.main.MainActivity.suppressHideForExternalLaunch = true
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            android.net.Uri.parse("package:${context.packageName}")
                                        )
                                        it.startActivity(intent)
                                    }
                                } else {
                                    prefs.edit().putBoolean("status_bar_resident_enabled", true).apply()
                                    val intent = android.content.Intent(context, com.github.heartratemonitor_compose.service.StatusBarResidentService::class.java)
                                    context.startService(intent)
                                    residentChecked = true
                                }
                            } else {
                                prefs.edit().putBoolean("status_bar_resident_enabled", false).apply()
                                val intent = android.content.Intent(context, com.github.heartratemonitor_compose.service.StatusBarResidentService::class.java)
                                context.stopService(intent)
                                residentChecked = false
                            }
                        }
                ) {
                    Text(
                        text = stringResource(R.string.status_bar_heart_rate),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.status_bar_heart_rate_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = residentChecked,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M &&
                                !android.provider.Settings.canDrawOverlays(context)
                            ) {
                                (context as? android.app.Activity)?.let {
                                    com.github.heartratemonitor_compose.ui.main.MainActivity.suppressHideForExternalLaunch = true
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    it.startActivity(intent)
                                }
                            } else {
                                prefs.edit().putBoolean("status_bar_resident_enabled", true).apply()
                                val intent = android.content.Intent(context, com.github.heartratemonitor_compose.service.StatusBarResidentService::class.java)
                                context.startService(intent)
                                residentChecked = true
                            }
                        } else {
                            prefs.edit().putBoolean("status_bar_resident_enabled", false).apply()
                            val intent = android.content.Intent(context, com.github.heartratemonitor_compose.service.StatusBarResidentService::class.java)
                            context.stopService(intent)
                            residentChecked = false
                        }
                    }
                )
            }
        }

        SettingsDivider()
        // 显示 'bpm' 单位
        SettingsItem {
            var isBpmTextEnabled by remember { mutableStateOf(prefs.getBoolean("status_bar_bpm_text_enabled", true)) }
            SettingsSwitch(
                checked = isBpmTextEnabled,
                onCheckedChange = {
                    isBpmTextEnabled = it
                    prefs.edit().putBoolean("status_bar_bpm_text_enabled", it).apply()
                },
                title = stringResource(R.string.display_bpm_unit),
                leadingIcon = painterResource(R.drawable.ic_bpm_unit)
            )
        }

        SettingsDivider()
        // 水平位置
        SettingsItem {
            DragSlider(
                label = stringResource(R.string.horizontal_position),
                value = prefs.getInt("status_bar_x_position", 0),
                onValueChange = { prefs.edit().putInt("status_bar_x_position", it).apply() },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_horizontal_position)
            )
        }

        SettingsDivider()
        // 垂直微调
        SettingsItem {
            DragSlider(
                label = stringResource(R.string.vertical_adjust),
                value = prefs.getInt("status_bar_y_offset", 10),
                onValueChange = { prefs.edit().putInt("status_bar_y_offset", it).apply() },
                range = 0..20,
                suffix = "dp",
                leadingIcon = painterResource(R.drawable.ic_vertical_adjust)
            )
        }

        SettingsDivider()
        // 整体大小
        SettingsItem {
            DragSlider(
                label = stringResource(R.string.overall_size),
                value = prefs.getInt("status_bar_size", 100),
                onValueChange = { prefs.edit().putInt("status_bar_size", it).apply() },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_resize)
            )
        }

        SettingsDivider()
        // 文字粗细
        SettingsItem {
            DragSlider(
                label = stringResource(R.string.text_thickness),
                value = prefs.getInt("status_bar_text_thickness", 0),
                onValueChange = { prefs.edit().putInt("status_bar_text_thickness", it).apply() },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_text_thickness)
            )
        }

        SettingsDivider()
        // 文字颜色：黑/白预设，点击切换 status_bar_white_text
        SettingsItem {
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
                            prefs.edit().putBoolean("status_bar_white_text", false).apply()
                        }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.white),
                        color = android.graphics.Color.WHITE,
                        onClick = {
                            val isAuto = prefs.getBoolean("status_bar_auto_color", false)
                            if (isAuto) {
                                android.widget.Toast.makeText(context, context.getString(R.string.auto_color_conflict), android.widget.Toast.LENGTH_SHORT).show()
                                return@ColorPreviewButton
                            }
                            prefs.edit().putBoolean("status_bar_white_text", true).apply()
                        }
                    )
                }
            }
        }

        SettingsDivider()
        // 自动识别屏幕颜色
        SettingsItem {
            var autoChecked by remember { mutableStateOf(prefs.getBoolean("status_bar_auto_color", false)) }
            // 监听 sharedPrefs 变化：MediaProjection 授权结果由 MainActivity 异步写入，
            // 不监听会导致开关状态与实际启用状态不一致。
            DisposableEffect(Unit) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "status_bar_auto_color") {
                        autoChecked = prefs.getBoolean("status_bar_auto_color", false)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_auto_color),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (!autoChecked) {
                                if (!prefs.getBoolean("status_bar_resident_enabled", false)) {
                                    android.widget.Toast.makeText(context, context.getString(R.string.enable_resident_first), android.widget.Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                val projectionManager = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                                onRequestMediaProjection(projectionManager.createScreenCaptureIntent())
                            } else {
                                prefs.edit().putBoolean("status_bar_auto_color", false).apply()
                                val intent = android.content.Intent(context, com.github.heartratemonitor_compose.service.StatusBarResidentService::class.java).apply {
                                    action = com.github.heartratemonitor_compose.service.StatusBarResidentService.ACTION_STOP_MEDIA_PROJECTION
                                }
                                context.startService(intent)
                                autoChecked = false
                            }
                        }
                ) {
                    Text(
                        text = stringResource(R.string.auto_color_detect),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.auto_color_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = autoChecked,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (!prefs.getBoolean("status_bar_resident_enabled", false)) {
                                android.widget.Toast.makeText(context, context.getString(R.string.enable_resident_first), android.widget.Toast.LENGTH_SHORT).show()
                                return@Switch
                            }
                            val projectionManager = context.getSystemService(android.media.projection.MediaProjectionManager::class.java)
                            onRequestMediaProjection(projectionManager.createScreenCaptureIntent())
                        } else {
                            prefs.edit().putBoolean("status_bar_auto_color", false).apply()
                            val intent = android.content.Intent(context, com.github.heartratemonitor_compose.service.StatusBarResidentService::class.java).apply {
                                action = com.github.heartratemonitor_compose.service.StatusBarResidentService.ACTION_STOP_MEDIA_PROJECTION
                            }
                            context.startService(intent)
                            autoChecked = false
                        }
                    }
                )
            }
        }

        SettingsDivider()
        // 使用白色文字
        SettingsItem {
            var whiteChecked by remember { mutableStateOf(prefs.getBoolean("status_bar_white_text", false)) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_white_text),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val newValue = !whiteChecked
                            val isAuto = prefs.getBoolean("status_bar_auto_color", false)
                            if (newValue && isAuto) {
                                android.widget.Toast.makeText(context, context.getString(R.string.auto_color_unavailable), android.widget.Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            prefs.edit().putBoolean("status_bar_white_text", newValue).apply()
                            whiteChecked = newValue
                        }
                ) {
                    Text(
                        text = stringResource(R.string.use_white_text),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.white_text_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = whiteChecked,
                    onCheckedChange = { checked ->
                        val isAuto = prefs.getBoolean("status_bar_auto_color", false)
                        if (checked && isAuto) {
                            android.widget.Toast.makeText(context, context.getString(R.string.auto_color_unavailable), android.widget.Toast.LENGTH_SHORT).show()
                            return@Switch
                        }
                        prefs.edit().putBoolean("status_bar_white_text", checked).apply()
                        whiteChecked = checked
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingWindowSection(
    prefs: SharedPreferences,
    onShowColorPicker: (prefKey: String, title: String, defaultColor: Int) -> Unit
) {
    val context = LocalContext.current
    SectionTitle(stringResource(R.string.floating_window_style))
    SettingsGroupCard {
        // 说明
        SettingsItem {
            Text(
                text = stringResource(R.string.floating_window_tip),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsDivider()
        // 显示 'bpm' 文字
        SettingsItem {
            var isBpmTextEnabled by remember { mutableStateOf(prefs.getBoolean("bpm_text_enabled", true)) }
            SettingsSwitch(
                checked = isBpmTextEnabled,
                onCheckedChange = {
                    isBpmTextEnabled = it
                    prefs.edit().putBoolean("bpm_text_enabled", it).apply()
                },
                title = stringResource(R.string.display_bpm_text),
                leadingIcon = painterResource(R.drawable.ic_bpm_text)
            )
        }

        SettingsDivider()
        // 显示心形图标
        SettingsItem {
            var isHeartIconEnabled by remember { mutableStateOf(prefs.getBoolean("heart_icon_enabled", true)) }
            SettingsSwitch(
                checked = isHeartIconEnabled,
                onCheckedChange = {
                    isHeartIconEnabled = it
                    prefs.edit().putBoolean("heart_icon_enabled", it).apply()
                },
                title = stringResource(R.string.display_heart_icon),
                leadingIcon = painterResource(R.drawable.ic_heart_icon)
            )
        }

        SettingsDivider()
        // 整体大小
        SettingsItem {
            DragSlider(
                label = stringResource(R.string.overall_size),
                value = prefs.getInt("floating_size", 100),
                onValueChange = { prefs.edit().putInt("floating_size", it).apply() },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_resize)
            )
        }

        SettingsDivider()
        // 图标大小
        SettingsItem {
            DragSlider(
                label = stringResource(R.string.icon_size),
                value = prefs.getInt("floating_icon_size", 100),
                onValueChange = { prefs.edit().putInt("floating_icon_size", it).apply() },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_icon_size)
            )
        }

        SettingsDivider()
        // 圆角半径
        SettingsItem {
            DragSlider(
                label = stringResource(R.string.corner_radius),
                value = prefs.getInt("floating_corner_radius", 50),
                onValueChange = { prefs.edit().putInt("floating_corner_radius", it).apply() },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_corner_radius)
            )
        }

        SettingsDivider()
        // 背景不透明度
        SettingsItem {
            DragSlider(
                label = stringResource(R.string.bg_opacity),
                value = prefs.getInt("floating_bg_alpha", 80),
                onValueChange = { prefs.edit().putInt("floating_bg_alpha", it).apply() },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_bg_opacity)
            )
        }

        SettingsDivider()
        // 边框不透明度
        SettingsItem {
            DragSlider(
                label = stringResource(R.string.border_opacity),
                value = prefs.getInt("floating_border_alpha", 100),
                onValueChange = { prefs.edit().putInt("floating_border_alpha", it).apply() },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_border_opacity)
            )
        }

        SettingsDivider()
        // 颜色选择
        SettingsItem {
            Column {
                // 标题行:Leading Icon + 标题文字
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
                        color = prefs.getInt("floating_text_color", android.graphics.Color.BLACK),
                        onClick = { onShowColorPicker("floating_text_color", context.getString(R.string.text_color_picker), android.graphics.Color.BLACK) }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.background_label),
                        color = prefs.getInt("floating_bg_color", android.graphics.Color.BLACK),
                        onClick = { onShowColorPicker("floating_bg_color", context.getString(R.string.bg_color_picker), android.graphics.Color.BLACK) }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.border_label),
                        color = prefs.getInt("floating_border_color", android.graphics.Color.GRAY),
                        onClick = { onShowColorPicker("floating_border_color", context.getString(R.string.border_color_picker), android.graphics.Color.GRAY) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    prefs: SharedPreferences,
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
        // 版本
        SettingsItem {
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

        SettingsDivider()
        // 检查更新（GitHub Release）
        SettingsItem(
            onClick = {
                if (updateState is UpdateChecker.Result.UpdateAvailable) {
                    // 已经知道有更新，直接打开弹窗
                    updateDialog = updateState as UpdateChecker.Result.UpdateAvailable
                } else {
                    // 发起新的检查
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

        SettingsDivider()
        // 公平运行内存
        SettingsItem(onClick = { onNavigate("fair_memory") }) {
            SettingsLink(title = stringResource(R.string.fair_memory), leadingIcon = painterResource(R.drawable.ic_fair_memory))
        }

        SettingsDivider()
        // GitHub
        SettingsItem(
            onClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/XiaochangXu/HeartRateMonitor-composeui")
                )
                com.github.heartratemonitor_compose.ui.main.MainActivity.suppressHideForExternalLaunch = true
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
            // 左下角：确认（关闭弹窗）
            confirmButton = {
                TextButton(onClick = { updateDialog = null }) { Text(stringResource(R.string.confirm)) }
            },
            // 右下角：跳转更新（打开 GitHub Release 页）
            dismissButton = {
                TextButton(onClick = {
                    updateDialog = null
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        android.net.Uri.parse(info.htmlUrl)
                    )
                    com.github.heartratemonitor_compose.ui.main.MainActivity.suppressHideForExternalLaunch = true
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

// ──────────────────────────────────────────────
// Compose ColorPickerDialog（替代原 ColorPickerView 依赖）
// ──────────────────────────────────────────────

/**
 * Compose 颜色选择对话框。
 *
 * 替代原 com.skydoves:colorpickerview 依赖，使用 Compose Canvas + Slider 自建：
 * - HSV 色轮（Canvas 自绘）：H 与 S 通过点击/拖动选择
 * - 亮度滑块（Slider）：V 通道
 * - 当前色 / 预览色色块
 * - 「确认」「取消」按钮
 *
 * 颜色存储格式与原 ColorPickerView 一致：ARGB Int（直接写入 SharedPreferences），
 * 通过 [android.graphics.Color.colorToHSV] / [Color.HSVToColor] 与 HSV 互转。
 *
 * @param title 对话框标题
 * @param initialColor 初始颜色（ARGB Int）
 * @param onConfirm 确认回调，参数为选中的 ARGB Int
 * @param onDismiss 取消/关闭回调
 */
@Composable
private fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    // 初始 HSV
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hsv by remember { mutableStateOf(initialHsv.copyOf()) }
    val currentColor = remember(hsv) { android.graphics.Color.HSVToColor(hsv) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // HSV 色轮：H（0-360）+ S（0-1），通过点击/拖动选择
                HueSatWheelPicker(
                    hsv = hsv,
                    onHsvChanged = { hsv = it }
                )
                Spacer(Modifier.height(12.dp))

                // 亮度滑块（V 通道，0-100）
                val brightnessPercent = (hsv[2] * 100f).toInt()
                Text(
                    text = stringResource(R.string.brightness, brightnessPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = hsv[2],
                    onValueChange = { v -> hsv = floatArrayOf(hsv[0], hsv[1], v) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                // 当前色 + 预览色色块
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.initial_color), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(initialColor), RoundedCornerShape(8.dp))
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.current_color), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(currentColor), RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * HSV 色轮选择器。Canvas 自绘色相/饱和度圆盘 + 选择圆圈。
 *
 * 圆盘角度 → H（0-360），半径 → S（0-1，中心为 0，边缘为 1）。
 */
@Composable
private fun HueSatWheelPicker(
    hsv: FloatArray,
    onHsvChanged: (FloatArray) -> Unit,
    modifier: Modifier = Modifier
) {
    val wheelSize = 240.dp
    val density = LocalDensity.current
    val wheelSizePx = with(density) { wheelSize.toPx() }
    val radiusPx = wheelSizePx / 2f

    // 选择圆圈位置（相对于圆盘中心）
    val indicatorOffset = remember(hsv) {
        val angleRad = Math.toRadians(hsv[0].toDouble())
        val r = hsv[1] * radiusPx
        Offset(
            (radiusPx + r * cos(angleRad)).toFloat(),
            (radiusPx + r * sin(angleRad)).toFloat()
        )
    }

    Canvas(
        modifier = modifier
            .size(wheelSize)
            .pointerInput(Unit) {
                // 用 awaitEachGesture 替代 detectDragGestures：
                // detectDragGestures 的 onDragStart 只在手指移动超过 touch slop 后才触发，
                // 纯点击（tap）不会更新 hsv——用户"点色轮选位置"完全无反应。
                // awaitEachGesture 在 down 时立即调用 updateHsvFromTouch，tap 和 drag 都能选色。
                awaitEachGesture {
                    val down = awaitFirstDown()
                    updateHsvFromTouch(down.position, radiusPx, hsv, onHsvChanged)
                    drag(down.id) { change ->
                        updateHsvFromTouch(change.position, radiusPx, hsv, onHsvChanged)
                        change.consume()
                    }
                }
            }
    ) {
        val center = Offset(radiusPx, radiusPx)
        // 色相圆盘：sweepGradient 一次性绘制 360° 色相。
        // 原实现用 360 个 drawArc(useCenter=false) 拼接，useCenter=false 画的是弓形（弧+弦）
        // 而非扇形，1 度弓形填充区域趋近于零 → 拼起来圆盘内部空白，只有边缘一圈有色。
        // sweepGradient 沿角度方向渐变，与 HSV 色相环一致（0°红→60°黄→...→360°红）。
        // 起点在 3 点钟方向（0°），与下方 atan2 的角度起点对齐。
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Red,        // 0°
                    Color.Yellow,     // 60°
                    Color.Green,      // 120°
                    Color.Cyan,       // 180°
                    Color.Blue,       // 240°
                    Color.Magenta,    // 300°
                    Color.Red         // 360° 回到红，闭合
                ),
                center = center
            )
        )
        // S 模拟：中心白（S=0）→ 边缘原色（S=1），径向渐变叠加
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    Color.White.copy(alpha = 0f)
                ),
                center = center,
                radius = radiusPx
            )
        )
        // 选择圆圈（白色描边 + 黑色描边）
        drawCircle(
            color = Color.White,
            radius = 10f,
            center = indicatorOffset,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
        )
        drawCircle(
            color = Color.Black,
            radius = 10f,
            center = indicatorOffset,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f)
        )
    }
}

/**
 * 将触摸坐标转换为 HSV 并更新。
 * - 距圆心距离 → S（0-1，超过半径截断为 1）
 * - 角度 → H（0-360）
 *
 * V 通道处理：
 * 默认颜色是 BLACK（HSV 中 V=0），若直接保留 V=0，无论 H/S 怎么变，
 * HSVToColor 出来都是黑色——用户在色轮上点来点去永远是黑。
 * 色轮本身画的是 V=1 的色相，故此处当 V=0 时自动提升到 1f：
 * 用户首次点色轮立即看到真实颜色，之后 V 由亮度滑块控制（不会被重置）。
 */
private fun updateHsvFromTouch(
    offset: Offset,
    radiusPx: Float,
    currentHsv: FloatArray,
    onHsvChanged: (FloatArray) -> Unit
) {
    val cx = offset.x - radiusPx
    val cy = offset.y - radiusPx
    val r = sqrt(cx * cx + cy * cy).coerceAtMost(radiusPx)
    val s = (r / radiusPx).coerceIn(0f, 1f)
    var h = Math.toDegrees(atan2(cy.toDouble(), cx.toDouble())).toFloat()
    if (h < 0f) h += 360f
    // V=0（默认 BLACK）时提升到 1f，让色轮交互立即产生可见颜色
    val v = if (currentHsv[2] <= 0f) 1f else currentHsv[2]
    onHsvChanged(floatArrayOf(h, s, v))
}
