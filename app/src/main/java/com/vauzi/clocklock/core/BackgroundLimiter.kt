package com.vauzi.clocklock.core

import android.content.Context
import android.content.pm.ApplicationInfo
import com.vauzi.clocklock.shizuku.FpsSampler

/**
 * While a game is in the foreground, pushes other user-installed apps into the
 * "restricted" standby bucket so they stop waking up and stealing CPU. Fully
 * reversible — everything is put back when the game exits.
 *
 * Needs Shizuku (shell). System apps, the game itself and this app are never
 * touched, and all changes are applied in a single batched shell call so it
 * doesn't spawn dozens of processes.
 */
object BackgroundLimiter {

    private val restricted = LinkedHashSet<String>()

    val activeCount: Int get() = restricted.size

    fun restrictFor(context: Context, gamePackage: String) {
        if (restricted.isNotEmpty()) return
        FpsSampler.ensureShellBound(context)
        if (!FpsSampler.shellReady) return

        val targets = candidatePackages(context, gamePackage)
        if (targets.isEmpty()) return

        val cmd = targets.joinToString("; ") { "am set-standby-bucket $it restricted" }
        FpsSampler.execShell(cmd) ?: return
        restricted.addAll(targets)
    }

    fun restore(context: Context) {
        if (restricted.isEmpty()) return
        FpsSampler.ensureShellBound(context)
        if (FpsSampler.shellReady) {
            val cmd = restricted.joinToString("; ") { "am set-standby-bucket $it active" }
            FpsSampler.execShell(cmd)
        }
        restricted.clear()
    }

    private fun candidatePackages(context: Context, gamePackage: String): List<String> {
        val pm = context.packageManager
        val installed = runCatching { pm.getInstalledApplications(0) }.getOrNull() ?: return emptyList()
        val self = context.packageName
        return installed.asSequence()
            .filter { ai ->
                ai.packageName != self &&
                    ai.packageName != gamePackage &&
                    (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            }
            .map { it.packageName }
            .toList()
    }
}
