package com.github.heartratemonitor_compose.data.repository

import android.content.Context
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.db.HeartRateSession
import com.github.heartratemonitor_compose.data.db.SessionStats
import kotlinx.coroutines.flow.Flow

/**
 * 心率历史数据访问封装。
 *
 * 将 UI 层对 [AppDatabase][com.github.heartratemonitor_compose.data.db.AppDatabase] 的直接访问下沉到 repository 层，
 * 统一暴露会话列表、统计信息、记录查询与批量删除能力。
 *
 * 通过 [AppContainer][com.github.heartratemonitor_compose.data.di.AppContainer] 注入
 * [AppDatabase][com.github.heartratemonitor_compose.data.db.AppDatabase]，
 * 消除散布的 `AppDatabase.getDatabase(context)` 直接调用。
 */
class HistoryRepository(context: Context) {

    private val dao = context.applicationContext.appContainer.appDatabase.heartRateDao()

    val allSessions: Flow<List<HeartRateSession>> = dao.getAllSessions()

    suspend fun getSessionStats(): List<SessionStats> = dao.getAllSessionStats()

    suspend fun getHeartRatesForSession(sessionId: Long): List<Int> =
        dao.getHeartRatesForSession(sessionId)

    suspend fun getRecordsForSession(sessionId: Long): List<HeartRateRecord> =
        dao.getRecordsForSession(sessionId)

    suspend fun deleteSessionsByIds(ids: List<Long>) = dao.deleteSessionsByIds(ids)
}
