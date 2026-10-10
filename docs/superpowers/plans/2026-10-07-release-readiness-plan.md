## Goal

Produce a signed, release-ready InLove build for Google Play internal testing, preserving the existing Compose/M3 and offline-first architecture. The current checkout is **not ready**: the required SDK module is absent, release signing is incomplete, and code inspection reveals remaining data isolation, pairing, deletion, billing, consent, and fallback-content defects. This plan covers the remaining delta from the existing plans; no files were modified or builds executed.

## Assumptions

- **VERIFIED:** `app/build.gradle.kts:34` sets **minSdk 26**, targetSdk 36, versionCode 1, and versionName `"1.0"`. Preserve minSdk 26 unless the OWNER requests broader support.
- **VERIFIED:** `settings.gradle.kts:49` includes `:appplugin`, but its directory is absent. Its current Remote Config, Analytics, Messaging, mediation, and telemetry implementations cannot be audited from this checkout.
- **VERIFIED:** Guest use means local Room data without Firebase registration. Production registration uses Firebase email/password; the local password-login branch requires `isTestMode`. `AuthScreen.onBackToGuest` closes authentication rather than creating a separate local account.
- **VERIFIED:** Cloud content sync currently covers **paired memories and anniversaries**. Signing in alone does not establish personal cloud backup, and guest records are not automatically adopted or uploaded.
- **VERIFIED:** Completed foundations should be retained: App Check installation precedes plugin startup; Billing 8.1 and shared product IDs exist; entitlement caching, request-configuration merging, the Settings privacy-options row, R8, Room 12→13 migration, NetworkMonitor, outbox transactions, and sync adapters exist.
- **VERIFIED:** Gifts and holiday seeds are already wired into the repository. Wallpaper persistence also exists. These supersede pending entries in `docs/superpowers/plans/2026-09-24-master-hardening-and-relaunch.md`.
- **VERIFIED:** `GiftIdeasSeed.all` contains **20 entries**, despite the older “18” description. The default catalog still needs correctness fixes.
- **UNVERIFIED:** Production Firebase/App Check enforcement, deployed rules/indexes, AdMob configuration, Play products/offers, keystore availability, previous uploaded versionCode, developer-account eligibility, and Cloudinary preset restrictions.
- **UNVERIFIED:** The privacy, terms, and deletion URLs work publicly. Browser checks could not retrieve them; that is insufficient evidence to declare them broken.
- **UNVERIFIED:** Historical test results in the ledgers still hold. Running Gradle or emulator tests would write files and was excluded from this read-only review.

## Review findings

| Area | Current state and evidence | Verdict | Priority |
|---|---|---|---|
| **1. Theme** | `app/src/main/java/com/example/ui/theme/Theme.kt:64` exposes unused dark/dynamic arguments and selects light at line 69; `MaterialTheme` receives no Shapes. Hardcoded colors remain, e.g. `app/src/main/java/com/example/ui/screens/SettingsScreen.kt:273`. `app/src/main/res/values/themes.xml:4` uses DeviceDefault; `app/src/main/res/values/colors.xml:3` retains purple/teal defaults. `app/src/main/java/com/example/MainActivity.kt:74` enables edge-to-edge without explicit matching bar appearance. | Rose styling is broadly shared, but theme behavior and tokens are inconsistent. Recommend deliberate light-only for v1. | **P1** |
| **2. Online/offline** | Firebase registration exists at `app/src/main/java/com/example/data/repository/AuthRepository.kt:473`; session restoration trusts a Room token at line 193; logout retains data at line 953. Pairing search remains local at `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt:291`, code publication has no production caller, and acceptance requires a locally cached sender at line 393. | Architecture exists; two-device onboarding and identity boundaries remain incomplete. | **P0** |
| **3. Retention hooks** | Five tabs are wired at `app/src/main/java/com/example/MainActivity.kt:380`; reminders and gift reminders persist. `app/src/main/java/com/example/ui/screens/LoveHomeScreen.kt:1157` contains an **in-app** widget, while `app/src/main/AndroidManifest.xml:1` declares no launcher-widget provider. | Finish existing milestones, reminders/calendar, and memories. A real home-screen widget would be new work. | **P1**, widget deferred |
| **4. Firebase SDK/config** | Matching Firebase package configuration exists at `app/google-services.json:12`; App Check precedes plugin startup at `app/src/main/java/com/example/InLoveApplication.kt:49`. Firebase AI is a dependency at `app/build.gradle.kts:179`, but gift generation is local templates at `app/src/main/java/com/example/data/repository/InLoveRepository.kt:568`. `ContentRepository` has no implementation. | Auth/Firestore/App Check are wired. Other SDK behavior requires restored-module inspection; no verified app-wide operational configuration exists. | **P0 consent**, **P1 config** |
| **5. Ads/billing** | Release AdMob IDs default to empty at `app/build.gradle.kts:79`. Banner requests are gated at `app/src/main/java/com/example/ui/components/AdBannerPlaceholder.kt:47`; interstitial showing lacks a fresh consent check at `app/src/main/java/com/example/ads/AdsManagerImpl.kt:176`. Purchase queries discard errors at `app/src/main/java/com/example/billing/BillingManager.kt:200`. Paywall casts the wrapped context at `app/src/main/java/com/example/ui/screens/PaywallScreen.kt:118`. | Several protections are complete, but startup, consent withdrawal, restore failures, and purchase hosting need correction. No app interstitial placement currently calls the wrapper. | **P0** |
| **6. Gifts** | Unique indexes/default empty IDs are defined at `app/src/main/java/com/example/data/model/Entities.kt:30`; `REPLACE` insertion occurs at `app/src/main/java/com/example/data/db/InLoveDao.kt:67`. Seeds omit IDs at `app/src/main/java/com/example/data/seed/GiftIdeasSeed.kt:46`. “Mutual interest” suggestions simply take three items at `app/src/main/java/com/example/ui/screens/GiftScreen.kt:351`; English text is overridden by Room IDs at line 1078. | Local inserts can replace earlier entries. Personalization and translation are incomplete; AI labeling is inaccurate. | **P0 identity defect**, **P1 personalization** |
| **7. Play readiness** | Release signing is declared but not assigned at `app/build.gradle.kts:57`. Deletion removes Auth before protected Firestore operations and still targets removed collections at `app/src/main/java/com/example/data/repository/AuthRepository.kt:1024`. Nested content is not cleaned. Exact-alarm fallback exists at `app/src/main/java/com/example/alarm/AlarmNotificationScheduler.kt:157`. | Signing, deletion, privacy disclosures, final SDK inventory, and release verification remain gates. | **P0** |

## Steps

All proposed files and symbols absent from the current repository are marked **NEW**. Execute the release-verification steps after completing the OWNER-selected P1 scope.

1. **P0 — Restore the required SDK reproducibly.**  
   **Files/symbols:** `settings.gradle.kts` — `include(":appplugin")`; `app/build.gradle.kts` — `implementation(project(":appplugin"))`; `appplugin/build.gradle.kts` — **NEW/restored dependency file**; `docs/SDK_INVENTORY.md` — **NEW**.  
   Restore the OWNER-approved SDK revision and document how a clean checkout obtains it. Audit its actual dependencies, startup hooks, telemetry endpoints, consent handling, and Firebase integrations before treating historical findings as current.  
   **Proof:** `./gradlew :app:compileDebugKotlin :appplugin:compileDebugKotlin`; dependency reports resolve one intended Ads/Billing version.

2. **P0 — Complete release configuration.**  
   **Files/symbols:** `app/build.gradle.kts` — `signingConfigs`, `buildTypes.release`, `defaultConfig`, `googleServices`; `app/src/main/AndroidManifest.xml` — AdMob metadata.  
   Assign the release signing configuration; validate keystore path/passwords/alias and Firebase package configuration. Fail release builds clearly for missing/invalid AdMob App ID and enabled-placement IDs; debug builds must remain usable. **DECISION — OWNER:** retain the existing package/product IDs; use a versionCode above every previous upload, with `"1.0.0"` recommended for v1.  
   **Proof:** missing release inputs fail deliberately; valid inputs produce a signed AAB with expected package/version metadata.

3. **P0 — Fix empty catalog identities.**  
   **Files/symbols:** `app/src/main/java/com/example/data/seed/GiftIdeasSeed.kt` — `GiftSeed.toEntity`; `app/src/main/java/com/example/data/repository/InLoveRepository.kt` — `seedOfflineDataIfStillEmpty`, `addMilestone`, `generateAiGiftSuggestions`; `app/src/main/java/com/example/data/model/Entities.kt` — `GiftIdeaEntity`, `MilestoneEntity`.  
   Assign stable IDs to seed items, distinct IDs to user-created content, and stable recommendation identities. Repair previously collapsed fallback catalogs without replacing real remote content or favorites. Preserve the existing remote-ID deduplication.  
   **Proof:** extend `app/src/test/java/com/example/CloudEnrichmentDataTest.kt` and `app/src/test/java/com/example/data/repository/PresetDedupeTest.kt`: all 20 seeds survive; reseeding is idempotent; multiple custom milestones coexist.

4. **P0 — Add local ownership boundaries.**  
   **Files/symbols:** `app/src/main/java/com/example/data/model/Entities.kt` — personal entities and `SyncOutboxEntity`; `app/src/main/java/com/example/data/db/Migrations.kt` — `MIGRATION_13_14` **NEW**; `app/src/main/java/com/example/data/db/AppDatabase.kt` — database version/migrations.  
   Introduce an explicit local owner/scope for profiles, personal reminders/checklists, memories, anniversaries, saved preferences, and queued writes. Keep shared catalog content separate from personal selections; preserve ambiguous legacy records for explicit adoption instead of uploading them under an assumed account.  
   **Proof:** `app/src/test/java/com/example/data/db/Migration13To14Test.kt` **NEW** verifies preservation and ownership; retain the existing 12→13 test.

5. **P0 — Apply ownership during reads and sign-out.**  
   **Files/symbols:** `app/src/main/java/com/example/data/db/InLoveDao.kt` — personal queries; `app/src/main/java/com/example/data/repository/InLoveRepository.kt` — exposed flows; `app/src/main/java/com/example/data/repository/AuthRepository.kt` — `logout`; `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt` — `setCurrentUserId`; `app/src/main/java/com/example/data/sync/SyncCoordinator.kt` — `stop`.  
   Scope every personal read/write. On sign-out, stop listeners and account work, reset online state, and switch to the guest scope; reject late callbacks from the previous session. **DECISION — OWNER:** retain account-scoped local data for later sign-in, hidden from guest/other accounts; preserve unsynced edits.  
   **Proof:** `app/src/test/java/com/example/data/repository/AccountIsolationTest.kt` **NEW** covers A→guest→B→A, process restart, and delayed callbacks.

6. **P0 — Implement explicit guest adoption.**  
   **Files/symbols:** `app/src/main/java/com/example/domain/usecase/AdoptGuestDataUseCase.kt` — **NEW**; `app/src/main/java/com/example/data/repository/AuthRepository.kt` — `register`, `login`; `app/src/main/java/com/example/ui/screens/AuthScreen.kt` — `AuthScreen`; `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt` — adoption state **NEW**.  
   Offer a transactional, idempotent “keep this device’s data” choice after authentication. **DECISION — OWNER:** recommend adopting local records into the account, with a separate preview/choice before sharing existing records with a paired partner; private records remain private.  
   **Proof:** `app/src/test/java/com/example/data/repository/GuestAdoptionTest.kt` **NEW** covers registration, existing-account login, cancellation, interruption, retry, and pairing.

7. **P0 — Reconcile authentication and recovery.**  
   **Files/symbols:** `app/src/main/java/com/example/data/repository/AuthRepository.kt` — `AuthState`, `restoreSession`, `requestPasswordResetOtp`, `resetPasswordWithOtp`, `resetPasswordWithSecurityAnswer`; `app/src/main/java/com/example/ui/screens/AuthScreen.kt` — `ForgotPasswordDialog`.  
   Reconcile cached identity with Firebase Auth before enabling cloud actions; retain offline local access when connectivity fails. Route production recovery directly through Firebase reset email and remove the obsolete OTP/security-answer continuation from production UI. Return localized domain outcomes rather than raw SDK messages.  
   **Proof:** extend `app/src/test/java/com/example/data/repository/AuthRepositoryTest.kt`; device checks cover expired/revoked sessions and an actual Firebase reset-link round trip.

8. **P0 — Finish remote pairing discovery and profiles.**  
   **Files/symbols:** `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt` — `publishMyCoupleCode`, `searchUserByCodeOrNameOrEmail`, `updateMyProfile`, `toggleInterest`; `app/src/main/java/com/example/data/repository/AuthRepository.kt` — `syncOnlineUserWithAccount`; `app/src/main/java/com/example/data/sync/SyncCoordinator.kt` — `handleInviteSnapshot`; `firestore.rules`.  
   Publish codes from the real account flow, verify ownership/collisions, and use remote code lookup in the UI search path. Materialize the sender from the received invite and refresh incoming/outgoing UI state. Synchronize partner profile fields through member-only access; expose minimal information through code lookup.  
   **Proof:** extend `app/src/test/java/com/example/data/repository/OnlineCoupleRepositoryPairingTest.kt` and `scripts/rules-tests/pairing.rules.test.js`; two clean devices can discover each other without shared Room records.

9. **P0 — Make pairing transitions authoritative and atomic.**  
   **Files/symbols:** `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt` — invite acceptance/rejection/cancellation and breakup methods; `firestore.rules`; `firebase.json`; `functions/src/index.ts` — `acceptCoupleInvite` **NEW**.  
   Use an authenticated, App Check-protected transaction to enforce one active relationship per UID and idempotent acceptance. Commit local success only after server confirmation; keep pending breakup relationships visible. **DECISION — OWNER:** require connectivity for relationship transitions in v1, with explicit retry instead of apparent local success.  
   **Proof:** emulator tests cover double acceptance, concurrent partners, forged membership, partial failure, repeated cancellation, and breakup; repeat on two devices.

10. **P0 — Complete outbox and conflict correctness.**  
    **Files/symbols:** `app/src/main/java/com/example/data/sync/SyncWorker.kt` — `doWork`, `pushOne`; `app/src/main/java/com/example/data/sync/SyncCoordinator.kt` — remote merge methods; `app/src/main/java/com/example/data/db/InLoveDao.kt` — outbox/acknowledgment methods; `app/src/main/java/com/example/data/sync/NetworkMonitor.kt` — connectivity state.  
    Validate queued ownership against the active Firebase UID; drain beyond one 20-entry batch; clear pending state only for the acknowledged revision. Propagate listener failures and preserve cancellation. **DECISION — OWNER:** recommend server-ordered revisions with retained local conflict drafts rather than device-clock authority; prevent obsolete queued writes from resurrecting newer deletions.  
    **Proof:** extend `app/src/test/java/com/example/data/sync/SyncWorkerTest.kt` and `app/src/test/java/com/example/data/sync/SyncCoordinatorTest.kt` for 21+ writes, clock skew, retries, edit-during-upload, tombstones, and account changes.

11. **P0 — Enforce media/privacy semantics below the UI.**  
    **Files/symbols:** `app/src/main/java/com/example/ui/screens/MemoriesGridScreen.kt` — `AddMemoryDialog`; `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt` — `addSharedMemory`; `app/src/main/java/com/example/data/cloudinary/CloudinaryStorageService.kt` — `uploadToCloudinary`; `app/src/main/java/com/example/data/cloudinary/CloudinaryMediaService.kt` — `uploadMemoryMedia`; `app/src/main/java/com/example/data/sync/MemorySyncAdapter.kt`; `firestore.rules`.  
    Move upload orchestration through a ViewModel/use case and repository; preserve durable local files, explicit upload failure/retry, and close HTTP responses. Never synchronize device-local paths as usable partner media. **DECISION — OWNER:** v1 “Private” stays on this device; “Couple-only” uploads only after pairing/sharing authorization.  
    **Proof:** `app/src/test/java/com/example/data/repository/MemoryPrivacyTest.kt` **NEW** verifies no private upload, denied cross-couple access, truthful failure state, and successful remote media playback.

12. **P0 — Replace client-only account cleanup with durable deletion.**  
    **Files/symbols:** `app/src/main/java/com/example/data/repository/AuthRepository.kt` — `deleteCurrentAccount`; `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` — deletion UI; `functions/src/index.ts` — `requestAccountDeletion`, `processAccountDeletion` **NEW**; `firebase.json`.  
    Reauthenticate before accepting a deletion job, then perform retryable administrative cleanup of actual user documents, codes, invites, nested content, media, and Auth. Remove stale `_3nf` operations; stop listeners/outbox resurrection and clear only that local scope. **DECISION — OWNER:** delete the requester’s authored media/content and unlink the couple while preserving the partner’s independent records; disclose retention and separate subscription cancellation.  
    **Proof:** backend/emulator tests **NEW** cover stale authentication, partial failures, repeated requests, partner preservation, and completed deletion; verify Cloudinary removal on a disposable account.

13. **P0 — Finish consent propagation and telemetry gating.**  
    **Files/symbols:** `app/src/main/java/com/example/ads/AdsManagerImpl.kt` — `requestConsentAndInitialize`; `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` — privacy-options handling; `app/src/main/java/com/example/InLoveApplication.kt` — startup; `app/src/main/java/com/example/privacy/AppPrivacyCoordinator.kt` — **NEW**.  
    Publish consent/privacy-options state after every update and withdrawal; replace the Settings row’s one-time visibility snapshot. Gate advertising through UMP eligibility and gate telemetry independently by purpose, including restored-plugin startup and purchase events. Prevent duplicate SDK initialization.  
    **Proof:** extend `app/src/test/java/com/example/ui/PrivacyOptionsRowTest.kt`; network/device checks cover first launch, consent errors, denial, withdrawal, and restart. Follow [Google’s UMP flow](https://developers.google.com/admob/android/privacy).

14. **P0 — Close ad display and entitlement races.**  
    **Files/symbols:** `app/src/main/java/com/example/ads/AdsManagerImpl.kt` — load/show callbacks and `setVipStatus`; `app/src/main/java/com/example/ui/components/AdBannerPlaceholder.kt` — `ComposeBannerAd`; `app/src/main/java/com/example/di/AppServiceLocator.kt` — initialization; `app/src/main/java/com/example/MainActivity.kt` — ad startup.  
    Apply cached no-ads entitlement before any SDK can load/show; recheck consent, entitlement, activity, and policy at display and asynchronous completion. Destroy banners/preloads when eligibility changes. **DECISION — OWNER:** recommend banner-only v1, App Open/interstitial off; keep one App Open owner if later enabled, with configurable caps and suppression around sensitive flows.  
    **Proof:** `app/src/test/java/com/example/ads/AdsEligibilityTest.kt` **NEW** plus VIP cold-start/withdrawal device checks; preserve `RequestConfigurationMergeTest`.

15. **P0 — Make Billing restoration reliable.**  
    **Files/symbols:** `app/src/main/java/com/example/billing/BillingManager.kt` — `queryExistingPurchases`, `handlePurchase`, `launchPurchaseFlow`; `app/src/main/java/com/example/billing/EntitlementRepository.kt` — cache updates.  
    Aggregate successful SUBS/INAPP queries, filter `VipProductIds.ALL`, exclude suspended purchases, and preserve cached entitlement on query failure. Handle immediate launch failures and purchase-event cache updates; grant once from the aggregate result and retain acknowledgment retries.  
    **Proof:** `app/src/test/java/com/example/billing/BillingManagerTest.kt` **NEW** covers failed queries, unrelated products, suspended subscription plus lifetime ownership, pending purchases, and acknowledgment retry; use Play license testers.

16. **P0 — Repair the paywall purchase host and disclosures.**  
    **Files/symbols:** `app/src/main/java/com/example/ui/screens/PaywallScreen.kt` — `PaywallScreen`; `app/src/main/java/com/example/MainActivity.kt` — paywall call; `app/src/main/res/values/strings.xml`; `app/src/main/res/values-vi/strings.xml`.  
    Pass the real Activity explicitly. Derive price/trial/renewal disclosure and CTA from the selected eligible offer, disabling purchase until details arrive. Describe subscription ad-free access for its active term; advertise only implemented entitlements, removing unsupported exclusive themes, letters, or backup claims.  
    **Proof:** `app/src/test/java/com/example/ui/PaywallScreenTest.kt` **NEW** covers wrapped context, unavailable products, no-trial eligibility, and both languages; actual internal-track purchases open Google Play.

17. **P0 — Complete privacy and store configuration.**  
    **Files/symbols:** `app/src/main/java/com/example/ui/screens/PaywallScreen.kt` — legal links; `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` — deletion link; `app/src/main/java/com/example/config/LegalLinks.kt` — **NEW**; `docs/PLAY_RELEASE_CHECKLIST.md` — **NEW**.  
    **DECISION — OWNER:** select one controlled HTTPS domain and publish working privacy, terms, and deletion-request pages. Complete Data safety from the restored SDK inventory and actual data flows: account/profile data, photos/videos, typed location/notes, purchases, identifiers, diagnostics, and recipients; record retention, deletion, audience, ads, and reviewer access.  
    **Proof:** public mobile-browser checks and a completed disposable deletion request; reconcile Play forms with [account-deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en).

18. **P0 — Establish the release verification gate.**  
    **Files/symbols:** `app/proguard-rules.pro`; `app/src/main/AndroidManifest.xml`; `docs/RELEASE_QA.md` — **NEW**; existing test suites.  
    Run `./gradlew testDebugUnitTest`, `./gradlew lintRelease`, then `./gradlew bundleRelease` with valid release inputs; Windows equivalents use `.\gradlew.bat`. Run `npm run test:rules` from `scripts/`. Validate the signed AAB, merged permissions/SDK declarations, R8 runtime behavior, and native-library 16 KB compatibility. Target 36 already satisfies the current [API requirement](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en-AU); verify [16 KB support](https://developer.android.com/guide/practices/page-sizes).

    **Manual QA checklist to record:** fresh install and 12/13→new-schema upgrade; VI/EN and language switching; airplane-mode guest use; adoption/sign-out/account switching; two-device pairing and sync; private/shared photo/video failures and retries; permission denial/revocation; alarm delivery after reboot/timezone changes; notification navigation; consent withdrawal; VIP cold start/purchase/restore/cancel/pending; deletion and legal links; API 26/33/35/36, gesture/three-button navigation, large fonts, keyboard insets, and a large screen.

    **Proof:** passing reports plus signed-build device evidence. Existing `GreetingScreenshotTest` alone is insufficient visual coverage.

19. **P0 — Distribute through internal testing.**  
    **Files/sections:** `docs/PLAY_RELEASE_CHECKLIST.md` — **NEW**, internal-testing section.  
    Enroll Play App Signing; register its certificate with Firebase/Play Integrity; deploy tested rules/backend; configure Play products, AdMob messages, and registered test devices. Upload the validated AAB, add testers/license testers, publish release notes, and install through the opt-in link; begin with remote ads disabled.  
    **Proof:** tester installation, App Check-protected sync, real Play purchase testing, and clean pre-launch results. **DECISION — OWNER:** confirm developer-account type; qualifying new personal accounts additionally need the prescribed [closed test](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-en).

20. **P1 — Make the light theme deliberate and synchronized.**  
    **Files/symbols:** `app/src/main/java/com/example/ui/theme/Theme.kt` — `MyApplicationTheme`; `app/src/main/java/com/example/ui/theme/Color.kt`; `app/src/main/java/com/example/ui/theme/Type.kt`; `app/src/main/java/com/example/ui/theme/Shape.kt` — **NEW**; `app/src/main/res/values/themes.xml`; `app/src/main/res/values/colors.xml`; `app/src/main/java/com/example/MainActivity.kt` — `onCreate`.  
    **DECISION — OWNER:** recommend branded light-only v1, dynamic colors off. Remove misleading unused theme options, supply M3 Shapes, match XML launch/window backgrounds and system-bar icon appearance, and retire unused purple/teal resources after checking references. Replace conflicting shared-surface colors incrementally.  
    **Proof:** `app/src/test/java/com/example/ui/ReleaseScreensScreenshotTest.kt` **NEW** covers all tabs/auth/pairing/paywall under system light/dark, both languages, and navigation modes.

21. **P1 — Explain mode and sync status accurately.**  
    **Files/symbols:** `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt` — `AppModeUiState` **NEW**; `app/src/main/java/com/example/ui/screens/SettingsScreen.kt` — `SettingsScreen`; `app/src/main/java/com/example/ui/screens/OnboardingScreen.kt` — onboarding; `app/src/main/java/com/example/ui/screens/AuthScreen.kt` — `AuthScreen`.  
    Distinguish local guest, signed-in/unpaired, paired/online, and paired/offline; show pending/error status. Explain that guest data is device-local and lost on uninstall, that sign-in alone is not complete backup, and that offline partner updates, pairing transitions, cloud-only media, and purchases are unavailable until connectivity returns.  
    **Proof:** `app/src/test/java/com/example/ui/AppModeUiStateTest.kt` **NEW** and onboarding/account-switch QA.

22. **P1 — Finish bilingual release paths.**  
    **Files/symbols:** `app/src/main/res/values/strings.xml`; `app/src/main/res/values-vi/strings.xml`; `app/src/main/java/com/example/ui/util/Localization.kt` — `LocalizedStrings`, `LocaleManager`; `app/src/main/java/com/example/ui/screens/AuthScreen.kt`; `app/src/main/java/com/example/alarm/ReminderAlarmReceiver.kt`.  
    Complete missing authentication, error, permission, notification, consent, and paywall strings using the existing localization mechanism. Resolve stored catalog language at display time; audit selected v1 controls for localized accessibility labels and ≥48dp touch targets.  
    **Proof:** both-language screenshot/interaction checks and background-notification checks after process restart.

23. **P1 — Select and finish the smallest retention set.**  
    **Files/symbols:** `app/src/main/java/com/example/ui/screens/LoveHomeScreen.kt` — `LoveHomeScreen`; `app/src/main/java/com/example/ui/screens/CalendarScreen.kt` — `CalendarScreen`; `app/src/main/java/com/example/alarm/AlarmNotificationScheduler.kt`; `app/src/main/java/com/example/alarm/BootCompletedReceiver.kt`; `app/src/main/java/com/example/data/repository/InLoveRepository.kt` — `buildHolidayAnniversaries`.  
    **DECISION — OWNER:** recommend the existing day counter/milestones, reminders/calendar, and photo memories as v1’s main hooks, with the focused gift changes below. Wire notification preferences to scheduling/delivery, restore personal alarms after reboot, and recompute lunar/rule-based holidays by year rather than repeating their previously resolved Gregorian date. Request notification permission in context; preserve exact-alarm fallback and explain timing limitations.  
    **Proof:** `app/src/test/java/com/example/alarm/ReminderSchedulingTest.kt` **NEW** covers disabled notifications, reboot, leap/year boundaries, denied exact access, and timezone changes; follow [Android alarm guidance](https://developer.android.com/develop/background-work/services/alarms).

    Audit-based ranking, without user analytics:

    | Rank | Hook | Value / remaining effort | v1 choice |
    |---|---|---|---|
    | 1 | Milestones/day counter | High / low | Finish |
    | 2 | Reminders | High / medium | Finish |
    | 3 | Memories | High / medium | Reliable photos first |
    | 4 | Calendar | High / medium | Finish existing flow |
    | 5 | Personalized gifts | Medium–high / medium | Top three suggestions + sharing |
    | 6 | Paywall | Revenue value / medium | Correct existing purchase flow |
    | 7 | Launcher widget | Potentially high / high | Defer; provider is absent |

24. **P1 — Repair gift presentation and seed quality.**  
    **Files/symbols:** `app/src/main/java/com/example/ui/screens/GiftScreen.kt` — `GiftIdeaCard`; `app/src/main/java/com/example/data/seed/GiftIdeasSeed.kt` — `GiftSeed`, `all`; `app/src/main/java/com/example/domain/content/ContentModels.kt` — `GiftCatalogItem`; `app/src/main/java/com/example/domain/content/ContentRepository.kt`; `app/src/main/java/com/example/data/content/FirebaseContentRepository.kt` — **NEW**.  
    Remove Room-ID-based English substitutions; resolve bilingual content through stable catalog IDs and canonical category/occasion keys. Keep all 20 fallback ideas. Replace unsupported bestseller, allergy, fragrance-duration, warranty, delivery, and included-service claims with neutral suggestions; label prices as indicative VND ranges and images as illustrations where appropriate.  
    **Proof:** `app/src/test/java/com/example/domain/content/GiftCatalogTest.kt` **NEW** verifies VI↔EN switching, favorites, categories, remote failure fallback, and accurate item identity.

25. **P1 — Add minimal partner preferences.**  
    **Files/symbols:** `app/src/main/java/com/example/data/model/Entities.kt` — partner preferences **NEW**; `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt` — profile/interest methods; `app/src/main/java/com/example/ui/screens/GiftScreen.kt` — preferences editor **NEW**; `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt`.  
    Reuse name/birthday/start date; add optional likes/interests, spending limit, and occasion-region choice, editable for local couples too. Begin interests empty instead of treating seeded coffee/travel preferences as user choices. **DECISION — OWNER:** recommend optional fields and VND budgets initially, clearly labeled in both languages; occasion region is independent of UI language.  
    **Proof:** repository tests verify guest persistence, paired propagation, missing fields, and restart; schema additions join the unshipped migration before the release gate.

26. **P1 — Rank useful personalized suggestions locally.**  
    **Files/symbols:** `app/src/main/java/com/example/domain/usecase/GetPersonalizedGiftSuggestionsUseCase.kt` — **NEW**; `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt` — `triggerAiGiftSuggestions`; `app/src/main/java/com/example/data/repository/InLoveRepository.kt` — `generateAiGiftSuggestions`; `app/src/main/java/com/example/ui/screens/GiftScreen.kt`.  
    Rank the catalog by the partner’s interests, budget, upcoming birthday/holiday/anniversary, and relationship duration; show three ideas with a short localized reason and preparation note. Use existing anniversary/holiday data, cover VN occasions and Valentine/Christmas/anniversaries, and preserve fallback browsing. Rename template-based “AI” claims to personalized suggestions.  
    **Proof:** `app/src/test/java/com/example/domain/usecase/PersonalizedGiftSuggestionsTest.kt` **NEW** covers budget exclusion, upcoming occasions, missing profiles, no matching interests, offline use, and both languages.

27. **P1 — Share one suggestion through existing channels.**  
    **Files/symbols:** `app/src/main/java/com/example/data/repository/OnlineCoupleRepository.kt` — `sendGiftSuggestion` **NEW**; `app/src/main/java/com/example/data/sync/GiftSuggestionSyncAdapter.kt` — **NEW**; `app/src/main/java/com/example/data/sync/SyncCoordinator.kt`; `app/src/main/java/com/example/data/sync/SyncWorker.kt`; `app/src/main/java/com/example/ui/screens/GiftScreen.kt`; `firestore.rules`.  
    Add a small member-only suggestion record under the relationship, with sender, recipient, stable suggestion ID, message, and acknowledgment/pending state. Provide preview and explicit send; offer Android Sharesheet for guest/unpaired users. Share the selected recommendation without unnecessary birthday/profile details.  
    **Proof:** adapter/rules tests **NEW** and two-device delivery/retry checks; Sharesheet cancellation succeeds without marking anything delivered. No push or chat system is required.

28. **P1 — Establish one operational configuration source.**  
    **Files/symbols:** `app/src/main/java/com/example/domain/config/AppConfig.kt` — **NEW**; `app/src/main/java/com/example/data/config/FirebaseAppConfigRepository.kt` — **NEW**; `app/src/main/java/com/example/di/AppServiceLocator.kt`; `app/src/main/java/com/example/ads/AdsManagerImpl.kt`; `gradle/libs.versions.toml`.  
    Reuse one restored-plugin Remote Config fetch owner where suitable, or add one app-owned implementation. Expose immutable StateFlow defaults/cached values for ad master/banner/interstitial/App Open toggles, placement caps, feature kill switches, minimum/recommended version, and maintenance messaging; bound invalid values and apply updates to both app and SDK paths. Build-time IDs, consent, and verified entitlement remain authoritative.  
    **Proof:** `app/src/test/java/com/example/config/AppConfigTest.kt` **NEW** covers missing/malformed/stale config, offline startup, kill-switch activation, cap limits, and minimum-version UX.

29. **P1 — Complete only useful Firebase diagnostics.**  
    **Files/symbols:** `app/build.gradle.kts`; `gradle/libs.versions.toml`; `app/src/main/AndroidManifest.xml`; `app/src/main/java/com/example/InLoveApplication.kt`; `docs/SDK_INVENTORY.md` — **NEW**.  
    Record the restored module’s exact Remote Config/Analytics/Crashlytics/Messaging wiring and remove duplicate initialization. **DECISION — OWNER:** recommend consent-controlled Crashlytics with release mapping upload; keep Analytics and FCM off unless a defined v1 requirement exists. Firebase AI remains optional/disabled because current personalization works locally.  
    **Proof:** one test crash resolves in the intended Firebase project with readable mapping; denied diagnostics produce no diagnostic collection; debug App Check never appears in release.

30. **P2 — Add dark mode only after token coverage supports it.**  
    **Files/symbols:** `app/src/main/java/com/example/ui/theme/Theme.kt` — `DarkColorScheme`, `MyApplicationTheme`; `app/src/main/java/com/example/ui/theme/Color.kt`; `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`; `app/src/main/java/com/example/ui/util/Localization.kt` — theme preference **NEW**.  
    **DECISION — OWNER:** defer by default. If selected later, implement Light/Dark/System preference, complete semantic color pairs across every screen/dialog, and synchronize launch/system-bar behavior; retain branded colors unless dynamic color is explicitly approved.  
    **Proof:** full light/dark screenshot and contrast matrix, including wallpaper overlays and large text.

31. **P2 — Reduce proven maintenance overhead.**  
    **Files/symbols:** `app/proguard-rules.pro`; `app/src/main/java/com/example/MainActivity.kt` — `InLoveApp`; `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt` — `upgradeSubscription`.  
    Narrow broad keep rules incrementally after release coverage exists; memoize localized bundles appropriately and remove unused direct entitlement-upgrade paths after verifying callers. Keep architecture changes limited to code touched by the release work.  
    **Proof:** unit/lint checks, signed minified smoke tests, and unchanged selected-screen screenshots.

## Risks

- The absent SDK prevents a complete telemetry, mediation, native-library, Firebase, and release-build assessment.
- Empty-ID replacement may already have discarded fallback rows; reseeding can recover catalog entries, but previously lost user-created data may be unrecoverable.
- Identity/schema changes need upgrade testing and conservative handling of ambiguous legacy ownership.
- Pairing and deletion require deployed, tested server behavior; local unit tests alone cannot establish two-device correctness.
- Unsigned Cloudinary presets and public delivery URLs require console restrictions and accurate privacy promises. Client code must never contain administrative deletion credentials.
- UMP ad eligibility does not automatically authorize every analytics or telemetry purpose.
- Internal testing does not establish production eligibility; OWNER console configuration and applicable closed-testing requirements remain external gates.

## Out of scope

- A new launcher/lock-screen widget, additional navigation tabs, full chat, or new social features.
- A Firebase AI generation pipeline, trained AdsBrain rollout, web purchases, or broad monetization experiments.
- Automatic currency conversion, a large catalog expansion, or a complete lunar-calendar engine.
- A full ViewModel/navigation/DI rewrite, blanket dependency upgrades, or repository-wide visual redesign.
- Deployment, purchases, data deletion, credential rotation, or file modifications during this planning review.
