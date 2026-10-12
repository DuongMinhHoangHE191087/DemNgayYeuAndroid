# Phase 5 "Paywall" — Design Spec

**Status:** approved (standing autonomous-port mandate — see `monetization-brain-ios-autonomous-mandate`
memory; no per-phase design checkpoint). Payment-adjacent code (gates an IAP offer surface) — a
`security-reviewer` pass is required before this phase merges, same as Phase 4.

## 1. Scope

Port Android's paywall layer — `com.app.plugin.paywall.PaywallManager` (commits `5d4a992` +
`37fb8b5`, 281 lines Kotlin, read in full). This is confirmed by research to be a small,
**decision-only** layer, not a UI layer — genuinely portable scope is well under 150 lines of
Swift, and this spec does not inflate it to match the Android commits' apparent size (most of
that size is Vietnamese rationale comments and backend-side analytics work with no iOS-SDK
counterpart).

**In scope:**
- `PaywallManager`: a small facade (same shape as `IapHelper`/`AdsBrain` — `.shared` singleton,
  own `configure(_:)`) answering "what to sell, in what order, with which variant" for a named
  placement — never rendering anything. Confirmed by Android's own class doc: *"SDK không vẽ
  giao diện"* (the SDK does not draw UI) — the exact same boundary `AdsBrain`'s decision points
  and `IapHelper`'s `purchase(_:)` already established (answer the question, never render).
- Remote-config-driven, per-placement A/B variant selection — weighted, deterministic per
  `playerId`, reusing `HoldoutManager.bucketWith(_:_:)` (already shipped, Phase 1) with a
  dedicated hash namespace (`"paywall_variant"`) so it's statistically independent from both
  brain holdout and brain rollout.
- **Native holdout, unified with the SDK-wide brain holdout** — NOT a separate paywall-specific
  holdout mechanism. This is the single most load-bearing fact in the whole layer (confirmed by
  reading Android's own class doc verbatim, quoted in §3.1): the paywall control group and the
  brain control group are the SAME group (`HoldoutManager.isHoldout(playerId)`, already shipped),
  because the thing being measured is "does the whole offer machine (brain + paywall) create
  value versus nothing," not "paywall variant A vs. variant B alone." This means **zero new
  holdout code** — `paywallFor` calls the exact `HoldoutManager.isHoldout` this port already has.
- The brain gate: `paywallFor` calls the already-shipped `AdsBrain.shared.shouldOfferIap(decisionPoint:)`
  (Phase 3.5) — every paywall offer runs through the SAME guardrails (frustration, purchase
  velocity, session/24h caps) every other IAP-offer path does, so paywall can never become a
  back door around them.
- Measurement feedback: `onPaywallShown` calls the already-shipped `AdsBrain.shared.onIapOfferShown()`
  (Phase 3.5, sync) so a paywall impression consumes the same offer-frequency counters every
  other IAP-offer path does — the exact mechanism that keeps paywall from silently bypassing its
  own gate's guardrails on the NEXT call.
- Two new `EventSink` event types, `paywall_impression` and `paywall_holdout_skip`, matching
  Android's field sets exactly (see §5) minus `screen_name` (see §3.3 — same reasoning Phase 4
  already established for `iap_purchase`).
- Permissive, per-field-defensive remote-config JSON parsing matching Android's exact contract:
  a malformed config REJECTS (keeps the last-known-good config running) rather than crashing or
  clearing state — the same fail-safe principle `BrainPolicy`/`FxRates` already established.

**Explicitly out of scope, per Android's own design doc (`DOC/49_PAYWALL.md`, read in full) and
confirmed by the codebase itself:**
- **No paywall UI/rendering/theming** — the host app owns 100% of the visual surface. Confirmed
  by the entire absence of any view/layout/color type in Android's `PaywallManager.kt`.
- **No price A/B** — variants only reorder/select WHICH SKUs to offer, never change price. Doc 49
  §E.1 explicitly defers price-bandit work (needs multiple same-tier SKUs at different real store
  price points — an ops/App-Store-Connect decision, not an SDK feature).
- **Dunning/win-back** and **free-trial eligibility** — both explicitly deferred by Android's own
  doc to separate, later features (doc 51-equivalent; free-trial eligibility is left to the
  store's own offer-token mechanism, not the SDK's job).
- **Multi-channel purchase routing** — already tracked as a Phase 4 deferred item (Android's own
  `PurchaseRouter`, doc 50); paywall has zero coupling to channel selection.
- **The AppsFlyer revenue-reporting fix** bundled into Android's first commit — confirmed by
  research to have ZERO coupling to `PaywallManager.kt` (it's `IapHelper`/`AppsflyerHelper`
  code that happened to land in the same commit). This is Phase 4/attribution territory
  (already flagged deferred there), not Phase 5's concern.
- **Backend `/metrics/paywall` conversion-funnel computation** — pure backend analytics, joining
  the two new client events against the already-shipped `iap_purchase` event server-side. No
  client-side counterpart beyond the two events this phase emits.

## 2. Architecture

```
PaywallManager (final class, NSLock-protected, mirrors IapHelper's/AdsBrain's own singleton shape)
├── configure(_:) — stores an injected EventSink (no RemoteConfigProvider dependency: matches
│     Android exactly — the CALLER fetches the config string and passes it to apply(_:), this
│     type never reaches out to fetch anything itself)
├── apply(_:) — permissive JSON parse + atomic swap-in, reject-keeps-last-good on any error
├── paywallFor(placement:playerId:) async -> Paywall? — the one decision entry point:
│     config lookup → HoldoutManager.isHoldout (skip + counterfactual event) → AdsBrain.shared
│     .shouldOfferIap (brain gate) → pick(variants:playerId:) (weighted deterministic bucket)
├── variantIdFor(placement:playerId:) -> String? — same pick(), skips the holdout/brain gates
│     (used to compute the counterfactual for a holdout player's skip event)
├── onPaywallShown(_:playerId:) async — AdsBrain.shared.onIapOfferShown() + paywall_impression event
├── activeVersion() / holdoutSkipCount() / clear() — read-only/reset accessors
└── pick(_:playerId:) — private static, pure: weighted deterministic bucket over
      HoldoutManager.bucketWith("paywall_variant", playerId)
```

No new target, no new protocol. This phase adds exactly one new file's worth of orchestration on
top of infrastructure every earlier phase already shipped.

## 3. Key deviations from the Kotlin source (all deliberate, all documented in code)

1. **The holdout-unification fact itself is not a deviation to port — it's already the current
   state.** Android's OWN commit history shows this layer started with a separate paywall-holdout
   namespace and was later collapsed into the shared brain-holdout namespace (`37fb8b5`). iOS
   ports the CURRENT (unified) state directly — `HoldoutManager.isHoldout(playerId)`, no separate
   paywall-holdout type ever exists on this port. Quoted from Android's own class doc, translated:
   *"The paywall holdout group and the brain holdout group are ONE (the same
   `brain_holdout_v1` hash space of `HoldoutManager`) — deliberate, not a shortcut: the
   measurement unit is the whole offer machine (brain + paywall), so someone in the control group
   gets neither personalized decisions nor any paywall. Splitting into two groups would dilute
   the measurement."*
2. **`paywallFor`/`onPaywallShown` are `async` on iOS, unlike Android's synchronous methods** —
   same reason every other event-emitting call in this codebase is `async` since Phase 1.5:
   deterministic event-emission testability, not fire-and-forget. `apply`/`variantIdFor`/
   `activeVersion`/`holdoutSkipCount`/`clear` stay synchronous (they touch no `EventSink`).
3. **No `screen_name` field on either new event**, matching Phase 4's own precedent for
   `iap_purchase`: no equivalent caller-context concept exists on this port yet. Omitted rather
   than fabricated.
4. **JSON parsing uses `JSONSerialization` + manual, per-field defensive casting — never
   `Codable`'s all-or-nothing strict decode.** Android's `Gson`-based parsing is deliberately
   forgiving per-field (a malformed `decision_point` string logs a note and falls back to
   `.shopOpen` rather than rejecting the whole placement; an empty-products variant is dropped
   with a note rather than rejecting the whole config) — `Codable`'s default behavior of failing
   the ENTIRE decode on one bad field would silently discard a config that's 95% fine because one
   placement had a typo. Port the exact per-field permissiveness, not the encoding mechanism.
5. **`ApplyResult.report`'s prose is in English**, matching this whole port's established
   convention of translating Vietnamese log/rationale strings while keeping guardrail `code`
   identifiers and wire field names byte-identical to Android where cross-platform comparison
   matters (this string is neither — it's a local diagnostic message, safe to translate).
6. **`onPaywallShown(_:playerId:)` takes an explicit `playerId` parameter Android's method
   doesn't have.** Android's `Paywall` struct carries no player identity (matching iOS's own
   `Paywall`), and Android's `LogEventServer`/`AdsBrain` read player identity from ambient global
   state. iOS's event emission needs an explicit `UserProperty(userId:)`, and `AdsBrain.shared
   .onIapOfferShown()` needs no playerId at all (it's gated on the already-configured `AdsBrain`
   session) — so the ONE piece `onPaywallShown` needs that Android's doesn't require explicitly
   is `playerId` for the event's `userProperty`. The caller already has it in scope (it's the same
   `playerId` just passed to the `paywallFor` call that produced this `Paywall`), so this is a
   zero-burden explicit parameter, not new caller responsibility.

## 4. Types

### `PaywallVariant` (struct, `Sendable, Equatable`)
```swift
public let id: String
public let weight: Int
public let products: [String]
public let highlight: String?
```

### `Paywall` (struct, `Sendable, Equatable`)
```swift
public let placement: String
public let variantId: String
public let products: [String]      // already in the chosen variant's declared order
public let highlight: String?      // the product to visually emphasize, if the variant declared one
public let version: String
```

### `PaywallApplyResult` (struct, `Sendable, Equatable`)
```swift
public let applied: Bool
public let report: String
```

### `PaywallManager` (final class, `@unchecked Sendable`)
```swift
public static let shared = PaywallManager()

public struct Configuration: Sendable {
    public let eventSink: EventSink
    public init(eventSink: EventSink) { self.eventSink = eventSink }
}

public init()
public func configure(_ configuration: Configuration)
public func activeVersion() -> String                       // "not loaded" before the first apply(_:)
public func holdoutSkipCount() -> Int                        // in-memory only, reset by clear()
public func clear()
@discardableResult public func apply(_ json: String?) -> PaywallApplyResult
public func paywallFor(placement: String, playerId: String) async -> Paywall?
public func variantIdFor(placement: String, playerId: String) -> String?
public func onPaywallShown(_ paywall: Paywall, playerId: String) async
```

`paywallFor`'s null/nil contract (doc comment must state this explicitly, matching Android's own
triple-repeated warning): `nil` means "the SDK has no opinion, run your old flow" — NEVER "show an
empty paywall." A zero-product variant is rejected at parse time specifically so this can never
happen from a data-shape bug. One call to `paywallFor` = one bandit decision when the brain is
active (real decision log entry, real guardrail evaluation) — callers must not call BOTH
`AdsBrain.shared.shouldOfferIap` and `paywallFor` for the same offer opportunity (that is two
decisions for one opportunity, skewing propensity in the decision log — document this contract,
matching Android's own explicit warning, §3 of the Kotlin class doc).

## 5. Event wire shapes (locked, verified against Android's real payload)

`paywall_impression` (`eventData`, snake_case):
```
placement           .string
variant_id           .string
product_count        .int
paywall_version      .string
```

`paywall_holdout_skip` (`eventData`, snake_case):
```
placement                    .string
counterfactual_variant_id    .string
paywall_version               .string
```

Both emitted with `userProperty: UserProperty(userId: playerId)`, matching every other
event-emitting call site in this codebase.

## 6. Testing

Every method gets the same treatment every prior phase established. `apply(_:)` is pure logic
with no I/O — deterministic unit tests, no double needed:
- Well-formed config round-trips correctly (version, placements, variants, decision points).
- Missing `version`/`placements` → rejected, `applied == false`, existing config unchanged.
- A placement with a malformed `decision_point` string keeps the placement (falls back to
  `.shopOpen`) rather than dropping it.
- A variant with an empty `products` array is dropped; a placement left with zero
  positive-weight variants is dropped entirely; a config where EVERY placement gets dropped this
  way is still `applied == true` (the JSON itself was valid) with `parsed.count == 0` and notes.
- `reject(_:)` never clears the currently-running config — apply a good config, then a bad one,
  assert `activeVersion()`/`paywallFor` still reflect the FIRST (good) config.
- Weight parsing accepts both a JSON number and a numeric string; a negative/non-numeric weight
  clamps to `0` (never negative), matching `.coerceAtLeast(0)`.

`paywallFor`/`onPaywallShown` get a recording fake `EventSink` (reuse the codebase's existing
per-file fake convention) plus real `HoldoutManager`/`AdsBrain` (configuring `AdsBrain.shared`
with a fresh session per test, exactly like `IapHelperTests` already does for its own
`AdsBrain`-touching tests):
- Unconfigured placement (no `apply` called, or the specific placement never declared) → `nil`,
  no event, no brain call.
- A player in the SDK-wide holdout bucket (`HoldoutManager.holdoutFraction = 1.0`) → `nil`,
  `holdoutSkipCount()` increments, a `paywall_holdout_skip` event fires with the correct
  `counterfactual_variant_id` (computed via `variantIdFor`, independent of the holdout check
  itself).
- **Holdout-before-brain-gate ordering** (the single most important invariant in this layer, per
  Android's own explicit doc-comment and dedicated test): configure `AdsBrain.shared` with a
  recording fake `EventSink` AND `PaywallManager.shared` with the SAME fake sink, put the player
  in the SDK-wide holdout bucket, call `paywallFor`. Assert the sink's events are EXACTLY
  `["sdk_configured", "paywall_holdout_skip"]` (or whatever the fixed prefix from `AdsBrain`
  configuration is, established via the same `sentEvents` pattern `AdsBrainTests`/
  `IapHelperTests` already use) — i.e. NO `brain_decision` event ever appears, proving
  `shouldOfferIap`'s underlying `DecisionEngine.decide` was never reached for a holdout player.
  This directly proves the ordering, not just infers it from the return value.
- A non-holdout, brain-active player with a real declared placement → a `Paywall` is returned,
  `variantId` matches what `variantIdFor` independently computes for the same `placement`/
  `playerId`, `products` matches the chosen variant's declared list and order.
- `onPaywallShown` calls `AdsBrain.shared.onIapOfferShown()` (assert via the SAME session-counter
  mechanism `AdsBrainTests`/`IapHelperTests` already use — e.g. `assistsThisSession`-style
  counter, here `iapOffersThisSession`) and emits exactly one `paywall_impression` event with the
  full field set.
- `pick`'s determinism: same `playerId`/`placement`/variant set → same `variantId` across
  repeated calls (not flaky like the bandit — this is pure deterministic hashing, unlike
  Thompson Sampling, so exact-value assertions ARE safe and expected here, unlike the
  `BanditPolicy`-backed non-holdout assertions this project's `AdsBrainTests`/`IapHelperTests`
  had to avoid).
