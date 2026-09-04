package com.github.heartratemonitor_compose.service

import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.db.HeartRateDao
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import com.github.heartratemonitor_compose.data.db.HeartRateSession
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class HeartRateRecorder(
    private val settingsRepository: SettingsRepository,
    private val dao: HeartRateDao,
    private val scope: CoroutineScope
) {

    @Volatile
    private var currentSessionId: Long? = null

    private val pendingRecords = mutableListOf<HeartRateRecord>()
    private val pendingRecordsLock = Any()
    private var recordFlushJob: Job? = null


    suspend fun startSession(deviceName: String): Long? {
        if (!isHistoryEnabled()) return null
        // ⚠️ 反直觉设计：先结束可能残留的旧会话，再创建新 session。
        endSession()
        val session = HeartRateSession(
            deviceName = deviceName,
            startTime = System.currentTimeMillis()
        )
        currentSessionId = dao.insertSession(session)
        trimOldSessionsIfNeeded()
        startRecordFlushLoop()
        return currentSessionId
    }

    suspend fun record(bpm: Int, deviceName: String) {
        if (!isHistoryEnabled()) return

        if (currentSessionId == null) {
            val session = HeartRateSession(
                deviceName = deviceName,
                startTime = System.currentTimeMillis()
            )
            currentSessionId = dao.insertSession(session)
            trimOldSessionsIfNeeded()
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
            // DB 故障时 flush 反复失败→缓冲无限增长，设上限保证有界（约 1 小时心率量，不撑爆 Data 10KB）；put-back 瞬时超限下次 record() 截断。
            if (pendingRecords.size > MAX_PENDING_RECORDS) {
                val dropped = pendingRecords.size - MAX_PENDING_RECORDS
                pendingRecords.subList(0, dropped).clear()
                Log.w(TAG, "待落盘缓冲超过上限（$MAX_PENDING_RECORDS），丢弃最旧的 $dropped 条记录")
            }
        }
    }

    suspend fun endSession() {
        cancelFlushLoop()
        flushPendingRecords()
        currentSessionId?.let { id ->
            try {
                dao.endSession(id, System.currentTimeMillis())
            } catch (e: Exception) {
                // ⚠️ 反直觉设计：teardown 路径无外层兜底，异常必须就地消化（会话未关闭，下次 startSession 修复）。
                Log.e(TAG, "endSession 失败（会话 $id 保持未关闭，下次连接时修复）", e)
            }
            currentSessionId = null
        }
    }

    fun cancelFlushLoop() {
        recordFlushJob?.cancel()
        recordFlushJob = null
    }

    // 主线程安全（仅持锁拷贝清空）；调用方负责持久化，避免生命周期回调同步阻塞。
    fun drainPendingRecords(): List<HeartRateRecord> {
        synchronized(pendingRecordsLock) {
            if (pendingRecords.isEmpty()) return emptyList()
            val drained = pendingRecords.toList()
            pendingRecords.clear()
            return drained
        }
    }

    // 异常分级：SQLiteConstraintException→重置 sessionId 丢弃数据；其他→放回缓冲重试。
    suspend fun flushPendingRecords() {
        val toFlush: List<HeartRateRecord>
        synchronized(pendingRecordsLock) {
            if (pendingRecords.isEmpty()) return
            toFlush = pendingRecords.toList()
            pendingRecords.clear()
        }
        try {
            dao.insertRecords(toFlush)
        } catch (e: CancellationException) {
            // ⚠️ 反直觉设计：放回缓冲头部让后续 drain/endSession 抢救，但必须继续传播取消。
            synchronized(pendingRecordsLock) {
                pendingRecords.addAll(0, toFlush)
            }
            throw e
        } catch (_: SQLiteConstraintException) {
            currentSessionId = null
        } catch (e: Exception) {
            // 瞬时故障：记录放回缓冲头部自动重试，避免静默数据丢失。
            Log.e(TAG, "flush 失败，${toFlush.size} 条记录将下轮重试", e)
            synchronized(pendingRecordsLock) {
                pendingRecords.addAll(0, toFlush)
            }
        }
    }

    private fun isHistoryEnabled(): Boolean {
        return settingsRepository.get(SettingsKeys.HISTORY_RECORDING_ENABLED)
    }

    private fun startRecordFlushLoop() {
        recordFlushJob?.cancel()
        recordFlushJob = scope.launch {
            while (true) {
                delay(BATCH_FLUSH_INTERVAL_MS)
                try {
                    flushPendingRecords()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "flush 循环未预见异常，跳过本轮", e)
                }
            }
        }
    }

    // 创建新 session 后调用，保证历史不超 MAX_SESSIONS 条；异常就地消化。
    private suspend fun trimOldSessionsIfNeeded() {
        try {
            dao.trimOldSessions(MAX_SESSIONS, currentSessionId)
        } catch (e: Exception) {
            Log.e(TAG, "清理旧会话失败", e)
        }
    }

    companion object {
        private const val TAG = "HeartRateRecorder"
        private const val BATCH_FLUSH_INTERVAL_MS = 5000L
        private const val MAX_SESSIONS = 30
        // 缓冲宽松上限（约 1 小时心率量）：DB 故障时无上限会撑爆 Data 10KB 限制，超限时丢弃最旧记录。
        private const val MAX_PENDING_RECORDS = 3600
    }
}
