package com.example.photosessions

import android.app.Activity
import android.app.Instrumentation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Uses only tracked, app-owned test images; never clears the owner's catalog or media. */
@RunWith(AndroidJUnit4::class)
class ScreenshotFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun mixedImagesDisplayCopyShareAndDoNotRediscoverExports() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = (context.applicationContext as SessionApp).repository
        assertEquals(PhotoAccess.FULL, Permissions.access(context))
        repo.scan()
        val before = repo.db.load()
        val resolver = context.contentResolver
        val sources = mutableListOf<Uri>()
        val copies = mutableListOf<Uri>()
        val stamp = System.currentTimeMillis()
        val title = "Screenshot validation $stamp"
        var sessionId: Long? = null
        var activity: Activity? = null
        fun create(path: String, png: Boolean, color: Int): Uri {
            val collection = if(path.startsWith("Download/")) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = checkNotNull(resolver.insert(collection, ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "screenshot-validation-${sources.size}-$stamp.${if(png) "png" else "jpg"}")
                put(MediaStore.MediaColumns.MIME_TYPE, if(png) "image/png" else "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, path)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }))
            sources += uri
            val bitmap = Bitmap.createBitmap(480, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            try { resolver.openOutputStream(uri)!!.use { bitmap.compress(if(png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it) } }
            finally { bitmap.recycle() }
            check(resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) == 1)
            return uri
        }
        fun hash(uri: Uri) = resolver.openInputStream(uri)!!.use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        fun isSource(photo: Photo, uri: Uri) = ContentUris.parseId(Uri.parse(photo.uri)) == ContentUris.parseId(uri)
        try {
            val id = repo.begin(title, duration = HOUR / 2)
            sessionId = id
            // DATE_ADDED is second precision; place fixtures fully inside the manual period.
            SystemClock.sleep(1100)
            val camera = create("DCIM/Camera/PhotoSessionsValidation-$stamp/", false, Color.rgb(46, 117, 89))
            val samsung = create("DCIM/Screenshots/PhotoSessionsValidation-$stamp/", true, Color.rgb(63, 111, 185))
            val standard = create("Pictures/Screenshots/PhotoSessionsValidation-$stamp/", true, Color.rgb(193, 127, 55))
            val download = create("Download/PhotoSessionsValidation-$stamp/", true, Color.GRAY)
            val export = create("Pictures/Photo Sessions/Validation-$stamp/", true, Color.GRAY)
            val originalHashes = sources.associateWith { hash(it) }
            repo.scan()
            val mixed = repo.db.load().photos.filter { it.sessionId == id }
            assertEquals(3, mixed.size)
            listOf(camera, samsung, standard).forEach { uri -> assertTrue(mixed.any { isSource(it, uri) }) }
            listOf(download, export).forEach { uri -> assertFalse(repo.db.load().photos.any { isSource(it, uri) }) }
            assertFalse(repo.db.load().sessions.single { it.id == id }.complete)

            activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                .putExtra("open_session_id", id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            compose.waitUntil(15_000) { compose.onAllNodesWithText(title, substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription("Photo taken", substring = true), useUnmergedTree = true).fetchSemanticsNodes().size == 3 }
            val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_VIEW).apply {
                addDataType("image/*"); addDataScheme("content")
            }, Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
            try {
                compose.onAllNodes(hasContentDescription("Photo taken", substring = true)).onFirst().performClick()
                assertEquals(1, monitor.hits)
            } finally { instrumentation.removeMonitor(monitor) }

            if(compose.onAllNodesWithContentDescription("Show upload and copy controls").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithContentDescription("Show upload and copy controls").performClick()
            compose.onNodeWithText("Copy Locally").performScrollTo().performClick()
            compose.waitUntil(20_000) { repo.db.load().photos.filter { it.sessionId == id }.count { it.copyState == "copied" } == 3 }
            val copied = repo.db.load().photos.filter { it.sessionId == id }
            copies += copied.map { Uri.parse(it.copyUri!!) }
            for(photo in copied) {
                val source = sources.single { isSource(photo, it) }
                assertEquals(originalHashes.getValue(source), hash(Uri.parse(photo.copyUri!!)))
                val upload = checkNotNull(photoSource(context, photo))
                if(source == samsung || source == standard) {
                    assertEquals("image/png", upload.mime)
                    assertTrue(upload.name.endsWith(".png"))
                }
            }
            assertEquals("image/*", SessionFiles(context, repo.db).shareIntent(id).type)
            repo.scan(); repo.scan(); repo.copy(id)
            assertEquals(copied, repo.db.load().photos.filter { it.sessionId == id })
            assertEquals(originalHashes, sources.associateWith { hash(it) })
            assertEquals(HOUR / 2, repo.db.load().periods.single { it.sessionId == id }.let { it.deadline!! - it.start })
            compose.waitForIdle()
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                try { File(context.filesDir, "validation-screenshots-v1.7.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                finally { bitmap.recycle() }
            }
        } finally {
            sessionId?.let { id ->
                val photos = repo.db.load().photos.filter { it.sessionId == id }
                (copies + photos.mapNotNull { it.copyUri?.let(Uri::parse) }).distinct().forEach { resolver.delete(it, null, null) }
                sources.forEach { resolver.delete(it, null, null) }
                repo.delete(id)
                photos.forEach { repo.db.remove("deleted_" + it.key) }
            }
            activity?.let { instrumentation.runOnMainSync { it.finish() } }
            val after = repo.db.load()
            before.sessions.forEach { original -> assertEquals(original, after.sessions.single { it.id == original.id }) }
            before.photos.forEach { original -> assertEquals(original, after.photos.single { it.key == original.key }) }
            assertEquals(before.periods, after.periods)
        }
    }
}
