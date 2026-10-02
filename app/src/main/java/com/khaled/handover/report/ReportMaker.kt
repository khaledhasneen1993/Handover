package com.khaled.handover.report

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.room.withTransaction
import com.khaled.handover.data.*
import com.khaled.handover.media.AssetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

/** Device PDF renderer; does not re-encode or alter original assets. Sensitive pixels must be reviewed by the user. */
class ReportMaker(private val context: Context, private val repository: InspectionRepository) {
    data class Options(val detailed: Boolean = false, val language: String = "en", val hideParty: Boolean = true,
        val hideIdentifiers: Boolean = true, val hideLocation: Boolean = true, val highQuality: Boolean = false)
    private val navy = Color.rgb(17, 42, 66)
    private val teal = Color.rgb(0, 143, 151)
    private val ink = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = navy; textSize = 11f }
    private val pageWidth = 595
    private val pageHeight = 842
    private val inset = 42f
    private val bottom = 786f

    private fun labels(lang: String) = when (lang) {
        "ar" -> listOf("تقرير حالة الممتلكات", "الحالة الأولى", "الحالة عند الإعادة", "ملاحظات المستخدم", "الملحقات", "لم تُوثّق هذه الجلسة", "تقرير توثيقي يتضمن بيانات وملاحظات المستخدم؛ وليس فحصًا معتمدًا.")
        "fr" -> listOf("Rapport d'état", "État initial", "État au retour", "Notes de l'utilisateur", "Accessoires", "Session non documentée", "Documentation et remarques de l'utilisateur ; aucune certification officielle.")
        "es" -> listOf("Informe del estado", "Estado inicial", "Estado al devolver", "Observaciones del usuario", "Accesorios", "Sesión no documentada", "Documentación y notas del usuario; no es una inspección certificada.")
        else -> listOf("Condition report", "Initial condition", "Return condition", "User observations", "Accessories", "No session recorded", "User-provided documentation and observations, not a certified inspection.")
    }
    suspend fun create(id: String, options: Options): File = withContext(Dispatchers.IO) {
        val dao = repository.dao
        val inspection = dao.getInspection(id) ?: error("Inspection not found")
        val sessions = dao.sessions(id)
        val items = dao.items(id)
        val notes = dao.observations(id)
        val accessories = dao.accessories(id)
        val l = labels(options.language)
        val fileId = newId()
        val temp = File(repository.assets.exports, "$fileId.part")
        val pdf = File(repository.assets.exports, "$fileId.pdf")
        val doc = PdfDocument()
        val rtl = options.language == "ar"
        val locale = Locale.forLanguageTag(options.language)
        val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        lateinit var canvas: Canvas
        var pageNum = 0
        var y = inset
        var active: PdfDocument.Page? = null
        fun newPage() {
            active?.let { p ->
                paint.color = Color.LTGRAY; p.canvas.drawLine(inset, 795f, pageWidth - inset, 795f, paint)
                ink.textSize = 9f; ink.color = navy
                p.canvas.drawText("Handover  •  $pageNum", inset, 813f, ink)
                doc.finishPage(p)
            }
            pageNum++
            active = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = active!!.canvas; canvas.drawColor(Color.WHITE); y = inset
        }
        fun ensure(h: Float) { if (y + h > bottom) newPage() }
        fun text(s: String, size: Float = 11f, bold: Boolean = false, spacing: Float = 10f) {
            ink.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
            ink.textSize = size; ink.color = navy
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, ink, (pageWidth - inset * 2).toInt())
                .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false).build()
            // Draw one or more whole text lines per page. A long Arabic/English paragraph
            // must never overflow the page just because it exceeded one page of height.
            var firstLine = 0
            while (firstLine < layout.lineCount) {
                if (bottom - y < ink.textSize * 1.6f) newPage()
                val top = layout.getLineTop(firstLine)
                var endLine = firstLine
                while (endLine < layout.lineCount &&
                    layout.getLineBottom(endLine) - top <= bottom - y) endLine++
                if (endLine == firstLine) { newPage(); continue }
                val segmentHeight = layout.getLineTop(endLine) - top
                canvas.save()
                canvas.clipRect(inset, y, pageWidth - inset, y + segmentHeight)
                canvas.translate(inset, y - top)
                layout.draw(canvas)
                canvas.restore()
                y += segmentHeight
                firstLine = endLine
                if (firstLine < layout.lineCount) newPage()
            }
            y += spacing
        }
        fun line() { ensure(20f); paint.color = Color.rgb(221, 233, 235); canvas.drawLine(inset, y, pageWidth - inset, y, paint); y += 18f }
        fun photo(asset: MediaAsset, label: String) {
            val original = repository.assets.resolve(asset.relativePath)
            require(repository.assets.verify(asset)) {
                "An original photo is missing or changed. Resolve it before exporting: ${asset.id}"
            }
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(original.path, o)
            val imageLimit = if (options.highQuality) 1400 else 900
            o.inSampleSize = boundedSampleSize(o.outWidth, o.outHeight, imageLimit)
            o.inJustDecodeBounds = false
            val decoded = BitmapFactory.decodeFile(original.path, o)
            if (decoded == null) { text("[Unreadable image] $label"); return }
            val bitmap = try {
                val exif = androidx.exifinterface.media.ExifInterface(original)
                val rotation = exif.rotationDegrees
                if (rotation != 0 || exif.isFlipped) {
                    val matrix = Matrix().apply { if (exif.isFlipped) postScale(-1f, 1f); postRotate(rotation.toFloat()) }
                    Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also { decoded.recycle() }
                } else decoded
            } catch (_: Exception) { decoded }
            try {
                // Contain instead of crop; avoid splitting captions from images.
                val maxW = pageWidth - inset * 2
                val maxH = 285f
                val factor = min(maxW / bitmap.width, maxH / bitmap.height)
                val w = bitmap.width * factor; val h = bitmap.height * factor
                ensure(h + 45f)
                text(label, size = 10f, bold = true, spacing = 5f)
                paint.isFilterBitmap = true; paint.color = Color.WHITE
                canvas.drawBitmap(bitmap, null, RectF(inset + (maxW - w)/2f, y, inset + (maxW + w)/2f, y + h), paint)
                y += h + 12f
                text("${asset.source.lowercase().replaceFirstChar { it.uppercase() }} · SHA-256 ${asset.sha256.take(12)}…", 8f)
            } finally { bitmap.recycle() }
        }
        fun fmt(t: Long, zone: String): String { format.timeZone = TimeZone.getTimeZone(zone); return "${format.format(Date(t))} ($zone)" }
        try {
            newPage()
            text("HANDOVER", 16f, true)
            paint.color = teal; canvas.drawRect(inset, y - 6f, pageWidth - inset, y - 3f, paint)
            text(l[0], 21f, true)
            text(inspection.title, 16f, true)
            text("ID: ${inspection.id}  ·  ${inspection.category} / ${inspection.context}", 10f)
            if (!options.hideParty && !inspection.partyName.isNullOrBlank()) text("Party: ${inspection.partyName}")
            if (!options.hideIdentifiers && !inspection.reference.isNullOrBlank()) text("Reference: ${inspection.reference}")
            inspection.description?.takeIf { it.isNotBlank() }?.let { text(it) }
            line()
            for (session in sessions) {
                text(if (session.phase == Phase.BASELINE) l[1] else l[2], 16f, true)
                text(fmt(session.startedAt, session.zoneId), 10f)
                val states = dao.states(session.id)
                val counts = countStatuses(states.map { it.captureStatus })
                text("${counts.captured} captured  ·  ${counts.skipped} skipped  ·  ${counts.notApplicable} N/A  ·  ${counts.pending} pending", 10f)
                if (session.completedAt == null) text("In progress · Revision ${session.revision}", 9f)
                if (session.phase == Phase.RETURN && dao.sessionMedia(session.id).isEmpty()) text(l[5])
                for (item in items) {
                    val state = states.firstOrNull { it.itemId == item.id }
                    val photos = dao.itemMedia(item.id, session.id)
                    if (options.detailed || photos.isNotEmpty()) {
                        text("${item.groupName} · ${item.label} — ${state?.captureStatus ?: "NOT_CAPTURED"}", 11f, true)
                        (if (options.detailed) photos else photos.take(1)).forEachIndexed { i, asset -> photo(asset, "${item.label} · ${i + 1}") }
                    }
                }
                line()
            }
            if (sessions.none { it.phase == Phase.RETURN }) {
                text(l[5], 10f)
                line()
            }
            if (notes.isNotEmpty()) {
                text(l[3], 15f, true)
                notes.forEach { note ->
                    val item = items.firstOrNull { it.id == note.itemId }?.label ?: "Other"
                    text("$item · ${note.category} · ${note.assessment}: ${note.text}", 11f)
                }
                line()
            }
            if (accessories.isNotEmpty()) {
                text(l[4], 15f, true)
                accessories.forEach { item -> text("${item.name}: ${item.baselineQuantity} → ${item.returnQuantity ?: "not recorded"}. ${item.note}") }
                line()
            }
            text(l[6], 9f)
            text("Created: ${fmt(System.currentTimeMillis(), java.time.ZoneId.systemDefault().id)}", 9f)
            active?.let { p ->
                paint.color = Color.LTGRAY; p.canvas.drawLine(inset, 795f, pageWidth-inset, 795f, paint)
                ink.textSize = 9f; ink.color = navy; p.canvas.drawText("Handover  •  $pageNum", inset, 813f, ink)
                doc.finishPage(p)
            }
            FileOutputStream(temp).use { doc.writeTo(it); it.fd.sync() }
            require(temp.renameTo(pdf)) { "Unable to finalize PDF" }
            repository.db.withTransaction {
                dao.insertReport(ReportRecord(fileId, id, if (options.detailed) "DETAILED" else "SUMMARY", dao.revision(id) ?: 0,
                    System.currentTimeMillis(), options.language, "exports/${pdf.name}",
                    JSONObject().put("hideParty", options.hideParty).put("hideIdentifiers", options.hideIdentifiers).put("hideLocation", options.hideLocation).toString()))
            }
            pdf
        } catch (t: Throwable) { temp.delete(); pdf.delete(); throw t }
        finally { doc.close() }
    }

    fun renderPage(file: File, index: Int): Pair<Bitmap, Int> {
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
            require(index in 0 until renderer.pageCount)
            val total = renderer.pageCount
            renderer.openPage(index).use { page ->
                val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE); page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp to total
            }
        }
    }
}
