package com.example.photosessions

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import androidx.core.net.toUri

/** Only writes copies owned by this app; never writes to camera originals. */
class SessionFiles(private val context: Context, private val db: CatalogDb) {
    /** Local copies are checked on-device; Drive coverage uses the last confirmed upload record. */
    fun unexported(photos: List<Photo>): Int = SessionLabels.unexported(photos.map { photo ->
        if(photo.copyState == "copied" && photo.copyUri != null &&
            !runCatching { context.contentResolver.openInputStream(photo.copyUri.toUri())?.use { true } == true }.getOrDefault(false))
            photo.copy(copyState = "unavailable") else photo
    })
    fun folder(id: Long): String? = db.state("folder_$id")
    fun copyPending(id: Long) {
        val catalog = db.load()
        val session = catalog.sessions.single { it.id == id }
        require(!session.name.isNullOrBlank()) { "Name this session before copying photos." }
        // Missing copies must be eligible for recreation before moving the remaining copies.
        catalog.photos.filter { it.sessionId == id && it.copyState == "copied" && it.copyUri != null }.forEach { photo ->
            try {
                if(context.contentResolver.openInputStream(photo.copyUri!!.toUri())?.use { true } != true) db.copyStatus(photo.key, null, "failed")
            } catch(_: java.io.FileNotFoundException) { db.copyStatus(photo.key, null, "failed") }
        }
        rename(id)
        val path = folder(id) ?: SessionLabels.localPath(session).also { db.put("folder_$id", it) }
        val resolver = context.contentResolver
        var failures = 0
        for(photo in catalog.photos.filter { it.sessionId == id }) {
            try {
                if(photo.copyState == "copied" && photo.copyUri != null) {
                    val exists = resolver.openInputStream(photo.copyUri.toUri())?.use { true } ?: false
                    if(exists) continue
                }
            } catch (_: Exception) { /* Missing copies can be recreated from the camera original. */ }
            // A tracked pending URI is reused after interruption, preventing duplicate copies.
            var target = photo.copyUri?.takeIf { photo.copyState == "copying" }?.toUri()
            try {
                val source = photo.uri.toUri()
                val mime = resolver.getType(source) ?: "image/jpeg"
                var filename = "Photo-${photo.time}.jpg"
                resolver.query(source, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use {
                    if(it.moveToFirst()) filename = it.getString(0)
                }
                if(target == null) {
                    target = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, path)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                        put(MediaStore.Images.Media.DATE_TAKEN, photo.time)
                    }) ?: error("Could not create copy")
                    db.copyStatus(photo.key, target.toString(), "copying")
                }
                resolver.openInputStream(source).use { input ->
                    checkNotNull(input) { "Original photo is unavailable" }
                    resolver.openOutputStream(target, "wt").use { output ->
                        checkNotNull(output) { "Could not write copy" }; input.copyTo(output)
                    }
                }
                check(resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
                db.copyStatus(photo.key, target.toString(), "copied")
            } catch (_: Exception) {
                // Delete only this app's incomplete copy, never the source.
                val removed = target == null || runCatching { resolver.delete(target, null, null); true }.getOrDefault(false)
                db.copyStatus(photo.key, if(removed) null else target.toString(), if(removed) "failed" else "copying")
                failures++
            }
        }
        check(failures == 0) { "$failures photos could not be copied. Check storage and photo access, then retry." }
    }

    /** Move only tracked app-owned copies by their stable MediaStore URI, never by a name search. */
    fun rename(id: Long) {
        val old = folder(id) ?: return
        val session = db.load().sessions.single { it.id == id }
        val target = SessionLabels.localPath(session)
        if(old == target && db.state("rename_local_$id") == null) return
        db.put("rename_local_$id", "Folder name needs updating.")
        val resolver = context.contentResolver
        val failures = db.load().photos.filter { it.sessionId == id && it.copyUri != null }.count { photo ->
            runCatching {
                check(resolver.update(photo.copyUri!!.toUri(), ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, target) }, null, null) == 1) { "A tracked copy is unavailable." }
            }.isFailure
        }
        check(failures == 0) { "$failures local copies could not be moved. Check storage and photo access, then retry folder names." }
        db.put("folder_$id", target)
        db.remove("rename_local_$id")
    }

    fun shareIntent(id: Long): Intent {
        val photos = db.load().photos.filter { it.sessionId == id }
        check(photos.isNotEmpty()) { "There are no photos to upload yet." }
        check(photos.all { it.copyState == "copied" && it.copyUri != null }) { "Copy pending photos before uploading." }
        val uris = ArrayList(photos.map { it.copyUri!!.toUri() })
        uris.forEach { uri -> check(context.contentResolver.openInputStream(uri)?.use { true } == true) { "A copy is unavailable. Copy pending photos again." } }
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newUri(context.contentResolver, "Session photos", uris.first()).also { clip -> uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) } }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
