[BLOCKER] B-70  
Problem: The selected Facebook login implementation crashes against the pinned Firebase SDK outside the emulator.  
Evidence: [p3-backend-auth-media.md:424](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:424) constructs `OAuthProvider(providerID: "facebook.com")`; [Firebase 12.19.2’s OAuthProvider.swift](https://raw.githubusercontent.com/firebase/firebase-ios-sdk/12.19.2/FirebaseAuth/Sources/Swift/AuthProvider/OAuthProvider.swift), lines 68–72, explicitly calls `fatalError` for that provider when no emulator is configured. This is a verified incompatibility, not an UNVERIFIED claim.  
Fix: Choose the supported Facebook Limited Login flow now, assign its SDK/configuration files with appropriate shared-file labels, and require a production-backed device check before downstream bootstrap tasks.

[BLOCKER] B-49  
Problem: The proposed move crosses module boundaries before its required types and tests are ready, breaking “Android always green.”  
Evidence: [AuthMessages.kt:7](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/ui/util/AuthMessages.kt:7) imports `UnlockResult`, which B-28 leaves in `:app` until B-51; [B-49](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:410) moves `AuthMessages` earlier. It also moves internal media parsers into `shared` while [FirebaseMemoryMediaParseTest.kt:31](F:/Github_Project/DemNgayYeuAndroid/app/src/test/java/com/example/data/media/FirebaseMemoryMediaParseTest.kt:31) remains in `:app` and calls them.  
Fix: Move the standalone `UnlockResult` contract before its consumers, and assign migration/adaptation of the parser tests alongside their implementation, including the changed exception type.

[MAJOR] B-25  
Problem: The tasks do not implement the decided UID-based isolation for Apple accounts and retain an email-based account-merging path.  
Evidence: [DECISIONS.md:263](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md:263), DEC-246, requires Apple scopes based on Firebase UID without automatic email merging. [AuthRepository.kt:926](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/data/repository/AuthRepository.kt:926) falls back to lookup by email, rewrites the existing UID, and calls `enterScope(account.email)`; [B-25](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:387) changes provider names and placeholders without replacing this behavior.  
Fix: Explicitly assign Apple UID lookup/scope handling across login and restore; preserve legacy email scopes for password accounts. Add same-email/different-UID and relay-email-change isolation tests.

[MAJOR] B-43  
Problem: Advancing the device clock still bypasses the progressive PIN lockout.  
Evidence: [UnlockAttemptStore.kt:22](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/data/auth/UnlockAttemptStore.kt:22) opens the gate once wall time exceeds `unlock_until`; [B-43](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:404) preserves that algorithm and tests only rollback. B-20 marks `clock-forward-keeps-lock` optional, and B-49 skips it. [00-master.md:328](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/00-master.md:328) confirms this remains open.  
Fix: Assign a bounded task using persisted monotonic timing plus boot identity, reuse P6’s clock seam where appropriate, and make forward-jump, rollback, restart, and reboot lockout tests mandatory.

[MAJOR] B-97  
Problem: The cleanup contract can classify referenced media as orphaned after an iOS container change and delete it.  
Evidence: [B-97](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:438) compares paths as unchanged strings. [DECISIONS.md:261](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/DECISIONS.md:261) deliberately leaves old absolute `file://` URLs in Room and rebases them when reading. Enumerated files under the current container therefore differ from their stored references; URI versus bare-path representations can also differ.  
Fix: Specify one canonical filesystem-path representation for enumerated, referenced, and uploading files before calling the sweeper. Add a stale-container-reference test proving that a referenced file older than 24 hours is retained.

[MAJOR] B-41  
Problem: NFC password handling is a design statement without an implementation task or a failure-detecting acceptance test.  
Evidence: [p3-backend-auth-media.md:286](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:286) requires normalized registration/password changes and NFC-then-raw login. B-23 replaces Firebase calls, B-41 replaces cryptographic primitives, and B-50 removes platform dependencies; none assigns that behavior. [AuthRepository.kt:710](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/data/repository/AuthRepository.kt:710) also checks the raw local password hash before reauthentication.  
Fix: Add a dedicated auth-normalization task covering Firebase calls and local password fallback coherently, with NFC/NFD registration, login, password-change, and PIN-fallback tests while preserving legacy hashes.

[MAJOR] B-96  
Problem: App relocking and local orphan cleanup remain disconnected helpers despite the matrix treating them as assigned features.  
Evidence: [B-96 and B-97](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:437) leave their callers to “P4 U-100a or P5” and “N-48 or N-19.” Those task rows do not implement these calls. [00-master.md:330](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/00-master.md:330) explicitly confirms both integrations are still unowned.  
Fix: Assign concrete Mac tasks owning scene lifecycle relocking and startup cleanup, with dependencies on B-96/B-97 and the relevant services. Require foreground relocking and actual orphan-file deletion checks, not only pure-function tests.

[MAJOR] B-47  
Problem: The documented iOS upload resolver preserves a file URL rather than producing the filesystem path required by the file reader.  
Evidence: [B-47](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:411) reads through okio after `resolvePath`; B-65 supplies `rebaseContainerUrl(..., NSHomeDirectory())`. [U-103](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p4-ui-compose.md:544) explicitly preserves `file://` and percent encoding. Replacing the container prefix alone does not yield a usable local filesystem path.  
Fix: Define URL-to-path conversion at the platform boundary, including percent decoding and rejection of non-file schemes. Test an actual upload from a rebased file URL containing spaces and Vietnamese characters.

[MAJOR] B-69  
Problem: Treating Apple’s first-authorization name as the sole display-name source loses the saved name on subsequent sign-ins without local cache.  
Evidence: [B-69](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:423) calls `formattedFullName` the only display-name source and returns nil when components are absent. [Firebase’s Apple authentication documentation](https://firebase.google.com/docs/auth/ios/apple) explains that later authorizations omit the name but Firebase retains it in `currentUser.displayName`. B-25 tests only the initial profile name.  
Fix: Specify first-time persistence through `appleCredential(...fullName:)`, then use the Firebase user’s stored display name when Apple returns nil. Add a repeat-sign-in-after-reinstall test.

[MAJOR] B-63  
Problem: The bridge task assigns a second declaration of `Subscription` instead of reusing the foundation contract.  
Evidence: [B-63](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:417) declares its bridge callbacks including `Subscription` under `com.example.ios`. [F-26](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:560) already creates `shared/src/iosMain/kotlin/com/example/ios/Subscription.kt` with that interface. B-63 has no dependency on F-26.  
Fix: State explicitly that B-63 reuses F-26’s `Subscription`, exclude it from new declarations, add the dependency, and compile all bridge consumers against that one contract.

[MAJOR] B-95  
Problem: The prescribed failure return is incompatible with the auth methods’ preserved public signatures.  
Evidence: [B-95](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:436) instructs login, registration, and social login to return `Result.failure` after vault errors. [AuthRepository.kt:282](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/data/repository/AuthRepository.kt:282), `register` at line 489, and `loginWithFacebook` at line 917 return `Pair<Boolean, String>`; B-25 preserves this shape for `loginWithSocial`.  
Fix: Specify `false to <stable localized error>` for those methods, reserve `Result.failure` for methods already returning `Result`, and make the tests assert both the return contract and unchanged account scope/state.

[MAJOR] B-04  
Problem: The upload spike can reject a correct multipart transfer and miss the transient memory peak it is meant to detect.  
Evidence: [B-04](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:371) sends multipart but expects received bytes to equal exactly 52,428,800; even a minimal multipart wrapper adds bytes. It measures RSS before and after, allowing temporary body copies to disappear before measurement. B-47 does not depend on B-04.  
Fix: Count the extracted file payload, sample peak RSS throughout upload, and make the production uploader depend on the result. Give the URLSession fallback concrete owned files, checks, and downstream wiring if selected.

[MAJOR] B-40  
Problem: Apple source changes are assigned to Windows, and several Mac acceptance commands are not executable as written.  
Evidence: [B-40](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:402) writes `SecureRandom.ios.kt` with `host=win`; B-93 edits `CreateIosCloudServices.kt` with `host=win`. B-63/B-64/B-65/B-82 prescribe `.\gradlew.bat` on Mac. B-72 uses `Select-String -Recurse`, although that cmdlet has no `-Recurse` parameter.  
Fix: Split Apple edits into Mac tasks with Apple compilation/tests; retain common/Android work on Windows. Use `./gradlew` and portable `rg` checks for Mac acceptance commands.

[MAJOR] B-49  
Problem: The pure-move acceptance check cannot pass for the task’s specified changes under the no-commit workflow.  
Evidence: [B-49](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:410) combines renames, a new golden test, and edits to `client-contract.test.js`, then invokes `check-pure-move.sh <base-ref>`. [F-30](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:562) defines that checker to reject additions/modifications and makes its default inspect committed `HEAD`; uncommitted work requires `--worktree`. B-51 repeats the default invocation.  
Fix: Separate supporting edits/test creation from the rename-only check, use `--worktree` with an explicit task snapshot reference, and restrict the checked changes to the declared moves.

[MINOR] GENERAL  
Problem: A VERIFIED Firebase release-note claim is incorrect.  
Evidence: [p3-backend-auth-media.md:42](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:42) says the official page contains no 13.0.1. The [Firebase Apple SDK release notes](https://firebase.google.com/support/release-notes/ios) explicitly link 13.0.1 and state that 13.0.0 and 13.0.1 are identical for SPM users.  
Fix: Correct assumption 25 and its comparison wording; retain the independently justified 12.19.2 pin.

## Missing steps

- Assign the forced Firebase ID-token refresh required by DEC-80 to the deletion flow’s actual gateway/task contract. B-38 currently specifies reauthentication, revocation, and the callable, but no refresh operation. Add an acceptance case proving the callable receives the refreshed `auth_time` before local deletion.
- Add the owner action registering Firebase’s email sender or custom sender domain with Apple Private Email Relay before release verification. This prerequisite is documented in [Firebase’s Apple authentication guide](https://firebase.google.com/docs/auth/ios/apple) and is absent from Part 3’s owner-action table.
- Add concrete Swift gateway behavior tests for B-67/B-68/B-70/B-83: cancellation followed by late completion, exactly-once completion, UID changes during an operation, delegate/subscription release, and main-thread presentation versus background media processing. Protocol compilation and Kotlin fake-bridge tests do not exercise the real Swift implementations.
- Explicitly preserve constant-time `secureEquals` when removing `MessageDigest` in B-41; the current implementation is [AuthSecurityManager.kt:121](F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/ui/util/AuthSecurityManager.kt:121). Name the replacement and its acceptance checks.
- Extend MQ-607 to verify Android-uploaded HEIC and every retained Android video format on iOS, including WebM, rather than only iOS media exported to JPEG/MP4. Add the PHPicker → processed metadata → `validateMemoryMedia` → signed upload integration test requested by [p8-feature-parity-matrix.md:583](F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p8-feature-parity-matrix.md:583).

VERDICT: FAIL

