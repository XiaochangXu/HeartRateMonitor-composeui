package com.github.heartratemonitor_compose.data.repository

import android.content.Context
import com.github.heartratemonitor_compose.data.db.AppDatabase

/**
 * 会话数据访问封装。
 *
 * 将 UI 层对 [AppDatabase] 的直接访问下沉到 data/repository 层，
 * 启动时修复未正常关闭的心率会话。
 */
object SessionRepository {

    suspend fun closeOpenSessions(context: Context) {
        val db = AppDatabase.getDatabase(context.applicationContext)
        val dao = db.heartRateDao()
        val openSessions = dao.getOpenSessions()
        for (session in openSessions) {
            val lastTimestamp = dao.getLastRecordTimestampForSession(session.id)
            dao.endSession(session.id, lastTimestamp ?: session.startTime)
        }
    }
}
