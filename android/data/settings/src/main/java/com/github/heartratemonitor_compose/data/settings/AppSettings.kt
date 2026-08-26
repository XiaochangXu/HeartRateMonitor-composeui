package com.github.heartratemonitor_compose.data.settings

import android.graphics.Color
import androidx.datastore.preferences.core.Preferences

/**
 * 由 SettingsRepository 以 StateFlow 暴露，与底层 Preferences 内存快照同步更新
 *
 * 默认值约定：
 * - [DEFAULTS] 是全部设置项默认值的唯一来源。
 * - 存在三处历史默认值分歧，统一为实际渲染生效值：
 *   悬浮窗圆角（渲染 100 / 旧设置页滑块 50）、悬浮窗背景透明度（渲染 10 / 旧滑块 80）
 *   统一取渲染值；全屏心率文字色默认 RED 仅属全屏页面，
 *   该调用点保留显式默认值参数，不纳入本快照语义。
 */
data class AppSettings(
    val historyRecordingEnabled: Boolean,
    val heartbeatAnimationEnabled: Boolean,
    val speedDisplayEnabled: Boolean,
    val hideFromRecentsEnabled: Boolean,
    val fullscreenSoundEnabled: Boolean,
    val fullscreenSoundMode: String?,
    val autoConnectEnabled: Boolean,
    val autoReconnectEnabled: Boolean,
    val scanFilterEnabled: Boolean,
    val navAnimationDisabled: Boolean,
    val favoriteDeviceId: String?,
    val floatingWindowEnabled: Boolean,
    val floatingSize: Int,
    val floatingIconSize: Int,
    val floatingCornerRadius: Int,
    val floatingBgAlpha: Int,
    val floatingBorderAlpha: Int,
    val floatingTextColor: Int,
    val floatingBgColor: Int,
    val floatingBorderColor: Int,
    val bpmTextEnabled: Boolean,
    val heartIconEnabled: Boolean,
    val floatingX: Int,
    val floatingY: Int,
    val statusBarResidentEnabled: Boolean,
    val statusBarBpmTextEnabled: Boolean,
    val statusBarXPosition: Int,
    val statusBarYOffset: Int,
    val statusBarSize: Int,
    val statusBarTextThickness: Int,
    val statusBarTextColor: Int,
    val heartRateAlarmEnabled: Boolean,
    val heartRateAlarmExcludePostureDetection: Boolean,
    val heartRateAlarmHighThreshold: Int,
    val heartRateAlarmLowThreshold: Int,
    val heartRateAlarmDurationSeconds: Int,
    val heartRateAlarmRepeatEnabled: Boolean,
    val heartRateAlarmRepeatIntervalMinutes: Int,
    val postureCalibrationData: String?,
    val heartRateRingMax: Int,
    val httpServerEnabled: Boolean,
    val httpServerPort: Int,
    val websocketServerEnabled: Boolean,
    val websocketServerPort: Int,
    val serverAccessToken: String,
    val themeSource: Int,
    val themeMode: Int,
    val themeCustomSeed: Int,
    val themePaletteStyle: String,
    val liquidGlassEnabled: Boolean,
    val liquidGlassBlurDp: Float,
    val liquidGlassDistortionDp: Float,
    val killStateSaved: Boolean,
    val killStateRoute: String,
    val killStateTab: String,
    val killStateFullscreen: Boolean,
    val killStateConnectedDeviceId: String?,
    val killStateConnectedDeviceName: String?,
    val killStateTimestamp: Long,
    val changelogLastShownVersion: Int,
    val lastMemoryLimiterExitChecked: Long,
    val favoriteHistoryMigratedToRoom: Boolean,
    val favoriteDeviceHistory: String?,
    val searchTipShown: Boolean,
    /** 应用语言设置。null = 自动跟随系统，非 null = 语言 Tag。 */
    val appLanguage: String?
) {

    companion object {

        /** 避免 data 层反向依赖 ui 层常量。 */
        const val DEFAULT_THEME_SOURCE = 0

        const val DEFAULT_THEME_MODE = 0

        /** 与 M3 规范默认 seed 一致。 */
        const val DEFAULT_THEME_CUSTOM_SEED = 0xFF6750A4.toInt()

        const val DEFAULT_THEME_PALETTE_STYLE = "TonalSpot"

        /** 液态玻璃模糊半径默认值。 */
        const val DEFAULT_LIQUID_GLASS_BLUR_DP = 5f

        /** 液态玻璃扭曲强度默认值。 */
        const val DEFAULT_LIQUID_GLASS_DISTORTION_DP = 30f

        val DEFAULTS: Map<Preferences.Key<*>, Any?> = buildMap {
            put(SettingsKeys.HISTORY_RECORDING_ENABLED, false,
)
            put(SettingsKeys.HEARTBEAT_ANIMATION_ENABLED, true,
)
            put(SettingsKeys.SPEED_DISPLAY_ENABLED, false,
)
            put(SettingsKeys.HIDE_FROM_RECENTS_ENABLED, false,
)
            put(SettingsKeys.FULLSCREEN_SOUND_ENABLED, true,
)
            put(SettingsKeys.FULLSCREEN_SOUND_MODE, null,
)
            put(SettingsKeys.AUTO_CONNECT_ENABLED, false,
)
            put(SettingsKeys.AUTO_RECONNECT_ENABLED, true,
)
            put(SettingsKeys.SCAN_FILTER_ENABLED, true,
)
            put(SettingsKeys.NAV_ANIMATION_DISABLED, true,
)
            put(SettingsKeys.FAVORITE_DEVICE_ID, null,
)
            put(SettingsKeys.FLOATING_WINDOW_ENABLED, false,
)
            put(SettingsKeys.FLOATING_SIZE, 100,
)
            put(SettingsKeys.FLOATING_ICON_SIZE, 100,
)
            put(SettingsKeys.FLOATING_CORNER_RADIUS, 100,
)
            put(SettingsKeys.FLOATING_BG_ALPHA, 10,
)
            put(SettingsKeys.FLOATING_BORDER_ALPHA, 100,
)
            put(SettingsKeys.FLOATING_TEXT_COLOR, Color.BLACK,
)
            put(SettingsKeys.FLOATING_BG_COLOR, Color.BLACK,
)
            put(SettingsKeys.FLOATING_BORDER_COLOR, Color.GRAY,
)
            put(SettingsKeys.BPM_TEXT_ENABLED, true,
)
            put(SettingsKeys.HEART_ICON_ENABLED, true,
)
            put(SettingsKeys.FLOATING_X, 100,
)
            put(SettingsKeys.FLOATING_Y, 100,
)
            put(SettingsKeys.STATUS_BAR_RESIDENT_ENABLED, false,
)
            put(SettingsKeys.STATUS_BAR_BPM_TEXT_ENABLED, true,
)
            put(SettingsKeys.STATUS_BAR_X_POSITION, 0,
)
            put(SettingsKeys.STATUS_BAR_Y_OFFSET, 10,
)
            put(SettingsKeys.STATUS_BAR_SIZE, 100,
)
            put(SettingsKeys.STATUS_BAR_TEXT_THICKNESS, 0,
)
            put(SettingsKeys.STATUS_BAR_WHITE_TEXT, false,
)
            put(SettingsKeys.STATUS_BAR_TEXT_COLOR, Color.BLACK,
)
            put(SettingsKeys.HEART_RATE_ALARM_ENABLED, false,
)
            put(SettingsKeys.HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION, false,
)
            put(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, 100,
)
            put(SettingsKeys.HEART_RATE_ALARM_LOW_THRESHOLD, 50,
)
            put(SettingsKeys.HEART_RATE_ALARM_DURATION_SECONDS, 10,
)
            put(SettingsKeys.HEART_RATE_ALARM_REPEAT_ENABLED, false,
)
            put(SettingsKeys.HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES, 5,
)
            put(SettingsKeys.POSTURE_CALIBRATION_DATA, null,
)
            put(SettingsKeys.HEART_RATE_RING_MAX, 180,
)
            put(SettingsKeys.HTTP_SERVER_ENABLED, false,
)
            put(SettingsKeys.HTTP_SERVER_PORT, 8000,
)
            put(SettingsKeys.WEBSOCKET_SERVER_ENABLED, false,
)
            put(SettingsKeys.WEBSOCKET_SERVER_PORT, 8001,
)
            put(SettingsKeys.SERVER_ACCESS_TOKEN, "",
)
            put(SettingsKeys.LAN_PAIRING_TOKEN, "",
)
            put(SettingsKeys.LAN_LAST_PAIRED_PC_NAME, "",
)
            put(SettingsKeys.THEME_SOURCE, DEFAULT_THEME_SOURCE,
)
            put(SettingsKeys.THEME_MODE, DEFAULT_THEME_MODE,
)
            put(SettingsKeys.THEME_CUSTOM_SEED, DEFAULT_THEME_CUSTOM_SEED,
)
            put(SettingsKeys.THEME_PALETTE_STYLE, DEFAULT_THEME_PALETTE_STYLE,
)
            put(SettingsKeys.LIQUID_GLASS_ENABLED, true,
)
            put(SettingsKeys.LIQUID_GLASS_BLUR, DEFAULT_LIQUID_GLASS_BLUR_DP,
)
            put(SettingsKeys.LIQUID_GLASS_DISTORTION, DEFAULT_LIQUID_GLASS_DISTORTION_DP,
)
            put(SettingsKeys.KILL_STATE_SAVED, false,
)
            put(SettingsKeys.KILL_STATE_ROUTE, "",
)
            put(SettingsKeys.KILL_STATE_TAB, "",
)
            put(SettingsKeys.KILL_STATE_FULLSCREEN, false,
)
            put(SettingsKeys.KILL_STATE_CONNECTED_DEVICE_ID, null,
)
            put(SettingsKeys.KILL_STATE_CONNECTED_DEVICE_NAME, null,
)
            put(SettingsKeys.KILL_STATE_TIMESTAMP, 0L,
)
            put(SettingsKeys.CHANGELOG_LAST_SHOWN_VERSION, -1,
)
            put(SettingsKeys.LAST_MEMORY_LIMITER_EXIT_CHECKED, 0L,
)
            put(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM, false,
)
            put(SettingsKeys.FAVORITE_DEVICE_HISTORY, null,
)
            put(SettingsKeys.SEARCH_TIP_SHOWN, false)
            put(SettingsKeys.APP_LANGUAGE, null)
            // Webhook 配置默认值：null 表示未配置，由 WebhookRepository 独立管理
            put(SettingsKeys.WEBHOOKS_JSON, null)
        }

        /** 键未登记时抛异常（快速失败，防止漏登记）。 */
        @Suppress("UNCHECKED_CAST")
        fun <T> defaultFor(key: Preferences.Key<T>): T = DEFAULTS.getValue(key) as T

        fun from(prefs: Preferences): AppSettings = AppSettings(
            historyRecordingEnabled = prefs.orDefault(SettingsKeys.HISTORY_RECORDING_ENABLED),
            heartbeatAnimationEnabled = prefs.orDefault(SettingsKeys.HEARTBEAT_ANIMATION_ENABLED),
            speedDisplayEnabled = prefs.orDefault(SettingsKeys.SPEED_DISPLAY_ENABLED),
            hideFromRecentsEnabled = prefs.orDefault(SettingsKeys.HIDE_FROM_RECENTS_ENABLED),
            fullscreenSoundEnabled = prefs.orDefault(SettingsKeys.FULLSCREEN_SOUND_ENABLED),
            fullscreenSoundMode = prefs.orDefault(SettingsKeys.FULLSCREEN_SOUND_MODE),
            autoConnectEnabled = prefs.orDefault(SettingsKeys.AUTO_CONNECT_ENABLED),
            autoReconnectEnabled = prefs.orDefault(SettingsKeys.AUTO_RECONNECT_ENABLED),
            scanFilterEnabled = prefs.orDefault(SettingsKeys.SCAN_FILTER_ENABLED),
            navAnimationDisabled = prefs.orDefault(SettingsKeys.NAV_ANIMATION_DISABLED),
            favoriteDeviceId = prefs.orDefault(SettingsKeys.FAVORITE_DEVICE_ID),
            floatingWindowEnabled = prefs.orDefault(SettingsKeys.FLOATING_WINDOW_ENABLED),
            floatingSize = prefs.orDefault(SettingsKeys.FLOATING_SIZE),
            floatingIconSize = prefs.orDefault(SettingsKeys.FLOATING_ICON_SIZE),
            floatingCornerRadius = prefs.orDefault(SettingsKeys.FLOATING_CORNER_RADIUS),
            floatingBgAlpha = prefs.orDefault(SettingsKeys.FLOATING_BG_ALPHA),
            floatingBorderAlpha = prefs.orDefault(SettingsKeys.FLOATING_BORDER_ALPHA),
            floatingTextColor = prefs.orDefault(SettingsKeys.FLOATING_TEXT_COLOR),
            floatingBgColor = prefs.orDefault(SettingsKeys.FLOATING_BG_COLOR),
            floatingBorderColor = prefs.orDefault(SettingsKeys.FLOATING_BORDER_COLOR),
            bpmTextEnabled = prefs.orDefault(SettingsKeys.BPM_TEXT_ENABLED),
            heartIconEnabled = prefs.orDefault(SettingsKeys.HEART_ICON_ENABLED),
            floatingX = prefs.orDefault(SettingsKeys.FLOATING_X),
            floatingY = prefs.orDefault(SettingsKeys.FLOATING_Y),
            statusBarResidentEnabled = prefs.orDefault(SettingsKeys.STATUS_BAR_RESIDENT_ENABLED),
            statusBarBpmTextEnabled = prefs.orDefault(SettingsKeys.STATUS_BAR_BPM_TEXT_ENABLED),
            statusBarXPosition = prefs.orDefault(SettingsKeys.STATUS_BAR_X_POSITION),
            statusBarYOffset = prefs.orDefault(SettingsKeys.STATUS_BAR_Y_OFFSET),
            statusBarSize = prefs.orDefault(SettingsKeys.STATUS_BAR_SIZE),
            statusBarTextThickness = prefs.orDefault(SettingsKeys.STATUS_BAR_TEXT_THICKNESS),
            statusBarTextColor = prefs.orDefault(SettingsKeys.STATUS_BAR_TEXT_COLOR),
            heartRateAlarmEnabled = prefs.orDefault(SettingsKeys.HEART_RATE_ALARM_ENABLED),
            heartRateAlarmExcludePostureDetection =
                prefs.orDefault(SettingsKeys.HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION),
            heartRateAlarmHighThreshold = prefs.orDefault(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD),
            heartRateAlarmLowThreshold = prefs.orDefault(SettingsKeys.HEART_RATE_ALARM_LOW_THRESHOLD),
            heartRateAlarmDurationSeconds = prefs.orDefault(SettingsKeys.HEART_RATE_ALARM_DURATION_SECONDS),
            heartRateAlarmRepeatEnabled = prefs.orDefault(SettingsKeys.HEART_RATE_ALARM_REPEAT_ENABLED),
            heartRateAlarmRepeatIntervalMinutes =
                prefs.orDefault(SettingsKeys.HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES),
            postureCalibrationData = prefs.orDefault(SettingsKeys.POSTURE_CALIBRATION_DATA),
            heartRateRingMax = prefs.orDefault(SettingsKeys.HEART_RATE_RING_MAX),
            httpServerEnabled = prefs.orDefault(SettingsKeys.HTTP_SERVER_ENABLED),
            httpServerPort = prefs.orDefault(SettingsKeys.HTTP_SERVER_PORT),
            websocketServerEnabled = prefs.orDefault(SettingsKeys.WEBSOCKET_SERVER_ENABLED),
            websocketServerPort = prefs.orDefault(SettingsKeys.WEBSOCKET_SERVER_PORT),
            serverAccessToken = prefs.orDefault(SettingsKeys.SERVER_ACCESS_TOKEN),
            themeSource = prefs.orDefault(SettingsKeys.THEME_SOURCE),
            themeMode = prefs.orDefault(SettingsKeys.THEME_MODE),
            themeCustomSeed = prefs.orDefault(SettingsKeys.THEME_CUSTOM_SEED),
            themePaletteStyle = prefs.orDefault(SettingsKeys.THEME_PALETTE_STYLE),
            liquidGlassEnabled = prefs.orDefault(SettingsKeys.LIQUID_GLASS_ENABLED),
            liquidGlassBlurDp = prefs.orDefault(SettingsKeys.LIQUID_GLASS_BLUR),
            liquidGlassDistortionDp = prefs.orDefault(SettingsKeys.LIQUID_GLASS_DISTORTION),
            killStateSaved = prefs.orDefault(SettingsKeys.KILL_STATE_SAVED),
            killStateRoute = prefs.orDefault(SettingsKeys.KILL_STATE_ROUTE),
            killStateTab = prefs.orDefault(SettingsKeys.KILL_STATE_TAB),
            killStateFullscreen = prefs.orDefault(SettingsKeys.KILL_STATE_FULLSCREEN),
            killStateConnectedDeviceId = prefs.orDefault(SettingsKeys.KILL_STATE_CONNECTED_DEVICE_ID),
            killStateConnectedDeviceName = prefs.orDefault(SettingsKeys.KILL_STATE_CONNECTED_DEVICE_NAME),
            killStateTimestamp = prefs.orDefault(SettingsKeys.KILL_STATE_TIMESTAMP),
            changelogLastShownVersion = prefs.orDefault(SettingsKeys.CHANGELOG_LAST_SHOWN_VERSION),
            lastMemoryLimiterExitChecked = prefs.orDefault(SettingsKeys.LAST_MEMORY_LIMITER_EXIT_CHECKED),
            favoriteHistoryMigratedToRoom = prefs.orDefault(SettingsKeys.FAVORITE_HISTORY_MIGRATED_TO_ROOM),
            favoriteDeviceHistory = prefs.orDefault(SettingsKeys.FAVORITE_DEVICE_HISTORY),
            searchTipShown = prefs.orDefault(SettingsKeys.SEARCH_TIP_SHOWN),
            appLanguage = prefs.orDefault(SettingsKeys.APP_LANGUAGE)
        )

        private fun <T> Preferences.orDefault(key: Preferences.Key<T>): T =
            this[key] ?: defaultFor(key)
    }
}
