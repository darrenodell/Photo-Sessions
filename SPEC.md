# Android Photo Session Organizer — MVP Handoff for Astra

> Historical version 1.0 specification. The user’s October 1 changes in CHANGELOG.md supersede the original 30-minute timeout, no-copy rule, and deferred sharing/folder scope. Version 1.1 adds manual sessions, copying, sharing through Android, renaming, and deletion. Remaining original-photo protection still applies.

## Assignment

Build the smallest reliable Android app that detects newly taken camera photos, groups them into sessions based on time, and prompts the user once to name each completed session.

Deliver a complete project that opens in Android Studio, compiles, installs on a physical Android phone, and can be tested. Prioritize reliable photo discovery, session grouping, and the notification-to-naming flow. Avoid unnecessary architecture and dependencies.

This handoff consolidates the MVP specification from the conversation **“Photo app recommendations.”** The implementation defaults below resolve details that were not settled in that conversation; they are explicitly distinguished from the original requirements.

## 1. Product goal and scope

The user takes photos with the phone’s existing Camera app. After a period without new photos, the organizer asks for one descriptive name for the whole session, such as **Garage wiring**, **Baby at the park**, or **ESP32 project**.

The MVP maintains logical groups inside its own catalog. It stores references to the original photos alongside a session name. These groups do not create folders or albums in other apps.

### Required constraints

- Do not delete, move, rename, or modify original photos.
- Do not duplicate original photos.
- Keep session data locally on the device.
- Use Kotlin and Jetpack Compose.
- Access photos through Android MediaStore.
- Persist sessions and photo references with Room or another standard Android database.
- Use appropriate Android background mechanisms, such as WorkManager.
- Handle photo and notification permissions correctly for supported Android versions.
- Target a current stable Android SDK when implementing; document the chosen minimum and target SDKs.
- Resume discovery after ordinary app restarts and device reboots as reliably as Android permits.

## 2. Core user flow

1. The user grants the permissions needed for discovery and notifications.
2. The first newly detected camera photo starts a session.
3. Additional photos taken close together extend that session.
4. After **30 minutes without another photo**, the session becomes complete.
5. Send a notification, for example: **“You took 18 photos. Name this session.”**
6. Tapping the notification opens that session’s naming screen.
7. The user enters a name and taps **Save**, or taps **Skip**.
8. The session appears in the home list with its date/time, thumbnail, photo count, and name or unnamed label.
9. Opening the session displays its photos in a grid.

## 3. Session rules

### Requirements from the conversation

- Default inactivity timeout: **30 minutes after the last photo**.
- Each additional photo within the timeout extends the session.
- A session completes after the timeout elapses with no additional photos.
- Prompt once per completed session, rather than once per photo.
- Store the session name and references to all its photos.

### Implementation defaults

- Group by photo capture time, rather than the time a background scan discovers the photo. Document a fallback when capture time is missing or invalid.
- Use a strict boundary: a gap **less than** the timeout extends the session; a gap **equal to or greater than** the timeout starts another session.
- A session may contain just one photo and may span midnight.
- Process discovered photos in chronological order. A delayed scan can discover several distinct sessions.
- Reconcile newly available photos before finalizing sessions and issuing notifications.
- Repeated scans must not duplicate photos, sessions, or naming prompts. Persist discovery and prompt state across restarts.
- Start monitoring from initial setup; do not import the entire historical library by default. Establish a persistent baseline.
- **Skip** retains an unnamed session. The user can open it and name it later. Do not repeatedly notify about it.
- Trim names and require a nonempty value for **Save**. Allow duplicate session names.
- Keep session identity stable so a notification always opens the correct session.

## 4. MVP screens

### Home

- Sessions ordered newest first.
- Each row shows name or an unnamed label, date/time, thumbnail, and photo count.
- Tap a row to open the session.
- Include an empty state explaining that new camera photos will appear here.
- Provide a clear permission/status message when discovery is unavailable or limited.

### Session

- Session name or unnamed label.
- Start and end time.
- Photo grid using the existing media references.
- A way to name an unnamed session.
- Handle unavailable photos gracefully without crashing or modifying the originals.

### Name Session

- Representative thumbnail.
- Photo count.
- Start and end time.
- Name text field.
- **Save** and **Skip** buttons.
- Accessible both from the notification and from an unnamed session in the app.

### Settings

- Notifications on/off.
- Session timeout, displaying the default of 30 minutes.

**Scope clarification:** The conversation lists a timeout setting but places configurable values under “later settings.” For the smallest MVP, show the fixed 30-minute value; defer selection among **10 minutes, 30 minutes, 1 hour, and 2 hours**.

Turning notifications off must not stop discovery, grouping, or in-app naming. Distinguish the app’s preference from notification permission in Android settings.

## 5. Architecture guidance

Keep the implementation small, with clear responsibilities:

| Component | Responsibility |
| --- | --- |
| Media discovery | Query MediaStore for eligible new camera images and reconcile changes. |
| Session logic | Apply timestamp and inactivity rules deterministically. |
| Local persistence | Store sessions, media references, discovery progress, and prompt state. |
| Background coordination | Schedule discovery/finalization and recover persisted work where supported. |
| Notifications | Create one logical naming prompt per completed session and route to its screen. |
| Compose UI | Display sessions, collect names, and expose settings and permission status. |

Suggested stored information:

- **Session:** stable ID, optional name, first/last capture time, active/completed state, naming/prompt state.
- **Photo reference:** stable MediaStore identity including volume where applicable, content URI, session ID, and effective capture timestamp.
- **App state:** monitoring baseline, durable discovery progress, notification preference, and timeout value.

Use database constraints and transactions where needed to prevent duplicate membership or partial updates. Keep grouping logic independent of Android UI code so boundary cases can be tested directly.

## 6. Discovery, permissions, and reliability

The original requirement is best-effort operation within Android’s limits. The timeout defines session membership; notification delivery need not occur at an exact wall-clock deadline.

During implementation, verify current official Android documentation for MediaStore, media permissions, notifications, and background scheduling. Document the actual behavior and limitations of the chosen approach.

- Define and document how camera photos are distinguished from screenshots, downloads, messaging images, and other media. State any device-specific limitations.
- Treat videos and historical-library organization as outside this MVP.
- Recover photos taken while the process was not running, when Android permits the app to run again and those photos remain accessible.
- Make catch-up scans safe to repeat and resilient to delayed media indexing.
- Explain permission requests plainly. Handle denial, limited access, and later revocation without crashing or claiming complete coverage.
- With notifications denied or disabled, keep completed sessions available to name in the app.
- Account for photos removed externally or becoming inaccessible. Preserve session metadata and show an appropriate unavailable state.
- Document observed behavior under battery restrictions, reboot, ordinary process termination, and explicit force-stop. Do not promise continuous execution or exact notification timing.

## 7. Acceptance criteria

The MVP is ready for review when:

1. It builds, installs, and launches on a physical Android phone.
2. New camera photos are discovered and assigned to sessions without changing or duplicating their files.
3. Photos less than 30 minutes apart form one session; a gap of 30 minutes or more starts another.
4. A completed session produces one logical naming prompt when notifications are enabled and permitted.
5. The notification opens the correct session, including after an app restart.
6. Saving a name persists it and updates the home list and session view.
7. Skipping preserves an unnamed session that can be named later without repeated prompts.
8. Home shows sessions newest first with the required summary fields; a session opens its photo grid.
9. Repeated scans and restart recovery do not duplicate membership or prompts.
10. Delayed discovery correctly separates photos into multiple sessions according to capture times.
11. Permission denial/revocation, disabled notifications, and externally removed photos are handled gracefully.
12. Restart, reboot, and background behavior have been tested on a physical device, with limitations recorded.

Test the grouping logic with a controllable clock or synthetic timestamps. Include cases immediately below, exactly at, and above the timeout; a single-photo session; a session crossing midnight; and delayed batch discovery. Do not shorten the production default merely to speed testing.

## 8. Deferred features

Do not implement these in the MVP:

- Real filesystem folders or moving/copying photos into folders.
- Android or Google Photos album creation.
- Voice naming.
- AI-generated names.
- Location-based grouping.
- Face or object recognition.
- Search.
- Cloud sync.
- Export.
- Sharing entire sessions.
- Automatically combining adjacent sessions.
- Configurable timeout values beyond the fixed MVP default.

## 9. Deliverables from Astra

- Complete Android Studio project with dependency versions and build configuration.
- Working Kotlin/Compose implementation and local persistence.
- Focused tests for session boundaries, duplicate prevention, and recovery behavior.
- README with build/install steps, supported Android versions, permission setup, and a physical-device test procedure.
- Brief architecture explanation, camera-photo detection assumptions, and known background limitations.
- Validation report separating tests actually run from checks requiring access to a phone or other unavailable environment.

Build the core discovery → grouping → notification → naming flow first, then finish the session browser and settings. Keep the implementation within this MVP scope.
