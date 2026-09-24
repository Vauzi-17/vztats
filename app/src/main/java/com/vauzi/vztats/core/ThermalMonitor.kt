package com.vauzi.vztats.core

import java.io.File
import java.util.Locale

/**
 * Which hardware component a thermal zone most likely belongs to, inferred
 * from its kernel `type` string. Zone naming is vendor-specific, so this is a
 * best-effort classification — [OTHER] is the honest answer for anything that
 * doesn't clearly match.
 */
enum class SensorGroup { CPU, GPU, BATTERY, OTHER }

/**
 * One kernel thermal zone (`/sys/class/thermal/thermal_zoneN`). The zone's type
 * and group never change at runtime, so they are resolved once; only the
 * temperature is re-read.
 */
class ThermalSensor internal constructor(
    /** Directory name, e.g. "thermal_zone12". Stable for the life of the boot. */
    val zone: String,
    /** Kernel type string, e.g. "gpuss-0", "cpu-1-2-usr", "battery". */
    val type: String,
    val group: SensorGroup,
    private val dir: File
) {
    /** Raw value of the `temp` node — normally milli-degrees Celsius. Null if unreadable. */
    fun readRaw(): Long? =
        runCatching { File(dir, "temp").readText().trim().toLong() }.getOrNull()

    /**
     * Temperature in °C, normalised for the few zones that report whole degrees
     * instead of milli-degrees. Null if unreadable or outside a plausible range,
     * so a broken sensor never shows up as a bogus number.
     */
    fun readCelsius(): Float? {
        val raw = readRaw() ?: return null
        val c = if (raw > 1000L || raw < -1000L) raw / 1000f else raw.toFloat()
        return c.takeIf { it in -20f..150f }
    }
}

/** One kernel thermal zone and its current reading. */
data class ThermalZoneReading(
    val type: String,
    val tempC: Float,
    val group: SensorGroup = SensorGroup.OTHER
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
 * Single catalogue of every readable `/sys/class/thermal/thermal_zone*`. Needs
 * no permission — these nodes are world-readable on the devices this app targets.
 *
 * [CpuMonitor] and [GpuMonitor] pick their headline sensor through [primary]
 * rather than scanning sysfs themselves, so supporting more than one sensor per
 * component later (e.g. per-cluster CPU temps) only needs [sensorsIn], not a
 * rewrite of the monitors.
 */
object ThermalMonitor {

    private val THERMAL_BASE = File("/sys/class/thermal")

    /**
     * Every zone with a readable type, in the order the kernel lists them.
     * That order is kept on purpose: the CPU/GPU monitors have always used the
     * first matching zone in this order, and changing it would silently change
     * which sensor their temperature comes from.
     */
    val sensors: List<ThermalSensor> by lazy {
        (THERMAL_BASE.listFiles { f -> f.name.startsWith("thermal_zone") } ?: emptyArray())
            .mapNotNull { dir ->
                val type = runCatching { File(dir, "type").readText().trim() }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                ThermalSensor(dir.name, type, classify(type), dir)
            }
    }

    val isSupported: Boolean get() = sensors.isNotEmpty()

    /** All zones classified into [group], in kernel order. */
    fun sensorsIn(group: SensorGroup): List<ThermalSensor> = sensors.filter { it.group == group }

    /** The zone used for a component's headline temperature, or null if none matched. */
    fun primary(group: SensorGroup): ThermalSensor? = sensors.firstOrNull { it.group == group }

    /**
     * Current reading of every zone, hottest first. Zones that cannot be read,
     * or that report a value outside a plausible range, are dropped rather than
     * shown as bogus numbers.
     */
    fun readAll(): List<ThermalZoneReading> = sensors.mapNotNull { s ->
        val c = s.readCelsius() ?: return@mapNotNull null
        ThermalZoneReading(type = s.type, tempC = c, group = s.group)
    }.sortedByDescending { it.tempC }

    /**
     * Same matching rules the monitors used before this catalogue existed:
     * GPU = type mentions "gpu"; CPU = mentions cpu/apc/cluster but not gpu.
     */
    private fun classify(rawType: String): SensorGroup {
        val t = rawType.lowercase(Locale.ROOT)
        return when {
            t.contains("gpu") -> SensorGroup.GPU
            t.contains("cpu") || t.contains("apc") || t.contains("cluster") -> SensorGroup.CPU
            t.contains("batt") -> SensorGroup.BATTERY
            else -> SensorGroup.OTHER
        }
    }
}
