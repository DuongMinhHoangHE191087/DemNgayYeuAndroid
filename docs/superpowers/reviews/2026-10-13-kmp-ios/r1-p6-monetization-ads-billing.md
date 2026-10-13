[BLOCKER] M-13  
Problem: Sandbox purchases can grant production VIP, allowing a free TestFlight lifetime purchase to become a permanent entitlement on Android or the released iOS app.  
Evidence: [p6:259](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:259) accepts Sandbox into the same entitlement system; p6:419–422 records environment internally but omits it from the client response and grant policy. [Apple’s TestFlight guidance](https://testflight.apple.com/) confirms beta purchases are free.  
Fix: Separate test and production entitlement namespaces and authorization policies; production consumers must reject Sandbox grants. Add `sandboxLifetime_neverGrantsProductionVip` and a same-uid TestFlight-to-production migration test while preserving App Review’s sandbox flow.

[BLOCKER] M-33  
Problem: A well-formed edited snapshot can grant VIP without any server-verified purchase.  
Evidence: [p6:273–277](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:273) trusts unsigned `vip`, expiry, server-time, and clock anchors in SharedPreferences/UserDefaults. M-36a checks syntax rather than provenance. An in-memory reproduction of the specified evaluator returned `vip=true` for a fabricated lifetime snapshot with matching clock anchors. This contradicts [MQ-103’s acceptance criterion](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p8-feature-parity-matrix.md:191).  
Fix: Require a server-signed, uid-bound, environment-bound authorization snapshot with a fixed offline deadline. Test validly encoded edits to every authorization field, cross-uid replay, and repeated clock-anchor replacement.

[BLOCKER] M-25  
Problem: The plan explicitly leaves server-cost features without the entitlement gate required by the owner.  
Evidence: [p6:269](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:269), M-25, and p6:597 defer all `requireEntitlement` integration. However, [_addendum-owner-update.md:25](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/_addendum-owner-update.md:25) explicitly includes media uploads. [firebase/functions/index.js:107–125](/F:/Github_Project/DemNgayYeuAndroid/firebase/functions/index.js:107) currently checks authentication, App Check, and quotas without entitlements.  
Fix: Add a concrete, jointly sequenced P3/P6 task enforcing the owner-required media entitlement policy at server entry points, with tests denying absent, expired, revoked, and Sandbox entitlements. Keep unimplemented AI functionality explicitly skipped.

[BLOCKER] M-12  
Problem: First-claimer binding permits an attacker with another person’s unbound transaction identifier to take that purchase.  
Evidence: [p6:251](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:251) accepts missing or unregistered account tokens, including deleted-account tokens; M-12 deliberately tests this behavior. p6:421 accepts only `{store,id}`, so server verification proves that a purchase exists, not that the caller owns it. M-21 deletes the token mapping and enables another uid to reclaim.  
Fix: Reject unknown tokens for ordinary claims. Define a separate audited legacy/deleted-account recovery process with ownership evidence and retained binding tombstones. Test receipt theft before first claim and after account deletion.

[MAJOR] M-41  
Problem: The SDK can still acknowledge purchases and overwrite server-derived ad-removal state independently of `AndroidVipBridge`.  
Evidence: [p6:491](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:491) claims SDK entitlements will exclusively follow server state. Yet [MonetizationSdk.kt:230–234](/F:/Github_Project/DemNgayYeuAndroid/appplugin/src/main/java/com/app/plugin/MonetizationSdk.kt:230) starts `IapHelper`; [AppPluginBase.kt:215](/F:/Github_Project/DemNgayYeuAndroid/appplugin/src/main/java/com/app/plugin/app/AppPluginBase.kt:215) restores on foreground; and [IapHelper.kt:282,385–386](/F:/Github_Project/DemNgayYeuAndroid/appplugin/src/main/java/com/app/plugin/iap/IapHelper.kt:282) synchronizes, acknowledges, and grants locally. M-50 retains product configuration.  
Fix: Explicitly disable the SDK billing/restore writer during cutover using a verified supported integration path. Add a foreground integration test proving server rejection cannot be acknowledged or overridden by SDK restore.

[MAJOR] M-18  
Problem: Missed revocation notifications can preserve refunded lifetime VIP indefinitely, and missed recovery notifications can leave paying subscribers inactive.  
Evidence: [p6:257](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:257) and M-18 recheck only subscriptions near expiry or expired in nonterminal states; lifetime purchases and terminal states are excluded. p6:595 defers reconciliation. Repeated `getEntitlement` calls can therefore renew the client’s 72-hour trust window from stale server data indefinitely.  
Fix: Give every store-backed purchase a bounded verification freshness policy, including lifetime and terminal states. Preserve a usable, protected store lookup identifier—the Google SHA-256 key alone cannot query Play. Test missed lifetime refund and missed subscription recovery notifications.

[MAJOR] M-11  
Problem: Adding 16 days to Google’s grace-period expiry grants benefits beyond the store-authorized period.  
Evidence: [p6:255](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:255) defines Google grace as `expiryTime + graceMaxDays`. [Google’s subscription lifecycle documentation](https://developer.android.com/google/play/billing/lifecycle/subscriptions#grace-period) states that Play already dynamically extends `expiryTime` during grace and access ends when grace becomes account hold. The proposed rule double-counts grace if a hold notification is delayed or lost.  
Fix: Use the verified store expiry directly, with a bounded freshness deadline. Add vectors covering grace expiry, transition to hold, delayed RTDN, and recovery; remove the UNVERIFIED semantic substitution.

[MAJOR] M-14  
Problem: Google purchase replacement and out-of-app repurchase handling can leave duplicate entitlements or automatically refund a legitimate new purchase.  
Evidence: [p6:419](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:419) omits `linkedPurchaseToken` and `outOfAppPurchaseContext`; M-14/M-20 omit their handling, and acknowledgement exists only in the client-triggered claim path. [Google’s security guidance](https://developer.android.com/google/play/billing/security#verify) requires revoking linked-token entitlement; [its lifecycle guidance](https://developer.android.com/google/play/billing/lifecycle/subscriptions#resubscribe-after-expiration) requires server association and acknowledgement for out-of-app repurchases.  
Fix: Persist purchase lineage, atomically retire replaced grants, recover the prior uid from verified repurchase context, and durably retry server acknowledgement independently of app launches. Add replacement-across-uids and app-never-reopened tests.

[MAJOR] M-19  
Problem: Notification deduplication is not atomically coupled to entitlement application, so a failed update can be permanently skipped on retry.  
Evidence: [p6:465–466](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:465) specifies writing `notificationLog` through `tx.get`/`tx.set`, followed by a separate `applyStoreState` transaction. Neither webhook suite tests failure after the marker commits but before entitlement application. A refund can consequently be marked processed while VIP remains active.  
Fix: Commit the processed marker with the entitlement mutation, or implement recoverable processing/processed states. Add `dedupeCommitted_applyFails_retryStillApplies` and concurrent duplicate-delivery tests for both stores.

[MAJOR] M-36c  
Problem: An entitlement response started under account A can restore A’s VIP after logout or after switching to account B.  
Evidence: [p6:484](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:484) clears snapshots on uid changes but specifies no generation fence for pending responses; M-38a similarly lacks an account boundary for claims and scheduled retries. p6:422’s response contains no uid. Current tests cover immediate uid changes, not delayed completion.  
Fix: Capture uid and session generation for refreshes, claims, retries, and snapshot writes; discard late results after the generation changes. Add `refreshACompletesAfterLoginB_discarded`, `claimCompletesAfterLogout_noVip`, and retry cancellation tests.

[MAJOR] M-38a  
Problem: The claim protocol finishes transactions without granting their purchase and does not define outcomes for several legitimate server responses.  
Evidence: [p6:313–319](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:313) finishes `AlreadyBound`, `FamilyShared`, and `NotOurs`, contrary to the owner’s finish-after-grant requirement. The mapping handles `vip=false` only for `pending`, although M-17 returns revoked purchases without granting and M-12 returns `too-many-purchases`. M-35 also provides no durable transaction-correlated rejection state.  
Fix: Define transaction-specific outcomes for granted, inactive, revoked, quota-rejected, and recoverable ownership failures. Preserve unfinished purchases until the required grant or an explicitly resolved refund/support disposition; test every response and subsequent recovery.

[MAJOR] GENERAL  
Problem: Several task boundaries cannot satisfy “Android always green” or their own acceptance checks.  
Evidence: [M-39b](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:489) compiles commonMain against `BannerSpec` still in `:app`, without depending on M-48. [M-51](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:499) forbids all `billingManager` references while leaving methods at [InLoveViewModel.kt:1133,1140](/F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/ui/viewmodel/InLoveViewModel.kt:1133) until M-52. M-54 edits banner code in `MainActivity` after [U-47](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p4-ui-compose.md:208) moves it into unowned `ui/app` files.  
Fix: Add the migration dependency, move the remaining VM references in M-51, and update M-54’s exact ownership and checks to the extracted files.

[MAJOR] M-69b  
Problem: The iOS bootstrap does not establish a single ordered owner for App Check installation before Firebase configuration.  
Evidence: [M-73](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:520) assigns the installation call to M-69b, but M-69b owns only `iOSApp.swift`, project wiring, and monetization bootstrap. [B-72](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p3-backend-auth-media.md:421) separately owns provider installation and `FirebaseApp.configure()` in `CloudBootstrap`; M-69b has no B-72 dependency. Factory-choice tests do not prove launch ordering or working tokens.  
Fix: Assign installation to the actual Firebase bootstrap owner, add explicit dependencies and shared-file sequencing, and test one configuration call with provider installation preceding it and successful debug/release callable authentication.

[MAJOR] M-62b  
Problem: The planned kill switch does not reliably disable actual banners offline on either platform.  
Evidence: [M-41](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:491) hard-codes Android `killed=false`, but [AdBannerPlaceholder.kt:68–71](/F:/Github_Project/DemNgayYeuAndroid/app/src/main/java/com/example/ui/components/AdBannerPlaceholder.kt:68) loads `AdView` directly, bypassing `AdsHelper`’s kill check. M-62b owns no persisted kill implementation or `KillSwitchTests`, despite p6:343/394 promising both. Its Remote Config listener also lacks the [activation step required to expose fetched values](https://firebase.google.com/docs/remote-config/ios/real-time#selectively_activate_parameter_values).  
Fix: Gate the actual Android banner path and add an owned iOS kill-state implementation with startup restoration, fetch/activation, session latching, and offline tests; make `FirebaseRemoteConfig` an explicit prerequisite rather than an optional fallback.

[MAJOR] GENERAL  
Problem: Multiple acceptance commands fail for infrastructure reasons or inspect the wrong revision, preventing reviewable completion.  
Evidence: [p6:446](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:446) specifies PowerShell 5.1, yet M-05/M-09/M-15–M-26/M-24 use `&&`. M-47–M-49 invoke `check-pure-move.sh HEAD`; [F-30](/F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p1-foundation-build.md:556) defines that checker as `<base-ref> HEAD` and rejects an empty diff, so it cannot verify uncommitted moves. M-49 also adds a golden test while requiring a rename-only diff.  
Fix: Use fail-fast PowerShell-compatible commands, verify staged/worktree moves without requiring a commit, and separate pure moves from new tests.

## Missing steps

- Add deadline-driven entitlement reevaluation while the app remains foregrounded. M-36c currently specifies foreground hooks but no timer; test expiry and the 72-hour offline cap without navigation, restart, or a foreground transition. Use these cases in GV-062.
- Make M-02 exercise an actual Compose `UIKitView` inside `ComposeUIViewController`. Its current task only tests a UIKit container, which cannot establish Compose touch routing, scrolling, disposal, or fallback placement. Record numeric thresholds and verify FP-807/MQ-109.
- Add an Xcode app-target compilation check to every task introducing `Ios*Bridge.swift`. The Kotlin/Native tests in M-61/M-62b/M-67/M-68 do not compile those Swift files; package tests likewise do not compile target-app bridge or Firebase-factory code.
- Replace the permanent ten-purchase-account limit with a policy that preserves fraud bindings while allowing legitimate subsequent purchases. Add tests for an eleventh historical purchase, repeated repurchases, and buying lifetime after multiple expired subscriptions.
- Generate the shared pure-function golden vectors from the Android implementation, as required by the addendum. M-49b currently describes manually writing expected vectors; also provide cross-platform vectors for `isoPeriodToDays` and `planOfferOf`.
- Resolve SDK event parity explicitly. “No first-party analytics” does not mean the Android SDK emits no events: FP-813 documents SDK telemetry/FCM behavior. Decide which existing SDK events are preserved or deliberately removed, update DEC-06 and FP-813, and include the resulting data collection in privacy-label inputs.
- Correct the verified inventory: `monetization-brain-ios` **does contain UMP** ([ConsentManager.swift:1–15](/F:/Github_Project/DemNgayYeuAndroid/monetization-brain-ios/Sources/MonetizationBrainUMP/ConsentManager.swift:1)); the app coordinator is `privacy/AppPrivacyCoordinator.kt`, not `ads/AppPrivacyCoordinator.kt`; p6:371’s Firebase 13.x conflict contradicts its own corrected assumption #29 and P3’s 12.19.2 choice; the shared brief’s AGP 9.4.1 claim conflicts with [libs.versions.toml:2](/F:/Github_Project/DemNgayYeuAndroid/gradle/libs.versions.toml:2), which currently specifies 9.2.1.
- Keep the SDK gap decision grounded in the verified missing ad-serving layer and StoreKit shortcomings. Do not describe the entire Swift package as only a decision brain or lacking consent.
- Split security-sensitive purchase mapping in M-42 onto `sonnet`, and explicitly split S tasks if their scope grows to include the missing native integration, persistence, or recovery logic.
- Refresh the combined DAG before execution. The read-only `plan-tools.mjs lint` run exited **1**, reporting foundation cycles and missing F-31/F-32 dependencies. It confirmed p6’s **74 tasks and estimate totals**, but does not establish that p6’s prerequisites are executable.

VERDICT: FAIL

