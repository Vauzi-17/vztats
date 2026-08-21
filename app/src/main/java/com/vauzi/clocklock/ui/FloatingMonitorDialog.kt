package com.vauzi.clocklock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vauzi.clocklock.core.Prefs
import com.vauzi.clocklock.service.TurboService
import com.vauzi.clocklock.ui.theme.Dimens

private data class FloatMetric(val key: String, val label: String)

private val FLOAT_METRICS = listOf(
    FloatMetric(Prefs.METRIC_FPS, "FPS"),
    FloatMetric(Prefs.METRIC_GPU_FREQ, "GPU freq"),
    FloatMetric(Prefs.METRIC_GPU_LOAD, "GPU %"),
    FloatMetric(Prefs.METRIC_GPU_TEMP, "GPU temp"),
    FloatMetric(Prefs.METRIC_CPU_FREQ, "CPU freq"),
    FloatMetric(Prefs.METRIC_CPU_LOAD, "CPU %"),
    FloatMetric(Prefs.METRIC_CPU_TEMP, "CPU temp"),
    FloatMetric(Prefs.METRIC_BATT_POWER, "Batt draw"),
    FloatMetric(Prefs.METRIC_BATT_TEMP, "Batt temp"),
    FloatMetric(Prefs.METRIC_BATT_PCT, "Batt %"),
    FloatMetric(Prefs.METRIC_RAM, "RAM"),
    FloatMetric(Prefs.METRIC_RAM_PCT, "RAM %")
)

/**
 * Popup for the floating hover monitor — the first-class entry point for it,
 * reached from a button on Home instead of being buried in Settings.
 */
@Composable
fun FloatingMonitorDialog(
    onDismiss: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    hasOverlayPermission: () -> Boolean
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var overlayEnabled by remember { mutableStateOf(prefs.overlayEnabled) }
    val metrics = remember { mutableStateListOf<String>().apply { addAll(prefs.floatingMetrics) } }
    var floatMode by remember { mutableStateOf(prefs.floatingMode) }
    var opacity by remember { mutableIntStateOf(prefs.floatingOpacity) }
    var floatSize by remember { mutableIntStateOf(prefs.floatingSize) }
    var compactMetric by remember { mutableStateOf(prefs.floatingCompactMetric) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(Dimens.RadiusXL),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(Dimens.SpaceL),
                verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Floating monitor",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }

                SettingSwitch(
                    title = "Enable floating panel",
                    description = "A draggable panel over other apps.",
                    checked = overlayEnabled,
                    onCheckedChange = { want ->
                        if (want && !hasOverlayPermission()) {
                            onRequestOverlayPermission()
                        } else {
                            overlayEnabled = want
                            prefs.overlayEnabled = want
                            TurboService.sync(context)
                        }
                    }
                )

                Text(
                    "Metrics to show",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FloatMetricChips(metrics) { key, on ->
                    if (on) metrics.add(key) else metrics.remove(key)
                    prefs.floatingMetrics = metrics.toSet()
                    TurboService.sync(context)
                }

                Text(
                    "Layout",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SegmentedControl(
                    options = listOf("Compact", "Horizontal", "Vertical"),
                    selectedIndex = when (floatMode) {
                        Prefs.MODE_COMPACT -> 0
                        Prefs.MODE_VERTICAL -> 2
                        else -> 1
                    },
                    onSelect = {
                        floatMode = when (it) {
                            0 -> Prefs.MODE_COMPACT
                            2 -> Prefs.MODE_VERTICAL
                            else -> Prefs.MODE_HORIZONTAL
                        }
                        prefs.floatingMode = floatMode
                        TurboService.sync(context)
                    }
                )

                if (floatMode == Prefs.MODE_COMPACT) {
                    Text(
                        "Pill shows",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val pickable = FLOAT_METRICS.filter { metrics.contains(it.key) }
                    val effective = compactMetric.takeIf { it.isNotEmpty() && metrics.contains(it) }
                        ?: pickable.firstOrNull()?.key
                    if (pickable.isEmpty()) {
                        Text(
                            "Enable at least one metric above to choose.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FlowRowChips(pickable, effective) { key ->
                            compactMetric = key
                            prefs.floatingCompactMetric = key
                            TurboService.sync(context)
                        }
                    }
                }

                LimitSlider(
                    label = "Opacity: $opacity%",
                    value = opacity.toFloat(),
                    range = 20f..100f,
                    onChange = { opacity = it.toInt(); prefs.floatingOpacity = opacity; TurboService.sync(context) }
                )
                LimitSlider(
                    label = "Size: $floatSize%",
                    value = floatSize.toFloat(),
                    range = 80f..140f,
                    onChange = { floatSize = it.toInt(); prefs.floatingSize = floatSize; TurboService.sync(context) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatMetricChips(selected: List<String>, onToggle: (String, Boolean) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        modifier = Modifier.fillMaxWidth()
    ) {
        FLOAT_METRICS.forEach { m ->
            val isOn = selected.contains(m.key)
            ToggleChip(text = m.label, selected = isOn, onClick = { onToggle(m.key, !isOn) })
        }
    }
}

/** Single-select chip row — used for picking the one metric the compact pill shows. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowChips(options: List<FloatMetric>, selectedKey: String?, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
        modifier = Modifier.fillMaxWidth()
    ) {
        options.forEach { m ->
            ToggleChip(text = m.label, selected = m.key == selectedKey, onClick = { onSelect(m.key) })
        }
    }
}
