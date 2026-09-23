# Design Spec: Ads, Firebase SDK & Android 16 (Target SDK 36) Hardening

## Overview
This design spec defines the comprehensive optimization plan (Option 3) to eliminate app crashes, de-duplicate App Open Ad triggers between `:app` and `:appplugin`, harden Firebase SDK initialization on devices without Google Play Services (GMS), and ensure 100% compatibility across Android versions from Min SDK 26 (Android 8.0) up to Target SDK 36 (Android 16).

## Key Architecture Changes

### 1. Unified Ads Architecture (De-duplicating App Open Ads)
- **Problem**: Both `AppPluginBase` (in `:appplugin`) and `AdsManagerImpl` (in `:app`) listen to `ProcessLifecycleOwner.get().lifecycle` and trigger App Open Ads on `onStart()`. This causes dual AOA launches, UI race conditions, and `BadTokenException` crashes.
- **Solution**:
  - Deprecate/Disable `registerAppOpenAdLifecycle` and internal AOA `onStart` observer in `AdsManagerImpl`.
  - Delegate all App Open Ad lifecycle handling directly to `:appplugin` (`AppPluginBase`).
  - Add `activity.isFinishing || activity.isDestroyed` guards before presenting full-screen ads in `showInterstitial` and `showAppOpenAdIfAvailable`.

### 2. Firebase SDK Hardening & GMS Resilience
- **Problem**:
  - `FirHelper` accesses `FirebaseMessaging.getInstance().token` without guarding against devices lacking GMS (e.g. HMS / custom ROMs), which throws `IllegalStateException`.
  - `InLoveApplication` uses reflection to load `DebugAppCheckProviderFactory` and `PlayIntegrityAppCheckProviderFactory`. Without explicit ProGuard keep rules, R8 minification strips these classes in release builds, causing `ClassNotFoundException`.
- **Solution**:
  - Wrap `FirebaseMessaging.getInstance().token` in a safe try-catch block with fallback logging.
  - Add explicit `-keep class com.google.firebase.appcheck.** { *; }` rules in `app/proguard-rules.pro`.
  - Wrap App Check reflection in `InLoveApplication` with explicit Exception handlers.

### 3. Target SDK 36 (Android 16) & System Compatibility
- **Problem**:
  - `AlarmManager.setExactAndAllowWhileIdle` can throw `SecurityException` on Android 12+ (SDK 31+) if exact alarm permission (`SCHEDULE_EXACT_ALARM`) is revoked by the OS or user.
  - Dynamically registered `BroadcastReceiver`s on Android 13+ (SDK 33+) must specify `RECEIVER_NOT_EXPORTED` or `RECEIVER_EXPORTED`.
- **Solution**:
  - Add fallback handling in `AlarmNotificationScheduler` to catch `SecurityException` and fallback to non-exact `setAndAllowWhileIdle` or `set`.
  - Audit all receiver registrations for export flags.

## Verification Strategy
- **Unit Tests**: Run `:app:testDebugUnitTest` and `:appplugin:testDebugUnitTest`.
- **Build Verification**: Run `:app:assembleDebug` and `:appplugin:assembleDebug`.
