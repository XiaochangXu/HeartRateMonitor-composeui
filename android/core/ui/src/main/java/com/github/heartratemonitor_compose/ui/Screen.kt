package com.github.heartratemonitor_compose.ui

const val FLOATING_NAV_HEIGHT = 64
const val FLOATING_NAV_BOTTOM_MARGIN = 12

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
    object Language : Screen("language")
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

fun tabScreenAt(index: Int): Screen = when (index) {
    0 -> Screen.Home
    1 -> Screen.History
    2 -> Screen.Favorite
    3 -> Screen.Settings
    else -> Screen.Home
}
