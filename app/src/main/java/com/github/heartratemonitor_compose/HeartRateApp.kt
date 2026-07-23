package com.github.heartratemonitor_compose

import android.app.Application
import com.github.heartratemonitor_compose.data.di.AppContainer
import com.github.heartratemonitor_compose.service.FairMemoryNotifier
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import com.github.heartratemonitor_compose.service.MemoryDiagnostics
import com.github.heartratemonitor_compose.ui.theme.ThemePreviewCache
import com.github.heartratemonitor_compose.ui.theme.ThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 全局 Application。
 *
 * 主题系统（Material You 动态取色 + 自定义 seed）：
 * - 在 onCreate 中初始化 [ThemeState]，从 `app_settings` 加载持久化的主题配置
 *   （色彩来源 / 明暗模式 / 自定义 seed / PaletteStyle variant）。
 * - Compose 主题 [com.github.heartratemonitor_compose.ui.theme.HeartRateMonitorMobileTheme]
 *   通过 collectAsState 读取 [ThemeState]，决定使用系统 Monet 还是 MaterialKolor 自定义方案。
 *
 * 退出应用隐藏后台：
 * - 由 [com.github.heartratemonitor_compose.ui.main.MainActivity] 统一处理。
 *
 * 公平运行内存机制：
 * - 在 onCreate 中初始化 [FairMemoryReceiver]，动态注册 TRIM/KILL 广播。
 */
class HeartRateApp : Application() {

    /** 应用级依赖容器，供 ViewModel / Service 获取 Repository 与系统服务包装类。 */
    val appContainer: AppContainer by lazy { AppContainer(this) }

    /** 兼容旧代码：直接暴露 [SettingsRepository]，避免一次性改动所有引用。 */
    val settingsRepository by lazy { appContainer.settingsRepository }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ThemeState.init(settingsRepository)
        FairMemoryReceiver.getInstance().initialize(this)
        // 公平运行内存用户提示：创建通知渠道并注册关闭应用广播接收器
        FairMemoryNotifier.initialize(this)
        // Android 17+ 内存诊断：注册系统异常触发器并检查上次是否因 MemoryLimiter 被终止
        MemoryDiagnostics.initialize(this)
        // 后台预计算主题设置页所有预览色卡，避免首帧卡顿
        ThemePreviewCache.preload(appScope)
    }
}
