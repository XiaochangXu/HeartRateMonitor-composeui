package com.github.heartratemonitor_compose.ui.settings

import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 功能设置页面的 ViewModel（教科书式 MVI 试点，Phase 0）。
 *
 * 职责：
 * - 从 [SettingsRepository.settings] 全量快照派生页面所需 7 个开关状态：
 *   UiState 是设置真源的派生投影，Flow 回流经 [setState] 归约（状态下行）。
 * - 开关事件经 [FunctionSettingsIntent] dispatch 上行，handler 写入
 *   [SettingsRepository]，写后立读语义由乐观快照回流保证（契约 6 / §3.5）。
 *
 * 「开启历史记录/速度显示需确认弹窗」的弹窗显隐属纯瞬时态保留 UI 层，
 * 确认后 dispatch 对应 Intent（业务分支语义原样保留）。
 */
@HiltViewModel
class FunctionSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : MviViewModel<FunctionSettingsUiState, FunctionSettingsIntent>(
    initialFunctionSettingsUiState(settings)
) {

    init {
        // 设置真源投影：每次快照变化原子归约进 UiState，禁止本地双写（§3.5）
        viewModelScope.launch {
            settings.settings.collect { s ->
                setState {
                    it.copy(
                        historyRecordingEnabled = s.historyRecordingEnabled,
                        heartbeatAnimationEnabled = s.heartbeatAnimationEnabled,
                        speedDisplayEnabled = s.speedDisplayEnabled,
                        hideFromRecentsEnabled = s.hideFromRecentsEnabled,
                        autoConnectEnabled = s.autoConnectEnabled,
                        autoReconnectEnabled = s.autoReconnectEnabled,
                        scanFilterEnabled = s.scanFilterEnabled
                    )
                }
            }
        }
    }

    override suspend fun handleIntent(intent: FunctionSettingsIntent) {
        when (intent) {
            is FunctionSettingsIntent.SetHistoryRecording ->
                settings.set(SettingsKeys.HISTORY_RECORDING_ENABLED, intent.enabled)
            is FunctionSettingsIntent.SetHeartbeatAnimation ->
                settings.set(SettingsKeys.HEARTBEAT_ANIMATION_ENABLED, intent.enabled)
            is FunctionSettingsIntent.SetSpeedDisplay ->
                settings.set(SettingsKeys.SPEED_DISPLAY_ENABLED, intent.enabled)
            is FunctionSettingsIntent.SetHideFromRecents ->
                settings.set(SettingsKeys.HIDE_FROM_RECENTS_ENABLED, intent.enabled)
            is FunctionSettingsIntent.SetAutoConnect ->
                settings.set(SettingsKeys.AUTO_CONNECT_ENABLED, intent.enabled)
            is FunctionSettingsIntent.SetAutoReconnect ->
                settings.set(SettingsKeys.AUTO_RECONNECT_ENABLED, intent.enabled)
            is FunctionSettingsIntent.SetScanFilter ->
                settings.set(SettingsKeys.SCAN_FILTER_ENABLED, intent.enabled)
        }
    }
}

/** 功能设置页用户意图。 */
sealed interface FunctionSettingsIntent {
    data class SetHistoryRecording(val enabled: Boolean) : FunctionSettingsIntent
    data class SetHeartbeatAnimation(val enabled: Boolean) : FunctionSettingsIntent
    data class SetSpeedDisplay(val enabled: Boolean) : FunctionSettingsIntent
    data class SetHideFromRecents(val enabled: Boolean) : FunctionSettingsIntent
    data class SetAutoConnect(val enabled: Boolean) : FunctionSettingsIntent
    data class SetAutoReconnect(val enabled: Boolean) : FunctionSettingsIntent
    data class SetScanFilter(val enabled: Boolean) : FunctionSettingsIntent
}

/** 功能设置页 UI 状态（只读快照）。 */
data class FunctionSettingsUiState(
    val historyRecordingEnabled: Boolean,
    val heartbeatAnimationEnabled: Boolean,
    val speedDisplayEnabled: Boolean,
    val hideFromRecentsEnabled: Boolean,
    val autoConnectEnabled: Boolean,
    val autoReconnectEnabled: Boolean,
    val scanFilterEnabled: Boolean
)

/**
 * 初始状态：读 [SettingsRepository] 内存快照真实值（app 启动时已预热、零 IO），
 * 消除进入页面时"先默认值后快照覆盖"的闪变；键缺失时 [SettingsRepository.get]
 * 回落 [AppSettings.DEFAULTS]（契约 10.3）。
 */
internal fun initialFunctionSettingsUiState(settings: SettingsRepository): FunctionSettingsUiState = FunctionSettingsUiState(
    historyRecordingEnabled = settings.get(SettingsKeys.HISTORY_RECORDING_ENABLED),
    heartbeatAnimationEnabled = settings.get(SettingsKeys.HEARTBEAT_ANIMATION_ENABLED),
    speedDisplayEnabled = settings.get(SettingsKeys.SPEED_DISPLAY_ENABLED),
    hideFromRecentsEnabled = settings.get(SettingsKeys.HIDE_FROM_RECENTS_ENABLED),
    autoConnectEnabled = settings.get(SettingsKeys.AUTO_CONNECT_ENABLED),
    autoReconnectEnabled = settings.get(SettingsKeys.AUTO_RECONNECT_ENABLED),
    scanFilterEnabled = settings.get(SettingsKeys.SCAN_FILTER_ENABLED)
)
