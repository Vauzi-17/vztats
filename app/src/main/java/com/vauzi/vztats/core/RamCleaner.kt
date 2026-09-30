package com.vauzi.vztats.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import com.vauzi.vztats.shizuku.FpsSampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Per-app memory from one scan. Sizes are from `dumpsys meminfo` ([MemoryReport.metric]). */
data class AppMemory(
    val packageName: String,
    val label: String,
    /** Everything the app's processes hold. */
    val totalKb: Long,
    /**
     * The part held by processes Android lets us stop (services / previous /
     * cached). Processes that are visible or "perceptible" — e.g. playing music —
     * are left alone by the platform and don't count toward the estimate.
     */
    val reclaimableKb: Long,
    /** Most important OOM category among its processes, e.g. "Cached", "Perceptible". */
    val state: String,
    val blocked: Boolean
)

data class MemoryReport(
    val metric: String,
    val totalMb: Int,
    val usedMb: Int,
    /** Apps the user may pick, biggest first. */
    val apps: List<AppMemory>,
    /** Memory held by everything outside [apps] — system, native, protected apps. */
    val untouchableKb: Long
) {
    val usedPct: Int get() = pct(usedMb)

    /** Estimated RAM in use if every app in [apps] were stopped — the realistic floor. */
    val floorMb: Int get() = (usedMb - apps.sumOf { it.reclaimableKb } / 1024).toInt().coerceAtLeast(0)

    fun projectedMb(selected: Set<String>): Int =
        (usedMb - apps.filter { it.packageName in selected }.sumOf { it.reclaimableKb } / 1024)
            .toInt().coerceAtLeast(0)

    fun pct(mb: Int): Int = if (totalMb > 0) (mb * 100 / totalMb).coerceIn(0, 100) else 0
}

data class CleanResult(
    val totalMb: Int,
    val beforeUsedMb: Int,
    val afterUsedMb: Int,
    val estimatedFreedMb: Int,
    val blockedCount: Int,
    /** Picked apps that still have a process after the kill (e.g. a foreground service). */
    val stillRunning: List<String>,
    val blockingSupported: Boolean
) {
    val freedMb: Int get() = (beforeUsedMb - afterUsedMb).coerceAtLeast(0)
}

/** What a package's background settings were before we blocked it, so Restore can put them back. */
data class BlockRecord(val appOpMode: String, val bucket: String, val sinceMs: Long)

/**
 * Data-driven RAM cleaner (needs Shizuku).
 *
 * 1. [scan] measures per-app memory with `dumpsys meminfo` and works out how
 *    much each app could actually give back.
 * 2. [clean] stops the apps the user picked (`am kill` — background processes
 *    only), then *blocks* them from coming back: `RUN_ANY_IN_BACKGROUND` is
 *    set to ignore and the app goes into the restricted standby bucket. The
 *    previous values are saved first.
 * 3. [restore] writes those saved values back, so the app behaves exactly as
 *    before — including notifications — without having to be opened.
 *
 * The measured before/after is always reported next to the estimate; the
 * estimate is never presented as the result.
 */
object RamCleaner {

    /** OOM categories whose processes `am kill` is allowed to stop. */
    private val RECLAIMABLE = setOf("a services", "previous", "b services", "cached", "empty")

    /** Rank of a category, most important first, for the "state" shown per app. */
    private val CATEGORY_ORDER = listOf(
        "foreground", "visible", "perceptible", "perceptible low", "perceptible medium",
        "heavy weight", "backup", "a services", "previous", "b services", "cached"
    )

    /** Never offered, even though some of these are updated system apps. */
    private val ALWAYS_PROTECTED = setOf(
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.google.android.webview",
        "com.android.webview",
        "moe.shizuku.privileged.api"
    )

    private val PACKAGE_NAME = Regex("^[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+$")

    /** RUN_ANY_IN_BACKGROUND and standby buckets exist from Android 9. */
    val blockingSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    // --- Shell ------------------------------------------------------------------

    /** Binds the Shizuku shell and waits briefly for it. False if Shizuku isn't usable. */
    suspend fun awaitShell(context: Context, timeoutMs: Long = 4000L): Boolean {
        FpsSampler.ensureShellBound(context)
        var waited = 0L
        while (!FpsSampler.shellReady && waited < timeoutMs) {
            delay(100L); waited += 100L
        }
        return FpsSampler.shellReady
    }

    private fun sh(cmd: String): String? =
        FpsSampler.execShell(cmd)?.takeUnless { it.startsWith("ERROR:") }

    // --- Scan -------------------------------------------------------------------

    /** Null when Shizuku isn't available or meminfo couldn't be parsed. */
    suspend fun scan(context: Context): MemoryReport? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!awaitShell(app)) return@withContext null

        // --oom limits the dump to the section we parse; older builds ignore
        // unknown flags or reject them, so fall back to the full dump.
        val snapshot = sh("dumpsys meminfo --oom")?.let { MeminfoParser.parse(it) }
            ?: sh("dumpsys meminfo")?.let { MeminfoParser.parse(it) }
            ?: return@withContext null

        val power = PowerMonitor.read(app)
        val eligible = eligiblePackages(app)
        val blocked = blocked(app).keys
        val pm = app.packageManager

        val byPkg = snapshot.processes.groupBy { it.packageName }
        val apps = eligible.map { pkg ->
            val procs = byPkg[pkg].orEmpty()
            AppMemory(
                packageName = pkg,
                label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg),
                totalKb = procs.sumOf { it.kb },
                reclaimableKb = procs.filter { it.category.lowercase() in RECLAIMABLE }.sumOf { it.kb },
                state = procs.map { it.category }
                    .minByOrNull { c -> CATEGORY_ORDER.indexOf(c.lowercase()).let { if (it < 0) 99 else it } }
                    ?: "Not running",
                blocked = pkg in blocked
            )
        }
            // Running apps first (biggest first), then blocked/idle ones so they
            // can still be restored from the list.
            .filter { it.totalKb > 0 || it.blocked }
            .sortedByDescending { it.totalKb }

        val appKb = apps.sumOf { it.totalKb }
        MemoryReport(
            metric = snapshot.metric,
            totalMb = power.ramTotalMb ?: 0,
            usedMb = power.ramUsedMb ?: 0,
            apps = apps,
            untouchableKb = (snapshot.processes.sumOf { it.kb } - appKb).coerceAtLeast(0)
        )
    }

    /**
     * Apps the user may pick: installed, launchable, not a core system
     * component, and not something that would break the phone or this app
     * (launcher, keyboard, Play services, Shizuku, VZtats itself).
     */
    private fun eligiblePackages(context: Context): Set<String> {
        val pm = context.packageManager
        val protected = HashSet(ALWAYS_PROTECTED).apply {
            add(context.packageName)
            defaultLauncher(context)?.let { add(it) }
            enabledKeyboards(context).forEach { add(it) }
        }
        val installed = runCatching { pm.getInstalledApplications(0) }.getOrNull() ?: return emptySet()
        return installed.asSequence()
            .filter { ai ->
                val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val updatedSystem = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                val persistent = (ai.flags and ApplicationInfo.FLAG_PERSISTENT) != 0
                // Plain user apps, plus updated system apps the user can open
                // (e.g. YouTube, Chrome) — never persistent/core ones.
                !persistent && (!system || updatedSystem) &&
                    ai.packageName !in protected &&
                    PACKAGE_NAME.matches(ai.packageName) &&
                    pm.getLaunchIntentForPackage(ai.packageName) != null
            }
            .map { it.packageName }
            .toSet()
    }

    private fun defaultLauncher(context: Context): String? {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return runCatching {
            context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName
        }.getOrNull()
    }

    private fun enabledKeyboards(context: Context): List<String> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val fromImm = runCatching { imm?.enabledInputMethodList?.map { it.packageName } }.getOrNull().orEmpty()
        val current = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')
        }.getOrNull()
        return fromImm + listOfNotNull(current)
    }

    // --- Clean ------------------------------------------------------------------

    /**
     * Stops [packages] and, where supported, blocks them from running in the
     * background. [report] provides the estimate that is shown next to the
     * measured result. Null when Shizuku isn't available.
     */
    suspend fun clean(context: Context, packages: Set<String>, report: MemoryReport?): CleanResult? =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val targets = packages.filter { PACKAGE_NAME.matches(it) && it != app.packageName }
            if (targets.isEmpty() || !awaitShell(app)) return@withContext null

            val estimated = report?.apps
                ?.filter { it.packageName in targets }
                ?.sumOf { it.reclaimableKb }
                ?.let { (it / 1024).toInt() } ?: 0

            // Block first, so nothing restarts in the gap between kill and block.
            val blockedNow = if (blockingSupported) block(app, targets) else 0

            val before = PowerMonitor.read(app).ramUsedMb ?: 0
            sh(targets.joinToString("; ") { "am kill $it" })
            // Give the kernel a moment to hand the pages back before measuring.
            delay(2000L)
            val after = PowerMonitor.read(app).ramUsedMb ?: before

            CleanResult(
                totalMb = PowerMonitor.read(app).ramTotalMb ?: 0,
                beforeUsedMb = before,
                afterUsedMb = after,
                estimatedFreedMb = estimated,
                blockedCount = blockedNow,
                stillRunning = stillRunning(targets),
                blockingSupported = blockingSupported
            )
        }

    /**
     * Saves each package's current settings (once — an app that is already
     * blocked keeps its original record), then restricts every target.
     * Returns how many of [targets] are blocked afterwards.
     */
    private fun block(context: Context, targets: List<String>): Int {
        val records = blocked(context).toMutableMap()
        val fresh = targets.filter { it !in records }

        if (fresh.isNotEmpty()) {
            // One shell call to read the current state of every new package.
            val query = fresh.joinToString("; ") {
                "echo '@@$it'; cmd appops get $it RUN_ANY_IN_BACKGROUND; am get-standby-bucket $it"
            }
            val out = sh(query)
            val now = System.currentTimeMillis()
            out?.split("@@")?.drop(1)?.forEach { chunk ->
                val lines = chunk.lines()
                val pkg = lines.first().trim()
                if (pkg !in fresh) return@forEach
                val body = lines.drop(1)
                records[pkg] = BlockRecord(
                    appOpMode = parseAppOpMode(body),
                    bucket = parseBucket(body),
                    sinceMs = now
                )
            }
        }

        // Never restrict an app whose previous settings we couldn't save:
        // without a record, Restore couldn't put it back.
        val toBlock = targets.filter { it in records }
        if (toBlock.isEmpty()) return 0
        save(context, records)

        val restricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) "restricted" else "rare"
        sh(toBlock.joinToString("; ") {
            "cmd appops set $it RUN_ANY_IN_BACKGROUND ignore; am set-standby-bucket $it $restricted"
        })
        return toBlock.size
    }

    private fun stillRunning(targets: List<String>): List<String> {
        val ps = sh("ps -A -o NAME") ?: return emptyList()
        val names = ps.lineSequence().map { it.trim().substringBefore(':') }.toSet()
        return targets.filter { it in names }
    }

    // --- Restore ----------------------------------------------------------------

    /** Puts back the saved background settings for [packages] (all blocked apps when null). */
    suspend fun restore(context: Context, packages: Set<String>? = null): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val records = blocked(app).toMutableMap()
        val targets = records.keys.filter { packages == null || it in packages }
        if (targets.isEmpty() || !awaitShell(app)) return@withContext 0

        val out = sh(targets.joinToString("; ") { pkg ->
            val r = records.getValue(pkg)
            "cmd appops set $pkg RUN_ANY_IN_BACKGROUND ${r.appOpMode}; am set-standby-bucket $pkg ${r.bucket}"
        })
        // Only forget the records once the shell actually ran the commands.
        if (out == null) return@withContext 0
        targets.forEach { records.remove(it) }
        save(app, records)
        targets.size
    }

    // --- Persistence ------------------------------------------------------------

    /** Blocked packages and their saved settings. Survives app restarts and reboots. */
    fun blocked(context: Context): Map<String, BlockRecord> {
        val json = Prefs.get(context).ramBlockedJson.takeIf { it.isNotBlank() } ?: return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { k ->
                val r = o.getJSONObject(k)
                BlockRecord(r.getString("appop"), r.getString("bucket"), r.optLong("since"))
            }
        }.getOrDefault(emptyMap())
    }

    private fun save(context: Context, records: Map<String, BlockRecord>) {
        val o = JSONObject()
        records.forEach { (pkg, r) ->
            o.put(pkg, JSONObject().put("appop", r.appOpMode).put("bucket", r.bucket).put("since", r.sinceMs))
        }
        Prefs.get(context).ramBlockedJson = o.toString()
    }

    // --- Parsing helpers (internal for tests) -----------------------------------

    /** "RUN_ANY_IN_BACKGROUND: ignore; time=..." -> "ignore"; "No operations." -> "default". */
    internal fun parseAppOpMode(lines: List<String>): String {
        // Some versions prefix the line, e.g. "Uid mode: RUN_ANY_IN_BACKGROUND: ignore".
        val mode = lines.firstNotNullOfOrNull { APP_OP_MODE.find(it)?.groupValues?.get(1)?.lowercase() }
        return mode?.takeIf { it in setOf("allow", "ignore", "deny", "default", "foreground") } ?: "default"
    }

    private val APP_OP_MODE = Regex("RUN_ANY_IN_BACKGROUND:\\s*(\\w+)")

    /** "10" -> "active", "45" -> "restricted"; anything else -> "active". */
    internal fun parseBucket(lines: List<String>): String {
        val n = lines.firstNotNullOfOrNull { it.trim().toIntOrNull() }
        return when (n) {
            20 -> "working_set"
            30 -> "frequent"
            40 -> "rare"
            45 -> "restricted"
            else -> "active"
        }
    }
}
