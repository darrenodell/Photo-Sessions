package com.example.photosessions

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
    fun deadline(context: Context, end: Long) {
        // Unique per last capture instant: another scan at the same deadline cannot cancel itself.
        WorkManager.getInstance(context).enqueueUniqueWork("finish-$end", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ScanWorker>().setInitialDelay((end + SESSION_TIMEOUT - System.currentTimeMillis()).coerceAtLeast(1000), TimeUnit.MILLISECONDS).build())
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
    val revision = MutableStateFlow(0L)
    val status = MutableStateFlow("")
    @Synchronized fun start() { db.start(System.currentTimeMillis()); changed(); Work.schedule(context); Work.scan(context) }
    @Synchronized fun scan() {
        val baseline = db.state("baseline")?.toLongOrNull() ?: return
        if(Permissions.access(context) == PhotoAccess.NONE) { status.value = "Photo permission is needed to discover new sessions."; return }
        val now = System.currentTimeMillis()
        val discovered = MediaDiscovery(context).scan(baseline, now)
        // Only a successful full scan may reconcile/finalize. A provider error commits nothing.
        val catalog = db.reconcile(discovered, now)
        catalog.sessions.filter { !it.complete }.forEach { Work.deadline(context, maxOf(it.end, catalog.periods.lastOrNull { p -> p.sessionId == it.id && p.stop == null }?.start ?: it.end)) }
        if(db.state("notifications") != "off" && Prompts.allowed(context)) {
            for(s in catalog.sessions.filter { it.complete && it.prompt == "pending" && it.name == null }) {
                if(db.claim(s.id)) Prompts.show(context, s, catalog.photos.count { it.sessionId == s.id })
            }
        }
        status.value = if(Permissions.access(context) == PhotoAccess.LIMITED) "Limited access: only selected photos can be discovered. Allow all photos for automatic coverage."
            else "Monitoring camera photos. Background checks may be delayed by Android."
        changed()
    }
    @Synchronized fun begin(name: String, resumeId: Long? = null): Long {
        scan()
        val id = db.begin(name, System.currentTimeMillis(), resumeId)
        changed(); Work.scan(context); Work.deadline(context, System.currentTimeMillis())
        return id
    }
    @Synchronized fun stop(id: Long) { scan(); db.stop(id, System.currentTimeMillis()); changed() }
    @Synchronized fun delete(id: Long) { db.deleteSession(id); Prompts.cancel(context, id); changed() }
    @Synchronized fun copy(id: Long) { try { SessionFiles(context, db).copyPending(id) } finally { changed() } }
    @Synchronized fun name(id: Long, value: String?) { db.name(id, value); Prompts.cancel(context, id); changed() }
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
    fun refresh() = changed()
    private fun changed() { revision.value += 1 }
}

object Prompts {
    const val CHANNEL = "session-names"
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Name photo sessions", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "One invitation to name each completed photo session" })
    }
    fun allowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    @Suppress("MissingPermission")
    fun show(context: Context, session: Session, count: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = "photosessions://name/${session.id}".toUri()
            putExtra("session_id", session.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_session)
            .setContentTitle("Name this photo session")
            .setContentText("You took $count ${if(count == 1) "photo" else "photos"}. Name this session.")
            .setContentIntent(pending).setAutoCancel(true).setOnlyAlertOnce(true).build()
        try { NotificationManagerCompat.from(context).notify(session.id.toString(), 1, notification) } catch (_: SecurityException) { /* Revoked between check and posting. */ }
    }
    fun cancel(context: Context, id: Long) = NotificationManagerCompat.from(context).cancel(id.toString(), 1)
}
