package com.fartopblu.adrenoturbomode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fartopblu.adrenoturbomode.core.GpuSample
import com.fartopblu.adrenoturbomode.ui.theme.TurboAmber
import com.fartopblu.adrenoturbomode.ui.theme.TurboGreen
import com.fartopblu.adrenoturbomode.ui.theme.TurboRed

@Composable
fun MonitorScreen(
    modifier: Modifier = Modifier,
    sample: GpuSample?,
    history: List<Int>
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Live GPU monitor",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Values read directly from /sys/class/kgsl and the kernel thermal zones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Frequency (MHz) — last ${history.size}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FrequencyChart(
                    history = history,
                    maxMhz = sample?.maxFreqMhz,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .padding(top = 12.dp)
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Current",
                value = sample?.freqMhz?.let { "$it MHz" } ?: "—",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Maximum",
                value = sample?.maxFreqMhz?.let { "$it MHz" } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val loadPct = sample?.loadOfMax?.let { (it * 100).toInt() }
            StatTile(
                label = "% of max",
                value = loadPct?.let { "$it%" } ?: "—",
                modifier = Modifier.weight(1f),
                accent = when {
                    sample?.isAtMax == true -> TurboGreen
                    loadPct != null -> TurboAmber
                    else -> null
                }
            )
            val tempC = sample?.tempC
            StatTile(
                label = "GPU temp",
                value = tempC?.let { "%.1f °C".format(it) } ?: "—",
                modifier = Modifier.weight(1f),
                accent = when {
                    tempC == null -> null
                    tempC >= 50f -> TurboRed
                    tempC >= 44f -> TurboAmber
                    else -> TurboGreen
                }
            )
        }

        if (sample?.isAtMax == true) {
            Text(
                "✓ Clock is sitting at the reported maximum — turbo is working.",
                style = MaterialTheme.typography.bodySmall,
                color = TurboGreen
            )
        }
    }
}
