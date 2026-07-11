package com.fartopblu.adrenoturbomode.core

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

    /** Current GPU clock in Hz, or null if unreadable. */
    fun currentFreqHz(): Long? = readLong(GPUCLK)

    /**
     * Maximum GPU clock in Hz. Tries the direct nodes first, then falls back to
     * the largest entry of the available-frequencies list.
     */
    fun maxFreqHz(): Long? {
        readLong(MAX_GPUCLK)?.let { if (it > 0) return it }
        readLong(DEVFREQ_MAX)?.let { if (it > 0) return it }
        return runCatching {
            AVAILABLE_FREQS.readText()
                .trim()
                .split(Regex("\\s+"))
                .mapNotNull { it.toLongOrNull() }
                .maxOrNull()
        }.getOrNull()
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
