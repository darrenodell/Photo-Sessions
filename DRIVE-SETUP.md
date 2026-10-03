# Google Drive setup

Current app: Photo Sessions 1.3. Direct Drive upload was implemented in version 1.2; the gallery refresh retains it.

## Current Cloud project

- Project name: Photo Sessions
- Project ID: `cohesive-apogee-510314-j3`
- Project number: `75836670368`
- Google Drive API enabled; external audience in Testing mode.
- Android package: `com.example.photosessions`
- Debug testing signer SHA-1: `64:1B:2C:91:92:52:79:52:70:15:58:A0:0E:53:AA:19:50:C5:28:AA`
- Requested scope: `https://www.googleapis.com/auth/drive.file` (non-sensitive, per-file access).

## Add a tester

Open [Google Auth Platform → Audience](https://console.cloud.google.com/auth/audience?project=cohesive-apogee-510314-j3). Under Test users, choose Add users, enter the person's Google account email, and save. They must select that account when connecting Drive in the app.

Testing can continue indefinitely. While the Cloud project remains in Testing, each user's authorization expires seven days after consent; reconnecting is required. The application and uploaded photos do not expire.

## App behavior

Open a session and choose Upload to Google Drive. Choose Wait for Wi-Fi or Upload now, then select an account and grant access when requested. The app creates My Drive / Photo Sessions / <session name> and reuses its stored folder on subsequent uploads.

Uploads use WorkManager, show progress, and retry without creating duplicate files. State is scoped to the Google account. The app uploads a gallery copy when available, otherwise the original camera photo. Copies, sharing, and Drive uploads are explicit user actions. Disconnect stops pending uploads; deleting a local session keeps uploaded files.

Owner confirmed successful Google consent and Drive uploads with 1.2. The 1.3 UI was tested on the owner's tablet and with synthetic sessions on a disposable emulator; actual Drive uploads were not reverified during the UI refresh. Friend's phone testing is pending.

## Moving to production

Prepare public branding, support details, a home page, and privacy policy; complete the branding requirements shown by Google. Publish the authorization app from Audience when ready. Production removes the test-user allowlist requirement and testing-specific seven-day expiration. Google authorization publishing is separate from publishing on Google Play.

Before distribution, prepare a permanent release signing key and register its certificate as a matching Android OAuth client. The current APK uses the retained debug signer. Keep private keys and credentials out of the repository. Android authorization does not require embedding an OAuth client secret in the APK.

No production publishing has been performed. A phone test, network interruption/resume, Wi-Fi-only constraints on mobile data, reconnection, and large-session behavior remain useful follow-up checks.

## Official references

- [Manage app audience and testing](https://support.google.com/cloud/answer/15549945?hl=en)
- [Android authorization](https://developer.android.com/identity/authorization)
- [Drive access scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [Brand verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/brand-verification)
