package com.github.heartratemonitor_compose.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * 键名字符串与 SharedPreferences / 旧 PrefsKeys 时代完全一致，
 * 保证 SharedPreferencesMigration 迁入的老数据无缝对应，禁止改动任何键名。
 * 读写一律经 SettingsRepository 的类型化 API，禁止字符串键名直接操作。
 * 默认值唯一来源是 [AppSettings.DEFAULTS]。
 */
object SettingsKeys {

    val HISTORY_RECORDING_ENABLED = booleanPreferencesKey("history_recording_enabled")
    val HEARTBEAT_ANIMATION_ENABLED = booleanPreferencesKey("heartbeat_animation_enabled")
    val SPEED_DISPLAY_ENABLED = booleanPreferencesKey("speed_display_enabled")
    val HIDE_FROM_RECENTS_ENABLED = booleanPreferencesKey("hide_from_recents_enabled")
    val FULLSCREEN_SOUND_ENABLED = booleanPreferencesKey("fullscreen_sound_enabled")
    val FULLSCREEN_SOUND_MODE = stringPreferencesKey("fullscreen_sound_mode")

    val AUTO_CONNECT_ENABLED = booleanPreferencesKey("auto_connect_enabled")
    val AUTO_RECONNECT_ENABLED = booleanPreferencesKey("auto_reconnect_enabled")
    val SCAN_FILTER_ENABLED = booleanPreferencesKey("scan_filter_enabled")
    val FAVORITE_DEVICE_ID = stringPreferencesKey("favorite_device_id")

    val FLOATING_WINDOW_ENABLED = booleanPreferencesKey("floating_window_enabled")
    val FLOATING_SIZE = intPreferencesKey("floating_size")
    val FLOATING_ICON_SIZE = intPreferencesKey("floating_icon_size")
    val FLOATING_CORNER_RADIUS = intPreferencesKey("floating_corner_radius")
    val FLOATING_BG_ALPHA = intPreferencesKey("floating_bg_alpha")
    val FLOATING_BORDER_ALPHA = intPreferencesKey("floating_border_alpha")
    val FLOATING_TEXT_COLOR = intPreferencesKey("floating_text_color")
    val FLOATING_BG_COLOR = intPreferencesKey("floating_bg_color")
    val FLOATING_BORDER_COLOR = intPreferencesKey("floating_border_color")
    val BPM_TEXT_ENABLED = booleanPreferencesKey("bpm_text_enabled")
    val HEART_ICON_ENABLED = booleanPreferencesKey("heart_icon_enabled")
    val FLOATING_X = intPreferencesKey("floating_x")
    val FLOATING_Y = intPreferencesKey("floating_y")

    val STATUS_BAR_RESIDENT_ENABLED = booleanPreferencesKey("status_bar_resident_enabled")
    val STATUS_BAR_BPM_TEXT_ENABLED = booleanPreferencesKey("status_bar_bpm_text_enabled")
    val STATUS_BAR_X_POSITION = intPreferencesKey("status_bar_x_position")
    val STATUS_BAR_Y_OFFSET = intPreferencesKey("status_bar_y_offset")
    val STATUS_BAR_SIZE = intPreferencesKey("status_bar_size")
    val STATUS_BAR_TEXT_THICKNESS = intPreferencesKey("status_bar_text_thickness")
    // 历史遗留键，当前无调用点；保留以承接存量设备数据
    val STATUS_BAR_WHITE_TEXT = booleanPreferencesKey("status_bar_white_text")
    val STATUS_BAR_TEXT_COLOR = intPreferencesKey("status_bar_text_color")

    val HEART_RATE_ALARM_ENABLED = booleanPreferencesKey("heart_rate_alarm_enabled")
    val HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION =
        booleanPreferencesKey("heart_rate_alarm_exclude_posture_detection")
    val HEART_RATE_ALARM_HIGH_THRESHOLD = intPreferencesKey("heart_rate_alarm_high_threshold")
    val HEART_RATE_ALARM_LOW_THRESHOLD = intPreferencesKey("heart_rate_alarm_low_threshold")
    val HEART_RATE_ALARM_DURATION_SECONDS = intPreferencesKey("heart_rate_alarm_duration_seconds")
    val HEART_RATE_ALARM_REPEAT_ENABLED = booleanPreferencesKey("heart_rate_alarm_repeat_enabled")
    val HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES =
        intPreferencesKey("heart_rate_alarm_repeat_interval_minutes")
    val POSTURE_CALIBRATION_DATA = stringPreferencesKey("posture_calibration_data")

    val HEART_RATE_RING_MAX = intPreferencesKey("heart_rate_ring_max")

    val HTTP_SERVER_ENABLED = booleanPreferencesKey("http_server_enabled")
    val HTTP_SERVER_PORT = intPreferencesKey("http_server_port")
    val WEBSOCKET_SERVER_ENABLED = booleanPreferencesKey("websocket_server_enabled")
    val WEBSOCKET_SERVER_PORT = intPreferencesKey("websocket_server_port")
    val SERVER_ACCESS_TOKEN = stringPreferencesKey("server_access_token")

    val LAN_PAIRING_TOKEN = stringPreferencesKey("lan_pairing_token")
    val LAN_LAST_PAIRED_PC_NAME = stringPreferencesKey("lan_last_paired_pc_name")

    val THEME_SOURCE = intPreferencesKey("theme_source")
    val THEME_MODE = intPreferencesKey("theme_mode")
    val THEME_CUSTOM_SEED = intPreferencesKey("theme_custom_seed")
    val THEME_PALETTE_STYLE = stringPreferencesKey("theme_palette_style")

    val LIQUID_GLASS_ENABLED = booleanPreferencesKey("liquid_glass_enabled")
    val LIQUID_GLASS_BLUR = floatPreferencesKey("liquid_glass_blur")                    // dp
    val LIQUID_GLASS_DISTORTION = floatPreferencesKey("liquid_glass_distortion")        // dp

    val KILL_STATE_SAVED = booleanPreferencesKey("kill_state_saved")
    val KILL_STATE_ROUTE = stringPreferencesKey("kill_state_route")
    val KILL_STATE_TAB = stringPreferencesKey("kill_state_tab")
    val KILL_STATE_FULLSCREEN = booleanPreferencesKey("kill_state_fullscreen")
    val KILL_STATE_CONNECTED_DEVICE_ID = stringPreferencesKey("kill_state_connected_device_id")
    val KILL_STATE_CONNECTED_DEVICE_NAME = stringPreferencesKey("kill_state_connected_device_name")
    val KILL_STATE_TIMESTAMP = longPreferencesKey("kill_state_timestamp")

    val CHANGELOG_LAST_SHOWN_VERSION = intPreferencesKey("changelog_last_shown_version")

    val LAST_MEMORY_LIMITER_EXIT_CHECKED = longPreferencesKey("last_memory_limiter_exit_checked")

    val FAVORITE_HISTORY_MIGRATED_TO_ROOM = booleanPreferencesKey("favorite_history_migrated_to_room")
    val FAVORITE_DEVICE_HISTORY = stringPreferencesKey("favorite_device_history")
    val SEARCH_TIP_SHOWN = booleanPreferencesKey("search_tip_shown")

    /** 应用语言设置。null = 自动跟随系统，非 null = 语言 Tag（如 "zh-CN"、"en"、"de"）。 */
    val APP_LANGUAGE = stringPreferencesKey("app_language")
}
