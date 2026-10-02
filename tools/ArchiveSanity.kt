import com.khaled.handover.backup.*

fun rejected(label: String, block: () -> Unit) {
    try {block();error("FAIL: $label was incorrectly accepted")}
    catch (e: IllegalArgumentException) { println("PASS: rejected $label") }
    catch (e: IllegalStateException) {
        if (e.message?.startsWith("FAIL") == true) throw e
        println("PASS: rejected $label")
    }
}
fun main() {
    val original="originals/3edb8d38-6ef0-423f-aaf1-7416a6c4e649.jpg"
    val digest="a".repeat(64)
    val actual=mapOf(
        "manifest.json" to BackupEntryDigest(15,"b".repeat(64)),
        "data.json" to BackupEntryDigest(7,"c".repeat(64)),
        original to BackupEntryDigest(42,digest))
    val valid=listOf(DeclaredBackupEntry("data.json",7,"c".repeat(64)),DeclaredBackupEntry(original,42,digest))
    check(ArchivePolicy.accepts(original))
    check(!ArchivePolicy.accepts("../private.db"))
    check(!ArchivePolicy.accepts("originals/foo.png"))
    check(!ArchivePolicy.accepts("exports/x.pdf"))
    check(ArchivePolicy.limit(original)==100_000_000L)
    ArchivePolicy.verifyManifest(valid,actual)
    println("PASS: valid manifest")
    rejected("duplicate manifest names") {ArchivePolicy.verifyManifest(listOf(valid[0],valid[0]),actual)}
    rejected("undeclared different file") {ArchivePolicy.verifyManifest(listOf(valid[0],DeclaredBackupEntry(original.replace("3ed", "4ed"),42,digest)),actual)}
    rejected("modified photo") {ArchivePolicy.verifyManifest(valid,actual+ (original to BackupEntryDigest(42,"d".repeat(64))))}
    rejected("wrong size") {ArchivePolicy.verifyManifest(listOf(valid[0],valid[1].copy(size=41)),actual)}
    rejected("missing data.json") {ArchivePolicy.verifyManifest(valid,actual-"data.json")}
    rejected("manifest claims manifest") {ArchivePolicy.verifyManifest(listOf(valid[0],valid[1].copy(path="manifest.json")),actual)}
    rejected("unexpected extra member") {ArchivePolicy.verifyManifest(valid, actual+("exports/9edb8d38-6ef0-423f-aaf1-7416a6c4e649.pdf" to BackupEntryDigest(10,"e".repeat(64))))}
    println("PASS: archive policy checks")
}
