package com.vauzi.vztats.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Aggregated summary of one recorded gaming session. */
data class SessionSummary(
    val id: String,
    val startedAtMs: Long,
    val durationSec: Int,
    val gpuMaxMhz: Int?,
    val gpuAvgMhz: Int?,
    val gpuMaxTempC: Float?,
    val cpuMaxTempC: Float?,
    val batteryMaxTempC: Float?,
    val avgFps: Int?,
    val minFps: Int?,
    val throttlePct: Int?
)

/**
 * Records a session by aggregating samples on the fly (no per-sample storage).
 * Fed by the foreground service loop so it keeps recording while the game is in
 * the foreground and the app UI is in the background.
 */
object SessionRecorder {

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private var startMs = 0L
    private var samples = 0
    private var gpuSum = 0L
    private var gpuMax = 0
    private var gpuMaxTemp = 0f
    private var cpuMaxTemp = 0f
    private var battMaxTemp = 0f
    private var fpsSum = 0L
    private var fpsCount = 0
    private var fpsMin = Int.MAX_VALUE
    private var throttleSamples = 0
    private var maxSeenMhz = 0

    fun start() {
        startMs = System.currentTimeMillis()
        samples = 0; gpuSum = 0; gpuMax = 0
        gpuMaxTemp = 0f; cpuMaxTemp = 0f; battMaxTemp = 0f
        fpsSum = 0; fpsCount = 0; fpsMin = Int.MAX_VALUE
        throttleSamples = 0; maxSeenMhz = 0
        _recording.value = true
    }

    fun feed(s: SystemSample) {
        if (!_recording.value) return
        samples++
        s.gpu.freqMhz?.let {
            gpuSum += it
            if (it > gpuMax) gpuMax = it
            if (it > maxSeenMhz) maxSeenMhz = it
            if (maxSeenMhz > 0 && it < maxSeenMhz * 0.9f) throttleSamples++
        }
        s.gpu.tempC?.let { if (it > gpuMaxTemp) gpuMaxTemp = it }
        s.cpu.tempC?.let { if (it > cpuMaxTemp) cpuMaxTemp = it }
        s.power.batteryTempC?.let { if (it > battMaxTemp) battMaxTemp = it }
        s.fps?.let { fpsSum += it; fpsCount++; if (it < fpsMin) fpsMin = it }
    }

    /** Finalises the session, saves it, and returns the summary (or null). */
    fun stop(context: Context): SessionSummary? {
        if (!_recording.value) return null
        _recording.value = false
        if (samples == 0) return null

        val summary = SessionSummary(
            id = startMs.toString(),
            startedAtMs = startMs,
            durationSec = ((System.currentTimeMillis() - startMs) / 1000L).toInt(),
            gpuMaxMhz = gpuMax.takeIf { it > 0 },
            gpuAvgMhz = if (samples > 0 && gpuSum > 0) (gpuSum / samples).toInt() else null,
            gpuMaxTempC = gpuMaxTemp.takeIf { it > 0f },
            cpuMaxTempC = cpuMaxTemp.takeIf { it > 0f },
            batteryMaxTempC = battMaxTemp.takeIf { it > 0f },
            avgFps = if (fpsCount > 0) (fpsSum / fpsCount).toInt() else null,
            minFps = fpsMin.takeIf { it != Int.MAX_VALUE },
            throttlePct = if (samples > 0) (throttleSamples * 100 / samples) else null
        )
        SessionStore.save(context, summary)
        return summary
    }
}

/** Persists session summaries as small JSON files in the app's private storage. */
object SessionStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "sessions").apply { mkdirs() }

    fun save(context: Context, s: SessionSummary) {
        runCatching {
            File(dir(context), "${s.id}.json").writeText(toJson(s))
        }
    }

    fun list(context: Context): List<SessionSummary> =
        dir(context).listFiles { f -> f.extension == "json" }
            ?.mapNotNull { runCatching { parse(it.readText()) }.getOrNull() }
            ?.sortedByDescending { it.startedAtMs }
            ?: emptyList()

    fun delete(context: Context, id: String) {
        runCatching { File(dir(context), "$id.json").delete() }
    }

    /**
     * Writes every saved session to a CSV in the app's cache and returns it, or
     * null if there is nothing to export. Lives in cache/exports so it can be
     * shared through the FileProvider declared in the manifest.
     */
    fun exportCsv(context: Context): File? {
        val sessions = list(context)
        if (sessions.isEmpty()) return null

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val out = File(File(context.cacheDir, "exports").apply { mkdirs() }, "vztats-sessions-$stamp.csv")
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

        return runCatching {
            out.writeText(buildString {
                appendLine(
                    "started_at,duration_sec,gpu_max_mhz,gpu_avg_mhz,gpu_max_temp_c," +
                        "cpu_max_temp_c,battery_max_temp_c,avg_fps,min_fps,throttle_pct"
                )
                // Oldest first reads more naturally in a spreadsheet.
                sessions.sortedBy { it.startedAtMs }.forEach { s ->
                    appendLine(
                        listOf(
                            iso.format(Date(s.startedAtMs)),
                            s.durationSec.toString(),
                            s.gpuMaxMhz?.toString().orEmpty(),
                            s.gpuAvgMhz?.toString().orEmpty(),
                            s.gpuMaxTempC?.let { "%.1f".format(Locale.ROOT, it) }.orEmpty(),
                            s.cpuMaxTempC?.let { "%.1f".format(Locale.ROOT, it) }.orEmpty(),
                            s.batteryMaxTempC?.let { "%.1f".format(Locale.ROOT, it) }.orEmpty(),
                            s.avgFps?.toString().orEmpty(),
                            s.minFps?.toString().orEmpty(),
                            s.throttlePct?.toString().orEmpty()
                        ).joinToString(",")
                    )
                }
            })
            out
        }.getOrNull()
    }

    private fun toJson(s: SessionSummary): String = JSONObject().apply {
        put("id", s.id)
        put("startedAtMs", s.startedAtMs)
        put("durationSec", s.durationSec)
        putOpt("gpuMaxMhz", s.gpuMaxMhz)
        putOpt("gpuAvgMhz", s.gpuAvgMhz)
        putOpt("gpuMaxTempC", s.gpuMaxTempC?.toDouble())
        putOpt("cpuMaxTempC", s.cpuMaxTempC?.toDouble())
        putOpt("batteryMaxTempC", s.batteryMaxTempC?.toDouble())
        putOpt("avgFps", s.avgFps)
        putOpt("minFps", s.minFps)
        putOpt("throttlePct", s.throttlePct)
    }.toString()

    private fun parse(text: String): SessionSummary {
        val o = JSONObject(text)
        fun optInt(k: String) = if (o.has(k) && !o.isNull(k)) o.getInt(k) else null
        fun optFloat(k: String) = if (o.has(k) && !o.isNull(k)) o.getDouble(k).toFloat() else null
        return SessionSummary(
            id = o.getString("id"),
            startedAtMs = o.getLong("startedAtMs"),
            durationSec = o.getInt("durationSec"),
            gpuMaxMhz = optInt("gpuMaxMhz"),
            gpuAvgMhz = optInt("gpuAvgMhz"),
            gpuMaxTempC = optFloat("gpuMaxTempC"),
            cpuMaxTempC = optFloat("cpuMaxTempC"),
            batteryMaxTempC = optFloat("batteryMaxTempC"),
            avgFps = optInt("avgFps"),
            minFps = optInt("minFps"),
            throttlePct = optInt("throttlePct")
        )
    }
}
