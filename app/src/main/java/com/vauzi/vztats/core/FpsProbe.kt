package com.vauzi.vztats.core

/**
 * Holds the latest measured game FPS. Populated by the Shizuku-backed FPS
 * sampler (phase 2); stays null when FPS measurement isn't running, so the rest
 * of the app can read [currentFps] safely regardless.
 */
object FpsProbe {

    @Volatile
    var currentFps: Int? = null
        private set

    /** True while a Shizuku FPS session is actively feeding values. */
    @Volatile
    var active: Boolean = false
        private set

    fun update(fps: Int?) {
        currentFps = fps
    }

    fun setActive(value: Boolean) {
        active = value
        if (!value) currentFps = null
    }
}
