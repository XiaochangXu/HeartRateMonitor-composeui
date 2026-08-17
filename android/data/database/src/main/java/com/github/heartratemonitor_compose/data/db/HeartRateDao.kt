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

    /**
     * 外键约束失败（如 session 已被删除）时抛出 SQLiteConstraintException，
     * 由 HeartRateRecorder.flushPendingRecords 捕获并重置 currentSessionId。
     */
    @Insert
    suspend fun insertRecords(records: List<HeartRateRecord>)

    @Query("SELECT * FROM heart_rate_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<HeartRateSession>>

    @Query("SELECT * FROM heart_rate_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getRecordsForSession(sessionId: Long): List<HeartRateRecord>

    /** 迷你图表采样专用。 */
    @Query("SELECT heartRate FROM heart_rate_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getHeartRatesForSession(sessionId: Long): List<Int>

    @Query("DELETE FROM heart_rate_sessions WHERE id IN (:sessionIds)")
    suspend fun deleteSessionsByIds(sessionIds: List<Long>)

    @Query("DELETE FROM heart_rate_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT * FROM heart_rate_sessions WHERE endTime IS NULL")
    suspend fun getOpenSessions(): List<HeartRateSession>

    @Query("SELECT MAX(timestamp) FROM heart_rate_records WHERE sessionId = :sessionId")
    suspend fun getLastRecordTimestampForSession(sessionId: Long): Long?

    /**
     * 替代 HistoryScreen 中对每个 session 单独查询的 N+1 模式，单次 SQL 完成聚合。
     */
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