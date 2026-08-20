package com.github.heartratemonitor_compose.service.posture

import kotlin.math.sqrt

/**
 * 姿态检测器。
 *
 * 接收加速度传感器样本，维护一个滑动窗口，每 ~200ms 调用 [classify] 输出当前姿态。
 *
 * 算法分两步：
 * 1. 运动判定：加速度模长标准差（stdMag）超过 [EXERCISE_SHAKE_THRESHOLD] 视为"幅度较大的晃动"；
 *    仅当大幅晃动**连续持续 [EXERCISE_SUSTAINED_MS]（3 秒）以上**才识别为 EXERCISE。
 *    短暂晃动（调整姿势、拿取手机等）不会触发，避免误判。
 * 2. 静坐/站立区分：计算窗口各轴均值，与校准样本列表中每个样本求欧氏距离，取最小者；
 *    最小距离 < [PostureCalibration.MATCH_THRESHOLD] 且小于另一姿态者胜出；
 *    两者距离都大 → UNKNOWN（非校准姿态，如躺下睡眠，不触发预警）。
 *    多样本支持不同体位（口袋、手持、桌面等）。
 *
 * 滞回防抖：最近 5 次分类投票，票数 >= 3 才切换 stablePosture，避免边界抖动。
 * 运动姿态因已通过持续时长判定，直接写入 stablePosture 不经滞回。
 */
class PostureDetector {

    /** 滑动窗口大小（~2 秒 @ SENSOR_DELAY_GAME 50Hz） */
    private val windowSize = 100
    private val sampleBuffer = ArrayDeque<FloatArray>(windowSize)
    private val magnitudeBuffer = ArrayDeque<Float>(windowSize)

    private var calibration: PostureCalibration? = null

    /** 滞回防抖：最近 5 次分类结果 */
    private val recentClassifications = ArrayDeque<PostureType>(5)
    private var stablePosture = PostureType.UNKNOWN

    /** 大幅晃动持续计时起点（0 表示当前未处于大幅晃动状态） */
    private var largeMotionStartMs = 0L

    /** 设置校准数据（SharedPreferences 变化时热更新） */
    fun setCalibration(cal: PostureCalibration?) {
        calibration = cal
        reset()
    }

    /** 校准数据是否完整（静坐+站立均已采集） */
    fun isCalibrated(): Boolean = calibration?.isComplete() == true

    /**
     * 接收一个加速度样本。
     * @param x y z 三轴加速度（m/s²）
     */
    fun onSensorSample(x: Float, y: Float, z: Float) {
        if (sampleBuffer.size >= windowSize) {
            sampleBuffer.removeFirst()
            magnitudeBuffer.removeFirst()
        }
        sampleBuffer.addLast(floatArrayOf(x, y, z))
        magnitudeBuffer.addLast(sqrt(x * x + y * y + z * z))
    }

    /**
     * 对当前窗口进行姿态分类。
     * 每 ~200ms 调用一次，不必每样本调。
     */
    fun classify(): PostureType {
        val cal = calibration
        // 样本不足窗口一半 → 数据不足
        if (sampleBuffer.size < windowSize / 2) return PostureType.UNKNOWN
        // 无校准或校准不完整 → 无法区分静坐/站立
        if (cal == null || !cal.isComplete()) return PostureType.UNKNOWN

        // 计算窗口特征
        val meanX = sampleBuffer.map { it[0] }.average().toFloat()
        val meanY = sampleBuffer.map { it[1] }.average().toFloat()
        val meanZ = sampleBuffer.map { it[2] }.average().toFloat()
        val stdMag = computeStd(magnitudeBuffer)
        val now = System.currentTimeMillis()

        // 第一步：运动判定 —— 大幅晃动需持续 3 秒以上才识别为运动
        if (stdMag > EXERCISE_SHAKE_THRESHOLD) {
            if (largeMotionStartMs == 0L) {
                largeMotionStartMs = now
            }
            // 持续时长达标 → 直接识别为运动（持续时长已提供稳定性，不经滞回防抖）
            if (now - largeMotionStartMs >= EXERCISE_SUSTAINED_MS) {
                stablePosture = PostureType.EXERCISE
                recentClassifications.clear()
                return stablePosture
            }
            // 大幅晃动尚未持续 3 秒，保持当前稳定姿态，不立即切换
            return stablePosture
        } else {
            // 晃动停止，重置持续计时
            largeMotionStartMs = 0L
        }

        // 第二步：静坐/站立欧氏距离匹配（多样本取最小距离，应对不同体位）
        val distSit = cal.sittingSamples.minOfOrNull {
            euclidean(meanX, meanY, meanZ, it.meanX, it.meanY, it.meanZ)
        } ?: Float.MAX_VALUE
        val distStand = cal.standingSamples.minOfOrNull {
            euclidean(meanX, meanY, meanZ, it.meanX, it.meanY, it.meanZ)
        } ?: Float.MAX_VALUE

        val candidate = when {
            distSit < PostureCalibration.MATCH_THRESHOLD && distSit < distStand -> PostureType.SITTING
            distStand < PostureCalibration.MATCH_THRESHOLD && distStand < distSit -> PostureType.STANDING
            else -> PostureType.UNKNOWN  // 距离都大 → 非校准姿态（如躺下睡眠）
        }
        return updateStable(candidate)
    }

    /** 当前稳定的姿态（不经滞回防抖的最近结果） */
    fun currentStablePosture(): PostureType = stablePosture

    /** 清空缓冲区，用于传感器重新注册或校准重新开始 */
    fun reset() {
        sampleBuffer.clear()
        magnitudeBuffer.clear()
        recentClassifications.clear()
        stablePosture = PostureType.UNKNOWN
        largeMotionStartMs = 0L
    }

    /** 滞回防抖：候选加入最近 5 次记录，票数 >= 3 才更新稳定姿态 */
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
        /**
         * 大幅晃动阈值（加速度模长标准差，m/s²）。
         * 高于此值视为"幅度较大的晃动"。设置在基线噪声之上，避免轻微移动误判为运动。
         */
        private const val EXERCISE_SHAKE_THRESHOLD = 2.5f

        /** 大幅晃动需连续持续的时长（毫秒），达此值才识别为运动姿态。 */
        private const val EXERCISE_SUSTAINED_MS = 3000L
    }
}
