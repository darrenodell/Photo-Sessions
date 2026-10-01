package com.example.photosessions

import android.Manifest
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import androidx.core.net.toUri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private var requestedSession by mutableLongStateOf(0)
    private var openSession by mutableLongStateOf(0)
    private var resumed by mutableIntStateOf(0)
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { Work.scan(this@MainActivity) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedSession = intent.getLongExtra("session_id", 0)
        openSession = intent.getLongExtra("open_session_id", 0)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF235D51), secondary = Color(0xFF8C6239), background = Color(0xFFF5F3ED), surface = Color(0xFFFFFBF4), surfaceVariant = Color(0xFFE4EBE2))) {
                Organizer((application as SessionApp).repository, requestedSession, openSession, resumed) { requestedSession = 0; openSession = 0; intent.removeExtra("session_id"); intent.removeExtra("open_session_id") }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while(true) { Work.scan(this@MainActivity); delay(30_000) }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); requestedSession = intent.getLongExtra("session_id", 0); openSession = intent.getLongExtra("open_session_id", 0) }
    override fun onStart() {
        super.onStart()
        try { contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer) }
        catch (_: SecurityException) { /* Permission UI remains available; polling retries discovery. */ }
    }
    override fun onResume() { super.onResume(); resumed++; Work.scan(this) }
    override fun onStop() { contentResolver.unregisterContentObserver(observer); super.onStop() }
}

private data class ScreenData(val catalog: Catalog = Catalog(), val baseline: Long? = null, val lastScan: Long? = null, val notifications: Boolean = true,
    val driveAccount: String? = null, val driveReconnect: Boolean = false)
private fun date(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Organizer(repository: Repository, requestedId: Long, openId: Long, resumeCount: Int, consumeRequest: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by repository.revision.collectAsState()
    val scanStatus by repository.status.collectAsState()
    var data by remember { mutableStateOf(ScreenData()) }
    var loaded by remember { mutableStateOf(false) }
    var screen by rememberSaveable { mutableStateOf("home") }
    var sessionId by rememberSaveable { mutableLongStateOf(0) }
    var permissionsTick by remember { mutableIntStateOf(0) }
    var newSession by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    fun action(block: () -> Unit) { if(busy) return; busy = true; scope.launch {
        try { withContext(Dispatchers.IO) { block() } }
        catch (e: Exception) { error = e.message ?: "That action could not finish. Please try again." }
        finally { busy = false }
    } }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); error = null } }
    LaunchedEffect(revision, resumeCount) {
        data = withContext(Dispatchers.IO) { ScreenData(repository.db.load(), repository.db.state("baseline")?.toLongOrNull(), repository.db.state("last_scan")?.toLongOrNull(), repository.db.state("notifications") != "off", repository.db.state("drive_account"), repository.db.state("drive_reconnect") != null) }
        loaded = true
    }
    LaunchedEffect(requestedId) { if(requestedId > 0) { sessionId = requestedId; screen = "name"; consumeRequest() } }
    LaunchedEffect(openId) { if(openId > 0) { sessionId = openId; screen = "session"; consumeRequest() } }
    val access = remember(resumeCount, permissionsTick, revision) { Permissions.access(context) }
    val notificationAllowed = remember(resumeCount, permissionsTick, revision) { Prompts.allowed(context) }
    val photoPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissionsTick++; Work.scan(context) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionsTick++; Work.scan(context) }
    fun requestPhotos() { photoPermission.launch(Permissions.photoPermissions()) }
    fun appSettings() { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())) }
    fun notificationSettings() { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
    // Google Drive: ask for drive.file access, remember the account, then queue the upload.
    var pendingUploadId by rememberSaveable { mutableLongStateOf(0) }
    var pendingWifiOnly by rememberSaveable { mutableStateOf(true) }
    var choosingNetwork by remember { mutableStateOf(false) }
    var disconnecting by remember { mutableStateOf(false) }
    fun queueUpload(result: AuthorizationResult) {
        val token = result.accessToken
        val id = pendingUploadId; val wifiOnly = pendingWifiOnly
        pendingUploadId = 0
        if(token == null || id == 0L) { error = "Google Drive access was not granted."; return }
        action { repository.driveConnected(token); repository.upload(id, wifiOnly) }
    }
    val driveConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { outcome ->
        try { queueUpload(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(outcome.data)) }
        catch(_: Exception) { pendingUploadId = 0; error = "Google Drive access was not granted. Nothing was uploaded." }
    }
    fun startUpload(id: Long, wifiOnly: Boolean) {
        pendingUploadId = id; pendingWifiOnly = wifiOnly
        Identity.getAuthorizationClient(context).authorize(DriveAuth.request(data.driveAccount))
            .addOnSuccessListener { result ->
                val intent = result.pendingIntent
                if(result.hasResolution() && intent != null) driveConsent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                else queueUpload(result)
            }
            .addOnFailureListener { pendingUploadId = 0; error = "Could not reach Google to connect Drive. Check the connection and try again." }
    }
    fun back() { screen = if(screen == "name") "session" else "home" }
    BackHandler(screen != "home") { back() }
    val current = data.catalog.sessions.find { it.id == sessionId }
    val currentPhotos = data.catalog.photos.filter { it.sessionId == sessionId }
    if(newSession) {
        var title by rememberSaveable { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if(!busy) newSession = false }, title = { Text("Start a session") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Give it a name, then take photos with your Camera app. Any active session will be completed.")
                OutlinedTextField(value = title, onValueChange = { title = it.take(200) }, label = { Text("Session name") }, singleLine = true)
            } }, confirmButton = { TextButton(enabled = title.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    try { sessionId = withContext(Dispatchers.IO) { repository.begin(title.trim()) }; screen = "session"; newSession = false }
                    catch(e: Exception) { error = e.message ?: "Could not start session." }
                    finally { busy = false }
                }
            }) { Text("Start session") } }, dismissButton = { TextButton(enabled = !busy, onClick = { newSession = false }) { Text("Cancel") } })
    }
    if(deleting && current != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete session?") },
        text = { Text("Remove this session from the app? Camera originals and copied files will stay on your tablet. This cannot be undone.") },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            val id = sessionId
            scope.launch {
                busy = true
                try { withContext(Dispatchers.IO) { repository.delete(id) }; deleting = false; screen = "home" }
                catch(e: Exception) { error = e.message }
                finally { busy = false }
            }
        }) { Text("Delete session", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
    if(choosingNetwork && current != null) AlertDialog(onDismissRequest = { choosingNetwork = false }, title = { Text("Upload to Google Drive") },
        text = { Text("Photos go to My Drive / $DRIVE_ROOT_FOLDER / ${DriveQuery.folderName(current.name)}. Only photos not already uploaded are sent. Uploading uses about as much data as the photos' size.") },
        confirmButton = { Column(horizontalAlignment = Alignment.End) {
            TextButton(onClick = { choosingNetwork = false; startUpload(current.id, true) }) { Text("Wait for Wi-Fi") }
            TextButton(onClick = { choosingNetwork = false; startUpload(current.id, false) }) { Text("Upload now (Wi-Fi or mobile data)") }
        } },
        dismissButton = { TextButton(onClick = { choosingNetwork = false }) { Text("Cancel") } })
    if(disconnecting) AlertDialog(onDismissRequest = { disconnecting = false }, title = { Text("Disconnect Google Drive?") },
        text = { Text("Pending uploads stop. Files already in Drive stay there. To fully remove this app's access, visit your Google Account's third-party access page.") },
        confirmButton = { TextButton(onClick = { disconnecting = false; action { repository.disconnectDrive() } }) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { disconnecting = false }) { Text("Cancel") } })
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(title = { Text(when(screen) { "settings" -> "Settings"; "name" -> "Name session"; "session" -> "Photo session"; else -> "Photo Sessions" }) },
            navigationIcon = { if(screen != "home") TextButton(onClick = { back() }) { Text("Back") } },
            actions = { if(screen == "home") TextButton(onClick = { screen = "settings" }) { Text("Settings") } })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                screen == "settings" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Discovery", style = MaterialTheme.typography.headlineSmall)
                    Text("New camera photos are grouped into sessions. Copy them into named folders and upload sessions to Google Drive. Camera originals stay untouched.")
                    Text("Session timeout: 15 minutes", style = MaterialTheme.typography.titleMedium)
                    Text("Sessions complete after 15 minutes without a photo. Resume any session to add more photos. Android may delay background checks.")
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Session notifications", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = data.notifications, onCheckedChange = { enabled -> action { repository.notifications(enabled) } })
                    }
                    Text("App preference: ${if(data.notifications) "on" else "off"}. Android permission/channel: ${if(notificationAllowed) "allowed" else "blocked"}.")
                    Text("Turning this off keeps discovery and in-app naming active. Unprompted completed sessions can notify when it is turned back on.")
                    if(Build.VERSION.SDK_INT >= 33 && !notificationAllowed) Button(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                    OutlinedButton(onClick = { notificationSettings() }) { Text("Android notification settings") }
                    HorizontalDivider()
                    Text("Google Drive", style = MaterialTheme.typography.titleMedium)
                    Text(data.driveAccount?.let { "Connected as $it. Uploads go to My Drive / $DRIVE_ROOT_FOLDER." } ?: "Not connected. Tap Upload to Google Drive in a session to connect.")
                    if(data.driveReconnect) Text("Google needs you to reconnect. Open a session and tap Upload to Google Drive.", color = MaterialTheme.colorScheme.error)
                    if(data.driveAccount != null) OutlinedButton(onClick = { disconnecting = true }) { Text("Disconnect Google Drive") }
                    HorizontalDivider()
                    Text("Photo access: ${access.name.lowercase()}")
                    Button(onClick = { requestPhotos() }) { Text("Review photo access") }
                    OutlinedButton(onClick = { appSettings() }) { Text("Android app settings") }
                    data.baseline?.let { Text("Monitoring since ${date(it)}") }
                    data.lastScan?.let { Text("Last successful scan: ${date(it)}") }
                }
                screen == "home" -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("YOUR PHOTO JOURNAL", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                                Text("Make room for\nevery moment.", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                                Text("Start a named session, capture your photos, and keep them together.", color = Color.White.copy(alpha = 0.9f))
                                FilledTonalButton(enabled = !busy, onClick = { newSession = true }) { Text("+  Start a session") }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Your sessions", style = MaterialTheme.typography.titleLarge)
                    }
                    item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            when {
                                data.baseline == null -> {
                                    Text("Start with your next photo", style = MaterialTheme.typography.titleMedium)
                                    Text("Allow photo access to find new camera photos. Existing photos will not be imported. Photos stay on this device unless you choose to share them.")
                                    Button(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { repository.start() }
                                            requestPhotos()
                                        }
                                    }) { Text("Start monitoring") }
                                }
                                access != PhotoAccess.FULL -> {
                                    Text(if(access == PhotoAccess.NONE) "Photo access needed" else "Limited photo access", style = MaterialTheme.typography.titleMedium)
                                    Text("Allow all photos for automatic discovery. With selected access, only the photos you select are visible; new camera photos may be missed.")
                                    Button(onClick = { requestPhotos() }) { Text("Review photo access") }
                                    TextButton(onClick = { appSettings() }) { Text("Open Android settings") }
                                }
                                else -> Text(scanStatus.ifBlank { "Monitoring is ready. Checking for new camera photos…" })
                            }
                            if(data.baseline != null) TextButton(onClick = { Work.scan(context) }) { Text("Check now") }
                        } }
                    }
                    if(data.baseline != null && data.notifications && !notificationAllowed) item {
                        OutlinedCard { Column(Modifier.padding(16.dp)) {
                            Text("Get one reminder to name each completed session.")
                            TextButton(onClick = {
                                if(Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else notificationSettings()
                            }) { Text("Enable reminders") }
                            TextButton(onClick = { notificationSettings() }) { Text("Android notification settings") }
                        } }
                    }
                    if(data.catalog.sessions.isEmpty()) item {
                        Spacer(Modifier.height(24.dp))
                        Text("No sessions yet", style = MaterialTheme.typography.titleLarge)
                        Text("New camera photos taken after setup will appear here. Photos in screenshots, downloads, and other folders are not included.")
                    }
                    items(data.catalog.sessions, key = { it.id }) { session ->
                        val photos = data.catalog.photos.filter { it.sessionId == session.id }
                        Card(onClick = { sessionId = session.id; screen = "session" }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Thumbnail(photos.firstOrNull { it.available } ?: photos.firstOrNull(), revision + resumeCount, Modifier.size(80.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(session.name ?: "Unnamed session", style = MaterialTheme.typography.titleMedium)
                                    Text(date(session.start), style = MaterialTheme.typography.bodySmall)
                                    Text("${photos.size} photos · ${if(session.complete) "Complete" else "Active"}")
                                    Text("${photos.count { it.copyState == "copied" }} copied · ${photos.count { it.driveState == "uploaded" && it.driveAccount == data.driveAccount }} in Drive", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                current == null -> Column(Modifier.padding(24.dp)) { Text("This session is unavailable."); TextButton(onClick = { screen = "home" }) { Text("Go to sessions") } }
                screen == "name" -> {
                    var name by rememberSaveable(sessionId) { mutableStateOf(current.name.orEmpty()) }
                    var saving by remember { mutableStateOf(false) }
                    fun finish(value: String?) { saving = true; scope.launch {
                        try { withContext(Dispatchers.IO) { repository.name(sessionId, value) }; screen = "session" }
                        catch (_: Exception) { error = "Could not save. Please try again." }
                        finally { saving = false }
                    } }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Thumbnail(currentPhotos.firstOrNull { it.available } ?: currentPhotos.firstOrNull(), revision + resumeCount, Modifier.fillMaxWidth().height(220.dp))
                        Text("${currentPhotos.size} photos", style = MaterialTheme.typography.headlineSmall)
                        Text("${date(current.start)} — ${date(current.end)}")
                        OutlinedTextField(value = name, onValueChange = { name = it.take(200) }, modifier = Modifier.fillMaxWidth(), label = { Text("Session name") }, placeholder = { Text("e.g. Garage wiring") }, singleLine = true, supportingText = { Text("Choose any name, up to 200 characters.") })
                        Button(onClick = { finish(name.trim()) }, enabled = name.isNotBlank() && !saving, modifier = Modifier.fillMaxWidth()) { Text("Save") }
                        if(current.name == null) OutlinedButton(onClick = { finish(null) }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
                        if(current.name == null) Text("Skipping keeps this session unnamed. You can name it here later.")
                    }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(current.name ?: "Unnamed session", style = MaterialTheme.typography.headlineSmall)
                    Text("${date(current.start)} — ${date(current.end)}")
                    Text("${currentPhotos.size} photos · ${if(current.complete) "Complete" else "In progress"}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = { if(current.complete) {
                            if(current.name == null) screen = "name" else action { repository.begin(current.name, current.id) }
                        } else action { repository.stop(current.id) } }) { Text(if(current.complete) "Resume session" else "Stop session") }
                        OutlinedButton(enabled = !busy, onClick = { screen = "name" }) { Text(if(current.name == null) "Name session" else "Rename") }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${currentPhotos.count { it.copyState == "copied" }} copied · ${currentPhotos.count { it.copyState != "copied" }} need copying", style = MaterialTheme.typography.titleSmall)
                            Text(SessionFiles(context, repository.db).folder(current.id) ?: "Copies will appear in Pictures/Photo Sessions after you copy them.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(enabled = !busy && currentPhotos.isNotEmpty() && current.name != null, onClick = { action { repository.copy(current.id) } }) { Text("Copy pending photos") }
                            }
                            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    val works by remember(current.id) { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(DriveWork.name(current.id)) }.collectAsState(initial = emptyList())
                    val work = works.firstOrNull { !it.state.isFinished }
                    val workDone = work?.progress?.getInt("done", 0) ?: 0
                    val workTotal = work?.progress?.getInt("total", 0) ?: 0
                    val uploaded = currentPhotos.count { it.driveState == "uploaded" && it.driveAccount == data.driveAccount && data.driveAccount != null }
                    val failedUploads = currentPhotos.count { it.driveState == "failed" && it.driveAccount == data.driveAccount }
                    val folderUrl = remember(current.id, revision, data.driveAccount) { DriveUploader(context, repository.db).folderUrl(current.id) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Google Drive · $uploaded of ${currentPhotos.size} uploaded", style = MaterialTheme.typography.titleSmall)
                            val wifiOnly = repository.db.state("drive_mode_${current.id}") != "any"
                            Text(when(work?.state) {
                                WorkInfo.State.RUNNING -> if(workTotal > 0) "Uploading $workDone of $workTotal…" else "Connecting to Google Drive…"
                                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> if(wifiOnly) "Waiting for Wi-Fi…" else "Waiting for a connection…"
                                else -> when {
                                    data.driveReconnect -> "Google needs you to reconnect. Tap Upload to Google Drive."
                                    failedUploads > 0 -> "$failedUploads photos could not be read. Upload again to retry them."
                                    data.driveAccount == null -> "Connect your Google account to upload this session."
                                    uploaded == currentPhotos.size && uploaded > 0 -> "All photos are in Drive."
                                    else -> "Uploads go to My Drive / $DRIVE_ROOT_FOLDER / ${DriveQuery.folderName(current.name)}."
                                }
                            }, style = MaterialTheme.typography.bodySmall)
                            if(work?.state == WorkInfo.State.RUNNING && workTotal > 0)
                                LinearProgressIndicator(progress = { workDone.toFloat() / workTotal }, modifier = Modifier.fillMaxWidth())
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if(work == null) TextButton(enabled = !busy && currentPhotos.isNotEmpty() && pendingUploadId == 0L, onClick = { choosingNetwork = true }) { Text("Upload to Google Drive") }
                                else TextButton(enabled = !busy, onClick = { action { repository.cancelUpload(current.id) } }) { Text("Cancel upload") }
                                if(folderUrl != null) TextButton(onClick = {
                                    try { context.startActivity(Intent(Intent.ACTION_VIEW, folderUrl.toUri())) } catch(_: Exception) { error = "No app can open Drive links." }
                                }) { Text("Open in Drive") }
                            }
                            TextButton(enabled = !busy && currentPhotos.isNotEmpty(), onClick = {
                                busy = true
                                scope.launch {
                                    try {
                                        val share = withContext(Dispatchers.IO) { SessionFiles(context, repository.db).shareIntent(current.id) }
                                        context.startActivity(Intent.createChooser(share, "Share copied photos"))
                                    } catch(e: Exception) { error = e.message ?: "Could not share photos." }
                                    finally { busy = false }
                                }
                            }) { Text("Share copies…") }
                            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                    TextButton(enabled = !busy, onClick = { deleting = true }) { Text("Delete session", color = MaterialTheme.colorScheme.error) }
                    if(currentPhotos.any { !it.available } || access != PhotoAccess.FULL) Text("Some photos may be unavailable because access changed, their storage is offline, or they were removed.")
                    currentPhotos.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { photo -> Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Thumbnail(photo, revision + resumeCount, Modifier.fillMaxWidth().aspectRatio(1f))
                                Text(when(photo.copyState) { "copied" -> "Copied"; "failed" -> "Copy failed · retry"; "copying" -> "Needs retry"; else -> "Needs copying" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Thumbnail(photo: Photo?, refresh: Long, modifier: Modifier) {
    val context = LocalContext.current
    var bitmap by remember(photo?.key, refresh) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(photo?.key, refresh) { mutableStateOf(true) }
    LaunchedEffect(photo?.key, photo?.available, refresh) {
        bitmap = if(photo?.available == true && Permissions.access(context) != PhotoAccess.NONE) withContext(Dispatchers.IO) {
            try { context.contentResolver.loadThumbnail(photo.uri.toUri(), Size(400, 400), null) }
            catch (_: Exception) { null }
        } else null
        loading = false
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Camera photo taken ${date(photo!!.time)}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Text(if(loading) "Loading…" else "Photo unavailable", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
    }
}
