package com.github.heartratemonitor_compose.data.repository

import com.github.heartratemonitor_compose.data.db.HeartRateDao
import com.github.heartratemonitor_compose.data.model.HeartRateRecordInfo
import com.github.heartratemonitor_compose.data.model.HeartRateSessionInfo
import com.github.heartratemonitor_compose.data.model.SessionStatsInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 将 UI 层对 AppDatabase 的直接访问下沉到 repository 层，
 * 对外返回 Domain Model，避免 Room Entity 泄漏到 UI/ViewModel 层。
 *
 * DAO 由 Hilt 构造注入（Phase 2 起，替代 AppContainer 手工装配）。
 */
@Singleton
class HistoryRepository @Inject constructor(private val dao: HeartRateDao) {
    val allSessions: Flow<List<HeartRateSessionInfo>> =
        dao.getAllSessions().map { sessions -> sessions.map { it.toInfo() } }

    suspend fun getSessionStats(): List<SessionStatsInfo> =
        dao.getAllSessionStats().map { it.toInfo() }

    suspend fun getHeartRatesForSession(sessionId: Long): List<Int> =
        dao.getHeartRatesForSession(sessionId)

    suspend fun getRecordsForSession(sessionId: Long): List<HeartRateRecordInfo> =
        dao.getRecordsForSession(sessionId).map { it.toInfo() }

    suspend fun deleteSessionsByIds(ids: List<Long>) = dao.deleteSessionsByIds(ids)
}