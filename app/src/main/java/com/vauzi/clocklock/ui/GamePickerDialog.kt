package com.vauzi.clocklock.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.vauzi.clocklock.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class AppEntry(
    val pkg: String,
    val label: String,
    val isGameCategory: Boolean
)

/**
 * Lets the user tick exactly which apps count as "games" for the automation.
 * Relying on android:appCategory alone misses emulators (Winlator, etc.), so the
 * explicit list is the source of truth.
 */
@Composable
fun GamePickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(prefs.gamePackages) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadApps(context) }
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Pick your games",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${selected.size} selected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("Done") }
                }

                Text(
                    "Ticked apps trigger auto RAM boost and background restriction. " +
                        "Emulators like Winlator won't be auto-detected — tick them here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (loading) {
                    Row(
                        Modifier.fillMaxWidth().padding(32.dp),
                        horizontalArrangement = Arrangement.Center
                    ) { CircularProgressIndicator() }
                } else {
                    LazyColumn {
                        items(apps, key = { it.pkg }) { entry ->
                            AppRow(
                                context = context,
                                entry = entry,
                                checked = entry.pkg in selected,
                                onToggle = { on ->
                                    selected = if (on) selected + entry.pkg
                                    else selected - entry.pkg
                                    prefs.gamePackages = selected
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    context: Context,
    entry: AppEntry,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val icon = remember(entry.pkg) {
        runCatching {
            context.packageManager.getApplicationIcon(entry.pkg).toBitmap(96, 96).asImageBitmap()
        }.getOrNull()
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(entry.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (entry.isGameCategory) "${entry.pkg}  •  detected game" else entry.pkg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(checked = checked, onCheckedChange = onToggle)
    }
}

private fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val self = context.packageName
    val installed = runCatching { pm.getInstalledApplications(0) }.getOrNull() ?: return emptyList()

    return installed.asSequence()
        .filter { ai ->
            ai.packageName != self &&
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                pm.getLaunchIntentForPackage(ai.packageName) != null
        }
        .map { ai ->
            AppEntry(
                pkg = ai.packageName,
                label = runCatching { pm.getApplicationLabel(ai).toString() }
                    .getOrDefault(ai.packageName),
                isGameCategory = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                    ai.category == ApplicationInfo.CATEGORY_GAME
            )
        }
        // Detected games first, then alphabetical — quick to find, easy to scan.
        .sortedWith(compareByDescending<AppEntry> { it.isGameCategory }.thenBy { it.label.lowercase() })
        .toList()
}
