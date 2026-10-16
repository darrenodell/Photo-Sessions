package com.example.photosessions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SessionTimersTest {
    private lateinit var context: Context
    private lateinit var db: CatalogDb
    private val now = 1_800_000_000_000L
    private fun photo(key: String, time: Long) = Photo(key, "content://photo/$key", time)
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase("timers.db"); db = CatalogDb(context, "timers.db") }
    @After fun teardown() { db.close(); context.deleteDatabase("timers.db") }

    @Test fun autoTimeoutIsThirtyMinutesAndTracksLastPhoto() {
        assertEquals(30 * 60_000L, SESSION_TIMEOUT)
        val photos = listOf(photo("a", now), photo("b", now + 29 * 60_000))
        assertFalse(db.reconcile(photos, now + 58 * 60_000).sessions.single().complete)
        assertTrue(db.reconcile(photos, now + 59 * 60_000).sessions.single().complete)
    }
    @Test fun fixedTimerIgnoresInactivityAndDoesNotResetWithPhotos() {
        val id = db.begin("Trip", now, duration = 2 * HOUR)
        assertFalse(db.reconcile(emptyList(), now + HOUR).sessions.single().complete)
        val photos = listOf(photo("a", now + 2 * HOUR - 1), photo("b", now + 2 * HOUR))
        val result = db.reconcile(photos.reversed(), now + 2 * HOUR)
        assertTrue(result.sessions.single { it.id == id }.complete)
        assertEquals(id, result.photos.single { it.key == "a" }.sessionId)
        assertNotEquals(id, result.photos.single { it.key == "b" }.sessionId)
        assertEquals(now + 2 * HOUR, result.periods.single().stop)
        assertTrue(db.claim(id)); assertFalse(db.claim(id))
    }
    @Test fun unnamedAutoCanResumeRepeatedlyAndRecoverLatePhotosWithoutTakingGapPhotos() {
        val first = photo("a", now)
        val id = db.reconcile(listOf(first), now + SESSION_TIMEOUT).sessions.single().id
        assertTrue(db.claim(id))
        val tapped = now + HOUR
        db.begin(null, tapped, id, HOUR)
        val date = db.load().sessions.single().sessionDate
        db.close(); db = CatalogDb(context, "timers.db")
        assertEquals(tapped + HOUR, SessionRules.deadline(db.load().sessions.single(), db.load()))
        assertFalse(db.reconcile(listOf(first), tapped + HOUR - 1).sessions.single().complete)
        db.expireTimers(tapped + HOUR)
        assertTrue(db.claim(id))
        val all = listOf(first, photo("gap", now + SESSION_TIMEOUT + 1000), photo("late", tapped + HOUR - 1), photo("after", tapped + HOUR))
        val result = db.reconcile(all, tapped + HOUR + 1000)
        assertEquals(id, result.photos.single { it.key == "late" }.sessionId)
        assertNotEquals(id, result.photos.single { it.key == "gap" }.sessionId)
        assertNotEquals(id, result.photos.single { it.key == "after" }.sessionId)
        db.begin(null, tapped + 2 * HOUR, id, 2 * HOUR)
        assertEquals(date, db.load().sessions.single { it.id == id }.sessionDate)
        assertNull(db.load().sessions.single { it.id == id }.name)
        db.expireTimers(tapped + 4 * HOUR)
        assertTrue(db.claim(id))
    }
    @Test fun namingDuringTimerKeepsItsCompletionReminderAndManualStopSuppressesIt() {
        val id = db.begin("Trip", now, duration = HOUR)
        db.name(id, "Renamed", "")
        db.expireTimers(now + HOUR)
        assertTrue(db.claim(id))
        db.begin("Renamed", now + HOUR + 1, id, HOUR)
        db.stop(id, now + HOUR + 2)
        assertFalse(db.claim(id))
        assertEquals("", db.load().sessions.single().sessionDate)
    }
    @Test fun stoppingOrStartingAnotherSessionNeverMovesAnExpiredDeadline() {
        val id = db.begin("First", now, duration = HOUR)
        db.begin("Second", now + 2 * HOUR, duration = HOUR)
        assertEquals(now + HOUR, db.load().periods.single { it.sessionId == id }.stop)
        val result = db.reconcile(listOf(photo("gap", now + HOUR + 1)), now + 2 * HOUR)
        assertNotEquals(id, result.photos.single().sessionId)
    }
    @Test fun wholeHoursOnlyAndInvalidInput() {
        assertEquals(HOUR, durationFromHours("1"))
        assertEquals(3 * HOUR, durationFromHours("3"))
        assertEquals(168 * HOUR, durationFromHours("168"))
        listOf("", "0", "-1", "NaN", "Infinity", "169", "0.001", "abc", "1.5", "1,5", "1.0", "+1", "1e2", "999999999999").forEach { assertNull(it, durationFromHours(it)) }
    }
    @Test fun versionFourMigrationPreservesCatalogPreferencesExportsAndLegacyPeriods() {
        val id = db.begin("Existing", now)
        db.put("photos_album_account_$id", "album-id")
        db.put("notifications", "off")
        db.reconcile(listOf(photo("a", now + 1000)), now + 1000)
        db.writableDatabase.execSQL("ALTER TABLE periods RENAME TO newer_periods")
        db.writableDatabase.execSQL("CREATE TABLE periods(session_id INTEGER NOT NULL REFERENCES sessions(id),start INTEGER NOT NULL PRIMARY KEY,stop INTEGER)")
        db.writableDatabase.execSQL("INSERT INTO periods SELECT session_id,start,stop FROM newer_periods")
        db.writableDatabase.execSQL("DROP TABLE newer_periods")
        db.writableDatabase.version = 4
        db.close(); db = CatalogDb(context, "timers.db")
        assertEquals(5, db.readableDatabase.version)
        assertEquals("Existing", db.load().sessions.single().name)
        assertEquals(id, db.load().photos.single().sessionId)
        assertNull(db.load().periods.single().deadline)
        assertEquals("album-id", db.state("photos_album_account_$id"))
        assertEquals("off", db.state("notifications"))
    }
}
