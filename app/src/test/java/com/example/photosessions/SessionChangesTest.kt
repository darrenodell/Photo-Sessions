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
class SessionChangesTest {
    private lateinit var context: Context
    private lateinit var db: CatalogDb
    @Before fun setup() { context = ApplicationProvider.getApplicationContext(); context.deleteDatabase("changes.db"); db = CatalogDb(context, "changes.db") }
    @After fun teardown() { db.close(); context.deleteDatabase("changes.db") }
    @Test fun dateSurvivesRenameResumeAndDiscovery() {
        val now = 1780000000000L
        val id = db.begin("Beach", now)
        val originalDate = db.load().sessions.single().sessionDate
        db.name(id, "Beach Trip")
        assertEquals(originalDate, db.load().sessions.single().sessionDate)
        db.name(id, "Beach Trip", "2026-10-04")
        db.stop(id, now + 1000); db.begin("Beach Trip", now + 2000, id)
        db.reconcile(listOf(Photo("a", "content://original/1", now + 3000)), now + 4000)
        db.close(); db = CatalogDb(context, "changes.db")
        val s = db.load().sessions.single()
        assertEquals("2026-10-04 — Beach Trip", SessionLabels.title(s))
        assertEquals(now, s.start)
        assertEquals(id, db.load().photos.single().sessionId)
    }
    @Test fun localNamesCannotEscapeTheirSessionFolder() {
        val session = Session(7, "../Beach/Trip\\name\n", 1000, 1000, true, sessionDate = "2026-10-04")
        val path = SessionLabels.localPath(session)
        assertTrue(path.startsWith("Pictures/Photo Sessions/2026-10-04 — "))
        assertTrue(path.endsWith(" (7)/"))
        assertEquals(3, path.count { it == '/' })
        assertFalse(path.contains('\n')); assertFalse(path.contains('\\'))
    }
    @Test fun datesStrictlyRejectInvalidDaysAndFormats() {
        assertTrue(SessionLabels.validDate("2024-02-29"))
        listOf("2026-02-29", "2026-2-01", "2026-13-01", "Oct 4", "").forEach { assertFalse(SessionLabels.validDate(it)) }
    }
    @Test fun warningCountsPhotosWithNeitherCompletedExport() {
        val p = Photo("a", "content://original/1", 1)
        assertEquals(1, SessionLabels.unexported(listOf(p)))
        assertEquals(0, SessionLabels.unexported(listOf(p.copy(copyState = "copied", copyUri = "content://copy/1"))))
        assertEquals(0, SessionLabels.unexported(listOf(p.copy(driveState = "uploaded", driveFileId = "file"))))
        assertEquals(1, SessionLabels.unexported(listOf(p.copy(copyState = "copying", copyUri = "content://copy/1", driveState = "failed"))))
    }
    @Test fun failedDriveRenameDoesNotUndoLocalOrSessionAndCanRetry() {
        val id = db.begin("Old", 1000)
        db.put("folder_$id", "Pictures/Photo Sessions/Old/")
        db.put("drive_folder_friend@example.com_$id", "stable-folder-id")
        db.put("drive_name_friend@example.com_$id", "Old")
        db.name(id, "New", "2026-10-04")
        val calls = mutableListOf<String>()
        var fail = true
        val sync = ExportNames(context, db, { sessionId ->
            calls += "local"; db.put("folder_$sessionId", SessionLabels.localPath(db.load().sessions.single())); db.remove("rename_local_$sessionId")
        }, { account, folder, title ->
            assertEquals("friend@example.com", account); assertEquals("stable-folder-id", folder)
            assertEquals("2026-10-04 — New", title); calls += "drive"; if(fail) error("Offline")
        })
        sync.retry(id)
        assertEquals(listOf("local", "drive"), calls)
        assertEquals("New", db.load().sessions.single().name)
        assertTrue(db.state("folder_$id")!!.contains("2026-10-04 — New"))
        assertEquals("Old", db.state("drive_name_friend@example.com_$id"))
        assertEquals("Offline", db.state("rename_drive_friend@example.com_$id"))
        db.close(); db = CatalogDb(context, "changes.db")
        assertEquals("Offline", db.state("rename_drive_friend@example.com_$id"))
        fail = false
        ExportNames(context, db, {}, { _, folder, _ -> assertEquals("stable-folder-id", folder) }).retry(id)
        assertEquals("2026-10-04 — New", db.state("drive_name_friend@example.com_$id"))
        assertTrue(ExportNames(context, db).problems(id).isEmpty())
    }
    @Test fun localFailureStillAllowsDriveRename() {
        val id = db.begin("Old", 1000)
        db.put("folder_$id", "old"); db.put("drive_folder_me_$id", "folder-id")
        db.name(id, "New")
        ExportNames(context, db, { error("Storage unavailable") }, { _, _, _ -> }).retry(id)
        assertEquals("Storage unavailable", db.state("rename_local_$id"))
        assertEquals(SessionLabels.title(db.load().sessions.single()), db.state("drive_name_me_$id"))
        assertNull(db.state("rename_drive_me_$id"))
    }
    @Test fun deleteRemovesExportReferencesButRetainsReplayProtection() {
        val id = db.reconcile(listOf(Photo("a", "content://original/1", 1000)), 1000).sessions.single().id
        db.put("folder_$id", "old"); db.put("drive_folder_me_$id", "stable-id"); db.put("drive_root_me", "root")
        db.name(id, "New"); db.deleteSession(id)
        assertNull(db.state("folder_$id")); assertNull(db.state("drive_folder_me_$id")); assertNull(db.state("rename_drive_me_$id"))
        assertEquals("root", db.state("drive_root_me")); assertEquals("1", db.state("deleted_a"))
    }
    @Test fun versionThreeUpgradePreservesExportsAndUsesStartDate() {
        db.close(); context.deleteDatabase("changes.db")
        val legacy = context.openOrCreateDatabase("changes.db", 0, null)
        legacy.execSQL("CREATE TABLE sessions(id INTEGER PRIMARY KEY,name TEXT,start INTEGER NOT NULL,end INTEGER NOT NULL,complete INTEGER NOT NULL,prompt TEXT NOT NULL,manual INTEGER NOT NULL DEFAULT 0,stopped_at INTEGER)")
        legacy.execSQL("CREATE TABLE photos(media_key TEXT PRIMARY KEY,uri TEXT,time INTEGER,session_id INTEGER,available INTEGER,copy_uri TEXT,copy_state TEXT,drive_state TEXT,drive_file_id TEXT,drive_account TEXT)")
        legacy.execSQL("CREATE TABLE periods(session_id INTEGER,start INTEGER PRIMARY KEY,stop INTEGER)")
        legacy.execSQL("CREATE TABLE state(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO sessions VALUES(1,'Existing',1780000000000,1780086400000,1,'handled',1,NULL)")
        legacy.execSQL("INSERT INTO photos VALUES('p','content://original/1',1780000000000,1,1,'content://copy/1','copied','uploaded','drive-file','me')")
        legacy.execSQL("INSERT INTO state VALUES('folder_1','Pictures/Photo Sessions/Existing (1)/')")
        legacy.execSQL("INSERT INTO state VALUES('drive_folder_me_1','stable-folder')")
        legacy.version = 3; legacy.close(); db = CatalogDb(context, "changes.db")
        assertEquals(SessionLabels.date(1780000000000), db.load().sessions.single().sessionDate)
        assertEquals("drive-file", db.load().photos.single().driveFileId)
        assertEquals("content://copy/1", db.load().photos.single().copyUri)
        assertEquals("stable-folder", db.state("drive_folder_me_1"))
        assertEquals(4, db.readableDatabase.version)
    }
}
