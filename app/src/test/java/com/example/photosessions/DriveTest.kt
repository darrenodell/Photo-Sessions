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

class DriveQueryTest {
    @Test fun literalsEscapeQuotesAndBackslashes() {
        assertEquals("'it\\'s'", DriveQuery.literal("it's"))
        assertEquals("'a\\\\b'", DriveQuery.literal("a\\b"))
    }
    @Test fun uploadedQueryTargetsFolderAndMediaKey() {
        assertEquals("'F1' in parents and appProperties has { key='mediaKey' and value='primary:1:123' } and trashed = false",
            DriveQuery.uploadedQuery("F1", "primary:1:123"))
    }
    @Test fun folderNamesAreCleanedAndNeverBlank() {
        assertEquals("Garage wiring", DriveQuery.folderName("  Garage wiring  "))
        assertEquals("Unnamed session", DriveQuery.folderName(null))
        assertEquals("Unnamed session", DriveQuery.folderName("   "))
        assertEquals("a b", DriveQuery.folderName("a\nb"))
        assertEquals(120, DriveQuery.folderName("x".repeat(300)).length)
    }
    @Test fun folderUrlOpensDriveFolder() {
        assertEquals("https://drive.google.com/drive/folders/F1", DriveQuery.folderUrl("F1"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DriveStateTest {
    private lateinit var context: Context
    private lateinit var db: CatalogDb
    private val name = "drive-test.db"
    private val now = 1_800_000_000_000L
    private val photos = listOf(Photo("primary:1:123", "content://media/external_primary/images/media/1", now))
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase(name); db = CatalogDb(context, name) }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun uploadStateSurvivesReopenAndReconciliation() {
        db.reconcile(photos, now)
        db.driveStatus("primary:1:123", "friend@gmail.com", "uploaded", "FILE1")
        db.close(); db = CatalogDb(context, name)
        db.reconcile(photos, now + SESSION_TIMEOUT)
        val photo = db.load().photos.single()
        assertEquals("uploaded", photo.driveState)
        assertEquals("FILE1", photo.driveFileId)
        assertEquals("friend@gmail.com", photo.driveAccount)
    }
    @Test fun newPhotosStartNotUploaded() {
        db.reconcile(photos, now)
        val photo = db.load().photos.single()
        assertEquals("none", photo.driveState); assertNull(photo.driveFileId); assertNull(photo.driveAccount)
    }
    @Test fun stateKeysCanBeRemoved() {
        db.put("drive_account", "friend@gmail.com"); db.remove("drive_account")
        assertNull(db.state("drive_account"))
    }
    @Test fun versionTwoUpgradeAddsDriveColumnsAndKeepsCopies() {
        db.close(); context.deleteDatabase(name)
        val legacy = context.openOrCreateDatabase(name, 0, null)
        legacy.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY, name TEXT, start INTEGER NOT NULL, end INTEGER NOT NULL, complete INTEGER NOT NULL, prompt TEXT NOT NULL, manual INTEGER NOT NULL DEFAULT 0, stopped_at INTEGER)")
        legacy.execSQL("CREATE TABLE photos(media_key TEXT PRIMARY KEY, uri TEXT NOT NULL, time INTEGER NOT NULL, session_id INTEGER NOT NULL REFERENCES sessions(id), available INTEGER NOT NULL, copy_uri TEXT, copy_state TEXT NOT NULL DEFAULT 'pending')")
        legacy.execSQL("CREATE TABLE periods(session_id INTEGER NOT NULL REFERENCES sessions(id), start INTEGER NOT NULL PRIMARY KEY, stop INTEGER)")
        legacy.execSQL("CREATE INDEX photos_session ON photos(session_id)")
        legacy.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO sessions VALUES(1,'Garage',100,200,1,'handled',0,NULL)")
        legacy.execSQL("INSERT INTO photos VALUES('k1','content://media/1',100,1,1,'content://media/copy1','copied')")
        legacy.version = 2; legacy.close()
        db = CatalogDb(context, name)
        assertEquals(3, db.readableDatabase.version)
        val photo = db.load().photos.single()
        assertEquals("copied", photo.copyState); assertEquals("none", photo.driveState)
        assertEquals("Garage", db.load().sessions.single().name)
    }
}
