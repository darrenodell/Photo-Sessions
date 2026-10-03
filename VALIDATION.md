# Validation report

Implementation date: 2026-09-30. Host: Apple Silicon macOS. Scope: the MVP in SPEC.md.

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
