package com.vauzi.vztats.core

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One tick of everything we display: GPU + CPU + power + (optionally) FPS. */
data class SystemSample(
    val gpu: GpuSample,
    val cpu: CpuSample,
    val power: PowerSample,
    val fps: Int?,
    val timestampMs: Long
)

/**
 * Single polling source combining GPU, CPU and power so the UI collects one
 * flow. FPS is supplied separately (Shizuku) and merged in when available.
 */
object SystemMonitor {

    fun snapshot(context: Context, gpuMaxHz: Long?, cpuMaxKhz: Long?, fps: Int?): SystemSample {
        val now = System.currentTimeMillis()
        return SystemSample(
            gpu = GpuSample(
                freqHz = GpuMonitor.currentFreqHz(),
                maxFreqHz = gpuMaxHz ?: GpuMonitor.maxFreqHz(),
                tempMilliC = GpuMonitor.gpuTempMilliC(),
                timestampMs = now,
                busyPct = GpuMonitor.busyPercent()
            ),
            cpu = CpuSample(
                curKhzMax = CpuMonitor.curFreqKhzMax(),
                maxKhz = cpuMaxKhz ?: CpuMonitor.maxFreqKhz(),
                tempMilliC = CpuMonitor.cpuTempMilliC(),
                perCore = CpuMonitor.perCore()
            ),
            power = PowerMonitor.read(context),
            fps = fps,
            timestampMs = now
        )
    }

    fun sampleFlow(context: Context, periodMs: Long = 1000L): Flow<SystemSample> = flow {
        val gpuMax = GpuMonitor.maxFreqHz()
        val cpuMax = CpuMonitor.maxFreqKhz()
        while (true) {
            emit(snapshot(context, gpuMax, cpuMax, FpsProbe.currentFps))
            delay(periodMs)
        }
    }
}
