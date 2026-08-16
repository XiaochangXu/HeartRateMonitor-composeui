package com.github.heartratemonitor_compose.service

import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 将设置项变更响应逻辑从 [BleService] 中剥离。
 * 通过 SettingsRepository.observe 返回的 StateFlow 感知设置变化，
 * 各流 drop(1) 跳过订阅时的初始发射，保持原 listener「仅响应注册后变化」的语义。
 */
class BleSettingsListener(
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    private val onServerSettingsChanged: () -> Unit,
    private val onSpeedSettingsChanged: () -> Unit,
    private val onHistoryRecordingDisabled: () -> Unit
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
                        // 关闭历史记录开关时，立即结束当前 session，
                        // 避免 endTime 一直为 NULL 导致 UI 显示「进行中」直到下次启动。
                        if (!enabled) onHistoryRecordingDisabled()
                    }
            }
        )
    }

    fun unregister() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }
}
