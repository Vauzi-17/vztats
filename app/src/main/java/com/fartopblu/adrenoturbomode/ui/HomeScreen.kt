package com.fartopblu.adrenoturbomode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fartopblu.adrenoturbomode.core.ApplyOutcome
import com.fartopblu.adrenoturbomode.core.GpuSample
import com.fartopblu.adrenoturbomode.core.Prefs
import com.fartopblu.adrenoturbomode.core.TurboManager
import com.fartopblu.adrenoturbomode.core.TurboState
import com.fartopblu.adrenoturbomode.service.TurboService
import com.fartopblu.adrenoturbomode.ui.theme.TurboAmber
import com.fartopblu.adrenoturbomode.ui.theme.TurboGreen
import com.fartopblu.adrenoturbomode.ui.theme.TurboRed

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    turboState: TurboState,
    sample: GpuSample?,
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

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // --- Main turbo toggle + verification --------------------------------
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "GPU Turbo",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Lock the Adreno clock at maximum",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Switch(
                        checked = turboState.desiredOn,
                        onCheckedChange = { TurboManager.setTurbo(context, it) }
                    )
                }

                Spacer(Modifier.height(14.dp))

                val (pillText, pillColor) = statusFor(turboState, sample)
                StatusPill(pillText, pillColor)

                Spacer(Modifier.height(10.dp))

                val freqText = when {
                    sample?.freqMhz != null && sample.maxFreqMhz != null ->
                        "${sample.freqMhz} / ${sample.maxFreqMhz} MHz"
                    sample?.freqMhz != null -> "${sample.freqMhz} MHz"
                    else -> "Frequency unavailable on this device"
                }
                Text(
                    freqText,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // --- Heat warning ----------------------------------------------------
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                "⚠ Locking the maximum frequency increases heat and battery drain. " +
                    "Enable auto-safety below if you want the app to back off automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }

        // --- Settings --------------------------------------------------------
        SectionHeader("Behaviour")

        SettingSwitch(
            title = "Floating toggle",
            description = "Show a draggable ON/OFF button over games. Needs \"display over other apps\".",
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

        SettingSwitch(
            title = "Re-apply after unlock",
            description = "Fights the \"stuck at low clock\" bug by re-asserting turbo when the screen turns on.",
            checked = reapply,
            onCheckedChange = {
                reapply = it
                prefs.reapplyOnUnlock = it
            }
        )

        SectionHeader("Auto-safety (optional)")

        SettingSwitch(
            title = "Auto-disable on limits",
            description = "Turn turbo off automatically when it gets too hot or the battery is low. Leave off to stay at max no matter what.",
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
                onChange = {
                    tempLimit = it.toInt()
                    prefs.tempLimitC = tempLimit
                }
            )
            LimitSlider(
                label = "Battery floor: $battLimit %",
                value = battLimit.toFloat(),
                range = 5f..40f,
                onChange = {
                    battLimit = it.toInt()
                    prefs.batteryLimitPct = battLimit
                }
            )
        }

        Spacer(Modifier.height(8.dp))
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
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range
        )
    }
}

/** Maps the native outcome + live clock into an honest status label + colour. */
private fun statusFor(state: TurboState, sample: GpuSample?): Pair<String, androidx.compose.ui.graphics.Color> {
    if (!state.desiredOn) return "Turbo OFF" to TurboRed.copy(alpha = 0.55f)

    return when (state.outcome) {
        ApplyOutcome.UNSUPPORTED ->
            "Not supported on this device" to TurboRed
        ApplyOutcome.FAILED ->
            "Failed — kernel rejected the request" to TurboRed
        ApplyOutcome.APPLIED, ApplyOutcome.NONE -> {
            when {
                sample?.isAtMax == true -> "Turbo ACTIVE ✓ verified (clock at max)" to TurboGreen
                else -> "Applied — waiting for load / verification" to TurboAmber
            }
        }
    }
}
