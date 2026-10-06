# Secrets and Config (Release)

No secret values belong in this file, in Git, or in the APK.

## Decisions (code, done)

- minSdk is 24 (matches AGENTS.md). Code paths use SDK_INT guards: NotificationChannel (O), exact alarms (S / M), POST_NOTIFICATIONS (TIRAMISU). appplugin modules also declare minSdk 24.
- The client has no SMTP path. `RealSmtpEmailSender` and the `SMTP_*` entries (`.env.example`, BuildConfig ignore list) are removed. Verification/reset mail is sent by Firebase Auth. `EmailQueueService` only keeps local OTP state and emits a UI event; it never sends mail or opens network connections, so its unit tests need no fake sender.
- `.gitignore` covers `*.jks`, `*.keystore`, `license.properties`, `.env.*` (except `.env.example`).
- `.env.example` holds only variable names and non-secret placeholders. The Cloudinary API secret must never be added to the client.

## Owner actions (console, not code)

### 1. Google API key (Firebase / `google-services.json`)
The key in the app is public by nature, so restrict it in Google Cloud Console > APIs & Services > Credentials:
1. Application restriction: Android apps. Add package `com.aistudio.inlove.kmrv` with the SHA-1 of the upload key, and the SHA-1 of the Play App Signing key (see 3). Add the debug SHA-1 only to a separate dev key.
2. API restriction: only the APIs used (Firebase Auth/Identity Toolkit, Firestore, Firebase AI / Generative Language, Firebase Installations, App Check). Remove unused ones.
3. Enable Firebase App Check (Play Integrity) enforcement for Firestore and Firebase AI.
4. If any key or SMTP password was ever committed or shipped, rotate/revoke it in the console.

### 2. Cloudinary unsigned preset (`inlove_unsigned`)
In Cloudinary Console > Settings > Upload > Upload presets:
- Allowed formats: jpg, png, webp (plus mp4 if video is used); everything else rejected.
- Max file size and max image/video dimensions set.
- Fixed folder (`inlove_memories`); disallow client-chosen folder override if possible.
- Resource type limited to image (and video only if needed).
- Overwrite = off; unique filename on; do not allow client-supplied public_id.
- Enable moderation/quotas and usage alerts. If product needs exceed these limits, disable release upload and build server-signed upload (follow-up).

### 3. Play App Signing
- Enroll the app in Play App Signing; keep the upload keystore offline and out of Git.
- Copy the app-signing SHA-1/SHA-256 from Play Console > App integrity into the Google API key restriction and Firebase project settings.
- Upload the R8 mapping file with each release.

## Checks
- `git ls-files -- .env app/.env debug.keystore my-upload-key.jks license.properties` must return nothing.
- Inspect the release APK/BuildConfig for absence of SMTP_*, Cloudinary secret and keystore material.
