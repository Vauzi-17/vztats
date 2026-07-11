package com.vauzi.clocklock.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vauzi.clocklock.core.Prefs
import com.vauzi.clocklock.service.TurboService
import com.vauzi.clocklock.ui.theme.ThemeMode

private data class Metric(val key: String, val label: String)

private val METRICS = listOf(
    Metric(Prefs.METRIC_GPU_FREQ, "GPU frequency"),
    Metric(Prefs.METRIC_GPU_TEMP, "GPU temperature"),
    Metric(Prefs.METRIC_CPU_FREQ, "CPU frequency"),
    Metric(Prefs.METRIC_CPU_TEMP, "CPU temperature")
)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    hasOverlayPermission: () -> Boolean
) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var overlayEnabled by remember { mutableStateOf(prefs.overlayEnabled) }
    var reapply by remember { mutableStateOf(prefs.reapplyOnUnlock) }
    var autoSafety by remember { mutableStateOf(prefs.autoSafetyEnabled) }
    var tempLimit by remember { mutableIntStateOf(prefs.tempLimitC) }
    var battLimit by remember { mutableIntStateOf(prefs.batteryLimitPct) }
    val metrics = remember { mutableStateListOf<String>().apply { addAll(prefs.floatingMetrics) } }
    var floatMode by remember { mutableStateOf(prefs.floatingMode) }
    var opacity by remember { mutableIntStateOf(prefs.floatingOpacity) }
    var floatSize by remember { mutableIntStateOf(prefs.floatingSize) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // --- Appearance ------------------------------------------------------
        SectionHeader("Appearance")
        Text("Theme", style = MaterialTheme.typography.bodyLarge)
        SegmentedControl(
            options = listOf("System", "Light", "Dark"),
            selectedIndex = when (themeMode) {
                ThemeMode.SYSTEM -> 0
                ThemeMode.LIGHT -> 1
                ThemeMode.DARK -> 2
            },
            onSelect = {
                onThemeModeChange(
                    when (it) {
                        1 -> ThemeMode.LIGHT
                        2 -> ThemeMode.DARK
                        else -> ThemeMode.SYSTEM
                    }
                )
            },
            modifier = Modifier.padding(vertical = 8.dp)
        )
        SettingSwitch(
            title = "Material You colours",
            description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                "Use wallpaper-based colours instead of the built-in palette."
            else "Requires Android 12+.",
            checked = dynamicColor,
            onCheckedChange = onDynamicColorChange,
            enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        )

        // --- Floating window -------------------------------------------------
        SectionHeader("Floating window")
        SettingSwitch(
            title = "Enable floating panel",
            description = "A draggable panel over other apps. Tap it to toggle turbo.",
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
            "Shown in the panel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        METRICS.forEach { m ->
            val checked = metrics.contains(m.key)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { on ->
                        if (on) metrics.add(m.key) else metrics.remove(m.key)
                        prefs.floatingMetrics = metrics.toSet()
                        TurboService.sync(context)
                    }
                )
                Text(m.label, style = MaterialTheme.typography.bodyLarge)
            }
        }

        Text(
            "Layout",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
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
            },
            modifier = Modifier.padding(vertical = 8.dp)
        )
        LimitSlider(
            label = "Opacity: $opacity%",
            value = opacity.toFloat(),
            range = 20f..100f,
            onChange = { opacity = it.toInt(); prefs.floatingOpacity = opacity }
        )
        LimitSlider(
            label = "Size: $floatSize%",
            value = floatSize.toFloat(),
            range = 80f..140f,
            onChange = { floatSize = it.toInt(); prefs.floatingSize = floatSize }
        )
        Text(
            "In Horizontal/Vertical, tap the panel to reveal the ON/OFF button, × to hide it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )

        // --- Behaviour -------------------------------------------------------
        SectionHeader("Behaviour")
        SettingSwitch(
            title = "Re-apply after unlock",
            description = "Re-assert turbo when the screen turns on (fixes stuck-at-low-clock).",
            checked = reapply,
            onCheckedChange = { reapply = it; prefs.reapplyOnUnlock = it }
        )

        // --- Auto-safety -----------------------------------------------------
        SectionHeader("Auto-safety (optional)")
        SettingSwitch(
            title = "Auto-disable on limits",
            description = "Turn turbo off when it gets too hot or the battery is low. Leave off to stay at max no matter what.",
            checked = autoSafety,
            onCheckedChange = {
                autoSafety = it
                prefs.autoSafetyEnabled = it
                TurboService.sync(context)
            }
        )
        if (autoSafety) {
            LimitSlider(
                label = "Temperature limit: $tempLimit °C",
                value = tempLimit.toFloat(),
                range = 40f..60f,
                onChange = { tempLimit = it.toInt(); prefs.tempLimitC = tempLimit }
            )
            LimitSlider(
                label = "Battery floor: $battLimit %",
                value = battLimit.toFloat(),
                range = 5f..40f,
                onChange = { battLimit = it.toInt(); prefs.batteryLimitPct = battLimit }
            )
        }
    }
}

@Composable
private fun LimitSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
