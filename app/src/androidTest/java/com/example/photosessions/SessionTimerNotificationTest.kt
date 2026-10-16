package com.example.photosessions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic deadlines only, and only on a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class SessionTimerNotificationTest {
    @Test fun quickActionsResumeUnnamedAndNamedSessionsAndRejectStaleTaps() {
        check(Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish") { "Run this test only on an emulator." }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = (context.applicationContext as SessionApp).repository
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_IMAGES)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        val now = System.currentTimeMillis()
        repo.db.start(now - 40 * HOUR)
        val photo = Photo("timer-test", "content://test/timer", now - 30 * HOUR)
        val id = repo.db.reconcile(listOf(photo), now).photos.single { it.key == photo.key }.sessionId
        fun waitFor(check: () -> Boolean) {
            val end = android.os.SystemClock.elapsedRealtime() + 15_000
            while(!check() && android.os.SystemClock.elapsedRealtime() < end) android.os.SystemClock.sleep(100)
            assertTrue(check())
        }
        try {
            repo.scan()
            waitFor { manager.activeNotifications.any { it.tag == id.toString() } }
            val notification = manager.activeNotifications.single { it.tag == id.toString() }.notification
            assertEquals(listOf("30 minutes", "1 hour", "2 hours"), notification.actions.map { it.title.toString() })
            val tappedAt = System.currentTimeMillis()
            notification.actions[0].actionIntent.send()
            waitFor { !repo.db.load().sessions.single { it.id == id }.complete }
            val first = repo.db.load().periods.last { it.sessionId == id }
            assertTrue(first.start in tappedAt..(tappedAt + 10_000))
            assertEquals(first.start + HOUR / 2, first.deadline)
            assertNull(repo.db.load().sessions.single { it.id == id }.name)
            notification.actions[1].actionIntent.send() // Same completion has already been handled.
            android.os.SystemClock.sleep(300)
            assertEquals(first, repo.db.load().periods.last { it.sessionId == id })
            repo.name(id, "Test session", "")
            synchronized(repo) {
                repo.db.writableDatabase.execSQL("UPDATE periods SET deadline=? WHERE session_id=? AND stop IS NULL", arrayOf(System.currentTimeMillis() - 1, id))
                repo.scan()
            }
            waitFor { manager.activeNotifications.any { it.tag == id.toString() } }
            val second = manager.activeNotifications.single { it.tag == id.toString() }.notification
            assertEquals("Session time is up", second.extras.getString("android.title"))
            second.actions[2].actionIntent.send()
            waitFor { !repo.db.load().sessions.single { it.id == id }.complete }
            val period = repo.db.load().periods.last { it.sessionId == id }
            assertEquals(period.start + 2 * HOUR, period.deadline)
            assertEquals("Test session", repo.db.load().sessions.single { it.id == id }.name)
            assertEquals("", repo.db.load().sessions.single { it.id == id }.sessionDate)
            notification.actions[0].actionIntent.send() // An earlier reminder cannot alter this run.
            android.os.SystemClock.sleep(300)
            assertEquals(period, repo.db.load().periods.last { it.sessionId == id })
            synchronized(repo) {
                repo.db.writableDatabase.execSQL("UPDATE periods SET deadline=? WHERE session_id=? AND stop IS NULL", arrayOf(System.currentTimeMillis() - 1, id))
                repo.scan()
            }
            waitFor { manager.activeNotifications.any { it.tag == id.toString() } }
            manager.activeNotifications.single { it.tag == id.toString() }.notification.actions[1].actionIntent.send()
            waitFor { !repo.db.load().sessions.single { it.id == id }.complete }
            val oneHour = repo.db.load().periods.last { it.sessionId == id }
            assertEquals(oneHour.start + HOUR, oneHour.deadline)
        } finally { repo.delete(id) }
    }
}
