# Purchase Router Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port Android's `PurchaseRouter` — one `purchase(productId)` API for game logic, with a
remote-config rule set deciding App Store vs. web-shop checkout per product.

**Architecture:** See `docs/superpowers/specs/2026-09-04-purchase-router-design.md` for full
rationale. Three tasks: Task 1 ships the config/parsing/route layer (pure, no I/O) with stubs for
the two I/O-touching methods; Task 2 replaces those stubs with the real implementations (App Store
delegation, web-shop checkout + event emission, server-granted entitlement); Task 3 updates the
README.

**Tech Stack:** Swift Package Manager, `MonetizationBrainCore` target, `Foundation.JSONSerialization`,
`NSLock`, XCTest.

**Global Constraints (apply to every task):**
- No force-unwraps (`!`) in new production code.
- NSLock discipline: lock only actual mutable state; never call `.lock()`/`.unlock()` directly
  inside an `async` function body (route through synchronous private helpers, exactly like
  `PaywallManager`'s `currentPlacements()`/`incrementHoldoutSkips()` pattern); never call
  cross-component code (`EventSink.send`, `IapHelper.purchase`, `Entitlements.grant`,
  `AdsBrain.shared.onPurchase`) while holding `PurchaseRouter`'s own lock.
- Permissive JSON parsing only — `JSONSerialization` into `[String: Any]` with manual per-field
  casting, never `Codable`'s strict decode. **Every `guard ... else { continue }` / early-return in
  the parsing loop must append a `notes` entry** — this repeats a mistake this project's own
  Phase 5 whole-branch review already found and fixed once (silent config-drops with zero
  diagnostic); do not reintroduce it here. Cast the `rules` array as `[Any]` and filter per
  element (never `[[String: Any]]`, which is all-or-nothing across the whole array — the same
  brittleness Phase 5's review also caught and fixed).
- `DecisionEngine`'s default `store` parameter is real, disk-persisted `UserDefaults` — any test
  that builds an `AdsBrain.Configuration`/`DecisionEngine` MUST pass an in-memory
  `BrainKeyValueStore` fixture, never the default.
- Never assert an exact bandit-chosen outcome in a test with 2+ real candidates (not expected to
  come up in this plan — nothing here calls `AdsBrain.shared.shouldOfferIap`/`DecisionEngine.decide`
  — but if a task finds itself needing to, stop and flag it rather than writing a flaky assertion).

---

### Task 1: Config, parsing, and pure routing decision

**Files:**
- Modify: `Sources/MonetizationBrainCore/Iap/IapModels.swift`
- Create: `Sources/MonetizationBrainCore/Iap/PurchaseRoutingModels.swift`
- Create: `Sources/MonetizationBrainCore/Iap/PurchaseRouter.swift`
- Test: `Tests/MonetizationBrainCoreTests/PurchaseRouterTests.swift`

- [ ] **Step 1: Add the `webCheckoutOpened` case to `IapResult`**

Open `Sources/MonetizationBrainCore/Iap/IapModels.swift`. Find the existing `IapResult` enum
(currently ends with `case failed(code: Int, message: String)`). Add one case:

```swift
public enum IapResult: Sendable, Equatable {
    case success(IapPurchase)
    case pending(IapPurchase)
    case userCanceled
    case alreadyOwned
    case failed(code: Int, message: String)
    /// The router chose the web-shop channel (design spec, Purchase Router phase): the SDK built
    /// a checkout URL and is handing it back — the host app opens it however fits its UX
    /// (SFSafariViewController, an in-app WKWebView, the system browser). No entitlement has been
    /// granted yet: that only happens after `PurchaseRouter.grantFromServer(...)`, once the host
    /// app's own backend has verified the vendor's webhook.
    case webCheckoutOpened(productId: String, url: String)
}
```

Do not touch any other case or any other type in this file.

- [ ] **Step 2: Write the failing model tests (config-parsing surface)**

Create `Tests/MonetizationBrainCoreTests/PurchaseRouterTests.swift` with this content — it will
fail to compile until Steps 3-5 exist. Read every test before moving on; this is the full Task 1
test surface (Task 2 appends more tests to this same file for the I/O-touching methods).

```swift
import XCTest
@testable import MonetizationBrainCore

final class PurchaseRouterTests: XCTestCase {
    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

    private let wellFormedConfig = """
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
        """

    override func setUp() {
        super.setUp()
        PurchaseRouter.shared.clear()
    }

    override func tearDown() {
        PurchaseRouter.shared.clear()
        super.tearDown()
    }

    // MARK: - Default state

    func test_beforeAnyApplyEveryProductRoutesToAppStore() {
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "anything", country: "US"), .appStore)
        XCTAssertEqual(PurchaseRouter.shared.activeVersion(), "not loaded")
    }

    // MARK: - Reject paths keep the last-good config

    func test_applyNilOrBlankConfigIsRejected() {
        XCTAssertFalse(PurchaseRouter.shared.apply(nil).applied)
        XCTAssertFalse(PurchaseRouter.shared.apply("   ").applied)
    }

    func test_applyMissingVersionIsRejected() {
        XCTAssertFalse(PurchaseRouter.shared.apply(#"{"rules": []}"#).applied)
    }

    func test_applyMalformedJsonIsRejectedAndKeepsExistingConfig() {
        XCTAssertTrue(PurchaseRouter.shared.apply(wellFormedConfig).applied)
        let result = PurchaseRouter.shared.apply("{not valid json")
        XCTAssertFalse(result.applied)
        XCTAssertEqual(PurchaseRouter.shared.activeVersion(), "routing_v1")
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .webShop)
    }

    // MARK: - Rule matching

    func test_firstMatchingRuleWins() {
        let config = """
            {
              "version": "routing_v2",
              "web_shop": {"enabled": true, "url_template": "https://s.example.com/{product_id}"},
              "rules": [
                {"product_id": "coin_pack_1", "channel": "web_shop"},
                {"product_id": "coin_pack_1", "channel": "play_store"}
              ]
            }
            """
        XCTAssertTrue(PurchaseRouter.shared.apply(config).applied)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .webShop)
    }

    func test_undeclaredProductRoutesToAppStore() {
        _ = PurchaseRouter.shared.apply(wellFormedConfig)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "unknown_sku", country: "US"), .appStore)
    }

    func test_explicitPlayStoreRuleRoutesToAppStore() {
        _ = PurchaseRouter.shared.apply(wellFormedConfig)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "vip_pass", country: "US"), .appStore)
    }

    func test_appStoreSpelledEitherWayParsesTheSame() {
        let config = """
            {
              "version": "routing_v3",
              "rules": [
                {"product_id": "a", "channel": "app_store"},
                {"product_id": "b", "channel": "play_store"}
              ]
            }
            """
        XCTAssertTrue(PurchaseRouter.shared.apply(config).applied)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "a", country: "US"), .appStore)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "b", country: "US"), .appStore)
    }

    func test_invalidChannelIsDroppedWithNote() {
        let config = """
            {
              "version": "routing_v4",
              "web_shop": {"enabled": true, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "crypto"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("invalid"), "report was: \(result.report)")
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
    }

    func test_missingWebShopBlockRoutesEveryWebShopRuleToAppStoreWithNote() {
        let config = """
            {
              "version": "routing_v5",
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("web_shop"), "report was: \(result.report)")
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
    }

    func test_ruleWithNonObjectPlacementInArrayIsSkippedWithNoteButOthersStillParse() {
        let config = """
            {
              "version": "routing_v6",
              "web_shop": {"enabled": true, "url_template": "https://s.example.com/{product_id}"},
              "rules": ["not_an_object", {"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.isEmpty)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .webShop)
    }

    func test_ruleMissingProductIdIsSkippedWithNote() {
        let config = """
            {
              "version": "routing_v7",
              "web_shop": {"enabled": true, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"channel": "web_shop"}, {"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.isEmpty)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .webShop)
    }

    // MARK: - Web-shop gating — each condition independently proven to fail closed to .appStore

    func test_webShopDisabledFallsBackToAppStore() {
        let config = """
            {
              "version": "routing_v8",
              "web_shop": {"enabled": false, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        _ = PurchaseRouter.shared.apply(config)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
    }

    func test_emptyUrlTemplateFallsBackToAppStore() {
        let config = """
            {
              "version": "routing_v9",
              "web_shop": {"enabled": true, "url_template": ""},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        _ = PurchaseRouter.shared.apply(config)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
    }

    func test_nonHttpsUrlTemplateFallsBackToAppStore() {
        let config = """
            {
              "version": "routing_v10",
              "web_shop": {"enabled": true, "url_template": "http://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("https"), "report was: \(result.report)")
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
    }

    func test_countryNotInEligibleListFallsBackToAppStore() {
        _ = PurchaseRouter.shared.apply(wellFormedConfig) // eligible_countries: ["US", "DE"]
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "FR"), .appStore)
    }

    func test_emptyCountryFallsBackToAppStoreWhenListRestricted() {
        _ = PurchaseRouter.shared.apply(wellFormedConfig)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: ""), .appStore)
    }

    func test_defaultSentinelCountryFallsBackToAppStoreWhenListRestricted() {
        _ = PurchaseRouter.shared.apply(wellFormedConfig)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "DEFAULT"), .appStore)
    }

    func test_emptyEligibleCountriesListMeansNoRestriction() {
        let config = """
            {
              "version": "routing_v11",
              "web_shop": {"enabled": true, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        _ = PurchaseRouter.shared.apply(config)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "ZZ"), .webShop)
    }

    func test_countryMatchIsCaseInsensitive() {
        _ = PurchaseRouter.shared.apply(wellFormedConfig)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "us"), .webShop)
    }

    // MARK: - checkoutUrlFor

    func test_checkoutUrlForSubstitutesProductAndUserIdWhenWebShopResolved() {
        PurchaseRouter.shared.configure(.init(eventSink: FakeEventSink(), playerId: "player one"))
        _ = PurchaseRouter.shared.apply(wellFormedConfig)
        let url = PurchaseRouter.shared.checkoutUrlFor(productId: "coin_pack_1", country: "US")
        XCTAssertEqual(
            url,
            "https://shop.example.com/checkout?product=coin_pack_1&user=player%20one"
        )
    }

    func test_checkoutUrlForReturnsNilWhenResolvedChannelIsNotWebShop() {
        PurchaseRouter.shared.configure(.init(eventSink: FakeEventSink(), playerId: "p1"))
        _ = PurchaseRouter.shared.apply(wellFormedConfig)
        XCTAssertNil(PurchaseRouter.shared.checkoutUrlFor(productId: "vip_pass", country: "US"))
    }

    // MARK: - Config reload atomicity

    func test_reapplyingConfigReplacesRulesAndWebShopTogetherNotPartially() {
        _ = PurchaseRouter.shared.apply(wellFormedConfig)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .webShop)

        let webShopOffConfig = """
            {
              "version": "routing_v12",
              "web_shop": {"enabled": false, "url_template": ""},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        _ = PurchaseRouter.shared.apply(webShopOffConfig)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
        XCTAssertEqual(PurchaseRouter.shared.activeVersion(), "routing_v12")
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail to compile**

Run: `swift test --filter PurchaseRouterTests`
Expected: compile error — `PurchaseRoutingModels.swift`/`PurchaseRouter.swift` don't exist yet.

- [ ] **Step 4: Create `PurchaseRoutingModels.swift`**

```swift
/// Purchase-routing data types — mirrors Android's `PurchaseRouter.Rule`/`WebShopConfig`/
/// `ApplyResult` (Kotlin nested data classes become top-level Swift types, matching this
/// codebase's existing convention for `PaywallVariant`/`Paywall`/`IapProduct`/etc.).

/// One routing rule: this product goes to this channel. The first matching rule in declaration
/// order wins.
public struct PurchaseRoutingRule: Sendable, Equatable {
    public let productId: String
    public let channel: PurchaseChannel

    public init(productId: String, channel: PurchaseChannel) {
        self.productId = productId
        self.channel = channel
    }
}

/// Web-shop channel config. `.webShop` is only ever selectable when every field here clears —
/// see `PurchaseRouter`'s doc comment for the full four-condition gate.
public struct WebShopConfig: Sendable, Equatable {
    public let enabled: Bool
    public let urlTemplate: String
    /// Empty = no country restriction. Non-empty = the caller-supplied country must be a member.
    public let eligibleCountries: Set<String>
    /// `false` when `urlTemplate` is non-empty but not `https://` — when `false`, web-shop can
    /// never be selected regardless of `enabled`. https is not a negotiable/bypassable condition:
    /// steering a paying user's card details to an unencrypted URL is a security defect, not a
    /// tolerable config typo.
    public let urlOk: Bool

    public init(enabled: Bool, urlTemplate: String, eligibleCountries: Set<String>, urlOk: Bool) {
        self.enabled = enabled
        self.urlTemplate = urlTemplate
        self.eligibleCountries = eligibleCountries
        self.urlOk = urlOk
    }

    public static let off = WebShopConfig(enabled: false, urlTemplate: "", eligibleCountries: [], urlOk: false)
}

/// Result of one `PurchaseRouter.apply(_:)` call.
public struct RoutingApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String

    public init(applied: Bool, report: String) {
        self.applied = applied
        self.report = report
    }
}
```

- [ ] **Step 5: Create `PurchaseRouter.swift`**

```swift
import Foundation

/// Multi-channel purchase router (design spec: Purchase Router phase; Android
/// `com.app.plugin.iap.PurchaseRouter`, doc 23 §C.2, doc 18 §6.1).
///
/// ═══ ONE API, CHANNEL CHOSEN BY CONFIG ═══
///
/// Game logic calls `purchase(productId:country:)` and never needs to know whether that resolves
/// to the App Store (via `IapHelper`) or an external web-shop checkout — the channel is chosen by
/// a remote-config rule set, completely hidden from game logic. This is doc 23 §C.2's literal
/// requirement.
///
/// ═══ FAIL-SAFE DIRECTION — THE ROUTER NEVER CREATES AN UNCOMPLETABLE PURCHASE PATH ═══
///
/// `.webShop` is only selected when ALL of the following are true; failing ANY of them falls back
/// to `.appStore` (the channel that always works, and the pre-router behavior):
///
///   1. a rule for this product says `.webShop`;
///   2. the `web_shop` config block has `enabled == true`;
///   3. `urlTemplate` is non-empty AND `https://` — steering a paying user's card details to an
///      unencrypted URL is a security defect, not a config typo to be lenient about;
///   4. the caller-supplied `country` is a member of `eligibleCountries` (empty list = no
///      restriction; an UNKNOWN country with a non-empty list falls back to `.appStore` — losing
///      one routing opportunity is a better failure than steering someone into a channel that
///      doesn't serve their country).
///
/// A malformed config keeps the config that's already running — same fail-safe principle as
/// `PaywallManager`/`FxRates`.
///
/// ═══ WEB-SHOP ENTITLEMENT IS NEVER SELF-GRANTED ═══
///
/// An App Store purchase gets its entitlement from a result the device itself received (that's
/// enough for a remove-ads flag — see `Entitlements`' own limits). A web-shop purchase is
/// completely invisible to the device: only the host studio's backend and payment vendor know it
/// happened. The correct flow is: vendor webhook → studio's server verifies it → studio calls
/// `grantFromServer(...)`. Granting from unverified client-supplied data (e.g. a deep link that
/// simply claims "I paid") is trivial fraud — the SDK has no vendor secret to verify anything
/// itself, and every client-side signal is forgeable (doc 18 §6.5: server-side entitlement is the
/// only source of truth).
///
/// ═══ THE ROUTER NEVER ASKS THE BRAIN — DELIBERATELY ═══
///
/// `AdsBrain.shared.shouldOfferIap()` gates an OFFER (a paywall is an invitation, and invitations
/// get gated). Here, the user has ALREADY tapped buy — gating an already-expressed purchase intent
/// protects no one and only costs revenue. The paywall asks the brain because the paywall IS the
/// invitation; the router executes an intent that already exists.
///
/// ═══ THE SDK NEVER OPENS A BROWSER ═══
///
/// Same "the SDK doesn't draw UI" boundary as `PaywallManager`: for the web-shop channel, this
/// type builds a checkout URL and hands it back via `IapResult.webCheckoutOpened` — the host app
/// decides how to open it (`SFSafariViewController`, an in-app `WKWebView`, the system browser).
public final class PurchaseRouter: @unchecked Sendable {
    public static let shared = PurchaseRouter()

    public struct Configuration: Sendable {
        public let eventSink: EventSink
        public let playerId: String
        /// The App Store execution path. Defaults to the real singleton for app usage; tests pass
        /// a fresh `IapHelper()` configured with a fake `StoreBackend` — `IapHelper.shared` has no
        /// `resetForTest()` hook and every existing `IapHelperTests` test already avoids
        /// reconfiguring it directly, for the same cross-test-leakage reason.
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

    public func configure(_ configuration: Configuration) {
        lock.lock()
        self.configuration = configuration
        lock.unlock()
    }

    private func currentConfiguration() -> Configuration? {
        lock.lock()
        defer { lock.unlock() }
        return configuration
    }

    public func activeVersion() -> String {
        lock.lock()
        defer { lock.unlock() }
        return version
    }

    public func clear() {
        lock.lock()
        rules = []
        webShop = .off
        version = "not loaded"
        lock.unlock()
    }

    // MARK: - Config

    /// Parses and applies a remote config JSON string. NEVER throws out to the caller — a
    /// malformed config just means the currently-running config keeps running, matching
    /// `PaywallManager`'s own fail-safe principle. Every rejection path below appends a `notes`
    /// entry — never a bare silent drop.
    @discardableResult
    public func apply(_ json: String?) -> RoutingApplyResult {
        guard let json, !json.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return reject("empty config")
        }
        guard let data = json.data(using: .utf8),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return reject("JSON did not parse to an object")
        }
        guard let ver = root["version"] as? String, !ver.isEmpty else {
            return reject("missing 'version' field")
        }

        var notes: [String] = []

        var shop = WebShopConfig.off
        if let block = root["web_shop"] as? [String: Any] {
            let template = (block["url_template"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let urlOk = template.hasPrefix("https://")
            if !template.isEmpty && !urlOk {
                notes.append("url_template is not https -> web_shop can never be selected")
            }
            let countries: Set<String> = {
                guard let raw = block["eligible_countries"] as? [Any] else { return [] }
                return Set(raw.compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
                    .filter { !$0.isEmpty })
            }()
            shop = WebShopConfig(
                enabled: (block["enabled"] as? Bool) ?? false,
                urlTemplate: template,
                eligibleCountries: countries,
                urlOk: urlOk
            )
        } else {
            notes.append("no 'web_shop' block -> every web_shop rule falls back to app_store")
        }

        var parsedRules: [PurchaseRoutingRule] = []
        if let rawRules = root["rules"] as? [Any] {
            for element in rawRules {
                guard let m = element as? [String: Any] else {
                    notes.append("a rule entry is not an object -> skipped")
                    continue
                }
                guard let pid = (m["product_id"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                      !pid.isEmpty else {
                    notes.append("a rule is missing a valid 'product_id' -> skipped")
                    continue
                }
                guard let channel = Self.parseChannel(m["channel"] as? String) else {
                    notes.append("rule '\(pid)': channel is invalid -> skipped")
                    continue
                }
                parsedRules.append(PurchaseRoutingRule(productId: pid, channel: channel))
            }
        } else {
            notes.append("no 'rules' array -> every product goes to app_store")
        }

        lock.lock()
        rules = parsedRules
        webShop = shop
        version = ver
        lock.unlock()

        var report = "loaded '\(ver)', \(parsedRules.count) rule(s), web_shop=\(webShopUsableForReport(shop) ? "ready" : "off/unusable")"
        if !notes.isEmpty {
            report += "\n  \u{26A0} " + notes.joined(separator: "\n  \u{26A0} ")
        }
        return RoutingApplyResult(applied: true, report: report)
    }

    /// Accepts both `"play_store"` and `"app_store"` for the native-store channel — defensive
    /// against a hand-edited config that reuses Android's own spelling; the wire vocabulary is
    /// otherwise identical to Android's `purchase_routing_android` schema.
    private static func parseChannel(_ raw: String?) -> PurchaseChannel? {
        switch raw?.lowercased() {
        case "web_shop": return .webShop
        case "play_store", "app_store": return .appStore
        default: return nil
        }
    }

    private func reject(_ reason: String) -> RoutingApplyResult {
        let report = "REJECTED routing config (\(reason)) — keeping '\(activeVersion())'"
        return RoutingApplyResult(applied: false, report: report)
    }

    // MARK: - Decision

    /// Pure function of the loaded config — no side effects, no event emission. Exposed standalone
    /// for a caller who wants to know the channel before acting on it.
    public func route(productId: String, country: String) -> PurchaseChannel {
        let (currentRules, shop) = currentRulesAndWebShop()
        guard let rule = currentRules.first(where: { $0.productId == productId }) else {
            return .appStore
        }
        if rule.channel == .webShop && webShopUsable(shop, country: country) {
            return .webShop
        }
        return .appStore
    }

    /// `nil` when the resolved channel for this product/country isn't `.webShop`, or when no
    /// player is configured yet. `{product_id}`/`{user_id}` are substituted and percent-encoded.
    public func checkoutUrlFor(productId: String, country: String) -> String? {
        guard route(productId: productId, country: country) == .webShop else { return nil }
        guard let playerId = currentConfiguration()?.playerId else { return nil }
        let (_, shop) = currentRulesAndWebShop()
        return shop.urlTemplate
            .replacingOccurrences(of: "{product_id}", with: Self.encode(productId))
            .replacingOccurrences(of: "{user_id}", with: Self.encode(playerId))
    }

    private static func encode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlQueryValueAllowed) ?? value
    }

    private func currentRulesAndWebShop() -> ([PurchaseRoutingRule], WebShopConfig) {
        lock.lock()
        defer { lock.unlock() }
        return (rules, webShop)
    }

    private func webShopUsable(_ shop: WebShopConfig, country: String) -> Bool {
        guard shop.enabled, !shop.urlTemplate.isEmpty, shop.urlOk else { return false }
        return countryAllowed(shop, country: country)
    }

    /// Only used to build the human-readable `apply()` report — reads the just-parsed config
    /// directly rather than going through the lock (the report is built right after the locked
    /// swap, before any other call could observe a different config).
    private func webShopUsableForReport(_ shop: WebShopConfig) -> Bool {
        shop.enabled && !shop.urlTemplate.isEmpty && shop.urlOk
    }

    private func countryAllowed(_ shop: WebShopConfig, country: String) -> Bool {
        if shop.eligibleCountries.isEmpty { return true }
        let normalized = country.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        // An unknown country ("" or the "DEFAULT" sentinel some upstream providers use as their
        // own fallback) must NOT be treated as a match against a restricted list — losing one
        // routing opportunity beats steering someone into a channel that doesn't serve them.
        guard !normalized.isEmpty, normalized != "DEFAULT" else { return false }
        return shop.eligibleCountries.contains(normalized)
    }

    // MARK: - Execution (Task 2 replaces these stubs)

    /// Placeholder for Task 2 — replaced with the real App Store/web-shop dispatch there. Kept
    /// here only so Task 1 compiles; Task 1's own tests never call this.
    public func purchase(productId: String, country: String) async -> IapResult {
        .failed(code: -1, message: "PurchaseRouter.purchase not yet implemented")
    }

    /// Placeholder for Task 2 — replaced with the real server-granted-entitlement flow there.
    /// Kept here only so Task 1 compiles; Task 1's own tests never call this.
    public func grantFromServer(
        productId: String,
        orderId: String,
        priceMicros: Int64,
        currencyCode: String,
        quantity: Int = 1,
        isSubscription: Bool = false
    ) async -> Bool {
        false
    }
}

private extension CharacterSet {
    /// RFC 3986 query-value-safe set: letters, digits, and `-._~` — everything else (including
    /// `&`, `=`, `+`, space) gets percent-encoded. Deliberately stricter than `.urlQueryAllowed`
    /// (which leaves `&`/`=`/`+` unescaped, since those are meaningful when the value is dropped
    /// into a query string unescaped) — a player id or product id containing any of those
    /// characters must not be able to inject an extra query parameter into the studio's
    /// `url_template`.
    static let urlQueryValueAllowed: CharacterSet = {
        var set = CharacterSet.alphanumerics
        set.insert(charactersIn: "-._~")
        return set
    }()
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `swift test --filter PurchaseRouterTests`
Expected: all tests in Step 2 PASS. If `test_checkoutUrlForSubstitutesProductAndUserIdWhenWebShopResolved`
fails on the exact percent-encoding of the space in `"player one"`, confirm it encodes to `%20`
(not `+`) per `urlQueryValueAllowed` above — adjust the test's expected string only if you find the
actual encoding differs in a way that's still correct per RFC 3986, never weaken the assertion to
"contains" to dodge a real bug.

- [ ] **Step 7: Run the full suite to confirm no regressions**

Run: `swift test`
Expected: PASS (all tests including the pre-existing 274 plus this task's new ones). If any test
elsewhere in the suite starts failing, do not proceed — it means Step 1's `IapResult` addition
broke an existing exhaustive `switch` somewhere; find it and add a case, do not weaken the switch
into a `default:`.

- [ ] **Step 8: Commit**

```bash
git add Sources/MonetizationBrainCore/Iap/IapModels.swift \
        Sources/MonetizationBrainCore/Iap/PurchaseRoutingModels.swift \
        Sources/MonetizationBrainCore/Iap/PurchaseRouter.swift \
        Tests/MonetizationBrainCoreTests/PurchaseRouterTests.swift
git commit -m "feat: add PurchaseRoutingModels + PurchaseRouter config/parsing/routing (execution stubbed for Task 2)

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Execution — App Store delegation, web-shop checkout, server-granted entitlement

**Files:**
- Modify: `Sources/MonetizationBrainCore/Iap/PurchaseRouter.swift`
- Modify: `Tests/MonetizationBrainCoreTests/PurchaseRouterTests.swift`

- [ ] **Step 1: Write the failing tests for `purchase(productId:country:)`, `grantFromServer`, and the locked-URL regression test**

Append this content to the SAME `PurchaseRouterTests` class from Task 1 (add a fake `StoreBackend`
above the existing `FakeEventSink` — do not create a second test file). Do not modify any existing
Task 1 test.

```swift
    // Append inside `final class PurchaseRouterTests: XCTestCase { ... }`, alongside the
    // existing `FakeEventSink`:

    /// In-memory `StoreBackend` fake — same shape as `IapHelperTests`' own file-local fake,
    /// redeclared here per this project's established per-file-fake convention (no shared test
    /// target for fakes).
    private final class FakeStoreBackend: StoreBackend, @unchecked Sendable {
        var productsToReturn: [IapProduct] = []
        var purchaseResultToReturn: IapResult = .userCanceled
        func products(for identifiers: [String]) async throws -> [IapProduct] {
            productsToReturn.filter { identifiers.contains($0.productId) }
        }
        func purchase(_ product: IapProduct) async throws -> IapResult { purchaseResultToReturn }
        func currentEntitlements() async throws -> [IapPurchase] { [] }
        func transactionUpdates() -> AsyncStream<IapPurchase> { AsyncStream { _ in } }
        func finish(_ purchase: IapPurchase) async {}
    }

    /// `IapHelper.purchase(_:)` returns `.failed("product not ready")` unless the product was
    /// already queried/cached — `setProducts` + `queryProducts()` populate that cache from the
    /// fake backend's `productsToReturn`, matching `IapHelperTests`' own setup sequence exactly.
    /// Takes `productId` explicitly since different tests exercise the App Store fallback for
    /// different products (the web-shop-eligible "coin_pack_1" in the defensive-fallback test,
    /// the App-Store-only "vip_pass" in the direct-delegation test) — caching the wrong product id
    /// would make `purchase(_:)` return `.failed("product not ready")` instead of the intended
    /// fake result, silently invalidating the test.
    private func makeAppStoreIapHelper(productId: String, purchaseResult: IapResult, eventSink: EventSink) async -> IapHelper {
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [IapProduct(
            productId: productId, type: .nonConsumable, formattedPrice: "$4.99",
            priceMicros: 4_990_000, currencyCode: "USD", title: "Test Product"
        )]
        backend.purchaseResultToReturn = purchaseResult
        await helper.configure(.init(backend: backend, eventSink: eventSink, playerId: "p1"))
        helper.setProducts([productId])
        _ = await helper.queryProducts()
        return helper
    }

    private let samplePurchase = IapPurchase(
        productId: "vip_pass", orderId: "order_appstore_1", purchaseToken: "jws_token",
        purchaseTimeMs: 1_700_000_000_000, state: .purchased, isFinished: true,
        isAutoRenewing: false, quantity: 1, priceMicros: 4_990_000, currencyCode: "USD"
    )

    // MARK: - purchase(productId:country:) — App Store branch

    func test_purchaseAppStoreBranchDelegatesToConfiguredIapHelperUnmodified() async {
        let eventSink = FakeEventSink()
        let iapHelper = await makeAppStoreIapHelper(productId: "vip_pass", purchaseResult: .success(samplePurchase), eventSink: eventSink)
        PurchaseRouter.shared.configure(.init(eventSink: eventSink, playerId: "p1", iapHelper: iapHelper))
        // No apply() call: undeclared product -> .appStore, exercising the default-routing path.

        let result = await PurchaseRouter.shared.purchase(productId: "vip_pass", country: "US")

        guard case .success(let purchase) = result else {
            return XCTFail("expected .success, got \(result)")
        }
        XCTAssertEqual(purchase.orderId, "order_appstore_1")
    }

    // MARK: - purchase(productId:country:) — web-shop branch

    func test_purchaseWebShopBranchReturnsWebCheckoutOpenedAndEmitsOneEvent() async {
        let eventSink = FakeEventSink()
        PurchaseRouter.shared.configure(.init(eventSink: eventSink, playerId: "player one"))
        _ = PurchaseRouter.shared.apply(wellFormedConfig)

        let result = await PurchaseRouter.shared.purchase(productId: "coin_pack_1", country: "US")

        guard case .webCheckoutOpened(let productId, let url) = result else {
            return XCTFail("expected .webCheckoutOpened, got \(result)")
        }
        XCTAssertEqual(productId, "coin_pack_1")
        XCTAssertEqual(url, "https://shop.example.com/checkout?product=coin_pack_1&user=player%20one")

        let events = await eventSink.sentEvents
        XCTAssertEqual(events.count, 1)
        XCTAssertEqual(events[0].eventName, "web_checkout_opened")
        XCTAssertEqual(events[0].eventData["product_id"], .string("coin_pack_1"))
        XCTAssertEqual(events[0].eventData["channel"], .string("WEB_SHOP"))
        XCTAssertEqual(events[0].eventData["routing_version"], .string("routing_v1"))
        XCTAssertEqual(events[0].userProperty.userId, "player one")
    }

    /// Regression test for the "checkout URL is never logged" rule — the studio's `url_template`
    /// can embed a session token, and logging the resolved URL would leak it into the log
    /// pipeline. Every string value in the emitted event must be checked, not just a `contains`
    /// on the whole payload, so this actually proves the URL isn't hiding in an unexpected field.
    func test_webCheckoutOpenedEventNeverCarriesTheResolvedUrl() async {
        let eventSink = FakeEventSink()
        PurchaseRouter.shared.configure(.init(eventSink: eventSink, playerId: "p1"))
        _ = PurchaseRouter.shared.apply(wellFormedConfig)

        let result = await PurchaseRouter.shared.purchase(productId: "coin_pack_1", country: "US")
        guard case .webCheckoutOpened(_, let url) = result else {
            return XCTFail("expected .webCheckoutOpened, got \(result)")
        }

        let events = await eventSink.sentEvents
        XCTAssertEqual(events.count, 1)
        for (_, value) in events[0].eventData {
            if case .string(let s) = value {
                XCTAssertFalse(s.contains(url), "event field leaked the checkout URL: \(s)")
            }
        }
    }

    // Note: `purchase()`'s "route said .webShop but checkoutUrlFor returned nil" fallback branch
    // is deliberately NOT covered by a test — it's only reachable through a `clear()`/`configure()`
    // race between the two independent `currentConfiguration()` reads inside `route`/
    // `checkoutUrlFor` (see that method's doc comment), not through any input this test file can
    // construct deterministically. Do not add a test that fakes this by passing an empty
    // `playerId` — `Configuration.playerId` is non-optional, so an empty string does not make
    // `checkoutUrlFor` return `nil`, and a test built on that premise would silently test nothing.

    // MARK: - grantFromServer

    private func makeAdsBrainConfiguration(playerId: String, eventSink: EventSink) -> AdsBrain.Configuration {
        AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: eventSink,
            playerId: playerId,
            decisionEngine: DecisionEngine(store: InMemoryBrainStore(), eventSink: eventSink)
        )
    }

    /// In-memory `BrainKeyValueStore` — same shape as `PaywallManagerTests`' own fixture,
    /// redeclared file-locally per this project's established per-file-fake convention.
    private final class InMemoryBrainStore: BrainKeyValueStore, @unchecked Sendable {
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? { values[key] }
        func setString(_ value: String, forKey key: String) { values[key] = value }
        func removeObject(forKey key: String) { values.removeValue(forKey: key) }
    }

    func test_grantFromServerWithBlankProductIdOrOrderIdReturnsFalseAndGrantsNothing() async {
        Entitlements.shared.resetForTest()
        let eventSink = FakeEventSink()
        PurchaseRouter.shared.configure(.init(eventSink: eventSink, playerId: "p1"))

        let result1 = await PurchaseRouter.shared.grantFromServer(
            productId: "", orderId: "order_1", priceMicros: 1_000_000, currencyCode: "USD"
        )
        let result2 = await PurchaseRouter.shared.grantFromServer(
            productId: "coin_pack_1", orderId: "", priceMicros: 1_000_000, currencyCode: "USD"
        )

        XCTAssertFalse(result1)
        XCTAssertFalse(result2)
        XCTAssertFalse(Entitlements.shared.isActive("coin_pack_1"))
        let events = await eventSink.sentEvents
        XCTAssertTrue(events.isEmpty)
    }

    func test_grantFromServerGrantsEntitlementEmitsIapPurchaseEventAndFeedsAdsBrainPurchaseCount() async {
        Entitlements.shared.resetForTest()
        AdsBrain.shared.resetForTest()
        let eventSink = FakeEventSink()
        PurchaseRouter.shared.configure(.init(eventSink: eventSink, playerId: "p1"))
        await AdsBrain.shared.configure(makeAdsBrainConfiguration(playerId: "p1", eventSink: eventSink))
        AdsBrain.shared.enabled = true
        // `AdsBrain.currentState()`/the purchase-count update inside `onPurchase` both read the
        // live session (`withSession` no-ops with no session yet) — `onSessionStart()` is required
        // before either means anything, matching `PaywallManagerTests`' own established setup.
        await AdsBrain.shared.onSessionStart()
        let before = AdsBrain.shared.currentState()?.purchaseCountLifetime ?? 0

        let result = await PurchaseRouter.shared.grantFromServer(
            productId: "coin_pack_1", orderId: "order_ws_1", priceMicros: 4_990_000,
            currencyCode: "USD", quantity: 1, isSubscription: false
        )

        XCTAssertTrue(result)
        XCTAssertTrue(Entitlements.shared.isActive("coin_pack_1"))

        let events = await eventSink.sentEvents
        XCTAssertEqual(events.count, 1)
        XCTAssertEqual(events[0].eventName, "iap_purchase")
        XCTAssertEqual(events[0].eventData["product_id"], .string("coin_pack_1"))
        XCTAssertEqual(events[0].eventData["order_id"], .string("order_ws_1"))
        XCTAssertEqual(events[0].eventData["price_micros"], .int(4_990_000))
        XCTAssertEqual(events[0].eventData["currency_code"], .string("USD"))
        XCTAssertEqual(events[0].eventData["quantity"], .int(1))
        XCTAssertEqual(events[0].eventData["channel"], .string("WEB_SHOP"))
        XCTAssertEqual(events[0].eventData["is_subscription"], .int(0))
        XCTAssertEqual(events[0].eventData["purchase_token"], .string(""))

        XCTAssertEqual(AdsBrain.shared.currentState()?.purchaseCountLifetime, before + 1)
    }

    func test_grantFromServerIsSubscriptionFlagReflectedInEvent() async {
        Entitlements.shared.resetForTest()
        let eventSink = FakeEventSink()
        PurchaseRouter.shared.configure(.init(eventSink: eventSink, playerId: "p1"))

        _ = await PurchaseRouter.shared.grantFromServer(
            productId: "vip_sub", orderId: "order_ws_2", priceMicros: 9_990_000,
            currencyCode: "USD", quantity: 1, isSubscription: true
        )

        let events = await eventSink.sentEvents
        XCTAssertEqual(events.last?.eventData["is_subscription"], .int(1))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `swift test --filter PurchaseRouterTests`
Expected: FAIL — `purchase()` returns the Step-1-stub `.failed(...)`, `grantFromServer` returns
`false` and grants nothing.

- [ ] **Step 3: Replace the two execution stubs in `PurchaseRouter.swift`**

Replace the entire `// MARK: - Execution (Task 2 replaces these stubs)` section (both stub methods)
with:

```swift
    // MARK: - Execution

    /// The one API game logic calls. App Store branch delegates to `configuration.iapHelper
    /// .purchase(_:)` unchanged; web-shop branch builds a checkout URL, emits
    /// `web_checkout_opened`, and returns `.webCheckoutOpened` — never opens anything itself. If
    /// `route()` resolves `.webShop` but `checkoutUrlFor` can't build a URL, falls back to the App
    /// Store path rather than handing the caller a dead result. This is only reachable through a
    /// narrow race (a concurrent `clear()`/`configure()` call landing between the two independent
    /// `currentConfiguration()` reads `route`/`checkoutUrlFor` each take) — NOT a normal
    /// "no player configured" case, since `Configuration.playerId` is non-optional and this whole
    /// method already returned above if `configuration` were `nil`. Matches Android's own
    /// "in theory unreachable, but fall back rather than dead-end" comment at this exact spot;
    /// like Phase 5's own analogous config-generation race (documented, not unit-tested — see
    /// design spec), this is accepted as a documented defensive fallback rather than something a
    /// deterministic test can exercise.
    public func purchase(productId: String, country: String) async -> IapResult {
        guard let configuration = currentConfiguration() else {
            return .failed(code: -1, message: "PurchaseRouter not configured")
        }
        guard route(productId: productId, country: country) == .webShop,
              let url = checkoutUrlFor(productId: productId, country: country) else {
            return await configuration.iapHelper.purchase(productId)
        }
        await configuration.eventSink.send(SdkEvent(
            eventName: "web_checkout_opened",
            eventData: [
                "product_id": .string(productId),
                "channel": .string(PurchaseChannel.webShop.rawValue),
                "routing_version": .string(activeVersion())
            ],
            userProperty: UserProperty(userId: configuration.playerId)
        ))
        return .webCheckoutOpened(productId: productId, url: url)
    }

    /// Grants a web-shop entitlement for a transaction the CALLER has already independently
    /// verified — typically a webhook-confirmed vendor payment relayed by the host app's own
    /// backend.
    ///
    /// ⚠️ The SDK does NOT and CANNOT verify this itself: it has no vendor secret, and every
    /// client-side signal (a deep link that simply claims "I paid") is forgeable. Calling this
    /// with unverified data is trivial fraud — server-side verification is the only source of
    /// truth (doc 18 §6.5). A retried webhook is safe: the backend dedupes on `orderId`, and
    /// `Entitlements.grant` is idempotent.
    ///
    /// Unlike Android's own `grantFromServer`, this ALSO feeds `AdsBrain.shared.onPurchase(usd:)`
    /// — a web-shop sale is exactly as much a successful purchase as an App Store one, and this
    /// port's own binding invariant is that the payer-guardrail signal fires unconditionally on
    /// every successful purchase (see design spec §3.2 for why Android's own source doesn't do
    /// this, and why that's treated as a gap here, not a pattern to replicate).
    ///
    /// - Returns: `false` only when `productId`/`orderId` are blank — nothing is granted.
    @discardableResult
    public func grantFromServer(
        productId: String,
        orderId: String,
        priceMicros: Int64,
        currencyCode: String,
        quantity: Int = 1,
        isSubscription: Bool = false
    ) async -> Bool {
        guard !productId.isEmpty, !orderId.isEmpty else { return false }
        Entitlements.shared.grant(productId)
        if let eventSink = currentConfiguration()?.eventSink {
            await eventSink.send(SdkEvent(
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
                userProperty: UserProperty(userId: currentConfiguration()?.playerId ?? "")
            ))
        }
        let usd = FxRates.toUsd(priceMicros: priceMicros, currencyCode: currencyCode)
        await AdsBrain.shared.onPurchase(usd: usd ?? 0.0)
        return true
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `swift test --filter PurchaseRouterTests`
Expected: every test from Task 1 AND Task 2 passes.

- [ ] **Step 5: Run the full suite to confirm no regressions**

Run: `swift test`
Expected: PASS.

- [ ] **Step 6: Self-review before committing**

Re-read the full `purchase(productId:country:)` and `grantFromServer` bodies against the Global
Constraints at the top of this plan: confirm no `lock.lock()`/`.unlock()` appears directly inside
either `async` body (both route through `currentConfiguration()`, a synchronous helper — this is
correct as written above; verify it stayed that way if you made any edits), and confirm
`eventSink.send`/`configuration.iapHelper.purchase`/`Entitlements.shared.grant`/
`AdsBrain.shared.onPurchase` are never called while `lock` is held (none of them are, in the code
above — the lock is only ever touched inside the tiny synchronous helpers).

- [ ] **Step 7: Commit**

```bash
git add Sources/MonetizationBrainCore/Iap/PurchaseRouter.swift \
        Tests/MonetizationBrainCoreTests/PurchaseRouterTests.swift
git commit -m "feat: PurchaseRouter execution — App Store delegation, web-shop checkout, grantFromServer

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: README update

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Verify the current test count**

Run: `swift test 2>&1 | tail -5`
Note the exact "Executed N tests" count — use that real number in Step 2, not a guess.

- [ ] **Step 2: Add the Purchase Router phase section**

Follow the exact pattern of the existing Phase 5 entry (the paragraph starting "Phase 5 ('Paywall')
is complete and merged...", and its own "### Known deferred subsystems (from design spec §1, Phase
5)" section further down). Add a new paragraph immediately after the Phase 5 paragraph (before its
own "Known deferred subsystems" heading — that heading and everything after it moves down to make
room), describing:

- What this phase ports: Android's `PurchaseRouter` — one `purchase(productId:country:)` API,
  channel chosen by remote config, App Store branch delegating to `IapHelper`, web-shop branch
  returning a checkout URL via `IapResult.webCheckoutOpened` (SDK never opens a browser).
- The four-condition fail-safe gate (enabled + https + eligible country + a matching rule), all
  falling back to App Store — the channel that always works.
- Web-shop entitlement is never self-granted — only through `grantFromServer(...)`, called by the
  host app once its own backend has verified a vendor webhook.
- The router never asks the brain (an already-expressed purchase intent isn't an offer to gate).
- The one deliberate deviation from Android: `grantFromServer` DOES feed
  `AdsBrain.shared.onPurchase(usd:)` (Android's own source doesn't — traced as a real gap in
  Android's payer-guardrail wiring, not a pattern worth replicating; see design spec §3.2).
- New event: `web_checkout_opened` (the routing funnel's numerator) — and that the checkout URL
  itself is never logged in any event, by design.
- The real test count from Step 1.
- Link to `docs/superpowers/specs/2026-09-04-purchase-router-design.md`.
- State plainly whether the whole-branch review and security review have happened yet — if this
  README update lands BEFORE those reviews (i.e. you are the Task 3 implementer, not the person
  doing the final pre-merge pass), you MUST say the phase is "implemented" and the reviews are
  "pending"/"scheduled" — do NOT write "complete and merged." This exact mistake (claiming a phase
  complete-and-merged while its own review was still pending) was made and caught in BOTH Phase 4
  and Phase 5's own README reviews — do not make it a third time. The final whole-branch-review
  pass will update this wording again once the reviews genuinely finish, exactly like it did for
  Phases 4 and 5.

- [ ] **Step 3: Add the "Known deferred subsystems" section for this phase**

Immediately after the new phase paragraph (and its own placement in the file — after Phase 5's
"Known deferred subsystems" section, matching the file's established phase-by-phase ordering), add:

```markdown
### Known deferred subsystems (from design spec §1, Purchase Router phase)

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
```

- [ ] **Step 4: Verify the README's phase ordering and links are all still correct**

Read through the whole file once after editing: confirm no earlier phase's section was
accidentally altered, confirm the new section's markdown heading level matches its siblings exactly
(`###`), and confirm the link to the design spec resolves to a real file
(`docs/superpowers/specs/2026-09-04-purchase-router-design.md`).

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: update README for Purchase Router phase

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```
