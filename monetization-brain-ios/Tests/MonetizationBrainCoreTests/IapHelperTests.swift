import XCTest
@testable import MonetizationBrainCore

final class IapHelperTests: XCTestCase {
    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

    /// In-memory `StoreBackend` fake. `entitlementsToReturn` drives `currentEntitlements()`;
    /// `transactionUpdates()` stays open until `emitBackgroundUpdate(_:)` feeds it, simulating a
    /// real store's background transaction stream that lives for the process lifetime.
    private actor RecordingAttribution: AttributionBridge {
        private(set) var logged: [AttributionPurchase] = []
        func logPurchase(_ purchase: AttributionPurchase) async { logged.append(purchase) }
    }

    /// Stands in for a vendor SDK that misbehaves. `logPurchase` cannot throw, so the shape a
    /// conformance actually fails in is "takes its time"; this one also proves the purchase
    /// result does not depend on it having finished cleanly.
    private actor SlowAttribution: AttributionBridge {
        private(set) var callCount = 0
        func logPurchase(_ purchase: AttributionPurchase) async {
            callCount += 1
            await Task.yield()
        }
    }

    private final class FakeStoreBackend: StoreBackend, @unchecked Sendable {
        var productsToReturn: [IapProduct] = []
        var purchaseResultToReturn: IapResult = .userCanceled
        var entitlementsToReturn: [IapPurchase] = []
        var currentEntitlementsError: Error?
        var subscriptionSnapshotsToReturn: [SubscriptionSnapshot] = []
        /// Set to simulate the interrupted-fetch case the `subscriptionSnapshots()` contract
        /// exists for — a `CancellationError` from a dismissed view, or any store failure. A
        /// caller must classify nothing at all when this throws.
        var subscriptionSnapshotsError: Error?
        /// How many times `subscriptionSnapshots()` was called. The post-purchase path's
        /// "subscriptions only" rule is only really provable as a NEGATIVE — a consumable must not
        /// cost a subscription-status round trip at all — and an unchanged lifecycle state alone
        /// can't tell "never asked" apart from "asked and classified nothing."
        private(set) var subscriptionSnapshotsCallCount = 0
        private(set) var finishedPurchases: [IapPurchase] = []
        private let lock = NSLock()
        private var updatesContinuation: AsyncStream<IapPurchase>.Continuation?

        func products(for identifiers: [String]) async throws -> [IapProduct] {
            productsToReturn.filter { identifiers.contains($0.productId) }
        }

        func purchase(_ product: IapProduct) async throws -> IapResult {
            purchaseResultToReturn
        }

        func currentEntitlements() async throws -> [IapPurchase] {
            if let currentEntitlementsError {
                throw currentEntitlementsError
            }
            return entitlementsToReturn
        }

        func subscriptionSnapshots() async throws -> [SubscriptionSnapshot] {
            recordSubscriptionSnapshotsCall()
            if let subscriptionSnapshotsError {
                throw subscriptionSnapshotsError
            }
            return subscriptionSnapshotsToReturn
        }

        private func recordSubscriptionSnapshotsCall() {
            lock.lock()
            subscriptionSnapshotsCallCount += 1
            lock.unlock()
        }

        func transactionUpdates() -> AsyncStream<IapPurchase> {
            AsyncStream { continuation in
                lock.lock()
                updatesContinuation = continuation
                lock.unlock()
            }
        }

        /// Test hook: simulate a transaction arriving in the background, outside any
        /// `purchase(_:)` call.
        func emitBackgroundUpdate(_ purchase: IapPurchase) {
            lock.lock()
            let continuation = updatesContinuation
            lock.unlock()
            continuation?.yield(purchase)
        }

        func finish(_ purchase: IapPurchase) async {
            recordFinishedPurchase(purchase)
        }

        private func recordFinishedPurchase(_ purchase: IapPurchase) {
            lock.lock()
            finishedPurchases.append(purchase)
            lock.unlock()
        }
    }

    private let product = IapProduct(
        productId: "gem_pack_1", type: .consumable, formattedPrice: "$4.99",
        priceMicros: 4_990_000, currencyCode: "USD", title: "Gem Pack"
    )

    private func samplePurchase(mode: IapPurchaseState = .purchased) -> IapPurchase {
        IapPurchase(
            productId: "gem_pack_1", orderId: "order_1", purchaseToken: "jws_token",
            purchaseTimeMs: 1_700_000_000_000, state: mode, isFinished: false,
            isAutoRenewing: false, quantity: 1, priceMicros: 4_990_000, currencyCode: "USD"
        )
    }

    // MARK: - Subscription fixtures (SubscriptionLifecycle wiring, design spec §6)

    /// In-memory `BrainKeyValueStore` — redeclared per-file, matching this project's established
    /// convention of NOT sharing test fakes across files (see `SubscriptionLifecycleTests`' copy).
    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? {
            lock.lock(); defer { lock.unlock() }
            return values[key]
        }
        func setString(_ value: String, forKey key: String) {
            lock.lock(); values[key] = value; lock.unlock()
        }
        func removeObject(forKey key: String) {
            lock.lock(); _ = values.removeValue(forKey: key); lock.unlock()
        }
    }

    private let subscriptionId = "sub_monthly"

    private let subscriptionProduct = IapProduct(
        productId: "sub_monthly", type: .autoRenewable, formattedPrice: "$9.99",
        priceMicros: 9_990_000, currencyCode: "USD", title: "Monthly"
    )

    private let subscriptionPurchase = IapPurchase(
        productId: "sub_monthly", orderId: "order_sub_1", purchaseToken: "jws_sub",
        purchaseTimeMs: 1_700_000_000_000, state: .purchased, isFinished: false,
        isAutoRenewing: true, quantity: 1, priceMicros: 9_990_000, currencyCode: "USD"
    )

    private let activeSnapshot = SubscriptionSnapshot(
        productId: "sub_monthly", renewalState: .subscribed, willAutoRenew: true
    )

    private let expiredSnapshot = SubscriptionSnapshot(
        productId: "sub_monthly", renewalState: .expired, willAutoRenew: false,
        expirationReason: .billingError
    )

    /// A FRESH `SubscriptionLifecycle` per test, never `.shared` — `.shared` has no per-test reset
    /// path from this file and would leak persisted state across tests. This is the same reason
    /// `PurchaseRouter.Configuration.iapHelper` is injected rather than reached for inline, and the
    /// reason `IapHelper.Configuration` now carries the lifecycle too.
    private func makeLifecycle(eventSink: EventSink = FakeEventSink()) -> SubscriptionLifecycle {
        let lifecycle = SubscriptionLifecycle()
        lifecycle.configure(.init(eventSink: eventSink, playerId: "p1", store: InMemoryStore()))
        return lifecycle
    }

    override func setUp() {
        super.setUp()
        AdsBrain.shared.resetForTest()
        Entitlements.shared.resetForTest()
        FxRates.reset()
        // `Configuration`'s default `store` is a real `UserDefaultsBrainStore()`, and every test
        // in this file that doesn't pass its own `store:` shares that same on-disk UserDefaults.
        // `IapHelper`'s orderId dedupe (Finding 1) persists under this key, and `samplePurchase()`
        // reuses `orderId: "order_1"` across many tests — without this reset, one test's dedupe
        // write silently makes a LATER test's identical orderId look already-reported, matching
        // how `Entitlements.shared.resetForTest()` above clears ITS OWN real-UserDefaults keys for
        // exactly the same reason. References `IapHelper.processedOrderIdsKey` directly (internal,
        // not private, for exactly this purpose) rather than a duplicated string literal that could
        // silently drift from the real key.
        UserDefaults.standard.removeObject(forKey: IapHelper.processedOrderIdsKey)
    }

    func test_isReadyFalseBeforeConfigure() {
        let helper = IapHelper()
        XCTAssertFalse(helper.isReady())
    }

    func test_isReadyTrueAfterConfigure() async {
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))
        XCTAssertTrue(helper.isReady())
    }

    func test_queryProductsPopulatesCatalogFromBackend() async {
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))
        helper.setProducts(["gem_pack_1"])

        let result = await helper.queryProducts()

        XCTAssertEqual(result, [product])
        XCTAssertEqual(helper.productFor("gem_pack_1"), product)
        XCTAssertEqual(helper.products(), [product])
    }

    func test_purchaseReturnsFailedWhenProductNotCached() async {
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))

        let result = await helper.purchase("unknown_product")

        if case .failed = result {} else { XCTFail("expected .failed, got \(result)") }
    }

    func test_purchaseSuccessGrantsEntitlementAndFinishesTransaction() async {
        Entitlements.shared.resetForTest()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        let purchase = samplePurchase()
        backend.purchaseResultToReturn = .success(purchase)
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()

        let result = await helper.purchase("gem_pack_1")

        if case .success = result {} else { return XCTFail("expected .success, got \(result)") }
        XCTAssertTrue(Entitlements.shared.isActive("gem_pack_1"))
        XCTAssertEqual(backend.finishedPurchases, [purchase])
    }

    func test_purchaseSuccessEmitsIapPurchaseEventWithFullFieldSet() async {
        Entitlements.shared.resetForTest()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        let sink = FakeEventSink()
        backend.purchaseResultToReturn = .success(samplePurchase())
        await helper.configure(.init(backend: backend, eventSink: sink, playerId: "p1"))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()

        _ = await helper.purchase("gem_pack_1")

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.count, 1)
        XCTAssertEqual(sent[0].eventName, "iap_purchase")
        XCTAssertEqual(sent[0].eventData["product_id"], .string("gem_pack_1"))
        XCTAssertEqual(sent[0].eventData["order_id"], .string("order_1"))
        XCTAssertEqual(sent[0].eventData["price_micros"], .int(4_990_000))
        XCTAssertEqual(sent[0].eventData["currency_code"], .string("USD"))
        XCTAssertEqual(sent[0].eventData["quantity"], .int(1))
        XCTAssertEqual(sent[0].eventData["channel"], .string("APP_STORE"))
        XCTAssertEqual(sent[0].eventData["is_subscription"], .int(0))
        XCTAssertEqual(sent[0].eventData["purchase_token"], .string("jws_token"))
        XCTAssertEqual(sent[0].userProperty.userId, "p1")
    }

    func test_purchaseSuccessCallsAdsBrainOnPurchaseWithConvertedUsd() async {
        Entitlements.shared.resetForTest()
        FxRates.setRates([:]) // USD needs no rate table entry
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        backend.purchaseResultToReturn = .success(samplePurchase())
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()
        // AdsBrain.onPurchase only mutates state once a session exists — matches every
        // AdsBrain-state-asserting test in AdsBrainTests.swift (configure + onSessionStart
        // always precede an onPurchase/currentState() assertion there too).
        await AdsBrain.shared.configure(.init(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: FakeEventSink(), playerId: "p1"
        ))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        _ = await helper.purchase("gem_pack_1")

        XCTAssertEqual(AdsBrain.shared.currentState()?.purchaseCountLifetime, 1)
    }

    func test_purchaseSuccessCallsAdsBrainOnPurchaseEvenWithUnknownCurrency() async {
        Entitlements.shared.resetForTest()
        let unknownCurrencyProduct = IapProduct(
            productId: "gem_pack_2", type: .consumable, formattedPrice: "99,000 VND",
            priceMicros: 99_000_000_000, currencyCode: "VND"
        )
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [unknownCurrencyProduct]
        backend.purchaseResultToReturn = .success(IapPurchase(
            productId: "gem_pack_2", orderId: "order_2", purchaseToken: "t2",
            purchaseTimeMs: 0, state: .purchased, isFinished: false, isAutoRenewing: false,
            quantity: 1, priceMicros: 99_000_000_000, currencyCode: "VND"
        ))
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))
        helper.setProducts(["gem_pack_2"])
        _ = await helper.queryProducts()
        // See test_purchaseSuccessCallsAdsBrainOnPurchaseWithConvertedUsd — AdsBrain needs an
        // active session before onPurchase mutates purchaseCountLifetime.
        await AdsBrain.shared.configure(.init(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: FakeEventSink(), playerId: "p1"
        ))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        _ = await helper.purchase("gem_pack_2")

        // The call must still happen — payer-guardrail invariant reads purchaseCountLifetime,
        // never a USD total (design spec §3.8).
        XCTAssertEqual(AdsBrain.shared.currentState()?.purchaseCountLifetime, 1)
    }

    func test_purchasePendingDoesNotGrantEntitlement() async {
        Entitlements.shared.resetForTest()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        backend.purchaseResultToReturn = .pending(samplePurchase(mode: .pending))
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()

        let result = await helper.purchase("gem_pack_1")

        if case .pending = result {} else { XCTFail("expected .pending, got \(result)") }
        XCTAssertFalse(Entitlements.shared.isActive("gem_pack_1"))
    }

    func test_purchaseListenerFiresForSynchronousPurchaseCall() async {
        Entitlements.shared.resetForTest()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        backend.purchaseResultToReturn = .success(samplePurchase())
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()

        let receivedResult = ActorIsolatedBox<IapResult?>(nil)
        helper.purchaseListener = { result in
            Task { await receivedResult.set(result) }
        }
        _ = await helper.purchase("gem_pack_1")
        // The listener closure is synchronous and hops into the actor via its own Task, so
        // poll with a bounded retry rather than assuming one fixed sleep is always enough
        // (matches Task 6's background-update test, for the same reason).
        var received: IapResult?
        for _ in 0..<50 {
            received = await receivedResult.get()
            if received != nil { break }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }

        if case .success = received {} else { XCTFail("expected .success, got \(String(describing: received))") }
    }

    func test_restorePurchasesSyncsEntitlementsFromCurrentEntitlementsStream() async {
        Entitlements.shared.resetForTest()
        Entitlements.shared.grant("stale_product") // must be REPLACED, not kept
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.entitlementsToReturn = [samplePurchase()]
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))

        let restored = await helper.restorePurchases()

        XCTAssertEqual(restored, ["gem_pack_1"])
        XCTAssertFalse(Entitlements.shared.isActive("stale_product"))
        XCTAssertTrue(Entitlements.shared.isActive("gem_pack_1"))
    }

    func test_restorePurchasesFinishesEveryRestoredTransaction() async {
        Entitlements.shared.resetForTest()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        let purchase = samplePurchase()
        backend.entitlementsToReturn = [purchase]
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))

        _ = await helper.restorePurchases()

        XCTAssertEqual(backend.finishedPurchases, [purchase])
    }

    func test_restorePurchasesWithEmptyEntitlementsClearsEverything() async {
        Entitlements.shared.resetForTest()
        Entitlements.shared.grant("stale_product")
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))

        let restored = await helper.restorePurchases()

        XCTAssertEqual(restored, [])
        XCTAssertFalse(Entitlements.shared.isActive("stale_product"))
    }

    /// The regression test for the fix itself: `currentEntitlements()` being interrupted
    /// (cancellation or a genuine backend error) partway through must NOT be treated as "the
    /// store reports nothing owned" — that would revoke still-valid entitlements for products the
    /// fetch hadn't reached yet. `restorePurchases()` must leave existing entitlements untouched.
    func test_restorePurchasesDoesNotRevokeEntitlementsWhenCurrentEntitlementsFetchIsInterrupted() async {
        Entitlements.shared.resetForTest()
        Entitlements.shared.grant("still_valid_product")
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.currentEntitlementsError = CancellationError()
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))

        let result = await helper.restorePurchases()

        // The fetch was interrupted — restorePurchases must NOT have synced an empty/partial set,
        // which would have revoked "still_valid_product". The pre-existing entitlement survives.
        XCTAssertTrue(Entitlements.shared.isActive("still_valid_product"))
        XCTAssertEqual(result, Entitlements.shared.activeProductIds())
    }

    /// The core "never re-report" invariant: a restored purchase happened in a PRIOR session, so
    /// routing it through the same reporting path as a fresh `purchase(_:)` would double-count or
    /// backdate revenue. Configures AdsBrain with an active session (matching
    /// `test_purchaseSuccessCallsAdsBrainOnPurchaseWithConvertedUsd`'s setup, where onPurchase DOES
    /// mutate state) so that an unchanged purchaseCountLifetime here is actually proof
    /// restorePurchases never called onPurchase — not an artifact of no session existing.
    func test_restorePurchasesDoesNotEmitIapPurchaseEventOrNotifyAdsBrain() async {
        Entitlements.shared.resetForTest()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.entitlementsToReturn = [samplePurchase()]
        let sink = FakeEventSink()
        await helper.configure(.init(backend: backend, eventSink: sink, playerId: "p1"))
        await AdsBrain.shared.configure(.init(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: FakeEventSink(), playerId: "p1"
        ))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        let countBefore = AdsBrain.shared.currentState()?.purchaseCountLifetime

        _ = await helper.restorePurchases()

        let sent = await sink.sentEvents
        XCTAssertTrue(sent.isEmpty, "restorePurchases must never emit iap_purchase — that would double-count/backdate revenue")
        XCTAssertEqual(AdsBrain.shared.currentState()?.purchaseCountLifetime, countBefore)
    }

    /// Finding 3 (restore→listener) had no direct test until now — the existing restore tests
    /// cover entitlements/finish/reporting but not `purchaseListener`. Reuses the single-value
    /// `ActorIsolatedBox<IapResult?>` shape from `test_purchaseListenerFiresForSynchronousPurchaseCall`
    /// rather than a list box — one restored item is all this needs to prove the listener fired
    /// with the right result.
    func test_restorePurchasesCallsPurchaseListenerPerRestoredItem() async {
        Entitlements.shared.resetForTest()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.entitlementsToReturn = [samplePurchase()]
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))

        let receivedResult = ActorIsolatedBox<IapResult?>(nil)
        helper.purchaseListener = { result in
            Task { await receivedResult.set(result) }
        }

        _ = await helper.restorePurchases()

        // Listener closure hops into the actor via its own Task — poll with a bounded retry,
        // matching this file's established pattern (see the other purchaseListener tests).
        var received: IapResult?
        for _ in 0..<50 {
            received = await receivedResult.get()
            if received != nil { break }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }

        if case .success = received {} else { XCTFail("expected .success, got \(String(describing: received))") }
    }

    func test_backgroundTransactionUpdateGrantsEntitlementAndCallsPurchaseListener() async throws {
        Entitlements.shared.resetForTest()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()

        let receivedResult = ActorIsolatedBox<IapResult?>(nil)
        helper.purchaseListener = { result in
            Task { await receivedResult.set(result) }
        }

        backend.emitBackgroundUpdate(samplePurchase())
        // Background consumption runs on an independently-scheduled Task — poll briefly rather
        // than assuming a fixed delay is always enough.
        // Poll the SAME thing the assertion below checks — receivedResult — not a proxy
        // (Entitlements) that can become true before the listener's own async hop completes.
        // In production, Entitlements.shared.grant(...) happens (inside handle(...)) strictly
        // before purchaseListener is invoked, so once receivedResult is non-nil both the grant
        // and the listener call have already happened.
        var received: IapResult?
        for _ in 0..<50 {
            received = await receivedResult.get()
            if received != nil { break }
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertTrue(Entitlements.shared.isActive("gem_pack_1"))
        if case .success = received {} else { XCTFail("expected .success, got \(String(describing: received))") }
    }

    /// `test_backgroundTransactionUpdateGrantsEntitlementAndCallsPurchaseListener` above proves
    /// entitlement + listener fire on this path; this proves the phase's other headline invariant
    /// — the `iap_purchase` event and `AdsBrain.onPurchase` — also fires here, not just on the
    /// `purchase(_:)` path (see `test_purchaseSuccessCallsAdsBrainOnPurchaseWithConvertedUsd`).
    func test_backgroundTransactionUpdateEmitsIapPurchaseEventAndCallsAdsBrainOnPurchase() async throws {
        Entitlements.shared.resetForTest()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        let sink = FakeEventSink()
        await helper.configure(.init(backend: backend, eventSink: sink, playerId: "p1"))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()
        // See test_purchaseSuccessCallsAdsBrainOnPurchaseWithConvertedUsd — AdsBrain needs an
        // active session before onPurchase mutates purchaseCountLifetime.
        await AdsBrain.shared.configure(.init(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: FakeEventSink(), playerId: "p1"
        ))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        backend.emitBackgroundUpdate(samplePurchase())

        // Poll on the thing actually being asserted, not a proxy — matches this file's
        // established pattern (see the purchaseListener tests) for why a proxy poll is unsafe.
        var sawEvent = false
        for _ in 0..<50 {
            let sent = await sink.sentEvents
            if sent.contains(where: { $0.eventName == "iap_purchase" }) {
                sawEvent = true
                break
            }
            try await Task.sleep(nanoseconds: 10_000_000)
        }

        XCTAssertTrue(sawEvent, "expected an iap_purchase event from the background transactionUpdates path")
        XCTAssertEqual(AdsBrain.shared.currentState()?.purchaseCountLifetime, 1)
    }

    // MARK: - SubscriptionLifecycle wiring (design spec §6)

    /// Spec §8's "`restorePurchases` with a throwing `subscriptionSnapshots()` classifies nothing."
    /// A throw — a `CancellationError` from a dismissed SwiftUI `.task { }`, or any genuine backend
    /// failure — must leave the lifecycle exactly as it was and emit nothing.
    ///
    /// Seeds a real prior state (`.active`) first, because "classifies nothing" is only meaningful
    /// against something that could have been overwritten. `subscriptionSnapshotsToReturn` is also
    /// populated with a contradicting `.expired` snapshot — unreachable while the error is set, but
    /// it makes the intent unambiguous: if this ever starts classifying, it fails loudly.
    ///
    /// ═══ WHAT THIS TEST CANNOT PROVE, STATED RATHER THAN IMPLIED ═══
    ///
    /// It pins the OBSERVABLE contract, not the implementation's `return`. A `catch` that swallowed
    /// the error and carried on with `[]` passes this test too — verified by mutation, not assumed.
    /// That is not a hole in the test; it is design spec §3.4 showing through: absence from a
    /// snapshot set means "no news", never "lapsed", so `observe([])` produces no transitions, no
    /// events and no writes, and is genuinely indistinguishable from not calling it. Nothing on
    /// `SubscriptionLifecycle`'s public surface can tell the two apart, and it is `final`, so no
    /// spy can either.
    ///
    /// The skip in `classifySubscriptions` is therefore held by its own doc comment and by review,
    /// not by this assertion — and it is cheap insurance rather than the load-bearing guard its
    /// `currentEntitlements()` counterpart (see
    /// `test_restorePurchasesDoesNotRevokeEntitlementsWhenCurrentEntitlementsFetchIsInterrupted`,
    /// which DOES fail on a swallow) is. What this test does buy: the throw never propagates, never
    /// costs the entitlement sync that already succeeded, and never reclassifies.
    func test_restorePurchasesSkipsClassificationWhenSubscriptionSnapshotsFetchIsInterrupted() async {
        Entitlements.shared.resetForTest()
        let sink = FakeEventSink()
        let lifecycle = makeLifecycle(eventSink: sink)
        await lifecycle.observe([activeSnapshot], complete: true)
        let eventsAfterSeed = await sink.sentEvents.count

        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.subscriptionSnapshotsError = CancellationError()
        backend.subscriptionSnapshotsToReturn = [expiredSnapshot]
        await helper.configure(.init(
            backend: backend, eventSink: FakeEventSink(), playerId: "p1",
            subscriptionLifecycle: lifecycle
        ))

        _ = await helper.restorePurchases()

        XCTAssertEqual(lifecycle.stateFor(productId: subscriptionId), .active,
                       "an interrupted snapshot fetch must never reclassify — prior state stands")
        let eventsAfter = await sink.sentEvents.count
        XCTAssertEqual(eventsAfter, eventsAfterSeed,
                       "no subscription_state event may be emitted when the fetch never completed")
    }

    func test_restorePurchasesClassifiesSubscriptionSnapshots() async {
        Entitlements.shared.resetForTest()
        let lifecycle = makeLifecycle()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.subscriptionSnapshotsToReturn = [activeSnapshot]
        await helper.configure(.init(
            backend: backend, eventSink: FakeEventSink(), playerId: "p1",
            subscriptionLifecycle: lifecycle
        ))

        _ = await helper.restorePurchases()

        XCTAssertEqual(lifecycle.stateFor(productId: subscriptionId), .active)
    }

    /// An expired subscription that the store still reports stays expired through a restore — the
    /// companion to the test above, proving `restorePurchases` feeds the classifier the store's
    /// real answer rather than only ever producing entitled states.
    func test_restorePurchasesClassifiesAnExpiredSubscriptionAsExpired() async {
        Entitlements.shared.resetForTest()
        let lifecycle = makeLifecycle()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.subscriptionSnapshotsToReturn = [expiredSnapshot]
        await helper.configure(.init(
            backend: backend, eventSink: FakeEventSink(), playerId: "p1",
            subscriptionLifecycle: lifecycle
        ))

        _ = await helper.restorePurchases()

        XCTAssertEqual(lifecycle.stateFor(productId: subscriptionId), .expired)
        XCTAssertTrue(lifecycle.isWinBackEligible(productId: subscriptionId))
    }

    /// The whole point of the post-purchase call (§6): a win-back buyer must leave `.expired` the
    /// moment the purchase succeeds, not at the next `restorePurchases()`.
    ///
    /// Also pins the `complete: false` scoping — a second, unrelated subscription the store also
    /// reports must NOT be classified by a single-product purchase.
    func test_successfulSubscriptionPurchaseMovesLifecycleOutOfExpiredImmediately() async {
        Entitlements.shared.resetForTest()
        let lifecycle = makeLifecycle()
        await lifecycle.observe([expiredSnapshot], complete: true)
        XCTAssertEqual(lifecycle.stateFor(productId: subscriptionId), .expired)

        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [subscriptionProduct]
        backend.purchaseResultToReturn = .success(subscriptionPurchase)
        backend.subscriptionSnapshotsToReturn = [
            activeSnapshot,
            SubscriptionSnapshot(productId: "other_sub", renewalState: .expired, willAutoRenew: false)
        ]
        await helper.configure(.init(
            backend: backend, eventSink: FakeEventSink(), playerId: "p1",
            subscriptionLifecycle: lifecycle
        ))
        helper.setProducts([subscriptionId])
        _ = await helper.queryProducts()

        _ = await helper.purchase(subscriptionId)

        XCTAssertEqual(lifecycle.stateFor(productId: subscriptionId), .active)
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: subscriptionId))
        XCTAssertEqual(lifecycle.stateFor(productId: "other_sub"), .none,
                       "complete: false must classify only the product the purchase touched")
    }

    /// A RENEWAL arriving through `transactionUpdates()` — not through an in-process `purchase(_:)`
    /// call — must refresh the lifecycle too. This is the real-world path for every auto-renewal
    /// after the first purchase, so it is the case that matters most in production and the one no
    /// other test covers: the two existing background-update tests both use a consumable.
    ///
    /// It defends the "single funnel" design directly. Moving the classification call out of
    /// `handle(_:configuration:)` and into `purchase(_:)` looks equivalent, compiles, and leaves
    /// every other test green — while silently stopping renewals from ever refreshing lifecycle
    /// state. Only this test goes red.
    func test_backgroundSubscriptionRenewalRefreshesLifecycleState() async {
        Entitlements.shared.resetForTest()
        let lifecycle = makeLifecycle()
        await lifecycle.observe([expiredSnapshot], complete: true)
        XCTAssertEqual(lifecycle.stateFor(productId: subscriptionId), .expired)

        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [subscriptionProduct]
        backend.subscriptionSnapshotsToReturn = [activeSnapshot]
        await helper.configure(.init(
            backend: backend, eventSink: FakeEventSink(), playerId: "p1",
            subscriptionLifecycle: lifecycle
        ))
        helper.setProducts([subscriptionId])
        _ = await helper.queryProducts()

        backend.emitBackgroundUpdate(subscriptionPurchase)

        // Poll the thing actually being asserted — the stream is consumed on a background Task.
        var state = lifecycle.stateFor(productId: subscriptionId)
        for _ in 0..<50 where state != .active {
            try? await Task.sleep(nanoseconds: 10_000_000)
            state = lifecycle.stateFor(productId: subscriptionId)
        }
        XCTAssertEqual(state, .active, "a renewal arriving via transactionUpdates must reclassify")
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: subscriptionId))
    }

    /// A consumable has no subscription lifecycle at all — and must not pay for one. Asserting the
    /// call count (not just the resulting state) is what distinguishes "never asked the store" from
    /// "asked, and happened to classify nothing."
    func test_successfulConsumablePurchaseDoesNotTouchSubscriptionLifecycle() async {
        Entitlements.shared.resetForTest()
        let lifecycle = makeLifecycle()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        backend.purchaseResultToReturn = .success(samplePurchase())
        backend.subscriptionSnapshotsToReturn = [activeSnapshot]
        await helper.configure(.init(
            backend: backend, eventSink: FakeEventSink(), playerId: "p1",
            subscriptionLifecycle: lifecycle
        ))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()

        _ = await helper.purchase("gem_pack_1")

        XCTAssertEqual(backend.subscriptionSnapshotsCallCount, 0)
        XCTAssertEqual(lifecycle.stateFor(productId: subscriptionId), .none)
    }

    /// The post-purchase path inherits the same skip: a throwing `subscriptionSnapshots()` after an
    /// otherwise-successful subscription purchase leaves the lifecycle exactly as it was, and never
    /// fails the purchase itself.
    func test_successfulSubscriptionPurchaseSkipsClassificationWhenSnapshotFetchIsInterrupted() async {
        Entitlements.shared.resetForTest()
        let lifecycle = makeLifecycle()
        await lifecycle.observe([expiredSnapshot], complete: true)

        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [subscriptionProduct]
        backend.purchaseResultToReturn = .success(subscriptionPurchase)
        backend.subscriptionSnapshotsError = CancellationError()
        await helper.configure(.init(
            backend: backend, eventSink: FakeEventSink(), playerId: "p1",
            subscriptionLifecycle: lifecycle
        ))
        helper.setProducts([subscriptionId])
        _ = await helper.queryProducts()

        let result = await helper.purchase(subscriptionId)

        if case .success = result {} else { XCTFail("expected .success, got \(result)") }
        XCTAssertEqual(lifecycle.stateFor(productId: subscriptionId), .expired)
    }

    /// `Configuration`'s new `subscriptionLifecycle` field carries a `.shared` default, so every
    /// pre-existing three-argument call site still compiles and still configures a usable helper.
    /// Every other test in this file exercises that implicitly; this one states it.
    func test_configurationOmittingSubscriptionLifecycleStillConfigures() async {
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))
        XCTAssertTrue(helper.isReady())
    }

    // MARK: - Attribution

    /// ═══ THE GAP THIS CLOSES, AND WHY IT NEVER REPORTS ITSELF ═══
    ///
    /// Android's equivalent was an EMPTY function nobody called, so the attribution network saw
    /// ad revenue and not one cent of IAP. Every ROAS number the UA campaigns bid on was missing
    /// a side — the bigger side for a game that sells anything — and nothing ever errored:
    /// campaigns ran, dashboards showed numbers, and the auction optimised against half the truth.
    func test_aSuccessfulPurchaseReachesAttributionWithTheStoresOwnAmountAndCurrency() async {
        Entitlements.shared.resetForTest()
        let attribution = RecordingAttribution()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        backend.purchaseResultToReturn = .success(samplePurchase())
        await helper.configure(.init(
            backend: backend, eventSink: FakeEventSink(), playerId: "p1", attribution: attribution
        ))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()

        _ = await helper.purchase("gem_pack_1")

        let logged = await attribution.logged
        XCTAssertEqual(logged.count, 1)
        let sample = samplePurchase()
        XCTAssertEqual(logged.first?.productId, sample.productId)
        XCTAssertEqual(logged.first?.orderId, sample.orderId)
        // Verbatim: the store's own micros and the store's own currency code. Converting to USD
        // here would hand the network a guess layered on a guess — it converts with its own
        // same-day rate, which is the whole reason `FxRates` refuses to guess too.
        XCTAssertEqual(logged.first?.revenueMicros, sample.priceMicros)
        XCTAssertEqual(logged.first?.currencyCode, sample.currencyCode)
        XCTAssertEqual(logged.first?.quantity, sample.quantity)
    }

    /// ═══ THE RULE THE USD FIXTURE ABOVE CANNOT TEST ═══
    ///
    /// With a $4.99 purchase, "send the store's amount" and "convert to USD first" produce the
    /// same number, so the assertion above passes either way — mutation testing said so. The rule
    /// only has teeth in a currency that is not USD: the network converts with its own same-day
    /// rate, and a figure converted here would be this client's guess (or, for a currency
    /// `FxRates` has no rate for, a silent ZERO) presented to the network as fact.
    func test_aNonUsdPurchaseReachesAttributionUnconverted() async {
        Entitlements.shared.resetForTest()
        let attribution = RecordingAttribution()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [IapProduct(
            productId: "gem_pack_vn", type: .consumable, formattedPrice: "129.000₫",
            priceMicros: 129_000_000_000, currencyCode: "VND", title: "Gem Pack"
        )]
        backend.purchaseResultToReturn = .success(IapPurchase(
            productId: "gem_pack_vn", orderId: "order_vn", purchaseToken: "jws",
            purchaseTimeMs: 1_700_000_000_000, state: .purchased, isFinished: false,
            isAutoRenewing: false, quantity: 1, priceMicros: 129_000_000_000, currencyCode: "VND"
        ))
        await helper.configure(.init(
            backend: backend, eventSink: FakeEventSink(), playerId: "p1", attribution: attribution
        ))
        helper.setProducts(["gem_pack_vn"])
        _ = await helper.queryProducts()

        _ = await helper.purchase("gem_pack_vn")

        let logged = await attribution.logged
        XCTAssertEqual(logged.first?.revenueMicros, 129_000_000_000)
        XCTAssertEqual(logged.first?.currencyCode, "VND")
        XCTAssertEqual(logged.first?.revenue ?? -1, 129_000, accuracy: 1e-6)
    }

    /// A subscription must not be reported as a one-off. Networks model recurring revenue
    /// differently, so the flag decides which event is sent, and it comes from the SAME helper
    /// the `is_subscription` event field uses — the two cannot disagree.
    func test_attributionSeesTheSameSubscriptionVerdictAsTheEvent() async {
        Entitlements.shared.resetForTest()
        let attribution = RecordingAttribution()
        let sink = FakeEventSink()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [IapProduct(
            productId: "sub_monthly", type: .autoRenewable, formattedPrice: "$4.99",
            priceMicros: 4_990_000, currencyCode: "USD", title: "Monthly"
        )]
        backend.purchaseResultToReturn = .success(IapPurchase(
            productId: "sub_monthly", orderId: "o-sub", purchaseToken: "jws",
            purchaseTimeMs: 1_700_000_000_000, state: .purchased, isFinished: false,
            isAutoRenewing: true, quantity: 1, priceMicros: 4_990_000, currencyCode: "USD"
        ))
        await helper.configure(.init(
            backend: backend, eventSink: sink, playerId: "p1", attribution: attribution
        ))
        helper.setProducts(["sub_monthly"])
        _ = await helper.queryProducts()

        _ = await helper.purchase("sub_monthly")

        let logged = await attribution.logged
        let events = await sink.sentEvents
        let purchaseEvent = events.first { $0.eventName == "iap_purchase" }
        XCTAssertEqual(logged.first?.isSubscription, true)
        XCTAssertEqual(purchaseEvent?.eventData["is_subscription"], .int(1))
    }

    /// Attribution is opt-in: a host that never supplies a bridge must be able to sell things.
    func test_aPurchaseSucceedsWithNoAttributionBridgeConfigured() async {
        Entitlements.shared.resetForTest()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        backend.purchaseResultToReturn = .success(samplePurchase())
        await helper.configure(.init(backend: backend, eventSink: FakeEventSink(), playerId: "p1"))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()

        let result = await helper.purchase("gem_pack_1")

        if case .success = result {} else { XCTFail("expected .success, got \(result)") }
        XCTAssertTrue(Entitlements.shared.isActive("gem_pack_1"))
    }

    /// A slow vendor SDK must not change the outcome the player sees, and the entitlement must
    /// already be granted by the time reporting runs at all.
    func test_aSlowAttributionBridgeDoesNotChangeThePurchaseOutcome() async {
        Entitlements.shared.resetForTest()
        let attribution = SlowAttribution()
        let helper = IapHelper()
        let backend = FakeStoreBackend()
        backend.productsToReturn = [product]
        backend.purchaseResultToReturn = .success(samplePurchase())
        await helper.configure(.init(
            backend: backend, eventSink: FakeEventSink(), playerId: "p1", attribution: attribution
        ))
        helper.setProducts(["gem_pack_1"])
        _ = await helper.queryProducts()

        let result = await helper.purchase("gem_pack_1")

        if case .success = result {} else { XCTFail("expected .success, got \(result)") }
        XCTAssertTrue(Entitlements.shared.isActive("gem_pack_1"))
        let calls = await attribution.callCount
        XCTAssertEqual(calls, 1)
    }

}

/// Minimal actor box for capturing a value from a `@Sendable` closure in a test — this codebase
/// has no existing shared test utility for this; declare it once per test file that needs it,
/// matching the established per-file-fake convention.
private actor ActorIsolatedBox<T> {
    private var value: T
    init(_ value: T) { self.value = value }
    func set(_ newValue: T) { value = newValue }
    func get() -> T { value }
}
