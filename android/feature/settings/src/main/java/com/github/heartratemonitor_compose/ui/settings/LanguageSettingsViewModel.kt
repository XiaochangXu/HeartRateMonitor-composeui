package com.github.heartratemonitor_compose.ui.settings

import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 语言设置页面的 ViewModel（MVI 架构）。
 *
 * 职责：
 * - 从 [SettingsRepository] 派生当前语言选择（nullable String，null = 自动跟随系统）。
 *   UiState 是设置真源的派生投影，Flow 回流经 [setState] 归约（状态下行）。
 * - 选择语言事件经 [LanguageSettingsIntent.SelectLanguage] dispatch 上行，
 *   handler 写入 [SettingsRepository]（写后立读语义由乐观快照回流保证）。
 *   语言切换需重启应用才能生效（[LocaleHelper] 在 attachBaseContext 时读取），
 *   故选择后只持久化、不在此处即时切换 UI 语言。
 *
 * 「确认重启」属一次性事件（§3.4 方案 1），经 [confirmRestart] 回调返回值上报，
 * 不进 UiState / SharedFlow。
 */
@HiltViewModel
class LanguageSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : MviViewModel<LanguageSettingsUiState, LanguageSettingsIntent>(
    LanguageSettingsUiState(
        selectedLanguage = settings.getNullable(SettingsKeys.APP_LANGUAGE)
    )
) {

    init {
        viewModelScope.launch {
            settings.observeNullable(SettingsKeys.APP_LANGUAGE).collect { lang ->
                setState { it.copy(selectedLanguage = lang) }
            }
        }
    }

    override suspend fun handleIntent(intent: LanguageSettingsIntent) {
        when (intent) {
            is LanguageSettingsIntent.SelectLanguage -> {
                if (intent.languageTag == null) {
                    settings.remove(SettingsKeys.APP_LANGUAGE)
                } else {
                    settings.set(SettingsKeys.APP_LANGUAGE, intent.languageTag)
                }
            }
        }
    }
}

/** 语言设置页用户意图。 */
sealed interface LanguageSettingsIntent {
    /**
     * 选择语言。null 表示自动跟随系统（删除已保存的键）。
     * 非空值为 BCP 47 语言 Tag（如 "zh-CN"、"en"、"de"）。
     */
    data class SelectLanguage(val languageTag: String?) : LanguageSettingsIntent
}

/** 语言设置页 UI 状态（只读快照）。 */
data class LanguageSettingsUiState(
    /** null = 自动跟随系统，非 null = 已选语言的 BCP 47 Tag */
    val selectedLanguage: String? = null
)
