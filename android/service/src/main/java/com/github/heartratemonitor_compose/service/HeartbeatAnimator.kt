package com.github.heartratemonitor_compose.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt

/**
 * 心跳缩放动画驱动器：用 [ValueAnimator]（非 Compose Animatable）+ 手动 30fps 跳帧 +
 * 全局帧时钟同步，scale 值写入 [mutableFloatStateOf] 只在 draw-phase 触发 draw-only。
 *
 * ⚠️ 反直觉设计：仅周期变化时重建 animator（非每帧），开关/连接变化但心率不变时不重建——
 * 避免 stop→restart 的 scale 跳变；首次循环从当前 scale 平滑过渡到 1f，后续从 1f 正常起跳。
 */
class HeartbeatAnimator {

    private var animator: ValueAnimator? = null
    private var currentCycleMs = 0
    private var isFirstCycle = true

    val scaleState = mutableFloatStateOf(1f)

    fun update(bpm: Int, enabled: Boolean, connected: Boolean) {
        if (!enabled || bpm <= 30 || !connected) {
            stop()
            return
        }
        val cycleMs = (60000f / bpm).roundToInt()

        // 仅周期变化时重建 animator
        if (cycleMs != currentCycleMs) {
            currentCycleMs = cycleMs
            restartAnimator(cycleMs)
        }
    }

    private fun restartAnimator(cycleMs: Int) {
        animator?.cancel()
        isFirstCycle = true
        val startScale = scaleState.floatValue
        animator = ValueAnimator.ofFloat(startScale, 1.2f, 1f).apply {
            duration = cycleMs.toLong()
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            // 手动 30fps 跳帧：全局帧计数器，同进程所有实例统一刷新或跳过
            addUpdateListener {
                if (!GlobalFrameClock.shouldRender()) {
                    return@addUpdateListener
                }
                scaleState.floatValue = animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: Animator) {
                    if (isFirstCycle) {
                        isFirstCycle = false
                        // RESTART 在动画开始后才读取 values，首次循环结束切回 1f 起点
                        (animation as ValueAnimator).setFloatValues(1f, 1.2f, 1f)
                    }
                }
            })
            start()
        }
    }

    /**
     * ⚠️ 反直觉设计：stop 不重置 scaleState——关闭时爱心停在当前值而非跳回 1f，
     * 重新开启从当前位置继续，消除顿挫。
     */
    fun stop() {
        animator?.cancel()
        animator = null
        currentCycleMs = 0
    }

    /**
     * ⚠️ 反直觉设计：全局帧时钟使悬浮窗+状态栏实例在同一帧统一刷新/跳过——
     * nanoTime/16.6ms 帧号偶数渲染，所有实例看到同一奇偶值。
     */
    private object GlobalFrameClock {
        private val frameIntervalNs = 16_666_667L // 60fps ≈ 16.6ms

        fun shouldRender(): Boolean {
            val frameNumber = System.nanoTime() / frameIntervalNs
            return frameNumber % 2L == 0L
        }
    }
}
