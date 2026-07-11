package com.vauzi.clocklock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauzi.clocklock.R
import com.vauzi.clocklock.core.CpuMonitor
import com.vauzi.clocklock.core.GpuDetails
import com.vauzi.clocklock.core.GpuMonitor
import com.vauzi.clocklock.core.NativeBridge
import com.vauzi.clocklock.ui.theme.TurboAmber
import com.vauzi.clocklock.ui.theme.TurboGreen
import com.vauzi.clocklock.ui.theme.TurboRed

@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val details = remember { GpuMonitor.readDetails() }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Compatibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card {
            CompatRow("Native turbo library", NativeBridge.available)
            CompatRow("GPU frequency readable", GpuMonitor.isSupported)
            CompatRow("CPU frequency readable", CpuMonitor.isSupported)
            Text(
                if (NativeBridge.available && GpuMonitor.isSupported)
                    "This device exposes the Adreno interfaces turbo relies on."
                else
                    "Some interfaces are missing — turbo may not work here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Text("GPU hardware", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        GpuDetailsCard(details)

        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card {
            Text(
                text = context.getString(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun GpuDetailsCard(d: GpuDetails) {
    Card {
        InfoRow("Model", d.model ?: "—")
        InfoRow(
            "Ceiling (top bin)",
            d.ceilingMhz?.let { "$it MHz" } ?: "—",
            valueColor = MaterialTheme.colorScheme.primary
        )
        if (d.cappedBelowCeiling) {
            InfoRow("Active max clamp", "${d.maxClampMhz} MHz", valueColor = TurboAmber)
        }
        InfoRow("Governor", d.governor ?: "—")
        InfoRow("Power levels", d.numPwrLevels?.toString() ?: "—")
        InfoRow("Thermal-limited level", d.thermalPwrLevel?.toString() ?: "—")

        if (d.availableFreqsMhz.isNotEmpty()) {
            Text(
                "Frequency table (MHz)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
            )
            FreqTable(d.availableFreqsMhz, top = d.ceilingMhz)
        }

        Text(
            verdictFor(d),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreqTable(freqs: List<Int>, top: Int?) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        freqs.forEach { mhz ->
            val isTop = mhz == top
            Text(
                text = mhz.toString(),
                fontFamily = NumberFont,
                fontSize = 12.sp,
                color = if (isTop) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isTop) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
}

private fun verdictFor(d: GpuDetails): String {
    val ceiling = d.ceilingMhz ?: return "Couldn't read the frequency table on this device."
    val base = "Going above $ceiling MHz (true overclock) requires a custom kernel — it can't be " +
        "done from userspace or with root alone."
    return if (d.cappedBelowCeiling)
        "The active max clamp (${d.maxClampMhz} MHz) sits below the $ceiling MHz ceiling — a " +
            "vendor/thermal cap. Lifting it to the ceiling would need root, and it still can't " +
            "exceed $ceiling MHz. $base"
    else
        "Turbo already targets the top bin ($ceiling MHz) — the ceiling. $base"
}

@Composable
private fun Card(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun CompatRow(label: String, ok: Boolean) {
    Text(
        text = (if (ok) "✓  " else "✗  ") + label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (ok) TurboGreen else TurboRed,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}
