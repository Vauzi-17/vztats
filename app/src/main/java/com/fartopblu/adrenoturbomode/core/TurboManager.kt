package com.fartopblu.adrenoturbomode.core

import android.content.Context
import com.fartopblu.adrenoturbomode.service.TurboService
import com.fartopblu.adrenoturbomode.service.TurboTileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Result of the last native apply attempt. */
enum class ApplyOutcome {
    /** No apply attempted yet this process. */
    NONE,

    /** The KGSL ioctl was accepted. Whether the clock actually pins at max is
     *  confirmed live by [GpuMonitor] (see [GpuSample.isAtMax]). */
    APPLIED,

    /** The device was opened but the kernel rejected the property. */
    FAILED,

    /** The native library or KGSL device is not present at all. */
    UNSUPPORTED
}

data class TurboState(
    val desiredOn: Boolean,
    val outcome: ApplyOutcome
)

/**
 * Single source of truth for turbo state shared by the UI, the Quick Settings
 * tile, the floating overlay and the keep-alive service.
 */
object TurboManager {

    private val _state = MutableStateFlow(TurboState(false, ApplyOutcome.NONE))
    val state: StateFlow<TurboState> = _state.asStateFlow()

    /** Seed the in-memory state from persisted intent (call once on startup). */
    fun init(context: Context) {
        val prefs = Prefs.get(context)
        _state.value = _state.value.copy(desiredOn = prefs.desiredTurbo)
    }

    /**
     * Applies [on] through the native bridge, persists the intent, refreshes the
     * tile and keeps the service lifecycle in sync. Returns the native outcome.
     */
    fun setTurbo(context: Context, on: Boolean): ApplyOutcome {
        val prefs = Prefs.get(context)

        val outcome = when {
            !NativeBridge.available || !GpuMonitor.isSupported -> {
                // Still record intent, but report honestly that we can't drive it.
                if (!NativeBridge.available) ApplyOutcome.UNSUPPORTED
                else if (NativeBridge.setTurbo(on)) ApplyOutcome.APPLIED else ApplyOutcome.FAILED
            }
            NativeBridge.setTurbo(on) -> ApplyOutcome.APPLIED
            else -> ApplyOutcome.FAILED
        }

        prefs.desiredTurbo = on
        _state.value = TurboState(on, outcome)

        TurboTileService.requestUpdate(context)
        TurboService.sync(context)
        return outcome
    }

    /** Convenience toggle used by the tile and the overlay button. */
    fun toggle(context: Context): ApplyOutcome =
        setTurbo(context, !_state.value.desiredOn)

    /**
     * Re-applies the persisted intent without changing it. Used by the service
     * after the screen unlocks to defeat the "stuck at low clock" behaviour.
     */
    fun reapplyIfDesired(context: Context) {
        if (Prefs.get(context).desiredTurbo) {
            val ok = NativeBridge.setTurbo(true)
            _state.value = _state.value.copy(
                desiredOn = true,
                outcome = if (ok) ApplyOutcome.APPLIED else ApplyOutcome.FAILED
            )
            TurboTileService.requestUpdate(context)
        }
    }

    /** Used by auto-safety to force turbo off and record the reason in state. */
    fun forceOff(context: Context) {
        setTurbo(context, false)
    }
}
