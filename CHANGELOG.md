# Changelog

## 1.7 — 2026-10-16

- Discover new screenshots in Pictures/Screenshots and DCIM/Screenshots alongside camera photos, including subfolders and external media volumes. Downloads and Photo Sessions exports remain excluded.
- Persist the screenshot discovery start on the first run of this version. Older screenshots are not backfilled; camera discovery retains its existing baseline. No schema migration or changes to existing session membership.
- Screenshots use the same automatic inactivity grouping and fixed manual deadlines as camera photos. Gallery guidance and accessibility labels now cover both.
- Reuse original image types and bytes for local copies, sharing, Drive uploads and Google Photos uploads. Version code 14.
- Build/lint and all 68 host tests passed. Mixed-image discovery, actual gallery/copy controls, PNG preservation, sharing preparation and duplicate prevention passed on the owner's Galaxy Tab A9. Installed in place with catalog/settings preserved and test fixtures removed; uploaded to the existing shared Drive APK with its link and sharing retained.

## 1.6.3 — 2026-10-12

- Remove the “Not copied” caption and its padding beneath photos that have not been copied locally. Existing copied/retry status labels remain.
- Version code 13; display-only change with no database or transfer changes. Installed and visually checked on the owner’s tablet; uploaded to the existing shared Drive APK without changing its link.

## 1.6.2 — 2026-10-12

- Show Open in Google Photos only after at least one photo is recorded as added and the session has no active Photos upload. Previously the link appeared as soon as an album was created, before its first upload finished.
- Investigated a transient Google Photos “Can’t find link” error on the owner’s tablet. The same saved URL subsequently opened the correct album and photo in both Chrome and Google Photos; uploads were successful. This change prevents premature opening during upload, but does not control Google’s album processing or account selection.
- Version code 12; no database migration or changes to upload bytes, account permissions or album sharing. Installed and verified on the owner’s tablet; published in place to the shared Drive APK with the same link.

## 1.6.1 — 2026-10-11

- Add 30 minutes alongside 1 hour and 2 hours for manual sessions, resume/extend controls, and all completion notifications. Presets wrap on narrow screens.
- Custom hours accepts only whole numbers from 1 to 168; numeric keyboard and concise guidance replace fractional-hour instructions. Decimal input is rejected without silently changing its value.
- Preserve existing fixed timers and accept quick actions from notifications posted by version 1.6. No database migration; version code 11.
- Completed validation and installed in place on the owner’s tablet on 2026-10-12, preserving its existing data. Published to the existing shared Drive APK with its link and sharing retained.

## 1.6 — 2026-10-11

- Automatic sessions end after 30 minutes without a photo.
- Start session offers 1 hour, 2 hours, or custom decimal hours (for example, 1.5). Manual sessions and extensions use a fixed deadline from the action, unaffected by later photos or quiet gaps.
- Completion reminders offer +1 hour and +2 hours directly in the notification. Opening the reminder offers naming and custom hours; unnamed sessions can resume without a name. Named timed sessions notify again at each deadline.
- Resume session uses the same quick/custom choices. Active session details show the end time. Explicit Stop suppresses the completion reminder; photos after a timer expires return to automatic discovery.
- Persist fixed deadlines in database version 5 with an additive migration. Existing sessions, names, dates, photo membership, exports and preferences are retained. Previously open manual periods without a fixed deadline keep inactivity-based completion until resumed.
- Protect against duplicate and stale notification actions. Fixed timers can complete when photo permission is missing; delayed indexing still honors the original capture-time boundaries. Background execution and reminder delivery remain subject to Android scheduling.
- Version code 10. Local delivery: `delivery/Photo-Sessions-1.6-debug.apk`. Published in place to the existing shared Drive APK on 2026-10-11, preserving its link and sharing settings.

## 1.5.1 — 2026-10-06

- Allow a blank session date and add Clear to the optional date field. Titles, local folders, Drive folders and Photos albums use only the name when the date is removed. Capture timestamps and session membership are retained.

- Compact home-card counts for Local, Drive and Photos; Photos counts respect the connected account and current album membership.
- Bold Upload/Copy heading; consistent full-width Copy Locally, Upload to Google Drive and Add to Google Photos buttons.
- Move Share copies into Local and show it only when all copies are complete. Add Open Local to browse this session's copied files and open them in the device viewer.
- Correct single-photo/session wording, sharing errors, and local thumbnail accessibility labels; refresh thumbnails when their URI changes.
- Version code 9; tested and installed on the owner's tablet with data retained. Published to the existing shared Drive APK on 2026-10-06, preserving its link and sharing settings.

## 1.5 — 2026-10-05

- Added direct Google Photos upload into one app-created album per session, alongside Google Drive.
- Added separate account connection, Wi-Fi/data chooser, progress, cancellation, retry, album link and disconnect controls.
- Persisted account/session album membership and short-lived upload tokens for retries. Recover a lost album-creation response using a unique temporary album title; recover a deleted album on a later upload. Albums follow session renames, with destination failures reported independently.
- Keep originals untouched, send original bytes, and avoid resending photos already added to the current album by this app. Photos exports count toward deletion-warning coverage. Existing catalog schema stays at version 4.
- Version code 8, signed with the existing debug key. The owner enabled Google Photos and the Cloud permission declarations are configured. Tablet consent and a live two-photo upload succeeded; the owner confirmed the upload in Google Photos Albums.


Track requested changes, implementation, builds, and testing feedback here. Pending requests are not shipped features.

## 1.4.2 — consistent phone popups — 2026-10-05

- Replaced the cramped upload chooser's text actions with full-width, stacked buttons. Shortened Upload now and moved Wi-Fi/mobile-data wording beneath its label.
- Applied the same shared dialog and button layout to starting a session, deleting a session, and disconnecting Google Drive. Buttons use left-aligned labels, consistent spacing, and at least 52 dp height; deletion uses a red filled button. Content can scroll on shorter screens or with larger text and an open keyboard.
- Preserved existing action callbacks, cancellation and busy-state handling. No database migration or upload-policy changes.
- Version 1.4.2 / code 7. Settings now reads the installed version instead of displaying a hard-coded 1.4 label. Built the local APK with the retained signer; shared Drive download remains 1.4.1.


## 1.4.1 — Drive upload error reporting — 2026-10-04

- Diagnosed the connected Vivo V2250: validated, unmetered Wi-Fi was available, Drive authorization and session-folder creation succeeded, but Google Drive returned HTTP 403 because the account's storage quota was exceeded. Upload retries were incorrectly labeled as waiting for a connection.
- Parse Drive API error reasons and messages. Storage-quota failures now stop retrying and explain that the connected Google account needs free space. Other failures retain the existing retry policy and expose a readable error. Clear previous errors on a fresh upload, successful completion, or required reconnection.
- Version 1.4.1 / code 6; database schema remains version 4. Built and installed as an update on the connected phone; no data clearing or synthetic photo fixtures. Replaced the shared Drive APK with 1.4.1 at the owner's request, preserving file ID, parent folder, link and anyone-with-link reader access.
- Earlier in this session, installed the clown launcher icon on the owner's tablet and replaced the shared Drive APK in place, preserving its file ID, sharing and link.


## 1.4 — dates and export folder names — 2026-10-04

- Implemented the uploaded planned changes, with the owner's requested trash button directly on session cover images.
- Added a separately persisted, editable ISO date to the session label and export names. Renaming/resuming preserves it; changing it does not regroup photos. Schema 3 → 4 fills existing dates from start times.
- Added independent device/Drive folder rename attempts after saving the session. Device copies move using their stored URIs; Drive folders are renamed by stored IDs for each account. Successful names are recorded; failures are persisted with a retry action. Uploads retry the associated Drive folder rename as well.
- Serialized export changes against in-flight uploads, kept per-session device folder suffixes, and retained recreation of missing device copies.
- Added cover deletion confirmation and an additional warning for photos lacking a readable device copy or recorded successful Drive upload. Deletes only catalog metadata/export references; retains originals, exports, and discovery replay protection.
- Version 1.4 / code 5. Uploaded the new APK to the existing shared Drive file on 2026-10-04, preserving folder and anyone-with-link read access: https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view?usp=drivesdk
- No Google Cloud authorization publishing changes.

## 1.3 — gallery refresh — 2026-10-04

### Testing and delivery

- Owner confirms that 1.3 looks good and works on their Galaxy Tab A9. Individual acceptance scenarios were not enumerated.
- Friend's Android phone testing is pending; next checks are the smaller-screen layout, camera discovery, and Google Drive connection/upload.
- Uploaded `Photo-Sessions-1.3-debug.apk` over the existing Drive APK, preserving file ID and sharing state: https://drive.google.com/file/d/1Pbif3_jMADr6TfyfbGbrg9LUrU0Tbhoz/view
- Work paused at the owner's request after cleanup. No production authorization publishing or Google Play release has been performed.

### Implemented

- Replaced the introductory banner and session rows with an adaptive album-cover grid and All / Active / Complete filters.
- Added a compact discovery status, empty-state guidance, consistent blue/neutral theme, accessible icon controls, and dark system-bar icons on light backgrounds.
- Replaced eager fixed three-column photo rows with a lazy adaptive grid. Photos open in the device viewer when tapped.
- Moved upload/copy/share controls into an expandable panel on narrow windows and a persistent side panel on wide windows. Upload progress and reconnect/failure status remain visible in the collapsed panel summary.
- Moved Delete session into the Session options menu; preserved its confirmation and existing session actions.
- Grouped Settings and constrained the naming form width on tablets.
- Kept loaded thumbnails visible during background refreshes to avoid flicker.
- App version 1.3 / code 4; no database or session-rule changes. Installed as an update on the owner's Galaxy Tab A9. Validation includes catalog/preference preservation, host tests, and disposable-emulator UI checks.

## 1.2 — testing build — 2026-10-01

### Requested and implemented

- Upload sessions directly to Google Drive from the app, replacing reliance on the sharing screen for this flow. A button per session; the person chooses "Wait for Wi-Fi" or "Upload now (Wi-Fi or mobile data)".
- Destination: My Drive / Photo Sessions / <session name>. Drive folder IDs are stored per Google account, so later uploads (including resumed sessions) reuse the same folder. A folder deleted or trashed in Drive is recreated and the session uploaded again.
- Access limited to `drive.file` (only files and folders this app creates). Authorization uses Google Identity Services (`play-services-auth` 22.0.0) with an Android OAuth client registered to the package name and debug signing certificate in the owner's Google Cloud project "Photo Sessions".
- Uploads run in WorkManager with the chosen network constraint, survive restarts, show progress notifications, and retry with backoff. Each Drive file is tagged with the photo's media key, so a retry never creates duplicates. The Gallery copy is uploaded when present, otherwise the camera original.
- If Google needs the person to reconnect (for example, the 7-day expiry while the Cloud project is in Testing), uploads pause with a "Reconnect Google Drive" notification and message.
- Session screen: "X of N uploaded", live status, Cancel upload, Open in Drive. Settings: connected account and Disconnect. Copying to Gallery folders and "Share copies…" are kept.
- Database migration to version 3 (Drive state per photo). App version 1.2 / code 3. Adds INTERNET and ACCESS_NETWORK_STATE permissions, used only for Drive uploads.

### Notes — 2026-10-01

- First sign-in attempt was blocked ("has not completed the Google verification process") until the owner's Gmail was listed under Google Auth Platform > Audience > Test users. Every account that connects must be a test user while the project is in Testing.
- 1.2 installed over 1.1 on the Galaxy Tab A9 with `adb install -r` after freeing tablet storage; sessions were preserved. Owner confirmed a successful upload: Drive shows a Photo Sessions folder with one subfolder per session containing the photos.

## 1.1 — testing build — 2026-10-01

### Notes — 2026-10-01

- User reports that the app is now working reliably on their Galaxy Tab A9 running Android 16. The earlier installation problem is no longer an active blocker; the resolution was not specified.
- User wants to make changes and rebuild the app for further testing.

### Requested and implemented

- Start a named session immediately, stop it manually, and resume the same session later.
- Change the inactivity timeout from 30 to 15 minutes. Starting or resuming completes other active sessions. Photos outside manual capture periods continue to form automatic sessions.
- Rename existing sessions.
- Copy photos into a named folder under Pictures/Photo Sessions. Camera originals remain intact. A session ID suffix separates identically named sessions. The folder is created on first copy; renaming a session afterward keeps the existing folder, whose path is displayed.
- Show copied / needs-copying / failed-copy status per photo and session totals. Copying is an explicit action; retries skip existing successful copies.
- Add Upload to Drive using Android sharing, as chosen by the user. Copy photos first, choose Drive, then confirm the destination in Drive. Upload completion is tracked by Drive, not this app.
- Delete sessions with confirmation, keeping originals and copied files. Recorded photos from deleted sessions are excluded from later discovery.
- Refresh the home screen, colors, cards, and controls; remove the “30 quiet minutes” wording.
- Preserve existing catalogs through a database migration; increase app version to 1.1 / code 2.

### Delivery and testing

- User requested a rebuilt APK replacing the existing Drive APK, preserving its link.
- Build, migration/session tests, emulator checks, and delivery verification are recorded in VALIDATION.md.

## 1.0 — initial MVP — 2026-09-30

- Implemented discovery of new camera photos, grouping with a fixed 30-minute inactivity timeout, naming notifications, session naming/skipping, session browsing, and local persistence.
- Preserved original photos without moving, renaming, modifying, or duplicating them.
- Produced a debug APK and packaged source, installation/usage instructions, and validation results for Google Drive delivery.
- Recorded 25 passing host tests, a successful Android 14 emulator flow test, and lint with zero errors. See VALIDATION.md for the original test scope and limitations.
