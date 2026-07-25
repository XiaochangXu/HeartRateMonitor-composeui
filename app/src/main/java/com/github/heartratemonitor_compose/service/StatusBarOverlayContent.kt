package com.github.heartratemonitor_compose.service

import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import kotlin.math.roundToInt

/**
 * 状态栏覆盖层外观参数（全部以 px 为单位，已应用 scaleFactor）。
 *
 * 文字颜色为纯黑（0xFF000000）或纯白（0xFFFFFFFF），无 alpha，无阴影。
 * 由 [StatusBarResidentService] 的 applyAppearance / applySize / applyTextStyle 从
 * SharedPreferences 读取并计算后赋值，变更触发 [StatusBarOverlayContent] 重绘。
 *
 * 复刻原 layout_status_bar_overlay.xml + StatusBarResidentService 的 View 属性操作：
 * - textColor：applyAppearance 计算（autoColor / whiteText / 默认黑）
 * - textSize / unitTextSize / iconSize / padding / numberMargin / unitMargin：applySize 缩放
 * - thickness / isBpmTextEnabled：applyTextStyle
 */
data class StatusBarOverlayAppearance(
    val textColor: Int = android.graphics.Color.BLACK,
    val textSize: Float = 12f,
    val unitTextSize: Float = 9f,
    val iconSize: Float = 14f,
    val padding: Float = 6f,
    val numberMargin: Float = 3f,
    val unitMargin: Float = 1f,
    val thickness: Int = 0,
    val isBpmTextEnabled: Boolean = true
)

/**
 * 状态栏覆盖层 Composable。复刻原 [layout_status_bar_overlay.xml]：
 * LinearLayout → Canvas；ImageView(heart) + TextView(bpm_number) + TextView(bpm_unit)
 * 全部在 Canvas 内用 [android.graphics.Canvas] 原生绘制。
 *
 * **硬约束**：状态栏文字必须 [Paint.Style.FILL_AND_STROKE]（纯黑/纯白无阴影）。
 * Compose Text 不支持 stroke，故用 `nativeCanvas.drawText` + 自建 [Paint]，
 * thickness > 0 时叠加 strokeWidth（= textSize * thickness / 100 * 0.25），与原 TextView paint 逻辑一致。
 *
 * 心跳动画在内部用 [Animatable] + [LaunchedEffect] 驱动（替代原 ValueAnimator），
 * 仅作用于心形图标（scale 1f↔1.2f），不影响文字。
 */
@Composable
fun StatusBarOverlayContent(
    heartRate: String,
    bpm: Int,
    isAnimationEnabled: Boolean,
    isConnected: Boolean,
    appearance: StatusBarOverlayAppearance,
    statusBarHeightPx: Int
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // 心率数字 Paint：bold + 可选 FILL_AND_STROKE
    val numberPaint = remember(appearance.textColor, appearance.textSize, appearance.thickness) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = appearance.textColor
            textSize = appearance.textSize
            typeface = Typeface.DEFAULT_BOLD
            if (appearance.thickness > 0) {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = appearance.textSize * appearance.thickness / 100f * 0.25f
            } else {
                style = Paint.Style.FILL
                strokeWidth = 0f
            }
        }
    }
    // "bpm" 单位 Paint：非 bold + 可选 FILL_AND_STROKE
    val unitPaint = remember(appearance.textColor, appearance.unitTextSize, appearance.thickness) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = appearance.textColor
            textSize = appearance.unitTextSize
            typeface = Typeface.DEFAULT
            if (appearance.thickness > 0) {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = appearance.unitTextSize * appearance.thickness / 100f * 0.25f
            } else {
                style = Paint.Style.FILL
                strokeWidth = 0f
            }
        }
    }

    val heartDrawable = remember { context.getDrawable(R.drawable.ic_heart)?.mutate() }

    // 心跳动画：1f↔1.2f，单周期 = 60000 / bpm ms（与 FloatingWindowContent 一致）
    val heartScale = remember { Animatable(1f) }
    LaunchedEffect(bpm, isAnimationEnabled, isConnected) {
        if (isAnimationEnabled && bpm > 30 && isConnected) {
            val durationMs = (60000f / bpm).roundToInt()
            val halfDuration = (durationMs / 2).coerceAtLeast(1)
            while (true) {
                heartScale.animateTo(1.2f, tween(halfDuration, easing = FastOutSlowInEasing))
                heartScale.animateTo(1f, tween(halfDuration, easing = FastOutSlowInEasing))
            }
        } else {
            heartScale.animateTo(1f, tween(200))
        }
    }

    // 测量文本宽度以计算 Canvas 总宽（WRAP_CONTENT 等价）
    val numberWidth = numberPaint.measureText(heartRate)
    val unitWidth = if (appearance.isBpmTextEnabled) unitPaint.measureText("bpm") else 0f
    val totalWidthPx = appearance.padding +
        appearance.iconSize +
        appearance.numberMargin +
        numberWidth +
        (if (appearance.isBpmTextEnabled) appearance.unitMargin + unitWidth else 0f) +
        appearance.padding
    val totalWidthDp = with(density) { totalWidthPx.toDp() }
    val heightDp = with(density) { statusBarHeightPx.toDp() }

    Canvas(modifier = Modifier.size(width = totalWidthDp, height = heightDp)) {
        drawIntoCanvas { composeCanvas ->
            val native = composeCanvas.nativeCanvas
            val centerY = size.height / 2f
            var x = appearance.padding

            // 心形图标：以中心为锚点应用 heartScale，与原 ImageView.scaleX/Y 一致
            val icon = heartDrawable
            if (icon != null) {
                icon.setTint(appearance.textColor)
                icon.setTintMode(PorterDuff.Mode.SRC_IN)
                val iconLeft = x
                val iconTop = centerY - appearance.iconSize / 2f
                icon.setBounds(
                    iconLeft.toInt(), iconTop.toInt(),
                    (iconLeft + appearance.iconSize).toInt(),
                    (iconTop + appearance.iconSize).toInt()
                )
                native.save()
                native.scale(heartScale.value, heartScale.value, iconLeft + appearance.iconSize / 2f, centerY)
                icon.draw(native)
                native.restore()
            }
            x += appearance.iconSize

            // 心率数字：垂直居中（baseline = centerY - (ascent+descent)/2）
            x += appearance.numberMargin
            val numberBaseline = centerY - (numberPaint.ascent() + numberPaint.descent()) / 2f
            native.drawText(heartRate, x, numberBaseline, numberPaint)
            x += numberWidth

            // "bpm" 单位：显隐受 isBpmTextEnabled 控制
            if (appearance.isBpmTextEnabled) {
                x += appearance.unitMargin
                val unitBaseline = centerY - (unitPaint.ascent() + unitPaint.descent()) / 2f
                native.drawText("bpm", x, unitBaseline, unitPaint)
            }
        }
    }
}

