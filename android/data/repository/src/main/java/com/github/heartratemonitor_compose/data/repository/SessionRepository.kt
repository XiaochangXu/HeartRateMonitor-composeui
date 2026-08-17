package com.github.heartratemonitor_compose.data.repository

import com.github.heartratemonitor_compose.data.db.HeartRateDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 将 UI 层对 AppDatabase 的直接访问下沉到 data/repository 层，
 * 启动时修复未正常关闭的心率会话。
 *
 * DAO 由 Hilt 构造注入（Phase 2 起，替代 AppContainer 手工装配）。
 */
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