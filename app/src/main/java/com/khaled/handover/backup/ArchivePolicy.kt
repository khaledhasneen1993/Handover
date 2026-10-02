package com.khaled.handover.backup

/** Device-independent backup admission policy; used before any restored file moves to private storage. */
data class BackupEntryDigest(val size: Long, val sha256: String)
data class DeclaredBackupEntry(val path: String, val size: Long, val sha256: String)

object ArchivePolicy {
    const val maxEntries = 10_000
    const val maxUncompressedBytes = 1_200_000_000L
    private val image = Regex("originals/[0-9a-f-]{36}\\.(jpg|png|webp|heic)")
    private val report = Regex("exports/[0-9a-f-]{36}\\.pdf")
    private val hash = Regex("[0-9a-f]{64}")

    fun accepts(path: String): Boolean = path == "manifest.json" || path == "data.json" ||
        image.matches(path) || report.matches(path)

    fun limit(path: String): Long = when {
        path == "manifest.json" || path == "data.json" -> 20_000_000L
        report.matches(path) -> 250_000_000L
        image.matches(path) -> 100_000_000L
        else -> error("Unexpected backup member")
    }

    /** Reject extra members, duplicate manifest claims, missing members and unexpected hashes/sizes. */
    fun verifyManifest(declared: List<DeclaredBackupEntry>, actual: Map<String, BackupEntryDigest>) {
        require("manifest.json" in actual && "data.json" in actual) { "Manifest/data missing" }
        require(actual.size <= maxEntries && declared.size == actual.size - 1) { "Backup member count mismatch" }
        val seen = mutableSetOf<String>()
        for (entry in declared) {
            require(entry.path != "manifest.json" && accepts(entry.path) && seen.add(entry.path)) {
                "Duplicate or invalid manifest claim"
            }
            val digest = actual[entry.path] ?: error("Declared backup member missing")
            require(entry.size in 0..limit(entry.path) && entry.size == digest.size &&
                hash.matches(entry.sha256) && entry.sha256 == digest.sha256) { "Backup checksum/size mismatch" }
        }
        require(seen == actual.keys - "manifest.json") { "Undeclared backup member" }
    }
}
