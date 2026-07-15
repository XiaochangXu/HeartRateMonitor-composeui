package com.github.heartratemonitor_compose

import android.app.Application
import com.github.heartratemonitor_compose.service.FairMemoryReceiver

/**
 * 全局 Application。
 *
 * 莫奈取色（Material You 动态取色）：
 * - 由 Compose 主题 [com.github.heartratemonitor_compose.ui.theme.HeartRateMonitorMobileTheme]
 *   通过 [androidx.compose.material3.dynamicLightColorScheme] / [dynamicDarkColorScheme] 接管，
 *   根据传入的 dynamicColor 参数（来自 app_settings.monet_color_enabled）决定是否启用。
 * - 不再依赖 com.google.android.material.color.DynamicColors overlay（Material Components）。
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
        FairMemoryReceiver.getInstance().initialize(this)
    }
}
