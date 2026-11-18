# InLove Monetization, Firebase & Cross-Platform SDK Architecture Analysis

This document analyzes the production monetization, Remote Config, and backend setup across **Firebase** (`demngayyeuandroid`, region `asia-southeast1`), **Android (`:app` + `:appplugin`)**, and **iOS (`iosApp` + `monetization-brain-ios` + `:shared`)**.

---

## 1. Firebase Stack (`demngayyeuandroid`, `asia-southeast1`)

| Service | Configuration Source | Role & Guardrails |
|---|---|---|
| **Auth** | `firebase.json` (`emailPassword: true`), Firebase Console | Email/password + Facebook OAuth Web Custom Tab flow (`facebook.com` provider; zero Facebook SDK or secret embedded in APK). Guest users fall back gracefully to local Room/JSON state. |
| **App Check** | `InLoveApplication.installAppCheckProviderFactory()`, `firebase/functions/index.js` | Every Cloud Function callable sets `enforceAppCheck: true`. Production uses Play Integrity on Android (and App Attest/DeviceCheck on iOS). Must be initialized before `AppPluginBase.onCreate()` so early Remote Config/Firestore traffic carries App Check tokens. |
| **Firestore** | `firestore.rules`, `firestore.indexes.json` (`mediaQuota.expiresAt` TTL) | Catalogs (`gift_ideas`, `milestone_presets`, `badge_definitions`, `preset_assets`, `checklist_templates`) are read-only for clients; writes are denied globally. Couple spaces are gated to `ACTIVE` members. Rate-limit counters live under `mediaQuota/rl_*` (48h TTL). |
| **Cloud Functions** | `firebase/functions/index.js`, `src/config.js`, `src/entitlement/gatePolicy.js`, `src/aiAdvisor.js` | Node 22 callables (`getAccountToken`, `claimPurchase`, `getEntitlement`, `acceptCoupleInvite`, `requestAccountDeletion`, `signMemoryUpload`, `confirmMemoryUpload`, `getMemoryMediaUrl`, `generateGiftAdvice`), Apple ASSN V2 (`appleNotifications`), Play RTDN (`googlePlayNotifications`), and account cleanup (`releaseEntitlementBindings`). |
| **AI Concierge Quotas** | `firebase/functions/src/aiAdvisor.js` | `generateGiftAdvice` is gated as `tiered` (`freeAllowed: true`). Free users receive **2 starter requests / UTC month** tracked in Firestore `aiAdvisorQuota/{uid}_{YYYYMM}`; VIP users receive **60 requests / UTC month**. Exhausting free quota throws `HttpsError('permission-denied', 'vip-required')`. Calls Gemini `gemini-2.5-flash` structured JSON when `GEMINI_API_KEY` secret is present and falls back cleanly to `source: 'server_smart_engine'` otherwise. Client guest/offline mode uses `:shared` `SmartRuleGiftAdvisor` via `HybridAiGiftAdvisorRepository`. |
| **Remote Config** | `firebase.json` -> `firebase/remoteconfig.template.json` | Defines all 15 verified JSON parameters across Android `:appplugin` (6 keys) and iOS `monetization-brain-ios` (9 keys). |

---

## 2. Android `:appplugin` SDK Setup & Ed25519 Licensing

### 2.1 Initialization Order (`InLoveApplication.kt`)
1. `FirebaseApp.initializeApp(this)` and `installAppCheckProviderFactory()` run first.
2. `AppServiceLocator.initialize(this)` wires `AdsManagerImpl`, `BillingManager`, and `EntitlementRepository`.
3. `super.onCreate()` (`AppPluginBase`) initializes `:appplugin` internals (`FirHelperConfig` fetches Remote Config with `minimumFetchIntervalInSeconds = 10L` in debug, `3600L` in release).
4. `MonetizationSdk.configure(this)` binds canonical billing product IDs without duplicating purchase flows:
   - `inappProducts = [VipProductIds.LIFETIME]` (`"vip_lifetime"`)
   - `subsProducts = [VipProductIds.MONTHLY, VipProductIds.YEARLY]` (`"vip_monthly"`, `"vip_yearly"`)
   - `removeAdsProducts = {"vip_monthly", "vip_yearly", "vip_lifetime"}`
   - `brainEnabled = false` (`AdsManagerImpl` + `AdPlacementPolicyGate` enforce deterministic win-win pacing rather than bandit decisions until BigQuery pipelines exist).

### 2.2 Ed25519 License Verification (`com.app.plugin.license`)
- Manifest placeholder `${appPluginLicense}` injects `com.app.plugin.LICENSE` (`ALP1.<payload>.<sig>`) supplied via Gradle property `APPPLUGIN_LICENSE` (`app/build.gradle.kts:44`), keeping secrets out of Git.
- Signature verification uses RFC 8032 PureEdDSA (`Ed25519.kt`) off the main thread (`appplugin-license`) with cold-start result caching (`LicenseCache`).
- **Key rotation requirement**: Demo key `kid=dev1` only authenticates debug/demo builds bound to the local debug certificate (`appplugin/README.md`). Before any Play Store release of `com.aistudio.inlove.kmrv`, issue a production token bound to BOTH the upload key SHA-1/SHA-256 and Google Play App Signing certificate using `appplugin/tools/license_issue.py`, otherwise `:appplugin` degrades fail-safe to `license: INVALID` (ads suppressed).

### 2.3 `ads_ids.xml` Debug vs. Release Strategy
- `app/src/debug/res/values/ads_ids.xml` sets `flagTest=1` and Google's official sample test ad unit IDs (`ca-app-pub-3940256099942544/*`) plus `<string-array name="ads_collapsible_placements"><item>bn_collapsible</item></string-array>` for local development and Robolectric unit tests.
- `app/src/main/res/values/ads_ids.xml` provides the safe baseline for release builds: `flagTest=0` (disables AdMob test device flags and debug logging in `AppUtil.isTestFlagOn`) with empty fallback ad unit strings and empty `ads_collapsible_placements`. No Google sample ad IDs live in `main`. Production AdMob unit IDs should be supplied via `app/src/release/res/values/ads_ids.xml` or Firebase Remote Config `cf_ids_placement` prior to store rollout.

---

## 3. iOS `monetization-brain-ios` SDK, StoreKit 2 & Privacy

### 3.1 Modular SwiftPM Architecture (`monetization-brain-ios/Package.swift`)
All four targets compile under Swift 6 `StrictConcurrency`:
1. **`MonetizationBrainCore`**: Pure Swift logic (`AdsBrain`, `BrainPolicy`, `HoldoutManager`, `PaywallManager`, `PurchaseRouter`, `SubscriptionLifecycle`, `BattlePass`, `RecurringGrants`, `OfferwallManager`, `CrmBridge`, `RemoteConfigDispatcher`).
2. **`MonetizationBrainStoreKit`**: Live `StoreKit2Backend` for App Store transactions, renewal states, and restoration (`InLove.storekit`).
3. **`MonetizationBrainUMP`**: `ConsentManager` wrapping `GoogleUserMessagingPlatform` (`@MainActor` safe) for EEA/UK GDPR CMP flows.
4. **`MonetizationBrainFirebase`**: `FirebaseRemoteConfigProvider` feeding `RemoteConfigDispatcher.shared.refresh(from:)`.

### 3.2 StoreKit 2, ATT & SKAdNetwork Readiness
- **StoreKit 2 & Server Entitlements**: `iosApp/Config/Base.xcconfig` targets iOS 17.0 (`com.aistudio.inlove.kmrv`, Swift 5.10). `iosApp/StoreKit/InLove.storekit` defines `vip_monthly`, `vip_yearly` (with 3-day free trial `P3D`), and `vip_lifetime`. Production purchase verification routes exclusively through Cloud Functions (`getAccountToken` -> `appAccountToken` UUID -> `claimPurchase` + `appleNotifications` ASSN V2) and `purchase_routing_ios` routes all three SKUs strictly to `"app_store"` (`web_shop.enabled = false`).
- **UMP vs. ATT & SKAdNetwork**: Because `iosApp/InLoveIos/Info.plist` currently does not embed ad networks or request IDFA tracking (`NSUserTrackingUsageDescription` and `SKAdNetworkItems` are intentionally omitted while iOS runs ad-light / StoreKit-focused monetization), no ATT prompt or `SKAdNetworkItems` declaration is needed in v1. If Google Mobile Ads (`GADApplicationIdentifier`) or third-party bidding SDKs are added on iOS later, add `SKAdNetworkItems` (Google + Meta SKAdNetwork IDs), `NSUserTrackingUsageDescription`, and present UMP consent BEFORE calling `ATTrackingManager.requestTrackingAuthorization` and `AdsBrain.configure`.

---

## 4. Geo-Tiered Ad Caps & Sacred Zones (`Win-Win Monetization`)

### 4.1 Sacred Zones (`:shared` `SacredZone` + `AdPlacementPolicyGate`)
Ads are **unconditionally blocked** (across banner, interstitial, and app-open placements) when:
- The user holds an active VIP entitlement (`isVipUser == true`).
- UMP consent denies ad requests (`canRequestAds == false`) or `sdk_kill_*` (`ads: true`) / `:appplugin` license check is inactive.
- The user is inside any `SacredZone`:
  - `ONBOARDING`
  - `PAYWALL`
  - `PIN_LOCK`
  - `MILESTONE_CELEBRATION_DAY` (anniversary/milestone celebration screens must never show an ad)
  - `MEMORY_CAPTURE_FLOW`
  - `PAIRING_ACCEPT_FLOW`

### 4.2 Session Pacing & Geo-Tiered Policy (`ads_policy_android`)
`:shared`'s `AdPlacementPolicy` enforces a **60s warmup** after app launch, **120s minimum cooldown** between interstitials, and a **3-interstitial session cap**. Remote Config `ads_policy_android` (`schema_version: 1`) mirrors and refines this per country tier (`AdsPolicyTiers.COUNTRY_TIER`):

| Tier | Markets | Interstitial First Session | App Open From Session | Min Interstitial Gap | Screens Cooldown | Post-Click Cooldown | Rationale |
|---|---|---|---|---|---|---|---|
| **T1** | US, CA, GB, AU, DE, FR, NL, Nordics, CH | Blocked (`true`) | Session 2 | 120s | 3 | 5s | High eCPM enables revenue without aggressive frequency. |
| **T2** | EU-2, JP, KR | Blocked (`true`) | Session 2 | 120s | 3 | 5s | Balanced pacing aligned with `AdPlacementPolicy`. |
| **VN** | Vietnam (primary couple base) | Blocked (`true`) | Session 3 | 180s | 4 | 8s | Protects organic couple retention, ratings & word-of-mouth referrals. |
| **OTHER** | Rest of World | Blocked (`true`) | Session 2 | 150s | 4 | 6s | Conservative global baseline. |

In addition, `brain_policy_android` and `brain_policy_ios` keep `enabled: false` (matching `InLoveApplication`) while clamping `HARD_MAX_*` guardrails (`max_ads_24h: 12`, `max_interstitial_per_session: 3`, `reward.weight_continuation: 0.7 > weight_immediate_value: 0.3`).

---

## 5. Subscription Packaging, Pricing & Holdout Experimentation

### 5.1 Canonical SKU Parity (ARCH-04)
All platforms, billing clients, and Remote Config templates enforce identical canonical SKU IDs (`VipProductIds`, `ProTierCatalog`, `InLove.storekit`, `firebase/remoteconfig.template.json`):

| SKU ID | Type | Play VN / StoreKit Ref Price | Anchor Strategy | Features Unlocked (`ProFeature`) |
|---|---|---|---|---|
| `vip_monthly` | `SUBS` (`P1M`) | `39.000₫` / `$2.99 / mo` | Low-friction monthly entry (`~1.300₫/ngày`) | Ad-free experience, 60/month AI Gift Concierge calls, full private dossier backup, unlimited secret hints. |
| `vip_yearly` | `SUBS` (`P1Y`) | `199.000₫` (~`16.500₫/tháng`) / `$17.99 / yr` (`$1.50/mo`, 3-day free trial `P3D`) | **Primary Recommended Anchor** (**Save 57%** vs. 12× monthly) | Same full VIP benefits + 3-day trial removes anxiety before an upcoming anniversary. |
| `vip_lifetime` | `INAPP` (Non-Consumable) | `399.000₫` / `$39.99` one-time | Long-term couple anchor (`2×` annual price) | Permanent VIP across the couple's relationship without recurring billing friction. |

### 5.2 Paywall Placement & Holdout Design (`paywall_config_*`)
- `paywall_config_android` uses `schema_version: 2` with an isolated **`5%` holdout** (`holdout.fraction: 0.05` on `paywall_holdout_v2`, independent of brain holdout) to measure true incremental conversion lift vs. no paywall.
- **User-initiated placements** (`settings_vip`, `gift_ai_limit` mapped to `DecisionPoint.CURRENCY_LOW`) set `proactive: false` so users clicking Upgrade or hitting the free 2/month AI limit are never blocked by session caps.
- **Proactive soft nudges** (`post_gift_explore` -> `proactive: true`) are capped at `session_cap: 2` per process and never triggered inside `SacredZone`s.
- **Dunning & Win-back**: `subscription_lifecycle_android` & `subscription_lifecycle_ios` enable `dunning_cadence: [1, 2, 4]` (gentle grace-period reminders on app opens 1, 2, and 4 + Play `in_app_messages`) and `win_back_enabled: true`.

---

## 6. Remote Config Inventory (`firebase/remoteconfig.template.json`)

- **Android (`:appplugin`)**: `sdk_kill_android`, `ads_policy_android`, `brain_policy_android`, `paywall_config_android`, `subscription_lifecycle_android`, `ads_bidding_partners_android` (`["meta"]`).
- **iOS (`monetization-brain-ios`)**: `sdk_kill_ios`, `brain_policy_ios`, `paywall_config_ios`, `purchase_routing_ios`, `subscription_lifecycle_ios`, `battle_pass_ios` (dormant past window for safe parser validation), `recurring_grants_ios` (`enabled: false`), `offerwall_config_ios` (`enabled: false`), `crm_bridge_ios` (`enabled: false`).

---

## 7. Pricing Research & Online Synchronization Architecture Audit

### 7.1 Market Benchmarks & Unit Economics (`VN` + `T1/T2` Geo Segments)

InLove targets romantic couples seeking a calm, distraction-free companion rather than an ad-heavy utility. Pricing is structured around Vietnam purchasing power (`VN` anchor) and Tier-1/Tier-2 App Store / Google Play utility norms (`T1/T2`):

| Segment / SKU | Price Anchor | Monthly Equivalent | Target Audience & Unit Economics |
|---|---|---|---|
| **`vip_monthly`** (`P1M`) | `39.000₫` (VN) / `$2.99` (T1/US) | `39.000₫` / `$2.99` | Impulse entry (< cost of one milk tea / coffee date in VN). Covers Play/App Store 15–30% fee + AI Concierge API headroom while intentionally keeping 12× monthly cost (`468.000₫` / `$35.88`) above annual to drive annual upgrades. |
| **`vip_yearly`** (`P1Y`, `P3D` trial) | `199.000₫` (VN) / `$17.99` (T1/US) | `~16.500₫` / `$1.50` | **Primary Recommended Anchor** (**57% savings** vs. monthly). Matches long-horizon couple milestones (`100`, `365`, `730` days), stabilizes LTV, lowers churn vs. monthly, and yields healthy gross margin after 15% store fee and average AI Concierge token usage (<`$0.15/yr` per typical active user capped at 60/month). |
| **`vip_lifetime`** (`INAPP`) | `399.000₫` (VN) / `$39.99` (T1/US) | One-time (`2×` annual) | Captures high-intent long-term couples who dislike recurring subscriptions; `2×` annual price preserves 2-year LTV upfront without requiring subscription dunning. |

> **Store Pricing Authority**: Play Console live `ProductDetails` (and StoreKit 2 `Product` prices on iOS) remain the single runtime source of truth for formatted local currency; `:shared` `ProPlanSpec` reference prices serve only as fallback copy when offline or in preview fixtures.

### 7.2 Online Feature Architecture Audit

1. **Identity & Account Scoping (`AuthRepository` + `AccountDataVault`)**:
   - Firebase Auth (`emailPassword`, Facebook Web OAuth via Custom Tabs, PIN lockout with PBKDF2 120k) gates online identity.
   - Changing accounts runs `AccountDataVault.switchTo(scope)` **before** flipping `AuthState` and increments `AccountDataVault.epoch`, fencing stale sync snapshots from writing into another account's Room tables or local dossier JSON files.
2. **Two-Tier Realtime Couple Sync (`SyncCoordinator`)**:
   - **Identity tier**: listens to `relationships` (`Filter.or(user1 == uid, user2 == uid)`) and `invites` (`targetUid == uid`) for the entire authenticated session via a single serial coroutine `Channel`.
   - **Content tier**: attaches listeners for `relationships/{id}/memories` and `anniversaries` ONLY when `DataResidencyPolicy.cloudContent` is active (`LOCAL_ONLY` keeps memories/photos local-first unless enabled) and `status == ACTIVE`.
3. **Public Profile Last-Write-Wins Sync (`ProfileSync`)**:
   - Syncs `/users/{uid}` (name, birthday, gender, bio, interestsCsv, schemaVersion, updatedAt) under strict owner-only Firestore rules (`firestore.rules:61-83`).
   - Uses `synced_<uid>` hash comparison ("dirty local wins first sync; remote wins when clean").
4. **Owner-Only Private Partner Dossier Cloud Sync (`CloudPartnerDossierSync` + `firestore.rules`)**:
   - Persists serialized `PartnerDossier` JSON to `/users/{uid}/privateDossier/current` (`schemaVersion = 1`, max `32,768` bytes, `updatedAtEpochMs`, server `updatedAt`).
   - **Privacy boundary**: Firestore rules (`match /users/{userId}/privateDossier/{docId}`) strictly verify `docId == 'current' && isOwner(userId)` with `allow list: if false` and **zero** `isRelationshipMember` read grants — guaranteeing a user's `secretSurpriseNotes` stay invisible to their partner even when paired!
   - **Conflict resolution**: Pull + merge uses `:shared`'s deterministic `DossierMergePolicy.merge(local, remote)` (newer `updatedAtEpochMs` wins scalar profile fields while deterministically union-merging `secretSurpriseNotes` and `giftHistory`). Guest mode and offline sessions degrade silently to atomic `filesDir/dossier/` JSON files via `LocalJsonPartnerDossierRepository`, then debounced-push when connectivity returns.
5. **Network Reconnect Recovery (`NetworkMonitor` + `InLoveViewModel`)**:
   - Process-wide `NetworkMonitor.isOnline` triggers automatic `onlineRepo.refreshState()` and `cloudDossierSync.syncNow()` whenever connectivity transitions `false -> true`.
   - `InLoveViewModel.onlineSyncPhase` exposes a unified `:shared` `OnlineSyncPhase` (`GUEST_LOCAL`, `SIGNED_IN_SOLO`, `INVITE_OUTGOING`, `INVITE_INCOMING`, `COUPLED_SYNCED`, `COUPLED_OFFLINE_QUEUE`, `BREAKUP_PENDING`) so Pairing and Settings views always reflect live sync status cleanly.
6. **Server-Side Cloud Functions (`asia-southeast1`, `enforceAppCheck: true`)**:
   - Relationship binding (`acceptCoupleInvite`), account purge (`requestAccountDeletion`, `releaseEntitlementBindings`), R2 private media presigning (`signMemoryUpload`, `confirmMemoryUpload`, `getMemoryMediaUrl`), billing verification (`getAccountToken`, `claimPurchase`, `getEntitlement`, `appleNotifications`, `googlePlayNotifications`), and tiered AI Gift Concierge (`generateGiftAdvice`) all execute server-side behind per-UID rate limits (`cfg.rateLimits`) and Firestore entitlement/quota guards.
