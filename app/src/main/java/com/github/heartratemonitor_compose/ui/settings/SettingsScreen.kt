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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
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
                        "设置",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Normal
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
            Spacer(Modifier.height(32.dp))
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun SettingsItemCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

    // 用 Card(onClick) 重载（而非 modifier.clickable）：该重载把 ripple 关联到 shape，
    // ripple 被正确 clip 到圆角内，不会溢出到圆角外侧的矩形角落。
    val columnContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            elevation = elevation,
            colors = colors,
            content = columnContent
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            elevation = elevation,
            colors = colors,
            content = columnContent
        )
    }
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
    // 点击由外层 SettingsItemCard(onClick = ...) 承担，使 ripple 覆盖整个圆角卡片
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

    SectionTitle("常规")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 记录历史数据
        SettingsItemCard {
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
                title = "记录历史数据",
                leadingIcon = painterResource(R.drawable.ic_deployed_code_history)
            )
        }

        if (showWarningDialog.value) {
            AlertDialog(
                onDismissRequest = { showWarningDialog.value = false },
                title = { Text("性能警告") },
                text = { Text("开启历史记录将持续写入数据到存储，可能会增加耗电量。确认开启吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("history_recording_enabled", true).apply()
                        isHistoryEnabled = true
                        showWarningDialog.value = false
                    }) { Text("确认") }
                },
                dismissButton = {
                    TextButton(onClick = { showWarningDialog.value = false }) { Text("取消") }
                }
            )
        }

        // 心跳动画效果
        SettingsItemCard {
            SettingsSwitch(
                checked = isAnimationEnabled,
                onCheckedChange = {
                    isAnimationEnabled = it
                    prefs.edit().putBoolean("heartbeat_animation_enabled", it).apply()
                },
                title = "心跳动画效果",
                leadingIcon = painterResource(R.drawable.ic_animation)
            )
        }

        // 显示时速
        SettingsItemCard {
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
                title = "显示时速 (GPS)",
                subtitle = "开启时速显示将使用GPS定位，可能会增加耗电。",
                leadingIcon = painterResource(R.drawable.ic_speed)
            )
        }

        if (showSpeedDialog.value) {
            AlertDialog(
                onDismissRequest = { showSpeedDialog.value = false },
                title = { Text("开启速度显示") },
                text = { Text("该功能使用 GPS 计算速度，可能会增加耗电量并需要定位权限。确认开启吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        prefs.edit().putBoolean("speed_display_enabled", true).apply()
                        isSpeedEnabled = true
                        showSpeedDialog.value = false
                    }) { Text("确认") }
                },
                dismissButton = {
                    TextButton(onClick = { showSpeedDialog.value = false }) { Text("取消") }
                }
            )
        }

        // 退出应用隐藏后台
        SettingsItemCard {
            SettingsSwitch(
                checked = isHideFromRecents,
                onCheckedChange = {
                    isHideFromRecents = it
                    prefs.edit().putBoolean("hide_from_recents_enabled", it).apply()
                },
                title = "退出应用隐藏后台",
                subtitle = "开启后应用不会出现在最近任务列表中",
                leadingIcon = painterResource(R.drawable.ic_hide_source)
            )
        }

        // 收藏设备
        SettingsItemCard(onClick = { onNavigate("favorite") }) {
            SettingsLink(title = "收藏设备", leadingIcon = painterResource(R.drawable.ic_star))
        }

        // 心率预警
        SettingsItemCard(onClick = { onNavigate("alarm") }) {
            SettingsLink(title = "心率预警", leadingIcon = painterResource(R.drawable.ic_warning))
        }
    }
}

@Composable
private fun BluetoothSection(prefs: SharedPreferences) {
    var isAutoConnectEnabled by remember { mutableStateOf(prefs.getBoolean("auto_connect_enabled", false)) }
    var isAutoReconnectEnabled by remember { mutableStateOf(prefs.getBoolean("auto_reconnect_enabled", true)) }

    SectionTitle("蓝牙")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsItemCard {
            SettingsSwitch(
                checked = isAutoConnectEnabled,
                onCheckedChange = {
                    isAutoConnectEnabled = it
                    prefs.edit().putBoolean("auto_connect_enabled", it).apply()
                },
                title = "启动时自动连接收藏设备",
                leadingIcon = painterResource(R.drawable.ic_bluetooth_connected_symbol)
            )
        }

        SettingsItemCard {
            SettingsSwitch(
                checked = isAutoReconnectEnabled,
                onCheckedChange = {
                    isAutoReconnectEnabled = it
                    prefs.edit().putBoolean("auto_reconnect_enabled", it).apply()
                },
                title = "断开后自动重连",
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
    SectionTitle("集成 & 外部访问")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsItemCard(onClick = { onNavigate("server") }) {
            SettingsLink(title = "HTTP & WebSocket 服务器", leadingIcon = painterResource(R.drawable.ic_http_websocket))
        }

        SettingsItemCard(onClick = { onNavigate("webhook") }) {
            SettingsLink(title = "Webhook 设置", leadingIcon = painterResource(R.drawable.ic_webhook))
        }
    }
}

@Composable
private fun StatusBarSection(
    prefs: SharedPreferences,
    onRequestMediaProjection: (Intent) -> Unit
) {
    val context = LocalContext.current
    SectionTitle("状态栏常驻")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 状态栏常驻心率
        SettingsItemCard {
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
                        text = "状态栏常驻心率",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "在顶部状态栏显示实时心率（需悬浮窗权限）",
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

        // 显示 'bpm' 单位
        SettingsItemCard {
            var isBpmTextEnabled by remember { mutableStateOf(prefs.getBoolean("status_bar_bpm_text_enabled", true)) }
            SettingsSwitch(
                checked = isBpmTextEnabled,
                onCheckedChange = {
                    isBpmTextEnabled = it
                    prefs.edit().putBoolean("status_bar_bpm_text_enabled", it).apply()
                },
                title = "显示 BPM 单位",
                leadingIcon = painterResource(R.drawable.ic_bpm_unit)
            )
        }

        // 水平位置
        SettingsItemCard {
            DragSlider(
                label = "水平位置",
                value = prefs.getInt("status_bar_x_position", 0),
                onValueChange = { prefs.edit().putInt("status_bar_x_position", it).apply() },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_horizontal_position)
            )
        }

        // 垂直微调
        SettingsItemCard {
            DragSlider(
                label = "垂直微调",
                value = prefs.getInt("status_bar_y_offset", 10),
                onValueChange = { prefs.edit().putInt("status_bar_y_offset", it).apply() },
                range = 0..20,
                suffix = "dp",
                leadingIcon = painterResource(R.drawable.ic_vertical_adjust)
            )
        }

        // 整体大小
        SettingsItemCard {
            DragSlider(
                label = "整体大小",
                value = prefs.getInt("status_bar_size", 100),
                onValueChange = { prefs.edit().putInt("status_bar_size", it).apply() },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_resize)
            )
        }

        // 文字粗细
        SettingsItemCard {
            DragSlider(
                label = "文字粗细",
                value = prefs.getInt("status_bar_text_thickness", 0),
                onValueChange = { prefs.edit().putInt("status_bar_text_thickness", it).apply() },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_text_thickness)
            )
        }

<<<<<<< HEAD
        // 文字颜色：黑/白预设，点击切换 status_bar_white_text
        SettingsItemCard {
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
                        text = "文字颜色",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ColorPreviewButton(
                        label = "黑色",
                        color = android.graphics.Color.BLACK,
                        onClick = {
                            prefs.edit().putBoolean("status_bar_white_text", false).apply()
                        }
                    )
                    ColorPreviewButton(
                        label = "白色",
                        color = android.graphics.Color.WHITE,
                        onClick = {
                            val isAuto = prefs.getBoolean("status_bar_auto_color", false)
                            if (isAuto) {
                                android.widget.Toast.makeText(context, "自动识别已开启，请先关闭后再手动选择颜色。", android.widget.Toast.LENGTH_SHORT).show()
                                return@ColorPreviewButton
                            }
                            prefs.edit().putBoolean("status_bar_white_text", true).apply()
                        }
                    )
                }
=======
        // 文字颜色
        SettingsItemCard {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_text_color),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "文字颜色",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61
            }
        }

        // 自动识别屏幕颜色
        SettingsItemCard {
            var autoChecked by remember { mutableStateOf(prefs.getBoolean("status_bar_auto_color", false)) }
<<<<<<< HEAD
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
=======
>>>>>>> 5411686d21345985822abde01a9f90c414e63b61

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
                                    android.widget.Toast.makeText(context, "请先开启「状态栏常驻心率」开关后再使用自动识别。", android.widget.Toast.LENGTH_SHORT).show()
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
                        text = "自动识别屏幕颜色",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "通过截屏采样状态栏区域亮度，自动切换黑/白文字。需要一次录屏授权，会增加少量耗电。",
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
                                android.widget.Toast.makeText(context, "请先开启「状态栏常驻心率」开关后再使用自动识别。", android.widget.Toast.LENGTH_SHORT).show()
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

        // 使用白色文字
        SettingsItemCard {
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
                                android.widget.Toast.makeText(context, "自动识别开启时不可用", android.widget.Toast.LENGTH_SHORT).show()
                                return@clickable
                            }
                            prefs.edit().putBoolean("status_bar_white_text", newValue).apply()
                            whiteChecked = newValue
                        }
                ) {
                    Text(
                        text = "使用白色文字",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "关闭「自动识别」时生效。默认关闭即为纯黑文字。",
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
                            android.widget.Toast.makeText(context, "自动识别开启时不可用", android.widget.Toast.LENGTH_SHORT).show()
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
    SectionTitle("悬浮窗样式")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 说明
        SettingsItemCard {
            Text(
                text = "长按悬浮窗开启触摸穿透，开启后触摸直接传递给下方应用，不影响手机其他操作。再次长按悬浮窗或点击通知栏按钮关闭。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 显示 'bpm' 文字
        SettingsItemCard {
            var isBpmTextEnabled by remember { mutableStateOf(prefs.getBoolean("bpm_text_enabled", true)) }
            SettingsSwitch(
                checked = isBpmTextEnabled,
                onCheckedChange = {
                    isBpmTextEnabled = it
                    prefs.edit().putBoolean("bpm_text_enabled", it).apply()
                },
                title = "显示 BPM 文字",
                leadingIcon = painterResource(R.drawable.ic_bpm_text)
            )
        }

        // 显示心形图标
        SettingsItemCard {
            var isHeartIconEnabled by remember { mutableStateOf(prefs.getBoolean("heart_icon_enabled", true)) }
            SettingsSwitch(
                checked = isHeartIconEnabled,
                onCheckedChange = {
                    isHeartIconEnabled = it
                    prefs.edit().putBoolean("heart_icon_enabled", it).apply()
                },
                title = "显示心形图标",
                leadingIcon = painterResource(R.drawable.ic_heart_icon)
            )
        }

        // 整体大小
        SettingsItemCard {
            DragSlider(
                label = "整体大小",
                value = prefs.getInt("floating_size", 100),
                onValueChange = { prefs.edit().putInt("floating_size", it).apply() },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_resize)
            )
        }

        // 图标大小
        SettingsItemCard {
            DragSlider(
                label = "图标大小",
                value = prefs.getInt("floating_icon_size", 100),
                onValueChange = { prefs.edit().putInt("floating_icon_size", it).apply() },
                range = 50..200,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_icon_size)
            )
        }

        // 圆角半径
        SettingsItemCard {
            DragSlider(
                label = "圆角半径",
                value = prefs.getInt("floating_corner_radius", 50),
                onValueChange = { prefs.edit().putInt("floating_corner_radius", it).apply() },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_corner_radius)
            )
        }

        // 背景不透明度
        SettingsItemCard {
            DragSlider(
                label = "背景不透明度",
                value = prefs.getInt("floating_bg_alpha", 80),
                onValueChange = { prefs.edit().putInt("floating_bg_alpha", it).apply() },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_bg_opacity)
            )
        }

        // 边框不透明度
        SettingsItemCard {
            DragSlider(
                label = "边框不透明度",
                value = prefs.getInt("floating_border_alpha", 100),
                onValueChange = { prefs.edit().putInt("floating_border_alpha", it).apply() },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_border_opacity)
            )
        }

        // 颜色选择
        SettingsItemCard {
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
                        text = "颜色选择",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ColorPreviewButton(
                        label = "文本",
                        color = prefs.getInt("floating_text_color", android.graphics.Color.BLACK),
                        onClick = { onShowColorPicker("floating_text_color", "文本颜色", android.graphics.Color.BLACK) }
                    )
                    ColorPreviewButton(
                        label = "背景",
                        color = prefs.getInt("floating_bg_color", android.graphics.Color.BLACK),
                        onClick = { onShowColorPicker("floating_bg_color", "背景颜色", android.graphics.Color.BLACK) }
                    )
                    ColorPreviewButton(
                        label = "边框",
                        color = prefs.getInt("floating_border_color", android.graphics.Color.GRAY),
                        onClick = { onShowColorPicker("floating_border_color", "边框颜色", android.graphics.Color.GRAY) }
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
    SectionTitle("关于")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 版本
        SettingsItemCard {
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
                    text = "版本",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                val versionName = remember {
                    try {
                        aboutContext.packageManager.getPackageInfo(aboutContext.packageName, 0).versionName ?: "未知"
                    } catch (e: Exception) {
                        "未知"
                    }
                }
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 公平运行内存
        SettingsItemCard(onClick = { onNavigate("fair_memory") }) {
            SettingsLink(title = "公平运行内存", leadingIcon = painterResource(R.drawable.ic_fair_memory))
        }

        // GitHub
        SettingsItemCard(
            onClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/XiaochangXu/HeartRateMonitor-composeui")
                )
                com.github.heartratemonitor_compose.ui.main.MainActivity.suppressHideForExternalLaunch = true
                onOpenExternal(intent)
            }
        ) {
            SettingsLink(title = "访问 GitHub 仓库", leadingIcon = painterResource(R.drawable.ic_github_repo))
        }
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
                    text = "亮度: $brightnessPercent%",
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
                        Text("初始", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(initialColor), RoundedCornerShape(8.dp))
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("当前", style = MaterialTheme.typography.bodySmall)
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
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
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
