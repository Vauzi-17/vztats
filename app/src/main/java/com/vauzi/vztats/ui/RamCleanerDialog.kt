package com.vauzi.vztats.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vauzi.vztats.core.AppMemory
import com.vauzi.vztats.core.CleanResult
import com.vauzi.vztats.core.MemoryReport
import com.vauzi.vztats.core.Prefs
import com.vauzi.vztats.core.RamCleaner
import com.vauzi.vztats.shizuku.FpsSampler
import com.vauzi.vztats.ui.theme.Dimens
import com.vauzi.vztats.ui.theme.TurboAmber
import com.vauzi.vztats.ui.theme.TurboGreen
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Data-driven RAM cleaner: measure per-app memory, pick apps, stop and block
 * them, see the measured result, and restore them later. All work goes
 * through [RamCleaner]; this is only the presentation.
 */
@Composable
fun RamCleanerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    val scope = rememberCoroutineScope()

    var report by remember { mutableStateOf<MemoryReport?>(null) }
    var result by remember { mutableStateOf<CleanResult?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var blocked by remember { mutableStateOf(RamCleaner.blocked(context)) }
    var target by remember { mutableIntStateOf(prefs.ramTargetPct) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(prefs.ramCleanSelected) } }

    fun rescan() {
        scope.launch {
            busy = "Measuring memory per app…"
            error = null
            val r = RamCleaner.scan(context)
            report = r
            blocked = RamCleaner.blocked(context)
            if (r == null) {
                error = if (!FpsSampler.shizukuReady())
                    "Needs Shizuku. Start Shizuku and pair it in Settings → Shizuku."
                else "Couldn't read per-app memory from dumpsys meminfo on this device."
            }
            busy = null
        }
    }

    LaunchedEffect(Unit) { rescan() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(Dimens.RadiusXL),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.SpaceL),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RAM cleaner",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                busy?.let { BusyRow(it) }
                error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = TurboAmber) }

                result?.let { ResultCard(it) }

                report?.let { r ->
                    // Only picks that are in this scan count — a remembered pick may
                    // have been uninstalled since.
                    val picks = r.apps.map { it.packageName }.filter { it in selected }.toSet()
                    SummaryCard(
                        report = r,
                        selected = picks,
                        target = target,
                        onTargetChange = { target = it; prefs.ramTargetPct = it }
                    )

                    SectionHeader("Apps using memory")
                    if (r.apps.isEmpty()) {
                        Text(
                            "No user apps are holding memory right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    r.apps.forEach { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in selected,
                            onCheckedChange = { on ->
                                if (on) selected.add(app.packageName) else selected.remove(app.packageName)
                                prefs.ramCleanSelected = selected.toSet()
                            }
                        )
                    }

                    Text(
                        if (RamCleaner.blockingSupported)
                            "Picked apps are stopped and then blocked from running in the background " +
                                "until you restore them. Their notifications stop in the meantime."
                        else "This Android version can't block apps from the background, so picked " +
                            "apps are only stopped and may start again on their own.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                        OutlinedButton(
                            onClick = { rescan() },
                            enabled = busy == null,
                            modifier = Modifier.weight(1f)
                        ) { Text("Scan again") }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = "Stopping apps and measuring…"
                                    result = RamCleaner.clean(context, picks, r)
                                    if (result == null) error = "Cleaning needs Shizuku."
                                    busy = null
                                    rescan()
                                }
                            },
                            enabled = busy == null && picks.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (RamCleaner.blockingSupported) "Clean & block (${picks.size})"
                                else "Clean (${picks.size})",
                                maxLines = 1
                            )
                        }
                    }
                }

                if (blocked.isNotEmpty()) {
                    BlockedSection(
                        packages = blocked.keys.sorted(),
                        labelOf = { pkg ->
                            report?.apps?.firstOrNull { it.packageName == pkg }?.label
                                ?: runCatching {
                                    val pm = context.packageManager
                                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                                }.getOrDefault(pkg)
                        },
                        enabled = busy == null,
                        onRestore = { pkgs ->
                            scope.launch {
                                busy = "Restoring…"
                                val n = RamCleaner.restore(context, pkgs)
                                if (n == 0) error = "Restore needs Shizuku running."
                                blocked = RamCleaner.blocked(context)
                                busy = null
                                rescan()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BusyRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(Dimens.SpaceS))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SummaryCard(
    report: MemoryReport,
    selected: Set<String>,
    target: Int,
    onTargetChange: (Int) -> Unit
) {
    val projected = report.projectedMb(selected)
    val targetMb = report.totalMb * target / 100
    TurboCard {
        InfoRow("In use now", "${gb(report.usedMb)} / ${gb(report.totalMb)} GB (${report.usedPct}%)")
        InfoRow(
            "Picked apps free ≈",
            "${(report.usedMb - projected).coerceAtLeast(0)} MB → ≈${report.pct(projected)}%"
        )
        InfoRow(
            "Lowest reachable ≈",
            "${report.pct(report.floorMb)}%",
            valueColor = if (report.pct(report.floorMb) > target) TurboAmber else TurboGreen
        )
        LimitSlider(
            label = "Target: $target% (≈${gb(targetMb)} GB in use)",
            value = target.toFloat(),
            range = 20f..70f,
            onChange = { onTargetChange((it / 5f).toInt() * 5) }
        )
        val note = when {
            report.pct(report.floorMb) > target ->
                "$target% isn't reachable right now: even stopping every app below leaves ≈" +
                    "${report.pct(report.floorMb)}%. The rest is Android itself, drivers and " +
                    "protected apps (launcher, keyboard, Play services)."
            report.pct(projected) > target ->
                "Pick more apps to get to $target%: about ${projected - targetMb} MB more is needed."
            else -> "The picked apps should be enough to reach $target%."
        }
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceXS)
        )
        Text(
            "Sizes are ${report.metric} from dumpsys meminfo, so these are estimates. " +
                "The real result is measured after cleaning.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceXS)
        )
    }
}

@Composable
private fun ResultCard(r: CleanResult) {
    TurboCard {
        Text("Last clean", style = MaterialTheme.typography.titleSmall)
        InfoRow("Freed (measured)", "${r.freedMb} MB", valueColor = TurboGreen)
        InfoRow("Estimated", "${r.estimatedFreedMb} MB")
        InfoRow("In use", "${pctOf(r.beforeUsedMb, r.totalMb)}% → ${pctOf(r.afterUsedMb, r.totalMb)}%")
        if (r.blockingSupported) InfoRow("Blocked from background", "${r.blockedCount} apps")
        if (r.stillRunning.isNotEmpty()) {
            Text(
                "Still running (Android kept them, e.g. a foreground service): " +
                    r.stillRunning.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = TurboAmber,
                modifier = Modifier.padding(top = Dimens.SpaceXS)
            )
        }
    }
}

@Composable
private fun AppRow(app: AppMemory, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusS))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append(app.state)
                    if (app.blocked) append(" · blocked")
                    if (app.totalKb > 0 && app.reclaimableKb < app.totalKb) {
                        append(" · ≈${app.reclaimableKb / 1024} MB can be freed")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (app.blocked) TurboAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "${app.totalKb / 1024} MB",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = NumberFont,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = Dimens.SpaceS)
        )
    }
}

@Composable
private fun BlockedSection(
    packages: List<String>,
    labelOf: (String) -> String,
    enabled: Boolean,
    onRestore: (Set<String>?) -> Unit
) {
    SectionHeader("Blocked from background")
    TurboCard {
        packages.forEach { pkg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    labelOf(pkg),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onRestore(setOf(pkg)) }, enabled = enabled) { Text("Restore") }
            }
        }
        Button(
            onClick = { onRestore(null) },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.SpaceS)
        ) { Text("Restore all (${packages.size})") }
        Text(
            "Restore puts back each app's previous background settings, so it runs and " +
                "notifies as before without having to be opened.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceS)
        )
    }
}

private fun gb(mb: Int): String = String.format(Locale.ROOT, "%.1f", mb / 1024f)

private fun pctOf(mb: Int, totalMb: Int): Int = if (totalMb > 0) (mb * 100 / totalMb).coerceIn(0, 100) else 0
