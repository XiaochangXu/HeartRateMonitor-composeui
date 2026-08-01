package com.github.heartratemonitor_compose.init

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.service.StatusBarResidentService


class StatusBarResidentInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        val prefs = ctx.getSharedPreferences(PrefsKeys.FILE_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PrefsKeys.STATUS_BAR_RESIDENT_ENABLED, false)) return true
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
