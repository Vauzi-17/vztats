package com.vauzi.clocklock.core

import java.io.File
import java.util.Locale

/**
 * Best-effort CPU readout. Unlike the GPU there is no unprivileged way to *lock*
 * CPU clocks (that lives in root-owned sysfs), so this is monitoring only.
 *
 * Even reading `scaling_cur_freq` is blocked by SELinux on some devices; every
 * accessor degrades to null rather than guessing.
 */
object CpuMonitor {

    private val CPU_BASE = File("/sys/devices/system/cpu")

    val coreCount: Int by lazy {
        (CPU_BASE.listFiles { f -> f.name.matches(Regex("cpu[0-9]+")) }?.size
            ?: Runtime.getRuntime().availableProcessors()).coerceAtLeast(1)
    }

    val isSupported: Boolean get() = curFreqKhzMax() != null

    /** Highest current core clock in kHz (a good proxy for the boost clock). */
    fun curFreqKhzMax(): Long? {
        var max: Long? = null
        for (i in 0 until coreCount) {
            val v = readLong(File("$CPU_BASE/cpu$i/cpufreq/scaling_cur_freq")) ?: continue
            if (max == null || v > max) max = v
        }
        return max
    }

    /** Highest advertised max clock in kHz across all cores. */
    fun maxFreqKhz(): Long? {
        var max: Long? = null
        for (i in 0 until coreCount) {
            val v = readLong(File("$CPU_BASE/cpu$i/cpufreq/cpuinfo_max_freq")) ?: continue
            if (max == null || v > max) max = v
        }
        return max
    }

    fun cpuTempMilliC(): Int? = cpuThermalZone?.let { readInt(File("$it/temp")) }

    private val cpuThermalZone: String? by lazy { findCpuThermalZone() }

    private fun findCpuThermalZone(): String? {
        val zones = File("/sys/class/thermal")
            .listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        for (z in zones) {
            val type = runCatching { File(z, "type").readText().trim().lowercase(Locale.ROOT) }
                .getOrNull() ?: continue
            // Match cpu / cluster / apc style zones, but not the GPU one.
            if ((type.contains("cpu") || type.contains("apc") || type.contains("cluster")) &&
                !type.contains("gpu")
            ) return z.absolutePath
        }
        return null
    }

    private fun readLong(f: File): Long? = runCatching { f.readText().trim().toLong() }.getOrNull()
    private fun readInt(f: File): Int? = runCatching { f.readText().trim().toInt() }.getOrNull()
}

data class CpuSample(
    val curKhzMax: Long?,
    val maxKhz: Long?,
    val tempMilliC: Int?
) {
    val freqMhz: Int? get() = curKhzMax?.let { (it / 1000L).toInt() }
    val maxMhz: Int? get() = maxKhz?.let { (it / 1000L).toInt() }
    val freqGhzText: String?
        get() = curKhzMax?.let { String.format(Locale.ROOT, "%.2f GHz", it / 1_000_000f) }
    val tempC: Float? get() = tempMilliC?.let { it / 1000f }
}
