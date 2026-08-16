package com.github.heartratemonitor_compose.ui

import androidx.navigation3.runtime.NavKey
import com.github.heartratemonitor_compose.ui.Screen
import kotlinx.serialization.Serializable

/**
 * navigation3 导航键（NavKey）：仅二级页面使用。
 * 返回栈永不为空：栈底固定为 [TabRoot] 占位键（渲染 Tab 页，Tab 页 = 栈大小 1）。
 *
 * 必须 @Serializable：navigation3 1.1 的 rememberNavBackStack 用 kotlinx.serialization
 * 持久化返回栈（进程重建恢复），NavKey 多态序列化要求基类与子类可序列化。
 */
@Serializable
sealed interface AppNavKey : NavKey {
    /** 栈底占位：Tab 页（AppTabPager）由 NavDisplay 渲染，栈大小 1 表示在 Tab 页 */
    @Serializable
    data object TabRoot : AppNavKey
    @Serializable
    data object Devices : AppNavKey
    @Serializable
    data class Chart(val sessionId: Long) : AppNavKey
    @Serializable
    data object Alarm : AppNavKey
    @Serializable
    data object Server : AppNavKey
    @Serializable
    data object Webhook : AppNavKey
    @Serializable
    data object LanTransfer : AppNavKey
    @Serializable
    data object FairMemory : AppNavKey
    @Serializable
    data object Theme : AppNavKey
    @Serializable
    data object NavStyle : AppNavKey
    @Serializable
    data object FullscreenSound : AppNavKey
    @Serializable
    data object License : AppNavKey
    @Serializable
    data object Privacy : AppNavKey
    @Serializable
    data object AboutDetails : AppNavKey
    @Serializable
    data object FunctionSettings : AppNavKey
    @Serializable
    data object StatusBarSettings : AppNavKey
    @Serializable
    data object FloatingWindowSettings : AppNavKey
}

/** feature 层字符串路由 → [AppNavKey] 映射（navigation3 版的 String.toScreenRoute） */
fun appNavKeyOf(route: String): AppNavKey = when (route) {
    Screen.Devices.route -> AppNavKey.Devices
    Screen.Alarm.route -> AppNavKey.Alarm
    Screen.Server.route -> AppNavKey.Server
    Screen.Webhook.route -> AppNavKey.Webhook
    Screen.LanTransfer.route -> AppNavKey.LanTransfer
    Screen.FairMemory.route -> AppNavKey.FairMemory
    Screen.Theme.route -> AppNavKey.Theme
    Screen.NavStyle.route -> AppNavKey.NavStyle
    Screen.FullscreenSound.route -> AppNavKey.FullscreenSound
    Screen.License.route -> AppNavKey.License
    Screen.Privacy.route -> AppNavKey.Privacy
    Screen.AboutDetails.route -> AppNavKey.AboutDetails
    Screen.FunctionSettings.route -> AppNavKey.FunctionSettings
    Screen.StatusBarSettings.route -> AppNavKey.StatusBarSettings
    Screen.FloatingWindowSettings.route -> AppNavKey.FloatingWindowSettings
    else -> AppNavKey.Devices
}
