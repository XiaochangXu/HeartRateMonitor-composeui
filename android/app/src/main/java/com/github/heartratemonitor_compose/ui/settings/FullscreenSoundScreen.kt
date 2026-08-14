package com.github.heartratemonitor_compose.ui.settings

import android.widget.Toast
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
import androidx.core.os.LocaleListCompat
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.util.SoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.github.heartratemonitor_compose.ui.util.StatusBarScrim



internal fun defaultFullscreenSoundMode(): String {
    return if (LocaleListCompat.getDefault()[0]?.language == "zh") "cn" else "en"
}


internal fun resolveSoundMode(settings: SettingsRepository): String {
    val existing = settings.getStringNullable(PrefsKeys.FULLSCREEN_SOUND_MODE)
    if (existing != null) return existing

    
    val oldEnabled = settings.getBoolean(PrefsKeys.FULLSCREEN_SOUND_ENABLED, true)
    val mode = if (!oldEnabled) "off" else defaultFullscreenSoundMode()
    settings.setString(PrefsKeys.FULLSCREEN_SOUND_MODE, mode)
    return mode
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FullscreenSoundScreen(
    settings: SettingsRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentMode by remember { mutableStateOf(resolveSoundMode(settings)) }
    var isPreviewing by remember { mutableStateOf(false) }
    var previewProgress by remember { mutableFloatStateOf(0f) }
    var previewJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(currentMode) {
        previewJob?.cancel()
        previewJob = null
        isPreviewing = false
        previewProgress = 0f
    }

    fun startPreview(languageMode: String) {
        if (isPreviewing) return
        isPreviewing = true
        previewProgress = 0f

        previewJob = scope.launch {
            val sm = SoundManager(context, languageMode)
            try {
                withTimeoutOrNull(2000) { sm.awaitLoaded() }

                val tooLowDuration = sm.getDurationMs(SoundManager.SoundType.TOO_LOW)
                val lowBeepDuration = sm.getDurationMs(SoundManager.SoundType.LOW_BEEP)
                val tooHighDuration = sm.getDurationMs(SoundManager.SoundType.TOO_HIGH)
                val highBeepDuration = sm.getDurationMs(SoundManager.SoundType.HIGH_BEEP)
                val pauseMs = 500L
                val groupPauseMs = 1000L
                val totalMs = tooLowDuration + pauseMs + lowBeepDuration + groupPauseMs + tooHighDuration + pauseMs + highBeepDuration

                val startTime = System.currentTimeMillis()

                        val progressJob = launch {
                    while (isActive) {
                        val elapsed = System.currentTimeMillis() - startTime
                        previewProgress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
                        delay(16)
                    }
                }

                
                sm.play(SoundManager.SoundType.TOO_LOW)
                delay(tooLowDuration)
                delay(pauseMs)
                sm.play(SoundManager.SoundType.LOW_BEEP)
                delay(lowBeepDuration)
                delay(groupPauseMs)

              
                sm.play(SoundManager.SoundType.TOO_HIGH)
                delay(tooHighDuration)
                delay(pauseMs)
                sm.play(SoundManager.SoundType.HIGH_BEEP)
                delay(highBeepDuration)

                
                previewProgress = 1f
                delay(300) 
                progressJob.cancel()
                sm.release()
            } catch (_: Exception) {
                sm.release()
            } finally {
                isPreviewing = false
                previewProgress = 0f
                previewJob = null
            }
        }
    }

    fun stopPreview() {
        previewJob?.cancel()
        previewJob = null
        isPreviewing = false
        previewProgress = 0f
    }

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
                        stringResource(R.string.fullscreen_sound),
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

            // 语音选项 
            SettingsGroupCard {
                SettingsItem(isFirst = true) {
                    SoundSwitchRow(
                        title = stringResource(R.string.fullscreen_sound_off),
                        subtitle = stringResource(R.string.subtitle_fullscreen_sound_off),
                        icon = painterResource(R.drawable.ic_sound_off),
                        checked = currentMode == "off",
                        onCheckedChange = {
                            currentMode = "off"
                            settings.setString(PrefsKeys.FULLSCREEN_SOUND_MODE, "off")
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
                            currentMode = "cn"
                            settings.setString(PrefsKeys.FULLSCREEN_SOUND_MODE, "cn")
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
                            currentMode = "en"
                            settings.setString(PrefsKeys.FULLSCREEN_SOUND_MODE, "en")
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
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            onClick = {
                                if (currentMode == "off") {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.enable_voice_first),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else if (isPreviewing) {
                                    stopPreview()
                                } else {
                                    startPreview(currentMode)
                                }
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPreviewing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                    contentDescription = stringResource(R.string.start_preview),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
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
