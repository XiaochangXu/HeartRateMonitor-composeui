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
 * @HiltWorker：[BleService.onDestroy]/[BleService.onKillMemory] 入队，
 * WorkManager 持久化——进程被杀后下次启动自动补执行，不丢数据。
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

        /**
         * ⚠️ 反直觉设计：WorkManager Data 上限 10KB，DB 故障时缓冲可能大量累积，
         * 按 250 条/片分片入队（~5KB），避免打包超限抛 IllegalStateException 导致进程崩溃。
         */
        private const val MAX_RECORDS_PER_REQUEST = 250

        /** 调用不阻塞——仅做内存拷贝与入队（微秒级）。 */
        fun enqueue(context: Context, records: List<HeartRateRecord>) {
            if (records.isEmpty()) return
            val chunks = records.chunked(MAX_RECORDS_PER_REQUEST)
            for (chunk in chunks) {
                val data = workDataOf(
                    KEY_SESSION_IDS to chunk.map { it.sessionId }.toLongArray(),
                    KEY_TIMESTAMPS to chunk.map { it.timestamp }.toLongArray(),
                    KEY_HEART_RATES to chunk.map { it.heartRate }.toIntArray()
                )
                val request = OneTimeWorkRequestBuilder<FlushRecordsWorker>()
                    .setInputData(data)
                    .setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR, 1, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            }
            Log.i(TAG, "已入队 ${records.size} 条心率记录待落盘（${chunks.size} 个分片）")
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
            Log.w(TAG, "外键约束失败，丢弃 ${records.size} 条记录（会话已不存在）")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "落盘失败：${records.size} 条心率记录，将重试", e)
            Result.retry()
        }
    }
}
