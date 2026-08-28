package com.vauzi.vztats.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlin.math.abs

/**
 * Battery power / temperature and RAM usage. All from framework APIs that need
 * no special permission.
 */
object PowerMonitor {

    fun read(context: Context): PowerSample {
        val app = context.applicationContext

        val bm = app.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
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
            ramAvailMb = availMb
        )
    }
}

data class PowerSample(
    val currentMa: Int?,
    val batteryTempC: Float?,
    val batteryPct: Int?,
    val charging: Boolean,
    val ramTotalMb: Int?,
    val ramAvailMb: Int?
) {
    val ramUsedMb: Int?
        get() {
            val t = ramTotalMb ?: return null
            val a = ramAvailMb ?: return null
            return (t - a).coerceAtLeast(0)
        }

    val ramUsedGbText: String?
        get() = ramUsedMb?.let { String.format(java.util.Locale.ROOT, "%.1f", it / 1024f) }

    val ramTotalGbText: String?
        get() = ramTotalMb?.let { String.format(java.util.Locale.ROOT, "%.1f", it / 1024f) }

    companion object {
        val EMPTY = PowerSample(null, null, null, false, null, null)
    }
}
