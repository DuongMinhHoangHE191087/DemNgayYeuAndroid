import XCTest
@testable import MonetizationBrainCore


final class RecurringGrantsTests: XCTestCase {
    /// In-memory `BrainKeyValueStore` — redeclared per-file, matching this project's established
    /// convention of NOT sharing test fakes across files (see `SubscriptionLifecycleTests`' own
    /// copy). This one additionally counts writes, because `dueGrants()`' purity claim (§7) is
    /// only provable by showing it never touches the store.
    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        private(set) var writeCount = 0
        func string(forKey key: String) -> String? {
            lock.lock(); defer { lock.unlock() }
            return values[key]
        }
        func setString(_ value: String, forKey key: String) {
            lock.lock(); values[key] = value; writeCount += 1; lock.unlock()
        }
        func removeObject(forKey key: String) {
            lock.lock(); _ = values.removeValue(forKey: key); lock.unlock()
        }
        func peek(_ key: String) -> String? { string(forKey: key) }
        func writes() -> Int { lock.lock(); defer { lock.unlock() }; return writeCount }
    }

    /// Same shape as `SubscriptionLifecycleTests`' own file-local fake.
    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

    /// The test seam design spec §3.3 introduces: the clock is injected, so NO public API needs a
    /// `ts:` parameter to be testable.
    private final class FakeClock: BrainClock, @unchecked Sendable {
        private let lock = NSLock()
        private var current: Int64
        init(_ start: Int64) { self.current = start }
        func nowMs() -> Int64 { lock.lock(); defer { lock.unlock() }; return current }
        func advance(ms: Int64) { lock.lock(); current += ms; lock.unlock() }
        func advance(hours: Int64) { advance(ms: hours * 3_600_000) }
        func rewind(hours: Int64) { advance(ms: -hours * 3_600_000) }
    }

    /// Engineered contention for the STORE, the way `StallingClock` engineers it for the clock.
    /// The lost-write this exists to catch lives entirely between two `setString` calls, so the
    /// delay has to be there: `armDelay()` makes the NEXT write to arrive sleep while a later one
    /// runs past it.
    private final class DelayingStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        private var delayNext = false
        private var writeOrder: [String] = []
        private let delay: TimeInterval
        init(delay: TimeInterval = 0.4) { self.delay = delay }
        func armDelay() { lock.lock(); delayNext = true; lock.unlock() }
        func string(forKey key: String) -> String? {
            lock.lock(); defer { lock.unlock() }
            return values[key]
        }
        func setString(_ value: String, forKey key: String) {
            lock.lock()
            let shouldDelay = delayNext
            delayNext = false
            lock.unlock()
            if shouldDelay { Thread.sleep(forTimeInterval: delay) }
            lock.lock(); values[key] = value; writeOrder.append(value); lock.unlock()
        }
        func removeObject(forKey key: String) {
            lock.lock(); _ = values.removeValue(forKey: key); lock.unlock()
        }
        func peek(_ key: String) -> String? { string(forKey: key) }
        func landed() -> [String] { lock.lock(); defer { lock.unlock() }; return writeOrder }
    }

    private let productId = "vip_pass"
    private let baseMs: Int64 = 1_700_000_000_000

    private let config = """
    {"version": "rg_v1", "enabled": true, "grants": [
      {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500},
      {"product_id": "vip_pass", "period_hours": 168, "reward_type": "ticket", "reward_amount": 1}
    ]}
    """

    // MARK: - Helpers

    private func makeGrants(
        store: BrainKeyValueStore = InMemoryStore(),
        clock: BrainClock,
        entitlements: Entitlements = Entitlements(store: InMemoryStore()),
        lifecycle: SubscriptionLifecycle = SubscriptionLifecycle(),
        eventSink: EventSink = FakeEventSink()
    ) -> RecurringGrants {
        let grants = RecurringGrants()
        grants.configure(.init(
            eventSink: eventSink,
            playerId: "p1",
            store: store,
            clock: clock,
            entitlements: entitlements,
            subscriptionLifecycle: lifecycle
        ))
        return grants
    }

    /// An owner by `Entitlements` alone — the cheapest ownership source for tests whose subject is
    /// timing or parsing rather than ownership itself.
    private func makeOwnedGrants(
        store: BrainKeyValueStore = InMemoryStore(),
        clock: BrainClock,
        eventSink: EventSink = FakeEventSink(),
        json: String? = nil
    ) -> RecurringGrants {
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant(productId)
        let grants = makeGrants(store: store, clock: clock, entitlements: entitlements, eventSink: eventSink)
        _ = grants.apply(json ?? config)
        return grants
    }

    /// `claim` is `async`, and an `async` call cannot live inside an `XCTAssert*` autoclosure —
    /// so every claim assertion goes through this, which also surfaces the result's `report` in
    /// the failure message.
    private func expectClaim(
        _ grants: RecurringGrants,
        productId: String? = nil,
        rewardType: String,
        claimed expected: Bool,
        _ message: String = "",
        file: StaticString = #filePath,
        line: UInt = #line
    ) async {
        let result = await grants.claim(productId: productId ?? self.productId, rewardType: rewardType)
        XCTAssertEqual(
            result.claimed,
            expected,
            message.isEmpty ? "expected claimed=\(expected); report was: \(result.report)" : message,
            file: file,
            line: line
        )
        if !expected { XCTAssertEqual(result.rewardAmount, 0, "a rejected claim must grant nothing", file: file, line: line) }
    }

    private func lifecycle(in state: SubscriptionState) async -> SubscriptionLifecycle {
        let lifecycle = SubscriptionLifecycle()
        lifecycle.configure(.init(eventSink: FakeEventSink(), playerId: "p1", store: InMemoryStore()))
        guard let snapshot = Self.snapshotFor(state, productId: productId) else { return lifecycle }
        _ = await lifecycle.observe([snapshot])
        return lifecycle
    }

    private static func snapshotFor(_ state: SubscriptionState, productId: String) -> SubscriptionSnapshot? {
        switch state {
        case .none: return nil
        case .active: return SubscriptionSnapshot(productId: productId, renewalState: .subscribed, willAutoRenew: true)
        case .cancelled: return SubscriptionSnapshot(productId: productId, renewalState: .subscribed, willAutoRenew: false)
        case .inGracePeriod: return SubscriptionSnapshot(productId: productId, renewalState: .inGracePeriod, willAutoRenew: true)
        case .inBillingRetry: return SubscriptionSnapshot(productId: productId, renewalState: .inBillingRetryPeriod, willAutoRenew: true)
        case .expired: return SubscriptionSnapshot(productId: productId, renewalState: .expired, willAutoRenew: false)
        case .revoked: return SubscriptionSnapshot(productId: productId, renewalState: .revoked, willAutoRenew: false)
        }
    }

    override func setUp() {
        super.setUp()
        RecurringGrants.shared.resetForTest()
    }

    override func tearDown() {
        RecurringGrants.shared.resetForTest()
        super.tearDown()
    }

    // MARK: - Defaults

    func test_beforeAnyApplyNothingIsDueAndVersionIsNotLoaded() {
        let grants = makeGrants(clock: FakeClock(baseMs))
        XCTAssertEqual(grants.activeVersion(), "not loaded")
        XCTAssertTrue(grants.dueGrants().isEmpty)
    }

    func test_claimBeforeConfigureIsRejectedAndGrantsNothing() async {
        let grants = RecurringGrants()
        let result = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertFalse(result.claimed)
        XCTAssertEqual(result.rewardAmount, 0)
        XCTAssertEqual(result.rewardType, "gem")
        XCTAssertFalse(result.report.isEmpty)
    }

    // MARK: - Config: reject paths keep the last-good config

    func test_applyNilOrBlankConfigIsRejected() {
        let grants = makeGrants(clock: FakeClock(baseMs))
        XCTAssertFalse(grants.apply(nil).applied)
        XCTAssertFalse(grants.apply("   ").applied)
        XCTAssertEqual(grants.activeVersion(), "not loaded")
    }

    func test_applyMissingOrBlankVersionIsRejected() {
        let grants = makeGrants(clock: FakeClock(baseMs))
        XCTAssertFalse(grants.apply(#"{"enabled": true}"#).applied)
        XCTAssertFalse(grants.apply(#"{"version": "  "}"#).applied)
        XCTAssertEqual(grants.activeVersion(), "not loaded")
    }

    func test_applyMalformedJsonIsRejectedAndKeepsLastGoodConfig() {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs))
        let result = grants.apply("{not valid json")
        XCTAssertFalse(result.applied)
        XCTAssertTrue(result.report.contains("rg_v1"), "report was: \(result.report)")
        XCTAssertEqual(grants.activeVersion(), "rg_v1")
        XCTAssertEqual(grants.dueGrants().count, 2, "the last-good config must still be running")
    }

    func test_applyLoadsEveryValidGrantLine() {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs))
        XCTAssertEqual(grants.activeVersion(), "rg_v1")
        let due = grants.dueGrants()
        XCTAssertEqual(due.count, 2)
        XCTAssertEqual(due.first, RecurringGrant(productId: productId, periodHours: 24, rewardType: "gem", rewardAmount: 500))
        XCTAssertEqual(due.last, RecurringGrant(productId: productId, periodHours: 168, rewardType: "ticket", rewardAmount: 1))
    }

    // MARK: - Config: 'enabled'

    func test_enabledWrongTypeDegradesToTrueWithANote() {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs), json: """
        {"version": "rg_v1", "enabled": 1, "grants": [
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500}
        ]}
        """)
        let result = grants.apply("""
        {"version": "rg_v2", "enabled": 1, "grants": []}
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("'enabled' is not a boolean"), "report was: \(result.report)")
    }

    func test_enabledFalseHidesEveryGrant() async {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs), json: """
        {"version": "rg_v1", "enabled": false, "grants": [
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500}
        ]}
        """)
        XCTAssertTrue(grants.dueGrants().isEmpty)
        let result = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertFalse(result.claimed)
    }

    /// The clock MUST advance across the disable/enable cycle for this test to mean anything.
    /// Without it, "timestamp preserved" and "timestamp silently reset at re-enable" are
    /// indistinguishable — both read as 0 elapsed, not due — so the assertion would hold against
    /// exactly the regression it exists to catch. Advancing 23h and then 1h more proves the gem
    /// line becomes due 24h after the ORIGINAL claim, not 24h after re-enabling.
    func test_disabledConfigHidesStateWithoutDestroyingClaimTimestamps() async {
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock)
        await expectClaim(grants, rewardType: "gem", claimed: true)

        let disabled = config.replacingOccurrences(of: "\"enabled\": true", with: "\"enabled\": false")
        XCTAssertTrue(grants.apply(disabled).applied)
        XCTAssertTrue(grants.dueGrants().isEmpty)

        clock.advance(hours: 23)
        XCTAssertTrue(grants.apply(config).applied)
        XCTAssertEqual(grants.dueGrants().map(\.rewardType), ["ticket"],
                       "23h after the original claim the gem line is still not due")

        clock.advance(hours: 1)
        XCTAssertEqual(Set(grants.dueGrants().map(\.rewardType)), ["gem", "ticket"],
                       "the gem line must become due 24h after the ORIGINAL claim — a timestamp reset at re-enable would delay it to 24h after that")
    }

    // MARK: - Several cadences on one reward type

    /// The one behavior `claim`'s own doc comment singles out as a deliberate decision, and the one
    /// with no test before this. Config order is deliberately "wrong" here — the 168h line is
    /// declared first — so a regression to `matching[0]` (taking the first CONFIGURED line rather
    /// than the first DUE one) makes the 24h line permanently unclaimable and fails this test.
    func test_claimConsumesTheDueLineRegardlessOfConfigOrder() async {
        let twoCadences = """
        {"version": "rg_multi", "enabled": true, "grants": [
          {"product_id": "\(productId)", "period_hours": 168, "reward_type": "gem", "reward_amount": 900},
          {"product_id": "\(productId)", "period_hours": 24,  "reward_type": "gem", "reward_amount": 100}
        ]}
        """
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock, json: twoCadences)

        // Both start due; the first DUE line in config order is the 168h one.
        let first = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertTrue(first.claimed)
        XCTAssertEqual(first.rewardAmount, 900)

        // The 24h line is independently claimable and must not have been consumed by the above.
        let second = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertTrue(second.claimed, "the other cadence must remain claimable: \(second.report)")
        XCTAssertEqual(second.rewardAmount, 100)

        // Both consumed: nothing left this period.
        let third = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertFalse(third.claimed)

        // 24h on, only the shorter line comes back.
        clock.advance(hours: 24)
        let fourth = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertTrue(fourth.claimed)
        XCTAssertEqual(fourth.rewardAmount, 100, "only the 24h line should have elapsed")
    }

    /// A rejection must name the line closest to elapsing, not whichever the operator happened to
    /// declare first — a support engineer reading "167h" off a debug screen when the real wait is
    /// an hour is worse than no number at all.
    func test_rejectionReportsTheSoonestLineNotTheFirstConfigured() async {
        let twoCadences = """
        {"version": "rg_multi", "enabled": true, "grants": [
          {"product_id": "\(productId)", "period_hours": 168, "reward_type": "gem", "reward_amount": 900},
          {"product_id": "\(productId)", "period_hours": 24,  "reward_type": "gem", "reward_amount": 100}
        ]}
        """
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock, json: twoCadences)
        _ = await grants.claim(productId: productId, rewardType: "gem")
        _ = await grants.claim(productId: productId, rewardType: "gem")

        clock.advance(hours: 23)
        let rejected = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertFalse(rejected.claimed)
        XCTAssertTrue(rejected.report.contains("24h"),
                      "must cite the 24h line (1h away), not the 168h line: \(rejected.report)")
        XCTAssertTrue(rejected.report.contains("1h remaining"),
                      "must report the real remaining wait: \(rejected.report)")
    }

    // MARK: - Config: per-line drops

    func test_grantEntryThatIsNotAnObjectIsDroppedWithANote() {
        let grants = makeGrants(clock: FakeClock(baseMs))
        let result = grants.apply("""
        {"version": "rg_v1", "grants": ["nope", {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500}]}
        """)
        XCTAssertTrue(result.applied, "one bad line must never reject the whole config")
        XCTAssertTrue(result.report.contains("not an object"), "report was: \(result.report)")
        XCTAssertEqual(grants.activeVersion(), "rg_v1")
    }

    func test_grantMissingProductIdOrRewardTypeIsDroppedWithANote() {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs), json: """
        {"version": "rg_v1", "grants": [
          {"period_hours": 24, "reward_type": "gem", "reward_amount": 500},
          {"product_id": "vip_pass", "period_hours": 24, "reward_amount": 500},
          {"product_id": "  ", "period_hours": 24, "reward_type": "gem", "reward_amount": 500},
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": 7, "reward_amount": 500}
        ]}
        """)
        XCTAssertTrue(grants.dueGrants().isEmpty)
        let result = grants.apply(#"{"version": "rg_v2", "grants": [{"period_hours": 24, "reward_type": "gem", "reward_amount": 500}]}"#)
        XCTAssertTrue(result.report.contains("product_id"), "report was: \(result.report)")
    }

    func test_productIdContainingAPersistenceDelimiterIsDroppedWithANote() {
        let grants = makeGrants(clock: FakeClock(baseMs))
        let result = grants.apply("""
        {"version": "rg_v1", "grants": [
          {"product_id": "vip|pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500},
          {"product_id": "vip:pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500}
        ]}
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(grants.dueGrants().isEmpty, "both delimiter-bearing lines must be dropped — fail closed")
        XCTAssertTrue(result.report.contains("vip|pass"), "report was: \(result.report)")
        XCTAssertTrue(result.report.contains("vip:pass"), "report was: \(result.report)")
    }

    func test_rewardTypeContainingAPersistenceDelimiterIsDroppedWithANote() {
        let grants = makeGrants(clock: FakeClock(baseMs))
        let result = grants.apply("""
        {"version": "rg_v1", "grants": [
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "ge|m", "reward_amount": 500},
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "ge:m", "reward_amount": 500}
        ]}
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(grants.dueGrants().isEmpty)
        XCTAssertTrue(result.report.contains("reward_type"), "report was: \(result.report)")
    }

    func test_nonPositiveNumericsAreDroppedWithANote() {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs), json: """
        {"version": "rg_v1", "grants": [
          {"product_id": "vip_pass", "period_hours": 0, "reward_type": "gem", "reward_amount": 500},
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "ticket", "reward_amount": 0},
          {"product_id": "vip_pass", "period_hours": -5, "reward_type": "coin", "reward_amount": 500}
        ]}
        """)
        XCTAssertTrue(grants.dueGrants().isEmpty)
    }

    func test_booleanNumericsAreDroppedWithANote() {
        let grants = makeGrants(clock: FakeClock(baseMs))
        let result = grants.apply("""
        {"version": "rg_v1", "grants": [
          {"product_id": "vip_pass", "period_hours": true, "reward_type": "gem", "reward_amount": 500},
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "ticket", "reward_amount": true}
        ]}
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(grants.dueGrants().isEmpty, "'true' must never bridge to a usable 1")
        XCTAssertTrue(result.report.contains("period_hours"), "report was: \(result.report)")
        XCTAssertTrue(result.report.contains("reward_amount"), "report was: \(result.report)")
    }

    func test_missingOrNonNumericFieldsAreDroppedWithANote() {
        let grants = makeGrants(clock: FakeClock(baseMs))
        let result = grants.apply("""
        {"version": "rg_v1", "grants": [
          {"product_id": "vip_pass", "reward_type": "gem", "reward_amount": 500},
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "ticket"},
          {"product_id": "vip_pass", "period_hours": "soon", "reward_type": "coin", "reward_amount": 500}
        ]}
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(grants.dueGrants().isEmpty)
    }

    func test_nonFiniteNumericIsDroppedRatherThanClamped() {
        let grants = makeGrants(clock: FakeClock(baseMs))
        _ = grants.apply("""
        {"version": "rg_v1", "grants": [
          {"product_id": "vip_pass", "period_hours": "nan", "reward_type": "gem", "reward_amount": 500},
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "ticket", "reward_amount": "inf"}
        ]}
        """)
        XCTAssertTrue(grants.dueGrants().isEmpty)
    }

    /// The branch `Int(someDouble)` traps on: `1e300` decodes ONLY as a `Double` (`as? Int` is
    /// `nil`), so the clamp must happen in `Double` space. A value like `999999999` casts cleanly
    /// as `Int` and would never reach the trapping conversion at all.
    func test_absurdPeriodHoursClampsInDoubleSpaceToOneYear() {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs), json: """
        {"version": "rg_v1", "grants": [
          {"product_id": "vip_pass", "period_hours": 1e300, "reward_type": "gem", "reward_amount": 500}
        ]}
        """)
        XCTAssertEqual(grants.dueGrants().first?.periodHours, 8760)
    }

    /// A 20-digit integer literal likewise decodes only as a `Double` (`NSDecimalNumber`, `as? Int`
    /// == `nil`) and would trap an unguarded `Int64(_:)`.
    func test_absurdRewardAmountClampsInDoubleSpaceToHalfOfInt64Max() {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs), json: """
        {"version": "rg_v1", "grants": [
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 99999999999999999999}
        ]}
        """)
        XCTAssertEqual(grants.dueGrants().first?.rewardAmount, Int64.max / 2)
    }

    func test_fractionalPeriodHoursClampsUpToTheOneHourFloor() {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs), json: """
        {"version": "rg_v1", "grants": [
          {"product_id": "vip_pass", "period_hours": 0.5, "reward_type": "gem", "reward_amount": 500}
        ]}
        """)
        XCTAssertEqual(grants.dueGrants().first?.periodHours, 1)
    }

    func test_duplicateGrantLinesKeepTheFirstWithANote() {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs), json: """
        {"version": "rg_v1", "grants": [
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500},
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 99999}
        ]}
        """)
        XCTAssertEqual(grants.dueGrants().count, 1)
        XCTAssertEqual(grants.dueGrants().first?.rewardAmount, 500, "first wins")
    }

    func test_grantsAbsentOrNotAnArrayLeavesNothingDueWithANote() {
        let grants = makeGrants(clock: FakeClock(baseMs))
        let absent = grants.apply(#"{"version": "rg_v1"}"#)
        XCTAssertTrue(absent.applied)
        XCTAssertTrue(absent.report.contains("grants"), "report was: \(absent.report)")
        XCTAssertTrue(grants.dueGrants().isEmpty)

        let wrongType = grants.apply(#"{"version": "rg_v2", "grants": {"product_id": "vip_pass"}}"#)
        XCTAssertTrue(wrongType.applied)
        XCTAssertTrue(grants.dueGrants().isEmpty)
    }

    // MARK: - Ownership (§3.1)

    func test_ownershipFollowsIsEntitledAcrossAllSevenSubscriptionStates() async {
        // `.cancelled` and `.inGracePeriod` are the two Android's `== ACTIVE` check silently cuts
        // off despite the customer having already paid for the period.
        let entitled: [SubscriptionState] = [.active, .cancelled, .inGracePeriod]
        for state in SubscriptionState.allCases {
            let grants = makeGrants(clock: FakeClock(baseMs), lifecycle: await lifecycle(in: state))
            _ = grants.apply(config)
            let due = grants.dueGrants()
            if entitled.contains(state) {
                XCTAssertEqual(due.count, 2, "\(state) is entitled and must receive grants")
            } else {
                XCTAssertTrue(due.isEmpty, "\(state) is not entitled and must receive nothing")
            }
        }
    }

    func test_inBillingRetryIsNotEntitledAndCannotClaim() async {
        let grants = makeGrants(clock: FakeClock(baseMs), lifecycle: await lifecycle(in: .inBillingRetry))
        _ = grants.apply(config)
        let result = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertFalse(result.claimed)
        XCTAssertEqual(result.rewardAmount, 0)
    }

    func test_entitlementsAloneIsEnoughToOwnAGrant() async {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs))
        XCTAssertEqual(grants.dueGrants().count, 2)
        await expectClaim(grants, rewardType: "gem", claimed: true)
    }

    func test_bothOwnershipSourcesTrueStillGrantsOnlyOnce() async {
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant(productId)
        let clock = FakeClock(baseMs)
        let grants = makeGrants(clock: clock, entitlements: entitlements, lifecycle: await lifecycle(in: .active))
        _ = grants.apply(config)

        XCTAssertEqual(grants.dueGrants().filter { $0.rewardType == "gem" }.count, 1, "one line, not one per ownership source")
        await expectClaim(grants, rewardType: "gem", claimed: true)
        await expectClaim(grants, rewardType: "gem", claimed: false)
    }

    func test_revokingOwnershipStopsFurtherGrants() async {
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant(productId)
        let grants = makeGrants(clock: FakeClock(baseMs), entitlements: entitlements)
        _ = grants.apply(config)
        XCTAssertFalse(grants.dueGrants().isEmpty)
        entitlements.revoke(productId)
        XCTAssertTrue(grants.dueGrants().isEmpty)
    }

    // MARK: - Timing (§3.3 / §3.5)

    func test_firstEverClaimIsImmediatelyDue() async {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs))
        XCTAssertEqual(grants.dueGrants().count, 2)
        let result = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertTrue(result.claimed)
        XCTAssertEqual(result.rewardAmount, 500)
        XCTAssertEqual(result.rewardType, "gem")
    }

    func test_aClaimedLineIsNotDueAgainUntilThePeriodElapses() async {
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock)
        await expectClaim(grants, rewardType: "gem", claimed: true)
        XCTAssertEqual(grants.dueGrants().map(\.rewardType), ["ticket"])
    }

    func test_periodBoundaryJustBeforeElapsedIsNotDue() async {
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock)
        _ = await grants.claim(productId: productId, rewardType: "gem")
        clock.advance(ms: 24 * 3_600_000 - 1)
        XCTAssertFalse(grants.dueGrants().contains { $0.rewardType == "gem" })
        await expectClaim(grants, rewardType: "gem", claimed: false)
    }

    func test_periodBoundaryExactlyAtElapsedIsDue() async {
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock)
        _ = await grants.claim(productId: productId, rewardType: "gem")
        clock.advance(ms: 24 * 3_600_000)
        XCTAssertTrue(grants.dueGrants().contains { $0.rewardType == "gem" })
        await expectClaim(grants, rewardType: "gem", claimed: true)
    }

    func test_periodBoundaryJustAfterElapsedIsDue() async {
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock)
        _ = await grants.claim(productId: productId, rewardType: "gem")
        clock.advance(ms: 24 * 3_600_000 + 1)
        await expectClaim(grants, rewardType: "gem", claimed: true)
    }

    func test_noCatchUpAfterALongAbsence() async {
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock)
        await expectClaim(grants, rewardType: "gem", claimed: true)
        clock.advance(hours: 24 * 30) // thirty missed periods
        await expectClaim(grants, rewardType: "gem", claimed: true)
        await expectClaim(grants, rewardType: "gem", claimed: false, "missing thirty periods grants one reward, not thirty")
    }

    func test_clockMovedBackwardsDoesNotGrantEarly() async {
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock)
        await expectClaim(grants, rewardType: "gem", claimed: true)
        clock.rewind(hours: 500)
        XCTAssertFalse(grants.dueGrants().contains { $0.rewardType == "gem" })
        await expectClaim(grants, rewardType: "gem", claimed: false)
    }

    /// §3.4's documented, deliberately UNMITIGATED limit: advancing the device clock by one period
    /// farms another reward. This test pins the known behavior so nobody "fixes" it by accident
    /// without reading why both candidate mitigations were rejected.
    func test_clockMovedForwardIsDocumentedNotPrevented() async {
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock)
        for _ in 0..<5 {
            await expectClaim(grants, rewardType: "gem", claimed: true)
            clock.advance(hours: 24)
        }
    }

    func test_eachGrantLineKeepsItsOwnIndependentSchedule() async {
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock)
        await expectClaim(grants, rewardType: "gem", claimed: true)
        await expectClaim(grants, rewardType: "ticket", claimed: true)
        clock.advance(hours: 24)
        await expectClaim(grants, rewardType: "gem", claimed: true)
        await expectClaim(grants, rewardType: "ticket", claimed: false, "the 168h line is nowhere near due")
    }

    // MARK: - claim()

    func test_claimForAnUnconfiguredRewardTypeIsRejected() async {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs))
        let result = await grants.claim(productId: productId, rewardType: "diamond")
        XCTAssertFalse(result.claimed)
        XCTAssertEqual(result.rewardAmount, 0)
    }

    func test_claimForAnUnconfiguredProductIsRejected() async {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs))
        let result = await grants.claim(productId: "other_pass", rewardType: "gem")
        XCTAssertFalse(result.claimed)
    }

    func test_claimEmitsExactlyOneEventWithTheLockedWireShape() async {
        let sink = FakeEventSink()
        let grants = makeOwnedGrants(clock: FakeClock(baseMs), eventSink: sink)
        await expectClaim(grants, rewardType: "gem", claimed: true)

        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 1)
        guard let event = events.first else { return }
        XCTAssertEqual(event.eventName, "recurring_grant_claim")
        XCTAssertEqual(event.eventData["product_id"], .string(productId))
        XCTAssertEqual(event.eventData["reward_type"], .string("gem"))
        XCTAssertEqual(event.eventData["reward_amount"], .int(500))
        XCTAssertEqual(event.eventData["period_hours"], .int(24))
        XCTAssertEqual(event.eventData["recurring_grants_version"], .string("rg_v1"))
        XCTAssertEqual(event.userProperty.userId, "p1")
        XCTAssertEqual(event.sequenceId, 0, "sequenceId is EventQueue's to assign, never a call site's")
    }

    func test_rejectedClaimEmitsNoEvent() async {
        let sink = FakeEventSink()
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock, eventSink: sink)
        _ = await grants.claim(productId: productId, rewardType: "gem")
        _ = await grants.claim(productId: productId, rewardType: "gem") // too soon
        _ = await grants.claim(productId: productId, rewardType: "diamond") // unknown line
        _ = await grants.claim(productId: "other_pass", rewardType: "gem") // unknown product
        try? await Task.sleep(nanoseconds: 100_000_000)
        let sentCount1 = await sink.sentEvents.count
        XCTAssertEqual(sentCount1, 1, "only the one successful claim may emit")
    }

    /// `claim` awaits its own send rather than handing it to a detached `Task` — the drop window
    /// `SubscriptionLifecycle.observe`'s doc comment describes. A bounded poll would pass against
    /// a regression to fire-and-forget; reading the sink with no wait at all is what holds it.
    func test_claimDeliversItsEventBeforeReturning() async {
        let sink = FakeEventSink()
        let grants = makeOwnedGrants(clock: FakeClock(baseMs), eventSink: sink)
        _ = await grants.claim(productId: productId, rewardType: "gem")
        let sentCount2 = await sink.sentEvents.count
        XCTAssertEqual(sentCount2, 1)
    }

    func test_claimReportIsPopulatedOnBothOutcomes() async {
        let grants = makeOwnedGrants(clock: FakeClock(baseMs))
        let granted = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertFalse(granted.report.isEmpty)
        let rejected = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertFalse(rejected.report.isEmpty)
    }

    // MARK: - dueGrants() purity

    func test_dueGrantsIsPureAcrossRepeatedCalls() async {
        let store = InMemoryStore()
        let sink = FakeEventSink()
        let grants = makeOwnedGrants(store: store, clock: FakeClock(baseMs), eventSink: sink)
        let first = grants.dueGrants()
        for _ in 0..<5 { XCTAssertEqual(grants.dueGrants(), first) }
        XCTAssertEqual(store.writes(), 0, "a pure query must never persist")
        let sentCount3 = await sink.sentEvents.count
        XCTAssertEqual(sentCount3, 0, "a pure query must never emit")
    }

    // MARK: - Persistence (§3.2) — THE round-trip

    /// The test whose absence shipped Android's bug: its composite key contains the same `|` it
    /// joins entries with, so restored keys never match again and EVERY grant is due after any
    /// restart — unlimited rewards by force-quitting.
    func test_claimTimestampsRoundTripAcrossAFreshInstanceOverTheSameStore() async {
        let store = InMemoryStore()
        let clock = FakeClock(baseMs)
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant(productId)

        let first = makeGrants(store: store, clock: clock, entitlements: entitlements)
        _ = first.apply(config)
        await expectClaim(first, rewardType: "gem", claimed: true)
        await expectClaim(first, rewardType: "ticket", claimed: true)

        // A process restart: a brand new instance reading the same store.
        let restarted = makeGrants(store: store, clock: clock, entitlements: entitlements)
        _ = restarted.apply(config)
        XCTAssertTrue(restarted.dueGrants().isEmpty, "both claim timestamps must be recovered under keys that still match")
        await expectClaim(restarted, rewardType: "gem", claimed: false)

        clock.advance(hours: 24)
        XCTAssertEqual(restarted.dueGrants().map(\.rewardType), ["gem"], "the recovered gem timestamp must drive the next period, not a zero baseline")
    }

    func test_persistedLineIsParseableAndKeyedByProductRewardAndPeriod() async {
        let store = InMemoryStore()
        let grants = makeOwnedGrants(store: store, clock: FakeClock(baseMs))
        _ = await grants.claim(productId: productId, rewardType: "gem")
        let raw = store.peek(RecurringGrants.lastClaimKey) ?? ""
        XCTAssertFalse(raw.isEmpty)
        XCTAssertTrue(raw.contains("vip_pass"), "raw was: \(raw)")
        XCTAssertTrue(raw.contains("gem"), "raw was: \(raw)")
        XCTAssertTrue(raw.contains("24"), "raw was: \(raw)")
        XCTAssertTrue(raw.contains(String(baseMs)), "raw was: \(raw)")
    }

    func test_twoPersistedEntriesBothSurviveTheirJoin() async {
        let store = InMemoryStore()
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(store: store, clock: clock)
        _ = await grants.claim(productId: productId, rewardType: "gem")
        _ = await grants.claim(productId: productId, rewardType: "ticket")

        let restarted = makeOwnedGrants(store: store, clock: clock)
        XCTAssertTrue(restarted.dueGrants().isEmpty, "the second entry must not mangle the first")
    }

    func test_corruptPersistedEntriesAreIgnoredAndTheRestSurvives() {
        let store = InMemoryStore()
        store.setString("garbage|vip_pass:gem:24:\(baseMs)|:99|novalue:", forKey: RecurringGrants.lastClaimKey)
        let clock = FakeClock(baseMs)
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant(productId)
        let grants = makeGrants(store: store, clock: clock, entitlements: entitlements)
        _ = grants.apply(config)
        XCTAssertEqual(grants.dueGrants().map(\.rewardType), ["ticket"], "the one readable entry must still suppress its own line")
    }

    func test_resetForTestClearsBothMemoryAndStore() async {
        let store = InMemoryStore()
        let grants = makeOwnedGrants(store: store, clock: FakeClock(baseMs))
        _ = await grants.claim(productId: productId, rewardType: "gem")
        XCTAssertNotNil(store.peek(RecurringGrants.lastClaimKey))
        grants.resetForTest()
        XCTAssertEqual(grants.activeVersion(), "not loaded")
        XCTAssertTrue(grants.dueGrants().isEmpty)
        XCTAssertNil(store.peek(RecurringGrants.lastClaimKey))
    }

    // MARK: - Clock seam (§3.3)

    func test_systemClockReportsMillisecondsNotSeconds() {
        let now = SystemClock().nowMs()
        XCTAssertGreaterThan(now, 1_600_000_000_000, "epoch MILLIseconds, matching SdkEvent.ts")
        XCTAssertLessThan(now, 4_000_000_000_000)
    }

    // MARK: - Concurrency

    func test_concurrentClaimsGrantTheLineExactlyOnce() async {
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(clock: clock)
        let results = await withTaskGroup(of: Bool.self) { group -> [Bool] in
            for _ in 0..<20 { group.addTask { await grants.claim(productId: self.productId, rewardType: "gem").claimed } }
            var out: [Bool] = []
            for await claimed in group { out.append(claimed) }
            return out
        }
        XCTAssertEqual(results.filter { $0 }.count, 1, "a period must be claimable exactly once, however many callers race")
    }

    /// A clock that stalls INSIDE the critical section, because `claim` reads it there.
    ///
    /// The unengineered race above depends on the cooperative pool happening to overlap two tasks
    /// inside a window that is only nanoseconds wide — on a loaded CI runner it can pass without
    /// ever exercising the race, so it detects nothing a code read doesn't already establish.
    /// Stalling here widens that window to 50ms and guarantees the other callers are queued on the
    /// lock before the first one writes its timestamp.
    private final class StallingClock: BrainClock, @unchecked Sendable {
        private let lock = NSLock()
        private var callCount = 0
        private let fixed: Int64
        private let stallFirst: Int
        init(_ fixed: Int64, stallFirst: Int) { self.fixed = fixed; self.stallFirst = stallFirst }
        func nowMs() -> Int64 {
            lock.lock()
            callCount += 1
            let shouldStall = callCount <= stallFirst
            lock.unlock()
            if shouldStall { Thread.sleep(forTimeInterval: 0.05) }
            return fixed
        }
    }

    /// The engineered-contention version: proves the authorization-and-consumption window really is
    /// one atomic acquisition, rather than passing because the scheduler never overlapped anything.
    ///
    /// Measured, not assumed. With `withLock` mutated to run its body without taking the lock:
    ///   • the unengineered test above passes — 3 runs out of 3. It detects nothing.
    ///   • this test kills the test process (SIGSEGV) on every run.
    ///
    /// The detection is a crash rather than a failed assertion, because concurrent mutation of the
    /// `lastClaim` dictionary is undefined behavior long before the winner count is comparable —
    /// which is the honest shape of this bug, and still an unmissable red.
    ///
    /// That contrast is the whole reason this test exists: a race test with no engineered overlap
    /// asserts a property the scheduler is under no obligation to exercise.
    func test_concurrentClaimsGrantExactlyOnceUnderForcedContention() async {
        let grants = makeOwnedGrants(clock: StallingClock(baseMs, stallFirst: 8))
        let results = await withTaskGroup(of: Bool.self) { group -> [Bool] in
            for _ in 0..<8 { group.addTask { await grants.claim(productId: self.productId, rewardType: "gem").claimed } }
            var out: [Bool] = []
            for await claimed in group { out.append(claimed) }
            return out
        }
        XCTAssertEqual(results.filter { $0 }.count, 1,
                       "exactly one caller may consume the period even when every caller is forced to queue on the lock")
    }

    // MARK: - Config identifier length ceiling (L1)

    /// `product_id` and `reward_type` are both embedded in the persistence key, so an unbounded one
    /// is rewritten in full on every claim. Operator-controlled, so availability rather than a
    /// privilege boundary — but silent, and the fix is a comparison. A malformed line degrades (the
    /// line is dropped, the rest of the config applies), which is this parser's existing rule.
    func test_anAbsurdlyLongProductIdOrRewardTypeDropsOnlyThatGrantLine() async {
        let store = InMemoryStore()
        let huge = String(repeating: "x", count: 5_000)
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant(productId)
        entitlements.grant(huge)
        let grants = makeGrants(store: store, clock: FakeClock(baseMs), entitlements: entitlements)

        let result = grants.apply("""
        {"version": "rg_v9", "enabled": true, "grants": [
          {"product_id": "\(huge)", "period_hours": 24, "reward_type": "gem", "reward_amount": 1},
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "\(huge)", "reward_amount": 1},
          {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500}
        ]}
        """)

        XCTAssertTrue(result.applied, "report was: \(result.report.prefix(300))")
        XCTAssertLessThan(result.report.count, 2_000, "the diagnostic must not reproduce the ids")
        XCTAssertTrue(result.report.contains("128-character limit"), "report was: \(result.report.prefix(300))")
        // The good line survives; neither over-long line became a real, live grant.
        XCTAssertEqual(grants.dueGrants().map(\.rewardType), ["gem"])
        await expectClaim(grants, productId: huge, rewardType: "gem", claimed: false)
        await expectClaim(grants, rewardType: huge, claimed: false)
        await expectClaim(grants, rewardType: "gem", claimed: true)
        XCTAssertFalse(store.peek(RecurringGrants.lastClaimKey)?.contains("xxxx") ?? false,
                       "an over-long id must never reach storage")
    }

    // MARK: - Concurrency: persisted claim history must not regress (H1)

    /// ═══ THE DOUBLE-GRANT THIS CLOSES ═══
    ///
    /// The lock made authorization and consumption atomic, but the `setString` that followed was
    /// outside it and nothing ordered two of them. The `gem` claim encoded a map holding only its
    /// own baseline; the `ticket` claim then encoded a map holding BOTH. With the writes unordered,
    /// the older snapshot could land LAST, and storage forgot a period that had already been
    /// consumed and handed to the host app to grant. In-memory state stayed correct, so nothing
    /// looked wrong until a force-quit — at which point the player claims the reward again. This is
    /// the same unlimited-farming outcome as Android's delimiter bug (§3.2), reached by a different
    /// road and needing no clock manipulation either.
    ///
    /// The ordering is engineered, not hoped for: the first claim's write is armed to stall inside
    /// the store, and the second claim is issued 100ms into a 400ms stall.
    func test_aStragglingClaimWriteCannotEraseALaterClaimFromStorage() async {
        let store = DelayingStore()
        let clock = FakeClock(baseMs)
        let grants = makeOwnedGrants(store: store, clock: clock)
        store.armDelay()

        let first = Task { await grants.claim(productId: self.productId, rewardType: "gem").claimed }
        // Long enough for the first claim to leave the lock and be inside the stalling store call.
        try? await Task.sleep(nanoseconds: 100_000_000)
        let second = await grants.claim(productId: productId, rewardType: "ticket")
        let firstClaimed = await first.value

        XCTAssertTrue(firstClaimed)
        XCTAssertTrue(second.claimed, "report was: \(second.report)")
        let raw = store.peek(RecurringGrants.lastClaimKey) ?? ""
        XCTAssertTrue(raw.contains("vip_pass:gem:24:\(baseMs)"),
                      "landed writes were: \(store.landed()); raw was: \(raw)")
        XCTAssertTrue(raw.contains("vip_pass:ticket:168:\(baseMs)"),
                      "the newest snapshot must be the one that stands; landed writes were: \(store.landed()); raw was: \(raw)")

        // What the regression actually costs, made concrete: a process restart over the same store.
        let restarted = makeOwnedGrants(store: store, clock: clock)
        await expectClaim(restarted, rewardType: "gem", claimed: false, "a consumed period must not come back")
        await expectClaim(restarted, rewardType: "ticket", claimed: false, "a consumed period must not come back")
    }
}
