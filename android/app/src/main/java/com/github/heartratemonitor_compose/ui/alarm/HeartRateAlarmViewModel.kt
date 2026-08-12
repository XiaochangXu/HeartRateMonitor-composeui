package com.github.heartratemonitor_compose.ui.alarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.heartratemonitor_compose.data.PrefsKeys
import com.github.heartratemonitor_compose.data.di.appContainer
import com.github.heartratemonitor_compose.data.repository.SettingsRepository
import com.github.heartratemonitor_compose.data.sensor.PostureSensorProvider
import com.github.heartratemonitor_compose.service.ServiceController
import com.github.heartratemonitor_compose.service.posture.PostureCalibration
import com.github.heartratemonitor_compose.service.posture.PostureDetector
import com.github.heartratemonitor_compose.service.posture.PostureFeatures
import com.github.heartratemonitor_compose.service.posture.PostureType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * 心率预警设置页面的 ViewModel。
 *
 * 职责：
 * - 通过 [SettingsRepository] 的 StateFlow 暴露预警相关设置，UI 通过 collectAsStateWithLifecycle 收集。
 * - 管理姿态传感器生命周期（启动/停止）与姿态分类状态。
 * - 管理姿态校准流程（采集、计算特征、持久化）。
 * - 封装设置写入逻辑（阈值互相约束、Service 启停）。
 *
 * 替代原 [HeartRateAlarmScreen] 中 8 个 mutableStateOf 和直接 settings.set* 调用。
 */
class HeartRateAlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val settings: SettingsRepository = application.appContainer.settingsRepository
    private val postureSensorProvider: PostureSensorProvider = application.appContainer.postureSensorProvider
    private val postureDetector = PostureDetector()

    // ── 预警设置（直接暴露 SettingsRepository 的 StateFlow，SharedPreferences listener 同步更新）──
    val alarmEnabled: StateFlow<Boolean> =
        settings.observeBoolean(PrefsKeys.HEART_RATE_ALARM_ENABLED, false)
    val excludePostureDetection: StateFlow<Boolean> =
        settings.observeBoolean(PrefsKeys.HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION, false)
    val highThreshold: StateFlow<Int> =
        settings.observeInt(PrefsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, 100)
    val lowThreshold: StateFlow<Int> =
        settings.observeInt(PrefsKeys.HEART_RATE_ALARM_LOW_THRESHOLD, 50)
    val durationSeconds: StateFlow<Int> =
        settings.observeInt(PrefsKeys.HEART_RATE_ALARM_DURATION_SECONDS, 10)
    val repeatEnabled: StateFlow<Boolean> =
        settings.observeBoolean(PrefsKeys.HEART_RATE_ALARM_REPEAT_ENABLED, false)
    val repeatInterval: StateFlow<Int> =
        settings.observeInt(PrefsKeys.HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES, 5)

    // ── 姿态检测状态 ──
    private val _currentPosture = MutableStateFlow(PostureType.UNKNOWN)
    val currentPosture: StateFlow<PostureType> = _currentPosture.asStateFlow()

    // ── 校准状态 ──
    private val _currentCalibration = MutableStateFlow(
        PostureCalibration.fromJson(settings.getStringNullable(PrefsKeys.POSTURE_CALIBRATION_DATA))
    )
    val currentCalibration: StateFlow<PostureCalibration?> = _currentCalibration.asStateFlow()

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibrating: StateFlow<Boolean> = _isCalibrating.asStateFlow()

    private val _calibrationProgress = MutableStateFlow(0)
    val calibrationProgress: StateFlow<Int> = _calibrationProgress.asStateFlow()

    /** 当前正在校准的姿态是否为静坐（false = 站立） */
    private val _calibratingIsSitting = MutableStateFlow(false)
    val calibratingIsSitting: StateFlow<Boolean> = _calibratingIsSitting.asStateFlow()

    // 校准数据缓冲区（非 UI 状态，不暴露）
    private val calibrationBuffer = mutableListOf<FloatArray>()

    init {
        postureDetector.setCalibration(_currentCalibration.value)

        // 修正历史无效值：高阈值至少比低阈值大 1
        if (highThreshold.value <= lowThreshold.value) {
            settings.setInt(PrefsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, lowThreshold.value + 1)
        }
    }

    // ── 传感器生命周期 ──

    /**
     * 启动姿态传感器监听。
     * 由 Composable 的 DisposableEffect 调用，在 excludePostureDetection 为 false 时启动。
     */
    fun startPostureDetection() {
        postureDetector.setCalibration(_currentCalibration.value)
        postureSensorProvider.start(
            onSample = { x, y, z ->
                postureDetector.onSensorSample(x, y, z)
                if (_isCalibrating.value) {
                    calibrationBuffer.add(floatArrayOf(x, y, z))
                }
            },
            onClassify = { _currentPosture.value = postureDetector.classify() },
            classifyIntervalMs = CLASSIFY_INTERVAL_MS
        )
    }

    /**
     * 停止姿态传感器监听并重置当前姿态。
     */
    fun stopPostureDetection() {
        postureSensorProvider.stop()
        _currentPosture.value = PostureType.UNKNOWN
    }

    // ── 设置写入 ──

    fun setAlarmEnabled(enabled: Boolean) {
        settings.setBoolean(PrefsKeys.HEART_RATE_ALARM_ENABLED, enabled)
        val context = getApplication<Application>()
        if (enabled) ServiceController.startHeartRateAlarmService(context)
        else ServiceController.stopHeartRateAlarmService(context)
    }

    fun setExcludePostureDetection(enabled: Boolean) {
        settings.setBoolean(PrefsKeys.HEART_RATE_ALARM_EXCLUDE_POSTURE_DETECTION, enabled)
        if (enabled) {
            // 开启排除时中断正在进行的校准
            _isCalibrating.value = false
            calibrationBuffer.clear()
            stopPostureDetection()
        }
    }

    /** 高阈值至少比低阈值大 1 */
    fun setHighThreshold(value: Int) {
        val clamped = maxOf(value, lowThreshold.value + 1)
        settings.setInt(PrefsKeys.HEART_RATE_ALARM_HIGH_THRESHOLD, clamped)
    }

    /** 低阈值至多比高阈值小 1 */
    fun setLowThreshold(value: Int) {
        val clamped = minOf(value, highThreshold.value - 1)
        settings.setInt(PrefsKeys.HEART_RATE_ALARM_LOW_THRESHOLD, clamped)
    }

    fun setDurationSeconds(value: Int) {
        settings.setInt(PrefsKeys.HEART_RATE_ALARM_DURATION_SECONDS, value)
    }

    fun setRepeatEnabled(enabled: Boolean) {
        settings.setBoolean(PrefsKeys.HEART_RATE_ALARM_REPEAT_ENABLED, enabled)
    }

    fun setRepeatInterval(value: Int) {
        settings.setInt(PrefsKeys.HEART_RATE_ALARM_REPEAT_INTERVAL_MINUTES, value)
    }

    // ── 校准 ──

    /**
     * 启动姿态校准。
     * @param isSitting true = 校准静坐，false = 校准站立
     */
    fun startCalibration(isSitting: Boolean) {
        _calibratingIsSitting.value = isSitting
        _isCalibrating.value = true
        _calibrationProgress.value = 0
        calibrationBuffer.clear()

        viewModelScope.launch {
            for (i in 1..CALIBRATION_DURATION_SECONDS) {
                delay(1000L)
                _calibrationProgress.value = i
            }
            _isCalibrating.value = false
            finishCalibration()
        }
    }

    /**
     * 计算校准缓冲区特征并持久化。
     * 计算三轴均值与加速度模长标准差，生成 [PostureFeatures]，
     * 追加到对应姿态的样本列表中。
     */
    private fun finishCalibration() {
        if (calibrationBuffer.isEmpty()) return
        val isSitting = _calibratingIsSitting.value
        val samples = calibrationBuffer.toList()
        calibrationBuffer.clear()

        val n = samples.size
        val meanX = samples.map { it[0] }.average().toFloat()
        val meanY = samples.map { it[1] }.average().toFloat()
        val meanZ = samples.map { it[2] }.average().toFloat()
        val magnitudes = samples.map { sqrt(it[0] * it[0] + it[1] * it[1] + it[2] * it[2]) }
        val stdMag = sqrt(magnitudes.map { (it - magnitudes.average()) * (it - magnitudes.average()) }.average()).toFloat()

        val features = PostureFeatures(meanX, meanY, meanZ, stdMag, n)
        val existing = _currentCalibration.value
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
        settings.setString(PrefsKeys.POSTURE_CALIBRATION_DATA, updated.toJson())
        _currentCalibration.value = updated
        postureDetector.setCalibration(updated)
    }

    /**
     * 清除姿态校准数据。
     */
    fun clearCalibration() {
        settings.remove(PrefsKeys.POSTURE_CALIBRATION_DATA)
        _currentCalibration.value = null
        postureDetector.setCalibration(null)
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
