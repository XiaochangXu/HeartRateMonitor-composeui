package com.github.heartratemonitor_compose.ui.main

import android.content.Context
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.AppSettings
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.service.ConnectedDevice
import com.github.heartratemonitor_compose.ui.util.resolveSoundMode
import com.juul.kable.Advertisement

enum class AppStatus {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

/**
 * BLE 状态变化触发的一次性 Toast 事件（UI 文案由 Activity 映射，
 * VM 不持有 :app 模块字符串资源）。
 */
enum class BleToastEvent { CONNECTED, AUTO_RECONNECTING, RECONNECT_FAILED, AUTO_CONNECT_FAILED }

/**
 * 主模块唯一 UI 状态（MVI 架构，Phase 5）。
 * 默认值引用 [AppSettings.DEFAULTS]，全屏文字色默认 RED 为历史分歧点。
 */
data class MainUiState(
    val heartRate: Int = 0,
    val speed: Float = 0f,
    val appStatus: AppStatus = AppStatus.DISCONNECTED,
    val statusMessage: String = "",
    val connectingDeviceId: String? = null,
    val scanResults: List<Advertisement> = emptyList(),
    val connectedDevice: ConnectedDevice? = null,
    val favoriteDeviceId: String? = null,
    val chartDataSnapshot: ChartDataSnapshot? = null,
    val sessionMaxHr: Int = 0,
    val sessionMinHr: Int = 0,
    val isHistoryEnabled: Boolean = AppSettings.defaultFor(SettingsKeys.HISTORY_RECORDING_ENABLED),
    val isSpeedEnabled: Boolean = AppSettings.defaultFor(SettingsKeys.SPEED_DISPLAY_ENABLED),
    val scanFilterEnabled: Boolean = AppSettings.defaultFor(SettingsKeys.SCAN_FILTER_ENABLED),
    val ringMaxHr: Int = AppSettings.defaultFor(SettingsKeys.HEART_RATE_RING_MAX),
    val floatingWindowEnabled: Boolean = AppSettings.defaultFor(SettingsKeys.FLOATING_WINDOW_ENABLED),
    val hideFromRecentsEnabled: Boolean = AppSettings.defaultFor(SettingsKeys.HIDE_FROM_RECENTS_ENABLED),
    val searchTipShown: Boolean = AppSettings.defaultFor(SettingsKeys.SEARCH_TIP_SHOWN),
    val heartbeatAnimationEnabled: Boolean = AppSettings.defaultFor(SettingsKeys.HEARTBEAT_ANIMATION_ENABLED),
    val fullscreenHeartTextColor: Int = android.graphics.Color.RED,
    val fullscreenSoundMode: String = "off"
)

/**
 * 设备页专用精简状态：从 [MainUiState] 投影出设备页需要的字段。
 *
 * 避免设备页全量订阅 [MainUiState]：heartRate / speed / chartDataSnapshot 等高频字段
 * 每秒更新，设备页完全不需要，但会导致整个 DevicesScreen 无效重组。
 * 通过 [MainViewModel.devicesUiState] 的 map + distinctUntilChanged 只在相关字段变化时发射。
 */
data class DevicesUiState(
    val appStatus: AppStatus = AppStatus.DISCONNECTED,
    val scanResults: List<Advertisement> = emptyList(),
    val connectingDeviceId: String? = null,
    val connectedDevice: ConnectedDevice? = null,
    val favoriteDeviceId: String? = null,
    val scanFilterEnabled: Boolean = AppSettings.defaultFor(SettingsKeys.SCAN_FILTER_ENABLED),
    val searchTipShown: Boolean = AppSettings.defaultFor(SettingsKeys.SEARCH_TIP_SHOWN)
)

/** 将 [MainUiState] 投影为 [DevicesUiState]，用于 [MainViewModel.devicesUiState] 的初始值。 */
internal fun MainUiState.toDevicesUiState() = DevicesUiState(
    appStatus = appStatus,
    scanResults = scanResults,
    connectingDeviceId = connectingDeviceId,
    connectedDevice = connectedDevice,
    favoriteDeviceId = favoriteDeviceId,
    scanFilterEnabled = scanFilterEnabled,
    searchTipShown = searchTipShown
)

sealed interface MainIntent {
    data object StartScan : MainIntent
    data object StopScan : MainIntent
    data class ConnectToDevice(val identifier: String) : MainIntent
    data object DisconnectDevice : MainIntent
    data class ToggleFavoriteDevice(val advertisement: Advertisement) : MainIntent
    data object MarkSearchTipShown : MainIntent
    data class SetHeartRateRingMax(val value: Int) : MainIntent
}

/**
 * 初始状态：设置字段取预热快照真值（构造期零 IO），
 * 声音模式经 [resolveSoundMode] 完成旧开关一次性迁移（幂等）。
 */
internal fun initialMainUiState(settings: SettingsRepository, context: Context): MainUiState {
    val s = settings.settings.value
    return MainUiState(
        statusMessage = context.getString(
            com.github.heartratemonitor_compose.service.R.string.ble_idle
        ),
        favoriteDeviceId = s.favoriteDeviceId,
        isHistoryEnabled = s.historyRecordingEnabled,
        isSpeedEnabled = s.speedDisplayEnabled,
        scanFilterEnabled = s.scanFilterEnabled,
        ringMaxHr = s.heartRateRingMax,
        floatingWindowEnabled = s.floatingWindowEnabled,
        hideFromRecentsEnabled = s.hideFromRecentsEnabled,
        searchTipShown = s.searchTipShown,
        heartbeatAnimationEnabled = s.heartbeatAnimationEnabled,
        // 全屏页专属显式默认值 RED（历史分歧点，不进 AppSettings 快照语义）
        fullscreenHeartTextColor = settings.get(
            SettingsKeys.FLOATING_TEXT_COLOR, android.graphics.Color.RED
        ),
        fullscreenSoundMode = settings.getNullable(SettingsKeys.FULLSCREEN_SOUND_MODE)
            ?: resolveSoundMode(settings)
    )
}
