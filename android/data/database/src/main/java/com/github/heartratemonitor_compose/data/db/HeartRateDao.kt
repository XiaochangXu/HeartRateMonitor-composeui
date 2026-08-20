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

    /**
     * 迷你图表采样专用。加载全部心率值（已废弃，仅保留兼容）。
     * 优先使用 [getSampledHeartRatesForSession] 减少内存开销。
     */
    @Query("SELECT heartRate FROM heart_rate_records WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getHeartRatesForSession(sessionId: Long): List<Int>

    /**
     * 迷你图表采样专用：在 SQL 层完成等间距采样，避免将全部心率记录加载到 Kotlin 内存。
     *
     * 利用自增 id（INTEGER PRIMARY KEY = rowid 别名）做取模采样：
     * 心率记录按时间顺序插入，id 连续递增，取模后等间距分布。
     * 调用方需先从 SessionStats 获取 recordCount，计算 step = max(1, recordCount / 50)。
     * 返回最多 ceil(recordCount / step) 个心率值（通常 ≤ 50）。
     *
     * 注意：此查询依赖 id 连续递增（无删除操作），当前应用中心率记录仅随 session
     * 级联删除，不会单独删除，故 id 在 session 内是连续的。
     */
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