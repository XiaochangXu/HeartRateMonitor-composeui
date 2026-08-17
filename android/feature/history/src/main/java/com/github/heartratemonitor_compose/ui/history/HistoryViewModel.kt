package com.github.heartratemonitor_compose.ui.history

import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.model.HeartRateSessionInfo
import com.github.heartratemonitor_compose.data.repository.HistoryRepository
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import javax.inject.Inject

/**
 * MVI 架构，Phase 2。历史会话列表、统计/迷你图采样、多选态归约进单一 UiState
 * （多选态为业务状态：影响删除行为，D3 自 Composable 上提）。
 * 依赖由 Hilt 构造注入（Phase 3 起）。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val fairMemoryReceiver: FairMemoryReceiver
) : MviViewModel<HistoryUiState, HistoryIntent>(HistoryUiState()),
    FairMemoryReceiver.MemoryListener {

    init {
        viewModelScope.launch {
            repository.allSessions.collect { list ->
                setState { it.copy(sessions = list, isLoading = false) }
                loadStatsForSessions(list)
            }
        }
        fairMemoryReceiver.addMemoryListener(this)
    }

    override suspend fun handleIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.DeleteSessions -> repository.deleteSessionsByIds(intent.ids)
            is HistoryIntent.EnterMultiSelect ->
                setState { it.copy(isMultiSelectMode = true, selectedIds = setOf(intent.initialId)) }
            HistoryIntent.ExitMultiSelect ->
                setState { it.copy(isMultiSelectMode = false, selectedIds = emptySet()) }
            is HistoryIntent.ToggleSelection -> setState { state ->
                val current = state.selectedIds
                val updated =
                    if (current.contains(intent.sessionId)) current - intent.sessionId
                    else current + intent.sessionId
                // 取消选中后无剩余选中项时是否自动退出多选
                //（原页面语义：复选框回调退出、卡片点击不退出）；联动字段一次归约
                val stillMultiSelect = state.isMultiSelectMode && !(intent.exitIfEmpty && updated.isEmpty())
                state.copy(selectedIds = updated, isMultiSelectMode = stillMultiSelect)
            }
            HistoryIntent.SelectAll ->
                setState { it.copy(selectedIds = currentState.sessions.map { s -> s.id }.toSet()) }
        }
    }

    private suspend fun loadStatsForSessions(currentSessions: List<HeartRateSessionInfo>) {
        if (currentSessions.isEmpty()) {
            setState { it.copy(previewDataMap = emptyMap()) }
            return
        }
        val statsList = repository.getSessionStats()
        val statsMap = statsList.associateBy { it.sessionId }

        val previewMap = mutableMapOf<Long, SessionPreviewData>()
        for (session in currentSessions) {
            val stats = statsMap[session.id] ?: continue
            if (stats.recordCount <= 0) continue

            val heartRates = repository.getHeartRatesForSession(session.id)
            val step = maxOf(1, heartRates.size / 50)
            val samples = heartRates.filterIndexed { index, _ -> index % step == 0 }
            previewMap[session.id] = SessionPreviewData(
                recordCount = stats.recordCount,
                avgHeartRate = stats.avgHeartRate?.toDouble() ?: 0.0,
                minHeartRate = stats.minHeartRate ?: 0,
                maxHeartRate = stats.maxHeartRate ?: 0,
                heartRateSamples = samples
            )
        }
        setState { it.copy(previewDataMap = previewMap) }
    }

    /** 公平运行内存 TRIM：清空历史预览采样数据，释放内存。 */
    override fun onTrimMemory(notifyType: Int) {
        setState { it.copy(previewDataMap = emptyMap()) }
    }

    /** 公平运行内存 KILL：历史数据已由 Room 持久化，无需额外保存。 */
    override fun onKillMemory() {
    }

    override fun onCleared() {
        super.onCleared()
        fairMemoryReceiver.removeMemoryListener(this)
    }
}

/** 历史记录页用户意图。 */
sealed interface HistoryIntent {
    data class DeleteSessions(val ids: List<Long>) : HistoryIntent

    /** 长按进入多选并选中触发项。 */
    data class EnterMultiSelect(val initialId: Long) : HistoryIntent

    /** 退出多选并清空选中集。 */
    data object ExitMultiSelect : HistoryIntent

    /**
     * 切换单项选中。
     * @param exitIfEmpty 取消选中后无剩余选中项时是否自动退出多选
     */
    data class ToggleSelection(val sessionId: Long, val exitIfEmpty: Boolean) : HistoryIntent

    /** 全选当前会话列表。 */
    data object SelectAll : HistoryIntent
}

/** 历史记录页 UI 状态（只读快照）。 */
data class HistoryUiState(
    val sessions: List<HeartRateSessionInfo> = emptyList(),
    val previewDataMap: Map<Long, SessionPreviewData> = emptyMap(),
    val isMultiSelectMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true
)

data class SessionPreviewData(
    val recordCount: Int,
    val avgHeartRate: Double,
    val minHeartRate: Int,
    val maxHeartRate: Int,
    val heartRateSamples: List<Int>
)
