package com.khaled.handover

import com.khaled.handover.backup.*
import org.junit.Assert.*
import org.junit.Test

class BackupPolicyTest {
    private val photo = "originals/3edb8d38-6ef0-423f-aaf1-7416a6c4e649.jpg"
    private val checksum = "f".repeat(64)
    private val manifest = "manifest.json" to BackupEntryDigest(20, "e".repeat(64))
    private val metadata = "data.json" to BackupEntryDigest(10, "d".repeat(64))
    private val content = photo to BackupEntryDigest(100, checksum)
    private val actual = mapOf(manifest, metadata, content)
    private val entries = listOf(DeclaredBackupEntry("data.json", 10, "d".repeat(64)),
        DeclaredBackupEntry(photo, 100, checksum))

    @Test fun `valid archive includes exactly declared members`() {
        ArchivePolicy.verifyManifest(entries, actual)
    }
    @Test fun `reject traversal and unknown member`() {
        assertFalse(ArchivePolicy.accepts("../files/originals/photo.jpg"))
        assertFalse(ArchivePolicy.accepts("originals/../../files/private"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `reject duplicate manifest`() = ArchivePolicy.verifyManifest(listOf(entries[0], entries[0]), actual)
    @Test(expected = IllegalArgumentException::class)
    fun `reject undeclared archive member`() = ArchivePolicy.verifyManifest(entries, actual +
        ("exports/4edb8d38-6ef0-423f-aaf1-7416a6c4e649.pdf" to BackupEntryDigest(9, "a".repeat(64))))
    @Test(expected = IllegalArgumentException::class)
    fun `reject checksum mismatch`() = ArchivePolicy.verifyManifest(entries,
        actual + (photo to BackupEntryDigest(100, "b".repeat(64))))
}
