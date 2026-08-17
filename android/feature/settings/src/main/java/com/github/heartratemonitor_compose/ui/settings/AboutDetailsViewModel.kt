package com.github.heartratemonitor_compose.ui.settings

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 关于详情页的 ViewModel（MVI 架构）。
 *
 * 检查更新流程（发起/取消/结果）归约进单一 [AboutDetailsUiState]，
 * 替代原页面经 SettingsDependencies EntryPoint 直取 UpdateChecker 的写法；
 * 更新结果弹窗的显隐属 UI 瞬时态，保留 Composable。
 */
@HiltViewModel
class AboutDetailsViewModel @Inject constructor(
    private val updateChecker: UpdateChecker,
    @ApplicationContext private val appContext: Context
) : MviViewModel<AboutDetailsUiState, AboutDetailsIntent>(AboutDetailsUiState()) {

    /** 检查任务句柄：重复发起/弹窗关闭时须取消旧任务，避免结果迟到重弹弹窗 */
    private var checkJob: Job? = null

    override suspend fun handleIntent(intent: AboutDetailsIntent) {
        when (intent) {
            is AboutDetailsIntent.CheckUpdate -> {
                checkJob?.cancel()
                setState { it.copy(isChecking = true, updateResult = null) }
                checkJob = viewModelScope.launch {
                    val result = updateChecker.check(appContext, intent.currentVersion)
                    setState { it.copy(isChecking = false, updateResult = result) }
                }
            }
            AboutDetailsIntent.CancelCheck -> {
                checkJob?.cancel()
                checkJob = null
                setState { it.copy(isChecking = false) }
            }
        }
    }
}

/** 关于详情页用户意图。 */
sealed interface AboutDetailsIntent {
    data class CheckUpdate(val currentVersion: String) : AboutDetailsIntent
    data object CancelCheck : AboutDetailsIntent
}

/** 关于详情页 UI 状态（只读快照）。 */
data class AboutDetailsUiState(
    val isChecking: Boolean = false,
    val updateResult: UpdateChecker.Result? = null
)
