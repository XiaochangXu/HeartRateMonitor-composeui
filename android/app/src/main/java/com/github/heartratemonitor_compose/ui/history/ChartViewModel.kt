package com.github.heartratemonitor_compose.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.HistoryRepository
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 心率历史详情图表 ViewModel。
 *
 * 将会话心率记录的加载从 Composable 移入 ViewModel，
 * UI 层仅订阅记录状态并触发加载。
 */
class ChartViewModel(application: Application) : AndroidViewModel(application),
    FairMemoryReceiver.MemoryListener {

    private val repository: HistoryRepository = application.appContainer.historyRepository

    private val _records = MutableStateFlow<List<HeartRateRecord>>(emptyList())
    val records: StateFlow<List<HeartRateRecord>> = _records.asStateFlow()

    init {
        FairMemoryReceiver.getInstance().addMemoryListener(this)
    }

    fun loadRecords(sessionId: Long) {
        viewModelScope.launch {
            _records.value = repository.getRecordsForSession(sessionId)
        }
    }

    /** 公平运行内存 TRIM：清空详情页心率记录缓存，释放内存。 */
    override fun onTrimMemory(notifyType: Int) {
        _records.value = emptyList()
    }

    /** 公平运行内存 KILL：历史数据已由 Room 持久化，无需额外保存。 */
    override fun onKillMemory() {
    }

    override fun onCleared() {
        super.onCleared()
        FairMemoryReceiver.getInstance().removeMemoryListener(this)
    }
}
