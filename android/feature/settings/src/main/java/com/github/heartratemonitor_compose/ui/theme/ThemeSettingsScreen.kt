package com.github.heartratemonitor_compose.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.heartratemonitor_compose.feature.settings.R
import com.github.heartratemonitor_compose.ui.settings.ColorPickerDialog
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim

/**
 * 主题设置页：
 * - 色卡预览网格与配色缓存见 ThemePresetSeeds.kt（ThemePreviewCache / PresetSeedsRow）
 * - 模式选择器/开关行/variant 选择等见 ThemeSections.kt
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: ThemeSettingsViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config = uiState.config
    val isCustom = config.source == ThemeSource.CUSTOM
    var showSeedPicker by remember { mutableStateOf(false) }
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
                        stringResource(R.string.theme_settings),
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

            SettingsGroupCard {
                SettingsItem(isFirst = true) {
                    HeaderRow()
                }

              SettingsItem {
                    SettingsSwitchRow(
                        title = stringResource(R.string.theme_custom),
                        checked = isCustom,
                        onCheckedChange = { checked ->
                            viewModel.dispatch(
                                ThemeSettingsIntent.SetSource(
                                    if (checked) ThemeSource.CUSTOM else ThemeSource.SYSTEM_MONET
                                )
                            )
                        }
                    )
                }

                // 2. 主题模式（始终可用）
                SettingsItem {
                    ThemeModeRow(
                        currentMode = config.mode,
                        onModeSelected = { viewModel.dispatch(ThemeSettingsIntent.SetMode(it)) }
                    )
                }

                // 3. 预设色卡（仅自定义模式可用）
                SettingsItem(enabled = isCustom) {
                    PresetSeedsRow(
                        currentSeed = config.seedArgb,
                        style = config.style,
                        onSeedSelected = { viewModel.dispatch(ThemeSettingsIntent.SetSeed(it)) },
                        previewCache = viewModel.themePreviewCache,
                        enabled = isCustom
                    )
                }

                // 4. 自定义种子色（仅自定义模式可用）
                SettingsItem(enabled = isCustom) {
                    CustomSeedRow(
                        seedArgb = config.seedArgb,
                        onClick = { showSeedPicker = true },
                        enabled = isCustom
                    )
                }

                // 5. PaletteStyle variant（仅自定义模式可用）
                SettingsItem(enabled = isCustom, isLast = true) {
                    VariantSelectorRow(
                        currentStyle = config.style,
                        onStyleSelected = { viewModel.dispatch(ThemeSettingsIntent.SetStyle(it)) },
                        enabled = isCustom
                    )
                }
            }

                 Spacer(Modifier.height(64.dp + 8.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
            StatusBarScrim()
        }
    }
    }

    if (showSeedPicker) {
        ColorPickerDialog(
            title = stringResource(R.string.theme_custom_seed),
            initialColor = config.seedArgb,
            onConfirm = {
                viewModel.dispatch(ThemeSettingsIntent.SetSeed(it))
                showSeedPicker = false
            },
            onDismiss = { showSeedPicker = false }
        )
    }
}
