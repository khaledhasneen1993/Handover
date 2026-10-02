import com.khaled.handover.backup.*

fun rejection(label: String, checkThatFails: () -> Unit) {
    try { checkThatFails(); error("Accepted bad archive: $label") }
    catch (_: IllegalArgumentException) { }
}
val photo = "originals/3edb8d38-6ef0-423f-aaf1-7416a6c4e649.jpg"
val actual = mapOf("manifest.json" to BackupEntryDigest(20, "e".repeat(64)),
    "data.json" to BackupEntryDigest(10, "d".repeat(64)),
    photo to BackupEntryDigest(100, "f".repeat(64)))
val entries = listOf(DeclaredBackupEntry("data.json", 10, "d".repeat(64)),
    DeclaredBackupEntry(photo, 100, "f".repeat(64)))
ArchivePolicy.verifyManifest(entries, actual)
check(!ArchivePolicy.accepts("../secret"))
check(!ArchivePolicy.accepts("originals/../../private"))
rejection("duplicate manifest claims") { ArchivePolicy.verifyManifest(listOf(entries[0], entries[0]), actual) }
rejection("undeclared member") { ArchivePolicy.verifyManifest(entries,
    actual + ("exports/4edb8d38-6ef0-423f-aaf1-7416a6c4e649.pdf" to BackupEntryDigest(5, "a".repeat(64)))) }
rejection("wrong digest") { ArchivePolicy.verifyManifest(entries, actual + (photo to BackupEntryDigest(100, "0".repeat(64)))) }
rejection("truncated member") { ArchivePolicy.verifyManifest(entries, actual + (photo to BackupEntryDigest(50, "f".repeat(64)))) }
println("PASS: backup manifest valid case, 2 path traversal cases, duplicate/undeclared/digest/size rejection")
