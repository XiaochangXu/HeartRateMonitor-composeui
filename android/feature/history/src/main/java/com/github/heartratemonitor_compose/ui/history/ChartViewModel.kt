package com.github.heartratemonitor_compose.ui.history

import com.github.heartratemonitor_compose.data.model.HeartRateRecordInfo
import com.github.heartratemonitor_compose.data.repository.HistoryRepository
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * 会话心率记录归约进单一 UiState，
 * UI 层仅订阅记录状态并经 Intent 触发加载。
 * 依赖由 Hilt 构造注入（Phase 3 起）。
 */
@HiltViewModel
class ChartViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val fairMemoryReceiver: FairMemoryReceiver
) : MviViewModel<ChartUiState, ChartIntent>(ChartUiState()),
    FairMemoryReceiver.MemoryListener {

    init {
        fairMemoryReceiver.addMemoryListener(this)
    }

    override suspend fun handleIntent(intent: ChartIntent) {
        when (intent) {
            is ChartIntent.LoadRecords -> {
                val records = repository.getRecordsForSession(intent.sessionId).toImmutableList()
                setState { it.copy(records = records) }
            }
        }
    }

    /** 公平运行内存 TRIM：清空详情页心率记录缓存，释放内存。 */
    override fun onTrimMemory(notifyType: Int) {
        setState { it.copy(records = persistentListOf()) }
    }

    /** 公平运行内存 KILL：历史数据已由 Room 持久化，无需额外保存。 */
    override fun onKillMemory() {
    }

    override fun onCleared() {
        super.onCleared()
        fairMemoryReceiver.removeMemoryListener(this)
    }
}

/** 心率历史详情页用户意图。 */
sealed interface ChartIntent {
    data class LoadRecords(val sessionId: Long) : ChartIntent
}

/** 心率历史详情页 UI 状态（只读快照）。 */
data class ChartUiState(
    val records: ImmutableList<HeartRateRecordInfo> = persistentListOf()
)
