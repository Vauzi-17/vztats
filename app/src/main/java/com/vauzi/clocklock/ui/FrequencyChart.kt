package com.vauzi.clocklock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Minimal GPU-frequency line chart with a soft gradient fill. Pure Compose
 * Canvas, no chart dependency. The dashed line marks the reported max clock, so
 * a flat line riding the dash is visible proof turbo is pinning the clock.
 */
@Composable
fun FrequencyChart(
    history: List<Int>,
    maxMhz: Int?,
    modifier: Modifier = Modifier
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val maxLineColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fillTop = lineColor.copy(alpha = 0.28f)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val dataMax = history.maxOrNull() ?: 0
        val ceiling = (maxMhz ?: dataMax).coerceAtLeast(1).toFloat() * 1.08f

        drawLine(
            color = gridColor.copy(alpha = 0.3f),
            start = Offset(0f, h),
            end = Offset(w, h),
            strokeWidth = 2f
        )

        if (maxMhz != null && maxMhz > 0) {
            val y = h - (maxMhz / ceiling) * h
            drawLine(
                color = maxLineColor.copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
        }

        if (history.size < 2) return@Canvas

        val stepX = w / (history.size - 1).toFloat()
        fun pointY(mhz: Int) = h - (mhz / ceiling) * h

        val line = Path()
        history.forEachIndexed { i, mhz ->
            val x = i * stepX
            val y = pointY(mhz)
            if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
        }

        // Gradient fill under the line.
        val fill = Path().apply {
            addPath(line)
            lineTo((history.size - 1) * stepX, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(listOf(fillTop, Color.Transparent))
        )
        drawPath(path = line, color = lineColor, style = Stroke(width = 5f))
    }
}
