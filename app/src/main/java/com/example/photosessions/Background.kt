package com.example.photosessions

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

class SessionApp : Application() {
    val repository by lazy { Repository(this) }
    override fun onCreate() {
        super.onCreate()
        Prompts.channel(this)
        UploadNotes.channel(this)
        PhotosNotes.channel(this)
        // WorkManager's own persisted scheduler restores work after reboot.
        Work.schedule(this)
    }
}

object Work {
    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("photo-discovery", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ScanWorker>(15, TimeUnit.MINUTES).build())
    }
    fun scan(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("scan-now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<ScanWorker>().build())
    }
    fun deadline(context: Context, deadline: Long) {
        // Unique per deadline: a scan at the same deadline cannot cancel itself.
        WorkManager.getInstance(context).enqueueUniqueWork("finish-$deadline", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ScanWorker>().setInitialDelay((deadline - System.currentTimeMillis()).coerceAtLeast(1000), TimeUnit.MILLISECONDS).build())
    }
}

class ScanWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val repository = (applicationContext as SessionApp).repository
        return try { repository.scan(); Result.success() }
        catch (_: SecurityException) { repository.status.value = "Photo access changed. Review permissions."; Result.success() }
        catch (_: Exception) { repository.status.value = "Scan could not finish. It will retry; your sessions are safe."; Result.retry() }
    }
}

class Repository(private val context: Context) {
    val db = CatalogDb(context)
    private val screenshotsSince = db.screenshotsSince(System.currentTimeMillis())
    val revision = MutableStateFlow(0L)
    val status = MutableStateFlow("")
    @Synchronized fun start() { db.start(System.currentTimeMillis()); changed(); Work.schedule(context); Work.scan(context) }
    @Synchronized fun scan() {
        val baseline = db.state("baseline")?.toLongOrNull() ?: return
        val now = System.currentTimeMillis()
        db.expireTimers(now)
        if(Permissions.access(context) == PhotoAccess.NONE) {
            notifyCompleted(db.load())
            status.value = "Photo permission is needed to discover new sessions."; changed(); return
        }
        val discovered = MediaDiscovery(context).scan(baseline, now, screenshotsSince)
        // Only successful discovery changes photo membership or completes inactivity sessions.
        val catalog = db.reconcile(discovered, now)
        catalog.sessions.forEach { session -> SessionRules.deadline(session, catalog)?.let { Work.deadline(context, it) } }
        notifyCompleted(catalog)
        status.value = if(Permissions.access(context) == PhotoAccess.LIMITED) "Limited access: only selected photos can be discovered. Allow all photos for automatic coverage."
            else "Monitoring camera photos and screenshots. Background checks may be delayed by Android."
        changed()
    }
    private fun notifyCompleted(catalog: Catalog) {
        if(db.state("notifications") != "off" && Prompts.allowed(context)) {
            for(s in catalog.sessions.filter { it.complete && it.prompt == "pending" }) {
                if(db.claim(s.id)) Prompts.show(context, s, catalog.photos.count { it.sessionId == s.id }, SessionRules.promptToken(s, catalog))
            }
        }
    }
    @Synchronized fun begin(name: String?, resumeId: Long? = null, duration: Long = HOUR): Long {
        val now = System.currentTimeMillis()
        scan()
        return beginAt(name, resumeId, duration, now)
    }
    private fun beginAt(name: String?, resumeId: Long?, duration: Long, now: Long): Long {
        val id = db.begin(name, now, resumeId, duration)
        Prompts.cancel(context, id)
        changed(); Work.scan(context); Work.deadline(context, now + duration)
        return id
    }
    @Synchronized fun extendFromNotification(id: Long, duration: Long, token: String, now: Long) {
        val catalog = db.load()
        val session = catalog.sessions.find { it.id == id } ?: return
        if(!session.complete || session.prompt != "claimed" || SessionRules.promptToken(session, catalog) != token) return
        // Record the tap immediately. The subsequent scan can still recover photos by capture time.
        beginAt(session.name, id, duration, now)
    }
    @Synchronized fun stop(id: Long) { scan(); db.stop(id, System.currentTimeMillis()); Prompts.cancel(context, id); changed() }
    @Synchronized fun delete(id: Long) { DriveWork.cancel(context, id); PhotosWork.cancel(context, id); NotificationManagerCompat.from(context).cancel("photos-$id", 3); synchronized(ExportNames.lock) { db.deleteSession(id) }; Prompts.cancel(context, id); NotificationManagerCompat.from(context).cancel("upload-$id", 2); changed() }
    @Synchronized fun copy(id: Long) { try { synchronized(ExportNames.lock) { SessionFiles(context, db).copyPending(id) } } finally { changed() } }
    @Synchronized fun name(id: Long, value: String?, date: String? = null) {
        synchronized(ExportNames.lock) { db.name(id, value, date); Prompts.cancel(context, id); changed(); ExportNames(context, db).retry(id) }
        changed()
    }
    fun retryNames(id: Long) { try { ExportNames(context, db).retry(id) } finally { changed() } }
    @Synchronized fun notifications(enabled: Boolean) {
        db.put("notifications", if(enabled) "on" else "off")
        if(!enabled) NotificationManagerCompat.from(context).cancelAll()
        changed(); Work.scan(context)
    }
    /** Called after the person grants Drive access in the app. Remembers which Google account to use. */
    fun driveConnected(token: String) {
        val email = DriveApi(token).email()
        db.put("drive_account", email)
        db.remove("drive_reconnect")
        changed()
    }
    @Synchronized fun upload(id: Long, wifiOnly: Boolean) {
        check(db.state("drive_account") != null) { "Connect Google Drive first." }
        check(db.load().photos.any { it.sessionId == id }) { "There are no photos to upload yet." }
        db.remove("drive_error_$id")
        db.put("drive_mode_$id", if(wifiOnly) "wifi" else "any")
        DriveWork.enqueue(context, id, wifiOnly)
        changed()
    }
    @Synchronized fun cancelUpload(id: Long) { DriveWork.cancel(context, id); changed() }
    @Synchronized fun disconnectDrive() {
        WorkManager.getInstance(context).cancelAllWorkByTag("drive-upload")
        db.remove("drive_account"); db.remove("drive_reconnect")
        changed()
    }
    fun photosConnected(token: String) {
        val account = PhotosApi(token).email()
        db.put("photos_account", account); db.remove("photos_reconnect"); changed()
    }
    @Synchronized fun addToPhotos(id: Long, wifiOnly: Boolean) {
        check(db.state("photos_account") != null) { "Connect Google Photos first." }
        check(db.load().photos.any { it.sessionId == id }) { "There are no photos to add yet." }
        db.remove("photos_error_$id"); db.put("photos_mode_$id", if(wifiOnly) "wifi" else "any")
        PhotosWork.enqueue(context, id, wifiOnly); changed()
    }
    @Synchronized fun cancelPhotos(id: Long) {
        PhotosWork.cancel(context, id); NotificationManagerCompat.from(context).cancel("photos-$id", 3); changed()
    }
    @Synchronized fun disconnectPhotos() {
        WorkManager.getInstance(context).cancelAllWorkByTag("photos-upload")
        db.remove("photos_account"); db.remove("photos_reconnect")
        db.load().sessions.forEach { NotificationManagerCompat.from(context).cancel("photos-${it.id}", 3) }
        changed()
    }
    fun refresh() = changed()
    private fun changed() { revision.value += 1 }
}

object Prompts {
    const val CHANNEL = "session-names"
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Photo session reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Name or extend a session when it ends" })
    }
    fun allowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    @Suppress("MissingPermission")
    fun show(context: Context, session: Session, count: Int, token: String = "") {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = "photosessions://name/${session.id}".toUri()
            putExtra("session_id", session.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun extension(minutes: Int): PendingIntent {
            val action = Intent(context, ExtendSessionReceiver::class.java).apply {
                data = "photosessions://extend/${session.id}/minutes/$minutes/${android.net.Uri.encode(token)}".toUri()
                putExtra("session_id", session.id); putExtra("minutes", minutes); putExtra("token", token)
            }
            return PendingIntent.getBroadcast(context, 0, action, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val message = if(session.name == null) "You took $count ${if(count == 1) "photo" else "photos"}. Name it or keep going. Tap for custom hours."
            else "${SessionLabels.title(session)} has ended. Keep going or tap for custom hours."
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_session)
            .setContentTitle(if(session.name == null) "Name or extend this session" else "Session time is up")
            .setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .addAction(0, "30 minutes", extension(30))
            .addAction(0, "1 hour", extension(60))
            .addAction(0, "2 hours", extension(120))
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build()
        try { NotificationManagerCompat.from(context).notify(session.id.toString(), 1, notification) } catch (_: SecurityException) { /* Revoked between check and posting. */ }
    }
    fun cancel(context: Context, id: Long) = NotificationManagerCompat.from(context).cancel(id.toString(), 1)
}

/** A quick action records a new timer without interrupting the camera with an activity. */
class ExtendSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("session_id", 0)
        // Notifications posted before this update used whole-hour extras.
        val minutes = if(intent.hasExtra("minutes")) intent.getIntExtra("minutes", 0)
            else when(intent.getIntExtra("hours", 0)) { 1 -> 60; 2 -> 120; else -> 0 }
        val token = intent.getStringExtra("token") ?: return
        if(id <= 0 || minutes !in listOf(30, 60, 120)) return
        val clickedAt = System.currentTimeMillis()
        val pending = goAsync()
        executor.execute {
            try {
                (context.applicationContext as SessionApp).repository.extendFromNotification(id, minutes * 60_000L, token, clickedAt)
            } catch (_: Exception) {
                (context.applicationContext as SessionApp).repository.status.value = "Could not extend the session. Open it and try again."
            } finally { pending.finish() }
        }
    }
    companion object { private val executor = java.util.concurrent.Executors.newSingleThreadExecutor() }
}
