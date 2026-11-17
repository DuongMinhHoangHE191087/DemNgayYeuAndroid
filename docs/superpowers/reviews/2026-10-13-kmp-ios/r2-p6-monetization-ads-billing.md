[BLOCKER] M-12  
Problem: The accepted round-1 receipt-theft finding remains open for Google purchases because an unknown account token still permits first-claimer ownership.  
Evidence: The disposition accepts this finding in `2026-10-13-kmp-ios-log.md:129`, but [M-12]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:483) explicitly tests `googleUnregisteredToken_sameAsNoToken_legacyPath` and grants missing/unregistered-token purchases to the first claimant during the legacy window. A window, per-uid limit, and audit record do not establish ownership. Current `BillingManager.kt:266–268` confirms that legacy purchases lack an account binding.  
Fix: Reject unknown non-null tokens; recover tokenless legacy purchases through an audited process requiring ownership evidence. Add theft tests for both branches while the legacy window is open.

[BLOCKER] M-15  
Problem: A delayed Apple store response can overwrite a newer refund and restore unpaid VIP because response arrival time is treated as the store-state revision.  
Evidence: [M-100]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:508) assigns `asOfMs = deps.nowMs()` after the response arrives; [M-15]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:486) accepts larger timestamps and clears Apple revocation from the incoming state. An active snapshot requested before REFUND but returned afterward therefore wins. Apple defines signed snapshot ordering through [`signedDate`](https://developer.apple.com/documentation/appstoreservernotifications/signeddate?changes=_6__8).  
Fix: Preserve the verified Apple snapshot revision and transaction identity; fence concurrent reconciliation and require newer authoritative recovery before clearing revocation. Add `activeFetchCompletesAfterRefund_staysRevoked`.

[MAJOR] M-36c  
Problem: Two refreshes within the same account generation can install an older VIP token after a newer verified non-VIP response.  
Evidence: [M-36c]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:522) checks uid, session generation, signature, and each request’s nonce, but specifies neither refresh serialization nor response ordering. Foreground/ticker refresh and purchase-triggered forced refresh provide competing callers (`p6:523,525`). Both responses can satisfy every stated check. Applying those prescribed checks in memory allowed the late VIP response to replace the newer non-VIP state.  
Fix: Serialize refreshes or add a request generation and reject obsolete completions; also reject decreasing signed issuance times. Test both completion orders during refund and purchase confirmation.

[MAJOR] M-19  
Problem: Refunding an older Apple subscription transaction can revoke a newer, still-paid renewal in the same subscription chain.  
Evidence: [M-13]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:484) maps every transaction to `apple_<originalTransactionId>` and lets any `revocationDate` override active status. [M-19]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:490) applies the notification transaction directly using the newer notification signing time. The stored contract (`p6:442`) omits the transaction ID needed to distinguish billing periods. Apple’s [REFUND notification](https://developer.apple.com/documentation/AppStoreServerNotifications/notificationType) concerns a particular transaction.  
Fix: Reconcile subscription notifications against the current subscription status, or retain transaction-level state and aggregate the chain. Add `refundPreviousPeriod_currentPaidRenewalRemainsVip`.

[MAJOR] M-90  
Problem: Out-of-app Google repurchases cannot reliably recover their established owner from the predecessor purchase, including after a supported account rebind.  
Evidence: [M-12]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:483) binds using the current token owner or legacy first-claim rules. [M-90]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:506) uses the predecessor only for supersession/conflict checks; M-89 changes purchase ownership without defining how future repurchases follow it. Legacy repurchases with no account identifier become orphans, while repurchases after rebind can resolve to the deleted/previous token owner. Google explicitly permits association through [`expiredPurchaseToken`](https://developer.android.com/google/play/billing/lifecycle/subscriptions).  
Fix: Resolve verified predecessor ownership before binding, preserve audited rebind lineage, and test legacy and rebound repurchases without reopening the app.

[MAJOR] M-14  
Problem: Deferred Google plan changes can permanently remove a paying subscriber’s VIP because normalization selects the first recognized line item rather than the current entitlement.  
Evidence: [M-14]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:485) selects the first catalog product in `lineItems`. Google’s [deferred-replacement documentation](https://developer.android.com/google/play/billing/subscriptions#handle-deferred-replacement) shows both the old and new items; after replacement, the old item has a past expiry while the new item has a future expiry. Selecting the old item makes the resolver deny VIP despite the paid replacement.  
Fix: Select entitlement-bearing items using their expiry and replacement state. Test before and after the deferred transition, both array orders, and the future item without an expiry.

[MAJOR] M-91  
Problem: The accepted round-1 missed-recovery finding remains unresolved because terminal purchases are explicitly excluded from server revalidation.  
Evidence: `2026-10-13-kmp-ios-log.md:131` accepts recovery handling, but [M-91]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:507) requires `terminalState_notPolled` and delegates recovery to a local client claim. If an Apple refund reversal/recovery notification is lost and the user subsequently uses Android, Play’s local transaction enumeration cannot rediscover that Apple purchase. Repeated `getEntitlement` calls keep returning inactive state.  
Fix: Give recoverable terminal states a bounded reconciliation policy independent of the local store. Add `missedAppleRecovery_androidConsumer_regainsVip` and equivalent cross-store recovery coverage.

[MAJOR] M-73  
Problem: Adding the App Check factory can break the next iOS app build because its provider-choice module is not yet linked into the app.  
Evidence: [M-73]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:565) places `AppCheckProviderChoice` in `InLoveBilling` and its consumer in the automatically compiled app `Gateways` directory. Package linkage occurs only in M-95 (`p6:566`). `p3-backend-auth-media.md:439` explicitly excludes monetization linkage from B-66. The current read-only schedule places M-73 in wave 61, B-66’s app build in wave 62, and M-95 in wave 66.  
Fix: Put provider choice in an already-linked bootstrap module or add P4 linkage before creating its consumer. Require an app-target compilation check in M-73.

[MAJOR] M-92  
Problem: The foreground entitlement ticker can enter a zero-delay loop at expiry and block the app.  
Evidence: [M-92]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:523) unconditionally delays by `min(deadlineMs, 60 seconds)`. M-33 (`p6:515`) supplies remaining entitlement time but defines no positive polling interval for `EXPIRED`, `NO_SNAPSHOT`, or other inactive states. At the exact deadline, the prescribed delay is zero; refresh backoff prevents another network call but does not stop repeated local evaluations.  
Fix: Define a positive wait for states without a future entitlement deadline, while publishing expiry immediately. Add bounded-evaluation tests for expired, signed-out, non-VIP, and offline states.

[MAJOR] M-98  
Problem: The iOS kill switch ignores the JSON payload mandated by its own design and decisions ledger.  
Evidence: `p6:361` and `DECISIONS.md:209` specify `sdk_kill_ios` with the same JSON structure as Android. Current `appplugin/src/main/java/com/app/plugin/SdkKillSwitch.kt:69–82` parses fields including `ads`, `iap`, and `below_version`. However, [M-98]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:554) recognizes only literal `true`/`false`; `{"ads":true}` leaves a previously un-killed app unchanged.  
Fix: Decide one payload format and align M-98, DEC-192, console instructions, and tests. Test the exact published payload, persistence, malformed input, and offline restart.

[MAJOR] M-41  
Problem: After the banner cutover, an Android kill applied during a foreground session may leave the actual Compose banner running until the next foreground transition.  
Evidence: [M-41]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:530) samples `SdkAdsPort.isDisabled()` through `refresh()`; M-46 (`p6:537`) calls refresh at foreground and initialization. M-54a (`p6:543`) removes the banner’s direct SDK kill check and makes it depend on `BannerSpec`. No listener or foreground polling updates `engine.killed` when the SDK changes its flag. Recomposition alone cannot refresh that cached flow.  
Fix: Add an owned runtime observation or bounded polling path. Test killing an already-visible banner without navigation or a lifecycle transition.

[MAJOR] M-80  
Problem: P6’s final verification does not cover the added critical scenarios and conflicts with the feature matrix’s account-recovery acceptance.  
Evidence: [M-80]( /F:/Github_Project/DemNgayYeuAndroid/docs/superpowers/plans/2026-10-13-kmp-ios/p6-monetization-ads-billing.md:568) explicitly lists MQ-101–109 but omits MQ-110–114. `p8-feature-parity-matrix.md:203` requires automatic reassignment of a Sandbox purchase after account deletion, whereas P6’s tombstone policy returns `owner-deleted` and M-89 (`p6:505`) refuses Sandbox rebinds. That script cannot pass under the decided implementation.  
Fix: Route the recovery script to the decided tombstone/support behavior, distinguish Sandbox and Production rebind evidence, and explicitly include MQ-110–114 in the applicable verification tasks.

[MINOR] GENERAL  
Problem: The inventory and estimate still contain incorrect facts despite round-1 dispositions saying they were corrected.  
Evidence: `p6:149` says the Swift package has no UMP, although `monetization-brain-ios/Sources/MonetizationBrainUMP/ConsentManager.swift:1–15` imports and wraps it. `p6:552` dates `BannerView` to 11.x; Google documents that rename in [12.0](https://developers.google.com/admob/ios/migration#migrate-from-sdk-version-v11-to-v12). `p6:705–713` estimates 93 tasks/108 days; the current table contains 96 tasks/112.5 upper-bound days. The npm “latest” assertions at `p6:87` remain UNVERIFIED in this review because registry retrieval failed.  
Fix: Correct those statements and regenerate counts and totals from the current table.

## Missing steps

- Make the round-1-required SDK foreground integration check mandatory for M-99: a server-rejected purchase must remain unacknowledged and must not regain ad-removal state through SDK restore. If Robolectric cannot run it, assign a concrete device check instead of dropping it as UNVERIFIED.
- Explicitly assign `Transaction.unfinished` enumeration and startup delivery buffering to the StoreKit facade/bridge tasks. Test a transaction emitted before the Kotlin subscriber attaches and its recovery after sign-in; the current StoreKit task rows primarily specify `updates` and `currentEntitlements`.
- Assign owned files and execution commands for the promised `IosBridgeLeakTest` and `CancelOnDisposeTest` (`p6:417`), including repeated Kotlin/Swift creation, cancellation, late callbacks, and release.
- Schedule genuine Production refund/revoke validation after the first release, with server logic tests and supported Sandbox evidence as the pre-submission gate. Resolve the “real App Store purchase/refund before M-80” requirement at `p6:698`; unsupported Sandbox events must remain UNVERIFIED.
- Add explicit StoreKit offer-mapping tests for eligible and previously-used introductory offers, and align Apple grace duration with App Store Connect. Cover missing grace-expiry metadata and prevent granting a fabricated fallback interval.

VERDICT: FAIL

