# Secrets and Config (Release)

No secret values belong in this file, in Git, or in the APK.

## Decisions (code, done)

- minSdk is 24 (matches AGENTS.md). Code paths use SDK_INT guards: NotificationChannel (O), exact alarms (S / M), POST_NOTIFICATIONS (TIRAMISU). appplugin modules also declare minSdk 24.
- The client has no SMTP path. `RealSmtpEmailSender` and the `SMTP_*` entries (`.env.example`, BuildConfig ignore list) are removed. Verification/reset mail is sent by Firebase Auth. `EmailQueueService` only keeps local OTP state and emits a UI event; it never sends mail or opens network connections, so its unit tests need no fake sender.
- `.gitignore` covers `*.jks`, `*.keystore`, `license.properties`, `.env`, `.env.*`, with `!.env.example` re-included after them (the negation only works in that order). Verified 2026-10-08: `git check-ignore -v .env` → `.gitignore:25`; `.env.example` is not ignored.
- **P1 (open, owner action):** a `.env` containing SMTP settings (`SMTP_HOST`, `SMTP_PORT`, `SMTP_SENDER_EMAIL`, `SMTP_SENDER_PASSWORD`, `SMTP_SENDER_NAME`) and Cloudinary names was committed in HEAD (`9bccfdd`). The index deletion is staged, not committed. Rotate `SMTP_SENDER_PASSWORD` at the mail provider before any release. Do not rewrite history unless the repo is or will become public. Values are intentionally not recorded here.
- `.env.example` holds only variable names and non-secret placeholders. The R2 access keys (`R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY`) must never be added to the client; they live only in Firebase Secret Manager.

## Owner actions (console, not code)

### 1. Google API key (Firebase / `google-services.json`)
The key in the app is public by nature, so restrict it in Google Cloud Console > APIs & Services > Credentials:
1. Application restriction: Android apps. Add package `com.aistudio.inlove.kmrv` with the SHA-1 of the upload key, and the SHA-1 of the Play App Signing key (see 3). Add the debug SHA-1 only to a separate dev key.
2. API restriction: only the APIs used (Firebase Auth/Identity Toolkit, Firestore, Firebase AI / Generative Language, Firebase Installations, App Check). Remove unused ones.
3. Enable Firebase App Check (Play Integrity) enforcement for Firestore and Firebase AI.
4. If any key or SMTP password was ever committed or shipped, rotate/revoke it in the console.

### 2. Cloudinary unsigned preset (`inlove_unsigned`): replaced by R2, disable it (O-14)
Private memory media is uploaded through a Cloud Function and a private Cloudflare R2 bucket, not through Cloudinary. The app asks the function for a one-time presigned PUT URL on the bucket `inlove-media` (public access off) and reads files through 15-minute presigned GET URLs. No R2 key is on the device (server side: see 4b).
- Owner (O-14): in Cloudinary Console > Settings > Upload > Upload presets, disable `inlove_unsigned` or set it to Signed, so nobody can keep uploading through the old preset.
- Media already uploaded to Cloudinary is not migrated. It stays there until the owner deletes it in the Cloudinary console.

### 3. Play App Signing
- Enroll the app in Play App Signing; keep the upload keystore offline and out of Git.
- Copy the app-signing SHA-1/SHA-256 from Play Console > App integrity into the Google API key restriction and Firebase project settings.
- Upload the R8 mapping file with each release.

## Checks
- `git ls-files -- .env app/.env debug.keystore my-upload-key.jks license.properties` must return nothing.
- Inspect the release APK/BuildConfig for absence of SMTP_*, R2_* keys and keystore material.

### 4. Firestore rules and App Check (anti-tamper / anti-scrape)
1. Deploy rules from this repo only: `npx firebase-tools deploy --only firestore:rules` (never edit rules in the console).
2. Firebase Console > App Check > register the Android app with Play Integrity, then switch Firestore and Firebase Auth to **Enforced** after a day of verified-traffic metrics. Debug builds use a debug provider token registered in the console.
3. Catalog collections (`gift_ideas`, `milestone_presets`, `badge_definitions`, `preset_assets`, `checklist_templates`) stay world-readable because guests have no Firebase session; App Check enforcement plus the quota alerts below are what limit scraping. Writes are denied for all clients.
4. Relationship data is writable only by ACTIVE members; invites can only be created as PENDING and only the target can change their `status`; a relationship can only be created by the invite's accepter. Re-test any rules change in the Firestore emulator (`firebase.json` has it on port 8080) before deploying.
5. Console > Usage and billing > set a budget alert and a Firestore daily read/write quota alert.

### 4b. Cloud Functions deploy (D3, NOT RUN; needs the owner's explicit go-ahead)
1. Owner: Firebase Console > Usage and billing > upgrade project `demngayyeuandroid` to **Blaze** (functions cannot deploy on Spark).
2. Owner: create the private R2 bucket `inlove-media` (public access off) and an API token with Object Read & Write limited to that bucket only (Cloudflare Dashboard > R2). Set the secrets (values never in Git, never pasted in chat): `npx firebase-tools functions:secrets:set R2_ACCESS_KEY_ID --project demngayyeuandroid` and the same for `R2_SECRET_ACCESS_KEY`. The params `R2_ACCOUNT_ID` (the Cloudflare Account ID) and `R2_BUCKET` (`inlove-media`) are prompted on the first deploy and saved in `firebase/functions/.env.demngayyeuandroid`, which Git ignores. `firebase/functions/src/objectStore.js` throws at the first media call if any of the four is missing, naming only the parameter names. Never print the values.
3. Pre-check: `npm --prefix firebase/functions test` (740/740 on 2026-10-21; runtime is Node 22).
4. Deploy order (rules deny client relationship creates, so order matters): `--only functions` first, then `--only firestore:rules`, then the app release.
5. Giới hạn tần suất theo uid: 5 callable (`acceptCoupleInvite`, `requestAccountDeletion`, `signMemoryUpload`, `confirmMemoryUpload`, `getMemoryMediaUrl`) bị giới hạn theo uid bằng `cfg.rateLimits` (trong `firebase/functions/src/config.js`); bộ đếm nằm ở `mediaQuota/rl_*`; client vượt giới hạn nhận lỗi `resource-exhausted`. Fail-closed: Firestore lỗi thì callable cũng lỗi.
6. Điều kiện owner trước khi deploy: vào Firebase Console > Firestore > TTL, xác nhận policy trên `mediaQuota.expiresAt` ở trạng thái Active (trong repo mới chỉ khai báo ở `firestore.indexes.json`, chưa xác minh trên project thật).
7. Các doc `rl_*` hết hạn sau 48 giờ và được TTL xoá bất đồng bộ (thường trong vòng 24 giờ sau khi hết hạn). Khoá doc chứa uid nên ghi vào mục lưu trữ/xoá dữ liệu (data retention); job xoá tài khoản không dọn `rl_*`.
8. Owner: set a Cloudflare billing notification at 1 USD and a Firebase budget alert (section 4 item 5). R2 is billed only above the free tier (10 GB-month of storage, 1 million Class A and 10 million Class B operations per month) and has no spend cap.

### 5. Facebook sign-in / unlock
The app uses Firebase Auth's generic `facebook.com` OAuth provider (Custom Tab web flow, no Facebook SDK, no Facebook secret in the APK).
1. developers.facebook.com > create an app (type Consumer) > add **Facebook Login**; copy App ID and App Secret.
2. Firebase Console > Authentication > Sign-in method > **Facebook** > enable, paste App ID and App Secret, copy the shown OAuth redirect URI (`https://<project>.firebaseapp.com/__/auth/handler`).
3. Facebook Login > Settings > Valid OAuth Redirect URIs: paste that URI. Set the Facebook app to Live before release; request only `email` and `public_profile`.
4. Facebook app settings: add the privacy policy URL and data-deletion URL (same as Play listing).
5. Play Console Data safety: declare name/email collected via Facebook sign-in (account management).
6. Until step 2 is done the button shows a "not configured" message and email/PIN login keeps working.

PIN security (code, done): PBKDF2 (120k) hash, persisted escalating lockout (5 free tries, then 30s, 1m, 5m, 15m, 1h) shared with the password fallback; Facebook re-authentication is the recovery path that clears the lockout. Accounts created through Facebook have no local password.
