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


    /**
     * 连接成功时预先创建会话。历史记录开关关闭时返回 null。
     */
    suspend fun startSession(deviceName: String): Long? {
        if (!isHistoryEnabled()) return null
        // 先结束可能残留的旧会话（如切换设备时旧连接的 cleanup 被纪元守卫跳过），
        // 刷新缓冲记录并关闭旧 session，再创建新 session。
        endSession()
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
     * 从缓冲区中取出所有待写入记录并清空缓冲区，不执行数据库写入。
     *
     * 可在主线程安全调用：仅持锁拷贝+清空（微秒级），不涉及任何 I/O。
     * 取出的记录应由调用方负责持久化（如通过 WorkManager 入队异步落盘），
     * 避免在 [BleService.onDestroy] 等生命周期回调中同步阻塞主线程。
     */
    fun drainPendingRecords(): List<HeartRateRecord> {
        synchronized(pendingRecordsLock) {
            if (pendingRecords.isEmpty()) return emptyList()
            val drained = pendingRecords.toList()
            pendingRecords.clear()
            return drained
        }
    }

    /**
     * 立即把缓冲区中的记录写入数据库。可在任务移除或销毁时调用。
     *
     * 异常分级处理（与 [FlushRecordsWorker] 保持一致）：
     * - [SQLiteConstraintException]：外键约束失败（会话已删除），重置 sessionId，丢弃本批数据。
     * - 其他 [Exception]：磁盘 I/O 错误、数据库锁竞争等瞬时故障，将记录放回缓冲区，
     *   由 [startRecordFlushLoop] 的下一轮 flush 自动重试，避免静默终止循环导致数据丢失。
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
        } catch (e: Exception) {
            // 瞬时故障（磁盘 I/O 错误、数据库锁竞争、数据库已关闭等）：
            // 将记录放回缓冲区头部，下一轮 flush 自动重试，避免静默数据丢失。
            Log.e(TAG, "flush 失败，${toFlush.size} 条记录将下轮重试", e)
            synchronized(pendingRecordsLock) {
                pendingRecords.addAll(0, toFlush)
            }
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
                // flushPendingRecords 内部已做异常分级捕获，
                // 此处 try-catch 为双保险，确保任何未预见异常都不会终止循环。
                try {
                    flushPendingRecords()
                } catch (e: Exception) {
                    Log.e(TAG, "flush 循环未预见异常，跳过本轮", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "HeartRateRecorder"
        private const val BATCH_FLUSH_INTERVAL_MS = 5000L
    }
}
