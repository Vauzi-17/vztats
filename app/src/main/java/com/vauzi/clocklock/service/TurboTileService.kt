package com.vauzi.clocklock.service

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vauzi.clocklock.R
import com.vauzi.clocklock.core.GpuMonitor
import com.vauzi.clocklock.core.NativeBridge
import com.vauzi.clocklock.core.TurboManager

/** Quick Settings tile to toggle turbo without opening the app. */
class TurboTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        TurboManager.init(this)
        render()
    }

    override fun onClick() {
        super.onClick()
        TurboManager.toggle(this)
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val supported = NativeBridge.available && GpuMonitor.isSupported
        val on = TurboManager.state.value.desiredOn

        tile.state = when {
            !supported -> Tile.STATE_UNAVAILABLE
            on -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = "GPU Turbo"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_turbo)
        if (Build_VERSION_TIRAMISU_OR_ABOVE) {
            tile.subtitle = when {
                !supported -> "Unsupported"
                on -> "On"
                else -> "Off"
            }
        }
        tile.updateTile()
    }

    private val Build_VERSION_TIRAMISU_OR_ABOVE: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

    companion object {
        /** Asks the platform to call [onStartListening] so the tile repaints. */
        fun requestUpdate(context: Context) {
            runCatching {
                requestListeningState(
                    context,
                    ComponentName(context, TurboTileService::class.java)
                )
            }
        }
    }
}
