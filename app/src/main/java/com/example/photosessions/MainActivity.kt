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
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.text.style.TextOverflow
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
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        requestedSession = intent.getLongExtra("session_id", 0)
        openSession = intent.getLongExtra("open_session_id", 0)
        setContent {
            PhotoSessionsTheme {
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
    val driveAccount: String? = null, val driveReconnect: Boolean = false, val photosAccount: String? = null, val photosReconnect: Boolean = false, val photosCounts: Map<Long, Int> = emptyMap())
private fun date(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
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
        data = withContext(Dispatchers.IO) {
            val catalog = repository.db.load()
            val photosAccount = repository.db.state("photos_account")
            ScreenData(catalog, repository.db.state("baseline")?.toLongOrNull(), repository.db.state("last_scan")?.toLongOrNull(), repository.db.state("notifications") != "off", repository.db.state("drive_account"), repository.db.state("drive_reconnect") != null, photosAccount, repository.db.state("photos_reconnect") != null, photosExportCounts(repository.db, catalog, photosAccount)) }
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
    var photosDestination by rememberSaveable { mutableStateOf(false) }
    var disconnectingPhotos by remember { mutableStateOf(false) }
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
    fun queuePhotos(result: AuthorizationResult) {
        val token = result.accessToken; val id = pendingUploadId; val wifi = pendingWifiOnly
        pendingUploadId = 0
        if(token == null || id == 0L || !result.grantedScopes.containsAll(PhotosAuth.scopes)) {
            error = "Google Photos access was not fully granted. Nothing was added."; return
        }
        action { repository.photosConnected(token); repository.addToPhotos(id, wifi) }
    }
    val photosConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { outcome ->
        try { queuePhotos(Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(outcome.data)) }
        catch(_: Exception) { pendingUploadId = 0; error = "Google Photos access was not granted. Nothing was added." }
    }
    fun startPhotos(id: Long, wifiOnly: Boolean) {
        pendingUploadId = id; pendingWifiOnly = wifiOnly
        Identity.getAuthorizationClient(context).authorize(PhotosAuth.request(data.photosAccount))
            .addOnSuccessListener { result ->
                val intent = result.pendingIntent
                if(result.hasResolution() && intent != null) photosConsent.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                else queuePhotos(result)
            }.addOnFailureListener { pendingUploadId = 0; error = "Could not connect Google Photos. Check the connection and try again." }
    }
    fun back() { screen = if(screen == "name" || screen == "local") "session" else "home" }
    BackHandler(screen != "home") { back() }
    val current = data.catalog.sessions.find { it.id == sessionId }
    val currentPhotos = data.catalog.photos.filter { it.sessionId == sessionId }
    var resuming by rememberSaveable { mutableStateOf(false) }
    if(newSession) StartSessionDialog(busy = busy, onDismiss = { newSession = false }, onStart = { title, duration ->
        busy = true
        scope.launch {
            try { sessionId = withContext(Dispatchers.IO) { repository.begin(title, duration = duration) }; screen = "session"; newSession = false }
            catch(e: Exception) { error = e.message ?: "Could not start session." }
            finally { busy = false }
        }
    })
    if(resuming && current != null) SessionDialog(title = "Resume session", onDismissRequest = { if(!busy) resuming = false },
        content = {
            Text(SessionLabels.title(current))
            ExtensionChoices(!busy) { duration -> action { repository.begin(current.name, current.id, duration); resuming = false } }
        }, actions = { DialogAction("Cancel", onClick = { resuming = false }, enabled = !busy, outlined = true) })
    val deletingSession = data.catalog.sessions.find { it.id == deletingId }
    val deletionPhotos = data.catalog.photos.filter { it.sessionId == deletingSession?.id }
    var deletionWarning by remember(deletingId, deletionPhotos) { mutableStateOf<Int?>(null) }
    LaunchedEffect(deletingId, deletionPhotos) {
        if(deletingSession != null) deletionWarning = withContext(Dispatchers.IO) { SessionFiles(context, repository.db).unexported(deletionPhotos) }
    }
    if(deletingSession != null) SessionDialog(onDismissRequest = { if(!busy) deletingId = null }, title = "Delete session?",
        content = {
            Text(SessionLabels.title(deletingSession), style = MaterialTheme.typography.titleMedium)
            if(deletionWarning == null) Text("Checking device copies…")
            val unexported = deletionWarning ?: 0
            if(unexported > 0) Text("Warning: $unexported ${if(unexported == 1) "photo has" else "photos have"} no device copy, Google Drive upload or Google Photos album. Copy or upload them first if you want to keep this grouping.", color = MaterialTheme.colorScheme.error)
            Text("The session will be removed from Photo Sessions. Your original photos, device copies, Google Drive folders and Google Photos albums will not be deleted. This cannot be undone.")
        },
        actions = { DialogAction("Delete session", destructive = true, enabled = !busy && deletionWarning != null, onClick = {
            val id = deletingSession.id
            busy = true
            scope.launch {
                try { withContext(Dispatchers.IO) { repository.delete(id) }; deletingId = null; if(sessionId == id) screen = "home" }
                catch(e: Exception) { error = e.message }
                finally { busy = false }
            }
        })
            DialogAction("Cancel", enabled = !busy, onClick = { deletingId = null }, outlined = true)
        })
    if(choosingNetwork && current != null) UploadNetworkDialog(
        sessionLabel = SessionLabels.title(current),
        onWifi = { choosingNetwork = false; if(photosDestination) startPhotos(current.id, true) else startUpload(current.id, true) },
        onAnyNetwork = { choosingNetwork = false; if(photosDestination) startPhotos(current.id, false) else startUpload(current.id, false) },
        onDismiss = { choosingNetwork = false }, googlePhotos = photosDestination)
    if(disconnecting) SessionDialog(onDismissRequest = { disconnecting = false }, title = "Disconnect Google Drive?",
        content = { Text("Pending uploads stop. Files already in Drive stay there. To fully remove this app's access, visit your Google Account's third-party access page.") },
        actions = {
            DialogAction("Disconnect", onClick = { disconnecting = false; action { repository.disconnectDrive() } })
            DialogAction("Cancel", onClick = { disconnecting = false }, outlined = true)
        })
    if(disconnectingPhotos) SessionDialog(title = "Disconnect Google Photos?", onDismissRequest = { disconnectingPhotos = false },
        content = { Text("Pending Photos uploads stop. Photos and albums already in Google Photos stay there. To fully remove access, visit your Google Account's third-party access page.") },
        actions = {
            DialogAction("Disconnect", onClick = { disconnectingPhotos = false; action { repository.disconnectPhotos() } })
            DialogAction("Cancel", onClick = { disconnectingPhotos = false }, outlined = true)
        })
    var sessionMenu by remember { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) }, topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            title = { Text(when(screen) { "settings" -> "Settings"; "name" -> if(current?.complete == true) "Name or extend" else "Name session"; "session" -> "Session"; "local" -> "Local copies"; else -> "Photo Sessions" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { if(screen != "home") IconButton(onClick = { back() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = {
                if(screen == "home") IconButton(onClick = { screen = "settings" }) { Icon(Icons.Outlined.Settings, "Settings") }
                if(screen == "session" && current != null) Box {
                    IconButton(onClick = { sessionMenu = true }) { Icon(Icons.Outlined.MoreVert, "Session options") }
                    DropdownMenu(expanded = sessionMenu, onDismissRequest = { sessionMenu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, enabled = !busy,
                            onClick = { sessionMenu = false; screen = "name" })
                        DropdownMenuItem(text = { Text("Delete session", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, enabled = !busy,
                            onClick = { sessionMenu = false; deletingId = current.id })
                    }
                }
            })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                screen == "settings" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = 680.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                        SettingsGroup("Photo discovery") {
                            Text("New camera photos and screenshots are grouped into sessions. Your originals stay untouched.")
                            Text("Session timing", style = MaterialTheme.typography.titleMedium)
                            Text("Automatic sessions end after 30 minutes without a photo or screenshot. Manual sessions and extensions use the hours you choose, starting when you tap. Android may delay background reminders.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider()
                            Text("Photo access: ${access.name.lowercase()}", style = MaterialTheme.typography.titleSmall)
                            TextButton(onClick = { requestPhotos() }) { Text("Review photo access") }
                            TextButton(onClick = { appSettings() }) { Text("Android app settings") }
                        }
                        SettingsGroup("Notifications") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Session reminders", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Switch(checked = data.notifications, onCheckedChange = { enabled -> action { repository.notifications(enabled) } })
                            }
                            Text("One reminder to name each completed session. Photo discovery continues when reminders are off.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Android notifications: ${if(notificationAllowed) "allowed" else "blocked"}", style = MaterialTheme.typography.bodySmall)
                            if(Build.VERSION.SDK_INT >= 33 && !notificationAllowed) TextButton(onClick = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                            TextButton(onClick = { notificationSettings() }) { Text("Android notification settings") }
                        }
                        SettingsGroup("Google Drive") {
                            Text(data.driveAccount ?: "Not connected", style = MaterialTheme.typography.titleMedium)
                            Text(if(data.driveAccount == null) "Open a session and choose Upload to Google Drive to connect." else "Uploads go to My Drive / $DRIVE_ROOT_FOLDER.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if(data.driveReconnect) Text("Reconnect by opening a session and choosing Upload to Google Drive.", color = MaterialTheme.colorScheme.error)
                            if(data.driveAccount != null) TextButton(onClick = { disconnecting = true }) { Text("Disconnect Google Drive") }
                        }
                        SettingsGroup("Google Photos") {
                            Text(data.photosAccount ?: "Not connected", style = MaterialTheme.typography.titleMedium)
                            Text("Each session goes into its own album. Open a session and choose Add to Google Photos to connect.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if(data.photosReconnect) Text("Reconnect by choosing Add to Google Photos in a session.", color = MaterialTheme.colorScheme.error)
                            if(data.photosAccount != null) TextButton(onClick = { disconnectingPhotos = true }) { Text("Disconnect Google Photos") }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Photo Sessions ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}", style = MaterialTheme.typography.labelLarge)
                            data.baseline?.let { Text("Monitoring since ${date(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            data.lastScan?.let { Text("Last successful scan: ${date(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                screen == "home" -> SessionLibrary(data.catalog, revision + resumeCount, data.driveAccount, busy, photosCounts = data.photosCounts,
                    onStart = { newSession = true }, onOpen = { sessionId = it; screen = "session" }, onDelete = { deletingId = it }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            data.baseline == null || access != PhotoAccess.FULL -> Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if(data.baseline == null) "Set up photo discovery" else if(access == PhotoAccess.NONE) "Photo access needed" else "Limited photo access", style = MaterialTheme.typography.titleMedium)
                                    Text("Allow all photos to discover new camera photos and screenshots. Existing images are not imported.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(enabled = !busy, onClick = {
                                            if(data.baseline == null) {
                                                busy = true
                                                scope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) { repository.start() }
                                                        requestPhotos()
                                                    } catch(e: Exception) { error = e.message ?: "Could not start photo discovery." }
                                                    finally { busy = false }
                                                }
                                            } else requestPhotos()
                                        }) { Text(if(data.baseline == null) "Start monitoring" else "Review photo access") }
                                        if(data.baseline != null) TextButton(onClick = { appSettings() }) { Text("Android app settings") }
                                    }
                                }
                            }
                            else -> MonitoringStatus(
                                message = scanStatus.takeIf { it.isNotBlank() && it != "Monitoring camera photos and screenshots. Background checks may be delayed by Android." },
                                onCheck = { Work.scan(context) })
                        }
                        if(data.baseline != null && data.notifications && !notificationAllowed) TextButton(onClick = {
                            if(Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else notificationSettings()
                        }) { Text("Enable session reminders") }
                    }
                }
                current == null -> Column(Modifier.padding(24.dp)) { Text("This session is unavailable."); TextButton(onClick = { screen = "home" }) { Text("Go to sessions") } }
                screen == "local" -> LocalCopiesGallery(
                    session = current, photos = currentPhotos, refresh = revision + resumeCount,
                    folder = SessionFiles(context, repository.db).folder(current.id),
                    onPhoto = { photo ->
                        try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(photo.copyUri!!.toUri(), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                        catch(_: Exception) { error = "This local copy is unavailable. Use Copy Locally to recreate missing copies." }
                    })
                screen == "name" -> {
                    var name by rememberSaveable(sessionId) { mutableStateOf(current.name.orEmpty()) }
                    var sessionDate by rememberSaveable(sessionId) { mutableStateOf(current.sessionDate) }
                    var saving by remember { mutableStateOf(false) }
                    fun finish(value: String?) { saving = true; scope.launch {
                        try { withContext(Dispatchers.IO) { repository.name(sessionId, value, sessionDate) }; screen = "session" }
                        catch (_: Exception) { error = "Could not save. Please try again." }
                        finally { saving = false }
                    } }
                    Column(Modifier.align(Alignment.TopCenter).widthIn(max = 600.dp).fillMaxHeight().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Thumbnail(currentPhotos.firstOrNull { it.available } ?: currentPhotos.firstOrNull(), revision + resumeCount, Modifier.fillMaxWidth().height(if(current.complete) 120.dp else 220.dp))
                        Text(photoCountLabel(currentPhotos.size), style = MaterialTheme.typography.headlineSmall)
                        Text("${date(current.start)} — ${date(current.end)}")
                        OutlinedTextField(value = name, onValueChange = { name = it.take(200) }, modifier = Modifier.fillMaxWidth(), label = { Text("Session name") }, placeholder = { Text("e.g. Garage wiring") }, singleLine = true, supportingText = { Text("Choose any name, up to 200 characters.") })
                        OutlinedTextField(value = sessionDate, onValueChange = { sessionDate = it.take(10) }, modifier = Modifier.fillMaxWidth(), label = { Text("Session date (optional)") }, singleLine = true,
                            isError = !SessionLabels.validSessionDate(sessionDate), placeholder = { Text("YYYY-MM-DD") },
                            trailingIcon = { if(sessionDate.isNotEmpty()) TextButton(enabled = !saving, onClick = { sessionDate = "" }) { Text("Clear") } },
                            supportingText = { Text("Leave blank to use only the session name.") })
                        Text("Preview: ${SessionLabels.title(current.copy(name = name.trim().ifBlank { null }, sessionDate = sessionDate))}", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { finish(name.trim()) }, enabled = name.isNotBlank() && SessionLabels.validSessionDate(sessionDate) && !saving, modifier = Modifier.fillMaxWidth()) { Text("Save") }
                        if(current.name == null) OutlinedButton(onClick = { finish(null) }, enabled = !saving && SessionLabels.validSessionDate(sessionDate), modifier = Modifier.fillMaxWidth()) { Text("Skip") }
                        if(current.name == null) Text("Skipping keeps this session unnamed. You can name it here later.")
                        if(current.complete) {
                            HorizontalDivider()
                            ExtensionChoices(!saving && !busy && SessionLabels.validSessionDate(sessionDate)) { duration ->
                                saving = true
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            repository.begin(current.name, current.id, duration)
                                            if(name.isNotBlank() || sessionDate != current.sessionDate)
                                                repository.name(current.id, name.trim().ifBlank { current.name }, sessionDate)
                                        }
                                        screen = "session"
                                    } catch(e: Exception) { error = e.message ?: "Could not resume session." }
                                    finally { saving = false }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val works by remember(current.id) { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(DriveWork.name(current.id)) }.collectAsState(initial = emptyList())
                    val work = works.firstOrNull { !it.state.isFinished }
                    val workDone = work?.progress?.getInt("done", 0) ?: 0
                    val workTotal = work?.progress?.getInt("total", 0) ?: 0
                    val uploaded = currentPhotos.count { it.driveState == "uploaded" && it.driveAccount == data.driveAccount && data.driveAccount != null }
                    val failedUploads = currentPhotos.count { it.driveState == "failed" && it.driveAccount == data.driveAccount }
                    val photosWorks by remember(current.id) { WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(PhotosWork.name(current.id)) }.collectAsState(initial = emptyList())
                    val photosWork = photosWorks.firstOrNull { !it.state.isFinished }
                    val photosState = remember(current.id, data.photosAccount, revision) { data.photosAccount?.let { PhotosState(repository.db, it, current.id) } }
                    val inPhotos = data.photosCounts[current.id] ?: 0
                    val localCount = currentPhotos.count { it.copyState == "copied" && it.copyUri != null }
                    SessionGallery(
                        sessionId = current.id, photos = currentPhotos, refresh = revision + resumeCount,
                        transferSummary = when(work?.state) {
                            WorkInfo.State.RUNNING -> if(workTotal > 0) "Uploading $workDone of $workTotal…" else "Connecting to Google Drive…"
                            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "Upload queued — open for details"
                            else -> when {
                                photosWork != null -> "Adding to Google Photos — open for details"
                                ExportNames(context, repository.db).problems(current.id).isNotEmpty() -> "Export names need updating"
                                data.driveReconnect -> "Reconnect to Google Drive"
                                failedUploads > 0 -> "$failedUploads uploads need attention"
                                else -> "$localCount in Local  •  $uploaded in Drive  •  $inPhotos in Photos"
                            }
                        },
                        onPhoto = { photo ->
                            try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(photo.uri.toUri(), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                            catch(_: Exception) { error = "This photo is unavailable, or no app can open it." }
                        },
                        header = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SessionBadge(!current.complete)
                                SessionRules.deadline(current, data.catalog)?.let { deadline ->
                                    Text(if(current.manual) "Ends ${date(deadline)}" else "Ends after 30 minutes without a photo or screenshot", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(SessionLabels.title(current), style = MaterialTheme.typography.headlineLarge)
                                Text("${photoCountLabel(currentPhotos.size)}  •  ${date(current.start)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(enabled = !busy, onClick = { if(current.complete) {
                                        resuming = true
                                    } else action { repository.stop(current.id) } }) { Text(if(current.complete) "Resume session" else "Stop session") }
                                    TextButton(enabled = !busy, onClick = { screen = "name" }) { Text(if(current.name == null) "Name session" else "Rename") }
                                }
                                if(currentPhotos.any { !it.available } || access != PhotoAccess.FULL) Text("Some photos may be unavailable. Check photo access or device storage.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        },
                        tools = {
                            val renameProblems = remember(current.id, revision) { ExportNames(context, repository.db).problems(current.id) }
                            if(renameProblems.isNotEmpty()) Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Export names need updating", style = MaterialTheme.typography.titleSmall)
                                    Text("Your session name was saved. Some exported folders or albums still use an older name.", style = MaterialTheme.typography.bodySmall)
                                    renameProblems.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    TextButton(enabled = !busy, onClick = { action { repository.retryNames(current.id) } }) { Text("Retry names") }
                                }
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Google Photos · $inPhotos of ${currentPhotos.size} added", style = MaterialTheme.typography.titleSmall)
                                    val photosError = repository.db.state("photos_error_${current.id}")
                                    val wifiOnly = repository.db.state("photos_mode_${current.id}") != "any"
                                    val done = photosWork?.progress?.getInt("done", 0) ?: 0
                                    val total = photosWork?.progress?.getInt("total", 0) ?: 0
                                    Text(when(photosWork?.state) {
                                        WorkInfo.State.RUNNING -> if(total > 0) "Adding $done of $total…" else "Connecting to Google Photos…"
                                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> photosError?.let { "Upload paused. Retrying automatically.\n$it" } ?: if(wifiOnly) "Waiting for Wi-Fi…" else "Waiting for a connection…"
                                        else -> when {
                                            data.photosReconnect -> "Google needs you to reconnect. Tap Add to Google Photos."
                                            photosError != null -> "Could not finish. Add again to retry.\n$photosError"
                                            inPhotos == currentPhotos.size && inPhotos > 0 -> "All photos have been added to this session's album."
                                            else -> "Album: ${photosAlbumTitle(SessionLabels.title(current))}"
                                        }
                                    }, style = MaterialTheme.typography.bodySmall)
                                    if(photosWork?.state == WorkInfo.State.RUNNING && total > 0) LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                                    if(photosWork == null) ExportActionButton("Add to Google Photos", enabled = !busy && currentPhotos.isNotEmpty() && pendingUploadId == 0L,
                                        onClick = { photosDestination = true; choosingNetwork = true })
                                    else ExportActionButton("Cancel Photos upload", enabled = !busy, onClick = { action { repository.cancelPhotos(current.id) } })
                                    // Album creation precedes media upload. Don't send the user to
                                    // an empty/not-yet-ready album while its upload is still running.
                                    photosState?.url()?.takeIf { inPhotos > 0 && photosWork == null }?.let { url -> TextButton(onClick = {
                                        try { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } catch(_: Exception) { error = "No app can open Google Photos links." }
                                    }) { Text("Open in Google Photos") } }
                                }
                            }
                            val folderUrl = remember(current.id, revision, data.driveAccount) { DriveUploader(context, repository.db).folderUrl(current.id) }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Google Drive · $uploaded of ${currentPhotos.size} uploaded", style = MaterialTheme.typography.titleSmall)
                                    val wifiOnly = repository.db.state("drive_mode_${current.id}") != "any"
                                    val uploadError = repository.db.state("drive_error_${current.id}")
                                    Text(when(work?.state) {
                                        WorkInfo.State.RUNNING -> if(workTotal > 0) "Uploading $workDone of $workTotal…" else "Connecting to Google Drive…"
                                        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> if(uploadError != null) "Upload paused. Retrying automatically.\n$uploadError" else if(wifiOnly) "Waiting for Wi-Fi…" else "Waiting for a connection…"
                                        else -> when {
                                            data.driveReconnect -> "Google needs you to reconnect. Tap Upload to Google Drive."
                                            uploadError != null -> "Upload could not finish. Upload again to retry.\n$uploadError"
                                            failedUploads > 0 -> "$failedUploads photos could not be read. Upload again to retry them."
                                            data.driveAccount == null -> "Connect your Google account to upload this session."
                                            uploaded == currentPhotos.size && uploaded > 0 -> "All photos are in Drive."
                                            else -> "Uploads go to My Drive / $DRIVE_ROOT_FOLDER / ${DriveQuery.folderName(SessionLabels.title(current))}."
                                        }
                                    }, style = MaterialTheme.typography.bodySmall)
                                    if(work?.state == WorkInfo.State.RUNNING && workTotal > 0)
                                        LinearProgressIndicator(progress = { workDone.toFloat() / workTotal }, modifier = Modifier.fillMaxWidth())
                                    if(work == null) ExportActionButton("Upload to Google Drive", enabled = !busy && currentPhotos.isNotEmpty() && pendingUploadId == 0L,
                                        onClick = { photosDestination = false; choosingNetwork = true })
                                    else ExportActionButton("Cancel Drive upload", enabled = !busy, onClick = { action { repository.cancelUpload(current.id) } })
                                    if(folderUrl != null) TextButton(onClick = {
                                        try { context.startActivity(Intent(Intent.ACTION_VIEW, folderUrl.toUri())) } catch(_: Exception) { error = "No app can open Drive links." }
                                    }) { Text("Open in Drive") }
                                }
                            }
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Local · $localCount of ${currentPhotos.size} copied", style = MaterialTheme.typography.titleSmall)
                                    Text(SessionFiles(context, repository.db).folder(current.id) ?: "Copies will appear in Pictures/Photo Sessions after you copy them.", style = MaterialTheme.typography.bodySmall)
                                    LocalCopyActions(copied = localCount, total = currentPhotos.size, busy = busy, named = current.name != null,
                                        onCopy = { action { repository.copy(current.id) } }, onOpen = { screen = "local" },
                                        onShare = {
                                            busy = true
                                            scope.launch {
                                                try {
                                                    val share = withContext(Dispatchers.IO) { SessionFiles(context, repository.db).shareIntent(current.id) }
                                                    context.startActivity(Intent.createChooser(share, "Share copied photos"))
                                                } catch(e: Exception) { error = e.message ?: "Could not share photos." }
                                                finally { busy = false }
                                            }
                                        })
                                    if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun Thumbnail(photo: Photo?, refresh: Long, modifier: Modifier) {
    val context = LocalContext.current
    // Keep an already loaded image visible while a background scan refreshes it.
    // Clear immediately when the photo identity or availability changes.
    var bitmap by remember(photo?.key, photo?.uri, photo?.available) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(photo?.key, photo?.uri, photo?.available) { mutableStateOf(photo?.available == true) }
    LaunchedEffect(photo?.key, photo?.uri, photo?.available, refresh) {
        bitmap = if(photo?.available == true && Permissions.access(context) != PhotoAccess.NONE) withContext(Dispatchers.IO) {
            try { context.contentResolver.loadThumbnail(photo.uri.toUri(), Size(400, 400), null) }
            catch (_: Exception) { null }
        } else null
        loading = false
    }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = "${if(photo!!.uri == photo.copyUri) "Local copy" else "Photo"} taken ${date(photo.time)}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlbumIcon(Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(loading) "Loading…" else if(photo == null) "No photos yet" else "Photo unavailable", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
    }
}
