package com.khaled.handover.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.khaled.handover.HandoverApp
import com.khaled.handover.data.*
import com.khaled.handover.report.ReportMaker
import com.khaled.handover.report.AssetPackageExporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.File

class HandoverViewModel(app: Application, private val saved: SavedStateHandle): AndroidViewModel(app) {
    val repo = (app as HandoverApp).repository
    val inspections = repo.inspections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var recoveryReady by androidx.compose.runtime.mutableStateOf(false)
    var recoveryError by androidx.compose.runtime.mutableStateOf<String?>(null)
    init { viewModelScope.launch {
        try { (app as HandoverApp).recovery.await();recoveryReady=true }
        catch(cancelled:CancellationException) { throw cancelled }
        catch(failure:Exception) { recoveryError=failure.message ?: "Data recovery failed" }
    } }
    var screen by androidx.compose.runtime.mutableStateOf(saved.get<String>("screen") ?: "HOME")
        private set
    var inspectionId by androidx.compose.runtime.mutableStateOf(saved.get<String>("inspectionId") ?: "")
        private set
    var phase by androidx.compose.runtime.mutableStateOf(saved.get<String>("phase") ?: Phase.BASELINE)
        private set
    var itemId by androidx.compose.runtime.mutableStateOf(saved.get<String>("itemId") ?: "")
        private set
    var assetId by androidx.compose.runtime.mutableStateOf(saved.get<String>("assetId") ?: "")
        private set
    var refresh by androidx.compose.runtime.mutableIntStateOf(0)
    var loading by androidx.compose.runtime.mutableStateOf(false)
    var error by androidx.compose.runtime.mutableStateOf<String?>(null)
    var lastReport by androidx.compose.runtime.mutableStateOf<File?>(null)
    private fun navigateToScreen(next:String) { screen=next;saved["screen"]=next;refresh++ }
    private fun choosePhase(value:String) { phase=value;saved["phase"]=value }
    private val stack get()=saved.get<ArrayList<String>>("screenStack") ?: arrayListOf()
    fun go(next:String) {
        if(next!=screen) { val previous=stack;previous.add(screen);saved["screenStack"]=previous }
        navigateToScreen(next)
    }
    fun back() {
        if(screen=="HOME") return
        val previous=stack
        val route=if(previous.isNotEmpty()) previous.removeAt(previous.lastIndex) else when(screen) {
            "CAMERA","REVIEW"->"SESSION";"SESSION"->"DETAIL";else->"HOME"
        }
        saved["screenStack"]=previous;navigateToScreen(route)
    }
    fun inspect(id:String) { inspectionId=id;saved["inspectionId"]=id;go("DETAIL") }
    fun session(p:String) {
        if(p!=Phase.RETURN) { choosePhase(p);go("SESSION");return }
        task {
            if(repo.dao.session(inspectionId,Phase.BASELINE)?.completedAt==null) {
                error="Complete the initial inspection before starting return";return@task
            }
            choosePhase(p);go("SESSION")
        }
    }
    fun camera(item:String) { itemId=item;saved["itemId"]=item;go("CAMERA") }
    fun review(id:String) { assetId=id;saved["assetId"]=id;go("REVIEW") }
    fun task(action:suspend ()->Unit) {
        if(loading) return
        loading=true;error=null
        viewModelScope.launch {
            try { (getApplication() as HandoverApp).recovery.await();action();refresh++ }
            catch(cancelled:CancellationException) { throw cancelled }
            catch(failure:Exception) { error=failure.message ?: "Operation failed" }
            finally { loading=false }
        }
    }
    fun create(title: String, category: String, context: String, role: String, rooms: List<String>) = task {
        inspectionId=repo.create(title,category,context,role,rooms)
        saved["inspectionId"]=inspectionId;choosePhase(Phase.BASELINE);go("SESSION")
    }
    fun captured(temp:File,item:String) {
        if(loading) { temp.delete();return }
        task {
            try {
                val session=repo.ensureSession(inspectionId,phase)
                val asset=repo.capture(temp,session,item)
                assetId=asset.id;saved["assetId"]=asset.id;go("REVIEW")
            } finally { temp.delete() }
        }
    }
    fun imported(uri: Uri, item: String) = task {
        val session = repo.ensureSession(inspectionId, phase)
        val asset = repo.import(uri, session, item)
        assetId=asset.id;saved["assetId"]=asset.id;go("REVIEW")
    }
    fun mark(item: String, status: String) = task {
        repo.setStatus(item, repo.ensureSession(inspectionId, phase), status); navigateToScreen("SESSION")
    }
    fun complete() = task { repo.complete(repo.ensureSession(inspectionId, phase)); navigateToScreen("DETAIL") }
    fun addNote(item: String, asset: String, kind: String, detail: String, assessment: String) = task {
        repo.note(inspectionId, item, asset, kind, detail, assessment)
        navigateToScreen("SESSION")
    }
    fun addAccessory(name: String, before: Int, after: Int?, note: String) = task { repo.addAccessory(inspectionId, name, before, after, note) }
    fun updatePair(pair: ComparisonPair, assessment: String) = task { repo.updateComparison(inspectionId, pair.returnAssetId, pair.baselineAssetId, assessment, pair.note) }
    fun linkPair(returnAssetId: String, baselineAssetId: String, assessment: String = Assessment.NONE) = task {
        repo.updateComparison(inspectionId, returnAssetId, baselineAssetId, assessment)
    }
    fun makeReport(options: ReportMaker.Options) = task {
        lastReport = ReportMaker(getApplication(), repo).create(inspectionId, options)
        navigateToScreen("REPORT_PREVIEW")
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
