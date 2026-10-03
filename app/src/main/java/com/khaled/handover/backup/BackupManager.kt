package com.khaled.handover.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.khaled.handover.data.InspectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Stable v1 format: magic + [salt|IV if encrypted] + authenticated AES-GCM ZIP. No cloud or device-bound keys. */
class BackupManager(private val context: Context, private val repo: InspectionRepository) {
    companion object {
        val TABLES = listOf("inspections", "template_snapshots", "sessions", "checklist", "item_states", "media",
            "annotations", "observations", "comparison_pairs", "accessories", "reports", "revision_events")
        private const val LIMIT = 1_200_000_000L
        private const val MAX_FILES = 10_000
        private const val ITERATIONS = 310_000
    }
    private val random = SecureRandom()
    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
    private fun tableData(): JSONObject {
        val result = JSONObject()
        val db = repo.db.openHelper.readableDatabase
        for (table in TABLES) {
            val rows = JSONArray()
            db.query("SELECT * FROM $table").use { cursor ->
                while (cursor.moveToNext()) {
                    val row = JSONObject()
                    for (i in 0 until cursor.columnCount) {
                        val value: Any? = when (cursor.getType(i)) {
                            android.database.Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                            android.database.Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                            else -> error("Unsupported backup column: ${cursor.getColumnName(i)}")
                        }
                        row.put(cursor.getColumnName(i), value)
                    }
                    rows.put(row)
                }
            }
            result.put(table, rows)
        }
        return result
    }
    suspend fun export(uri: Uri, password: CharArray? = null) = withContext(Dispatchers.IO) {
        val zip = File.createTempFile("handover-", ".zip", context.cacheDir)
        val output = File.createTempFile("handover-", ".backup", context.cacheDir)
        try {
            val snapshot = repo.db.withTransaction { tableData() }
            val media = snapshot.getJSONArray("media")
            val manifest = JSONObject().put("format", "handover-backup").put("schema", 1)
                .put("createdAt", System.currentTimeMillis()).put("entries", JSONArray())
            val entries = manifest.getJSONArray("entries")
            ZipOutputStream(BufferedOutputStream(FileOutputStream(zip))).use { out ->
                fun add(name: String, content: ByteArray) {
                    out.putNextEntry(ZipEntry(name)); out.write(content); out.closeEntry()
                    entries.put(JSONObject().put("path", name).put("sha256", sha(content)).put("size", content.size))
                }
                add("data.json", JSONObject().put("schema", 1).put("tables", snapshot).toString().toByteArray(Charsets.UTF_8))
                for (i in 0 until media.length()) {
                    val asset = media.getJSONObject(i)
                    val name = asset.getString("relativePath")
                    require(name.matches(Regex("originals/[0-9a-f-]{36}\\.(jpg|png|webp|heic)")))
                    val file = repo.assets.resolve(name)
                    require(file.isFile && repo.assets.sha256(file) == asset.getString("sha256")) { "Original missing or changed: $name" }
                    out.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                    entries.put(JSONObject().put("path",name).put("sha256",asset.getString("sha256")).put("size",file.length()))
                }
                val reports = snapshot.getJSONArray("reports")
                for (i in 0 until reports.length()) {
                    val report = reports.getJSONObject(i)
                    val name = report.getString("relativePath")
                    require(name.matches(Regex("exports/[0-9a-f-]{36}\\.pdf")))
                    val file = repo.assets.resolve(name)
                    require(file.isFile) { "Previously generated PDF missing: $name" }
                    out.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                    entries.put(JSONObject().put("path",name).put("sha256",repo.assets.sha256(file)).put("size",file.length()))
                }
                out.putNextEntry(ZipEntry("manifest.json")); out.write(manifest.toString().toByteArray(Charsets.UTF_8));out.closeEntry()
            }
            FileOutputStream(output).use { result ->
                if((password == null || password.isEmpty())) {result.write("HBK0".toByteArray());zip.inputStream().use{it.copyTo(result)}}
                else {
                    val salt = ByteArray(16).also { random.nextBytes(it) }; val iv = ByteArray(12).also {random.nextBytes(it)}
                    result.write("HBK1".toByteArray());result.write(salt);result.write(iv)
                    val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key(password!!,salt),GCMParameterSpec(128,iv))}
                    CipherOutputStream(result,cipher).use{enc->zip.inputStream().use{it.copyTo(enc)}}
                }
            }
            context.contentResolver.openOutputStream(uri,"w")?.use { dest -> output.inputStream().use{it.copyTo(dest)} }
                ?: error("Cannot open backup destination")
        } finally { password?.fill('\u0000');zip.delete();output.delete() }
    }
    private fun sha(bytes:ByteArray) = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private val journalFile: File get() = File(context.filesDir, "restore-journal.json")
    private fun persistJournal(value: JSONObject) {
        val temp = File(context.filesDir, "restore-journal.part")
        FileOutputStream(temp).use { out ->
            out.write(value.toString().toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        require(temp.renameTo(journalFile)) { "Cannot persist restore journal" }
    }
    /** Must finish before the app displays any restored records or accepts a new restore. */
    suspend fun recoverInterruptedRestore() = withContext(Dispatchers.IO) {
        if (!journalFile.exists()) return@withContext
        val log = JSONObject(journalFile.readText())
        // SQLite either committed the complete restore transaction or rolled it back.
        if (repo.dao.allInspections().isEmpty()) {
            val paths = log.getJSONArray("paths")
            for (i in 0 until paths.length()) {
                val path = paths.getString(i)
                require(ArchivePolicy.accepts(path) && path !in setOf("manifest.json","data.json"))
                repo.assets.resolve(path).delete()
            }
        }
        log.optString("stagingName").takeIf { it.matches(Regex("restore-[0-9a-f-]{36}")) }
            ?.let { File(context.cacheDir,it).deleteRecursively() }
        journalFile.delete()
    }
    suspend fun restore(uri: Uri, password: CharArray? = null) = withContext(Dispatchers.IO) {
        // Restore is intentionally create-only: an existing database is never overwritten or silently merged.
        require(!journalFile.exists()) { "Finish recovering an interrupted restore before retrying" }
        require(repo.dao.allInspections().isEmpty()) { "Restore requires a fresh empty app. Export existing operations first." }
        val incoming=File.createTempFile("handover-incoming-",".bak",context.cacheDir)
        val clear=File.createTempFile("handover-decrypted-",".zip",context.cacheDir)
        val stage=File(context.cacheDir,"restore-${java.util.UUID.randomUUID()}").also{it.mkdirs()}
        val moved=mutableListOf<File>()
        var databaseCommitted=false
        try {
            context.contentResolver.openInputStream(uri)?.use { src ->
                incoming.outputStream().use { out ->
                    val bytes=ByteArray(65536);var total=0L
                    while(true){val n=src.read(bytes);if(n<0)break;total+=n;require(total<=LIMIT+100_000_000L){"Backup too large"};out.write(bytes,0,n)}
                }
            }?:error("Backup inaccessible")
            incoming.inputStream().use { input ->
                val magic=ByteArray(4);require(input.read(magic)==4)
                when(String(magic,Charsets.US_ASCII)) {
                    "HBK0" -> clear.outputStream().use{out->input.copyTo(out)}
                    "HBK1" -> {
                        require((password != null && password.isNotEmpty())){"Backup password required"}
                        val salt=ByteArray(16);val iv=ByteArray(12)
                        require(input.read(salt)==16 && input.read(iv)==12)
                        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(password!!,salt),GCMParameterSpec(128,iv))}
                        CipherInputStream(input,cipher).use{stream->clear.outputStream().use{out->stream.copyTo(out)}}
                    }
                    else -> error("Not a Handover backup")
                }
            }
            val actual=mutableMapOf<String,Pair<Long,String>>()
            var entries=0;var expanded=0L
            ZipInputStream(BufferedInputStream(clear.inputStream())).use { zip ->
                while(true) {
                    val entry=zip.nextEntry ?: break
                    entries++;require(entries<=MAX_FILES){"Too many entries"}
                    val name=entry.name
                    require(!entry.isDirectory && ArchivePolicy.accepts(name)){"Invalid entry"}
                    require(name !in actual){"Duplicate backup entry"}
                    val destination=File(stage,name).canonicalFile
                    require(destination.path.startsWith(stage.canonicalPath+File.separator)){"Unsafe archive path"}
                    destination.parentFile?.mkdirs()
                    var written=0L
                    destination.outputStream().use { out ->
                        val buf=ByteArray(65536)
                        while(true){val n=zip.read(buf);if(n<0)break;written+=n;expanded+=n
                            require(expanded <= ArchivePolicy.maxUncompressedBytes && written <= ArchivePolicy.limit(name)) {"Archive exceeds safe size"}
                            out.write(buf,0,n)
                        }
                    }
                    actual[name]=written to repo.assets.sha256(destination)
                    zip.closeEntry()
                }
            }
            val manifest=JSONObject(File(stage,"manifest.json").readText())
            require(manifest.getString("format")=="handover-backup" && manifest.getInt("schema")==1)
            val expected=manifest.getJSONArray("entries")
            val declarations=(0 until expected.length()).map { i ->
                val entry=expected.getJSONObject(i)
                DeclaredBackupEntry(entry.getString("path"),entry.getLong("size"),entry.getString("sha256"))
            }
            ArchivePolicy.verifyManifest(declarations, actual.mapValues { BackupEntryDigest(it.value.first, it.value.second) })
            val payload=JSONObject(File(stage,"data.json").readText())
            require(payload.getInt("schema")==1)
            val tables=payload.getJSONObject("tables")
            // No extra tables, columns or executable SQL from untrusted archives.
            require(tables.keys().asSequence().toSet()==TABLES.toSet())
            // Reject hostile metadata BEFORE moving files or writing any database row.
            val savedMedia=tables.getJSONArray("media")
            val savedReports=tables.getJSONArray("reports")
            val originals=(0 until savedMedia.length()).map { index ->
                val row=savedMedia.getJSONObject(index)
                RestorableMedia(row.getString("id"),row.getString("sessionId"),row.getString("itemId"),
                    row.getString("relativePath"),row.optString("thumbnailPath",null),
                    row.getString("mimeType"),row.getLong("size"),row.getInt("width"),row.getInt("height"),row.getString("sha256"))
            }
            val reportPaths=(0 until savedReports.length()).map { savedReports.getJSONObject(it).getString("relativePath") }
            RestoreAdmission.validateReferencedAssets(originals,reportPaths,actual.mapValues { BackupEntryDigest(it.value.first,it.value.second) })
            val knownIds=mutableMapOf<String,Set<String>>()
            for(table in TABLES) {
                val allowed=mutableSetOf<String>()
                repo.db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { cursor ->
                    val col=cursor.getColumnIndexOrThrow("name")
                    while(cursor.moveToNext()) allowed.add(cursor.getString(col))
                }
                val rows=tables.getJSONArray(table)
                require(rows.length()<=MAX_FILES) { "Too many database rows" }
                val ids=mutableSetOf<String>()
                for(i in 0 until rows.length()) {
                    val row=rows.getJSONObject(i)
                    val fields=row.keys().asSequence().toList()
                    require(fields.isNotEmpty() && fields.all { it in allowed }) { "Unknown database columns" }
                    if("id" in allowed) {
                        val id=row.getString("id")
                        require(RestoreAdmission.validUuid(id) && ids.add(id)) { "Invalid/duplicate row ID" }
                    }
                }
                if("id" in allowed) knownIds[table]=ids
            }
            fun requireReference(table:String,column:String,parent:String) {
                val rows=tables.getJSONArray(table)
                for(i in 0 until rows.length()) require(rows.getJSONObject(i).getString(column) in knownIds.getValue(parent)) {
                    "Broken database relationship"
                }
            }
            for(table in listOf("template_snapshots","sessions","checklist","accessories","reports","revision_events","observations"))
                requireReference(table,"inspectionId","inspections")
            requireReference("item_states","itemId","checklist")
            requireReference("item_states","sessionId","sessions")
            requireReference("media","itemId","checklist")
            requireReference("media","sessionId","sessions")
            requireReference("annotations","assetId","media")
            requireReference("comparison_pairs","baselineAssetId","media")
            requireReference("comparison_pairs","returnAssetId","media")
            val sessions=tables.getJSONArray("sessions")
            val sessionOwner=(0 until sessions.length()).associate { val x=sessions.getJSONObject(it);x.getString("id") to x.getString("inspectionId") }
            val sessionPhase=(0 until sessions.length()).associate { val x=sessions.getJSONObject(it);x.getString("id") to x.getString("phase") }
            val items=tables.getJSONArray("checklist")
            val itemOwner=(0 until items.length()).associate { val x=items.getJSONObject(it);x.getString("id") to x.getString("inspectionId") }
            originals.forEach { require(sessionOwner[it.sessionId]==itemOwner[it.itemId]) { "Image crosses inspection boundaries" } }
            val states=tables.getJSONArray("item_states")
            for(i in 0 until states.length()) {
                val x=states.getJSONObject(i)
                require(sessionOwner[x.getString("sessionId")]==itemOwner[x.getString("itemId")]) { "Invalid point state" }
            }
            val mediaById=originals.associateBy { it.id }
            val pairs=tables.getJSONArray("comparison_pairs")
            for(i in 0 until pairs.length()) {
                val x=pairs.getJSONObject(i)
                val first=mediaById.getValue(x.getString("baselineAssetId"))
                val returning=mediaById.getValue(x.getString("returnAssetId"))
                require(first.itemId==returning.itemId && sessionPhase[first.sessionId]=="BASELINE" &&
                    sessionPhase[returning.sessionId]=="RETURN") { "Invalid before/after match" }
            }
            for(asset in originals) {
                val file=File(stage,asset.originalPath)
                val image=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                android.graphics.BitmapFactory.decodeFile(file.path,image)
                require(image.outWidth==asset.width && image.outHeight==asset.height) { "Photo metadata differs from original" }
            }
            val managedPaths=originals.map { it.originalPath }+reportPaths
            require(managedPaths.all { !repo.assets.resolve(it).exists() }) { "Existing original would be overwritten" }
            val movingBytes=managedPaths.sumOf { actual.getValue(it).first }
            require(repo.assets.root.usableSpace>=movingBytes+64_000_000L) { "Not enough storage to restore safely" }
            persistJournal(JSONObject().put("phase","MOVING").put("stagingName",stage.name).put("paths",JSONArray(managedPaths)))
            for (path in managedPaths) {
                val to=repo.assets.resolve(path)
                require(!to.exists()) { "Restore would replace an existing file" }
                val from=File(stage,path)
                to.parentFile?.mkdirs();require(from.renameTo(to)){"Cannot move restored asset"};moved.add(to)
            }
            repo.db.withTransaction {
                require(repo.dao.allInspections().isEmpty())
                val sql=repo.db.openHelper.writableDatabase
                for(table in TABLES) {
                    val allowed=mutableSetOf<String>()
                    sql.query("PRAGMA table_info($table)").use { c -> val index=c.getColumnIndexOrThrow("name");while(c.moveToNext())allowed.add(c.getString(index)) }
                    val rows=tables.getJSONArray(table)
                    for(i in 0 until rows.length()){
                        val row=rows.getJSONObject(i)
                        val columns=row.keys().asSequence().toList()
                        require(columns.isNotEmpty() && columns.all { it in allowed })
                        val placeholders=columns.joinToString(",") {"?"}
                        val values=columns.map { val v=row.get(it);if(v==JSONObject.NULL)null else v }.toTypedArray()
                        sql.execSQL("INSERT INTO $table (${columns.joinToString(",")}) VALUES ($placeholders)",values)
                    }
                }
            }
            databaseCommitted=true
            persistJournal(JSONObject().put("phase","COMMITTED").put("stagingName",stage.name).put("paths",JSONArray(managedPaths)))
            repo.db.invalidationTracker.refreshAsync()
            // Derived thumbnails can always be rebuilt without touching original bytes.
            for(i in 0 until savedMedia.length()){
                val r=savedMedia.getJSONObject(i)
                val record=repo.dao.mediaById(r.getString("id")) ?: continue
                try {repo.assets.rebuildThumbnail(record)}catch (_:Exception){}
            }
        } catch(t:Throwable) {
            if (!databaseCommitted) moved.forEach { it.delete() }
            throw t
        } finally {
            password?.fill('\u0000')
            incoming.delete();clear.delete();stage.deleteRecursively()
            if (databaseCommitted || moved.all { !it.exists() }) journalFile.delete()
        }
    }
}
