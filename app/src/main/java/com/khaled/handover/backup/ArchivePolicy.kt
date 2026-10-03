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


/** Android-independent restore admission: thumbnail destinations are derived from validated IDs. */
data class RestorableMedia(
    val id: String, val sessionId: String, val itemId: String,
    val originalPath: String, val untrustedThumbnailPath: String?,
    val mimeType: String, val size: Long, val width: Int, val height: Int,
    val sha256: String
)
object RestoreAdmission {
    private val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val hash = Regex("[0-9a-f]{64}")
    private val ext = mapOf("image/jpeg" to "jpg", "image/png" to "png", "image/webp" to "webp", "image/heic" to "heic", "image/heif" to "heic")
    fun validUuid(id: String): Boolean = uuid.matches(id) &&
        runCatching { java.util.UUID.fromString(id).toString() == id }.getOrDefault(false)
    fun thumbnailFor(id: String): String {
        require(validUuid(id)) { "Invalid media ID" }
        return "thumbnails/$id.jpg"
    }
    fun validateMedia(row: RestorableMedia, files: Map<String, BackupEntryDigest>) {
        require(validUuid(row.id) && validUuid(row.sessionId) && validUuid(row.itemId)) { "Invalid media IDs" }
        val suffix = ext[row.mimeType] ?: throw IllegalArgumentException("Unsupported image type")
        require(row.originalPath == "originals/${row.id}.$suffix") { "Unexpected original destination" }
        require(row.untrustedThumbnailPath == thumbnailFor(row.id)) { "Invalid thumbnail destination" }
        require(row.size in 1..100_000_000L && row.width in 1..100_000 && row.height in 1..100_000) { "Invalid media size/dimensions" }
        require(hash.matches(row.sha256) && files[row.originalPath] == BackupEntryDigest(row.size, row.sha256)) { "Original record does not match bytes" }
    }
    fun validateReferencedAssets(media: List<RestorableMedia>, reports: List<String>, files: Map<String, BackupEntryDigest>) {
        require(media.size <= ArchivePolicy.maxEntries && reports.size <= ArchivePolicy.maxEntries)
        require(media.map { it.id }.distinct().size == media.size) { "Duplicate media identity" }
        media.forEach { validateMedia(it, files) }
        require(reports.all { it.matches(Regex("exports/[0-9a-f-]{36}\\.pdf")) &&
            validUuid(it.removePrefix("exports/").removeSuffix(".pdf")) && it in files }) { "Invalid report path" }
        val expected = media.map { it.originalPath }.toSet() + reports.toSet() + setOf("manifest.json", "data.json")
        require(expected.size == media.size + reports.size + 2 && files.keys == expected) { "Unreferenced or duplicate archive member" }
    }
}
