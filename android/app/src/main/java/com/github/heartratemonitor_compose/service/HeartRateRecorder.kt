package com.github.heartratemonitor_compose.service

import android.content.SharedPreferences
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.db.HeartRateDao
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import com.github.heartratemonitor_compose.data.db.HeartRateSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * 负责历史会话与心率记录的批量写入。
 *
 * 将原本散落在 [BleService] 中的「会话创建 → 缓冲 → 批量 flush → 会话结束」逻辑收敛到一处，
 * 让 [BleService] 只关心“何时连接/断开/收到心率”，而不必直接操作数据库与缓冲队列。
 */
class HeartRateRecorder(
    private val prefs: SharedPreferences,
    private val dao: HeartRateDao,
    private val scope: CoroutineScope
) {

    @Volatile
    private var currentSessionId: Long? = null

    private val pendingRecords = mutableListOf<HeartRateRecord>()
    private val pendingRecordsLock = Any()
    private var recordFlushJob: Job? = null

    companion object {
        private const val BATCH_FLUSH_INTERVAL_MS = 5000L
    }

    /**
     * 连接成功时预先创建会话。历史记录开关关闭时返回 null。
     */
    suspend fun startSession(deviceName: String): Long? {
        if (!isHistoryEnabled()) return null
        val session = HeartRateSession(
            deviceName = deviceName,
            startTime = System.currentTimeMillis()
        )
        currentSessionId = dao.insertSession(session)
        startRecordFlushLoop()
        return currentSessionId
    }

    /**
     * 收到心率数据时调用。若中途开启历史记录，会懒创建会话。
     */
    suspend fun record(bpm: Int, deviceName: String) {
        if (!isHistoryEnabled()) return

        if (currentSessionId == null) {
            val session = HeartRateSession(
                deviceName = deviceName,
                startTime = System.currentTimeMillis()
            )
            currentSessionId = dao.insertSession(session)
            startRecordFlushLoop()
        }

        synchronized(pendingRecordsLock) {
            pendingRecords.add(
                HeartRateRecord(
                    sessionId = currentSessionId!!,
                    timestamp = System.currentTimeMillis(),
                    heartRate = bpm
                )
            )
        }
    }

    /**
     * 断开连接时调用：停止 flush 循环、写入剩余记录、结束当前会话。
     */
    suspend fun endSession() {
        cancelFlushLoop()
        flushPendingRecords()
        currentSessionId?.let { id ->
            dao.endSession(id, System.currentTimeMillis())
            currentSessionId = null
        }
    }

    fun cancelFlushLoop() {
        recordFlushJob?.cancel()
        recordFlushJob = null
    }

    /**
     * 立即把缓冲区中的记录写入数据库。可在任务移除或销毁时调用。
     */
    suspend fun flushPendingRecords() {
        val toFlush: List<HeartRateRecord>
        synchronized(pendingRecordsLock) {
            if (pendingRecords.isEmpty()) return
            toFlush = pendingRecords.toList()
            pendingRecords.clear()
        }
        try {
            dao.insertRecords(toFlush)
        } catch (_: SQLiteConstraintException) {
            // 外键约束失败（如会话已被删除），后续数据不再归属当前会话
            currentSessionId = null
        }
    }

    private fun isHistoryEnabled(): Boolean {
        return prefs.getBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false)
    }

    private fun startRecordFlushLoop() {
        recordFlushJob?.cancel()
        recordFlushJob = scope.launch {
            while (true) {
                delay(BATCH_FLUSH_INTERVAL_MS)
                flushPendingRecords()
            }
        }
    }
}
