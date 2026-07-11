package com.fartopblu.adrenoturbomode.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.fartopblu.adrenoturbomode.core.TurboManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A small draggable pill drawn over other apps. Tapping it toggles turbo without
 * leaving the running game (solves README point 5). Built with a plain View to
 * avoid the lifecycle wiring a ComposeView would need inside a window.
 */
class OverlayController(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null
    private lateinit var params: WindowManager.LayoutParams

    val isShowing: Boolean get() = view != null

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return
        if (!Settings.canDrawOverlays(context)) return

        val pill = TextView(context).apply {
            setPadding(dp(16), dp(10), dp(16), dp(10))
            textSize = 13f
            setTextColor(Color.WHITE)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        attachDrag(pill)
        view = pill
        refresh()
        runCatching { wm.addView(pill, params) }
    }

    fun hide() {
        val v = view ?: return
        runCatching { wm.removeView(v) }
        view = null
    }

    /** Repaints the pill to match the current turbo state. */
    fun refresh() {
        val v = view ?: return
        val on = TurboManager.state.value.desiredOn
        v.text = if (on) "⚡ Turbo ON" else "Turbo OFF"
        v.background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(if (on) 0xF2D4A017.toInt() else 0xF2333333.toInt())
        }
    }

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDrag(v: View) {
        val touchSlop = dp(6)
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX)
                    val dy = (e.rawY - downY)
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        TurboManager.toggle(context)
                        refresh()
                    }
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
