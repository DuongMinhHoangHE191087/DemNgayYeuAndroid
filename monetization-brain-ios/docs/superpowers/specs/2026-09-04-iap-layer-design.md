# Phase 4 "IAP Layer" — Design Spec

**Status:** approved (standing autonomous-port mandate — see `monetization-brain-ios-autonomous-mandate`
memory; no per-phase design checkpoint). **Payment/financial code** — per the user's own global
coding-style rules, a `security-reviewer` pass is mandatory before this phase merges, in addition
to the whole-branch review every phase already gets.

## 1. Scope

Port Android's base in-app-purchase layer — the code introduced in commit `f710795`
("Monetization Brain + IAP layer + single entry point"), NOT the later multi-channel router
(`7e16ee8`), subscription lifecycle (`4e9610e`), battle pass, offerwall, or any paywall UI. Source:
`com.app.plugin.iap.{IapHelper,IapModels,Entitlements,FxRates}` (Kotlin, ~790 lines combined) plus
the `AdsBrain.onPurchase` hook this layer calls into (already ported, Phase 3.5).

**In scope:**
- `IapModels`-equivalent: `IapProduct`, `IapPurchase`, `IapResult`, `IapProductType`,
  `PurchaseChannel`, `IapPurchaseState` — public types that hide every StoreKit type from the
  SDK's own public API, exactly matching Android's own stated reason for `IapModels.kt`'s
  existence (a published artifact, and the vendor API has broken its shape once already).
- `StoreBackend`: a NEW protocol abstraction (no Android equivalent — Android wraps
  `BillingClient` directly inside `IapHelper`) so the orchestration logic is unit-testable
  without a real App Store connection, matching this codebase's `RemoteConfigProvider`/
  `BrainKeyValueStore` precedent.
- `Entitlements`-equivalent: local owned-product state, `sync()`-replaces-not-unions semantics
  (the exact mechanism that lets a cancelled subscription lose entitlement on next launch).
- `FxRates`-equivalent: local-currency → USD conversion, `nil` on unknown currency rather than
  guessing — same contract, same rationale, as Android's `FxRates.kt`.
- `IapHelper`-equivalent (same class name, for cross-platform grep-ability — matches this
  project's established naming precedent: `AdsBrain`, `GuardrailEngine`, `DecisionEngine`, etc.
  all keep Android's literal type names): the orchestrating facade — configure/init,
  `queryProducts`, `purchase`, `restorePurchases`, a purchase-updates stream, finalize
  (acknowledge/consume-equivalent), `iap_purchase` event emission, and the outbound call into
  `AdsBrain.shared.onPurchase(usd:)`.
- A new SPM target, `MonetizationBrainStoreKit`, containing the ONE real `StoreBackend`
  conformance built on StoreKit 2 (`Product`/`Transaction`/`Transaction.updates`) — mirrors
  `MonetizationBrainUMP`'s existing isolation of a platform/vendor framework import away from
  `MonetizationBrainCore`. Unlike UMP (an external SPM package dependency), StoreKit 2 is a
  first-party Apple framework requiring no new `Package.swift` dependency entry — just a new
  target with `import StoreKit`.
- The payer-guardrail invariant Android's own doc 45 §D.2 documents as a real, previously-shipped
  bug fix: "has this player ever paid" must be judged by a monotonically-incrementing purchase
  COUNT (`PlayerState.purchaseCountLifetime`, already ported, Phase 2), never by an FX-dependent
  USD total — this phase must not regress that invariant when wiring `onPurchase`.

**Out of scope, deferred to later phases (each already flagged in the Android source itself, or a
direct consequence of Android's own later-commit split):**
- **Multi-channel purchase routing** (`PurchaseRouter.kt`, commit `7e16ee8`) — web-shop checkout,
  channel selection by config. iOS Phase 4 supports exactly one channel (App Store via StoreKit
  2); `PurchaseChannel` keeps a `webShop` case for forward-compat (matching Android's own
  forward-compat pattern) but nothing in this phase ever produces it.
- **Subscription lifecycle** (`SubscriptionLifecycle.kt`, commit `4e9610e`) — dunning, win-back,
  state classification beyond "currently entitled or not." This phase reports whatever
  entitlement state StoreKit 2 itself reports (including its grace-period semantics, see §3.2)
  but does not build any dunning/win-back campaign logic on top.
- **`RecurringGrants`/`BattlePass`/offerwall** — separate later phases per Android's own file
  layout.
- **Paywall UI** — Android's own `IapHelper.kt` doc comment is explicit: "Không dựng paywall. Đó
  là Phase 1-2 của doc 23, một lớp riêng" (does not build a paywall; that is doc 23's own Phase
  1-2, a separate layer). The host app owns purchase-initiation UI entirely; this phase's
  `purchase(_:)` method is the same "answer the API call, don't render anything" contract
  `AdsBrain`'s decision points already establish.
- **`shouldOfferIap`/`chooseCrossModal` coupling** — confirmed (research pass) that Android's
  base IAP layer has ZERO references to `AdsBrain`'s decision-point methods from inside `iap/`;
  the only coupling direction is IAP → brain (`IapHelper.reportPurchase()` calls
  `AdsBrain.onPurchase(usd)`), never brain → IAP. This phase preserves that one-directional
  coupling exactly and adds no new brain-side changes.
- **Server-side receipt verification deploy** — the SDK sends a verifiable receipt (the signed
  JWS transaction, see §3.3) to the backend via the existing event pipeline; standing up an
  App Store Server API verification endpoint on the backend (Android's parallel is
  `backend/app/play_verify.py`) is explicitly a backend-team concern, not part of this SDK port,
  matching how this project has never ported any Android backend code.
- **`Entitlements`' bridge to an iOS ad-control facade** — Android's `Entitlements.kt` is the
  sole caller of `AdsHelper.setRemoveAds()` (an ad-serving layer this iOS port hasn't built yet —
  ads are entirely out of scope so far; every phase to date is the decision-engine/brain side,
  never actual ad rendering). This phase's `Entitlements` type still computes `hasRemoveAds()`
  correctly and exposes it publicly, but there is no `setRemoveAds()`-equivalent call to make
  yet — documented as a dangling bridge point for a future ad-serving phase, not a gap in THIS
  phase's own logic.
- **AppsFlyer revenue reporting.** Android's `reportPurchase()` sends every successful purchase
  to THREE places, not two: the `iap_purchase` event (in scope), `AdsBrain.onPurchase()` (in
  scope), and `AppsflyerHelper.instance.logIapPurchase(...)` for ROAS/UA-campaign attribution
  (Android's own comment: "missing this step means every campaign's ROAS only has the ad-spend
  side, and nothing errors to say so"). No AppsFlyer (or any attribution SDK) wrapper exists in
  this iOS port yet — the same reason `Entitlements`' `AdsHelper` bridge is deferred (§ below).
  `IapHelper.reportPurchase`'s Core-side implementation must still call the `iap_purchase` event
  and `AdsBrain.onPurchase` unconditionally regardless of this gap (each of Android's three
  reporting calls is independently try/caught — one failing or being absent must never block the
  other two), and should leave a clearly-marked call site (a doc comment, not a stub method) for
  a future attribution-bridge phase to fill in, matching how `Entitlements`' `AdsHelper` bridge
  point is handled below.
- **`StoreKitTest`/`SKTestSession` integration testing** against a real `.storekit` configuration
  file — this project's entire test suite runs via plain `swift test` (SPM), with zero real I/O
  in any test so far (every phase to date uses in-memory fakes for every I/O boundary:
  `BrainKeyValueStore`, `EventSink`, `RemoteConfigProvider`). `MonetizationBrainStoreKit`
  (the real StoreKit 2 adapter target) gets minimal-to-no automated test coverage for the same
  reason `MonetizationBrainUMP` (the real Google UMP wrapper) does not carry exhaustive tests —
  it is a thin, type-checked conformance to `StoreBackend`, verified by construction; the
  exhaustive test suite lives entirely against the protocol in `MonetizationBrainCore`, using an
  in-memory fake `StoreBackend`.

## 2. Architecture

```
MonetizationBrainCore (existing target, no new import)
├── IapModels.swift          (new: IapProduct/IapPurchase/IapResult/enums)
├── StoreBackend.swift        (new: protocol — the ONLY thing standing between IapHelper and a real store)
├── FxRates.swift             (new: enum namespace, mirrors HoldoutManager's shape)
├── Entitlements.swift        (new: final class, NSLock-protected owned-product set)
└── IapHelper.swift           (new: final class, orchestrates the above + calls AdsBrain.shared.onPurchase)

MonetizationBrainStoreKit (new target, `import StoreKit`)
└── StoreKit2Backend.swift    (new: the ONE real `StoreBackend` conformance, StoreKit 2-backed)
```

`IapHelper` never imports `StoreKit` — every store interaction goes through the injected
`StoreBackend`. A host app links `MonetizationBrainStoreKit` for the real conformance; a test
constructs an in-memory fake conformance directly against `MonetizationBrainCore` alone, exactly
mirroring how `DecisionEngine`'s tests never touch a real `UserDefaults`-backed
`BrainKeyValueStore`.

`IapHelper` is configured independently of `AdsBrain` (Android's `IapHelper`/`AdsBrain` are
independent singleton objects too, coupled only by the one `onPurchase` call) — this phase does
NOT merge `AdsBrain.Configuration` and `IapHelper.Configuration` into one type. A host app's own
`MonetizationSdk.configure()`-equivalent (not part of this SDK; the host app's own integration
code) calls both facades' `configure(_:)` separately, matching Android's own documented
integration order (doc 45 §C: `Entitlements.setRemoveAdsProducts()` → `IapHelper.setProducts()` →
`IapHelper.init()`, entirely independent of `AdsBrain.configure()`'s own call).

## 3. Key deviations from the Kotlin source (all deliberate, all documented in code)

1. **`StoreBackend` protocol is new infrastructure with no Android equivalent.** Android wraps
   `BillingClient` directly inside `IapHelper` because Kotlin/Android test tooling for billing
   (Robolectric + fake `BillingClient` shadows) works differently from this project's own
   established Swift testing style (in-memory fakes behind a protocol, zero real I/O in any
   test). Introducing this seam is the SAME architectural move Phase 1 already made for
   `RemoteConfigProvider` and Phase 3 made for `BrainKeyValueStore` — not a new pattern, an
   application of an existing one to a third domain.
2. **Acknowledge/consume collapses to one `finish(_:)` call.** Google Play Billing has two
   distinct post-purchase finalization calls (`acknowledgePurchase` for non-consumables,
   `consumeAsync` for consumables) with different semantics and a 3-day auto-refund clock on an
   unacknowledged non-consumable. StoreKit 2 has exactly one — `Transaction.finish()` — for
   every product type, and carries no auto-refund clock; an unfinished transaction instead
   persists indefinitely in `Transaction.updates`, reappearing every launch until finished. Both
   platforms therefore share the SAME underlying rule ("finalize every transaction you've
   handled, or it comes back to bite you"), just via a different mechanism and a different
   failure mode if skipped. `IapHelper.finish(_:)` calls `backend.finish(purchase)` unconditionally
   for every purchase this layer successfully processes — no consumable/non-consumable branch
   needed on the Swift side, because StoreKit 2's `finish()` needs none.
3. **`IapProductType` gets 4 cases, not Android's 2 + a separately-declared consumable set.**
   Android's `IapProductType` is `{INAPP, SUBS}`, with consumability a SEPARATE fact the host app
   must declare via `setConsumableProducts(Set<String>)` — Google Play's `ProductDetails` API
   does not itself say whether a one-time product is consumable, and Android's own doc comment
   explains why it refuses to guess ("guessing wrong toward consume permanently deletes a paying
   user's entitlement"). StoreKit 2's `Product.ProductType` DOES report this directly:
   `.consumable`, `.nonConsumable`, `.autoRenewable`, `.nonRenewing`. iOS's `IapProductType`
   mirrors StoreKit 2's real 4-case type instead of re-deriving Android's coarser 2-case
   split — a case where the source platform's own type system removes a whole category of
   app-declaration risk Android had to design around. `isSubscription` (needed for the
   `iap_purchase` event's `is_subscription` field, to stay wire-compatible with Android's
   backend) is computed as `type == .autoRenewable || type == .nonRenewing`, not stored
   separately. No `setConsumableProducts`-equivalent configuration step exists on iOS.
4. **Purchase token becomes the signed JWS transaction string, not an opaque token.** Android's
   `purchaseToken` is an opaque string that REQUIRES a server round-trip to Google's
   `androidpublisher` API to learn anything about (Android's own backend, `play_verify.py`, does
   this lookup). StoreKit 2's transactions are self-verifying: `Transaction.jwsRepresentation`
   is a signed JSON Web Signature the backend (or the client itself, via
   `VerificationResult<Transaction>`) can cryptographically verify WITHOUT calling Apple at all
   for the base verification — Apple's public key chain is embedded in the JWS. Server-side
   deeper checks (refund status, subscription renewal state) still benefit from the App Store
   Server API, but the trust model is fundamentally different from Play's "opaque token, must
   ask Google" model. `IapPurchase.purchaseToken: String` on iOS carries the JWS representation —
   same field name and same wire-event key (`purchase_token`) as Android for backend
   cross-platform compatibility, different content shape, explicitly documented in the field's
   doc comment so a future backend integrator doesn't assume Play's opaque-token semantics.
5. **`price_micros` is derived from StoreKit 2's `Decimal` price, not native to the platform.**
   Google Play reports price natively as integer micros (`priceAmountMicros: Long`). StoreKit 2
   reports price as `Decimal` (exact base-10, no floating-point rounding risk) plus a
   `currencyCode`. Conversion: `Int64((price as NSDecimalNumber).multiplying(byPowerOf10: 6).int64Value)`
   or equivalent exact `Decimal` arithmetic (never `Double` — converting through `Double` first
   would reintroduce the exact floating-point imprecision `Decimal` exists to avoid). This
   conversion is lossless for every real-world currency amount (no currency has sub-micro-USD-
   equivalent precision) and exists purely so the `iap_purchase` event's `price_micros` field
   stays wire-compatible with Android's backend contract — internally, nothing on iOS ever needs
   micros precision; `IapProduct`/`IapPurchase` could have carried `Decimal` throughout, but the
   event wire format is the one place cross-platform compatibility is non-negotiable (same
   reasoning as `is_holdout: .int(0/1)` in Phase 3, `chosen_action_mode` string values in Phase
   3.5 — a locked, byte-for-byte wire contract is never "cleaned up" independently per platform).
6. **PENDING-state handling is intentionally NOT a 1:1 port — StoreKit 2's grace-period concept
   is server-managed, not client-inferred.** Android's `IapHelper` special-cases Play's PENDING
   purchase state asymmetrically: never entitled for INAPP, but grace-period-entitled for SUBS
   (up to 30 days), because the Play Billing Library's client-side query directly exposes a
   `PENDING` enum value the client can branch on. StoreKit 2 has no equivalent client-visible
   enum value — a `Transaction` from `currentEntitlements`/`Transaction.updates` is either present
   (entitled) or absent; subscription grace-period/billing-retry state instead lives in
   `Product.SubscriptionInfo.RenewalInfo` (`renewalInfo.gracePeriodExpirationDate`,
   `renewalInfo.expirationReason`), which is a SEPARATE, server-synced query, not a purchase-flow
   state. This phase's `IapPurchaseState` keeps a `pending` case (for `Product.PurchaseResult
   .pending`, which DOES occur client-side for Ask-to-Buy/SCA flows — a real, distinct StoreKit 2
   concept, not invented) but does NOT attempt to replicate Android's INAPP/SUBS grace-period
   asymmetry, since the underlying mechanism it depended on doesn't exist on this platform in the
   same shape. A future subscription-lifecycle phase (mirroring Android's own later
   `SubscriptionLifecycle.kt` split) is the correct place to build real grace-period handling via
   `RenewalInfo`, once that phase exists — not bolted onto this base layer by approximation now.
7. **No `enableAutoServiceReconnection()`-equivalent exists, and none is needed.** Android's
   `BillingClient` is a bindable service connection that can be killed and must be reconnected;
   Play Billing 9.0's `enableAutoServiceReconnection()` exists specifically to stop hand-rolled
   reconnect loops from turning into battery-draining infinite retries. StoreKit 2's
   `Transaction.updates` is a persistent `AsyncSequence`, not a bindable connection with a
   lifecycle to manage — this entire category of Android complexity has no iOS counterpart to
   port, flagged explicitly so a future reviewer doesn't go looking for a "reconnection" method
   that was silently dropped rather than never needed.
8. **The payer-guardrail invariant is preserved by construction, not by new code.** Android's own
   doc 45 §D.2 documents a real historical bug: an early payer-protection guardrail checked
   `purchaseValueLifetimeUsd > 0.0`, which silently failed for any player whose currency had no
   known FX rate (their USD total stayed `0.0` even after paying). The fix was to check
   `purchaseCountLifetime > 0` instead. iOS never had this bug — `AdsBrain.onPurchase(usd:)`
   (Phase 3.5) already increments `purchaseCountLifetime` unconditionally, independent of
   whatever `usd` value `FxRates` produces (including `0.0` when the currency is unknown — see
   `RewardModel`), and `GuardrailEngine.evaluate`'s `PAYER_PROTECTION_RECENT` rule (Phase 3)
   already reads `state.purchaseCountLifetime > 0`, not a USD total. This phase's job is simply
   to NOT introduce a new call site that checks a USD total instead — `IapHelper.reportPurchase`
   must call `AdsBrain.shared.onPurchase(usd: fxRates.toUsd(...) ?? 0.0)`, exactly mirroring
   Android's own `usd ?: 0.0` fallback (never skip the call when FX is unknown; the purchase
   still counts).

## 4. Types (field-for-field, adapted per §3's deviations)

### `IapProductType` (enum, `Sendable, Equatable, CaseIterable`)
`case consumable, nonConsumable, autoRenewable, nonRenewing` (StoreKit 2's real 4-case type — see
§3.3). Computed `var isSubscription: Bool { self == .autoRenewable || self == .nonRenewing }`.

### `PurchaseChannel` (enum, `String, Sendable, CaseIterable`)
`case appStore = "APP_STORE"`, `case webShop = "WEB_SHOP"` (unused this phase — forward-compat
only, matching Android's own `PurchaseChannel.WEB_SHOP` which Android's OWN base-layer commit
also never produces; wiring belongs to a future purchase-router phase).

### `IapPurchaseState` (enum, `String, Sendable, CaseIterable`)
`case purchased = "PURCHASED"`, `case pending = "PENDING"`, `case unspecified = "UNSPECIFIED"` —
raw values kept as Android's exact enum-name strings for any future cross-platform log
comparison, even though this phase's event payload sends purchase STATE only implicitly (a
`Failed`/`Pending`/`Success` `IapResult`, not a raw state string field — see Android's own event
payload in §5, which has no `state` field either).

### `IapProduct` (struct, `Sendable, Equatable`)
```swift
public let productId: String
public let type: IapProductType
public let formattedPrice: String       // store-localized display string, e.g. "$4.99"
public let priceMicros: Int64           // derived from StoreKit 2's Decimal price, see §3.5
public let currencyCode: String
public let title: String
public let description: String
```
(No `offerToken` field — Android's exists because Play requires re-supplying a specific
subscription offer token at purchase time; StoreKit 2's `Product.purchase()` takes the `Product`
object itself, no separate offer-token concept for the base subscription case. A future
promotional-offers phase can add this if it becomes load-bearing.)

### `IapPurchase` (struct, `Sendable, Equatable`)
```swift
public let productId: String
public let orderId: String              // Transaction.id (or originalID for restored/renewed), stringified
public let purchaseToken: String        // Transaction.jwsRepresentation — see §3.4
public let purchaseTimeMs: Int64
public let state: IapPurchaseState
public let isFinished: Bool             // was `isAcknowledged` on Android — see §3.2, one finalize call not two
public let isAutoRenewing: Bool         // false for non-subscription types
public let quantity: Int
public let priceMicros: Int64           // joined in from the matching IapProduct at purchase time
public let currencyCode: String
public let channel: PurchaseChannel     // always .appStore this phase
```

### `IapResult` (enum, `Sendable, Equatable`)
```swift
case success(IapPurchase)
case pending(IapPurchase)
case userCanceled
case alreadyOwned
case failed(code: Int, message: String)
```
(No `.webCheckoutOpened` case — that's the purchase-router phase's concern, out of scope here.)

### `StoreBackend` (protocol, `Sendable`)
```swift
func products(for identifiers: [String]) async throws -> [IapProduct]
func purchase(_ product: IapProduct) async throws -> IapResult
func currentEntitlements() -> AsyncStream<IapPurchase>
func transactionUpdates() -> AsyncStream<IapPurchase>
func finish(_ purchase: IapPurchase) async
```
`currentEntitlements()` maps StoreKit 2's `Transaction.currentEntitlements` (a snapshot, used by
`restorePurchases`); `transactionUpdates()` maps `Transaction.updates` (the ongoing stream, used
by the purchase-listener). Both return `AsyncStream` rather than a throwing async sequence — a
stream that can't itself fail (individual malformed/unverifiable transactions are a per-item
concern the real conformance filters or logs, not a stream-level failure) matches this codebase's
existing preference for the simplest type that can't misrepresent the actual contract.

### `Entitlements` (final class, `@unchecked Sendable`)
Independent singleton, exactly like Android's own standalone `object Entitlements` — NOT threaded
through `IapHelper.Configuration`; `IapHelper` calls `Entitlements.shared` directly, the same
direct cross-singleton reference Android's `handlePurchase`/`restorePurchases` make. Mirrors
`DecisionEngine`'s own injectable-with-a-sane-default shape for testability:
```swift
public static let shared = Entitlements()
public init(store: BrainKeyValueStore = UserDefaultsBrainStore())
```
`NSLock`-protected `private var owned: Set<String>`, persisted through the injected
`BrainKeyValueStore` (Phase 3's existing protocol — reused, not re-invented; matches Android's
own `PreferenceHelper`-backed persistence, so `hasRemoveAds()` is correct immediately at cold
start, before the store has even connected, not just after the first `restorePurchases()`
completes). Two write paths, matching Android's own two:
- `public func grant(_ productId: String)` / `public func revoke(_ productId: String)` — single-
  product incremental ops (the fast path `IapHelper` uses right after a successful purchase, so a
  win-back purchase clears any "lapsed" impression immediately rather than waiting for the next
  sync).
- `public func sync(_ productIds: Set<String>)` — REPLACES the whole set, never unions (the
  mechanism that lets a lapsed subscription lose entitlement on the next
  `restorePurchases`/`currentEntitlements` sync — Android's own doc comment: "sync must REPLACE,
  not add — a cancelled subscription simply isn't in the store's returned set anymore; unioning
  would give it permanent free entitlement on that device").

`public func isActive(_ productId: String) -> Bool`, `public func activeProductIds() -> Set<String>`.
A `removeAdsProductIds: Set<String>` config value (settable, mirrors Android's
`setRemoveAdsProducts`, recomputes `hasRemoveAds()` immediately on change) and `public func
hasRemoveAds() -> Bool` — computed, no `setRemoveAds()`-equivalent call OUT to an ad-control
facade (§1, deferred — no ad-serving layer exists yet on iOS to call; Android's own `recompute()`
is explicitly try/caught around that one external call so a not-yet-initialized ad layer never
breaks the purchase flow — the iOS port simply has nothing to call yet, same fail-safe shape).

### `FxRates` (enum namespace, `NSLock`-protected — mirrors `ClickFraudGuard`'s locked-static-
state shape, not `HoldoutManager`'s unsynchronized one, since this state is genuinely written at
runtime from remote config)
Android's `FxRates.kt` has NO embedded rate table — it is entirely remote-config-driven
(`setRates(newRates:)`, keyed `fx_rates` per the Kotlin doc comment), defaulting to an empty
table with only `"USD"` implicitly rate-1.0 via a dedicated branch. Port this exactly, not a
hardcoded table:
```swift
public static func setRates(_ newRates: [String: Double])  // REPLACES, filters out <= 0 / non-finite entries, uppercases keys
public static func reset()
public static func knownCurrencyCount() -> Int
public static func toUsd(priceMicros: Int64, currencyCode: String) -> Double?  // nil on unknown/blank currency; "USD" always resolves to priceMicros / 1_000_000
```

### `IapHelper` (final class, `@unchecked Sendable`)
```swift
public static let shared = IapHelper()

public struct Configuration: Sendable {
    public let backend: StoreBackend
    public let eventSink: EventSink
    public let playerId: String
}
```
No `Entitlements` reference in `Configuration` — `IapHelper` calls `Entitlements.shared` directly
(see `Entitlements` above), matching Android's own independent-singleton coupling exactly. Tests
that exercise `IapHelper`'s effect on entitlements therefore assert against `Entitlements.shared`
(not a separately-constructed instance) and must reset it between tests — give `Entitlements` a
package-internal `func resetForTest()`, the same pattern `AdsBrain`/`ClickFraudGuard` already use
for their own singleton/static state.
No app-level "is this product consumable" override — deliberately omitted, per §3.3:
`Product.ProductType` already reports this directly (`.consumable` vs. the other three cases),
so there is no gap for the app to fill the way Android's `setConsumableProducts` had to.

```swift
public func configure(_ configuration: Configuration) async
public func isReady() -> Bool
public func setProducts(_ productIds: [String])
public func products() -> [IapProduct]
public func productFor(_ productId: String) -> IapProduct?
public func queryProducts() async -> [IapProduct]
public func purchase(_ productId: String) async -> IapResult
public func restorePurchases() async -> Set<String>   // returns the resulting active product ids

/// Every processed transaction, including ones NOT triggered by this process's own `purchase(_:)`
/// call — a restore, or one arriving in the background transaction stream (purchased on another
/// device, or completed after the app was relaunched). Single-slot, last-set-wins, mirrors
/// Android's `purchaseListener: ((IapResult) -> Unit)?` exactly (not a multi-subscriber stream —
/// Android never supported more than one listener either, and this phase isn't the place to add
/// that).
public var purchaseListener: (@Sendable (IapResult) -> Void)?
```
Internally: on `configure`, starts consuming `backend.transactionUpdates()` (an unstructured
`Task` owned by `IapHelper`, matching how this codebase's other long-lived listeners are wired —
plan-writing decides the exact structured-concurrency shape). Two distinct entitlement-write paths
(matching Android's own two — see `Entitlements` above): a successful `purchase(_:)` or an
incoming `transactionUpdates()` item calls `entitlements.grant(_:)` (single-product, immediate);
`restorePurchases()` calls `entitlements.sync(_:)` (bulk replace) after collecting the FULL
current-entitlements snapshot. Every successfully processed purchase, from EITHER path, then goes
through one shared `reportPurchase(_:)` step: `finish(_:)` on the backend, `iap_purchase` event
emission (§5), and `await AdsBrain.shared.onPurchase(usd: FxRates.toUsd(...) ?? 0.0)` (§3.8 —
unconditional, never skipped on unknown FX) — each of these three independently isolated (one
failing must never block the others, matching Android's own three separate try/catch blocks
around what its `reportPurchase()` does; see the AppsFlyer note in §1 for the third, deferred,
slot). `purchaseListener` (if set) is invoked with the final `IapResult` after `reportPurchase`
completes, for every path, including ones the caller's own `purchase(_:)` return value already
reported — so a caller relying on the listener alone still sees calls it itself triggered, exactly
matching Android's "dispatch sends to both the per-call callback AND the global listener" shape
(iOS's per-call "callback" is the `async` return value itself; `purchaseListener` covers the same
role Android's global listener does — everything, always).

## 5. `iap_purchase` event — wire shape (locked, verified against Android's real payload)

`eventName: "iap_purchase"`, `eventData` (snake_case, matching Android's `LogEventServer
.iapPurchaseEventData` exactly — field NAMES are the locked contract; content per §3.4/§3.5 for
the two fields whose underlying representation differs by platform):

```
product_id        .string
order_id           .string
price_micros       .int      (Int64 value, fits Swift's 64-bit Int — see §4's IapPurchase field)
currency_code       .string
quantity            .int
channel             .string  ("APP_STORE", always, this phase — see PurchaseChannel)
is_subscription     .int     (0/1, matching Phase 3's is_holdout precedent — not .bool)
purchase_token      .string  (JWS representation, see §3.4)
```

No `screen_name` field this phase: `grep -rn "screen_name\|ScreenName" Sources Tests` confirms
this codebase has no equivalent caller-context concept yet (Android's `ScreenName` is populated
from a UI-navigation tracker this port hasn't built). Omitted rather than fabricated — add it in
a future phase if/when an equivalent concept exists, matching this project's standing rule
against inventing wire fields with no real source of truth.

Emitted with `userProperty: UserProperty(userId: configuration.playerId)`, exactly matching every
other event-emitting call site in this codebase.

## 6. Testing

Every new type gets the same treatment every prior phase established: pure-logic types
(`FxRates`, `IapProductType`'s computed properties) get deterministic unit tests with no I/O
double needed. `Entitlements` gets an in-memory fake `BrainKeyValueStore` (reuse Phase 3's
existing in-memory fake, do not redeclare). `IapHelper` gets an in-memory fake `StoreBackend`
(constructed per-test, using `AsyncStream`'s continuation to feed synthetic transactions for
stream-based tests) — the exact pattern `BrainStorageTests`/`DecisionEngineTests` already
established for their own I/O boundaries. Coverage must include:
- `queryProducts`/`purchase`/`restorePurchases` happy paths.
- `purchase` → `.userCanceled`/`.alreadyOwned`/`.failed` mapping from whatever raw signal the
  fake backend produces.
- `Entitlements.grant`/`.revoke` — single-product incremental correctness, and that
  `hasRemoveAds()` recomputes immediately after either.
- `Entitlements.sync` REPLACES not unions — a product present in one sync and absent in the next
  must become `!isActive`.
- `Entitlements` persistence: a fresh `Entitlements` instance constructed against a store that
  already has prior state (from a previous instance's writes) reads that state back correctly at
  construction — cold-start correctness, not just in-process correctness.
- `purchaseListener` fires exactly once per processed transaction, for all three trigger paths
  (`purchase(_:)`, `restorePurchases()`, background `transactionUpdates()`), with the correct
  final `IapResult`.
- The `iap_purchase` event's full field set, using a recording fake `EventSink` (reuse this
  codebase's existing fake, do not redeclare).
- `AdsBrain.shared.onPurchase(usd:)` is genuinely called on every successful purchase path
  (`purchase`, `restorePurchases`, AND the background `transactionUpdates` stream) — assert via
  `AdsBrain.shared.currentState()?.purchaseCountLifetime` incrementing, reusing the
  `AdsBrainTests` `resetForTest()`/`makeConfiguration()` pattern already established.
- The FX-unknown-currency case still calls `onPurchase(usd: 0.0)` — never skips the call (§3.8).
- `FxRates.toUsd` returns `nil` (not a guessed value) for an unrecognized currency code; `"USD"`
  always resolves regardless of `setRates`; `setRates` REPLACES the table (a currency present in
  one call and absent in the next resolves back to `nil`, not the stale rate).

## 7. Security review (mandatory, per user's global rules — payment/financial code)

Beyond the usual final whole-branch review, this phase's branch must go through a
`security-reviewer` pass before merge, covering at minimum: no purchase can be granted
entitlement without SOME verifiable signal from the store (never trust a client-only "I paid"
claim with no backing `Transaction`); the JWS transaction string is never logged/exposed anywhere
outside the `iap_purchase` event's `purchase_token` field (no debug logging of raw receipts); no
force-unwrap or silently-swallowed error exists on any path that could grant entitlement
incorrectly; `Entitlements.sync`'s replace-not-union semantics are not accidentally weakened by
any later task's edit.
