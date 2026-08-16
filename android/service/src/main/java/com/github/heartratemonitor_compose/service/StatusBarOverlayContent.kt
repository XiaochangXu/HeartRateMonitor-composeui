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
import com.github.heartratemonitor_compose.service.R
import kotlin.math.roundToInt


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

            // 与原 ImageView.scaleX/Y 一致：以图标中心为锚点缩放
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

            x += appearance.numberMargin
            val numberBaseline = centerY - (numberPaint.ascent() + numberPaint.descent()) / 2f
            native.drawText(heartRate, x, numberBaseline, numberPaint)
            x += numberWidth

            if (appearance.isBpmTextEnabled) {
                x += appearance.unitMargin
                val unitBaseline = centerY - (unitPaint.ascent() + unitPaint.descent()) / 2f
                native.drawText("bpm", x, unitBaseline, unitPaint)
            }
        }
    }
}

