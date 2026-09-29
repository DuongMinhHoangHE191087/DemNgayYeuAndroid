# Ads & Monetization SDK Compliance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the `RequestConfiguration` overwrite bug, close the dependency version skew, trim InLove's mediation footprint to match the SDK's own guidance, add a persisted offline entitlement cache behind one `EntitlementRepository`, close two compliance gaps (`startSdkInit`, privacy-options UI), disable the unrolled-out AdsBrain, and turn on R8.

**Architecture:** No new architectural layer beyond one small `EntitlementRepository` sitting in front of `BillingManager`'s existing StateFlows, persisting the last known answer to `SharedPreferences`. Everything else is a targeted fix to existing files: a Gradle dependency cleanup, a one-line `RequestConfiguration` change, one new call (`startSdkInit`), one new Settings row, one config flip (`brainEnabled`), and one build-type flag (`isMinifyEnabled`).

**Tech Stack:** Kotlin, Gradle version catalog, Google Mobile Ads SDK 25.4.x, Firebase App Check/UMP, Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-30-ads-monetization-sdk-compliance-design.md`

## Global Constraints

- No change to `:app`'s public `AdsManager` interface signatures.
- AppLovin MAX removal is out of scope — do not touch `appplugin/src/main/java/com/app/plugin/ads/adsmax/` or `com.applovin:*` dependency lines.
- Changes to `appplugin/build.gradle.kts` apply only to this repo's local `appplugin/` checkout; nothing here is pushed to the shared `dungduong2899/appplugin` GitHub repository.
- `EntitlementRepository` becomes the only thing `:app` UI code reads for VIP status going forward; `BillingManager.isVipUser`/`hasSyncedOnce`/`activeProductId` stay as they are (still the only caller of `Entitlements.grant/revoke/sync`), just no longer read directly outside `EntitlementRepository`.

## Review Focus

1. **Cold start with no network, on a device that was VIP last session.** `EntitlementRepository.isVipUser` must read `true` immediately (from the persisted cache), not wait for `BillingManager.hasSyncedOnce`, which may never become true this session. Covered by Task 2's `EntitlementRepositoryTest`.
2. **`RequestConfiguration` set from two places in either order.** Whichever of `:app`'s own `AdsManagerImpl.initialize()` and appplugin's `AdsMobMy.startNetwork()` runs its `setRequestConfiguration` call second must not erase the first's fields (this is the exact historical bug `AdsMobMy.kt`'s own comment documents). Covered by Task 3's merge-logic test.
3. **`billingManager.isVipUser` still read directly somewhere the trim missed**, leaving two different "am I VIP" answers live in the app simultaneously. Covered by Task 2 Step 5's exhaustive grep across `app/src/main/java`, not just the three sites already known.
4. **A removed mediation adapter dependency was silently required by something else** (a transitive need from Meta's adapter, or from Firebase). Covered by Task 1 Step 4's full dependency-resolution + compile check.
5. **`ConsentManager.isPrivacyOptionsRequired(context)` called before any consent flow has ever run in this process** (a cold Settings-screen open via deep link, before `MainActivity` calls `requestConsentAndInitialize`). Must return `false` safely, not throw — already true of the real implementation (`ConsentManager.kt:151-157`, wrapped in `try/catch` returning `false`), pinned by Task 4's test so a future SDK change can't silently regress it.

---

### Task 1: Dependency hygiene — fix version skew, trim unused mediation adapters

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `appplugin/build.gradle.kts`
- Test: manual dependency-resolution check (no unit test — this task has no runtime logic to test, only a resolved dependency graph)

**Interfaces:**
- Consumes: nothing.
- Produces: a single resolved `play-services-ads` version across `:app`/`:appplugin`; a trimmed `appplugin` dependency graph. Nothing else in this plan depends on this task's internals, only on the app still compiling afterward.

- [ ] **Step 1: Remove `:app`'s direct `play-services-ads` dependency**

In `app/build.gradle.kts`, remove line 192:
```kotlin
  implementation(libs.google.play.services.ads)
```
`AdsManagerImpl.kt` imports `com.google.android.gms.ads.*` directly, but `:appplugin` (`implementation(project(":appplugin"))`, line 146, already present) already provides this transitively at 25.4.0 — removing the direct line means there is exactly one place (`appplugin/build.gradle.kts:103`) declaring this version, so it cannot skew again.

In `gradle/libs.versions.toml`, remove the now-unused version and library entries:
```toml
playServicesAds = "23.6.0"
```
(line 44) and
```toml
google-play-services-ads = { group = "com.google.android.gms", name = "play-services-ads", version.ref = "playServicesAds" }
```
(line 109). First confirm no other `:app` file references `libs.google.play.services.ads` or `libs.playServicesAds` (`grep -rn "google.play.services.ads\|playServicesAds" app/src` — `AdsManagerImpl.kt` importing the `com.google.android.gms.ads` *package* is unaffected by removing the Gradle *catalog alias*; only a second `implementation(libs.google.play.services.ads)` elsewhere would break).

- [ ] **Step 2: Trim the 8 unused AdMob-side mediation adapters in `appplugin/build.gradle.kts`**

Remove these lines (all confirmed to have zero direct source references anywhere in `appplugin/src/main/java` — they self-register purely via AdMob's own manifest mechanism):
```kotlin
    implementation("com.google.ads.mediation:pangle:8.1.0.3.1")
    implementation("com.bigossp:admob-mediation:5.7.0.0")
    implementation("com.google.ads.mediation:applovin:13.6.2.1")
    implementation("com.google.ads.mediation:chartboost:9.12.1.1")
    implementation("com.google.ads.mediation:fyber:8.4.5.1")
    implementation("com.google.ads.mediation:vungle:7.7.4.2")
    implementation("com.google.ads.mediation:mintegral:17.1.61.1")
    implementation("com.google.ads.mediation:unity:4.19.0.0")
    implementation("com.unity3d.ads:unity-ads:4.19.0")
```
(the exact 9 lines at `appplugin/build.gradle.kts:118, 123, 126, 127, 128, 129, 136, 137, 139` — one more than "8" in the spec's count because `com.unity3d.ads:unity-ads` and `com.google.ads.mediation:unity` are two separate lines for the same network; line 119 is a Bigo-explaining comment and line 138 is the already-commented-out Tapjoy line — both stay untouched, match by exact text content, not by line range, when editing). Keep everything else, including the `com.google.ads.mediation:facebook`/`com.facebook.android:audience-network-sdk` lines (Meta stays, per the spec) and the entire AppLovin MAX block (lines 150-160, out of scope per Global Constraints).

Also remove the now-orphaned comment block above the trimmed lines (`appplugin/build.gradle.kts:110-117`, the "GMA 25.4.0 set" explanation) only if it no longer applies to any remaining line — re-read the file after trimming: it still applies to the kept Meta adapter (`facebook:6.21.0.4`), so **leave that comment in place**, just remove the 9 `implementation(...)` lines it was warning about keeping in lockstep.

- [ ] **Step 3: Remove the now-unused Maven repositories from root `settings.gradle.kts`**

`settings.gradle.kts` declares repositories for Pangle, AppLovin, Chartboost and Mintegral specifically to resolve the adapters just removed (AppLovin's repo also serves the still-present MAX SDK, so check before removing that one). Read the current `dependencyResolutionManagement { repositories { ... } }` block and remove the three now-orphaned blocks:
```kotlin
    maven {
      url = uri("https://artifact.bytedance.com/repository/pangle")
      content { includeGroup("com.pangle.global") }
    }
```
```kotlin
    maven {
      url = uri("https://cboost.jfrog.io/artifactory/chartboost-ads/")
      content {
        includeGroup("com.chartboost")
        includeGroup("com.iab.omid.library")
      }
    }
```
```kotlin
    maven {
      url = uri("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
      content { includeGroup("com.mbridge.msdk.oversea") }
    }
```
(`settings.gradle.kts:20-40` in the current file — exact line numbers may shift slightly; match by content, not position.) Keep the AppLovin (`https://artifacts.applovin.com/android`) repository, since the MAX SDK itself (out of scope, still present) resolves through it.

- [ ] **Step 4: Verify the dependency graph resolves and the project compiles**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath > /tmp/deps-after.txt` (or any writable path) and confirm:
- Exactly one `com.google.android.gms:play-services-ads` version appears (25.4.x), not two candidate versions being resolved between.
- None of `com.google.ads.mediation:pangle`, `com.bigossp:admob-mediation`, `com.google.ads.mediation:applovin`, `com.google.ads.mediation:chartboost`, `com.google.ads.mediation:fyber`, `com.google.ads.mediation:vungle`, `com.google.ads.mediation:mintegral`, `com.google.ads.mediation:unity`, `com.unity3d.ads:unity-ads` appear anywhere in the output.

Then run: `./gradlew :app:compileDebugKotlin :appplugin:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — this specifically catches Review Focus item 4 (something else transitively needing one of the trimmed adapters).

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts appplugin/build.gradle.kts settings.gradle.kts
git commit -m "chore(ads): fix play-services-ads version skew, trim 9 unused AdMob mediation adapters"
```

---

### Task 2: `EntitlementRepository` — single source of truth + persisted offline cache

**Files:**
- Create: `app/src/main/java/com/example/billing/EntitlementRepository.kt`
- Modify: `app/src/main/java/com/example/di/AppServiceLocator.kt`
- Modify: `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt:337` (only — lines 358-386 intentionally stay on `billingManager` directly, see Step 6)
- Modify: `app/src/main/java/com/example/ui/screens/PaywallScreen.kt:122`
- Test: `app/src/test/java/com/example/billing/EntitlementRepositoryTest.kt`

**Interfaces:**
- Consumes: `BillingManager.hasSyncedOnce`/`isVipUser`/`activeProductId` (existing `StateFlow`s, unchanged).
- Produces: `class EntitlementRepository(prefs: SharedPreferences, hasSyncedOnceFlow: StateFlow<Boolean>, isVipFlow: StateFlow<Boolean>, activeProductIdFlow: StateFlow<String?>, scope: CoroutineScope) { val isVipUser: StateFlow<Boolean>; val activeProductId: StateFlow<String?> }`. `AppServiceLocator.entitlementRepository`. Every later task in this plan that needs "is the user VIP" reads this, not `BillingManager` directly.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/billing/EntitlementRepositoryTest.kt`:

```kotlin
package com.example.billing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntitlementRepositoryTest {

  private fun prefs(context: Context) =
    context.getSharedPreferences("test_entitlement_prefs", Context.MODE_PRIVATE)

  @Test
  fun isVipUser_seedsFromPersistedCache_beforeAnyBillingResponseThisSession() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    // Simulate a prior session that ended VIP=true.
    prefs(context).edit().putBoolean("key_last_known_vip", true).putString("key_last_known_product_id", "vip_yearly").apply()

    val hasSyncedOnce = MutableStateFlow(false) // no network yet this session
    val isVip = MutableStateFlow(false)          // BillingManager's own un-synced default
    val activeProductId = MutableStateFlow<String?>(null)
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    val repo = EntitlementRepository(prefs(context), hasSyncedOnce, isVip, activeProductId, scope)

    assert(repo.isVipUser.value) { "must read the persisted cache immediately, not wait for hasSyncedOnce" }
    assert(repo.activeProductId.value == "vip_yearly")
  }

  @Test
  fun isVipUser_updatesAndPersists_onlyAfterHasSyncedOnceBecomesTrue() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val hasSyncedOnce = MutableStateFlow(false)
    val isVip = MutableStateFlow(false)
    val activeProductId = MutableStateFlow<String?>(null)
    val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    val repo = EntitlementRepository(prefs(context), hasSyncedOnce, isVip, activeProductId, scope)
    assert(!repo.isVipUser.value)

    // A real Play Billing answer arrives: VIP, then hasSyncedOnce flips.
    isVip.value = true
    activeProductId.value = "vip_lifetime"
    hasSyncedOnce.value = true

    assert(repo.isVipUser.value) { "must adopt the real answer once hasSyncedOnce is true" }
    assert(prefs(context).getBoolean("key_last_known_vip", false)) { "must persist the real answer for the next cold start" }
    assert(prefs(context).getString("key_last_known_product_id", null) == "vip_lifetime")
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.billing.EntitlementRepositoryTest"`
Expected: FAIL to compile — `EntitlementRepository` doesn't exist yet.

- [ ] **Step 3: Implement `EntitlementRepository`**

Create `app/src/main/java/com/example/billing/EntitlementRepository.kt`:

```kotlin
package com.example.billing

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Single source of truth for "is this user VIP" across the app. Wraps [BillingManager]'s own
 * StateFlows (still the only caller of com.app.plugin.iap.Entitlements.grant/revoke/sync — this
 * class does not duplicate that call) with one addition: a SharedPreferences-backed cache of the
 * last real answer, read synchronously at construction. Without this, a VIP user who opens the
 * app with no network sees [BillingManager.hasSyncedOnce] never become true this session, and
 * every direct reader of [BillingManager.isVipUser] sees `false` — not "unknown", `false` — for
 * the whole session. This class instead starts at the last known real answer and only overwrites
 * it once a fresh, real answer actually arrives.
 */
class EntitlementRepository(
  private val prefs: SharedPreferences,
  hasSyncedOnceFlow: StateFlow<Boolean>,
  isVipFlow: StateFlow<Boolean>,
  activeProductIdFlow: StateFlow<String?>,
  scope: CoroutineScope
) {
  private val _isVipUser = MutableStateFlow(prefs.getBoolean(KEY_LAST_KNOWN_VIP, false))
  val isVipUser: StateFlow<Boolean> = _isVipUser.asStateFlow()

  private val _activeProductId = MutableStateFlow(prefs.getString(KEY_LAST_KNOWN_PRODUCT_ID, null))
  val activeProductId: StateFlow<String?> = _activeProductId.asStateFlow()

  init {
    scope.launch {
      combine(hasSyncedOnceFlow, isVipFlow, activeProductIdFlow) { synced, vip, productId ->
        Triple(synced, vip, productId)
      }.collect { (synced, vip, productId) ->
        if (!synced) return@collect // keep showing the cached answer until a real one arrives
        _isVipUser.value = vip
        _activeProductId.value = productId
        prefs.edit()
          .putBoolean(KEY_LAST_KNOWN_VIP, vip)
          .putString(KEY_LAST_KNOWN_PRODUCT_ID, productId)
          .apply()
      }
    }
  }

  companion object {
    private const val KEY_LAST_KNOWN_VIP = "key_last_known_vip"
    private const val KEY_LAST_KNOWN_PRODUCT_ID = "key_last_known_product_id"
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.billing.EntitlementRepositoryTest"`
Expected: PASS.

- [ ] **Step 5: Wire it into `AppServiceLocator`, and exhaustively find every direct reader of `BillingManager.isVipUser`/`hasSyncedOnce`/`activeProductId`**

Run `grep -rn "billingManager\.\(isVipUser\|hasSyncedOnce\|activeProductId\)\|\.isVipUser\b\|\.hasSyncedOnce\b" app/src/main/java` (or the Grep tool with that pattern). Confirmed real result (5 hits, not 3 — 2 are expected false positives, not new sites to migrate):
- `InLoveViewModel.kt:337` — the app-wide `isVip` combine → **migrates** to `EntitlementRepository` (Step 6).
- `InLoveViewModel.kt:359` and `:373` — both inside the same write-gating block described in Step 6 below (`:373` reads `billingManager.activeProductId.value` to decide which `SubscriptionTier` to persist, using the exact same `hasSyncedOnce`-gated block as `:359`) → **stays** on `billingManager` directly, unchanged.
- `PaywallScreen.kt:122` → **migrates** to `EntitlementRepository` (Step 6).
- `AdsManagerImpl.kt:135` — `this.isVipUser = isVip`, a private field *assignment* on `AdsManagerImpl` itself (its `setVipStatus(isVip: Boolean)` parameter, unrelated to `BillingManager.isVipUser`) — a pattern-matching false positive, not a `BillingManager` reader at all. Confirm it's this and move on; it needs no change.
If any OTHER file appears beyond these 5 known hits, treat it as a real fourth reader: add it to Step 6 below; do not leave it on the old, non-cached flow.

In `app/src/main/java/com/example/di/AppServiceLocator.kt`, add, following the exact double-checked-locking pattern already used for `_billingManager`:
```kotlin
    @Volatile private var _entitlementRepository: com.example.billing.EntitlementRepository? = null

    val entitlementRepository: com.example.billing.EntitlementRepository
        get() = _entitlementRepository ?: error("AppServiceLocator chưa được initialize. Gọi initialize(context) trong onCreate().")
```
and, inside `fun initialize(context: Context)`, after the existing `_billingManager` block (so `billingManager` is guaranteed non-null when read below):
```kotlin
        if (_entitlementRepository == null) {
            synchronized(this) {
                if (_entitlementRepository == null) {
                    val bm = _billingManager!!
                    _entitlementRepository = com.example.billing.EntitlementRepository(
                        appContext.getSharedPreferences("inlove_entitlement_prefs", Context.MODE_PRIVATE),
                        bm.hasSyncedOnce, bm.isVipUser, bm.activeProductId,
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
                    )
                }
            }
        }
```

- [ ] **Step 6: Switch the two VIP-for-display readers to `EntitlementRepository`; leave the one VIP-for-persistence reader on `BillingManager`**

Only 2 of the 5 sites Step 5's grep confirms are "what should the UI show as the current VIP status" readers — those two move to `EntitlementRepository`. The `InLoveViewModel.kt:358-386` block (containing both the `:359` and `:373` hits) has a different job: it exists specifically to gate a **permanent Room DB write** (syncing `billingManager.isVipUser`'s answer, and `billingManager.activeProductId.value`'s tier mapping, into the user's saved account record via `authRepo.updateUserSubscription(...)`) on `hasSyncedOnce` being `true` — i.e. "only persist a downgrade once Play Billing has genuinely answered, never on the cache-seeded default". `EntitlementRepository` deliberately does not expose a `hasSyncedOnce` of its own (it already folds that wait into what `isVipUser` returns, for display purposes), so this write-gating block keeps reading `billingManager.hasSyncedOnce`/`billingManager.isVipUser`/`billingManager.activeProductId` directly, unchanged. Migrating it too would be wrong, not just unnecessary: it would either drop the "wait for a real answer" guard entirely (persisting the cache-seeded default as if it were freshly confirmed) or require adding a `hasSyncedOnce` passthrough to `EntitlementRepository` for a single caller that doesn't need the rest of what that class provides. `AdsManagerImpl.kt:135` is an unrelated false positive (see Step 5) and needs no change either.

In `app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt`, replace line 337's `billingManager.isVipUser` with `com.example.di.AppServiceLocator.entitlementRepository.isVipUser` (the `billingManager` local val at this point in `init` stays — it is still needed for `startBillingConnection()`/purchase flow elsewhere in the class, and by the line 358-386 block just described — this only changes which flow is read for the app-wide `isVip` combine at line 337). Leave lines 358-386 exactly as they are.

In `app/src/main/java/com/example/ui/screens/PaywallScreen.kt:122`, replace `billingManager.isVipUser.collectAsState()` with `com.example.di.AppServiceLocator.entitlementRepository.isVipUser.collectAsState()` (drop the `billingManager` parameter from this composable's signature only if nothing else in the same file still uses it — check first).

- [ ] **Step 7: Run the full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS, including `EntitlementRepositoryTest` and every pre-existing test that touches `InLoveViewModel`/`PaywallScreen`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/billing/EntitlementRepository.kt \
        app/src/main/java/com/example/di/AppServiceLocator.kt \
        app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt \
        app/src/main/java/com/example/ui/screens/PaywallScreen.kt \
        app/src/test/java/com/example/billing/EntitlementRepositoryTest.kt
git commit -m "feat(billing): EntitlementRepository — persisted offline VIP cache, single source of truth"
```

---

### Task 3: Fix the `RequestConfiguration` overwrite bug, call `startSdkInit`

**Files:**
- Modify: `app/src/main/java/com/example/ads/AdsManagerImpl.kt`
- Modify: `app/src/main/java/com/example/MainActivity.kt`
- Test: `app/src/test/java/com/example/ads/RequestConfigurationMergeTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces: `AdsManagerImpl`'s public `initialize(context: Context)` signature is unchanged; internal behavior only.

- [ ] **Step 1: Extract the merge logic into a small, pure, testable function**

`RequestConfiguration`/`MobileAds` are Android-framework/GMS types that need Robolectric to touch directly, but the *logic* being fixed — "start from the existing configuration's builder, not a fresh one" — is a one-line change best pinned by testing the actual call, not a hand-rolled abstraction over it. `RequestConfiguration`/`MobileAds.setRequestConfiguration`/`getRequestConfiguration` are in-memory value holders on the `MobileAds` singleton (no network, no ad load), so they are expected to work under plain Robolectric without needing `MobileAds.initialize()` to have run first — but this has not been confirmed by an actual test run in this environment (no network access to resolve dependencies here). If this specific test throws under Robolectric for an environment reason unrelated to the merge logic itself (e.g. a GMS-under-Robolectric class-loading issue), fall back to testing `AdsManagerImpl.applyTestDeviceIds`'s logic without Robolectric by refactoring the merge into a pure function that takes and returns a plain data holder instead of touching the real `MobileAds`/`RequestConfiguration` singleton directly, and verify the real call manually via Task 6's device check instead. Do not delete the test outright if it fails to run for an environment reason — replace it with the pure-function version instead.

Create `app/src/test/java/com/example/ads/RequestConfigurationMergeTest.kt`:

```kotlin
package com.example.ads

import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RequestConfigurationMergeTest {

  @Test
  fun applyingTestDeviceIds_preservesAnExistingContentRatingAlreadySet() {
    // Simulate appplugin's AdsMobMy.startNetwork() having already run and set a content rating.
    MobileAds.setRequestConfiguration(
      RequestConfiguration.Builder()
        .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_MA)
        .build()
    )

    AdsManagerImpl.applyTestDeviceIds(listOf("TEST-DEVICE-ID"))

    val current = MobileAds.getRequestConfiguration()
    assert(current.maxAdContentRating == RequestConfiguration.MAX_AD_CONTENT_RATING_MA) {
      "a prior setRequestConfiguration()'s fields must survive — this is the exact bug appplugin's own AdsMobMy.kt history already hit"
    }
    assert(current.testDeviceIds.contains("TEST-DEVICE-ID"))
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ads.RequestConfigurationMergeTest"`
Expected: FAIL — `AdsManagerImpl.applyTestDeviceIds` doesn't exist yet (or, if left inline, the assertion fails because the current code replaces the configuration instead of merging).

- [ ] **Step 3: Extract and fix the merge in `AdsManagerImpl.kt`**

Replace the top of `initialize(context: Context)` (`AdsManagerImpl.kt:115-120`):
```kotlin
    override fun initialize(context: Context) {
        // 1. Cấu hình Test Device IDs để tránh vi phạm Invalid Traffic Policy.
        val requestConfig = RequestConfiguration.Builder()
            .setTestDeviceIds(testDeviceIds)
            .build()
        MobileAds.setRequestConfiguration(requestConfig)
```
with:
```kotlin
    override fun initialize(context: Context) {
        // 1. Cấu hình Test Device IDs — MERGE vào cấu hình hiện có, không tạo Builder() rỗng:
        // appplugin's AdsMobMy.startNetwork() can set/replace this same global object in either
        // order relative to this call, and its own history (AdsMobMy.kt:106-111) documents the
        // exact failure a fresh Builder() causes: whichever call runs second silently erases the
        // other's fields (production traffic served test ads, or a debug build served real ads).
        applyTestDeviceIds(testDeviceIds)
```
Add a companion function next to the existing `companion object` block (or create one if the class doesn't have one — check `AdsManagerImpl.kt` first; it currently has no companion object, so add one):
```kotlin
    companion object {
        internal fun applyTestDeviceIds(testDeviceIds: List<String>) {
            val merged = MobileAds.getRequestConfiguration().toBuilder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(merged)
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ads.RequestConfigurationMergeTest"`
Expected: PASS.

- [ ] **Step 5: Call `AdsMobMy.instance.startSdkInit(applicationContext)` from `MainActivity.onCreate`**

In `app/src/main/java/com/example/MainActivity.kt`, immediately after the existing `com.example.di.AppServiceLocator.initialize(applicationContext)` call (`MainActivity.kt:76`) and before the `adsManager.requestConsentAndInitialize(...)` call, add:
```kotlin
    try {
      com.app.plugin.ads.adsmob.AdsMobMy.instance.startSdkInit(applicationContext)
    } catch (e: Exception) {
      android.util.Log.d("MainActivity", "AdsMobMy.startSdkInit skipped: ${e.message}")
    }
```
(matching the try/catch-with-Log.d style already used for every other optional appplugin call in this codebase; `startSdkInit` itself defers internally until UMP has answered, per `AdsMobMy.kt`'s own documentation, so calling it this early in `onCreate` — before consent is known — is correct and matches what `DOC/47` specifies).

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/ads/AdsManagerImpl.kt \
        app/src/main/java/com/example/MainActivity.kt \
        app/src/test/java/com/example/ads/RequestConfigurationMergeTest.kt
git commit -m "fix(ads): merge RequestConfiguration instead of replacing it, call startSdkInit"
```

---

### Task 4: Disable AdsBrain, add the privacy-options Settings entry

**Files:**
- Modify: `app/src/main/java/com/example/InLoveApplication.kt`
- Modify: `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`
- Test: `app/src/test/java/com/example/ui/PrivacyOptionsRowTest.kt`

**Interfaces:**
- Consumes: `ConsentManager.isPrivacyOptionsRequired(context: Context): Boolean`, `ConsentManager.showPrivacyOptions(activity: Activity, onDone: (ConsentTier) -> Unit = {})` (both existing, real appplugin API, confirmed at `appplugin/src/main/java/com/app/plugin/consent/ConsentManager.kt:151,240`).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Disable the brain**

In `app/src/main/java/com/example/InLoveApplication.kt`, change:
```kotlin
        val report = MonetizationSdk.configure(this) {
            brainEnabled = true
            brainRolloutFraction = 1.0
```
to:
```kotlin
        val report = MonetizationSdk.configure(this) {
            // Off: InLove is not a game, has no backend training/BigQuery pipeline deployed
            // for this brain (per appplugin's own docs), and AdsManagerImpl's explicit 30s
            // interstitial interval cap is already the real, auditable gate. Re-enabling later
            // must follow the SDK's own staged rollout (5% -> 20% -> 100%, >=2 weeks per step
            // with a holdout comparison, appplugin/DOC/38_INTEGRATION_GUIDE.md:126-144) — the
            // value below is left in place as a starting point for that, not a live setting.
            brainEnabled = false
            brainRolloutFraction = 0.05
```
(`AdsManagerImpl.kt:186`'s `if (AdsBrain.enabled && !AdsBrain.shouldShowInterstitial())` already short-circuits to skip the brain check entirely once `AdsBrain.enabled` reflects `brainEnabled = false` — no change needed there.)

- [ ] **Step 2: Write the failing test for the privacy-options row's visibility logic**

Create `app/src/test/java/com/example/ui/PrivacyOptionsRowTest.kt`:

```kotlin
package com.example.ui

import androidx.test.core.app.ApplicationProvider
import com.app.plugin.consent.ConsentManager
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivacyOptionsRowTest {

  @Test
  fun isPrivacyOptionsRequired_returnsFalseSafely_whenNoConsentFlowHasEverRunInThisProcess() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    // No UMP flow has run in this test process at all — this is exactly the "Settings opened
    // cold, e.g. via deep link, before MainActivity's own consent flow ever ran" case (Review
    // Focus item 5). ConsentManager.kt:151-157 wraps this in try/catch and returns false; this
    // test pins that so a future SDK change can't silently make it throw instead.
    val required = ConsentManager.isPrivacyOptionsRequired(context)

    assert(!required) { "must degrade to false, never throw, when UMP has no saved consent state" }
  }
}
```

- [ ] **Step 3: Run test to verify it currently passes (this step confirms the SDK's existing safety net, not new production code)**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.ui.PrivacyOptionsRowTest"`
Expected: PASS already — `ConsentManager.isPrivacyOptionsRequired(context)` already has this safety net (`appplugin/src/main/java/com/app/plugin/consent/ConsentManager.kt:151-157`); this step exists to pin that guarantee with a test, not to fix a bug.

- [ ] **Step 4: Add the Settings row — do NOT derive the `Activity` from `LocalContext.current`**

`MainActivity.kt` (inside `InLoveApp()`, ~lines 140-159) wraps the whole composition in a locale-adjusted context: `val context = remember(rawContext, configuration) { rawContext.createConfigurationContext(configuration) }`, then `CompositionLocalProvider(LocalContext provides context, ...)`. `Context.createConfigurationContext()` returns a plain `Context`/`ContextImpl`, never the `Activity` itself and never a `ContextWrapper` chain leading back to it — so `LocalContext.current as? Activity` is `null` for every composable under this provider, including `SettingsScreen` and `PaywallScreen` (shown via the `Dialog` at `MainActivity.kt:558-572`, which still inherits the same composition). A naive `LocalContext.current as? Activity` guard would make this whole row permanently invisible, compiling cleanly and silently never firing — exactly the kind of gap this task exists to close, so it must not reappear here.

Instead, reuse `LocalActivityResultRegistryOwner` — already provided once, directly around the real `Activity` instance, specifically for this reason: `MainActivity.onCreate`'s `setContent { CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides this) { ... InLoveApp() } }` passes `this` (the real `MainActivity`, a `ComponentActivity`/`Activity`) as the registry owner, and that provider wraps the same subtree `LocalContext` is separately (and unreliably, for this purpose) overridden in. `LocalActivityResultRegistryOwner.current` is therefore always the real `Activity` here, and casting it is safe:

In `app/src/main/java/com/example/ui/screens/SettingsScreen.kt`, add a new `item { }` block immediately after the existing "2. Language Selection Toggle" block (`SettingsScreen.kt:643-650`), matching that block's surrounding `LazyColumn` `item { }` structure and the file's existing `Card`+`clickable` idiom (seen at the VIP banner, `SettingsScreen.kt:654-668`):

```kotlin
    // 2.1. Ad privacy options (GDPR/CCPA "manage consent" re-entry point)
    item {
      val context = androidx.compose.ui.platform.LocalContext.current
      // NOT `context as? Activity` — MainActivity wraps LocalContext with
      // createConfigurationContext() for locale support, so it is never an Activity here.
      // LocalActivityResultRegistryOwner is provided once, directly around the real
      // Activity, in the same MainActivity.onCreate setContent block, specifically usable
      // for this.
      val activity = androidx.activity.compose.LocalActivityResultRegistryOwner.current as? android.app.Activity
      val showPrivacyRow = remember {
        try { com.app.plugin.consent.ConsentManager.isPrivacyOptionsRequired(context) } catch (e: Exception) { false }
      }
      AdPrivacyOptionsRow(
        visible = showPrivacyRow && activity != null,
        isEnglish = isEnglish,
        onClick = {
          val act = activity ?: return@AdPrivacyOptionsRow
          try {
            com.app.plugin.consent.ConsentManager.showPrivacyOptions(act)
          } catch (e: Exception) {
            android.util.Log.d("SettingsScreen", "showPrivacyOptions failed: ${e.message}")
          }
        }
      )
    }
```
(`isEnglish` — verify it is already available in this file's scope the same way the surrounding blocks use it, e.g. as a parameter of the enclosing composable or read from the ViewModel.)

Add the extracted, independently testable composable near the file's other small private/standalone composables (or as a top-level `internal` composable in the same file — match whatever the file's existing convention is for a small reusable row):

```kotlin
/**
 * Pure UI: takes the already-resolved visibility decision and click handler as parameters,
 * rather than reading LocalContext/LocalActivityResultRegistryOwner itself — this is what
 * makes PrivacyOptionsRowTest able to verify the row's actual on-screen visibility without
 * needing to fake an Activity or a wrapped Context.
 */
@Composable
internal fun AdPrivacyOptionsRow(visible: Boolean, isEnglish: Boolean, onClick: () -> Unit) {
  if (!visible) return
  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F5)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("settings_privacy_options_row")
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Default.Info,
        contentDescription = null,
        tint = Primary
      )
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        if (isEnglish) "Ad Privacy Options" else "Quyền riêng tư quảng cáo",
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
      )
    }
  }
}
```
`Icons.Default.Info` needs `import androidx.compose.material.icons.filled.Info` added to `SettingsScreen.kt` if not already present — check the file's existing imports before assuming.

- [ ] **Step 4b: Add a real visibility test for `AdPrivacyOptionsRow`, not just `ConsentManager`'s safety net**

Append to `app/src/test/java/com/example/ui/PrivacyOptionsRowTest.kt` (Step 1's file):
```kotlin
  @get:Rule
  val composeRule = androidx.compose.ui.test.junit4.createComposeRule()

  @Test
  fun adPrivacyOptionsRow_rendersOnlyWhenVisibleIsTrue() {
    composeRule.setContent {
      com.example.ui.screens.AdPrivacyOptionsRow(visible = false, isEnglish = true, onClick = {})
    }
    composeRule.onNodeWithTag("settings_privacy_options_row").assertDoesNotExist()

    composeRule.setContent {
      com.example.ui.screens.AdPrivacyOptionsRow(visible = true, isEnglish = true, onClick = {})
    }
    composeRule.onNodeWithTag("settings_privacy_options_row").assertExists()
  }

  @Test
  fun adPrivacyOptionsRow_clickInvokesCallback() {
    var clicked = false
    composeRule.setContent {
      com.example.ui.screens.AdPrivacyOptionsRow(visible = true, isEnglish = true, onClick = { clicked = true })
    }
    composeRule.onNodeWithTag("settings_privacy_options_row").performClick()
    assert(clicked) { "tapping the row must invoke onClick — this is what wires to ConsentManager.showPrivacyOptions(activity) at the real call site" }
  }
```
Add the needed imports (`androidx.compose.ui.test.junit4.createComposeRule`, `androidx.compose.ui.test.onNodeWithTag`, `androidx.compose.ui.test.assertExists`, `androidx.compose.ui.test.assertDoesNotExist`, `androidx.compose.ui.test.performClick`, `org.junit.Rule`) and the `androidx.compose.ui.test.junit4:ui-test-junit4`/`debugImplementation ui-test-manifest` dependencies already present in `app/build.gradle.kts` (`libs.androidx.compose.ui.test.junit4` is already a `testImplementation` per the existing catalog — confirm before assuming a new dependency is needed).

This is the test that actually exercises Review Focus item 5's sibling risk (finding #1 from this plan's own adversarial review): it proves the row appears/disappears and responds to clicks purely from its own parameters, independent of whatever `LocalContext`/`LocalActivityResultRegistryOwner` resolve to at the real call site — the real call site's correctness (using `LocalActivityResultRegistryOwner`, not `LocalContext`) is a one-line, code-reviewable fact once the hard-to-test CompositionLocal-reading part is this small.

- [ ] **Step 5: Run the full test suite**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/InLoveApplication.kt \
        app/src/main/java/com/example/ui/screens/SettingsScreen.kt \
        app/src/test/java/com/example/ui/PrivacyOptionsRowTest.kt
git commit -m "feat(consent): disable AdsBrain, add ad privacy-options entry to Settings"
```

---

### Task 5: Enable R8 for release

**Files:**
- Modify: `app/build.gradle.kts`
- Test: manual release build (no unit test — this is a build-configuration change)

**Interfaces:**
- Consumes: `appplugin/consumer-rules.pro`'s existing ClassValue fix (already shipped, confirmed present, auto-merged via `consumerProguardFiles`).
- Produces: nothing consumed by later tasks.

- [ ] **Step 1: Turn on minification and resource shrinking**

In `app/build.gradle.kts`, inside `buildTypes { release { ... } }` (`app/build.gradle.kts:74-87`), change:
```kotlin
      isCrunchPngs = false
      isMinifyEnabled = false
```
to:
```kotlin
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
```

- [ ] **Step 2: Build a release APK and verify the ClassValue release gate**

Run (with real `KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_PASSWORD`/`ADMOB_*_RELEASE` environment set, per the existing `signingConfigs.release` block — or `-x validateSigningRelease`-equivalent if only checking compilation, not a signed artifact):
`./gradlew :app:assembleRelease`
Expected: BUILD SUCCESSFUL.

Then run the exact check `appplugin`'s own release process uses (`appplugin/consumer-rules.pro:81-82`'s comment references this):
`grep -c ClassValue app/build/outputs/mapping/release/mapping.txt`
Expected: `0`.

If the build fails with a `ClassNotFoundException`/missing-class R8 error unrelated to ClassValue, that is a genuine new keep-rule gap this task must close by adding the specific `-keep` rule R8's own error output names — do not blanket-disable minification to work around it.

- [ ] **Step 3: Run the full unit test suite once more (release config changes should never affect debug unit tests, but this confirms it)**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build(release): enable R8 minification + resource shrinking"
```

---

### Task 6: Full-plan verification

**Files:**
- Test: full suite, both modules

- [ ] **Step 1: Run the complete unit test suite for both modules**

Run: `./gradlew test`
Expected: ALL TESTS PASS (`:app` and `:appplugin`).

- [ ] **Step 2: Run both debug and release assembly**

Run: `./gradlew :app:assembleDebug :app:assembleRelease`
Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 3: Manual device check — cold start offline as a previously-VIP account**

On a device or emulator where a VIP purchase was already restored once, enable airplane mode, force-stop and reopen the app. Confirm no ads appear and VIP-gated UI (Paywall entry point, ad-free state) shows correctly with no network — this is Review Focus item 1, exercised end-to-end.

- [ ] **Step 4: Manual device check — Settings privacy row**

After granting UMP consent once (debug builds show the EEA test form when `DebugGeography` is configured, or use a real EEA test device), open Settings and confirm "Quyền riêng tư quảng cáo" appears and reopens the consent form on tap.
