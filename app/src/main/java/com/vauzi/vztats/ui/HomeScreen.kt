package com.vauzi.vztats.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauzi.vztats.core.ApplyOutcome
import com.vauzi.vztats.core.CoreFreq
import com.vauzi.vztats.core.CpuMonitor
import com.vauzi.vztats.core.GpuMonitor
import com.vauzi.vztats.core.NativeBridge
import com.vauzi.vztats.core.RamCleaner
import com.vauzi.vztats.core.SystemSample
import com.vauzi.vztats.core.ThermalMonitor
import com.vauzi.vztats.core.TurboManager
import com.vauzi.vztats.core.TurboState
import com.vauzi.vztats.ui.theme.Dimens
import com.vauzi.vztats.ui.theme.TurboAmber
import com.vauzi.vztats.ui.theme.TurboGreen
import com.vauzi.vztats.ui.theme.TurboRed
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    turboState: TurboState,
    sample: SystemSample?,
    history: List<FreqPoint>,
    graphWindow: GraphWindow,
    onGraphWindowChange: (GraphWindow) -> Unit
) {
    val context = LocalContext.current
    val gpu = sample?.gpu
    val cpu = sample?.cpu
    val power = sample?.power
    val clusterLabel = cpu?.perCore?.takeIf { it.isNotEmpty() }?.let { CpuMonitor.clusterLabel(it) }
    // The KGSL description is static for the life of the process; read it once
    // instead of on every 1 s recomposition.
    val gpuDetails = remember { GpuMonitor.readDetails() }
    var showRamCleaner by remember { mutableStateOf(false) }
    if (showRamCleaner) RamCleanerDialog(onDismiss = { showRamCleaner = false })

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
    ) {
        Spacer(Modifier.height(Dimens.SpaceXS))

        // --- GPU (with the lock as one of its controls) --------------------------
        TurboCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("GPU", gpuDetails.model, Modifier.weight(1f))
                LockButton(
                    on = turboState.desiredOn,
                    onToggle = { TurboManager.setTurbo(context, !turboState.desiredOn) }
                )
            }
            Spacer(Modifier.height(Dimens.SpaceM))
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
                    CardStatLine("Clock", mhzOfMax(gpu?.freqMhz, gpu?.maxFreqMhz))
                    CardStatLine("Of max", gpu?.loadOfMax?.let { "${(it * 100).roundToInt()}%" } ?: "—")
                    // Only where the driver exposes a busy counter — no placeholder
                    // otherwise, and never a clock ratio passed off as load.
                    gpu?.busyPct?.let { CardStatLine("Load", "$it%") }
                    CardStatLine(
                        "Temp",
                        gpu?.tempC?.let { "%.0f°C".format(it) } ?: "—",
                        tempAccent(gpu?.tempC)
                    )
                }
            }
            lockStatus(turboState, gpu?.isAtMax == true)?.let { (text, color) ->
                Spacer(Modifier.height(Dimens.SpaceS))
                StatusLine(text, color)
            }
        }

        // --- CPU -------------------------------------------------------------------
        TurboCard {
            CardTitle(
                "CPU",
                if (clusterLabel.isNullOrEmpty()) "${CpuMonitor.coreCount} cores"
                else "${CpuMonitor.coreCount} cores · $clusterLabel"
            )
            Spacer(Modifier.height(Dimens.SpaceM))
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
                    CardStatLine("Max", cpu?.maxMhz?.let { "%.2f GHz".format(it / 1000f) } ?: "—")
                    CardStatLine("Clock %", cpu?.loadOfMax?.let { "${(it * 100).roundToInt()}%" } ?: "—")
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
            Footnote("Clock % = fastest core ÷ max clock. Not CPU utilisation.")
        }

        // --- Memory & battery --------------------------------------------------
        TurboCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ramPct = power?.ramUsedPct
                RingStat(
                    fraction = (ramPct ?: 0) / 100f,
                    centerValue = ramPct?.toString() ?: "—",
                    subLabel = "% RAM",
                    modifier = Modifier.size(84.dp)
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = Dimens.SpaceL),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
                ) {
                    Text("Memory", style = MaterialTheme.typography.titleSmall)
                    CardStatLine(
                        "Used",
                        if (power?.ramUsedGbText != null && power.ramTotalGbText != null)
                            "${power.ramUsedGbText} / ${power.ramTotalGbText} GB" else "—"
                    )
                    CardStatLine(
                        "Available",
                        power?.ramAvailMb?.let { "%.1f GB".format(Locale.ROOT, it / 1024f) } ?: "—"
                    )
                }
            }

            // Re-read on each tick; it's an in-memory SharedPreferences lookup.
            val blockedCount = remember(sample?.timestampMs, showRamCleaner) {
                RamCleaner.blocked(context).size
            }
            NavRow(
                title = "RAM cleaner",
                subtitle = if (blockedCount > 0)
                    "$blockedCount apps blocked from background — open to restore"
                else "Measure per-app memory, then clean and block",
                actionLabel = "Open",
                onClick = { showRamCleaner = true }
            )

            HorizontalDivider(
                Modifier.padding(vertical = Dimens.SpaceM),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle(
                    "Battery",
                    listOfNotNull(power?.status?.label, power?.pluggedSource).joinToString(" · ")
                        .ifEmpty { null },
                    Modifier.weight(1f)
                )
                Text(
                    power?.batteryPct?.let { "$it%" } ?: "—",
                    fontFamily = NumberFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(Dimens.SpaceS))
            // Only values the platform actually reported; nothing is estimated.
            MiniStatGrid(
                listOfNotNull(
                    power?.batteryTempC?.let { MiniStat("Temp", "%.0f°C".format(it), tempAccent(it)) },
                    power?.currentMa?.let { MiniStat("Current", "$it mA") },
                    power?.voltageMv?.let { MiniStat("Voltage", "%.2f V".format(Locale.ROOT, it / 1000f)) },
                    power?.powerW?.let { MiniStat("Power", "%.2f W".format(Locale.ROOT, it)) }
                )
            )
        }

        // --- Performance: FPS + GPU frequency graph ------------------------------
        TurboCard {
            val windowed = history.inWindow(graphWindow)
            val stats = windowed.stats()

            Row(verticalAlignment = Alignment.CenterVertically) {
                CardTitle("Performance", "GPU clock · KGSL", Modifier.weight(1f))
                Text(
                    "FPS ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    sample?.fps?.toString() ?: "—",
                    fontFamily = NumberFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = fpsAccent(sample?.fps) ?: MaterialTheme.colorScheme.onSurface
                )
            }
            if (sample?.fps == null) {
                Footnote("FPS needs the Shizuku sampler (pair it in Settings).")
            }

            Spacer(Modifier.height(Dimens.SpaceM))
            SegmentedControl(
                options = GraphWindow.entries.map { it.label },
                selectedIndex = graphWindow.ordinal,
                onSelect = { onGraphWindowChange(GraphWindow.entries[it]) }
            )
            Spacer(Modifier.height(Dimens.SpaceM))
            MiniStatGrid(
                listOf(
                    MiniStat("Now", stats?.let { "${it.nowMhz} MHz" } ?: "—"),
                    MiniStat("Average", stats?.let { "${it.avgMhz} MHz" } ?: "—"),
                    MiniStat("Peak", stats?.let { "${it.peakMhz} MHz" } ?: "—")
                )
            )
            FrequencyChart(
                history = windowed.map { it.mhz },
                maxMhz = gpu?.maxFreqMhz,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = Dimens.SpaceM)
            )
            Footnote(
                if (stats != null && stats.spanSec + 1 < graphWindow.ms / 1000L)
                    "Last ${graphWindow.longLabel} — ${stats.spanSec} s collected so far. Dashed line = max clock."
                else "Last ${graphWindow.longLabel}. Dashed line = max clock."
            )
        }

        // --- Temperature sensors --------------------------------------------------
        if (ThermalMonitor.isSupported) {
            var sensorsExpanded by remember { mutableStateOf(false) }
            // Re-read on each sample tick so the list stays live while open.
            val zones = remember(sample?.timestampMs, sensorsExpanded) {
                if (sensorsExpanded) ThermalMonitor.readAll() else emptyList()
            }

            SectionHeader("Temperature sensors")
            NavRow(
                title = "All thermal zones",
                subtitle = "Every sensor the kernel exposes, hottest first",
                actionLabel = if (sensorsExpanded) "Hide" else "Show",
                onClick = { sensorsExpanded = !sensorsExpanded }
            )
            if (sensorsExpanded) {
                TurboCard {
                    if (zones.isEmpty()) {
                        Text(
                            "No readable thermal zones on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        zones.forEach { z ->
                            InfoRow(
                                z.label,
                                "%.1f°C".format(z.tempC),
                                valueColor = tempAccent(z.tempC)
                            )
                        }
                    }
                }
            }
        }

        // --- Device info ---------------------------------------------------------
        SectionHeader("Device info")
        val fullySupported = NativeBridge.available && GpuMonitor.isSupported
        if (!fullySupported) {
            BannerCard(
                icon = Icons.Filled.WarningAmber,
                title = "Some interfaces missing",
                subtitle = "GPU lock may not work reliably on this device.",
                tone = TurboRed
            )
        }
        TurboCard {
            val details = gpuDetails
            // observedMaxMhz in the cached snapshot is stale; ask for the live one.
            val gpuMax = details.ceilingMhz ?: GpuMonitor.observedMaxMhz
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

/** Card heading with an optional muted qualifier, e.g. "GPU  Adreno 740". */
@Composable
private fun CardTitle(title: String, qualifier: String?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (!qualifier.isNullOrEmpty()) {
            Text(
                "  $qualifier",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Dimens.SpaceS)
    )
}

/** Small coloured dot + text — the lock's state, sized as a detail of the GPU card. */
@Composable
private fun StatusLine(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(Dimens.SpaceS))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

private data class MiniStat(val label: String, val value: String, val accent: Color? = null)

/** Label-over-value stats laid out three per row — denser than a card per metric. */
@Composable
private fun MiniStatGrid(items: List<MiniStat>) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { item ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            item.value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = NumberFont,
                            fontWeight = FontWeight.Medium,
                            color = item.accent ?: MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                }
                // Keep columns aligned when the last row is short.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun mhzOfMax(cur: Int?, max: Int?): String = when {
    cur == null && max == null -> "—"
    max == null -> "$cur MHz"
    else -> "${cur ?: "—"} / $max MHz"
}

/** Compact GPU Lock toggle, sized to sit in the GPU card's header. */
@Composable
private fun LockButton(on: Boolean, onToggle: () -> Unit) {
    val bg by animateColorAsState(
        if (on) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        label = "btn-bg"
    )
    val fg = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(Dimens.RadiusL))
            .background(bg)
            .clickable { onToggle() }
            .padding(horizontal = Dimens.SpaceM - 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.PowerSettingsNew,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = if (on) "Lock on" else "Lock off",
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
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

/**
 * GPU Lock status line, or null while the lock is off (the button already says
 * so). Same states and wording as before; "verified" still means the live clock
 * reached the reported maximum.
 */
private fun lockStatus(state: TurboState, atMax: Boolean): Pair<String, Color>? {
    if (!state.desiredOn) return null
    return when (state.outcome) {
        ApplyOutcome.UNSUPPORTED -> "Lock: not supported" to TurboRed
        ApplyOutcome.FAILED -> "Lock: kernel rejected" to TurboRed
        ApplyOutcome.APPLIED, ApplyOutcome.NONE ->
            if (atMax) "Lock: active — verified at max clock" to TurboGreen
            else "Lock: applied — waiting for max clock" to TurboAmber
    }
}
