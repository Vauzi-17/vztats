package com.vauzi.clocklock.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.vauzi.clocklock.core.CpuSample
import com.vauzi.clocklock.core.GpuSample
import com.vauzi.clocklock.core.PowerSample
import com.vauzi.clocklock.core.Prefs
import com.vauzi.clocklock.core.TurboManager
import kotlin.math.roundToInt
import java.util.Locale

/**
 * Customisable floating panel. Three layouts (compact pill, horizontal bar,
 * vertical panel), adjustable opacity and size. In the always-on layouts the
 * stats are always visible; a single tap reveals the turbo toggle, and an "×"
 * hides it again. Built from plain Views to avoid Compose-in-window plumbing.
 */
class OverlayController(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = Prefs.get(context)

    private var root: DraggableOverlayLayout? = null
    private lateinit var params: WindowManager.LayoutParams

    private var mode = Prefs.MODE_HORIZONTAL
    private var scale = 1f
    private var expandedState = false   // compact only
    private var showButton = false      // horizontal / vertical

    private val valueViews = LinkedHashMap<String, TextView>()
    private var dot: View? = null
    private var statusText: TextView? = null
    private var toggleBtn: TextView? = null
    private var pillValue: TextView? = null
    private var ramChipView: TextView? = null

    /** Invoked when the user taps the RAM chip in the control row. */
    var onRamBoost: (() -> Unit)? = null

    private var lastGpu: GpuSample? = null
    private var lastCpu: CpuSample? = null
    private var lastPower: PowerSample? = null
    private var lastFps: Int? = null

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
            x = dpRaw(16)
            y = dpRaw(120)
        }

        root = DraggableOverlayLayout(context).apply {
            onDragStart = { dragStartX = params.x; dragStartY = params.y }
            onDrag = { dx, dy ->
                params.x = dragStartX + dx.roundToInt()
                params.y = dragStartY + dy.roundToInt()
                runCatching { wm.updateViewLayout(this, params) }
            }
        }
        rebuild()
        runCatching { wm.addView(root, params) }
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        clearRefs()
    }

    /** Rebuilds the whole panel from the current prefs (mode/metrics/size/opacity). */
    fun rebuild() {
        val container = root ?: return
        mode = prefs.floatingMode
        scale = prefs.floatingSize.coerceIn(60, 200) / 100f

        clearRefs()
        container.removeAllViews()
        container.addView(
            when (mode) {
                Prefs.MODE_COMPACT -> buildCompact()
                Prefs.MODE_VERTICAL -> buildVertical()
                else -> buildHorizontal()
            }
        )
        container.alpha = (prefs.floatingOpacity.coerceIn(20, 100)) / 100f
        applyValues()
        refresh()
    }

    private fun clearRefs() {
        valueViews.clear()
        dot = null; statusText = null; toggleBtn = null; pillValue = null; ramChipView = null
    }

    // --- data -----------------------------------------------------------------

    fun updateStats(gpu: GpuSample?, cpu: CpuSample?, power: PowerSample? = null, fps: Int? = null) {
        lastGpu = gpu
        lastCpu = cpu
        lastPower = power
        lastFps = fps
        applyValues()
    }

    private fun applyValues() {
        for ((key, tv) in valueViews) {
            tv.text = if (mode == Prefs.MODE_HORIZONTAL) metricValueShort(key) else metricValue(key)
        }
        pillValue?.text = collapsedText()
    }

    fun refresh() {
        val on = TurboManager.state.value.desiredOn
        dot?.background = circle(if (on) ACCENT else OFF_DOT)
        statusText?.text = if (on) "ON" else "OFF"
        statusText?.setTextColor(if (on) ACCENT else MUTED)
        toggleBtn?.let {
            it.text = if (on) "TURBO ON" else "TURN ON"
            it.background = roundedFill(if (on) ACCENT else BTN_OFF_BG, dp(11f))
            it.setTextColor(if (on) BTN_ON_TEXT else TEXT)
        }
        pillValue?.text = collapsedText()
    }

    // --- COMPACT --------------------------------------------------------------

    private fun buildCompact(): LinearLayout =
        if (!expandedState) buildPill() else buildVerticalPanel(compact = true)

    private fun buildPill(): LinearLayout {
        val d = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(11f), dp(11f))
            background = circle(OFF_DOT)
        }
        val value = TextView(context).apply {
            setTextColor(TEXT)
            textSize = 13f * scale
            typeface = Typeface.MONOSPACE
            setPadding(dp(8f), 0, 0, 0)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        compactMetricKey()?.let { key ->
            value.minWidth = value.paint.measureText(metricSampleLong(key)).toInt() + dp(2f)
        }
        dot = d; pillValue = value
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedFill(SURFACE, dp(22f), STROKE)
            setPadding(dp(14f), dp(9f), dp(16f), dp(9f))
            addView(d); addView(value)
            setOnClickListener { expandedState = true; rebuild() }
        }
    }

    // --- VERTICAL -------------------------------------------------------------

    private fun buildVertical(): LinearLayout = buildVerticalPanel(compact = false)

    private fun buildVerticalPanel(compact: Boolean): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill(SURFACE, dp(18f), STROKE)
            setPadding(dp(15f), dp(11f), dp(15f), dp(13f))
            minimumWidth = dp(178f)
        }
        val showClose = compact || showButton
        panel.addView(header(withClose = showClose) {
            if (compact) expandedState = false else showButton = false
            rebuild()
        })
        for (key in orderedMetrics()) panel.addView(verticalRow(key))

        if (compact || showButton) panel.addView(toggleButton(topMargin = dp(10f)))
        if (!compact) {
            panel.setOnClickListener { if (!showButton) { showButton = true; rebuild() } }
        }
        return panel
    }

    private fun verticalRow(key: String): LinearLayout {
        val label = TextView(context).apply {
            text = metricLabel(key)
            setTextColor(MUTED)
            textSize = 12f * scale
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val value = TextView(context).apply {
            setTextColor(TEXT)
            textSize = 13f * scale
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
        }
        value.minWidth = value.paint.measureText(metricSampleLong(key)).toInt() + dp(1f)
        valueViews[key] = value
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4f), 0, dp(4f))
            addView(label); addView(value)
        }
    }

    // --- HORIZONTAL -----------------------------------------------------------

    private fun buildHorizontal(): LinearLayout {
        val d = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(10f), dp(10f)).apply {
                rightMargin = dp(12f)
            }
            background = circle(OFF_DOT)
        }
        dot = d

        // Single row of stats.
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(d)
        }
        orderedMetrics().forEach { key -> row.addView(horizontalCell(key)) }

        // Measure the natural width; if it overflows, shrink everything uniformly
        // so all metrics stay on ONE line instead of wrapping.
        val maxW = context.resources.displayMetrics.widthPixels - dp(50f)
        row.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        if (maxW in 1 until row.measuredWidth) {
            scaleViews(row, maxW.toFloat() / row.measuredWidth)
        }

        // Outer column so the toggle + close controls sit on their own line.
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill(SURFACE, dp(16f), STROKE)
            setPadding(dp(13f), dp(8f), dp(13f), dp(8f))
            addView(row)
            if (showButton) addView(controlRow())
            setOnClickListener { if (!showButton) { showButton = true; rebuild() } }
        }
    }

    /** Uniformly scales text sizes, paddings and margins of a view subtree. */
    private fun scaleViews(v: View, f: Float) {
        if (v is TextView) {
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, v.textSize * f)
            if (v.minWidth > 0) v.minWidth = (v.minWidth * f).toInt()
        }
        v.setPadding(
            (v.paddingLeft * f).toInt(), (v.paddingTop * f).toInt(),
            (v.paddingRight * f).toInt(), (v.paddingBottom * f).toInt()
        )
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.leftMargin = (lp.leftMargin * f).toInt()
            lp.rightMargin = (lp.rightMargin * f).toInt()
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) scaleViews(v.getChildAt(i), f)
        }
    }

    private fun controlRow(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(8f), 0, dp(1f))
        addView(toggleButton(topMargin = 0, compactChip = true))
        addView(ramChip())
        addView(closeChip())
    }

    /** Frees background RAM. The foreground game is never killed by this. */
    private fun ramChip(): TextView {
        val chip = TextView(context).apply {
            text = "RAM"
            gravity = Gravity.CENTER
            textSize = 11f * scale
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(TEXT)
            background = roundedFill(BTN_OFF_BG, dp(11f))
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8f) }
            setOnClickListener {
                text = "…"
                onRamBoost?.invoke()
            }
        }
        ramChipView = chip
        return chip
    }

    /** Briefly shows the RAM-boost outcome on the chip, then restores the label. */
    fun flashRamResult(msg: String) {
        val chip = ramChipView ?: return
        chip.text = msg
        chip.postDelayed({ ramChipView?.text = "RAM" }, 2500L)
    }

    private fun horizontalCell(key: String): LinearLayout {
        val value = TextView(context).apply {
            setTextColor(TEXT)
            textSize = 14f * scale
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val label = TextView(context).apply {
            text = metricLabel(key).uppercase()
            setTextColor(MUTED)
            textSize = 8.5f * scale
            letterSpacing = 0.06f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        // Reserve the worst-case width up front (before real values are set by
        // applyValues()) so both the layout never jitters as digits change AND
        // the single-line overflow measurement below is accurate rather than
        // based on still-empty TextViews.
        val cellWidth = maxOf(
            value.paint.measureText(metricSampleShort(key)),
            label.paint.measureText(label.text.toString())
        ).toInt() + dp(1f)
        value.minWidth = cellWidth
        label.minWidth = cellWidth
        valueViews[key] = value
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = dp(14f) }
            addView(value); addView(label)
        }
    }

    // --- shared bits ----------------------------------------------------------

    private fun header(withClose: Boolean, onClose: () -> Unit): LinearLayout {
        val title = TextView(context).apply {
            text = "VZTATS"
            setTextColor(MUTED)
            textSize = 10.5f * scale
            letterSpacing = 0.12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val status = TextView(context).apply {
            textSize = 11f * scale
            setTextColor(MUTED)
            setPadding(0, 0, if (withClose) dp(8f) else 0, 0)
        }
        statusText = status
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(3f))
            addView(title); addView(status)
            if (withClose) addView(TextView(context).apply {
                text = "×"
                setTextColor(MUTED)
                textSize = 16f * scale
                setOnClickListener { onClose() }
            })
        }
    }

    private fun toggleButton(topMargin: Int, compactChip: Boolean = false): TextView {
        val btn = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = (if (compactChip) 11f else 13f) * scale
            setTypeface(Typeface.DEFAULT_BOLD)
            if (compactChip) {
                setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            } else {
                setPadding(0, dp(10f), 0, dp(10f))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { this.topMargin = topMargin }
            }
            setOnClickListener { TurboManager.toggle(context); refresh() }
        }
        toggleBtn = btn
        return btn
    }

    private fun closeChip(): TextView = TextView(context).apply {
        text = "×"
        setTextColor(MUTED)
        textSize = 16f * scale
        setPadding(dp(10f), 0, dp(2f), 0)
        setOnClickListener { showButton = false; rebuild() }
    }

    // --- metric helpers -------------------------------------------------------

    private fun orderedMetrics(): List<String> {
        val enabled = prefs.floatingMetrics
        return listOf(
            Prefs.METRIC_FPS,
            Prefs.METRIC_GPU_FREQ, Prefs.METRIC_GPU_TEMP,
            Prefs.METRIC_CPU_FREQ, Prefs.METRIC_CPU_TEMP,
            Prefs.METRIC_BATT_POWER, Prefs.METRIC_BATT_TEMP, Prefs.METRIC_BATT_PCT,
            Prefs.METRIC_RAM
        ).filter { it in enabled }
    }

    /** Which metric the compact pill shows — the user's pick if still enabled, else the first. */
    private fun compactMetricKey(): String? {
        val enabled = orderedMetrics()
        if (enabled.isEmpty()) return null
        val picked = prefs.floatingCompactMetric
        return picked.takeIf { it in enabled } ?: enabled.first()
    }

    private fun metricLabel(key: String): String = when (key) {
        Prefs.METRIC_FPS -> "FPS"
        Prefs.METRIC_GPU_FREQ -> "GPU"
        Prefs.METRIC_GPU_TEMP -> "GPU °C"
        Prefs.METRIC_CPU_FREQ -> "CPU"
        Prefs.METRIC_CPU_TEMP -> "CPU °C"
        Prefs.METRIC_BATT_POWER -> "mA"
        Prefs.METRIC_BATT_TEMP -> "BAT °C"
        Prefs.METRIC_BATT_PCT -> "BAT %"
        Prefs.METRIC_RAM -> "RAM"
        else -> key
    }

    /** Full text with unit — used where there's room (vertical rows, compact pill). */
    private fun metricValue(key: String): String = when (key) {
        Prefs.METRIC_FPS -> lastFps?.toString() ?: "—"
        Prefs.METRIC_GPU_FREQ -> lastGpu?.freqMhz?.let { "$it MHz" } ?: "—"
        Prefs.METRIC_GPU_TEMP -> lastGpu?.tempC?.let { "${it.roundToInt()}°" } ?: "—"
        Prefs.METRIC_CPU_FREQ -> lastCpu?.freqGhzText ?: "—"
        Prefs.METRIC_CPU_TEMP -> lastCpu?.tempC?.let { "${it.roundToInt()}°" } ?: "—"
        Prefs.METRIC_BATT_POWER -> lastPower?.currentMa?.let { "$it mA" } ?: "—"
        Prefs.METRIC_BATT_TEMP -> lastPower?.batteryTempC?.let { "${it.roundToInt()}°" } ?: "—"
        Prefs.METRIC_BATT_PCT -> lastPower?.batteryPct?.let { "$it%" } ?: "—"
        Prefs.METRIC_RAM -> lastPower?.ramUsedGbText?.let { "$it GB" } ?: "—"
        else -> "—"
    }

    /** Unit-less text — used in the horizontal bar, where the label below already says what it is. */
    private fun metricValueShort(key: String): String = when (key) {
        Prefs.METRIC_CPU_FREQ -> lastCpu?.freqMhz?.let { String.format(Locale.ROOT, "%.2f", it / 1000f) } ?: "—"
        Prefs.METRIC_GPU_FREQ -> lastGpu?.freqMhz?.toString() ?: "—"
        Prefs.METRIC_BATT_POWER -> lastPower?.currentMa?.toString() ?: "—"
        Prefs.METRIC_RAM -> lastPower?.ramUsedGbText ?: "—"
        else -> metricValue(key)
    }

    /** Widest plausible full-text value, used to reserve a jitter-free width. */
    private fun metricSampleLong(key: String): String = when (key) {
        Prefs.METRIC_FPS -> "999"
        Prefs.METRIC_GPU_FREQ -> "9999 MHz"
        Prefs.METRIC_GPU_TEMP -> "99°"
        Prefs.METRIC_CPU_FREQ -> "9.99 GHz"
        Prefs.METRIC_CPU_TEMP -> "99°"
        Prefs.METRIC_BATT_POWER -> "9999 mA"
        Prefs.METRIC_BATT_TEMP -> "99°"
        Prefs.METRIC_BATT_PCT -> "100%"
        Prefs.METRIC_RAM -> "99.9 GB"
        else -> "999"
    }

    /** Widest plausible short-text value (no unit), same idea for the horizontal bar. */
    private fun metricSampleShort(key: String): String = when (key) {
        Prefs.METRIC_GPU_FREQ -> "9999"
        Prefs.METRIC_CPU_FREQ -> "9.99"
        Prefs.METRIC_BATT_POWER -> "9999"
        Prefs.METRIC_RAM -> "99.9"
        else -> metricSampleLong(key)
    }

    private fun collapsedText(): String {
        val key = compactMetricKey()
            ?: return if (TurboManager.state.value.desiredOn) "ON" else "OFF"
        return metricValue(key)
    }

    // --- drawing helpers ------------------------------------------------------

    /** dp scaled by the user's size setting. */
    private fun dp(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v * scale, context.resources.displayMetrics
    ).roundToInt()

    /** dp without size scaling (used for the initial window position). */
    private fun dpRaw(v: Int): Int = TypedValue.applyDimension(
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
            if (strokeColor != null) setStroke(dp(1f), strokeColor)
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
