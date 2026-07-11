package com.fartopblu.adrenoturbomode.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Minimal line chart of recent GPU frequency samples (MHz). Pure Compose Canvas,
 * no external chart dependency. A dashed line marks the reported max clock, so a
 * flat line riding the dash is visible proof that turbo is pinning the clock.
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

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val dataMax = history.maxOrNull() ?: 0
        val ceiling = (maxMhz ?: dataMax).coerceAtLeast(1).toFloat() * 1.08f

        // Baseline
        drawLine(
            color = gridColor.copy(alpha = 0.35f),
            start = Offset(0f, h),
            end = Offset(w, h),
            strokeWidth = 2f
        )

        // Max-clock reference line
        if (maxMhz != null && maxMhz > 0) {
            val y = h - (maxMhz / ceiling) * h
            drawLine(
                color = maxLineColor.copy(alpha = 0.6f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
        }

        if (history.size < 2) return@Canvas

        val stepX = w / (history.size - 1).toFloat()
        val path = Path()
        history.forEachIndexed { i, mhz ->
            val x = i * stepX
            val y = h - (mhz / ceiling) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 5f)
        )
    }
}
