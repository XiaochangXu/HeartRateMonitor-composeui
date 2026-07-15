package com.github.heartratemonitor_compose.init

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import com.github.heartratemonitor_compose.service.HeartRateAlarmService

/**
 * 应用冷启动时自动恢复心率预警服务。
 *
 * ContentProvider.onCreate 在应用进程启动时（早于 Application.onCreate 和 Activity）自动调用。
 * 利用此机制检查 heart_rate_alarm_enabled 偏好，若已开启则拉起 HeartRateAlarmService，
 * 无需修改 MainActivity。
 *
 * - 用户主动冷启动（点击图标）时进程处于前台，startService 不会被后台启动限制拒绝；
 *   服务在 onStartCommand 中自行调用 startForeground 提升为前台保活。
 * - 极端情况下（非用户主动启动）后台 startService 可能被拒，try-catch 忽略；
 *   用户进入设置页时 recoverHeartRateAlarmIfNeeded 兜底恢复。
 *
 * authority 与 StatusBarResidentInitializer 的 ${applicationId}.init 不同，用 ${applicationId}.alarm_init。
 */
class HeartRateAlarmInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        val prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("heart_rate_alarm_enabled", false)) return true
        try {
            ctx.startService(Intent(ctx, HeartRateAlarmService::class.java))
        } catch (_: Exception) {
            // 后台启动被拒时忽略，用户进入设置页时 recoverHeartRateAlarmIfNeeded 兜底
        }
        return true
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
