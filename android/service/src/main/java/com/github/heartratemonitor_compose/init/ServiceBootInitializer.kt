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
 * 合并原 StatusBarResidentInitializer 与 HeartRateAlarmInitializer 为单一 ContentProvider，
 * 消除多 Provider 之间的初始化顺序不确定性。
 *
 * ContentProvider.onCreate() 先于 Application.onCreate() 执行，此处直连 DataStore 单例
 * 读取设置（首次读取同时完成 SharedPreferences → DataStore 迁移），
 * 不访问 Hilt 组件（此时尚未初始化；契约 2 例外，永远不能走注入）。
 * 读取与服务恢复在后台线程执行，不再阻塞主线程的进程启动关键路径：
 * DataStore 实例全进程唯一，Application 阶段 SettingsRepository 构造的同步读
 * 会命中预热结果或与之并发共享同一份 IO。
 * startService 为异步排队，实际 Service.onCreate() 在主线程 Looper 后续调度时执行，
 * 此时 Application.onCreate() 已完成，Hilt 组件可安全使用。
 * Android 12+ 后台启动限制：用户主动冷启动时进程处于前台，startService 不会被拒绝；
 * 极端情况下若被拒绝，try-catch 降级忽略，由 MainActivity.recoverServices 兜底恢复。
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
        // 直连 DataStore 单例读取（首次读取触发迁移）；读取失败时降级跳过恢复，
        // 等用户进入应用时由 MainActivity.recoverServices 补启恢复。
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
            // 后台启动被拒时忽略，用户进入应用时 recoverServices 兜底恢复
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
