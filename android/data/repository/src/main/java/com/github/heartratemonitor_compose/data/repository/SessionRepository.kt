package com.github.heartratemonitor_compose.data.repository

import com.github.heartratemonitor_compose.data.db.HeartRateDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(private val dao: HeartRateDao) {

    suspend fun closeOpenSessions() {
        val openSessions = dao.getOpenSessions()
        for (session in openSessions) {
            val lastTimestamp = dao.getLastRecordTimestampForSession(session.id)
            dao.endSession(session.id, lastTimestamp ?: session.startTime)
        }
    }
}