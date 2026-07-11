package com.vauzi.clocklock.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One tick of everything we display: GPU + CPU. */
data class SystemSample(
    val gpu: GpuSample,
    val cpu: CpuSample,
    val timestampMs: Long
)

/**
 * Single polling source combining [GpuMonitor] and [CpuMonitor] so the UI only
 * collects one flow. Max clocks are read once and reused each tick.
 */
object SystemMonitor {

    fun snapshot(gpuMaxHz: Long?, cpuMaxKhz: Long?): SystemSample {
        val now = System.currentTimeMillis()
        return SystemSample(
            gpu = GpuSample(
                freqHz = GpuMonitor.currentFreqHz(),
                maxFreqHz = gpuMaxHz ?: GpuMonitor.maxFreqHz(),
                tempMilliC = GpuMonitor.gpuTempMilliC(),
                timestampMs = now
            ),
            cpu = CpuSample(
                curKhzMax = CpuMonitor.curFreqKhzMax(),
                maxKhz = cpuMaxKhz ?: CpuMonitor.maxFreqKhz(),
                tempMilliC = CpuMonitor.cpuTempMilliC()
            ),
            timestampMs = now
        )
    }

    fun sampleFlow(periodMs: Long = 1000L): Flow<SystemSample> = flow {
        val gpuMax = GpuMonitor.maxFreqHz()
        val cpuMax = CpuMonitor.maxFreqKhz()
        while (true) {
            emit(snapshot(gpuMax, cpuMax))
            delay(periodMs)
        }
    }
}
