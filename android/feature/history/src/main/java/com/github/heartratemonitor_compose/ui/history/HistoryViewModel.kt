package com.github.heartratemonitor_compose.ui.history

import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.model.HeartRateSessionInfo
import com.github.heartratemonitor_compose.data.repository.HistoryRepository
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet

/**
 * MVI 架构。历史会话列表、统计/迷你图采样、多选态归约进单一 UiState
 * （多选态为业务状态：影响删除行为，自 Composable 上提）。
 * 依赖由 Hilt 构造注入。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val fairMemoryReceiver: FairMemoryReceiver
) : MviViewModel<HistoryUiState, HistoryIntent>(HistoryUiState()),
    FairMemoryReceiver.MemoryListener {

    /**
     * 删除结果一次性回调：Composable 注册/注销（一次性事件不进 UiState，避免重组重放）。
     * ⚠️ 反直觉设计：删除异常必须在 VM 内捕获（dispatch 为即发即忘，DAO 异常回传不到 Composable 的 try/catch，未捕获会崩进程）。
     * Toast 只能在删除真正完成后触发，不能紧跟 dispatch 弹出。
     */
    @Volatile
    var deleteResultListener: ((HistoryDeleteResult) -> Unit)? = null

    init {
        viewModelScope.launch {
            repository.allSessions.collect { list ->
                val immutableList = list.toImmutableList()
                setState { it.copy(sessions = immutableList, isLoading = false) }
                loadStatsForSessions(immutableList)
            }
        }
        fairMemoryReceiver.addMemoryListener(this)
    }

    override suspend fun handleIntent(intent: HistoryIntent) {
        when (intent) {
            is HistoryIntent.DeleteSessions -> deleteSessions(intent.ids)
            is HistoryIntent.EnterMultiSelect ->
                setState { it.copy(isMultiSelectMode = true, selectedIds = persistentSetOf(intent.initialId)) }
            HistoryIntent.ExitMultiSelect ->
                setState { it.copy(isMultiSelectMode = false, selectedIds = persistentSetOf()) }
            is HistoryIntent.ToggleSelection -> setState { state ->
                val current = state.selectedIds
                val updated =
                    if (current.contains(intent.sessionId)) current - intent.sessionId
                    else current + intent.sessionId
                // 取消选中后无剩余选中项时是否自动退出多选
                //（原页面语义：复选框回调退出、卡片点击不退出）；联动字段一次归约
                val stillMultiSelect = state.isMultiSelectMode && !(intent.exitIfEmpty && updated.isEmpty())
                state.copy(selectedIds = updated.toImmutableSet(), isMultiSelectMode = stillMultiSelect)
            }
            HistoryIntent.SelectAll ->
                setState { it.copy(selectedIds = currentState.sessions.map { s -> s.id }.toImmutableSet()) }
        }
    }

    private suspend fun deleteSessions(ids: List<Long>) {
        val count = ids.size
        val result = try {
            repository.deleteSessionsByIds(ids)
            HistoryDeleteResult.Deleted(count)
        } catch (e: CancellationException) {
            throw e // ⚠️ 反直觉设计：取消必须重抛，禁止并入普通 Exception 分支吞掉
        } catch (e: Exception) {
            HistoryDeleteResult.Failed(e.message ?: e.javaClass.simpleName)
        }
        // 仅删除成功后退出多选：失败时保留选中项，用户可直接重试
        if (result is HistoryDeleteResult.Deleted) {
            setState { it.copy(isMultiSelectMode = false, selectedIds = persistentSetOf()) }
        }
        deleteResultListener?.invoke(result)
    }

    /**
     * 增量加载会话预览数据：只查询缓存中不存在的 session，复用已有数据。
     *
     * Room 的 allSessions Flow 在表内容变化时（如删除一条会话）会重新发射整个列表，
     * 若无条件全量重查，删除 1 条时剩余 29 条未变化的采样数据会被全部重新查询。
     * 此方法通过 diff 缓存，仅对新出现的 session 发起采样查询，已缓存的直接复用。
     */
    private suspend fun loadStatsForSessions(currentSessions: List<HeartRateSessionInfo>) {
        if (currentSessions.isEmpty()) {
            setState { it.copy(previewDataMap = persistentMapOf()) }
            return
        }

        val currentSessionIds = currentSessions.map { it.id }.toSet()
        val existingMap = currentState.previewDataMap

        // 只查询：当前列表中有、但缓存中没有的 session
        val newSessions = currentSessions.filter { it.id !in existingMap }

        if (newSessions.isEmpty()) {
            // 无新增 session：仅移除已删除 session 的预览数据，复用其余缓存
            val retainedMap = existingMap.filterKeys { it in currentSessionIds }.toImmutableMap()
            if (retainedMap.size != existingMap.size) {
                setState { it.copy(previewDataMap = retainedMap) }
            }
            return
        }

        // 只对新增 session 查询统计与采样数据，已有 session 的预览数据全部复用
        val statsList = repository.getSessionStats()
        val statsMap = statsList.associateBy { it.sessionId }

        // 并发查询新增 session 的采样数据，替代串行 for 循环中的逐个 suspend 调用。
        // coroutineScope 保证结构化并发：任一查询异常不会泄漏，且随调用方取消而取消。
        // Room 内部连接池（默认 4 并发）限制实际并发度，超出部分自动排队。
        val newPreviews = coroutineScope {
            newSessions.map { session ->
                val stats = statsMap[session.id]
                if (stats == null || stats.recordCount <= 0) {
                    return@map null
                }
                async {
                    val step = maxOf(1, stats.recordCount / 50)
                    val samples = repository.getSampledHeartRatesForSession(session.id, step).toImmutableList()
                    session.id to SessionPreviewData(
                        recordCount = stats.recordCount,
                        avgHeartRate = stats.avgHeartRate?.toDouble() ?: 0.0,
                        minHeartRate = stats.minHeartRate ?: 0,
                        maxHeartRate = stats.maxHeartRate ?: 0,
                        heartRateSamples = samples
                    )
                }
            }.filterNotNull().awaitAll()
        }

        // 合并：保留缓存中仍然存在的 session 的预览数据 + 新查询的数据
        val mergedMap = buildMap {
            // 先放入缓存中仍然存在的预览数据（同时移除已删除 session 的条目）
            existingMap.forEach { (id, data) ->
                if (id in currentSessionIds) put(id, data)
            }
            // 再放入新查询的预览数据
            newPreviews.forEach { (id, data) -> put(id, data) }
        }.toImmutableMap()

        setState { it.copy(previewDataMap = mergedMap) }
    }

    /** 公平运行内存 TRIM：清空历史预览采样数据，释放内存。 */
    override fun onTrimMemory(notifyType: Int) {
        setState { it.copy(previewDataMap = persistentMapOf()) }
    }

    /** 公平运行内存 KILL：历史数据已由 Room 持久化，无需额外保存。 */
    override fun onKillMemory() {
    }

    override fun onCleared() {
        super.onCleared()
        fairMemoryReceiver.removeMemoryListener(this)
    }
}

/**
 * 删除结果一次性事件（VM 无 Context，文案由 UI 侧映射，不进 UiState）。
 */
sealed interface HistoryDeleteResult {
    data class Deleted(val count: Int) : HistoryDeleteResult

    data class Failed(val reason: String) : HistoryDeleteResult
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
    val sessions: ImmutableList<HeartRateSessionInfo> = persistentListOf(),
    val previewDataMap: ImmutableMap<Long, SessionPreviewData> = persistentMapOf(),
    val isMultiSelectMode: Boolean = false,
    val selectedIds: ImmutableSet<Long> = persistentSetOf(),
    val isLoading: Boolean = true
)

data class SessionPreviewData(
    val recordCount: Int,
    val avgHeartRate: Double,
    val minHeartRate: Int,
    val maxHeartRate: Int,
    val heartRateSamples: ImmutableList<Int>
)
