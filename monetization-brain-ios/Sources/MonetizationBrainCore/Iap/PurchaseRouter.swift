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

    /// Test-only, and INTERNAL on purpose. This was `public clear()` — the exact Android footgun
    /// design spec §3.8 diagnoses: any host code could call it in production and reset a live
    /// routing config back to defaults, wiping an operator's remote-config state with no signal
    /// that it happened. Narrowed to match `Entitlements`/`ConsentState`/`AdsBrain`/
    /// `SubscriptionLifecycle`, which all keep their reset hook inside the module under this name.
    ///
    /// Deliberately does NOT clear `configuration` — `configure(_:)` is host wiring (event sink,
    /// player id, `IapHelper`), not remote-config state, and several tests rely on it surviving
    /// (see `test_grantFromServerWhenNotConfiguredReturnsFalseAndGrantsNothing`, which uses a
    /// fresh instance precisely because of this).
    func resetForTest() {
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
            if template.isEmpty {
                notes.append("'web_shop.url_template' is empty -> web_shop can never be selected")
            } else if !urlOk {
                notes.append("url_template is not https -> web_shop can never be selected")
            }
            var countries: Set<String> = []
            if let rawCountries = block["eligible_countries"] {
                if let rawArray = rawCountries as? [Any] {
                    let usable = rawArray.compactMap { ($0 as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
                        .filter { !$0.isEmpty }
                    if usable.count != rawArray.count {
                        // Element-level drops point the SAFE way (a shorter list can only route
                        // fewer people to web-shop) but must still not be silent: an operator
                        // whose list half survived would otherwise never learn why a country they
                        // configured never routes. Duplicates and casing noise are NOT counted
                        // here — they collapse legitimately in the `Set` below, after this check.
                        notes.append("'eligible_countries': \(rawArray.count - usable.count) of \(rawArray.count) entries are not a non-empty string -> dropped")
                    }
                    countries = Set(usable)
                } else {
                    notes.append("'eligible_countries' is not an array -> treated as no country restriction")
                }
            }
            // else: key absent -> countries stays empty -> no restriction (legitimate, not a note-worthy case)

            // ═══ 'enabled' MUST BE A REAL JSON BOOLEAN — `as? Bool` FAILS *OPEN* HERE ═══
            //
            // This read used to be `(block["enabled"] as? Bool) ?? false`, which looks fail-safe
            // and is not: `JSONSerialization` bridges value-preservingly, so `1 as? Bool` SUCCEEDS
            // and returns `true`. `{"web_shop": {"enabled": 1}}` therefore silently ENABLED
            // external web-shop checkout — steering a paying customer to an off-App-Store
            // checkout URL on the strength of an operator typo, with nothing in the returned
            // report to say so (and `0` silently disabled it, equally silently).
            //
            // `JsonConfigValue.flag` accepts only a real JSON boolean and degrades to `false` WITH
            // a note otherwise — the same treatment `eligible_countries` above already got from
            // Phase 6's security review, and the rule design spec §3.8 states for this package.
            // See `JsonConfigValue.isJsonBoolean` for why the CoreFoundation type is the only
            // honest discriminator.
            shop = WebShopConfig(
                enabled: JsonConfigValue.flag(block["enabled"], name: "web_shop.enabled", defaultingTo: false, notes: &notes),
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

    /// One atomic decision: the resolved channel AND the exact `WebShopConfig` snapshot that decision
    /// was validated against — computed from a SINGLE lock acquisition. A concurrent `apply()` must
    /// never be able to swap `webShop` between validating the https/enabled/country gate and building
    /// a checkout URL from it; that gate is a documented hard requirement (see the class doc comment's
    /// FAIL-SAFE DIRECTION section), not a soft check that's safe to re-derive from a stale or newer
    /// snapshot than the one that passed it.
    private func resolvedChannel(productId: String, country: String) -> (channel: PurchaseChannel, shop: WebShopConfig) {
        let (currentRules, shop) = currentRulesAndWebShop()
        guard let rule = currentRules.first(where: { $0.productId == productId }) else {
            return (.appStore, shop)
        }
        if rule.channel == .webShop && webShopUsable(shop, country: country) {
            return (.webShop, shop)
        }
        return (.appStore, shop)
    }

    /// Pure function of the loaded config — no side effects, no event emission. Exposed standalone
    /// for a caller who wants to know the channel before acting on it.
    public func route(productId: String, country: String) -> PurchaseChannel {
        resolvedChannel(productId: productId, country: country).channel
    }

    /// `nil` when the resolved channel for this product/country isn't `.webShop`, or when no player is
    /// configured yet. `{product_id}`/`{user_id}` are substituted and percent-encoded. Builds the URL
    /// from the SAME locked snapshot `resolvedChannel` validated the gate against — see that method's
    /// doc comment for why this must be one atomic read, not two.
    public func checkoutUrlFor(productId: String, country: String) -> String? {
        let resolved = resolvedChannel(productId: productId, country: country)
        guard resolved.channel == .webShop else { return nil }
        guard let playerId = currentConfiguration()?.playerId else { return nil }
        return resolved.shop.urlTemplate
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

    /// Same three conditions as `webShopUsable` MINUS the country check — there's no caller-supplied
    /// country available yet when this is called (right after `apply()`'s locked config swap, for the
    /// human-readable report string only).
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

    // MARK: - Execution

    /// The one API game logic calls. App Store branch delegates to `configuration.iapHelper
    /// .purchase(_:)` unchanged; web-shop branch builds a checkout URL, emits
    /// `web_checkout_opened`, and returns `.webCheckoutOpened` — never opens anything itself.
    /// `checkoutUrlFor` is the single source of truth for "is this a validated web-shop purchase" —
    /// it is now internally atomic (see `resolvedChannel`'s doc comment), so this method calls it
    /// exactly once rather than re-deriving the same decision via a separate `route()` call. A `nil`
    /// result covers BOTH cases: the resolved channel is `.appStore`, or the narrow "no player
    /// configured yet" edge case — either way, falling back to the App Store path is correct. The
    /// TOCTOU race this whole fix closed (a concurrent `apply()` swapping `webShop` between the
    /// gate check and the URL build) is no longer possible: `checkoutUrlFor` validates the gate and
    /// builds the URL from the identical locked snapshot in one call.
    public func purchase(productId: String, country: String) async -> IapResult {
        guard let configuration = currentConfiguration() else {
            return .failed(code: -1, message: "PurchaseRouter not configured")
        }
        guard let url = checkoutUrlFor(productId: productId, country: country) else {
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
    /// - Returns: `false` when `productId`/`orderId` are blank, or when the router hasn't been
    ///   `configure(_:)`d yet — nothing is granted either way (a grant whose `iap_purchase` revenue
    ///   event has nowhere to go must not be reported as a success).
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
        guard let configuration = currentConfiguration() else { return false }
        Entitlements.shared.grant(productId)
        await configuration.eventSink.send(SdkEvent(
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
        ))
        let usd = FxRates.toUsd(priceMicros: priceMicros, currencyCode: currencyCode)
        await AdsBrain.shared.onPurchase(usd: usd ?? 0.0)
        return true
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
