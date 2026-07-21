package com.vauzi.clocklock.core

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import com.vauzi.clocklock.shizuku.FpsSampler
import kotlinx.coroutines.delay

/**
 * Frees RAM by killing *background* processes only — the foreground game is never
 * touched, so this is safe to trigger from the overlay mid-game.
 *
 * Preferred path is Shizuku's `am kill-all`, which the platform itself limits to
 * background processes. Without Shizuku it falls back to
 * [ActivityManager.killBackgroundProcesses] per non-system package.
 *
 * Honest note: free RAM is not automatically "better" on Android — the win here
 * is reduced memory pressure so a heavy game is less likely to be evicted or to
 * stutter. The measured delta is reported rather than a made-up number.
 */
object RamBooster {

    data class Result(
        val freedMb: Int,
        val beforeUsedMb: Int,
        val afterUsedMb: Int,
        val viaShizuku: Boolean
    )

    /** Packages we never touch even in the fallback path. */
    private fun isProtected(context: Context, ai: ApplicationInfo, foreground: String?): Boolean {
        if (ai.packageName == context.packageName) return true
        if (ai.packageName == foreground) return true
        if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return true
        return false
    }

    suspend fun boost(context: Context, foregroundPackage: String? = null): Result {
        val app = context.applicationContext
        val beforeUsed = PowerMonitor.read(app).ramUsedMb ?: 0

        FpsSampler.ensureShellBound(app)
        val out = FpsSampler.execShell("am kill-all")
        val viaShizuku = out != null && !out.startsWith("ERROR")
        if (!viaShizuku) killBackgroundFallback(app, foregroundPackage)

        // Give the system a moment to reclaim before measuring the delta.
        delay(900)
        val afterUsed = PowerMonitor.read(app).ramUsedMb ?: beforeUsed

        return Result(
            freedMb = (beforeUsed - afterUsed).coerceAtLeast(0),
            beforeUsedMb = beforeUsed,
            afterUsedMb = afterUsed,
            viaShizuku = viaShizuku
        )
    }

    private fun killBackgroundFallback(context: Context, foreground: String?) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val pm = context.packageManager
        val installed = runCatching { pm.getInstalledApplications(0) }.getOrNull() ?: return
        for (ai in installed) {
            if (isProtected(context, ai, foreground)) continue
            runCatching { am.killBackgroundProcesses(ai.packageName) }
        }
    }
}
