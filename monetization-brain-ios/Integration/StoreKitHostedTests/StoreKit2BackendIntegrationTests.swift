#if os(iOS)
import XCTest
import StoreKit
import StoreKitTest
@testable import MonetizationBrainStoreKit
import MonetizationBrainCore

/// `StoreKit2Backend` against a real StoreKit, driven by `SKTestSession`.
///
/// ═══ WHY THIS TARGET EXISTS ═══
///
/// Everything in `StoreKit2Backend` divides into two halves. The pure mapping half — renewal
/// states, expiration reasons, price conversion, the `moreFavorable` ordering — is covered by
/// `MonetizationBrainStoreKitTests` against values a test constructs directly. The other half is
/// the part that calls StoreKit: `products(for:)`, `purchase(_:)`, `currentEntitlements()`,
/// `subscriptionSnapshots()`, `finish(_:)`, and the `cache`/`store`/`takeCachedTransaction`
/// bookkeeping between them. None of it was reachable from a test, because `Product` and
/// `Transaction` have no public initialiser — the only way to obtain one is to have StoreKit make
/// it. That left the adapter between this SDK and the App Store as the least-tested file in the
/// package, which is a poor place for that to be true.
///
/// `SKTestSession` makes them: it stands up a local StoreKit backed by `Products.storekit`, so the
/// objects are genuine StoreKit objects and the mapping under test is the real one.
///
/// ═══ THESE ONLY RUN ON A SIMULATOR ═══
///
/// `#if os(iOS)` means this file compiles to NOTHING under `swift test` on macOS. That is
/// deliberate and it is also a trap, and it is why `Scripts/verify.sh` runs this target on a
/// simulator explicitly: a test target that silently evaluates to zero tests reports success just
/// as loudly as one that passed.
///
/// ═══ THIS TARGET LIVES OUTSIDE `Tests/`, AND THAT IS THE POINT ═══
///
/// It is built ONLY by `Integration/MonetizationBrainIntegration.xcodeproj`, never by
/// `swift test`. There was briefly a mirror target under `Tests/` that skipped when StoreKit was
/// unavailable, and it was deleted: on a developer Mac it skipped, and on the CI runner StoreKit
/// served products but refused the purchase, so the same commit skipped in one place and failed
/// in the other. A test target whose outcome depends on which machine ran it is worse than no
/// target — these tests have one home now, and in it they run.
///
/// StoreKit needed all THREE of these, and each one was found by measurement after the previous
/// one turned out not to be enough on its own:
///
///   1. a HOST APP, so the process has an app identity. Without it `Bundle.main` is
///      `com.apple.dt.xctest.tool`, Xcode's generic agent.
///   2. CODE SIGNING — ad-hoc (`CODE_SIGN_IDENTITY = "-"`) is enough, but signing disabled is not.
///      An unsigned app carries no entitlements at all.
///   3. a `get-task-allow` ENTITLEMENT. This was the last missing piece and the least obvious:
///      with a host app AND ad-hoc signing but no entitlements file, `SKTestSession.buyProduct`
///      still threw `.notEntitled` and the catalogue was still empty.
///
/// The symptom for all three is identical and silent — `Product.products(for:)` returns `[]` and
/// nothing throws — which is why `setUp` probes for a served catalogue and calls `XCTSkip` with
/// what it actually measured, rather than letting fifteen assertions fail on "no product" and
/// bury the one fact that explains them.
@available(iOS 15.4, *)
final class StoreKit2BackendIntegrationTests: XCTestCase {
    private var session: SKTestSession!
    private var backend: StoreKit2Backend!

    private static let consumableId = "test.gems.small"
    private static let nonConsumableId = "test.removeads"
    private static let subscriptionId = "test.vip.monthly"

    override func setUp() async throws {
        try await super.setUp()
        let url = try XCTUnwrap(
            Self.storekitConfigurationURL(),
            "Products.storekit missing from the test bundle; check the target's resources rule"
        )
        session = try SKTestSession(contentsOf: url)
        // ORDER MATTERS. `resetToDefaultState()` resets the session's SETTINGS too, so setting
        // these before it silently puts `disableDialogs` back to false — and a purchase then
        // blocks forever on a confirmation sheet no test can tap. That hang is not obvious from
        // the outside: xcodebuild simply prints nothing and eventually times out the whole run.
        session.resetToDefaultState()
        try session.clearTransactions()
        session.disableDialogs = true
        session.askToBuyEnabled = false
        backend = StoreKit2Backend()

        // The probe. An unentitled process does not throw here — it returns an EMPTY catalogue,
        // which is why this must be checked rather than assumed: without it every assertion below
        // fails on "no product test.removeads" and buries the one fact that explains all of them.
        // See the type doc for the measurements behind this.
        let served = try await backend.products(for: [Self.nonConsumableId])
        if served.isEmpty {
            // A skip here is NOT routine — this target exists precisely so these run, and
            // `Scripts/verify.sh` turns any skip in it into a failure. The message explains
            // what broke rather than what is expected.
            throw XCTSkip(
                "StoreKit served no products, so StoreKit testing is not active. "
                + "Bundle.main is \(Bundle.main.bundleIdentifier ?? "<none>"). "
                + "This target supplies the three things StoreKit needs — host app, ad-hoc "
                + "signing, get-task-allow — so if this fires, one of them has regressed. "
                + "Products.storekit is not the problem."
            )
        }
    }

    override func tearDown() async throws {
        session = nil
        backend = nil
        try await super.tearDown()
    }

    // ═══ products(for:) ═══

    /// The `Product` -> `IapProduct` mapping over a real catalogue, including the thing most
    /// likely to be wrong and least likely to be noticed: `priceMicros`. A price is a `Decimal`
    /// and the conversion goes through `NSDecimalNumber`; a float round-trip here would show up
    /// as revenue that is off by a cent on every single event.
    func test_everyProductTypeMapsWithItsPriceIntact() async throws {
        let products = try await backend.products(for: [
            Self.consumableId, Self.nonConsumableId, Self.subscriptionId,
        ])
        XCTAssertEqual(products.count, 3)

        let byId = Dictionary(uniqueKeysWithValues: products.map { ($0.productId, $0) })

        let gems = try XCTUnwrap(byId[Self.consumableId])
        XCTAssertEqual(gems.type, .consumable)
        XCTAssertEqual(gems.priceMicros, 990_000, "0.99 must be exactly 990000 micros")
        XCTAssertEqual(gems.title, "Gem Pack (Small)")
        XCTAssertEqual(gems.description, "A small handful of gems.")
        XCTAssertFalse(gems.currencyCode.isEmpty)
        XCTAssertFalse(gems.formattedPrice.isEmpty)

        let removeAds = try XCTUnwrap(byId[Self.nonConsumableId])
        XCTAssertEqual(removeAds.type, .nonConsumable)
        XCTAssertEqual(removeAds.priceMicros, 4_990_000)

        let vip = try XCTUnwrap(byId[Self.subscriptionId])
        XCTAssertEqual(vip.type, .autoRenewable)
        XCTAssertEqual(vip.priceMicros, 9_990_000)
        XCTAssertTrue(vip.type.isSubscription)
    }

    /// The `StoreBackend` contract says an unrecognised identifier is simply absent, never a
    /// failure for the whole call. A store that drops one renamed product must not take the
    /// other two down with it.
    func test_anUnknownIdentifierIsAbsentRatherThanFailingTheWholeQuery() async throws {
        let products = try await backend.products(
            for: [Self.consumableId, "test.does.not.exist"]
        )
        XCTAssertEqual(products.map(\.productId), [Self.consumableId])
    }

    func test_anEmptyQueryReturnsNothingAndDoesNotThrow() async throws {
        let products = try await backend.products(for: [])
        XCTAssertTrue(products.isEmpty)
    }

    // ═══ purchase(_:) ═══

    /// The success path end to end, and the two fields that only this path can prove: the
    /// purchase token is the signed JWS (what the backend verifies the receipt from — an empty
    /// one means server-side verification can never succeed), and price/currency are joined in
    /// from the product, since a raw `Transaction` carries no price at all.
    func test_aSuccessfulPurchaseCarriesTheJwsAndTheJoinedPrice() async throws {
        let product = try await XCTUnwrapFirstProduct(Self.nonConsumableId)

        let result = try await backend.purchase(product)

        guard case .success(let purchase) = result else {
            return XCTFail("expected .success, got \(result)")
        }
        XCTAssertEqual(purchase.productId, Self.nonConsumableId)
        XCTAssertEqual(purchase.state, .purchased)
        XCTAssertEqual(purchase.quantity, 1)
        XCTAssertEqual(purchase.channel, .appStore)
        XCTAssertFalse(purchase.isFinished, "finish() has not been called yet")
        XCTAssertFalse(
            purchase.purchaseToken.isEmpty,
            "purchaseToken is the signed JWS; empty means the backend cannot verify this receipt"
        )
        XCTAssertFalse(purchase.orderId.isEmpty)
        XCTAssertEqual(purchase.priceMicros, 4_990_000, "price is joined in from the product")
        XCTAssertFalse(purchase.currencyCode.isEmpty)
        XCTAssertGreaterThan(purchase.purchaseTimeMs, 0)
    }

    /// A non-consumable is not auto-renewing. `isAutoRenewing` drives whether the host treats a
    /// purchase as an ongoing entitlement, and `renewalIntent` returns early for anything that is
    /// not `.autoRenewable` — this is the assertion that the early return is the right one.
    func test_aNonConsumableIsNotReportedAsAutoRenewing() async throws {
        let product = try await XCTUnwrapFirstProduct(Self.nonConsumableId)
        guard case .success(let purchase) = try await backend.purchase(product) else {
            return XCTFail("expected .success")
        }
        XCTAssertFalse(purchase.isAutoRenewing)
    }

    /// A fresh subscription renews by default, and this is the path that reads `RenewalInfo`
    /// through `renewalIntent` — the one place a `.verified` check gates a real value rather than
    /// a mapping.
    func test_aFreshSubscriptionIsReportedAsAutoRenewing() async throws {
        let product = try await XCTUnwrapFirstProduct(Self.subscriptionId)
        guard case .success = try await backend.purchase(product) else {
            return XCTFail("expected .success")
        }
        // Re-read rather than asserting on the value `purchase(_:)` returned: at that instant
        // `transaction.subscriptionStatus` is still nil, so `renewalIntent` correctly fails closed
        // to false. That is the SDK behaving as designed on absent data, not a wrong answer — see
        // `eventually`. What must become true is the state once StoreKit has published it.
        try await eventually("the subscription reports it will auto-renew") {
            let snapshots = try await self.backend.subscriptionSnapshots()
            return snapshots.first { $0.productId == Self.subscriptionId }?.willAutoRenew == true
        }
    }

    /// Covers `renewalIntent(for:)`, which nothing else does once the subscription assertions
    /// read from `subscriptionSnapshots()`. It is a separate code path: `snapshots` goes through
    /// `Product.SubscriptionInfo.status(for:)`, while `isAutoRenewing` on an `IapPurchase` comes
    /// from `renewalIntent` reading `transaction.subscriptionStatus` and requiring a `.verified`
    /// `RenewalInfo`. Inverting that `.verified` check passes every other test in this file — this
    /// is the one that catches it.
    ///
    /// Read from `currentEntitlements()` rather than from the value `purchase(_:)` returned,
    /// because at that instant the status has not been published yet (see `eventually`).
    func test_isAutoRenewingOnARestoredSubscriptionComesFromVerifiedRenewalInfo() async throws {
        let product = try await XCTUnwrapFirstProduct(Self.subscriptionId)
        guard case .success(let purchase) = try await backend.purchase(product) else {
            return XCTFail("expected .success")
        }
        await backend.finish(purchase)

        try await eventually("the subscription reports auto-renewal through renewalIntent") {
            try await self.backend.currentEntitlements()
                .first { $0.productId == Self.subscriptionId }?.isAutoRenewing == true
        }
    }

    /// Asking for a product the store does not have must be a `.failed` result, not a throw and
    /// not a crash: `purchase(_:)`'s contract reserves throwing for call-level failures.
    func test_purchasingAProductTheStoreDoesNotHaveFailsWithoutThrowing() async throws {
        let ghost = IapProduct(
            productId: "test.does.not.exist", type: .consumable,
            formattedPrice: "$0.99", priceMicros: 990_000, currencyCode: "USD"
        )
        let result = try await backend.purchase(ghost)
        guard case .failed(_, let message) = result else {
            return XCTFail("expected .failed, got \(result)")
        }
        XCTAssertFalse(message.isEmpty)
    }

    // ═══ currentEntitlements() and finish(_:) ═══

    /// What `restorePurchases()` is built on. A non-consumable stays entitled after purchase, and
    /// the restored record must carry the same JWS — a restore that hands back an empty token
    /// cannot be verified server-side either.
    func test_aPurchasedNonConsumableComesBackFromCurrentEntitlements() async throws {
        let product = try await XCTUnwrapFirstProduct(Self.nonConsumableId)
        guard case .success(let purchase) = try await backend.purchase(product) else {
            return XCTFail("expected .success")
        }
        await backend.finish(purchase)

        try await eventually("the non-consumable appears in currentEntitlements") {
            try await self.backend.currentEntitlements()
                .contains { $0.productId == Self.nonConsumableId }
        }
        let entitlements = try await backend.currentEntitlements()
        let restored = try XCTUnwrap(
            entitlements.first { $0.productId == Self.nonConsumableId }
        )
        XCTAssertFalse(restored.purchaseToken.isEmpty)
        XCTAssertEqual(restored.state, .purchased)
    }

    /// `finish(_:)` is what stops StoreKit redelivering a transaction forever. The observable
    /// consequence is `Transaction.unfinished` going empty — asserted directly rather than
    /// trusting that calling the method did something.
    func test_finishActuallyFinishesTheTransaction() async throws {
        let product = try await XCTUnwrapFirstProduct(Self.consumableId)
        guard case .success(let purchase) = try await backend.purchase(product) else {
            return XCTFail("expected .success")
        }

        try await eventually("the purchase shows up as unfinished") {
            await Self.unfinishedCount() > 0
        }

        await backend.finish(purchase)

        try await eventually("finish() clears the unfinished transaction") {
            await Self.unfinishedCount() == 0
        }
    }

    /// The cache is keyed by order id and `takeCachedTransaction` REMOVES on read, so a second
    /// finish has nothing to act on. It must be a no-op rather than a crash: duplicate finishes
    /// are ordinary — a retry path and the updates stream can both reach the same purchase.
    func test_finishingTwiceIsHarmless() async throws {
        let product = try await XCTUnwrapFirstProduct(Self.consumableId)
        guard case .success(let purchase) = try await backend.purchase(product) else {
            return XCTFail("expected .success")
        }
        await backend.finish(purchase)
        await backend.finish(purchase)
    }

    /// An order id this backend never issued must not match a cached transaction. If the cache
    /// were keyed loosely, finishing an unrelated purchase would finish somebody else's.
    func test_finishingAnUnknownOrderIdDoesNothing() async throws {
        let stranger = IapPurchase(
            productId: Self.consumableId, orderId: "not-an-order-this-backend-issued",
            purchaseToken: "", purchaseTimeMs: 1, state: .purchased, isFinished: false,
            isAutoRenewing: false, quantity: 1, priceMicros: 0, currencyCode: "USD"
        )
        await backend.finish(stranger)
    }

    // ═══ subscriptionSnapshots() ═══

    /// The input to `SubscriptionLifecycle`. A live subscription reports `.subscribed` AND
    /// `willAutoRenew`. Note that `.subscribed` alone says nothing about renewal — a cancelled
    /// subscription reports it too, right up to expiry (see the next test); `willAutoRenew` is
    /// the only field that tells the two apart, and misreading it starts dunning a paying user.
    func test_aLiveSubscriptionIsReportedAsSubscribedAndRenewing() async throws {
        let product = try await XCTUnwrapFirstProduct(Self.subscriptionId)
        guard case .success(let purchase) = try await backend.purchase(product) else {
            return XCTFail("expected .success")
        }
        await backend.finish(purchase)

        try await eventually("the subscription is visible to the store") {
            try await self.backend.subscriptionSnapshots()
                .contains { $0.productId == Self.subscriptionId }
        }
        let snapshots = try await backend.subscriptionSnapshots()
        let vip = try XCTUnwrap(snapshots.first { $0.productId == Self.subscriptionId })
        XCTAssertEqual(vip.renewalState, .subscribed)
        XCTAssertTrue(vip.willAutoRenew)
        XCTAssertGreaterThan(vip.expirationMs, 0, "an auto-renewable states its next renewal")
        XCTAssertNil(vip.expirationReason, "a live subscription has not expired")
    }

    /// Cancelled but not yet lapsed: still `.subscribed`, no longer renewing. This is the case
    /// that proves `renewalState` is not sufficient on its own. Collapsing it into `.expired`
    /// would revoke an entitlement the customer has already paid for; reading it as a plain
    /// renewing subscription would miss every win-back opportunity.
    func test_aCancelledSubscriptionStaysSubscribedButStopsRenewing() async throws {
        let product = try await XCTUnwrapFirstProduct(Self.subscriptionId)
        guard case .success(let purchase) = try await backend.purchase(product) else {
            return XCTFail("expected .success")
        }
        await backend.finish(purchase)

        // Wait for the subscription to exist at all before turning its renewal off, or the
        // session has nothing to act on yet.
        try await eventually("the subscription is visible to the store") {
            try await self.backend.subscriptionSnapshots()
                .contains { $0.productId == Self.subscriptionId }
        }
        try session.disableAutoRenewForTransaction(
            identifier: try XCTUnwrap(UInt(purchase.orderId))
        )

        try await eventually("the subscription stops renewing") {
            try await self.backend.subscriptionSnapshots()
                .first { $0.productId == Self.subscriptionId }?.willAutoRenew == false
        }
        let snapshots = try await backend.subscriptionSnapshots()
        let vip = try XCTUnwrap(snapshots.first { $0.productId == Self.subscriptionId })
        XCTAssertEqual(vip.renewalState, .subscribed, "still entitled until the period ends")
        XCTAssertFalse(vip.willAutoRenew)
    }

    /// A customer who has never subscribed has no subscription history, so there is no group to
    /// enumerate and the result is empty. Emphatically NOT an error: `subscriptionSnapshots()`
    /// throwing here would make every non-subscriber look like an interrupted enumeration.
    func test_aCustomerWithNoSubscriptionHistoryGetsAnEmptyListNotAnError() async throws {
        let snapshots = try await backend.subscriptionSnapshots()
        XCTAssertTrue(snapshots.isEmpty)
    }

    /// A consumable is not an entitlement and not a subscription — it must appear in neither
    /// snapshot, or a gem pack would read as an ongoing membership.
    func test_aConsumableIsNeitherAnEntitlementNorASubscription() async throws {
        let product = try await XCTUnwrapFirstProduct(Self.consumableId)
        guard case .success(let purchase) = try await backend.purchase(product) else {
            return XCTFail("expected .success")
        }
        await backend.finish(purchase)

        let entitlements = try await backend.currentEntitlements()
        XCTAssertFalse(entitlements.contains { $0.productId == Self.consumableId })

        let snapshots = try await backend.subscriptionSnapshots()
        XCTAssertFalse(snapshots.contains { $0.productId == Self.consumableId })
    }

    // ═══ the lapse states ═══
    //
    // `toRenewalState` and `toExpirationReason` were covered only by the mapping tests, which feed
    // them values built by hand. That proves the switch arms, not that a real StoreKit ever
    // produces those values or that `toSubscriptionSnapshot` carries them through. These four
    // drive the store into each state and read the result back through the whole adapter.

    /// A lapsed subscription. `.expired` with a stated reason is what makes churn an OBSERVED
    /// fact rather than an inference from absence — the distinction `subscriptionSnapshots()`
    /// exists for, and the one that decides whether a customer gets dunned.
    func test_anExpiredSubscriptionReportsExpiredWithAReason() async throws {
        let purchase = try await purchaseSubscriptionAndWait()
        try session.disableAutoRenewForTransaction(
            identifier: try XCTUnwrap(UInt(purchase.orderId))
        )
        try session.expireSubscription(productIdentifier: Self.subscriptionId)

        try await eventually("the subscription reports expired") {
            try await self.snapshot()?.renewalState == .expired
        }
        let current = try await snapshot()
        let vip = try XCTUnwrap(current)
        XCTAssertFalse(vip.willAutoRenew)
        XCTAssertEqual(
            vip.expirationReason, .autoRenewDisabled,
            "the customer turned renewal off; that is not a billing failure and must not be dunned"
        )
        XCTAssertGreaterThan(vip.expirationMs, 0, "an expiry that happened has an instant")
    }

    /// A refund. `.revoked` must not read as `.expired`: `signalRank` deliberately puts revoked
    /// ABOVE expired so `isWinBackEligible` withholds discounts from someone who has refunded,
    /// and that ordering is worthless if the state never arrives as `.revoked` in the first place.
    func test_aRefundedSubscriptionReportsRevokedRatherThanExpired() async throws {
        let purchase = try await purchaseSubscriptionAndWait()
        try session.refundTransaction(identifier: try XCTUnwrap(UInt(purchase.orderId)))

        try await eventually("the subscription reports revoked") {
            try await self.snapshot()?.renewalState == .revoked
        }
        let current = try await snapshot()
        let vip = try XCTUnwrap(current)
        XCTAssertNotEqual(vip.renewalState, .expired, "a refund is not a lapse")
    }

    /// Billing failed but the customer is STILL ENTITLED. Apple's grace period is the window in
    /// which revoking access would lock out a paying customer over a card that merely needs
    /// updating. `gracePeriodExpirationMs` is the deadline the host prompts against, so a zero
    /// there is a silently missing prompt.
    func test_aSubscriptionInTheGracePeriodIsStillEntitledAndStatesItsDeadline() async throws {
        // Flags set AFTER the purchase, deliberately. Setting `shouldEnterBillingRetryOnRenewal`
        // first makes the INITIAL purchase enter billing retry too, so the subscription never
        // becomes visible as a normal one and this test fails intermittently on "not visible to
        // the store" rather than on anything it is trying to assert. It also matches the scenario:
        // the customer subscribed successfully, and it is the RENEWAL that fails.
        let purchase = try await purchaseSubscriptionAndWait()
        session.billingGracePeriodIsEnabled = true
        session.shouldEnterBillingRetryOnRenewal = true
        try session.forceRenewalOfSubscription(productIdentifier: Self.subscriptionId)

        try await eventually("the subscription enters the grace period", timeout: 15) {
            try await self.snapshot()?.renewalState == .inGracePeriod
        }
        let current = try await snapshot()
        let vip = try XCTUnwrap(current)
        XCTAssertGreaterThan(
            vip.gracePeriodExpirationMs, 0,
            "the grace deadline is what a host prompts against; 0 means no prompt happens"
        )
        _ = purchase
    }

    /// Billing failed and entitlement is GONE, but the charge is still being retried. Android
    /// cannot see this state at all (design spec §3.3); collapsing it into `.expired` would start
    /// a win-back campaign at someone whose payment may yet succeed.
    func test_aSubscriptionInBillingRetryIsDistinctFromExpired() async throws {
        // After the purchase, for the reason given in the grace-period test above.
        _ = try await purchaseSubscriptionAndWait()
        session.billingGracePeriodIsEnabled = false
        session.shouldEnterBillingRetryOnRenewal = true
        try session.forceRenewalOfSubscription(productIdentifier: Self.subscriptionId)

        try await eventually("the subscription enters billing retry", timeout: 15) {
            try await self.snapshot()?.renewalState == .inBillingRetryPeriod
        }
        let current = try await snapshot()
        let vip = try XCTUnwrap(current)
        XCTAssertNotEqual(vip.renewalState, .expired, "the charge is still being attempted")
    }

    // ═══ helpers ═══

    /// Buys the subscription and waits until the store actually reports it — see `eventually` for
    /// why the wait is required rather than defensive.
    private func purchaseSubscriptionAndWait() async throws -> IapPurchase {
        let product = try await XCTUnwrapFirstProduct(Self.subscriptionId)
        guard case .success(let purchase) = try await backend.purchase(product) else {
            throw XCTSkip("purchase did not succeed, so there is nothing to drive into a state")
        }
        await backend.finish(purchase)
        try await eventually("the subscription is visible to the store") {
            try await self.snapshot() != nil
        }
        return purchase
    }

    private func snapshot() async throws -> SubscriptionSnapshot? {
        try await backend.subscriptionSnapshots().first { $0.productId == Self.subscriptionId }
    }


    /// Finds `Products.storekit` under EITHER host this file is built by.
    ///
    /// `configurationFileNamed:` is not used, because it searches only the bundle root and a
    /// nested-resource layout would defeat it silently. Both locations are checked instead, which
    /// costs nothing and does not care how the file got copied in.
    private static func storekitConfigurationURL() -> URL? {
        let testBundle = Bundle(for: StoreKit2BackendIntegrationTests.self)
        if let direct = testBundle.url(forResource: "Products", withExtension: "storekit") {
            return direct
        }
        let nested = (try? FileManager.default.contentsOfDirectory(
            at: testBundle.bundleURL, includingPropertiesForKeys: nil
        )) ?? []
        for candidate in nested where candidate.pathExtension == "bundle" {
            if let bundle = Bundle(url: candidate),
               let found = bundle.url(forResource: "Products", withExtension: "storekit") {
                return found
            }
        }
        return nil
    }

    /// Polls until `condition` holds, or gives up after ~5s.
    ///
    /// ═══ WHY POLLING IS CORRECT HERE AND NOT A PAPERED-OVER RACE ═══
    ///
    /// StoreKit's global sequences are EVENTUALLY consistent with a purchase that has just
    /// returned. Measured in this environment, immediately after a successful `purchase(_:)`:
    /// `Product.SubscriptionInfo.status(for:)` is still empty, and `transaction.subscriptionStatus`
    /// is still nil — so `subscriptionSnapshots()` returns `[]` and `isAutoRenewing` reads false,
    /// a moment before both report the truth.
    ///
    /// A test that reads once and asserts is therefore asserting on a value StoreKit never
    /// promised to have published yet. Polling is what makes the assertion match the actual
    /// contract; it does not weaken it, because the deadline still fails the test if the value
    /// never arrives. What it must NOT be used for is hiding a wrong value — every use below
    /// waits for a SPECIFIC expected state, not for "any state that passes".
    private static func unfinishedCount() async -> Int {
        var count = 0
        for await _ in Transaction.unfinished { count += 1 }
        return count
    }

    private func eventually(
        _ description: String,
        timeout: TimeInterval = 5,
        file: StaticString = #filePath,
        line: UInt = #line,
        _ condition: () async throws -> Bool
    ) async throws {
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if try await condition() { return }
            try await Task.sleep(nanoseconds: 100_000_000)
        }
        XCTFail("timed out after \(timeout)s waiting for: \(description)", file: file, line: line)
    }

    private func XCTUnwrapFirstProduct(
        _ id: String, file: StaticString = #filePath, line: UInt = #line
    ) async throws -> IapProduct {
        let products = try await backend.products(for: [id])
        return try XCTUnwrap(products.first, "no product \(id)", file: file, line: line)
    }
}
#endif
