package com.github.heartratemonitor_compose.ui.main

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.heartratemonitor_compose.feature.main.R

import com.github.heartratemonitor_compose.util.SoundManager
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.PI
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 全屏模式心率状态机：用于驱动声音播放。
 * - HIGH：心率 > highThreshold（默认 100）
 * - LOW：心率 <= highThreshold
 */
private enum class FullscreenHrState { HIGH, LOW }

/**
 * 全屏心率模式覆盖层。
 *
 * - 纯黑背景，横屏全屏显示
 * - 静态爱心 + 心率数值，按屏幕高度自适应放到最大
 * - ECG 滚动波形：屏幕底部持续左滚的心电波形，QRS 与实际心率同步
 * - 爱心在 QRS 波峰时产生微妙光晕脉冲（非缩放动画）
 * - 颜色固定为红色（黑色背景下自定义文本颜色不可见）
 * - 点击屏幕或按返回键退出
 */
@Composable
fun FullScreenHeartRate(
    viewModel: MainViewModel,
    onExit: () -> Unit
) {
    // derivedStateOf 隔离：只有 heartRate 值真正变化时才触发读取位置的重组，
    // 避免 MainUiState 其他字段（speed/statusMessage 等）变化导致的无效重组。
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val heartRate by remember { derivedStateOf { uiState.heartRate } }
    val context = LocalContext.current
    // 全屏模式始终使用红色：黑色背景下悬浮窗/状态栏的自定义颜色不可见
    val heartColor = remember { ComposeColor.Red }
    val isAnimationEnabled = remember { viewModel.uiState.value.heartbeatAnimationEnabled }

    // ── 全屏模式声音：根据 FULLSCREEN_SOUND_MODE 选择关闭/中文/英文语音 ──
    val soundMode = remember { viewModel.uiState.value.fullscreenSoundMode }
    val highThreshold = 100  // 全屏模式声音阈值固定 100：高于 100 播高音，低于等于 100 播低音
    val soundManager = remember(soundMode) {
        if (soundMode != "off") SoundManager(context, soundMode) else null
    }
    DisposableEffect(soundManager) {
        onDispose { soundManager?.release() }
    }
    // 初始 null：首次状态语音播完后才设置，触发循环 beep 启动
    // UDF D3 定性：hrState/beepPaused 与组合作用域内的 soundManager 生命周期绑定，
    // 每次进入重置、不持久化不跨页面，符合判定标准 4 的瞬时态边界；
    // 上提 VM 需手工复制重置语义且对语音/beep 重叠时序（见下方注释）有回归风险，故保留 UI 层。
    var hrState by remember { mutableStateOf<FullscreenHrState?>(null) }
    // 语音播放期间暂停 beep，防止重叠
    var beepPaused by remember { mutableStateOf(false) }
    // 心率值变化不重启 beep 循环，循环内动态读取最新值
    val currentHeartRate by rememberUpdatedState(heartRate)

    // 心率更新驱动语音播放（单一收集协程，key 为 soundManager 而非 heartRate）：
    // - hrState == null 且 heartRate > 0：首次心率到达，播放初始状态语音
    // - hrState != null 且跨阈值：播放状态切换语音
    // 旧实现用 LaunchedEffect(heartRate) 作 key：心率每秒更新都会取消进行中的语音等待，
    // finally 提前放开 beepPaused → 语音与 beep 重叠；初始语音的 delay(500) 也会被反复打断。
    // snapshotFlow 合流心率更新：语音播放期间的 delay 不再被下一次心率更新取消；
    // 语音期间发生的跨阈值变化在语音结束后以最新心率重新判定，避免重复播放。
    // 必须经 rememberUpdatedState 的 currentHeartRate 读取：LaunchedEffect 闭包捕获的是
    // 首次组合时的值类型局部变量，重组不重启协程，直接读 heartRate 会永远停在进入全屏时的值，
    // 导致心率 0→首次数据、跨阈值切换等全部语音更新失效（与 beep 循环同一陷阱）。
    LaunchedEffect(soundManager) {
        val sm = soundManager ?: return@LaunchedEffect
        snapshotFlow { currentHeartRate }.collect { rate ->
            if (rate <= 0) return@collect
            val newState = if (rate > highThreshold) FullscreenHrState.HIGH else FullscreenHrState.LOW
            if (hrState == null) {
                // 首次心率到达：等待音频加载后播放初始状态语音
                delay(500)
                if (withTimeoutOrNull(2000) { sm.awaitLoaded() } == null) return@collect
                hrState = newState
                beepPaused = true
                try {
                    val voiceType = if (newState == FullscreenHrState.HIGH) SoundManager.SoundType.TOO_HIGH else SoundManager.SoundType.TOO_LOW
                    sm.play(voiceType)
                    delay(sm.getDurationMs(voiceType) + 500)
                } finally {
                    beepPaused = false
                }
            } else if (newState != hrState) {
                // 后续跨阈值：播放状态切换语音
                hrState = newState
                beepPaused = true
                try {
                    val voiceType = if (newState == FullscreenHrState.HIGH) SoundManager.SoundType.TOO_HIGH else SoundManager.SoundType.TOO_LOW
                    sm.play(voiceType)
                    delay(sm.getDurationMs(voiceType) + 150)
                } finally {
                    beepPaused = false
                }
            }
        }
    }

    // 循环 beep：按 60_000/bpm 间隔重复播放，节奏跟心跳
    // HIGH→high_beep, LOW→low_beep
    // 依赖 hrState + beepPaused：首次状态语音播完后（hrState 被设置）才启动；
    // 心率值不作为 key，循环内动态读取 currentHeartRate，避免每次心率更新取消重启导致节奏紊乱
    LaunchedEffect(hrState, beepPaused) {
        val sm = soundManager ?: return@LaunchedEffect
        if (beepPaused) return@LaunchedEffect
        if (currentHeartRate <= 0 || hrState == null) return@LaunchedEffect
        withTimeoutOrNull(2000) { sm.awaitLoaded() } ?: return@LaunchedEffect
        val state = hrState!!
        val beepType = when (state) {
            FullscreenHrState.HIGH -> SoundManager.SoundType.HIGH_BEEP
            FullscreenHrState.LOW -> SoundManager.SoundType.LOW_BEEP
        }
        while (isActive) {
            val hr = currentHeartRate
            if (hr <= 0) break
            val intervalMs = (60_000f / hr).toLong().coerceIn(200L, 3_000L)
            sm.play(beepType)
            delay(intervalMs)
        }
    }

    // ECG 滚动动画：ecgPhase 在 0..1 之间循环，每个周期 = 一个心动周期（60_000/bpm ms）
    val effectiveBpm = if (isAnimationEnabled && heartRate > 30) heartRate else 0
    val ecgPhase = remember { Animatable(0f) }
    LaunchedEffect(effectiveBpm) {
        if (effectiveBpm > 0) {
            val cycleMs = (60_000f / effectiveBpm).toInt().coerceAtLeast(200)
            ecgPhase.snapTo(0f)
            ecgPhase.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = cycleMs, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    // 爱心光晕：QRS 波峰时最亮；alpha 计算移到 graphicsLayer 绘制阶段，避免每帧重组
    // rPeakPhase = 0.21f 对应 ecgWaveformValue 中 R 波的实际峰值位置
    val rPeakPhase = 0.21f

    // 全屏沉浸模式：隐藏状态栏/导航栏 + 保持屏幕常亮，退出时恢复
    val fullscreenView = LocalView.current
    DisposableEffect(fullscreenView) {
        val window = (fullscreenView.context as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, fullscreenView) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        fullscreenView.keepScreenOn = true

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            fullscreenView.keepScreenOn = false
        }
    }

    BackHandler { onExit() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor.Black)
            .drawBehind {
                // 动画关闭（心率动画开关关闭，或未连接/心率 ≤30）时不画网格与波形线：
                // 纯黑背景 + 静态爱心与心率数字，避免"静止红平线"的观感歧义。
                // 动画开启时绘制行为与之前完全一致。
                if (effectiveBpm > 0) {
                    val canvasW = size.width
                    val canvasH = size.height
                    val baseline = canvasH * 0.82f
                    val amplitude = canvasH * 0.12f
                    val cyclesOnScreen = 4f
                    val currentPhase = ecgPhase.value

                    val gridColor = heartColor.copy(alpha = 0.1f)
                    val gridStep = canvasW / 20f
                    var gx = 0f
                    while (gx <= canvasW) {
                        drawLine(
                            color = gridColor,
                            start = Offset(gx, baseline - amplitude * 1.5f),
                            end = Offset(gx, baseline + amplitude * 1.5f),
                            strokeWidth = 2f
                        )
                        gx += gridStep
                    }
                    var gy = baseline - amplitude * 1.5f
                    while (gy <= baseline + amplitude * 1.5f) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, gy),
                            end = Offset(canvasW, gy),
                            strokeWidth = 2f
                        )
                        gy += amplitude * 0.5f
                    }

                    val path = Path()
                    var first = true
                    var x = 0f
                    while (x <= canvasW) {
                        val phase = (x / canvasW * cyclesOnScreen + currentPhase) % 1f
                        val y = baseline - ecgWaveformValue(phase, amplitude)
                        if (first) {
                            path.moveTo(x, y)
                            first = false
                        } else {
                            path.lineTo(x, y)
                        }
                        x += 2f
                    }
                    drawPath(
                        path = path,
                        color = heartColor,
                        style = Stroke(
                            width = 5f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onExit() }
    ) {
        val horizontalMargin = maxWidth * 0.05f
        val halfWidth = (maxWidth - horizontalMargin * 2) / 2
        val heartSize = minOf(halfWidth, maxHeight) * 0.9f
        val maxFontSizeByWidth = halfWidth / 2.0f
        val maxFontSizeByHeight = maxHeight * 0.85f
        val bpmFontSize = minOf(maxFontSizeByWidth, maxFontSizeByHeight).value.toInt().sp

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalMargin),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = heartColor,
                    modifier = Modifier
                        .size(heartSize)
                        .graphicsLayer {
                            val phaseFraction = ecgPhase.value % 1f
                            val rawDist = abs(phaseFraction - rPeakPhase)
                            val distToPeak = min(rawDist, 1f - rawDist)
                            val heartGlow = if (effectiveBpm > 0) (1f - distToPeak / 0.06f).coerceIn(0f, 1f) else 0f
                            alpha = 0.75f + 0.25f * heartGlow
                        }
                )
            }

            Text(
                text = ":",
                color = heartColor,
                fontSize = bpmFontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    val phaseFraction = ecgPhase.value % 1f
                    val rawDist = abs(phaseFraction - rPeakPhase)
                    val distToPeak = min(rawDist, 1f - rawDist)
                    val heartGlow = if (effectiveBpm > 0) (1f - distToPeak / 0.06f).coerceIn(0f, 1f) else 0f
                    alpha = 0.75f + 0.25f * heartGlow
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (heartRate > 0) "$heartRate" else "--",
                        color = heartColor,
                        fontSize = bpmFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = stringResource(R.string.bpm_unit),
                        color = heartColor.copy(alpha = 0.7f),
                        fontSize = (bpmFontSize.value * 0.3f).toInt().sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.fullscreen_exit_hint),
            color = ComposeColor.White.copy(alpha = 0.35f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        )
    }
}

/**
 * ECG 波形函数：返回给定相位 [phase]（0..1）处的 Y 偏移量（正值向上）。
 * 包含标准 P-QRS-T 波形：
 * - P 波（0.05~0.12）：小凸起
 * - PR 段（0.12~0.17）：基线
 * - Q 波（0.17~0.19）：小下凹
 * - R 波（0.19~0.23）：尖锐主峰
 * - S 波（0.23~0.26）：下凹
 * - ST 段（0.26~0.35）：基线
 * - T 波（0.35~0.52）：中等凸起
 * - 基线（0.52~1.0）：平线
 */
private fun ecgWaveformValue(phase: Float, amplitude: Float): Float {
    val t = phase
    return when {
        // P 波
        t < 0.05f -> 0f
        t < 0.12f -> {
            val pt = (t - 0.05f) / 0.07f
            sin(pt * PI).toFloat() * amplitude * 0.15f
        }
        // PR 段
        t < 0.17f -> 0f
        // Q 波（小下凹）
        t < 0.19f -> {
            -((t - 0.17f) / 0.02f) * amplitude * 0.1f
        }
        // R 波（尖锐主峰）
        t < 0.21f -> {
            val rt = (t - 0.19f) / 0.02f
            rt * amplitude
        }
        t < 0.23f -> {
            val rt = (t - 0.21f) / 0.02f
            (1f - rt) * amplitude
        }
        // S 波（下凹）
        t < 0.26f -> {
            val st = (t - 0.23f) / 0.03f
            -(1f - st) * amplitude * 0.25f
        }
        // ST 段
        t < 0.35f -> 0f
        // T 波
        t < 0.52f -> {
            val tt = (t - 0.35f) / 0.17f
            sin(tt * PI).toFloat() * amplitude * 0.3f
        }
        // 基线
        else -> 0f
    }
}
