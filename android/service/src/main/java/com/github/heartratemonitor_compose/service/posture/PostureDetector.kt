package com.github.heartratemonitor_compose.service.posture

import kotlin.math.sqrt

/**
 * 接收加速度样本，滑动窗口，~200ms/classify 输出姿态。
 *
 * 1. 运动判定：stdMag > 阈值且**持续 3 秒以上**才识别 EXERCISE（短暂晃动不触发）。
 * 2. 静坐/站立：窗口各轴均值与校准样本取欧氏距离，最小且 < 阈值者胜出。
 *
 * ⚠️ 反直觉设计：运动经持续时长判定不经滞回；静坐/站立经 5 次投票 >= 3 才切换，防边界抖动。
 */
class PostureDetector {

    private val windowSize = 100
    private val sampleBuffer = ArrayDeque<FloatArray>(windowSize)
    private val magnitudeBuffer = ArrayDeque<Float>(windowSize)

    private var calibration: PostureCalibration? = null

    private val recentClassifications = ArrayDeque<PostureType>(5)
    private var stablePosture = PostureType.UNKNOWN

    private var largeMotionStartMs = 0L

    fun setCalibration(cal: PostureCalibration?) {
        calibration = cal
        reset()
    }

    fun isCalibrated(): Boolean = calibration?.isComplete() == true

    fun onSensorSample(x: Float, y: Float, z: Float) {
        if (sampleBuffer.size >= windowSize) {
            sampleBuffer.removeFirst()
            magnitudeBuffer.removeFirst()
        }
        sampleBuffer.addLast(floatArrayOf(x, y, z))
        magnitudeBuffer.addLast(sqrt(x * x + y * y + z * z))
    }

    fun classify(): PostureType {
        val cal = calibration
        if (sampleBuffer.size < windowSize / 2) return PostureType.UNKNOWN
        if (cal == null || !cal.isComplete()) return PostureType.UNKNOWN

        val meanX = sampleBuffer.map { it[0] }.average().toFloat()
        val meanY = sampleBuffer.map { it[1] }.average().toFloat()
        val meanZ = sampleBuffer.map { it[2] }.average().toFloat()
        val stdMag = computeStd(magnitudeBuffer)
        val now = System.currentTimeMillis()

        if (stdMag > EXERCISE_SHAKE_THRESHOLD) {
            if (largeMotionStartMs == 0L) {
                largeMotionStartMs = now
            }
            if (now - largeMotionStartMs >= EXERCISE_SUSTAINED_MS) {
                // 持续时长已提供稳定性，不经滞回
                stablePosture = PostureType.EXERCISE
                recentClassifications.clear()
                return stablePosture
            }
            return stablePosture
        } else {
            largeMotionStartMs = 0L
        }

        val distSit = cal.sittingSamples.minOfOrNull {
            euclidean(meanX, meanY, meanZ, it.meanX, it.meanY, it.meanZ)
        } ?: Float.MAX_VALUE
        val distStand = cal.standingSamples.minOfOrNull {
            euclidean(meanX, meanY, meanZ, it.meanX, it.meanY, it.meanZ)
        } ?: Float.MAX_VALUE

        val candidate = when {
            distSit < PostureCalibration.MATCH_THRESHOLD && distSit < distStand -> PostureType.SITTING
            distStand < PostureCalibration.MATCH_THRESHOLD && distStand < distSit -> PostureType.STANDING
            else -> PostureType.UNKNOWN
        }
        return updateStable(candidate)
    }

    fun currentStablePosture(): PostureType = stablePosture

    fun reset() {
        sampleBuffer.clear()
        magnitudeBuffer.clear()
        recentClassifications.clear()
        stablePosture = PostureType.UNKNOWN
        largeMotionStartMs = 0L
    }

    private fun updateStable(candidate: PostureType): PostureType {
        if (recentClassifications.size >= 5) recentClassifications.removeFirst()
        recentClassifications.addLast(candidate)
        if (recentClassifications.count { it == candidate } >= 3) {
            stablePosture = candidate
        }
        return stablePosture
    }

    private fun computeStd(values: ArrayDeque<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average()
        var sumSq = 0.0
        for (v in values) sumSq += (v - mean) * (v - mean)
        return sqrt(sumSq / values.size).toFloat()
    }

    private fun euclidean(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float
    ): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        val dz = z1 - z2
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        /** 大幅晃动阈值（加速度模长标准差 m/s²），基线噪声之上。 */
        private const val EXERCISE_SHAKE_THRESHOLD = 2.5f

        private const val EXERCISE_SUSTAINED_MS = 3000L
    }
}
