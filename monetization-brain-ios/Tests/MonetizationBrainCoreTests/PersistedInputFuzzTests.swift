import XCTest
@testable import MonetizationBrainCore

/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// EVERY STORE READ IS AN UNTRUSTED INPUT
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// `UserDefaults` is a plist the app — and anything else running as the app — can write. Each
/// subsystem here parses a record out of it on construction, and each of those parsers is a
/// boundary in the sense the project's own rules mean it: validate, never trust.
///
/// This file exists because that class of input has already produced two real defects in this
/// port, neither of which announced itself: a persisted "inf" belief that made the NEXT DECISION
/// crash, and an infinite bandit record that silently collapsed selection onto one arm while
/// still logging a confident propensity. Both looked locally reasonable at every line.
///
/// So each test poisons one record with the shapes that actually break parsers — infinity, NaN,
/// negative and overflowing integers, embedded separators, empty fields, a record truncated
/// mid-write — and asserts the thing that matters commercially: **the subsystem behaves as if
/// there were no record at all.** Never a crash, and never a grant, an entitlement or a skipped
/// dedup that the player did not earn.
final class PersistedInputFuzzTests: XCTestCase {

    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String]

        init(_ values: [String: String] = [:]) { self.values = values }

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

    /// The shapes worth throwing at a parser, and why each one is here rather than being a random
    /// string: every one of them is a value some parser in this package has accepted or would
    /// accept — `Double("inf")` and `Double("nan")` both parse, `-1` passes an `Int` parse, an
    /// overflowing literal passes a `Double` one, an embedded separator re-splits a record into
    /// fields that were never meant to be fields, and a truncated tail is what a kill mid-write
    /// leaves behind.
    private static let poison = [
        "inf", "-inf", "nan", "1e999", "-1", "0",
        "9223372036854775808",           // Int64.max + 1
        "-9223372036854775809",
        "|", "||", "a|b|c|d|e", "=", ";;;", ",,,",
        "", " ", "\n", "\u{0}", "🙂",
        String(repeating: "x", count: 10_000),
        "p1|", "|1700000000000", "p1|nan", "p1|inf", "p1|-5"
    ]

    private static func poisonedValues(for key: String) -> [[String: String]] {
        poison.map { [key: $0] }
    }

    // MARK: - Recurring grants: the clock record decides when currency is handed out

    /// A poisoned last-claim timestamp must never make a grant due EARLIER than the period. The
    /// worst shape is a negative or absurd timestamp: if it were accepted, `now - last` is a huge
    /// elapsed time and every launch pays out again.
    func test_aPoisonedLastClaimNeverMakesAGrantDueEarly() async {
        let now: Int64 = 1_700_000_000_000
        for values in Self.poisonedValues(for: RecurringGrants.lastClaimKey) {
            let store = InMemoryStore(values)
            let entitlements = Entitlements(store: InMemoryStore())
            entitlements.grant("vip_pass")
            let grants = RecurringGrants()
            grants.configure(.init(
                eventSink: FakeEventSink(),
                playerId: "p1",
                store: store,
                clock: FakeClock(now),
                entitlements: entitlements,
                subscriptionLifecycle: SubscriptionLifecycle()
            ))
            _ = grants.apply(#"""
            {"version": "rg_v1", "enabled": true, "grants": [
              {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500}]}
            """#)

            // A first claim is always due (that is the documented "new owner gets one now"
            // behaviour); the SECOND one inside the same period must not be.
            let first = await grants.claim(productId: "vip_pass", rewardType: "gem")
            let second = await grants.claim(productId: "vip_pass", rewardType: "gem")

            XCTAssertTrue(first.claimed, "record \(values) blocked a legitimate first claim")
            XCTAssertFalse(second.claimed, "record \(values) allowed a second claim inside the period")
        }
    }

    // MARK: - Battle pass: the progress record decides which rewards are owed

    /// A poisoned progress record must not hand out a tier the player has not earned, and must not
    /// let a claim happen twice. Resetting progress is an acceptable outcome; granting is not.
    func test_aPoisonedProgressRecordNeverGrantsAnUnearnedTier() async {
        for values in Self.poisonedValues(for: BattlePass.stateKey) {
            let store = InMemoryStore(values)
            let pass = BattlePass()
            pass.configure(.init(
                eventSink: FakeEventSink(),
                playerId: "p1",
                store: store,
                clock: FakeClock(1_700_000_100_000),
                entitlements: Entitlements(store: InMemoryStore())
            ))
            _ = pass.apply(#"""
            {"version": "bp_v1", "season_id": "s3",
             "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
             "premium_product_id": "bp_premium", "xp_cap": 1000,
             "tiers": [{"tier": 1, "xp_required": 0,
                        "rewards_free": [{"id": "r1", "type": "gem", "amount": 10}],
                        "rewards_premium": []},
                       {"tier": 5, "xp_required": 900,
                        "rewards_free": [{"id": "r5", "type": "gem", "amount": 99}],
                        "rewards_premium": []}]}
            """#)

            // Tier 5 needs 900 XP. No poisoned record may be read as having earned it.
            let highTier = await pass.claim(tier: 5, track: .free)
            XCTAssertFalse(highTier.claimed, "record \(values) granted an unearned tier 5")

            // Tier 1 needs nothing, so it is claimable — but exactly once.
            let firstClaim = await pass.claim(tier: 1, track: .free)
            let secondClaim = await pass.claim(tier: 1, track: .free)
            XCTAssertTrue(firstClaim.claimed, "record \(values) blocked an earned tier 1")
            XCTAssertFalse(secondClaim.claimed, "record \(values) allowed tier 1 to be claimed twice")
        }
    }

    // MARK: - Offerwall: the seen-transaction record is the double-reward guard

    /// The dedup record is the only thing standing between a replayed callback and a second
    /// reward. A poisoned record may lose history — that is recoverable — but must never make the
    /// manager accept the SAME transaction twice in one run.
    func test_aPoisonedSeenTransactionRecordStillBlocksAReplayedReward() async {
        for values in Self.poisonedValues(for: OfferwallManager.seenTransactionsKey) {
            let manager = OfferwallManager()
            manager.configure(.init(
                eventSink: FakeEventSink(),
                playerId: "p1",
                store: InMemoryStore(values)
            ))
            _ = manager.apply(#"{"version": "ow_v1", "enabled": true}"#)

            let first = await manager.reportCompletion(
                providerId: "prov", transactionId: "tx-1", rewardType: "gem", rewardAmount: 10
            )
            let replay = await manager.reportCompletion(
                providerId: "prov", transactionId: "tx-1", rewardType: "gem", rewardAmount: 10
            )

            XCTAssertTrue(first.accepted, "record \(values) blocked a legitimate first reward")
            XCTAssertFalse(replay.accepted, "record \(values) paid the same transaction twice")
        }
    }

    // MARK: - Event sequence numbering

    /// A poisoned counter may restart the numbering — the backend dedupes on `dedup_key` and the
    /// doc says so — but it must never hand out a non-positive or repeating number within a run.
    func test_aPoisonedSequenceCounterStillIssuesIncreasingPositiveNumbers() {
        for values in Self.poisonedValues(for: EventSequenceCounter.storageKey) {
            let counter = EventSequenceCounter(store: InMemoryStore(values))

            let first = counter.next()
            let second = counter.next()

            XCTAssertGreaterThan(first, 0, "record \(values) produced a non-positive sequence id")
            XCTAssertEqual(second, first + 1, "record \(values) broke monotonic numbering")
        }
    }

    // MARK: - Entitlements

    /// Entitlement is client-side state the type's own doc calls out as not fraud-resistant, so
    /// the bar here is narrower and still worth pinning: a poisoned record must not turn into a
    /// remove-ads entitlement for a product the app never declared as one.
    func test_aPoisonedEntitlementRecordGrantsNoUndeclaredRemoveAds() {
        for values in Self.poisonedValues(for: "iap_active_products") {
            let entitlements = Entitlements(store: InMemoryStore(values))
            entitlements.removeAdsProductIds = ["remove_ads_pack"]

            XCTAssertFalse(
                entitlements.hasRemoveAds(),
                "record \(values) granted remove-ads without the declared product"
            )
        }
    }

    // MARK: - Belief and bandit, the two that already bit

    func test_aPoisonedBeliefOrBanditRecordLeavesTheBrainUsable() {
        for value in Self.poison {
            let belief = BeliefState.deserialize(playerId: "p1", raw: value)
            for type in LatentPlayerType.allCases {
                let weight = belief.posterior[type] ?? .nan
                XCTAssertFalse(weight.isNaN, "belief record '\(value.prefix(20))' produced a NaN posterior")
            }

            let bandit = BanditPolicy()
            bandit.restore(value)
            var rng = SystemRandomNumberGenerator()
            let result = bandit.selectArm(
                segmentWeights: ["seg": 1.0],
                candidateArmIds: ["a", "b"],
                randomSource: &rng
            )
            XCTAssertFalse(result.propensity.isNaN, "bandit record '\(value.prefix(20))' produced a NaN propensity")
        }
    }
}
