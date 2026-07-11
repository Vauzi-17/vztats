package com.vauzi.clocklock.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vauzi.clocklock.core.ApplyOutcome
import com.vauzi.clocklock.core.SystemSample
import com.vauzi.clocklock.core.TurboManager
import com.vauzi.clocklock.core.TurboState
import com.vauzi.clocklock.ui.theme.TurboAmber
import com.vauzi.clocklock.ui.theme.TurboGreen
import com.vauzi.clocklock.ui.theme.TurboRed

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    turboState: TurboState,
    sample: SystemSample?
) {
    val context = LocalContext.current
    val gpu = sample?.gpu
    val cpu = sample?.cpu

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- Hero: gauge + status + power button -----------------------------
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TurboGauge(
                fraction = gpu?.loadOfMax ?: 0f,
                centerValue = gpu?.freqMhz?.toString() ?: "—",
                subLabel = gpu?.maxFreqMhz?.let { "of $it MHz" } ?: "GPU clock",
                active = turboState.desiredOn,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
            )

            val (pillText, pillColor) = statusFor(turboState, gpu?.isAtMax == true)
            StatusPill(pillText, pillColor)

            PowerButton(
                on = turboState.desiredOn,
                onToggle = { TurboManager.setTurbo(context, !turboState.desiredOn) }
            )
        }

        // --- At-a-glance tiles ----------------------------------------------
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricTile(
                label = "GPU temp",
                value = gpu?.tempC?.let { "%.0f".format(it) } ?: "—",
                unit = "°C",
                accent = tempAccent(gpu?.tempC),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "CPU clock",
                value = cpu?.freqMhz?.let { "%.1f".format(it / 1000f) } ?: "—",
                unit = "GHz",
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "CPU temp",
                value = cpu?.tempC?.let { "%.0f".format(it) } ?: "—",
                unit = "°C",
                accent = tempAccent(cpu?.tempC),
                modifier = Modifier.weight(1f)
            )
        }

        // --- Heat note -------------------------------------------------------
        Text(
            "Locking the max frequency raises heat and battery drain. " +
                "Turn on auto-safety in Settings if you want it to back off automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TurboGauge(
    fraction: Float,
    centerValue: String,
    subLabel: String,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val arcColor by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "arc"
    )
    val animFraction by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "frac")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
            val stroke = size.minDimension * 0.09f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            val start = 135f
            val sweep = 270f
            drawArc(
                color = track,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = arcColor,
                startAngle = start,
                sweepAngle = sweep * animFraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = centerValue,
                fontFamily = NumberFont,
                fontWeight = FontWeight.Bold,
                fontSize = 46.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable { onToggle() }
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = fg)
        Spacer(Modifier.size(10.dp))
        Text(
            text = if (on) "TURBO ON — TAP TO STOP" else "TURN ON TURBO",
            color = fg,
            style = MaterialTheme.typography.titleSmall,
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

/** Maps the native outcome + live "at max" flag into an honest status + colour. */
private fun statusFor(state: TurboState, atMax: Boolean): Pair<String, Color> {
    if (!state.desiredOn) return "Turbo off" to TurboRed.copy(alpha = 0.5f)
    return when (state.outcome) {
        ApplyOutcome.UNSUPPORTED -> "Not supported on this device" to TurboRed
        ApplyOutcome.FAILED -> "Failed — kernel rejected request" to TurboRed
        ApplyOutcome.APPLIED, ApplyOutcome.NONE ->
            if (atMax) "Active — verified at max clock" to TurboGreen
            else "Applied — waiting for load" to TurboAmber
    }
}
