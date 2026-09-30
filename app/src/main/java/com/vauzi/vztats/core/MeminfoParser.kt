package com.vauzi.vztats.core

/** One process line from `dumpsys meminfo`, under the OOM category it was listed in. */
data class ProcessMemory(
    val process: String,
    val pid: Int,
    val kb: Long,
    val category: String
) {
    /** "com.foo:remote" -> "com.foo". Process names of app processes start with the package. */
    val packageName: String get() = process.substringBefore(':')
}

/** Result of parsing one meminfo dump. [metric] is "PSS" or "RSS" — whichever the dump listed. */
data class MeminfoSnapshot(
    val metric: String,
    val processes: List<ProcessMemory>
)

/**
 * Parses the "Total PSS by OOM adjustment" section of `dumpsys meminfo`
 * (falling back to the RSS variant some Android versions print instead):
 *
 * ```
 * Total PSS by OOM adjustment:
 *     318,016K: System
 *         318,016K: system (pid 1553)
 *     123,450K: Cached
 *          45,000K: com.whatsapp (pid 1234 / activities)
 * ```
 *
 * Pure string parsing with no Android dependencies, so it is unit-testable.
 */
object MeminfoParser {

    // "    318,016K: System"   /   "    318,016 kB: System"
    private val CATEGORY = Regex("""^\s*([\d,]+)\s*K(?:B)?:\s+([A-Za-z][A-Za-z ]*?)\s*$""", RegexOption.IGNORE_CASE)

    // "         45,000K: com.whatsapp (pid 1234 / activities)"
    private val PROCESS = Regex("""^\s*([\d,]+)\s*K(?:B)?:\s+(\S+)\s+\(pid\s+(\d+)""", RegexOption.IGNORE_CASE)

    fun parse(dump: String): MeminfoSnapshot? {
        for (metric in listOf("PSS", "RSS")) {
            val procs = parseSection(dump, "Total $metric by OOM adjustment:")
            if (procs.isNotEmpty()) return MeminfoSnapshot(metric, procs)
        }
        return null
    }

    private fun parseSection(dump: String, header: String): List<ProcessMemory> {
        val lines = dump.lineSequence().iterator()
        // Skip to the section header.
        while (lines.hasNext()) {
            if (lines.next().trim().equals(header, ignoreCase = true)) break
            if (!lines.hasNext()) return emptyList()
        }

        val out = mutableListOf<ProcessMemory>()
        var category = "Unknown"
        while (lines.hasNext()) {
            val line = lines.next()
            // The section ends at a blank line or the next "Total ..." header.
            if (line.isBlank() || line.trimStart().startsWith("Total ")) break

            val p = PROCESS.find(line)
            if (p != null) {
                val kb = p.groupValues[1].replace(",", "").toLongOrNull() ?: continue
                val pid = p.groupValues[3].toIntOrNull() ?: continue
                out += ProcessMemory(p.groupValues[2], pid, kb, category)
                continue
            }
            CATEGORY.find(line)?.let { category = it.groupValues[2].trim() }
        }
        return out
    }
}
