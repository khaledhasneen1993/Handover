package com.khaled.handover.report

import android.content.Context
import android.net.Uri
import com.khaled.handover.data.InspectionRepository
import com.khaled.handover.data.MediaAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** One operation's untouched originals and a documented v1 manifest; not an app backup. */
class AssetPackageExporter(private val context: Context, private val repository: InspectionRepository) {
    suspend fun export(inspectionId: String, destination: Uri): Unit = withContext(Dispatchers.IO) {
        val dao = repository.dao
        val inspection = dao.getInspection(inspectionId) ?: error("Operation not found")
        val sessions = dao.sessions(inspectionId)
        val checkpoints = dao.items(inspectionId).associateBy { it.id }
        val file = File.createTempFile("handover-assets-", ".zip", context.cacheDir)
        try {
            val metadata = JSONArray()
            val assets = sessions.flatMap { session ->
                dao.sessionMedia(session.id).map { asset -> session to asset }
            }
            require(assets.size <= 10_000) { "Too many photos for one package" }
            ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
                assets.forEach { (session, asset) ->
                    val original = repository.assets.resolve(asset.relativePath)
                    require(repository.assets.verify(asset)) { "Original missing or changed: ${asset.id}" }
                    val packagePath = "media/${original.name}"
                    zip.putNextEntry(ZipEntry(packagePath))
                    original.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                    metadata.put(assetMetadata(session.phase, session.zoneId, asset, packagePath,
                        checkpoints[asset.itemId]?.label.orEmpty()))
                }
                val manifest = JSONObject()
                    .put("format", "handover-asset-package")
                    .put("schema", 1)
                    .put("operationId", inspection.id)
                    .put("operationTitle", inspection.title)
                    .put("category", inspection.category)
                    .put("context", inspection.context)
                    .put("generatedAtEpochMillis", System.currentTimeMillis())
                    .put("notice", "User-provided photos and metadata. File hashes detect changes relative to this package, not when or where a photograph was actually taken.")
                    .put("assets", metadata)
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            // The document provider may not support atomic replacement: a provider failure
            // is surfaced to the user; no original app asset is touched.
            val stream = context.contentResolver.openOutputStream(destination, "w")
                ?: error("Cannot write the selected destination")
            stream.use { target -> file.inputStream().buffered().use { it.copyTo(target) } }
        } finally {
            file.delete()
        }
    }

    private fun assetMetadata(phase: String, zone: String, asset: MediaAsset, path: String, point: String): JSONObject = JSONObject()
        .put("id", asset.id)
        .put("sessionPhase", phase)
        .put("sessionZoneId", zone)
        .put("checklistPoint", point)
        .put("path", path)
        .put("sha256", asset.sha256)
        .put("mimeType", asset.mimeType)
        .put("sizeBytes", asset.size)
        .put("width", asset.width)
        .put("height", asset.height)
        .put("source", asset.source)
        .put("importedAtEpochMillis", asset.importedAt)
        .put("capturedAtEpochMillis", asset.capturedAt ?: JSONObject.NULL)
        .put("timestampSource", asset.timestampSource)
}
