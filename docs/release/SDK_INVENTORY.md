# SDK inventory

| SDK | Purpose | Where configured |
|---|---|---|
| `com.app.plugin:appplugin` 2.4.2 (internal) — **D2 decided (2026-10-10): keep 2.4.2.** `gradle/libs.versions.toml` pins 2.4.2. Proven with a fresh clone of tag `v2.4.2` (2b2689c) via `APPPLUGIN_DIR` plus `ANDROID_HOME` (or `local.properties` `sdk.dir`; the clone has none): `:app:compileDebugKotlin` BUILD SUCCESSFUL. Remote also has v2.4.3, 2.4.4 and v2.5.0, not adopted (unverified against the tests/lint) | Ads, IAP entitlements, Firebase Remote Config (`FirHelper`) for owner-editable ads/app config | composite build in `settings.gradle.kts`; see `docs/APPPLUGIN_SDK_OVERVIEW.md` |
| Firebase (BoM 34.17.0): Auth, Firestore, App Check, AI | accounts, pairing, content, anti-abuse | `google-services.json` |
| Play Billing 8.x | VIP purchases / restore | `billing/BillingManager.kt` |
| AdMob + UMP | banner ads, consent | `ads/`, `ConsentManager` |
| Cloudinary | photo upload (signed by a Cloud Function callable; no secret in the app) | Cloud Functions params / Secret Manager; see `SECRETS_AND_CONFIG.md` 4b |
| Room | local store (schema v13) | `data/db` |

Owner-editable config (ads, flags) lives in Firebase Remote Config via `appplugin`; no second config source is added.
