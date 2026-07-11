package com.fartopblu.adrenoturbomode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.fartopblu.adrenoturbomode.core.Prefs
import com.fartopblu.adrenoturbomode.core.TurboManager
import com.fartopblu.adrenoturbomode.ui.AppRoot
import com.fartopblu.adrenoturbomode.ui.theme.AdrenoTurboTheme
import com.fartopblu.adrenoturbomode.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        TurboManager.init(this)
        maybeRequestNotifications()

        val prefs = Prefs.get(this)

        setContent {
            var themeMode by remember { mutableStateOf(readThemeMode(prefs)) }
            var dynamicColor by remember { mutableStateOf(prefs.dynamicColor) }

            AdrenoTurboTheme(themeMode = themeMode, dynamicColor = dynamicColor) {
                AppRoot(
                    themeMode = themeMode,
                    dynamicColor = dynamicColor,
                    onThemeModeChange = {
                        themeMode = it
                        prefs.themeMode = it.name.lowercase()
                    },
                    onDynamicColorChange = {
                        dynamicColor = it
                        prefs.dynamicColor = it
                    },
                    onRequestOverlayPermission = ::requestOverlayPermission,
                    hasOverlayPermission = { Settings.canDrawOverlays(this) }
                )
            }
        }
    }

    private fun readThemeMode(prefs: Prefs): ThemeMode = when (prefs.themeMode) {
        Prefs.THEME_LIGHT -> ThemeMode.LIGHT
        Prefs.THEME_DARK -> ThemeMode.DARK
        else -> ThemeMode.SYSTEM
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }
}
