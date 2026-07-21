package com.vauzi.clocklock.core

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Detects which app is in the foreground so gaming-only tweaks can switch
 * themselves on and off. Uses UsageStats, which the user grants once under
 * "Usage access" — no root, no Shizuku needed for the detection itself.
 */
object GameWatcher {

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
                )
            }
        } catch (t: Throwable) {
            return false
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** Package most recently moved to the foreground, or null if unknown. */
    fun foregroundPackage(context: Context): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val events = runCatching { usm.queryEvents(now - 15_000L, now) }.getOrNull() ?: return null

        var last: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            val isForeground = event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            if (isForeground) last = event.packageName
        }
        return last
    }

    /** True when the package declares android:appCategory="game". */
    fun isGame(context: Context, pkg: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val ai = runCatching { context.packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
            ?: return false
        return ai.category == ApplicationInfo.CATEGORY_GAME
    }
}
