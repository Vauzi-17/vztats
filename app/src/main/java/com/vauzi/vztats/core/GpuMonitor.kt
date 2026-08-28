package com.vauzi.vztats.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Reads real values straight from the KGSL sysfs nodes and the kernel thermal
 * zones. Nothing here is fabricated — every number comes from a file the driver
 * exposes, which is why it doubles as an honest verification tool.
 */
object GpuMonitor {

    private const val KGSL_DIR = "/sys/class/kgsl/kgsl-3d0"
    private val GPUCLK = File("$KGSL_DIR/gpuclk")
    private val MAX_GPUCLK = File("$KGSL_DIR/max_gpuclk")
    private val DEVFREQ_MAX = File("$KGSL_DIR/devfreq/max_freq")
    private val AVAILABLE_FREQS = File("$KGSL_DIR/gpu_available_frequencies")

    /** True if this device exposes the Adreno/KGSL frequency node at all. */
    val isSupported: Boolean get() = GPUCLK.canRead()

    @Volatile
    private var observedMaxHz: Long = 0L

    /** Highest GPU clock actually seen this session — the empirical ceiling. */
    val observedMaxMhz: Int?
        get() = if (observedMaxHz > 0) (observedMaxHz / 1_000_000L).toInt() else null

    /** Current GPU clock in Hz, or null if unreadable. */
    fun currentFreqHz(): Long? {
        val v = readLong(GPUCLK)
        if (v != null && v > observedMaxHz) observedMaxHz = v
        return v
    }

    /**
     * Maximum GPU clock in Hz. Tries the direct nodes first, then the
     * available-frequencies list, and finally falls back to the highest clock
     * observed live (the only option on devices that block the static nodes).
     */
    fun maxFreqHz(): Long? {
        readLong(MAX_GPUCLK)?.let { if (it > 0) return it }
        readLong(DEVFREQ_MAX)?.let { if (it > 0) return it }
        runCatching {
            AVAILABLE_FREQS.readText()
                .trim()
                .split(Regex("\\s+"))
                .mapNotNull { it.toLongOrNull() }
                .maxOrNull()
        }.getOrNull()?.let { if (it > 0) return it }

        return if (observedMaxHz > 0) observedMaxHz else null
    }

    /** GPU temperature in milli-degrees Celsius, or null if no GPU zone found. */
    fun gpuTempMilliC(): Int? = gpuThermalZone?.let { readInt(File("$it/temp")) }

    private val gpuThermalZone: String? by lazy { findGpuThermalZone() }

    private fun findGpuThermalZone(): String? {
        val base = File("/sys/class/thermal")
        val zones = base.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        // Prefer a zone whose type mentions the GPU (e.g. "gpuss-0", "gpu-usr").
        for (z in zones) {
            val type = runCatching { File(z, "type").readText().trim().lowercase() }.getOrNull() ?: continue
            if (type.contains("gpu")) return z.absolutePath
        }
        return null
    }

    private fun readLong(f: File): Long? =
        runCatching { f.readText().trim().toLong() }.getOrNull()

    private fun readInt(f: File): Int? =
        runCatching { f.readText().trim().toInt() }.getOrNull()

    private fun readRaw(name: String): String? =
        runCatching { File("$KGSL_DIR/$name").readText().trim() }
            .getOrNull()?.takeIf { it.isNotEmpty() }

    /**
     * One-shot dump of the KGSL power/frequency description. The app can read
     * these even though `adb shell` often can't (different SELinux domain).
     */
    fun readDetails(): GpuDetails {
        val freqsHz = (readRaw("gpu_available_frequencies")
            ?: readRaw("devfreq/available_frequencies"))
            ?.split(Regex("\\s+"))
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

        val freqsMhz = freqsHz
            .map { (it / 1_000_000L).toInt() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()

        val maxClampHz = readLong(MAX_GPUCLK)?.takeIf { it > 0 }
            ?: readLong(DEVFREQ_MAX)?.takeIf { it > 0 }

        return GpuDetails(
            model = readRaw("gpu_model"),
            availableFreqsMhz = freqsMhz,
            maxClampMhz = maxClampHz?.let { (it / 1_000_000L).toInt() },
            minMhz = readLong(File("$KGSL_DIR/devfreq/min_freq"))?.let { (it / 1_000_000L).toInt() },
            numPwrLevels = readInt(File("$KGSL_DIR/num_pwrlevels")),
            thermalPwrLevel = readInt(File("$KGSL_DIR/thermal_pwrlevel")),
            defaultPwrLevel = readInt(File("$KGSL_DIR/default_pwrlevel")),
            maxPwrLevel = readInt(File("$KGSL_DIR/max_pwrlevel")),
            minPwrLevel = readInt(File("$KGSL_DIR/min_pwrlevel")),
            governor = readRaw("devfreq/governor"),
            observedMaxMhz = observedMaxMhz
        )
    }

    /**
     * Emits a fresh sample roughly every [periodMs]. Cold flow — collection
     * drives the polling and cancellation stops it.
     */
    fun sampleFlow(periodMs: Long = 1000L): Flow<GpuSample> = flow {
        val max = maxFreqHz()
        while (true) {
            emit(
                GpuSample(
                    freqHz = currentFreqHz(),
                    maxFreqHz = max ?: maxFreqHz(),
                    tempMilliC = gpuTempMilliC(),
                    timestampMs = System.currentTimeMillis()
                )
            )
            delay(periodMs)
        }
    }
}

data class GpuSample(
    val freqHz: Long?,
    val maxFreqHz: Long?,
    val tempMilliC: Int?,
    val timestampMs: Long
) {
    val freqMhz: Int? get() = freqHz?.let { (it / 1_000_000L).toInt() }
    val maxFreqMhz: Int? get() = maxFreqHz?.let { (it / 1_000_000L).toInt() }
    val tempC: Float? get() = tempMilliC?.let { it / 1000f }

    /** How close the current clock sits to the maximum (0f..1f). */
    val loadOfMax: Float?
        get() {
            val f = freqHz ?: return null
            val m = maxFreqHz ?: return null
            if (m <= 0) return null
            return (f.toFloat() / m.toFloat()).coerceIn(0f, 1f)
        }

    /** True when the clock is pinned at (near) the maximum — turbo working. */
    val isAtMax: Boolean
        get() = loadOfMax?.let { it >= 0.97f } ?: false
}

/** Static-ish description of the GPU's frequency/power-level table. */
data class GpuDetails(
    val model: String?,
    val availableFreqsMhz: List<Int>,
    val maxClampMhz: Int?,
    val minMhz: Int?,
    val numPwrLevels: Int?,
    val thermalPwrLevel: Int?,
    val defaultPwrLevel: Int?,
    val maxPwrLevel: Int?,
    val minPwrLevel: Int?,
    val governor: String?,
    val observedMaxMhz: Int?
) {
    /** Highest bin in the table = the absolute ceiling without a custom kernel. */
    val ceilingMhz: Int? get() = availableFreqsMhz.maxOrNull()

    /** True when the device blocks apps from reading the static description. */
    val staticTableBlocked: Boolean
        get() = model == null && availableFreqsMhz.isEmpty() && numPwrLevels == null

    /** True if the active max clamp sits below the table ceiling (vendor/thermal cap). */
    val cappedBelowCeiling: Boolean
        get() {
            val c = ceilingMhz ?: return false
            val m = maxClampMhz ?: return false
            return m < c
        }
}
