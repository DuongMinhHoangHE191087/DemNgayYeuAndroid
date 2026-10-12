# MonetizationBrainIOS

iOS (Swift) port of the Player Decision Platform / Monetization Brain SDK. Kept in its own
repo, separate from the Android SDK (`appplugin`), so iOS work never mixes with Android
history, branches, or build.

This is a multi-phase port — the Android side is a large platform (bandit/causal decisioning,
guardrails, paywall, IAP, subscription lifecycle, battle pass, offerwall, CRM bridge, and more).
Each phase gets its own design doc under `docs/superpowers/specs/` before implementation starts.

**Current phase:** Phase 12 — remote-config dispatch (the eight `apply()` entry points finally
have something that calls them, plus the Firebase-backed provider) and `BrainInspector`
(835 tests under `swift test`, plus 6 UMP and 28 StoreKit tests that only a simulator can run —
869 in total). Phases 1–11 are merged to `main`.

**Every Android subsystem is now ported.** A file-by-file parity audit against the Android source
(`brain/`, `iap/`, `paywall/`, `offerwall/`, `crm/`, `consent/`) found no missing subsystem:
28/28 `PlayerState` fields, all 5 decision points and 12 lifecycle hooks, all 8 config-driven
`apply()` entry points, and all 14 brain/IAP/paywall events match. What remains is integration
surface and follow-ups, listed at the end of this file — not missing features. Seven parity gaps
that audit surfaced are now closed (869 tests): `iapOffers24h` is persisted per UTC day so a
relaunch no longer hands back the `max_iap_offers_24h` allowance; `AdsBrain.comfort()` is a public
read; the UMP `ConsentManager` gained `canRequestAds()`/`isPrivacyOptionsRequired()`; and
`RemoteConfigDispatcher` + `MonetizationBrainFirebase` finally give the eight `apply()` entry
points something that calls them. Phases 6 and 7 and the follow-up
hardening those reviews surfaced are merged to `main`. See
[`docs/superpowers/specs/2026-09-15-subscription-lifecycle-design.md`](docs/superpowers/specs/2026-09-15-subscription-lifecycle-design.md)
for scope and architecture. Phase 1 shipped the plumbing (consent, event pipeline, remote config
abstraction, holdout bucketing, and an `AdsBrain` facade whose decision points return safe
baselines); Phase 1.5 wired the facade to that plumbing. Phase 2 ports the standalone
decision-making primitives (`PlayerState`, `BeliefState`, `RewardModel`, `LikelihoodEstimator`,
`BanditPolicy`). Phase 3 builds the orchestration layer on top — real
`GuardrailEngine` rules (HARD + RESOURCE), `ClickFraudGuard` (click-window lockout),
`LagrangianBudget` (shadow-price resource shaping), `PersonalizationScenario` (segment →
policy-knob mapping), `BrainStorage` (belief/bandit persistence), and the `DecisionEngine`
orchestrator with `decide`/`feedback`/`observe`. Phase 3.5 wires `DecisionEngine` into the public
facade: `AdsBrain` becomes a real stateful orchestrator with five `async` decision-point methods
(`shouldShowInterstitial`/`shouldOfferRewarded`/`shouldOfferIap`/`chooseCrossModal`/`chooseAssist`),
session-state ownership, twelve lifecycle/behavior hooks, and automatic feedback resolution.
Phase 4 ports the base in-app-purchase layer: `IapModels` (`IapProduct`, `IapPurchase`,
`IapResult`, enums), a `StoreBackend` protocol abstraction (enabling unit testing without real
App Store access), `Entitlements` (owned-product state persistence), `FxRates` (local-currency →
USD conversion), and `IapHelper` (the orchestrating facade) — all in `MonetizationBrainCore`,
plus a new `MonetizationBrainStoreKit` target containing the real StoreKit 2 conformance
(`StoreKit2Backend`). Apps requiring real purchases link both targets; test targets link only
`MonetizationBrainCore` with an in-memory `StoreBackend` fake. Phase 5 ports the paywall layer:
`PaywallManager` — a decision-only facade (not a UI layer) answering "what to sell, in what order,
with which variant" for a named placement. It reuses `HoldoutManager.isHoldout` directly (paywall
holdout IS the SDK-wide brain holdout, no separate mechanism), gates decisions through
`AdsBrain.shared.shouldOfferIap` (the same guardrails that protect every IAP offer), and applies
remote-config-driven A/B variant selection via a dedicated hash namespace (`"paywall_variant"`)
using the existing `HoldoutManager.bucketWith`. Two new events: `paywall_impression` and
`paywall_holdout_skip`.

## Integrating this SDK

Verify a change with **`Scripts/verify.sh`** — the macOS test suite, an iOS build of all four
targets, the simulator-only UMP tests, and warnings-as-errors over this package's own sources. A
green `swift test` alone does not compile the UIKit-guarded targets; see the section below on the
UMP target that was broken for iOS for several phases because of exactly that. CI
([`.github/workflows/verify.yml`](.github/workflows/verify.yml)) runs that same script and nothing
else, so there is one definition of done rather than two that drift.

### Swift 6 concurrency checking is on for every target

`Package.swift` enables `StrictConcurrency` on all four targets and both test targets, and every
one of them is clean under it. This is a guard, not a badge: it was turned on **because** the UMP
target was found calling a `@MainActor` method from a nonisolated UMP completion handler — a hard
error under the Swift 6 language mode — and because two `public static var` debug flags in that
same file were nonisolated global mutable state. Both are fixed (`@MainActor`, and `onDone` is now
`@escaping @MainActor @Sendable`); see `LockedValue`'s doc comment for why this package fixes such
knobs rather than reaching for `nonisolated(unsafe)`.

**[docs/INTEGRATION.md](docs/INTEGRATION.md)** — install, start-up order, the five decision points,
the feedback hooks, selling, remote config, and the handful of things that bite integrators who were
never told them. This README is the engineering record of how the port was built; that document is
what a host app actually needs.

## Requirements

- iOS 15+
- Swift Package Manager (no CocoaPods/Carthage)

## Status

Phase 1 ("Core Foundation") and Phase 1.5 ("Wire Up the Facade") implemented and merged — see
[`docs/superpowers/specs/2026-09-03-core-foundation-design.md`](docs/superpowers/specs/2026-09-03-core-foundation-design.md)
and
[`docs/superpowers/specs/2026-09-03-wire-facade-design.md`](docs/superpowers/specs/2026-09-03-wire-facade-design.md).
Phase 2 ("Bandit Core") adds the standalone Thompson Sampling bandit, belief state, reward
model, and likelihood estimator — ported from Android but not yet wired into `AdsBrain`. See
[`docs/superpowers/specs/2026-09-03-bandit-core-design.md`](docs/superpowers/specs/2026-09-03-bandit-core-design.md)
for scope and
[`docs/superpowers/plans/2026-09-03-bandit-core.md`](docs/superpowers/plans/2026-09-03-bandit-core.md)
for the implementation plan. Every `AdsBrain` decision point still returns its safe baseline —
this phase adds intelligence with no wiring, mirroring Phase 1's own "plumbing, no wiring" scope.
Not yet added as a dependency in any app.
`SdkEvent`'s wire format matches the same backend contract
(`sequenceId`/`eventName`/`eventData`/`ts`/`userProperty`) the Android SDK already sends to.

Phase 3 ("Decision Engine") is complete and merged — real `GuardrailEngine` rules (HARD + RESOURCE),
`ClickFraudGuard` (click-window lockout), `LagrangianBudget` (shadow-price resource shaping),
`PersonalizationScenario` (segment → policy-knob mapping), `BrainStorage` (belief/bandit persistence),
and `DecisionEngine` (`decide`/`feedback`/`observe`); see
[`docs/superpowers/specs/2026-09-03-decision-engine-design.md`](docs/superpowers/specs/2026-09-03-decision-engine-design.md)
for scope and architecture.

Phase 3.5 ("Wire the Decision Engine") is complete and merged — `AdsBrain` is now a real stateful facade with
five `async` decision-point methods (`shouldShowInterstitial`/`shouldOfferRewarded`/`shouldOfferIap`/
`chooseCrossModal`/`chooseAssist`), full session-state ownership (per-player `PlayerState`,
`adsShownThisSession`, last-action timestamps, progression counters, and pending-decision holder),
twelve lifecycle/behavior hooks (`onSessionStart`/`onSessionEnd`/`onLevelFail`/`onLevelWin`/`onPurchase`/
`onRewardedOffered`/`onInterstitialShown`/`onAdSkipped`/`onIapOfferShown`/`onAssistShown`/`onAssistUsed`/
`onPlayerContinued`), `rolloutFraction` tuning, and automatic feedback resolution (`resolvePending`).
See
[`docs/superpowers/specs/2026-09-04-wire-decision-engine-design.md`](docs/superpowers/specs/2026-09-04-wire-decision-engine-design.md)
for scope and architecture. The `sdk_baseline_decision` event (Phase 1.5's observability stopgap) is now
retired — `brain_decision` (emitted by `DecisionEngine` itself) is the real signal.

Phase 4 ("IAP Layer") is complete and merged — the base in-app-purchase layer ported from Android.
`IapModels` (public types hiding all StoreKit details from the SDK's public API), `StoreBackend`
(a protocol abstraction enabling testability without real App Store access, with no Android equivalent
but following this project's established pattern for I/O abstractions), `Entitlements` (owned-product
state, `sync()`-replaces-not-unions semantics), `FxRates` (local-currency → USD conversion, `nil`
on unknown currency), and `IapHelper` (the orchestrating facade with `queryProducts`, `purchase`,
`restorePurchases`, purchase-updates stream, finalize, `iap_purchase` event emission, and the call
into `AdsBrain.shared.onPurchase(usd:)`) — all in `MonetizationBrainCore`. A new SPM target,
`MonetizationBrainStoreKit`, adds the real `StoreBackend` conformance via StoreKit 2 (`StoreKit2Backend`).
Apps requiring real purchases link both `MonetizationBrainCore` and `MonetizationBrainStoreKit`;
test targets link only `MonetizationBrainCore` with an in-memory fake. All 267 tests pass; see
[`docs/superpowers/specs/2026-09-04-iap-layer-design.md`](docs/superpowers/specs/2026-09-04-iap-layer-design.md)
for scope and architecture. This phase carried the user's global security-review requirement for
payment/financial code (see spec §7) — the whole-branch review found one Critical finding
(`restorePurchases()` could silently revoke a paying customer's still-valid entitlements if its
fetch of the store's current-entitlements snapshot was interrupted, e.g. by routine `Task`
cancellation on a SwiftUI view dismissal — `StoreBackend.currentEntitlements()` now returns
`async throws -> [IapPurchase]` instead of a stream, so an interrupted fetch is a thrown error
`restorePurchases()` catches and skips syncing on, rather than silently trusting a partial
snapshot); the fix was independently re-verified and the security review passed clean.

Phase 5 ("Paywall") is complete and merged — the paywall layer ported from Android, a pure
decision-only facade with no UI. `PaywallManager` answers "what to sell, in what order, with which
variant" for a named placement by reusing `HoldoutManager.isHoldout` directly (paywall holdout IS
the SDK-wide brain holdout — no separate mechanism, per Android's own design), gating decisions
through `AdsBrain.shared.shouldOfferIap` (so every paywall offer runs through the exact same
guardrails that protect every other IAP offer), and applying remote-config-driven A/B variant
selection via a dedicated hash namespace (`"paywall_variant"`) using the existing
`HoldoutManager.bucketWith`. Two new events: `paywall_impression` (emitted when a paywall is shown)
and `paywall_holdout_skip` (emitted when a player in the SDK-wide holdout bucket is skipped from
a paywall offer, plus a counterfactual variant computation to track what variant they would have
received). All 274 tests pass; see
[`docs/superpowers/specs/2026-09-04-paywall-design.md`](docs/superpowers/specs/2026-09-04-paywall-design.md)
for scope and architecture. This phase carried the user's global security-review requirement for
payment/financial code (gates an IAP offer surface): the whole-branch review found one Critical
finding (`parseWeight` used `Int(Double)`, which traps on a non-finite or absurdly large
remote-config weight value — an operator-pushed config could crash the host app; fixed by clamping
to the same `[0, 10_000]` bucket space `HoldoutManager` already uses) plus two Important findings
(unbounded total variant weight silently skewing/orphaning a variant past the 10,000-bucket space;
three config-parse failure paths dropping a placement/variant with no diagnostic note) — all fixed
and independently re-verified; the security review then passed with no Critical/High findings (one
Medium — a public counterfactual-only method that bypasses the holdout/brain gates was more
strongly documented against misuse, per its own doc comment).

Phase 6 ("Purchase Router") is complete, reviewed, and merged — the multi-channel purchase router
ported from
Android's `PurchaseRouter.kt` (commit `7e16ee8`). One public API: `purchase(productId:country:)`,
delegating the App Store branch to `IapHelper.shared` via dependency injection (matching
`IapHelperTests`' established per-test-instance pattern) and returning a checkout URL on the
web-shop branch via `IapResult.webCheckoutOpened` — the SDK never opens a browser itself. Channel
selection is a pure function of remote config: the first matching rule wins, with a four-condition
fail-safe gate (enabled + https + eligible country + a matching rule) that always falls back to App
Store, which is always the pre-router behavior and therefore always safe. Web-shop entitlement is
granted only through `grantFromServer(...)`, called by the host app from a webhook-verified server
response, never inferred client-side. The router never consults the brain (an already-expressed
purchase intent isn't an offer to gate). One deliberate deviation from Android: `grantFromServer`
DOES call `AdsBrain.shared.onPurchase(usd:)` (Android's own source doesn't — traced as a real gap
in Android's payer-guardrail wiring, not a pattern worth replicating; see design spec §3.2). New
event: `web_checkout_opened` (the routing funnel's numerator); the checkout URL itself is never
logged in any event, by design. All 308 tests pass; see
[`docs/superpowers/specs/2026-09-04-purchase-router-design.md`](docs/superpowers/specs/2026-09-04-purchase-router-design.md)
for scope and architecture. This phase carried the user's global security-review requirement for
payment/financial code (channel selection gates purchase execution): the whole-branch review found
two Important findings (`eligible_countries` present-but-wrong-type silently failing OPEN with no
diagnostic — the only fail-open path in a file whose whole design thesis is failing closed; and
`grantFromServer` silently dropping its revenue event, while still granting and reporting success,
when the router was never configured) — both fixed and independently re-verified; the security
review then found one Medium finding — a TOCTOU race where `checkoutUrlFor` validated the
https/enabled/country gate against one locked config snapshot but built the checkout URL from a
second, independently-read snapshot, so a concurrent remote-config reload could theoretically serve
a URL never actually checked by the https gate — fixed by consolidating the decision into a single
atomic read, and the security review then passed clean.

Phase 7 ("Subscription Lifecycle") is complete, both reviews passed, and merged. It ports Android's
`SubscriptionLifecycle.kt` (commit `4e9610e`), the layer
answering "what is the operational state of this subscription, and what should the app do about
it?" — dunning notices while a payment is failing, win-back eligibility after churn, and a
management link. `SubscriptionLifecycle` is a pure classification layer: it never talks to a store
itself, it is fed snapshots by `IapHelper` and classifies them.

**This is deliberately not a 1:1 port**, and the reason is structural. Android's class exists to
compensate for what Google Play Billing does not answer on-device — its own KDoc says so — and it
derives five states from exactly two facts (`purchaseState`, `isAutoRenewing`). StoreKit 2 states
renewal status natively, so three of Android's mechanisms are guesses Apple simply answers:
`PENDING ⇒ grace period` (a heuristic, and the same enum value means "awaiting parental approval,
never to be granted" for a one-time purchase), absence-from-snapshot `⇒ lapsed`, and an
open-count-based dunning cadence used because Play's grace-period end date is server-only. See
design spec §3.1.

The ported state machine has **seven** states, not five. Two differences carry real behavioral
weight. `.cancelled` is **entitled** — auto-renew off but the paid period still running, which is
the most common modeling mistake when porting off Play. And `.inBillingRetry` exists as a
first-class state: Apple's own contract is that exiting the billing grace period *continues* in
billing retry, so a machine that treats grace-expiry as terminal stops dunning exactly when dunning
can still work, and marks the customer win-back-eligible while Apple is still trying to charge
their card. Android cannot see that state at all. There is deliberately **no `.paused` case** —
Play's paused state has no StoreKit analog whatsoever (verified by exhaustive enumeration of
`SubscriptionInfo`, `RenewalInfo`, `RenewalState`, `Transaction`, and the App Store Server API's own
1–5 `status`), and an unreachable case added for cross-platform symmetry would be dishonest
modeling.

Four Android defects were found during the port and deliberately not replicated, each covered by a
test here: an `UNSPECIFIED` snapshot **freezes a product's state permanently** (it is skipped by the
classifier, yet its presence in the snapshot list also suppresses the absence-based lapse
inference, so a formerly-active product sticks forever — untested on Android); a dunning counter
mutated from both a `@Synchronized` and an unsynchronized path; a public unguarded `clear()` that
resets `enabled` back to `true`, silently re-enabling a feature ops had disabled via remote config;
and a parser that rejects a malformed `dunning_cadence` element while silently defaulting a
wrong-typed `enabled` to `true`. Android's `dunningNoticeDue` is also a query that mutates a
persisted counter on every call — split here into a command and a pure query.

This phase also fixes a **Phase 4 defect it depends on**: `StoreKit2Backend` reported
`IapPurchase.isAutoRenewing` from `Transaction.productType`, i.e. product type rather than renewal
intent, so a cancelled-but-still-entitled subscription read as auto-renewing. Ported naively, the
`.active`/`.cancelled` branch would never once have taken the `.cancelled` path. The true value is
`RenewalInfo.willAutoRenew`, which that file did not consult anywhere.

One deliberate product decision worth stating outright: **`.revoked` is withheld from win-back
eligibility.** `.revoked` means a refund *or* a Family Sharing removal, and iOS 15 cannot tell them
apart (`RevocationType` is iOS 26.4). Offering a discount to the refunder half would fund a
purchase-refund-discount loop in which the SDK supplies the incentive. The blind tie breaks against
offering; the cost is the family-share member who gets no nudge, which is the smaller cohort and
the recoverable mistake. The Family Sharing reduction in `StoreKit2Backend` ranks `.revoked` above
`.expired` for the same reason — so a stale lapse cannot mask a refund and hand that customer the
offer anyway.

New event: `subscription_state` (`product_id`, `from_state`, `to_state`, `lifecycle_version`, plus
`expiration_reason` when the store stated one). Android's `screen_name` field is dropped rather than
importing the global mutable it reads from. All 373 tests pass; see
[`docs/superpowers/specs/2026-09-15-subscription-lifecycle-design.md`](docs/superpowers/specs/2026-09-15-subscription-lifecycle-design.md)
for scope and architecture.

This phase carried the user's global security-review requirement for payment/financial code (it
decides subscription entitlement and gates win-back discount offers). The security review found one
**Critical**: `parseOpenCount` reached `Int(someDouble)`, which traps above `Int.max` — and
`isFinite` does not guard it, since `1e20` is finite and still far too large, while the caller's
min/max clamp ran only after the conversion and was therefore dead code for that input. It was
reachable from remote config with no ill intent at all: `JSONSerialization` returns any number too
large for `Int64` as an `NSDecimalNumber`/`__NSCFNumber` wrapping a `Double`, so
`{"dunning_cadence": [99999999999999999999]}` — one extra digit from an operator typo — crashed
every app instance that fetched the config, on the purchase path. This is the same bug class as
Phase 5's Critical `parseWeight` finding, and the fix is the one `PaywallManager.clampWeight` and
`HoldoutManager` already use: bound the `Double` in `Double` space, then convert. Notably the
pre-existing "absurdly large" test could never have caught it — its `999999999` casts cleanly as
`Int` and never enters the `Double` branch.

The code review found two **High** findings, both tests that could not fail on regression: no test
covered a subscription renewal arriving via `transactionUpdates()` (the real-world path for every
renewal after the first purchase), so moving the classification call out of the shared funnel would
have left the suite green while silently breaking renewals; and every event assertion polled, so
regressing `await sink.send` back to a detached `Task` — reopening the drop window `observe` was
made `async` to close — would also have passed. Both now have tests that go red on exactly those
regressions.

All fixes were independently re-verified by a second security pass that reverted each one in place
to confirm the corresponding test genuinely fails: the Critical test dies with the exact fatal
error, and both High tests fail with the expected assertion. That pass also confirmed the
Int32/Int64 boundary is not a gap, and raised one further low-severity item — the same unguarded
`Int64(date.timeIntervalSince1970 * 1000)` pattern in `StoreKit2Backend` — which is reachable only
through a cryptographically verified StoreKit payload rather than remote config, and has been
clamped anyway as defense in depth.

Three findings were deliberately left OUT of that branch, since they are pre-existing and belong to
other phases' files. The first two are now fixed on `hardening/config-failopen-and-sequence`:
- ~~**`PurchaseRouter.web_shop.enabled` has the identical `NSNumber`/`Bool` bridging hole**~~ —
  FIXED. `{"enabled": 1}` silently ENABLED external web-shop checkout routing with no diagnostic
  note (verified end to end: the apply report read `web_shop=ready` and `checkoutUrlFor` returned a
  live off-App-Store URL), and `0` silently disabled it. The `CFBoolean` discriminator that
  `SubscriptionLifecycle` had kept private is now `JsonConfigValue` in `Config/`, shared by the
  router and the paywall parser — production parsing subtleties are shared, unlike this package's
  deliberately per-file test fakes. The audit that came with it closed four more silent
  degradations in the same two `apply` methods: `eligible_countries` and `products` element-level
  drops, a wrong-typed `decision_point`, and a wrong-typed variant `weight` (where `true as? Int`
  == `1` made `{"weight": true}` a real, selectable variant).
- ~~**`PurchaseRouter.clear()` and `PaywallManager.clear()` are still `public` and unguarded**~~ —
  FIXED. Both are now internal `resetForTest()`, matching
  `Entitlements`/`ConsentState`/`AdsBrain`/`SubscriptionLifecycle` and closing the Android footgun
  design spec §3.8 diagnoses. A public-API narrowing, safe here because this SDK is still
  "not yet added as a dependency in any app."
- ~~**`SdkEvent.sequenceId` is never assigned anywhere in the SDK**~~ — FIXED, on the same branch.
  It is part of the backend wire contract and every event since Phase 1 shipped `0`. Android
  assigns it in `LogEventServer.kt:95-100` — one global counter for the whole SDK, persisted on
  every bump, never reset, **first value 1**, so `0` was precisely the "never assigned" value iOS
  had been sending. Ported with matching semantics through the injected `BrainKeyValueStore` seam,
  assigned at enqueue inside `EventQueue`'s actor (not at `SdkEvent.init`, where events that are
  later dropped by the consent gate would burn numbers and fake gaps).

  **One deliberate divergence from Android**, recorded because it is otherwise invisible: iOS
  persists the full wire envelope, so a retry reuses its original number rather than drawing a
  fresh one — Android cannot, since its queue stores decoded fields and the number is
  unrecoverable. Reuse serves the column's only job better (a gap means an event never arrived; a
  repeat means one arrived twice, instead of retry storms inflating the sequence space and masking
  real loss). Consequence for whoever builds analytics on this column: **segment by platform** —
  Android's numbers count send attempts, iOS's count logical events.

Phase 8 ("Recurring Grants") is complete, both reviews passed, and merged — the layer answering
"this player owns a pass or subscription; is a periodic reward due, and what is it?" It ports
Android's `RecurringGrants.kt` (`DOC/53`), which Phase 7 unblocked: its ownership test depends on
subscription state. `RecurringGrants` never touches the game's economy — it decides eligibility and
timing and returns an opaque `(rewardType, rewardAmount)` for the host app to grant, the same
"answer the question, don't render anything" boundary `PaywallManager` and `IapHelper.purchase`
keep. One pure query (`dueGrants()`) and one mutating `async` entry point (`claim(productId:
rewardType:)`, async because it awaits its own event send).

**Two Android defects are the headline here, not the feature.** Both are corrected, and each
correction is mutation-tested — the implementation was reverted to the Android behavior to confirm
the tests actually go red rather than merely existing:

- **Android's persistence does not round-trip, and the failure is exploitable.** `keyOf` builds
  `"productId|rewardType|periodHours"` while `persist` joins entries with that same `|`, so
  `loadLast` reconstructs the keys as `24` and `168`. They never match `keyOf(g)` again, so
  `lastClaim[key] ?: 0L` yields `0` and **every grant reads as never-claimed after every process
  restart** — force-quit, relaunch, claim, repeat, with no clock manipulation needed. Verified by
  simulating the exact persist/load pair. Android has no persistence round-trip test, which is why
  it ships. This port validates at parse time (a `product_id`/`reward_type` containing `|`, `:` or
  a newline is dropped with a diagnostic note) **and** mandates the round-trip test by name.
  Reverting the key composition to Android's fails 6 tests.
- **Android's ownership check contradicts the class it queries.** It tests
  `stateFor(productId) == SubState.ACTIVE`, so a subscriber who turns off auto-renew loses their
  rewards **immediately**, despite having already paid through the end of the period — and so does
  a subscriber in a billing grace period. Android's own `SubscriptionLifecycle` documents both
  states as still entitled, and its test suite covers neither. This port uses
  `SubscriptionState.isEntitled`, which Phase 7 added for exactly this question, OR'd with
  `Entitlements.isActive`. `.inBillingRetry` stays excluded — recoverable but not entitled, so a
  player whose payment has actually failed should not keep drawing rewards while Apple retries.
  Reverting to `== .active` fails 2 tests.

Android's remaining faults are corrected the same way earlier phases handled theirs: `clear()` was
public and unguarded and reset **config** as well as history (so host code could silently re-enable
a feature ops had disabled) — here it is internal `resetForTest()`; `apply()` and `dueGrants()` were
unsynchronized while `claim()` was, with `dueGrants()` reading the very map `claim()` mutates in
place — here every path takes one lock, and a claim's authorization and its timestamp write are a
single acquisition; and Android's parser rejected a whole config for one malformed grant line while
silently defaulting a wrong-typed `enabled` to `true` — here a bad line is dropped with a note and
the rest applies, and `enabled` degrades with a note via `JsonConfigValue`.

**A new `BrainClock` seam** (`Support/BrainClock.swift`) comes with this phase. The package had no
clock abstraction at all — every site calls `Date()` directly, with only `ClickFraudGuard`'s
per-method `nowMs:` parameter as precedent — and a grant-once-per-period feature cannot be tested
deterministically without one. It is named `BrainClock`, not `Clock`, because the standard library
owns that name (SE-0329); this package already carries one shadowing trap in
`SubscriptionRenewalState` that could only be documented rather than fixed, and taking a second one
while nothing yet depends on the name would be choosing it deliberately. Retrofitting existing
callers onto the seam is deliberately out of scope.

New event: `recurring_grant_claim` (`product_id`, `reward_type`, `reward_amount`, `period_hours`,
`recurring_grants_version`). Android's `screen_name` is dropped, as in Phase 7, rather than
importing the ambient mutable global it reads from. All 452 tests pass; see
[`docs/superpowers/specs/2026-09-15-recurring-grants-design.md`](docs/superpowers/specs/2026-09-15-recurring-grants-design.md)
for scope and architecture.

This phase carried the security-review requirement for value-granting code. The review was
adversarial rather than read-only — it wrote a throwaway harness attempting persistence
key-injection (a `product_id` crafted to forge an extra entry), 5000-character and Unicode
identifiers, `Int64.max + 1`, `Double.greatestFiniteMagnitude`, corrupt stored timestamps, and a
concurrent `apply`/`claim`/`dueGrants` race — and **passed clean, with no Critical, High or Medium
findings**, the first phase in this port to do so on the first pass. The `Int(someDouble)` trap
that was Critical in Phases 5 and 7 was specifically re-verified as clamped in `Double` space
before conversion.

The code review found one High and two Mediums, **all of them in what the tests prove rather than
in the shipped logic** — it found no correctness bug in the production code. All are fixed:

- A rejected claim named `matching[0]`'s period — the first line in config declaration order, which
  bears no relation to the wait actually remaining. With a 168h line declared before a 24h one, a
  claim a minute from due reported "the 168h period has not elapsed yet," pointing a support
  engineer at a week. It now names the line closest to elapsing, with its remaining hours.
- The multi-cadence behavior that `claim`'s own doc comment singles out as a deliberate decision
  had no test at all. The new one declares the config in deliberately "wrong" order, so a
  regression to `matching[0]` makes the shorter line permanently unclaimable and fails.
- The disable/re-enable test never advanced the clock, so "timestamp preserved" and "timestamp
  silently reset at re-enable" were indistinguishable — it would have passed against the exact
  regression it was named for.
- **The concurrency test proved nothing.** Measured rather than assumed: with the lock removed
  entirely, the original 20-task race passed 3 runs out of 3. A race test with no engineered
  overlap asserts a property the scheduler is under no obligation to exercise. The replacement
  stalls a clock inside the critical section to force every caller to queue on the lock; under the
  same mutation it kills the test process every run.

### Known limitations (from design spec §6, Phase 8)

- **Timing is device-local wall clock, and advancing the clock farms rewards without bound.** Each
  claim rewrites the baseline forward, so the attack repeats — it is not the self-limiting one it
  appears to be. Two mitigations were considered and rejected with stated reasons: rejecting
  backwards clock movement punishes honest users on every DST shift and NTP correction while not
  touching the forward-advance loop at all, and pairing with a monotonic reading does not survive a
  relaunch. Neither prevents the attack; both add real failure modes for honest users. The honest
  fix is server-authoritative timing, which is a backend project. **Consequence for integrators:
  these rewards are fine for cosmetics and soft currency, and must not be used for anything with
  cash value** — the same line `Entitlements`' own doc comment already draws.
- **No server reconciliation**, so a reinstall resets claim history and re-grants one period.
- **No calendar or timezone semantics.** Periods roll from the last claim, so there is no "resets at
  midnight"; Android documents this as a deliberate trade for needing no server.
- ~~**Retrofitting `BrainClock` onto existing `Date()` callers**~~ — **settled.** One of them was
  a real gap and is fixed: `brain_feedback`'s `outcome_timestamp_ms` is a value the BACKEND reads
  and it came from a bare `Date()`, so no test could pin it and a wrong unit would have shipped
  looking plausible; it now comes from the engine's injected clock. The rest stay as they are, on
  reasons written at `BrainClock`: `ClickFraudGuard` already takes an overridable `nowMs`,
  `DecisionEngine`'s `runId` is a one-shot uniqueness suffix nothing branches on, `SdkEvent.ts` is
  already overridable per event, and `StoreKit2Backend`'s instants come from the real App Store —
  faking those would describe a transaction as happening when it did not.

Phase 9 ("Battle Pass") is complete, both reviews passed after fixes, and merged — a seasonal
progression layer ported from Android's `BattlePass.kt` (`DOC/52`). A season has an absolute
start/end window, XP accumulates into tiers, each tier carries a free and a premium reward list, and
premium is unlocked by owning that season's one-off IAP. As everywhere else in this port, the SDK
owns the arithmetic, the timing and double-claim prevention, and the game owns deciding when XP is
earned and actually granting the items.

**This Android original is in better shape than the last two**, which is worth recording because the
research was calibrated by contrast rather than by pattern-matching: its persistence genuinely
round-trips, and its ownership check is correct for what it gates. Three things still needed
deciding rather than copying:

- **`season_id` is a latent version of the `RecurringGrants` delimiter bug.** It is a pass-through
  remote-config string with no character validation, sitting first in a pipe-delimited record. Name
  a season `summer|2026` and every field shifts: XP parses from a fragment and silently becomes
  zero, and the whole claim history is lost on next launch. Unlike the Phase 8 defect this one is
  **conditional on an operator typo rather than guaranteed**, which is exactly why it would survive
  every test and every season until the day it didn't. Routed through the shared
  delimiter-validated identifier parser; the forbidden set also includes the comma, because the
  comma's safety was only *positional* (it splits field 2, and `season_id` is field 0) and a later
  change to the record shape would have retired that silently.
- **`lastTier` is not ported.** Android persists it, restores it, and never reads it — `addXp`
  recomputes the tier from XP instead. Tier is a pure function of XP and the tier table, so storing
  it is a second source of truth for something already derivable.
- **`apply()` is Android's one unsynchronized mutator**, so between the new config landing and
  progress resetting, the new season's window and thresholds are evaluated against the old season's
  XP. Here one lock covers `apply` and every read accessor.

Premium ownership is `Entitlements.isActive` alone, **not** Phase 8's OR with `SubscriptionLifecycle`
— the pass is a non-renewing per-season IAP with no renewal state, so the subscription source would
never have an answer. Recorded because reaching for the previous phase's pattern is the natural
reflex and would have been wrong here.

Two behaviors are preserved deliberately rather than silently: **unclaimed rewards become
permanently unreachable the instant a season ends** (a product decision, not a technical one — a
player who earns a tier an hour before the deadline and does not tap claim loses it), and the
`xp_cap` parse asymmetry is kept because its "safe default" is *no cap at all*, so the usual
degrade-with-a-note rule would fail open.

The catch-up activation boundary was changed to compare against an absolute threshold
(`start + Int64(span × fraction)`) rather than dividing to form a ratio. The division rounds twice,
so a fraction like `0.3` against an odd span could land a millisecond either side of the boundary
for reasons invisible from the config; the threshold form rounds once and is what Android computes,
so both platforms switch catch-up on at the same instant rather than approximately.

Five mutations were killed, including one run unprompted: removing the `season_id` validation
reproduces the Android defect verbatim (the record becomes `summer|2026|0|` and 250 XP plus the
claim history vanish), and removing the lock entirely crashes inside `BattlePass` under forced
contention — proving the contention tests are load-bearing rather than decorative.

**Review findings — all fixed; 541 tests pass.** The security review found one **High** that reached
past this phase: persisted progress can regress to a stale snapshot under ordinary concurrent use,
enabling a double-grant. Both mutating paths compute a consistent snapshot inside the lock and then
write it *outside* the lock with nothing ordering the writes, so two callers can compute in the
right order and land in the wrong one. In-memory state stays correct, so nothing looks wrong at
runtime; after a force-quit, storage is missing a claim that was genuinely consumed and already
handed to the host to grant. No jailbreak, no clock tampering, no storage editing.

**`RecurringGrants` shares the pattern and is already merged**, so the fix spans three subsystems.
Its awkward origin is worth recording: the pattern exists because of a correct rule — never hold the
decision lock across store I/O, itself the product of a Phase 3 fix — and the remedy for holding the
lock too long reintroduced a lost update one layer down.

A **Medium** was also found in the just-shared `JsonConfigValue.clamped`: it traps when `upperBound`
is `Int64.max`, because `Double(Int64.max)` rounds *up* to 2^63 and cannot convert back. Nothing
reaches it today — every caller uses `Int64.max / 2` or `Int32` bounds deliberately — but that
file's own header invites two more parsers to migrate onto it, and `Int64.max` is exactly what
someone reaches for meaning "no bound." The code review separately returned two High maintainability
items and several test-coverage gaps, including one on a change made in this phase: the catch-up
threshold switch above had no test able to distinguish the two formulas, since every catch-up test
used `0.5` against an even span, where they agree exactly. That one is worth naming plainly: the
threshold change was made in this phase with a confident rationale and **no test able to falsify
it**. The fix adds a case where the two formulas genuinely disagree (span 1,000,001ms at fraction
0.3, sampled at exactly `Int64(span × 0.3)`), verified by reverting to the ratio form and confirming
that this test — and only this test — goes red.

The High was fixed with `Support/SequencedStoreWriter.swift`: each snapshot is stamped with a
sequence number assigned inside the caller's main lock, and written under a dedicated leaf lock that
drops any write not newer than what already landed. The main lock is never held across I/O, writes
are totally ordered, and stale writes are discarded rather than clobbering.

Moving `setString` inside the main lock would have closed the race in three lines instead of ~120,
and was weighed rather than dismissed: every pure query takes that lock and is called on the UI
thread to render a screen, the store is host-supplied, and host code could reach back into
`Entitlements` — whose leaf lock `BattlePass` already nests — closing a deadlock cycle. An async
serial queue was rejected for a sharper reason: it would make `claim()` return a consumed reward
*before* the write landed, trading a rare race for a guaranteed loss window on **every** call.

Proven by reverting: the H1 revert fails 5 tests with 12 assertions, including the double-grant
itself (a restarted instance re-grants tier 1). The M1 revert does not fail an assertion — it traps.

Phase 10 ("Comfort Index + CRM Bridge") is implemented on `phase10/comfort-and-crm` and is under
review. Two independent subsystems, both unblocking items deferred since Phase 3.5:

- **`ComfortIndex`** — the 4th North Star Metric, a deliberate counterweight to
  ARPDAU/Revenue/Subscription, which are all easy to improve by making the player's experience
  worse. A pure function, no state, no persistence.
- **`CrmBridge`** — a vendor-neutral outbound signal channel. The SDK carries no CRM dependency;
  the host registers a sink and the SDK hands it normalized signals (`segment_change` on a real
  belief-argmax flip, `first_purchase` once per player).

Neither Android original persists anything, so the delimiter-collision class that dominated Phases
8 and 9 is **absent from both by construction** — worth recording, because the research was
calibrated by contrast rather than by assuming the pattern repeats.

**Three Android faults corrected:**

- **`enabled` failed open.** Android parses it as `root["enabled"] != false`, so a missing or
  wrong-typed value is not the Kotlin literal `false` and evaluates to **`true`** — the bridge
  silently enables, contradicting its own documented default of OFF and its own stated principle
  that "with CRM, silence is the safe state". No Android test covers either case. Fourth instance
  of this fail-open class in this port, and the least recoverable: CRM signals leave the device for
  a marketing platform.
- **Host sinks were called while holding the lock.** JVM monitors are reentrant so Android
  survives it; `NSLock` is not, and the same shape deadlocks here. Eligible sinks are snapshotted
  under the lock and delivered after releasing it — which also stops one slow sink blocking every
  other caller.
- **`clear()` was public** and wiped the remote-config-derived state alongside the registered
  sinks. Internal `resetForTest()`, as everywhere else in this port.

**`ComfortIndex.isTrustworthy()` is structurally impossible today** — `coverage` is fixed at 2/5 =
0.4 against a 0.6 threshold, so it always returns `false`, and a caller gating on it waits forever
for a value that cannot arrive. Android ships that silently. Here it ships with a test asserting
the impossibility, derived from the component arrays rather than a hardcoded 0.4 — so when a third
component lands, that test fails and forces a conscious decision instead of a silent behavior
change.

**Three corrections made after the implementation was handed back**, none of which a green suite
would have caught:

- **The missing-component names are a cross-platform wire contract, not labels.** They had been
  invented; the backend ingests `components_missing` verbatim, so two platforms naming the same
  absent component differently splits one dashboard into two. Now matching Android exactly:
  `organic_purchase_probability`, `refund_rate`, `post_purchase_retention_delta`.
- **`first_purchase` re-fired on every relaunch.** `PlayerState.purchaseCountLifetime` is
  in-memory, so "first" meant "first since the process started" — and a marketing platform re-runs
  its welcome campaign for an existing payer, on the highest-value trigger there is. Closed with a
  persisted per-player flag. Still device-local: a reinstall or a second device sends it again,
  which needs account-level truth the SDK does not have.
- **A wrong-typed `sinks` broadened delivery instead of narrowing it.** The operator writes that
  key to restrict; degrading to the default did the reverse and sent personal data to a processor
  they had named and rejected — possibly for a contractual reason the SDK cannot see. Now blocks
  every sink. An EMPTY array still means "declined to narrow" (matching Android); only an
  unreadable value blocks. Those are two distinct states now, where before both were one empty set
  — and that collapse was what let a typo silently widen the audience.

Phase 11 ("Offerwall + Brain Policy") completes the port. `OfferwallManager` is the 4th
monetization surface — a vendor-neutral adapter where the host wraps each offerwall SDK in a
provider, and this decides whether one may open and de-duplicates completion payouts.
`BrainPolicy` applies the remote policy artifact: it makes no decisions, it writes the tuning knobs
the decision path reads, under hard caps an artifact can never exceed.

It also carries a **strict-concurrency sweep: 12 warnings to 0**, so the package now builds clean
under `-strict-concurrency=complete`. These were hard ERRORS in the Swift 6 language mode, and the
README had carried `HoldoutManager.salt`/`holdoutFraction` as a known data race since Phase 1.
`nonisolated(unsafe)` was rejected explicitly — it asserts a race is safe rather than removing it,
and these races were real. `ClickFraudGuard`'s five values went into ONE `LockedValue` rather than
five, because `registerClick` reads the window, compares it against two caps and writes two maps as
a single decision; five locks would have traded a compiler error for a race.

**The port hazard that governed this phase is a language difference, not an Android bug.** Kotlin's
`Double.toInt()` saturates; Swift's `Int(Double)` **traps**. `BrainPolicy`'s artifact parser is
crash-proof on Android by language accident, so porting its `toInt()` calls as `Int(...)` would have
introduced a crash that does not exist in the original — and a policy artifact is remote config
fetched by every instance at once, so `{"max_ads_24h": 1e300}` would have taken down the fleet.
Every conversion goes through `JsonConfigValue.clamped`, with each `HARD_*` ceiling doubling as the
clamp bound so the safety cap and the trap-guard are one operation rather than two chances to
forget.

`OfferwallManager` repeats the `enabled != false` fail-open for the **fifth** time in this port —
notable because `BrainPolicy`, in the same Android codebase, parses the same field correctly. And
its dedup key embeds a **vendor-supplied** `transactionId` split on a comma at load, so a vendor id
containing a comma fractures the record and the completion is **paid twice**. Narrower than
`RecurringGrants`' unconditional corruption, but the input is less trustworthy: operator typos are
rare, vendor payload formats are not ours to choose. The key components are now percent-escaped
injectively, so a sanitized id still de-duplicates against itself and stays distinct from its
neighbours — a lossy scheme would have satisfied the spec while collapsing two different ids into
one key, suppressing a *legitimate* second payout.

### The correction worth recording

`BrainPolicy` was first implemented **without the Android source in front of it** — the brief did
not name where it lives and the spec listed neither the constants nor the wire field names. The
implementer said so plainly and asked for a diff against `BrainPolicy.kt`. That diff found:

- **Four safety ceilings looser than Android's** (interstitials 12 vs 10, IAP offers 8 vs 6,
  frustration streak 10 vs 5, assists 10 vs 8) and one **missing entirely**
  (`hardMaxClicksPerWindow`), so click-fraud tolerance clamped against a trap-guard of 1000 instead
  of 12 — an artifact could raise it 83×. The whole purpose of these constants is that an artifact
  may make the SDK more conservative but never less.
- **Eleven wrong wire names**, so those fields silently never applied. Two were unit bugs rather
  than renames: `window_seconds`/`lockout_seconds` were read as milliseconds, turning a five-minute
  click-fraud window into 300ms.
- **`max_iap_offers_24h` read from the wrong block** (`iap`, not `progression`) — a rename alone
  would not have fixed it.
- **Every frequency cap floored at 1 instead of 0**, so an operator setting `max_ads_24h: 0` to
  switch ads off had the field *dropped* and kept the compiled default of 40 a day.
- **`holdout_fraction` clamped `[0,1]` against Android's `[0,0.5]`** — an artifact asking 0.9 put
  90% of players in the control group on iOS and 50% on Android.
- **`fx_rates` could blank the whole table** when no rate parsed, zeroing the USD column for every
  non-USD market.

**None of that broke a test.** The test guarding the nine ceilings asserted `> 0` for eight of
them, which is exactly why 12, 10 and 10 sailed through. It now pins Android's literals, and a new
test applies Android's own reference artifact and asserts all 25 knobs actually *moved* — because a
wrong field name produces silence, not a failure.

### The payment target's first tests, and the defect they found

`StoreKit2Backend` carried "verified by type-checking only" since Phase 7. That was half right:
`Transaction` and `Product` cannot be built in a unit test, but the mapping layer — the part that
decides who keeps an entitlement and who is offered a win-back discount — is value-in, value-out.
Its helpers are now `internal` rather than `private`, and
`Tests/MonetizationBrainStoreKitTests/` exercises them (15 tests).

**The defect:** `clampToEpochMs` guarded `seconds * 1000` for finiteness. For a finite but huge
input that multiply overflows to infinity, so the guard fired and the function returned **0** — the
epoch. Callers order by this value, and 0 is the oldest instant there is, so a far-future expiry
would have read as long-expired: the exact inversion of its meaning, in a function whose own
comment promised saturation. The guard now sits on the input and the clamp happens in seconds;
NaN and ±infinity still read as 0, the same as an absent date. Nothing reachable produced such a
date (these come from a cryptographically verified payload), so this was defence in depth that did
not defend — which is only findable by testing it.

Three mutations confirm the new tests bite: restoring the old clamp, swapping the `.revoked`/
`.expired` ranks (which would hand a refunder the win-back discount the ranking exists to
withhold), and reducing Family Sharing statuses with `.first` (which revokes a live family
entitlement whenever the store lists an expired purchase first) each fail a test.

`MonetizationBrainUMP` was considered for the same treatment and left alone: its one piece of
mapping logic reads `ConsentInformation.shared` in the branch that matters, so a test would be
asserting on UMP SDK state rather than on this package's behaviour. The consent gates that
actually decide anything (`ConsentState.allowsThirdPartySignals`, `allowsProfiling`) are Core types
and are already tested there.

### The UMP target did not compile for iOS, and nothing could have told us

`swift test` on a Mac is not a verification of this package. Everything guarded by
`#if canImport(UIKit)` — which is all of `MonetizationBrainUMP` — compiles to nothing there, and
that target has no test target either. So when an earlier phase added `canRequestAds()` and
`isPrivacyOptionsRequired()` as FUNCTIONS next to the `canRequestAds` and `isPrivacyOptionsRequired`
PROPERTIES that were already there, the duplicate declarations sat in `main` through several
phases. On iOS — the only platform that target has — it simply did not build.

It surfaced the first time anyone ran `xcodebuild -destination 'generic/platform=iOS'`, which had
not happened in this project. The duplicates are merged into the properties (keeping the better
doc comments from the later pair), and all four targets now build for iOS.

**The lesson is the script, not the fix — and the script itself had the same hole twice more.**
It used to announce "warnings are errors here by convention" and then only grep the log when a step
exited NON-ZERO; a warning does not do that, so the convention was enforced by nobody, and the UMP
main-actor warning sat in plain sight across several phases. It also ran no test that needed a
simulator, so `MonetizationBrainUMPTests` — the ConsentStatus -> ConsentTier table, the only
decision that target makes — would have compiled to nothing under `swift test` and reported a
cheerful zero failures. Both are closed: warnings from this package's `Sources/` fail the step, and
the UMP tests run on an iOS Simulator. Both gates were checked by breaking them on purpose and
confirming the script goes red.

`Scripts/verify.sh` is what "done" means here: a macOS
build, the tests, and an iOS build of every one of the four targets. A green `swift test` on a Mac
says nothing about the two targets whose entire reason for existing is a platform that Mac is not.

### Proving a lock does something, instead of asserting around it

The money paths (`BattlePass.claim`, `RecurringGrants.claim`, `OfferwallManager.reportCompletion`)
had only sequential tests: claim, then claim again. That proves the RECORD works, not the LOCK —
and this port has already shipped one double-grant from a race, so the difference matters.

The first version of the concurrent tests ran 64 simultaneous claims and asserted that exactly one
succeeded. **It passed with the lock removed entirely** — a claim is a few microseconds of
synchronous work, so 64 tasks almost never collide at exactly the check-then-insert boundary. An
assertion that cannot fail is not coverage, which is the same lesson two earlier concurrency tests
in this repo already carry.

What works is measuring instead of inferring: `BattlePass.claim` and `RecurringGrants.claim` call
the injected clock INSIDE their critical section, so a clock that counts how many callers are in
it at once counts how many are inside the lock. Under mutual exclusion that number is 1 by
definition, whatever the timing. Removing the battle pass lock now fails the test with "2 claims
were inside the critical section at once".

Honest scorecard, because the point of this exercise is not to claim three green ticks:

| path | invariant asserted | proven to catch a missing lock |
|---|---|---|
| `BattlePass.claim` | yes | **yes** — verified by removing the lock |
| `RecurringGrants.claim` | yes | no — removing its lock still passes, even at a 1.5 s window |
| `OfferwallManager.reportCompletion` | yes | no — nothing injected inside its critical section to count with |

All three invariants hold today, and the code was read for the shape that matters (check and
record in ONE acquisition). Two of the three tests are regression protection for the invariant
rather than for the lock, and they say so in their own comments.

### Attribution: the SDK owns the purchase, the host owns the vendor

`iap_purchase` reached the backend and no attribution SDK ever saw a cent of it — the gap the
`reportPurchase` comment itself described as "intentionally absent". It is closed by
`AttributionBridge`, a two-method protocol in Core that names no vendor, wired into `IapHelper` as
a third independent reporting step beside the event sink and the brain.

Android's counterpart is worth quoting for the reason it exists: `logIapPurchase` was an EMPTY
function nobody called, so the network saw ad revenue and no IAP at all. Every ROAS number the UA
campaigns bid on was missing the larger half, and nothing ever errored — campaigns ran, dashboards
showed numbers, and the auction optimised against half the truth.

Why a protocol rather than a `MonetizationBrainAppsFlyer` target: an attribution SDK is a
closed-source binary, and putting one in this package's dependencies would put it in the graph of
every consumer, including those on a different vendor or none. The SDK owns the part that is
actually hard — knowing a purchase completed, what it was worth, whether it recurs — and the host
writes ~15 lines of conformance (there is a worked AppsFlyer example on the protocol). Same shape
as `RemoteConfigProvider`, and it leaves the door open for a vendor target later.

Two rules a conformance must keep, both enforced by tests verified to bite:

- **Amount and currency go out verbatim.** The network converts with its own same-day rate; a
  figure converted here would be this client's guess, or — for a currency `FxRates` has no rate
  for — a silent **zero** presented as fact. The first version of this test used a $4.99 fixture,
  where "send the store's amount" and "convert to USD" are the same number, so it passed either
  way. It now buys in đồng.
- **A subscription is never reported as a one-off.** Networks model recurring revenue differently,
  and the flag comes from the same helper the `is_subscription` event field uses, so the two
  cannot disagree.

### Every store read is an untrusted input

`UserDefaults` is a plist the app — and anything running as the app — can write, and this port has
now had two defects from that class, neither of which announced itself. So all nine store-backed
parsers were swept, and `PersistedInputFuzzTests` keeps them swept: infinity, NaN, negative and
overflowing integers, embedded separators, empty fields, control characters, a 10 000-character
value and a record truncated mid-write, against every one of them.

The second find: **`BanditPolicy.restore` accepted an infinite alpha/beta** (`inf > 0` is true).
Nothing failed loudly — `sampleBeta` returns `inf / (inf + inf)` = NaN, every `sample > bestSample`
comparison is false forever, so the first arm in the list wins every round. The bandit silently
stops learning **and logs a propensity near 1.0 for that arm**, which is the number off-policy
evaluation trusts, so the damage outlives the bad record. A crash would at least have been noticed.

The other seven parsers came back clean, and the tests that say so are not vacuous — each one
first confirms a legitimate claim, reward or grant still goes through, then that a second one does
not.

### A corrupted belief crashed the next decision

Found by sweeping the crash paths (`precondition`/`fatalError`/force-unwraps) rather than by a bug
report. The chain, every link of which looked locally reasonable:

`Double("inf")` is a successful parse → `inf >= 0` is true → an infinite total passes `total > 0` →
the posterior normalises to `inf / inf` and is **NaN in every slot** → `segmentWeights` filters on
`weight > 0`, which is FALSE for NaN, so the weights come back **empty** →
`BanditPolicy.selectArm`'s `precondition(!segmentWeights.isEmpty)` **takes the host app down on a
decision path**.

`BeliefState.deserialize`'s own doc comment promises the opposite: "malformed data returns a
uniform prior instead of throwing — crashing would break the whole play session". The guards just
never covered non-finite values. The same hole existed in `update`, where an infinite normalizer
passes `> 0` and NaNs the posterior in one step.

Not reachable from normal operation — `LikelihoodEstimator` returns values in (0, 1], so nothing
the SDK itself writes can be infinite — but reachable from a corrupted or tampered persisted
belief, which is precisely what that guard exists to survive. Fixed in both places, with tests
verified to fail without the fix. `segmentWeights` also falls back to the uniform prior rather than
returning empty; that one is unreachable by construction now, is documented as such, and is not
test-covered.

Also checked in the same sweep, all clean: four force-unwraps (each guarded by a preceding
`precondition` or a total enum table), and `swift build -strict-concurrency=complete` reports zero
warnings, which is the README claim that had never been verified with the flag.

### `decide` was 3.5 ms, and 2 200 of those microseconds were dispatch

Same method as the queue: measure the path, then fix what the measurement points at. One
`DecisionEngine.decide` with five candidates cost **3.46 ms**, and `BanditPolicy.selectArm` was
effectively all of it. Two causes, neither of them the sampling itself:

- **The posterior table was read once per sample.** The Monte Carlo loop called
  `key(segmentKey, armId)` twice per arm per round — building a `"segment|arm"` String and hashing
  it, to read a value and write the identical value back. At 200 samples over 5 arms that is 2 000
  string allocations per decision, for values that cannot change while the lock is held. The table
  is now materialised once up front (including the write-back, which is NOT dead code — it is what
  puts a default `BetaParams` in place so `serialize()` persists an arm that was offered but never
  rewarded; there is a test, and it fails if the write is dropped).
- **The random generator was an existential.** Every `Double.random(in:using:)` through
  `RandomNumberGenerator` is a dynamic dispatch the compiler cannot inline, and this path makes
  ~2 200 draws per decision: measured at **0.63 µs per draw**. Making the generator a generic
  parameter lets the generic specialise and the draws inline — 0.30 µs.

**3.46 ms → 1.49 ms, with no behavioural change at all**: the loop structure, the order of draws
and the comparisons are identical, which is exactly what `test_selectArmIsDeterministicForAFixedSeed`
checks — a fixed seed still yields the same arm and the same propensity.

The cost is one source-breaking signature: a caller holding `var rng: RandomNumberGenerator` now
holds the concrete type instead (four lines in this package's own tests). `DecisionEngine` owns
this API and its doc already says nothing outside the package should call `BanditPolicy` directly.

**What was left on the table, deliberately:** the remaining ~1.5 ms is dominated by
`SystemRandomNumberGenerator`, which is the OS's cryptographic RNG, plus the `log`/`cos` in
Box-Muller. Monte Carlo sampling does not need cryptographic randomness and a seeded PRNG would
likely halve it again — but that trades a property of the SDK's randomness for speed, and it is a
call for whoever owns the experiment design, not a cleanup to slip into a performance pass.

### The NaN that `min(max(x, 0), 1)` lets through

The `Int(Double)` trap has now been found six times in this port, so the conversions were swept
systematically rather than one bug report at a time. One live, host-reachable crash came out of it.

`configure(_:)` clamped `brainRolloutFraction` with `min(max(f, 0), 1)`. **Every comparison with a
NaN is false, so both calls hand the NaN straight back.** Two things followed, and the quiet one
was worse:

- `HoldoutManager.isInRollout` guards on `isFinite`, so a NaN meant NOBODY was in the rollout — the
  brain off for the entire population with `brainEnabled == true`, no warning, no symptom. The
  existing `rolloutFraction <= 0` check could not catch it, because that comparison is false for a
  NaN too.
- `Report.describe()` — the string someone prints BECAUSE something already looks wrong — then
  **trapped** converting it to a percentage: `Fatal error: Double value cannot be converted to Int
  because it is either infinite or NaN`.

A NaN arriving here is ordinary; it is what `Double("")` and a missing remote-config field produce.
Now: `safeFraction` maps it to 0, `checkConfig` says so out loud, and every percentage conversion
in a diagnostic string goes through `JsonConfigValue.clamped`. `BrainInspector.format`'s posterior
bar got the same treatment for the same reason — a debug dump is the last thing that should be able
to kill the app. The mutation test restores the old clamp and reproduces the fatal error exactly.

The other conversions in the sweep were checked and left alone, each for a stated reason: `Int64`→
`Int` narrowings (64-bit, exact), `BattlePass.catchUpMultiplier` (both operands validated at parse
time), and the two `Date()`-derived timestamps (a system clock cannot reach the trapping range).

### Remote config: one payload, eight subsystems

Eight subsystems take a config artifact (`BrainPolicy`, `PaywallManager`, `PurchaseRouter`,
`SubscriptionLifecycle`, `BattlePass`, `RecurringGrants`, `OfferwallManager`, `CrmBridge`). Until
now nothing called any of them, so the whole config surface was inert unless the host wrote the
fan-out itself. `RemoteConfigDispatcher` is that fan-out.

```swift
FirebaseApp.configure()
let provider = FirebaseRemoteConfigProvider()          // MonetizationBrainFirebase
await MonetizationBrainSDK.configure(.init(eventSink: sink, remoteConfig: provider, playerId: id))
let report = await RemoteConfigDispatcher.shared.refresh(from: provider)
print(report.describe())                               // one line per key: applied / rejected / absent
```

| Key | Applied to |
|---|---|
| `brain_policy_ios` | `DecisionEngine.applyPolicy` |
| `paywall_config_ios` | `PaywallManager` |
| `purchase_routing_ios` | `PurchaseRouter` |
| `subscription_lifecycle_ios` | `SubscriptionLifecycle` |
| `battle_pass_ios` | `BattlePass` |
| `recurring_grants_ios` | `RecurringGrants` |
| `offerwall_config_ios` | `OfferwallManager` |
| `crm_bridge_ios` | `CrmBridge` |

Points worth knowing, all covered in
[the design spec](docs/superpowers/specs/2026-09-15-remote-config-dispatcher-design.md):

- **The dispatcher imports nothing.** It talks to `RemoteConfigProvider`, so a host with its own
  config backend gets the identical dispatch, and the whole fan-out is tested against a dictionary.
- **Dispatch runs after `configure`, not inside it.** Both set `AdsBrain.rolloutFraction`; the
  artifact is the operator's live override and should win. Folding it into `configure` would have
  inverted that.
- **A bad artifact costs exactly its own subsystem's update** — the reason for eight keys rather
  than one blob. An empty value is `.absent`, not a parse failure, matching Android's `isNotEmpty`
  guard.
- **`DecisionEngine.applyPolicy` had to be ported too**, because `guardrails` is private on
  purpose. Doing so found a dead field: the artifact's `exploration_floor` was parsed, clamped and
  then discarded — `BrainPolicy.pendingExplorationFloor` is now `Double?` and reaches
  `BanditPolicy`. It is a public API change, and it is the one that stops a paywall-only artifact
  from silently re-tuning exploration.
- **One deliberate divergence from what Android ships.** Android's Remote Config path calls
  `BrainPolicy.apply(json, DecisionEngine.instance.guardrails)` directly and never calls its own
  `DecisionEngine.applyPolicy`, so `exploration_floor` never reaches the bandit there. iOS routes
  through the engine, so **iOS re-tunes the exploration floor on a policy push and Android does
  not** — segment off-policy analysis by platform, the same caveat that already applies to
  `sequenceId`. Shipping a config field that is accepted and then ignored was the worse option.
- **Firebase is a real dependency cost.** SwiftPM resolves the whole graph, so every consumer of
  this package now fetches Firebase's binary artifacts (~144 MB of checkouts) even if it only uses
  Core. Accepted because this SDK's consumers are Firebase apps already (SwiftPM dedupes), unused
  libraries are resolved but not linked, and a nested package cannot be referenced by URL. A host
  that wants none of it can copy `FirebaseRemoteConfigProvider.swift` into its own app — it uses
  no private API. The range is `11.0.0 ..< 13.0.0`; 11.15.0 and 12.19.1 were both built here.

### Parity gaps remaining after the audit (integration surface, not brain behaviour)

- **The ad-serving layer is not ported — and, measured, it should not be.** The files were counted
  rather than described, because "82 files" was doing too much work in this bullet:

  | | files | lines |
  |---|---|---|
  | `adsmob/` (AdMob adapters) | 17 | 3 567 |
  | `adsmax/` (AppLovin MAX adapters) | 17 | 2 497 |
  | `adsfb/` (Meta adapters) | 16 | 2 560 |
  | `ads/` itself | 32 | 4 470 |
  | **total** | **82** | **13 094** |

  **Two thirds of it is mediation-vendor plumbing**, and that part is not portable in any useful
  sense: on iOS you write against the GoogleMobileAds and AppLovin **iOS** SDKs, so a "port" of
  Android's adapter classes would be a translation of code nobody should run.

  What is actually brain-related is smaller than the bullet implied: **seven call sites, all in
  `AdsHelper.kt`** — one `enabled` check, one `shouldShowInterstitial()`, one
  `onInterstitialShown()` and four `onAdSkipped()` reports, around the interstitial path.

  iOS exposes all seven already, through the five decision points and the feedback hooks, and
  [`docs/INTEGRATION.md`](docs/INTEGRATION.md) §4–§5 is how a host wires them into whatever ad
  stack it uses. So this is not a missing subsystem; it is an integration step, and it now has
  documentation instead of a backlog entry.
- ~~**No one-call facade.**~~ **Closed.** `MonetizationBrainSDK.configure(_:)` applies the eight
  steps in the one safe order and returns a `Report` with warnings and `appliedOrder`; the order
  is locked by a test verified to bite (swapping `enabled` ahead of `rolloutFraction` fails it).
  Deliberate divergences from Android are documented at the type: `playerId` required, explicit
  `eventSink`/`remoteConfig`, no `consumableProducts` (StoreKit 2 reports product type), and a
  warning — not a silent skip — when products are declared without a `StoreBackend`.
- ~~**No Firebase Remote Config adapter.**~~ **Closed.** `RemoteConfigDispatcher` (in Core, with
  no Firebase import) fans one payload out to all eight `apply()` entry points, in Android's
  order, under the eight `*_ios` keys; `MonetizationBrainFirebase` supplies the Firebase-backed
  `RemoteConfigProvider`. Android's `FirHelperConfig` reads twelve keys — the other four belong to
  the unported ad layer. See the section below.
- ~~**No AppsFlyer bridge.**~~ **Closed for the half that belongs to this SDK.**
  `AttributionBridge` + `IapHelper` wiring deliver purchase revenue to whatever attribution SDK
  the host supplies (see the section above). `logAdRevenue` and the conversion-data callbacks stay
  unported: the first belongs to the ad layer, the second to app startup — neither is this
  package's to own.
- ~~**App-lifecycle hooks are manual.**~~ **Closed.** `SessionLifecycle` installs UIKit
  foreground/background observers (opt-out via `Config.observeAppLifecycle`; a no-op without
  UIKit so the core still builds for macOS). A foreground is a new session only after Android's
  same 30-minute gap, every foreground re-syncs entitlements, every background ends the session.
  The decision is a pure function tested without UIKit. `session_start`/`first_open` events are
  still not emitted — they belong to Android's ads/app layer, not the brain.
- ~~**`BrainInspector` (26-field snapshot) and `BrainDebugView`**~~ **Closed for the inspector.**
  `SelfCheckReport` answers "is the SDK healthy"; `BrainInspector` answers "what is it deciding
  for this player right now" — posterior, segment confidence, scenario (with a fallback flag),
  active signals, effective cap, guardrail block counts, the last 30 decisions, policy version +
  report + kill switch, holdout verdict and bucket, exploration floor — plus `format(_:)` for a
  log or bug report. It reaches nothing private: `DecisionEngine` gained four read-throughs, and
  the snapshot reads the policy THAT engine applied rather than `BrainPolicy.shared`.
  `BrainDebugView` (the on-screen overlay) stays unported — it is a UIKit view, and `format(_:)`
  is the string it would show.

### Known follow-ups surfaced by Phase 9's reviews — now closed

All three are resolved. Two of them had in fact been fixed by later phases and this list was
simply never updated; saying so is more useful than quietly deleting the bullets.

- ~~**`DecisionEngine.observe` has the same write-ordering shape, and slightly worse**~~ —
  **already fixed, list was stale.** `observe` takes ONE acquisition covering the update, the
  snapshot and the sequence stamp (`BeliefState.updateAndSerialize`) and writes through
  `BrainStorage`'s sequenced writer, so a concurrent observer can neither interleave into the
  serialized posterior nor overtake the write.
- ~~**`Entitlements` writes inside its own lock**, so `BattlePass.claim` / `RecurringGrants.claim`
  can block on a lock held across a `UserDefaults` write~~ — **already fixed, list was stale.**
  `Entitlements` mutates under its lock, takes the snapshot AND its sequence number in that same
  acquisition, releases, and only then writes through `SequencedStoreWriter`. No store I/O happens
  under that lock any more, so the nesting the two claim paths do is a leaf-lock read that cannot
  wait on I/O. That nesting is itself deliberate and documented at both call sites: ownership is
  part of the same atomic decision as the elapsed-period/tier check and the claim write, and
  re-reading it outside the lock would reopen the TOCTOU window the claim exists to close.
- ~~**`PaywallManager.parseWeight` and `HoldoutManager.isHoldout`** still carry hand-rolled
  clamp-before-convert logic~~ — **done.** `parseWeight` now borrows
  `JsonConfigValue.numeric`'s extraction (including the CFBoolean discriminator, without which
  `{"weight": true}` parses as a live weight of 1) while keeping its own note text, because
  `numeric`'s "-> dropped" is wrong here: an unusable weight leaves the variant in the config,
  unselectable. That migration also turned a silent zero into a noted one for values like
  `"Infinity"`. `HoldoutManager`'s fraction→bucket conversion goes through
  `JsonConfigValue.clamped`, so `Int(Double)` can no longer trap regardless of whether the three
  guards above it stay in that order — the same trap this port has now found in five places. The
  defence itself is not test-covered and cannot be (no public call can reach the conversion with
  an extreme value); what is tested is the property the experiment depends on: raising the
  holdout fraction only ever ADDS players to the control group.
### Known deferred subsystems (from design spec §1, Phase 7)
**Status note (Phase 12):** `RecurringGrants`' ownership OR-ing shipped with Phase 8 —
`RecurringGrants.owns` is exactly `subscriptionLifecycle.stateFor(id).isEntitled ||
entitlements.isActive(id)`. Everything else in this list still stands; the StoreKit ones are
platform limits, not backlog.


- **Everything that happens while the app is closed.** Expiry, renewal, billing failure, grace
  entry/exit, refund approval, `REFUND_REVERSED`, and every App Store Server Notification *subtype*
  (`EXPIRED/VOLUNTARY` vs `EXPIRED/BILLING_RETRY`, `DID_RENEW/BILLING_RECOVERY`,
  `SUBSCRIBED/INITIAL_BUY` vs `/RESUBSCRIBE`) are invisible to an on-device layer. A customer who
  churns and never reopens the app is invisible to this SDK **by construction**. This bounds what
  the layer can claim: it is correct about "is this customer entitled right now, while they are
  looking at the app," and cannot be correct about churn analytics, dunning-recovery attribution,
  or refund reaction. Closing it needs an App Store Server Notifications V2 endpoint — a backend
  project, not Swift code.
- **Native StoreKit win-back offers** (`RenewalInfo.eligibleWinBackOfferIDs`,
  `Product.SubscriptionInfo.winBackOffers`) — both iOS 18.0+, above this package's iOS 15 floor.
  `isWinBackEligible` stays the home-grown device-local heuristic. The first thing to add if the
  floor rises.
- **`Product.SubscriptionInfo.Status.updates` as a live listener.** Apple does not document its
  emission contract (which transitions fire, whether an initial value is emitted, whether it
  coalesces), and expiry is time-based, so a foreground reconcile is required regardless. This
  phase classifies at explicit sync points, exactly as Android does.
- **Subscription group modeling.** `Status` carries no group id on iOS 15 (`Status.all` is iOS 17+).
- **Refund vs. Family Sharing revocation** are indistinguishable on iOS 15 — both set
  `revocationDate`, and `RevocationReason` offers only `.developerIssue`/`.other`.
- **`history` is device-local** — reinstall loses it, so a returning churned user can read `.none`.
- **`RecurringGrants`' ownership OR-ing** (Android's `Entitlements.isActive(id) || stateFor(id) ==
  ACTIVE`) — `RecurringGrants` is not ported; that wiring belongs to its own phase.
- ~~**Not testable in this package:** everything in `StoreKit2Backend`~~ — **half of it was, and
  now is (Phase 12).** The claim was true of the half that talks to the store (`Transaction` and
  `Product` cannot be minted in a test) and false of the half that decides what the store's answer
  MEANS. `MonetizationBrainStoreKit` now has a test target covering the Family Sharing reduction,
  the `RenewalState`/`ExpirationReason`/`ProductType` mappings **including their load-bearing
  `default:` branches** (those three StoreKit types are `RawRepresentable` structs a test can build
  with a raw value Apple has not shipped), the epoch-ms conversion and the `Decimal` price
  conversion. Writing them found a real defect — see below. Still genuinely untestable here:
  `purchase`, `currentEntitlements`, `transactionUpdates`, unverified-payload handling and the
  `isAutoRenewing` read, all of which need real StoreKit objects.

### Known deferred subsystems (from design spec §1)

**Status note (Phase 12):** three of these have since shipped, and the list is kept as the record
of what Phase 3 deferred at the time rather than edited into agreement with today. `BrainPolicy`
landed in Phase 11 and is now applied automatically by `RemoteConfigDispatcher`; `BrainInspector`
landed in Phase 12; `BrainStorage.loadOfferCount24h`/`bumpOfferCount24h` are persisted per UTC day
and read by `DecisionEngine`.

The following were explicitly scoped out of Phase 3 and are deferred to later phases:

- **`BrainPolicy`** / remote policy artifact application — fixed local defaults used instead of a
  remote override. A distinct remote-config-driven tuning subsystem, not part of the decision loop.
- **`BrainInspector`** — pure debug/diagnostic snapshot tool. `SelfCheckReport` (Phase 1) covers
  the "is this SDK healthy" question.
- **`DecisionLogQueue`** — iOS already has this exact guarantee generically in `EventQueue` (persist
  before send, remove by content match), built in Phase 1. Porting a second queue would duplicate it.
- **Wiring into `AdsBrain`** — mirrors the Phase 1 → Phase 1.5 split; Phase 3 ships a complete,
  independently testable orchestration layer, Phase 3.5 wires it into the public facade.
- **Consent scope on `brain_decision` logging, and the `precondition` on `decide`'s candidate-list
  shape** — both flagged for a design decision during Phase 3.5's wiring work, and both now
  resolved: consent scope is closed by gating `segment`/`segment_confidence` on
  `ConsentState.allowsPersonalization()`, and the precondition risk is closed by construction
  in `AdsBrain`'s candidate-building code (always prepends `.noAction`). See Phase 3.5 section
  above for full details.
- **`BrainStorage.playerId()`** — iOS's `AdsBrain.Configuration.playerId` is required; the host
  app always supplies one.
- **`BrainStorage.loadOfferCount24h`/`bumpOfferCount24h`** — not called by `DecisionEngine` in
  the Android source; belongs to paywall/IAP offer-frequency tracking not yet ported.
- **`DecisionRequest.context: Map<String, Any>`** — never read by `DecisionEngine.decide()` in
  the Android source. Omitted rather than ported as dead weight.

### Known deferred subsystems (from design spec §1, Phase 3.5)
**Status note (Phase 12):** five of these have since shipped — remote-config-driven
`rolloutFraction` (the artifact's `experiment.rollout_fraction` writes `AdsBrain.rolloutFraction`,
dispatched from `brain_policy_ios`), `experiment_exposure` logging (one row per session in
`onSessionStart`), `ComfortIndex` (Phase 10), both CRM bridge signals (`emitFirstPurchase` from
`onPurchase`, `emitSegmentChange` from `observe`), and the `iapOffers24h` persistence.
`decide`'s two `precondition`s are unchanged and the question they raise is still open.


The following were explicitly scoped out of Phase 3.5 and are deferred to later phases:

- **Remote-config-driven `rolloutFraction`** — the field and the `isActive()` gate ARE ported; what
  Android calls "raise it via policy artifact" is not, for the same reason `BrainPolicy` itself is
  deferred (Phase 3 spec §1). Fixed at whatever the host app sets directly.
- **Experiment-exposure logging** — `HoldoutManager.exposureRecord` + a `experiment_exposure` event on
  every `onSessionStart`. No CRM/analytics event of that shape exists on iOS yet. This subsumes and
  formally retires the "no backend consumer yet" gap already on `sdk_baseline_decision` (that event is
  now deleted, see Phase 3.5 spec §3.4) rather than leaving two unconsumed events instead of one.
- **`ComfortIndex`** (the 4th North Star Metric — see `[[sdk-product-output-definition]]` memory)
  and `onSessionEnd`'s `reportComfort()` call — no `ComfortIndex` type exists on iOS. Explicitly
  flagged here (not silently dropped) because it maps to a real, named product goal.
- **CRM bridge signals** (`emitFirstPurchase` on `onPurchase`, `emitSegmentChange` on a belief-type
  argmax flip inside `observe`) — no CRM bridge exists on iOS yet.
- **`BrainStorage.loadOfferCount24h`/`bumpOfferCount24h` persistence** — already flagged deferred
  in Phase 3's own spec §1. `PlayerState.iapOffers24h` stays session-scoped only (bumped in-memory
  by `onIapOfferShown`, reset to `0` every `onSessionStart`, never loaded/persisted across
  sessions) — a smaller, honest approximation rather than silently wrong data.
- **`DecisionLogger.flushQueue()` equivalent** — no analog needed; `EventQueue` (Phase 1) already
  owns durable delivery generically, per Phase 3 spec §1's identical reasoning for `DecisionLogQueue`.
- **Replacing `DecisionEngine.decide`'s `precondition` with a fail-safe fallback** — Phase 3.5 closes
  the practical risk by construction instead of by loosening the contract: `AdsBrain`'s own
  candidate-construction code always prepends `ActionCandidate(.noAction, "no_action")` unconditionally,
  so every call `AdsBrain` itself makes is non-empty by construction. The broader question (should
  `DecisionEngine` itself have a fail-safe for external callers) is left open for a future phase if
  a real misuse case surfaces; Phase 3.5 does not need to answer it to close the risk through
  `AdsBrain`.

### Known deferred subsystems (from design spec §1, Phase 4)
**Status note (Phase 12):** three of these have since shipped — multi-channel purchase routing
(`PurchaseRouter`, Phase 6), subscription lifecycle (Phase 7), and
`RecurringGrants`/`BattlePass`/offerwall (Phases 8, 9, 11). AppsFlyer revenue reporting,
server-side receipt verification, the ad-control facade and `SKTestSession` integration testing are
unchanged.


The following were explicitly scoped out of Phase 4 and are deferred to later phases:

- **Multi-channel purchase routing** — iOS Phase 4 supports exactly one channel (App Store via StoreKit 2).
  `PurchaseChannel` keeps a `webShop` case for forward-compat (matching Android's own pattern) but nothing
  in this phase ever produces it. The purchase-router phase (mirroring Android's `PurchaseRouter.kt`,
  commit `7e16ee8`) is deferred.
- **Subscription lifecycle** — this phase reports whatever entitlement state StoreKit 2 itself reports
  (including grace-period semantics) but does not build dunning/win-back campaign logic on top. The
  subscription-lifecycle phase (mirroring Android's `SubscriptionLifecycle.kt`, commit `4e9610e`) is
  deferred.
- **`RecurringGrants`/`BattlePass`/offerwall** — separate later phases per Android's own file layout.
- **Paywall UI** — the host app owns purchase-initiation UI entirely; `IapHelper.purchase(_:)` mirrors
  `AdsBrain`'s decision points' "answer the API call, don't render anything" contract.
- **Server-side receipt verification deploy** — the SDK sends a verifiable receipt (the signed JWS
  transaction) to the backend via the existing event pipeline; standing up an App Store Server API
  verification endpoint on the backend (Android's parallel is `backend/app/play_verify.py`) is
  explicitly a backend-team concern, not part of this SDK port.
- **`Entitlements`' bridge to an iOS ad-control facade** — the type still computes `hasRemoveAds()`
  correctly and exposes it publicly, but there is no outbound `setRemoveAds()`-equivalent call yet
  (no ad-serving layer exists on iOS to call). Documented as a dangling bridge point for a future
  ad-serving phase.
- **AppsFlyer revenue reporting** — no AppsFlyer (or any attribution SDK) wrapper exists in this iOS
  port yet. `IapHelper.reportPurchase` calls the `iap_purchase` event and `AdsBrain.onPurchase`
  unconditionally; future attribution-bridge phase will add the third call site, with a clearly-marked
  comment at the deferred location.
- ~~**`StoreKitTest`/`SKTestSession` integration testing**~~ — **Closed.** 20 tests now run
  `StoreKit2Backend` against a real StoreKit: `products(for:)`, `purchase(_:)`,
  `currentEntitlements()`, `subscriptionSnapshots()`, `finish(_:)`, `renewalIntent` and the
  order-id cache between them, over a `Products.storekit` catalogue (consumable, non-consumable,
  auto-renewable).

  **Four of them drive the lapse states.** `toRenewalState` and `toExpirationReason` had been
  covered only by the mapping tests, which feed them values built by hand — that proves the switch
  arms, not that a real StoreKit ever produces those values or that `toSubscriptionSnapshot`
  carries them through. `SKTestSession` now drives the store into `.expired` (with an
  `.autoRenewDisabled` reason), `.revoked` via a refund, `.inGracePeriod` (asserting
  `gracePeriodExpirationMs` is non-zero, since that deadline is what a host prompts against) and
  `.inBillingRetryPeriod` — the state Android cannot see at all, and the one that must not be
  collapsed into `.expired` or a win-back campaign starts at someone whose payment may yet
  succeed. Mutating `.revoked -> .expired` and `.inGracePeriod -> .subscribed` fails 3 of them;
  mutating `.autoRenewDisabled -> .billingError` fails 1.

  The billing flags are set AFTER the purchase, not before. Setting
  `shouldEnterBillingRetryOnRenewal` first makes the INITIAL purchase enter billing retry too, so
  the subscription never becomes visible and the test fails intermittently on "not visible to the
  store" instead of on what it asserts. That was a real flake, found by a verify run and fixed at
  the cause rather than by raising a timeout; the suite was then run three times clean.

  **Getting there took three findings, each only visible after the previous one turned out not to
  be enough.** This bullet twice said what was missing and was twice wrong, so the sequence is
  recorded rather than tidied away:

  1. It first claimed the gap was "the `.storekit` configuration and the session setup". Wrong —
     the file always loaded. The real blocker was that a SwiftPM test target has no **host app**,
     so `Bundle.main` is `com.apple.dt.xctest.tool` and StoreKit answers `.notEntitled`.
  2. With a host app, `Bundle.main` became the app and it STILL returned `.notEntitled`. Signing
     was off (`CODE_SIGNING_ALLOWED = NO`); an unsigned app carries no entitlements at all.
     Ad-hoc signing (`CODE_SIGN_IDENTITY = "-"`) is required.
  3. Host app plus ad-hoc signing, and still `.notEntitled`. The last piece was a
     **`get-task-allow` entitlement**. With it, the storefront populated and the catalogue served.

  The symptom for all three was identical and silent — `Product.products(for:)` returns `[]`,
  nothing throws — which is why `setUp` probes and `XCTSkip`s with what it measured instead of
  letting sixteen assertions fail on "no product".

  **A fourth finding came from CI, not from a test.** `SKTestSession.resetToDefaultState()` also
  resets the session's settings, so setting `disableDialogs` BEFORE it silently puts it back to
  false and a purchase then blocks forever on a confirmation sheet. A CI run sat on that for 27
  minutes and was killed by the job timeout having printed nothing. `Scripts/verify.sh` now puts
  a per-step `timeout` around every command, because a step that hangs must fail like any other.

  **A fifth finding came from CI too, and it deleted a target.** These tests were briefly ALSO a
  SwiftPM target that skipped when StoreKit was unavailable. On a developer Mac it skipped; on the
  CI runner StoreKit served the catalogue but refused the purchase, so the same commit skipped in
  one place and failed with `caught error: "unknown"` in the other. A test target whose outcome
  depends on which machine ran it is worse than no target, so the SwiftPM mirror is gone. The
  tests live in `Integration/StoreKitHostedTests/`, outside `Tests/`, and are built only by
  `Integration/MonetizationBrainIntegration.xcodeproj` — one home, in which they actually run.
  `.gitignore` excludes `*.xcodeproj` with this one deliberate exception, because CI has to build
  it and an integration test nobody runs is not an integration test. `verify.sh` FAILS if that
  target ever skips, since `xcodebuild` reports an all-skipped target as success.

  **Two mutations were used to confirm the tests bite**: blanking the JWS receipt in
  `toIapPurchase` (2 failures), and inverting `renewalIntent`'s `.verified` check on `RenewalInfo`.
  The second initially SURVIVED — the subscription assertions had been moved onto
  `subscriptionSnapshots()`, which reaches that value by a different path — so
  `test_isAutoRenewingOnARestoredSubscriptionComesFromVerifiedRenewalInfo` was added to cover it,
  and the mutation then failed as it should.

  **Eight more tests cross the seam into `SubscriptionLifecycle`.** Both halves were well covered
  and neither covered the join: the lifecycle has 790 lines of tests that all feed it a
  `SubscriptionSnapshot` built by hand, and the adapter tests drive a real store and stop at the
  snapshot. So classification was proven for snapshots a test invents, and the adapter was proven
  to produce snapshots nobody then classified — a field mismatch between the two passes both
  suites and bills the wrong person. `SubscriptionLifecycleEndToEndTests` takes one path: drive a
  real store into a state, read it through `subscriptionSnapshots()`, hand THAT to
  `observe(_:)`, and assert on the decision. It covers the cancelled-but-entitled rule (the
  Play-port mistake `SubscriptionState` warns about), the lapse transition, win-back eligibility,
  the refund exclusion, grace-period dunning, billing retry, and `observe`'s idempotence across
  repeated polls.

  Mutation-checked across the seam, not within one half: making `.cancelled` non-entitled fails 2,
  and offering win-back to refunders and mid-retry customers fails 3.

  Note the coverage number: `swift test`'s report still shows `StoreKit2Backend` at 24.6% of
  lines, because that measurement only sees the SwiftPM run, where these tests skip. The
  xcodebuild run does not report the package target separately, so no combined figure is quoted
  here — the mutation results above are the evidence that these paths are exercised, not a
  percentage.

  This project's test suite otherwise runs via
  plain `swift test` (SPM) with zero real I/O in any test. `MonetizationBrainStoreKit` (the real
  StoreKit 2 adapter) carries minimal-to-no automated test coverage (thin, type-checked conformance
  verified by construction); exhaustive tests live entirely against the `StoreBackend` protocol in
  `MonetizationBrainCore`, using an in-memory fake.

### Known deferred subsystems (from design spec §1, Phase 5)
**Status note (Phase 12):** dunning shipped with `SubscriptionLifecycle` (Phase 7) and
multi-channel routing with `PurchaseRouter` (Phase 6). Native StoreKit win-back offers stay
deferred — see the Phase 7 list above. Free-trial eligibility, paywall UI, price A/B and the
backend funnel are unchanged.


The following were explicitly scoped out of Phase 5 and are deferred to later phases:

- **No paywall UI/rendering/theming** — the host app owns 100% of the visual surface. `PaywallManager`
  is a decision-only facade answering the API question; it never renders, styles, or constructs a
  user-facing view. Confirmed by Android's own `PaywallManager.kt` containing zero view/layout/color
  types.
- **No price A/B** — variants only reorder/select which SKUs to offer, never change price. Android's
  design spec (DOC/49 §E.1) explicitly defers price-bandit work (needs multiple same-tier SKUs at
  different real store price points — an ops/App-Store-Connect decision, not an SDK feature).
- **Dunning/win-back and free-trial eligibility** — both explicitly deferred by Android's own design
  to separate, later features. Free-trial eligibility is left to the store's own offer-token mechanism,
  not the SDK's job.
- **Multi-channel purchase routing** — already tracked as a Phase 4 deferred item (Android's own
  `PurchaseRouter`). Paywall has zero coupling to channel selection.
- **The AppsFlyer revenue-reporting fix** — confirmed by research to have zero coupling to paywall
  (it's `IapHelper`/`AppsFlyer` code). This is Phase 4/attribution territory, not Phase 5's concern.
- **Backend `/metrics/paywall` conversion-funnel computation** — pure backend analytics, joining
  the two new client events (`paywall_impression`, `paywall_holdout_skip`) against the already-shipped
  `iap_purchase` event server-side. No client-side counterpart beyond the two events this phase emits.

### Known deferred subsystems (from design spec §1, Phase 6)

The following were explicitly scoped out and are deferred to later phases or a separate backend
project:

- **Server-side entitlement source of truth / `GET /entitlements` sync.** `Entitlements` stays the
  on-device source of truth (lost on app delete, no cross-device sync) — a real fix needs a studio
  backend endpoint, not SDK code.
- **Server-side receipt verification for the App Store branch.** Unchanged by this phase — tracked
  as a Phase 4 deferred item already.
- **`restorePurchases` covering web-shop purchases.** Restore only ever talks to the App Store; a
  web-shop "restore" is a studio-server concern the SDK has no data to perform.
- **Channel-selection heuristics** (cost/conversion-optimized routing). `route()` is a pure
  function of static config on both platforms — no bandit/brain involvement.
- **Deep-link return-to-app after web checkout.** The checkout URL belongs to the studio; getting
  the user back into the app after paying is the checkout page's job.
- **Backend changes** (a `routing_version` column, `web_checkout_opened` ingestion). Not needed —
  `revenue.by_channel_usd` already keys on the existing `channel` column every `iap_purchase` event
  already carries (Phase 4). This port has never touched the Python backend in any SDK phase.
- **A UI for opening the checkout URL.** Same "SDK doesn't draw UI" boundary as `PaywallManager`
  and `IapHelper.purchase` — the host app decides how to open it.

### Known Phase 1 limitations
**Status note (Phase 12):** `HoldoutManager.salt` and `holdoutFraction` are no longer
unsynchronized `static var`s — both sit behind `LockedValue`, which is what took the package to
zero strict-concurrency warnings. The `EventQueue` items below are unchanged.


Found and consciously deferred during the final whole-branch review (not oversights — each is
a scoped-out follow-up for a later phase):

- **`AdsBrain` now wires event emission and holdout membership for real (Phase 1.5 → Phase 3.5).**
  Phase 1.5 established the pattern: `configure(_:)` emits `sdk_configured`, and the five decision-point
  methods report real `HoldoutManager` bucket lookups for `isHoldout` — see
  [`docs/superpowers/specs/2026-09-03-wire-facade-design.md`](docs/superpowers/specs/2026-09-03-wire-facade-design.md).
  Phase 3.5 completes the wiring: `DecisionEngine.decide` emits real `brain_decision` events with
  `chosen_action_probability` and `eligible_action_ids` (from the bandit), and feedback resolution
  via `DecisionEngine.feedback` emits `brain_feedback` with the actual outcome reward. The Phase 1.5
  stopgap `sdk_baseline_decision` event is retired — `brain_decision`/`brain_feedback` (from
  `DecisionEngine`) are the real signals now. Two things stay deliberately unwired: `guardrailEngine.allows(...)`
  is never consulted directly by `AdsBrain` (guardrails filter the candidate set inside `DecisionEngine`),
  and `experiment_exposure` logging is deferred (Phase 3.5 spec §1). **Known gaps:** a decision-point
  call now awaits the full event-send path (persist + network attempt) rather than an enqueue-only step,
  so it can block on network latency — **fixed, and it was worse than this sentence implied**.
  Measured: `shouldShowInterstitial()` blocked for **2.09 s against a 2.0 s sink**, a 1:1
  pass-through, with `URLSessionEventSender`'s 30 s default timeout as the worst case. Awaiting it
  bought the caller nothing — `attemptSend` catches every error and returns `Void`, so no amount of
  waiting tells the caller whether delivery worked — and durability comes from `enqueueLine`, which
  has already run. `EventQueue.send` now returns at persist and delivers on a background chain
  (sequential, so a burst cannot open N concurrent POSTs at a backend with no batch endpoint);
  `quiesce()` awaits it for tests and for a host draining before background. Re-measured at
  **0.002 s**, with the record already on disk. `DecisionLatencyTests` locks both halves — restoring
  the old default fails them with 2.04 s and a zero queue — because every other test uses a fake
  sender that returns instantly, so nothing else in the suite could ever see this; and event emission is gated on `ConsentState.allowsDecisionLogging()`
  and (for `segment`/`segment_confidence` fields) `ConsentState.allowsPersonalization()`, not on
  `AdsBrain.enabled` — a host that calls `configure(_:)` but leaves `enabled == false` (the documented
  default) still writes to disk and attempts network sends on every decision point, which remains an
  open design question ("does the kill switch also govern observability?") deferred to a future phase.
- **No retry backoff, and `dedupKey`-style idempotency isn't implemented client-side.**
  `EventQueue.flush()` retries every queued event on every call with no delay and no
  distinction between a transient failure (retry) and a permanent one like a malformed payload
  (which would otherwise sit in the queue forever, occupying a slot until `maxRecords` trims
  it — potentially trimming good events to make room for a poison pill).
- ~~**`EventQueue` does a full read-modify-write on every `send()`/`flush()` step**~~ — **fixed
  (Phase 12), after measuring it the way the code's own comment asked for.** Measured first, on
  the case a durable queue exists for:

  | queued events | enqueue (before → after) | flush (before → after) |
  |---|---|---|
  | 200 | 440 ms → **16 ms** | 434 ms → **20 ms** |
  | 500 | 1 628 ms → **40 ms** | 1 751 ms → **85 ms** |

  Quadratic in both directions, and the enqueue half was the ordinary path every event takes, not
  a recovery path. Now, matching Android's `DecisionLogQueue`: `enqueue` appends in O(1) and
  rewrites only to trim (which is why the record count is cached), and `flush` batches removals
  into one rewrite per 25 delivered records.

  **What it trades:** delivery is at-least-once by design and there is no client-side dedup key,
  so removing one record at a time meant a process killed mid-flush re-sent at most ONE
  already-delivered event. Batching widens that bound to 25. An append torn by a kill leaves a
  partial trailing line, which is not a new failure mode — `flush` already drops a record it
  cannot decode rather than retrying it forever, and there is now a test for it.

  **Three things code review caught, all fixed before merge:** the top-of-file "every write is an
  atomic replace" contract was no longer true of the append path and now says so; a cached record
  count gave up the self-correction the old re-read-everything design had, so a foreign write is
  now detected for free (the append's seek returns the real file length) and the count re-read,
  because an under-count means the trim never fires and the file grows unbounded; and `flush` —
  public, host-driven, suspending on every send — had no guard against a second concurrent drain,
  which silently doubled both of the bounds above. Each has a test verified to fail without the fix.

  **The floor, found by a test that failed:** if neither the append nor the fallback rewrite can
  write (full disk, read-only volume), the record never reaches disk and "persist, then send"
  degrades to send-and-hope. There is nowhere else to put it. What is guaranteed in that state is
  that the queue does not crash and does not count the record as queued — a `queuedEvents` figure
  that reports data already lost is worse than one that reports the loss.
- **`HoldoutManager.salt`/`holdoutFraction` are unsynchronized `static var`**, unlike every
  other piece of shared state in this package (`ConsentState`, `AdsBrain`: `NSLock`;
  `EventQueue`: `actor`). A concurrent write-during-read is a real (if narrow) data race — and as
  of Phase 1.5, `isHoldout(_:)` is reachable from `AdsBrain`'s `async` methods on arbitrary
  executor threads, which raises this from theoretical to a real (still unexploited, since
  nothing writes these statics in production yet) risk. Confirmed via
  `swift build -Xswiftc -strict-concurrency=complete`: these are the package's only remaining
  warnings, and they are hard errors under the Swift 6 language mode.
