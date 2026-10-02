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
    suspend fun restore(uri: Uri, password: CharArray? = null) = withContext(Dispatchers.IO) {
        // Restore is intentionally create-only: an existing database is never overwritten or silently merged.
        require(repo.dao.allInspections().isEmpty()) { "Restore requires a fresh empty app. Export existing operations first." }
        val incoming=File.createTempFile("handover-incoming-",".bak",context.cacheDir)
        val clear=File.createTempFile("handover-decrypted-",".zip",context.cacheDir)
        val stage=File(context.cacheDir,"restore-${java.util.UUID.randomUUID()}").also{it.mkdirs()}
        val moved=mutableListOf<File>()
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
            val savedMedia=tables.getJSONArray("media")
            require(savedMedia.length()<=MAX_FILES)
            for(i in 0 until savedMedia.length()){
                val r=savedMedia.getJSONObject(i);require(actual.containsKey(r.getString("relativePath")))
                require(actual[r.getString("relativePath")]!!.second==r.getString("sha256"))
            }
            val savedReports=tables.getJSONArray("reports")
            for (i in 0 until savedReports.length()) {
                val name=savedReports.getJSONObject(i).getString("relativePath")
                require(name.matches(Regex("exports/[0-9a-f-]{36}\\.pdf")) && actual.containsKey(name)) { "Missing exported PDF" }
            }
            val managedPaths = buildList {
                for (i in 0 until savedMedia.length()) add(savedMedia.getJSONObject(i).getString("relativePath"))
                for (i in 0 until savedReports.length()) add(savedReports.getJSONObject(i).getString("relativePath"))
            }
            require(managedPaths.size == managedPaths.toSet().size) { "Duplicate asset path" }
            val movingBytes = managedPaths.sumOf { actual[it]?.first ?: error("Missing asset") }
            require(repo.assets.root.usableSpace > movingBytes + 16_777_216L) {"Not enough free storage to safely restore"}
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
            repo.db.invalidationTracker.refreshAsync()
            // Derived thumbnails can always be rebuilt without touching original bytes.
            for(i in 0 until savedMedia.length()){
                val r=savedMedia.getJSONObject(i)
                val record=repo.dao.mediaById(r.getString("id")) ?: continue
                try {repo.assets.rebuildThumbnail(record)}catch (_:Exception){}
            }
        } catch(t:Throwable){moved.forEach{it.delete()};throw t}
        finally{password?.fill('\u0000'); incoming.delete();clear.delete();stage.deleteRecursively()}
    }
}
