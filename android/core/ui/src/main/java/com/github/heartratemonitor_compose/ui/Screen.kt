package com.github.heartratemonitor_compose.ui

const val FLOATING_NAV_HEIGHT = 64
const val FLOATING_NAV_BOTTOM_MARGIN = 12

const val SECONDARY_SLIDE_DURATION = 350

const val BACKGROUND_PARALLAX_RATIO = 0.2f

const val SAME_ROUTE_DEBOUNCE_MS = 100L
/**
 * 异路由转场互斥窗口：防止转场动画期间导航导致 AnimatedContent 竞态。
 * 与 [SECONDARY_SLIDE_DURATION] 对齐，确保动画完成前不放行下一次导航。
 */
const val TRANSITION_DEBOUNCE_MS = 350L

/** NavHost 占位路由：Tab 页在 NavHost 外部管理，仅作为 startDestination */
const val TAB_PLACEHOLDER = "tab_placeholder"

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

fun Screen.isTab(): Boolean =
    this is Screen.Home || this is Screen.History || this is Screen.Favorite || this is Screen.Settings

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

fun tabScreenAt(index: Int): Screen = when (index) {
    0 -> Screen.Home
    1 -> Screen.History
    2 -> Screen.Favorite
    3 -> Screen.Settings
    else -> Screen.Home
}
