package com.khaled.handover.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.webkit.MimeTypeMap
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.khaled.handover.data.MediaAsset
import com.khaled.handover.data.newId
import com.khaled.handover.data.boundedSampleSize
import com.khaled.handover.backup.RestoreAdmission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Original bytes are immutable. Images are private; only explicit exported copies reach FileProvider. */
class AssetStore(private val ctx: Context) {
    val root: File get() = ctx.filesDir
    private val originals get() = File(root, "originals").also { it.mkdirs() }
    private val thumbnails get() = File(root, "thumbnails").also { it.mkdirs() }
    val exports get() = File(root, "exports").also { it.mkdirs() }
    fun resolve(relative: String): File = File(root, relative).canonicalFile.also {
        require(it.path.startsWith(root.canonicalPath + File.separator)) { "Unsafe asset path" }
    }
    fun temporaryCapture(): File = File(ctx.cacheDir, "capture-${newId()}.jpg")
    fun deleteTemps() { ctx.cacheDir.listFiles()?.filter { it.name.startsWith("capture-") && it.name.endsWith(".jpg") }?.forEach { if (System.currentTimeMillis() - it.lastModified() > 24*3600_000L) it.delete() } }
    suspend fun addFromUri(uri: Uri, sessionId: String, itemId: String) = withContext(Dispatchers.IO) {
        val tmp = File(ctx.cacheDir, "import-${newId()}.tmp")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        copied += n
                        require(copied <= 100_000_000L) { "Photo exceeds the 100 MB import limit" }
                        output.write(buf, 0, n)
                    }
                }
            } ?: error("Unable to read the selected photo")
            val mime = ctx.contentResolver.getType(uri)?.lowercase() ?: "image/jpeg"
            require(mime in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) { "Unsupported image type" }
            ingest(tmp, sessionId, itemId, "IMPORTED", null, mime)
        } finally { tmp.delete() }
    }
    suspend fun addCapture(temp: File, sessionId: String, itemId: String) = withContext(Dispatchers.IO) {
        try { ingest(temp, sessionId, itemId, "CAMERA", System.currentTimeMillis(), "image/jpeg") }
        finally { temp.delete() }
    }
    private fun ingest(temp: File, sessionId: String, itemId: String, source: String, capturedAt: Long?, mime: String): MediaAsset {
        require(temp.length() in 1..100_000_000L) { "Photo empty or exceeds the 100 MB import limit" }
        val id = newId()
        val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/heic", "image/heif" -> "heic"; else -> "jpg" }
        val relative = "originals/$id.$ext"
        val final = resolve(relative)
        val stage = File(originals, "$id.part")
        try {
            temp.inputStream().use { input -> FileOutputStream(stage).use { output -> input.copyTo(output); output.fd.sync() } }
            val hash = sha256(stage)
            val (width, height) = dimensions(stage)
            require(width > 0 && height > 0) { "Invalid image" }
            require(stage.renameTo(final)) { "Unable to finalize original" }
            val thumb = File(thumbnails, "$id.jpg")
            createThumbnail(final, thumb)
            val exifTime = if (source == "IMPORTED") readExifTime(final) else null
            return MediaAsset(id, sessionId, itemId, relative, "thumbnails/$id.jpg", mime, final.length(), width, height, hash,
                source, System.currentTimeMillis(), capturedAt ?: exifTime,
                if (source == "CAMERA") "DEVICE_CLOCK" else if (exifTime != null) "UNVERIFIED_EXIF" else "UNKNOWN")
        } catch (e: Exception) { stage.delete(); final.delete(); File(thumbnails, "$id.jpg").delete(); throw e }
    }
    private fun dimensions(file: File): Pair<Int,Int> {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, opts)
        return opts.outWidth to opts.outHeight
    }
    private fun readExifTime(file: File): Long? {
        return try {
            val text = ExifInterface(file).getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            if (text == null) null else {
                // EXIF local wall time often lacks a time zone; this is explicitly unverified.
                java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).parse(text)?.time
            }
        } catch (_: Exception) { null }
    }
    private fun createThumbnail(src: File, dst: File) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, o)
        o.inSampleSize = boundedSampleSize(o.outWidth, o.outHeight, 768)
        o.inJustDecodeBounds = false
        val bitmap = BitmapFactory.decodeFile(src.path, o) ?: return
        val rotation = when (ExifInterface(src).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val result = if (rotation != 0f) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true) else bitmap
        FileOutputStream(dst).use { result.compress(Bitmap.CompressFormat.JPEG, 82, it); it.fd.sync() }
        if (result !== bitmap) bitmap.recycle()
        result.recycle()
    }
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val bytes = ByteArray(64 * 1024); while (true) { val count = input.read(bytes); if (count < 0) break; digest.update(bytes, 0, count) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun verify(asset: MediaAsset): Boolean = resolve(asset.relativePath).let { it.isFile && it.length() == asset.size && sha256(it) == asset.sha256 }
    /** Never write to a thumbnailPath read from a backup or old database row. */
    fun rebuildThumbnail(asset: MediaAsset) {
        val target = resolve(RestoreAdmission.thumbnailFor(asset.id))
        require(target.parentFile?.canonicalFile == thumbnails.canonicalFile) { "Unsafe thumbnail destination" }
        val original = resolve(asset.relativePath)
        require(original.parentFile?.canonicalFile == originals.canonicalFile) { "Unsafe original directory" }
        if (original.isFile && original.length() == asset.size && sha256(original) == asset.sha256) {
            val temporary = File(thumbnails, "${asset.id}.part")
            try {
                createThumbnail(original, temporary)
                if (temporary.isFile) {
                    if (target.exists()) require(target.delete()) { "Cannot replace thumbnail" }
                    require(temporary.renameTo(target)) { "Cannot finalize thumbnail" }
                }
            } finally { temporary.delete() }
        }
    }
    fun derivedBytes(): Long = thumbnails.listFiles()?.sumOf { it.length() } ?: 0
    fun clearThumbnails() { thumbnails.listFiles()?.forEach { it.delete() } }
}
// End of AssetStore
