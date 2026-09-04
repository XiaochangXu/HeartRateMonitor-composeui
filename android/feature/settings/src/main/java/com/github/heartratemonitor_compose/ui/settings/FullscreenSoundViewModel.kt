package com.github.heartratemonitor_compose.ui.settings

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import com.github.heartratemonitor_compose.ui.util.resolveSoundMode
import com.github.heartratemonitor_compose.util.SoundManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * 全屏提示音设置页面的 ViewModel（MVI 架构）。
 *
 * 职责：
 * - 声音模式归约进 [FullscreenSoundUiState.soundMode]（observeNullable 派生）。
 * - 试听流程与 Job 管理留在本 VM，状态经 [setState] 归约。
 */
@HiltViewModel
class FullscreenSoundViewModel @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationContext private val appContext: Context
) : MviViewModel<FullscreenSoundUiState, FullscreenSoundIntent>(
    // 构造期解析（含旧开关迁移落盘），初值即持久化真值
    FullscreenSoundUiState(soundMode = resolveSoundMode(settings))
) {

    /** 构造期解析出的模式，供键缺失（null）发射时兜底。 */
    private val initialMode: String = currentState.soundMode

    init {
        viewModelScope.launch {
            settings.observeNullable(SettingsKeys.FULLSCREEN_SOUND_MODE).collect { mode ->
                setState { it.copy(soundMode = mode ?: initialMode) }
            }
        }
    }

    private var previewJob: Job? = null

    override suspend fun handleIntent(intent: FullscreenSoundIntent) {
        when (intent) {
            is FullscreenSoundIntent.SelectSoundMode -> {
                settings.set(SettingsKeys.FULLSCREEN_SOUND_MODE, intent.mode)
                // 与原页面语义一致：模式切换打断进行中的试听
                cancelPreview()
            }
            FullscreenSoundIntent.StartPreview -> startPreview()
            FullscreenSoundIntent.StopPreview -> cancelPreview()
        }
    }

    /** 开始试听当前模式；试听中重复调用无效。 */
    private fun startPreview() {
        if (currentState.isPreviewing) return
        val languageMode = currentState.soundMode
        setState { it.copy(isPreviewing = true, previewProgress = 0f) }

        previewJob = viewModelScope.launch {
            // SoundManager 构造会同步做 4 次 MediaPlayer.create 音频解码（读文件+解析），
            // 必须移出主线程，否则点击试听瞬间卡顿、低端设备有 ANR 风险
            val sm = withContext(Dispatchers.IO) { SoundManager(appContext, languageMode) }
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
                        val progress = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
                        setState { it.copy(previewProgress = progress) }
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

                setState { it.copy(previewProgress = 1f) }
                delay(300)
                progressJob.cancel()
                sm.release()
            } catch (_: Exception) {
                sm.release()
            } finally {
                setState { it.copy(isPreviewing = false, previewProgress = 0f) }
                previewJob = null
            }
        }
    }

    private fun cancelPreview() {
        previewJob?.cancel()
        previewJob = null
        setState { it.copy(isPreviewing = false, previewProgress = 0f) }
    }
}

/** 全屏提示音设置页用户意图。 */
sealed interface FullscreenSoundIntent {
    data class SelectSoundMode(val mode: String) : FullscreenSoundIntent
    data object StartPreview : FullscreenSoundIntent
    data object StopPreview : FullscreenSoundIntent
}

/** 全屏提示音设置页 UI 状态（只读快照）。 */
data class FullscreenSoundUiState(
    val soundMode: String,
    val isPreviewing: Boolean = false,
    val previewProgress: Float = 0f
)
