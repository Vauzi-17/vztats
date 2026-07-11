package com.fartopblu.adrenoturbomode.core

/**
 * Thin JNI wrapper around libadrenotools' `adrenotools_set_turbo`.
 *
 * Both calls return `true` only when `/dev/kgsl-3d0` was opened and the KGSL
 * SETPROPERTY(PWRCTRL) ioctl was accepted by the kernel. A `false` result means
 * the device almost certainly does not support this mechanism (non-Adreno, or a
 * kernel that ignores the property).
 */
object NativeBridge {

    @Volatile
    var available: Boolean = false
        private set

    init {
        available = try {
            System.loadLibrary("adrenoturboswitch")
            true
        } catch (t: Throwable) {
            false
        }
    }

    external fun enableTurbo(): Boolean
    external fun disableTurbo(): Boolean

    /** Applies the requested turbo state, guarding against a missing native lib. */
    fun setTurbo(on: Boolean): Boolean {
        if (!available) return false
        return if (on) enableTurbo() else disableTurbo()
    }
}
