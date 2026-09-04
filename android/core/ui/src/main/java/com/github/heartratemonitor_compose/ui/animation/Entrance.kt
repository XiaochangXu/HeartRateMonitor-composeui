package com.github.heartratemonitor_compose.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** 相邻元素之间的入场间隔 */
const val ENTRANCE_STAGGER_MILLIS = 80L

/** 入场侧滑距离（dp） */
private const val ENTRANCE_TRANSLATION_DP = 48f

/** 入场弹簧阻尼比：中等回弹，幅度更大 */
private const val ENTRANCE_DAMPING = Spring.DampingRatioMediumBouncy

/** 入场弹簧刚度：降低让动画更柔和、持续时间更长 */
private const val ENTRANCE_STIFFNESS = 300f

/** 降级动效下的入场淡入时长 */
private const val ENTRANCE_REDUCED_FADE_MILLIS = 120

// 交错瀑布式弹簧入场：按 order 间隔启动，alpha + 侧滑；动画值在 Draw 阶段读取，入场零重组。

@Stable
class EntranceState internal constructor(
    internal val progress: Animatable<Float, AnimationVector1D>,
    internal val translate: Boolean,
) {
    /** 淡入进度 0f → 1f */
    val alpha: Float
        get() = progress.value

    /** 侧滑位移的剩余比例 1f → 0f，乘上位移距离即可用。降级时恒为 0f。 */
    val translateFraction: Float
        get() = if (translate) 1f - progress.value else 0f
}

// 记住出场进度（0f → 1f）；仅在首次组合且 play=true 时播放一次。
@Composable
fun rememberEntrance(
    order: Int,
    play: Boolean = true,
): EntranceState {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (play) 0f else 1f) }
    val state = remember(reduced) { EntranceState(progress = progress, !reduced) }
    LaunchedEffect(Unit) {
        if (progress.value >= 1f) return@LaunchedEffect
        if (reduced) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = ENTRANCE_REDUCED_FADE_MILLIS,
                    easing = { fraction -> 1f - (1f - fraction).let { it * it } }, // EaseOut
                ),
            )
        } else {
            delay(order * ENTRANCE_STAGGER_MILLIS)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = ENTRANCE_DAMPING,
                    stiffness = ENTRANCE_STIFFNESS,
                ),
            )
        }
    }
    return state
}

fun Modifier.entranceGraphics(entrance: EntranceState): Modifier =
    graphicsLayer {
        alpha = entrance.alpha
        translationX = entrance.translateFraction * ENTRANCE_TRANSLATION_DP.dp.toPx()
    }
