package com.example.photosessions

import org.junit.Assert.*
import org.junit.Test

class SessionRulesTest {
    private val start = 1_800_000_000_000L
    private fun photo(id: Int, offset: Long) = Photo("external_primary:$id:1800000000", "content://media/external_primary/images/media/$id", start + offset)
    private fun group(vararg offsets: Long) = SessionRules.reconcile(Catalog(), offsets.mapIndexed { i, time -> photo(i, time) }, start + offsets.max() + SESSION_TIMEOUT)

    @Test fun belowBoundaryExtends() { assertEquals(1, group(0, SESSION_TIMEOUT - 1).sessions.size) }
    @Test fun exactBoundarySplits() { assertEquals(2, group(0, SESSION_TIMEOUT).sessions.size) }
    @Test fun aboveBoundarySplits() { assertEquals(2, group(0, SESSION_TIMEOUT + 1).sessions.size) }
    @Test fun onePhotoCompletesOnlyAtDeadline() {
        val p = photo(1, 0)
        assertFalse(SessionRules.reconcile(Catalog(), listOf(p), start + SESSION_TIMEOUT - 1).sessions.single().complete)
        assertTrue(SessionRules.reconcile(Catalog(), listOf(p), start + SESSION_TIMEOUT).sessions.single().complete)
    }
    @Test fun timeoutExtendsFromLastPhoto() {
        val result = SessionRules.reconcile(Catalog(), listOf(photo(1, 0), photo(2, SESSION_TIMEOUT - 1)), start + SESSION_TIMEOUT)
        assertFalse(result.sessions.single().complete)
    }
    @Test fun midnightDoesNotSplit() {
        val midnight = 1_800_057_600_000L
        val result = SessionRules.reconcile(Catalog(), listOf(photo(1, 0).copy(time = midnight - 60_000), photo(2, 0).copy(time = midnight + 60_000)), midnight + SESSION_TIMEOUT + 60_000)
        assertEquals(1, result.sessions.size)
    }
    @Test fun delayedUnsortedBatchCreatesDistinctSessions() {
        val result = group(3 * SESSION_TIMEOUT, 0, SESSION_TIMEOUT - 1, 5 * SESSION_TIMEOUT, 3 * SESSION_TIMEOUT + 1)
        assertEquals(listOf(1, 2, 2), result.sessions.map { s -> result.photos.count { it.sessionId == s.id } })
        assertTrue(result.sessions.all { it.complete })
    }
    @Test fun duplicatesAndRestartReplayAreIdempotent() {
        val photos = listOf(photo(1, 0), photo(2, 10), photo(3, 3 * SESSION_TIMEOUT))
        val first = SessionRules.reconcile(Catalog(), photos + photos, start + 4 * SESSION_TIMEOUT)
        val restarted = SessionRules.reconcile(first.copy(sessions = first.sessions.map { it.copy(prompt = "claimed") }), photos.reversed(), start + 5 * SESSION_TIMEOUT)
        assertEquals(first.photos, restarted.photos)
        assertEquals(first.sessions.map { it.id }, restarted.sessions.map { it.id })
        assertTrue(restarted.sessions.all { it.prompt == "claimed" })
    }
    @Test fun lateIndexedPhotoJoinsExistingSessionWithoutRenamingOrReprompting() {
        val first = group(0, 20_000).let { it.copy(sessions = it.sessions.map { s -> s.copy(name = "Garage", prompt = "handled") }) }
        val second = SessionRules.reconcile(first, first.photos + photo(3, 10_000), start + SESSION_TIMEOUT + 20_000)
        assertEquals(1, second.sessions.size)
        assertEquals("Garage", second.sessions.single().name)
        assertEquals("handled", second.sessions.single().prompt)
        assertEquals(3, second.photos.size)
    }
    @Test fun lateBridgeKeepsBothStableSessionIds() {
        val first = group(0, SESSION_TIMEOUT + 20)
        val second = SessionRules.reconcile(first, first.photos + photo(3, SESSION_TIMEOUT / 2), start + 3 * SESSION_TIMEOUT)
        assertEquals(first.sessions.map { it.id }.toSet(), second.sessions.map { it.id }.toSet())
        first.photos.forEach { original -> assertEquals(original.sessionId, second.photos.single { it.key == original.key }.sessionId) }
    }
    @Test fun unavailablePhotosRetainMembershipAndRecover() {
        val first = group(0)
        val missing = SessionRules.reconcile(first, emptyList(), start + SESSION_TIMEOUT)
        assertFalse(missing.photos.single().available)
        assertEquals(first.sessions, missing.sessions)
        val recovered = SessionRules.reconcile(missing, first.photos, start + SESSION_TIMEOUT)
        assertEquals(first, recovered)
    }
    @Test fun volumeIsPartOfIdentity() {
        val p = photo(1, 0)
        val other = p.copy(key = "sdcard:1:1800000000", uri = "content://media/sdcard/images/media/1")
        assertEquals(2, SessionRules.reconcile(Catalog(), listOf(p, other), start).photos.size)
    }
    @Test fun reusedMediaIdDoesNotReuseOldMembership() {
        val first = group(0)
        val newPhoto = photo(0, 3 * SESSION_TIMEOUT).copy(key = "external_primary:0:1800005400")
        val next = SessionRules.reconcile(first, listOf(newPhoto), start + 4 * SESSION_TIMEOUT)
        assertEquals(2, next.photos.size)
        assertFalse(next.photos.single { it.key == first.photos.single().key }.available)
    }
    @Test fun timestampFallbackAndInvalidDates() {
        assertEquals(start, SessionRules.effectiveTime(start, 1, start))
        assertEquals(start, SessionRules.effectiveTime(0, start / 1000, start))
        assertEquals(start, SessionRules.effectiveTime(start + 60_000, start / 1000, start))
        assertNull(SessionRules.effectiveTime(-1, -1, start))
        assertNull(SessionRules.effectiveTime(0, Long.MAX_VALUE, start))
    }
    @Test fun cameraPathExcludesOtherFolders() {
        assertTrue(SessionRules.cameraPath("DCIM/Camera/"))
        assertTrue(SessionRules.cameraPath("dcim/CAMERA/burst/"))
        listOf("Pictures/Screenshots/", "Download/", "DCIM/CameraOther/", "Pictures/WhatsApp/", "").forEach { assertFalse(SessionRules.cameraPath(it)) }
    }
    @Test fun screenshotFoldersExcludeDownloadsExportsAndSimilarNames() {
        listOf("Pictures/Screenshots/", "DCIM/Screenshots", "/pictures/SCREENSHOTS/edited/", "DCIM/Screenshots/scroll/").forEach {
            assertTrue(it, SessionRules.screenshotPath(it))
        }
        listOf("Pictures/Photo Sessions/Test/", "Download/Screenshots/", "DCIM/ScreenshotsOther/", "Pictures/WhatsApp/", "").forEach {
            assertFalse(it, SessionRules.screenshotPath(it))
        }
    }
    @Test fun screenshotCutoffDoesNotBackfillOrHideOlderCameraPhotos() {
        val baseline = start - HOUR
        fun time(path: String, taken: Long, added: Long = taken / 1000) =
            SessionRules.discoveryTime(path, taken, added, baseline, start, start + HOUR)
        assertEquals(start - 1, time("DCIM/Camera/", start - 1))
        assertNull(time("Pictures/Screenshots/", start - 1, (start + HOUR) / 1000))
        assertEquals(start, time("Pictures/Screenshots/", start))
        assertEquals(start + 1000, time("DCIM/Screenshots/", start + 1000))
        assertEquals(start, time("DCIM/Screenshots/", 0, start / 1000))
        assertNull(time("DCIM/Screenshots/", 0, start / 1000 - 1))
        assertNull(time("Pictures/Photo Sessions/Test/", start))
        assertNull(time("Download/", start))
        assertNull(time("DCIM/Camera/", baseline - 1))
        // Missing capture dates cannot lose a screenshot created during the activation second.
        assertEquals(start, SessionRules.discoveryTime("DCIM/Screenshots/", 0, start / 1000, baseline, start + 500, start + HOUR))
        // A new installation still excludes screenshots taken before monitoring starts.
        assertNull(SessionRules.discoveryTime("DCIM/Screenshots/", start, start / 1000, start + 1000, start, start + HOUR))
    }
}
