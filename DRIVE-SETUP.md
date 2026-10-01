# Direct Drive uploads — setup and proposed behavior

Status: preparation only; not implemented in the current APK.

## Phone experience

1. Open a named session and tap Upload session to Drive.
2. On first use, choose a Google account and grant access.
3. Create Photo Sessions in My Drive if this app has not already created it, then create a child folder named after the session.
4. Upload the session's images, showing confirmed uploads, pending files, and errors separately from local copy status.
5. Later uploads for the same session reuse its saved Drive folder ID and upload missing photos. Distinct sessions with the same name remain separate; use a date suffix when needed to distinguish them.

Local organization continues to work without Google authorization. Deleting a local session must not silently delete its Drive files. Renaming and account switching require explicit, documented behavior before implementation. Upload state must be scoped to the Google account and persistent session identity. Interrupted requests must be reconciled before retrying to avoid duplicates.

## One-time developer setup

Use a Google Cloud project controlled by the app owner. Enable the Google Drive API and configure Google Auth Platform branding, audience/test users, and data access. Register an Android OAuth client for:

- Package: `com.example.photosessions`
- Current testing APK signing certificate SHA-1: `64:1B:2C:91:92:52:79:52:70:15:58:A0:0E:53:AA:19:50:C5:28:AA`
- Requested scope: `https://www.googleapis.com/auth/drive.file`

The fingerprint identifies the existing debug testing signer. A future release signed with a different key needs its own matching Android OAuth registration. Keep private signing keys and account credentials out of source archives. Android authorization does not require embedding an OAuth client secret in the APK.

This project currently contains no Drive authorization configuration or direct Drive client. Google Play services AuthorizationClient can request consent on the phone; Drive REST API calls can create folders and upload files using the resulting short-lived access token. Do not log or store tokens in the catalog. Any server-side authorization flow would require separate setup; a backend is not necessary for user-initiated uploads performed on the phone.

## Official references

- Android authorization and project setup: https://developer.android.com/identity/authorization
- Create and populate Drive folders: https://developers.google.com/workspace/drive/api/guides/folder
- Limited Drive access scopes: https://developers.google.com/workspace/drive/api/guides/api-specific-auth

## Checks required before shipping

Test first authorization, cancellation, denied/revoked access, reconnecting, account switching, session folder reuse, identically named sessions, new photos after resuming, network interruption, expired authorization, and retry after an uncertain upload response. Confirm the actual folder and photos in a test Google account before claiming the feature works. Retain upgrade coverage for existing local sessions.

## Cloud setup progress — 2026-10-01

- Created Google Cloud project Photo Sessions.
- Project ID: `cohesive-apogee-510314-j3`; project number: `75836670368`.
- Enabled Google Drive API.
- Prepared OAuth branding: Photo Sessions; external testing audience; owner account as support/contact email.
- Paused before accepting Google API Services User Data Policy, pending user approval. Branding creation, Android client registration, test users, app implementation, and live upload validation remain outstanding.
