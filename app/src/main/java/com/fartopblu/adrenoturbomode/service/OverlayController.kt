package com.fartopblu.adrenoturbomode.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.fartopblu.adrenoturbomode.core.CpuSample
import com.fartopblu.adrenoturbomode.core.GpuSample
import com.fartopblu.adrenoturbomode.core.Prefs
import com.fartopblu.adrenoturbomode.core.TurboManager
import kotlin.math.roundToInt

/**
 * Modern, customisable floating panel. A compact pill that expands into a stats
 * card (user picks which metrics show) with a turbo toggle. Built from plain
 * Views so it can live in a window without Compose lifecycle plumbing.
 */
class OverlayController(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs.get(context)

    private var root: DraggableOverlayLayout? = null
    private lateinit var params: WindowManager.LayoutParams

    private var collapsed: LinearLayout? = null
    private var expanded: LinearLayout? = null
    private var expandedState = false

    private var dot: View? = null
    private var pillValue: TextView? = null
    private var statusText: TextView? = null
    private var toggleBtn: TextView? = null
    private val valueViews = LinkedHashMap<String, TextView>()

    private var lastGpu: GpuSample? = null
    private var lastCpu: CpuSample? = null

    private var dragStartX = 0
    private var dragStartY = 0

    val isShowing: Boolean get() = root != null

    // --- lifecycle ------------------------------------------------------------

    fun show() {
        if (root != null) return
        if (!Settings.canDrawOverlays(context)) return

        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(120)
        }

        val container = DraggableOverlayLayout(context).apply {
            onDragStart = { dragStartX = params.x; dragStartY = params.y }
            onDrag = { dx, dy ->
                params.x = dragStartX + dx.roundToInt()
                params.y = dragStartY + dy.roundToInt()
                runCatching { wm.updateViewLayout(this, params) }
            }
        }

        collapsed = buildCollapsed()
        expanded = buildExpanded()
        container.addView(collapsed)
        container.addView(expanded)
        root = container

        applyExpandedState()
        refresh()
        runCatching { wm.addView(container, params) }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        collapsed = null
        expanded = null
        valueViews.clear()
    }

    /** Rebuild the metric rows after the user changes which metrics to show. */
    fun rebuild() {
        val container = root ?: return
        container.removeAllViews()
        collapsed = buildCollapsed()
        expanded = buildExpanded()
        container.addView(collapsed)
        container.addView(expanded)
        applyExpandedState()
        refresh()
    }

    // --- data -----------------------------------------------------------------

    fun updateStats(gpu: GpuSample?, cpu: CpuSample?) {
        lastGpu = gpu
        lastCpu = cpu
        for ((key, tv) in valueViews) tv.text = metricValue(key)
        pillValue?.text = collapsedText()
    }

    /** Repaint state-dependent visuals (turbo on/off). */
    fun refresh() {
        val on = TurboManager.state.value.desiredOn
        dot?.background = circle(if (on) ACCENT else OFF_DOT)
        statusText?.text = if (on) "ON" else "OFF"
        statusText?.setTextColor(if (on) ACCENT else MUTED)
        toggleBtn?.let {
            it.text = if (on) "TURBO ON" else "TURN ON"
            it.background = roundedFill(if (on) ACCENT else BTN_OFF_BG, dp(12))
            it.setTextColor(if (on) BTN_ON_TEXT else TEXT)
        }
        pillValue?.text = collapsedText()
    }

    // --- view building --------------------------------------------------------

    private fun buildCollapsed(): LinearLayout {
        val dotView = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(11), dp(11))
            background = circle(OFF_DOT)
        }
        val value = TextView(context).apply {
            setTextColor(TEXT)
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(dp(8), 0, 0, 0)
        }
        dot = dotView
        pillValue = value

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedFill(SURFACE, dp(22), STROKE)
            setPadding(dp(14), dp(9), dp(16), dp(9))
            addView(dotView)
            addView(value)
            setOnClickListener { expandedState = true; applyExpandedState() }
        }
    }

    private fun buildExpanded(): LinearLayout {
        valueViews.clear()

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = "GPU TURBO"
            setTextColor(MUTED)
            textSize = 11f
            letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val status = TextView(context).apply {
            textSize = 12f
            setTextColor(MUTED)
            setPadding(0, 0, dp(10), 0)
        }
        val collapse = TextView(context).apply {
            text = "×"
            setTextColor(MUTED)
            textSize = 16f
            setOnClickListener { expandedState = false; applyExpandedState() }
        }
        statusText = status
        header.addView(title)
        header.addView(status)
        header.addView(collapse)

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill(SURFACE, dp(20), STROKE)
            setPadding(dp(16), dp(12), dp(16), dp(14))
            minimumWidth = dp(190)
            addView(header)
        }

        for (key in orderedMetrics()) {
            panel.addView(metricRow(key))
        }

        val button = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(11), 0, dp(11))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) }
            layoutParams = lp
            setOnClickListener {
                TurboManager.toggle(context)
                refresh()
            }
        }
        toggleBtn = button
        panel.addView(button)

        return panel
    }

    private fun metricRow(key: String): LinearLayout {
        val label = TextView(context).apply {
            text = metricLabel(key)
            setTextColor(MUTED)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val value = TextView(context).apply {
            setTextColor(TEXT)
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            text = metricValue(key)
        }
        valueViews[key] = value
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(5), 0, dp(5))
            addView(label)
            addView(value)
        }
    }

    private fun applyExpandedState() {
        collapsed?.visibility = if (expandedState) View.GONE else View.VISIBLE
        expanded?.visibility = if (expandedState) View.VISIBLE else View.GONE
    }

    // --- metric helpers -------------------------------------------------------

    private fun orderedMetrics(): List<String> {
        val enabled = prefs.floatingMetrics
        return listOf(
            Prefs.METRIC_GPU_FREQ, Prefs.METRIC_GPU_TEMP,
            Prefs.METRIC_CPU_FREQ, Prefs.METRIC_CPU_TEMP
        ).filter { it in enabled }
    }

    private fun metricLabel(key: String): String = when (key) {
        Prefs.METRIC_GPU_FREQ -> "GPU"
        Prefs.METRIC_GPU_TEMP -> "GPU temp"
        Prefs.METRIC_CPU_FREQ -> "CPU"
        Prefs.METRIC_CPU_TEMP -> "CPU temp"
        else -> key
    }

    private fun metricValue(key: String): String = when (key) {
        Prefs.METRIC_GPU_FREQ -> lastGpu?.freqMhz?.let { "$it MHz" } ?: "—"
        Prefs.METRIC_GPU_TEMP -> lastGpu?.tempC?.let { "${it.roundToInt()}°C" } ?: "—"
        Prefs.METRIC_CPU_FREQ -> lastCpu?.freqGhzText ?: "—"
        Prefs.METRIC_CPU_TEMP -> lastCpu?.tempC?.let { "${it.roundToInt()}°C" } ?: "—"
        else -> "—"
    }

    /** Compact pill text: first enabled metric's value, else the turbo state. */
    private fun collapsedText(): String {
        val first = orderedMetrics().firstOrNull() ?: return if (TurboManager.state.value.desiredOn) "TURBO ON" else "TURBO"
        return metricValue(first)
    }

    // --- drawing helpers ------------------------------------------------------

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics
    ).roundToInt()

    private fun circle(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    private fun roundedFill(color: Int, radius: Int, strokeColor: Int? = null) =
        GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(color)
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    companion object {
        private val SURFACE = Color.parseColor("#F01B1C1F")
        private val STROKE = Color.parseColor("#26FFFFFF")
        private val TEXT = Color.parseColor("#ECECEC")
        private val MUTED = Color.parseColor("#9AA0A6")
        private val ACCENT = Color.parseColor("#F2C14E")
        private val OFF_DOT = Color.parseColor("#6B7075")
        private val BTN_OFF_BG = Color.parseColor("#1FFFFFFF")
        private val BTN_ON_TEXT = Color.parseColor("#3A2D00")

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
