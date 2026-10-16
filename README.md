# Photo Sessions

A native Android app that groups new camera photos and screenshots into named sessions. It supports automatic grouping, fixed-duration manual sessions, local copies and sharing, plus optional uploads to Google Drive and Google Photos. Original media files stay untouched.

## Build from source

1. Clone this repository and open it in Android Studio:

   ```sh
   git clone https://github.com/darrenodell/Photo-Sessions.git
   cd Photo-Sessions
   ```

2. Use JDK 17 and install Android SDK Platform 37 (`platforms;android-37.0`), Build Tools 36.0.0 and Platform Tools. Android Studio can configure your local SDK path in `local.properties`; that file stays out of Git.
3. Build and run the host checks from Android Studio, or with `JAVA_HOME` and `ANDROID_HOME` pointing to your installations:

   ```sh
   ./gradlew testDebugUnitTest lintDebug assembleDebug
   ```

4. Install `app/build/outputs/apk/debug/app-debug.apk` on an Android 10 or newer device. Grant full photo access for automatic discovery. Notifications and Google account connections are optional.

The Gradle wrapper and pinned dependency versions are included. The local `.tools/` directory, SDK/JDK installations, device databases, credentials, signing keys and built APKs are excluded. Building does not require the original developer's `.tools/build-local.sh` script.

### Google integrations and signing

A fresh development machine normally creates a different debug signing key. Google Drive and Google Photos authorization require an Android OAuth client matching the package `com.example.photosessions` and the certificate signing that APK. Configure your own Google Cloud project/client and enable the required APIs/scopes; [DRIVE-SETUP.md](DRIVE-SETUP.md) records the original installation's setup, and the version 1.5 notes below list the Photos scopes. Local grouping and copying do not require Google Cloud configuration.

Signing keys and user tokens are not included in this repository. Updating an existing installed app requires its matching signing key; keep that key private. The owner's downloadable APK retains the existing signer and is distributed through the Drive link below.

## Current release: 1.7 — Screenshots — October 16, 2026

New screenshots join camera photos in the same sessions. Supported screenshot folders are `Pictures/Screenshots/` and `DCIM/Screenshots/`, including their subfolders on accessible media volumes. Screenshot discovery starts when this version first runs; existing screenshots are not imported. Custom screenshot save locations outside those folders are not automatically included.

Screenshots start and extend automatic sessions using the same 30-minute inactivity rule. Manual sessions retain their fixed deadline. Original files stay untouched; local copies, sharing and uploads preserve the image bytes and file type. No additional Android permission or database migration is required. Android's existing background scheduling and limited-photo-access constraints still apply.

Local APK: `delivery/Photo-Sessions-1.7-debug.apk`. Installed and tested on the owner's Galaxy Tab A9, then uploaded to the [unchanged Drive download](https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view?usp=drivesdk). See [VALIDATION.md](VALIDATION.md) for verification and delivery details.

# Version 1.6.3 — Photo captions — October 12, 2026

Photos that have not been copied locally no longer show a “Not copied” caption. Local APK: `delivery/Photo-Sessions-1.6.3-debug.apk`. Installed on the owner’s tablet and available at the [unchanged Drive download](https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view).

# Version 1.6.2 — Google Photos album opening — October 12, 2026

**Open in Google Photos** becomes available after photos have been added and the current upload has finished. This prevents opening a newly created album before its first upload completes. All duration choices from 1.6.1 are retained.

Local APK: `delivery/Photo-Sessions-1.6.2-debug.apk`. Installed on the owner’s tablet and published to the [existing Drive download](https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view).

# Version 1.6.1 — Simple duration choices — October 11, 2026

Manual sessions and automatic-session extensions now offer **30 minutes**, **1 hour**, and **2 hours**. The same choices appear in the session-ending notification. **Custom hours** accepts whole numbers from 1 to 168; decimals are rejected. Use the dedicated 30-minute button for half an hour.

Timers still run from when you tap; automatic discovery still completes after 30 minutes without a photo. Existing sessions and running timers are preserved. Local APK: `delivery/Photo-Sessions-1.6.1-debug.apk`. Installed and verified on the owner’s tablet and published to the [existing Drive download](https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view) on October 12, 2026. Earlier release notes below describe historical versions.

# Version 1.6 — Session timers — October 11, 2026

Automatic sessions now end after **30 minutes without a photo**. Their notification offers **+1 hour** and **+2 hours**; tap the notification for naming and a custom-hours field. You can continue an unnamed session without naming it first.

**Start session** now asks for the session name and length: **1 hour**, **2 hours**, or custom hours such as **1.5**. Manual and resumed sessions end at a fixed time measured from the action. Taking more photos does not reset the timer, and quiet gaps do not end it early. The session screen shows when it ends. You can still stop it manually.

At each timer deadline, a reminder offers another extension, including for named sessions. Resuming keeps the same session ID, name, date and photos; any other active session is completed. New photos taken after the deadline are discovered automatically in a separate session. Custom durations range from one minute to 168 hours, rounded to the nearest minute.

Version 1.6 / code 10 uses database version 5 with a non-destructive migration for persistent deadlines. Install as an update to retain existing data. Legacy manual periods without a fixed deadline retain inactivity-based completion until resumed. Android may delay background reminders; capture-time membership still uses the stored deadline. See [VALIDATION.md](VALIDATION.md) for checks and limitations. Local APK: `delivery/Photo-Sessions-1.6-debug.apk`. [Download 1.6 from Google Drive](https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view?usp=drivesdk); replaced the existing shared APK on October 11, preserving its link and sharing settings. Earlier release notes below describe historical versions.

# Version 1.5.1 — Consistent copy and upload controls — October 6, 2026

Home cards show compact Local, Drive and Photos counts, wrapping whole labels only when needed. **Upload/Copy** is bold. The sections appear in this order: Google Photos, Google Drive, then Local. Add to Google Photos, Upload to Google Drive and Copy Locally use matching full-width buttons. Local copies have their own Open Local gallery; tapping a thumbnail opens the copy in the device's image viewer. Share copies stays in the Local section and appears only when every photo has a completed copy. Single-photo labels and sharing error messages are corrected.

The date is optional when naming or renaming a session. Tap Clear beside the date, or delete it, to use the name alone. Existing exported folder and album names follow the label; original capture times are retained.

Installed on the owner's Galaxy Tab A9 as an in-place update. Local APK: `delivery/Photo-Sessions-1.5.1-debug.apk`, version code 9. [Download 1.5.1 from Google Drive](https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view); the existing link and sharing settings are preserved. See [VALIDATION.md](VALIDATION.md).

# Version 1.5 — Google Photos albums — October 5, 2026

Open a session's **Upload & copies** panel and choose **Add to Google Photos**. Each session gets an app-created album named with its date and session name. Choose Wait for Wi-Fi or Upload now, approve Photos access, and follow progress in the panel. Cancel, retry and Open in Google Photos are available independently of Drive. Settings has separate Drive and Photos connection controls. Renaming a session also updates its exported Photos album; deleting a session keeps exported photos and albums.

Photos are sent at original quality. Google documents that identical uploaded bytes return the same media item ID, but album membership for photos previously backed up outside this app still requires live device verification. This integration cannot browse or reorganize the rest of a person's Google Photos library. Device originals and copies stay untouched. Photos-only export can use a camera original without first making a device copy.

Owner setup: enable the [Google Photos Library API](https://console.cloud.google.com/apis/library/photoslibrary.googleapis.com?project=cohesive-apogee-510314-j3) in the existing Photo Sessions Cloud project. In Google Auth Platform > Data Access, retain `drive.file` and add `photoslibrary.appendonly`, `photoslibrary.readonly.appcreateddata`, `photoslibrary.edit.appcreateddata` (all under `https://www.googleapis.com/auth/`) plus `userinfo.email`. Keep the existing Android OAuth client/signing key and test-user audience. Each user must consent to the added permissions. The owner enabled the Photos Library API and the five permission declarations were saved on 2026-10-05. Tablet consent succeeded; the app reported 2 of 2 photos added for the Ehra session, and the owner confirmed finding the upload in Google Photos Albums.

Local APK: `delivery/Photo-Sessions-1.5-debug.apk`, version code 8. Installed on the connected Galaxy Tab A9 as an in-place update. [Download 1.5 from Google Drive](https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view); the existing download link and sharing settings are preserved. See [VALIDATION.md](VALIDATION.md).

References: [Photos uploads](https://developers.google.com/photos/library/guides/upload-media), [album management](https://developers.google.com/photos/library/guides/manage-albums), [Photos permissions](https://developers.google.com/photos/overview/authorization).

# Version 1.4.2 — consistent phone popups — October 5, 2026

The upload Wi-Fi/data chooser, Start session, Delete session, and Disconnect Drive popups now share full-width, vertically stacked buttons with left-aligned labels. The upload chooser uses a short **Upload now** label with **Use Wi-Fi or mobile data** underneath. Dialogs scroll when space is limited, including with larger text or the keyboard open. Delete remains clearly marked in red, and Settings displays the installed version.

Local APK: `delivery/Photo-Sessions-1.4.2-debug.apk`, version code 7. See [VALIDATION.md](VALIDATION.md) for emulator checks and previews. [Download 1.4.2 from Google Drive](https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view); the existing download link and sharing settings are preserved.

# Version 1.4.1 — Drive upload error reporting — October 4, 2026

Google Drive storage-full errors now explain that space must be freed in the connected Google account. These errors stop the upload rather than repeatedly retrying while displaying a connection wait. Other upload failures retain automatic retries with a visible error message. Existing sessions and photos are preserved; the database schema remains version 4.

Local APK: `delivery/Photo-Sessions-1.4.1-debug.apk`, version code 6. Installed on the connected Vivo V2250 during troubleshooting. [Download 1.4.1 from Google Drive](https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view); the existing download link and sharing settings are preserved.

# Version 1.4 — dates, folder renames and cover deletion — October 4, 2026

- Sessions display `YYYY-MM-DD — Name`. The date defaults to the session start, stays fixed when renaming or resuming, and can be edited in **Rename → Session date**. Editing the label date does not change photo membership or capture times.
- Renaming saves the session first, then updates device copies by stored MediaStore URIs and Drive folders by stored folder IDs, including folders from previously connected accounts. Device folders keep a session-ID suffix to distinguish identical names. Only tracked app-created copies move; unrelated files are untouched.
- Each destination succeeds or fails independently. **Upload & copies** shows any older folder names and **Retry folder names**. Pending rename errors survive restarting the app. Drive must be authorized for the folder's original Google account; reconnect that account through Upload when needed.
- Tap the trash button in the upper-right corner of a session cover to delete without opening it. Confirmation always explains that originals, copies and Drive folders remain. Photos with neither a readable device copy nor a recorded successful Drive upload trigger an additional warning. Drive coverage uses the last confirmed upload; remotely deleted Drive files cannot be detected offline.
- Session deletion removes catalog/export references and cancels its upload. Discovery tombstones prevent the same photos from recreating the deleted session. No camera originals or exports are deleted.
- APK: `delivery/Photo-Sessions-1.4-debug.apk`, version code 5. Database version 4 migrates existing session dates from their stored start times while preserving photos, exports and preferences. The shared Drive download now contains 1.4 at the same file ID and link.

See [VALIDATION.md](VALIDATION.md) for checks and limitations. Earlier release notes below describe historical versions.

# Version 1.3 gallery refresh — October 4, 2026

A gallery-style interface inspired by the album organization patterns in [Samsung Gallery](https://www.samsung.com/in/support/mobile-devices/how-to-create-albums-in-gallery-app-on-galaxy-device/) and [Google Photos](https://www.google.com/photos/about/), implemented with native Material 3 controls.

- **Sessions:** cover-photo grid, All / Active / Complete filters, compact discovery status, and a clear Start a session action.
- **Session detail:** responsive photo grid. Tap a photo to open it in a device photo viewer. Open **Upload & copies** for copying, sharing, Drive upload/progress/cancel, and Open in Drive. On wide windows these controls appear in a side panel.
- **Manage:** Rename remains next to Stop / Resume; Delete session is in the three-dot Session options menu with the existing confirmation.
- **Settings:** grouped discovery, notification, and Drive controls; consistent neutral surfaces and blue actions; readable system-bar icons.
- **Install:** `delivery/Photo-Sessions-1.3-debug.apk`, as an update over 1.2. Version code 4; database version remains 3. Existing discovery, grouping, copying, and Drive behavior is retained.

[Download version 1.3 from Google Drive](https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view). The existing shared download link is preserved.

Owner confirms the app looks good and works on the Galaxy Tab A9. Friend's Android phone testing is pending. See [DRIVE-SETUP.md](DRIVE-SETUP.md) for adding test users and [VALIDATION.md](VALIDATION.md) for tested behavior and limitations. Older release notes below are historical.

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
