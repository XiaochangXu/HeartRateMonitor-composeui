package com.github.heartratemonitor_compose.util

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.github.heartratemonitor_compose.ui.widgets.R
import kotlinx.coroutines.CompletableDeferred


class SoundManager(context: Context, languageMode: String) {

    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<SoundType, Int>()
    private val loadedIds = mutableSetOf<Int>()
    private val loadDeferred = CompletableDeferred<Unit>()
    private val durationsMs = mutableMapOf<SoundType, Long>()
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
            if (status == 0) {
                loadedIds.add(sampleId)
            }
            if (loadCallbackCount.incrementAndGet() == SoundType.entries.size) {
                loadDeferred.complete(Unit)
            }
        }
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

    fun getDurationMs(type: SoundType): Long = durationsMs[type] ?: 1000L

    // 个别样本加载失败不会导致永久挂起，play 自动跳过未成功加载的样本。
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
