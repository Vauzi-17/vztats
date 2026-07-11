package com.vauzi.clocklock.service

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min

/**
 * Minimal wrapping row layout: places children left-to-right and wraps to a new
 * line when they would exceed [maxWidthPx]. Keeps the horizontal floating panel
 * from overflowing the screen when many metrics are shown.
 */
@SuppressLint("ViewConstructor")
class FlowLayout(context: Context) : ViewGroup(context) {

    var horizontalGap = 0
    var verticalGap = 0
    /** Hard cap on line width; 0 means use the measured constraint. */
    var maxWidthPx = 0

    private fun limit(specWidth: Int): Int {
        val cap = if (maxWidthPx > 0) maxWidthPx else Int.MAX_VALUE
        return min(specWidth.takeIf { it > 0 } ?: cap, cap)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxW = limit(MeasureSpec.getSize(widthMeasureSpec))
        val childWSpec = MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST)
        val childHSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0
        var widest = 0

        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            measureChild(c, childWSpec, childHSpec)
            if (x > paddingLeft && x + c.measuredWidth > maxW - paddingRight) {
                x = paddingLeft
                y += lineHeight + verticalGap
                lineHeight = 0
            }
            x += c.measuredWidth + horizontalGap
            lineHeight = max(lineHeight, c.measuredHeight)
            widest = max(widest, x)
        }

        val width = min(widest - horizontalGap + paddingRight, maxW).coerceAtLeast(0)
        val height = y + lineHeight + paddingBottom
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = width
        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility == View.GONE) continue
            if (x > paddingLeft && x + c.measuredWidth > maxW - paddingRight) {
                x = paddingLeft
                y += lineHeight + verticalGap
                lineHeight = 0
            }
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
            x += c.measuredWidth + horizontalGap
            lineHeight = max(lineHeight, c.measuredHeight)
        }
    }
}
