package com.khaled.handover.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "inspections", indices = [Index("updatedAt")])
data class Inspection(
    @PrimaryKey val id: String, val title: String, val category: String, val context: String = "OTHER",
    val role: String = "UNSPECIFIED", val status: String = Progress.DRAFT,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val dueAt: Long? = null, val reminderLeadMinutes: Int? = null, val partyName: String? = null,
    val reference: String? = null, val description: String? = null,
    val depositText: String? = null, val notes: String? = null
)
@Entity(tableName = "template_snapshots", foreignKeys = [ForeignKey(entity = Inspection::class, parentColumns = ["id"], childColumns = ["inspectionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("inspectionId")])
data class TemplateSnapshot(@PrimaryKey val id: String, val inspectionId: String, val schemaVersion: Int = 1, val snapshotJson: String, val capturedAt: Long)
@Entity(tableName = "sessions", foreignKeys = [ForeignKey(entity = Inspection::class, parentColumns = ["id"], childColumns = ["inspectionId"], onDelete = ForeignKey.CASCADE)], indices = [Index(value = ["inspectionId", "phase"], unique = true)])
data class CaptureSession(@PrimaryKey val id: String, val inspectionId: String, val phase: String, val startedAt: Long, val completedAt: Long? = null, val zoneId: String, val revision: Int = 0)
@Entity(tableName = "checklist", foreignKeys = [ForeignKey(entity = Inspection::class, parentColumns = ["id"], childColumns = ["inspectionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("inspectionId")])
data class ChecklistItem(@PrimaryKey val id: String, val inspectionId: String, val groupName: String, val label: String, val hint: String, val position: Int, val key: String)
@Entity(tableName = "item_states", primaryKeys = ["itemId", "sessionId"], foreignKeys = [ForeignKey(entity = ChecklistItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = CaptureSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("sessionId")])
data class ItemSessionState(val itemId: String, val sessionId: String, val captureStatus: String = Capture.NOT_CAPTURED, val skipReason: String? = null)
@Entity(tableName = "media", foreignKeys = [ForeignKey(entity = CaptureSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = ChecklistItem::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = ForeignKey.CASCADE)], indices = [Index("sessionId"), Index("itemId"), Index("sha256")])
data class MediaAsset(
    @PrimaryKey val id: String, val sessionId: String, val itemId: String,
    val relativePath: String, val thumbnailPath: String,
    val mimeType: String, val size: Long, val width: Int, val height: Int,
    val sha256: String, val source: String, val importedAt: Long,
    val capturedAt: Long? = null, val timestampSource: String = "UNKNOWN",
    val locationText: String? = null
)
@Entity(tableName = "annotations", foreignKeys = [ForeignKey(entity = MediaAsset::class, parentColumns = ["id"], childColumns = ["assetId"], onDelete = ForeignKey.CASCADE)], indices = [Index("assetId")])
data class Annotation(@PrimaryKey val id: String, val assetId: String, val geometryJson: String, val kind: String, val text: String, val revision: Int = 1)
@Entity(tableName = "observations", foreignKeys = [ForeignKey(entity = Inspection::class, parentColumns = ["id"], childColumns = ["inspectionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("inspectionId"), Index("itemId")])
data class Observation(@PrimaryKey val id: String, val inspectionId: String, val itemId: String, val relatedAssetIdsJson: String, val category: String, val text: String, val assessment: String)
@Entity(tableName = "comparison_pairs", foreignKeys = [ForeignKey(entity = MediaAsset::class, parentColumns = ["id"], childColumns = ["baselineAssetId"], onDelete = ForeignKey.CASCADE), ForeignKey(entity = MediaAsset::class, parentColumns = ["id"], childColumns = ["returnAssetId"], onDelete = ForeignKey.CASCADE)], indices = [Index("baselineAssetId"), Index("returnAssetId", unique = true)])
data class ComparisonPair(@PrimaryKey val id: String, val baselineAssetId: String, val returnAssetId: String, val assessment: String = Assessment.NONE, val note: String = "")
@Entity(tableName = "accessories", foreignKeys = [ForeignKey(entity = Inspection::class, parentColumns = ["id"], childColumns = ["inspectionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("inspectionId")])
data class Accessory(@PrimaryKey val id: String, val inspectionId: String, val name: String, val baselineQuantity: Int, val returnQuantity: Int? = null, val note: String = "")
@Entity(tableName = "reports", foreignKeys = [ForeignKey(entity = Inspection::class, parentColumns = ["id"], childColumns = ["inspectionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("inspectionId")])
data class ReportRecord(@PrimaryKey val id: String, val inspectionId: String, val reportType: String, val sourceRevision: Int, val createdAt: Long, val language: String, val relativePath: String, val privacyJson: String)
@Entity(tableName = "revision_events", foreignKeys = [ForeignKey(entity = Inspection::class, parentColumns = ["id"], childColumns = ["inspectionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("inspectionId")])
data class RevisionEvent(@PrimaryKey val id: String, val inspectionId: String, val sessionId: String?, val revision: Int, val event: String, val at: Long)

@Dao interface InspectionDao {
    @Query("SELECT * FROM inspections ORDER BY updatedAt DESC") fun watchInspections(): Flow<List<Inspection>>
    @Query("SELECT * FROM inspections ORDER BY updatedAt DESC") suspend fun allInspections(): List<Inspection>
    @Query("SELECT * FROM inspections WHERE id=:id LIMIT 1") fun watchInspection(id: String): Flow<Inspection?>
    @Query("SELECT * FROM inspections WHERE id=:id LIMIT 1") suspend fun getInspection(id: String): Inspection?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertInspection(x: Inspection)
    @Update suspend fun updateInspection(x: Inspection)
    @Query("DELETE FROM inspections WHERE id=:id") suspend fun deleteInspection(id: String)
    @Insert suspend fun addSnapshot(x: TemplateSnapshot)
    @Query("SELECT * FROM template_snapshots WHERE inspectionId=:id LIMIT 1") suspend fun snapshot(id: String): TemplateSnapshot?
    @Query("SELECT * FROM sessions WHERE inspectionId=:id ORDER BY startedAt") fun watchSessions(id: String): Flow<List<CaptureSession>>
    @Query("SELECT * FROM sessions WHERE inspectionId=:id ORDER BY startedAt") suspend fun sessions(id: String): List<CaptureSession>
    @Query("SELECT * FROM sessions WHERE inspectionId=:id AND phase=:phase LIMIT 1") suspend fun session(id: String, phase: String): CaptureSession?
    @Query("SELECT * FROM sessions WHERE id=:id LIMIT 1") suspend fun sessionById(id: String): CaptureSession?
    @Insert suspend fun insertSession(x: CaptureSession)
    @Update suspend fun updateSession(x: CaptureSession)
    @Insert suspend fun insertItems(xs: List<ChecklistItem>)
    @Query("SELECT * FROM checklist WHERE inspectionId=:id ORDER BY position") fun watchItems(id: String): Flow<List<ChecklistItem>>
    @Query("SELECT * FROM checklist WHERE inspectionId=:id ORDER BY position") suspend fun items(id: String): List<ChecklistItem>
    @Insert suspend fun insertStates(xs: List<ItemSessionState>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setState(x: ItemSessionState)
    @Query("SELECT * FROM item_states WHERE sessionId=:id") fun watchStates(id: String): Flow<List<ItemSessionState>>
    @Query("SELECT * FROM item_states WHERE sessionId=:id") suspend fun states(id: String): List<ItemSessionState>
    @Query("SELECT * FROM media WHERE itemId=:id AND sessionId=:sessionId ORDER BY importedAt") suspend fun itemMedia(id: String, sessionId: String): List<MediaAsset>
    @Query("SELECT * FROM media WHERE sessionId=:id ORDER BY importedAt") suspend fun sessionMedia(id: String): List<MediaAsset>
    @Query("SELECT * FROM media WHERE id=:id LIMIT 1") suspend fun mediaById(id: String): MediaAsset?
    @Insert suspend fun addMedia(x: MediaAsset)
    @Query("SELECT * FROM media WHERE sessionId=:id ORDER BY importedAt") fun watchMedia(id: String): Flow<List<MediaAsset>>
    @Insert suspend fun addPair(x: ComparisonPair)
    @Query("SELECT * FROM comparison_pairs WHERE returnAssetId=:id LIMIT 1") suspend fun pairForReturn(id: String): ComparisonPair?
    @Query("SELECT * FROM comparison_pairs WHERE returnAssetId=:id LIMIT 1") fun watchPair(id: String): Flow<ComparisonPair?>
    @Update suspend fun updatePair(x: ComparisonPair)
    @Query("SELECT * FROM accessories WHERE inspectionId=:id ORDER BY name") fun watchAccessories(id: String): Flow<List<Accessory>>
    @Query("SELECT * FROM accessories WHERE inspectionId=:id ORDER BY name") suspend fun accessories(id: String): List<Accessory>
    @Insert suspend fun insertAccessory(x: Accessory)
    @Update suspend fun updateAccessory(x: Accessory)
    @Query("DELETE FROM accessories WHERE id=:id") suspend fun deleteAccessory(id: String): Int
    @Insert suspend fun insertAnnotation(x: Annotation)
    @Query("SELECT * FROM annotations WHERE assetId=:id") suspend fun annotations(id: String): List<Annotation>
    @Insert suspend fun insertObservation(x: Observation)
    @Query("SELECT * FROM observations WHERE inspectionId=:id") suspend fun observations(id: String): List<Observation>
    @Insert suspend fun insertReport(x: ReportRecord)
    @Query("SELECT * FROM reports WHERE inspectionId=:id ORDER BY createdAt DESC") suspend fun reports(id: String): List<ReportRecord>
    @Insert suspend fun addRevision(x: RevisionEvent)
    @Query("SELECT MAX(revision) FROM revision_events WHERE inspectionId=:id") suspend fun revision(id: String): Int?
}

@Database(entities = [Inspection::class, TemplateSnapshot::class, CaptureSession::class, ChecklistItem::class, ItemSessionState::class, MediaAsset::class, Annotation::class, Observation::class, ComparisonPair::class, Accessory::class, ReportRecord::class, RevisionEvent::class], version = 1, exportSchema = true)
abstract class HandoverDb : RoomDatabase() { abstract fun dao(): InspectionDao }
