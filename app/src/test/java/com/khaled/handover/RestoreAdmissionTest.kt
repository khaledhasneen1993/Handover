package com.khaled.handover

import com.khaled.handover.backup.*
import org.junit.Assert.*
import org.junit.Test

class RestoreAdmissionTest {
    private val id="3edb8d38-6ef0-423f-aaf1-7416a6c4e649"
    private val session="7edb8d38-6ef0-423f-aaf1-7416a6c4e649"
    private val path="originals/$id.jpg"
    private val hash="f".repeat(64)
    private val files=mapOf("manifest.json" to BackupEntryDigest(10,"a".repeat(64)),
        "data.json" to BackupEntryDigest(20,"b".repeat(64)),path to BackupEntryDigest(100,hash))
    private fun media()=RestorableMedia(id,session,session,path,"thumbnails/$id.jpg","image/jpeg",100,300,200,hash)
    private fun rejected(action:()->Unit) {
        try { action(); fail("Expected rejection") } catch (_:IllegalArgumentException) {}
    }
    @Test fun validMediaAccepted() { RestoreAdmission.validateReferencedAssets(listOf(media()),emptyList(),files) }
    @Test fun thumbnailCannotOverwriteOriginal()=rejected {
        RestoreAdmission.validateReferencedAssets(listOf(media().copy(untrustedThumbnailPath=path)),emptyList(),files)
    }
    @Test fun thumbnailCannotOverwriteAnotherImage()=rejected {
        RestoreAdmission.validateReferencedAssets(listOf(media().copy(untrustedThumbnailPath="thumbnails/$session.jpg")),emptyList(),files)
    }
    @Test fun originalMustBeInsideOriginals()=rejected {
        RestoreAdmission.validateReferencedAssets(listOf(media().copy(originalPath="../$path")),emptyList(),files)
    }
    @Test fun originalBytesAndMetadataMustMatch()=rejected {
        RestoreAdmission.validateReferencedAssets(listOf(media().copy(size=99)),emptyList(),files)
    }
    @Test fun recordHashMismatchRejected()=rejected {
        RestoreAdmission.validateReferencedAssets(listOf(media().copy(sha256="0".repeat(64))),emptyList(),files)
    }
    @Test fun malformedIdentityRejected()=rejected {
        RestoreAdmission.validateReferencedAssets(listOf(media().copy(id="../source")),emptyList(),files)
    }
    @Test fun typeMismatchRejected()=rejected {
        RestoreAdmission.validateReferencedAssets(listOf(media().copy(mimeType="image/png")),emptyList(),files)
    }
    @Test fun unreferencedFilesRejected()=rejected {
        RestoreAdmission.validateReferencedAssets(listOf(media()),emptyList(),files+
            ("exports/$session.pdf" to BackupEntryDigest(100,hash)))
    }
}
