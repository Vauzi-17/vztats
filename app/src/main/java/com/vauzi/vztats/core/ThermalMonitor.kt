package com.vauzi.vztats.core

import java.io.File
import java.util.Locale

/** One kernel thermal zone and its current reading. */
data class ThermalZoneReading(
    val type: String,
    val tempC: Float
) {
    /** Human-friendly name: "gpuss-0" -> "Gpuss 0", "battery" -> "Battery". */
    val label: String
        get() = type.replace('_', ' ').replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part ->
                part.replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
            }
}

/**
 * Reads every readable `/sys/class/thermal/thermal_zone*`, not just the single
 * CPU/GPU zone [CpuMonitor] and [GpuMonitor] pick out. Needs no permission —
 * these nodes are world-readable on the devices this app targets.
 */
object ThermalMonitor {

    private val THERMAL_BASE = File("/sys/class/thermal")

    /**
     * Zone directories paired with their type, resolved once. The type of a zone
     * never changes at runtime, so only the temperature is re-read per sample.
     */
    private val zones: List<Pair<File, String>> by lazy {
        (THERMAL_BASE.listFiles { f -> f.name.startsWith("thermal_zone") } ?: emptyArray())
            .mapNotNull { dir ->
                val type = runCatching { File(dir, "type").readText().trim() }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                dir to type
            }
            .sortedBy { it.first.name }
    }

    val isSupported: Boolean get() = zones.isNotEmpty()

    /**
     * Current reading of every zone, hottest first. Zones that cannot be read,
     * or that report a value outside a plausible range, are dropped rather than
     * shown as bogus numbers.
     */
    fun readAll(): List<ThermalZoneReading> = zones.mapNotNull { (dir, type) ->
        val milli = runCatching { File(dir, "temp").readText().trim().toLong() }.getOrNull()
            ?: return@mapNotNull null
        // Most zones report milli-degrees, a few report whole degrees.
        val c = if (milli > 1000L || milli < -1000L) milli / 1000f else milli.toFloat()
        if (c < -20f || c > 150f) return@mapNotNull null
        ThermalZoneReading(type = type, tempC = c)
    }.sortedByDescending { it.tempC }
}
