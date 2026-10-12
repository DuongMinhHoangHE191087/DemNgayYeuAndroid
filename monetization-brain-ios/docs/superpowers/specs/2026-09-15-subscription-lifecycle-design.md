# Subscription Lifecycle — Design Spec

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:writing-plans to turn this into an
> implementation plan, then superpowers:subagent-driven-development to execute it.

**Goal:** Port Android's `SubscriptionLifecycle` (`com.app.plugin.iap.SubscriptionLifecycle`,
commit `4e9610e`) — the layer that answers "what is the operational state of this subscription, and
what should the app do about it?" (dunning notices while a payment is failing, win-back eligibility
after churn, a management deep link). This is the last of Phase 4's deferred items that the base IAP
layer already unblocks.

**Architecture:** A new `SubscriptionLifecycle` singleton (`.shared`, matching
`IapHelper`/`PaywallManager`/`PurchaseRouter` convention) holds NSLock-protected config +
per-product state, loaded via the same permissive-JSON `apply(_:)` parser every other config-driven
subsystem in this package uses. It is a **pure classification layer**: it never talks to a store
itself, it is fed snapshots by `IapHelper` and classifies them. Unlike Android, those snapshots
carry StoreKit's own native renewal state rather than two booleans the layer has to guess from —
see §3.1, which is the single most consequential decision in this spec.

**Tech Stack:** Swift Package Manager, `MonetizationBrainCore` target (classification + config),
`MonetizationBrainStoreKit` target (the StoreKit 2 → owned-model mapping only),
`Foundation.JSONSerialization`, `NSLock`, XCTest.

---

## 1. Scope

**In scope:**
- `SubscriptionLifecycle` type: `.shared` singleton, `Configuration { eventSink, playerId, store }`,
  `configure(_:)`, `activeVersion()`, `resetForTest()`.
- Config parsing: `apply(_ json: String?) -> SubscriptionApplyResult`, matching the JSON shape in §4.
- Classification: `observe(_ snapshots: [SubscriptionSnapshot], complete: Bool = true) -> [SubscriptionTransition]`.
- Queries: `stateFor(productId:)`, `isWinBackEligible(productId:)`, `winBackEligibleProductIds()`,
  `managementUrl()`.
- Dunning: `noteForegroundOpen(productId:)` (mutating) + `dunningNoticeDue(productId:)` (pure) —
  split, unlike Android's single method that mutates on read (§3.5).
- New owned models in `Sources/MonetizationBrainCore/Iap/SubscriptionModels.swift`:
  `SubscriptionRenewalState`, `SubscriptionSnapshot`, `SubscriptionState`, `SubscriptionTransition`,
  `SubscriptionApplyResult`, `SubscriptionExpirationReason`.
- One new `StoreBackend` method: `subscriptionSnapshots() async throws -> [SubscriptionSnapshot]`,
  with the real mapping in `StoreKit2Backend` and stubs in both per-file test fakes.
- **A prerequisite correctness fix** to `StoreKit2Backend.toIapPurchase`'s `isAutoRenewing` (§3.2).
- One new event: `subscription_state` (§5).
- Wiring: `IapHelper.restorePurchases()` feeds a complete snapshot set; the post-purchase path feeds
  an incomplete one.

**Out of scope (explicitly):**
- **Any server-side component.** Everything that happens while the app is closed — expiry, renewal,
  billing failure, grace entry/exit, refund approval, `REFUND_REVERSED`, and every App Store Server
  Notification *subtype* (`EXPIRED/VOLUNTARY` vs `EXPIRED/BILLING_RETRY`,
  `DID_RENEW/BILLING_RECOVERY`, `SUBSCRIBED/INITIAL_BUY` vs `/RESUBSCRIBE`) — is invisible to an
  on-device layer. A user who churns and never reopens the app is invisible to this SDK, by
  construction. This is a real, structural limitation, not a gap to be closed later in Swift; it
  needs an App Store Server Notifications V2 endpoint, which is a backend project. Recorded in §7.
- **Dunning/win-back UI, copy, or scheduling.** Same "SDK doesn't draw UI" boundary as
  `PaywallManager` and `IapHelper.purchase`. This layer answers *whether* a notice is due; the host
  app decides what it looks like and when it's shown.
- **Native StoreKit win-back offers** (`RenewalInfo.eligibleWinBackOfferIDs`,
  `Product.SubscriptionInfo.winBackOffers`) — both are **iOS 18.0+**, above this package's iOS 15
  floor. `isWinBackEligible` stays the home-grown device-local heuristic Android uses. Flagged in §7
  as the natural first thing to add if the floor ever rises.
- **Subscription *offer* inspection** (intro/promo/code eligibility). Reachable on iOS 15 only
  through the deprecated `offerID`/`offerType` accessors; no caller needs it in this phase.
- **`Product.SubscriptionInfo.Status.updates` as a live listener.** Deferred deliberately — see
  §3.6; this phase reconciles on explicit sync points, exactly as Android does.
- **Subscription group modeling.** `Status` carries no group id on iOS 15 (`Status.all` is iOS 17+),
  and nothing in this phase needs group-level rollups.
- **`RecurringGrants`' ownership OR-ing** (Android's `Entitlements.isActive(id) ||
  stateFor(id) == ACTIVE`) — `RecurringGrants` is not ported yet; that wiring belongs to its phase.

## 2. Dependencies On Existing Code

- `StoreBackend` — extended with one new method; the seam that keeps StoreKit out of `MonetizationBrainCore`.
- `IapHelper` — the only producer of snapshots; gains two call sites (§6).
- `BrainKeyValueStore`/`UserDefaultsBrainStore` — persistence, exactly as `Entitlements` uses it.
- `EventSink`/`SdkEvent`/`UserProperty` — the `subscription_state` event.
- `IapPurchaseState` (`IapModels.swift`, Phase 4) — reused unchanged by `SubscriptionSnapshot`? **No** — see §3.1.

## 3. Deviations From Android (with rationale)

### 3.1 Classify from StoreKit's native renewal state, not from two booleans — the core decision

Android's entire class exists to compensate for what Google Play Billing does **not** answer
on-device. Its own KDoc says so. It derives five states from exactly two facts (`purchaseState`,
`isAutoRenewing`), and that constraint produces three approximations that this port must not
inherit:

| Android mechanism | Why it exists on Android | What iOS actually has |
|---|---|---|
| `PENDING` ⇒ `IN_GRACE_PERIOD` | Play exposes no grace state locally | `RenewalState.inGracePeriod`, a first-class value, plus `gracePeriodExpirationDate` |
| Absence-from-snapshot ⇒ `LAPSED` | Play exposes no expiry reason locally | `RenewalState.expired` + `.revoked`, plus a 5-valued `ExpirationReason` |
| Local `history` set, "once in never out" | No account-level truth on-device | Still needed (see §3.4), but only for win-back, not for lapse detection |

Worse, Android's `PENDING` overload is semantically unsound on its own platform: the same enum value
means "billing retry" for a subscription and "awaiting parental approval, never to be granted" for a
one-time purchase. Porting that would be porting a known wart.

**Decision:** `SubscriptionSnapshot` carries a `SubscriptionRenewalState` — an owned mirror of
StoreKit's `Product.SubscriptionInfo.RenewalState` — not an `IapPurchaseState`. The classifier is
then a near-total function of a value the platform states authoritatively, rather than an inference.

**`RenewalState` is a `struct`, not an `enum`.** Apple can add values without an API break, so the
owned mirror gets an explicit `case unknown` and every mapping site is written fail-closed: an
unrecognized `rawValue` maps to `.unknown`, which **never grants entitlement and never produces a
transition**. This is also why `SubscriptionRenewalState` is a Swift `enum` while its StoreKit
source is not — the mapping boundary in `StoreKit2Backend` is precisely where the open-ended set
gets closed.

### 3.2 `StoreKit2Backend.isAutoRenewing` is currently wrong, and this phase must fix it first

[`StoreKit2Backend.swift:136`](../../../Sources/MonetizationBrainStoreKit/StoreKit2Backend.swift#L136)
sets `isAutoRenewing: transaction.productType == .autoRenewable`. That reports the *product type*,
not renewal intent: a subscription the customer cancelled but is still entitled to has
`productType == .autoRenewable` and `willAutoRenew == false`, and is reported as auto-renewing.

The value lives in `Product.SubscriptionInfo.RenewalInfo.willAutoRenew` (iOS 15.0), which
`StoreKit2Backend` does not consult anywhere today. Ported naively, Android's
`PURCHASED && isAutoRenewing ⇒ ACTIVE` / `PURCHASED ⇒ CANCELLED` branch would never once take the
`CANCELLED` path on iOS.

**Decision:** fix it as Task 1, before any lifecycle code exists. `IapPurchase` is a Phase 4 public
type whose `isAutoRenewing` field is already documented as renewal intent — this is a defect fix,
not a behavior change, and it stands on its own merits independent of this phase.

**The fix cannot carry an automated test, and that is a known cost, not an oversight.**
`MonetizationBrainStoreKit` has no test target, and StoreKit's `Transaction`/`RenewalInfo` cannot be
constructed in a test — testing it would need either a `.storekit` configuration running under
XCTest-in-simulator (this package's suite is plain `swift test` with zero real I/O, by design since
Phase 1) or a further protocol indirection inside the adapter. Neither is in scope. The fix is
verified by type-checking only, exactly as the README already states for this whole target. §8
records this rather than listing a test that cannot be written.

### 3.3 Seven states, not five — `billingRetry` is a first-class state, and grace-expiry is not terminal

Apple's own notification contract: a subscription that exits the billing grace period **continues in
billing retry** (`GRACE_PERIOD_EXPIRED`), losing entitlement but remaining recoverable — it resolves
to either `.subscribed` (billing recovery) or `.expired`. Android has no state for this at all: with
only `PENDING`/`PURCHASED` to work from, a subscription in retry simply disappears from the snapshot
set and gets inferred as `LAPSED`.

A state machine that treats grace-expiry as terminal **hard-churns users who are still recoverable**
— it stops dunning exactly when dunning still has a chance to work, and marks them win-back-eligible
while Apple is still trying to charge the card.

**Decision:** the ported state set is:

| `SubscriptionState` | Entitled? | Derived from |
|---|---|---|
| `.none` | — | never seen, or feature disabled |
| `.active` | Yes | `.subscribed` && `willAutoRenew` |
| `.cancelled` | **Yes, until expiry** | `.subscribed` && `!willAutoRenew` |
| `.inGracePeriod` | **Yes** | `.inGracePeriod` |
| `.inBillingRetry` | No | `.inBillingRetryPeriod` |
| `.expired` | No | `.expired` |
| `.revoked` | No | `.revoked` (refund or Family Sharing removal) |

`.cancelled` is **entitled** — this is the most common porting mistake off Play, and it is the
reason `.cancelled` is a distinct state rather than a flavor of churn. `.revoked` is kept separate
from `.expired` rather than collapsed (Android has no way to tell them apart) because a refund and a
lapse call for opposite host-app responses.

**No `.paused` case.** Play's `SUBSCRIPTION_STATE_PAUSED` has no StoreKit analog whatsoever —
verified by exhaustive enumeration of `SubscriptionInfo`, `RenewalInfo`, `RenewalState`,
`Transaction`, and the App Store Server API's own 1–5 `status`. Apple has no user-initiated pause.
Adding an unreachable case so the enum "matches Android" would be dishonest modeling; the iOS mapper
is total without it. Same for Play's prepaid `PENDING`. Recorded in §7 so the absence is documented,
not silent — which is how Android handles the *prepaid* ambiguity, but notably **not** how it
handles paused (Android silently collapses paused into `CANCELLED` and does not list it among its
own documented limitations).

### 3.4 Lapse is observed, not inferred — but the `history` set survives, scoped down

Android infers `LAPSED` from absence: any product in the permanent `history` set that is missing
from a `complete` snapshot set is churned. That inference is what makes its `UNSPECIFIED` handling
dangerous (§3.7).

On iOS, `.expired` and `.revoked` arrive as stated values, so absence-inference is not needed for
lapse detection and is **not ported**. The `history` set is still kept, for one narrower job:
win-back eligibility needs "was this player ever subscribed?", which no single current snapshot
answers. Its known weakness (device-local, lost on reinstall, so a genuine returning churned user
looks like `.none`) is inherited and documented rather than fixed — fixing it needs account-level
truth, i.e. a server.

Absence from a `complete` set therefore maps to `.none`-by-omission (no transition, state retained),
not to `.expired`. A product that genuinely lapsed will say so.

### 3.5 `dunningNoticeDue` is split into a command and a query

Android's `dunningNoticeDue(productId)` increments and persists a counter **on every call** — a
query with a hidden side effect. Calling it twice to render two surfaces silently burns a notice;
calling it in a debug inspector corrupts the cadence.

**Decision:** split into `noteForegroundOpen(productId:)` (mutates, persists) and
`dunningNoticeDue(productId:) -> Bool` (pure). The host app calls the first once per foreground, and
the second as often as it likes. The cadence semantics (`[1, 2, 4]` = the 1st, 2nd and 4th
foreground opens) are preserved exactly.

**Extended to cover `.inBillingRetry` as well as `.inGracePeriod`,** per §3.3: both are recoverable
payment-problem states, and stopping dunning at grace-expiry abandons a recoverable user. The
episode-reset rule (leaving the dunning window clears the counter, so the next episode restarts at
1) is preserved, and now triggers on leaving *either* state for a non-dunning one, rather than on
leaving grace specifically.

### 3.6 Reconcile on sync points; no `Status.updates` listener in this phase

Apple does not document which transitions `Status.updates` fires on, whether it emits an initial
value, or whether it coalesces — the page says only "emits status information when a subscription's
status changes." Building a listener on an undocumented emission contract, in a phase that has no
way to integration-test against the real App Store (this package's whole suite is `swift test` with
zero real I/O), would be speculative.

Moreover, a listener cannot solve the problem it looks like it solves: **expiry is time-based**.
Nothing "happens" at the moment a subscription lapses if the app isn't running, so a reconcile on
foreground is required regardless of whether a listener exists.

**Decision:** classify at explicit sync points (`restorePurchases()`, post-purchase), exactly as
Android does. Adding `Status.updates` as a *supplementary* delta channel is recorded in §7. The
`StoreBackend` method added here is a poll (`async throws -> [SubscriptionSnapshot]`), which matches
`currentEntitlements()`'s existing shape and its interrupted-fetch contract.

**One deliberate asymmetry with `currentEntitlements()`:** that method degrades per item, while
`subscriptionSnapshots()` throws the whole call if any single group's status fetch fails. That is
the correct reading of the contract rather than an inconsistency — a silently partial set is the
exact hazard the interrupted-fetch contract exists to prevent, and here a missing product means
"no transition" rather than "revoke", so signalling interruption costs nothing and guessing costs
correctness.

### 3.7 `.unknown` cannot freeze a product's state

Android has a real bug here, and it is subtle: an unclassifiable snapshot is skipped via
`?: continue`, so no transition happens — but because the product id **is** still present in the raw
snapshot list, the absence-based `LAPSED` inference also skips it. A formerly-`ACTIVE` product that
starts reporting `UNSPECIFIED` is therefore stuck in its last known state indefinitely. No test
covers it; the two existing `UNSPECIFIED` tests only exercise a never-before-seen product.

This port is structurally immune (it does no absence-inference, §3.4), but the underlying question
still needs an answer: what happens when StoreKit reports a `rawValue` this SDK's enum doesn't know?

**Decision:** `.unknown` produces **no transition and no entitlement**, and the prior state is
retained — the same outcome Android has, but reached deliberately and *tested*, with an explicit
test asserting that an `.active` product reporting `.unknown` stays `.active` rather than silently
lapsing. Retaining is the fail-safe direction here: the alternative (treating unknown as churn)
would revoke a paying customer's state on an Apple-side addition this SDK simply hasn't learned yet.

### 3.8 Parser strictness is made consistent; `clear()` does not become a public footgun

Two Android parser/API faults are corrected rather than ported:

- **Lenient/strict asymmetry.** Android rejects the whole config for a malformed `dunning_cadence`
  element, but silently defaults `enabled`/`win_back_enabled` to `true` when they're present with
  the wrong type (`"enabled": "false"` as a string reads as `true`). This port follows this
  codebase's own established rule instead: a wrong-typed field degrades to a safe default **and
  appends a diagnostic note**, never silently (the rule Phase 6's security review forced onto
  `eligible_countries` after it was found failing open with no note).
- **`clear()` is public and unguarded on Android**, documented only as "Reset (test)" — any host
  code can call it in production, wiping persisted state *and* resetting `enabled` back to `true`,
  silently re-enabling a feature ops had disabled via remote config. This port exposes
  `resetForTest()` as **internal**, matching `Entitlements`/`ConsentState`/`AdsBrain`.

### 3.9 The `subscription_state` event drops `screen_name`

Android's payload includes `screen_name`, read from a global mutable `ScreenName.name` (default
`"unknown"`). No such ambient global exists in this port, and introducing one to match a wire field
would import a mutable-global-state pattern this package has been actively removing. The field is
dropped; `expiration_reason` is added in its place when the state is `.expired` and StoreKit stated
a reason (§5).

## 4. Type Definitions

```swift
/// Owned mirror of StoreKit's `Product.SubscriptionInfo.RenewalState`. That type is a `struct`
/// with static members, not an enum — Apple can add values without an API break — so `.unknown`
/// is load-bearing, not defensive padding. See design spec §3.1.
public enum SubscriptionRenewalState: String, Sendable, Equatable, CaseIterable {
    case subscribed, expired, inBillingRetryPeriod, inGracePeriod, revoked, unknown
}

/// Why a subscription expired. Mirrors `RenewalInfo.ExpirationReason` (iOS 15.0, also a struct).
public enum SubscriptionExpirationReason: String, Sendable, Equatable, CaseIterable {
    case autoRenewDisabled, billingError, didNotConsentToPriceIncrease, productUnavailable, unknown
}

/// One subscription's state as the store reports it right now. The input to `observe(_:)`.
public struct SubscriptionSnapshot: Sendable, Equatable {
    public let productId: String
    public let renewalState: SubscriptionRenewalState
    public let willAutoRenew: Bool
    /// Epoch ms, matching `IapPurchase.purchaseTimeMs`'s convention — never a `Date`. `0` when unknown.
    public let expirationMs: Int64
    public let gracePeriodExpirationMs: Int64
    public let expirationReason: SubscriptionExpirationReason?
}

/// The classified operational state. See §3.3 for why `.cancelled` is entitled and why there is
/// no `.paused`.
public enum SubscriptionState: String, Sendable, Equatable, CaseIterable {
    case none, active, cancelled, inGracePeriod, inBillingRetry, expired, revoked

    /// Whether the customer should receive the service right now.
    public var isEntitled: Bool { self == .active || self == .cancelled || self == .inGracePeriod }

    /// Whether a payment problem is being actively retried — the dunning window (§3.5).
    public var isRecoverable: Bool { self == .inGracePeriod || self == .inBillingRetry }
}

public struct SubscriptionTransition: Sendable, Equatable {
    public let productId: String
    public let from: SubscriptionState
    public let to: SubscriptionState
}

public struct SubscriptionApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String
}
```

**Remote config** — key `subscription_lifecycle_ios` (mirroring Android's `_android` suffix), read
by the host and handed to `apply(_:)`:

```json
{
  "version": "sub_lifecycle_v1",
  "enabled": true,
  "dunning_cadence": [1, 2, 4],
  "win_back_enabled": true
}
```

Parse rules, following `PurchaseRouter.apply`/`PaywallManager.apply` structurally:
- `version` — required non-blank `String`; missing ⇒ whole config rejected, last-good kept running.
- `enabled` / `win_back_enabled` — optional `Bool`, default `true`; **wrong type ⇒ default + a note** (§3.8).
- `dunning_cadence` — optional array; not an array ⇒ reject; a non-numeric element ⇒ reject;
  elements `< 1` dropped with a note; result `sorted().distinct()`; **empty is valid** and means
  "never send a dunning notice" (matching Android). Every element is clamped to a sane
  `[1, 1000]` open-count range, per this codebase's never-trust-config-numerics rule.
- Malformed JSON overall ⇒ reject with the reason in `report`.
- Config fields are assigned **only on full success**, under one lock acquisition.

**Persistence** — `BrainKeyValueStore`, pipe-delimited strings, matching `Entitlements`:
`sub_lifecycle_states` (`productId:STATE|…`), `sub_lifecycle_history` (`productId|…`),
`sub_lifecycle_dunning` (`productId:count|…`).

## 5. Locked Event Wire Shape

### `subscription_state` (new)

Emitted **only on a real transition** (`from != to`), one per transition.

| Field | Type | Notes |
|---|---|---|
| `product_id` | string | |
| `from_state` | string | `SubscriptionState.rawValue` |
| `to_state` | string | `SubscriptionState.rawValue` |
| `lifecycle_version` | string | active config version |
| `expiration_reason` | string | **only** when `to_state == "expired"` and a reason was stated |

Consent: **not gated**, matching `iap_purchase`/`web_checkout_opened`/`paywall_impression` — this is
revenue/operational telemetry, not personalization or decision logging. Payload is a pseudonymous id
plus product identifiers and enums; no PII, no receipt/JWS value, no price.

## 6. Wiring Into `IapHelper`

`SubscriptionLifecycle` is injected via `IapHelper.Configuration` with a `.shared` default, matching
`PurchaseRouter.Configuration.iapHelper` — never reached for inline, so tests get a fresh instance.

- `restorePurchases()` — after the existing `Entitlements.sync(...)`, fetch
  `subscriptionSnapshots()` and call `observe(_, complete: true)`. A thrown error (including
  `CancellationError`) skips the classification pass rather than classifying a partial set.

  **The caller's obligation is identical to `currentEntitlements()`'; the stakes are not**, and the
  distinction matters because overstating it makes the code look load-bearing when it is insurance.
  There, a swallowed cancellation revokes live entitlements — the Phase 4 Critical finding. Here,
  §3.4 makes absence "no news", so `observe([])` writes nothing and revokes nothing: swallowing
  would be *useless*, not destructive. The skip is kept because it is nearly free, keeps one reading
  of the contract across both fetches, and stays correct if absence-inference is ever reintroduced
  — not because something breaks today without it.

  Consequently the skip is **held by review, not by an assertion**: it is observationally
  indistinguishable from `catch { snapshots = [] }` through `SubscriptionLifecycle`'s public
  surface, confirmed by mutation-testing (that substitution breaks zero tests). §8's requirement is
  written accordingly. A failed fetch does not affect `restorePurchases()`' return value — the two
  fetches are independent and the entitlement answer is still good.

- Post-purchase — after a successful **subscription** purchase, refresh the lifecycle for that
  product with `complete: false`, so a win-back buyer leaves `.expired` immediately rather than
  waiting for the next restore.

  **The snapshot is re-queried from the backend, never fabricated from the `IapPurchase` in hand.**
  This is the load-bearing decision on this path, and the spec has to state it because the call site
  looks like it already holds a snapshot and does not. `IapPurchase` carries no renewal state, so
  fabricating one means inventing `.subscribed` — precisely the inference §3.1 exists to eliminate —
  and taking `willAutoRenew` from `IapPurchase.isAutoRenewing`, which `renewalIntent(for:)`
  fail-safes to `false` on every path it cannot trust. A genuinely-renewing new subscriber whose
  renewal info was momentarily unverified would then be classified `.cancelled`: a stated wrong
  fact, which is worse than a missing one. Cost: one extra store round trip awaited inline on the
  purchase path, and if it throws the buyer stays `.expired` until the next restore — a delayed
  correct classification beats an immediate wrong one.

  **Placement:** in the single funnel every successful transaction passes through, **outside** the
  `orderId` dedupe (that guard exists to stop revenue double-counting; classification is not revenue
  and is idempotent, and a redelivered renewal is exactly when the lifecycle should refresh) and
  **after** `finish(_:)` (preserving the file's report-before-finish invariant rather than widening
  the crash-to-redelivery window with a store round trip).

## 7. Known Limitations (documented, not fixed)

- **Everything that happens while the app is closed is invisible.** Structural; needs App Store
  Server Notifications V2 on a backend. This bounds what the layer can claim: it is correct about
  "is this customer entitled right now, while they are looking at the app," and cannot be correct
  about churn analytics, dunning-recovery attribution, or refund reaction.
- **`history` is device-local** — reinstall loses it, so a returning churned user reads `.none`
  rather than `.expired` and is not win-back-eligible.
- **Refund vs. Family Sharing revocation are indistinguishable on iOS 15.** Both set
  `revocationDate`; `RevocationReason` offers only `.developerIssue`/`.other`, and the
  disambiguating `RevocationType` is iOS 26.4.
- **Native win-back offers are iOS 18+** — `isWinBackEligible` stays a home-grown heuristic.
- **`Status.updates` as a delta channel** — deferred, §3.6.
- **Grace-period availability is an App Store Connect opt-in.** If the studio hasn't enabled it,
  `.inGracePeriod` simply never occurs and dunning starts at `.inBillingRetry`. No code change
  needed; noted so the absence isn't read as a bug.
- **Snapshot discovery is bounded by `Transaction.all`.** `subscriptionSnapshots()` can only report
  subscription groups that appear in the device's transaction history, which is empty on a fresh
  install until the App Store syncs — so an early call can legitimately return `[]` for a real
  subscriber. §3.4's absence-is-not-churn rule is what makes this safe rather than destructive: an
  empty result produces no transitions and revokes nothing.
- **The owned `SubscriptionRenewalState` name shadows an Apple type.** StoreKit declares a
  top-level `typealias SubscriptionRenewalState` (iOS 18.4), so inside `MonetizationBrainStoreKit`
  — the one target importing both — the bare name is ambiguous and fails to compile. Every
  reference there must be qualified `MonetizationBrainCore.SubscriptionRenewalState`. Recorded
  because it is a trap for future work in that target, not a defect in either type.

## 8. Testing Requirements

Following the 15–35-tests-per-subsystem norm for a subsystem of this risk profile:
- Every state classification, including both `.subscribed` branches (`.active` vs `.cancelled`).
- `.unknown` retains prior state and emits no transition (§3.7) — explicitly including the
  `.active` → `.unknown` case Android leaves untested.
- Grace → billing retry is **not** terminal, and dunning continues across it (§3.3/§3.5).
- Dunning cadence: notice due on opens 1/2/4; command/query split does not double-count; episode
  reset on leaving the dunning window.
- Every config reject path keeps the last-good config; every wrong-typed field produces a note.
- `subscription_state` emitted once per transition, never on a no-op re-observe.
- `restorePurchases` with a throwing `subscriptionSnapshots()` classifies nothing, does not
  propagate, and does not cost the entitlement sync that already succeeded (§6). Note what this
  test can and cannot prove: §3.4 makes "classified nothing" and "classified an empty set"
  observationally identical, so the assertion covers the propagation and the sync, while the skip
  itself is held by review. Stated here rather than left as an implied guarantee.
- A successful subscription purchase refreshes lifecycle state; a consumable purchase does not.

**Not testable in this package, and recorded as such rather than claimed:** everything in
`StoreKit2Backend` — the `isAutoRenewing` fix, the `RenewalState`/`ExpirationReason` mappings, the
unverified-payload handling, the epoch-ms conversion, and the Family Sharing reduction. See §3.2.
The Family Sharing reduction is the one piece of genuinely non-obvious logic with no coverage; it
operates purely on owned value types, so if it ever earns a test, the move is to lift it into
`MonetizationBrainCore` rather than to stand up a simulator-bound test target.
