package com.github.heartratemonitor_compose.service

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.github.heartratemonitor_compose.data.db.HeartRateDao
import com.github.heartratemonitor_compose.data.db.HeartRateRecord
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 由 [BleService.onDestroy] / [BleService.onKillMemory] 通过 [enqueue] 入队，
 * 替代原先在主线程 runBlocking 同步落盘的做法，彻底消除 ANR 风险。
 * WorkManager 持久化工作请求——进程被杀后下次启动自动补执行，保证数据不丢失。
 *
 * Phase 6 起为 @HiltWorker：HeartRateDao 由 Hilt 注入。
 */
@HiltWorker
class FlushRecordsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: HeartRateDao
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "FlushRecordsWorker"
        private const val KEY_SESSION_IDS = "sessionIds"
        private const val KEY_TIMESTAMPS = "timestamps"
        private const val KEY_HEART_RATES = "heartRates"

        /** 调用不阻塞——仅做内存拷贝与入队操作（微秒级）。 */
        fun enqueue(context: Context, records: List<HeartRateRecord>) {
            if (records.isEmpty()) return
            val data = workDataOf(
                KEY_SESSION_IDS to records.map { it.sessionId }.toLongArray(),
                KEY_TIMESTAMPS to records.map { it.timestamp }.toLongArray(),
                KEY_HEART_RATES to records.map { it.heartRate }.toIntArray()
            )
            val request = OneTimeWorkRequestBuilder<FlushRecordsWorker>()
                .setInputData(data)
                .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 1, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
            Log.i(TAG, "已入队 ${records.size} 条心率记录待落盘")
        }
    }

    override suspend fun doWork(): Result {
        val sessionIds = inputData.getLongArray(KEY_SESSION_IDS) ?: return Result.success()
        val timestamps = inputData.getLongArray(KEY_TIMESTAMPS) ?: return Result.success()
        val heartRates = inputData.getIntArray(KEY_HEART_RATES) ?: return Result.success()

        if (sessionIds.isEmpty()) return Result.success()

        val records = sessionIds.indices.map { i ->
            HeartRateRecord(
                sessionId = sessionIds[i],
                timestamp = timestamps[i],
                heartRate = heartRates[i]
            )
        }

        return try {
            dao.insertRecords(records)
            Log.i(TAG, "落盘完成：${records.size} 条心率记录")
            Result.success()
        } catch (_: SQLiteConstraintException) {
            // 外键约束失败（如会话已被删除），重试无意义，丢弃此批数据
            Log.w(TAG, "外键约束失败，丢弃 ${records.size} 条记录（会话已不存在）")
            Result.success()
        } catch (e: Exception) {
            // 其他异常（磁盘 I/O 错误、数据库锁竞争等）可能为瞬时故障，请求重试
            Log.e(TAG, "落盘失败：${records.size} 条心率记录，将重试", e)
            Result.retry()
        }
    }
}
