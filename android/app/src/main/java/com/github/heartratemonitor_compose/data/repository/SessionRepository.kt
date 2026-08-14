package com.github.heartratemonitor_compose.data.repository

import android.app.Application
import com.github.heartratemonitor_compose.data.di.appContainer

/**
 * 会话数据访问封装。
 *
 * 将 UI 层对 [AppDatabase][com.github.heartratemonitor_compose.data.db.AppDatabase] 的直接访问下沉到 data/repository 层，
 * 启动时修复未正常关闭的心率会话。
 *
 * 通过 [AppContainer] 注入 [AppDatabase][com.github.heartratemonitor_compose.data.db.AppDatabase]，
 * 消除散布的 `AppDatabase.getDatabase(context)` 直接调用。
 */
class SessionRepository(private val application: Application) {

    private val dao = application.appContainer.appDatabase.heartRateDao()

    suspend fun closeOpenSessions() {
        val openSessions = dao.getOpenSessions()
        for (session in openSessions) {
            val lastTimestamp = dao.getLastRecordTimestampForSession(session.id)
            dao.endSession(session.id, lastTimestamp ?: session.startTime)
        }
    }
}
