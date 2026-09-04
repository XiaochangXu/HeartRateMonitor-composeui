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
import androidx.datastore.preferences.core.Preferences
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.settingsDataStore
import com.github.heartratemonitor_compose.service.HeartRateAlarmService
import com.github.heartratemonitor_compose.service.StatusBarResidentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

private const val TAG = "ServiceBootInitializer"

/**
 * 合并 StatusBarResidentInitializer 与 HeartRateAlarmInitializer 为单一 ContentProvider，
 * 消除多 Provider 初始化顺序不确定性。
 *
 * ⚠️ 反直觉设计：ContentProvider.onCreate() 先于 Application.onCreate()——
 * 直连 DataStore 单例读取设置（不访问 Hilt，此时尚未初始化），读取失败降级跳过。
 */
class ServiceBootInitializer : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context ?: return false
        Thread {
            restoreServices(ctx)
        }.apply {
            name = "ServiceBootInitializer"
            start()
        }
        return true
    }

    private fun restoreServices(ctx: Context) {
        val settings: Preferences = try {
            runBlocking(Dispatchers.IO) { ctx.settingsDataStore.data.first() }
        } catch (e: Exception) {
            Log.e(TAG, "读取 DataStore 设置失败，跳过服务恢复", e)
            return
        }

        if (settings[SettingsKeys.STATUS_BAR_RESIDENT_ENABLED] == true) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(ctx)) {
                tryStartService(ctx, StatusBarResidentService::class.java)
            }
        }

        if (settings[SettingsKeys.HEART_RATE_ALARM_ENABLED] == true) {
            tryStartService(ctx, HeartRateAlarmService::class.java)
        }
    }

    private fun tryStartService(ctx: Context, serviceClass: Class<*>) {
        try {
            ctx.startService(Intent(ctx, serviceClass))
        } catch (e: Exception) {
            Log.w(TAG, "启动 ${serviceClass.simpleName} 被系统拒绝，等待兜底恢复", e)
        }
    }

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
