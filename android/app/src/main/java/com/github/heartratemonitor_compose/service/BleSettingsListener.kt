package com.github.heartratemonitor_compose.service

import android.content.SharedPreferences
import com.github.heartratemonitor_compose.data.PrefsKeys

/**
 * 负责监听 SharedPreferences 变更并分发到对应回调。
 *
 * 将设置项变更响应逻辑从 [BleService] 中剥离，
 * [BleService] 通过构造函数注入各回调即可。
 *
 * @param sharedPreferences 应用设置 SharedPreferences 实例
 * @param onServerSettingsChanged 服务器相关设置（HTTP/WS 开关、端口、Token）变更回调
 * @param onSpeedSettingsChanged 速度显示开关变更回调
 * @param onHistoryRecordingDisabled 历史记录开关被关闭时的回调
 */
class BleSettingsListener(
    private val sharedPreferences: SharedPreferences,
    private val onServerSettingsChanged: () -> Unit,
    private val onSpeedSettingsChanged: () -> Unit,
    private val onHistoryRecordingDisabled: () -> Unit
) {
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            PrefsKeys.HTTP_SERVER_ENABLED,
            PrefsKeys.HTTP_SERVER_PORT,
            PrefsKeys.WEBSOCKET_SERVER_ENABLED,
            PrefsKeys.WEBSOCKET_SERVER_PORT,
            PrefsKeys.SERVER_ACCESS_TOKEN -> onServerSettingsChanged()

            PrefsKeys.SPEED_DISPLAY_ENABLED -> onSpeedSettingsChanged()

            PrefsKeys.HISTORY_RECORDING_ENABLED -> {
                // 关闭历史记录开关时，立即结束当前 session，
                // 避免 endTime 一直为 NULL 导致 UI 显示「进行中」直到下次启动。
                if (!sharedPreferences.getBoolean(PrefsKeys.HISTORY_RECORDING_ENABLED, false)) {
                    onHistoryRecordingDisabled()
                }
            }
        }
    }

    /** 注册 SharedPreferences 监听器。 */
    fun register() =
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)

    /** 注销 SharedPreferences 监听器。 */
    fun unregister() =
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
}
