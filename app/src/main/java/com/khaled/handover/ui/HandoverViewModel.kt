package com.khaled.handover.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.khaled.handover.HandoverApp
import com.khaled.handover.data.*
import com.khaled.handover.report.ReportMaker
import com.khaled.handover.report.AssetPackageExporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class HandoverViewModel(app: Application): AndroidViewModel(app) {
    val repo = (app as HandoverApp).repository
    val inspections = repo.inspections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var screen by androidx.compose.runtime.mutableStateOf("HOME")
    var inspectionId by androidx.compose.runtime.mutableStateOf("")
    var phase by androidx.compose.runtime.mutableStateOf(Phase.BASELINE)
    var itemId by androidx.compose.runtime.mutableStateOf("")
    var assetId by androidx.compose.runtime.mutableStateOf("")
    var refresh by androidx.compose.runtime.mutableIntStateOf(0)
    var loading by androidx.compose.runtime.mutableStateOf(false)
    var error by androidx.compose.runtime.mutableStateOf<String?>(null)
    var lastReport by androidx.compose.runtime.mutableStateOf<File?>(null)

    fun go(next: String) { screen = next; refresh++ }
    fun inspect(id: String) { inspectionId = id; go("DETAIL") }
    fun session(p: String) { phase = p; go("SESSION") }
    fun camera(item: String) { itemId = item; go("CAMERA") }
    fun review(id: String) { assetId = id; go("REVIEW") }
    fun task(action: suspend () -> Unit) {
        if (loading) return
        viewModelScope.launch {
            loading = true; error = null
            try { action(); refresh++ }
            catch (e: Exception) { error = e.message ?: "Operation failed" }
            finally { loading = false }
        }
    }
    fun create(title: String, category: String, context: String, role: String, rooms: List<String>) = task {
        inspectionId = repo.create(title, category, context, role, rooms); phase = Phase.BASELINE; screen = "SESSION"
    }
    fun captured(temp: File, item: String) = task {
        val session = repo.ensureSession(inspectionId, phase)
        val asset = repo.capture(temp, session, item)
        assetId = asset.id; screen = "REVIEW"
    }
    fun imported(uri: Uri, item: String) = task {
        val session = repo.ensureSession(inspectionId, phase)
        val asset = repo.import(uri, session, item)
        assetId = asset.id; screen = "REVIEW"
    }
    fun mark(item: String, status: String) = task {
        repo.setStatus(item, repo.ensureSession(inspectionId, phase), status); screen = "SESSION"
    }
    fun complete() = task { repo.complete(repo.ensureSession(inspectionId, phase)); screen = "DETAIL" }
    fun addNote(item: String, asset: String, kind: String, detail: String, assessment: String) = task {
        repo.note(inspectionId, item, asset, kind, detail, assessment)
        screen = "SESSION"
    }
    fun addAccessory(name: String, before: Int, after: Int?, note: String) = task { repo.addAccessory(inspectionId, name, before, after, note) }
    fun updatePair(pair: ComparisonPair, assessment: String) = task { repo.updateComparison(inspectionId, pair.returnAssetId, pair.baselineAssetId, assessment, pair.note) }
    fun linkPair(returnAssetId: String, baselineAssetId: String, assessment: String = Assessment.NONE) = task {
        repo.updateComparison(inspectionId, returnAssetId, baselineAssetId, assessment)
    }
    fun makeReport(options: ReportMaker.Options) = task {
        lastReport = ReportMaker(getApplication(), repo).create(inspectionId, options)
        screen = "REPORT_PREVIEW"
    }
    fun exportAssetPackage(uri: Uri) = task {
        AssetPackageExporter(getApplication(), repo).export(inspectionId, uri)
    }
    fun reportShareIntent(file: File): Intent {
        val ctx: Application = getApplication()
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
        return Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }
}
