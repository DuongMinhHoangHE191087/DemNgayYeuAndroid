# Ads, Firebase SDK & Android 16 Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** De-duplicate App Open Ads, harden Firebase SDK against non-GMS devices and ProGuard reflection stripping, and fix Android 12-16 (Target SDK 36) exact alarm & Activity lifecycle crashes.

**Architecture:** De-duplicate AOA lifecycle observers by delegating foreground AOA triggers to `:appplugin` (`AppPluginBase`), add Activity lifecycle guards before presenting full-screen ads, wrap GMS-dependent FCM token fetches in try-catch blocks, and add ProGuard keep rules for Firebase App Check reflection.

**Tech Stack:** Kotlin, Android SDK 36, Jetpack Lifecycle, Firebase Messaging & App Check, AdMob / AppPlugin SDK.

**Spec:** `docs/superpowers/specs/2026-03-31-ads-firebase-android16-hardening-design.md`

## Global Constraints

- Target SDK = 36, Min SDK = 26, Java 17 Compatibility
- Do not introduce breaking changes to existing `AdsManager` interface signatures
- All full-screen ad show calls must guard against `activity.isFinishing || activity.isDestroyed`

## Review Focus

- App Open Ads showing twice on app resume: Verify `:app` does not register a redundant `ProcessLifecycleOwner` observer.
- Non-GMS device startup crash: Verify `FirHelper` handles FCM token failures gracefully without crashing.
- ProGuard R8 stripping App Check factories: Verify `proguard-rules.pro` keeps `com.google.firebase.appcheck.**`.
- `BadTokenException` on Activity finish: Verify ad display calls check `activity.isFinishing` and `activity.isDestroyed`.
- `SecurityException` on `AlarmManager.setExactAndAllowWhileIdle`: Verify fallback to non-exact alarms.

---

### Task 1: De-duplicate App Open Ads & Add Activity Lifecycle Guards in `AdsManagerImpl`

**Files:**
- Modify: `app/src/main/java/com/example/ads/AdsManagerImpl.kt`
- Modify: `app/src/main/java/com/example/MainActivity.kt`

**Interfaces:**
- Consumes: `AdsManager`, `AppPluginBase`
- Produces: Lifecycle-safe `showInterstitial` and `showAppOpenAdIfAvailable` methods without duplicate `ProcessLifecycleOwner` registration.

- [ ] **Step 1: Inspect existing AOA lifecycle registration in `AdsManagerImpl`**

Check `registerAppOpenAdLifecycle` and `onStart` in `AdsManagerImpl.kt`.

- [ ] **Step 2: Update `AdsManagerImpl.kt` to guard against destroyed activities and remove duplicate AOA foreground triggering**

Modify `AdsManagerImpl.kt`:
1. In `showInterstitial`: Add guard `if (activity.isFinishing || activity.isDestroyed) { onAdDismissed(); return }`.
2. In `showAppOpenAdIfAvailable`: Add guard `if (activity.isFinishing || activity.isDestroyed) return`.
3. Disable automatic AOA triggering in `onStart` of `AdsManagerImpl` so that `:appplugin` (`AppPluginBase`) handles AOA foreground display exclusively.

```kotlin
    override fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onAdDismissed()
            return
        }
        val currentTime = System.currentTimeMillis()
        val isIntervalOk = (currentTime - lastInterstitialShownTime) >= minIntervalMs
        val currentAd = interstitialAd

        if (AdsBrain.enabled && !AdsBrain.shouldShowInterstitial()) {
            onAdDismissed()
            return
        }

        if (isVipUser || currentAd == null || !isIntervalOk) {
            onAdDismissed()
            if (!isVipUser && currentAd == null) {
                preloadInterstitial(activity.applicationContext, defaultInterstitialAdUnitId)
            }
            return
        }

        isAoaSuppressed = true

        currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                AdsBrain.onInterstitialShown()
                interstitialAd = null
                lastInterstitialShownTime = System.currentTimeMillis()
                isAoaSuppressed = false
                preloadInterstitial(activity.applicationContext, defaultInterstitialAdUnitId)
                onAdDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                isAoaSuppressed = false
                preloadInterstitial(activity.applicationContext, defaultInterstitialAdUnitId)
                onAdDismissed()
            }

            override fun onAdShowedFullScreenContent() {}
        }

        currentAd.show(activity)
    }
```

- [ ] **Step 3: Update `MainActivity.kt` to remove redundant AOA lifecycle registration**

In `MainActivity.onCreate`:
Remove `adsManager.registerAppOpenAdLifecycle(application)` since `AppPluginBase` already registers lifecycle callbacks and handles AOA on start.

- [ ] **Step 4: Verify build succeeds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit changes**

```bash
git add app/src/main/java/com/example/ads/AdsManagerImpl.kt app/src/main/java/com/example/MainActivity.kt
git commit -m "fix(ads): de-duplicate App Open Ads and add Activity finishing guards"
```

---

### Task 2: Firebase SDK Hardening & ProGuard Keep Rules

**Files:**
- Modify: `appplugin/src/main/java/com/app/plugin/firbase/FirHelper.kt`
- Modify: `app/src/main/java/com/example/InLoveApplication.kt`
- Modify: `app/proguard-rules.pro`

**Interfaces:**
- Consumes: Firebase App Check, FCM, ProGuard rules
- Produces: Exception-safe Firebase initialization resilient to non-GMS devices and R8 minification.

- [ ] **Step 1: Update `FirHelper.kt` to catch FCM registration failures on non-GMS devices**

In `FirHelper.kt` `init`:

```kotlin
    init {
        firCg = FirHelperConfig()
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.d(LogUtil.TAG, "Fir Fetching FCM registration token failed: ${task.exception}")
                    return@OnCompleteListener
                }
                val token = task.result
                Log.d(LogUtil.TAG, "Fir Fetching FCM registration token=$token")
            })
        } catch (e: Throwable) {
            LogUtil.logE("FirHelper FCM token init skipped (non-GMS or missing services): ${e.message}")
        }
    }
```

- [ ] **Step 2: Update `InLoveApplication.kt` App Check reflection try-catch blocks**

In `InLoveApplication.kt`: Ensure all reflection calls for `DebugAppCheckProviderFactory` and `PlayIntegrityAppCheckProviderFactory` are wrapped in `try { ... } catch (e: Throwable) { ... }`.

- [ ] **Step 3: Add ProGuard Keep Rules for Firebase App Check in `app/proguard-rules.pro`**

Add the following to `app/proguard-rules.pro`:

```proguard
# Firebase App Check Reflection Keep Rules
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory { *; }
-keep class com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory { *; }
```

- [ ] **Step 4: Run build to verify compilation**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit changes**

```bash
git add appplugin/src/main/java/com/app/plugin/firbase/FirHelper.kt app/src/main/java/com/example/InLoveApplication.kt app/proguard-rules.pro
git commit -m "fix(firebase): harden FCM token fetching and add ProGuard rules for App Check"
```

---

### Task Task 3: AlarmManager Exact Alarm Fallback for Android 12-16 Compatibility

**Files:**
- Modify: `app/src/main/java/com/example/alarm/AlarmNotificationScheduler.kt`

**Interfaces:**
- Consumes: `AlarmManager`
- Produces: Exception-safe exact and windowed alarm scheduling.

- [ ] **Step 1: Check `scheduleAlarm` in `AlarmNotificationScheduler.kt`**

Verify `try-catch (e: SecurityException)` surrounds `alarmManager.setExactAndAllowWhileIdle` and provides fallback to `alarmManager.setAndAllowWhileIdle` or `alarmManager.set`.

- [ ] **Step 2: Add comprehensive exception handling in `AlarmNotificationScheduler.kt`**

Ensure `try { ... } catch (e: SecurityException) { ... } catch (e: Throwable) { ... }` handles all potential system alarm exceptions on Android 12+ (SDK 31+).

```kotlin
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
          alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
          )
        } else {
          alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
          )
        }
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        alarmManager.setExactAndAllowWhileIdle(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      } else {
        alarmManager.setExact(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
      }
      Log.d(TAG, "Scheduled alarm $reminderId at $triggerAtMillis ($title)")
      return true
    } catch (e: SecurityException) {
      Log.e(TAG, "SecurityException scheduling exact alarm: ${e.message}, using windowed fallback", e)
      try {
        alarmManager.set(
          AlarmManager.RTC_WAKEUP,
          triggerAtMillis,
          pendingIntent
        )
        return true
      } catch (ex: Throwable) {
        Log.e(TAG, "Failed fallback alarm", ex)
        return false
      }
    } catch (e: Throwable) {
      Log.e(TAG, "Failed to schedule alarm", e)
      return false
    }
```

- [ ] **Step 3: Run unit tests to verify no regressions**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL and all tests pass

- [ ] **Step 4: Commit changes**

```bash
git add app/src/main/java/com/example/alarm/AlarmNotificationScheduler.kt
git commit -m "fix(alarm): add SecurityException and Throwable fallback handling for exact alarms"
```

---

### Task 4: Full Suite Verification & Build Confirmation

**Files:**
- Test: All unit test suites

- [ ] **Step 1: Run complete Gradle unit test suite**

Run: `./gradlew test`
Expected: ALL TESTS PASS

- [ ] **Step 2: Run debug build assembly**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL
