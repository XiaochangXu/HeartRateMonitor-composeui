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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.heartratemonitor_compose.feature.settings.R
import androidx.datastore.preferences.core.Preferences
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim


@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun StatusBarSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: StatusBarSettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // 哪个键在选色属纯瞬时态，保留 UI 层（判定标准 4）
     var colorPickerRequest by remember { mutableStateOf<ColorPickerRequest?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
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
                        stringResource(R.string.status_bar_settings),
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceBright
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(com.github.heartratemonitor_compose.ui.widgets.R.string.cd_back)
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

            StatusBarContent(
                uiState = uiState,
                viewModel = viewModel,
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

    // ColorPickerDialog：本页唯一选色键为文字色，初值取 uiState 当前值
    colorPickerRequest?.let { request ->
        ColorPickerDialog(
            title = request.title,
            initialColor = uiState.textColor,
            onConfirm = { color ->
                viewModel.dispatch(StatusBarSettingsIntent.ConfirmColor(request.prefKey, color))
                colorPickerRequest = null
            },
            onDismiss = { colorPickerRequest = null }
        )
    }
}



@Composable
private fun StatusBarContent(
    uiState: StatusBarSettingsUiState,
    viewModel: StatusBarSettingsViewModel,
    onShowColorPicker: (prefKey: Preferences.Key<Int>, title: String, defaultColor: Int) -> Unit
) {
    val context = LocalContext.current

    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconTint = MaterialTheme.colorScheme.onPrimaryContainer

    SettingsGroupCard {
        StatusBarResidentItem(
            residentEnabled = uiState.residentEnabled,
            onResidentEnabledChange = { enabled ->
                viewModel.dispatch(StatusBarSettingsIntent.SetResident(enabled) { permissionIntent ->
                    // 无悬浮窗权限：VM 经回调回传权限页 Intent，Activity 上下文执行跳转
                    (context as? Activity)?.startActivity(permissionIntent)
                })
            },
            isFirst = true,
            containerColor = containerColor,
            iconTint = iconTint
        )

        SettingsItem {
            SettingsSwitch(
                checked = uiState.bpmTextEnabled,
                onCheckedChange = {
                    viewModel.dispatch(StatusBarSettingsIntent.SetBpmText(it))
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
                value = uiState.xPosition,
                onValueChange = {
                    viewModel.dispatch(StatusBarSettingsIntent.SetXPosition(it))
                },
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
                value = uiState.yOffset,
                onValueChange = {
                    viewModel.dispatch(StatusBarSettingsIntent.SetYOffset(it))
                },
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
                value = uiState.size,
                onValueChange = {
                    viewModel.dispatch(StatusBarSettingsIntent.SetSize(it))
                },
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
                value = uiState.textThickness,
                onValueChange = {
                    viewModel.dispatch(StatusBarSettingsIntent.SetTextThickness(it))
                },
                range = 0..100,
                suffix = "%",
                leadingIcon = painterResource(R.drawable.ic_text_thickness),
                leadingIconContainerColor = containerColor,
                leadingIconTint = iconTint
            )
        }

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
                            viewModel.dispatch(
                                StatusBarSettingsIntent.SetTextColor(android.graphics.Color.BLACK)
                            )
                        }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.custom_color),
                        color = uiState.textColor,
                        onClick = {
                            onShowColorPicker(
                                SettingsKeys.STATUS_BAR_TEXT_COLOR,
                                context.getString(R.string.text_color_picker),
                                android.graphics.Color.BLACK
                            )
                        }
                    )
                    ColorPreviewButton(
                        label = stringResource(R.string.white),
                        color = android.graphics.Color.WHITE,
                        onClick = {
                            viewModel.dispatch(
                                StatusBarSettingsIntent.SetTextColor(android.graphics.Color.WHITE)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBarResidentItem(
    residentEnabled: Boolean,
    onResidentEnabledChange: (Boolean) -> Unit,
    isFirst: Boolean = false,
    containerColor: Color = Color.Transparent,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
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
                    .clickable { onResidentEnabledChange(!residentEnabled) }
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
                checked = residentEnabled,
                onCheckedChange = onResidentEnabledChange
            )
        }
    }
}
