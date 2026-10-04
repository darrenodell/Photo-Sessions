# Changelog

Track requested changes, implementation, builds, and testing feedback here. Pending requests are not shipped features.

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
