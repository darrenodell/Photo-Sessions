# Changelog

Track requested changes, implementation, builds, and testing feedback here. Pending requests are not shipped features.

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
