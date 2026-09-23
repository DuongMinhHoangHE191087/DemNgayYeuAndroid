# SDD ledger — plan: docs/superpowers/plans/2026-03-31-ads-firebase-android16-hardening.md
Pre-flight scan:
- Task 1: De-duplicate App Open Ads & Add Activity Lifecycle Guards
- Task 2: Firebase SDK Hardening & ProGuard Keep Rules
- Task 3: AlarmManager Exact Alarm Fallback for Android 12-16 Compatibility
- Task 4: Full Suite Verification & Build Confirmation
Pre-flight scan clean.

Task 1: complete (commits Task 1 edits, de-duplicated AOA lifecycle observers, added activity.isFinishing/isDestroyed guards)
Task 2: complete (hardened FCM token fetch for non-GMS devices, added App Check reflection Throwable catch & ProGuard Keep Rules)
Task 3: complete (added Throwable exception handling for exact alarms & windowed fallbacks in AlarmNotificationScheduler)
Task 4: complete (unit tests: :app 35 passed, :appplugin 530 passed; :app:assembleDebug finished successfully)
