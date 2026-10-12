import Foundation

/// Orchestrates purchases against an injected `StoreBackend` — the real store adapter (StoreKit
/// 2) lives in a separate `MonetizationBrainStoreKit` target and is never imported here.
///
/// ═══ WHERE THIS SITS IN THE PRODUCT ═══
///
/// This is the piece that unlocks most of the IAP-related roadmap: before it, `ActionMode
/// .iapOffer` is an enum case with nothing behind it, because the SDK has no way to know who has
/// bought what. With it, three things become real:
/// 1. **A payer signal into the brain** — `AdsBrain.shared.onPurchase(usd:)` is called
///    automatically, belief shifts toward the payer segment, and ad-pressure guardrails ease off
///    for someone who just paid.
/// 2. **Revenue reporting** — the `iap_purchase` event carries `price_micros`/`currency_code`/
///    `channel`, letting ARPDAU be computed across both IAP and ad revenue.
/// 3. **Entitlement flows into the ad layer** — `Entitlements` turns ads off for a subscriber
///    (once an ad-control facade exists — see design spec §1).
///
/// ═══ WHAT THIS DELIBERATELY DOES NOT DO ═══
///
/// - **No server-side receipt verification.** No backend exists for it yet (design spec §1).
///   `Entitlements` here should only ever be used for `hasRemoveAds()`, never for redeemable-
///   value items, until that verification exists.
/// - **No paywall.** The host app owns purchase-initiation UI entirely.
public final class IapHelper: @unchecked Sendable {
    public static let shared = IapHelper()

    public struct Configuration: Sendable {
        public let backend: StoreBackend
        public let eventSink: EventSink
        public let playerId: String
        public let store: BrainKeyValueStore
        /// Where subscription snapshots are classified (design spec §6). INJECTED, never reached
        /// for inline as `SubscriptionLifecycle.shared` inside this class's logic — exactly the
        /// precedent `PurchaseRouter.Configuration.iapHelper` set, and for the same reason:
        /// `.shared` has no per-test reset path reachable from a caller's test file, so a test
        /// that exercised this wiring against the singleton would leak persisted lifecycle state
        /// into every later test. Defaults to `.shared` so app wiring stays a three-argument call.
        public let subscriptionLifecycle: SubscriptionLifecycle
        /// Where purchase revenue goes for user-acquisition attribution. Defaults to a no-op, so
        /// attribution is opt-in and a host that has no attribution SDK pays nothing for it.
        public let attribution: AttributionBridge

        public init(
            backend: StoreBackend,
            eventSink: EventSink,
            playerId: String,
            store: BrainKeyValueStore = UserDefaultsBrainStore(),
            subscriptionLifecycle: SubscriptionLifecycle = .shared,
            attribution: AttributionBridge = NoOpAttributionBridge()
        ) {
            self.backend = backend
            self.eventSink = eventSink
            self.playerId = playerId
            self.store = store
            self.subscriptionLifecycle = subscriptionLifecycle
            self.attribution = attribution
        }
    }

    // Internal (not private): `IapHelperTests` references this directly to reset the dedupe key
    // in setUp() rather than duplicating the string — see the test file's setUp() comment.
    static let processedOrderIdsKey = "iap_processed_order_ids"
    private static let maxProcessedOrderIds = 200
    private static let separator = "|"

    private let lock = NSLock()
    private var configuration: Configuration?
    private var productCache: [String: IapProduct] = [:]
    private var declaredProductIds: [String] = []
    private var updatesTask: Task<Void, Never>?
    private var processedOrderIds: [String] = []
    private var _purchaseListener: (@Sendable (IapResult) -> Void)?

    /// Every processed transaction, from every trigger path. Single-slot, last-set-wins — see
    /// design spec §4's `IapHelper` section for why this isn't a list or a stream. Read from a
    /// background `Task` (the `transactionUpdates()` consumer) and written from wherever the host
    /// app calls it — lock-protected like every other mutable field on this class.
    ///
    /// See `restorePurchases()`'s doc comment for why a `.success` from this listener cannot be
    /// assumed to be a fresh purchase.
    public var purchaseListener: (@Sendable (IapResult) -> Void)? {
        get {
            lock.lock()
            defer { lock.unlock() }
            return _purchaseListener
        }
        set {
            lock.lock()
            _purchaseListener = newValue
            lock.unlock()
        }
    }

    public init() {}

    public func isReady() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return configuration != nil
    }

    /// Wires the facade up. Idempotent-in-spirit: safe to call again with a new configuration
    /// (e.g. a different player after a re-login) — this phase does not special-case that beyond
    /// simply replacing the stored configuration; product/entitlement state itself is not
    /// player-scoped the way `AdsBrain`'s `SessionState` is (Android's own `IapHelper`/
    /// `Entitlements` are device-scoped singletons too, not per-player).
    public func configure(_ configuration: Configuration) async {
        storeConfiguration(configuration)
        loadProcessedOrderIds(store: configuration.store)
        startConsumingTransactionUpdates(configuration: configuration)
    }

    private func storeConfiguration(_ configuration: Configuration) {
        lock.lock()
        self.configuration = configuration
        lock.unlock()
    }

    private func loadProcessedOrderIds(store: BrainKeyValueStore) {
        let raw = store.string(forKey: Self.processedOrderIdsKey) ?? ""
        let ids = raw.isEmpty ? [] : raw.split(separator: Character(Self.separator)).map(String.init)
        lock.lock()
        processedOrderIds = ids
        lock.unlock()
    }

    private func hasProcessed(orderId: String) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        return processedOrderIds.contains(orderId)
    }

    /// Records `orderId` as reported, trims to the most recent `maxProcessedOrderIds`, and persists
    /// the write OUTSIDE the lock (never hold `lock` across store I/O — matches this codebase's own
    /// established rule, e.g. `DecisionEngine.beliefFor`'s Phase 3 fix).
    private func markProcessed(orderId: String, store: BrainKeyValueStore) {
        lock.lock()
        processedOrderIds.append(orderId)
        if processedOrderIds.count > Self.maxProcessedOrderIds {
            processedOrderIds.removeFirst(processedOrderIds.count - Self.maxProcessedOrderIds)
        }
        let snapshot = processedOrderIds
        lock.unlock()
        store.setString(snapshot.joined(separator: Self.separator), forKey: Self.processedOrderIdsKey)
    }

    /// Consumes `backend.transactionUpdates()` for the lifetime of the process — subscription
    /// renewals, purchases completed after relaunch, or purchases made on another device all
    /// arrive here. Every item is a real, new transaction event, so it goes through the full
    /// `handle(...)` → `reportPurchase` path, same as a direct `purchase(_:)` call (unlike
    /// `restorePurchases()`, which must NOT re-report already-happened purchases).
    private func startConsumingTransactionUpdates(configuration: Configuration) {
        let previousTask = takeUpdatesTask()
        previousTask?.cancel()
        // `backend.transactionUpdates()` is called SYNCHRONOUSLY here, OUTSIDE `lock` — calling
        // into arbitrary injected third-party code while holding this class's own lock risks a
        // deadlock if any `StoreBackend` conformance's `transactionUpdates()` ever synchronously
        // calls back into `IapHelper`. `AsyncStream`'s build closure still runs synchronously at
        // this call site (that is what closes the original startup race — registering the
        // continuation before this function returns, so nothing emitted right after `configure(_:)`
        // returns can be silently dropped); it is simply not done under `lock` any more.
        let backend = configuration.backend
        let stream = backend.transactionUpdates()
        let task = Task { [weak self] in
            for await purchase in stream {
                guard let self else { return }
                guard let currentConfiguration = self.currentConfiguration() else { continue }
                let final = await self.handle(.success(purchase), configuration: currentConfiguration)
                self.purchaseListener?(final)
            }
        }
        storeUpdatesTask(task)
    }

    /// Atomically takes (and clears) whatever task was previously consuming updates, under a
    /// brief lock — the caller cancels it AFTER this returns, outside the lock.
    private func takeUpdatesTask() -> Task<Void, Never>? {
        lock.lock()
        defer { lock.unlock() }
        let existing = updatesTask
        updatesTask = nil
        return existing
    }

    private func storeUpdatesTask(_ task: Task<Void, Never>?) {
        lock.lock()
        updatesTask = task
        lock.unlock()
    }

    private func currentConfiguration() -> Configuration? {
        lock.lock()
        defer { lock.unlock() }
        return configuration
    }

    /// Declares which products this app sells. Call before `queryProducts()`/`purchase(_:)` —
    /// an undeclared product id will never be found in the catalog.
    public func setProducts(_ productIds: [String]) {
        lock.lock()
        declaredProductIds = productIds
        lock.unlock()
    }

    public func productFor(_ productId: String) -> IapProduct? {
        lock.lock()
        defer { lock.unlock() }
        return productCache[productId]
    }

    public func products() -> [IapProduct] {
        lock.lock()
        defer { lock.unlock() }
        return Array(productCache.values)
    }

    /// Fetches price/metadata for every declared product and caches it. Cached prices are what
    /// `purchase(_:)` and the `iap_purchase` event's price fields read — a purchase for a product
    /// never queried has no price to report.
    @discardableResult
    public func queryProducts() async -> [IapProduct] {
        guard let configuration = currentConfiguration() else { return [] }
        let ids = currentDeclaredProductIds()
        guard !ids.isEmpty else { return [] }
        do {
            let fetched = try await configuration.backend.products(for: ids)
            cacheProducts(fetched)
            return fetched
        } catch {
            return []
        }
    }

    private func currentDeclaredProductIds() -> [String] {
        lock.lock()
        defer { lock.unlock() }
        return declaredProductIds
    }

    private func cacheProducts(_ fetched: [IapProduct]) {
        lock.lock()
        for product in fetched { productCache[product.productId] = product }
        lock.unlock()
    }

    /// Initiates a purchase. Returns `.failed` immediately (no store call at all) if the product
    /// hasn't been queried/cached yet — the caller needs to know why nothing happened rather than
    /// silently seeing no response.
    @discardableResult
    public func purchase(_ productId: String) async -> IapResult {
        guard let configuration = currentConfiguration() else {
            return .failed(code: -1, message: "not configured")
        }
        guard let product = productFor(productId) else {
            return .failed(code: -1, message: "product not ready — call queryProducts() first")
        }
        let result: IapResult
        do {
            result = try await configuration.backend.purchase(product)
        } catch {
            result = .failed(code: -1, message: "\(error)")
        }
        let final = await handle(result, configuration: configuration)
        purchaseListener?(final)
        return final
    }

    /// Re-syncs entitlement from the store's current truth. Call on foreground/launch, or when
    /// the user taps "restore purchases." Deliberately does NOT call `reportPurchase`/
    /// `AdsBrain.onPurchase` — these are purchases that already happened in a prior session;
    /// re-reporting them here would double-count/backdate revenue. `Entitlements.sync(...)` and
    /// `backend.finish(...)` run per restored item, matching Android's `queryOwned` exactly, and
    /// `purchaseListener` also fires once per item (spec §4/§6 require this on all three trigger
    /// paths — see the property's own doc comment).
    ///
    /// A host relying on `purchaseListener` alone to grant items CANNOT tell a restored purchase
    /// from a fresh one this way — `IapResult`/`IapPurchase` carry no restore flag. A host that
    /// unconditionally grants a CONSUMABLE on every `.success` it sees from the listener would
    /// over-grant on every restore call. Consumables should be granted from `purchase(_:)`'s own
    /// return value, not from the shared listener, for exactly this reason.
    @discardableResult
    public func restorePurchases() async -> Set<String> {
        guard let configuration = currentConfiguration() else {
            return Entitlements.shared.activeProductIds()
        }
        let purchases: [IapPurchase]
        do {
            purchases = try await configuration.backend.currentEntitlements()
        } catch {
            // Interrupted (task cancellation, or a genuine backend error) partway through
            // fetching the current-entitlements snapshot — do NOT sync on an incomplete result,
            // that would revoke still-valid entitlements for products the fetch hadn't reached
            // yet. Return the last-known-good set instead, unchanged.
            return Entitlements.shared.activeProductIds()
        }
        var owned: Set<String> = []
        for purchase in purchases {
            owned.insert(purchase.productId)
            await configuration.backend.finish(purchase)
            purchaseListener?(.success(purchase))
        }
        // REPLACES, not unions — see Entitlements.sync's own doc comment for why.
        Entitlements.shared.sync(owned)
        // The store's full subscription picture is a SEPARATE fetch from the entitlement one, and
        // classifying it is deliberately the last thing that happens here: a failure of this pass
        // must never cost the caller the entitlement sync that already succeeded above.
        await classifySubscriptions(configuration: configuration, onlyProductId: nil, complete: true)
        return owned
    }

    /// Fetches the store's subscription snapshots and hands them to `SubscriptionLifecycle`.
    ///
    /// `onlyProductId` scopes the set: `nil` is the `restorePurchases()` sweep (`complete: true`,
    /// the store's whole picture), a product id is the post-purchase path (`complete: false`, just
    /// what that one operation touched).
    ///
    /// ═══ A THROW SKIPS THE PASS — IT IS NEVER TURNED INTO `[]` ═══
    ///
    /// `subscriptionSnapshots()` carries the same interrupted-fetch contract `currentEntitlements()`
    /// does, established by Phase 4's Critical finding: a thrown error — INCLUDING a bare
    /// `CancellationError` from routine SwiftUI `.task { }` teardown on view dismissal, which is
    /// not an error condition at all — means "this enumeration did not finish." The only honest
    /// reading of an unfinished enumeration is to classify nothing. A `catch` that swallowed it and
    /// carried on with `[]` would convert "I don't know" into the confident, false assertion
    /// "the store reports no subscriptions at all."
    ///
    /// ═══ HOW LOAD-BEARING IS THAT SKIP, HONESTLY ═══
    ///
    /// Less than the identical skip guarding `currentEntitlements()` above, and this comment will
    /// not claim otherwise. That one is what stands between a cancelled fetch and
    /// `Entitlements.sync([])` revoking a paying customer. This one is not: per design spec §3.4
    /// absence from a snapshot set means "no news", never "lapsed", so `observe([])` produces no
    /// transitions, emits no events and revokes nothing. The swallowing version would be useless
    /// here rather than destructive.
    ///
    /// The skip is kept anyway because it is nearly free and it is the cheaper side of the bet: it
    /// keeps ONE reading of the contract across both fetches (a future reader who sees the two
    /// catch blocks disagree learns the wrong lesson from whichever one they read first), and it
    /// stays correct if absence-inference is ever reintroduced — at which point a swallowed
    /// cancellation would churn a paying customer into `.expired` and start dunning them.
    private func classifySubscriptions(
        configuration: Configuration,
        onlyProductId: String?,
        complete: Bool
    ) async {
        let snapshots: [SubscriptionSnapshot]
        do {
            snapshots = try await configuration.backend.subscriptionSnapshots()
        } catch {
            return
        }
        let scoped = onlyProductId.map { id in snapshots.filter { $0.productId == id } } ?? snapshots
        await configuration.subscriptionLifecycle.observe(scoped, complete: complete)
    }

    /// Shared handling for a raw `IapResult` from ANY trigger path (a direct `purchase(_:)` call,
    /// or a `transactionUpdates()` item): grants entitlement + reports + finalizes, only for
    /// `.success`. Every other case passes through unchanged — a pending/cancelled/failed/
    /// already-owned result never touches entitlements or reporting.
    ///
    /// Order matters: REPORT before FINISH. StoreKit never redelivers a finished transaction, so
    /// finishing first and dying before the report step would lose the `iap_purchase` event and
    /// the `AdsBrain` payer signal permanently. Reporting first means a crash before `finish()`
    /// just causes StoreKit to redeliver the same transaction on the next launch — the
    /// `orderId`-keyed dedupe check below catches that redelivery and skips a second report,
    /// giving an at-least-once-with-dedupe shape instead of an at-most-once-with-silent-loss one.
    /// `Entitlements.grant` is idempotent (a `Set` insert), so re-granting on a redelivered
    /// transaction is always safe and always run.
    private func handle(_ result: IapResult, configuration: Configuration) async -> IapResult {
        guard case .success(let purchase) = result else { return result }
        Entitlements.shared.grant(purchase.productId)
        let alreadyReported = !purchase.orderId.isEmpty && hasProcessed(orderId: purchase.orderId)
        if !alreadyReported {
            await reportPurchase(purchase, configuration: configuration)
            if !purchase.orderId.isEmpty {
                markProcessed(orderId: purchase.orderId, store: configuration.store)
            }
        }
        await configuration.backend.finish(purchase)
        // Post-purchase lifecycle classification (design spec §6). Placed HERE, in the one funnel
        // every successful transaction passes through, rather than at each trigger path's own call
        // site — a scattered version would silently miss whichever path was added next.
        //
        // Deliberately OUTSIDE the `alreadyReported` dedupe above: that dedupe exists to stop
        // revenue being double-counted, and classification is not revenue. It is idempotent (a
        // re-observed state produces no transition and no event), and a redelivered renewal is
        // exactly the moment the lifecycle most wants to be refreshed.
        //
        // Placed AFTER `finish(...)` so the REPORT-before-FINISH invariant documented above keeps
        // its tight window: `classifySubscriptions` is a store round trip, and slotting it between
        // the report and the finish would widen the interval in which a crash forces a redelivery.
        if isSubscription(purchase) {
            await classifySubscriptions(
                configuration: configuration, onlyProductId: purchase.productId, complete: false
            )
        }
        return result
    }

    /// Whether this purchase is for a subscription — a consumable has no subscription lifecycle at
    /// all, and must not pay for a subscription-status round trip to learn that.
    ///
    /// The cached `IapProduct.type` is the real answer (StoreKit 2's own 4-case product type);
    /// `isAutoRenewing` is only the fallback for a purchase whose product was never queried, and is
    /// deliberately a WEAK one — `StoreKit2Backend` fail-safes renewal intent to `false` whenever it
    /// cannot be trusted, so it under-reports subscriptions rather than over-reporting them. Same
    /// expression `reportPurchase`'s `is_subscription` field already used, extracted so the two
    /// can't drift apart.
    private func isSubscription(_ purchase: IapPurchase) -> Bool {
        productFor(purchase.productId)?.type.isSubscription ?? purchase.isAutoRenewing
    }

    /// A successful transaction must reach every reporting destination independently — one
    /// failing must never block another. Three destinations now: the event sink, the brain, and
    /// attribution.
    ///
    /// The third was the gap this comment used to describe as deliberate. It is closed by
    /// `AttributionBridge`, which names no vendor — see that type for why the SDK owns the fact
    /// of the purchase and the host owns the SDK that receives it. The amount and currency go out
    /// exactly as the store reported them, never through `FxRates`: the attribution network
    /// converts with its own same-day rate, and a USD figure computed here would be a guess
    /// layered on top of a guess.
    private func reportPurchase(_ purchase: IapPurchase, configuration: Configuration) async {
        await configuration.eventSink.send(SdkEvent(
            eventName: "iap_purchase",
            eventData: [
                "product_id": .string(purchase.productId),
                "order_id": .string(purchase.orderId),
                "price_micros": .int(Int(purchase.priceMicros)),
                "currency_code": .string(purchase.currencyCode),
                "quantity": .int(purchase.quantity),
                "channel": .string(purchase.channel.rawValue),
                "is_subscription": .int(isSubscription(purchase) ? 1 : 0),
                "purchase_token": .string(purchase.purchaseToken)
            ],
            userProperty: UserProperty(userId: configuration.playerId)
        ))
        // Quantity of currency lost to unknown FX is left honestly blank, never guessed — see
        // FxRates. The purchase still counts toward purchaseCountLifetime regardless (the
        // payer-guardrail invariant reads that count, never a USD total — design spec §3.8).
        let usd = FxRates.toUsd(priceMicros: purchase.priceMicros, currencyCode: purchase.currencyCode)
        await AdsBrain.shared.onPurchase(usd: usd ?? 0.0)

        await configuration.attribution.logPurchase(AttributionPurchase(
            productId: purchase.productId,
            orderId: purchase.orderId,
            revenueMicros: purchase.priceMicros,
            currencyCode: purchase.currencyCode,
            quantity: purchase.quantity,
            // The SAME expression the `is_subscription` field above uses, through the same
            // helper: an attribution network told a subscription is a one-off mis-models the
            // recurring revenue, and the two must not be able to disagree.
            isSubscription: isSubscription(purchase)
        ))
    }
}
