package com.github.heartratemonitor_compose.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.github.heartratemonitor_compose.R
import kotlinx.coroutines.CompletableDeferred


class SoundManager(context: Context, languageMode: String) {

    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<SoundType, Int>()
    private val loadedIds = mutableSetOf<Int>()
    private val loadDeferred = CompletableDeferred<Unit>()
    private val durationsMs = mutableMapOf<SoundType, Long>()
    /** 已收到的回调计数（含成功与失败），用于判断是否所有样本都已上报 */
    private val loadCallbackCount = java.util.concurrent.atomic.AtomicInteger(0)

    enum class SoundType {
        HIGH_BEEP,
        LOW_BEEP,
        TOO_HIGH,
        TOO_LOW
    }

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(attrs)
            .build()
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            // 仅成功的样本加入 loadedIds，用于 play() 时做播放门控
            if (status == 0) {
                loadedIds.add(sampleId)
            }
            // 无论成功或失败都计数，确保所有回调到齐后 loadDeferred 完成
            if (loadCallbackCount.incrementAndGet() == SoundType.entries.size) {
                loadDeferred.complete(Unit)
            }
        }
        // 根据用户设置的语言模式选择中/英文语音
        val isChinese = languageMode == "cn"
        val resIds = mapOf(
            SoundType.HIGH_BEEP to R.raw.high_beep,
            SoundType.LOW_BEEP to R.raw.low_beep,
            SoundType.TOO_HIGH to if (isChinese) R.raw.too_high_cn else R.raw.too_high,
            SoundType.TOO_LOW to if (isChinese) R.raw.too_low_cn else R.raw.too_low
        )
        val appCtx = context.applicationContext
        for (type in SoundType.entries) {
            val resId = resIds[type]!!
            soundIds[type] = soundPool.load(appCtx, resId, 1)
            durationsMs[type] = measureDurationMs(appCtx, resId)
        }
    }

    /**
     * 用 MediaPlayer 读取音频文件实际时长（毫秒），用于精确控制状态语音独占时间。
     * 失败时回退 1000ms。
     */
    private fun measureDurationMs(context: Context, resId: Int): Long {
        return try {
            val mp = MediaPlayer.create(context, resId)
            val duration = mp.duration.toLong()
            mp.release()
            duration
        } catch (e: Exception) {
            1000L
        }
    }

    /** 返回指定音频的实际时长（毫秒），用于播放后精确等待播完 */
    fun getDurationMs(type: SoundType): Long = durationsMs[type] ?: 1000L

    /**
     * 挂起等待所有样本加载完成（含成功与失败）。建议在播放前调用一次。
     * 所有样本回调到齐后即返回；个别样本加载失败不会导致永久挂起，
     * 但 [play] 会自动跳过未成功加载的样本。
     */
    suspend fun awaitLoaded() = loadDeferred.await()

    fun play(type: SoundType, volume: Float = 1f) {
        val v = volume.coerceIn(0f, 1f)
        val id = soundIds[type] ?: return
        if (id !in loadedIds) return
        soundPool.play(id, v, v, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
