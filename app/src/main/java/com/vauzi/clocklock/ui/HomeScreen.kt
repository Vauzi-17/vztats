package com.vauzi.clocklock.ui

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauzi.clocklock.core.ApplyOutcome
import com.vauzi.clocklock.core.CpuMonitor
import com.vauzi.clocklock.core.GpuDetails
import com.vauzi.clocklock.core.GpuMonitor
import com.vauzi.clocklock.core.NativeBridge
import com.vauzi.clocklock.core.SessionRecorder
import com.vauzi.clocklock.core.SessionStore
import com.vauzi.clocklock.core.SessionSummary
import com.vauzi.clocklock.core.SystemSample
import com.vauzi.clocklock.core.TurboManager
import com.vauzi.clocklock.core.TurboState
import com.vauzi.clocklock.service.TurboService
import com.vauzi.clocklock.ui.theme.Dimens
import com.vauzi.clocklock.ui.theme.TurboAmber
import com.vauzi.clocklock.ui.theme.TurboGreen
import com.vauzi.clocklock.ui.theme.TurboRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
                    fraction = (cpu?.usagePct ?: 0) / 100f,
                    centerValue = cpu?.usagePct?.toString() ?: "—",
                    subLabel = "%",
                    modifier = Modifier.size(84.dp)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = Dimens.SpaceL),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
                ) {
                    Text("CPU", style = MaterialTheme.typography.titleSmall)
                    CardStatLine("Clock", cpu?.freqMhz?.let { "%.2f GHz".format(it / 1000f) } ?: "—")
                    CardStatLine("Max", cpu?.maxMhz?.let { "%.2f GHz".format(it / 1000f) } ?: "—")
                    CardStatLine(
                        "Temp",
                        cpu?.tempC?.let { "%.0f°C".format(it) } ?: "—",
                        tempAccent(cpu?.tempC)
                    )
                }
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

        // --- Session recorder ---------------------------------------------------
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
            Text(if (recording) "Stop & save" else "Record session")
        }
        if (recording) {
            Text(
                "Recording… keep the game in the foreground.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val sessions = remember(recording, refreshTick) { SessionStore.list(context) }
        if (sessions.isNotEmpty()) {
            sessions.take(8).forEach { s ->
                SessionRow(s) {
                    SessionStore.delete(context, s.id)
                    refreshTick++
                }
            }
        }

        // --- Hardware & compatibility (collapsible) ------------------------------
        var detailsExpanded by remember { mutableStateOf(false) }
        SectionHeader("Hardware & compatibility")
        NavRow(
            title = "GPU hardware details",
            subtitle = "Compatibility checks, frequency table, governor info",
            actionLabel = if (detailsExpanded) "Hide" else "Show",
            onClick = { detailsExpanded = !detailsExpanded }
        )
        if (detailsExpanded) {
            val details = remember { GpuMonitor.readDetails() }
            val effectiveMax = gpu?.maxFreqMhz ?: details.observedMaxMhz
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
                IconListRow(
                    icon = if (NativeBridge.available) Icons.Filled.Check else Icons.Filled.Close,
                    iconTint = if (NativeBridge.available) TurboGreen else TurboRed,
                    title = "Native turbo library"
                )
                IconListRow(
                    icon = if (GpuMonitor.isSupported) Icons.Filled.Check else Icons.Filled.Close,
                    iconTint = if (GpuMonitor.isSupported) TurboGreen else TurboRed,
                    title = "GPU frequency readable"
                )
                IconListRow(
                    icon = if (CpuMonitor.isSupported) Icons.Filled.Check else Icons.Filled.Close,
                    iconTint = if (CpuMonitor.isSupported) TurboGreen else TurboRed,
                    title = "CPU frequency readable"
                )
            }
            TurboCard { GpuDetailsBody(details, effectiveMax) }
        }

        Spacer(Modifier.height(Dimens.NavBarClearance))
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
            text = if (on) "TURBO ON" else "TURBO OFF",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SessionRow(s: SessionSummary, onDelete: () -> Unit) {
    TurboCard(padding = Dimens.SpaceM, radius = Dimens.RadiusM) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault()).format(Date(s.startedAtMs)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${s.durationSec}s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            modifier = Modifier.padding(top = Dimens.SpaceXS)
        )
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
            "This device blocks reading the static KGSL table. " +
                    "The observed max above is read live from gpuclk.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceM)
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
                modifier = Modifier.padding(top = Dimens.SpaceM, bottom = Dimens.SpaceS)
            )
            FreqTable(d.availableFreqsMhz, top = d.ceilingMhz)
        }
    }

    Text(
        verdictFor(d, effectiveMax),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Dimens.SpaceM)
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
                    .clip(RoundedCornerShape(Dimens.RadiusS - 4.dp))
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
    val kernelNote = "True overclock requires a custom kernel."
    return when {
        d.staticTableBlocked && effectiveMax != null ->
            "Observed ceiling so far is $effectiveMax MHz — turbo already targets it. $kernelNote"
        d.staticTableBlocked ->
            "Turn on turbo or run a game so the GPU reaches its peak. $kernelNote"
        d.cappedBelowCeiling ->
            "Active max clamp (${d.maxClampMhz} MHz) sits below the ${d.ceilingMhz} MHz ceiling. $kernelNote"
        d.ceilingMhz != null ->
            "Turbo targets the top bin (${d.ceilingMhz} MHz). $kernelNote"
        else -> "Couldn’t read the frequency table on this device."
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
