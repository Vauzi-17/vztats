package com.vauzi.clocklock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.vauzi.clocklock.core.SystemSample
import com.vauzi.clocklock.ui.theme.TurboAmber
import com.vauzi.clocklock.ui.theme.TurboGreen
import com.vauzi.clocklock.ui.theme.TurboRed

@Composable
fun InfoScreen(modifier: Modifier = Modifier, sample: SystemSample? = null) {
    val context = LocalContext.current
    val details = remember { GpuMonitor.readDetails() }
    val effectiveMax = sample?.gpu?.maxFreqMhz ?: details.observedMaxMhz

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Compatibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        InfoCard {
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
        InfoCard { GpuDetailsBody(details, effectiveMax) }

        Text("About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        InfoCard {
            Text(
                text = context.getString(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun GpuDetailsBody(d: GpuDetails, effectiveMax: Int?) {
    InfoRow("Model", d.model ?: "—")

    if (d.staticTableBlocked) {
        InfoRow(
            "Observed max (live)",
            effectiveMax?.let { "$it MHz" } ?: "—",
            valueColor = MaterialTheme.colorScheme.primary
        )
        Text(
            "This device blocks apps from reading the static KGSL table (root-only), so " +
                "model/bins/levels show \"—\". The observed max above is read live from gpuclk — " +
                "turn on turbo (or run a game) so the GPU hits its peak, and that value is your " +
                "real ceiling.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
    } else {
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
    }

    Text(
        verdictFor(d, effectiveMax),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp)
    )
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

private fun verdictFor(d: GpuDetails, effectiveMax: Int?): String {
    val kernelNote = "Going above the ceiling (true overclock) requires a custom kernel — it " +
        "can't be done from userspace or with root alone."
    return when {
        d.staticTableBlocked && effectiveMax != null ->
            "Observed ceiling so far is $effectiveMax MHz — turbo already targets it. $kernelNote"
        d.staticTableBlocked ->
            "Turn on turbo or run a game so the GPU reaches its peak; the observed max becomes " +
                "your ceiling. $kernelNote"
        d.cappedBelowCeiling ->
            "The active max clamp (${d.maxClampMhz} MHz) sits below the ${d.ceilingMhz} MHz " +
                "ceiling — a vendor/thermal cap. Lifting it needs root and still can't exceed " +
                "${d.ceilingMhz} MHz. $kernelNote"
        d.ceilingMhz != null ->
            "Turbo already targets the top bin (${d.ceilingMhz} MHz) — the ceiling. $kernelNote"
        else -> "Couldn't read the frequency table on this device."
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
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
