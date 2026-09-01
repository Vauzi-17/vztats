package com.vauzi.vztats.service

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.vauzi.vztats.core.CpuSample
import com.vauzi.vztats.core.GpuSample
import com.vauzi.vztats.core.PowerSample
import com.vauzi.vztats.core.Prefs
import com.vauzi.vztats.core.TurboManager
import com.vauzi.vztats.ui.theme.DarkOnPrimary
import com.vauzi.vztats.ui.theme.DarkOnSurface
import com.vauzi.vztats.ui.theme.DarkOnSurfaceVariant
import com.vauzi.vztats.ui.theme.DarkOutlineVariant
import com.vauzi.vztats.ui.theme.DarkPrimary
import com.vauzi.vztats.ui.theme.DarkSurfaceVariant
import com.vauzi.vztats.ui.theme.LightOnPrimary
import com.vauzi.vztats.ui.theme.LightOnSurface
import com.vauzi.vztats.ui.theme.LightOnSurfaceVariant
import com.vauzi.vztats.ui.theme.LightOutlineVariant
import com.vauzi.vztats.ui.theme.LightPrimary
import com.vauzi.vztats.ui.theme.LightSurfaceVariant
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

    /** Size the user picked in settings. */
    private var userScale = 1f

    /**
     * Extra shrink applied only when the horizontal bar would not fit the screen
     * on one line. Recomputed from scratch on every rebuild, so it can never
     * compound or go stale after a rotation.
     */
    private var fitScale = 1f

    /** Effective scale every dimension and text size is derived from. */
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

    // --- theme-derived palette (re-read from Prefs each rebuild) --------------

    private var SURFACE = 0
    private var STROKE = 0
    private var TEXT = 0
    private var MUTED = 0
    private var ACCENT = 0
    private var ON_ACCENT = 0
    private var OFF_DOT = 0
    private var BTN_OFF_BG = 0

    /**
     * Mirrors AdrenoTurboTheme's colour selection (Theme.kt) so the overlay
     * follows the same theme/dark-mode/Material You settings as the rest of
     * the app, instead of a hardcoded palette.
     */
    private fun refreshPalette() {
        val dark = when (prefs.themeMode) {
            Prefs.THEME_LIGHT -> false
            Prefs.THEME_DARK -> true
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        val useDynamic = prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        val primary: Int
        val onPrimary: Int
        val onSurface: Int
        val onSurfaceVariant: Int
        val outlineVariant: Int
        val panelBg: Int

        if (useDynamic) {
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            primary = scheme.primary.toArgb()
            onPrimary = scheme.onPrimary.toArgb()
            onSurface = scheme.onSurface.toArgb()
            onSurfaceVariant = scheme.onSurfaceVariant.toArgb()
            outlineVariant = scheme.outlineVariant.toArgb()
            panelBg = scheme.surfaceVariant.toArgb()
        } else if (dark) {
            primary = DarkPrimary.toArgb()
            onPrimary = DarkOnPrimary.toArgb()
            onSurface = DarkOnSurface.toArgb()
            onSurfaceVariant = DarkOnSurfaceVariant.toArgb()
            outlineVariant = DarkOutlineVariant.toArgb()
            panelBg = DarkSurfaceVariant.toArgb()
        } else {
            primary = LightPrimary.toArgb()
            onPrimary = LightOnPrimary.toArgb()
            onSurface = LightOnSurface.toArgb()
            onSurfaceVariant = LightOnSurfaceVariant.toArgb()
            outlineVariant = LightOutlineVariant.toArgb()
            panelBg = LightSurfaceVariant.toArgb()
        }

        ACCENT = primary
        ON_ACCENT = onPrimary
        TEXT = onSurface
        MUTED = onSurfaceVariant
        STROKE = withAlpha(outlineVariant, 0x26)
        SURFACE = withAlpha(panelBg, 0xF0)
        OFF_DOT = withAlpha(onSurfaceVariant, 0x80)
        BTN_OFF_BG = withAlpha(onSurface, 0x1F)
    }

    private fun withAlpha(argb: Int, alpha: Int): Int = (argb and 0x00FFFFFF) or (alpha shl 24)

    val isShowing: Boolean get() = root != null

    /**
     * The overlay is a WindowManager view, so it survives rotation instead of
     * being recreated like an Activity's content. Without this the panel keeps
     * whatever size it was built at for the previous orientation — appearing
     * too small in landscape, or too wide and clipped back in portrait.
     */
    private val configCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            // Display metrics can lag the configuration callback on some OEM
            // skins, so rebuild on the next loop rather than inline.
            root?.postDelayed({ if (root != null) rebuild() }, 120L)
        }

        override fun onLowMemory() = Unit
    }

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
                clampParamsToScreen()
                runCatching { wm.updateViewLayout(this, params) }
            }
        }
        rebuild()
        runCatching { wm.addView(root, params) }
        runCatching { context.registerComponentCallbacks(configCallbacks) }
    }

    fun hide() {
        runCatching { context.unregisterComponentCallbacks(configCallbacks) }
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        clearRefs()
    }

    /** Rebuilds the whole panel from the current prefs (mode/metrics/size/opacity). */
    fun rebuild() {
        val container = root ?: return
        mode = prefs.floatingMode
        userScale = prefs.floatingSize.coerceIn(60, 200) / 100f
        refreshPalette()

        // Always start from the user's size with no shrink, so the result only
        // depends on the current prefs and the current screen - never on what
        // the panel happened to look like before.
        fitScale = 1f
        scale = userScale
        populate(container)

        // The horizontal bar is the only layout that must fit the screen width
        // on a single line. Measure what it naturally wants, and if that
        // overflows, rebuild once at the exact scale that fits.
        if (mode == Prefs.MODE_HORIZONTAL) {
            val natural = measuredWidthOf(container)
            val available = realScreenWidthPx() - dpRaw(EDGE_MARGIN_DP) * 2
            if (natural > 0 && available > 0 && natural > available) {
                fitScale = (available.toFloat() / natural).coerceIn(MIN_FIT_SCALE, 1f)
                scale = userScale * fitScale
                populate(container)
            }
        }

        clampParamsToScreen()
        if (container.isAttachedToWindow) {
            runCatching { wm.updateViewLayout(container, params) }
        }
    }

    /** Builds the current layout into [container] at the current [scale]. */
    private fun populate(container: DraggableOverlayLayout) {
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

    private fun measuredWidthOf(v: View): Int {
        v.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return v.measuredWidth
    }

    /**
     * Keeps the panel fully on screen. Without this a position chosen in one
     * orientation can leave the panel hanging off the edge in the other.
     */
    private fun clampParamsToScreen() {
        val r = root ?: return
        if (!::params.isInitialized) return
        r.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val margin = dpRaw(EDGE_MARGIN_DP)
        val maxX = (realScreenWidthPx() - r.measuredWidth - margin).coerceAtLeast(margin)
        val maxY = (realScreenHeightPx() - r.measuredHeight - margin).coerceAtLeast(margin)
        params.x = params.x.coerceIn(margin, maxX)
        params.y = params.y.coerceIn(margin, maxY)
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
            it.text = if (on) "LOCK ON" else "LOCK OFF"
            it.background = roundedFill(if (on) ACCENT else BTN_OFF_BG, dp(11f))
            it.setTextColor(if (on) ON_ACCENT else TEXT)
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
            isSingleLine = true
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

        if (compact || showButton) panel.addView(controlRow(showClose = false, topMargin = dp(10f)))
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
            isSingleLine = true
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
        // No leading status dot here — it was eating width that mattered more
        // for fitting every metric on one line. Turbo state is still visible
        // via the Lock button once the row is tapped open.
        dot = null

        // Single row of label + value pairs, separated by hairline dividers.
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        orderedMetrics().forEachIndexed { i, key ->
            if (i > 0) row.addView(divider())
            row.addView(horizontalCell(key))
        }

        // Fitting the row to the screen is handled by rebuild(), which measures
        // this layout and rebuilds it at a corrected scale if needed. Doing it
        // there rather than mutating views here keeps the result dependent only
        // on the current screen, so rotating cannot leave a stale size behind.

        // Outer column so the toggle + close controls sit on their own line.
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedFill(SURFACE, dp(16f), STROKE)
            setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
            addView(row)
            if (showButton) addView(controlRow(topMargin = dp(6f)))
            setOnClickListener { if (!showButton) { showButton = true; rebuild() } }
        }
    }

    /** Thin vertical separator between horizontal-bar cells, Red Magic-style. */
    private fun divider(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(dp(1f), dp(14f)).apply {
            leftMargin = dp(4f); rightMargin = dp(4f)
        }
        setBackgroundColor(STROKE)
    }

    /** Short but readable label for the horizontal bar — e.g. "GPU", "GPU%", "GPU°". */
    private fun metricShortLabel(key: String): String = when (key) {
        Prefs.METRIC_FPS -> "FPS"
        Prefs.METRIC_GPU_FREQ -> "GPU"
        Prefs.METRIC_GPU_LOAD -> "GPU%"
        Prefs.METRIC_GPU_TEMP -> "GPU°"
        Prefs.METRIC_CPU_FREQ -> "CPU"
        Prefs.METRIC_CPU_LOAD -> "CPU%"
        Prefs.METRIC_CPU_TEMP -> "CPU°"
        Prefs.METRIC_BATT_POWER -> "mA"
        Prefs.METRIC_BATT_TEMP -> "BAT°"
        Prefs.METRIC_BATT_PCT -> "BAT%"
        Prefs.METRIC_RAM -> "RAM"
        Prefs.METRIC_RAM_PCT -> "RAM%"
        else -> key
    }

    private fun controlRow(showClose: Boolean = true, topMargin: Int = 0): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, dp(1f))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { this.topMargin = topMargin }
            addView(toggleButton())
            addView(ramChip())
            if (showClose) addView(closeChip())
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
        val label = TextView(context).apply {
            text = metricShortLabel(key)
            setTextColor(MUTED)
            textSize = 9f * scale
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER_VERTICAL
            isSingleLine = true
        }
        val value = TextView(context).apply {
            setTextColor(TEXT)
            textSize = 13f * scale
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), 0, 0, 0)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        // Reserve the worst-case width up front (before real values are set by
        // applyValues()) so both the layout never jitters as digits change AND
        // the single-line overflow measurement below is accurate rather than
        // based on still-empty TextViews. isSingleLine on both guarantees a cell
        // never wraps to a second line even if this estimate is ever exceeded.
        value.minWidth = value.paint.measureText(metricSampleShort(key)).toInt() + dp(1f)
        valueViews[key] = value
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(label); addView(value)
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

    private fun toggleButton(): TextView {
        val btn = TextView(context).apply {
            gravity = Gravity.CENTER
            textSize = 11f * scale
            setTypeface(Typeface.DEFAULT_BOLD)
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
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
            Prefs.METRIC_GPU_FREQ, Prefs.METRIC_GPU_LOAD, Prefs.METRIC_GPU_TEMP,
            Prefs.METRIC_CPU_FREQ, Prefs.METRIC_CPU_LOAD, Prefs.METRIC_CPU_TEMP,
            Prefs.METRIC_BATT_POWER, Prefs.METRIC_BATT_TEMP, Prefs.METRIC_BATT_PCT,
            Prefs.METRIC_RAM, Prefs.METRIC_RAM_PCT
        ).filter { it in enabled }
    }

    /** Which metric the compact pill shows — the user's pick if still enabled, else the first. */
    private fun compactMetricKey(): String? {
        val enabled = orderedMetrics()
        if (enabled.isEmpty()) return null
        val picked = prefs.floatingCompactMetric
        return picked.takeIf { it in enabled } ?: enabled.first()
    }

    private fun ramPct(): Int? {
        val p = lastPower ?: return null
        val used = p.ramUsedMb ?: return null
        val total = p.ramTotalMb?.takeIf { it > 0 } ?: return null
        return (used * 100 / total).coerceIn(0, 100)
    }

    private fun metricLabel(key: String): String = when (key) {
        Prefs.METRIC_FPS -> "FPS"
        Prefs.METRIC_GPU_FREQ -> "GPU"
        Prefs.METRIC_GPU_LOAD -> "GPU %"
        Prefs.METRIC_GPU_TEMP -> "GPU °C"
        Prefs.METRIC_CPU_FREQ -> "CPU"
        Prefs.METRIC_CPU_LOAD -> "CPU %"
        Prefs.METRIC_CPU_TEMP -> "CPU °C"
        Prefs.METRIC_BATT_POWER -> "mA"
        Prefs.METRIC_BATT_TEMP -> "BAT °C"
        Prefs.METRIC_BATT_PCT -> "BAT %"
        Prefs.METRIC_RAM -> "RAM"
        Prefs.METRIC_RAM_PCT -> "RAM %"
        else -> key
    }

    /** Full text with unit — used where there's room (vertical rows, compact pill). */
    private fun metricValue(key: String): String = when (key) {
        Prefs.METRIC_FPS -> lastFps?.toString() ?: "—"
        Prefs.METRIC_GPU_FREQ -> lastGpu?.freqMhz?.let { "$it MHz" } ?: "—"
        Prefs.METRIC_GPU_LOAD -> lastGpu?.loadOfMax?.let { "${(it * 100).roundToInt()}%" } ?: "—"
        Prefs.METRIC_GPU_TEMP -> lastGpu?.tempC?.let { "${it.roundToInt()}°" } ?: "—"
        Prefs.METRIC_CPU_FREQ -> lastCpu?.freqGhzText ?: "—"
        Prefs.METRIC_CPU_LOAD -> lastCpu?.loadOfMax?.let { "${(it * 100).roundToInt()}%" } ?: "—"
        Prefs.METRIC_CPU_TEMP -> lastCpu?.tempC?.let { "${it.roundToInt()}°" } ?: "—"
        Prefs.METRIC_BATT_POWER -> lastPower?.currentMa?.let { "$it mA" } ?: "—"
        Prefs.METRIC_BATT_TEMP -> lastPower?.batteryTempC?.let { "${it.roundToInt()}°" } ?: "—"
        Prefs.METRIC_BATT_PCT -> lastPower?.batteryPct?.let { "$it%" } ?: "—"
        Prefs.METRIC_RAM -> lastPower?.ramUsedGbText?.let { "$it GB" } ?: "—"
        Prefs.METRIC_RAM_PCT -> ramPct()?.let { "$it%" } ?: "—"
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
        Prefs.METRIC_GPU_LOAD, Prefs.METRIC_CPU_LOAD, Prefs.METRIC_BATT_PCT, Prefs.METRIC_RAM_PCT -> "100%"
        Prefs.METRIC_GPU_TEMP -> "99°"
        Prefs.METRIC_CPU_FREQ -> "9.99 GHz"
        Prefs.METRIC_CPU_TEMP -> "99°"
        Prefs.METRIC_BATT_POWER -> "9999 mA"
        Prefs.METRIC_BATT_TEMP -> "99°"
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

    /** Real usable screen width in px, from the WindowManager actually hosting the overlay. */
    private fun realScreenWidthPx(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        wm.currentWindowMetrics.bounds.width()
    } else {
        @Suppress("DEPRECATION")
        android.graphics.Point().also { wm.defaultDisplay.getRealSize(it) }.x
    }

    private fun realScreenHeightPx(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        wm.currentWindowMetrics.bounds.height()
    } else {
        @Suppress("DEPRECATION")
        android.graphics.Point().also { wm.defaultDisplay.getRealSize(it) }.y
    }

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
        /** Breathing room kept between the panel and every screen edge. */
        private const val EDGE_MARGIN_DP = 6

        /** Floor on the auto-shrink, so the bar stays legible instead of vanishing. */
        private const val MIN_FIT_SCALE = 0.55f

        fun canDraw(context: Context): Boolean = Settings.canDrawOverlays(context)
    }
}
