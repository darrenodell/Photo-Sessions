package com.example.photosessions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PhotosTest {
    private lateinit var db: CatalogDb
    private lateinit var context: Context
    private var id = 0L
    private val bytes = byteArrayOf(1, 2, 3)
    private fun source() = UploadSource("camera.jpg", "image/jpeg", 3) { ByteArrayInputStream(bytes) }
    private class Fake : PhotosService {
        val albums = mutableMapOf<String, PhotosAlbum>()
        var creates = 0; var uploads = 0; var additions = 0; var failAdd = false; var loseCreate = false
        override fun album(id: String) = albums[id]
        override fun findAlbum(title: String) = albums.values.find { it.title == title }
        override fun createAlbum(title: String): PhotosAlbum {
            val a = PhotosAlbum("album-${++creates}", title, "https://photos.google.com/album/$creates"); albums[a.id] = a
            if(loseCreate) { loseCreate = false; throw IOException("Response lost") }
            return a
        }
        override fun renameAlbum(id: String, title: String) { albums[id] = albums.getValue(id).copy(title = title) }
        override fun upload(source: UploadSource): String { uploads++; assertArrayEquals(byteArrayOf(1, 2, 3), source.open().use { it.readBytes() }); return "token-$uploads" }
        override fun add(album: String, uploadToken: String, filename: String): String {
            additions++; if(failAdd) { failAdd = false; throw IOException("Response lost") }; return "media-$uploadToken"
        }
    }
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext(); context.deleteDatabase("photos-test.db"); db = CatalogDb(context, "photos-test.db")
        id = db.begin("Beach", 1000); db.reconcile(listOf(Photo("p", "content://camera/1", 1001)), 1002)
    }
    @After fun teardown() { db.close(); context.deleteDatabase("photos-test.db") }
    @Test fun homeCountsUseCurrentAccountAlbumAndCatalogMembership() {
        val catalog = db.load(); val photo = catalog.photos.single()
        val state = PhotosState(db, "me", id)
        state.saveAlbum(PhotosAlbum("album", "Beach", "url")); state.done(photo)
        // Stale records for photos no longer in the catalog cannot inflate the count.
        db.put("photos_item_me_${id}_removed", "album")
        assertEquals(mapOf(id to 1), photosExportCounts(db, catalog, "me"))
        assertTrue(photosExportCounts(db, catalog, "friend").isEmpty())
        assertTrue(photosExportCounts(db, catalog, null).isEmpty())
        state.saveAlbum(PhotosAlbum("replacement", "Beach", "new-url"))
        assertTrue(photosExportCounts(db, catalog, "me").isEmpty())
    }
    @Test fun uploadedMembershipPersistsAndRepeatedRequestSkipsBytesAndAddition() {
        val api = Fake(); val uploader = PhotosTransfer(db, api, "me", { source() })
        val photo = db.load().photos.single()
        assertFalse(PhotosState(db, "me", id).added(photo))
        uploader.upload(id, { _, _ -> }, { false }); db.close(); db = CatalogDb(context, "photos-test.db")
        PhotosTransfer(db, api, "me", { source() }).upload(id, { _, _ -> }, { false })
        assertEquals(1, api.creates); assertEquals(1, api.uploads); assertEquals(1, api.additions)
        assertTrue(PhotosState(db, "me", id).added(photo))
        assertFalse(PhotosState(db, "friend", id).added(photo)); assertEquals("none", db.load().photos.single().driveState)
    }
    @Test fun lostAddResponseReusesTokenAndExpiryForcesNewBytes() {
        val api = Fake(); api.failAdd = true; var time = 1000L
        val uploader = PhotosTransfer(db, api, "me", { source() }, { time })
        try { uploader.upload(id, { _, _ -> }, { false }); fail() } catch(_: IOException) {}
        uploader.upload(id, { _, _ -> }, { false })
        assertEquals(1, api.uploads); assertEquals(2, api.additions)
        val photo = db.load().photos.single(); val state = PhotosState(db, "me", id)
        state.token(photo, "t", time)
        assertEquals("t", state.token(photo, time + 22 * 3600000))
        assertNull(state.token(photo, time + 23 * 3600000)); assertNull(state.token(photo, time - 1))
    }
    @Test fun lostAlbumCreateResponseRecoversTheSameAlbum() {
        val api = Fake(); api.loseCreate = true; val uploader = PhotosTransfer(db, api, "me", { source() })
        try { uploader.upload(id, { _, _ -> }, { false }); fail() } catch(_: IOException) {}
        uploader.upload(id, { _, _ -> }, { false })
        assertEquals(1, api.creates); assertEquals(1, api.albums.size)
        assertEquals(SessionLabels.title(db.load().sessions.single()), api.albums.values.single().title)
    }
    @Test fun deletedAlbumRecreatesMembershipAndSessionRenameKeepsAlbumId() {
        val api = Fake(); val uploader = PhotosTransfer(db, api, "me", { source() })
        uploader.upload(id, { _, _ -> }, { false }); val first = PhotosState(db, "me", id).album()
        db.name(id, "New name"); uploader.upload(id, { _, _ -> }, { false })
        assertEquals(first, PhotosState(db, "me", id).album()); assertEquals(1, api.uploads)
        api.albums.clear(); uploader.upload(id, { _, _ -> }, { false })
        assertEquals(2, api.uploads); assertEquals(2, api.creates)
        db.deleteSession(id)
        assertTrue(db.states("photos_").isEmpty()); assertTrue(db.states("rename_photos_").isEmpty())
    }
    @Test fun missingSourceAndCancellationAreNotReportedAsComplete() {
        val api = Fake(); val uploader = PhotosTransfer(db, api, "me", { null })
        assertEquals(UploadOutcome.STOPPED, uploader.upload(id, { _, _ -> }, { true })); assertEquals(0, api.creates)
        try { uploader.upload(id, { _, _ -> }, { false }); fail() } catch(e: IllegalStateException) { assertTrue(e.message!!.contains("could not be read")) }
        assertFalse(PhotosState(db, "me", id).added(db.load().photos.single()))
    }
    @Test fun renameRetriesEachDestinationIndependentlyAndPhotosCoverageSuppressesWarning() {
        val api = Fake(); PhotosTransfer(db, api, "me", { source() }).upload(id, { _, _ -> }, { false })
        val album = PhotosState(db, "me", id).album()!!
        assertEquals(0, SessionFiles(context, db).unexported(db.load().photos))
        db.put("drive_folder_me_$id", "drive-folder"); db.name(id, "Renamed")
        var photosCalls = 0
        val renamer = ExportNames(context, db, {}, { _, _, _ -> error("Drive offline") }, { _, a, title ->
            assertEquals(album, a); assertEquals(SessionLabels.title(db.load().sessions.single()), title); photosCalls++
        })
        renamer.retry(id)
        assertEquals(1, photosCalls); assertNotNull(db.state("rename_drive_me_$id"))
        assertNull(db.state("rename_photos_me_$id")); assertEquals(album, PhotosState(db, "me", id).album())
        renamer.retry(id); assertEquals(1, photosCalls)
    }
    @Test fun itemErrorsInsideSuccessfulHttpResponseDoNotCountAsAdded() {
        val successful = JSONObject("""{"newMediaItemResults":[{"uploadToken":"t","status":{},"mediaItem":{"id":"m"}}]}""")
        assertEquals("m", PhotosApi.parseAdded(successful, "t"))
        val partial = JSONObject("""{"newMediaItemResults":[{"uploadToken":"t","status":{"code":7,"message":"Not permitted"}}]}""")
        try { PhotosApi.parseAdded(partial, "t"); fail() } catch(e: PhotosHttpException) { assertEquals(403, e.code); assertFalse(PhotosErrors.retryable(e)) }
        assertTrue(PhotosErrors.retryable(PhotosHttpException(429, "Quota")))
        assertTrue(PhotosErrors.retryable(IOException()))
        assertFalse(PhotosErrors.retryable(PhotosHttpException(403, "Storage full")))
        assertTrue(PhotosErrors.message(PhotosHttpException(403, "Storage full")).contains("storage is full"))
    }
}
