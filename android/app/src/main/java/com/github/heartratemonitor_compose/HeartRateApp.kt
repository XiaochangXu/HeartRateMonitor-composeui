package com.github.heartratemonitor_compose

import android.app.Application
import com.github.heartratemonitor_compose.data.di.AppContainer
import com.github.heartratemonitor_compose.service.FairMemoryNotifier
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import com.github.heartratemonitor_compose.service.MemoryDiagnostics
import com.github.heartratemonitor_compose.ui.theme.LiquidGlassState
import com.github.heartratemonitor_compose.ui.theme.ThemePreviewCache
import com.github.heartratemonitor_compose.ui.theme.ThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HeartRateApp : Application() {

    /** 应用级依赖容器，供 ViewModel / Service 获取 Repository 与系统服务包装类。 */
    val appContainer: AppContainer by lazy { AppContainer(this) }

    /** 兼容旧代码：直接暴露 [SettingsRepository]，避免一次性改动所有引用。 */
    val settingsRepository by lazy { appContainer.settingsRepository }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ThemeState.init(settingsRepository)
        LiquidGlassState.init(settingsRepository)
        FairMemoryReceiver.getInstance().initialize(this)
        // 公平运行内存用户提示：创建通知渠道并注册关闭应用广播接收器
        FairMemoryNotifier.initialize(this)
        // Android 17+ 内存诊断：注册系统异常触发器并检查上次是否因 MemoryLimiter 被终止
        MemoryDiagnostics.initialize(this)
        // 后台预计算主题设置页所有预览色卡，避免首帧卡顿
        ThemePreviewCache.preload(appScope)
    }
}
