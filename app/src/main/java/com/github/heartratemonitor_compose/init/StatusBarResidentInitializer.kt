package com.github.heartratemonitor_compose.init

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.github.heartratemonitor_compose.service.StatusBarResidentService

/**
 * 应用冷启动时自动恢复状态栏常驻心率服务。
 *
 * ContentProvider.onCreate 在应用进程启动时（早于 Application.onCreate 和 Activity）自动调用。
 * 利用此机制检查 status_bar_resident_enabled 偏好，若已开启且悬浮窗权限已授予则拉起
 * StatusBarResidentService，无需修改 MainActivity。
 *
 * - 用户主动冷启动（点击图标）时进程处于前台，startService 不会被后台启动限制拒绝；
 *   服务在 onStartCommand 中自行调用 startForeground 提升为前台保活。
 * - 极端情况下（非用户主动启动）后台 startService 可能被拒，try-catch 忽略；
 *   用户进入设置页时 recoverStatusBarResidentIfNeeded 兜底恢复。
 */
class StatusBarResidentInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        val prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("status_bar_resident_enabled", false)) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
            return true
        }
        try {
            ctx.startService(Intent(ctx, StatusBarResidentService::class.java))
        } catch (_: Exception) {
            // 后台启动被拒时忽略，用户进入设置页时 recoverStatusBarResidentIfNeeded 兜底
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
