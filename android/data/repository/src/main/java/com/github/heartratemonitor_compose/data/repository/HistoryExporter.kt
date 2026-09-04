package com.github.heartratemonitor_compose.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 心率历史导出器：将会话明细序列化为 CSV 并写入 SAF 选定的 Uri。
 *
 * 归属 data/repository（契约 9 决策表：系统封装），UI/VM 只传 sessionId 与目标 Uri，
 * 数据组装与文件写入均在此完成，避免 Entity 或原始记录泄漏出数据层。
 */
@Singleton
class HistoryExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: HistoryRepository
) {
    /**
     * 导出指定会话为 CSV，返回写入的记录条数。
     * 失败抛 [IOException]/[SecurityException] 等，由调用方转为用户可见提示，禁止静默吞掉。
     */
    suspend fun exportSessionCsv(sessionId: Long, uri: Uri): Int = withContext(Dispatchers.IO) {
        val records = repository.getRecordsForSession(sessionId)
        // ISO 列供人读，epoch 列保真原始毫秒精度供机器处理；
        // 显式 Locale.US 保证小数/时间分隔符跨语言稳定（契约 12）
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("openOutputStream returned null: $uri")
        stream.use { output ->
            output.bufferedWriter().use { writer ->
                writer.appendLine("timestamp_iso,epoch_millis,heart_rate_bpm")
                for (record in records) {
                    writer.appendLine(
                        buildString {
                            append(isoFormat.format(Date(record.timestamp)))
                            append(',')
                            append(record.timestamp)
                            append(',')
                            append(record.heartRate)
                        }
                    )
                }
            }
        }
        records.size
    }
}
