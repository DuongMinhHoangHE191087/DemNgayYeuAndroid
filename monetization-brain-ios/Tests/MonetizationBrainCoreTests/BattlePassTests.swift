import XCTest
@testable import MonetizationBrainCore


final class BattlePassTests: XCTestCase {
    // MARK: - File-local fakes
    //
    // Redeclared per-file, matching this project's established convention of NOT sharing test
    // fakes across files (see `RecurringGrantsTests`/`SubscriptionLifecycleTests`' own copies).

    /// Counts writes, because the purity claim for `xpTotal`/`currentTier`/`isPremiumActive`/
    /// `claimable`/`phase` (spec §1) is only provable by showing they never touch the store.
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

    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

    /// The Phase 8 time seam: ALL season timing goes through it, so no public API needs a `ts:`
    /// parameter to be testable.
    private final class FakeClock: BrainClock, @unchecked Sendable {
        private let lock = NSLock()
        private var current: Int64
        init(_ start: Int64) { self.current = start }
        func nowMs() -> Int64 { lock.lock(); defer { lock.unlock() }; return current }
        func advance(ms: Int64) { lock.lock(); current += ms; lock.unlock() }
        func set(_ value: Int64) { lock.lock(); current = value; lock.unlock() }
    }

    /// Engineered contention (the `RecurringGrantsTests` pattern). `nowMs()` is called INSIDE the
    /// lock by both mutating paths, so stalling it forces every concurrent caller to queue on the
    /// lock instead of hoping the scheduler happens to overlap them.
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

    /// Engineered contention for the STORE, the way `StallingClock` engineers it for the clock.
    /// The race this exists to catch lives entirely between two `setString` calls, so the delay has
    /// to be there and nowhere else: `armDelay()` makes the NEXT write to arrive sleep while a
    /// later one runs past it. A plain task storm was measured (Phase 8) to detect nothing.
    private final class DelayingStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        private var delayNext = false
        private(set) var writeOrder: [String] = []
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

    // MARK: - Fixtures

    private let seasonId = "season_3"
    private let premiumProductId = "bp_season_3_premium"
    private let startMs: Int64 = 1_700_000_000_000
    private let endMs: Int64 = 1_700_086_400_000   // start + 24h
    private let midMs: Int64 = 1_700_043_200_000   // exactly half way — the catch-up boundary

    /// Tier 1 at 0 XP (reachable with no XP at all, §7), tier 2 at 100, tier 3 at 250.
    private var config: String {
        """
        {"version": "bp_v1", "season_id": "season_3",
         "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "premium_product_id": "bp_season_3_premium",
         "xp_cap": 1000,
         "tiers": [
           {"tier": 1, "xp_required": 0,
            "rewards_free": [{"id": "r1", "type": "gem", "amount": 10}],
            "rewards_premium": [{"id": "r2", "type": "skin", "amount": 1}]},
           {"tier": 2, "xp_required": 100,
            "rewards_free": [{"id": "r3", "type": "gem", "amount": 20}],
            "rewards_premium": []},
           {"tier": 3, "xp_required": 250,
            "rewards_free": [],
            "rewards_premium": [{"id": "r4", "type": "coin", "amount": 5}]}
         ]}
        """
    }

    /// Same shape, a DIFFERENT `season_id` — the rollover fixture.
    private var nextSeasonConfig: String {
        """
        {"version": "bp_v2", "season_id": "season_4",
         "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "premium_product_id": "bp_season_4_premium",
         "xp_cap": 1000,
         "tiers": [
           {"tier": 1, "xp_required": 0, "rewards_free": [{"id": "r1", "type": "gem", "amount": 10}], "rewards_premium": []},
           {"tier": 2, "xp_required": 100, "rewards_free": [], "rewards_premium": []},
           {"tier": 3, "xp_required": 250, "rewards_free": [], "rewards_premium": []}
         ]}
        """
    }

    private func catchUpConfig(enabled: String = "true", startFraction: String = "0.5", multiplier: String = "1.5") -> String {
        """
        {"version": "bp_v1", "season_id": "season_3",
         "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "premium_product_id": "bp_season_3_premium",
         "tiers": [{"tier": 1, "xp_required": 0, "rewards_free": [], "rewards_premium": []}],
         "catch_up": {"enabled": \(enabled), "start_fraction": \(startFraction), "multiplier": \(multiplier)}}
        """
    }

    // MARK: - Helpers

    private func makePass(
        store: BrainKeyValueStore = InMemoryStore(),
        clock: BrainClock,
        entitlements: Entitlements = Entitlements(store: InMemoryStore()),
        eventSink: EventSink = FakeEventSink()
    ) -> BattlePass {
        let pass = BattlePass()
        pass.configure(.init(
            eventSink: eventSink,
            playerId: "p1",
            store: store,
            clock: clock,
            entitlements: entitlements
        ))
        return pass
    }

    private func makeConfigured(
        store: BrainKeyValueStore = InMemoryStore(),
        clock: BrainClock,
        entitlements: Entitlements = Entitlements(store: InMemoryStore()),
        eventSink: EventSink = FakeEventSink(),
        json: String? = nil
    ) -> BattlePass {
        let pass = makePass(store: store, clock: clock, entitlements: entitlements, eventSink: eventSink)
        _ = pass.apply(json ?? config)
        return pass
    }

    private func ownedEntitlements(_ productId: String? = nil) -> Entitlements {
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant(productId ?? premiumProductId)
        return entitlements
    }

    /// `claim` is `async`, and an `async` call cannot live inside an `XCTAssert*` autoclosure — so
    /// every claim assertion goes through this, which also surfaces the `report` on failure.
    @discardableResult
    private func expectClaim(
        _ pass: BattlePass,
        tier: Int,
        track: BattlePassTrack,
        claimed expected: Bool,
        file: StaticString = #filePath,
        line: UInt = #line
    ) async -> BattlePassClaimResult {
        let result = await pass.claim(tier: tier, track: track)
        XCTAssertEqual(
            result.claimed, expected,
            "expected claimed=\(expected); report was: \(result.report)",
            file: file, line: line
        )
        if !expected {
            XCTAssertTrue(result.rewards.isEmpty, "a rejected claim must grant nothing", file: file, line: line)
        }
        XCTAssertFalse(result.report.isEmpty, "every outcome carries a report", file: file, line: line)
        return result
    }

    override func setUp() {
        super.setUp()
        BattlePass.shared.resetForTest()
    }

    override func tearDown() {
        BattlePass.shared.resetForTest()
        super.tearDown()
    }

    // MARK: - Defaults

    func test_beforeAnyApplyThereIsNoSeasonAndVersionIsNotLoaded() {
        let pass = makePass(clock: FakeClock(startMs))
        XCTAssertEqual(pass.activeVersion(), "not loaded")
        XCTAssertEqual(pass.phase(), .noSeason)
        XCTAssertEqual(pass.xpTotal(), 0)
        XCTAssertEqual(pass.currentTier(), 0)
        XCTAssertFalse(pass.isPremiumActive())
        XCTAssertTrue(pass.claimable().isEmpty)
    }

    func test_beforeConfigureEveryEntryPointIsInertRatherThanCrashing() async {
        let pass = BattlePass()
        XCTAssertEqual(pass.phase(), .noSeason)
        let credited = await pass.addXp(100)
        XCTAssertEqual(credited, 0)
        let result = await pass.claim(tier: 1, track: .free)
        XCTAssertFalse(result.claimed)
        XCTAssertTrue(result.rewards.isEmpty)
        XCTAssertFalse(result.report.isEmpty)
    }

    func test_addXpWithNoSeasonAppliedCreditsNothing() async {
        let pass = makePass(clock: FakeClock(startMs))
        let credited = await pass.addXp(500)
        XCTAssertEqual(credited, 0)
        XCTAssertEqual(pass.xpTotal(), 0)
    }

    // MARK: - Config: structural rejections (§4)

    func test_applyNilOrBlankOrMalformedConfigIsRejected() {
        let pass = makePass(clock: FakeClock(startMs))
        XCTAssertFalse(pass.apply(nil).applied)
        XCTAssertFalse(pass.apply("   ").applied)
        XCTAssertFalse(pass.apply("{not valid json").applied)
        XCTAssertFalse(pass.apply("[1, 2, 3]").applied)
        XCTAssertEqual(pass.activeVersion(), "not loaded")
    }

    func test_rejectedConfigKeepsTheLastGoodOneRunningAlongWithItsProgress() async {
        let pass = makeConfigured(clock: FakeClock(startMs), json: config)
        _ = await pass.addXp(150)
        let result = pass.apply("{not valid json")
        XCTAssertFalse(result.applied)
        XCTAssertTrue(result.report.contains("bp_v1"), "report was: \(result.report)")
        XCTAssertEqual(pass.activeVersion(), "bp_v1")
        XCTAssertEqual(pass.xpTotal(), 150, "a rejected config must not touch progress")
        XCTAssertEqual(pass.currentTier(), 2)
    }

    func test_missingOrBlankVersionIsRejected() {
        let pass = makePass(clock: FakeClock(startMs))
        XCTAssertFalse(pass.apply("""
        {"season_id": "s", "start_ts_ms": 1, "end_ts_ms": 2, "tiers": [{"tier": 1, "xp_required": 0}]}
        """).applied)
        XCTAssertFalse(pass.apply("""
        {"version": "  ", "season_id": "s", "start_ts_ms": 1, "end_ts_ms": 2, "tiers": [{"tier": 1, "xp_required": 0}]}
        """).applied)
    }

    func test_missingOrBlankSeasonIdIsRejected() {
        let pass = makePass(clock: FakeClock(startMs))
        let missing = pass.apply("""
        {"version": "bp_v1", "start_ts_ms": 1, "end_ts_ms": 2, "tiers": [{"tier": 1, "xp_required": 0}]}
        """)
        XCTAssertFalse(missing.applied)
        XCTAssertTrue(missing.report.contains("season_id"), "report was: \(missing.report)")
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "   ", "start_ts_ms": 1, "end_ts_ms": 2, "tiers": [{"tier": 1, "xp_required": 0}]}
        """).applied)
    }

    /// §3.2 — the latent Android bug. `season_id` is field 0 of the persisted record, so a pipe in
    /// it shifts every subsequent field on the way back in.
    func test_seasonIdContainingAPersistenceDelimiterIsRejected() {
        for hostile in ["summer|2026", "summer:2026", "summer\\n2026"] {
            let pass = makePass(clock: FakeClock(startMs))
            let result = pass.apply("""
            {"version": "bp_v1", "season_id": "\(hostile)", "start_ts_ms": 1700000000000,
             "end_ts_ms": 1700086400000, "tiers": [{"tier": 1, "xp_required": 0}]}
            """)
            XCTAssertFalse(result.applied, "'\(hostile)' must be rejected; report was: \(result.report)")
            XCTAssertTrue(result.report.contains("season_id"), "report was: \(result.report)")
            XCTAssertEqual(pass.activeVersion(), "not loaded")
        }
    }

    func test_missingOrNonNumericTimestampsAreRejected() {
        let pass = makePass(clock: FakeClock(startMs))
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "end_ts_ms": 2, "tiers": [{"tier": 1, "xp_required": 0}]}
        """).applied)
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": "soon", "end_ts_ms": 2, "tiers": [{"tier": 1, "xp_required": 0}]}
        """).applied)
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1, "tiers": [{"tier": 1, "xp_required": 0}]}
        """).applied)
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": true, "end_ts_ms": 2, "tiers": [{"tier": 1, "xp_required": 0}]}
        """).applied, "a JSON boolean bridges to 1 and must not become a timestamp")
    }

    func test_endAtOrBeforeStartIsRejected() {
        let pass = makePass(clock: FakeClock(startMs))
        for (start, end) in [(1_700_000_000_000, 1_700_000_000_000), (1_700_000_000_000, 1_600_000_000_000)] {
            let result = pass.apply("""
            {"version": "bp_v1", "season_id": "s", "start_ts_ms": \(start), "end_ts_ms": \(end),
             "tiers": [{"tier": 1, "xp_required": 0}]}
            """)
            XCTAssertFalse(result.applied, "report was: \(result.report)")
        }
    }

    func test_missingOrEmptyTiersAreRejected() {
        let pass = makePass(clock: FakeClock(startMs))
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1, "end_ts_ms": 2}
        """).applied)
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1, "end_ts_ms": 2, "tiers": []}
        """).applied)
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1, "end_ts_ms": 2, "tiers": "many"}
        """).applied)
    }

    func test_aTierMissingTierOrXpRequiredRejectsTheWholeConfig() {
        let pass = makePass(clock: FakeClock(startMs))
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1, "end_ts_ms": 2,
         "tiers": [{"tier": 1, "xp_required": 0}, {"xp_required": 100}]}
        """).applied)
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1, "end_ts_ms": 2,
         "tiers": [{"tier": 1, "xp_required": 0}, {"tier": 2}]}
        """).applied)
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1, "end_ts_ms": 2, "tiers": [17]}
        """).applied)
    }

    func test_tiersNotStrictlyIncreasingInBothDimensionsAreRejected() {
        let pass = makePass(clock: FakeClock(startMs))
        // tier numbers go backwards
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1, "end_ts_ms": 2,
         "tiers": [{"tier": 2, "xp_required": 0}, {"tier": 1, "xp_required": 100}]}
        """).applied)
        // xp thresholds do not increase
        XCTAssertFalse(pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1, "end_ts_ms": 2,
         "tiers": [{"tier": 1, "xp_required": 100}, {"tier": 2, "xp_required": 100}]}
        """).applied)
    }

    /// §3.6 — deliberately NOT the house degrade-with-a-note rule, because the default here is
    /// "no cap at all", so degrading would fail OPEN.
    func test_xpCapPresentButInvalidRejectsTheWholeConfigWhileAbsentMeansUncapped() async {
        let pass = makePass(clock: FakeClock(startMs))
        for bad in ["0", "-5", "\"lots\"", "true", "null"] {
            let result = pass.apply("""
            {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
             "xp_cap": \(bad), "tiers": [{"tier": 1, "xp_required": 0}]}
            """)
            XCTAssertFalse(result.applied, "xp_cap \(bad) must reject the whole config; report was: \(result.report)")
        }
        let uncapped = pass.apply("""
        {"version": "bp_v1", "season_id": "s", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "tiers": [{"tier": 1, "xp_required": 0}]}
        """)
        XCTAssertTrue(uncapped.applied)
        let credited = await pass.addXp(9_000_000_000)
        XCTAssertEqual(credited, 9_000_000_000, "an absent xp_cap means uncapped, not zero")
    }

    func test_catchUpOutOfRangeFieldsRejectTheWholeConfig() {
        let pass = makePass(clock: FakeClock(startMs))
        for fraction in ["0", "1", "1.5", "-0.2", "\"half\""] {
            XCTAssertFalse(pass.apply(catchUpConfig(startFraction: fraction)).applied,
                           "start_fraction \(fraction) must reject")
        }
        for multiplier in ["0.9", "0", "-1"] {
            XCTAssertFalse(pass.apply(catchUpConfig(multiplier: multiplier)).applied,
                           "multiplier \(multiplier) must reject")
        }
        XCTAssertTrue(pass.apply(catchUpConfig()).applied)
    }

    // MARK: - Config: degrade with a note (§4)

    func test_aMalformedRewardIsDroppedWithANoteWhileTheRestOfTheTierSurvives() async {
        let pass = makeConfigured(clock: FakeClock(startMs), json: """
        {"version": "bp_v1", "season_id": "season_3", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "tiers": [{"tier": 1, "xp_required": 0, "rewards_free": [
           {"id": "", "type": "gem", "amount": 10},
           {"type": "gem", "amount": 10},
           "not-an-object",
           {"id": "good", "type": "gem", "amount": 7}
         ]}]}
        """)
        let result = await expectClaim(pass, tier: 1, track: .free, claimed: true)
        XCTAssertEqual(result.rewards, [BattlePassReward(id: "good", type: "gem", amount: 7)])
    }

    func test_applyReportCarriesANoteForEveryDegradedField() {
        let pass = makePass(clock: FakeClock(startMs))
        let result = pass.apply("""
        {"version": "bp_v1", "season_id": "season_3", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "premium_product_id": "  ",
         "tiers": [{"tier": 1, "xp_required": 0, "rewards_free": [{"id": "", "amount": 1}]}]}
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("\u{26A0}"), "report was: \(result.report)")
        XCTAssertTrue(result.report.contains("premium_product_id"), "report was: \(result.report)")
        XCTAssertTrue(result.report.contains("id"), "report was: \(result.report)")
    }

    func test_absentRewardListsAndFieldsTakeTheirDocumentedDefaults() async {
        let pass = makeConfigured(clock: FakeClock(startMs), json: """
        {"version": "bp_v1", "season_id": "season_3", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "tiers": [{"tier": 1, "xp_required": 0, "rewards_free": [{"id": "bare"}]}]}
        """)
        let free = await expectClaim(pass, tier: 1, track: .free, claimed: true)
        XCTAssertEqual(free.rewards, [BattlePassReward(id: "bare", type: "", amount: 0)],
                       "absent 'type' -> \"\", absent 'amount' -> 0")
        // 'rewards_premium' absent -> empty, and with no premium_product_id the track is unavailable.
        await expectClaim(pass, tier: 1, track: .premium, claimed: false)
    }

    func test_negativeRewardAmountIsDroppedWithANote() async {
        let pass = makePass(clock: FakeClock(startMs))
        let result = pass.apply("""
        {"version": "bp_v1", "season_id": "season_3", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "tiers": [{"tier": 1, "xp_required": 0, "rewards_free": [
           {"id": "neg", "type": "gem", "amount": -5}, {"id": "ok", "type": "gem", "amount": 0}]}]}
        """)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("neg"), "report was: \(result.report)")
        let claim = await expectClaim(pass, tier: 1, track: .free, claimed: true)
        XCTAssertEqual(claim.rewards.map(\.id), ["ok"], "amount 0 is legal; a negative amount is dropped")
    }

    func test_blankOrAbsentPremiumProductIdMeansNoPremiumTrack() async {
        let entitlements = ownedEntitlements()
        let pass = makeConfigured(clock: FakeClock(startMs), entitlements: entitlements, json: """
        {"version": "bp_v1", "season_id": "season_3", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "tiers": [{"tier": 1, "xp_required": 0, "rewards_premium": [{"id": "r2", "type": "skin", "amount": 1}]}]}
        """)
        XCTAssertFalse(pass.isPremiumActive())
        XCTAssertEqual(pass.claimable().map(\.track), [.free], "no premium product id -> no premium track at all")
        await expectClaim(pass, tier: 1, track: .premium, claimed: false)
    }

    func test_catchUpEnabledWithAWrongTypeDegradesToDisabledWithANote() async {
        let clock = FakeClock(midMs)
        let pass = makePass(clock: clock)
        let result = pass.apply(catchUpConfig(enabled: "1"))
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("enabled"), "report was: \(result.report)")
        let credited = await pass.addXp(10)
        XCTAssertEqual(credited, 10, "a catch-up block whose 'enabled' is not a boolean must not multiply")
    }

    // MARK: - Phase (§7)

    func test_phaseFollowsTheSeasonWindowAndIsInclusiveAtBothEnds() {
        let clock = FakeClock(startMs - 1)
        let pass = makeConfigured(clock: clock)
        XCTAssertEqual(pass.phase(), .notStarted)
        clock.set(startMs)
        XCTAssertEqual(pass.phase(), .active, "exactly at start is active")
        clock.set(endMs)
        XCTAssertEqual(pass.phase(), .active, "exactly at end is still active")
        clock.set(endMs + 1)
        XCTAssertEqual(pass.phase(), .ended)
    }

    func test_addXpCreditsNothingOutsideTheSeasonWindow() async {
        let clock = FakeClock(startMs - 1)
        let pass = makeConfigured(clock: clock)
        var credited = await pass.addXp(100)
        XCTAssertEqual(credited, 0, "before start")
        clock.set(endMs + 1)
        credited = await pass.addXp(100)
        XCTAssertEqual(credited, 0, "after end")
        XCTAssertEqual(pass.xpTotal(), 0)
    }

    func test_addXpStillCreditsAtExactlyTheEndTimestamp() async {
        let pass = makeConfigured(clock: FakeClock(endMs))
        let credited = await pass.addXp(100)
        XCTAssertEqual(credited, 100)
        XCTAssertEqual(pass.currentTier(), 2)
    }

    /// §3.5 — preserved deliberately: no grace window. A player who earns a tier and does not tap
    /// claim before `endMs` loses it permanently.
    func test_claimingIsPossibleAtExactlyTheEndAndImpossibleOneMillisecondLater() async {
        let clock = FakeClock(startMs)
        let pass = makeConfigured(clock: clock)
        _ = await pass.addXp(250)
        clock.set(endMs)
        XCTAssertFalse(pass.claimable().isEmpty)
        await expectClaim(pass, tier: 1, track: .free, claimed: true)
        clock.set(endMs + 1)
        XCTAssertEqual(pass.phase(), .ended)
        XCTAssertTrue(pass.claimable().isEmpty, "nothing is claimable once the season has ended")
        await expectClaim(pass, tier: 2, track: .free, claimed: false)
    }

    func test_earnedButUnclaimedProgressSurvivesInStorageYetStaysUnreachableAfterTheSeasonEnds() async {
        let clock = FakeClock(startMs)
        let store = InMemoryStore()
        let pass = makeConfigured(store: store, clock: clock)
        _ = await pass.addXp(250)
        clock.set(endMs + 1)
        XCTAssertEqual(pass.xpTotal(), 250, "the XP is still there…")
        XCTAssertEqual(pass.currentTier(), 3)
        XCTAssertTrue(pass.claimable().isEmpty, "…and permanently unreachable (§3.5)")
    }

    // MARK: - Tier arithmetic (§7)

    func test_theZeroXpRequiredTierIsReachedWithNoXpAtAll() {
        let pass = makeConfigured(clock: FakeClock(startMs))
        XCTAssertEqual(pass.xpTotal(), 0)
        XCTAssertEqual(pass.currentTier(), 1)
    }

    func test_tierIsZeroWhenXpIsBelowTheFirstThreshold() async {
        let pass = makeConfigured(clock: FakeClock(startMs), json: """
        {"version": "bp_v1", "season_id": "season_3", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "tiers": [{"tier": 1, "xp_required": 100}, {"tier": 2, "xp_required": 200}]}
        """)
        XCTAssertEqual(pass.currentTier(), 0, "0 means 'below the first tier', not 'tier 0'")
        _ = await pass.addXp(99)
        XCTAssertEqual(pass.currentTier(), 0)
        _ = await pass.addXp(1)
        XCTAssertEqual(pass.currentTier(), 1)
    }

    func test_tierIsAPureFunctionOfXpAcrossEveryThreshold() async {
        let pass = makeConfigured(clock: FakeClock(startMs))
        XCTAssertEqual(pass.currentTier(), 1)
        _ = await pass.addXp(99)
        XCTAssertEqual(pass.currentTier(), 1)
        _ = await pass.addXp(1)
        XCTAssertEqual(pass.currentTier(), 2, "exactly at the threshold counts as reached")
        _ = await pass.addXp(149)
        XCTAssertEqual(pass.currentTier(), 2)
        _ = await pass.addXp(1)
        XCTAssertEqual(pass.currentTier(), 3)
        _ = await pass.addXp(10_000)
        XCTAssertEqual(pass.currentTier(), 3, "the last tier is the ceiling")
    }

    func test_tierNeverGoesBackwardsOnceTheCapIsReached() async {
        let pass = makeConfigured(clock: FakeClock(startMs))
        _ = await pass.addXp(1000)
        XCTAssertEqual(pass.currentTier(), 3)
        for _ in 0..<5 {
            let credited = await pass.addXp(500)
            XCTAssertEqual(credited, 0)
            XCTAssertEqual(pass.currentTier(), 3)
        }
    }

    // MARK: - XP cap (§7)

    func test_creditedXpTruncatesAtTheCapAndFurtherAddXpCreditsZero() async {
        let pass = makeConfigured(clock: FakeClock(startMs))
        var credited = await pass.addXp(900)
        XCTAssertEqual(credited, 900)
        credited = await pass.addXp(500)
        XCTAssertEqual(credited, 100, "only the headroom to the cap is credited")
        XCTAssertEqual(pass.xpTotal(), 1000)
        credited = await pass.addXp(1)
        XCTAssertEqual(credited, 0)
        XCTAssertEqual(pass.xpTotal(), 1000)
    }

    func test_nonPositiveAddXpCreditsNothing() async {
        let pass = makeConfigured(clock: FakeClock(startMs))
        let zero = await pass.addXp(0)
        let negative = await pass.addXp(-50)
        XCTAssertEqual(zero, 0)
        XCTAssertEqual(negative, 0)
        XCTAssertEqual(pass.xpTotal(), 0, "XP must never go down")
    }

    // MARK: - Catch-up (§7)

    func test_catchUpIsInactiveBeforeTheStartFractionAndActiveFromItOnwards() async {
        let clock = FakeClock(midMs - 1)
        let pass = makePass(clock: clock)
        _ = pass.apply(catchUpConfig(multiplier: "2.0"))
        var credited = await pass.addXp(10)
        XCTAssertEqual(credited, 10, "one millisecond before the half-way mark, no multiplier")
        clock.set(midMs)
        credited = await pass.addXp(10)
        XCTAssertEqual(credited, 20, "the start fraction is INCLUSIVE")
        clock.set(midMs + 1)
        credited = await pass.addXp(10)
        XCTAssertEqual(credited, 20)
    }

    /// §3.7 — Android only ever tests round multiples, so its truncation is untested there.
    func test_theCatchUpMultiplierTruncatesTowardZero() async {
        let pass = makePass(clock: FakeClock(midMs))
        _ = pass.apply(catchUpConfig(multiplier: "1.5"))
        let credited = await pass.addXp(3)
        XCTAssertEqual(credited, 4, "3 x 1.5 = 4.5 -> 4, not 5")
    }

    /// The test the catch-up change was missing. Activation compares `elapsed` against an absolute
    /// threshold `Int64(span x fraction)` rather than forming a ratio `elapsed/span >= fraction` —
    /// but every other catch-up test uses `0.5` against an even 24h span, where BOTH formulas agree
    /// exactly, so none of them could tell the two apart. A correctness claim with no test able to
    /// falsify it is not yet a verified claim.
    ///
    /// This picks a case where they genuinely disagree: span 1,000,001ms at fraction 0.3 gives
    /// threshold = Int64(300000.3) = 300000, so at exactly 300000ms elapsed the threshold form says
    /// ACTIVE while the ratio form computes 0.2999997 >= 0.3 and says inactive. Reverting to the
    /// ratio makes this test fail; no other test in the file notices.
    func test_catchUpActivationUsesAnAbsoluteThresholdNotARatio() async {
        let oddSpanStart: Int64 = 1_700_000_000_000
        let oddSpanEnd: Int64 = oddSpanStart + 1_000_001
        let thresholdMs: Int64 = oddSpanStart + 300_000   // Int64(1_000_001 * 0.3)
        let oddConfig = """
        {"version": "bp_odd", "season_id": "season_odd",
         "start_ts_ms": \(oddSpanStart), "end_ts_ms": \(oddSpanEnd),
         "xp_cap": 100000,
         "tiers": [{"tier": 1, "xp_required": 0, "rewards_free": [], "rewards_premium": []}],
         "catch_up": {"enabled": true, "start_fraction": 0.3, "multiplier": 2.0}}
        """

        let atThreshold = makePass(clock: FakeClock(thresholdMs))
        XCTAssertTrue(atThreshold.apply(oddConfig).applied)
        let creditedAt = await atThreshold.addXp(10)
        XCTAssertEqual(creditedAt, 20,
                       "exactly at Int64(span x 0.3) catch-up must be ACTIVE; the ratio form computes 0.2999997 and would say inactive")

        let justBefore = makePass(clock: FakeClock(thresholdMs - 1))
        XCTAssertTrue(justBefore.apply(oddConfig).applied)
        let creditedBefore = await justBefore.addXp(10)
        XCTAssertEqual(creditedBefore, 10, "one millisecond earlier it must still be inactive")
    }

    /// "Exactly one event per tier-up" was only ever demonstrated for a single-step crossing. A
    /// regression emitting one event per tier crossed, or computing `to_tier` as `fromTier + 1`
    /// rather than the real landing tier, would pass every other test in this file.
    func test_oneAddXpCrossingTwoTiersEmitsOneEventSpanningBoth() async {
        let sink = FakeEventSink()
        let pass = makePass(clock: FakeClock(startMs), eventSink: sink)
        XCTAssertTrue(pass.apply(config).applied)

        _ = await pass.addXp(250)   // 0 -> 250 crosses tier 2 (100) AND tier 3 (250)

        XCTAssertEqual(pass.currentTier(), 3)
        let events = await sink.sentEvents.filter { $0.eventName == "battle_pass_tier" }
        XCTAssertEqual(events.count, 1, "one call must emit one event, however many tiers it spans")
        XCTAssertEqual(events.first?.eventData["from_tier"], .int(1))
        XCTAssertEqual(events.first?.eventData["to_tier"], .int(3),
                       "to_tier must be the tier actually landed on, not fromTier + 1")
    }

    /// The JSON-boolean guard on `xp_required`/`amount` is tested for the timestamp fields but not
    /// for these, though `1 as? Bool` and `true as? Int` both succeed under JSONSerialization.
    func test_aJsonBooleanIsNotAcceptedAsATierNumberOrRewardAmount() async {
        let pass = makePass(clock: FakeClock(startMs))
        let boolXpRequired = """
        {"version": "bp_b", "season_id": "season_b",
         "start_ts_ms": \(startMs), "end_ts_ms": \(endMs),
         "tiers": [{"tier": 1, "xp_required": true, "rewards_free": [], "rewards_premium": []}]}
        """
        XCTAssertFalse(pass.apply(boolXpRequired).applied, "a JSON boolean is not an XP threshold")

        let boolAmount = """
        {"version": "bp_c", "season_id": "season_c",
         "start_ts_ms": \(startMs), "end_ts_ms": \(endMs),
         "tiers": [{"tier": 1, "xp_required": 0,
                    "rewards_free": [{"id": "r1", "type": "gem", "amount": true}],
                    "rewards_premium": []}]}
        """
        XCTAssertTrue(pass.apply(boolAmount).applied, "a bad reward drops its line, not the config")
        XCTAssertTrue(pass.claimable().allSatisfy { $0.rewards.isEmpty },
                      "the reward with a boolean amount must have been dropped")
    }

    /// Per-season premium ids are the ONLY thing that stops last season's purchase working this
    /// season — there is no revocation code. Nothing enforced that, so copying an id forward when
    /// cloning a config silently gave every prior buyer the new season's premium, free and forever.
    /// A note rather than a rejection: reuse is unlikely but not incoherent.
    func test_aNewSeasonReusingThePreviousPremiumProductIdIsFlagged() async {
        let pass = makePass(clock: FakeClock(startMs))
        XCTAssertTrue(pass.apply(config).applied)

        let clonedWithSameProduct = config
            .replacingOccurrences(of: "\"season_id\": \"season_3\"", with: "\"season_id\": \"season_4\"")
        let result = pass.apply(clonedWithSameProduct)
        XCTAssertTrue(result.applied, "reuse is flagged, never rejected")
        XCTAssertTrue(result.report.contains("reuses the previous season's 'premium_product_id'"),
                      "report was: \(result.report)")
    }

    func test_aNewSeasonWithItsOwnPremiumProductIdIsNotFlagged() async {
        let pass = makePass(clock: FakeClock(startMs))
        XCTAssertTrue(pass.apply(config).applied)

        let properlyRotated = config
            .replacingOccurrences(of: "\"season_id\": \"season_3\"", with: "\"season_id\": \"season_4\"")
            .replacingOccurrences(of: "bp_season_3_premium", with: "bp_season_4_premium")
        let result = pass.apply(properlyRotated)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.contains("reuses the previous season's"),
                       "a correctly rotated id must not warn: \(result.report)")
    }

    func test_catchUpDisabledAppliesNoMultiplierEvenPastTheStartFraction() async {
        let pass = makePass(clock: FakeClock(endMs))
        _ = pass.apply(catchUpConfig(enabled: "false", multiplier: "3.0"))
        let credited = await pass.addXp(10)
        XCTAssertEqual(credited, 10)
    }

    func test_aSeasonWithNoCatchUpBlockNeverMultiplies() async {
        let pass = makeConfigured(clock: FakeClock(endMs))
        let credited = await pass.addXp(10)
        XCTAssertEqual(credited, 10)
    }

    // MARK: - Overflow (§3.7) — must clamp, never trap

    func test_aHugeAmountClampsRatherThanTrapping() async {
        let pass = makeConfigured(clock: FakeClock(startMs), json: """
        {"version": "bp_v1", "season_id": "season_3", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "tiers": [{"tier": 1, "xp_required": 0}]}
        """)
        let credited = await pass.addXp(Int64.max)
        XCTAssertGreaterThan(credited, 0)
        XCTAssertLessThanOrEqual(pass.xpTotal(), Int64.max / 2)
    }

    func test_aHugeCatchUpMultiplierClampsRatherThanTrapping() async {
        let pass = makePass(clock: FakeClock(endMs))
        XCTAssertTrue(pass.apply(catchUpConfig(multiplier: "1e300")).applied)
        let credited = await pass.addXp(Int64.max / 4)
        XCTAssertGreaterThan(credited, 0)
        XCTAssertLessThanOrEqual(pass.xpTotal(), Int64.max / 2)
    }

    /// Only decodes as a `Double` (`as? Int` returns `nil`), which is exactly the input shape that
    /// made a clamp-after-convert a Critical finding in three earlier phases.
    func test_doubleOnlyNumericsInConfigClampRatherThanTrapping() async {
        let pass = makePass(clock: FakeClock(startMs))
        let result = pass.apply("""
        {"version": "bp_v1", "season_id": "season_3", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "xp_cap": 1e300,
         "tiers": [{"tier": 1, "xp_required": 0}, {"tier": 99999999999999999999, "xp_required": 1e300}]}
        """)
        XCTAssertTrue(result.applied, "report was: \(result.report)")
        let credited = await pass.addXp(1_000_000)
        XCTAssertEqual(credited, 1_000_000)
        XCTAssertEqual(pass.currentTier(), 1, "the absurd second tier clamps; it does not trap")
    }

    func test_anXpValueNearInt64MaxInStorageClampsRatherThanTrapping() async {
        let store = InMemoryStore()
        store.setString("season_3|\(Int64.max)|", forKey: BattlePass.stateKey)
        let pass = makeConfigured(store: store, clock: FakeClock(startMs), json: """
        {"version": "bp_v1", "season_id": "season_3", "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
         "tiers": [{"tier": 1, "xp_required": 0}]}
        """)
        XCTAssertLessThanOrEqual(pass.xpTotal(), Int64.max / 2, "a hostile stored XP is clamped on load")
        let credited = await pass.addXp(Int64.max)
        XCTAssertEqual(credited, 0, "there is no headroom left, and the addition must not overflow")
        XCTAssertLessThanOrEqual(pass.xpTotal(), Int64.max / 2)
    }

    // MARK: - Claiming (§7)

    func test_claimingATierThatHasNotBeenReachedIsRejected() async {
        let pass = makeConfigured(clock: FakeClock(startMs))
        await expectClaim(pass, tier: 2, track: .free, claimed: false)
        _ = await pass.addXp(100)
        await expectClaim(pass, tier: 2, track: .free, claimed: true)
    }

    func test_claimingATierThatIsNotInTheConfigIsRejected() async {
        let pass = makeConfigured(clock: FakeClock(startMs))
        await expectClaim(pass, tier: 99, track: .free, claimed: false)
        await expectClaim(pass, tier: 0, track: .free, claimed: false)
    }

    func test_aSecondClaimOfTheSameTierAndTrackIsRejected() async {
        let pass = makeConfigured(clock: FakeClock(startMs))
        let first = await expectClaim(pass, tier: 1, track: .free, claimed: true)
        XCTAssertEqual(first.rewards.map(\.id), ["r1"])
        await expectClaim(pass, tier: 1, track: .free, claimed: false)
    }

    func test_theFreeAndPremiumTracksOfOneTierAreIndependent() async {
        let pass = makeConfigured(clock: FakeClock(startMs), entitlements: ownedEntitlements())
        await expectClaim(pass, tier: 1, track: .free, claimed: true)
        let premium = await expectClaim(pass, tier: 1, track: .premium, claimed: true)
        XCTAssertEqual(premium.rewards.map(\.id), ["r2"])
    }

    func test_thePremiumTrackRequiresOwnershipOfTheSeasonsProduct() async {
        let entitlements = Entitlements(store: InMemoryStore())
        let pass = makeConfigured(clock: FakeClock(startMs), entitlements: entitlements)
        await expectClaim(pass, tier: 1, track: .premium, claimed: false)
        entitlements.grant(premiumProductId)
        await expectClaim(pass, tier: 1, track: .premium, claimed: true)
    }

    func test_buyingPremiumLateUnlocksThePremiumTrackOfAlreadyPassedTiers() async {
        let entitlements = Entitlements(store: InMemoryStore())
        let pass = makeConfigured(clock: FakeClock(startMs), entitlements: entitlements)
        _ = await pass.addXp(250)
        XCTAssertEqual(pass.claimable().filter { $0.track == .premium }.count, 0)
        entitlements.grant(premiumProductId)
        let premiumTiers = pass.claimable().filter { $0.track == .premium }.map(\.tier)
        XCTAssertEqual(premiumTiers, [1, 2, 3], "every already-passed tier's premium track opens at once")
        await expectClaim(pass, tier: 3, track: .premium, claimed: true)
    }

    func test_claimableListsEveryUnclaimedReachedTierAndTrackInOrder() async {
        let pass = makeConfigured(clock: FakeClock(startMs), entitlements: ownedEntitlements())
        _ = await pass.addXp(100)
        XCTAssertEqual(
            pass.claimable(),
            [
                BattlePassClaimable(tier: 1, track: .free, rewards: [BattlePassReward(id: "r1", type: "gem", amount: 10)]),
                BattlePassClaimable(tier: 1, track: .premium, rewards: [BattlePassReward(id: "r2", type: "skin", amount: 1)]),
                BattlePassClaimable(tier: 2, track: .free, rewards: [BattlePassReward(id: "r3", type: "gem", amount: 20)]),
                BattlePassClaimable(tier: 2, track: .premium, rewards: [])
            ]
        )
        await expectClaim(pass, tier: 1, track: .free, claimed: true)
        XCTAssertFalse(pass.claimable().contains { $0.tier == 1 && $0.track == .free },
                       "a claimed pair drops out of the list")
    }

    func test_claimingATierWithNoRewardsOnThatTrackStillSucceedsAndConsumesThePair() async {
        let pass = makeConfigured(clock: FakeClock(startMs), entitlements: ownedEntitlements())
        _ = await pass.addXp(100)
        let first = await expectClaim(pass, tier: 2, track: .premium, claimed: true)
        XCTAssertTrue(first.rewards.isEmpty)
        await expectClaim(pass, tier: 2, track: .premium, claimed: false)
    }

    // MARK: - Premium ownership (§3.1)

    func test_isPremiumActiveFollowsTheCurrentSeasonsProductIdOnly() async {
        let entitlements = Entitlements(store: InMemoryStore())
        entitlements.grant("bp_season_3_premium")
        let pass = makeConfigured(clock: FakeClock(startMs), entitlements: entitlements)
        XCTAssertTrue(pass.isPremiumActive())

        // Season 4 names a DIFFERENT product id, so last season's purchase does not carry forward —
        // no revocation code exists, and none is needed (§3.1).
        _ = pass.apply(nextSeasonConfig)
        XCTAssertFalse(pass.isPremiumActive())
        await expectClaim(pass, tier: 1, track: .premium, claimed: false)
    }

    // MARK: - Season rollover (§7)

    func test_aDifferentSeasonIdResetsXpAndClaims() async {
        let pass = makeConfigured(clock: FakeClock(startMs))
        _ = await pass.addXp(250)
        await expectClaim(pass, tier: 1, track: .free, claimed: true)
        XCTAssertEqual(pass.xpTotal(), 250)

        XCTAssertTrue(pass.apply(nextSeasonConfig).applied)
        XCTAssertEqual(pass.xpTotal(), 0, "a new season resets progress")
        XCTAssertEqual(pass.currentTier(), 1)
        await expectClaim(pass, tier: 1, track: .free, claimed: true)
    }

    func test_theSameSeasonIdReAppliedLeavesProgressUntouched() async {
        let pass = makeConfigured(clock: FakeClock(startMs))
        _ = await pass.addXp(250)
        await expectClaim(pass, tier: 1, track: .free, claimed: true)

        let reapplied = pass.apply(config)
        XCTAssertTrue(reapplied.applied)
        XCTAssertEqual(pass.xpTotal(), 250, "the same season re-applied must not wipe progress")
        await expectClaim(pass, tier: 1, track: .free, claimed: false)
    }

    func test_aSeasonRolloverIsPersistedNotJustHeldInMemory() async {
        let store = InMemoryStore()
        let clock = FakeClock(startMs)
        let pass = makeConfigured(store: store, clock: clock)
        _ = await pass.addXp(250)
        _ = pass.apply(nextSeasonConfig)

        let restarted = makePass(store: store, clock: clock)
        _ = restarted.apply(nextSeasonConfig)
        XCTAssertEqual(restarted.xpTotal(), 0, "the reset must have reached storage")
    }

    // MARK: - Persistence (§3.2, §4) — THE round trip

    func test_progressRoundTripsAcrossAFreshInstanceOverTheSameStore() async {
        let store = InMemoryStore()
        let clock = FakeClock(startMs)
        let entitlements = ownedEntitlements()
        let pass = makeConfigured(store: store, clock: clock, entitlements: entitlements)
        _ = await pass.addXp(250)
        await expectClaim(pass, tier: 1, track: .free, claimed: true)
        await expectClaim(pass, tier: 2, track: .premium, claimed: true)

        // A process restart: a brand new instance reading the same store.
        let restarted = makePass(store: store, clock: clock, entitlements: entitlements)
        _ = restarted.apply(config)
        XCTAssertEqual(restarted.xpTotal(), 250, "XP must survive the round trip")
        XCTAssertEqual(restarted.currentTier(), 3)
        await expectClaim(restarted, tier: 1, track: .free, claimed: false)
        await expectClaim(restarted, tier: 2, track: .premium, claimed: false)
        await expectClaim(restarted, tier: 1, track: .premium, claimed: true)
    }

    /// §3.2, mandatory: a hostile `season_id` is rejected at parse time, so it can NEVER reach
    /// storage and shift every subsequent field on the way back in.
    func test_aHostileSeasonIdNeverReachesStorageAndLeavesEarnedProgressIntact() async {
        let store = InMemoryStore()
        let clock = FakeClock(startMs)
        let pass = makeConfigured(store: store, clock: clock)
        _ = await pass.addXp(250)
        await expectClaim(pass, tier: 1, track: .free, claimed: true)

        let hostile = pass.apply("""
        {"version": "bp_v9", "season_id": "summer|2026", "start_ts_ms": 1700000000000,
         "end_ts_ms": 1700086400000, "xp_cap": 1000,
         "tiers": [{"tier": 1, "xp_required": 0}, {"tier": 2, "xp_required": 100}, {"tier": 3, "xp_required": 250}]}
        """)
        XCTAssertFalse(hostile.applied, "report was: \(hostile.report)")
        XCTAssertEqual(pass.activeVersion(), "bp_v1")
        XCTAssertEqual(pass.xpTotal(), 250)

        let raw = store.peek(BattlePass.stateKey) ?? ""
        XCTAssertFalse(raw.contains("summer"), "the hostile id must never be written; raw was: \(raw)")
        XCTAssertTrue(raw.hasPrefix("season_3|250|"), "raw was: \(raw)")

        // And the record still round-trips: this is the assertion the Android suite never makes.
        let restarted = makePass(store: store, clock: clock)
        _ = restarted.apply(config)
        XCTAssertEqual(restarted.xpTotal(), 250)
        await expectClaim(restarted, tier: 1, track: .free, claimed: false)
    }

    func test_thePersistedRecordIsSeasonIdThenXpThenClaimKeys() async {
        let store = InMemoryStore()
        let pass = makeConfigured(store: store, clock: FakeClock(startMs), entitlements: ownedEntitlements())
        _ = await pass.addXp(150)
        await expectClaim(pass, tier: 1, track: .free, claimed: true)
        await expectClaim(pass, tier: 1, track: .premium, claimed: true)
        let raw = store.peek(BattlePass.stateKey) ?? ""
        XCTAssertEqual(raw, "season_3|150|1:free,1:premium", "raw was: \(raw)")
    }

    func test_anUnreadableRecordDropsToEmptyProgressWithADiagnostic() {
        for corrupt in ["garbage-with-no-delimiters", "season_3|notanumber|1:free", "|500|"] {
            let store = InMemoryStore()
            store.setString(corrupt, forKey: BattlePass.stateKey)
            let pass = makePass(store: store, clock: FakeClock(startMs))
            let result = pass.apply(config)
            XCTAssertTrue(result.applied)
            XCTAssertEqual(pass.xpTotal(), 0)
            XCTAssertTrue(result.report.contains("unreadable"),
                          "'\(corrupt)' must be diagnosed, not silently discarded; report was: \(result.report)")
        }
    }

    func test_noStoredRecordAtAllProducesNoCorruptionDiagnostic() {
        let pass = makePass(store: InMemoryStore(), clock: FakeClock(startMs))
        let result = pass.apply(config)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.contains("unreadable"),
                       "a first launch is not a corrupt record; report was: \(result.report)")
    }

    func test_unparseableClaimKeysAreSkippedWhileTheRestOfTheRecordSurvives() async {
        let store = InMemoryStore()
        store.setString("season_3|150|1:free,junk,2:nosuchtrack,:,2:free", forKey: BattlePass.stateKey)
        let pass = makeConfigured(store: store, clock: FakeClock(startMs))
        XCTAssertEqual(pass.xpTotal(), 150)
        await expectClaim(pass, tier: 1, track: .free, claimed: false)
        await expectClaim(pass, tier: 2, track: .free, claimed: false)
    }

    func test_resetForTestClearsBothMemoryAndStore() async {
        let store = InMemoryStore()
        let pass = makeConfigured(store: store, clock: FakeClock(startMs))
        _ = await pass.addXp(100)
        XCTAssertNotNil(store.peek(BattlePass.stateKey))
        pass.resetForTest()
        XCTAssertEqual(pass.activeVersion(), "not loaded")
        XCTAssertEqual(pass.phase(), .noSeason)
        XCTAssertEqual(pass.xpTotal(), 0)
        XCTAssertNil(store.peek(BattlePass.stateKey))
    }

    // MARK: - Events (§5)

    func test_crossingATierEmitsExactlyOneEventWithTheLockedWireShape() async {
        let sink = FakeEventSink()
        let pass = makeConfigured(clock: FakeClock(startMs), entitlements: ownedEntitlements(), eventSink: sink)
        _ = await pass.addXp(150)

        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 1)
        guard let event = events.first else { return }
        XCTAssertEqual(event.eventName, "battle_pass_tier")
        XCTAssertEqual(event.eventData["season_id"], .string(seasonId))
        XCTAssertEqual(event.eventData["from_tier"], .int(1))
        XCTAssertEqual(event.eventData["to_tier"], .int(2))
        XCTAssertEqual(event.eventData["is_premium"], .int(1))
        XCTAssertEqual(event.eventData["battle_pass_version"], .string("bp_v1"))
        XCTAssertNil(event.eventData["screen_name"], "dropped, as in Phases 7 and 8")
        XCTAssertEqual(event.userProperty.userId, "p1")
        XCTAssertEqual(event.sequenceId, 0, "sequenceId is EventQueue's to assign, never a call site's")
    }

    func test_addXpThatCrossesNoTierBoundaryEmitsNothing() async {
        let sink = FakeEventSink()
        let pass = makeConfigured(clock: FakeClock(startMs), eventSink: sink)
        _ = await pass.addXp(50)
        _ = await pass.addXp(10)
        let count = await sink.sentEvents.count
        XCTAssertEqual(count, 0, "an event is emitted only when toTier > fromTier")
    }

    func test_aSuccessfulClaimEmitsExactlyOneEventWithTheLockedWireShape() async {
        let sink = FakeEventSink()
        let pass = makeConfigured(clock: FakeClock(startMs), entitlements: ownedEntitlements(), eventSink: sink)
        await expectClaim(pass, tier: 1, track: .free, claimed: true)

        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 1)
        guard let event = events.first else { return }
        XCTAssertEqual(event.eventName, "battle_pass_claim")
        XCTAssertEqual(event.eventData["season_id"], .string(seasonId))
        XCTAssertEqual(event.eventData["tier"], .int(1))
        XCTAssertEqual(event.eventData["track"], .string("free"))
        XCTAssertEqual(event.eventData["reward_count"], .int(1))
        XCTAssertEqual(event.eventData["is_premium"], .int(1),
                       "is_premium is OWNERSHIP, not which track was claimed — both fields exist because they differ")
        XCTAssertEqual(event.eventData["battle_pass_version"], .string("bp_v1"))
        XCTAssertEqual(event.sequenceId, 0)
    }

    func test_aRejectedClaimEmitsNoEvent() async {
        let sink = FakeEventSink()
        let pass = makeConfigured(clock: FakeClock(startMs), eventSink: sink)
        _ = await pass.claim(tier: 2, track: .free)      // not reached
        _ = await pass.claim(tier: 99, track: .free)     // no such tier
        _ = await pass.claim(tier: 1, track: .premium)   // not owned
        _ = await pass.claim(tier: 1, track: .free)      // the only success
        _ = await pass.claim(tier: 1, track: .free)      // double claim
        try? await Task.sleep(nanoseconds: 100_000_000)
        let count = await sink.sentEvents.count
        XCTAssertEqual(count, 1, "only the one successful claim may emit")
    }

    /// Both mutating entry points `await` their own send rather than handing it to a detached
    /// `Task` — reading the sink with no wait at all is what holds that.
    func test_addXpAndClaimDeliverTheirEventsBeforeReturning() async {
        let sink = FakeEventSink()
        let pass = makeConfigured(clock: FakeClock(startMs), eventSink: sink)
        _ = await pass.addXp(150)
        var count = await sink.sentEvents.count
        XCTAssertEqual(count, 1)
        _ = await pass.claim(tier: 2, track: .free)
        count = await sink.sentEvents.count
        XCTAssertEqual(count, 2)
    }

    // MARK: - Query purity (§1)

    func test_pureQueriesNeverPersistOrEmit() async {
        let store = InMemoryStore()
        let sink = FakeEventSink()
        let pass = makeConfigured(store: store, clock: FakeClock(startMs), entitlements: ownedEntitlements(), eventSink: sink)
        _ = await pass.addXp(150)
        let writesAfterSetup = store.writes()
        let firstClaimable = pass.claimable()
        for _ in 0..<5 {
            XCTAssertEqual(pass.xpTotal(), 150)
            XCTAssertEqual(pass.currentTier(), 2)
            XCTAssertEqual(pass.phase(), .active)
            XCTAssertTrue(pass.isPremiumActive())
            XCTAssertEqual(pass.claimable(), firstClaimable)
        }
        XCTAssertEqual(store.writes(), writesAfterSetup, "a pure query must never persist")
        let count = await sink.sentEvents.count
        XCTAssertEqual(count, 1, "a pure query must never emit")
    }

    // MARK: - Concurrency (§3.4, §7)

    /// The engineered-contention version: proves authorization and consumption really are one
    /// atomic acquisition, rather than passing because the scheduler never overlapped anything.
    /// A plain task storm with no forced stall was measured (in Phase 8) to detect nothing.
    func test_concurrentClaimsOfOneTierGrantExactlyOnceUnderForcedContention() async {
        let pass = makeConfigured(clock: StallingClock(startMs, stallFirst: 8))
        let results = await withTaskGroup(of: Bool.self) { group -> [Bool] in
            for _ in 0..<8 { group.addTask { await pass.claim(tier: 1, track: .free).claimed } }
            var out: [Bool] = []
            for await claimed in group { out.append(claimed) }
            return out
        }
        XCTAssertEqual(results.filter { $0 }.count, 1,
                       "exactly one caller may consume a tier/track pair even when every caller queues on the lock")
    }

    /// §3.4 — Android's `apply` is unsynchronized, so a config swap and its progress reset can be
    /// observed half-applied. Under a `withLock` mutated to skip the lock this test does not fail
    /// an assertion so much as crash the test process, which is the honest shape of concurrent
    /// mutation of a `Set` and is still an unmissable red.
    func test_aConfigSwapCannotInterleaveWithClaimsOrAddXpUnderForcedContention() async {
        let store = InMemoryStore()
        let pass = makeConfigured(store: store, clock: StallingClock(startMs, stallFirst: 12),
                                  entitlements: ownedEntitlements())
        _ = await pass.addXp(250)
        let nextConfig = nextSeasonConfig

        await withTaskGroup(of: Void.self) { group in
            for tier in 1...3 {
                group.addTask { _ = await pass.claim(tier: tier, track: .free) }
                group.addTask { _ = await pass.claim(tier: tier, track: .premium) }
            }
            for _ in 0..<2 { group.addTask { _ = await pass.addXp(10) } }
            for _ in 0..<4 { group.addTask { _ = pass.apply(nextConfig) } }
            await group.waitForAll()
        }

        XCTAssertEqual(pass.activeVersion(), "bp_v2")
        // Season 4's OWN progress at most (the two 10-XP grants, if they landed after the swap) —
        // never season 3's 250 XP evaluated against season 4's thresholds, which is precisely the
        // torn state Android's unsynchronized `apply` allows.
        XCTAssertLessThanOrEqual(pass.xpTotal(), 20)
        XCTAssertEqual(pass.currentTier(), 1)
        let raw = store.peek(BattlePass.stateKey) ?? ""
        XCTAssertTrue(raw.hasPrefix("season_4|"), "the persisted record must name the season its progress belongs to; raw was: \(raw)")
    }

    // MARK: - Config identifier length ceiling (L1)

    /// A five-million-character `season_id` parsed, was held for the life of the process, went into
    /// the persistence record — and was then rewritten in full by every `addXp` and every `claim`,
    /// turning each reward tap into a multi-megabyte store write. Operator-controlled, so this is
    /// availability rather than a privilege boundary, but it is silent and the fix is a comparison.
    /// `season_id` is STRUCTURAL (§3.2), so an over-long one rejects the whole config.
    func test_anAbsurdlyLongSeasonIdIsRejectedAndNeverReachesStorage() async {
        let store = InMemoryStore()
        let pass = makeConfigured(store: store, clock: FakeClock(startMs))
        _ = await pass.addXp(250)
        let writesBefore = store.writes()

        let huge = String(repeating: "s", count: 5_000_000)
        let result = pass.apply("""
        {"version": "bp_v9", "season_id": "\(huge)", "start_ts_ms": 1700000000000,
         "end_ts_ms": 1700086400000, "tiers": [{"tier": 1, "xp_required": 0}]}
        """)

        XCTAssertFalse(result.applied, "report was: \(result.report.prefix(300))")
        XCTAssertTrue(result.report.contains("128-character limit"), "report was: \(result.report.prefix(300))")
        XCTAssertLessThan(result.report.count, 1_000, "the diagnostic must not reproduce the id")
        XCTAssertEqual(pass.activeVersion(), "bp_v1", "the running season keeps running")
        XCTAssertEqual(store.writes(), writesBefore, "a rejected config must not persist anything")
        XCTAssertEqual(store.peek(BattlePass.stateKey), "season_3|250|")
    }

    /// `premium_product_id` is NOT structural — a season with no premium track is legitimate — so
    /// an over-long one degrades with a note instead of rejecting the config.
    func test_anAbsurdlyLongPremiumProductIdDegradesToNoPremiumTrack() async {
        let huge = String(repeating: "p", count: 5_000)
        let pass = makeConfigured(clock: FakeClock(startMs), entitlements: ownedEntitlements(huge), json: """
        {"version": "bp_v9", "season_id": "season_9", "start_ts_ms": 1700000000000,
         "end_ts_ms": 1700086400000, "premium_product_id": "\(huge)",
         "tiers": [{"tier": 1, "xp_required": 0, "rewards_premium": [{"id": "r1", "type": "gem", "amount": 1}]}]}
        """)

        XCTAssertEqual(pass.activeVersion(), "bp_v9", "the rest of the config still applies")
        XCTAssertFalse(pass.isPremiumActive(), "an id that cannot be trusted cannot confer premium")
        await expectClaim(pass, tier: 1, track: .premium, claimed: false)
        await expectClaim(pass, tier: 1, track: .free, claimed: true)
    }

    /// A reward `id`/`type` is the game's own vocabulary and is handed back to the host verbatim, so
    /// it gets the same ceiling. One bad reward is dropped; the tier and its siblings survive.
    func test_anAbsurdlyLongRewardIdOrTypeDropsOnlyThatReward() async {
        let hugeId = String(repeating: "i", count: 200)
        let hugeType = String(repeating: "t", count: 200)
        let pass = makeConfigured(clock: FakeClock(startMs), json: """
        {"version": "bp_v9", "season_id": "season_9", "start_ts_ms": 1700000000000,
         "end_ts_ms": 1700086400000,
         "tiers": [{"tier": 1, "xp_required": 0, "rewards_free": [
           {"id": "\(hugeId)", "type": "gem", "amount": 1},
           {"id": "r_ok", "type": "\(hugeType)", "amount": 2},
           {"id": "r_kept", "type": "gem", "amount": 3}
         ]}]}
        """)

        let result = await expectClaim(pass, tier: 1, track: .free, claimed: true)
        XCTAssertEqual(result.rewards.map(\.id), ["r_kept"], "only the two over-long rewards are dropped")
    }

    // MARK: - Concurrency: persisted progress must not regress (H1)

    /// ═══ THE DOUBLE-GRANT THIS CLOSES ═══
    ///
    /// The lock made authorization and consumption atomic, but the `setString` that followed was
    /// outside it and nothing ordered two of them. `addXp` encoded `"season_3|10|"` first; `claim`
    /// then encoded `"season_3|10|1:free"`, a strict superset. With the writes unordered, the older
    /// snapshot could land LAST — in-memory state stayed correct for the life of the process, so
    /// nothing looked wrong at runtime, and then a force-quit left storage missing a claim that had
    /// already been consumed and handed to the host app to grant. The player claims it again. No
    /// jailbreak, no clock tampering, no storage editing.
    ///
    /// The ordering here is engineered, not hoped for: the XP write is armed to stall inside the
    /// store, and the claim is issued 100ms into a 400ms stall, so the claim's snapshot is always
    /// the newer one and the XP write is always the straggler.
    func test_aStragglingWriteCannotRevertPersistedProgressToASnapshotTakenBeforeAClaim() async {
        let store = DelayingStore()
        let pass = makeConfigured(store: store, clock: FakeClock(startMs))
        store.armDelay()

        let xp = Task { await pass.addXp(10) }
        // Long enough for `addXp` to leave the lock and be inside the stalling store call.
        try? await Task.sleep(nanoseconds: 100_000_000)
        let claim = await pass.claim(tier: 1, track: .free)
        _ = await xp.value

        XCTAssertTrue(claim.claimed, "report was: \(claim.report)")
        XCTAssertEqual(pass.xpTotal(), 10, "in-memory state was never the problem")
        XCTAssertEqual(
            store.peek(BattlePass.stateKey), "season_3|10|1:free",
            "the newest snapshot must be the one that stands; landed writes were: \(store.landed())"
        )

        // What the regression actually costs, made concrete: a process restart over the same store.
        let restarted = makePass(store: store, clock: FakeClock(startMs))
        _ = restarted.apply(config)
        await expectClaim(restarted, tier: 1, track: .free, claimed: false)
    }

    /// The same ordering guarantee from the other side: TWO claims, the first stalling. The final
    /// record must carry both keys, not just the one the straggler knew about.
    func test_aStragglingClaimWriteCannotEraseALaterClaimFromStorage() async {
        let store = DelayingStore()
        let pass = makeConfigured(store: store, clock: FakeClock(startMs), entitlements: ownedEntitlements())
        _ = await pass.addXp(250)
        store.armDelay()

        let first = Task { await pass.claim(tier: 1, track: .free).claimed }
        try? await Task.sleep(nanoseconds: 100_000_000)
        let second = await pass.claim(tier: 2, track: .free)
        let firstClaimed = await first.value

        XCTAssertTrue(firstClaimed)
        XCTAssertTrue(second.claimed, "report was: \(second.report)")
        XCTAssertEqual(
            store.peek(BattlePass.stateKey), "season_3|250|1:free,2:free",
            "landed writes were: \(store.landed())"
        )

        let restarted = makePass(store: store, clock: FakeClock(startMs), entitlements: ownedEntitlements())
        _ = restarted.apply(config)
        await expectClaim(restarted, tier: 1, track: .free, claimed: false)
        await expectClaim(restarted, tier: 2, track: .free, claimed: false)
    }
}
