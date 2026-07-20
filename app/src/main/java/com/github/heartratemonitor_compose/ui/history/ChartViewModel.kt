package com.github.heartratemonitor_compose.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.HistoryRepository
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
class ChartViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HistoryRepository = application.appContainer.historyRepository

    private val _records = MutableStateFlow<List<HeartRateRecord>>(emptyList())
    val records: StateFlow<List<HeartRateRecord>> = _records.asStateFlow()

    fun loadRecords(sessionId: Long) {
        viewModelScope.launch {
            _records.value = repository.getRecordsForSession(sessionId)
        }
    }
}
