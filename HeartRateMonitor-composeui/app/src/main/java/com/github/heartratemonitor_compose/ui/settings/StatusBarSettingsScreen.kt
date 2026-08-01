package com.github.heartratemonitor_compose.ui.settings

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatusBarSettingsScreen(
    settings: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // ColorPickerDialog 状态。null 表示不显示；非 null 时渲染 Dialog。
    var colorPickerRequest by remember { mutableStateOf<ColorPickerRequest?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.status_bar_settings),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            StatusBarContent(
                settings = settings,
                onShowColorPicker = { prefKey, title, defaultColor ->
                    colorPickerRequest = ColorPickerRequest(prefKey, title, defaultColor)
                }
            )

            Spacer(Modifier.height(64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }

    // ColorPickerDialog
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
 * 定义在 SettingsComponents.kt 中，由多个二级页面共用。
 */

@Composable
private fun StatusBarContent(
    settings: SettingsRepository,
    onShowColorPicker: (prefKey: String, title: String, defaultColor: Int) -> Unit
) {
    val context = LocalContext.current
    val statusBarTextColor by settings.observeInt(PrefsKeys.STATUS_BAR_TEXT_COLOR, android.graphics.Color.BLACK)
        .collectAsState()

    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

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
