package com.github.heartratemonitor_compose.ui.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.service.HeartRateAlarmService
import com.github.heartratemonitor_compose.service.ServiceLauncher
import com.github.heartratemonitor_compose.service.posture.PostureCalibration
import com.github.heartratemonitor_compose.service.posture.PostureType
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * MVI 架构。预警设置自 SettingsRepository.settings 快照投影进 UiState，
 * 姿态/校准状态自 [HeartRateAlarmService] 的 StateFlow 投影，设置写入经 Intent 上行。
 *
 * **姿态检测与校准全部由服务侧管理**——VM 不再自建 PostureDetector 或注册传感器，
 * 避免服务与 VM 双重传感器注册导致的高频状态更新与主线程开销。
 * VM 绑定服务后订阅其 posture/isCalibrating/calibrationProgress 等流，
 * 姿态变化时才 setState（distinctUntilChanged 天然去抖）。
 */
@HiltViewModel
class HeartRateAlarmViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val serviceLauncher: ServiceLauncher,
    @ApplicationContext private val appContext: Context
) : MviViewModel<HeartRateAlarmUiState, HeartRateAlarmIntent>(initialHeartRateAlarmUiState(settings)) {

    private var alarmService: HeartRateAlarmService? = null
    @Volatile private var isServiceBound = false
    private var postureJob: Job? = null
    private var calibrationJob: Job? = null
    private var calibrationProgressJob: Job? = null
    private var calibratingIsSittingJob: Job? = null
    private var currentCalibrationJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HeartRateAlarmService.LocalBinder
            alarmService = binder.getService()
            isServiceBound = true
            observeServiceFlows()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            postureJob?.cancel()
            calibrationJob?.cancel()
            calibrationProgressJob?.cancel()
            calibratingIsSittingJob?.cancel()
            currentCalibrationJob?.cancel()
            alarmService = null
            isServiceBound = false
            setState { it.copy(currentPosture = PostureType.UNKNOWN) }
        }
    }

    init {
        val high = settings.get(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD)
        val low = settings.get(SettingsKeys.HEART_RATE_ALARM_LOW_THRESHOLD)
        if (high <= low) {
            settings.set(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, low + 1)
        }

        // 预警设置真源投影
        viewModelScope.launch {
            settings.settings.collect { s ->
                setState {
                    it.copy(
                        alarmEnabled = s.heartRateAlarmEnabled,
                        excludePostureDetection = s.heartRateAlarmExcludePostureDetection,
                        highThreshold = s.heartRateAlarmHighThreshold,
                        lowThreshold = s.heartRateAlarmLowThreshold,
                        durationSeconds = s.heartRateAlarmDurationSeconds,
                        repeatEnabled = s.heartRateAlarmRepeatEnabled,
                        repeatInterval = s.heartRateAlarmRepeatIntervalMinutes
                    )
                }
            }
        }

        bindAlarmService()
    }

    private fun bindAlarmService() {
        val intent = Intent(appContext, HeartRateAlarmService::class.java)
        try {
            appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (_: Exception) {
            // bindService 失败时 UI 保持初始姿态 UNKNOWN，不影响预警设置
        }
    }

    private fun observeServiceFlows() {
        val service = alarmService ?: return

        // 姿态流：StateFlow 本身只发射变化后的值，姿态未变不触发 setState
        postureJob?.cancel()
        postureJob = viewModelScope.launch {
            service.posture.collect { posture ->
                setState { it.copy(currentPosture = posture) }
            }
        }

        calibrationJob?.cancel()
        calibrationJob = viewModelScope.launch {
            service.isCalibrating.collect { calibrating ->
                setState { it.copy(isCalibrating = calibrating) }
            }
        }

        calibrationProgressJob?.cancel()
        calibrationProgressJob = viewModelScope.launch {
            service.calibrationProgress.collect { progress ->
                setState { it.copy(calibrationProgress = progress) }
            }
        }

        calibratingIsSittingJob?.cancel()
        calibratingIsSittingJob = viewModelScope.launch {
            service.calibratingIsSitting.collect { isSitting ->
                setState { it.copy(calibratingIsSitting = isSitting) }
            }
        }

        currentCalibrationJob?.cancel()
        currentCalibrationJob = viewModelScope.launch {
            service.currentCalibration.collect { cal ->
                setState { it.copy(currentCalibration = cal) }
            }
        }
    }

    override suspend fun handleIntent(intent: HeartRateAlarmIntent) {
        when (intent) {
            is HeartRateAlarmIntent.SetAlarmEnabled -> {
                settings.set(SettingsKeys.HEART_RATE_ALARM_ENABLED, intent.enabled)
                if (intent.enabled) serviceLauncher.startHeartRateAlarmService()
                else serviceLauncher.stopHeartRateAlarmService()
            }
            is HeartRateAlarmIntent.SetExcludePostureDetection -> {
                settings.set(SettingsKeys.HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION, intent.enabled)
            }
            is HeartRateAlarmIntent.SetHighThreshold ->
                settings.set(
                    SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD,
                    clampHighThreshold(intent.value, currentState.lowThreshold)
                )
            is HeartRateAlarmIntent.SetLowThreshold ->
                settings.set(
                    SettingsKeys.HEART_RATE_ALARM_LOW_THRESHOLD,
                    clampLowThreshold(intent.value, currentState.highThreshold)
                )
            is HeartRateAlarmIntent.SetDurationSeconds ->
                settings.set(SettingsKeys.HEART_RATE_ALARM_DURATION_SECONDS, intent.value)
            is HeartRateAlarmIntent.SetRepeatEnabled ->
                settings.set(SettingsKeys.HEART_RATE_ALARM_REPEAT_ENABLED, intent.enabled)
            is HeartRateAlarmIntent.SetRepeatInterval ->
                settings.set(SettingsKeys.HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES, intent.value)
            is HeartRateAlarmIntent.StartCalibration -> {
                alarmService?.startCalibration(intent.isSitting)
            }
            HeartRateAlarmIntent.ClearCalibration -> {
                alarmService?.clearCalibration()
            }
            HeartRateAlarmIntent.StartPostureDetection -> { /* ⚠️ 反直觉设计：姿态检测由服务管理，VM 仅透传 */ }
            HeartRateAlarmIntent.StopPostureDetection -> { /* ⚠️ 反直觉设计：姿态检测由服务管理，VM 仅透传 */ }
        }
    }

    override fun onCleared() {
        super.onCleared()
        postureJob?.cancel()
        calibrationJob?.cancel()
        calibrationProgressJob?.cancel()
        calibratingIsSittingJob?.cancel()
        currentCalibrationJob?.cancel()
        if (isServiceBound) {
            try {
                appContext.unbindService(serviceConnection)
            } catch (_: Exception) {
            }
            isServiceBound = false
        }
    }

    companion object {
        const val CALIBRATION_DURATION_SECONDS = HeartRateAlarmService.CALIBRATION_DURATION_SECONDS
    }
}

/** 阈值互相约束的纯归约函数（高 ≥ 低 + 1），可独立单测。 */
internal fun clampHighThreshold(value: Int, lowThreshold: Int): Int = maxOf(value, lowThreshold + 1)

/** 阈值互相约束的纯归约函数（低 ≤ 高 − 1），可独立单测。 */
internal fun clampLowThreshold(value: Int, highThreshold: Int): Int = minOf(value, highThreshold - 1)

/** 心率预警设置页用户意图。 */
sealed interface HeartRateAlarmIntent {
    data class SetAlarmEnabled(val enabled: Boolean) : HeartRateAlarmIntent
    data class SetExcludePostureDetection(val enabled: Boolean) : HeartRateAlarmIntent
    data class SetHighThreshold(val value: Int) : HeartRateAlarmIntent
    data class SetLowThreshold(val value: Int) : HeartRateAlarmIntent
    data class SetDurationSeconds(val value: Int) : HeartRateAlarmIntent
    data class SetRepeatEnabled(val enabled: Boolean) : HeartRateAlarmIntent
    data class SetRepeatInterval(val value: Int) : HeartRateAlarmIntent

    /** 启动姿态校准（true = 静坐，false = 站立）。 */
    data class StartCalibration(val isSitting: Boolean) : HeartRateAlarmIntent
    data object ClearCalibration : HeartRateAlarmIntent
    data object StartPostureDetection : HeartRateAlarmIntent
    data object StopPostureDetection : HeartRateAlarmIntent
}

/** 心率预警设置页 UI 状态（只读快照）。 */
data class HeartRateAlarmUiState(
    val alarmEnabled: Boolean,
    val excludePostureDetection: Boolean,
    val highThreshold: Int,
    val lowThreshold: Int,
    val durationSeconds: Int,
    val repeatEnabled: Boolean,
    val repeatInterval: Int,
    val currentPosture: PostureType = PostureType.UNKNOWN,
    val currentCalibration: PostureCalibration? = null,
    val isCalibrating: Boolean = false,
    val calibrationProgress: Int = 0,
    val calibratingIsSitting: Boolean = false
)

/** 初始状态：设置字段取预热快照真值，校准数据读一次。 */
internal fun initialHeartRateAlarmUiState(settings: SettingsRepository): HeartRateAlarmUiState {
    val s = settings.settings.value
    return HeartRateAlarmUiState(
        alarmEnabled = s.heartRateAlarmEnabled,
        excludePostureDetection = s.heartRateAlarmExcludePostureDetection,
        highThreshold = s.heartRateAlarmHighThreshold,
        lowThreshold = s.heartRateAlarmLowThreshold,
        durationSeconds = s.heartRateAlarmDurationSeconds,
        repeatEnabled = s.heartRateAlarmRepeatEnabled,
        repeatInterval = s.heartRateAlarmRepeatIntervalMinutes,
        currentCalibration = PostureCalibration.fromJson(
            settings.getNullable(SettingsKeys.POSTURE_CALIBRATION_DATA)
        )
    )
}
