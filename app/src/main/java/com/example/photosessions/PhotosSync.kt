package com.example.photosessions

import android.accounts.Account
import android.content.Context
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object PhotosAuth {
    val scopes = listOf("https://www.googleapis.com/auth/photoslibrary.appendonly",
        "https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata",
        "https://www.googleapis.com/auth/photoslibrary.edit.appcreateddata",
        "https://www.googleapis.com/auth/userinfo.email")
    fun request(account: String?) = AuthorizationRequest.builder().setRequestedScopes(scopes.map { Scope(it) })
        .apply { if(account != null) setAccount(Account(account, "com.google")) }.build()
    fun token(context: Context, account: String): String? {
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request(account)), 60, TimeUnit.SECONDS)
        return if(result.hasResolution()) null else result.accessToken
    }
}

class PhotosHttpException(val code: Int, message: String, val status: String = "") : IOException(message) {
    companion object {
        fun fromResponse(code: Int, body: String): PhotosHttpException {
            val e = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
            return PhotosHttpException(code, e?.optString("message")?.takeIf { it.isNotBlank() } ?: "Google Photos returned $code.", e?.optString("status").orEmpty())
        }
    }
}
object PhotosErrors {
    fun retryable(e: Exception): Boolean = when(e) {
        is PhotosHttpException -> e.code == 429 || e.code >= 500
        is IOException -> true
        else -> false
    }
    fun message(e: Exception): String = when {
        e is PhotosHttpException && e.message.orEmpty().contains("storage", true) -> "Google account storage is full. Free up space, then add the photos again."
        e is PhotosHttpException && e.code == 403 -> "Google Photos could not accept this request. ${e.message}"
        e is PhotosHttpException -> e.message ?: "Google Photos could not accept the photos."
        e is IOException -> "Could not reach Google Photos. Check the internet connection and try again."
        else -> e.message ?: "Could not add the photos. Try again."
    }
}

data class PhotosAlbum(val id: String, val title: String, val url: String)
interface PhotosService {
    fun album(id: String): PhotosAlbum?
    fun findAlbum(title: String): PhotosAlbum?
    fun createAlbum(title: String): PhotosAlbum
    fun renameAlbum(id: String, title: String)
    fun upload(source: UploadSource): String
    fun add(album: String, uploadToken: String, filename: String): String
}

/** REST access is limited to this app's albums and uploads, never the whole Photos library. */
class PhotosApi(private val token: String) : PhotosService {
    private val base = "https://photoslibrary.googleapis.com/v1"
    private fun request(url: String, method: String, payload: JSONObject? = null, source: UploadSource? = null): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 30_000; readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            if(source != null) {
                c.doOutput = true; c.setFixedLengthStreamingMode(source.size)
                c.setRequestProperty("Content-Type", "application/octet-stream")
                c.setRequestProperty("X-Goog-Upload-Content-Type", source.mime)
                c.setRequestProperty("X-Goog-Upload-Protocol", "raw")
                source.open().use { input -> c.outputStream.use { input.copyTo(it) } }
            } else if(payload != null) {
                c.doOutput = true; c.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val body = (if(code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if(code !in 200..299) throw PhotosHttpException.fromResponse(code, body)
            return body
        } finally { c.disconnect() }
    }
    private fun json(method: String, path: String, payload: JSONObject? = null) = JSONObject(request("$base/$path", method, payload).ifBlank { "{}" })
    fun email(): String = JSONObject(request("https://www.googleapis.com/oauth2/v2/userinfo", "GET")).getString("email")
    private fun parseAlbum(a: JSONObject) = PhotosAlbum(a.getString("id"), a.getString("title"), a.getString("productUrl"))
    override fun album(id: String): PhotosAlbum? = try { parseAlbum(json("GET", "albums/$id")) }
        catch(e: PhotosHttpException) { if(e.code == 404) null else throw e }
    override fun findAlbum(title: String): PhotosAlbum? {
        var page = ""
        do {
            val response = json("GET", "albums?pageSize=50&excludeNonAppCreatedData=true" + if(page.isBlank()) "" else "&pageToken=${URLEncoder.encode(page, "UTF-8")}")
            val albums = response.optJSONArray("albums") ?: JSONArray()
            for(i in 0 until albums.length()) { val a = parseAlbum(albums.getJSONObject(i)); if(a.title == title) return a }
            page = response.optString("nextPageToken")
        } while(page.isNotBlank())
        return null
    }
    override fun createAlbum(title: String) = parseAlbum(json("POST", "albums", JSONObject().put("album", JSONObject().put("title", title))))
    override fun renameAlbum(id: String, title: String) { json("PATCH", "albums/$id?updateMask=title", JSONObject().put("title", title)) }
    override fun upload(source: UploadSource): String {
        require(source.size <= 200L * 1024 * 1024) { "This photo exceeds Google Photos' 200 MB limit." }
        return request("$base/uploads", "POST", source = source).trim().also { check(it.isNotBlank()) { "Google Photos did not return an upload token." } }
    }
    override fun add(album: String, uploadToken: String, filename: String): String = parseAdded(json("POST", "mediaItems:batchCreate", JSONObject()
        .put("albumId", album).put("newMediaItems", JSONArray().put(JSONObject().put("simpleMediaItem", JSONObject().put("uploadToken", uploadToken).put("fileName", filename))))), uploadToken)
    companion object {
        internal fun parseAdded(response: JSONObject, token: String): String {
            val result = response.getJSONArray("newMediaItemResults").getJSONObject(0)
            check(result.getString("uploadToken") == token) { "Google Photos returned an unexpected upload result." }
            val status = result.optJSONObject("status")
            val code = status?.optInt("code", 0) ?: 0
            if(code != 0) throw PhotosHttpException(when(code) { 8 -> 429; 13, 14 -> 503; 7 -> 403; 16 -> 401; else -> 400 }, status?.optString("message")?.takeIf { it.isNotBlank() } ?: "Google Photos could not add this photo.")
            return result.getJSONObject("mediaItem").getString("id").also { check(it.isNotBlank()) }
        }
    }
}

internal fun photosAlbumTitle(label: String) = label.replace(Regex("\\p{Cntrl}"), " ").trim().take(500).ifBlank { "Unnamed session" }

/** Read once off the UI thread; count only this account's current album membership. */
internal fun photosExportCounts(db: CatalogDb, catalog: Catalog, account: String?): Map<Long, Int> {
    if(account == null) return emptyMap()
    val albums = db.states("photos_album_${account}_")
    val items = db.states("photos_item_${account}_")
    return catalog.photos.filter { photo ->
        val album = albums["photos_album_${account}_${photo.sessionId}"]
        album != null && items["photos_item_${account}_${photo.sessionId}_${photo.key}"] == album
    }.groupingBy { it.sessionId }.eachCount()
}

/** Completed album membership and short-lived upload tokens are persisted separately per account. */
class PhotosState(private val db: CatalogDb, val account: String, val session: Long) {
    val suffix = "${account}_$session"
    fun album() = db.state("photos_album_$suffix")
    fun url() = db.state("photos_url_$suffix")
    fun saveAlbum(album: PhotosAlbum) { db.put("photos_album_$suffix", album.id); db.put("photos_url_$suffix", album.url); db.put("photos_name_$suffix", album.title) }
    private fun key(photo: Photo) = "photos_item_${suffix}_${photo.key}"
    fun added(photo: Photo) = album()?.let { db.state(key(photo)) == it } ?: false
    fun addedKeys(): Set<String> {
        val album = album() ?: return emptySet()
        val prefix = "photos_item_${suffix}_"
        return db.states(prefix).filterValues { it == album }.keys.map { it.removePrefix(prefix) }.toSet()
    }
    fun done(photo: Photo) { db.put(key(photo), checkNotNull(album())); db.remove("photos_token_${suffix}_${photo.key}") }
    fun token(photo: Photo, now: Long): String? {
        val saved = db.state("photos_token_${suffix}_${photo.key}") ?: return null
        val parts = saved.split("|", limit = 2)
        val time = parts.first().toLongOrNull() ?: return null
        return parts.getOrNull(1)?.takeIf { now >= time && now - time < TimeUnit.HOURS.toMillis(23) }
    }
    fun token(photo: Photo, token: String, now: Long) { db.put("photos_token_${suffix}_${photo.key}", "$now|$token") }
}

data class UploadSource(val name: String, val mime: String, val size: Long, val open: () -> java.io.InputStream)
internal fun photoSource(context: Context, photo: Photo): UploadSource? {
    for(uri in listOfNotNull(photo.copyUri.takeIf { photo.copyState == "copied" }, photo.uri)) try {
        val parsed = uri.toUri(); val resolver = context.contentResolver
        val size = resolver.openAssetFileDescriptor(parsed, "r")?.use { it.length } ?: continue
        if(size <= 0) continue
        var name = "Photo-${photo.time}.jpg"
        resolver.query(parsed, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { if(it.moveToFirst() && !it.isNull(0)) name = it.getString(0) }
        return UploadSource(name, resolver.getType(parsed) ?: "image/jpeg", size) { resolver.openInputStream(parsed) ?: throw java.io.FileNotFoundException() }
    } catch(_: Exception) { /* Prefer a readable device copy, otherwise try the original. */ }
    return null
}

/** Testable transfer logic; requests are serialized with all other exports for this account. */
class PhotosTransfer(private val db: CatalogDb, private val api: PhotosService, private val account: String,
    private val source: (Photo) -> UploadSource?, private val clock: () -> Long = System::currentTimeMillis) {
    fun upload(id: Long, progress: (Int, Int) -> Unit, stopped: () -> Boolean): UploadOutcome = synchronized(ExportNames.lock) {
        val catalog = db.load(); val session = catalog.sessions.single { it.id == id }
        val state = PhotosState(db, account, id); val title = photosAlbumTitle(SessionLabels.title(session))
        if(stopped()) return@synchronized UploadOutcome.STOPPED
        val stored = state.album()?.let { api.album(it) }
        // A recoverable temporary title prevents a lost create response from creating a second album.
        val markerKey = "photos_pending_${state.suffix}"
        val marker = db.state(markerKey) ?: "Photo Sessions ${id} ${java.util.UUID.randomUUID()}".also { db.put(markerKey, it) }
        val album = stored ?: (api.findAlbum(marker) ?: api.createAlbum(marker)).also { state.saveAlbum(it) }
        if(album.title != title) {
            db.put("rename_photos_${state.suffix}", "Album name needs updating.")
            api.renameAlbum(album.id, title); db.put("photos_name_${state.suffix}", title); db.remove("rename_photos_${state.suffix}")
        }
        db.remove(markerKey)
        val photos = catalog.photos.filter { it.sessionId == id }
        val added = state.addedKeys()
        var done = photos.count { it.key in added }; progress(done, photos.size)
        var unreadable = 0
        for(photo in photos.filter { it.key !in added }) {
            if(stopped()) return@synchronized UploadOutcome.STOPPED
            val file = source(photo)
            if(file == null) { unreadable++; continue }
            val token = state.token(photo, clock()) ?: api.upload(file).also { state.token(photo, it, clock()) }
            if(stopped()) return@synchronized UploadOutcome.STOPPED
            try { api.add(album.id, token, file.name) } catch(e: PhotosHttpException) {
                if(e.code == 400) db.remove("photos_token_${state.suffix}_${photo.key}")
                throw e
            }
            state.done(photo); done++; progress(done, photos.size)
        }
        check(unreadable == 0) { "$unreadable photos could not be read. Restore photo access or device copies, then try again." }
        UploadOutcome.DONE
    }
}

object PhotosWork {
    fun name(id: Long) = "photos-upload-$id"
    fun enqueue(context: Context, id: Long, wifiOnly: Boolean) {
        WorkManager.getInstance(context).enqueueUniqueWork(name(id), ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PhotosWorker>().setConstraints(Constraints.Builder()
                .setRequiredNetworkType(if(wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(workDataOf("session" to id)).addTag("photos-upload").build())
    }
    fun cancel(context: Context, id: Long) { WorkManager.getInstance(context).cancelUniqueWork(name(id)) }
}
class PhotosWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val id = inputData.getLong("session", 0); val repo = (applicationContext as SessionApp).repository
        val account = repo.db.state("photos_account") ?: return Result.failure()
        var token: String? = null
        try {
            token = PhotosAuth.token(applicationContext, account)
            if(token == null) {
                repo.db.put("photos_reconnect", "1"); repo.db.remove("photos_error_$id")
                PhotosNotes.post(applicationContext, id, "Reconnect Google Photos", "Open the session and tap Add to Google Photos to continue.")
                return Result.failure()
            }
            val outcome = PhotosTransfer(repo.db, PhotosApi(token), account, { photoSource(applicationContext, it) }).upload(id, { done, total ->
                setProgressAsync(workDataOf("done" to done, "total" to total)); PhotosNotes.post(applicationContext, id, "Adding to Google Photos", "$done of $total photos", done < total)
            }, { isStopped || repo.db.state("photos_account") != account })
            if(outcome == UploadOutcome.STOPPED) return Result.failure()
            repo.db.remove("photos_error_$id")
            PhotosNotes.post(applicationContext, id, "Added to Google Photos", "Open the session to view its album.")
            return Result.success()
        } catch(e: Exception) {
            if(e is PhotosHttpException && e.code == 401) {
                token?.let { DriveAuth.clear(applicationContext, it) }; repo.db.put("photos_reconnect", "1")
            }
            val message = PhotosErrors.message(e); repo.db.put("photos_error_$id", message)
            if(PhotosErrors.retryable(e) && runAttemptCount < 6) return Result.retry()
            PhotosNotes.post(applicationContext, id, "Google Photos upload paused", message)
            return Result.failure()
        } finally {
            if(isStopped || repo.db.state("photos_account") != account) androidx.core.app.NotificationManagerCompat.from(applicationContext).cancel("photos-$id", 3)
            repo.refresh()
        }
    }
}
object PhotosNotes {
    const val CHANNEL = "photos-uploads"
    fun channel(context: Context) { context.getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(
        android.app.NotificationChannel(CHANNEL, "Google Photos uploads", android.app.NotificationManager.IMPORTANCE_LOW)) }
    @Suppress("MissingPermission")
    fun post(context: Context, id: Long, title: String, text: String, ongoing: Boolean = false) {
        val intent = android.content.Intent(context, MainActivity::class.java).setData("photosessions://session/$id".toUri()).putExtra("open_session_id", id)
        val pending = android.app.PendingIntent.getActivity(context, id.toInt(), intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        try { androidx.core.app.NotificationManagerCompat.from(context).notify("photos-$id", 3,
            androidx.core.app.NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_session).setContentTitle(title).setContentText(text)
                .setContentIntent(pending).setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(!ongoing).build()) }
        catch(_: SecurityException) { /* Progress remains available inside the app. */ }
    }
}
