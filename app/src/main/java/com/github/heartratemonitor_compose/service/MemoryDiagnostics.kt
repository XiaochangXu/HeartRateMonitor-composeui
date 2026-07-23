package com.github.heartratemonitor_compose.service

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import android.util.Log
import com.github.heartratemonitor_compose.data.PrefsKeys
import java.util.concurrent.Executors
import java.util.function.Consumer

/**
 * Android 17+ 内存诊断工具。
 *
 * 功能：
 * 1. 注册 [ProfilingManager] 的 [TRIGGER_TYPE_ANOMALY] 触发器，
 *    在系统检测到内存异常时自动收集堆转储 / 系统跟踪，便于线下分析。
 * 2. 应用启动时读取 [ApplicationExitInfo]，识别是否因 Android 17 MemoryLimiter 被终止，
 *    并在日志中标记。
 *
 * 这些诊断能力仅用于问题定位，不影响核心内存释放逻辑。
 */
object MemoryDiagnostics {

    private const val TAG = "MemoryDiagnostics"

    /** MemoryLimiter 终止时在 ApplicationExitInfo.getDescription() 中出现的标记。 */
    private const val MEMORY_LIMITER_MARKER = "MemoryLimiter:AnonSwap"

    private val profilingExecutor = Executors.newSingleThreadExecutor()

    /**
     * 在 Application.onCreate() 中调用，初始化诊断监听与启动时退出原因检查。
     */
    fun initialize(context: Context) {
        registerAnomalyTrigger(context)
        checkRecentExitReasons(context)
    }

    /**
     * 注册系统异常触发器（API 36+）。
     * 当系统检测到异常（包括内存使用过高）时，自动抓取性能数据。
     */
    private fun registerAnomalyTrigger(context: Context) {
        if (Build.VERSION.SDK_INT < 36) {
            Log.d(TAG, "ProfilingTrigger 需 API 36+，当前 ${Build.VERSION.SDK_INT}，跳过")
            return
        }

        try {
            val profilingManager = context.getSystemService(Context.PROFILING_SERVICE) as? ProfilingManager
                ?: return

            // 全局结果监听：系统触发（addProfilingTriggers）的结果只能通过此回调接收
            profilingManager.registerForAllProfilingResults(
                profilingExecutor,
                Consumer { result: ProfilingResult ->
                    if (result.errorCode == ProfilingResult.ERROR_NONE) {
                        Log.i(TAG, "收到系统触发的 profiling 结果: tag=${result.tag}, path=${result.resultFilePath}")
                    } else {
                        Log.w(TAG, "系统触发的 profiling 失败: tag=${result.tag}, " +
                                "errorCode=${result.errorCode}, message=${result.errorMessage}")
                    }
                }
            )

            val trigger = ProfilingTrigger.Builder(ProfilingTrigger.TRIGGER_TYPE_ANOMALY)
                .setRateLimitingPeriodHours(24)
                .build()

            profilingManager.addProfilingTriggers(listOf(trigger))
            Log.i(TAG, "已注册 ProfilingManager TRIGGER_TYPE_ANOMALY 触发器")
        } catch (e: Exception) {
            Log.e(TAG, "注册异常触发器失败", e)
        }
    }

    /**
     * 检查最近一次退出原因，识别 Android 17 MemoryLimiter 导致的终止。
     * ApplicationExitInfo 需 API 30+。
     */
    private fun checkRecentExitReasons(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "ApplicationExitInfo 需 API 30+，当前 ${Build.VERSION.SDK_INT}，跳过")
            return
        }

        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return

            val reasons = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            if (reasons.isNullOrEmpty()) return

            val prefs = context.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
            val lastChecked = prefs.getLong(PrefsKeys.LAST_MEMORY_LIMITER_EXIT_CHECKED, 0L)
            var latestTimestamp = lastChecked

            for (info in reasons) {
                val timestamp = info.timestamp
                if (timestamp <= lastChecked) continue
                if (timestamp > latestTimestamp) latestTimestamp = timestamp

                val description = info.description ?: ""
                val isMemoryLimiter = info.reason == ApplicationExitInfo.REASON_OTHER
                        && description.contains(MEMORY_LIMITER_MARKER)

                if (isMemoryLimiter) {
                    Log.w(TAG, "检测到 Android 17 MemoryLimiter 终止: " +
                            "timestamp=$timestamp, description=$description")
                } else {
                    Log.d(TAG, "历史退出原因: reason=${info.reason}, " +
                            "description=$description, timestamp=$timestamp")
                }
            }

            prefs.edit().putLong(PrefsKeys.LAST_MEMORY_LIMITER_EXIT_CHECKED, latestTimestamp).apply()
        } catch (e: Exception) {
            Log.e(TAG, "检查 ApplicationExitInfo 失败", e)
        }
    }
}
