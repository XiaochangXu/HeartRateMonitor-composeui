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


/**
 * 将原本散落在 [BleService] 中的「会话创建 → 缓冲 → 批量 flush → 会话结束」逻辑收敛到一处，
 * 让 [BleService] 只关心连接/断开/心率接收，而不必直接操作数据库与缓冲队列。
 */
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

    suspend fun endSession() {
        cancelFlushLoop()
        flushPendingRecords()
        currentSessionId?.let { id ->
            try {
                dao.endSession(id, System.currentTimeMillis())
            } catch (e: Exception) {
                // teardown 路径（connectionJob 的 NonCancellable finally）无外层兜底，
                // DAO 异常必须就地消化，避免协程异常逸出导致进程崩溃。
                // 会话保持未关闭状态，由下次 startSession 的 endSession() 修复。
                Log.e(TAG, "endSession 失败（会话 $id 保持未关闭，下次连接时修复）", e)
            }
            currentSessionId = null
        }
    }

    fun cancelFlushLoop() {
        recordFlushJob?.cancel()
        recordFlushJob = null
    }

    /**
     * 可在主线程安全调用：仅持锁拷贝+清空（微秒级），不涉及任何 I/O。
     * 取出的记录应由调用方负责持久化，避免在生命周期回调中同步阻塞主线程。
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
     * 异常分级处理：
     * - [SQLiteConstraintException]：外键约束失败（会话已删除），重置 sessionId，丢弃本批数据。
     * - 其他 [Exception]：磁盘 I/O 错误等瞬时故障，将记录放回缓冲区，下轮 flush 自动重试。
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
        } catch (e: CancellationException) {
            // 结构化取消（关停/设备切换）：记录放回缓冲区头部，让后续 drain/endSession
            // 有机会抢救，同时必须继续传播取消，不能当普通 IO 故障吞掉。
            synchronized(pendingRecordsLock) {
                pendingRecords.addAll(0, toFlush)
            }
            throw e
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
        return settingsRepository.get(SettingsKeys.HISTORY_RECORDING_ENABLED)
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
                } catch (e: CancellationException) {
                    throw e
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
