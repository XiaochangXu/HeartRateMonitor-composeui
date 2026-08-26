package com.github.heartratemonitor_compose.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.roundToInt

/**
 * 心跳缩放动画驱动器：替代 Compose 的 [androidx.compose.animation.core.Animatable]。
 *
 * 核心优化：
 * 1. 用 [ValueAnimator] 替代 [androidx.compose.animation.core.Animatable]，
 *    不走 Compose 的 [androidx.compose.runtime.MonotonicFrameClock] 调度链。
 * 2. 手动跳帧降为 30fps：心跳缩放是缓慢的视觉脉冲，60fps 完全不必要。
 * 3. scale 值通过 [mutableFloatStateOf] 写入，Compose 端在 [androidx.compose.ui.graphics.graphicsLayer]
 *    或 [androidx.compose.foundation.Canvas] 的 draw-phase lambda 中读取，
 *    值变化只触发 draw-only，不触发 recomposition + relayout。
 * 4. 跳帧判定基于全局帧计数器 [GlobalFrameClock]，使同一进程内的多个 HeartbeatAnimator
 *    实例（悬浮窗 + 状态栏）在同一帧统一刷新 / 统一跳过，避免两次独立的 draw dispatch
 *    分散在同一帧的不同时刻、加倍占用主线程。
 *
 * 使用单个循环 [ValueAnimator]（RESTART 模式），避免两段交替播放的段切换开销。
 *
 * 顿挫修复：
 * - [update] 区分「周期变化重建」与「开关/连接状态变化」：状态变化但周期不变时不重建 animator，
 *   避免 stop→restart 导致 scale 从 1f 突然跳变。
 * - [stop] 不再把 scaleState 强制设为 1f：关闭动画时 scale 停在当前值而非跳回 1f。
 * - [restartAnimator] 用 RESTART 模式 + AnimatorListener 在首次循环结束后将起点切回 1f：
 *   首次播放从当前 scaleState 值平滑过渡到 1f，之后每个循环从 1f 正常起跳。
 */
class HeartbeatAnimator {

    private var animator: ValueAnimator? = null
    private var currentCycleMs = 0
    private var isFirstCycle = true

    /** 当前 scale 值，供 Compose draw-phase 读取。 */
    val scaleState = mutableFloatStateOf(1f)

    /**
     * 启动或更新心跳动画。
     *
     * @param bpm 当前心率（bpm），决定动画周期
     * @param enabled 心跳动画是否启用
     * @param connected 设备是否已连接
     */
    fun update(bpm: Int, enabled: Boolean, connected: Boolean) {
        if (!enabled || bpm <= 30 || !connected) {
            stop()
            return
        }
        val cycleMs = (60000f / bpm).roundToInt()

        // 仅在周期变化时重建 animator，避免每帧重建。
        // 开关/连接状态变化但心率不变时（如开关心跳动画、触摸穿透恢复），
        // animator 仍在正常运行，不需要重建——避免 stop→restart 的 scale 跳变。
        if (cycleMs != currentCycleMs) {
            currentCycleMs = cycleMs
            restartAnimator(cycleMs)
        }
    }

    private fun restartAnimator(cycleMs: Int) {
        animator?.cancel()
        isFirstCycle = true
        // 首次播放从当前 scaleState 值起步，平滑过渡到 1f，不跳变。
        // 后续循环从 1f→1.2f→1f 正常起跳。
        val startScale = scaleState.floatValue
        animator = ValueAnimator.ofFloat(startScale, 1.2f, 1f).apply {
            duration = cycleMs.toLong()
            repeatMode = ValueAnimator.RESTART
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            // 手动跳帧降为 30fps：基于全局帧计数器，同一帧所有实例统一刷新或跳过
            addUpdateListener {
                if (!GlobalFrameClock.shouldRender()) {
                    return@addUpdateListener
                }
                // mutableFloatStateOf.floatValue 的 setter 本身是原子写入，
                // 不需要 Snapshot.withMutableSnapshot 包裹（省去每帧 snapshot 创建/提交开销）
                scaleState.floatValue = animatedValue as Float
            }
            // 首次循环结束后，将 ofFloat 起点切回 1f，后续循环从 1f 正常起跳。
            // 不直接用 setFloatValues 是因为 RESTART 在动画开始后才读取 values，
            // 在 repeat 回调中修改不影响当前正在进行的循环。
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: Animator) {
                    if (isFirstCycle) {
                        isFirstCycle = false
                        (animation as ValueAnimator).setFloatValues(1f, 1.2f, 1f)
                    }
                }
            })
            start()
        }
    }

    /**
     * 停止动画。
     *
     * 不重置 [scaleState]：关闭动画时爱心停在当前缩放值而非突然跳回 1f，
     * 重新开启时从当前位置继续，消除跳变顿挫。
     */
    fun stop() {
        animator?.cancel()
        animator = null
        currentCycleMs = 0
    }

    /**
     * 全局帧时钟：所有 [HeartbeatAnimator] 实例共享同一个帧计数器。
     *
     * ValueAnimator 的 addUpdateListener 回调与 Choreographer 帧回调同步（均 60fps），
     * 同一帧内多个 ValueAnimator 的回调顺序虽不固定，但 [System.nanoTime] 相同。
     * 以 nanoTime / 帧间隔（≈16.6ms）的整除结果作为帧号，偶数帧渲染、奇数帧跳过，
     * 即所有实例在同一帧看到同一个奇偶值 → 同一帧统一刷新或跳过。
     */
    private object GlobalFrameClock {
        private val frameIntervalNs = 16_666_667L // 60fps ≈ 16.6ms

        fun shouldRender(): Boolean {
            val frameNumber = System.nanoTime() / frameIntervalNs
            return frameNumber % 2L == 0L // 偶数帧渲染 → 30fps
        }
    }
}
