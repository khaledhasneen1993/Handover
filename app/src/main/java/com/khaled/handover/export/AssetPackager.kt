package com.khaled.handover.export

import android.content.Context
import android.net.Uri
import com.khaled.handover.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Explicitly shared, inspection-scoped originals + structured metadata.
 * This is NOT a backup and cannot be restored with BackupManager.
 * Includes user-entered identifiers; the UI must obtain a separate share action.
 */
class AssetPackager(private val context: Context, private val repo: InspectionRepository) {
    private fun obj(vararg fields: Pair<String, Any?>): JSONObject = JSONObject().also { result ->
        fields.forEach { (name,value) -> result.put(name, value ?: JSONObject.NULL) }
    }
    private fun array(records: List<JSONObject>): JSONArray = JSONArray().also { a -> records.forEach { record -> a.put(record) } }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    suspend fun export(inspectionId: String, destination: Uri) = withContext(Dispatchers.IO) {
        val inspection = repo.dao.getInspection(inspectionId) ?: error("Operation not found")
        val sessions = repo.dao.sessions(inspectionId)
        val items = repo.dao.items(inspectionId)
        val states = sessions.flatMap { repo.dao.states(it.id) }
        val media = sessions.flatMap { repo.dao.sessionMedia(it.id) }
        val observations = repo.dao.observations(inspectionId)
        val accessories = repo.dao.accessories(inspectionId)
        val snapshot = repo.dao.snapshot(inspectionId)
        val pairs = media.mapNotNull { asset -> repo.dao.pairForReturn(asset.id) }.distinctBy { it.id }
        val annotations = media.flatMap { repo.dao.annotations(it.id) }

        val data = obj(
            "format" to "handover-assets", "schema" to 1,
            "exportedAtDeviceClock" to System.currentTimeMillis(),
            "inspection" to obj(
                "id" to inspection.id,"title" to inspection.title,"category" to inspection.category,
                "context" to inspection.context,"role" to inspection.role,"status" to inspection.status,
                "createdAt" to inspection.createdAt,"updatedAt" to inspection.updatedAt,
                "dueAt" to inspection.dueAt,"partyName" to inspection.partyName,
                "reference" to inspection.reference,"description" to inspection.description,
                "depositText" to inspection.depositText,"notes" to inspection.notes
            ),
            "templateSnapshot" to (snapshot?.let { obj("schemaVersion" to it.schemaVersion,"snapshotJson" to it.snapshotJson) }
                ?: JSONObject.NULL),
            "sessions" to array(sessions.map { obj("id" to it.id,"phase" to it.phase,"startedAt" to it.startedAt,
                "completedAt" to it.completedAt,"zoneId" to it.zoneId,"revision" to it.revision) }),
            "checklist" to array(items.map { obj("id" to it.id,"key" to it.key,"group" to it.groupName,
                "label" to it.label,"hint" to it.hint,"position" to it.position) }),
            "pointStates" to array(states.map { obj("sessionId" to it.sessionId,"itemId" to it.itemId,
                "status" to it.captureStatus,"skipReason" to it.skipReason) }),
            "media" to array(media.map { obj("id" to it.id,"sessionId" to it.sessionId,"itemId" to it.itemId,
                "path" to it.relativePath,"mimeType" to it.mimeType,"size" to it.size,
                "width" to it.width,"height" to it.height,"sha256" to it.sha256,"source" to it.source,
                "importedAt" to it.importedAt,"capturedAt" to it.capturedAt,
                "timestampSource" to it.timestampSource,"location" to it.locationText) }),
            "annotations" to array(annotations.map { obj("id" to it.id,"assetId" to it.assetId,
                "geometry" to it.geometryJson,"type" to it.kind,"text" to it.text,"revision" to it.revision) }),
            "observations" to array(observations.map { obj("id" to it.id,"itemId" to it.itemId,
                "assetIds" to it.relatedAssetIdsJson,"category" to it.category,"text" to it.text,
                "userAssessment" to it.assessment) }),
            "comparisonPairs" to array(pairs.map { obj("id" to it.id,"initialAssetId" to it.baselineAssetId,
                "returnAssetId" to it.returnAssetId,"userAssessment" to it.assessment,"note" to it.note) }),
            "accessories" to array(accessories.map { obj("id" to it.id,"name" to it.name,
                "initialQuantity" to it.baselineQuantity,"returnQuantity" to it.returnQuantity,"note" to it.note) })
        )
        val stage = File.createTempFile("handover-assets-", ".zip", context.cacheDir)
        try {
            val manifest = obj("format" to "handover-asset-manifest", "schema" to 1,
                "inspectionId" to inspection.id, "entries" to JSONArray())
            val entries = manifest.getJSONArray("entries")
            ZipOutputStream(FileOutputStream(stage).buffered()).use { zip ->
                val json = data.toString(2).toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("data.json")); zip.write(json); zip.closeEntry()
                entries.put(obj("path" to "data.json","size" to json.size,"sha256" to sha(json)))
                for (asset in media) {
                    require(asset.relativePath.matches(Regex("originals/[0-9a-f-]{36}\\.(jpg|png|webp|heic)")))
                    require(repo.assets.verify(asset)) { "Missing or altered original: ${asset.id}" }
                    val original = repo.assets.resolve(asset.relativePath)
                    zip.putNextEntry(ZipEntry(asset.relativePath))
                    original.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    entries.put(obj("path" to asset.relativePath,"size" to asset.size,"sha256" to asset.sha256))
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
            }
            context.contentResolver.openOutputStream(destination, "w")?.use { stream ->
                stage.inputStream().use { it.copyTo(stream) }
                stream.flush()
            } ?: error("Cannot open export destination")
        } finally {stage.delete()}
    }
}
