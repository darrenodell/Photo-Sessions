# Version 1.2 testing update, October 1, 2026

Adds direct Google Drive upload. See [CHANGELOG.md](CHANGELOG.md) for details and [VALIDATION.md](VALIDATION.md) for checks performed.

- **Upload to Google Drive:** open a session, tap Upload to Google Drive, then choose Wait for Wi-Fi or Upload now. The first time, pick a Google account and allow access. Photos go to My Drive / Photo Sessions / <session name>. Only photos not yet uploaded are sent; retries never duplicate.
- **Progress:** the session screen shows X of N uploaded and a progress notification appears while uploading. Open in Drive opens the session's Drive folder. Cancel upload stops it; uploaded photos stay in Drive.
- **Settings:** shows the connected Google account; Disconnect stops pending uploads and forgets the account. Files already in Drive are kept.
- **Google Cloud setup (owner):** project "Photo Sessions" with Drive API enabled, OAuth audience External, scope `drive.file`, and an Android OAuth client for package `com.example.photosessions` with the debug signing SHA-1 `64:1B:2C:91:92:52:79:52:70:15:58:A0:0E:53:AA:19:50:C5:28:AA`. While in Testing, every account must be listed under Audience > Test users, and Google asks people to reconnect after 7 days. Builds signed with a different key need their SHA-1 added as another Android client.
- **Install:** `delivery/Photo-Sessions-1.2-debug.apk`, as an update over 1.1. The device needs free storage (at least 1 to 2 GB is a safe margin).

# Version 1.1 testing update — October 1, 2026

This update supersedes the original MVP behavior documented below. See [CHANGELOG.md](CHANGELOG.md) for the requested changes and [VALIDATION.md](VALIDATION.md) for checks performed.

- **Start a session:** name it immediately, then use your normal camera. Allow all-photo access for discovery.
- **Stop / Resume:** stop manually or let the session complete after 15 minutes without a photo. Resume adds future photos to the same session. Starting/resuming completes any other active session. Automatic grouping continues for photos outside manual sessions.
- **Rename:** available on every session. Existing membership stays intact.
- **Copy pending photos:** makes separate photo files under `Pictures/Photo Sessions/<name> (<session ID>)/`. Invalid folder-name characters are replaced and long names shortened. Originals stay in Camera. Identical session names use distinct folders. A folder created before renaming is retained and its path is displayed in the app.
- **Upload to Drive:** copy first, tap Upload to Drive, choose Drive in Android sharing, select the destination, and confirm there. The app does not sign in to Google or claim upload success; Drive owns upload progress and retries. Drive must be installed and signed in to appear as a target.
- **Delete session:** confirmation removes the catalog entry, retaining camera originals and copies. Existing member photos will not recreate the deleted session on the next scan.

Install the new APK as an **update**, without uninstalling the old app or clearing its data. Version 1.1 includes a database migration preserving names, membership, permissions-independent preferences, and monitoring baseline. The debug signing identity is retained.

Copy status is refreshed when Copy pending photos runs; externally removed copies can still display as copied until checked. Interrupted copies are retried on the next copy action. Large sessions may take time and require free internal storage. Sharing many photos can exceed limits of the receiving app; this version does not batch uploads. No actual Google Drive account upload or real 15-minute background timing is claimed by automated checks.

## Original version 1.0 documentation (historical)

# Photo Sessions

A local Android app that groups newly taken camera photos into 30-minute sessions and offers one reminder to name each completed session. The catalog contains photo references, not copies. The app never writes to, deletes, renames, or moves MediaStore photos.

## Open, build, and install

1. Open this folder in a current stable **Android Studio with AGP 9.1 support or newer**.
2. Use **JDK 17** for Gradle. Install **Android SDK Platform 37.0**, **Build Tools 36.0.0**, and **Platform Tools** using SDK Manager. The command-line package name is `platforms;android-37.0`.
3. Let Gradle sync, connect an Android 10+ phone with USB debugging enabled, select the `app` configuration and device, and press **Run**.
4. Alternatively, with `JAVA_HOME` and `ANDROID_HOME` set to your installations:

   ```sh
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   adb devices
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   adb shell am start -n com.example.photosessions/.MainActivity
   ```

The Gradle wrapper is included. Dependency downloads require internet during the build; the installed app declares no internet permission. Android Studio may create an untracked `local.properties` with `sdk.dir`. Do not commit that machine-specific file. Debug APKs are for testing; distribution signing is outside this MVP.

### Pinned toolchain

| Component | Version |
| --- | --- |
| Minimum Android | Android 10 / API 29 |
| Compile / target Android | Android 17 / API 37 |
| Android Gradle plugin | 9.1.1 |
| Gradle wrapper | 9.3.1 |
| Java | 17 |
| Kotlin / Compose compiler | 2.2.10 (AGP built-in Kotlin) |
| Compose BOM | 2025.08.01 |
| Activity Compose | 1.10.1 |
| Lifecycle | 2.9.3 |
| WorkManager | 2.10.3 |
| AndroidX Core | 1.16.0 |
| JUnit / Robolectric | 4.13.2 / 4.16.1 |

Use the checked-in versions for reproducibility. A full JDK and SDK were downloaded into ignored `.tools/` for local validation; they are not required project source. On the implementation machine, `.tools/build-local.sh` supplies those local paths and keeps caches under the project.

## First use

Tap **Start monitoring**. That moment becomes the persistent baseline, before the photo permission dialog opens. Existing photos are excluded. Grant **all photos** for automatic discovery. On Android 14+, **selected photos** access is supported, but the app only sees selected items; future camera photos will not automatically become accessible. Home clearly reports limited or missing access. Use **Review photo access** to reselect or Android app settings after a permanent denial.

Notification permission is requested separately and voluntarily, with an explanation. Denying it does not stop grouping. Home, session detail, and **Name this session** remain available. In Settings, the app's notification preference and Android's permission/channel status are displayed separately. Turning the preference off removes this app's outstanding notifications and stops new alerts. Completed sessions that have never been prompted can notify when permission and the preference are enabled again. Already prompted or skipped sessions do not notify again.

Take photos with the existing Camera app. **Check now** requests an immediate scan. Once a session completes, tap its notification to enter a name, or open the session from Home. **Save** trims surrounding whitespace and requires a nonempty name (up to 200 characters). Duplicate names are allowed. **Skip** keeps it unnamed and suppresses further prompts; it can be named later. Back leaves the current state unchanged. The timeout is fixed at 30 minutes for this MVP.

## How it works

- **MediaDiscovery** reads MediaStore image metadata from each mounted external volume. It ignores pending and trashed rows. No video access or write permissions are requested.
- **SessionRules** is pure Kotlin. New references are sorted by capture time, then identity. Gaps strictly below 30 minutes extend a session; gaps at or above 30 minutes split. A single photo is a session; midnight has no special meaning. Completion is based on the last capture time and a clock passed into reconciliation.
- **CatalogDb** uses Android's standard SQLiteOpenHelper, foreign keys, unique photo identity, and a transaction for session/reference/progress updates. It stores the baseline, last successful scan, notification preference, stable numeric session IDs, names, completion/prompt states, and media references. No image bytes are stored in the catalog.
- **Repository / ScanWorker** serialize scans and naming changes. A scan reconciles accessible photos before completing sessions or posting prompts. Failed provider queries commit nothing and retry with WorkManager backoff. Missing permission pauses discovery without deleting metadata.
- **WorkManager** persists a unique 15-minute periodic scan, immediate work on foreground/resume/media changes, and delayed work for an active session's deadline. Each deadline worker scans again before finalizing. Foreground polling every 30 seconds covers coalesced content observer events. Scheduling is inexact.
- **Prompts** uses one stable notification tag and a distinct immutable PendingIntent URI per session. `singleTop` plus `onNewIntent` handles both cold starts and an already open app. Notification permission and channel enablement are checked before claiming a prompt.
- **Compose** provides Home, Session, Name Session, and Settings. Thumbnails are loaded at bounded size off the main thread using `ContentResolver.loadThumbnail`; unavailable references get placeholders. No full-resolution files are copied or cached by the app.

### Camera and timestamp assumptions

A camera image has a case-insensitive relative path equal to `DCIM/Camera/` or a subfolder beneath it. This excludes ordinary screenshots, downloads, and messaging folders. Cameras that save elsewhere are not covered in this MVP. Conversely, an image copied into that camera folder can be included if its effective timestamp is after setup. MediaStore has no universal, reliable “taken by a camera” flag.

`DATE_TAKEN` is used when positive and not in the future relative to the scan. Otherwise the app uses valid `DATE_ADDED` seconds converted to milliseconds. If neither is valid, it skips the item until a later scan can read valid metadata. The effective timestamp must be at or after the baseline. The one-second resolution of the fallback may exclude a photo with no capture date inserted in the same second as setup. Clock changes and incorrect EXIF dates can affect grouping.

Every scan re-reads eligible metadata since setup rather than trusting an increasing row ID or timestamp watermark. This recovers delayed indexing, newly mounted storage, and newly granted access. The last-scan value is diagnostic, not an exclusion cutoff. Unique identity is **volume + MediaStore ID + insertion timestamp**, guarding against ordinary row-ID reuse. A photo's committed effective time and session membership remain stable. Restoring or reimporting a photo as a different MediaStore row may create a new reference.

A newly available photo can extend an existing session, including before its start. If it bridges two already committed sessions, it joins the nearest compatible session (earlier start breaks a tie), preserving both IDs and prior memberships. Automatically combining adjacent sessions is explicitly deferred in SPEC.md. Thus discovery in one delayed batch is grouped fully by time, while arbitrarily late bridge photos cannot retroactively merge previously visible/named groups. Late additions can change counts and completion status but do not reset prompt state or rename a session. An already posted notification retains its original count.

### Reliability and privacy limits

Android controls execution. Periodic work's 15-minute interval is a scheduling request, not a delivery promise. Doze, battery saver, OEM restrictions, job quotas, and storage/provider delays can postpone checks and alerts. The 30-minute timeout determines membership, not an exact alert time. No foreground service, exact alarm, or battery-optimization exemption is used.

WorkManager persists through ordinary process death and reboot. A force-stopped app cannot continue normal background discovery until the user opens it again. It then catches up from the baseline for photos still present and accessible. Uninstalling or clearing app data loses the catalog and creates a new baseline on setup. Photos remain untouched. Cloud backup and device transfer of app data are disabled so media references and names remain local.

Prompt state is **at most once**: the database durably reserves a prompt before posting it. SQLite and Android's notification service cannot share an atomic transaction. A crash or permission change in that small interval can lose an alert, but cannot generate a repeated naming prompt on recovery. The session remains available in the app. Notification dismissal also does not cause another alert.

When a photo is no longer returned by a successful scan, its reference is retained but marked unavailable. This can mean deletion, revoked selection, offline storage, or a moved image. A failed query does not mark the whole catalog missing. Thumbnail loading independently handles unavailable media and permission changes. Extremely unusual MediaStore identity reuse with an identical insertion timestamp cannot be distinguished without reading additional file identity information.

This baseline-rescan approach deliberately favors correctness and simplicity over large-library scalability. Metadata and the local catalog are loaded in memory and catalog rows are reconciled together. Very large post-setup libraries would benefit from incremental generation tracking and paging; those optimizations are deferred. This is an English-language MVP with Material accessibility semantics and scalable layouts, not a localized release.

## Tests and physical-device procedure

Run `./gradlew testDebugUnitTest lintDebug assembleDebug`. Unit tests cover strict boundaries, deadline extension, single photos, midnight, unsorted delayed batches, duplicate/restart recovery, stable IDs, late indexing, late bridges, unavailable/recovered references, volume identity, row-ID reuse, path filtering, and timestamp fallback. Robolectric tests exercise actual SQLite persistence, unique membership, durable claims, skip/name state, preference persistence, and foreign keys.

Before accepting the MVP for everyday use, record phone model, Android version, Camera app, its save folder, and battery mode. Keep the production timeout at **30 minutes**.

1. **Setup / history:** take a photo before setup; start monitoring and grant all photos; take another. Check now. Only the new photo should appear, and the original must be unchanged in the system gallery.
2. **Permissions:** repeat with denied photo permission, then restore it. On Android 14+, select a subset and verify the limited-access message and incomplete coverage. Revoke access from system settings while backgrounded, reopen, and confirm no crash and an actionable message. Reselect the photos and confirm references recover.
3. **Grouping:** take photos within 30 minutes; confirm one session. Take another after at least 30 minutes; confirm a second session. Use synthetic unit tests for precise millisecond boundaries rather than trying to time the camera manually. Check a session spanning midnight.
4. **Completion / notification:** after at least 30 minutes without a photo, allow Android to schedule work or use Check now. Expect one notification per completed session. Tap it with the app running and after swiping the app away; verify the correct session, count, times, and thumbnail. With multiple completed sessions, tap each notification and confirm distinct routing.
5. **Naming:** Save `  Garage wiring  ` and reopen the app: expect `Garage wiring`. Blank Save must be disabled. Skip another session, rescan and restart: it stays unnamed without another reminder; name it later from its detail screen.
6. **Disabled notifications:** deny the Android permission, disable the channel, and disable the app preference in separate trials. Grouping must continue. Turn notifications back on: pending sessions may prompt; claimed/skipped/named sessions must not repeat.
7. **Ordinary process death / catch-up:** swipe away the app or use `adb shell am kill com.example.photosessions` while backgrounded. Take photos across two time-separated sessions. Reopen and verify correct groups, stable counts, and no duplicates after repeated Check now.
8. **Reboot:** reboot after starting an active session, take more photos, and wait for work to run. Confirm discovery resumes without manually recreating sessions. Record actual delivery delay; unlock after reboot as normal.
9. **Force-stop:** explicitly force-stop through system settings, take photos, and confirm there is no promise of background alerts. Open the app and verify catch-up without duplication. This is expected Android behavior, not continuous monitoring.
10. **Battery restrictions:** test default mode, battery saver, and the phone's restricted-background mode with the screen off. Record observed delays separately. Do not infer reliable background delivery from foreground tests.
11. **External changes:** delete a test photo with the system gallery, move another out of DCIM/Camera, and unmount removable storage if present. Rescan: metadata remains and unavailable cells do not crash. Restore access/storage and verify recovery where the original identity remains.
12. **Preservation:** compare original file names, paths, size, and optional checksums before/after discovery and naming. The organizer should cause no changes. Rotate all screens and test a larger font size, keyboard visibility, Back, and an unavailable-thumbnail case.

See [VALIDATION.md](VALIDATION.md) for checks actually executed and limitations of this environment. Physical-device acceptance is not implied by a successful host build.

## Official references checked during implementation

- [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk) and [Android 17 release announcement](https://developer.android.com/blog/posts/android-17-is-here).
- [AGP 9.1 compatibility](https://developer.android.com/build/releases/agp-9-1-0-release-notes): API 37 support, Gradle 9.3.1, JDK 17 and built-in Kotlin 2.2.10.
- [Shared media access](https://developer.android.com/training/data-storage/shared/media): MediaStore, volumes, permissions, metadata queries.
- [Selected Photos Access](https://developer.android.com/about/versions/14/changes/partial-photo-video-access): full/partial/denied states and reselection.
- [Notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission).
- [Persistent background work](https://developer.android.com/develop/background-work/background-tasks/persistent) and [work request timing](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

## Making changes

Keep grouping rules in `SessionRules.kt` independent of Android APIs, and run the host tests and lint after changes. Database schema changes require an explicit migration in `CatalogDb`; do not replace the catalog destructively. Keep all production MediaStore operations read-only. Run `connectedDebugAndroidTest` only on a clean disposable emulator; the test creates and removes its own synthetic media. Update the validation report when behavior or tested environments change.

No cloud sync, AI naming, real folders/albums, voice, export, sharing, search, or configurable timeout has been added.
