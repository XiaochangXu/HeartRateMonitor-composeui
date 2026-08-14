package com.github.heartratemonitor_compose.ui

// ── 导航布局常量 ──
const val FLOATING_NAV_HEIGHT = 64
const val FLOATING_NAV_BOTTOM_MARGIN = 12

// ── 二级页面转场动画常量 ──
/** 二级页面 NavHost 转场动画时长（ms） */
const val SECONDARY_SLIDE_DURATION = 350
/** 进入二级页面时底层 Tab 层向左位移比例（视差效果） */
const val BACKGROUND_PARALLAX_RATIO = 0.2f

// ── 导航防抖参数 ──
/** 同路由防双击窗口：防止快速双击同一项目重复压栈 */
const val SAME_ROUTE_DEBOUNCE_MS = 100L
/**
 * 异路由转场互斥窗口：防止转场动画期间导航导致 AnimatedContent 竞态。
 * 低于 [SECONDARY_SLIDE_DURATION]：FastOutSlowInEasing 在 250/350≈71% 处已完成 ~94%
 */
const val TRANSITION_DEBOUNCE_MS = 250L

/** NavHost 占位路由：Tab 页在 NavHost 外部管理，此路由仅作为 startDestination */
const val TAB_PLACEHOLDER = "tab_placeholder"

/**
 * 路由定义。使用 Navigation Compose 管理页面栈。
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object History : Screen("history")
    object Chart : Screen("chart/{sessionId}") {
        fun createRoute(sessionId: Long) = "chart/$sessionId"
    }
    object Favorite : Screen("favorite")
    object Alarm : Screen("alarm")
    object Server : Screen("server")
    object Webhook : Screen("webhook")
    object LanTransfer : Screen("lan_transfer")
    object FairMemory : Screen("fair_memory")
    object Theme : Screen("theme")
    object NavStyle : Screen("nav_style")
    object Devices : Screen("devices")
    object FullscreenSound : Screen("fullscreen_sound")
    object License : Screen("license")
    object Privacy : Screen("privacy")
    object AboutDetails : Screen("about_details")
    object FunctionSettings : Screen("function_settings")
    object StatusBarSettings : Screen("status_bar_settings")
    object FloatingWindowSettings : Screen("floating_window_settings")
}

/** 底部导航 Tab 页：Home / History / Favorite / Settings 均为 Tab */
fun Screen.isTab(): Boolean =
    this is Screen.Home || this is Screen.History || this is Screen.Favorite || this is Screen.Settings

/** SettingsScreen 用字符串路由映射到 Navigation Compose 路由 */
fun String.toScreenRoute(): String = when (this) {
    "alarm" -> Screen.Alarm.route
    "server" -> Screen.Server.route
    "webhook" -> Screen.Webhook.route
    "lan_transfer" -> Screen.LanTransfer.route
    "fair_memory" -> Screen.FairMemory.route
    "theme" -> Screen.Theme.route
    "nav_style" -> Screen.NavStyle.route
    "devices" -> Screen.Devices.route
    "fullscreen_sound" -> Screen.FullscreenSound.route
    "license" -> Screen.License.route
    "privacy" -> Screen.Privacy.route
    "about_details" -> Screen.AboutDetails.route
    "function_settings" -> Screen.FunctionSettings.route
    "status_bar_settings" -> Screen.StatusBarSettings.route
    "floating_window_settings" -> Screen.FloatingWindowSettings.route
    else -> Screen.Home.route
}

/** Tab 索引 → Screen 映射（4 Tab：0=Home, 1=History, 2=Favorite, 3=Settings） */
fun tabScreenAt(index: Int): Screen = when (index) {
    0 -> Screen.Home
    1 -> Screen.History
    2 -> Screen.Favorite
    3 -> Screen.Settings
    else -> Screen.Home
}
