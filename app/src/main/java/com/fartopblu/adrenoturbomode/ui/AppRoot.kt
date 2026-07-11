package com.fartopblu.adrenoturbomode.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fartopblu.adrenoturbomode.core.SystemMonitor
import com.fartopblu.adrenoturbomode.core.SystemSample
import com.fartopblu.adrenoturbomode.core.TurboManager
import com.fartopblu.adrenoturbomode.ui.theme.ThemeMode

private enum class Dest(val label: String, val icon: ImageVector) {
    CONTROL("Control", Icons.Filled.Bolt),
    MONITOR("Monitor", Icons.Filled.Speed),
    SETTINGS("Settings", Icons.Filled.Tune),
    INFO("Info", Icons.Filled.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    hasOverlayPermission: () -> Boolean
) {
    var current by remember { mutableStateOf(Dest.CONTROL) }
    val turboState by TurboManager.state.collectAsStateWithLifecycle()

    var sample by remember { mutableStateOf<SystemSample?>(null) }
    val history = remember { mutableStateListOf<Int>() }

    LaunchedEffect(Unit) {
        SystemMonitor.sampleFlow(periodMs = 1000L).collect { s ->
            sample = s
            s.gpu.freqMhz?.let {
                history.add(it)
                if (history.size > MAX_HISTORY) history.removeAt(0)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Adreno GPU Turbo") },
                actions = {
                    IconButton(onClick = { onThemeModeChange(themeMode.next()) }) {
                        Icon(
                            imageVector = when (themeMode) {
                                ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                ThemeMode.DARK -> Icons.Filled.DarkMode
                            },
                            contentDescription = "Toggle theme"
                        )
                    }
                }
            )
        },
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
        val contentModifier = Modifier.padding(padding)
        when (current) {
            Dest.CONTROL -> HomeScreen(
                modifier = contentModifier,
                turboState = turboState,
                sample = sample
            )

            Dest.MONITOR -> MonitorScreen(
                modifier = contentModifier,
                sample = sample,
                history = history
            )

            Dest.SETTINGS -> SettingsScreen(
                modifier = contentModifier,
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                onThemeModeChange = onThemeModeChange,
                onDynamicColorChange = onDynamicColorChange,
                onRequestOverlayPermission = onRequestOverlayPermission,
                hasOverlayPermission = hasOverlayPermission
            )

            Dest.INFO -> InfoScreen(modifier = contentModifier)
        }
    }
}

private fun ThemeMode.next(): ThemeMode = when (this) {
    ThemeMode.SYSTEM -> ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.DARK
    ThemeMode.DARK -> ThemeMode.SYSTEM
}

private const val MAX_HISTORY = 60
