#if os(iOS)
import XCTest
import StoreKit
import StoreKitTest
@testable import MonetizationBrainStoreKit
import MonetizationBrainCore

/// The seam between `StoreKit2Backend` and `SubscriptionLifecycle`, tested across rather than
/// either side of.
///
/// ═══ WHY THIS FILE EXISTS ═══
///
/// Both halves were already well covered and neither covered the join. `SubscriptionLifecycle`
/// has 790 lines of tests, and every one of them feeds it a `SubscriptionSnapshot` built by hand;
/// `StoreKit2BackendIntegrationTests` drives a real store and stops at the snapshot. So the
/// classification was proven correct for snapshots a TEST invents, and the adapter was proven to
/// produce snapshots nobody then classified.
///
/// That gap is exactly where a port goes wrong, and it is expensive here: the output is whether a
/// paying customer keeps their entitlement, gets dunned, or is offered a win-back discount. A
/// field mismatch between the two halves — a `.subscribed` + `willAutoRenew == false` that the
/// classifier reads as churn, say — passes both suites and bills the wrong person.
///
/// These tests take one path: drive a REAL store into a state, read it through
/// `subscriptionSnapshots()`, hand THAT to `SubscriptionLifecycle.observe`, and assert on the
/// decision that comes out.
@available(iOS 15.4, *)
final class SubscriptionLifecycleEndToEndTests: XCTestCase {
    private var session: SKTestSession!
    private var backend: StoreKit2Backend!
    private var lifecycle: SubscriptionLifecycle!
    private var events: RecordingEventSink!

    private static let subscriptionId = "test.vip.monthly"

    override func setUp() async throws {
        try await super.setUp()
        let url = try XCTUnwrap(
            Self.storekitConfigurationURL(), "Products.storekit missing from the test bundle"
        )
        session = try SKTestSession(contentsOf: url)
        // Order matters — see the note in StoreKit2BackendIntegrationTests.setUp.
        session.resetToDefaultState()
        try session.clearTransactions()
        session.disableDialogs = true
        session.askToBuyEnabled = false

        backend = StoreKit2Backend()
        events = RecordingEventSink()
        // A FRESH lifecycle per test, not `.shared`: its state is persisted, and a shared instance
        // would carry one test's transitions into the next. The in-memory store keeps this off
        // UserDefaults entirely.
        lifecycle = SubscriptionLifecycle()
        lifecycle.configure(
            .init(eventSink: events, playerId: "e2e-player", store: InMemoryStore())
        )

        let served = try await backend.products(for: [Self.subscriptionId])
        if served.isEmpty {
            throw XCTSkip(
                "StoreKit served no products; run this through "
                + "Integration/MonetizationBrainIntegration.xcodeproj."
            )
        }
    }

    override func tearDown() async throws {
        session = nil
        backend = nil
        lifecycle = nil
        events = nil
        try await super.tearDown()
    }

    /// The happy path, and the baseline the rest are measured against: a live subscription
    /// classifies `.active` and is entitled.
    func test_aLiveSubscriptionClassifiesActiveAndEntitled() async throws {
        _ = try await subscribeAndObserve()

        XCTAssertEqual(lifecycle.stateFor(productId: Self.subscriptionId), .active)
        XCTAssertTrue(lifecycle.stateFor(productId: Self.subscriptionId).isEntitled)
    }

    /// ═══ THE ONE THAT MATTERS MOST ═══
    ///
    /// A cancelled subscription is STILL ENTITLED until the period ends. `SubscriptionState`'s own
    /// doc calls this the single most common mistake when porting off Google Play, where
    /// "cancelled" reads as churn. It is only provable end to end: the store reports `.subscribed`
    /// with `willAutoRenew == false`, and everything depends on the classifier reading BOTH fields
    /// rather than the state alone. Revoking here would cut off a customer who has already paid
    /// for the remaining days.
    func test_aCancelledSubscriptionIsStillEntitledThroughTheWholeChain() async throws {
        let purchase = try await subscribeAndObserve()
        try session.disableAutoRenewForTransaction(
            identifier: try XCTUnwrap(UInt(purchase.orderId))
        )
        try await observeUntil(.cancelled)

        XCTAssertTrue(
            lifecycle.stateFor(productId: Self.subscriptionId).isEntitled,
            "a cancelled-but-unexpired subscription must keep its entitlement"
        )
    }

    /// A lapse, end to end, and the transition that a backend campaign keys on. `observe` reports
    /// `.active -> .expired`; asserting the transition rather than only the final state is the
    /// point, because a state that was never transitioned INTO fires no campaign.
    func test_aLapseProducesAnExpiredTransitionAndDropsEntitlement() async throws {
        let purchase = try await subscribeAndObserve()
        try session.disableAutoRenewForTransaction(
            identifier: try XCTUnwrap(UInt(purchase.orderId))
        )
        try session.expireSubscription(productIdentifier: Self.subscriptionId)

        let transitions = try await observeUntil(.expired)
        XCTAssertTrue(
            transitions.contains { $0.productId == Self.subscriptionId && $0.to == .expired },
            "the lapse must arrive as a transition, not just as a state"
        )
        XCTAssertFalse(lifecycle.stateFor(productId: Self.subscriptionId).isEntitled)
    }

    /// Win-back eligibility after a genuine lapse. This is the decision the whole chain exists to
    /// produce, and the first point at which the classification turns into an offer.
    func test_aLapsedCustomerBecomesWinBackEligible() async throws {
        let purchase = try await subscribeAndObserve()
        try session.disableAutoRenewForTransaction(
            identifier: try XCTUnwrap(UInt(purchase.orderId))
        )
        try session.expireSubscription(productIdentifier: Self.subscriptionId)
        try await observeUntil(.expired)

        XCTAssertTrue(lifecycle.isWinBackEligible(productId: Self.subscriptionId))
        XCTAssertTrue(lifecycle.winBackEligibleProductIds().contains(Self.subscriptionId))
    }

    /// ═══ THE REFUND RULE, PROVEN ACROSS THE SEAM ═══
    ///
    /// A refunded customer must NOT be offered a win-back discount. `signalRank` puts `.revoked`
    /// above `.expired` for exactly this reason, but that ordering lives in the adapter while the
    /// withholding lives in the lifecycle — neither suite alone shows the rule survives the trip.
    func test_aRefundedCustomerIsNotOfferedAWinBack() async throws {
        let purchase = try await subscribeAndObserve()
        try session.refundTransaction(identifier: try XCTUnwrap(UInt(purchase.orderId)))
        try await observeUntil(.revoked)

        XCTAssertFalse(
            lifecycle.isWinBackEligible(productId: Self.subscriptionId),
            "a refund is not a lapse; discounting someone who refunded is the wrong offer"
        )
        XCTAssertFalse(lifecycle.winBackEligibleProductIds().contains(Self.subscriptionId))
    }

    /// Billing failed, entitlement kept. Dunning must be due — that notice is the only thing that
    /// gets the card updated before the grace period runs out — while the entitlement stays, since
    /// cutting service off mid-grace punishes a customer for an expired card.
    func test_aGracePeriodKeepsEntitlementAndStartsDunning() async throws {
        let purchase = try await subscribeAndObserve()
        session.billingGracePeriodIsEnabled = true
        session.shouldEnterBillingRetryOnRenewal = true
        try session.forceRenewalOfSubscription(productIdentifier: Self.subscriptionId)
        try await observeUntil(.inGracePeriod, timeout: 20)
        _ = purchase

        XCTAssertTrue(
            lifecycle.stateFor(productId: Self.subscriptionId).isEntitled,
            "grace means the charge failed, not that the customer lost what they paid for"
        )
        lifecycle.noteForegroundOpen(productId: Self.subscriptionId)
        XCTAssertTrue(
            lifecycle.dunningNoticeDue(productId: Self.subscriptionId),
            "the first foreground open of a failed-billing episode is a dunning notice"
        )
        XCTAssertNotNil(lifecycle.managementUrl(), "a dunning notice needs somewhere to send them")
    }

    /// Billing retry: entitlement gone, but still recoverable, so it must NOT be classified as a
    /// lapse. Android cannot see this state at all — collapsing it into `.expired` here would
    /// start a win-back campaign at someone whose payment may still go through.
    func test_billingRetryLosesEntitlementWithoutBeingTreatedAsALapse() async throws {
        _ = try await subscribeAndObserve()
        session.billingGracePeriodIsEnabled = false
        session.shouldEnterBillingRetryOnRenewal = true
        try session.forceRenewalOfSubscription(productIdentifier: Self.subscriptionId)
        try await observeUntil(.inBillingRetry, timeout: 20)

        let state = lifecycle.stateFor(productId: Self.subscriptionId)
        XCTAssertFalse(state.isEntitled)
        XCTAssertNotEqual(state, .expired, "the charge is still being attempted")
        XCTAssertFalse(
            lifecycle.isWinBackEligible(productId: Self.subscriptionId),
            "win-back is for customers who are gone, not for a retry in progress"
        )
    }

    /// `observe` must be idempotent across repeated polls. The host calls it on every foreground,
    /// so a second identical snapshot producing a second transition would re-fire whatever the
    /// backend hangs off that transition — duplicate dunning mail, duplicate campaign entry.
    func test_observingTheSameStateTwiceProducesNoSecondTransition() async throws {
        _ = try await subscribeAndObserve()

        let again = await lifecycle.observe(try await backend.subscriptionSnapshots())
        XCTAssertTrue(
            again.filter { $0.productId == Self.subscriptionId }.isEmpty,
            "an unchanged state is not a transition"
        )
    }

    // ═══ helpers ═══

    @discardableResult
    private func subscribeAndObserve() async throws -> IapPurchase {
        let products = try await backend.products(for: [Self.subscriptionId])
        let product = try XCTUnwrap(products.first)
        guard case .success(let purchase) = try await backend.purchase(product) else {
            throw XCTSkip("purchase did not succeed; nothing to classify")
        }
        await backend.finish(purchase)
        try await observeUntil(.active)
        return purchase
    }

    /// Polls the real store and feeds each reading through `observe` until the lifecycle lands on
    /// `expected`. See `StoreKit2BackendIntegrationTests.eventually` for why the store has to be
    /// polled at all; this variant also keeps `observe` in the loop, which is the point of the file.
    @discardableResult
    private func observeUntil(
        _ expected: SubscriptionState,
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async throws -> [SubscriptionTransition] {
        let deadline = Date().addingTimeInterval(timeout)
        var lastTransitions: [SubscriptionTransition] = []
        while Date() < deadline {
            let snapshots = try await backend.subscriptionSnapshots()
            let transitions = await lifecycle.observe(snapshots)
            if !transitions.isEmpty { lastTransitions = transitions }
            if lifecycle.stateFor(productId: Self.subscriptionId) == expected {
                return lastTransitions
            }
            try await Task.sleep(nanoseconds: 150_000_000)
        }
        XCTFail(
            "timed out after \(timeout)s waiting for \(expected.rawValue); last seen "
            + "\(lifecycle.stateFor(productId: Self.subscriptionId).rawValue)",
            file: file, line: line
        )
        return lastTransitions
    }

    private static func storekitConfigurationURL() -> URL? {
        let testBundle = Bundle(for: SubscriptionLifecycleEndToEndTests.self)
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
}

/// Captures what the lifecycle emitted. Lock-guarded because `send` is called from whatever
/// executor `observe` is running on, and this package does not use `nonisolated(unsafe)`.
private final class RecordingEventSink: EventSink, @unchecked Sendable {
    private let lock = NSLock()
    private var _events: [SdkEvent] = []

    var events: [SdkEvent] {
        lock.lock(); defer { lock.unlock() }
        return _events
    }

    func send(_ event: SdkEvent) async {
        lock.lock()
        _events.append(event)
        lock.unlock()
    }
}

/// Keeps lifecycle state out of `UserDefaults`, so one test cannot inherit another's history.
private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
    private let lock = NSLock()
    private var storage: [String: String] = [:]

    func string(forKey key: String) -> String? {
        lock.lock(); defer { lock.unlock() }
        return storage[key]
    }

    func setString(_ value: String, forKey key: String) {
        lock.lock(); storage[key] = value; lock.unlock()
    }

    func removeObject(forKey key: String) {
        lock.lock(); storage.removeValue(forKey: key); lock.unlock()
    }
}
#endif
