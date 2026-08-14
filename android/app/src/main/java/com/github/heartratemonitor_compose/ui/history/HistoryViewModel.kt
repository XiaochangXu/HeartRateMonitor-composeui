package com.github.heartratemonitor_compose.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.db.HeartRateSession
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.HistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.github.heartratemonitor_compose.service.FairMemoryReceiver

/**
 * 历史记录页面 ViewModel。
 *
 * 将历史会话列表、统计信息与迷你图表采样数据的管理从 Composable 移入 ViewModel，
 * UI 层仅订阅状态并触发删除操作。
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application),
    FairMemoryReceiver.MemoryListener {

    private val repository: HistoryRepository = application.appContainer.historyRepository

    val sessions: StateFlow<List<HeartRateSession>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _previewDataMap = MutableStateFlow<Map<Long, SessionPreviewData>>(emptyMap())
    val previewDataMap: StateFlow<Map<Long, SessionPreviewData>> = _previewDataMap.asStateFlow()

    init {
        viewModelScope.launch {
            sessions.collect { loadStatsForSessions(it) }
        }
        FairMemoryReceiver.getInstance().addMemoryListener(this)
    }

    fun loadStats() {
        viewModelScope.launch {
            loadStatsForSessions(sessions.value)
        }
    }

    private suspend fun loadStatsForSessions(currentSessions: List<HeartRateSession>) {
        if (currentSessions.isEmpty()) {
            _previewDataMap.value = emptyMap()
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
        _previewDataMap.value = previewMap
    }

    fun deleteSessions(ids: List<Long>) {
        viewModelScope.launch {
            repository.deleteSessionsByIds(ids)
        }
    }

    /** 公平运行内存 TRIM：清空历史预览采样数据，释放内存。 */
    override fun onTrimMemory(notifyType: Int) {
        _previewDataMap.value = emptyMap()
    }

    /** 公平运行内存 KILL：历史数据已由 Room 持久化，无需额外保存。 */
    override fun onKillMemory() {
    }

    override fun onCleared() {
        super.onCleared()
        FairMemoryReceiver.getInstance().removeMemoryListener(this)
    }
}

data class SessionPreviewData(
    val recordCount: Int,
    val avgHeartRate: Double,
    val minHeartRate: Int,
    val maxHeartRate: Int,
    val heartRateSamples: List<Int>
)
