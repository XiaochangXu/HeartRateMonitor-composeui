package com.github.heartratemonitor_compose.data

/**
 * 应用 SharedPreferences 中所有 key 的集中定义。
 *
 * 禁止在代码中直接硬编码这些字符串，统一通过本对象引用。
 */
object PrefsKeys {

    /** SharedPreferences 文件名 */
    const val FILE_NAME = "app_settings"

    // ── 通用 ──
    const val HISTORY_RECORDING_ENABLED = "history_recording_enabled"
    const val HEARTBEAT_ANIMATION_ENABLED = "heartbeat_animation_enabled"
    const val SPEED_DISPLAY_ENABLED = "speed_display_enabled"
    const val HIDE_FROM_RECENTS_ENABLED = "hide_from_recents_enabled"

    // ── 蓝牙 ──
    const val AUTO_CONNECT_ENABLED = "auto_connect_enabled"
    const val AUTO_RECONNECT_ENABLED = "auto_reconnect_enabled"
    const val FAVORITE_DEVICE_ID = "favorite_device_id"

    // ── 悬浮窗 ──
    const val FLOATING_WINDOW_ENABLED = "floating_window_enabled"
    const val FLOATING_SIZE = "floating_size"
    const val FLOATING_ICON_SIZE = "floating_icon_size"
    const val FLOATING_CORNER_RADIUS = "floating_corner_radius"
    const val FLOATING_BG_ALPHA = "floating_bg_alpha"
    const val FLOATING_BORDER_ALPHA = "floating_border_alpha"
    const val FLOATING_TEXT_COLOR = "floating_text_color"
    const val FLOATING_BG_COLOR = "floating_bg_color"
    const val FLOATING_BORDER_COLOR = "floating_border_color"
    const val BPM_TEXT_ENABLED = "bpm_text_enabled"
    const val HEART_ICON_ENABLED = "heart_icon_enabled"

    // ── 状态栏常驻 ──
    const val STATUS_BAR_RESIDENT_ENABLED = "status_bar_resident_enabled"
    const val STATUS_BAR_BPM_TEXT_ENABLED = "status_bar_bpm_text_enabled"
    const val STATUS_BAR_X_POSITION = "status_bar_x_position"
    const val STATUS_BAR_Y_OFFSET = "status_bar_y_offset"
    const val STATUS_BAR_SIZE = "status_bar_size"
    const val STATUS_BAR_TEXT_THICKNESS = "status_bar_text_thickness"
    const val STATUS_BAR_WHITE_TEXT = "status_bar_white_text"

    // ── 心率预警 ──
    const val HEART_RATE_ALARM_ENABLED = "heart_rate_alarm_enabled"
    const val HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION = "heart_rate_alarm_exclude_posture_detection"
    const val HEART_RATE_ALARM_HIGH_THRESHOLD = "heart_rate_alarm_high_threshold"
    const val HEART_RATE_ALARM_LOW_THRESHOLD = "heart_rate_alarm_low_threshold"
    const val HEART_RATE_ALARM_DURATION_SECONDS = "heart_rate_alarm_duration_seconds"
    const val HEART_RATE_ALARM_REPEAT_ENABLED = "heart_rate_alarm_repeat_enabled"
    const val HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES = "heart_rate_alarm_repeat_interval_minutes"
    const val POSTURE_CALIBRATION_DATA = "posture_calibration_data"

    // ── HTTP / WebSocket 服务器 ──
    const val HTTP_SERVER_ENABLED = "http_server_enabled"
    const val HTTP_SERVER_PORT = "http_server_port"
    const val WEBSOCKET_SERVER_ENABLED = "websocket_server_enabled"
    const val WEBSOCKET_SERVER_PORT = "websocket_server_port"
    const val SERVER_ACCESS_TOKEN = "server_access_token"

    // ── 主题 ──
    const val THEME_SOURCE = "theme_source"
    const val THEME_MODE = "theme_mode"
    const val THEME_CUSTOM_SEED = "theme_custom_seed"
    const val THEME_PALETTE_STYLE = "theme_palette_style"

    // ── 收藏设备迁移 / 一次性提示 ──
    const val FAVORITE_HISTORY_MIGRATED_TO_ROOM = "favorite_history_migrated_to_room"
    const val FAVORITE_DEVICE_HISTORY = "favorite_device_history"
    const val SEARCH_TIP_SHOWN = "search_tip_shown"
}
