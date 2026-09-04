package com.github.heartratemonitor_compose.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {
    @Insert
    suspend fun insertSession(session: HeartRateSession): Long

    @Query("UPDATE heart_rate_sessions SET endTime = :endTime WHERE id = :sessionId")
    suspend fun endSession(sessionId: Long, endTime: Long)

    @Insert
    suspend fun insertRecord(record: HeartRateRecord)

    // ⚠️ 反直觉设计：外键约束失败时抛 SQLiteConstraintException，由 HeartRateRecorder.flushPendingRecords 捕获并重置 currentSessionId。
    @Insert
    suspend fun insertRecords(records: List<HeartRateRecord>)

    @Query("SELECT * FROM heart_rate_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<HeartRateSession>>

    @Query("SELECT * FROM heart_rate_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getRecordsForSession(sessionId: Long): List<HeartRateRecord>

    /**
     * 加载全部心率值（已废弃，仅保留兼容）。
     * 优先使用 [getSampledHeartRatesForSession] 减少内存开销。
     */
    @Query("SELECT heartRate FROM heart_rate_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getHeartRatesForSession(sessionId: Long): List<Int>

    // SQL 层等间距采样：利用自增 id 取模（依赖 session 内 id 连续递增，因仅随 session 级联删除）。
    // 调用方需先从 SessionStats 获取 recordCount，计算 step = max(1, recordCount / 50)。
    @Query("""
        SELECT heartRate FROM heart_rate_records
        WHERE sessionId = :sessionId
        AND (
            :step = 1
            OR (id - (SELECT MIN(id) FROM heart_rate_records WHERE sessionId = :sessionId)) % :step = 0
        )
        ORDER BY timestamp ASC
    """)
    suspend fun getSampledHeartRatesForSession(sessionId: Long, step: Int): List<Int>

    @Query("DELETE FROM heart_rate_sessions WHERE id IN (:sessionIds)")
    suspend fun deleteSessionsByIds(sessionIds: List<Long>)

    @Query("DELETE FROM heart_rate_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT * FROM heart_rate_sessions WHERE endTime IS NULL")
    suspend fun getOpenSessions(): List<HeartRateSession>

    @Query("SELECT MAX(timestamp) FROM heart_rate_records WHERE sessionId = :sessionId")
    suspend fun getLastRecordTimestampForSession(sessionId: Long): Long?

    // 删除超出保留数量的最旧会话，在事务中执行避免查询中间态。
    @Transaction
    suspend fun trimOldSessions(keep: Int, excludeSessionId: Long?) {
        val ids = getExcessSessionIds(keep, excludeSessionId)
        if (ids.isNotEmpty()) {
            deleteSessionsByIds(ids)
        }
    }

    @Query("""
        SELECT id FROM heart_rate_sessions
        WHERE (:excludeSessionId IS NULL OR id != :excludeSessionId)
        ORDER BY startTime DESC
        LIMIT -1 OFFSET :keep
    """)
    suspend fun getExcessSessionIds(keep: Int, excludeSessionId: Long?): List<Long>

    // 替代 HistoryScreen 中每个 session 单独查询的 N+1 模式。
    @Query("""
        SELECT sessionId,
               COUNT(*) AS recordCount,
               CAST(AVG(heartRate) AS INTEGER) AS avgHeartRate,
               MAX(heartRate) AS maxHeartRate,
               MIN(heartRate) AS minHeartRate,
               MIN(timestamp) AS firstTimestamp,
               MAX(timestamp) AS lastTimestamp
        FROM heart_rate_records
        GROUP BY sessionId
    """)
    suspend fun getAllSessionStats(): List<SessionStats>
}