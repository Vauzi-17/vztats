package com.vauzi.clocklock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vauzi.clocklock.core.SystemSample
import com.vauzi.clocklock.ui.theme.TurboAmber
import com.vauzi.clocklock.ui.theme.TurboGreen
import com.vauzi.clocklock.ui.theme.TurboRed

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
    }
}

private fun tempAccentM(tempC: Float?) = when {
    tempC == null -> null
    tempC >= 50f -> TurboRed
    tempC >= 44f -> TurboAmber
    else -> TurboGreen
}
