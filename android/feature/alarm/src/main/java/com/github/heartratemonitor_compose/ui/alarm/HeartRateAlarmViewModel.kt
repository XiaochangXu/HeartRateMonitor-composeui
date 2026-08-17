package com.github.heartratemonitor_compose.ui.alarm

import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.settings.SettingsKeys
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.sensor.PostureSensorProvider
import com.github.heartratemonitor_compose.service.ServiceLauncher
import com.github.heartratemonitor_compose.service.posture.PostureCalibration
import com.github.heartratemonitor_compose.service.posture.PostureDetector
import com.github.heartratemonitor_compose.service.posture.PostureFeatures
import com.github.heartratemonitor_compose.service.posture.PostureType
import com.github.heartratemonitor_compose.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import javax.inject.Inject

/**
 * MVI 架构，Phase 4。预警设置自 SettingsRepository.settings 快照投影进 UiState，
 * 设置写入经 Intent 上行：阈值 clamp 为纯 reduce 函数，Service 启停经 ServiceLauncher。
 * 依赖由 Hilt 构造注入（Phase 3 起）。
 */
@HiltViewModel
class HeartRateAlarmViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val postureSensorProvider: PostureSensorProvider,
    private val serviceLauncher: ServiceLauncher
) : MviViewModel<HeartRateAlarmUiState, HeartRateAlarmIntent>(initialHeartRateAlarmUiState(settings)) {

    private val postureDetector = PostureDetector()

    /** 校准计数协程句柄：打断/重入时须取消旧协程，防止并发校准竞态 */
    private var calibrationJob: Job? = null

    private val calibrationBuffer = mutableListOf<FloatArray>()

    init {
        postureDetector.setCalibration(currentState.currentCalibration)

        val high = settings.get(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD)
        val low = settings.get(SettingsKeys.HEART_RATE_ALARM_LOW_THRESHOLD)
        if (high <= low) {
            settings.set(SettingsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, low + 1)
        }

        // 预警设置真源投影（currentCalibration 由校准流程自管理，不参与投影，与旧实现一致）
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
                if (intent.enabled) {
                    // 开启排除时中断正在进行的校准：取消计数协程，防止旧协程到点后
                    // 提前终止后续新校准的进度 UI 并提交不完整的校准数据
                    calibrationJob?.cancel()
                    calibrationJob = null
                    setState { it.copy(isCalibrating = false) }
                    calibrationBuffer.clear()
                    stopPostureDetection()
                }
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
            is HeartRateAlarmIntent.StartCalibration -> startCalibration(intent.isSitting)
            HeartRateAlarmIntent.ClearCalibration -> {
                settings.remove(SettingsKeys.POSTURE_CALIBRATION_DATA)
                setState { it.copy(currentCalibration = null) }
                postureDetector.setCalibration(null)
            }
            HeartRateAlarmIntent.StartPostureDetection -> startPostureDetection()
            HeartRateAlarmIntent.StopPostureDetection -> stopPostureDetection()
        }
    }

    // ── 传感器生命周期 ──

    private fun startPostureDetection() {
        postureDetector.setCalibration(currentState.currentCalibration)
        postureSensorProvider.start(
            onSample = { x, y, z ->
                postureDetector.onSensorSample(x, y, z)
                if (currentState.isCalibrating) {
                    calibrationBuffer.add(floatArrayOf(x, y, z))
                }
            },
            onClassify = {
                setState { it.copy(currentPosture = postureDetector.classify()) }
            },
            classifyIntervalMs = CLASSIFY_INTERVAL_MS
        )
    }

    private fun stopPostureDetection() {
        postureSensorProvider.stop()
        setState { it.copy(currentPosture = PostureType.UNKNOWN) }
    }

    // ── 校准 ──

    private fun startCalibration(isSitting: Boolean) {
        // 取消上一次未完成的校准（如被排除开关打断后残留的计数协程），
        // 避免两个校准协程并发：旧协程到点后提前结束新校准并重复提交
        calibrationJob?.cancel()
        calibrationJob = null
        setState { it.copy(calibratingIsSitting = isSitting, isCalibrating = true, calibrationProgress = 0) }
        calibrationBuffer.clear()

        calibrationJob = viewModelScope.launch {
            for (i in 1..CALIBRATION_DURATION_SECONDS) {
                delay(1000L)
                setState { it.copy(calibrationProgress = i) }
            }
            setState { it.copy(isCalibrating = false) }
            finishCalibration()
            calibrationJob = null
        }
    }

    /**
     * 计算校准缓冲区特征并持久化。
     * 计算三轴均值与加速度模长标准差，生成 [PostureFeatures]，
     * 追加到对应姿态的样本列表中。
     */
    private fun finishCalibration() {
        if (calibrationBuffer.isEmpty()) return
        val isSitting = currentState.calibratingIsSitting
        val samples = calibrationBuffer.toList()
        calibrationBuffer.clear()

        val n = samples.size
        val meanX = samples.map { it[0] }.average().toFloat()
        val meanY = samples.map { it[1] }.average().toFloat()
        val meanZ = samples.map { it[2] }.average().toFloat()
        val magnitudes = samples.map { sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) }
        val stdMag = sqrt(magnitudes.map { (it - magnitudes.average()) * (it - magnitudes.average()) }.average()).toFloat()

        val features = PostureFeatures(meanX, meanY, meanZ, stdMag, n)
        val existing = currentState.currentCalibration
        val sitSamples = existing?.sittingSamples ?: emptyList()
        val standSamples = existing?.standingSamples ?: emptyList()
        val updated = if (isSitting) {
            PostureCalibration(
                sittingSamples = sitSamples + features,
                standingSamples = standSamples,
                motionThreshold = existing?.motionThreshold ?: 1.5f,
                calibratedAt = System.currentTimeMillis()
            )
        } else {
            PostureCalibration(
                sittingSamples = sitSamples,
                standingSamples = standSamples + features,
                motionThreshold = existing?.motionThreshold ?: 1.5f,
                calibratedAt = System.currentTimeMillis()
            )
        }
        settings.set(SettingsKeys.POSTURE_CALIBRATION_DATA, updated.toJson())
        setState { it.copy(currentCalibration = updated) }
        postureDetector.setCalibration(updated)
    }

    override fun onCleared() {
        super.onCleared()
        postureSensorProvider.stop()
    }

    companion object {
        const val CALIBRATION_DURATION_SECONDS = 10
        private const val CLASSIFY_INTERVAL_MS = 200L
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

/** 初始状态：设置字段取预热快照真值，校准数据读一次（与旧实现一致）。 */
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
