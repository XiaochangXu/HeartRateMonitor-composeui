package com.github.heartratemonitor_compose.ui.settings

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.heartratemonitor_compose.feature.settings.R
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim
import com.github.heartratemonitor_compose.ui.util.cardShape


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullscreenSoundScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: FullscreenSoundViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentMode = uiState.soundMode
    val isPreviewing = uiState.isPreviewing
    val previewProgress = uiState.previewProgress

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                        stringResource(R.string.fullscreen_sound),
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

            // 语音选项 
            SettingsGroupCard {
                SettingsItem(isFirst = true) {
                    SoundSwitchRow(
                        title = stringResource(R.string.fullscreen_sound_off),
                        subtitle = stringResource(R.string.subtitle_fullscreen_sound_off),
                        icon = painterResource(R.drawable.ic_sound_off),
                        checked = currentMode == "off",
                        onCheckedChange = {
                            viewModel.dispatch(FullscreenSoundIntent.SelectSoundMode("off"))
                        }
                    )
                }
                SettingsItem {
                    SoundSwitchRow(
                        title = stringResource(R.string.fullscreen_sound_cn),
                        subtitle = stringResource(R.string.subtitle_fullscreen_sound_cn),
                        icon = painterResource(R.drawable.ic_sound_cn),
                        checked = currentMode == "cn",
                        onCheckedChange = {
                            viewModel.dispatch(FullscreenSoundIntent.SelectSoundMode("cn"))
                        }
                    )
                }
                SettingsItem(isLast = true) {
                    SoundSwitchRow(
                        title = stringResource(R.string.fullscreen_sound_en),
                        subtitle = stringResource(R.string.subtitle_fullscreen_sound_en),
                        icon = painterResource(R.drawable.ic_sound_en),
                        checked = currentMode == "en",
                        onCheckedChange = {
                            viewModel.dispatch(FullscreenSoundIntent.SelectSoundMode("en"))
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── 声音试听 ──
            SettingsGroupCard {
                 SettingsItem(isFirst = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_sound_preview),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.start_preview),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.subtitle_start_preview),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        // 圆形 filled 图标按钮：40dp 圆形 primary 底 +
                        // onPrimary 图标；按压时圆角以 fastSpatial spring（damping 0.6 /
                        // stiffness 800）平滑收缩到 8dp（按压缩形反馈）
                        val playInteractionSource = remember { MutableInteractionSource() }
                        val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                        val playCornerRadius by animateDpAsState(
                            targetValue = if (isPlayPressed) 8.dp else 20.dp,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
                            label = "playButtonCorner"
                        )
                        FilledIconButton(
                            onClick = {
                                if (currentMode == "off") {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.enable_voice_first),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else if (isPreviewing) {
                                    viewModel.dispatch(FullscreenSoundIntent.StopPreview)
                                } else {
                                    viewModel.dispatch(FullscreenSoundIntent.StartPreview)
                                }
                            },
                            shape = cardShape(playCornerRadius),
                            interactionSource = playInteractionSource,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = if (isPreviewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(R.string.start_preview),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                 SettingsItem(isLast = true) {
                    LinearWavyProgressIndicator(
                        progress = { previewProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        amplitude = { 1f }
                    )
                }
            }

            Spacer(Modifier.height(64.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
            }
            StatusBarScrim()
        }
    }
    }
}


@Composable
private fun SoundSwitchRow(
    title: String,
    subtitle: String? = null,
    icon: Painter,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
