package com.github.heartratemonitor_compose.ui.settings

import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.AppSettings
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 悬浮窗设置页面的 ViewModel（教科书式 MVI，Phase 1）。
 *
 * 职责：
 * - 从 [SettingsRepository.settings] 全量快照派生悬浮窗设置：
 *   UiState 是设置真源的派生投影，Flow 回流经 [setState] 归约（状态下行），
 *   预览色等展示值直接取快照字段，不在 UI 计算。
 * - 开关/滑块/颜色事件经 [FloatingWindowSettingsIntent] dispatch 上行：
 *   滑块 onValueChange 每拍写入（不加节流，语义同 StatusBarSettingsViewModel）；
 *   颜色选择器确认经 ConfirmColor 回写，选择哪个键属 UI 瞬时态。
 */
@HiltViewModel
class FloatingWindowSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : MviViewModel<FloatingWindowSettingsUiState, FloatingWindowSettingsIntent>(
    initialFloatingWindowSettingsUiState()
) {

    init {
        // 设置真源投影：每次快照变化原子归约进 UiState，禁止本地双写（§3.5）
        viewModelScope.launch {
            settings.settings.collect { s ->
                setState {
                    it.copy(
                        bpmTextEnabled = s.bpmTextEnabled,
                        heartIconEnabled = s.heartIconEnabled,
                        size = s.floatingSize,
                        iconSize = s.floatingIconSize,
                        cornerRadius = s.floatingCornerRadius,
                        bgAlpha = s.floatingBgAlpha,
                        borderAlpha = s.floatingBorderAlpha,
                        textColor = s.floatingTextColor,
                        bgColor = s.floatingBgColor,
                        borderColor = s.floatingBorderColor
                    )
                }
            }
        }
    }

    override suspend fun handleIntent(intent: FloatingWindowSettingsIntent) {
        when (intent) {
            is FloatingWindowSettingsIntent.SetBpmText ->
                settings.set(SettingsKeys.BPM_TEXT_ENABLED, intent.enabled)
            is FloatingWindowSettingsIntent.SetHeartIcon ->
                settings.set(SettingsKeys.HEART_ICON_ENABLED, intent.enabled)
            is FloatingWindowSettingsIntent.SetSize ->
                settings.set(SettingsKeys.FLOATING_SIZE, intent.value)
            is FloatingWindowSettingsIntent.SetIconSize ->
                settings.set(SettingsKeys.FLOATING_ICON_SIZE, intent.value)
            is FloatingWindowSettingsIntent.SetCornerRadius ->
                settings.set(SettingsKeys.FLOATING_CORNER_RADIUS, intent.value)
            is FloatingWindowSettingsIntent.SetBgAlpha ->
                settings.set(SettingsKeys.FLOATING_BG_ALPHA, intent.value)
            is FloatingWindowSettingsIntent.SetBorderAlpha ->
                settings.set(SettingsKeys.FLOATING_BORDER_ALPHA, intent.value)
            is FloatingWindowSettingsIntent.ConfirmColor ->
                settings.set(intent.key, intent.color)
        }
    }
}

/** 悬浮窗设置页用户意图。 */
sealed interface FloatingWindowSettingsIntent {
    data class SetBpmText(val enabled: Boolean) : FloatingWindowSettingsIntent
    data class SetHeartIcon(val enabled: Boolean) : FloatingWindowSettingsIntent
    data class SetSize(val value: Int) : FloatingWindowSettingsIntent
    data class SetIconSize(val value: Int) : FloatingWindowSettingsIntent
    data class SetCornerRadius(val value: Int) : FloatingWindowSettingsIntent
    data class SetBgAlpha(val value: Int) : FloatingWindowSettingsIntent
    data class SetBorderAlpha(val value: Int) : FloatingWindowSettingsIntent

    /** 颜色选择器确认回写（文字/背景/边框三键之一，由 UI 瞬时态决定）。 */
    data class ConfirmColor(val key: Preferences.Key<Int>, val color: Int) : FloatingWindowSettingsIntent
}

/** 悬浮窗设置页 UI 状态（只读快照）。 */
data class FloatingWindowSettingsUiState(
    val bpmTextEnabled: Boolean,
    val heartIconEnabled: Boolean,
    val size: Int,
    val iconSize: Int,
    val cornerRadius: Int,
    val bgAlpha: Int,
    val borderAlpha: Int,
    val textColor: Int,
    val bgColor: Int,
    val borderColor: Int
)

/** 初始状态：默认值唯一来源为 [AppSettings.DEFAULTS]（契约 10.3）。 */
internal fun initialFloatingWindowSettingsUiState(): FloatingWindowSettingsUiState =
    FloatingWindowSettingsUiState(
        bpmTextEnabled = AppSettings.defaultFor(SettingsKeys.BPM_TEXT_ENABLED),
        heartIconEnabled = AppSettings.defaultFor(SettingsKeys.HEART_ICON_ENABLED),
        size = AppSettings.defaultFor(SettingsKeys.FLOATING_SIZE),
        iconSize = AppSettings.defaultFor(SettingsKeys.FLOATING_ICON_SIZE),
        cornerRadius = AppSettings.defaultFor(SettingsKeys.FLOATING_CORNER_RADIUS),
        bgAlpha = AppSettings.defaultFor(SettingsKeys.FLOATING_BG_ALPHA),
        borderAlpha = AppSettings.defaultFor(SettingsKeys.FLOATING_BORDER_ALPHA),
        textColor = AppSettings.defaultFor(SettingsKeys.FLOATING_TEXT_COLOR),
        bgColor = AppSettings.defaultFor(SettingsKeys.FLOATING_BG_COLOR),
        borderColor = AppSettings.defaultFor(SettingsKeys.FLOATING_BORDER_COLOR)
    )
