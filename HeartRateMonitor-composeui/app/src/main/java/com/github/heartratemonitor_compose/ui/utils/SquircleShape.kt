package com.github.heartratemonitor_compose.ui.utils

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density


class SquircleShape(
    private val cornerSmoothing: Float = 0.67f, // 0f = 普通圆弧, 1f = 完全 squircle
    private val topStartRadiusPx: Int,
    private val topEndRadiusPx: Int,
    private val bottomEndRadiusPx: Int,
    private val bottomStartRadiusPx: Int,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = buildSquirclePath(size, 0f, layoutDirection)
        return Outline.Generic(path)
    }

    @PublishedApi
    internal fun buildSquirclePath(
        size: Size,
        padding: Float,
        layoutDirection: LayoutDirection,
    ): Path {
        val inset = padding
        val w = size.width - inset * 2
        val h = size.height - inset * 2

        val rtl = layoutDirection == LayoutDirection.Rtl

        fun clamp(px: Int, width: Float, height: Float): Int {
            val limit = (minOf(width, height) / 2).toInt()
            return px.coerceIn(0, limit)
        }

        val tlR = clamp(if (rtl) topEndRadiusPx else topStartRadiusPx, w, h)
        val trR = clamp(if (rtl) topStartRadiusPx else topEndRadiusPx, w, h)
        val brR = clamp(if (rtl) bottomStartRadiusPx else bottomEndRadiusPx, w, h)
        val blR = clamp(if (rtl) bottomEndRadiusPx else bottomStartRadiusPx, w, h)

        return Path().apply {
            val sx = inset
            val sy = inset

            moveTo(sx + tlR.toFloat(), sy)
            lineTo(sx + w - trR.toFloat(), sy)
            cubicCorner(sx + w, sy, trR.toFloat())
            lineTo(sx + w, sy + h - brR.toFloat())
            cubicCorner(sx + w, sy + h, brR.toFloat())
            lineTo(sx + blR.toFloat(), sy + h)
            cubicCorner(sx, sy + h, blR.toFloat())
            lineTo(sx, sy + tlR.toFloat())
            cubicCorner(sx, sy, tlR.toFloat())
            close()
        }
    }

    
    private fun Path.cubicCorner(cornerX: Float, cornerY: Float, cornerRadius: Float) {
        val t = cornerSmoothing.coerceIn(0f, 1f)
        val smoothingFactor = 0.55f + t * 0.45f // 0.55 (arc) .. 1.0 (full squircle)

        val dx = cornerRadius * smoothingFactor
        val dy = cornerRadius * smoothingFactor

        val c1x = cornerX - cornerRadius + dx
        val c1y = cornerY
        val c2x = cornerX
        val c2y = cornerY - cornerRadius + dy

        cubicTo(c1x, c1y, c2x, c2y, cornerX, cornerY)
    }
}

fun SquircleShape(
    radiusPx: Int,
    cornerSmoothing: Float = 0.67f,
): SquircleShape = SquircleShape(
    cornerSmoothing = cornerSmoothing,
    topStartRadiusPx = radiusPx,
    topEndRadiusPx = radiusPx,
    bottomEndRadiusPx = radiusPx,
    bottomStartRadiusPx = radiusPx,
)
