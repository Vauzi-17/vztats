package com.vauzi.clocklock.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauzi.clocklock.BuildConfig
import com.vauzi.clocklock.R
import com.vauzi.clocklock.core.GameWatcher
import com.vauzi.clocklock.core.Prefs
import com.vauzi.clocklock.service.TurboService
import com.vauzi.clocklock.shizuku.FpsSampler
import com.vauzi.clocklock.ui.theme.Dimens
import com.vauzi.clocklock.ui.theme.ThemeMode
import com.vauzi.clocklock.ui.theme.TurboAmber
import com.vauzi.clocklock.ui.theme.TurboGreen
import com.vauzi.clocklock.ui.theme.TurboRed

private data class Metric(val key: String, val label: String)

private val METRICS = listOf(
    Metric(Prefs.METRIC_FPS, "FPS (needs Shizuku)"),
    Metric(Prefs.METRIC_GPU_FREQ, "GPU frequency"),
    Metric(Prefs.METRIC_GPU_TEMP, "GPU temperature"),
    Metric(Prefs.METRIC_CPU_FREQ, "CPU frequency"),
    Metric(Prefs.METRIC_CPU_TEMP, "CPU temperature"),
    Metric(Prefs.METRIC_BATT_POWER, "Battery draw (mA)"),
    Metric(Prefs.METRIC_BATT_TEMP, "Battery temperature"),
    Metric(Prefs.METRIC_RAM, "RAM used")
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
            .padding(horizontal = Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
    ) {
        Spacer(Modifier.height(Dimens.SpaceXS))

        // Appearance
        SectionHeader("Appearance")
        TurboCard {
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
                modifier = Modifier.padding(vertical = Dimens.SpaceS)
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
        }

        // Floating window
        SectionHeader("Floating window")
        TurboCard {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpaceS)
            )
            METRICS.forEach { m ->
                val checked = metrics.contains(m.key)
                SettingCheckRow(
                    title = m.label,
                    checked = checked,
                    onCheckedChange = { on ->
                        if (on) metrics.add(m.key) else metrics.remove(m.key)
                        prefs.floatingMetrics = metrics.toSet()
                        TurboService.sync(context)
                    }
                )
            }

            Text(
                "Layout",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpaceS)
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
                modifier = Modifier.padding(vertical = Dimens.SpaceS)
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
        }

        // Shizuku pairing
        SectionHeader("Shizuku")
        TurboCard {
            val shizukuState by FpsSampler.state.collectAsStateWithLifecycle()
            val shizukuMsg by FpsSampler.message.collectAsStateWithLifecycle()
            val (statusLabel, statusTint) = shizukuStatus(shizukuState)
            IconListRow(
                icon = Icons.Filled.Cable,
                iconTint = statusTint,
                title = "Shizuku connection",
                subtitle = shizukuMsg.ifBlank { statusLabel },
                trailing = { Badge(statusLabel, tint = statusTint) },
                onClick = if (shizukuState != FpsSampler.State.RUNNING) {
                    { FpsSampler.enable(context) }
                } else null
            )
            Text(
                "Pairing enables real per-game FPS and, later, live process/thread stats — no root needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Dimens.SpaceXS)
            )
        }

        // FPS (Shizuku)
        SectionHeader("Frame rate")
        TurboCard {
            val fpsState by FpsSampler.state.collectAsStateWithLifecycle()
            val fpsMsg by FpsSampler.message.collectAsStateWithLifecycle()
            SettingSwitch(
                title = "Real FPS via Shizuku",
                description = "Reads true per-game FPS from SurfaceFlinger. Needs Shizuku running.",
                checked = fpsState == FpsSampler.State.RUNNING || fpsState == FpsSampler.State.STARTING,
                onCheckedChange = { want ->
                    if (want) FpsSampler.enable(context) else FpsSampler.disable()
                }
            )
            if (fpsMsg.isNotEmpty()) {
                Text(
                    fpsMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Gaming automation
        SectionHeader("Gaming automation")
        TurboCard {
            var autoRam by remember { mutableStateOf(prefs.autoRamBoost) }
            var restrictBg by remember { mutableStateOf(prefs.restrictBackground) }
            var showPicker by remember { mutableStateOf(false) }
            var gameCount by remember { mutableIntStateOf(prefs.gamePackages.size) }
            val hasUsage = GameWatcher.hasUsageAccess(context)

            NavRow(
                title = "Game list",
                subtitle = if (gameCount > 0) "$gameCount app(s) selected"
                else "Falls back to apps declared as games",
                actionLabel = "Choose",
                onClick = { showPicker = true }
            )

            if (showPicker) {
                GamePickerDialog(onDismiss = {
                    showPicker = false
                    gameCount = prefs.gamePackages.size
                })
            }

            if (!hasUsage && (autoRam || restrictBg)) {
                Text(
                    "Needs \"Usage access\" to detect games \u2014 tap to grant.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { context.startActivity(GameWatcher.usageAccessIntent()) }
                        .padding(vertical = Dimens.SpaceXS)
                )
            }

            SettingSwitch(
                title = "Auto RAM boost",
                description = "Frees background RAM when a game launches.",
                checked = autoRam,
                onCheckedChange = {
                    autoRam = it
                    prefs.autoRamBoost = it
                    if (it && !GameWatcher.hasUsageAccess(context)) {
                        context.startActivity(GameWatcher.usageAccessIntent())
                    }
                    TurboService.sync(context)
                }
            )
            SettingSwitch(
                title = "Restrict background apps",
                description = "Pauses other apps during gameplay. Needs Shizuku.",
                checked = restrictBg,
                onCheckedChange = {
                    restrictBg = it
                    prefs.restrictBackground = it
                    if (it && !GameWatcher.hasUsageAccess(context)) {
                        context.startActivity(GameWatcher.usageAccessIntent())
                    }
                    TurboService.sync(context)
                }
            )
        }

        // Behaviour
        SectionHeader("Behaviour")
        TurboCard {
            SettingSwitch(
                title = "Re-apply after unlock",
                description = "Re-asserts turbo when the screen turns on.",
                checked = reapply,
                onCheckedChange = { reapply = it; prefs.reapplyOnUnlock = it }
            )
        }

        // Auto-safety
        SectionHeader("Auto-safety")
        TurboCard {
            SettingSwitch(
                title = "Auto-disable on limits",
                description = "Turns turbo off when it gets too hot or battery is low.",
                checked = autoSafety,
                onCheckedChange = {
                    autoSafety = it
                    prefs.autoSafetyEnabled = it
                    TurboService.sync(context)
                }
            )
            if (autoSafety) {
                LimitSlider(
                    label = "Temperature limit: $tempLimit \u00B0C",
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

        // About
        SectionHeader("About")
        TurboCard {
            Text(
                "VZtats ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = context.getString(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Dimens.SpaceS)
            )
        }

        Spacer(Modifier.height(Dimens.NavBarClearance))
    }
}

private fun shizukuStatus(state: FpsSampler.State): Pair<String, Color> =
    when (state) {
        FpsSampler.State.RUNNING -> "Connected" to TurboGreen
        FpsSampler.State.STARTING -> "Connecting" to TurboAmber
        FpsSampler.State.NEED_PERMISSION -> "Needs permission" to TurboAmber
        FpsSampler.State.ERROR -> "Error" to TurboRed
        FpsSampler.State.UNAVAILABLE -> "Not paired" to TurboRed
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
