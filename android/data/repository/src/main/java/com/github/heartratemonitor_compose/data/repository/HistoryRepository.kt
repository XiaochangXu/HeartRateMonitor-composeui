package com.github.heartratemonitor_compose.data.repository

import com.github.heartratemonitor_compose.data.db.HeartRateDao
import com.github.heartratemonitor_compose.data.model.HeartRateRecordInfo
import com.github.heartratemonitor_compose.data.model.HeartRateSessionInfo
import com.github.heartratemonitor_compose.data.model.SessionStatsInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HistoryRepository @Inject constructor(private val dao: HeartRateDao) {
    val allSessions: Flow<List<HeartRateSessionInfo>> =
        dao.getAllSessions().map { sessions -> sessions.map { it.toInfo() } }

    suspend fun getSessionStats(): List<SessionStatsInfo> =
        dao.getAllSessionStats().map { it.toInfo() }

    suspend fun getHeartRatesForSession(sessionId: Long): List<Int> =
        dao.getHeartRatesForSession(sessionId)

    suspend fun getSampledHeartRatesForSession(sessionId: Long, step: Int): List<Int> =
        dao.getSampledHeartRatesForSession(sessionId, step)

    suspend fun getRecordsForSession(sessionId: Long): List<HeartRateRecordInfo> =
        dao.getRecordsForSession(sessionId).map { it.toInfo() }

    suspend fun deleteSessionsByIds(ids: List<Long>) = dao.deleteSessionsByIds(ids)
}