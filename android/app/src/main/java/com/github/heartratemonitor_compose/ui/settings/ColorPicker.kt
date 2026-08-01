package com.github.heartratemonitor_compose.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.heartratemonitor_compose.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 颜色选择对话框。
 *
 * 基于 Compose 自绘 HSV 色轮 + 亮度滑块，替代原 ColorPickerView 依赖。
 *
 * @param title 对话框标题
 * @param initialColor 初始颜色（ARGB Int）
 * @param onConfirm 确认回调，返回所选颜色 ARGB Int
 * @param onDismiss 取消/关闭回调
 */
@Composable
internal fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) }
    }
    var hsv by remember { mutableStateOf(initialHsv.copyOf()) }
    val currentColor = remember(hsv) { android.graphics.Color.HSVToColor(hsv) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HueSatWheelPicker(
                    hsv = hsv,
                    onHsvChanged = { hsv = it }
                )
                Spacer(Modifier.height(12.dp))

                // 亮度滑块（V 通道，0-100）
                val brightnessPercent = (hsv[2] * 100f).toInt()
                Text(
                    text = stringResource(R.string.brightness, brightnessPercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = hsv[2],
                    onValueChange = { v -> hsv = floatArrayOf(hsv[0], hsv[1], v) },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                ColorCompareRow(initialColor = initialColor, currentColor = currentColor)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ColorCompareRow(initialColor: Int, currentColor: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ColorPreviewColumn(label = stringResource(R.string.initial_color), color = initialColor)
        ColorPreviewColumn(label = stringResource(R.string.current_color), color = currentColor)
    }
}

@Composable
private fun ColorPreviewColumn(label: String, color: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(color), RoundedCornerShape(8.dp))
        )
    }
}

/**
 * HSV 色轮选择器。Canvas 自绘色相/饱和度圆盘 + 选择圆圈。
 *
 * 圆盘角度 → H（0-360），半径 → S（0-1，中心为 0，边缘为 1）。
 */
@Composable
private fun HueSatWheelPicker(
    hsv: FloatArray,
    onHsvChanged: (FloatArray) -> Unit,
    modifier: Modifier = Modifier
) {
    val wheelSize = 240.dp
    val density = LocalDensity.current
    val wheelSizePx = with(density) { wheelSize.toPx() }
    val radiusPx = wheelSizePx / 2f

    val indicatorOffset = remember(hsv) {
        val angleRad = Math.toRadians(hsv[0].toDouble())
        val r = hsv[1] * radiusPx
        Offset(
            (radiusPx + r * cos(angleRad)).toFloat(),
            (radiusPx + r * sin(angleRad)).toFloat()
        )
    }

    Canvas(
        modifier = modifier
            .size(wheelSize)
            .pointerInput(Unit) {
                // 用 awaitEachGesture 替代 detectDragGestures：
                // detectDragGestures 的 onDragStart 只在手指移动超过 touch slop 后才触发，
                // 纯点击（tap）不会更新 hsv——用户"点色轮选位置"完全无反应。
                // awaitEachGesture 在 down 时立即调用 updateHsvFromTouch，tap 和 drag 都能选色。
                awaitEachGesture {
                    val down = awaitFirstDown()
                    updateHsvFromTouch(down.position, radiusPx, hsv, onHsvChanged)
                    drag(down.id) { change ->
                        updateHsvFromTouch(change.position, radiusPx, hsv, onHsvChanged)
                        change.consume()
                    }
                }
            }
    ) {
        val center = Offset(radiusPx, radiusPx)
        // 色相圆盘：sweepGradient 一次性绘制 360° 色相。
        // 原实现用 360 个 drawArc(useCenter=false) 拼接，useCenter=false 画的是弓形（弧+弦）
        // 而非扇形，1 度弓形填充区域趋近于零 → 拼起来圆盘内部空白，只有边缘一圈有色。
        // sweepGradient 沿角度方向渐变，与 HSV 色相环一致（0°红→60°黄→...→360°红）。
        // 起点在 3 点钟方向（0°），与下方 atan2 的角度起点对齐。
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Red,        // 0°
                    Color.Yellow,     // 60°
                    Color.Green,      // 120°
                    Color.Cyan,       // 180°
                    Color.Blue,       // 240°
                    Color.Magenta,    // 300°
                    Color.Red         // 360° 回到红，闭合
                ),
                center = center
            )
        )
        // S 模拟：中心白（S=0）→ 边缘原色（S=1），径向渐变叠加
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    Color.White.copy(alpha = 0f)
                ),
                center = center,
                radius = radiusPx
            )
        )
        drawCircle(
            color = Color.White,
            radius = 10f,
            center = indicatorOffset,
            style = Stroke(width = 3f)
        )
        drawCircle(
            color = Color.Black,
            radius = 10f,
            center = indicatorOffset,
            style = Stroke(width = 1f)
        )
    }
}

/**
 * 将触摸坐标转换为 HSV 并更新。
 * - 距圆心距离 → S（0-1，超过半径截断为 1）
 * - 角度 → H（0-360）
 *
 * V 通道处理：
 * 默认颜色是 BLACK（HSV 中 V=0），若直接保留 V=0，无论 H/S 怎么变，
 * HSVToColor 出来都是黑色——用户在色轮上点来点去永远是黑。
 * 色轮本身画的是 V=1 的色相，故此处当 V=0 时自动提升到 1f：
 * 用户首次点色轮立即看到真实颜色，之后 V 由亮度滑块控制（不会被重置）。
 */
private fun updateHsvFromTouch(
    offset: Offset,
    radiusPx: Float,
    currentHsv: FloatArray,
    onHsvChanged: (FloatArray) -> Unit
) {
    val cx = offset.x - radiusPx
    val cy = offset.y - radiusPx
    val r = sqrt(cx * cx + cy * cy).coerceAtMost(radiusPx)
    val s = (r / radiusPx).coerceIn(0f, 1f)
    var h = Math.toDegrees(atan2(cy.toDouble(), cx.toDouble())).toFloat()
    if (h < 0f) h += 360f
    // V=0（默认 BLACK）时提升到 1f，让色轮交互立即产生可见颜色
    val v = if (currentHsv[2] <= 0f) 1f else currentHsv[2]
    onHsvChanged(floatArrayOf(h, s, v))
}
