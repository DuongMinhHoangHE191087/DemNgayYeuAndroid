# Purchase Router — Design Spec

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:writing-plans to turn this into an
> implementation plan, then superpowers:subagent-driven-development to execute it.

**Goal:** Port Android's `PurchaseRouter` (`com.app.plugin.iap.PurchaseRouter`, commit `7e16ee8`,
`DOC/50_PURCHASE_ROUTING.md`) — a multi-channel purchase router that gives game logic one API
(`purchase(productId)`) while a remote-config rule set decides, per product, whether the purchase
goes through the App Store (existing `IapHelper`) or an external web-shop checkout URL. This is
the first of Phase 4's deferred items to become unblocked, since the base IAP layer it depends on
already exists.

**Architecture:** A new `PurchaseRouter` singleton (`.shared`, matching `IapHelper`/`PaywallManager`
convention) holds NSLock-protected routing rules + web-shop config loaded via permissive JSON
parsing (never `Codable`'s strict decode — same fail-safe principle as `PaywallManager`/`FxRates`).
`route(productId:country:)` is a pure function of that config: the first matching rule wins,
`.webShop` only wins when the web-shop block is enabled, has an `https://` URL template, and the
caller-supplied `country` clears an optional allow-list — any missing condition falls back to
`.appStore`, which is always the pre-router behavior and therefore always safe. `purchase(_:)`
delegates the App Store branch straight to `IapHelper.shared.purchase(_:)` and, for the web-shop
branch, builds a checkout URL, emits a `web_checkout_opened` funnel event, and returns
`IapResult.webCheckoutOpened` — never opening a browser itself. Web-shop entitlement is granted
only through `grantFromServer(...)`, called by the host app from a channel it independently trusts
(a webhook-verified server response), never inferred client-side.

**Tech Stack:** Swift Package Manager, `MonetizationBrainCore` target, `Foundation.JSONSerialization`,
`NSLock`, XCTest.

---

## 1. Scope

**In scope:**
- `PurchaseRouter` type: `.shared` singleton, `Configuration { eventSink, playerId }`,
  `configure(_:)`, `activeVersion()`, `clear()`.
- Config parsing: `apply(_ json: String?) -> RoutingApplyResult`, matching the exact JSON shape in
  §4 (`version`, optional `web_shop` block, `rules` array).
- Decision: `route(productId:country:) -> PurchaseChannel` (pure, no side effects — mirrors
  Android's `route()` being callable standalone for a caller who wants to know the channel before
  acting).
- Execution: `purchase(productId:country:) async -> IapResult` — the one API game logic calls.
- URL building: `checkoutUrlFor(productId:country:) -> String?`.
- Web-shop entitlement grant: `grantFromServer(productId:orderId:priceMicros:currencyCode:quantity:isSubscription:) async -> Bool`.
- One new `IapResult` case: `.webCheckoutOpened(productId: String, url: String)`, added to the
  existing enum in `IapModels.swift` (Phase 4).
- One new event: `web_checkout_opened` (funnel numerator — see §5).
- `PurchaseChannel` already exists on this port (`IapModels.swift`, Phase 4, added as a forward-
  compat placeholder for exactly this phase) — reused as-is, no changes needed.

**Out of scope (explicitly, matching Android's own doc 50 §D "limits" and this port's established
practice of not building backend/analytics-only pieces in the SDK phase):**
- **Server-side entitlement source of truth / `GET /entitlements` sync endpoint.** `Entitlements`
  stays the on-device source of truth (lost on app delete, no cross-device sync) — doc 50 §D.1
  says this outright; a real fix is a studio backend project, not this SDK.
- **Server-side Play/App Store receipt verification.** Doc 50 §D.2: the router doesn't change this
  pre-existing limitation (tracked separately, Phase 4's own deferred item).
- **`restorePurchases` covering web-shop purchases.** Doc 50 §D.3: restore only ever talks to the
  App Store; a web-shop "restore" is a studio-server concern (re-grant from their own transaction
  history) that the SDK has no data to perform. The host app needs to know not to promise "restore"
  for web-shop-only purchases — a README note, not code.
- **Channel-selection heuristics (cost/conversion-optimized routing).** Doc 50 §D.4: `route()` is a
  pure function of static config, on both platforms — no bandit/brain involvement. (See §3.3 for
  why the brain is never consulted here at all, not even for the yes/no gate.)
- **Deep-link return-to-app after web checkout.** Doc 50 §D.5: the checkout URL is the studio's;
  getting the user back into the app after paying is the checkout page's job, not the SDK's — the
  SDK only ever finds out about a completed purchase through `grantFromServer`.
- **Backend changes** (`routing_version` column, `web_checkout_opened` event ingestion, a
  `PurchaseRouterTest`-equivalent backend test suite). Doc 50 §C notes the backend needs zero new
  endpoints (`revenue.by_channel_usd` already keys on the existing `channel` column, which
  `IapPurchase`/the `iap_purchase` event already carries per Phase 4) — this port has never touched
  the Python backend in any SDK phase, and this phase doesn't start.
- **A UI for opening the checkout URL.** Same "SDK doesn't draw UI" boundary as `PaywallManager`
  and `IapHelper.purchase` — the host app decides how to open the URL (`SFSafariViewController`,
  `UIApplication.open`, an in-app `WKWebView`, whatever fits its UX).

## 2. Dependencies On Existing Code

- `IapHelper.shared.purchase(_:)` — the App Store execution path; `PurchaseRouter` never
  reimplements purchase logic, it only decides which existing path runs.
- `Entitlements.shared.grant(_:)` — used by `grantFromServer`, exactly like `IapHelper` already
  uses it for App Store purchases.
- `EventSink`/`SdkEvent` — for `web_checkout_opened` and the web-shop `iap_purchase` event.
- `FxRates.toUsd(priceMicros:currencyCode:)` — for the payer-guardrail USD conversion in
  `grantFromServer` (see §3.2).
- `AdsBrain.shared.onPurchase(usd:)` — see §3.2.
- `PurchaseChannel`/`IapResult` in `IapModels.swift` (Phase 4) — extended, not replaced.

## 3. Deviations From Android (with rationale)

### 3.1 No ambient `countryProvider`/`userIdProvider` — explicit parameters instead

Android's `PurchaseRouter` reads country and user id from `LogEventServerHelper.currentCountry()`/
`currentUserId()`, two ambient singleton getters backed by state the SDK already tracks elsewhere.
This iOS port has no such ambient identity/geo singleton — Phase 5's `PaywallManager` already
established the pattern for this exact situation (`paywallFor(placement:playerId:)` takes
`playerId` explicitly rather than inventing an ambient provider) and this phase follows it:

- `playerId` lives in `PurchaseRouter.Configuration` (matching `IapHelper.Configuration`'s own
  `playerId` field, since the App Store branch of `purchase(_:)` IS `IapHelper.shared.purchase(_:)`
  under the hood — the two types should agree on where purchase identity comes from, not diverge).
- `country` is an explicit parameter on every method that needs it (`route`, `purchase`,
  `checkoutUrlFor`) — the SDK does not infer geography; the host app is already better positioned
  to know the device's region (or a server-confirmed one) than a guess baked into the SDK.

Android's `countryAllowed()` also treats the literal string `"DEFAULT"` as "unknown country" (an
Android-specific sentinel from its own ambient provider's fallback value). This port keeps that
same defensive check — an empty string OR the literal `"DEFAULT"` both count as "country unknown"
— purely as cheap insurance against a caller passing through an unprocessed upstream sentinel; it
costs nothing and doesn't reintroduce any ambient state.

### 3.2 `grantFromServer` DOES feed the payer-guardrail signal — Android's doesn't (a bug, not a feature)

Traced Android's `AdsBrain.onPurchase(usd)` call sites: it's called from exactly one place,
`IapHelper.kt:506` (the Play Billing path). `PurchaseRouter.grantFromServer` on Android calls
`Entitlements.grant` and logs the event, but never touches `AdsBrain`/`purchaseCountLifetime` at
all. That means on Android, a web-shop payer is invisible to every payer-aware guardrail and
belief-state signal (`LikelihoodEstimator`'s payer-segment thresholds, the guardrail rule that
eases ad-pressure "if `purchaseCountLifetime > 0` and a purchase happened in the last 2 days") —
they look, to the brain, exactly like someone who never paid.

This is inconsistent with this port's own binding invariant (established in Phase 4's `IapHelper`
port and reconfirmed in Phase 5): **`AdsBrain.shared.onPurchase(usd:)` must be called
unconditionally on every successful purchase**, because `purchaseCountLifetime` — not a USD total —
is what the payer-protection guardrail rule reads, and a web-shop sale is exactly as much a
successful purchase as an App Store one. Silently reproducing Android's gap here would mean this
port's own invariant is only true for one of two purchase channels, which defeats the point of
having stated it as unconditional.

**Decision:** iOS's `grantFromServer` calls `AdsBrain.shared.onPurchase(usd:)` after granting the
entitlement, using `FxRates.toUsd(priceMicros:currencyCode:) ?? 0.0` — identical to how
`IapHelper.reportPurchase` already does it. This is a deliberate, documented improvement over
Android's source, not a port of a bug. Call it out plainly in the README's Phase 6 section.

### 3.3 `async`, not a callback + `Activity` parameter

`IapHelper.purchase(_:)` (Phase 4) is already `async` and takes no `Activity`-equivalent — iOS
StoreKit 2 doesn't need one. `PurchaseRouter.purchase(productId:country:)` follows the same shape:
`async -> IapResult`, no callback closure, no view-controller parameter. `grantFromServer` is also
`async` (it awaits `eventSink.send` and `AdsBrain.shared.onPurchase`), where Android's version is
synchronous — matching this port's established "I/O-touching calls are `async`" convention from
every prior phase.

### 3.4 URL percent-encoding, not Java's `application/x-www-form-urlencoded`

Android encodes `{product_id}`/`{user_id}` substitutions with `URLEncoder.encode(value, "UTF-8")`,
which is form encoding (space becomes `+`). Swift has no direct equivalent; this port uses
`addingPercentEncoding(withAllowedCharacters:)` with a query-value-safe character set (RFC 3986
percent-encoding — space becomes `%20`, not `+`). Product IDs and player IDs are expected to be
simple identifiers with no whitespace in practice, so this is a low-risk, purely mechanical
platform difference — noted here so it isn't mistaken for an oversight later.

### 3.5 `Rule`/`WebShopConfig`/`RoutingApplyResult` are `public struct`s, not Kotlin `data class`es nested in an `object`

Matches this port's established type-organization convention (see `PaywallModels.swift`): shared
value types live in their own file/section as top-level `public struct`s conforming to
`Sendable, Equatable`, not nested inside the manager type.

### 3.6 The App Store execution path is dependency-injected, not a hardcoded call to `IapHelper.shared`

Android's `PurchaseRouter` is a Kotlin `object` calling the equally-global `IapHelper` object
directly — there's no DI seam because Android's own `IapHelper` doesn't have one either at this
call site. This port's `IapHelper` DOES have a DI seam (`StoreBackend`), but `IapHelper.shared`
itself has no `resetForTest()` hook and every existing `IapHelperTests` test deliberately
constructs a fresh, non-shared `IapHelper()` instance with its own fake `StoreBackend` rather than
reconfiguring the real singleton — reconfiguring `IapHelper.shared` mid-test-run would leak state
across tests exactly the way `Configuration`'s default disk-persisted store already causes
cross-test flakiness elsewhere in this port (Phase 5's own Task 2 finding).

**Decision:** `PurchaseRouter.Configuration` carries an `iapHelper: IapHelper` field (defaulting to
`.shared` for real app usage) that `purchase(productId:country:)` delegates the App Store branch
to, instead of a hardcoded reference to `IapHelper.shared`. This costs nothing in production
(default is still the real singleton) and lets tests pass a fresh `IapHelper()` + fake
`StoreBackend`, matching `IapHelperTests`' own established per-test-instance convention exactly —
the same DI-for-testability principle this project has applied at every prior injection point
(`StoreBackend`, `BrainKeyValueStore`, `RandomNumberGenerator`), just applied one level up.

## 4. Type Definitions

```swift
// IapModels.swift — ADD to the existing IapResult enum (do not touch any other case):
public enum IapResult: Sendable, Equatable {
    case success(IapPurchase)
    case pending(IapPurchase)
    case userCanceled
    case alreadyOwned
    case failed(code: Int, message: String)
    /// The router chose the web-shop channel (design spec §1, Purchase Router phase): the SDK
    /// built a checkout URL and is handing it back — the host app opens it however fits its UX
    /// (SFSafariViewController, an in-app WKWebView, the system browser). No entitlement has been
    /// granted yet: that only happens after `PurchaseRouter.grantFromServer(...)`, once the host
    /// app's own backend has verified the vendor's webhook.
    case webCheckoutOpened(productId: String, url: String)
}
```

```swift
// PurchaseRouter.swift — new file, Sources/MonetizationBrainCore/Iap/PurchaseRouter.swift

public struct PurchaseRoutingRule: Sendable, Equatable {
    public let productId: String
    public let channel: PurchaseChannel
}

public struct WebShopConfig: Sendable, Equatable {
    public let enabled: Bool
    public let urlTemplate: String
    /// Empty = no country restriction. Non-empty = device/caller country must be a member.
    public let eligibleCountries: Set<String>
    /// false when `urlTemplate` is non-empty but not `https://` — when false, web-shop can never
    /// be selected regardless of `enabled`, matching Android's "https is not negotiable" rule.
    public let urlOk: Bool

    public static let off = WebShopConfig(enabled: false, urlTemplate: "", eligibleCountries: [], urlOk: false)
}

public struct RoutingApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String
}

public final class PurchaseRouter: @unchecked Sendable {
    public static let shared = PurchaseRouter()

    public struct Configuration: Sendable {
        public let eventSink: EventSink
        public let playerId: String
        /// The App Store execution path (§3.6) — defaults to the real singleton for app usage;
        /// tests pass a fresh `IapHelper()` configured with a fake `StoreBackend`, matching
        /// `IapHelperTests`' own established convention of never reconfiguring `IapHelper.shared`.
        public let iapHelper: IapHelper

        public init(eventSink: EventSink, playerId: String, iapHelper: IapHelper = .shared) {
            self.eventSink = eventSink
            self.playerId = playerId
            self.iapHelper = iapHelper
        }
    }

    private let lock = NSLock()
    private var configuration: Configuration?
    private var rules: [PurchaseRoutingRule] = []
    private var webShop: WebShopConfig = .off
    private var version: String = "not loaded"

    public init() {}

    public func configure(_ configuration: Configuration)
    public func activeVersion() -> String
    public func clear()

    @discardableResult
    public func apply(_ json: String?) -> RoutingApplyResult

    /// Pure function of loaded config — no side effects, no event emission. Exposed standalone
    /// (matching Android) for a caller who wants to know the channel before acting on it.
    public func route(productId: String, country: String) -> PurchaseChannel

    /// The one API game logic calls. App Store branch delegates to `configuration.iapHelper
    /// .purchase(_:)` unchanged; web-shop branch builds a URL, emits `web_checkout_opened`, and
    /// returns `.webCheckoutOpened` — never opens anything itself.
    public func purchase(productId: String, country: String) async -> IapResult

    /// `nil` when web-shop isn't the resolved channel for this product/country. `{product_id}`/
    /// `{user_id}` substituted and percent-encoded.
    public func checkoutUrlFor(productId: String, country: String) -> String?

    /// Grants a web-shop entitlement for a transaction the CALLER has already independently
    /// verified (e.g. a webhook-confirmed vendor payment relayed by the host app's own backend).
    /// The SDK does not and cannot verify this itself — see the doc comment on the real method for
    /// the full warning. `orderId` is the backend's dedupe key on retry (same pattern as
    /// `IapHelper`'s `processedOrderIds` — here dedup is the backend's job, matching Android).
    /// @return false only when `productId`/`orderId` are blank — nothing is granted.
    public func grantFromServer(
        productId: String,
        orderId: String,
        priceMicros: Int64,
        currencyCode: String,
        quantity: Int = 1,
        isSubscription: Bool = false
    ) async -> Bool
}
```

Config JSON shape (identical field names/semantics to Android's `purchase_routing_android`):

```json
{
  "version": "routing_v1",
  "web_shop": {
    "enabled": true,
    "url_template": "https://shop.example.com/checkout?product={product_id}&user={user_id}",
    "eligible_countries": ["US", "DE"]
  },
  "rules": [
    {"product_id": "coin_pack_1", "channel": "web_shop"},
    {"product_id": "vip_pass", "channel": "play_store"}
  ]
}
```

Note the wire value is still `"play_store"`/`"web_shop"` (lowercase, snake_case) even though the
Swift enum is `PurchaseChannel.appStore`/`.webShop` — the remote-config schema is shared
infrastructure the backend/dashboard already knows about from Android; changing the wire vocabulary
to `"app_store"` would silently break nothing today (config is per-platform-keyed,
`purchase_routing_android` vs. a future `purchase_routing_ios`) but there's no reason to invent a
new vocabulary when the concept (Play Store / native store) maps 1:1. Parse `"play_store"` OR
`"app_store"` as `.appStore` (accept either spelling defensively — cheap, and removes a footgun if
someone reuses an Android-authored config key by hand); parse `"web_shop"` as `.webShop`. Anything
else is an invalid-channel note (see §6 permissive-parsing rules).

## 5. Locked Event Wire Shapes

### `web_checkout_opened` (new)

```swift
SdkEvent(
    eventName: "web_checkout_opened",
    eventData: [
        "product_id": .string(productId),
        "channel": .string(PurchaseChannel.webShop.rawValue),   // "WEB_SHOP"
        "routing_version": .string(activeVersion())
    ],
    userProperty: UserProperty(userId: configuration.playerId)
)
```

No `screen_name` field — matching Phase 5's own established deviation (this port has no ambient
screen-tracking concept). The checkout URL itself is NEVER included in this event or any log
payload — Android's own doc comment calls this out explicitly (a studio's `url_template` can embed
a session token; logging the resolved URL would leak it into the log pipeline) and this port
carries the same rule forward as a hard requirement, not a suggestion (see §6 for the regression
test that locks it).

### `iap_purchase` (existing shape, reused for web-shop transactions)

`grantFromServer` emits through the exact same wire shape `IapHelper.reportPurchase` already uses
(Phase 4) — same field set, same types, `channel` set to `.webShop`, `purchase_token` empty (web
shop has no store-issued token; `order_id` is the dedupe key instead, matching Android's default
`purchaseToken = ""` for this call site):

```swift
SdkEvent(
    eventName: "iap_purchase",
    eventData: [
        "product_id": .string(productId),
        "order_id": .string(orderId),
        "price_micros": .int(Int(priceMicros)),
        "currency_code": .string(currencyCode),
        "quantity": .int(quantity),
        "channel": .string(PurchaseChannel.webShop.rawValue),
        "is_subscription": .int(isSubscription ? 1 : 0),
        "purchase_token": .string("")
    ],
    userProperty: UserProperty(userId: configuration.playerId)
)
```

## 6. Testing Requirements

Follow `PaywallManagerTests.swift`'s established conventions (a `FakeEventSink` spy already exists
in that file — reuse it, don't rewrite one). Required coverage, mirroring Android's
`PurchaseRouterTest.kt` (283 lines, 22 tests) adapted to this port's own idioms:

- **Default state:** before any `apply()`, `route(_:country:)` returns `.appStore` for every
  product, `activeVersion() == "not loaded"`.
- **Reject paths, keep last-good config:** `nil`/blank/malformed JSON, missing `version` — each
  rejected, `applied == false`, and a SUBSEQUENT `route`/`activeVersion` call after a good config
  was already loaded still reflects that good config (same "reject-keeps-last-good" test shape as
  `PaywallManagerTests`).
- **Rule matching:** first-matching-rule-wins (a product declared twice, once per channel, in that
  order); undeclared product falls back to `.appStore`; an explicit `"play_store"`/`"app_store"`
  rule resolves to `.appStore`.
- **Web-shop gating — every one of the four conditions independently tested to fail closed to
  `.appStore`:** `enabled: false`; empty `url_template`; `url_template` not starting with
  `https://`; `eligible_countries` non-empty and the passed-in `country` not a member; `country`
  empty string; `country` == `"DEFAULT"`. Each must independently prove the fallback — a single
  "everything wrong at once" test does not prove which condition the code actually checks.
  (**Permissive-parsing rule, carried forward from Phase 5's own whole-branch review finding —
  applies here too:** every `guard ... else { continue }`/early-return in `apply(_:)`'s parsing
  must append a `notes` entry, and no `[[String: Any]]`-style all-or-nothing array cast — parse
  `rules` per-element so one malformed rule only drops that rule, matching the fix already applied
  to `PaywallManager`. Do not reintroduce either mistake in this new parser.**)
- **`purchase(productId:country:)`:** App Store branch calls through to `configuration.iapHelper`
  (a fresh `IapHelper()` configured with a fake `StoreBackend`, per §3.6 — same fake-`StoreBackend`
  shape as `IapHelperTests`, redeclared file-locally per this port's established per-file-fake
  convention) and returns whatever it returns, unmodified; web-shop branch returns
  `.webCheckoutOpened` with the correctly
  built URL and emits exactly one `web_checkout_opened` event with the locked field set from §5 —
  **assert the URL is NOT present anywhere in the emitted event's data** (the regression test for
  the "never log the checkout URL" rule).
- **`checkoutUrlFor`:** `{product_id}`/`{user_id}` substitution correctness including percent-
  encoding of a value containing reserved characters (e.g. a player id with `&` or a space);
  returns `nil` when the resolved channel isn't `.webShop` for that product/country.
- **`grantFromServer`:** blank `productId`/`orderId` → returns `false`, `Entitlements` unchanged,
  no event emitted; valid call → `Entitlements.shared.isActive(productId)` becomes true, exactly one
  `iap_purchase` event matching §5's shape, AND `AdsBrain.shared`'s `purchaseCountLifetime`
  increments by exactly one (the regression test proving §3.2's deviation is real, not just
  documented) — use an isolated `AdsBrain`/`DecisionEngine` test configuration with
  `InMemoryBrainStore` (this port's binding rule: never the real disk-persisted default store in
  tests).
- **Config reload behavior:** loading a second, different config replaces rules/web-shop
  atomically (a `route()` call during/after reload never observes a torn mix of old rules + new
  web-shop config or vice versa) — same locking shape as `PaywallManager.apply`.

Non-goals for this phase's tests (out of scope per §1, don't write these): App Store receipt
verification tests (Phase 4 territory), a live network test hitting a real checkout URL, any
backend-side test.
