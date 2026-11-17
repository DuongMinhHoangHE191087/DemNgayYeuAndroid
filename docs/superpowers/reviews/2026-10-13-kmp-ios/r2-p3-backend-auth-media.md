[MAJOR] B-109  
Problem: The signed-URL fallback removes the enforced expiration of private video links.  
Evidence: [P3:466](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:466) permits `FALLBACK-SIGNED` using an `s--` signature instead of `auth_token`. [media.js:296](F:/Github_Project/DemNgayYeuAndroid/firebase/functions/src/media.js:296) promises that previously issued links expire after the delivery TTL, including after breakup. Cloudinary’s ordinary delivery signature signs the asset/transformation path without an expiration. [Cloudinary access-control documentation](https://cloudinary.com/documentation/control_access_to_media).  
Fix: Remove this fallback or replace it with an access mechanism that enforces the same 15-minute expiration. Require a real-account test proving access fails after expiry and that removing the token cannot restore access.

[MAJOR] B-68  
Problem: Cancelling authentication only suppresses callbacks and can leave Firebase signed into an abandoned or superseded account.  
Evidence: [P3:441](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:441) uses `OneShot` cancellation, but applies UID checks only to operations on an existing user. Firebase 12.19.2 updates and persists `currentUser` before invoking the sign-in completion, so dropping that completion does not undo the session mutation. [Pinned Firebase Auth implementation](https://raw.githubusercontent.com/firebase/firebase-ios-sdk/12.19.2/FirebaseAuth/Sources/Swift/Auth/Auth.swift).  
Fix: Define serialization and generation fencing for sign-in, account creation, social login, and logout, including cleanup of abandoned SDK operations. Test the actual gateway with cancellation/logout followed by a late SDK completion that mutates its user before calling back.

[MAJOR] B-95  
Problem: A failed vault transition makes logout report success while retaining an authenticated UI after the underlying session has already been invalidated.  
Evidence: [P3:455](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:455) explicitly keeps `Authenticated` on logout vault failure. [AuthRepository.kt:1006](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/data/repository/AuthRepository.kt:1006) clears preferences, signs out Firebase, and stops sync before `enterScope(null)`; [InLoveViewModel.kt:1953](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt:1953) then unconditionally displays “Signed out.”  
Fix: Preserve session side effects until the vault transition succeeds, or enter an inaccessible, retryable logout state. Assign the caller’s failure handling and test Firebase user, preferences, hooks, vault scope, UI state, and the displayed result together.

[MAJOR] B-29  
Problem: Losing the deletion callable’s response can leave local account data behind permanently after the server deletes the account.  
Evidence: [accountDeletion.js:48](F:/Github_Project/DemNgayYeuAndroid/firebase/functions/src/accountDeletion.js:48) durably accepts the job before returning; [accountDeletion.js:88](F:/Github_Project/DemNgayYeuAndroid/firebase/functions/src/accountDeletion.js:88) subsequently deletes Auth. [AuthRepository.kt:1053](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/data/repository/AuthRepository.kt:1053) treats timeout as “not sent” and preserves local data. [P3:409](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:409) preserves this behavior without durable client recovery.  
Fix: Persist UID-scoped deletion intent before sending, distinguish rejection from an unknown result, and reconcile local cleanup on retry/relaunch when that account has been deleted. Add `timeoutAfterServerAcceptance_restartCompletesLocalDeletion`.

[MAJOR] B-70  
Problem: Apple credential revocation has no implementation task despite being a required feature.  
Evidence: [P8:227](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p8-feature-parity-matrix.md:227) requires MQ-405 to detect revocation and return to login. [P3:443](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:443) implements authorization and deletion-time revocation only; [P3:450](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:450) omits MQ-405 from its manual checks. No P3 task assigns credential-state checks or revocation observation.  
Fix: Assign storage of the Apple user identifier, startup/foreground `getCredentialState` checks, revocation observation, and session cleanup. Add tests for revoked/not-found credentials and late callbacks after a UID change, and make MQ-405 mandatory.

[MAJOR] B-90  
Problem: The parity gate can pass when an existing Facebook user receives a different Firebase account on iOS.  
Evidence: [P3:450](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:450) explicitly allows `UID_PARITY=DIFFERENT` without failing MQ-404; [P3:556](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:556) defaults to releasing with inaccessible Android accounts. This also disconnects server entitlements: [P6:488](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:488) rejects purchases bound to another UID. Actual UID equality remains UNVERIFIED.  
Fix: Make differing UIDs fail the release gate. If observed, decide and assign a verified account-linking/migration flow before release, preserving data isolation and purchase ownership; technical remediation cannot be deferred to an owner-only question.

[MAJOR] B-49  
Problem: The revised pure-move baseline command fails because it stages destination paths before those paths exist.  
Evidence: [P3:427](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:427) includes NEW destination directories in `$own`, then runs `git add -A -- $own` before moving files. [P3:432](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:432) repeats this for destination filenames. Git rejects unmatched, nonexistent pathspecs; the following PowerShell statements do not check its exit code. [Git implementation](https://raw.githubusercontent.com/git/git/master/builtin/add.c).  
Fix: Capture the baseline using only existing owned source paths, retaining source and destination paths for the subsequent diff. Check every Git exit code and restore `GIT_INDEX_FILE` in `finally`; add this exact pre-move capture case to F-30.

[MAJOR] B-50a  
Problem: The repository conversion calls a logging API that its dependency does not declare.  
Evidence: [P3:429](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:429) replaces `android.util.Log` with `AppLog`. However, [P2:320](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p2-data-domain.md:320) declares top-level `logDebug`, `logWarn`, and `logError` functions, and D-43 implements those functions; neither task declares an `AppLog` object. [P3:82](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:82) itself records the correct function contract.  
Fix: Specify the actual top-level functions in B-50a and update B-91’s logging scan accordingly. Keep one logging API and require compilation plus a negative test detecting sensitive output through each declared function.

[MAJOR] B-65  
Problem: The prescribed public factory signature violates the mandatory iOS bridge checker.  
Evidence: [P3:438](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:438) declares public `createIosCloudServices(..., deviceClock: DeviceClock? = null)`. [P1:563](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:563) requires `check-ios-bridge.sh` to reject default parameters at the exported boundary. P4’s callers require that checker to pass.  
Fix: Make this Kotlin-only factory `internal`, since `MainViewController` calls it within `:shared`, or remove the exported default and provide the required exception contract. Add `check-ios-bridge.sh` to B-65’s acceptance commands instead of discovering the conflict downstream.

[MAJOR] B-109  
Problem: New WebM uploads can be marked ready before their asynchronous MP4 derivative is available for playback.  
Evidence: [P3:466](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:466) sets `eager_async: true` and merely records the first request’s status. [media.js:256](F:/Github_Project/DemNgayYeuAndroid/firebase/functions/src/media.js:256) confirms the original asset and marks it active without checking the derivative. [P4:553](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p4-ui-compose.md:553) reports playback failure without retrying conversion readiness. Cloudinary documents that asynchronous eager transformations finish after upload. [Cloudinary documentation](https://cloudinary.com/documentation/eager_and_incoming_transformations).  
Fix: Define readiness and failed-conversion handling before returning the playback URL. Add pending-then-ready and conversion-failed tests, plus immediate iOS playback of a newly uploaded Android WebM in MQ-607.

[MAJOR] B-04  
Problem: The upload spike accepts a memory result that the final feature gate rejects, while disabling its only assigned fallback.  
Evidence: [P3:388](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:388) classifies peaks above 150 MB through 300 MB as WARN and retains Ktor. [P3:458](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:458) skips URLSession for WARN, but [P3:450](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:450) requires `PEAK_MB <= 150`. A 200 MB result has no assigned path to acceptance.  
Fix: Use one memory threshold throughout, and trigger B-102 for every spike result exceeding the release threshold. Test the decision at 150 MB and immediately above it.

[MAJOR] B-82  
Problem: The iOS media contract drops video cover images that the existing memory grid requires.  
Evidence: [LocalMediaStore.kt:57](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/data/media/LocalMediaStore.kt:57) extracts a video frame and returns it as `coverUri`; [MemoriesGridScreen.kt:331](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/ui/screens/MemoriesGridScreen.kt:331) uses `photoUri` for the grid thumbnail and excludes cloud video resolution. [P3:447](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:447) and U-101c explicitly set video covers to null, leaving FP-201 without Android-equivalent previews.  
Fix: Assign video-frame extraction, durable cover storage, and a `coverUri` field across the Swift/Kotlin picker boundary. Add a real-video test and an FP-201/MQ-207 check showing the saved video’s cover after relaunch.

[MINOR] B-19  
Problem: Authenticated media-delivery changes are routed to the routine-work model despite touching security behavior.  
Evidence: [P3:398](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:398) assigns `haiku` to a change in signed delivery URLs and explicitly requires token-validity verification. The owner’s routing rule assigns security and critical logic to `sonnet`.  
Fix: Change B-19 to `sonnet`, effort high; retain its small scope and existing acceptance checks.

## Missing steps

- Explicitly assign **Firestore App Check enforcement** and a real-service rejection check for missing/invalid tokens, alongside valid iOS App Attest/DeviceCheck and Android Play Integrity requests. B-13 checks callable source configuration; it cannot establish Firestore’s console setting, which remains UNVERIFIED. Firebase enables enforcement separately for each product. [Firebase enforcement documentation](https://firebase.google.com/docs/app-check/enable-enforcement).
- Complete the accepted round-1 **Swift gateway behavior tests through the actual gateway implementations**. The disposition at review-log line 82 promises this coverage, but B-67/B-68/B-70/B-83 primarily test `OneShot`, `AuthOperationScope`, `FlowSession`, and file helpers. Add gateway-level tests that fail when the wrappers omit cancellation, UID checks, delegate retention, or required queue handling.
- Assign durable **pending-upload identity and restart recovery** for FP-209/MQ-202. [MemoryMediaUseCases.kt:49](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/domain/media/MemoryMediaUseCases.kt:49) generates the memory ID and uploads before storing the memory; B-83 preserves file bytes but no task persists that ID or upload phase. Test termination after upload/confirmation but before the local save, resuming with the same ID and avoiding duplicate reservations.

VERDICT: FAIL

