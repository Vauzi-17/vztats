package com.fartopblu.adrenoturbomode.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fartopblu.adrenoturbomode.R
import com.fartopblu.adrenoturbomode.core.GpuMonitor
import com.fartopblu.adrenoturbomode.core.NativeBridge
import com.fartopblu.adrenoturbomode.ui.theme.TurboGreen
import com.fartopblu.adrenoturbomode.ui.theme.TurboRed

@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "About & compatibility",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Device support summary
        val nativeOk = NativeBridge.available
        val sysfsOk = GpuMonitor.isSupported
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CompatRow("Native turbo library loaded", nativeOk)
                CompatRow("KGSL frequency node readable", sysfsOk)
                Text(
                    if (nativeOk && sysfsOk)
                        "This device exposes the Adreno interfaces turbo relies on."
                    else
                        "Some interfaces are missing — turbo may not work here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Text(
                text = context.getString(R.string.about_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun CompatRow(label: String, ok: Boolean) {
    Text(
        text = (if (ok) "✓  " else "✗  ") + label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (ok) TurboGreen else TurboRed
    )
}
