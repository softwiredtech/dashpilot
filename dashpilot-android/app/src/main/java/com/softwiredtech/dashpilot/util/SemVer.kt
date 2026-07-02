package com.softwiredtech.dashpilot.util

/**
 * Minimal Semantic Versioning (MAJOR.MINOR.PATCH) helper used to decide whether
 * a firmware release advertised in the update manifest is newer than what the
 * DashKit currently reports over BLE.
 */
object SemVer {

    /**
     * Parse a version string into [major, minor, patch]. Tolerates a leading
     * "v" and any pre-release / build metadata suffix (e.g. "1.2.3-rc1+build").
     * Returns null if the core version cannot be parsed.
     */
    fun parse(version: String?): IntArray? {
        if (version.isNullOrBlank()) return null
        var core = version.trim()
        if (core.startsWith("v") || core.startsWith("V")) core = core.substring(1)
        // Drop pre-release/build metadata.
        core = core.substringBefore('-').substringBefore('+').trim()
        val parts = core.split('.')
        if (parts.isEmpty()) return null
        val out = IntArray(3)
        for (i in 0 until 3) {
            out[i] = parts.getOrNull(i)?.toIntOrNull() ?: 0
        }
        // Require at least a valid major to consider it a version.
        if (parts[0].toIntOrNull() == null) return null
        return out
    }

    /**
     * Compare two versions. Returns a negative number if [a] < [b], zero if
     * equal, positive if [a] > [b], or null if either cannot be parsed.
     */
    fun compare(a: String?, b: String?): Int? {
        val pa = parse(a) ?: return null
        val pb = parse(b) ?: return null
        for (i in 0 until 3) {
            if (pa[i] != pb[i]) return pa[i] - pb[i]
        }
        return 0
    }

    /** True if [candidate] is strictly newer than [current]. */
    fun isNewer(candidate: String?, current: String?): Boolean {
        val c = compare(candidate, current) ?: return false
        return c > 0
    }
}
