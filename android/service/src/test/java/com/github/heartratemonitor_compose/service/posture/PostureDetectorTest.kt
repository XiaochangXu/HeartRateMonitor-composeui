package com.github.heartratemonitor_compose.service.posture

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * [PostureDetector] 单元测试。
 *
 * 验证：
 * - 无校准时返回 UNKNOWN。
 * - 校准后样本与坐姿/站姿匹配，经滞回防抖后输出稳定姿态。
 * - 窗口样本不足时返回 UNKNOWN。
 */
class PostureDetectorTest {

    private lateinit var detector: PostureDetector

    @Before
    fun setup() {
        detector = PostureDetector()
    }

    @Test
    fun `no calibration returns UNKNOWN`() {
        fillSamples(sittingX = 0f, sittingY = 0f, sittingZ = 9.8f)
        assertEquals(PostureType.UNKNOWN, detector.classify())
    }

    @Test
    fun `insufficient samples returns UNKNOWN`() {
        detector.setCalibration(createCalibration())
        repeat(10) { detector.onSensorSample(0f, 0f, 9.8f) }
        assertEquals(PostureType.UNKNOWN, detector.classify())
    }

    @Test
    fun `sitting samples classified as SITTING after hysteresis`() {
        detector.setCalibration(createCalibration())
        // 连续 5 次分类窗口与坐姿样本一致，触发滞回防抖
        repeat(5) {
            fillSamples(sittingX = 0f, sittingY = 0f, sittingZ = 9.8f)
            detector.classify()
        }
        assertEquals(PostureType.SITTING, detector.currentStablePosture())
    }

    @Test
    fun `standing samples classified as STANDING after hysteresis`() {
        detector.setCalibration(createCalibration())
        repeat(5) {
            fillSamples(sittingX = 0f, sittingY = 9.8f, sittingZ = 0f)
            detector.classify()
        }
        assertEquals(PostureType.STANDING, detector.currentStablePosture())
    }

    @Test
    fun `unknown posture when neither sitting nor standing matches`() {
        detector.setCalibration(createCalibration())
        repeat(5) {
            fillSamples(sittingX = 5f, sittingY = 5f, sittingZ = 5f)
            detector.classify()
        }
        assertEquals(PostureType.UNKNOWN, detector.currentStablePosture())
    }

    private fun createCalibration(): PostureCalibration {
        val sitting = PostureFeatures(meanX = 0f, meanY = 0f, meanZ = 9.8f, stdMagnitude = 0.1f, sampleCount = 100)
        val standing = PostureFeatures(meanX = 0f, meanY = 9.8f, meanZ = 0f, stdMagnitude = 0.1f, sampleCount = 100)
        return PostureCalibration(
            sittingSamples = listOf(sitting),
            standingSamples = listOf(standing),
            motionThreshold = 1.5f,
            calibratedAt = 0L
        )
    }

    private fun fillSamples(sittingX: Float, sittingY: Float, sittingZ: Float) {
        // 滑动窗口大小为 100，需填满窗口才能进入分类逻辑
        repeat(100) { detector.onSensorSample(sittingX, sittingY, sittingZ) }
    }
}
