import StoreKit
import MonetizationBrainCore

/// The one real `StoreBackend` conformance — StoreKit 2. `MonetizationBrainCore` never imports
/// `StoreKit` directly; this target exists solely to isolate that import, mirroring
/// `MonetizationBrainUMP`'s isolation of `GoogleUserMessagingPlatform` (design spec §2).
/// A `final class`, not a `struct`: `finish(_:)` needs to look up the ORIGINAL `Transaction`
/// object for a given `IapPurchase` (StoreKit 2's `finish()` is a method on `Transaction` itself,
/// and re-deriving a `Transaction` from scratch isn't possible — it's not something you
/// reconstruct from an id). A struct's value semantics would make a cache populated by one
/// method invisible to another; a class with `NSLock`-protected shared storage is the correct
/// shape here, mirroring every other stateful type in this codebase.
///
/// `transactionsByOrderId` grows unboundedly for entitlements that are never finished (e.g. a
/// `currentEntitlements()`/`transactionUpdates()` item `IapHelper` chooses not to call
/// `finish(_:)` on for some reason) — acceptable for this phase (every path `IapHelper` actually
/// has DOES call `finish(_:)` on everything it processes), but worth revisiting if a future phase
/// adds a path that produces `IapPurchase`s without ever finishing them.
public final class StoreKit2Backend: StoreBackend, @unchecked Sendable {
    private let lock = NSLock()
    /// Caches the real `Transaction` behind every `IapPurchase` this instance has ever produced,
    /// keyed by `orderId` (`"\(transaction.id)"`) — populated everywhere an `IapPurchase` is
    /// built, consumed only by `finish(_:)`. Without this, `finish(_:)` would have no way to
    /// recover the transaction object StoreKit 2's `finish()` requires, for ANY purchase not
    /// still present in `Transaction.currentEntitlements` (every consumable, the instant it's
    /// been consumed once — which is exactly when `finish()` is called).
    private var transactionsByOrderId: [String: Transaction] = [:]

    public init() {}

    public func products(for identifiers: [String]) async throws -> [IapProduct] {
        let products = try await Product.products(for: identifiers)
        return products.map(Self.toIapProduct)
    }

    public func purchase(_ product: IapProduct) async throws -> IapResult {
        let storeProducts = try await Product.products(for: [product.productId])
        guard let storeProduct = storeProducts.first else {
            return .failed(code: -1, message: "product not found in store catalog")
        }
        let result = try await storeProduct.purchase()
        switch result {
        case .success(let verification):
            switch verification {
            case .verified(let transaction):
                return .success(await cache(transaction, jwsRepresentation: verification.jwsRepresentation, product: storeProduct))
            case .unverified:
                return .failed(code: -1, message: "transaction failed StoreKit verification")
            }
        case .userCancelled:
            return .userCanceled
        case .pending:
            // No verified Transaction exists yet for a pending result — build the IapPurchase
            // from the product/request we already have, matching Android's own contract that a
            // pending purchase must never be granted entitlement (design spec §3.6). Nothing to
            // cache: there is no Transaction object to look up later for this case.
            return .pending(IapPurchase(
                productId: product.productId, orderId: "", purchaseToken: "",
                purchaseTimeMs: Self.clampToEpochMs(Date().timeIntervalSince1970), state: .pending,
                isFinished: false, isAutoRenewing: false, quantity: 1,
                priceMicros: product.priceMicros, currencyCode: product.currencyCode
            ))
        @unknown default:
            return .failed(code: -1, message: "unknown StoreKit purchase result")
        }
    }

    public func currentEntitlements() async throws -> [IapPurchase] {
        var result: [IapPurchase] = []
        for await verification in Transaction.currentEntitlements {
            try Task.checkCancellation()
            guard case .verified(let transaction) = verification else { continue }
            let products = try? await Product.products(for: [transaction.productID])
            result.append(await cache(transaction, jwsRepresentation: verification.jwsRepresentation, product: products?.first))
        }
        try Task.checkCancellation()
        return result
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // SUBSCRIPTION SNAPSHOTS — FAIL-CLOSED MAPPING RULES
    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // Three separate open-ended/untrusted inputs meet here, and each gets an explicit answer:
    //
    // 1. `RenewalState` and `ExpirationReason` are STRUCTS with static members, not enums. A
    //    `switch` over either cannot be exhaustive and needs a `default:`. Every unrecognized
    //    value maps to `.unknown` — never to a guess. `.unknown` never grants entitlement and
    //    never produces a transition (design spec §3.7), so an Apple-side addition this SDK
    //    hasn't learned yet freezes a customer's state instead of churning them out of it.
    //
    // 2. `Status.transaction` and `Status.renewalInfo` are each a `VerificationResult`. Both are
    //    matched with a `switch`/`case .verified`, NOT `try payloadValue`, so the unverified case
    //    is a value handled on purpose rather than an error that unwinds the whole fetch. An
    //    unverified payload NEVER becomes an entitled state:
    //      • unverified `renewalInfo` ⇒ `.unknown` renewal state, `willAutoRenew: false`, no
    //        dates, no expiration reason. Not entitled, not a transition, prior state retained.
    //      • unverified `transaction` ⇒ the status is DROPPED entirely, because the product id is
    //        only reachable through that payload (`Status` carries no product id on iOS 15, and
    //        `Status.all`, which is tagged by group, is iOS 17+). Attributing it to the group's
    //        other product would be a guess. A dropped status means the product is simply absent
    //        from the returned set, which per design spec §3.4 is `.none`-by-omission — no
    //        transition, prior state retained. Identical fail-safe outcome to `.unknown`.
    //
    // 3. `Product.SubscriptionInfo.status(for:)` returns an ARRAY, per subscription GROUP, and
    //    Family Sharing can produce several statuses for the same product id (the customer's own
    //    purchase plus one shared into their family). Taking `.first` is a real bug. See
    //    `moreFavorable(_:_:)` for the reduction rule.
    //
    // Interrupted-fetch contract: any `Task` cancellation or store error propagates OUT of this
    // method rather than shortening the result, so a caller can tell a complete enumeration from
    // an interrupted one. See the `StoreBackend.subscriptionSnapshots()` doc comment.
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    /// Every auto-renewable subscription this customer has any history with, as StoreKit reports
    /// it right now. Throws rather than truncating — see the protocol's doc comment for the
    /// interrupted-fetch contract this inherits from `currentEntitlements()`.
    ///
    /// Known limitations, stated rather than silently absorbed:
    /// - **Discovery is bounded by `Transaction.all`.** A subscription group the customer has
    ///   never transacted in produces no snapshot — correct, there is nothing to classify. But
    ///   `Transaction.all` is also empty on a fresh install until the App Store has synced the
    ///   customer's history, so an early call can legitimately return `[]` for a real subscriber.
    ///   That is why absence is `.none`-by-omission and never churn (design spec §3.4).
    /// - **No subscription-group modeling.** Statuses are fetched per group but collapsed to a
    ///   flat per-product result; `Status.all`, which is tagged by group, is iOS 17+ and this
    ///   package's floor is iOS 15. Nothing in this phase needs group-level rollups (spec §1).
    /// - **Refund vs. Family Sharing revocation are indistinguishable** — both arrive as
    ///   `.revoked` on iOS 15 (`RevocationType` is iOS 26.4). Design spec §7.
    public func subscriptionSnapshots() async throws -> [SubscriptionSnapshot] {
        // Which subscription groups does this customer have any history in? Derived from the
        // transaction history rather than from a caller-supplied product list, because this
        // backend deliberately holds no product catalog (`products(for:)` is stateless) and
        // because the interesting states — `.expired`, `.revoked`, `.inBillingRetryPeriod` —
        // are exactly the ones absent from `Transaction.currentEntitlements`.
        var groupIds: Set<String> = []
        for await verification in Transaction.all {
            try Task.checkCancellation()
            guard case .verified(let transaction) = verification else { continue }
            guard transaction.productType == .autoRenewable,
                  let groupId = transaction.subscriptionGroupID else { continue }
            groupIds.insert(groupId)
        }
        try Task.checkCancellation()
        guard !groupIds.isEmpty else { return [] }

        var byProductId: [String: SubscriptionSnapshot] = [:]
        // Sorted purely for deterministic iteration; the reduction below is order-independent.
        for groupId in groupIds.sorted() {
            try Task.checkCancellation()
            for status in try await Product.SubscriptionInfo.status(for: groupId) {
                guard let snapshot = Self.toSubscriptionSnapshot(status) else { continue }
                byProductId[snapshot.productId] = Self.moreFavorable(byProductId[snapshot.productId], snapshot)
            }
        }
        try Task.checkCancellation()
        return byProductId.values.sorted { $0.productId < $1.productId }
    }

    public func transactionUpdates() -> AsyncStream<IapPurchase> {
        AsyncStream { continuation in
            let task = Task { [weak self] in
                for await verification in Transaction.updates {
                    guard case .verified(let transaction) = verification, let self else { continue }
                    let products = try? await Product.products(for: [transaction.productID])
                    continuation.yield(await self.cache(transaction, jwsRepresentation: verification.jwsRepresentation, product: products?.first))
                }
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    public func finish(_ purchase: IapPurchase) async {
        let transaction = takeCachedTransaction(orderId: purchase.orderId)
        await transaction?.finish()
    }

    /// Builds the `IapPurchase` AND records the real `Transaction` behind it — every path that
    /// produces an `IapPurchase` (purchase/restore/background update) goes through this, so
    /// `finish(_:)` can always find what it needs later regardless of which path produced the
    /// purchase.
    ///
    /// `async` because renewal intent is only reachable through an async hop (see
    /// `renewalIntent(for:)`). The `await` happens BEFORE the lock is taken — the lock lives
    /// entirely inside the synchronous `store(_:orderId:)` helper and is never held across a
    /// suspension point.
    private func cache(_ transaction: Transaction, jwsRepresentation: String, product: Product?) async -> IapPurchase {
        let willAutoRenew = await Self.renewalIntent(for: transaction)
        let purchase = Self.toIapPurchase(
            transaction, jwsRepresentation: jwsRepresentation, product: product,
            willAutoRenew: willAutoRenew
        )
        store(transaction, orderId: purchase.orderId)
        return purchase
    }

    private func store(_ transaction: Transaction, orderId: String) {
        lock.lock()
        defer { lock.unlock() }
        transactionsByOrderId[orderId] = transaction
    }

    private func takeCachedTransaction(orderId: String) -> Transaction? {
        lock.lock()
        defer { lock.unlock() }
        return transactionsByOrderId.removeValue(forKey: orderId)
    }

    private static func toIapProduct(_ product: Product) -> IapProduct {
        IapProduct(
            productId: product.id,
            type: toProductType(product.type),
            formattedPrice: product.displayPrice,
            priceMicros: toPriceMicros(product.price),
            currencyCode: product.priceFormatStyle.currencyCode,
            title: product.displayName,
            description: product.description
        )
    }

    private static func toIapPurchase(
        _ transaction: Transaction, jwsRepresentation: String, product: Product?, willAutoRenew: Bool
    ) -> IapPurchase {
        IapPurchase(
            productId: transaction.productID,
            orderId: "\(transaction.id)",
            purchaseToken: jwsRepresentation,
            purchaseTimeMs: Self.clampToEpochMs(transaction.purchaseDate.timeIntervalSince1970),
            state: .purchased,
            isFinished: false,
            isAutoRenewing: willAutoRenew,
            quantity: transaction.purchasedQuantity,
            priceMicros: product.map { toPriceMicros($0.price) } ?? 0,
            currencyCode: product?.priceFormatStyle.currencyCode ?? ""
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // RENEWAL INTENT — WHY THIS IS NOT `productType == .autoRenewable`
    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // `IapPurchase.isAutoRenewing` is documented as renewal INTENT — "will this charge again?".
    // This mapper used to answer it with `transaction.productType == .autoRenewable`, which is the
    // product's TYPE and is permanently `true` for every subscription ever sold, cancelled or not.
    // A customer who turned auto-renew off but is still inside their paid period reports
    // `productType == .autoRenewable` and `willAutoRenew == false`, and was being reported to the
    // SDK as actively renewing. Anything classifying "subscribed && isAutoRenewing ⇒ active,
    // subscribed ⇒ cancelled" could therefore never once take the cancelled branch, so a customer
    // on their way out looked identical to one who is staying. See design spec §3.2.
    //
    // The truth is `RenewalInfo.willAutoRenew` (iOS 15.0), which only exists behind an async hop.
    // Fail-safe direction on every path that can't answer: `false`. Reporting "won't renew" for a
    // subscription that will is a missed win-back nudge; reporting "will renew" for one that won't
    // suppresses the retention prompt that had a chance of working, and is how a churning customer
    // stays invisible until they are gone.
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    /// Renewal intent for the subscription behind `transaction`, or `false` when there is no such
    /// thing or it can't be trusted.
    ///
    /// Non-subscription purchases return immediately and pay NO async cost: a consumable has no
    /// subscription status at all, and looking one up for every gem pack would add a store round
    /// trip to the hot purchase path for a value that is always `false`.
    ///
    /// `Transaction.subscriptionStatus` is `@backDeployed(before: iOS 17.0)` and is genuinely
    /// available on this package's iOS 15.0 floor — unlike `Status.all`, which is iOS 17+ and is
    /// deliberately not used anywhere in this file.
    private static func renewalIntent(for transaction: Transaction) async -> Bool {
        guard transaction.productType == .autoRenewable else { return false }
        guard let status = await transaction.subscriptionStatus else { return false }
        // Fail closed on an unverified payload: an unverified `RenewalInfo` is not evidence of
        // anything, least of all of a future charge.
        guard case .verified(let renewalInfo) = status.renewalInfo else { return false }
        return renewalInfo.willAutoRenew
    }

    /// Maps one StoreKit subscription status to this SDK's owned snapshot, or `nil` when the
    /// status can't be attributed to a product id at all (unverified `transaction`).
    private static func toSubscriptionSnapshot(_ status: Product.SubscriptionInfo.Status) -> SubscriptionSnapshot? {
        // `Status` exposes no product id of its own on iOS 15 — the only way to learn which
        // product in the group this status describes is the transaction payload, so an unverified
        // transaction is unattributable and the status is dropped.
        guard case .verified(let transaction) = status.transaction else { return nil }

        guard case .verified(let renewalInfo) = status.renewalInfo else {
            // Known product, untrustworthy renewal state. `.unknown` is the fail-closed landing
            // spot: never entitled, never a transition.
            return SubscriptionSnapshot(
                productId: transaction.productID, renewalState: .unknown, willAutoRenew: false
            )
        }

        return SubscriptionSnapshot(
            productId: transaction.productID,
            renewalState: toRenewalState(status.state),
            willAutoRenew: renewalInfo.willAutoRenew,
            // `Transaction.expirationDate` and `RenewalInfo.gracePeriodExpirationDate` are both
            // `Date`. Note that `RenewalInfo.renewalDate` is ALSO a `Date` despite Apple's doc
            // abstract claiming it is "in milliseconds" — nothing here may assume otherwise.
            expirationMs: toEpochMs(transaction.expirationDate),
            gracePeriodExpirationMs: toEpochMs(renewalInfo.gracePeriodExpirationDate),
            expirationReason: renewalInfo.expirationReason.map(toExpirationReason)
        )
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════
    // THE PURE PART OF THIS FILE — `internal`, NOT `private`, SO IT CAN BE TESTED
    // ═══════════════════════════════════════════════════════════════════════════════════════
    //
    // Everything below takes values and returns values: no actor state, no network, no StoreKit
    // objects that only the store can mint. It is also the part where a mistake costs money or
    // an entitlement — the Family Sharing reduction decides who keeps access and who is offered
    // a win-back discount, and the epoch conversions sit on a payment path where `Int64(Double)`
    // traps.
    //
    // This file used to carry a README line saying it was "verified by type-checking only",
    // because `Transaction` and `Product` cannot be constructed in a unit test. That is true of
    // the I/O half and NOT of this half: `RenewalState`, `ExpirationReason` and `ProductType` are
    // `RawRepresentable` structs a test CAN build, including with an unrecognised raw value —
    // which is the only way to exercise the `default:` branches below. Dropping `private` is the
    // whole cost of testing the money-adjacent logic in this target, and it buys the target its
    // first tests.

    /// `Product.SubscriptionInfo.RenewalState` is a struct with static members, so this `switch`
    /// cannot be exhaustive and the `default:` is load-bearing rather than dead code.
    static func toRenewalState(_ state: Product.SubscriptionInfo.RenewalState) -> MonetizationBrainCore.SubscriptionRenewalState {
        switch state {
        case .subscribed: return .subscribed
        case .expired: return .expired
        case .inBillingRetryPeriod: return .inBillingRetryPeriod
        case .inGracePeriod: return .inGracePeriod
        case .revoked: return .revoked
        default: return .unknown
        }
    }

    /// `RenewalInfo.ExpirationReason` is likewise a struct, hence the `default:`.
    static func toExpirationReason(
        _ reason: Product.SubscriptionInfo.RenewalInfo.ExpirationReason
    ) -> SubscriptionExpirationReason {
        switch reason {
        case .autoRenewDisabled: return .autoRenewDisabled
        case .billingError: return .billingError
        case .didNotConsentToPriceIncrease: return .didNotConsentToPriceIncrease
        case .productUnavailable: return .productUnavailable
        default: return .unknown
        }
    }

    /// Reduction rule for the several statuses Family Sharing can report for ONE product id.
    ///
    /// Entitlement comes first, and that part is Apple's own rule — a customer is entitled if ANY
    /// of their statuses is `.subscribed` or `.inGracePeriod`. Below the entitlement line the
    /// ordering is NOT "least bad"; it is STRONGEST SIGNAL, so the reduction never hides a fact the
    /// lifecycle layer acts on:
    ///
    ///     subscribed > inGracePeriod > inBillingRetryPeriod > revoked > expired > unknown
    ///
    /// `.revoked` deliberately outranks `.expired`, which looks backwards until you follow what
    /// reads the result. A refund is an active, deliberate event; a lapse is the absence of one.
    /// A customer holding both a refunded purchase and a lapsed one has demonstrably refunded, and
    /// `SubscriptionLifecycle.isWinBackEligible` withholds discount offers from exactly that person
    /// (see its own banner). Ranking `.expired` higher would let a stale lapse mask the refund and
    /// hand them the offer anyway — the ordering is the only thing standing between those two
    /// outcomes, since neither state is entitled and nothing else distinguishes them downstream.
    ///
    /// Two further consequences worth stating: a customer whose own purchase lapsed but who is
    /// still on a family plan reads `.subscribed` (correct — they are entitled), and `.unknown`
    /// sits at the bottom so a single unrecognised status can never erase a real one that arrived
    /// alongside it.
    ///
    /// Taking `.first` instead — which is what this looks like it could be — silently revokes a
    /// family member's live entitlement whenever the store happens to list their own expired
    /// purchase first.
    ///
    /// Ties are broken by later `expirationMs`, then by `willAutoRenew`, so the reduction is
    /// independent of the order the store happened to list the statuses in. Without those two
    /// tiebreaks, two `.subscribed` statuses would resolve to whichever arrived first and the
    /// reported expiry would flip between calls.
    static func moreFavorable(
        _ current: SubscriptionSnapshot?, _ candidate: SubscriptionSnapshot
    ) -> SubscriptionSnapshot {
        guard let current else { return candidate }
        let candidateRank = signalRank(candidate.renewalState)
        let currentRank = signalRank(current.renewalState)
        if candidateRank != currentRank { return candidateRank > currentRank ? candidate : current }
        if candidate.expirationMs != current.expirationMs {
            return candidate.expirationMs > current.expirationMs ? candidate : current
        }
        return candidate.willAutoRenew && !current.willAutoRenew ? candidate : current
    }

    static func signalRank(_ state: MonetizationBrainCore.SubscriptionRenewalState) -> Int {
        switch state {
        case .subscribed: return 5
        case .inGracePeriod: return 4
        case .inBillingRetryPeriod: return 3
        case .revoked: return 2
        case .expired: return 1
        case .unknown: return 0
        }
    }

    /// Epoch MILLISECONDS, matching `IapPurchase.purchaseTimeMs`'s existing convention. An absent
    /// date is `0`, never a sentinel that could read as a real instant.
    ///
    /// Clamped in `Double` space before converting, for the same reason
    /// `SubscriptionLifecycle.parseOpenCount` is: `Int64(someDouble)` TRAPS above `Int64.max`, and
    /// a `Date` far enough in the future reaches that. Unlike the remote-config path — where the
    /// identical pattern was a Critical, ops-typo-reachable crash — these dates come from a
    /// cryptographically verified StoreKit payload, so this is defense in depth rather than a live
    /// hole: it costs one comparison and removes a whole class of "only if Apple ships something
    /// absurd" crash from a payment path. Saturating is right here — a date beyond year 292M is
    /// not a real instant, and the callers already treat these as opaque ordering values.
    static func toEpochMs(_ date: Date?) -> Int64 {
        guard let date else { return 0 }
        return clampToEpochMs(date.timeIntervalSince1970)
    }

    /// Shared by every `Date` -> epoch-ms conversion in this file, so no call site can forget it.
    ///
    /// ═══ THE GUARD IS ON THE INPUT, NOT ON THE PRODUCT — AND THAT IS THE FIX ═══
    ///
    /// It used to be `let millis = seconds * 1000; guard millis.isFinite`. For a finite but huge
    /// `seconds`, the multiply itself overflows to infinity, so the guard fired and the function
    /// returned **0** — the epoch. That contradicts the saturation this comment promises and, more
    /// to the point, INVERTS the value's meaning: callers treat these as opaque ordering values,
    /// and 0 sorts as the oldest instant there is while the input meant the newest. An absurd
    /// far-future expiry would have read as long-expired. Writing this target's first tests is
    /// what surfaced it; nothing reachable produces such a date, since these come from a verified
    /// StoreKit payload, so it was defence in depth that did not defend.
    ///
    /// Now: a finite input saturates, whatever its magnitude, by clamping in SECONDS before the
    /// multiply. NaN and ±infinity are not magnitudes at all — they are garbage, and they keep
    /// reading as 0, the same as an absent date.
    static func clampToEpochMs(_ secondsSince1970: Double) -> Int64 {
        guard secondsSince1970.isFinite else { return 0 }
        let maxSeconds = Double(Int64.max / 2) / 1000
        let clampedSeconds = max(-maxSeconds, min(secondsSince1970, maxSeconds))
        return Int64(clampedSeconds * 1000)
    }

    static func toProductType(_ type: Product.ProductType) -> IapProductType {
        switch type {
        case .consumable: return .consumable
        case .nonConsumable: return .nonConsumable
        case .autoRenewable: return .autoRenewable
        case .nonRenewable: return .nonRenewing
        default: return .nonConsumable
        }
    }

    /// Exact `Decimal` arithmetic — never through `Double` (design spec §3.5). Lossless for any
    /// real-world currency amount.
    static func toPriceMicros(_ price: Decimal) -> Int64 {
        let micros = price * 1_000_000
        let rounded = NSDecimalNumber(decimal: micros).rounding(accordingToBehavior: nil)
        return rounded.int64Value
    }
}
