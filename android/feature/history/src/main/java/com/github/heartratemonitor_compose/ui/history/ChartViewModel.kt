package com.github.heartratemonitor_compose.ui.history

import android.net.Uri
import com.github.heartratemonitor_compose.data.model.HeartRateRecordInfo
import com.github.heartratemonitor_compose.data.repository.HistoryExporter
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
 * 依赖由 Hilt 构造注入。
 */
@HiltViewModel
class ChartViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val exporter: HistoryExporter,
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
            is ChartIntent.ExportCsv -> {
                val event = try {
                    ChartExportEvent.Success(exporter.exportSessionCsv(intent.sessionId, intent.uri))
                } catch (e: Exception) {
                    // 导出失败必须用户可见：转一次性事件提示，禁止静默吞掉
                    ChartExportEvent.Failure(e.message ?: e.toString())
                }
                setState { it.copy(exportEvent = event) }
            }
            ChartIntent.ConsumeExportEvent -> setState { it.copy(exportEvent = null) }
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
    data class ExportCsv(val sessionId: Long, val uri: Uri) : ChartIntent

    /** 消费一次性导出结果事件（契约 10 §3.4 方案 2：可空字段 + Consume）。 */
    data object ConsumeExportEvent : ChartIntent
}

/** 一次性导出结果事件，不重放。 */
sealed interface ChartExportEvent {
    data class Success(val count: Int) : ChartExportEvent
    data class Failure(val message: String) : ChartExportEvent
}

/** 心率历史详情页 UI 状态（只读快照）。 */
data class ChartUiState(
    val records: ImmutableList<HeartRateRecordInfo> = persistentListOf(),
    // 一次性导出结果（契约 10 §3.4 方案 2：可空字段 + Consume Intent），不进重放流
    val exportEvent: ChartExportEvent? = null
)
