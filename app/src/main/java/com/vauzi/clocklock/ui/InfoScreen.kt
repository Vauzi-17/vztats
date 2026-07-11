package com.vauzi.clocklock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vauzi.clocklock.R
import com.vauzi.clocklock.core.CpuMonitor
import com.vauzi.clocklock.core.GpuMonitor
import com.vauzi.clocklock.core.NativeBridge
import com.vauzi.clocklock.ui.theme.TurboGreen
import com.vauzi.clocklock.ui.theme.TurboRed

@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Compatibility",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(16.dp)
        ) {
            CompatRow("Native turbo library", NativeBridge.available)
            CompatRow("GPU frequency readable", GpuMonitor.isSupported)
            CompatRow("CPU frequency readable", CpuMonitor.isSupported)
            Text(
                if (NativeBridge.available && GpuMonitor.isSupported)
                    "This device exposes the Adreno interfaces turbo relies on."
                else
                    "Some interfaces are missing — turbo may not work here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Text(
            "About",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(16.dp)
        ) {
            Text(
                text = context.getString(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CompatRow(label: String, ok: Boolean) {
    Text(
        text = (if (ok) "✓  " else "✗  ") + label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (ok) TurboGreen else TurboRed,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}
