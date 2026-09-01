package com.vauzi.vztats.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauzi.vztats.core.SessionRecorder
import com.vauzi.vztats.core.SessionStore
import com.vauzi.vztats.core.SessionSummary
import com.vauzi.vztats.service.TurboService
import com.vauzi.vztats.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val recording by SessionRecorder.recording.collectAsStateWithLifecycle()
    var refreshTick by remember { mutableIntStateOf(0) }

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.SpaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
    ) {
        Spacer(Modifier.height(Dimens.SpaceXS))

        Text(
            "Records GPU/CPU peaks, temps and FPS while a game runs in the foreground.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = {
                if (recording) {
                    SessionRecorder.stop(context)
                    refreshTick++
                } else {
                    SessionRecorder.start()
                    TurboService.sync(context)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (recording) "Stop & save" else "Record session")
        }
        if (recording) {
            Text(
                "Recording… keep the game in the foreground.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val sessions = remember(recording, refreshTick) { SessionStore.list(context) }

        if (sessions.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    val csv = SessionStore.exportCsv(context)
                    if (csv == null) {
                        Toast.makeText(context, "Nothing to export.", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    val uri = FileProvider.getUriForFile(
                        context, "${context.packageName}.fileprovider", csv
                    )
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "VZtats sessions")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(share, "Export sessions"))
                    }.onFailure {
                        Toast.makeText(context, "No app to share the file.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export ${sessions.size} session(s) as CSV")
            }
        }

        if (sessions.isEmpty() && !recording) {
            Text(
                "No saved sessions yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            sessions.forEach { s ->
                SessionRow(s) {
                    SessionStore.delete(context, s.id)
                    refreshTick++
                }
            }
        }

        Spacer(Modifier.height(Dimens.NavBarClearance))
    }
}

@Composable
private fun SessionRow(s: SessionSummary, onDelete: () -> Unit) {
    TurboCard(padding = Dimens.SpaceM, radius = Dimens.RadiusM) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                SimpleDateFormat("dd MMM • HH:mm", Locale.getDefault()).format(Date(s.startedAtMs)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${s.durationSec}s",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "  ✕",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onDelete() }
            )
        }
        Text(
            buildString {
                s.gpuMaxMhz?.let { append("GPU max ${it}MHz  ") }
                s.gpuMaxTempC?.let { append("GPU ${it.toInt()}°  ") }
                s.cpuMaxTempC?.let { append("CPU ${it.toInt()}°  ") }
                s.avgFps?.let { append("avg ${it}fps  ") }
                s.minFps?.let { append("min ${it}fps  ") }
                s.throttlePct?.let { append("throttle ${it}%") }
            }.ifBlank { "No metrics captured." },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Dimens.SpaceXS)
        )
    }
}
