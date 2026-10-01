package com.example.photosessions

import android.Manifest
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Run ONLY on a clean test installation. It refuses to use an existing user catalog. */
@RunWith(AndroidJUnit4::class)
class OrganizerFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun discoverNotifyNameSkipAndPreserveOriginals() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as SessionApp
        val repo = app.repository
        check(repo.db.state("baseline") == null) { "Use a fresh emulator installation; this test will not overwrite an existing catalog." }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val read = if(Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        automation.grantRuntimePermission(context.packageName, read)
        if(Build.VERSION.SDK_INT >= 33) automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val now = System.currentTimeMillis()
        repo.db.start(now - 4 * 60 * 60_000)
        val uris = mutableListOf<Uri>()
        fun resumedActivity(): Activity? {
            var activity: Activity? = null
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull { it is MainActivity }
            }
            return activity
        }
        fun create(time: Long, color: Int, path: String = "DCIM/Camera/OrganizerValidation/"): Uri {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "organizer-test-${System.nanoTime()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, path)
                put(MediaStore.Images.Media.DATE_TAKEN, time)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            uris += uri
            val bitmap = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
            context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
            // MediaProvider derives DATE_TAKEN from EXIF during indexing. Supply genuine
            // capture metadata instead of relying on a writable database column alone.
            context.contentResolver.openFileDescriptor(uri, "rw")!!.use { file ->
                val exif = ExifInterface(file.fileDescriptor)
                val captured = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(time))
                exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, captured)
                exif.setAttribute(ExifInterface.TAG_DATETIME, captured)
                exif.setAttribute("OffsetTimeOriginal", "+00:00")
                exif.setAttribute("OffsetTime", "+00:00")
                exif.saveAttributes()
            }
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0); put(MediaStore.Images.Media.DATE_TAKEN, time) }, null, null)
            context.contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DATE_TAKEN), null, null, null)!!.use { cursor ->
                assertTrue(cursor.moveToFirst())
                val indexed = cursor.getLong(0)
                assertTrue("Fixture capture time $time was indexed as $indexed", kotlin.math.abs(indexed - time) < 1500)
            }
            return uri
        }
        fun hash(uri: Uri): List<Byte> = context.contentResolver.openInputStream(uri)!!.use { MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
        try {
            create(now - 300 * 60_000, Color.GRAY) // Historical, excluded by baseline.
            create(now - 60 * 60_000, Color.GRAY, "Pictures/Screenshots/") // Non-camera.
            create(now - 120 * 60_000, Color.rgb(48, 104, 89))
            create(now - 115 * 60_000, Color.rgb(220, 170, 74))
            create(now - 60 * 60_000, Color.rgb(78, 122, 160))
            val before = uris.map { hash(it) }
            repo.scan()
            val first = repo.db.load()
            assertEquals("Discovered catalog: $first", 2, first.sessions.size)
            assertEquals(3, first.photos.size)
            assertTrue(first.sessions.all { it.complete })
            val manager = context.getSystemService(NotificationManager::class.java)
            compose.waitUntil(10_000) { manager.activeNotifications.size == 2 }
            assertEquals(2, manager.activeNotifications.size)
            val older = first.sessions.minBy { it.start }
            val newer = first.sessions.maxBy { it.start }
            instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            manager.activeNotifications.single { it.tag == older.id.toString() }.notification.contentIntent.send()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Session name").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Session name").performTextInput("  Garage wiring  ")
            compose.onNodeWithText("Save").performScrollTo().performClick()
            compose.waitUntil(10_000) { repo.db.load().sessions.single { it.id == older.id }.name == "Garage wiring" }
            compose.onNodeWithText("Garage wiring").assertIsDisplayed()
            // Notification routing updates Activity.intent. ActivityScenario matches its
            // original launch intent, so use the lifecycle monitor for this recreation check.
            val originalActivity = resumedActivity()!!
            instrumentation.runOnMainSync { originalActivity.recreate() }
            compose.waitUntil(10_000) { resumedActivity()?.let { it !== originalActivity } == true }
            compose.waitForIdle()
            compose.onNodeWithText("Garage wiring").assertIsDisplayed()
            automation.takeScreenshot()?.let { screenshot ->
                File(context.filesDir, "validation-session.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
            }
            manager.activeNotifications.single { it.tag == newer.id.toString() }.notification.contentIntent.send()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Skip").performScrollTo().performClick()
            compose.waitUntil(10_000) { repo.db.load().sessions.single { it.id == newer.id }.prompt == "handled" }
            repo.scan(); repo.scan()
            compose.waitUntil(10_000) { manager.activeNotifications.isEmpty() }
            assertEquals(0, manager.activeNotifications.size)
            assertEquals(3, repo.db.load().photos.size)
            assertNull(repo.db.load().sessions.single { it.id == newer.id }.name)
            assertEquals(before, uris.map { hash(it) })
            // Real MediaStore copies must be byte-identical, outside Camera, and idempotent.
            val files = SessionFiles(context, repo.db)
            repo.copy(older.id)
            val copied = repo.db.load().photos.filter { it.sessionId == older.id }
            assertTrue(copied.all { it.copyState == "copied" })
            copied.forEach { p ->
                val copyUri = Uri.parse(p.copyUri!!)
                uris += copyUri
                assertEquals(hash(Uri.parse(p.uri)), hash(copyUri))
                context.contentResolver.query(copyUri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)!!.use {
                    assertTrue(it.moveToFirst()); assertTrue(it.getString(0).startsWith("Pictures/Photo Sessions/Garage wiring"))
                }
            }
            repo.copy(older.id)
            assertEquals(copied.map { it.copyUri }, repo.db.load().photos.filter { it.sessionId == older.id }.map { it.copyUri })
            val share = files.shareIntent(older.id)
            assertEquals(Intent.ACTION_SEND_MULTIPLE, share.action)
            assertEquals(2, share.clipData!!.itemCount)
            assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            repo.name(older.id, "Renamed workshop")
            repo.scan()
            assertEquals(3, repo.db.load().photos.size) // Copies must never feed discovery.
            assertEquals("Renamed workshop", repo.db.load().sessions.single { it.id == older.id }.name)
            val removed = Uri.parse(first.photos.single { it.sessionId == newer.id }.uri)
            assertEquals(1, context.contentResolver.delete(removed, null, null))
            uris.removeAll { it.lastPathSegment == removed.lastPathSegment }
            repo.scan()
            assertEquals(3, repo.db.load().photos.size)
            assertEquals(1, repo.db.load().photos.count { !it.available })
            repo.delete(older.id)
            repo.scan()
            assertFalse(repo.db.load().sessions.any { it.id == older.id })
            copied.forEach { p -> assertEquals(hash(Uri.parse(p.uri)), hash(Uri.parse(p.copyUri!!))) }
            // Exercise the actual start/stop/resume/rename controls.
            resumedActivity()?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
            instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            compose.onNodeWithText("+  Start a session").performScrollTo().performClick()
            compose.onNodeWithText("Session name").performTextInput("New project")
            compose.onNodeWithText("Start session").performClick()
            compose.waitUntil(10_000) { repo.db.load().sessions.any { it.name == "New project" } }
            compose.onNodeWithText("Stop session").performScrollTo().performClick()
            compose.waitUntil(10_000) { repo.db.load().sessions.single { it.name == "New project" }.complete }
            compose.onNodeWithText("Resume session").performScrollTo().performClick()
            compose.waitUntil(10_000) { !repo.db.load().sessions.single { it.name == "New project" }.complete }
            compose.onNodeWithText("Rename").performScrollTo().performClick()
            compose.onNodeWithText("Session name").performTextReplacement("Project renamed")
            compose.onNodeWithText("Save").performScrollTo().performClick()
            compose.waitUntil(10_000) { repo.db.load().sessions.any { it.name == "Project renamed" } }
            automation.takeScreenshot()?.let { screenshot ->
                File(context.filesDir, "validation-session-v11.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
            }
            compose.onNodeWithText("Delete session").performScrollTo().performClick()
            compose.onAllNodesWithText("Delete session").onLast().performClick()
            compose.waitUntil(10_000) { repo.db.load().sessions.none { it.name == "Project renamed" } }
            automation.takeScreenshot()?.let { screenshot ->
                File(context.filesDir, "validation-home-v11.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
            }
        } finally {
            try {
                resumedActivity()?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
            } finally {
                // Delete only synthetic images created by this test, never pre-existing media.
                uris.forEach { context.contentResolver.delete(it, null, null) }
            }
        }
    }
}
