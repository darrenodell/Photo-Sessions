# Validation report

## Version 1.7 checks (2026-10-16)

- Build, Android test APK compilation, lint and all 68 host tests passed. Lint: zero errors, 10 existing warnings and one hint. New host checks cover accepted screenshot folders, rejected downloads/exports/similar folder names, activation cutoffs, missing capture-date fallback, second-precision insertion timestamps, and cutoff persistence without changing the camera baseline or catalog. Schema remains 5.
- Installed 1.7 / code 14 in place on the owner's Galaxy Tab A9 (SM_X115, Android 16), retaining the existing debug signer. Full photo access was already granted. The tablet uses DCIM/Screenshots; its ten existing screenshots were excluded by the new persisted cutoff.
- Physical-tablet screenshot integration passed (one test): one JPEG camera image and two PNG screenshot images from DCIM/Screenshots and Pictures/Screenshots joined the same fixed-duration session; all three gallery thumbnails loaded and image-viewer routing worked. Copy Locally through the actual UI produced byte-identical copies with PNG filenames/MIME types retained. Sharing produced the image sharing intent, repeated scan/copy did not duplicate records or copies, and downloads/app exports were excluded. The manual deadline remained unchanged.
- The first device run stopped on a test-fixture storage mistake (a Download file inserted through the Images collection). Corrected the test to use the Downloads collection, rebuilt the test APK, and reran successfully. This did not require changing the app APK. All temporary test originals, copies and the test session were removed; a MediaStore query confirmed no test images remained.
- Before/after database snapshots: sessions, photos and periods are identical (the owner's catalog was empty before the update). All existing account/export/preference settings retained; only next_id and last_scan changed during the temporary test, with screenshots_since added. Database integrity check passed. Tablet evidence: `validation/screenshots-v1.7-tablet.png`.
- APK signature verified: signer SHA-1 `641b2c9192527952701558a00e53aa1950c528aa`. Delivery: `delivery/Photo-Sessions-1.7-debug.apk`, 31,989,828 bytes; SHA-256 `b8b732f7dc5323a9fc6b87cc93d2713feb199ad908ae501ffaff696c13f05258`.
- No live Google Photos or Drive media upload was initiated by the tablet test. Their existing source-file paths retain MIME types and bytes; PNG source preparation was verified locally. Actual new PNG cloud transfers, custom screenshot save locations, and screen-off/Doze/OEM background timing remain outside this check.
- Published the APK in place to the existing shared Drive file. Metadata readback confirms the 1.7 filename, matching 31,989,828-byte size, original file ID/parent, and preserved anyone-with-link reader access. Download link unchanged: https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view?usp=drivesdk.

## Version 1.6.3 checks (2026-10-12)

- Removed only the default “Not copied” photo caption and its padding; existing copied/failure/retry labels remain. Build and lint passed. No new tests added for this display-only change.
- Installed 1.6.3 / code 13 in place on the owner's Galaxy Tab A9. Visually inspected the current two-photo session: both thumbnails display without the unwanted caption or caption padding. UI hierarchy also confirms “Not copied” is absent.
- Before/after snapshots confirm the current 1 session, 2 photo records and 1 period are unchanged. Only last_scan changed in state. No synthetic photos, session changes or uploads were made on the tablet.
- APK signature verification passed with the retained signer. Local delivery: `delivery/Photo-Sessions-1.6.3-debug.apk`, 31,989,832 bytes, SHA-256 `8f3a048907a6d075c8c9beb059dbe6ae6f0c8975fe7a620e7914c5599a20963f`.
- Replaced the existing shared Drive APK in place. Metadata readback confirms 1.6.3, original file ID and parent folder, and retained anyone-with-link reader access. Download link unchanged: https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view.

## Version 1.6.2 checks (2026-10-12)

- Owner reported Google Photos “Can’t find link / This link has been deleted or the URL is invalid.” Initial tablet inspection showed the error and a saved album URL while its first media membership had not yet been recorded. Later the worker recorded the photo as added without error. The same URL opened the correct album/photo in Chrome and then in Google Photos. The evidence establishes a transient opening failure, not a failed upload; Google’s internal cause is unverified.
- Changed only album-link visibility: require at least one recorded added photo and no active upload for this session. This prevents opening during initial transfer, but does not guarantee Google has finished all server-side propagation. Upload logic, permissions, sharing and schema are unchanged.
- Build, lint and all 65 host tests passed. Installed 1.6.2 / code 12 in place on Galaxy Tab A9. From the actual updated session screen, “1 of 1 added” and the album button were visible; tapping the button opened the correct album and its photo in Google Photos. No extra photo upload or sharing was initiated during diagnosis.
- Before/after snapshots: both sessions, both photo records and all 3 periods unchanged. Only last_scan changed in state; completed upload references retained. No data clearing or synthetic fixtures on the tablet.
- APK signature verified with retained signer SHA-1 `641b2c9192527952701558a00e53aa1950c528aa`. Delivery: `delivery/Photo-Sessions-1.6.2-debug.apk`, 31,989,832 bytes, SHA-256 `2ec670922f9d1f6c4613e367b864877ed5d5257a27eea666b272c3ad11518021`.
- Uploaded in place to the existing Drive APK. Metadata readback confirmed 1.6.2 filename/size, original file ID and folder, and anyone-with-link reader access. Download: https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view.

## Version 1.6.1 checks (2026-10-12)

- Finished the interrupted October 11 update. Build, lint and Android test APK compilation passed; all 65 host tests passed. Lint has zero errors, 10 warnings and one hint.
- Android 16 disposable emulator: all three duration/notification tests passed at 360 dp width with 1.5× text. Manual and resume controls exercise 30 minutes, 1 hour, 2 hours, whole custom hours, and rejection of decimals. Actual notification PendingIntents verify all three durations, unnamed and named sessions, and duplicate/stale tap rejection. Deadlines were simulated; this does not measure real-time background delivery.
- Installed version 1.6.1 / code 11 in place on the owner's Galaxy Tab A9. Both non-destructive duration UI tests passed on the tablet. Before/after database snapshots confirm the current 1 session, 1 photo record and 2 periods are unchanged. All 21 non-diagnostic state entries were retained; only last_scan changed. Schema remains 5. No synthetic photos or sessions were created on the owner's tablet.
- Visually verified the tablet Start session dialog with all three presets and whole-hours guidance, then cancelled. Screenshot: `validation/dialog-start-v1.6.1-tablet.png`.
- APK signature verification passed with retained signer SHA-1 `641b2c9192527952701558a00e53aa1950c528aa`. Local delivery: `delivery/Photo-Sessions-1.6.1-debug.apk`, 31,989,832 bytes, SHA-256 `af1f84d3647c35cfe4cc3765063788b2f1a49f8949c1712bad2cb128463b149f`.
- Replaced the existing shared Drive APK in place on 2026-10-12. Metadata readback confirmed the 1.6.1 filename and byte size, original file ID and parent folder, and preserved anyone-with-link reader permission. Download: https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view?usp=drivesdk.
- Real screen-off/Doze/OEM timing remains unmeasured; Android background scheduling is inexact. Prior full organizer-flow checks are recorded under version 1.6 below.

## Version 1.6 checks (2026-10-11)

- Final host checks: `testDebugUnitTest lintDebug assembleDebug` passed. All 65 unit/Robolectric tests passed; lint has 0 errors and 10 existing dependency/icon warnings. Android test APK builds successfully.
- Timing coverage includes the 30-minute inactivity boundary, fixed timers across long quiet gaps, photos immediately before/at expiry, late indexing after expiry, empty sessions, repeated unnamed extension, named completion reminders, explicit Stop suppression, fractional/invalid input, process reopen and migrations from versions 1–4. The version-4 migration retains sessions, photo membership, legacy periods, preferences and export references.
- Android 16 emulator: notification integration passed (1 test). Real immutable notification PendingIntents exercise +1 hour and +2 hours, unnamed resume, named reminder after simulated expiry, persisted end times, and duplicate/stale action rejection. Tests use synthetic deadlines; no one-hour/two-hour wall-clock wait is claimed.
- Android 16 emulator: the existing full organizer flow passed (1 test) at 600 dp width: discovery, notification routing, naming/skipping, byte-identical local copies, original preservation, manual start/stop/resume, rename and delete behavior. Its stale optional-date field selector was updated, and Resume now selects a duration. An initial narrow-screen run hit the flow's existing viewport assumption; separate duration tests cover narrow screens.
- Duration UI tests passed (2 tests) at 320 dp width with normal text and 360 dp width with 1.5× font scaling. Checks cover preset selection, invalid custom input, decimal hours, disabled actions, keyboard entry and scroll reachability. Tablet-width duration checks also passed.
- Installed version 1.6 / code 10 on the owner's Galaxy Tab A9 (SM_X115, Android 16) using an in-place update. Before/after database snapshots confirm all 4 existing sessions, 3 photo records and 5 historical periods retain their names/dates/membership/export fields. All 25 non-diagnostic state entries are unchanged. Schema migrated from 4 to 5. No owner test session was created or existing session resumed.
- Visually inspected the actual tablet Start session and Resume session dialogs and emulator preview. Both dialogs were cancelled without changing owner data. Resume preview: `validation/dialog-resume-v1.6-tablet.png`. Tablet preview: `validation/dialog-start-v1.6-tablet.png`; emulator preview: `validation/dialog-start-v1.6-emulator.png`.
- Retained debug signer SHA-1: `641b2c9192527952701558a00e53aa1950c528aa`. Delivery/build SHA-256: `48700cbf8bb43a77725dcf15195d9f1a1c55d876a76860177ebd66639dff639c`. Local delivery: `delivery/Photo-Sessions-1.6-debug.apk`. Published in place to the existing shared Drive APK on 2026-10-11 at the owner’s request. Metadata readback confirmed the 1.6 filename, 32,714,317-byte size, unchanged file ID and parent folder, and preserved anyone-with-link reader access: https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view?usp=drivesdk.
- Android scheduling remains inexact. Real 30-minute screen-off/Doze/OEM delivery, reboot during a running fixed timer and hour-long physical-device sessions remain unverified; capture-time boundaries and persistence are covered by controlled tests. Official references: [notification actions](https://developer.android.com/develop/ui/compose/notifications/create-notification) and [WorkManager timing](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

## Version 1.5.1 checks (2026-10-06)

- Published the final APK to the existing Drive file at the owner's request on 2026-10-06. Metadata readback confirmed version 1.5.1 filename, 32,617,857-byte size, the same file ID and folder, and anyone-with-link reader access. Delivery/build SHA-256: 6f3797ae3a39f8a744c8ed2d389158b5fe6bd99ad257d37c34b1bf0d1c14bc49. Earlier statements below describe the pre-publication state.

- Optional-date follow-up: build/lint and all 58 host tests passed. New tests verify blank-date persistence across rename, resume, discovery and database reopen; adding a date back; unchanged capture times/membership; and name-only local/Drive/Photos export renames using existing destination IDs. Invalid nonblank dates remain rejected. Installed the rebuilt 1.5.1 APK in place. On the tablet, Clear produced a name-only preview with Save enabled; navigated back without saving changes to the owner's session. Preview: `validation/optional-date-v1.5.1-tablet.png`. Refreshed delivery APK; shared Drive download remains 1.5.

- Follow-up order change: Google Photos first, Google Drive second, Local last in Upload/Copy. Rebuilt and installed version 1.5.1 in place; tablet UI readback confirmed the three action buttons in that order. Refreshed the delivery APK and export-controls screenshot.

- Follow-up heading-only change: renamed both narrow and wide layouts to Upload/Copy; rebuilt version 1.5.1, refreshed the delivery APK, installed in place, and verified the new heading on the tablet. Earlier screenshots retain the previous wording.

- Build and lint passed; all 56 host tests passed. Added coverage for home Photos counts excluding another account, obsolete album memberships and photo records outside the current catalog.
- Three non-destructive tablet UI instrumentation checks passed: export controls and both network dialogs. Export checks constrain content to phone width, verify compact count wrapping, aligned buttons with 48 dp minimum touch targets at 1.5× text size, distinct callbacks, and hidden sharing until all copies finish. These synthetic callbacks do not upload or share user photos.
- Installed version 1.5.1 / code 9 in place on Galaxy Tab A9. Verified real home counts on one line, bold Upload & Copies, matching destination buttons, and Share copies initially absent. Copied the one-photo Wiring session locally; count changed to 1 in Local and both local links appeared. Open Local displayed the copied photo; tapping it launched Google Photos' local photo viewer. No new cloud upload or share was performed.
- Corrected a single-photo pluralization issue found during tablet inspection. Final build, lint and 56 host tests passed again after the wording/thumbnail update; final APK reinstalled with existing data retained. Signature matches the existing signing key. Local delivery: `delivery/Photo-Sessions-1.5.1-debug.apk`. Shared Drive APK remains 1.5.
- Previews: `validation/home-v1.5.1-tablet.png`, `validation/exports-v1.5.1-tablet.png`, `validation/local-v1.5.1-tablet.png`.

## Version 1.5 checks (2026-10-05)

- Build and lint passed; all 55 host tests passed. Host upload tests cover unchanged byte transfer, persisted album membership, account isolation, skipped completed photos, reuse/expiry of pending upload tokens, recovery after a lost album-create response, album deletion/recreation, rename persistence, cancellation, unavailable files, embedded batchCreate errors and storage/rate-limit retry policy. Photos errors cannot be reported as successful album membership.
- Installed as an in-place update on Galaxy Tab A9 (SM_X115, serial R8YY3075TNA), version 1.5 / code 8, preserving app data. Both Drive and Photos dialog instrumentation checks passed (2 tests): action visibility, reachable scroll positions, minimum touch targets, aligned full-width buttons, and distinct callbacks. Test callbacks do not authorize accounts or upload photos. Preview: `validation/dialog-photos-v1.5-tablet.png` (synthetic long session name).
- The owner enabled Photos Library API in the Photo Sessions Google Cloud project; enabled status was read back on 2026-10-05. Saved and verified five permission declarations: existing drive.file, Photos appendonly/readonly.appcreateddata/edit.appcreateddata and userinfo.email. The owner approved tablet account consent. The Ehra session reported 2 of 2 photos added to Photos, while its existing Drive count remained 2 of 2. Open in Google Photos launched the Photos album activity, and the owner confirmed finding the upload in Albums. External-backup deduplication/album membership, background mobile-data behavior, and original-quality storage effect have not been verified on hardware. Byte uploads restart a whole individual file after interruption; completed album membership and valid upload tokens are retained.
- Local APK: `delivery/Photo-Sessions-1.5-debug.apk`. Uploaded in place to the existing shared Drive file at the owner's request on 2026-10-05. Metadata readback confirms the 1.5 filename, 32,572,001-byte size, same file ID and parent folder, and preserved anyone-with-link reader permission. Delivery APK matches the built APK (SHA-256 6337372736502a1a60a122cc388b3e46f6a68d0739681a48116faded2145008c). Database version remains 4, with new account/session Photos records in the existing state table.


Implementation date: 2026-09-30. Host: Apple Silicon macOS. Scope: the MVP in SPEC.md.

## Version 1.4.2 checks (2026-10-05)

- Build, lint, Android test APK compilation, and all 48 host tests passed. APK signature verification passed with the retained debug signer.
- Upload-dialog layout/callback regression check passed on the Android 16 emulator at phone width (360 dp), 1.5× font scaling, and narrow landscape (approximately 569 × 320 dp at 1.5× text). Checks verify matching button edges, touch targets of at least 48 dp, scroll reachability, and distinct Wi-Fi, any-network, and cancellation callbacks. These checks do not authorize Google or upload photos.
- Existing end-to-end emulator flow passed at 600 dp width, including session creation with keyboard input, delete/Cancel behavior, byte-identical local photo copies, and the upload chooser's Cancel action. Captured and visually inspected the actual Start, Delete, Disconnect, and upload popups. A temporary fake account was used only to show the Disconnect confirmation; it was removed after inspection.
- The existing gallery flow assumes two lazy-grid cards are on-screen and fails that assertion at phone width; the flow was run at its supported tablet width. Phone dialog checks run separately.
- Previews are under `validation/dialog-*-v1.4.2*.png`. No physical phone or tablet was connected for installation or UI testing. Local delivery: `delivery/Photo-Sessions-1.4.2-debug.apk`. Uploaded in place to the existing shared Drive file on 2026-10-05. Metadata readback confirms the 1.4.2 filename, 31,825,956-byte size, same file ID and parent folder, and preserved anyone-with-link reader permission.

## Version 1.4.1 checks (2026-10-04)

- `testDebugUnitTest lintDebug assembleDebug`: passed, 48 host tests. Added regression coverage for parsing storage-quota errors, the user-facing guidance, distinguishing other HTTP 403 failures, and non-JSON server responses.
- Physical Vivo V2250: confirmed Wi-Fi had validated internet access and was unmetered. The app had a connected Google account and a created session folder, but both photos were pending. A diagnostic update captured the server's HTTP 403 storage-quota-exceeded response.
- Installed version 1.4.1 / code 6 using an in-place update without clearing data. No successful photo upload is claimed: the Google account needs free storage. The device disconnected after installation, so the final storage-full screen and stopped retry behavior were not verified on the phone.
- Built `delivery/Photo-Sessions-1.4.1-debug.apk`; verified the APK signature and that the delivery copy matches the build. Uploaded it in place to the existing shared Drive file. Metadata readback confirms the 1.4.1 filename, 31,825,956-byte size, same file ID and parent folder, and preserved anyone-with-link reader permission.

## Version 1.4 checks (2026-10-04)

- Project-local `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: passed. 46 host tests pass; lint has zero errors and eight existing warnings.
- Added migration 3 → 4 preservation tests, start-date persistence across rename/resume/discovery/restart, ISO-date validation, local folder sanitization, export warning coverage, independent destination failures, durable retry state, stored-ID use, and metadata-only deletion/reference cleanup. Prior migrations 1 and 2 continue to pass.
- Final disposable Android 16 emulator flow: passed at 1200 × 1920 / 600 dp. Tests exercise date editing, cover-image deletion without opening the session, warning/cancel for unexported photos, readable-copy checks after external deletion, missing-copy recreation, real MediaStore folder moves by stored copy URIs, unchanged copy/original bytes, copy idempotency, share grants, notifications, start/stop/resume, and discovery replay protection.
- Installed version 1.4 / code 5 as an update on the connected Galaxy Tab A9. Backed up app databases before and after. Compared all preexisting columns: 2 sessions, 6 photo records, and 4 periods were unchanged; preferences and export references were preserved. Schema migrated 3 → 4 and existing label dates were populated. The existing photo availability flags were preserved. No synthetic fixtures or destructive tests were run on the owner's tablet.
- Visually inspected the tablet library with date labels and cover trash buttons; left the updated app open. Inspected the emulator gallery, detail screen and deletion warning. Public validation images use synthetic fixtures: `validation/library-v1.4.png`, `validation/session-v1.4.png`, `validation/delete-warning-v1.4.png`.
- Built local delivery `delivery/Photo-Sessions-1.4-debug.apk`. Uploaded in place to the existing shared Drive file at the owner’s request. Readback confirmed the 1.4 filename, 31,220,118-byte size, original parent folder, same file ID/link, and anyone-with-link reader permission. No authorization publishing change.
- Live Android OAuth authorization and Drive folder PATCH renames were not exercised. Independent rename failures/retries use injected test operations; previous live upload acceptance remains historical. Drive upload records cannot establish whether a user subsequently deleted files remotely while offline.
- Storage rename method follows [Android MediaStore guidance](https://developer.android.com/training/data-storage/shared/media); Drive folder names use [files.update](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/update) against stored IDs. Local virtual folders are moved by updating each tracked app-created copy's `RELATIVE_PATH`; unrelated files remain untouched. Partial moves retain the old stored folder name and retry the same copy URIs until all updates succeed.

## Version 1.3 checks (2026-10-04)

- Owner feedback after installation: app looks good and works on their tablet. This is user-reported acceptance; it does not imply that every individual device/Drive/background scenario was tested. Friend's Android phone test remains pending.

- Project-local `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: passed. All 38 host tests pass; lint reports zero errors and eight existing dependency/tool version warnings.
- Extended the disposable-emulator flow test to cover session filters, actual loaded thumbnails, tapping a photo (intercepted image-viewer intent), the copy button in the new upload/copy panel, and deletion through Session options. Existing checks still cover naming, skip, activity recreation, start/stop/resume, rename, byte-identical copies, sharing grants, and source preservation.
- Android 16 / API 36 Pixel Tablet emulator: flow passed at 2560 × 1600 (1280 dp wide, persistent transfer side panel) and 1200 × 1920 (600 dp wide, expandable transfer panel). Synthetic solid-color JPEGs were used. The initial cold boot showed an emulator System UI not-responding dialog; dismissed it and repeated visual checks after the emulator settled.
- Physical Galaxy Tab A9 (SM-X115): installed 1.3 / code 4 as an update over 1.2. Compared local catalog tables and persistent preferences before/after: unchanged, excluding the expected scan timestamp / ID bookkeeping. The device had no sessions at the start. No test fixtures or test session were added to the owner's tablet.
- Visually inspected Home and Settings on the physical tablet, including the corrected system-bar contrast. Session grids and transfer controls were inspected with fixtures on the emulator. Thumbnails now retain the loaded bitmap while background scans refresh them, avoiding blank flashes.
- Screenshots: `validation/home-v1.3-tablet.png`, `validation/library-v1.3.png`, `validation/library-v1.3-portrait.png`, `validation/session-v1.3-landscape.png`, and `validation/session-v1.3-portrait.png`.
- Delivered local APK: `delivery/Photo-Sessions-1.3-debug.apk`; uploaded in place to the existing shared Drive file and read back the 1.3 filename / 31,120,546-byte size. Database remains version 3; no migration or core session/copy/Drive algorithm changes.
- Not reverified in this UI pass: live Google account consent/uploads, mobile-data constraints, background timing, or very large libraries. Drive progress/controls are retained; existing 1.2 device-upload results remain historical.

## Version 1.2 checks (2026-10-01)

- `testDebugUnitTest lintDebug assembleDebug` on the Mac with the project-local toolchain: passed. 38 host tests (15 grouping, 13 database, 2 notification, 4 Drive query/name helpers, 4 Drive state persistence including the version 2 to 3 migration). Lint: zero errors.
- Installed and launched on the Android 16 / API 36 Pixel Tablet emulator. That image has no Google Play services, so Drive sign-in and upload were not tested there.
- Installed as an update over 1.1 on the owner's Galaxy Tab A9 (SM-X115, Android 16) with `adb install -r`; existing sessions preserved. Earlier attempts failed with "not enough space" until storage was freed (about 0.5 GB free before, about 10 GB after).
- Owner-reported on the tablet: Google account consent succeeded after adding the account as a test user; Drive shows My Drive / Photo Sessions with a subfolder per session containing the photos.
- Not yet verified: Wi-Fi-only waiting on mobile data, upload interruption and resume, reconnect after the 7-day testing expiry, very large sessions, and deleting a Drive folder then re-uploading.

## User testing update — 2026-10-01

The user reports that the app is now working reliably on their Galaxy Tab A9 running Android 16. The earlier installation issue is no longer an active blocker. This is user-reported testing feedback; individual physical-device acceptance checks were not enumerated. The original validation record below describes checks performed during the initial implementation. Subsequent changes and testing feedback are tracked in [CHANGELOG.md](CHANGELOG.md).

## Version 1.1 checks — 2026-10-01

- Final combined `testDebugUnitTest assembleDebug lintDebug connectedDebugAndroidTest`: passed.
- 30 host tests passed (15 grouping, 13 database, 2 notification), including version-1 database migration, manual start/stop/resume and timeout, rename/copy-state persistence, and deletion replay protection.
- Lint: zero errors, eight dependency-version warnings.
- Extended end-to-end test passed on a fresh Android 16 / API 36 ARM64 Pixel Tablet emulator: existing discovery/notification flow, byte-identical MediaStore copies in named folders, repeated-copy idempotency, copied-file exclusion from discovery, sharing intent with read grants, rename, delete retaining originals/copies, and Compose start/stop/resume/rename/delete controls.
- Initial emulator attempt ran before the emulator was online. A later test run exposed a fixture-cleanup URI alias mismatch (external vs external_primary); the harness now removes the already-deleted fixture by its media ID. Final run passed.
- Installed original 1.0 APK, then installed 1.1 over it with `adb install -r`: both succeeded. Updated app cold-launched successfully. Catalog migration preservation is covered by the seeded legacy-database test above.
- Refreshed home screen visually inspected on the Android 16 tablet emulator; screenshot: validation/home-v1.1.png.
- APK signature verification passed (v2 debug signer); version 1.1 / code 2, min SDK 29, target SDK 37.
- Not yet verified: this new build on the user's Galaxy Tab A9, real 15-minute screen-off timing, low-storage copy failures on hardware, or an actual Google Drive upload. Sharing-intent construction is verified; Drive is not installed/signed in on the test emulator.
- Original version 1.0 results below are historical. The original no-copy scope and 30-minute timeout are superseded by the user's version 1.1 requests in CHANGELOG.md.

## Completed host checks (version 1.0)

- Downloaded a project-local JDK 17 and Android SDK; built with the checked-in Gradle 9.3.1 wrapper and AGP 9.1.1.
- Verified the wrapper JAR against Gradle's published SHA-256 and pinned the Gradle distribution checksum.
- `testDebugUnitTest`: **25 tests passed**, zero failures or skipped tests. 15 pure grouping/discovery-rule tests, 8 SQLite/Robolectric persistence tests, 2 notification/Robolectric tests.
- `assembleDebug`: **passed**, producing `app/build/outputs/apk/debug/app-debug.apk`.
- `assembleDebugAndroidTest`: **passed**, producing the separate instrumentation APK.
- `lintDebug`: **passed: 0 errors, 8 warnings**. All eight warnings concern newer available Gradle/library versions; the project intentionally pins its tested versions. No lint errors were suppressed or baselined.
- APK signature validation: **passed**, Android v2 signature, debug signer.
- APK metadata: package `com.example.photosessions`, version `1.0` / code `1`, minimum SDK 29, target/compile SDK 37.
- Manifest inspection: read-only media permissions, notification permission, WorkManager scheduling permissions/receivers; no Internet or media write/delete permission. Backup and app-data transfer are disabled.
- Source review: production code performs MediaStore queries and thumbnail reads only. Media creation/deletion occurs solely in the instrumentation test, restricted to synthetic fixtures created by that test.

The SQLite tests verify that a deliberately aborted insertion rolls back session changes and scan progress together. They also close and reopen the database to check baseline, membership, naming/skip state, notification claims, and preferences. Notification tests check distinct stable session routes and cancellation; these are host simulations, not evidence of device delivery timing.

Reports after a build:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/lint-results-debug.html`
- `app/build/test-results/testDebugUnitTest/`

## Emulator check

**Passed** `connectedDebugAndroidTest` on an Android 14 / API 34 ARM64 AOSP emulator (Pixel 6 device profile). One end-to-end test passed, with zero failures/skips. The app and instrumentation APK installed and launched successfully. The final combined instrumentation/lint run completed successfully.

Verified on the emulator:

- Five real MediaStore JPEG fixtures, with UTC EXIF capture dates, produced exactly two sessions containing the three eligible new camera photos; the historical camera image and screenshot-folder image were excluded.
- Two real Android notifications had distinct routes. Their PendingIntents opened the correct naming screens while the app was running.
- Compose Save trimmed and persisted `Garage wiring`; Activity recreation retained the displayed session/name. Skip retained the other session unnamed without another alert.
- Repeated production scans kept exactly three memberships and produced no further notifications.
- SHA-256 checksums of all five fixture JPEGs were unchanged after discovery and naming.
- Deleting one test-owned fixture externally retained its catalog reference and marked it unavailable.
- The named session screen was visually inspected; the date range, count, name, and two loaded thumbnails were visible without clipping. [Captured session screen](validation/session-screen.png) uses solid-color test JPEGs, not personal photos.

The harness was corrected during validation to embed explicit UTC EXIF timestamps, to track activity recreation after notification intents, and to avoid deleting an already deleted fixture twice. These were test setup/cleanup corrections; the final production-code checks above passed. No real 30-minute wait or background-delivery measurement was performed.

Instrumentation report: `app/build/reports/androidTests/connected/debug/index.html`.

`OrganizerFlowTest` is designed for a **fresh disposable test installation** and refuses to overwrite an existing monitoring baseline. It creates its own colored JPEG fixtures in MediaStore, includes historical/non-camera exclusion cases, runs production discovery, checks notifications and their pending intents, saves and skips through Compose, recreates the Activity, repeats scans, checks source JPEG checksums, and tests external deletion of one fixture. It deletes only its own fixture media during cleanup. It does not wait 30 real minutes: synthetic capture times exercise production completion logic without changing the timeout.

To run on a clean emulator:

```sh
./gradlew connectedDebugAndroidTest
```

The test consumes that installation's empty baseline, so use a fresh emulator or reinstall the test app before repeating. Do not clear an everyday user's app data to run it.

## Physical device checks not performed

No physical Android device was connected (`adb devices -l` returned no devices before emulator setup). Therefore these acceptance items remain unverified on a phone:

- Install and launch on a physical device, including manufacturer-specific Camera folder detection.
- Actual camera capture and delayed media indexing on each intended phone.
- Interactive denial, partial access, permission revocation/reselection and OEM settings behavior.
- Thirty-minute idle delivery with the screen off, Doze, battery saver, restricted background mode, and OEM power management.
- Restart, reboot, explicit force-stop and automatic catch-up under those phone conditions.
- Notifications tapped from the real notification shade after process death, multiple simultaneous prompts, and channel/permission changes.
- Removable storage and external gallery deletion/moves on a phone.
- Large font, TalkBack, rotation across all screens, and larger form factors.

The README contains a step-by-step physical-device procedure. The project is build- and emulator-verified, but **physical-device acceptance criteria 1 and 12 are not yet signed off**. No measured phone/background behavior is claimed.

## Known limits carried into review

- Only `DCIM/Camera` and descendants are classified as camera images; there is no universal MediaStore camera-origin flag.
- Selected-photo access cannot give full automatic coverage of future photos.
- WorkManager delivery is best-effort and inexact. Force-stop prevents normal background execution until the app is opened again.
- Prompts use an at-most-once durable reservation. A crash between reservation and Android posting may lose an alert; it will not repeat it. The session remains in the app.
- Very late photos can extend a stable session but cannot merge two already committed sessions. This matches the deferred automatic-merge scope; arbitrary late bridges may differ from an initial all-at-once grouping.
- The MVP scans metadata since baseline and loads the catalog into memory. It is not optimized for years of high-volume shooting.
- Capture-time fallback, inaccurate clocks, and MediaStore row replacement have the limits described in README.md.
