package com.github.heartratemonitor_compose.service.posture

import org.json.JSONArray
import org.json.JSONObject

/**
 * 单个姿态的校准特征。
 *
 * @param meanX/Y/Z 加速度三轴均值（反映重力方向，即手机朝向）
 * @param stdMagnitude 加速度模长的标准差（反映该姿态的基线噪声）
 * @param sampleCount 采样数
 */
data class PostureFeatures(
    val meanX: Float,
    val meanY: Float,
    val meanZ: Float,
    val stdMagnitude: Float,
    val sampleCount: Int
)

/**
 * 姿态校准数据。
 *
 * 每种姿态可采集多个样本（[sittingSamples]/[standingSamples]），以应对不同体位
 * （如手机放口袋、握在手中、置于桌面等不同朝向）。实时检测时取与当前窗口欧氏距离
 * 最小的样本参与判定。
 *
 * 序列化为 JSON 存储于 SharedPreferences key [posture_calibration_data]。
 * 新格式使用 `sitting_samples`/`standing_samples` 数组；旧格式（单对象 `sitting`/`standing`）
 * 在 [fromJson] 中自动兼容，解析为单元素列表。
 */
data class PostureCalibration(
    val sittingSamples: List<PostureFeatures>,
    val standingSamples: List<PostureFeatures>,
    val motionThreshold: Float = 1.5f,
    val calibratedAt: Long = 0L
) {
    /** 兼容旧用法：取首个静坐样本（无则 null） */
    val sitting: PostureFeatures? get() = sittingSamples.firstOrNull()

    /** 兼容旧用法：取首个站立样本（无则 null） */
    val standing: PostureFeatures? get() = standingSamples.firstOrNull()

    /** 静坐和站立均至少有一个样本才算校准完成 */
    fun isComplete(): Boolean = sittingSamples.isNotEmpty() && standingSamples.isNotEmpty()

    /** 序列化为 JSON 字符串 */
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("motion_threshold", motionThreshold)
        obj.put("calibrated_at", calibratedAt)
        obj.put("sitting_samples", featuresListToJson(sittingSamples))
        obj.put("standing_samples", featuresListToJson(standingSamples))
        return obj.toString()
    }

    private fun featuresListToJson(list: List<PostureFeatures>): JSONArray {
        val arr = JSONArray()
        for (f in list) arr.put(featuresToJson(f))
        return arr
    }

    private fun featuresToJson(f: PostureFeatures): JSONObject = JSONObject().apply {
        put("mean_x", f.meanX)
        put("mean_y", f.meanY)
        put("mean_z", f.meanZ)
        put("std_magnitude", f.stdMagnitude)
        put("sample_count", f.sampleCount)
    }

    companion object {
        /** 欧氏距离匹配阈值（m/s²），距离小于此值才判定为对应姿态 */
        const val MATCH_THRESHOLD = 5.0f

        /** 从 JSON 字符串反序列化，解析失败返回 null。兼容旧单对象格式。 */
        fun fromJson(json: String?): PostureCalibration? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                val sitting = parseSamples(obj, "sitting_samples", "sitting")
                val standing = parseSamples(obj, "standing_samples", "standing")
                PostureCalibration(
                    sittingSamples = sitting,
                    standingSamples = standing,
                    motionThreshold = obj.optDouble("motion_threshold", 1.5).toFloat(),
                    calibratedAt = obj.optLong("calibrated_at", 0L)
                )
            } catch (_: Exception) {
                null
            }
        }

        /**
         * 解析某姿态的样本列表。
         * 优先读取新数组字段 [arrayKey]；若不存在则回退到旧单对象字段 [legacyKey]，
         * 包装为单元素列表，保证旧数据平滑迁移。
         */
        private fun parseSamples(obj: JSONObject, arrayKey: String, legacyKey: String): List<PostureFeatures> {
            obj.optJSONArray(arrayKey)?.let { arr ->
                val list = mutableListOf<PostureFeatures>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(parseFeatures(it)) }
                }
                return list
            }
            obj.optJSONObject(legacyKey)?.let { return listOf(parseFeatures(it)) }
            return emptyList()
        }

        private fun parseFeatures(o: JSONObject): PostureFeatures = PostureFeatures(
            meanX = o.optDouble("mean_x", 0.0).toFloat(),
            meanY = o.optDouble("mean_y", 0.0).toFloat(),
            meanZ = o.optDouble("mean_z", 0.0).toFloat(),
            stdMagnitude = o.optDouble("std_magnitude", 0.0).toFloat(),
            sampleCount = o.optInt("sample_count", 0)
        )
    }
}
