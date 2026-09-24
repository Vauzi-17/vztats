package com.vauzi.vztats.core

import java.io.File
import java.util.Locale

/**
 * Best-effort CPU readout. Unlike the GPU there is no unprivileged way to *lock*
 * CPU clocks (that lives in root-owned sysfs), so this is monitoring only.
 *
 * Even reading `scaling_cur_freq` is blocked by SELinux on some devices; every
 * accessor degrades to null rather than guessing. True CPU utilization needs
 * /proc/stat, which is root-only on many devices — so the only "load" figure
 * here is a clock ratio (current/max, same idea as GpuSample.loadOfMax), and
 * the UI labels it as such.
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

    fun cpuTempMilliC(): Int? = ThermalMonitor.primary(SensorGroup.CPU)?.readRaw()?.toInt()

    /** Per-core current/max clock in MHz, in core order — null entries where unreadable. */
    fun perCore(): List<CoreFreq> = (0 until coreCount).map { i ->
        CoreFreq(
            index = i,
            curMhz = readLong(File("$CPU_BASE/cpu$i/cpufreq/scaling_cur_freq"))?.let { (it / 1000L).toInt() },
            maxMhz = readLong(File("$CPU_BASE/cpu$i/cpufreq/cpuinfo_max_freq"))?.let { (it / 1000L).toInt() }
        )
    }

    /**
     * Cluster sizes joined with "+", e.g. "4+4" for a typical little.big split.
     * Empty string if nothing is readable.
     *
     * Uses the kernel's cpufreq policy grouping ([policyGroups]) when it's
     * readable — that is the real frequency domain, and it tells apart clusters
     * that happen to share a max clock (e.g. "3+2+2+1" rather than "3+4+1").
     * Otherwise falls back to grouping consecutive cores by max clock.
     */
    fun clusterLabel(cores: List<CoreFreq>): String {
        policyGroups?.let { groups ->
            if (groups.sumOf { it.size } == coreCount) return groups.joinToString("+") { it.size.toString() }
        }
        val groups = mutableListOf<Int>()
        var last: Int? = -1
        for (c in cores) {
            if (c.maxMhz != last) {
                groups.add(1)
                last = c.maxMhz
            } else {
                groups[groups.lastIndex]++
            }
        }
        return groups.joinToString("+")
    }

    /**
     * Cores grouped by cpufreq policy, from each core's `related_cpus` (the set
     * of CPUs sharing one clock). Null if any core's node is unreadable, so a
     * partial read never produces a wrong split. Resolved once: the topology
     * doesn't change at runtime, and `related_cpus` includes offline cores.
     */
    private val policyGroups: List<List<Int>>? by lazy {
        val seen = HashSet<Int>()
        val groups = mutableListOf<List<Int>>()
        for (i in 0 until coreCount) {
            if (i in seen) continue
            val related = runCatching {
                File("$CPU_BASE/cpu$i/cpufreq/related_cpus").readText().trim()
                    .split(Regex("\\s+")).map { it.toInt() }.sorted()
            }.getOrNull()
            if (related.isNullOrEmpty() || i !in related) return@lazy null
            seen.addAll(related)
            groups.add(related)
        }
        groups
    }

    private fun readLong(f: File): Long? = runCatching { f.readText().trim().toLong() }.getOrNull()
    private fun readInt(f: File): Int? = runCatching { f.readText().trim().toInt() }.getOrNull()
}

data class CoreFreq(val index: Int, val curMhz: Int?, val maxMhz: Int?)

data class CpuSample(
    val curKhzMax: Long?,
    val maxKhz: Long?,
    val tempMilliC: Int?,
    val perCore: List<CoreFreq> = emptyList()
) {
    val freqMhz: Int? get() = curKhzMax?.let { (it / 1000L).toInt() }
    val maxMhz: Int? get() = maxKhz?.let { (it / 1000L).toInt() }
    val freqGhzText: String?
        get() = curKhzMax?.let { String.format(Locale.ROOT, "%.2f GHz", it / 1_000_000f) }
    val tempC: Float? get() = tempMilliC?.let { it / 1000f }

    /**
     * Fastest core's current clock divided by the highest max clock (0f..1f).
     * A clock ratio, NOT CPU utilisation: true usage needs /proc/stat, which is
     * unreadable without root on modern Android. Shown as "clock %" in the UI.
     */
    val loadOfMax: Float?
        get() {
            val c = curKhzMax ?: return null
            val m = maxKhz ?: return null
            if (m <= 0) return null
            return (c.toFloat() / m.toFloat()).coerceIn(0f, 1f)
        }
}
