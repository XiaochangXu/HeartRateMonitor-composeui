package com.github.heartratemonitor_compose.init

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.service.HeartRateAlarmService
import com.github.heartratemonitor_compose.service.StatusBarResidentService

private const val TAG = "ServiceBootInitializer"

/**
 * 应用冷启动时的服务自动恢复入口。
 *
 * 合并原 [StatusBarResidentInitializer] 与 [HeartRateAlarmInitializer] 为单一 ContentProvider，
 * 消除多 Provider 之间的初始化顺序不确定性。在 [onCreate] 中按固定顺序检查并恢复用户已启用的
 * 常驻服务（状态栏 overlay + 心率预警）。
 *
 * 初始化时序说明：
 * - ContentProvider.onCreate() 先于 Application.onCreate() 执行，此处仅读取 SharedPreferences，
 *   不访问 [AppContainer][com.github.heartratemonitor_compose.data.di.AppContainer]（此时尚未初始化）。
 * - startService 为异步排队，实际 Service.onCreate() 会在主线程 Looper 后续调度时执行，
 *   此时 Application.onCreate() 已完成，AppContainer 可安全访问。
 * - Android 12+ 后台启动限制：用户主动冷启动时进程处于前台，startService 不会被拒绝；
 *   极端情况下若被拒绝，try-catch 降级忽略，由 [MainActivity.recoverServices] 兜底恢复。
 */
class ServiceBootInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        val prefs = ctx.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)

        // 1. 恢复状态栏常驻服务
        if (prefs.getBoolean(PrefsKeys.STATUS_BAR_RESIDENT_ENABLED, false)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)) {
                tryStartService(ctx, StatusBarResidentService::class.java)
            }
        }

        // 2. 恢复心率预警服务
        if (prefs.getBoolean(PrefsKeys.HEART_RATE_ALARM_ENABLED, false)) {
            tryStartService(ctx, HeartRateAlarmService::class.java)
        }

        return true
    }

    private fun tryStartService(ctx: Context, serviceClass: Class<*>) {
        try {
            ctx.startService(Intent(ctx, serviceClass))
        } catch (e: Exception) {
            // 后台启动被拒时忽略，用户进入应用时 recoverServices 兜底恢复
            Log.w(TAG, "启动 ${serviceClass.simpleName} 被系统拒绝，等待兜底恢复", e)
        }
    }

    // 以下方法均不提供实际功能，仅为满足 ContentProvider 抽象方法要求
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
