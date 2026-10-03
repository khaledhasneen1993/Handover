package com.khaled.handover.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.khaled.handover.media.AssetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.ZoneId

class InspectionRepository(ctx: Context, val db: HandoverDb) {
    val dao = db.dao()
    val assets = AssetStore(ctx)
    val inspections: Flow<List<Inspection>> get() = dao.watchInspections()
    /** Runs at startup, after journal recovery and before UI access; never removes referenced originals. */
    suspend fun recoverOrphanOriginals() = withContext(Dispatchers.IO) {
        val referenced=dao.allInspections().flatMap { inspection ->
            dao.sessions(inspection.id).flatMap { dao.sessionMedia(it.id) }
        }.mapTo(mutableSetOf()) { it.relativePath.substringAfterLast('/') }
        val originals=File(assets.root,"originals")
        val managed=Regex("[0-9a-f-]{36}\\.(jpg|png|webp|heic|part)")
        originals.listFiles()?.forEach { file ->
            if(file.isFile && managed.matches(file.name) && file.name !in referenced) file.delete()
        }
        assets.deleteTemps()
    }
    private fun snapshot(specs: List<PointSpec>) = JSONArray().also { j -> specs.forEach { j.put(JSONObject().put("key",it.key).put("group",it.group).put("label",it.label).put("hint",it.hint)) } }.toString()

    suspend fun create(title: String, category: String, context: String, role: String, rooms: List<String> = emptyList()): String {
        require(title.isNotBlank() && title.length <= 120)
        val id = newId(); val now = System.currentTimeMillis()
        val specs = Templates.forCategory(category, if (category == "APARTMENT" && rooms.isNotEmpty()) rooms else listOf("Living room", "Kitchen", "Bathroom"))
        require(specs.isNotEmpty())
        db.withTransaction {
            dao.insertInspection(Inspection(id, title.trim(), category, context, role, createdAt = now, updatedAt = now))
            dao.addSnapshot(TemplateSnapshot(newId(), id, 1, snapshot(specs), now))
            dao.insertItems(specs.mapIndexed { i, p -> ChecklistItem(newId(), id, p.group, p.label, p.hint, i, p.key) })
            newSessionInternal(id, Phase.BASELINE)
        }
        return id
    }
    private suspend fun newSessionInternal(id: String, phase: String): CaptureSession {
        val session = CaptureSession(newId(), id, phase, System.currentTimeMillis(), zoneId = ZoneId.systemDefault().id)
        dao.insertSession(session)
        dao.insertStates(dao.items(id).map { ItemSessionState(it.id, session.id) })
        return session
    }
    suspend fun ensureSession(id: String, phase: String): CaptureSession = db.withTransaction {
        require(phase == Phase.BASELINE || phase == Phase.RETURN) { "Unknown session phase" }
        val inspection = dao.getInspection(id) ?: error("Inspection not found")
        if (phase == Phase.RETURN) require(dao.session(id, Phase.BASELINE)?.completedAt != null) { "Complete first session before returning" }
        dao.session(id, phase) ?: newSessionInternal(id, phase).also {
            dao.updateInspection(inspection.copy(status = Progress.AWAITING_RETURN, updatedAt = System.currentTimeMillis()))
        }
    }
    suspend fun capture(temp: File, session: CaptureSession, itemId: String): MediaAsset = commitMedia(session, itemId) { assets.addCapture(temp, session.id, itemId) }
    suspend fun import(uri: Uri, session: CaptureSession, itemId: String): MediaAsset = commitMedia(session, itemId) { assets.addFromUri(uri, session.id, itemId) }
    private suspend fun commitMedia(session: CaptureSession, itemId: String, obtain: suspend () -> MediaAsset): MediaAsset {
        val media = obtain()
        try {
            db.withTransaction {
                dao.addMedia(media)
                dao.setState(ItemSessionState(itemId, session.id, Capture.CAPTURED))
                if (session.phase == Phase.RETURN) dao.itemMedia(itemId, dao.session(session.inspectionId, Phase.BASELINE)?.id ?: "")
                    .firstOrNull()?.let { dao.addPair(ComparisonPair(newId(), it.id, media.id)) }
                recordChange(session.inspectionId, session.id, "MEDIA_ADDED")
            }
        } catch (e: Exception) { assets.resolve(media.relativePath).delete(); assets.resolve(media.thumbnailPath).delete(); throw e }
        return media
    }
    private suspend fun recordChange(id: String, sessionId: String?, event: String) {
        val rev = (dao.revision(id) ?: 0) + 1
        dao.addRevision(RevisionEvent(newId(), id, sessionId, rev, event, System.currentTimeMillis()))
        dao.getInspection(id)?.let { dao.updateInspection(it.copy(updatedAt = System.currentTimeMillis())) }
        if (sessionId != null) {
            dao.sessions(id).firstOrNull { it.id == sessionId && it.completedAt != null }?.let {
                dao.updateSession(it.copy(revision = rev))
            }
        }
    }
    suspend fun setStatus(id: String, session: CaptureSession, state: String, reason: String? = null) = db.withTransaction {
        if (dao.itemMedia(id, session.id).isNotEmpty() && state != Capture.CAPTURED) error("Cannot mark a photographed point as skipped")
        dao.setState(ItemSessionState(id, session.id, state, reason))
        recordChange(session.inspectionId, session.id, "CHECKLIST_UPDATED")
    }
    suspend fun complete(session: CaptureSession) = db.withTransaction {
        val current = dao.session(session.inspectionId, session.phase) ?: error("Session missing")
        if (current.completedAt != null) return@withTransaction
        val inspection = dao.getInspection(session.inspectionId) ?: error("Inspection missing")
        require(current.completedAt == null) { "Session is already complete. New changes are recorded as revisions." }
        require(dao.states(current.id).none { it.captureStatus == Capture.NOT_CAPTURED }) { "Document or explicitly skip pending checklist points" }
        val rev = (dao.revision(session.inspectionId) ?: 0) + 1
        dao.updateSession(current.copy(completedAt = System.currentTimeMillis(), revision = rev))
        dao.updateInspection(inspection.copy(status = nextStatus(inspection.status, session.phase), updatedAt = System.currentTimeMillis()))
        dao.addRevision(RevisionEvent(newId(), session.inspectionId, session.id, rev, "SESSION_COMPLETED", System.currentTimeMillis()))
    }
    /** Pairing is explicit per checklist item; never infer a match solely from photo chronology. */
    suspend fun updateComparison(
        inspectionId: String, returnAssetId: String, baselineAssetId: String,
        assessment: String, note: String = ""
    ) = db.withTransaction {
        require(assessment in setOf(Assessment.NONE, Assessment.NO_VISIBLE_DIFFERENCE,
            Assessment.PRE_EXISTING, Assessment.RETURN_DIFFERENCE, Assessment.CANNOT_COMPARE))
        val baseline = dao.mediaById(baselineAssetId) ?: error("Initial image not found")
        val returning = dao.mediaById(returnAssetId) ?: error("Return image not found")
        require(baseline.itemId == returning.itemId) { "Images must document the same checkpoint" }
        val first = dao.session(inspectionId, Phase.BASELINE) ?: error("Initial session missing")
        val second = dao.session(inspectionId, Phase.RETURN) ?: error("Return session missing")
        require(baseline.sessionId == first.id && returning.sessionId == second.id) { "Images belong to different sessions" }
        val current = dao.pairForReturn(returnAssetId)
        if (current == null) dao.addPair(ComparisonPair(newId(), baselineAssetId, returnAssetId, assessment, note.take(2_000)))
        else dao.updatePair(current.copy(baselineAssetId = baselineAssetId, assessment = assessment, note = note.take(2_000)))
        recordChange(inspectionId, second.id, "COMPARISON_UPDATED")
    }
    suspend fun note(id: String, itemId: String, mediaId: String, category: String, text: String, assessment: String) = db.withTransaction {
        dao.insertObservation(Observation(newId(), id, itemId, JSONArray().put(mediaId).toString(), category, text, assessment))
        recordChange(id, null, "OBSERVATION_ADDED")
    }
    suspend fun assessPair(id: String, pair: ComparisonPair, assessment: String) = db.withTransaction {
        require(assessment in setOf(Assessment.NONE, Assessment.NO_VISIBLE_DIFFERENCE,
            Assessment.PRE_EXISTING, Assessment.RETURN_DIFFERENCE, Assessment.CANNOT_COMPARE))
        val baseline = dao.mediaById(pair.baselineAssetId) ?: error("Initial image missing")
        val returned = dao.mediaById(pair.returnAssetId) ?: error("Return image missing")
        val first = dao.sessionById(baseline.sessionId) ?: error("Initial session missing")
        val second = dao.sessionById(returned.sessionId) ?: error("Return session missing")
        require(first.inspectionId == id && second.inspectionId == id &&
            first.phase == Phase.BASELINE && second.phase == Phase.RETURN && baseline.itemId == returned.itemId)
        val current = dao.pairForReturn(pair.returnAssetId) ?: error("Comparison not found")
        if (current.assessment != assessment) {
            dao.updatePair(current.copy(assessment = assessment))
            recordChange(id, second.id, "COMPARISON_UPDATED")
        }
    }
    suspend fun addAccessory(id: String, name: String, first: Int, second: Int?, note: String) = db.withTransaction {
        require(first >= 0 && (second == null || second >= 0))
        dao.insertAccessory(Accessory(newId(), id, name.trim(), first, second, note))
        recordChange(id, null, "ACCESSORY_ADDED")
    }
    suspend fun updateDue(id: String, dueAt: Long?, leadMinutes: Int?) = db.withTransaction {
        require(leadMinutes == null || leadMinutes in 0..10080)
        dao.getInspection(id)?.let { dao.updateInspection(it.copy(dueAt=dueAt, reminderLeadMinutes=leadMinutes,updatedAt=System.currentTimeMillis())) }
        recordChange(id,null,"REMINDER_UPDATED")
    }
    suspend fun archive(id: String) = db.withTransaction {
        dao.getInspection(id)?.let { dao.updateInspection(it.copy(status = Progress.ARCHIVED, updatedAt = System.currentTimeMillis())) }
        recordChange(id, null, "ARCHIVED")
    }
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val old = dao.sessions(id).flatMap { dao.sessionMedia(it.id) }
        val oldReports = dao.reports(id)
        db.withTransaction { dao.deleteInspection(id) }
        old.forEach { assets.resolve(it.relativePath).delete(); assets.resolve(it.thumbnailPath).delete() }
        oldReports.forEach { assets.resolve(it.relativePath).delete() }
    }
}
