package com.fartopblu.adrenoturbomode.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fartopblu.adrenoturbomode.core.GpuMonitor
import com.fartopblu.adrenoturbomode.core.GpuSample
import com.fartopblu.adrenoturbomode.core.TurboManager

private enum class Dest(val label: String, val icon: ImageVector) {
    HOME("Control", Icons.Filled.Bolt),
    MONITOR("Monitor", Icons.Filled.ShowChart),
    INFO("Info", Icons.Filled.Info)
}

@Composable
fun AppRoot(
    onRequestOverlayPermission: () -> Unit,
    hasOverlayPermission: () -> Boolean
) {
    var current by remember { mutableStateOf(Dest.HOME) }

    val turboState by TurboManager.state.collectAsStateWithLifecycle()

    var sample by remember { mutableStateOf<GpuSample?>(null) }
    val history = remember { mutableStateListOf<Int>() }

    LaunchedEffect(Unit) {
        GpuMonitor.sampleFlow(periodMs = 1000L).collect { s ->
            sample = s
            s.freqMhz?.let {
                history.add(it)
                if (history.size > MAX_HISTORY) history.removeAt(0)
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Dest.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = current == dest,
                        onClick = { current = dest },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        when (current) {
            Dest.HOME -> HomeScreen(
                modifier = Modifier.padding(padding),
                turboState = turboState,
                sample = sample,
                onRequestOverlayPermission = onRequestOverlayPermission,
                hasOverlayPermission = hasOverlayPermission
            )

            Dest.MONITOR -> MonitorScreen(
                modifier = Modifier.padding(padding),
                sample = sample,
                history = history
            )

            Dest.INFO -> InfoScreen(
                modifier = Modifier.padding(padding)
            )
        }
    }
}

private const val MAX_HISTORY = 60
