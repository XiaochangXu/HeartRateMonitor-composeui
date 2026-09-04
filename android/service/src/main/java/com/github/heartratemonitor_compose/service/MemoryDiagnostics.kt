package com.github.heartratemonitor_compose.service

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.ProfilingManager
import android.os.ProfilingResult
import android.os.ProfilingTrigger
import android.util.Log
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import java.util.concurrent.Executors
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 注册 ProfilingManager TRIGGER_TYPE_ANOOMALY（API 36+），系统异常时自动抓取堆转储；
 * 应用启动时读取 ApplicationExitInfo，识别 Android 17 MemoryLimiter 终止（API 30+）。
 */
@Singleton
class MemoryDiagnostics @Inject constructor(
    private val application: Application,
    private val settingsRepository: SettingsRepository
) {

    private val profilingExecutor = Executors.newSingleThreadExecutor()

    fun initialize() {
        registerAnomalyTrigger(application)
        checkRecentExitReasons(application)
    }

    /**
     * ⚠️ 反直觉设计：全局 results 监听是系统触发（addProfilingTriggers）结果的唯一接收通道。
     */
    private fun registerAnomalyTrigger(context: Context) {
        if (Build.VERSION.SDK_INT < 36) {
            Log.d(TAG, "ProfilingTrigger 需 API 36+，当前 ${Build.VERSION.SDK_INT}，跳过")
            return
        }

        try {
            val profilingManager = context.getSystemService(Context.PROFILING_SERVICE) as? ProfilingManager
                ?: return

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
     * ⚠️ 反直觉设计：ApplicationExitInfo 仅记录最近若干次终止，且每次冷启动后读取——
     * 必须持久化 lastChecked timestamp 避免漏检。
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

            val settings = settingsRepository
            val lastChecked = settings.get(SettingsKeys.LAST_MEMORY_LIMITER_EXIT_CHECKED)
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

            settings.set(SettingsKeys.LAST_MEMORY_LIMITER_EXIT_CHECKED, latestTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "检查 ApplicationExitInfo 失败", e)
        }
    }

    companion object {
        private const val TAG = "MemoryDiagnostics"

        /** MemoryLimiter 终止时在 ApplicationExitInfo.getDescription() 中出现的标记。 */
        private const val MEMORY_LIMITER_MARKER = "MemoryLimiter:AnonSwap"
    }
}
