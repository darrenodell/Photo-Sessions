package com.example.photosessions

import android.accounts.Account
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Access limited to files and folders this app creates. The rest of the person's Drive stays invisible. */
const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
private const val API = "https://www.googleapis.com/drive/v3"
private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3/files"
private const val FOLDER_MIME = "application/vnd.google-apps.folder"
const val DRIVE_ROOT_FOLDER = "Photo Sessions"

/** Pure helpers for Drive query strings and names, kept separate so they can be unit tested. */
object DriveQuery {
    fun literal(value: String): String = "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"
    fun inFolder(folderId: String): String = "${literal(folderId)} in parents"
    fun mediaKey(key: String): String = "appProperties has { key='mediaKey' and value=${literal(key)} }"
    fun uploadedQuery(folderId: String, key: String): String = "${inFolder(folderId)} and ${mediaKey(key)} and trashed = false"
    fun folderName(name: String?): String = (name ?: "").replace(Regex("\\p{Cntrl}"), " ").trim().take(120).ifBlank { "Unnamed session" }
    fun folderUrl(folderId: String): String = "https://drive.google.com/drive/folders/$folderId"
}

object DriveAuth {
    fun request(account: String?): AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
        .apply { if(account != null) setAccount(Account(account, "com.google")) }
        .build()
    /** Blocking; never call on the main thread. Returns null when the person must reconnect in the app. */
    fun token(context: Context, account: String): String? {
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request(account)), 60, TimeUnit.SECONDS)
        return if(result.hasResolution()) null else result.accessToken
    }
    fun clear(context: Context, token: String) { runCatching { GoogleAuthUtil.clearToken(context, token) } }
}

class DriveHttpException(val code: Int, message: String, val reason: String? = null) : IOException(message) {
    companion object {
        fun fromResponse(code: Int, body: String): DriveHttpException {
            val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
            val message = error?.optString("message")?.takeIf { it.isNotBlank() }
                ?: "Google Drive returned $code."
            val reason = error?.optJSONArray("errors")?.optJSONObject(0)?.optString("reason")?.takeIf { it.isNotBlank() }
            return DriveHttpException(code, message, reason)
        }
    }
}

object DriveUploadErrors {
    fun storageFull(error: Exception) = error is DriveHttpException && error.reason == "storageQuotaExceeded"
    fun message(error: Exception): String = when {
        storageFull(error) -> "Google Drive storage is full. Free up space in that Google account, then upload again."
        error is DriveHttpException -> error.message ?: "Google Drive could not accept the upload."
        error is IOException -> "Could not reach Google Drive. Check the internet connection and try again."
        else -> "The upload could not finish. Try again."
    }
}

/** Minimal Drive REST v3 client over HttpURLConnection; no extra libraries. */
class DriveApi(private val token: String) {
    private fun connect(url: String, method: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method; connectTimeout = 30_000; readTimeout = 120_000
        setRequestProperty("Authorization", "Bearer $token")
    }
    private fun HttpURLConnection.text(): String {
        val code = responseCode
        val body = (if(code in 200..299) inputStream else errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if(code !in 200..299) throw DriveHttpException.fromResponse(code, body)
        return body
    }
    private fun json(method: String, url: String, payload: JSONObject? = null): JSONObject {
        val c = connect(url, method)
        try {
            if(payload != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                c.outputStream.use { it.write(payload.toString().toByteArray()) }
            }
            return JSONObject(c.text().ifBlank { "{}" })
        } finally { c.disconnect() }
    }
    fun renameFolder(id: String, name: String) { json("PATCH", "$API/files/$id?fields=id,name", JSONObject().put("name", name)) }
    fun email(): String = json("GET", "$API/about?fields=user(emailAddress)").getJSONObject("user").getString("emailAddress")
    /** True when the folder still exists, is a folder, and is not in the trash. */
    fun usableFolder(id: String): Boolean = try {
        val f = json("GET", "$API/files/$id?fields=id,trashed,mimeType")
        !f.optBoolean("trashed") && f.optString("mimeType") == FOLDER_MIME
    } catch(e: DriveHttpException) { if(e.code == 404) false else throw e }
    fun createFolder(name: String, parent: String?): String = json("POST", "$API/files?fields=id", JSONObject().apply {
        put("name", name); put("mimeType", FOLDER_MIME)
        if(parent != null) put("parents", JSONArray().put(parent))
    }).getString("id")
    /** Finds a file this app already uploaded for the photo, so a retry never creates a duplicate. */
    fun findUploaded(folderId: String, key: String): String? {
        val q = URLEncoder.encode(DriveQuery.uploadedQuery(folderId, key), "UTF-8")
        val files = json("GET", "$API/files?pageSize=1&fields=files(id)&q=$q").getJSONArray("files")
        return if(files.length() > 0) files.getJSONObject(0).getString("id") else null
    }
    /** Resumable upload session, then one PUT of the bytes. Returns the new Drive file ID. */
    fun upload(folderId: String, name: String, mime: String, key: String, size: Long, open: () -> InputStream): String {
        val start = connect("$UPLOAD_API?uploadType=resumable&fields=id", "POST")
        val location = try {
            start.doOutput = true
            start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            start.setRequestProperty("X-Upload-Content-Type", mime)
            start.setRequestProperty("X-Upload-Content-Length", size.toString())
            val meta = JSONObject().apply {
                put("name", name); put("parents", JSONArray().put(folderId))
                put("appProperties", JSONObject().put("mediaKey", key))
            }
            start.outputStream.use { it.write(meta.toString().toByteArray()) }
            start.text()
            start.getHeaderField("Location") ?: throw IOException("Drive did not start the upload.")
        } finally { start.disconnect() }
        val put = connect(location, "PUT")
        try {
            put.doOutput = true
            put.setRequestProperty("Content-Type", mime)
            put.setFixedLengthStreamingMode(size)
            open().use { input -> put.outputStream.use { out -> input.copyTo(out) } }
            return JSONObject(put.text()).getString("id")
        } finally { put.disconnect() }
    }
}

enum class UploadOutcome { DONE, RECONNECT, STOPPED }

/** Uploads a session's photos into My Drive / Photo Sessions / <session name>. */
class DriveUploader(private val context: Context, private val db: CatalogDb) {
    fun account(): String? = db.state("drive_account")
    private fun folderKey(account: String, id: Long) = "drive_folder_${account}_$id"
    fun folderUrl(id: Long): String? = account()?.let { db.state(folderKey(it, id)) }?.let { DriveQuery.folderUrl(it) }

    fun upload(id: Long, progress: (done: Int, total: Int) -> Unit, stopped: () -> Boolean): UploadOutcome = synchronized(ExportNames.lock) {
        uploadLocked(id, progress, stopped)
    }

    private fun uploadLocked(id: Long, progress: (done: Int, total: Int) -> Unit, stopped: () -> Boolean): UploadOutcome {
        val account = account() ?: return UploadOutcome.RECONNECT
        var token = DriveAuth.token(context, account) ?: return UploadOutcome.RECONNECT
        var api = DriveApi(token)
        // One automatic token refresh per run: an expired token gets 401 once, then a fresh token.
        fun <T> call(block: (DriveApi) -> T): T = try { block(api) } catch(e: DriveHttpException) {
            if(e.code != 401) throw e
            DriveAuth.clear(context, token)
            token = DriveAuth.token(context, account) ?: throw ReconnectNeeded()
            api = DriveApi(token)
            block(api)
        }
        try {
            val catalog = db.load()
            val session = catalog.sessions.single { it.id == id }
            val rootKey = "drive_root_$account"
            val root = db.state(rootKey)?.takeIf { call { a -> a.usableFolder(it) } }
                ?: call { it.createFolder(DRIVE_ROOT_FOLDER, null) }.also { db.put(rootKey, it) }
            val stored = db.state(folderKey(account, id))
            var photos = catalog.photos.filter { it.sessionId == id }
            val folder = if(stored != null && call { it.usableFolder(stored) }) stored else {
                // Folder was deleted or trashed in Drive: everything must be uploaded again.
                if(stored != null) photos.forEach { db.driveStatus(it.key, account, "none", null) }
                photos = db.load().photos.filter { it.sessionId == id }
                call { it.createFolder(DriveQuery.folderName(SessionLabels.title(session)), root) }.also { db.put(folderKey(account, id), it); db.put("drive_name_${account}_$id", DriveQuery.folderName(SessionLabels.title(session))); db.remove("rename_drive_${account}_$id") }
            }
            val wantedName = DriveQuery.folderName(SessionLabels.title(session))
            if(db.state("drive_name_${account}_$id") != wantedName || db.state("rename_drive_${account}_$id") != null) {
                db.put("rename_drive_${account}_$id", "Folder name needs updating.")
                try {
                    call { it.renameFolder(folder, wantedName) }
                    db.put("drive_name_${account}_$id", wantedName); db.remove("rename_drive_${account}_$id")
                } catch(e: Exception) { db.put("rename_drive_${account}_$id", e.message ?: "Drive folder rename failed.") }
            }
            val pending = photos.filter { !(it.driveState == "uploaded" && it.driveAccount == account) }
            var done = photos.size - pending.size
            progress(done, photos.size)
            for(photo in pending) {
                if(stopped()) return UploadOutcome.STOPPED
                val source = source(photo)
                if(source == null) { db.driveStatus(photo.key, account, "failed", null); continue }
                val fileId = call { it.findUploaded(folder, photo.key) } ?: try {
                    call { it.upload(folder, source.name, source.mime, photo.key, source.size) { open(source.uri) } }
                } catch(_: FileNotFoundException) { null } catch(_: SecurityException) { null }
                if(fileId == null) { db.driveStatus(photo.key, account, "failed", null); continue }
                db.driveStatus(photo.key, account, "uploaded", fileId)
                done++
                progress(done, photos.size)
            }
            return UploadOutcome.DONE
        } catch(_: ReconnectNeeded) { return UploadOutcome.RECONNECT }
    }

    private class ReconnectNeeded : Exception()
    private data class Source(val uri: String, val name: String, val mime: String, val size: Long)

    /** Prefers the Gallery copy, falls back to the camera original. Null when neither can be read. */
    private fun source(photo: Photo): Source? {
        val candidates = listOfNotNull(photo.copyUri.takeIf { photo.copyState == "copied" }, photo.uri)
        for(uri in candidates) {
            try {
                val resolver = context.contentResolver
                val parsed = uri.toUri()
                val size = resolver.openAssetFileDescriptor(parsed, "r")?.use { it.length } ?: continue
                if(size <= 0) continue
                var name = "Photo-${photo.time}.jpg"
                resolver.query(parsed, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { if(it.moveToFirst() && !it.isNull(0)) name = it.getString(0) }
                return Source(uri, name, resolver.getType(parsed) ?: "image/jpeg", size)
            } catch(_: Exception) { /* Try the next candidate. */ }
        }
        return null
    }
    private fun open(uri: String): InputStream = context.contentResolver.openInputStream(uri.toUri()) ?: throw FileNotFoundException(uri)
}

object DriveWork {
    fun name(id: Long) = "drive-upload-$id"
    fun enqueue(context: Context, id: Long, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if(wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf("session" to id))
            .addTag("drive-upload").build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(id), ExistingWorkPolicy.REPLACE, request)
    }
    fun cancel(context: Context, id: Long) { WorkManager.getInstance(context).cancelUniqueWork(name(id)) }
}

class UploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getLong("session", 0)
        val repository = (applicationContext as SessionApp).repository
        return try {
            val outcome = DriveUploader(applicationContext, repository.db).upload(id, { done, total ->
                setProgressAsync(workDataOf("done" to done, "total" to total))
                UploadNotes.progress(applicationContext, id, done, total)
            }, { isStopped })
            when(outcome) {
                UploadOutcome.DONE -> { repository.db.remove("drive_error_$id"); UploadNotes.finished(applicationContext, id, repository.db); Result.success() }
                UploadOutcome.STOPPED -> Result.retry()
                UploadOutcome.RECONNECT -> {
                    repository.db.remove("drive_error_$id")
                    repository.db.put("drive_reconnect", "1")
                    UploadNotes.reconnect(applicationContext, id)
                    Result.failure()
                }
            }
        } catch(e: Exception) {
            val message = DriveUploadErrors.message(e)
            repository.db.put("drive_error_$id", message)
            if(!DriveUploadErrors.storageFull(e) && runAttemptCount < 6) Result.retry() else {
                UploadNotes.failed(applicationContext, id, message)
                Result.failure()
            }
        } finally { repository.refresh() }
    }
}

object UploadNotes {
    const val CHANNEL = "drive-uploads"
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Google Drive uploads", NotificationManager.IMPORTANCE_LOW).apply { description = "Upload progress for photo sessions" })
    }
    private fun open(context: Context, id: Long): PendingIntent = PendingIntent.getActivity(context, id.toInt(),
        Intent(context, MainActivity::class.java).apply {
            data = "photosessions://session/$id".toUri()
            putExtra("open_session_id", id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    @Suppress("MissingPermission")
    private fun post(context: Context, id: Long, builder: NotificationCompat.Builder) {
        try { NotificationManagerCompat.from(context).notify("upload-$id", 2, builder.setSmallIcon(R.drawable.ic_session).setContentIntent(open(context, id)).build()) }
        catch(_: SecurityException) { /* Notifications are optional; progress also shows in the app. */ }
    }
    fun progress(context: Context, id: Long, done: Int, total: Int) = post(context, id, NotificationCompat.Builder(context, CHANNEL)
        .setContentTitle("Uploading to Google Drive").setContentText("$done of $total photos")
        .setProgress(total.coerceAtLeast(1), done, false).setOngoing(done < total).setOnlyAlertOnce(true))
    fun finished(context: Context, id: Long, db: CatalogDb) {
        if(db.load().sessions.none { it.id == id }) return
        val photos = db.load().photos.filter { it.sessionId == id }
        val failed = photos.count { it.driveState == "failed" }
        post(context, id, NotificationCompat.Builder(context, CHANNEL).setAutoCancel(true)
            .setContentTitle(if(failed == 0) "Uploaded to Google Drive" else "Upload finished with problems")
            .setContentText(if(failed == 0) "${photos.size} photos are in Drive." else "$failed photos could not be read. Open the session to retry."))
    }
    fun reconnect(context: Context, id: Long) = post(context, id, NotificationCompat.Builder(context, CHANNEL).setAutoCancel(true)
        .setContentTitle("Reconnect Google Drive").setContentText("Open the session and tap Upload to Google Drive to continue."))
    fun failed(context: Context, id: Long, reason: String?) = post(context, id, NotificationCompat.Builder(context, CHANNEL).setAutoCancel(true)
        .setContentTitle("Google Drive upload paused").setContentText(reason?.take(120) ?: "Open the session and tap Upload to try again."))
}
