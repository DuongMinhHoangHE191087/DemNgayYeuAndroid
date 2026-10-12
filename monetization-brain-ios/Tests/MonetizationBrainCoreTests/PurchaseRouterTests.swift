import XCTest
@testable import MonetizationBrainCore

final class PurchaseRouterTests: XCTestCase {
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
        func subscriptionSnapshots() async throws -> [SubscriptionSnapshot] { [] }
        func transactionUpdates() -> AsyncStream<IapPurchase> { AsyncStream { _ in } }
        func finish(_ purchase: IapPurchase) async {}
    }

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
        PurchaseRouter.shared.resetForTest()
        AdsBrain.shared.resetForTest()
        Entitlements.shared.resetForTest()
    }

    override func tearDown() {
        PurchaseRouter.shared.resetForTest()
        AdsBrain.shared.resetForTest()
        Entitlements.shared.resetForTest()
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

    /// Regression test for the "eligible_countries present but wrong type" fail-open gap: a
    /// studio typo like `"eligible_countries": "US"` (a bare string, not an array) must NOT
    /// silently collapse to an empty set (which `countryAllowed` reads as "no restriction" — the
    /// opposite of what a restricted config intended, and the file's only fail-*open* path). The
    /// config is still applied (malformed `eligible_countries` isn't fatal to the rest of the
    /// config) but must leave a note so the studio can see their typo, and must NOT be asserted to
    /// make web_shop unselectable — only the other three gate conditions still govern that.
    func test_eligibleCountriesPresentButWrongTypeAppliesWithNoteAndDoesNotSilentlyRestrict() {
        let config = """
            {
              "version": "routing_v13",
              "web_shop": {
                "enabled": true,
                "url_template": "https://s.example.com/{product_id}",
                "eligible_countries": "US"
              },
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("eligible_countries"), "report was: \(result.report)")
        // The other three gate conditions (enabled, https, a matching rule) are all satisfied, so
        // the malformed eligible_countries must not silently make web_shop selectable for a
        // country nothing intended to allow -- the note above is the diagnostic, not a behavior
        // assertion either way; this just proves the gate keeps evaluating the OTHER conditions
        // rather than crashing or applying nothing.
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "FR"), .webShop)
    }

    /// The key genuinely ABSENT (not present-but-wrong-type) must still mean "no restriction" with
    /// no note -- companion to the malformed-type test above, proving the fix didn't regress the
    /// legitimate "no restriction configured" case.
    func test_eligibleCountriesKeyAbsentMeansNoRestrictionWithNoNote() {
        let config = """
            {
              "version": "routing_v14",
              "web_shop": {"enabled": true, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.contains("eligible_countries"), "report was: \(result.report)")
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

    /// Spec §6 percent-encoding coverage: a player id containing `&` must be percent-encoded
    /// rather than appearing as a literal `&`, which would let it inject an extra query parameter
    /// into the studio's `url_template` -- the exact property `urlQueryValueAllowed`'s own doc
    /// comment claims to defend against. Companion to the existing space-encoding test above.
    func test_checkoutUrlForPercentEncodesAmpersandInPlayerIdToPreventQueryInjection() {
        PurchaseRouter.shared.configure(.init(eventSink: FakeEventSink(), playerId: "p1&admin=1"))
        _ = PurchaseRouter.shared.apply(wellFormedConfig)
        let url = PurchaseRouter.shared.checkoutUrlFor(productId: "coin_pack_1", country: "US")
        XCTAssertNotNil(url)
        XCTAssertFalse(url?.contains("&admin=1") ?? true, "raw & would inject a query parameter: \(url ?? "nil")")
        XCTAssertTrue(url?.contains("%26") ?? false, "expected & to be percent-encoded")
    }

    func test_checkoutUrlForReturnsNilWhenResolvedChannelIsNotWebShop() {
        PurchaseRouter.shared.configure(.init(eventSink: FakeEventSink(), playerId: "p1"))
        _ = PurchaseRouter.shared.apply(wellFormedConfig)
        XCTAssertNil(PurchaseRouter.shared.checkoutUrlFor(productId: "vip_pass", country: "US"))
    }

    /// Consistency regression test for the TOCTOU fix: `route` and `checkoutUrlFor` must agree with
    /// each other for the SAME config state, since both now derive their decision from a single
    /// atomic `resolvedChannel` lock read. `checkoutUrlFor` must never return a URL built from a
    /// config that failed its own https/enabled gate, regardless of which method a caller asks
    /// first -- a real data race isn't something a deterministic unit test can construct, but this
    /// locks the invariant the fix restores.
    func test_checkoutUrlForNeverReturnsANonHttpsOrDisabledWebShopUrlEvenWhenCalledIndependentlyOfRoute() {
        PurchaseRouter.shared.configure(.init(eventSink: FakeEventSink(), playerId: "p1"))
        let config = """
            {
              "version": "routing_v13",
              "web_shop": {"enabled": true, "url_template": "http://not-https.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        _ = PurchaseRouter.shared.apply(config)
        // route() must say .appStore (non-https template fails the gate)...
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
        // ...and checkoutUrlFor must independently agree -- it must NEVER return a URL built from a
        // config that failed its own gate, regardless of which method a caller asks first.
        XCTAssertNil(PurchaseRouter.shared.checkoutUrlFor(productId: "coin_pack_1", country: "US"))
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

    /// Regression test for the silent-event-drop gap: `grantFromServer` on a router that was
    /// never `configure(_:)`d used to still grant the entitlement and return `true`, with the
    /// `iap_purchase` revenue event silently vanishing (no `eventSink` to send it through). Now
    /// it must match `purchase(_:)`'s own not-configured handling and refuse the whole operation
    /// -- a grant whose revenue event never reaches the pipeline must not be reported as a success.
    ///
    /// Uses a fresh `PurchaseRouter()` instance rather than `.shared`: `PurchaseRouter.resetForTest()`
    /// (called in `setUp()`) resets `rules`/`webShop`/`version` but deliberately leaves
    /// `configuration` alone, so `.shared` can carry a `configure(_:)` call over from an earlier
    /// test in this file (test ordering isn't guaranteed, but in practice a prior grantFromServer
    /// test's `configure(_:)` would otherwise leak in) -- a fresh instance is the only
    /// deterministic way to prove the genuinely-unconfigured case, same reasoning as this file's
    /// own `makeAppStoreIapHelper` fresh-`IapHelper()` pattern.
    func test_grantFromServerWhenNotConfiguredReturnsFalseAndGrantsNothing() async {
        let router = PurchaseRouter()

        let result = await router.grantFromServer(
            productId: "coin_pack_1", orderId: "order_1", priceMicros: 1_000_000, currencyCode: "USD"
        )

        XCTAssertFalse(result)
        XCTAssertFalse(Entitlements.shared.isActive("coin_pack_1"))
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

        // `eventSink` is shared with `AdsBrain.configure(...)` above, which unconditionally emits
        // its own `sdk_configured` lifecycle event through it — filter by name rather than assume
        // position/count, matching `PaywallManagerTests`' own established pattern for a sink shared
        // with `AdsBrain`, so this test doesn't couple itself to `AdsBrain.configure`'s unrelated
        // event emission.
        let events = await eventSink.sentEvents
        XCTAssertEqual(events.filter { $0.eventName == "iap_purchase" }.count, 1)
        let iapPurchaseEvent = events.first { $0.eventName == "iap_purchase" }
        XCTAssertEqual(iapPurchaseEvent?.eventData["product_id"], .string("coin_pack_1"))
        XCTAssertEqual(iapPurchaseEvent?.eventData["order_id"], .string("order_ws_1"))
        XCTAssertEqual(iapPurchaseEvent?.eventData["price_micros"], .int(4_990_000))
        XCTAssertEqual(iapPurchaseEvent?.eventData["currency_code"], .string("USD"))
        XCTAssertEqual(iapPurchaseEvent?.eventData["quantity"], .int(1))
        XCTAssertEqual(iapPurchaseEvent?.eventData["channel"], .string("WEB_SHOP"))
        XCTAssertEqual(iapPurchaseEvent?.eventData["is_subscription"], .int(0))
        XCTAssertEqual(iapPurchaseEvent?.eventData["purchase_token"], .string(""))

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

    // MARK: - Hardening: 'web_shop.enabled' must be a REAL JSON boolean

    /// Regression test for the `NSNumber`/`Bool` bridging fail-open hole — the same class Phase 6's
    /// own security review forced onto `eligible_countries` in this file, on the same
    /// financially-sensitive path.
    ///
    /// `JSONSerialization` bridges value-preservingly, so `1 as? Bool` SUCCEEDS and returns `true`
    /// (empirically verified: a JSON `1` arrives as `__NSCFNumber` and still casts to `Bool`).
    /// `(block["enabled"] as? Bool) ?? false` therefore read `{"enabled": 1}` as ENABLED, silently
    /// switching on external web-shop checkout — steering a paying customer to an off-App-Store
    /// checkout URL on the strength of an operator typo, with nothing in the returned report to
    /// say so. Only a real JSON boolean may enable this channel.
    func test_webShopEnabledAsJsonNumberOneDoesNotEnableWebShopAndLeavesNote() {
        let config = """
            {
              "version": "routing_v15",
              "web_shop": {"enabled": 1, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("web_shop.enabled"), "report was: \(result.report)")
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
        XCTAssertNil(PurchaseRouter.shared.checkoutUrlFor(productId: "coin_pack_1", country: "US"))
    }

    /// The disabling direction of the same hole: `{"enabled": 0}` reached the identical safe
    /// default `false`, but SILENTLY — an operator who shipped `0` believing it a boolean got no
    /// signal that their config never meant what they wrote. A wrong-typed field degrades to a
    /// safe default AND leaves a note; "the default happened to be right this time" is not a
    /// reason to stay quiet (design spec §3.8).
    func test_webShopEnabledAsJsonNumberZeroLeavesNoteRatherThanDegradingSilently() {
        let config = """
            {
              "version": "routing_v16",
              "web_shop": {"enabled": 0, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("web_shop.enabled"), "report was: \(result.report)")
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
    }

    /// A stringly-typed flag (`"true"`) is the exact Android defect design spec §3.8 names — it
    /// must not enable the channel either, and must leave the same note.
    func test_webShopEnabledAsStringDoesNotEnableWebShopAndLeavesNote() {
        let config = """
            {
              "version": "routing_v17",
              "web_shop": {"enabled": "true", "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("web_shop.enabled"), "report was: \(result.report)")
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
    }

    /// Companion proving the fix did not break the legitimate case: a genuine JSON `true` still
    /// enables the channel, with no note.
    func test_webShopEnabledAsGenuineBooleanStillEnablesWebShopWithNoNote() {
        let config = """
            {
              "version": "routing_v18",
              "web_shop": {"enabled": true, "url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.contains("web_shop.enabled"), "report was: \(result.report)")
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .webShop)
    }

    /// The key genuinely ABSENT is a legitimate operator choice (web-shop simply not turned on),
    /// not a typo — it defaults to `false` with NO note, matching `eligible_countries`' own
    /// absent-key treatment.
    func test_webShopEnabledKeyAbsentDefaultsToDisabledWithNoNote() {
        let config = """
            {
              "version": "routing_v19",
              "web_shop": {"url_template": "https://s.example.com/{product_id}"},
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.contains("web_shop.enabled"), "report was: \(result.report)")
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .appStore)
    }

    // MARK: - Hardening: eligible_countries element-level drops are not silent

    /// A well-typed ARRAY whose ELEMENTS are wrong-typed (`["US", 42]`) used to drop the bad
    /// entries silently. The drop direction is safe (a narrower eligibility list can only route
    /// FEWER people to web-shop), but silence is the defect: an operator whose country list half
    /// survived would see a config that "applied" and never learn why Germany never routes.
    func test_eligibleCountriesWithNonStringElementsDropsThemAndLeavesNote() {
        let config = """
            {
              "version": "routing_v20",
              "web_shop": {
                "enabled": true,
                "url_template": "https://s.example.com/{product_id}",
                "eligible_countries": ["US", 42, "", "DE"]
              },
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("eligible_countries"), "report was: \(result.report)")
        // The entries that ARE well-formed still apply — a bad element degrades itself, not the list.
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "US"), .webShop)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "DE"), .webShop)
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "FR"), .appStore)
    }

    /// Companion: an all-strings list (including duplicates and casing noise, which collapse
    /// legitimately rather than being "dropped") leaves no note.
    func test_eligibleCountriesAllWellFormedElementsLeaveNoNote() {
        let config = """
            {
              "version": "routing_v21",
              "web_shop": {
                "enabled": true,
                "url_template": "https://s.example.com/{product_id}",
                "eligible_countries": ["US", "us", " DE "]
              },
              "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]
            }
            """
        let result = PurchaseRouter.shared.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.contains("eligible_countries"), "report was: \(result.report)")
        XCTAssertEqual(PurchaseRouter.shared.route(productId: "coin_pack_1", country: "DE"), .webShop)
    }
}
