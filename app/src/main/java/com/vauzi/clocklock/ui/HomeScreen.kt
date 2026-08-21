package com.vauzi.clocklock.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauzi.clocklock.core.ApplyOutcome
import com.vauzi.clocklock.core.CoreFreq
import com.vauzi.clocklock.core.CpuMonitor
import com.vauzi.clocklock.core.GpuMonitor
import com.vauzi.clocklock.core.NativeBridge
import com.vauzi.clocklock.core.SystemSample
import com.vauzi.clocklock.core.TurboManager
import com.vauzi.clocklock.core.TurboState
import com.vauzi.clocklock.ui.theme.Dimens
import com.vauzi.clocklock.ui.theme.TurboAmber
import com.vauzi.clocklock.ui.theme.TurboGreen
import com.vauzi.clocklock.ui.theme.TurboRed

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    turboState: TurboState,
    sample: SystemSample?,
    history: List<Int>
) {
    val context = LocalContext.current
    val gpu = sample?.gpu
    val cpu = sample?.cpu
    val power = sample?.power
    val clusterLabel = cpu?.perCore?.takeIf { it.isNotEmpty() }?.let { CpuMonitor.clusterLabel(it) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
    ) {
        Spacer(Modifier.height(Dimens.SpaceXS))

        // --- GPU & turbo ---------------------------------------------------------
        TurboCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingStat(
                    fraction = gpu?.loadOfMax ?: 0f,
                    centerValue = gpu?.freqMhz?.toString() ?: "—",
                    subLabel = "MHz",
                    active = turboState.desiredOn,
                    modifier = Modifier.size(84.dp)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = Dimens.SpaceL),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
                ) {
                    val (pillText, pillColor) = statusFor(turboState, gpu?.isAtMax == true)
                    StatusPill(pillText, pillColor)
                    CardStatLine("Max", gpu?.maxFreqMhz?.let { "$it MHz" } ?: "—")
                    CardStatLine(
                        "Temp",
                        gpu?.tempC?.let { "%.0f°C".format(it) } ?: "—",
                        tempAccent(gpu?.tempC)
                    )
                    Spacer(Modifier.height(Dimens.SpaceXS))
                    PowerButton(
                        on = turboState.desiredOn,
                        onToggle = { TurboManager.setTurbo(context, !turboState.desiredOn) }
                    )
                }
            }
        }

        // --- CPU -------------------------------------------------------------------
        TurboCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingStat(
                    fraction = cpu?.loadOfMax ?: 0f,
                    centerValue = cpu?.freqMhz?.toString() ?: "—",
                    subLabel = "MHz",
                    modifier = Modifier.size(84.dp)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = Dimens.SpaceL),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
                ) {
                    Text(
                        if (clusterLabel.isNullOrEmpty()) "CPU" else "CPU ($clusterLabel)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    CardStatLine("Max", cpu?.maxMhz?.let { "%.2f GHz".format(it / 1000f) } ?: "—")
                    CardStatLine(
                        "Temp",
                        cpu?.tempC?.let { "%.0f°C".format(it) } ?: "—",
                        tempAccent(cpu?.tempC)
                    )
                }
            }
            if (!cpu?.perCore.isNullOrEmpty()) {
                Spacer(Modifier.height(Dimens.SpaceM))
                CoreGrid(cpu!!.perCore)
            }
        }

        // --- Memory & battery --------------------------------------------------
        TurboCard {
            val ramPct = power?.ramUsedMb?.let { used ->
                power.ramTotalMb?.takeIf { it > 0 }?.let { total -> used.toFloat() / total.toFloat() }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingStat(
                    fraction = ramPct ?: 0f,
                    centerValue = ramPct?.let { (it * 100).toInt().toString() } ?: "—",
                    subLabel = "%",
                    modifier = Modifier.size(84.dp)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = Dimens.SpaceL),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
                ) {
                    Text("Memory & battery", style = MaterialTheme.typography.titleSmall)
                    CardStatLine(
                        "RAM",
                        if (power?.ramUsedGbText != null && power.ramTotalGbText != null)
                            "${power.ramUsedGbText} / ${power.ramTotalGbText} GB" else "—"
                    )
                    CardStatLine(
                        if (power?.charging == true) "Charging" else "Battery",
                        power?.batteryPct?.let { "$it%" } ?: "—"
                    )
                    CardStatLine(
                        "Batt temp",
                        power?.batteryTempC?.let { "%.0f°C".format(it) } ?: "—",
                        tempAccent(power?.batteryTempC)
                    )
                }
            }
        }

        // --- GPU frequency chart ------------------------------------------------
        TurboCard {
            Text(
                "GPU frequency",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Last ${history.size}s — read from /sys/class/kgsl",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpaceXS)
            )
            FrequencyChart(
                history = history,
                maxMhz = gpu?.maxFreqMhz,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = Dimens.SpaceM)
            )
            if (gpu?.isAtMax == true) {
                Text(
                    "Clock at reported maximum — turbo working.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TurboGreen,
                    modifier = Modifier.padding(top = Dimens.SpaceS)
                )
            }
        }

        // --- Frame rate --------------------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            MetricTile(
                label = "FPS",
                value = sample?.fps?.toString() ?: "—",
                unit = "fps",
                accent = fpsAccent(sample?.fps),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = if (power?.charging == true) "Charging" else "Battery",
                value = power?.batteryPct?.toString() ?: "—",
                unit = "%",
                modifier = Modifier.weight(1f)
            )
        }
        if (sample?.fps == null) {
            Text(
                "FPS needs the Shizuku sampler (pair it in Settings).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.SpaceXS)
            )
        }

        // --- Device info ---------------------------------------------------------
        SectionHeader("Device info")
        val fullySupported = NativeBridge.available && GpuMonitor.isSupported
        if (!fullySupported) {
            BannerCard(
                icon = Icons.Filled.WarningAmber,
                title = "Some interfaces missing",
                subtitle = "Turbo may not work reliably on this device.",
                tone = TurboRed
            )
        }
        TurboCard {
            val details = GpuMonitor.readDetails()
            val gpuMax = details.ceilingMhz ?: details.observedMaxMhz
            InfoRow("GPU model", details.model ?: "—")
            InfoRow("GPU max clock", gpuMax?.let { "$it MHz" } ?: "—")
            InfoRow("GPU governor", details.governor ?: "—")
            InfoRow(
                "CPU cores",
                if (clusterLabel.isNullOrEmpty()) "${CpuMonitor.coreCount}"
                else "${CpuMonitor.coreCount} ($clusterLabel)"
            )
            InfoRow(
                "CPU max clock",
                cpu?.perCore?.mapNotNull { it.maxMhz }?.maxOrNull()?.let { "$it MHz" } ?: "—"
            )
            InfoRow("Android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            InfoRow("Kernel", System.getProperty("os.version") ?: "—")
            InfoRow("Device", "${Build.MANUFACTURER} ${Build.MODEL}")
        }

        Spacer(Modifier.height(Dimens.NavBarClearance))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoreGrid(cores: List<CoreFreq>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        modifier = Modifier.fillMaxWidth()
    ) {
        cores.forEach { c -> CoreChip(c) }
    }
}

@Composable
private fun CoreChip(c: CoreFreq) {
    Column(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusS))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Dimens.SpaceS, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "C${c.index}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            c.curMhz?.toString() ?: "—",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = NumberFont,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CardStatLine(label: String, value: String, accent: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = NumberFont,
            fontWeight = FontWeight.Medium,
            color = accent ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun PowerButton(on: Boolean, onToggle: () -> Unit) {
    val bg by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceVariant,
        label = "btn-bg"
    )
    val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusL))
            .background(bg)
            .clickable { onToggle() }
            .padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.PowerSettingsNew,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (on) "LOCK ON" else "LOCK OFF",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

private fun tempAccent(tempC: Float?): Color? = when {
    tempC == null -> null
    tempC >= 50f -> TurboRed
    tempC >= 44f -> TurboAmber
    else -> TurboGreen
}

private fun fpsAccent(fps: Int?): Color? = when {
    fps == null -> null
    fps >= 55 -> TurboGreen
    fps >= 40 -> TurboAmber
    else -> TurboRed
}

private fun statusFor(state: TurboState, atMax: Boolean): Pair<String, Color> {
    if (!state.desiredOn) return "Turbo off" to TurboRed.copy(alpha = 0.5f)
    return when (state.outcome) {
        ApplyOutcome.UNSUPPORTED -> "Not supported" to TurboRed
        ApplyOutcome.FAILED -> "Kernel rejected" to TurboRed
        ApplyOutcome.APPLIED, ApplyOutcome.NONE ->
            if (atMax) "Active — verified" to TurboGreen
            else "Applied — waiting" to TurboAmber
    }
}
