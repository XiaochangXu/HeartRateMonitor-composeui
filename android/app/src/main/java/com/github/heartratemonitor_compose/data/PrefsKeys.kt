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
    const val FULLSCREEN_SOUND_ENABLED = "fullscreen_sound_enabled"
    const val FULLSCREEN_SOUND_MODE = "fullscreen_sound_mode"

    // ── 蓝牙 ──
    const val AUTO_CONNECT_ENABLED = "auto_connect_enabled"
    const val AUTO_RECONNECT_ENABLED = "auto_reconnect_enabled"
    const val SCAN_FILTER_ENABLED = "scan_filter_enabled"
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
    const val FLOATING_X = "floating_x"
    const val FLOATING_Y = "floating_y"

    // ── 状态栏常驻 ──
    const val STATUS_BAR_RESIDENT_ENABLED = "status_bar_resident_enabled"
    const val STATUS_BAR_BPM_TEXT_ENABLED = "status_bar_bpm_text_enabled"
    const val STATUS_BAR_X_POSITION = "status_bar_x_position"
    const val STATUS_BAR_Y_OFFSET = "status_bar_y_offset"
    const val STATUS_BAR_SIZE = "status_bar_size"
    const val STATUS_BAR_TEXT_THICKNESS = "status_bar_text_thickness"
    const val STATUS_BAR_WHITE_TEXT = "status_bar_white_text"
    const val STATUS_BAR_TEXT_COLOR = "status_bar_text_color"

    // ── 心率预警 ──
    const val HEART_RATE_ALARM_ENABLED = "heart_rate_alarm_enabled"
    const val HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION = "heart_rate_alarm_exclude_posture_detection"
    const val HEART_RATE_ALARM_HIGH_THRESHOLD = "heart_rate_alarm_high_threshold"
    const val HEART_RATE_ALARM_LOW_THRESHOLD = "heart_rate_alarm_low_threshold"
    const val HEART_RATE_ALARM_DURATION_SECONDS = "heart_rate_alarm_duration_seconds"
    const val HEART_RATE_ALARM_REPEAT_ENABLED = "heart_rate_alarm_repeat_enabled"
    const val HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES = "heart_rate_alarm_repeat_interval_minutes"
    const val POSTURE_CALIBRATION_DATA = "posture_calibration_data"

    // ── 首页心率卡片 ──
    // 半圆进度环的满量程值（默认 180 bpm），铅笔弹窗可调
    const val HEART_RATE_RING_MAX = "heart_rate_ring_max"

    // ── HTTP / WebSocket 服务器 ──
    const val HTTP_SERVER_ENABLED = "http_server_enabled"
    const val HTTP_SERVER_PORT = "http_server_port"
    const val WEBSOCKET_SERVER_ENABLED = "websocket_server_enabled"
    const val WEBSOCKET_SERVER_PORT = "websocket_server_port"
    const val SERVER_ACCESS_TOKEN = "server_access_token"

    // ── 局域网传输（mDNS 一键配对）──
    // 临时 token：配对成功后由本机生成，电脑端用它连本机 WebSocket Server
    // 空字符串表示当前未配对。每次新配对会覆盖旧值。
    const val LAN_PAIRING_TOKEN = "lan_pairing_token"
    // 上次成功配对的电脑名称，仅用于 UI 展示历史，不影响逻辑
    const val LAN_LAST_PAIRED_PC_NAME = "lan_last_paired_pc_name"

    // ── 主题 ──
    const val THEME_SOURCE = "theme_source"
    const val THEME_MODE = "theme_mode"
    const val THEME_CUSTOM_SEED = "theme_custom_seed"
    const val THEME_PALETTE_STYLE = "theme_palette_style"

    // ── 液态玻璃（底部导航栏）──
    // blur 需 API 31+，lens（扭曲）需 API 33+，低版本静默失效
    const val LIQUID_GLASS_ENABLED = "liquid_glass_enabled"
    const val LIQUID_GLASS_BLUR = "liquid_glass_blur"            // Float，dp
    const val LIQUID_GLASS_DISTORTION = "liquid_glass_distortion" // Float，dp

    // ── 公平运行内存 / KILL 现场保存 ──
    const val KILL_STATE_SAVED = "kill_state_saved"
    const val KILL_STATE_ROUTE = "kill_state_route"
    const val KILL_STATE_TAB = "kill_state_tab"

    // ── 更新日志 ──
    // 存储上次已展示更新日志时的 versionCode，用于判断是否需要再次弹出
    const val CHANGELOG_LAST_SHOWN_VERSION = "changelog_last_shown_version"
    const val KILL_STATE_FULLSCREEN = "kill_state_fullscreen"
    const val KILL_STATE_CONNECTED_DEVICE_ID = "kill_state_connected_device_id"
    const val KILL_STATE_CONNECTED_DEVICE_NAME = "kill_state_connected_device_name"
    const val KILL_STATE_TIMESTAMP = "kill_state_timestamp"

    // ── 内存诊断 ──
    const val LAST_MEMORY_LIMITER_EXIT_CHECKED = "last_memory_limiter_exit_checked"

    // ── 收藏设备迁移 / 一次性提示 ──
    const val FAVORITE_HISTORY_MIGRATED_TO_ROOM = "favorite_history_migrated_to_room"
    const val FAVORITE_DEVICE_HISTORY = "favorite_device_history"
    const val SEARCH_TIP_SHOWN = "search_tip_shown"
}
