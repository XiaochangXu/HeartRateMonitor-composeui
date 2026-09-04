package com.github.heartratemonitor_compose

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.ui.main.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 前台 Activity 计数观察：接管「退出应用隐藏后台」（hideFromRecents）。
 *
 * 反直觉设计：不用 ProcessLifecycleOwner——其 ON_STOP 派发内置约 700ms 防抖
 * （防旋转重建误判），导致最近任务隐藏明显滞后；ActivityLifecycleCallbacks
 * 计数零延迟，且新页 onStart 先于宿主 onStop，计数不会经过 0，无闪烁。
 */
@Singleton
class AppForegroundMonitor @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationContext private val appContext: Context
) : Application.ActivityLifecycleCallbacks {

    // 回调均在主线程，无需原子类
    private var startedCount = 0

    /** 应在 Application.onCreate 中调用。 */
    fun observe(app: Application) {
        app.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityStarted(activity: Activity) {
        if (++startedCount == 1) setExcludeFromRecents(false)
    }

    override fun onActivityStopped(activity: Activity) {
        if (--startedCount > 0) return
        // suppress 窗口内（用户跳外部页面返回中）不隐藏，与迁移前语义一致
        if (MainActivity.isSuppressHideForExternalLaunch()) return
        if (settings.get(SettingsKeys.HIDE_FROM_RECENTS_ENABLED)) {
            setExcludeFromRecents(true)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    private fun setExcludeFromRecents(exclude: Boolean) {
        try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var matched = false
            for (task in am.appTasks) {
                if (task.taskInfo?.baseIntent?.component?.packageName == appContext.packageName) {
                    task.setExcludeFromRecents(exclude)
                    matched = true
                    break
                }
            }
            if (!matched) {
                Log.w(TAG, "setExcludeFromRecents($exclude): 未找到包名匹配的任务")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "setExcludeFromRecents($exclude) 失败", e)
        }
    }

    companion object {
        private const val TAG = "AppForegroundMonitor"
    }
}
