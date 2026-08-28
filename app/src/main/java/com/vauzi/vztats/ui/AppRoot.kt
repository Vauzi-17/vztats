package com.vauzi.vztats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vauzi.vztats.core.SystemMonitor
import com.vauzi.vztats.core.SystemSample
import com.vauzi.vztats.core.TurboManager
import com.vauzi.vztats.ui.theme.Dimens
import com.vauzi.vztats.ui.theme.ThemeMode

private enum class Dest(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    SESSIONS("Sessions", Icons.Filled.History),
    SETTINGS("Settings", Icons.Filled.Tune)
}

@Composable
fun AppRoot(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    hasOverlayPermission: () -> Boolean
) {
    var current by remember { mutableStateOf(Dest.HOME) }
    var showFloatingDialog by remember { mutableStateOf(false) }
    val turboState by TurboManager.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var sample by remember { mutableStateOf<SystemSample?>(null) }
    val history = remember { mutableStateListOf<Int>() }

    LaunchedEffect(Unit) {
        SystemMonitor.sampleFlow(context, periodMs = 1000L).collect { s ->
            sample = s
            s.gpu.freqMhz?.let {
                history.add(it)
                if (history.size > MAX_HISTORY) history.removeAt(0)
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            AppHeader(
                title = current.label,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onOpenFloatingMonitor = { showFloatingDialog = true }
            )
            Box(Modifier.weight(1f)) {
                when (current) {
                    Dest.HOME -> HomeScreen(turboState = turboState, sample = sample, history = history)
                    Dest.SESSIONS -> SessionsScreen()
                    Dest.SETTINGS -> SettingsScreen(
                        themeMode = themeMode,
                        dynamicColor = dynamicColor,
                        onThemeModeChange = onThemeModeChange,
                        onDynamicColorChange = onDynamicColorChange,
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        hasOverlayPermission = hasOverlayPermission
                    )
                }
            }
        }

        FloatingNavBar(
            items = Dest.entries.map { it.label to it.icon },
            selectedIndex = Dest.entries.indexOf(current),
            onSelect = { current = Dest.entries[it] },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = Dimens.SpaceL)
        )
    }

    if (showFloatingDialog) {
        FloatingMonitorDialog(
            onDismiss = { showFloatingDialog = false },
            onRequestOverlayPermission = onRequestOverlayPermission,
            hasOverlayPermission = hasOverlayPermission
        )
    }
}

@Composable
private fun AppHeader(
    title: String,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenFloatingMonitor: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        RoundIconButton(
            icon = Icons.Filled.PictureInPictureAlt,
            contentDescription = "Floating monitor",
            onClick = onOpenFloatingMonitor
        )
        Spacer(Modifier.width(Dimens.SpaceS))
        RoundIconButton(
            icon = when (themeMode) {
                ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
                ThemeMode.LIGHT -> Icons.Filled.LightMode
                ThemeMode.DARK -> Icons.Filled.DarkMode
            },
            contentDescription = "Toggle theme",
            onClick = { onThemeModeChange(themeMode.next()) }
        )
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, contentDescription: String?, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FloatingNavBar(
    items: List<Pair<String, ImageVector>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        modifier = modifier
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { i, (label, icon) ->
                val selected = i == selectedIndex
                NavPillItem(label, icon, selected) { onSelect(i) }
                if (i != items.lastIndex) Spacer(Modifier.width(2.dp))
            }
        }
    }
}

@Composable
private fun NavPillItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(20.dp))
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun ThemeMode.next(): ThemeMode = when (this) {
    ThemeMode.SYSTEM -> ThemeMode.LIGHT
    ThemeMode.LIGHT -> ThemeMode.DARK
    ThemeMode.DARK -> ThemeMode.SYSTEM
}

private const val MAX_HISTORY = 60
