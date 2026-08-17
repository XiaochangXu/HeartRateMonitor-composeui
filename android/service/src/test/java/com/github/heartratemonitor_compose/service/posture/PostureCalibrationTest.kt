package com.github.heartratemonitor_compose.service.posture

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PostureCalibration] 序列化/反序列化单元测试。
 */
class PostureCalibrationTest {

    @Test
    fun `isComplete requires both sitting and standing samples`() {
        val empty = PostureCalibration(emptyList(), emptyList())
        assertFalse(empty.isComplete())

        val onlySitting = PostureCalibration(
            sittingSamples = listOf(PostureFeatures(0f, 0f, 9.8f, 0.1f, 10)),
            standingSamples = emptyList()
        )
        assertFalse(onlySitting.isComplete())

        val complete = PostureCalibration(
            sittingSamples = listOf(PostureFeatures(0f, 0f, 9.8f, 0.1f, 10)),
            standingSamples = listOf(PostureFeatures(0f, 9.8f, 0f, 0.1f, 10))
        )
        assertTrue(complete.isComplete())
    }

    @Test
    fun `toJson and fromJson round trip preserves samples`() {
        val original = PostureCalibration(
            sittingSamples = listOf(PostureFeatures(1f, 2f, 3f, 0.5f, 50)),
            standingSamples = listOf(
                PostureFeatures(4f, 5f, 6f, 0.6f, 60),
                PostureFeatures(7f, 8f, 9f, 0.7f, 70)
            ),
            motionThreshold = 2.0f,
            calibratedAt = 12345678L
        )

        val restored = PostureCalibration.fromJson(original.toJson())
        assertNotNull(restored)
        assertEquals(2.0f, restored!!.motionThreshold)
        assertEquals(12345678L, restored.calibratedAt)
        assertEquals(1, restored.sittingSamples.size)
        assertEquals(2, restored.standingSamples.size)

        val firstSitting = restored.sittingSamples.first()
        assertEquals(1f, firstSitting.meanX, 0.001f)
        assertEquals(2f, firstSitting.meanY, 0.001f)
        assertEquals(3f, firstSitting.meanZ, 0.001f)
        assertEquals(0.5f, firstSitting.stdMagnitude, 0.001f)
        assertEquals(50, firstSitting.sampleCount)
    }

    @Test
    fun `fromJson null or blank returns null`() {
        assertNull(PostureCalibration.fromJson(null))
        assertNull(PostureCalibration.fromJson(""))
        assertNull(PostureCalibration.fromJson("   "))
    }

    @Test
    fun `fromJson supports legacy single-object format`() {
        val legacyJson = JSONObject().apply {
            put("motion_threshold", 1.2)
            put("calibrated_at", 100L)
            put(
                "sitting", JSONObject().apply {
                    put("mean_x", 0.0)
                    put("mean_y", 0.0)
                    put("mean_z", 9.8)
                    put("std_magnitude", 0.1)
                    put("sample_count", 20)
                }
            )
            put(
                "standing", JSONObject().apply {
                    put("mean_x", 0.0)
                    put("mean_y", 9.8)
                    put("mean_z", 0.0)
                    put("std_magnitude", 0.1)
                    put("sample_count", 20)
                }
            )
        }.toString()

        val restored = PostureCalibration.fromJson(legacyJson)
        assertNotNull(restored)
        assertEquals(1, restored!!.sittingSamples.size)
        assertEquals(1, restored.standingSamples.size)
        assertTrue(restored.isComplete())
    }
}
