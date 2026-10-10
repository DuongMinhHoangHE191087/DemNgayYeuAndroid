# InLove — Play internal-testing checklist

Verified in repo (2026-10-10, `--rerun`): `:app:testDebugUnitTest` 39 suites, 225 tests, 0 failures, 0 errors, 0 skipped; `:app:lintRelease` BUILD SUCCESSFUL; `npm --prefix scripts run test:rules` 42 passing; `npm --prefix firebase/functions test` 53/53. `:app:assembleRelease` blocked by missing release inputs (section 1).

## 1. Build inputs (owner provides; never commit)
- Upload keystore: `my-upload-key.jks` at repo root, or env `KEYSTORE_PATH`
- env `STORE_PASSWORD`, `KEY_PASSWORD`
- Gradle props `-PADMOB_APP_ID_RELEASE=ca-app-pub-…~…`, `-PADMOB_BANNER_ID_RELEASE=ca-app-pub-…/…` (real ids; guard rejects blank/malformed)
- Internal SDK `appplugin` 2.4.2: sibling clone `../appplugin`, `./appplugin`, or env `APPPLUGIN_DIR` (composite build, substituted for `com.app.plugin:appplugin`)
- JDK 25 (Gradle daemon criteria), Gradle 9.4.1, AGP 9.2.1; config-cache off

## 2. Build
```
./gradlew :app:testDebugUnitTest :app:lintRelease
./gradlew :app:bundleRelease -PADMOB_APP_ID_RELEASE=… -PADMOB_BANNER_ID_RELEASE=…
```
Bump `versionCode` for every upload (currently 1, `versionName` 1.0.0). Install the signed, minified build on a device and smoke-test: launch, guest flow, sign-in, pairing offline/online, paywall open, banner shows only for non-VIP after consent.

## 3. Play Console
- App content: Privacy policy URL (public page), Ads = yes, Data safety (account email, Firestore content, photos via Cloudinary, crash/diagnostics only if enabled), target audience not children
- Declare permissions: POST_NOTIFICATIONS, exact alarms (reminders), RECEIVE_BOOT_COMPLETED, BILLING
- Billing: create subscription/in-app products matching `VipProductIds`, add licence testers, upload AAB to Internal testing track, add testers
- Verify UMP consent message is published in AdMob (Privacy & messaging) for EEA/UK

## 4. Owner decisions (defaults used)
- Light theme only for v1 (system bars forced light) — dark mode deferred
- Banner ads only; app-open/interstitial gated by consent and kept off by default
- Analytics/FCM off; Crashlytics only behind consent

## 5. Open before production
- Cloud Functions for atomic pairing + durable account deletion (needs Blaze plan)
- Export Room schema v12 and un-ignore Migration12To13Test
- Local ownership scoping / guest adoption / sync outbox (plan steps 4–10)

## 6. Legal pages (owner, before any track)
- Host HTTPS pages at `LEGAL_BASE_URL` (.env): `/privacy`, `/terms`, `/delete-account` (the app links to all three).
- Enter the same privacy-policy URL and the account-deletion URL in Play Console > App content; complete Data safety (account email, photos via Cloudinary, purchase history, ad ID via AdMob).
- Create Play products `vip_yearly` (with free-trial offer if desired), `vip_monthly`, `vip_lifetime`; the paywall shows only what Play returns, so a missing product keeps its button disabled.
