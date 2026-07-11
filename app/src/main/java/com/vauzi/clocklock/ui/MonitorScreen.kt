package com.vauzi.clocklock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauzi.clocklock.core.SessionRecorder
import com.vauzi.clocklock.core.SessionStore
import com.vauzi.clocklock.core.SessionSummary
import com.vauzi.clocklock.core.SystemSample
import com.vauzi.clocklock.service.TurboService
import com.vauzi.clocklock.ui.theme.TurboAmber
import com.vauzi.clocklock.ui.theme.TurboGreen
import com.vauzi.clocklock.ui.theme.TurboRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(
    modifier: Modifier = Modifier,
    sample: SystemSample?,
    history: List<Int>
) {
    val gpu = sample?.gpu
    val cpu = sample?.cpu

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Values read directly from /sys/class/kgsl, the CPU cpufreq nodes and " +
                "the kernel thermal zones — nothing is estimated.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // GPU frequency chart
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(16.dp)
        ) {
            Text(
                "GPU frequency — last ${history.size}s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FrequencyChart(
                history = history,
                maxMhz = gpu?.maxFreqMhz,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(top = 12.dp)
            )
            if (gpu?.isAtMax == true) {
                Text(
                    "✓ Clock sitting at the reported maximum — turbo is working.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TurboGreen,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        SectionHeader("GPU")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "Clock",
                value = gpu?.freqMhz?.toString() ?: "—",
                unit = "MHz",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Max",
                value = gpu?.maxFreqMhz?.toString() ?: "—",
                unit = "MHz",
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val loadPct = gpu?.loadOfMax?.let { (it * 100).toInt() }
            MetricTile(
                label = "% of max",
                value = loadPct?.toString() ?: "—",
                unit = "%",
                accent = if (gpu?.isAtMax == true) TurboGreen else null,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Temp",
                value = gpu?.tempC?.let { "%.0f".format(it) } ?: "—",
                unit = "°C",
                accent = tempAccentM(gpu?.tempC),
                modifier = Modifier.weight(1f)
            )
        }

        SectionHeader("CPU")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "Clock",
                value = cpu?.freqMhz?.let { "%.2f".format(it / 1000f) } ?: "—",
                unit = "GHz",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Max",
                value = cpu?.maxMhz?.let { "%.2f".format(it / 1000f) } ?: "—",
                unit = "GHz",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Temp",
                value = cpu?.tempC?.let { "%.0f".format(it) } ?: "—",
                unit = "°C",
                accent = tempAccentM(cpu?.tempC),
                modifier = Modifier.weight(1f)
            )
        }
        if (cpu?.freqMhz == null) {
            Text(
                "CPU clock unavailable — some devices block reading it even without root.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val power = sample?.power
        SectionHeader("Power & memory")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "Draw",
                value = power?.currentMa?.toString() ?: "—",
                unit = "mA",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Battery",
                value = power?.batteryTempC?.let { "%.0f".format(it) } ?: "—",
                unit = "°C",
                accent = tempAccentM(power?.batteryTempC),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "RAM",
                value = power?.ramUsedGbText ?: "—",
                unit = power?.ramTotalGbText?.let { "/ $it GB" } ?: "GB",
                modifier = Modifier.weight(1f)
            )
        }

        SectionHeader("Frame rate")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "FPS",
                value = sample?.fps?.toString() ?: "—",
                unit = "fps",
                accent = fpsAccent(sample?.fps),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = if (power?.charging == true) "Battery (chg)" else "Battery",
                value = power?.batteryPct?.toString() ?: "—",
                unit = "%",
                modifier = Modifier.weight(1f)
            )
        }
        if (sample?.fps == null) {
            Text(
                "FPS needs the Shizuku sampler (enable it in Settings). Without it, real " +
                    "per-game FPS can't be read.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // --- Session recorder ------------------------------------------------
        val context = LocalContext.current
        val recording by SessionRecorder.recording.collectAsStateWithLifecycle()
        var refreshTick by remember { mutableIntStateOf(0) }

        SectionHeader("Session recorder")
        Button(
            onClick = {
                if (recording) {
                    SessionRecorder.stop(context)
                    refreshTick++
                } else {
                    SessionRecorder.start()
                    TurboService.sync(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (recording) "Stop & save session" else "Record session")
        }
        Text(
            if (recording) "Recording… keep the game in the foreground."
            else "Logs GPU/CPU/battery (and FPS when Shizuku is on) into a saved summary.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val sessions = remember(recording, refreshTick) { SessionStore.list(context) }
        if (sessions.isEmpty()) {
            Text(
                "No saved sessions yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            sessions.take(12).forEach { s ->
                SessionRow(s) {
                    SessionStore.delete(context, s.id)
                    refreshTick++
                }
            }
        }
    }
}

@Composable
private fun SessionRow(s: SessionSummary, onDelete: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault()).format(Date(s.startedAtMs)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text("${s.durationSec}s", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "  ✕",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onDelete() }
            )
        }
        Text(
            buildString {
                s.gpuMaxMhz?.let { append("GPU max ${it}MHz  ") }
                s.gpuMaxTempC?.let { append("GPU ${it.toInt()}°  ") }
                s.cpuMaxTempC?.let { append("CPU ${it.toInt()}°  ") }
                s.avgFps?.let { append("avg ${it}fps  ") }
                s.minFps?.let { append("min ${it}fps  ") }
                s.throttlePct?.let { append("throttle ${it}%") }
            }.ifBlank { "No metrics captured." },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

private fun fpsAccent(fps: Int?) = when {
    fps == null -> null
    fps >= 55 -> TurboGreen
    fps >= 40 -> TurboAmber
    else -> TurboRed
}

private fun tempAccentM(tempC: Float?) = when {
    tempC == null -> null
    tempC >= 50f -> TurboRed
    tempC >= 44f -> TurboAmber
    else -> TurboGreen
}
