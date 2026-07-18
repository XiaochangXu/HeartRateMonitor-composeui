package com.github.heartratemonitor_compose

import android.app.Application
import com.github.heartratemonitor_compose.service.FairMemoryReceiver
import com.github.heartratemonitor_compose.ui.theme.ThemeState

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
    override fun onCreate() {
        super.onCreate()
        ThemeState.init(this)
        FairMemoryReceiver.getInstance().initialize(this)
    }
}
