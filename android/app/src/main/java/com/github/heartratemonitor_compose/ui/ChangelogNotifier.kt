package com.github.heartratemonitor_compose.ui

import android.content.Context
import android.os.Build
import android.util.Log
import com.github.heartratemonitor_compose.R
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.settings.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class ChangelogNotice(
    val content: String,
    val versionName: String
)

// 设置读写归本 Hilt 单例；prepare 只判定不弹，UI 就绪后由 publishIfPending 发布，关闭时才落版本。
@Singleton
class ChangelogNotifier @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
    @AppScope private val appScope: CoroutineScope
) {

    private val _notice = MutableStateFlow<ChangelogNotice?>(null)
    val notice: StateFlow<ChangelogNotice?> = _notice.asStateFlow()

    private val lock = Any()
    private var pending: ChangelogNotice? = null
    private var pendingVersionCode = 0
    private var uiReady = false
    private var permissionsSettled = false

    init {
        appScope.launch { prepare() }
    }

    /** 标记首帧已绘制并稳定；与 [markPermissionsSettled] 齐备才发布（到达顺序无关）。 */
    fun markUiReady() {
        synchronized(lock) {
            uiReady = true
            flushLocked()
        }
    }

    /** 标记权限流程已结束（用户处理完或超时兜底）。 */
    fun markPermissionsSettled() {
        synchronized(lock) {
            permissionsSettled = true
            flushLocked()
        }
    }

    fun dismiss() {
        synchronized(lock) {
            if (_notice.value == null) return@synchronized
            _notice.value = null
            // ⚠️ 反直觉设计：关闭时才落版本，延迟展示期间退出则下次启动重弹
            if (pendingVersionCode != 0) {
                settings.set(SettingsKeys.CHANGELOG_LAST_SHOWN_VERSION, pendingVersionCode)
                pendingVersionCode = 0
            }
        }
    }

    private fun prepare() {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            val versionName = packageInfo.versionName?.removePrefix("v")?.removePrefix("V") ?: ""
            if (settings.get(SettingsKeys.CHANGELOG_LAST_SHOWN_VERSION) == versionCode) return
            val content = context.resources
                .openRawResource(R.raw.changelog)
                .bufferedReader()
                .use { it.readText() }
            synchronized(lock) {
                pending = ChangelogNotice(content, versionName)
                pendingVersionCode = versionCode
                flushLocked()
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新日志准备失败", e)
        }
    }

    // 须在 lock 内调用：两个 gate 齐备才上抛，避免抢首帧吞掉展开动画、避免压在权限对话框上。
    private fun flushLocked() {
        if (!uiReady || !permissionsSettled) return
        val notice = pending ?: return
        pending = null
        _notice.value = notice
    }

    companion object {
        private const val TAG = "ChangelogNotifier"
    }
}
