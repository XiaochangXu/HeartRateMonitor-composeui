package com.github.heartratemonitor_compose.service

import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 设置项变更响应器：各流 drop(1) 跳过初始发射，「仅响应注册后变化」。
 */
class BleSettingsListener(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val onServerSettingsChanged: () -> Unit,
    private val onSpeedSettingsChanged: () -> Unit,
    private val onHistoryRecordingDisabled: () -> Unit,
    private val onChartCacheClear: () -> Unit
) {
    private var jobs: List<Job> = emptyList()

    fun register() {
        jobs = listOf(
            scope.launch {
                merge(
                    settingsRepository.observe(SettingsKeys.HTTP_SERVER_ENABLED).drop(1),
                    settingsRepository.observe(SettingsKeys.HTTP_SERVER_PORT).drop(1),
                    settingsRepository.observe(SettingsKeys.WEBSOCKET_SERVER_ENABLED).drop(1),
                    settingsRepository.observe(SettingsKeys.WEBSOCKET_SERVER_PORT).drop(1),
                    settingsRepository.observe(SettingsKeys.SERVER_ACCESS_TOKEN).drop(1)
                ).collect { onServerSettingsChanged() }
            },
            scope.launch {
                settingsRepository.observe(SettingsKeys.SPEED_DISPLAY_ENABLED)
                    .drop(1)
                    .collect { onSpeedSettingsChanged() }
            },
            scope.launch {
                settingsRepository.observe(SettingsKeys.HISTORY_RECORDING_ENABLED)
                    .drop(1)
                    .collect { enabled ->
                        if (!enabled) {
                            onHistoryRecordingDisabled()
                            onChartCacheClear()
                        }
                    }
            }
        )
    }

    fun unregister() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }
}
