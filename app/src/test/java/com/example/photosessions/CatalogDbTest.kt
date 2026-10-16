package com.example.photosessions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class CatalogDbTest {
    private lateinit var context: Context
    private lateinit var db: CatalogDb
    private val name = "catalog-test.db"
    private val now = 1_800_000_000_000L
    private val photos = listOf(Photo("primary:1:123", "content://media/external_primary/images/media/1", now))
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase(name); db = CatalogDb(context, name) }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    @Test fun baselineSurvivesReopenAndCannotBeReset() {
        db.start(now); db.start(now + 1000); db.close(); db = CatalogDb(context, name)
        assertEquals(now.toString(), db.state("baseline"))
    }
    @Test fun screenshotStartSurvivesReopenWithoutChangingCameraBaselineOrCatalog() {
        db.start(now - HOUR)
        val original = db.reconcile(photos, now)
        assertEquals(now, db.screenshotsSince(now))
        db.close(); db = CatalogDb(context, name)
        assertEquals(now, db.screenshotsSince(now + HOUR))
        assertEquals((now - HOUR).toString(), db.state("baseline"))
        assertEquals(original, db.load())
        assertEquals(5, db.readableDatabase.version)
    }
    @Test fun repeatScanAfterReopenDoesNotDuplicateOrReprompt() {
        val result = db.reconcile(photos + photos, now + SESSION_TIMEOUT)
        val id = result.sessions.single().id
        assertTrue(db.claim(id)); db.close(); db = CatalogDb(context, name)
        db.reconcile(photos, now + 2 * SESSION_TIMEOUT)
        assertEquals(1, db.load().photos.size); assertEquals(1, db.load().sessions.size)
        assertFalse(db.claim(id))
        assertEquals("claimed", db.load().sessions.single().prompt)
        assertEquals((now + 2 * SESSION_TIMEOUT).toString(), db.state("last_scan"))
    }
    @Test fun activeSessionsCannotBeClaimed() {
        val id = db.reconcile(photos, now).sessions.single().id
        assertFalse(db.claim(id))
    }
    @Test fun skipKeepsUnnamedSessionAndSuppressesPrompts() {
        val id = db.reconcile(photos, now + SESSION_TIMEOUT).sessions.single().id
        db.name(id, null); db.close(); db = CatalogDb(context, name)
        assertNull(db.load().sessions.single().name); assertFalse(db.claim(id))
        db.name(id, "  Garage wiring  ")
        assertEquals("Garage wiring", db.load().sessions.single().name)
    }
    @Test fun emptyNamesRejectedWithoutChangingSession() {
        val id = db.reconcile(photos, now).sessions.single().id
        assertThrows(IllegalArgumentException::class.java) { db.name(id, "  ") }
        assertNull(db.load().sessions.single().name)
    }
    @Test fun reconciliationPreservesNamesAndForeignKeys() {
        val id = db.reconcile(photos, now).sessions.single().id
        db.name(id, "Garage")
        db.reconcile(photos + Photo("primary:2:124", "content://media/external_primary/images/media/2", now + 1000), now + SESSION_TIMEOUT + 1000)
        assertEquals("Garage", db.load().sessions.single().name)
        assertEquals(2, db.load().photos.size)
        db.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
    }
    @Test fun failedTransactionDoesNotLeavePartialSessionsOrAdvanceProgress() {
        db.reconcile(photos, now)
        val original = db.load()
        db.writableDatabase.execSQL("CREATE TRIGGER reject_photo BEFORE INSERT ON photos BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertThrows(android.database.sqlite.SQLiteException::class.java) {
            db.reconcile(photos + Photo("primary:2:124", "content://media/external_primary/images/media/2", now + 3 * SESSION_TIMEOUT), now + 4 * SESSION_TIMEOUT)
        }
        assertEquals(original, db.load())
        assertEquals(now.toString(), db.state("last_scan"))
    }
    @Test fun notificationPreferenceAndMissingPhotosPersist() {
        db.reconcile(photos, now); db.put("notifications", "off")
        db.reconcile(emptyList(), now + SESSION_TIMEOUT); db.close(); db = CatalogDb(context, name)
        assertEquals("off", db.state("notifications")); assertFalse(db.load().photos.single().available)
        assertEquals(1, db.load().sessions.size)
    }

    @Test fun manualStartStopResumeKeepsIdentityAndExcludesGap() {
        val id = db.begin("Workshop", now)
        assertEquals("Workshop", db.load().sessions.single().name)
        assertFalse(db.load().sessions.single().complete)
        val first = photos.single().copy(time = now + 1000)
        db.reconcile(listOf(first), now + 1000)
        db.stop(id, now + 2000)
        val gap = first.copy(key = "gap", time = now + 3000)
        db.reconcile(listOf(first, gap), now + 4000)
        assertNotEquals(id, db.load().photos.single { it.key == "gap" }.sessionId)
        db.begin("Workshop", now + 5000, id)
        val resumed = first.copy(key = "resumed", time = now + 6000)
        db.reconcile(listOf(first, gap, resumed), now + 7000)
        assertEquals(id, db.load().photos.single { it.key == "resumed" }.sessionId)
        assertFalse(db.load().sessions.single { it.id == id }.complete)
        db.close(); db = CatalogDb(context, name)
        assertEquals(2, db.load().periods.count { it.sessionId == id })
        db.reconcile(listOf(first, gap, resumed), now + 6000 + SESSION_TIMEOUT)
        assertTrue(db.load().sessions.single { it.id == id }.complete)
    }
    @Test fun emptyManualSessionTimesOutAndCanResume() {
        val id = db.begin("Empty", now)
        db.reconcile(emptyList(), now + SESSION_TIMEOUT)
        assertTrue(db.load().sessions.single().complete)
        db.begin("Empty", now + SESSION_TIMEOUT + 1000, id)
        db.reconcile(emptyList(), now + SESSION_TIMEOUT + 2000)
        assertFalse(db.load().sessions.single().complete)
    }
    @Test fun deleteDoesNotRecreatePhotosOrReuseIdentity() {
        val old = db.reconcile(photos, now).sessions.single().id
        db.deleteSession(old)
        db.close(); db = CatalogDb(context, name)
        db.reconcile(photos, now + SESSION_TIMEOUT)
        assertTrue(db.load().sessions.isEmpty())
        val newer = photos.single().copy(key = "new", time = now + 2 * SESSION_TIMEOUT)
        db.reconcile(photos + newer, now + 2 * SESSION_TIMEOUT)
        assertNotEquals(old, db.load().sessions.single().id)
        assertEquals("new", db.load().photos.single().key)
    }
    @Test fun copiedStatusAndRenameSurviveReconciliation() {
        val id = db.reconcile(photos, now).sessions.single().id
        db.copyStatus(photos.single().key, "content://media/copy/1", "copied")
        db.name(id, "First"); db.name(id, "Renamed")
        db.reconcile(photos, now + SESSION_TIMEOUT)
        db.close(); db = CatalogDb(context, name)
        assertEquals("Renamed", db.load().sessions.single().name)
        assertEquals("copied", db.load().photos.single().copyState)
        assertEquals("content://media/copy/1", db.load().photos.single().copyUri)
    }
    @Test fun versionOneUpgradePreservesCatalogAndPreferences() {
        db.close(); context.deleteDatabase(name)
        val legacy = context.openOrCreateDatabase(name, 0, null)
        legacy.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY, name TEXT, start INTEGER NOT NULL, end INTEGER NOT NULL, complete INTEGER NOT NULL, prompt TEXT NOT NULL)")
        legacy.execSQL("CREATE TABLE photos(media_key TEXT PRIMARY KEY, uri TEXT NOT NULL, time INTEGER NOT NULL, session_id INTEGER NOT NULL REFERENCES sessions(id), available INTEGER NOT NULL)")
        legacy.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO sessions VALUES(1,'Old name',100,200,1,'handled')")
        legacy.execSQL("INSERT INTO photos VALUES('old','content://media/old',100,1,1)")
        legacy.execSQL("INSERT INTO state VALUES('notifications','off')")
        legacy.version = 1; legacy.close()
        db = CatalogDb(context, name)
        assertEquals("Old name", db.load().sessions.single().name)
        assertEquals("pending", db.load().photos.single().copyState)
        assertEquals("off", db.state("notifications"))
        assertEquals(5, db.readableDatabase.version)
        db.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { assertFalse(it.moveToFirst()) }
    }
}
