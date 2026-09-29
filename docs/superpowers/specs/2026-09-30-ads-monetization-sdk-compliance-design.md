# Design Spec: Ads & Monetization SDK Compliance (appplugin v1.6.2)

## Overview

`appplugin` was just updated from a 326-commit-old pinned checkout to v1.6.2
(fast-forward, no local commits lost). The new version already fixes the
consent-init-ordering problem this spec was originally going to patch itself
(`FcmConsent.holdAutoInitUntilConsent`, `SdkKillSwitch.restore`,
`AdSdkStartup.requestAnalytics` — all now run in the correct order inside
`AppPluginManager.initPlugin`, before this app existed against this SDK
version). What remains are problems in **how `:app` integrates with it**,
found by reading the current `AdsManagerImpl.kt`/`BillingManager.kt`
side-by-side with the SDK's own new standards docs
(`appplugin/DOC/60_SDK_LONG_TERM_STANDARDS.md`,
`DOC/62_AD_MONETIZATION_STANDARD.md`, updated `DOC/38_INTEGRATION_GUIDE.md`,
`DOC/47_SDK_INTEGRATION.md`):

1. **Dependency version skew.** `:app`'s own catalog pins
   `playServicesAds = "23.6.0"` (`gradle/libs.versions.toml:44`) while
   `appplugin/build.gradle.kts:103` now pulls Google Mobile Ads **25.4.0** —
   a version the SDK's own comment explains was bumped precisely because
   "GMA 25 removed APIs the 24.x adapters still call". A stale direct
   declaration in `:app` risks exactly that class of break the next time
   anyone touches `AdsManagerImpl.kt`.
2. **Mediation footprint mismatch with the SDK's own guidance.**
   `appplugin/DOC/62_AD_MONETIZATION_STANDARD.md:139` recommends AdMob
   mediation + Meta bidding for a utility-shaped app, no AppLovin MAX.
   `appplugin/build.gradle.kts` currently declares ~10 ad networks across
   both an AdMob mediation waterfall AND a full separate MAX integration —
   more than InLove needs and more than the SDK's own docs recommend for an
   app in InLove's category (InLove is not one of the SDK's three
   documented target apps — LAM2026, Speaker Clean, Invoice Maker — all
   games/utilities the mediation breadth was tuned for).
3. **`AdsManagerImpl.initialize()` replaces the whole `RequestConfiguration`
   instead of merging into it — a bug the SDK's own history already hit and
   fixed once, in code this app doesn't call.**
   `AdsManagerImpl.initialize()` (`app/src/main/java/com/example/ads/AdsManagerImpl.kt:115-130`)
   builds a fresh `RequestConfiguration.Builder().setTestDeviceIds(...)`
   and calls `MobileAds.setRequestConfiguration(...)` with it — which
   **replaces** the SDK's entire global configuration object, not merges
   into it. `appplugin/src/main/java/com/app/plugin/ads/adsmob/AdsMobMy.kt:106-129`
   documents, in a comment, the exact failure this causes: *"Bản trước gọi
   setRequestConfiguration() hai lần trên hai luồng... lần nào chạy sau thì
   xoá trường của lần kia. Hai app fork đã gặp cả hai chiều trên máy thật:
   phiên hiện 'Test Ad' [production traffic served test ads], và phiên mà
   cả 110 request mang gợi ý 'use setTestDeviceIds' [a debug build served
   real ads]."* The SDK's own fix for that historical bug, in the very
   same function, is `MobileAds.getRequestConfiguration().toBuilder()...` —
   read-modify-write instead of replace. `AdsManagerImpl.kt` never adopted
   that pattern and still does the replace form.
   `AdsMobMy.startNetwork()` — the function with the fix — currently never
   runs for InLove at all: it is only reached via
   `AdSdkStartup.requestAdNetworks(context)`, which only `AdsMobMy.instance.startSdkInit(context)`
   triggers, which nothing in `:app` calls today (see item 8) — so right
   now `AdsManagerImpl.initialize()`'s own `MobileAds.initialize()` call is
   the *only* thing that ever initializes GMA for this app. The moment item
   8's fix adds the missing `startSdkInit()` call, both this app's own init
   and appplugin's `startNetwork()` become live and can run in either order
   — at which point the replace-vs-merge bug becomes real for this app too,
   exactly as it already was for two other apps built on this SDK.
   (Calling `MobileAds.initialize()` itself more than once is fine — it is
   documented by Google as safe/idempotent, so no change is needed to that
   call, only to how the configuration is built before it.)
4. **No persisted entitlement cache for true offline-first behavior.**
   `BillingManager._isVipUser` (`app/src/main/java/com/example/billing/BillingManager.kt:61`)
   starts `false` and is only set from `queryExistingPurchases()`'s network
   response; `_hasSyncedOnce` (already shipped, `BillingManager.kt:74`)
   correctly prevents the previously-fixed "flashes free for 300ms" race,
   but there is still no on-disk cache — a VIP user who opens the app with
   no network at all sees `hasSyncedOnce` never become `true` this session,
   not "VIP" reliably shown. This is the one offline-first gap actually
   worth closing here (the race itself is already fixed).
5. **`brainRolloutFraction = 1.0`, decided.** Per the product decision
   already made: turn the brain off entirely (`brainEnabled = false`) rather
   than run a staged rollout — InLove is not a game, has no backend
   training/BigQuery pipeline deployed for it (per the SDK's own docs), and
   `:app`'s explicit 30-second interstitial interval cap
   (`AdsManagerImpl.kt:63,182,192`) is already the real, auditable gate.
6. **Mediation trim, decided.** Per the product decision already made: trim
   to AdMob mediation + Meta, matching `DOC/62`. Verified against
   `appplugin`'s actual source (not just its docs): the AdMob-side adapters
   for Pangle, Bigo, (AdMob's own) AppLovin adapter, Chartboost, Fyber,
   Vungle, Mintegral and Unity have **zero** direct source references
   anywhere in `appplugin/src/main/java` (`grep -rl "^import com\.(pangle|bigossp|chartboost|fyber|vungle|mbridge|unity3d)"`
   returns nothing) — they self-register purely via AdMob's manifest
   mechanism, so removing their Gradle dependencies is a clean, compile-safe
   trim with no source changes. The separate AppLovin MAX integration is
   **not** safe to remove the same way: `appplugin/src/main/java/com/app/plugin/ads/adsmax/`
   (12 files: `AdsMaxMy.kt` and 11 `AdsMaxPl*`/`AdsMaxNt*View.kt` classes)
   and part of `AdsHelper.kt` directly `import com.applovin.*` at compile
   time — removing `com.applovin:applovin-sdk` would break compilation of
   those files, which is source surgery on the shared SDK, not a dependency
   trim. **This spec removes only the 8 AdMob-side non-Meta adapters now**
   and leaves MAX in place but unused (InLove's own `AdsManagerImpl` never
   calls `AdsMaxMy` or anything else in that package — confirmed by reading
   its imports — so MAX sits inert; stripping it fully is a separate,
   optional follow-up if the APK-size cost ever matters enough to justify
   touching 12 files of a SDK shared with other apps).
7. **Missing "Quản lý quyền riêng tư" (privacy options) entry.**
   `appplugin/DOC/47_SDK_INTEGRATION.md`'s diff since this app's old pin
   adds a requirement: show a menu item gated on
   `ConsentManager.isPrivacyOptionsRequired(context)` that calls
   `ConsentManager.showPrivacyOptions(activity)` — not present anywhere in
   `SettingsScreen.kt` today. This is a real GDPR/CCPA UX gap: a user who
   granted or denied consent once currently has no way to reopen that choice.
8. **`AdsMobMy.instance.startSdkInit(applicationContext)` never called.**
   `DOC/47`'s diff states this is unconditionally required from every
   integrator's first visible Activity since 1.3.0. InLove's blast radius
   from skipping it is limited (InLove doesn't serve ads through
   appplugin's own ad-serving path at all, only through its own direct
   `MobileAds`/`InterstitialAd`/`AppOpenAd` calls), but it is a real,
   low-cost compliance gap worth closing — and it is the call that starts
   the SDK's own consent-aware `AdSdkStartup` flow, which the GPP mechanism
   (item 10) depends on running.
9. **R8 is ready, contrary to earlier concern.** The ClassValue/R8 issue
   flagged in earlier research is already fully handled:
   `appplugin/consumer-rules.pro:67-86` ships the fix
   (`-assumevalues ... CachingKt { static boolean useClassValue return false }`)
   and is auto-merged into `:app`'s R8 config via `consumerProguardFiles`
   the moment `:app` sets `isMinifyEnabled = true` — no manual proguard
   rule needed for this specific issue. This spec includes turning R8 on,
   since nothing found here still blocks it.
10. **GPP (US state privacy) needs one console setting, not code.** The
    new `GppConsent`/`GppSignals` mechanism (1.6.0) only does anything once
    the AdMob console's "US states" privacy message is enabled alongside
    GDPR for InLove's app entry — a one-time console change, not part of
    this spec's code changes, called out here so it isn't missed.

## Part A — Dependency hygiene

- **Stop declaring `play-services-ads` directly in `:app`.** `:app`
  currently has its own `libs.google.play.services.ads` reference
  (`gradle/libs.versions.toml:44,109`, pinned at 23.6.0) purely so
  `AdsManagerImpl.kt` can import `com.google.android.gms.ads.*`. Since
  `:app` already depends on `:appplugin` (`implementation(project(":appplugin"))`,
  `app/build.gradle.kts:146`), which transitively provides GMA 25.4.0, the
  direct declaration is redundant and is the ONLY reason a skew like this
  can recur — Gradle already resolves both to the higher version today
  (25.4.0) via normal conflict resolution, so `:app`'s compile classpath is
  arguably already 25.4.0 in practice, but the stale catalog entry misleads
  the next person who reads it into thinking 23.6.0 is what's compiled.
  Remove the direct dependency line from `app/build.gradle.kts` and the
  now-unused catalog entries from `gradle/libs.versions.toml`; keep the
  `google-play-services-ads` catalog entry only if some other `:app` file
  needs it directly (grep first — `AdsManagerImpl.kt` is the only user
  found).
- **Trim `appplugin/build.gradle.kts`'s 8 unused AdMob-side mediation
  adapters** (Pangle, Bigo, AdMob's AppLovin adapter, Chartboost, Fyber,
  Vungle, Mintegral, Unity + the standalone Unity Ads SDK) — the exact
  lines are cited in Part A's task below. This is a change to InLove's own
  local `appplugin/` checkout only; it is not pushed to the shared
  `dungduong2899/appplugin` upstream (that decision, and whether other apps
  sharing that SDK want the same trim, belongs to the SDK owner separately —
  out of scope here).
- **AppLovin MAX stays, deferred.** Documented above (Overview item 6) —
  no action in this spec.

## Part B — Single entitlement source of truth + persisted offline cache

- Add `EntitlementRepository` (new, `app/src/main/java/com/example/billing/EntitlementRepository.kt`)
  wrapping `BillingManager.isVipUser`/`hasSyncedOnce`/`activeProductId` with
  one additional piece: a `SharedPreferences`-backed last-known-VIP cache
  (`key_last_known_vip: Boolean`, `key_last_known_product_id: String?`),
  written every time `queryExistingPurchases()` gets a real answer
  (`_hasSyncedOnce` becomes true), read once at construction so the exposed
  `StateFlow<Boolean>` starts at the last real answer instead of `false`
  when there is no network yet this session. `AdsManagerImpl`/paywall gating
  code that currently reads `BillingManager.isVipUser` directly switches to
  reading `EntitlementRepository.isVipUser` instead — same `StateFlow<Boolean>`
  shape, so this is a drop-in swap at each call site, not a new UI contract.
- This repository is the one and only place that calls
  `com.app.plugin.iap.Entitlements.grant/revoke/sync` — `BillingManager`
  keeps calling it exactly as today (already the only caller,
  `BillingManager.kt:336,352,377`); `EntitlementRepository` does not
  duplicate that call, it only adds the persistence layer around
  `BillingManager`'s existing StateFlow. No second entitlement writer is
  introduced.

## Part C — `RequestConfiguration`: merge, never replace

`AdsManagerImpl.initialize()` keeps calling both `MobileAds.setRequestConfiguration(...)`
and `MobileAds.initialize(context) { ... }` exactly as it does today — calling
`MobileAds.initialize()` more than once (this app's own call, plus
appplugin's `AdsMobMy.startNetwork()` once Part D's `startSdkInit()` call
makes that path live too) is documented by Google as safe and idempotent,
so there is nothing to remove or restructure there. The one real fix,
precisely scoped by the Overview item 3 finding: change
`RequestConfiguration.Builder()` (a fresh, empty builder) to
`MobileAds.getRequestConfiguration().toBuilder()` (read-modify-write) before
adding `setTestDeviceIds(...)`, in `AdsManagerImpl.initialize()` — the exact
pattern `AdsMobMy.startNetwork()` already uses, for the exact reason its own
comment documents. Whichever of the two init paths' `setRequestConfiguration`
call happens to run second now preserves the other's fields instead of
erasing them, regardless of order.
- **Call `AdsMobMy.instance.startSdkInit(applicationContext)`** from
  `MainActivity.onCreate` (the first visible Activity, per `DOC/47`'s
  requirement), guarded the same way every other appplugin call in this
  codebase already is (`runCatching`/try-catch with `Log.d`).

## Part D — Disable the brain, add the privacy-options entry

- `InLoveApplication.kt:49-50`: `brainEnabled = true` → `false`;
  `brainRolloutFraction` becomes irrelevant once disabled but is left at
  its current value rather than removed, so re-enabling later is a
  one-line flip with an explicit value already in place, not a guess.
  `AdsManagerImpl.kt:186`'s `if (AdsBrain.enabled && ...)` already
  short-circuits correctly once `AdsBrain.enabled` reflects this — no
  `:app` code change needed there beyond the config flip.
- Add a "Quyền riêng tư quảng cáo" row to `SettingsScreen.kt` (alongside
  the existing language/notification rows), visible only when
  `ConsentManager.isPrivacyOptionsRequired(context)` is true, calling
  `ConsentManager.showPrivacyOptions(activity)` on tap.

## Part E — Enable R8 for release

- `app/build.gradle.kts:76`: `isMinifyEnabled = false` → `true`, add
  `isShrinkResources = true`. Per Overview item 9, no new proguard rule is
  required for the previously-flagged ClassValue issue — it is already
  handled transitively. Post-build verification step: `grep -c ClassValue mapping.txt`
  must be 0, matching the exact release-gate check `appplugin`'s own
  `tools/release_gate.sh` already runs on itself (`appplugin/consumer-rules.pro:81-82`
  comment references this same check).
- `app/proguard-rules.pro`'s existing broad keep-alls
  (`-keep class com.google.firebase.** { *; }`,
  `-keep class com.google.android.gms.ads.** { *; }`, etc.) are left as-is
  for this spec — they are safe (over-broad, not wrong) and narrowing them
  for extra shrink/rename coverage is a separate, optional cleanup, not a
  correctness requirement for turning R8 on.

## Error Handling

- Every new appplugin call site (`startSdkInit`, `ConsentManager.showPrivacyOptions`)
  is wrapped exactly like every existing one in this codebase
  (try/catch or `runCatching`, logged, never crashes the caller) — no new
  error-handling pattern is introduced.
- `EntitlementRepository`'s persisted cache is best-effort: a
  `SharedPreferences` read/write failure falls back to the in-memory
  `BillingManager` state exactly as today, never blocks or crashes.

## Testing

- Unit: `EntitlementRepository` — persisted cache is written on a real
  `hasSyncedOnce` transition and correctly seeds the initial `StateFlow`
  value from a prior session's cached preference on construction.
- Unit/Robolectric: `RequestConfiguration` merge logic — building from an
  existing non-default configuration preserves its `childDirected`/rating
  fields while adding test device IDs (no live `MobileAds` call needed to
  test the pure merge function once it's factored out).
- Manual/build: `:app:assembleRelease` with R8 on, `grep -c ClassValue mapping.txt` = 0.
- Manual: two-device dependency check — `./gradlew :app:dependencies --configuration debugRuntimeClasspath`
  confirms only one resolved `play-services-ads` version (25.4.x) and that
  the 8 trimmed mediation adapters no longer appear in the resolved graph.

## Global Constraints

- No change to `:app`'s public `AdsManager` interface signatures (matches
  the existing constraint already honored by the earlier 2026-03-31
  ads/Firebase hardening plan).
- The AppLovin MAX removal is explicitly out of scope for this spec/plan —
  do not attempt it as a "while I'm in here" addition.
- Changes to `appplugin/build.gradle.kts` apply only to InLove's local
  checkout; nothing here is pushed to the shared `dungduong2899/appplugin`
  GitHub repository.
