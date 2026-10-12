import XCTest
@testable import MonetizationBrainCore

/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// THE ONE PLACE A RACE COSTS REAL MONEY
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// Every type here hands out something a player can spend: a battle-pass tier, a recurring grant,
/// an offerwall reward. Each guards against paying twice with a check-then-record sequence, and a
/// check-then-record is exactly the shape that breaks when two callers interleave. This port has
/// already shipped one such bug — the Phase 9 double-grant, where two writes raced and the second
/// overwrote the first's record of the claim.
///
/// The tests this package already has for those paths are sequential: claim, then claim again.
/// That proves the RECORD works, not the LOCK. These run the same claim from many tasks at once
/// and assert an exact count, which is the only assertion that can tell "guarded" apart from
/// "usually fast enough".
///
/// A UI cannot normally double-tap 64 times, but the situations these stand in for are ordinary:
/// a retry on a flaky callback, a lifecycle hook that fires twice, an offerwall provider that
/// re-delivers a completion, a player who backgrounds and foregrounds mid-claim.
final class ConcurrentClaimSafetyTests: XCTestCase {

    private static let attempts = 64

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
            lock.lock(); values.removeValue(forKey: key); lock.unlock()
        }
    }

    private final class FakeEventSink: EventSink, @unchecked Sendable {
        func send(_ event: SdkEvent) async {}
    }

    private final class FakeClock: BrainClock, @unchecked Sendable {
        private let value: Int64
        init(_ value: Int64) { self.value = value }
        func nowMs() -> Int64 { value }
    }

    /// ═══ HOW TO PROVE A LOCK IS DOING ANYTHING ═══
    ///
    /// The first version of these tests just ran 64 claims at once and asserted that one
    /// succeeded. That passes with the lock REMOVED ENTIRELY — verified, not assumed: a claim is
    /// a few microseconds of synchronous work, so 64 tasks almost never collide at exactly the
    /// check-then-insert boundary. An assertion that cannot fail is not coverage.
    ///
    /// This measures the thing directly instead of inferring it. Both `BattlePass.claim` and
    /// `RecurringGrants.claim` call the injected clock INSIDE their critical section, so counting
    /// how many callers are inside `nowMs()` at the same moment counts how many are inside the
    /// lock. Under mutual exclusion that number is 1, by definition and regardless of timing.
    ///
    /// The first caller waits briefly for company so a second one has a real chance to arrive —
    /// only the first, so the whole test pays the delay once rather than 64 times. With the lock
    /// in place nobody can join it, the wait times out, and `maxConcurrent` stays 1; without the
    /// lock, callers pile in and it does not.
    private final class RendezvousClock: BrainClock, @unchecked Sendable {
        private let condition = NSCondition()
        private let value: Int64
        private let waitSeconds: TimeInterval
        private var inside = 0
        private var hasWaited = false
        private var peak = 0

        init(_ value: Int64, waitSeconds: TimeInterval = 0.25) {
            self.value = value
            self.waitSeconds = waitSeconds
        }

        /// The most callers ever inside `nowMs()` — and therefore inside the caller's lock — at
        /// one moment.
        var maxConcurrent: Int {
            condition.lock()
            defer { condition.unlock() }
            return peak
        }

        func nowMs() -> Int64 {
            condition.lock()
            inside += 1
            peak = max(peak, inside)
            if !hasWaited {
                hasWaited = true
                condition.broadcast()
                _ = condition.wait(until: Date().addingTimeInterval(waitSeconds))
            } else {
                condition.broadcast()
            }
            inside -= 1
            condition.unlock()
            return value
        }
    }

    /// Runs `body` from `attempts` tasks at once and counts how many reported success.
    private func concurrentSuccesses(
        _ body: @escaping @Sendable (Int) async -> Bool
    ) async -> Int {
        await withTaskGroup(of: Bool.self) { group in
            for index in 0..<Self.attempts {
                group.addTask { await body(index) }
            }
            var successes = 0
            for await didSucceed in group where didSucceed {
                successes += 1
            }
            return successes
        }
    }

    // MARK: - Battle pass

    func test_onlyOneOfManyConcurrentClaimsOfTheSameTierSucceeds() async {
        let clock = RendezvousClock(1_700_000_100_000)
        let pass = BattlePass()
        pass.configure(.init(
            eventSink: FakeEventSink(),
            playerId: "p1",
            store: InMemoryStore(),
            clock: clock,
            entitlements: Entitlements(store: InMemoryStore())
        ))
        _ = pass.apply(#"""
        {"version": "bp_v1", "season_id": "s3",
         "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "premium_product_id": "bp_premium", "xp_cap": 1000,
         "tiers": [{"tier": 1, "xp_required": 0,
                    "rewards_free": [{"id": "r1", "type": "gem", "amount": 10}],
                    "rewards_premium": []}]}
        """#)

        let granted = await concurrentSuccesses { _ in
            await pass.claim(tier: 1, track: .free).claimed
        }

        XCTAssertEqual(granted, 1, "tier 1 was granted \(granted) times")
        XCTAssertEqual(
            clock.maxConcurrent, 1,
            "\(clock.maxConcurrent) claims were inside the critical section at once — the lock is not holding"
        )
    }

    // MARK: - Recurring grants

    /// ═══ THE SAME MEASUREMENT, AND IT DOES NOT BITE HERE ═══
    ///
    /// The `maxConcurrent` check below is the same one that catches a missing lock in the battle
    /// pass test above. Here it does not: removing `RecurringGrants`' lock entirely leaves this
    /// test passing, with 64 callers and a rendezvous window stretched to 1.5 s. Something ahead
    /// of the clock call — `owns()` goes through `Entitlements` and `SubscriptionLifecycle`, each
    /// taking its own lock — appears to serialise the arrivals, and I did not chase further.
    ///
    /// So: the exact-count assertion is a real invariant and would catch a dedup that stops
    /// working for non-timing reasons, and `maxConcurrent` is a true statement being checked. But
    /// on this machine it is not evidence about this type's lock, and it is labelled that way
    /// rather than left to look like the test above.
    func test_onlyOneOfManyConcurrentGrantClaimsSucceedsInOnePeriod() async {
        let clock = RendezvousClock(1_700_000_000_000)
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant("vip_pass")
        let grants = RecurringGrants()
        grants.configure(.init(
            eventSink: FakeEventSink(),
            playerId: "p1",
            store: InMemoryStore(),
            clock: clock,
            entitlements: entitlements,
            subscriptionLifecycle: SubscriptionLifecycle()
        ))
        _ = grants.apply(#"""
        {"version": "rg_v1", "enabled": true, "grants": [
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500}]}
        """#)

        let granted = await concurrentSuccesses { _ in
            await grants.claim(productId: "vip_pass", rewardType: "gem").claimed
        }

        XCTAssertEqual(granted, 1, "the 24-hour grant paid out \(granted) times at one instant")
        XCTAssertEqual(
            clock.maxConcurrent, 1,
            "\(clock.maxConcurrent) claims were inside the critical section at once"
        )
    }

    // MARK: - Offerwall

    /// The offerwall case is the one with an outside caller: a provider SDK re-delivering a
    /// completion is the normal way this gets called twice, and it does not wait politely for the
    /// first call to finish.
    ///
    /// **Weaker than the two above, and here is why.** `reportCompletion` has no injected
    /// collaborator inside its critical section — no clock, no store read — so there is nothing to
    /// count callers with, and this test passes even with the lock removed. It pins the invariant
    /// and would catch a dedup that stops working for non-timing reasons (the in-memory list not
    /// being consulted, a key built differently on the second call), but it is not evidence about
    /// the lock. Said out loud so the next reader does not take it for more than it is.
    func test_onlyOneOfManyConcurrentCompletionsOfTheSameTransactionIsAccepted() async {
        let manager = OfferwallManager()
        manager.configure(.init(
            eventSink: FakeEventSink(),
            playerId: "p1",
            store: InMemoryStore()
        ))
        _ = manager.apply(#"{"version": "ow_v1", "enabled": true}"#)

        let accepted = await concurrentSuccesses { _ in
            await manager.reportCompletion(
                providerId: "prov", transactionId: "tx-1", rewardType: "gem", rewardAmount: 10
            ).accepted
        }

        XCTAssertEqual(accepted, 1, "transaction tx-1 paid out \(accepted) times")
    }

    /// Different transactions from the same provider must ALL pay — a dedup that over-blocks is
    /// its own bug, and a test that only proves "at most one" would pass with the offerwall
    /// switched off entirely.
    func test_concurrentCompletionsOfDifferentTransactionsAllPay() async {
        let manager = OfferwallManager()
        manager.configure(.init(
            eventSink: FakeEventSink(),
            playerId: "p1",
            store: InMemoryStore()
        ))
        _ = manager.apply(#"{"version": "ow_v1", "enabled": true}"#)

        let accepted = await concurrentSuccesses { index in
            await manager.reportCompletion(
                providerId: "prov", transactionId: "tx-\(index)", rewardType: "gem", rewardAmount: 10
            ).accepted
        }

        XCTAssertEqual(accepted, Self.attempts, "only \(accepted) of \(Self.attempts) distinct rewards paid")
    }

    // MARK: - Entitlements

    /// `hasRemoveAds` reads two fields — the active set and the remove-ads set. Reading them in
    /// two separate lock acquisitions is the TOCTOU shape this package fixed elsewhere; here the
    /// assertion is that a concurrent writer can never make the answer flicker to `false` for a
    /// player who owns the product throughout.
    func test_hasRemoveAdsNeverFlickersWhileUnrelatedProductsChurn() async {
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.removeAdsProductIds = ["remove_ads_pack"]
        entitlements.grant("remove_ads_pack")

        let stayedTrue = await concurrentSuccesses { index in
            if index.isMultiple(of: 2) {
                entitlements.grant("noise_\(index)")
            } else {
                entitlements.revoke("noise_\(index - 1)")
            }
            return entitlements.hasRemoveAds()
        }

        XCTAssertEqual(stayedTrue, Self.attempts, "entitlement flickered off \(Self.attempts - stayedTrue) times")
    }
}
