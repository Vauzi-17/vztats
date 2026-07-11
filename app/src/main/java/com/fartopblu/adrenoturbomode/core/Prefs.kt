package com.fartopblu.adrenoturbomode.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Small typed wrapper over SharedPreferences. Holds the user's intent and the
 * optional auto-safety configuration so every entry point (UI, tile, overlay,
 * service) reads from one place.
 */
class Prefs private constructor(private val sp: SharedPreferences) {

    var desiredTurbo: Boolean
        get() = sp.getBoolean(KEY_DESIRED_TURBO, false)
        set(value) = sp.edit().putBoolean(KEY_DESIRED_TURBO, value).apply()

    var overlayEnabled: Boolean
        get() = sp.getBoolean(KEY_OVERLAY, false)
        set(value) = sp.edit().putBoolean(KEY_OVERLAY, value).apply()

    var reapplyOnUnlock: Boolean
        get() = sp.getBoolean(KEY_REAPPLY, true)
        set(value) = sp.edit().putBoolean(KEY_REAPPLY, value).apply()

    var autoSafetyEnabled: Boolean
        get() = sp.getBoolean(KEY_SAFETY, false)
        set(value) = sp.edit().putBoolean(KEY_SAFETY, value).apply()

    var tempLimitC: Int
        get() = sp.getInt(KEY_TEMP_LIMIT, DEFAULT_TEMP_LIMIT)
        set(value) = sp.edit().putInt(KEY_TEMP_LIMIT, value).apply()

    var batteryLimitPct: Int
        get() = sp.getInt(KEY_BATT_LIMIT, DEFAULT_BATT_LIMIT)
        set(value) = sp.edit().putInt(KEY_BATT_LIMIT, value).apply()

    var firstRun: Boolean
        get() = sp.getBoolean(KEY_FIRST_RUN, true)
        set(value) = sp.edit().putBoolean(KEY_FIRST_RUN, value).apply()

    // --- Appearance ----------------------------------------------------------

    /** One of "system" | "light" | "dark". */
    var themeMode: String
        get() = sp.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = sp.edit().putString(KEY_THEME, value).apply()

    /** Use Material You wallpaper colours instead of the custom palette. */
    var dynamicColor: Boolean
        get() = sp.getBoolean(KEY_DYNAMIC, false)
        set(value) = sp.edit().putBoolean(KEY_DYNAMIC, value).apply()

    // --- Floating window customisation ---------------------------------------

    /** Which metrics the floating panel shows, as a set of METRIC_* keys. */
    var floatingMetrics: Set<String>
        get() = sp.getStringSet(KEY_FLOAT_METRICS, DEFAULT_METRICS)?.toSet() ?: DEFAULT_METRICS
        set(value) = sp.edit().putStringSet(KEY_FLOAT_METRICS, value).apply()

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)

    companion object {
        const val KEY_DESIRED_TURBO = "desired_turbo"
        const val KEY_OVERLAY = "overlay_enabled"
        const val KEY_REAPPLY = "reapply_on_unlock"
        const val KEY_SAFETY = "auto_safety"
        const val KEY_TEMP_LIMIT = "temp_limit_c"
        const val KEY_BATT_LIMIT = "battery_limit_pct"
        const val KEY_FIRST_RUN = "first_run"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_FLOAT_METRICS = "floating_metrics"

        const val DEFAULT_TEMP_LIMIT = 48
        const val DEFAULT_BATT_LIMIT = 15

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        // Floating-panel metric keys.
        const val METRIC_GPU_FREQ = "gpu_freq"
        const val METRIC_GPU_TEMP = "gpu_temp"
        const val METRIC_CPU_FREQ = "cpu_freq"
        const val METRIC_CPU_TEMP = "cpu_temp"

        val DEFAULT_METRICS: Set<String> =
            setOf(METRIC_GPU_FREQ, METRIC_GPU_TEMP, METRIC_CPU_FREQ)

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) {
                instance ?: Prefs(
                    context.applicationContext
                        .getSharedPreferences("adreno_turbo_prefs", Context.MODE_PRIVATE)
                ).also { instance = it }
            }
    }
}
