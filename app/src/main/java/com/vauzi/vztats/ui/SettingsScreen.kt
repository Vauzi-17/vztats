package com.vauzi.vztats.ui

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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauzi.vztats.BuildConfig
import com.vauzi.vztats.core.GameWatcher
import com.vauzi.vztats.core.Prefs
import com.vauzi.vztats.service.TurboService
import com.vauzi.vztats.shizuku.FpsSampler
import com.vauzi.vztats.ui.theme.Dimens
import com.vauzi.vztats.ui.theme.ThemeMode
import com.vauzi.vztats.ui.theme.TurboAmber
import com.vauzi.vztats.ui.theme.TurboGreen
import com.vauzi.vztats.ui.theme.TurboRed

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
    val uriHandler = LocalUriHandler.current
    val prefs = remember { Prefs.get(context) }

    var reapply by remember { mutableStateOf(prefs.reapplyOnUnlock) }
    var autoSafety by remember { mutableStateOf(prefs.autoSafetyEnabled) }
    var tempLimit by remember { mutableIntStateOf(prefs.tempLimitC) }
    var battLimit by remember { mutableIntStateOf(prefs.batteryLimitPct) }

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
                    "Needs \"Usage access\" to detect games — tap to grant.",
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
                description = "Re-asserts the GPU lock when the screen turns on.",
                checked = reapply,
                onCheckedChange = { reapply = it; prefs.reapplyOnUnlock = it }
            )
        }

        // Auto-safety
        SectionHeader("Auto-safety")
        TurboCard {
            SettingSwitch(
                title = "Auto-disable on limits",
                description = "Releases the GPU lock when it gets too hot or battery is low.",
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

        // About
        SectionHeader("About")
        TurboCard {
            InfoRow("Version", BuildConfig.VERSION_NAME)
            InfoRow("Build", BuildConfig.VERSION_CODE.toString())
            IconListRow(
                icon = Icons.Filled.Code,
                title = "View on GitHub",
                subtitle = GITHUB_URL.removePrefix("https://"),
                // No browser installed is the only realistic failure; ignore it.
                onClick = { runCatching { uriHandler.openUri(GITHUB_URL) } },
                trailing = {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        Spacer(Modifier.height(Dimens.NavBarClearance))
    }
}

private const val GITHUB_URL = "https://github.com/Vauzi-17/vztats"

private fun shizukuStatus(state: FpsSampler.State): Pair<String, Color> =
    when (state) {
        FpsSampler.State.RUNNING -> "Connected" to TurboGreen
        FpsSampler.State.STARTING -> "Connecting" to TurboAmber
        FpsSampler.State.NEED_PERMISSION -> "Needs permission" to TurboAmber
        FpsSampler.State.ERROR -> "Error" to TurboRed
        FpsSampler.State.UNAVAILABLE -> "Not paired" to TurboRed
    }
