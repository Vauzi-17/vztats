package com.vauzi.vztats.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import java.util.Locale
import kotlin.math.abs

/** Battery charge state as reported by the framework (EXTRA_STATUS). */
enum class BatteryStatus(val label: String) {
    CHARGING("Charging"),
    DISCHARGING("Discharging"),
    NOT_CHARGING("Not charging"),
    FULL("Full");

    companion object {
        fun from(status: Int): BatteryStatus? = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> DISCHARGING
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> NOT_CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> FULL
            else -> null // BATTERY_STATUS_UNKNOWN or missing
        }
    }
}

/**
 * Battery power / temperature and RAM usage. All from framework APIs that need
 * no special permission.
 */
object PowerMonitor {

    fun read(context: Context): PowerSample {
        val app = context.applicationContext

        val bm = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        // Documented as microamperes. A few devices report milliamperes here
        // instead; there is no reliable way to detect that, so the value is
        // passed through as documented rather than "corrected" by guesswork.
        val currentUa = bm?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = currentUa
            ?.takeIf { it != Long.MIN_VALUE && it != 0L }
            ?.let { abs(it / 1000L).toInt() }
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }

        val batteryIntent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val batteryTempC = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }
            ?.let { it / 10f }
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        // EXTRA_VOLTAGE is millivolts. Anything outside a single Li-ion cell's
        // range means the device reports it differently — drop it instead of
        // showing (and multiplying) a wrong number.
        val voltageMv = batteryIntent
            ?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            ?.takeIf { it in 2500..5000 }
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1

        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val totalMb = (mi.totalMem / (1024L * 1024L)).toInt().takeIf { it > 0 }
        val availMb = (mi.availMem / (1024L * 1024L)).toInt().takeIf { it > 0 }

        return PowerSample(
            currentMa = currentMa,
            batteryTempC = batteryTempC,
            batteryPct = level,
            charging = charging,
            ramTotalMb = totalMb,
            ramAvailMb = availMb,
            voltageMv = voltageMv,
            status = BatteryStatus.from(status),
            pluggedSource = pluggedLabel(plugged)
        )
    }

    private fun pluggedLabel(plugged: Int): String? = when (plugged) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
        else -> null // unplugged, or a source this API level doesn't name
    }
}

data class PowerSample(
    /** Magnitude of BATTERY_PROPERTY_CURRENT_NOW in mA; direction comes from [status]. */
    val currentMa: Int?,
    val batteryTempC: Float?,
    val batteryPct: Int?,
    /** Charging or full — kept as-is for existing callers. */
    val charging: Boolean,
    val ramTotalMb: Int?,
    val ramAvailMb: Int?,
    val voltageMv: Int? = null,
    val status: BatteryStatus? = null,
    /** "AC" / "USB" / "Wireless" while plugged in, null otherwise. */
    val pluggedSource: String? = null
) {
    val ramUsedMb: Int?
        get() {
            val t = ramTotalMb ?: return null
            val a = ramAvailMb ?: return null
            return (t - a).coerceAtLeast(0)
        }

    /** RAM in use as a whole percent of total (0..100), floored. */
    val ramUsedPct: Int?
        get() {
            val used = ramUsedMb ?: return null
            val total = ramTotalMb?.takeIf { it > 0 } ?: return null
            return (used * 100 / total).coerceIn(0, 100)
        }

    val ramUsedGbText: String?
        get() = ramUsedMb?.let { String.format(Locale.ROOT, "%.1f", it / 1024f) }

    val ramTotalGbText: String?
        get() = ramTotalMb?.let { String.format(Locale.ROOT, "%.1f", it / 1024f) }

    /**
     * Battery power in watts, calculated as voltage × current. Only present when
     * both inputs are reported; it inherits the current reading's accuracy, so
     * it is wrong on devices that misreport CURRENT_NOW units.
     */
    val powerW: Float?
        get() {
            val mv = voltageMv ?: return null
            val ma = currentMa ?: return null
            return mv * ma / 1_000_000f
        }

    companion object {
        val EMPTY = PowerSample(null, null, null, false, null, null)
    }
}
