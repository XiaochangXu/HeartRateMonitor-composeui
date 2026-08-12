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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FloatingWindowSettingsScreen(
    settings: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

 var colorPickerRequest by remember { mutableStateOf<ColorPickerRequest?>(null) }

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
                        stringResource(R.string.floating_window_settings),
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

            FloatingWindowContent(
                settings = settings,
                onShowColorPicker = { prefKey, title, defaultColor ->
                    colorPickerRequest = ColorPickerRequest(prefKey, title, defaultColor)
                }
            )

            Spacer(Modifier.height(64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
            StatusBarScrim()
        }
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



@Composable
private fun FloatingWindowContent(
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

    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

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
