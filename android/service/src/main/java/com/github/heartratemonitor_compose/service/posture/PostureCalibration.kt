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
 * 单个姿态校准特征。meanX/Y/Z 反映重力方向（手机朝向），stdMagnitude 反映基线噪声。
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
 * 姿态校准数据。每种姿态可采集多样本应对不同体位；实时检测取最小欧氏距离参与判定。
 *
 * ⚠️ 反直觉设计：新格式 sitting_samples/standing_samples 数组；旧格式 sitting/standing 单对象
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
    val sitting: PostureFeatures? get() = sittingSamples.firstOrNull()

    val standing: PostureFeatures? get() = standingSamples.firstOrNull()

    fun isComplete(): Boolean = sittingSamples.isNotEmpty() && standingSamples.isNotEmpty()

    fun toJson(): String {
        return json.encodeToString(this)
    }

    companion object {
        const val MATCH_THRESHOLD = 5.0f

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /**
         * ⚠️ 反直觉设计：先尝试 kotlinx.serialization 新格式；失败则降级到旧 org.json 格式——
         * 旧单对象字段 sitting/standing 解析为单元素列表。
         */
        fun fromJson(jsonString: String?): PostureCalibration? {
            if (jsonString.isNullOrBlank()) return null
            return try {
                json.decodeFromString<PostureCalibration>(jsonString)
            } catch (_: Exception) {
                try {
                    parseLegacyFormat(jsonString)
                } catch (_: Exception) {
                    null
                }
            }
        }

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
 * ImmutableList<PostureFeatures> 自定义序列化器：JSON 数组 ↔ ImmutableList，保持 Compose 稳定性推断。
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
