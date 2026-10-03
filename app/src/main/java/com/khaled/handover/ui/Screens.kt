package com.khaled.handover.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.khaled.handover.data.*
import com.khaled.handover.media.GuidedCamera
import com.khaled.handover.report.ReportMaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView

private val big = 22.sp
private val normal = 15.sp

/** A non-persistent demonstration; it never mixes example files with real inspections. */
@Composable fun OnboardingScreen(onStart: () -> Unit) {
    var showExample by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        verticalArrangement=Arrangement.spacedBy(17.dp)) {
        Spacer(Modifier.height(18.dp))
        Icon(Icons.Default.FactCheck, "Handover",Modifier.size(58.dp),tint=Teal)
        Text("Handover",fontSize=35.sp,fontWeight=FontWeight.ExtraBold,color=MaterialTheme.colorScheme.onBackground)
        Text("Record the condition. Compare on return. Share a clear report.",
            fontSize=23.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.onBackground)
        Text("Guided photos for rental cars, devices and rooms — saved privately on this device. No account or ads.",
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if (showExample) {
            AppCard(Modifier.fillMaxWidth()) {
                Text("EXAMPLE ONLY · Nothing is saved",color=Teal,fontWeight=FontWeight.Bold,fontSize=12.sp)
                Text("Airport rental car",fontSize=21.sp,fontWeight=FontWeight.Bold)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Default.DirectionsCar,null,tint=Teal)
                    Column { Text("Initial condition",fontWeight=FontWeight.Bold);Text("2 photographed · 1 skipped · 9 pending",fontSize=12.sp) }
                }
                LinearProgressIndicator(progress={2f / 12f},modifier=Modifier.fillMaxWidth())
                Text("On return, capture the same angle and add your own comparison notes.",fontSize=13.sp)
                Text("Example images and labels are illustrative; real photos are never added without your action.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            AppCard(Modifier.fillMaxWidth()) {
                listOf("1. Choose a property template", "2. Photograph guided checkpoints",
                    "3. Revisit the same angles on return", "4. Preview and share a PDF").forEach { step ->
                    Text(step,fontSize=16.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick={showExample=!showExample},modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)) {
            Text(if(showExample) "Back to overview" else "See an example")
        }
        PrimaryButton("Start documenting",action=onStart)
    }
}

@Composable
fun HandoverScreen(vm: HandoverViewModel) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.error) { vm.error?.let { snackbar.showSnackbar(it); vm.error = null } }
    BackHandler(vm.recoveryReady && vm.screen != "HOME") { vm.back() }
    if (!vm.recoveryReady) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            if(vm.recoveryError!=null) Text("Unable to recover saved data: ${vm.recoveryError}",color=MaterialTheme.colorScheme.error)
            else CircularProgressIndicator()
        }
        return
    }
    val all by vm.inspections.collectAsState()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (vm.screen in listOf("HOME", "ARCHIVE", "SETTINGS")) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(Triple("HOME", "Operations", Icons.Default.ListAlt), Triple("ARCHIVE", "Archive", Icons.Default.Inventory2), Triple("SETTINGS", "Settings", Icons.Default.Settings))
                    .forEach { (route, label, image) -> NavigationBarItem(selected = vm.screen == route, onClick = { vm.go(route) }, icon = { Icon(image, label) }, label = { Text(tr(label)) }) }
            }
        } }
    ) { insets ->
        Box(Modifier.padding(insets).fillMaxSize()) {
            when (vm.screen) {
                "HOME" -> HomeScreen(vm, all.filter { it.status != Progress.ARCHIVED })
                "DEMO" -> SampleScreen(vm)
                "ARCHIVE" -> HomeScreen(vm, all.filter { it.status == Progress.ARCHIVED }, true)
                "CREATE" -> CreateScreen(vm)
                "DETAIL" -> DetailScreen(vm)
                "SESSION" -> SessionScreen(vm)
                "CAMERA" -> CameraScreen(vm)
                "REVIEW" -> ReviewScreen(vm)
                "COMPARISON" -> ComparisonScreen(vm)
                "ACCESSORIES" -> AccessoriesScreen(vm)
                "REPORT" -> ReportScreen(vm)
                "REPORT_PREVIEW" -> PdfPreviewScreen(vm)
                "SETTINGS" -> SettingsScreen(vm)
                "BACKUP" -> BackupScreen(vm)
                "HELP" -> HelpScreen(vm)
                else -> HomeScreen(vm, all.filter { it.status != Progress.ARCHIVED })
            }
            if (vm.loading) LinearProgressIndicator(Modifier.align(Alignment.TopCenter).fillMaxWidth())
        }
    }
}

@Composable private fun Header(title: String, goBack: () -> Unit, action: (@Composable ()->Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = goBack) { Icon(Icons.Default.ArrowBack, "Back") }
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (action != null) action() else Spacer(Modifier.width(48.dp))
    }
}
@Composable private fun PrimaryButton(text: String, enabled: Boolean = true, action: ()->Unit) {
    Button(onClick=action, enabled=enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape=RoundedCornerShape(18.dp)) { Text(tr(text), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}
@Composable private fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.()->Unit) {
    Card(modifier, shape=RoundedCornerShape(22.dp), colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface), elevation=CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
@Composable private fun StatusText(value: String) {
    val description = when(value) {
        Progress.DRAFT -> "Draft"; Progress.BASELINE_DONE -> "Initial condition complete"
        Progress.AWAITING_RETURN -> "Awaiting return"; Progress.RETURN_DONE -> "Both sessions complete"
        Progress.ARCHIVED -> "Archived"; else -> value.replace('_', ' ').lowercase()
    }
    Surface(color = if (value == Progress.RETURN_DONE) Color(0xFFDBF5E9) else Color(0xFFE1F6F7), shape = RoundedCornerShape(30.dp)) {
        Text(tr(description), fontSize = 12.sp, color = Color(0xFF006B71), modifier=Modifier.padding(horizontal=10.dp, vertical=5.dp))
    }
}
/** Decode preview images off the UI thread. Never open full-resolution originals for cards. */
/** Decode off the UI thread; a missing generated thumbnail is recreated only from a
 * verified original, never from a persisted thumbnail destination. */
@Composable private fun Thumb(file: File?, modifier: Modifier = Modifier,
    contentDescription: String = "Documented photo", alpha: Float = 1f) {
    val app = LocalContext.current.applicationContext as? com.khaled.handover.HandoverApp
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,
        file?.path, file?.lastModified()) {
        value = withContext(Dispatchers.IO) {
            if (file == null) null else {
                val store = app?.repository?.assets
                if (store != null && (!file.isFile || file.length() == 0L)) {
                    val id = file.nameWithoutExtension
                    if (com.khaled.handover.backup.RestoreAdmission.validUuid(id) &&
                        file.canonicalFile == store.resolve(
                            com.khaled.handover.backup.RestoreAdmission.thumbnailFor(id))) {
                        val record = app.repository.dao.mediaById(id)
                        if (record != null) runCatching { store.rebuildThumbnail(record) }
                    }
                }
                if (!file.isFile) null else try {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.path, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = boundedSampleSize(bounds.outWidth, bounds.outHeight, 1000)
                    }
                    BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
                } catch (_: Exception) { null }
            }
        }
    }
    if (image != null) Image(image!!, contentDescription,
        modifier.alpha(alpha).clip(RoundedCornerShape(16.dp)),
        contentScale = ContentScale.Fit)
    else Box(modifier.clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PhotoCamera, "No photograph yet", Modifier.size(35.dp))
    }
}

@Composable private fun HomeScreen(vm: HandoverViewModel, inspections: List<Inspection>, archived: Boolean=false) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Column { Text(tr("Handover"), fontSize=28.sp, fontWeight=FontWeight.ExtraBold); Text(tr("Document with confidence"), color=MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!archived) Button(onClick={vm.go("CREATE")}) { Icon(Icons.Default.Add, null); Text(tr("New")) }
        }
        Text(tr(if (archived) "Archive" else "Your operations"), fontSize=25.sp, fontWeight=FontWeight.Bold, modifier=Modifier.padding(horizontal=20.dp))
        OutlinedTextField(search, {search=it}, label={Text(tr("Search operations"))}, leadingIcon={Icon(Icons.Default.Search,null)}, singleLine=true,
            modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp, vertical=12.dp), shape=RoundedCornerShape(17.dp))
        if (!archived) Row(Modifier.fillMaxWidth().padding(horizontal=20.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "All", "ACTIVE" to "In progress", "DONE" to "Complete").forEach { (id,label) -> FilterChip(filter==id, onClick={filter=id}, label={Text(tr(label))}, modifier=Modifier.weight(1f)) }
        }
        val filtered = inspections.filter { it.title.contains(search,true) && (archived || when(filter) { "ACTIVE" -> it.status != Progress.RETURN_DONE; "DONE" -> it.status == Progress.RETURN_DONE; else -> true }) }
        if (filtered.isEmpty()) Box(Modifier.fillMaxSize().padding(35.dp), contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.spacedBy(15.dp)) {
                Icon(Icons.Default.FactCheck,null,Modifier.size(64.dp),tint=Teal)
                Text(tr(if (archived) "No archived operations" else "No operations here yet"),fontWeight=FontWeight.Bold, fontSize=20.sp)
                Text(tr(if (archived) "Completed work can be archived." else "Create an operation to record your property's condition."),textAlign=TextAlign.Center)
                if (!archived) {
                    PrimaryButton("Start documenting") { vm.go("CREATE") }
                    TextButton(onClick = { vm.go("DEMO") }) { Text(tr("See an example")) }
                }
            }
        } else LazyColumn(contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
            items(filtered, key={it.id}) { inspection ->
                val sessionMedia by produceState<List<MediaAsset>>(emptyList(), inspection.id, vm.refresh) {
                    val sessions=vm.repo.dao.sessions(inspection.id)
                    value = sessions.firstOrNull()?.let { vm.repo.dao.sessionMedia(it.id) } ?: emptyList()
                }
                AppCard(Modifier.fillMaxWidth().clickable { vm.inspect(inspection.id) }) {
                    Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Thumb(sessionMedia.firstOrNull()?.let {vm.repo.assets.resolve(it.thumbnailPath)}, Modifier.size(104.dp))
                        Column(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(inspection.title,fontWeight=FontWeight.Bold,fontSize=19.sp)
                            StatusText(inspection.status)
                            Text(inspection.category.lowercase().replaceFirstChar { it.uppercase() }, fontSize=12.sp)
                            Text(DateFormat.getDateInstance().format(Date(inspection.updatedAt)),fontSize=12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
    }
}

/** Synthetic, read-only onboarding sample. No fake photographs or saved user records. */
@Composable private fun SampleScreen(vm: HandoverViewModel) {
    Column(Modifier.fillMaxSize()) {
        Header(tr("Example documentation"), { vm.go("HOME") })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
            item { Text(tr("SAMPLE ONLY · Not a real inspection"), color = Teal, fontWeight = FontWeight.Bold) }
            item { Text(tr("Airport rental car"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            item { AppCard(Modifier.fillMaxWidth()) {
                Text(tr("Initial condition"), fontWeight = FontWeight.Bold)
                Text("4 photographed · 1 skipped · 7 pending", color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("Front" to "Photographed", "Right side" to "Photographed", "Windshield" to "Skipped").forEach { (point, status) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (status == "Photographed") Icons.Default.CheckCircle else Icons.Default.Schedule, null, tint = if (status == "Photographed") Teal else Amber)
                        Spacer(Modifier.width(10.dp)); Text(tr(point), Modifier.weight(1f)); Text(tr(status), fontSize = 12.sp)
                    }
                }
            } }
            item { AppCard(Modifier.fillMaxWidth()) {
                Text(tr("At return"), fontWeight = FontWeight.Bold)
                Text(tr("A user noticed a new scratch on the right-side door and compared the two photos."))
                Text(tr("This sample illustrates the workflow; the app does not determine who caused a difference."), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            item { Text(tr("Use your own photographs to create a PDF report that can be shared without installing the app.")) }
        }
        Box(Modifier.padding(20.dp)) { PrimaryButton("Start documenting") { vm.go("CREATE") } }
    }
}

@Composable private fun CreateScreen(vm: HandoverViewModel) {
    var selected by remember { mutableStateOf("VEHICLE") }; var title by remember { mutableStateOf("") }
    var context by remember { mutableStateOf("RENTAL") }; var role by remember { mutableStateOf("UNSPECIFIED") }
    var rooms by remember { mutableStateOf("Living room, Kitchen, Bathroom") }
    Column(Modifier.fillMaxSize()) {
        Header("What are you documenting?", { vm.go("HOME") })
        LazyColumn(Modifier.weight(1f), contentPadding=PaddingValues(horizontal=20.dp, vertical=10.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text(tr("Choose a template to begin"),color=MaterialTheme.colorScheme.onSurfaceVariant) }
            items(listOf(Triple("VEHICLE","Rental car","Photograph the vehicle inside and out"), Triple("DEVICE","Device for repair","Document the device and accessories"), Triple("APARTMENT","Home or apartment","Document room-by-room condition"))) { (id,name,description) ->
                val icon = when(id) { "VEHICLE" -> Icons.Default.DirectionsCar; "DEVICE" -> Icons.Default.PhoneAndroid; else -> Icons.Default.Home }
                Card(onClick={selected=id}, border=BorderStroke(if (selected==id) 2.dp else 1.dp, if (selected==id) Teal else MaterialTheme.colorScheme.outline.copy(alpha=.3f)), shape=RoundedCornerShape(21.dp)) {
                    Row(Modifier.padding(18.dp), horizontalArrangement=Arrangement.spacedBy(14.dp), verticalAlignment=Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(48.dp), tint=Teal)
                        Column(Modifier.weight(1f)) { Text(tr(name),fontWeight=FontWeight.Bold,fontSize=18.sp); Text(tr(description),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        RadioButton(selected==id,{selected=id})
                    }
                }
            }
            item { OutlinedTextField(title,{title=it.take(120)}, label={Text(tr("Operation name *"))}, placeholder={Text(tr("e.g. Airport rental · Oct 2"))},singleLine=true,modifier=Modifier.fillMaxWidth()) }
            item { Text(tr("Context"),fontWeight=FontWeight.Bold); SingleChoiceRow(listOf("RENTAL","REPAIR","LOAN","OTHER"),context,{context=it}) }
            item { Text(tr("Your role (optional)"),fontWeight=FontWeight.Bold); SingleChoiceRow(listOf("UNSPECIFIED","RECEIVER","HANDOVER"),role,{role=it}) }
            if (selected=="APARTMENT") item { OutlinedTextField(rooms,{rooms=it},label={Text(tr("Rooms, separated by commas"))},modifier=Modifier.fillMaxWidth()) }
        }
        Box(Modifier.padding(20.dp)) { PrimaryButton("Begin initial condition",enabled=title.isNotBlank()&&!vm.loading) {
            vm.create(title,selected,context,role,rooms.split(',').map {it.trim()}.filter {it.isNotEmpty()})
        } }
    }
}
@Composable private fun SingleChoiceRow(options: List<String>, selected: String, onPick:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(5.dp)) { options.chunked(2).forEach { row ->
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
            row.forEach { option -> FilterChip(selected == option, onClick={onPick(option)}, label={Text(option.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() })}, modifier=Modifier.weight(1f)) }
            if (row.size==1) Spacer(Modifier.weight(1f))
        }
    } }
}

private data class InspectionContent(val inspection: Inspection?, val items: List<ChecklistItem>, val sessions: List<CaptureSession>, val media: Map<String,List<MediaAsset>>,
    val states: Map<String,List<ItemSessionState>>)
@Composable private fun loadInspection(vm:HandoverViewModel): InspectionContent {
    val result by produceState(InspectionContent(null,emptyList(),emptyList(),emptyMap(),emptyMap()),vm.inspectionId,vm.refresh) {
        val d=vm.repo.dao; val id=vm.inspectionId
        val sessions=d.sessions(id)
        value=InspectionContent(d.getInspection(id),d.items(id),sessions,sessions.associate{it.id to d.sessionMedia(it.id)},sessions.associate{it.id to d.states(it.id)})
    }
    return result
}
@Composable private fun DetailScreen(vm:HandoverViewModel) {
    val details=loadInspection(vm); val inspection=details.inspection
    if (inspection==null) { Text(tr("Loading operation…"));return }
    val first=details.sessions.firstOrNull{it.phase==Phase.BASELINE}
    val second=details.sessions.firstOrNull{it.phase==Phase.RETURN}
    var tab by remember { mutableStateOf("SUMMARY") }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete this operation?")},
        text={Text("All of its local photos and reports will be deleted. This cannot be undone.")},
        confirmButton={TextButton(onClick={confirmDelete=false;vm.task {
            com.khaled.handover.reminders.ReminderScheduler.cancel(vm.getApplication(),vm.inspectionId)
            vm.repo.delete(vm.inspectionId);vm.go("HOME")
        }}){Text("Delete")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}})
    Column(Modifier.fillMaxSize()) {
        Header(inspection.title,{vm.go("HOME")})
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            item {
                val cover=first?.let {details.media[it.id]?.firstOrNull()}
                Thumb(cover?.let {vm.repo.assets.resolve(it.thumbnailPath)},Modifier.fillMaxWidth().height(205.dp))
                Spacer(Modifier.height(12.dp)); StatusText(inspection.status)
            }
            item {
                val active=details.states[first?.id]?.let {countStatuses(it.map { it.captureStatus })}
                AppCard(Modifier.fillMaxWidth()) {
                    Text(tr("Documentation progress"),fontWeight=FontWeight.Bold)
                    Text("${active?.captured ?: 0} photographed · ${active?.skipped ?: 0} skipped · ${active?.notApplicable ?: 0} not applicable",fontSize=13.sp)
                }
            }
            item { SingleChoiceRow(listOf("SUMMARY","FIRST","RETURN","COMPARE","REPORTS"),tab,{tab=it}) }
            when(tab) {
                "SUMMARY" -> {
                    item { DetailAction(Icons.Default.CameraAlt,"Initial condition", "${first?.let {details.media[it.id]?.size} ?: 0} photos") {vm.session(Phase.BASELINE)} }
                    item { DetailAction(Icons.Default.CompareArrows,"Return condition",if (first?.completedAt==null) "Complete initial condition first" else "Compare with previous photos") { if(first?.completedAt!=null)vm.session(Phase.RETURN) } }
                    item { DetailAction(Icons.Default.Compare,"Compare photos","Review user-recorded changes") {vm.go("COMPARISON")} }
                    item { DetailAction(Icons.Default.Inventory2,"Accessories","List items and quantities") {vm.go("ACCESSORIES")} }
                    item { DetailAction(Icons.Default.Description,"Export report","Create PDF with chosen privacy options") {vm.go("REPORT")} }
                    item { ReminderEditor(vm, inspection) }
                    item { OutlinedButton(onClick={vm.task {
                        com.khaled.handover.reminders.ReminderScheduler.cancel(vm.getApplication(),vm.inspectionId)
                        vm.repo.archive(vm.inspectionId); vm.go("HOME")
                    }},modifier=Modifier.fillMaxWidth()) { Text(tr("Archive operation")) } }
                    item { TextButton(onClick={confirmDelete=true},modifier=Modifier.fillMaxWidth()) {Text("Delete operation",color=MaterialTheme.colorScheme.error)} }
                }
                "FIRST", "RETURN" -> item {
                    val returning=tab=="RETURN"
                    PrimaryButton(if(returning)"Return condition" else "Initial condition",
                        enabled=!returning || first?.completedAt!=null) {
                        vm.session(if(returning)Phase.RETURN else Phase.BASELINE)
                    }
                    if(returning && first?.completedAt==null)
                        Text("Complete initial condition first",color=MaterialTheme.colorScheme.error)
                }
                "COMPARE" -> item { LaunchedScreenLink("View comparisons") {vm.go("COMPARISON")} }
                "REPORTS" -> item { LaunchedScreenLink("Create a report") {vm.go("REPORT")} }
            }
        }
    }
}

@Composable private fun ReminderEditor(vm:HandoverViewModel, inspection:Inspection) {
    val context = LocalContext.current
    var pendingDue by rememberSaveable { mutableStateOf<Long?>(null) }
    var lead by rememberSaveable { mutableIntStateOf(inspection.reminderLeadMinutes ?: 60) }
    var pendingLead by rememberSaveable { mutableIntStateOf(lead) }
    val scheduler = com.khaled.handover.reminders.ReminderScheduler
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val due = pendingDue
        if (granted && due != null) vm.task {
            // A permission callback can arrive after recreation or after the date was changed.
            val saved = vm.repo.dao.getInspection(inspection.id)
            if (saved?.dueAt == due && saved.reminderLeadMinutes == pendingLead)
                scheduler.schedule(context, inspection.id, due, pendingLead)
            pendingDue = null
        } else {
            pendingDue = null
            if (!granted) vm.error = "Return date saved, but notifications were not permitted"
        }
    }
    fun chooseDate() {
        val calendar = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(context, { _, year, month, day ->
            android.app.TimePickerDialog(context, { _, hour, minute ->
                calendar.set(year, month, day, hour, minute)
                calendar.set(java.util.Calendar.SECOND, 0)
                val due = calendar.timeInMillis
                val chosenLead = lead
                vm.task {
                    // The notification is never scheduled before the Room transaction succeeds.
                    vm.repo.updateDue(inspection.id, due, chosenLead)
                    if (android.os.Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        pendingDue = due
                        pendingLead = chosenLead
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else scheduler.schedule(context, inspection.id, due, chosenLead)
                }
            }, calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE), false).show()
        }, calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH), calendar.get(java.util.Calendar.DAY_OF_MONTH)).show()
    }
    val finished = inspection.status == Progress.RETURN_DONE || inspection.status == Progress.ARCHIVED
    AppCard(Modifier.fillMaxWidth()) {
        Text("Return reminder", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(inspection.dueAt?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Not scheduled", fontSize = 13.sp)
        Text("Remind me before", fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(60 to "1 hour", 360 to "6 hours", 1440 to "1 day", 2880 to "2 days").forEach { (minutes,label) ->
                FilterChip(selected = lead == minutes, onClick = { lead = minutes }, label = { Text(label) })
            }
        }
        PrimaryButton("Set return date & reminder", enabled = !finished) { chooseDate() }
        if (finished) Text("Return already completed or archived; no further reminder will be sent", fontSize = 12.sp)
        if (inspection.dueAt != null) TextButton(onClick = {
            vm.task {
                vm.repo.updateDue(inspection.id, null, null)
                scheduler.cancel(context, inspection.id)
            }
        }) { Text("Cancel reminder") }
        Text("Local, non-exact reminder. Notifications require permission.", fontSize = 12.sp)
    }
}

@Composable private fun DetailAction(icon: androidx.compose.ui.graphics.vector.ImageVector,title:String,description:String,onClick:()->Unit) {
    AppCard(Modifier.fillMaxWidth().clickable(onClick=onClick)) { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)) {
        Icon(icon,null,Modifier.size(31.dp),tint=Teal)
        Column(Modifier.weight(1f)) {Text(tr(title),fontWeight=FontWeight.Bold);Text(tr(description),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Icon(Icons.Default.ChevronRight,null)
    } }
}
@Composable private fun LaunchedScreenLink(title:String,onClick:()->Unit) { PrimaryButton(title, action=onClick) }

private data class SessionContent(val session: CaptureSession?, val items:List<ChecklistItem>,val states:Map<String,ItemSessionState>,val media:Map<String,List<MediaAsset>>)
@Composable private fun SessionScreen(vm:HandoverViewModel) {
    val info by produceState(SessionContent(null,emptyList(),emptyMap(),emptyMap()),vm.inspectionId,vm.phase,vm.refresh) {
        try {
            val session=vm.repo.ensureSession(vm.inspectionId,vm.phase)
            val d=vm.repo.dao
            val items=d.items(vm.inspectionId)
            val states=d.states(session.id).associateBy {it.itemId}
            value=SessionContent(session,items,states,items.associate {it.id to d.itemMedia(it.id,session.id)})
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(failure:Exception) {
            vm.error=failure.message ?: "Cannot open session"
            vm.go("DETAIL")
        }
    }
    val session=info.session
    if(session==null){ Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return }
    val progress = countStatuses(info.items.map {info.states[it.id]?.captureStatus ?: Capture.NOT_CAPTURED})
    Column(Modifier.fillMaxSize()) {
        Header(if(vm.phase==Phase.BASELINE)"Initial condition" else "Return condition",{vm.go("DETAIL")})
        LinearProgressIndicator(progress={if(progress.total>0)(progress.captured+progress.notApplicable+progress.skipped).toFloat()/progress.total else 0f},modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp))
        Text("${progress.captured} photographed · ${progress.skipped} skipped · ${progress.notApplicable} N/A · ${progress.pending} pending",Modifier.padding(20.dp),fontSize=13.sp)
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            items(info.items,key={it.id}) { item ->
                val state=info.states[item.id]?.captureStatus ?: Capture.NOT_CAPTURED
                val photos=info.media[item.id].orEmpty()
                var expanded by remember {mutableStateOf(false)}
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth().clickable {vm.camera(item.id)}) {
                        Thumb(photos.firstOrNull()?.let {vm.repo.assets.resolve(it.thumbnailPath)},Modifier.size(74.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr(item.label),fontWeight=FontWeight.Bold,fontSize=16.sp)
                            Text(when(state){Capture.CAPTURED->"${photos.size} photo(s)";Capture.SKIPPED->"Skipped";Capture.NOT_APPLICABLE->"Not applicable";else->"To photograph"},fontSize=12.sp)
                        }
                        Icon(if(state==Capture.CAPTURED) Icons.Default.CheckCircle else Icons.Default.ChevronRight,null,tint=if(state==Capture.CAPTURED)Teal else MaterialTheme.colorScheme.onSurface)
                    }
                    if (photos.isEmpty()) {
                        TextButton(onClick={expanded=!expanded}) {Text(if(expanded) "Hide other options" else "Skip or mark not applicable")}
                        if(expanded) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={vm.mark(item.id,Capture.SKIPPED)},modifier=Modifier.weight(1f)){Text(tr("Skip"))}
                            OutlinedButton(onClick={vm.mark(item.id,Capture.NOT_APPLICABLE)},modifier=Modifier.weight(1f)){Text(tr("N/A"))}
                        }
                    }
                }
            }
        }
        if(progress.pending>0) Text(tr("Document, skip or mark all pending points before completing this session."), fontSize=12.sp,modifier=Modifier.padding(horizontal=20.dp))
        Box(Modifier.padding(18.dp)) {PrimaryButton(if(vm.phase==Phase.BASELINE) "Complete initial condition" else "Complete return condition", enabled=!vm.loading && progress.pending==0) {vm.complete()} }
    }
}

@Composable private fun CameraScreen(vm:HandoverViewModel) {
    val context=LocalContext.current; val lifecycle=LocalLifecycleOwner.current
    val details=loadInspection(vm)
    val item=details.items.firstOrNull{it.id==vm.itemId}
    val existing=details.sessions.firstOrNull{it.phase==Phase.BASELINE}?.let {details.media[it.id]?.firstOrNull { asset -> asset.itemId==vm.itemId }}
    val cam=remember(context) {GuidedCamera(context)}
    val previewView=remember(context) {PreviewView(context).apply {implementationMode=PreviewView.ImplementationMode.COMPATIBLE}}
    var flash by remember{mutableStateOf(false)};var opacity by remember{mutableFloatStateOf(.42f)}
    var busy by remember {mutableStateOf(false)}
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) }
    var error by remember {mutableStateOf<String?>(null)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){hasPermission=it}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri!=null && item!=null) vm.imported(uri,item.id) }
    DisposableEffect(hasPermission,lifecycle,vm.itemId,previewView) {
        if (hasPermission) cam.bind(lifecycle,previewView) {error=it.localizedMessage}
        onDispose {cam.close()}
    }
    Column(Modifier.fillMaxSize().background(Navy)) {
        Row(verticalAlignment=Alignment.CenterVertically, modifier=Modifier.fillMaxWidth().padding(8.dp)) {
            IconButton(onClick={vm.go("SESSION")}){Icon(Icons.Default.Close,"Close",tint=Color.White)}
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(item?.label ?: "Capture",fontSize=21.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Text(if(vm.phase==Phase.BASELINE)"Initial condition" else "Match the reference angle",color=Color.LightGray,fontSize=12.sp)
            }
            IconButton(onClick={vm.go("SESSION")}){Icon(Icons.Default.ChevronRight,"Next",tint=Color.White)}
        }
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
            if(!hasPermission) {
                Column(Modifier.padding(26.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Text(tr("Camera access is needed only when you take photos."),color=Color.White,textAlign=TextAlign.Center)
                    PrimaryButton("Allow camera") {permission.launch(Manifest.permission.CAMERA)}
                    TextButton(onClick={picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}) {Text(tr("Import a photo instead"))}
                }
            } else {
                AndroidView(factory={previewView},modifier=Modifier.fillMaxSize())
                if(vm.phase==Phase.RETURN && existing!=null) {
                    Thumb(vm.repo.assets.resolve(existing.thumbnailPath),Modifier.fillMaxWidth().fillMaxHeight().alpha(opacity),"Reference overlay")
                }
                Box(Modifier.align(Alignment.TopCenter).padding(18.dp).background(Navy.copy(alpha=.78f),RoundedCornerShape(16.dp)).padding(13.dp)) {
                    Text(item?.hint ?: "Document this area",color=Color.White)
                }
            }
        }
        if(error!=null) Text(error!!,color=Color(0xFFFFCC88),modifier=Modifier.padding(14.dp))
        if(vm.phase==Phase.RETURN && existing!=null) {
            Text(tr("Reference opacity · manual alignment aid"),color=Color.White,modifier=Modifier.padding(horizontal=22.dp,vertical=5.dp),fontSize=12.sp)
            Slider(opacity,{opacity=it},modifier=Modifier.padding(horizontal=22.dp))
        }
        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
            OutlinedButton(onClick={picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text(tr("Import"))}
            FilledIconButton(onClick={
                if (!busy && item!=null) {
                    busy=true
                    cam.take(vm.repo.assets.temporaryCapture(),onSuccess={vm.captured(it,item.id);busy=false},onError={error=it.localizedMessage;busy=false})
                }
            },enabled=hasPermission&&!busy,modifier=Modifier.size(78.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=Teal)) {
                Icon(Icons.Default.CameraAlt,"Take photo",Modifier.size(35.dp))
            }
            IconButton(onClick={flash=!flash;cam.flash(flash)}){Icon(if(flash)Icons.Default.FlashOn else Icons.Default.FlashOff,"Flash",tint=Color.White)}
        }
        TextButton(onClick={vm.go("SESSION")},modifier=Modifier.align(Alignment.CenterHorizontally)) { Text(tr("Back to checklist")) }
    }
}

@Composable private fun ReviewScreen(vm:HandoverViewModel) {
    val context=LocalContext.current
    val asset by produceState<MediaAsset?>(null,vm.assetId,vm.refresh){value=vm.repo.dao.mediaById(vm.assetId)}
    val details=loadInspection(vm)
    val item=details.items.firstOrNull{it.id==asset?.itemId}
    var kind by remember {mutableStateOf("SCRATCH")};var description by remember {mutableStateOf("")}
    var assessment by remember{mutableStateOf(Assessment.NONE)}
    var marker by remember{mutableStateOf<androidx.compose.ui.geometry.Offset?>(null)}
    val a=asset
    if(a==null){CircularProgressIndicator();return}
    Column(Modifier.fillMaxSize()) {
        Header("Add observation",{vm.go("SESSION")})
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(310.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black)
                    .pointerInput(a.id) { detectTapGestures { offset -> marker=androidx.compose.ui.geometry.Offset(offset.x/size.width,offset.y/size.height) } }, contentAlignment=Alignment.Center) {
                    Thumb(vm.repo.assets.resolve(a.thumbnailPath),Modifier.fillMaxSize(),"Captured photo")
                    marker?.let { position ->
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            drawCircle(Color(0xFFFFAD24),radius=35.dp.toPx(),center=androidx.compose.ui.geometry.Offset(position.x*size.width,position.y*size.height),style=androidx.compose.ui.graphics.drawscope.Stroke(width=3.dp.toPx()))
                        }
                    }
                }
            }
            item { Text(tr("Tap on the image to mark a point. This is stored as separate data; the original photo stays unchanged."),fontSize=12.sp) }
            item { Text(tr("Observation type"),fontWeight=FontWeight.Bold); SingleChoiceRow(listOf("SCRATCH","BREAK","MISSING","DIRT","FAULT","OTHER"),kind,{kind=it}) }
            item { OutlinedTextField(description,{description=it},label={Text(tr("Describe the observation"))},modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=5) }
            if(vm.phase==Phase.RETURN) item { Text(tr("Your assessment"),fontWeight=FontWeight.Bold); SingleChoiceRow(listOf(Assessment.NONE,Assessment.NO_VISIBLE_DIFFERENCE,Assessment.PRE_EXISTING,Assessment.RETURN_DIFFERENCE,Assessment.CANNOT_COMPARE),assessment,{assessment=it}) }
        }
        Box(Modifier.padding(18.dp)) {PrimaryButton(if(description.isBlank())"Save photo" else "Save observation") {
            if (description.isBlank()) vm.go("SESSION") else vm.task {
                marker?.let { p -> vm.repo.dao.insertAnnotation(Annotation(newId(), a.id, "{\"x\":${p.x},\"y\":${p.y}}", "CIRCLE", description)) }
                vm.repo.note(vm.inspectionId,a.itemId,a.id,kind,description,assessment)
                vm.go("SESSION")
            }
        } }
    }
}

@Composable private fun ComparisonScreen(vm:HandoverViewModel) {
    val details=loadInspection(vm)
    val first=details.sessions.firstOrNull{it.phase==Phase.BASELINE}
    val second=details.sessions.firstOrNull{it.phase==Phase.RETURN}
    var sideBySide by remember {mutableStateOf(true)}
    Column(Modifier.fillMaxSize()) {
        Header("Compare conditions",{vm.go("DETAIL")})
        if(second==null){ Box(Modifier.fillMaxSize().padding(22.dp),contentAlignment=Alignment.Center){Text(tr("Record return condition to start comparing."),textAlign=TextAlign.Center)};return }
        Row(Modifier.padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){Text(tr("Side-by-side"),modifier=Modifier.weight(1f));Switch(sideBySide,{sideBySide=it})}
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            items(details.items) { item ->
                val base=first?.let{details.media[it.id]?.filter{asset->asset.itemId==item.id}}.orEmpty()
                val returns=details.media[second.id]?.filter{it.itemId==item.id}.orEmpty()
                if(base.isNotEmpty()||returns.isNotEmpty()) {
                    var selectedReturnId by remember(item.id) {mutableStateOf(returns.firstOrNull()?.id)}
                    val ret=returns.firstOrNull{it.id==selectedReturnId} ?: returns.firstOrNull()
                    val pair by produceState<ComparisonPair?>(null,ret?.id,vm.refresh){value=ret?.let {vm.repo.dao.pairForReturn(it.id)}}
                    val baseline=base.firstOrNull{it.id==pair?.baselineAssetId} ?: base.firstOrNull()
                    var assessment by remember(pair?.id) {mutableStateOf(pair?.assessment?:Assessment.NONE)}
                    AppCard(Modifier.fillMaxWidth()) {
                        Text(tr(item.label),fontSize=18.sp,fontWeight=FontWeight.Bold)
                        if(returns.size>1) {
                            Text("Return photos · select one",fontSize=12.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.horizontalScroll(rememberScrollState())) {
                                returns.forEachIndexed { index, asset ->
                                    FilterChip(asset.id==ret?.id, onClick={selectedReturnId=asset.id},label={Text("Photo ${index+1}")})
                                }
                            }
                        }
                        if(base.size>1 && ret!=null) {
                            Text("Choose the matching initial angle",fontSize=12.sp)
                            Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.horizontalScroll(rememberScrollState())) {
                                base.forEachIndexed { index, asset ->
                                    FilterChip(asset.id==baseline?.id,onClick={vm.linkPair(ret.id,asset.id,pair?.assessment ?: Assessment.NONE)},label={Text("Initial ${index+1}")})
                                }
                            }
                        }
                        if(sideBySide) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {Text(tr("Initial"),fontSize=12.sp);Thumb(baseline?.let{vm.repo.assets.resolve(it.thumbnailPath)},Modifier.fillMaxWidth().height(185.dp))}
                            Column(Modifier.weight(1f)) {Text(tr("Return"),fontSize=12.sp);Thumb(ret?.let{vm.repo.assets.resolve(it.thumbnailPath)},Modifier.fillMaxWidth().height(185.dp))}
                        } else {
                            var showReturn by remember{mutableStateOf(false)}
                            TextButton(onClick={showReturn=!showReturn}){Text(if(showReturn)"Show initial" else "Show return")}
                            Thumb((if(showReturn)ret else baseline)?.let{vm.repo.assets.resolve(it.thumbnailPath)},Modifier.fillMaxWidth().height(230.dp))
                        }
                        if(pair!=null) {
                            Text(tr("Your comparison"),fontWeight=FontWeight.Bold)
                            SingleChoiceRow(listOf(Assessment.NO_VISIBLE_DIFFERENCE,Assessment.PRE_EXISTING,Assessment.RETURN_DIFFERENCE,Assessment.CANNOT_COMPARE),assessment,{assessment=it;vm.updatePair(pair!!,it)})
                        } else Text(tr("Take photos in both sessions to compare"),fontSize=12.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun AccessoriesScreen(vm: HandoverViewModel) {
    val list by produceState(emptyList<Accessory>(), vm.inspectionId, vm.refresh) {
        value = vm.repo.dao.accessories(vm.inspectionId)
    }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var before by rememberSaveable { mutableStateOf("1") }
    var after by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val firstQuantity = before.toIntOrNull()
    val returnQuantity = if (after.isBlank()) null else after.toIntOrNull()
    val valid = name.trim().length in 1..120 && firstQuantity != null &&
        firstQuantity in 0..10_000 && (after.isBlank() || returnQuantity != null &&
        returnQuantity in 0..10_000) && note.length <= 2000
    val resetDraft = {
        editingId = null; name = ""; before = "1"; after = ""; note = ""
    }
    Column(Modifier.fillMaxSize()) {
        Header("Accessories", { vm.go("DETAIL") })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(list, key = { it.id }) { accessory ->
                AppCard(Modifier.fillMaxWidth()) {
                    Text(accessory.name, fontWeight = FontWeight.Bold)
                    Text("Initial: ${accessory.baselineQuantity} · Return: ${accessory.returnQuantity?.toString() ?: "Not recorded"}")
                    if (accessory.note.isNotBlank()) Text(accessory.note)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            editingId = accessory.id
                            name = accessory.name
                            before = accessory.baselineQuantity.toString()
                            after = accessory.returnQuantity?.toString() ?: ""
                            note = accessory.note
                        }, enabled = !vm.loading) { Text(tr("Edit")) }
                        TextButton(onClick = { pendingDeleteId = accessory.id },
                            enabled = !vm.loading) { Text(tr("Delete")) }
                    }
                }
            }
            item {
                Text(tr(if (editingId == null) "Add accessory" else "Edit accessory"),
                    fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
            item {
                OutlinedTextField(name, { name = it.take(120) }, label = { Text(tr("Name")) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(before, { before = it.filter(Char::isDigit).take(5) },
                        label = { Text(tr("Initial quantity")) },
                        modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(after, { after = it.filter(Char::isDigit).take(5) },
                        label = { Text(tr("Return quantity")) },
                        modifier = Modifier.weight(1f), singleLine = true)
                }
            }
            item {
                OutlinedTextField(note, { note = it.take(2000) },
                    label = { Text(tr("Optional note")) }, modifier = Modifier.fillMaxWidth())
            }
            item {
                if (editingId != null) {
                    TextButton(onClick = resetDraft, enabled = !vm.loading) { Text(tr("Cancel editing")) }
                }
                PrimaryButton(if (editingId == null) "Save accessory" else "Save changes",
                    enabled = valid && !vm.loading) {
                    val first = firstQuantity ?: return@PrimaryButton
                    val current = editingId
                    if (current == null) vm.addAccessory(name, first, returnQuantity, note, resetDraft)
                    else vm.editAccessory(current, name, first, returnQuantity, note, resetDraft)
                }
            }
        }
    }
    val deleting = pendingDeleteId
    if (deleting != null) AlertDialog(
        onDismissRequest = { pendingDeleteId = null },
        title = { Text(tr("Delete accessory?")) },
        text = { Text(tr("This removes the accessory from this operation.")) },
        confirmButton = {
            TextButton(onClick = {
                vm.deleteAccessory(deleting)
                pendingDeleteId = null
            }) { Text(tr("Delete")) }
        },
        dismissButton = {
            TextButton(onClick = { pendingDeleteId = null }) { Text(tr("Cancel")) }
        }
    )
}

@Composable private fun ReportScreen(vm:HandoverViewModel) {
    var detailed by remember{mutableStateOf(false)};var hideParty by remember{mutableStateOf(true)}
    var hideIdentifiers by remember{mutableStateOf(true)};var hideLocation by remember{mutableStateOf(true)}
    var highQuality by remember{mutableStateOf(false)}
    var language by remember{mutableStateOf("en")}
    val details=loadInspection(vm)
    val exportAssets = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportAssetPackage(uri)
    }
    Column(Modifier.fillMaxSize()) {
        Header("Create report",{vm.go("DETAIL")})
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {Text(tr("Report type"),fontWeight=FontWeight.Bold,fontSize=19.sp)}
            item {AppCard(Modifier.fillMaxWidth()){
                RadioOption("Summary · key information and photos",!detailed){detailed=false}
                RadioOption("Detailed · all checklist points",detailed){detailed=true}
            }}
            item {Text(tr("Sessions included"),fontWeight=FontWeight.Bold)
                Text(details.sessions.joinToString {if(it.phase==Phase.BASELINE)"Initial condition" else "Return condition"}.ifBlank {"None yet"})
                Text("${details.media.values.sumOf{it.size}} photos",fontSize=13.sp)
            }
            item {Text(tr("Sharing privacy"),fontWeight=FontWeight.Bold,fontSize=19.sp)}
            item {AppCard(Modifier.fillMaxWidth()){
                ToggleLine("Hide other party's name",hideParty){hideParty=it}
                ToggleLine("Hide identifiers and reference",hideIdentifiers){hideIdentifiers=it}
                ToggleLine("Hide location field",hideLocation){hideLocation=it}
                Text(tr("Photos may still reveal faces, plates or serial numbers. Review and redact copies before sharing."),fontSize=12.sp)
            }}
            item { ToggleLine("Higher quality images (larger PDF)",highQuality){highQuality=it} }
            item {Text(tr("Report language"),fontWeight=FontWeight.Bold);SingleChoiceRow(listOf("en","ar","fr","es"),language,{language=it})}
            item {Text(tr("Documentation is provided by the user and is not a certified inspection."),fontSize=12.sp) }
            item { AppCard(Modifier.fillMaxWidth()) {
                Text(tr("Export original photos"), fontWeight=FontWeight.Bold)
                Text(tr("The ZIP includes unmodified original images and a JSON manifest with file hashes and timestamps. It may contain sensitive faces, plates or serial numbers."), fontSize=12.sp)
                OutlinedButton(onClick={exportAssets.launch("handover-assets-${System.currentTimeMillis()}.zip")},enabled=!vm.loading,modifier=Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Folder,null); Spacer(Modifier.width(8.dp)); Text(tr("Export asset package"))
                }
            } }
        }
        Box(Modifier.padding(18.dp)){ PrimaryButton("Generate & preview PDF",!vm.loading){
            vm.makeReport(ReportMaker.Options(detailed,language,hideParty,hideIdentifiers,hideLocation,highQuality))
        } }
    }
}
@Composable private fun RadioOption(label:String,selected:Boolean,action:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=action),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f));RadioButton(selected,{action()})}
}
@Composable private fun ToggleLine(label:String,checked:Boolean,onChecked:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {Text(label,Modifier.weight(1f),fontSize=14.sp);Switch(checked,onChecked)}
}
@Composable private fun PdfPreviewScreen(vm:HandoverViewModel) {
    val context=LocalContext.current
    val file=vm.lastReport
    var page by remember(file?.absolutePath) { mutableIntStateOf(0) }
    val preview by produceState<Pair<androidx.compose.ui.graphics.ImageBitmap,Int>?>(null,file?.absolutePath,page) {
        value=withContext(Dispatchers.IO) {
            if(file?.isFile!=true) null else try{ReportMaker(context,vm.repo).renderPage(file,page).let{it.first.asImageBitmap() to it.second}}catch (_:Exception){null}
        }
    }
    Column(Modifier.fillMaxSize()) {
        Header("Report preview",{vm.go("REPORT")})
        Box(Modifier.weight(1f).fillMaxWidth().padding(18.dp),contentAlignment=Alignment.Center){
            if(preview!=null) Image(preview!!.first,"Report page ${page+1}",Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            else Text(tr("Preview not available; inspect the PDF before sharing."))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={page--},enabled=page>0){Icon(Icons.Default.ChevronLeft,"Previous page")}
            Text("${page+1} / ${preview?.second ?: "…"}")
            IconButton(onClick={page++},enabled=page+1 < (preview?.second ?: 0)){Icon(Icons.Default.ChevronRight,"Next page")}
        }
        Text(tr("Review the full PDF before sharing sensitive photographs."),fontSize=12.sp,modifier=Modifier.padding(horizontal=18.dp))
        Box(Modifier.padding(18.dp)) {PrimaryButton("Share report",enabled=file?.isFile==true) {
            if(file!=null)context.startActivity(Intent.createChooser(vm.reportShareIntent(file),"Share PDF"))
        }}
    }
}

@Composable private fun SettingsScreen(vm:HandoverViewModel) {
    val context=LocalContext.current
    val prefs=context.getSharedPreferences("handover_preferences",android.content.Context.MODE_PRIVATE)
    val activity=context as? android.app.Activity
    var language by remember{mutableStateOf(prefs.getString("language","en")?:"en")}
    var theme by remember{mutableStateOf(prefs.getString("theme","system")?:"system")}
    var lock by remember{mutableStateOf(prefs.getBoolean("lock_enabled",false))}
    val auth = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val lockAvailable = androidx.biometric.BiometricManager.from(context).canAuthenticate(auth) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
    Column(Modifier.fillMaxSize()) {
        Header("Settings",{vm.go("HOME")})
        LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { AppCard(Modifier.fillMaxWidth()) {
                Text(tr("Language"),fontWeight=FontWeight.Bold,fontSize=18.sp)
                SingleChoiceRow(listOf("en","ar","fr","es"),language) {choice->
                    language=choice;prefs.edit().putString("language",choice).apply()
                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(choice))
                }
                Text(tr("English is the initial default. You can change language at any time."),fontSize=12.sp)
            } }
            item {AppCard(Modifier.fillMaxWidth()){
                Text(tr("Appearance"),fontWeight=FontWeight.Bold,fontSize=18.sp)
                SingleChoiceRow(listOf("system","light","dark"),theme) {choice->
                    theme=choice;prefs.edit().putString("theme",choice).apply();activity?.recreate()
                }
            }}
            item {AppCard(Modifier.fillMaxWidth()) {
                ToggleLine("Lock app using device security",lock){enabled->
                    if(!enabled || lockAvailable){lock=enabled;prefs.edit().putBoolean("lock_enabled",enabled).apply()}
                }
                if(!lockAvailable) Text("Set up a device screen lock to enable this option.",fontSize=12.sp)
                Text("App lock does not independently encrypt stored photos.",fontSize=12.sp)
            }}
            item {DetailAction(Icons.Default.Backup,"Backup & restore","Keep an offline copy of your original photos") {vm.go("BACKUP")} }
            item {DetailAction(Icons.Default.Help,"Privacy & help","Offline usage, sharing and licenses") {vm.go("HELP")} }
            item { AppCard(Modifier.fillMaxWidth()) {
                Text(tr("Storage"),fontWeight=FontWeight.Bold)
                Text("Originals: ${(context.filesDir.resolve("originals").listFiles()?.sumOf{it.length()}?:0L)/1024/1024} MB",fontSize=13.sp)
                Text("Derived thumbnails: ${vm.repo.assets.derivedBytes()/1024/1024} MB",fontSize=13.sp)
                OutlinedButton(onClick={vm.task { withContext(Dispatchers.IO) { vm.repo.assets.clearThumbnails() } }}, enabled=!vm.loading) {Text(tr("Clear generated thumbnails"))}
                Text(tr("If you uninstall the app without exporting a backup, its local data may be lost."),fontSize=12.sp)
            }}
        }
    }
}

@Composable private fun BackupScreen(vm:HandoverViewModel) {
    val context=LocalContext.current
    val manager=remember {com.khaled.handover.backup.BackupManager(context,vm.repo)}
    var password by remember{mutableStateOf("")};var confirmation by remember{mutableStateOf(false)}
    var status by remember{mutableStateOf("")}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->
        if(uri!=null)vm.task {manager.export(uri,password.takeIf{it.isNotBlank()}?.toCharArray());status="Backup exported. Keep it in a safe place.";password=""}
    }
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)vm.task {manager.restore(uri,password.takeIf{it.isNotBlank()}?.toCharArray());status="Backup restored.";password=""}
    }
    Column(Modifier.fillMaxSize()){
        Header("Backup & restore",{vm.go("SETTINGS")})
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
            item { AppCard(Modifier.fillMaxWidth()) {
                Text(tr("Complete offline backup"),fontWeight=FontWeight.Bold,fontSize=19.sp)
                Text(tr("Exports structured data and the original photos. Choose a secure location using the Android file picker."))
                OutlinedTextField(password,{password=it},label={Text(tr("Optional backup password"))},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation())
                Text(tr("If you forget the password, your encrypted backup cannot be recovered."),fontSize=12.sp)
                PrimaryButton("Export backup",!vm.loading){export.launch("handover-${System.currentTimeMillis()}.hbackup")}
            }}
            item {AppCard(Modifier.fillMaxWidth()) {
                Text(tr("Restore from backup"),fontWeight=FontWeight.Bold,fontSize=19.sp)
                Text(tr("Restore currently requires an empty app. It never silently replaces existing operations."))
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(confirmation,{confirmation=it});Text(tr("I understand and have backed up any existing data"))}
                PrimaryButton("Choose backup to restore",confirmation&&!vm.loading){import.launch(arrayOf("application/octet-stream","application/zip","*/*"))}
            }}
            if(status.isNotBlank()) item {Text(status,color=Teal)}
        }
    }
}

@Composable private fun HelpScreen(vm:HandoverViewModel){
    Column(Modifier.fillMaxSize()){
        Header("Privacy & help",{vm.go("SETTINGS")})
        LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {Text(tr("Your photos stay on your device"),fontWeight=FontWeight.Bold,fontSize=20.sp)}
            item {Text(tr("Handover has no ads, analytics, accounts, upload server, or INTERNET permission. Camera access is requested only for guided capture. You can import an existing photo without granting broad storage permission."))}
            item {Text(tr("Original images are kept as captured or imported. SHA-256 detects differences from the stored local record, but cannot prove when an image was taken or that the scene is authentic."))}
            item {Text(tr("PDF sharing and manual backup occur only when you choose them. Recipients and external storage providers operate under their own privacy practices. Photos can contain identifiers even when text fields are hidden."))}
            item {Text(tr("Deleting the app or losing the device can remove your data. Keep backups. Restore on a clean installation if needed. This is user-provided documentation, not legal certification."))}
            item {Text(tr("Third-party notices and full privacy policy are included in the source package as THIRD_PARTY_NOTICES and PRIVACY_POLICY.md."))}
        }
    }
}
