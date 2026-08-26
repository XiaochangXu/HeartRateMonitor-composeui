package com.github.heartratemonitor_compose.service.posture

import android.util.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

/**
 * 单个姿态的校准特征。
 *
 * @param meanX/Y/Z 加速度三轴均值（反映重力方向，即手机朝向）
 * @param stdMagnitude 加速度模长的标准差（反映该姿态的基线噪声）
 * @param sampleCount 采样数
 */
@Serializable
data class PostureFeatures(
    @SerialName("mean_x") val meanX: Float,
    @SerialName("mean_y") val meanY: Float,
    @SerialName("mean_z") val meanZ: Float,
    @SerialName("std_magnitude") val stdMagnitude: Float,
    @SerialName("sample_count") val sampleCount: Int
)

/**
 * 姿态校准数据。
 *
 * 每种姿态可采集多个样本（[sittingSamples]/[standingSamples]），以应对不同体位
 * （如手机放口袋、握在手中、置于桌面等不同朝向）。实时检测时取与当前窗口欧氏距离
 * 最小的样本参与判定。
 *
 * 持久化到 DataStore 的 `stringPreferencesKey("posture_calibration_data")`，
 * 值为 kotlinx.serialization 序列化的 JSON 字符串。
 * 新格式使用 `sitting_samples`/`standing_samples` 数组；旧格式（单对象 `sitting`/`standing`）
 * 在 [fromJson] 中自动兼容，解析为单元素列表。
 */
@Serializable
data class PostureCalibration(
    @Serializable(with = ImmutablePostureFeaturesListSerializer::class)
    @SerialName("sitting_samples")
    val sittingSamples: ImmutableList<PostureFeatures>,
    @Serializable(with = ImmutablePostureFeaturesListSerializer::class)
    @SerialName("standing_samples")
    val standingSamples: ImmutableList<PostureFeatures>,
    @SerialName("motion_threshold") val motionThreshold: Float = 1.5f,
    @SerialName("calibrated_at") val calibratedAt: Long = 0L
) {
    /** 兼容旧用法：取首个静坐样本（无则 null） */
    val sitting: PostureFeatures? get() = sittingSamples.firstOrNull()

    /** 兼容旧用法：取首个站立样本（无则 null） */
    val standing: PostureFeatures? get() = standingSamples.firstOrNull()

    /** 静坐和站立均至少有一个样本才算校准完成 */
    fun isComplete(): Boolean = sittingSamples.isNotEmpty() && standingSamples.isNotEmpty()

    /** 序列化为 JSON 字符串（kotlinx.serialization） */
    fun toJson(): String {
        return json.encodeToString(this)
    }

    companion object {
        /** 欧氏距离匹配阈值（m/s²），距离小于此值才判定为对应姿态 */
        const val MATCH_THRESHOLD = 5.0f

        // 包级单例 Json 配置，容忍未知字段（向前兼容旧格式）
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * 从 JSON 字符串反序列化，解析失败返回 null。
         * 支持旧 org.json 格式（sitting/standing 单对象 → sitting_samples/standing_samples 数组）。
         */
        fun fromJson(jsonString: String?): PostureCalibration? {
            if (jsonString.isNullOrBlank()) return null
            return try {
                // 先尝试用 kotlinx.serialization 解析新格式
                json.decodeFromString<PostureCalibration>(jsonString)
            } catch (_: Exception) {
                // 解析失败，尝试旧 org.json 格式
                try {
                    parseLegacyFormat(jsonString)
                } catch (_: Exception) {
                    null
                }
            }
        }

        /**
         * 解析旧 org.json 格式：
         * - 旧数组字段 sitting_samples/standing_samples（与新格式 key 相同但由 org.json 序列化）
         * - 旧单对象字段 sitting/standing
         */
        private fun parseLegacyFormat(jsonString: String): PostureCalibration? {
            val obj = org.json.JSONObject(jsonString)
            val sitting = parseSamplesLegacy(obj, "sitting_samples", "sitting")
            val standing = parseSamplesLegacy(obj, "standing_samples", "standing")
            return PostureCalibration(
                sittingSamples = sitting,
                standingSamples = standing,
                motionThreshold = obj.optDouble("motion_threshold", 1.5).toFloat(),
                calibratedAt = obj.optLong("calibrated_at", 0L)
            )
        }

        private fun parseSamplesLegacy(
            obj: org.json.JSONObject,
            arrayKey: String,
            legacyKey: String
        ): ImmutableList<PostureFeatures> {
            obj.optJSONArray(arrayKey)?.let { arr ->
                val list = mutableListOf<PostureFeatures>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(parseFeaturesLegacy(it)) }
                }
                return list.toImmutableList()
            }
            obj.optJSONObject(legacyKey)?.let { return persistentListOf(parseFeaturesLegacy(it)) }
            return persistentListOf()
        }

        private fun parseFeaturesLegacy(o: org.json.JSONObject): PostureFeatures = PostureFeatures(
            meanX = o.optDouble("mean_x", 0.0).toFloat(),
            meanY = o.optDouble("mean_y", 0.0).toFloat(),
            meanZ = o.optDouble("mean_z", 0.0).toFloat(),
            stdMagnitude = o.optDouble("std_magnitude", 0.0).toFloat(),
            sampleCount = o.optInt("sample_count", 0)
        )
    }
}

/**
 * ImmutableList<PostureFeatures> 的自定义序列化器：
 * 序列化时输出为普通 JSON 数组，反序列化时还原为 ImmutableList，
 * 保持 Compose 稳定性推断。
 */
object ImmutablePostureFeaturesListSerializer :
    KSerializer<ImmutableList<PostureFeatures>> {
    private val delegate = ListSerializer(PostureFeatures.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: ImmutableList<PostureFeatures>) {
        delegate.serialize(encoder, value.toList())
    }

    override fun deserialize(decoder: Decoder): ImmutableList<PostureFeatures> {
        return delegate.deserialize(decoder).toImmutableList()
    }
}
