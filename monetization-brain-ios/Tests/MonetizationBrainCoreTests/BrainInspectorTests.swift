import XCTest
@testable import MonetizationBrainCore

/// `BrainInspector` is debug machinery, and debug machinery gets tested for one reason above all:
/// a bug report written from a dump that lies is worse than no bug report. So these tests are
/// mostly about the dump telling the truth — the real candidate count, every latent type present,
/// the fallback scenario distinguishable from a real one, and the engine's OWN policy rather than
/// whatever singleton happened to be configured last.
final class BrainInspectorTests: XCTestCase {

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

    private final class FakeClock: BrainClock, @unchecked Sendable {
        private let lock = NSLock()
        private var now: Int64

        init(_ start: Int64) { self.now = start }

        func nowMs() -> Int64 {
            lock.lock(); defer { lock.unlock() }
            return now
        }

        func advance(_ ms: Int64) {
            lock.lock(); now += ms; lock.unlock()
        }
    }

    private struct Fixture {
        let engine: DecisionEngine
        let inspector: BrainInspector
        let policy: BrainPolicy
        let guardrails: GuardrailEngine
        let clock: FakeClock
    }

    private func makeFixture() -> Fixture {
        let clock = FakeClock(1_700_000_000_000)
        let inspector = BrainInspector(clock: clock)
        let policy = BrainPolicy()
        let guardrails = GuardrailEngine()
        return Fixture(
            engine: DecisionEngine(
                guardrails: guardrails,
                store: InMemoryStore(),
                policy: policy,
                inspector: inspector
            ),
            inspector: inspector,
            policy: policy,
            guardrails: guardrails,
            clock: clock
        )
    }

    private var fixture: Fixture!

    private func noAction(_ id: String = "no_action") -> ActionCandidate {
        ActionCandidate(mode: .noAction, actionId: id)
    }

    override func setUp() {
        super.setUp()
        HoldoutManager.holdoutFraction = 0.0
        ClickFraudGuard.resetForTest()
        fixture = makeFixture()
    }

    override func tearDown() {
        fixture?.policy.resetForTest()
        fixture = nil
        HoldoutManager.holdoutFraction = 0.05
        ClickFraudGuard.resetForTest()
        super.tearDown()
    }

    // MARK: - Recording

    /// The hook itself. Without this, everything below tests a type nothing ever calls.
    func test_decideRecordsExactlyOneDecision() async {
        _ = await fixture.engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noAction()]
        )

        XCTAssertEqual(fixture.inspector.recentDecisions().count, 1)
        XCTAssertEqual(fixture.inspector.recentDecisions().first?.decisionPoint, .shopOpen)
    }

    func test_recentDecisionsAreNewestFirst() async {
        for index in 0..<3 {
            fixture.clock.advance(1_000)
            _ = await fixture.engine.decide(
                request: DecisionRequest(playerId: "p\(index)", decisionPoint: .shopOpen),
                state: PlayerState(playerId: "p\(index)"),
                candidates: [noAction()]
            )
        }

        let timestamps = fixture.inspector.recentDecisions().map(\.timestampMs)
        XCTAssertEqual(timestamps, timestamps.sorted(by: >))
        XCTAssertEqual(timestamps.first, 1_700_000_003_000)
    }

    /// A session runs for hours; the history must not grow with it. The oldest goes, not the
    /// newest — a debug dump that dropped the decision you just triggered would be useless.
    func test_historyIsCappedAndDropsTheOldest() async {
        for index in 0..<(BrainInspector.maxHistory + 5) {
            fixture.clock.advance(1)
            _ = await fixture.engine.decide(
                request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
                state: PlayerState(playerId: "p1"),
                candidates: [noAction("a\(index)")]
            )
        }

        let records = fixture.inspector.recentDecisions()
        XCTAssertEqual(records.count, BrainInspector.maxHistory)
        XCTAssertEqual(records.first?.chosenActionId, "a34")
        XCTAssertEqual(records.last?.chosenActionId, "a5")
    }

    func test_clearHistoryEmptiesIt() async {
        _ = await fixture.engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noAction()]
        )

        fixture.inspector.clearHistory()

        XCTAssertTrue(fixture.inspector.recentDecisions().isEmpty)
    }

    /// The record must describe the decision the caller actually asked for: three candidates in,
    /// one surviving, two rules fired.
    ///
    /// Android would derive `totalCandidates` as `eligible + guardrailsApplied.count`, which here
    /// gives the same 3 — as it does on every path reachable today, for the reason spelled out on
    /// `BrainDecisionRecord.totalCandidates`. This test does not claim to catch a live Android
    /// bug; it pins that the iOS field is the caller's real input count, so that it stays right
    /// if either fact the derivation leans on ever stops holding.
    func test_totalCandidatesIsTheRealInputCount() async {
        var state = PlayerState(playerId: "p1")
        state.failStreakCurrent = 0 // manufactured-fail guardrail blocks rewarded

        _ = await fixture.engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .rewardedOpportunity),
            state: state,
            candidates: [
                noAction(),
                ActionCandidate(mode: .rewardedAd, actionId: "r1"),
                ActionCandidate(mode: .rewardedAd, actionId: "r2")
            ]
        )

        let record = try? XCTUnwrap(fixture.inspector.recentDecisions().first)
        XCTAssertEqual(record?.totalCandidates, 3)
        XCTAssertEqual(record?.eligibleCount, 1, "only no_action survives the guardrail")
        XCTAssertEqual(record?.guardrailsApplied.count, 2, "one code per blocked candidate")
    }

    // MARK: - Snapshot

    func test_snapshotCarriesEveryLatentTypeEvenAtZero() {
        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        XCTAssertEqual(Set(snapshot.posterior.keys), Set(LatentPlayerType.allCases))
    }

    /// A fresh player has learned nothing, and the dump has to say so rather than presenting the
    /// uniform prior as a conclusion.
    func test_snapshotFlagsAnUntouchedBeliefAsStillPrior() {
        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        XCTAssertTrue(snapshot.isStillPrior)
        XCTAssertTrue(snapshot.scenarioIsFallback)
        XCTAssertEqual(snapshot.scenario, .balancedHybrid)
    }

    /// `scenarioIsFallback` is the field that separates "this player IS balanced" from "we don't
    /// know yet" — two states that render with the same scenario name.
    func test_scenarioIsNotFallbackOnceConfidenceClearsTheThreshold() {
        var state = PlayerState(playerId: "p1")
        state.failStreakCurrent = 6
        state.sessionsLifetime = 4
        // Enough observations to move the belief off the uniform prior.
        for _ in 0..<12 {
            XCTAssertTrue(fixture.engine.observe(state))
        }

        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: state)

        XCTAssertFalse(snapshot.isStillPrior)
        XCTAssertGreaterThan(snapshot.confidence, PersonalizationScenario.minConfidence)
        XCTAssertFalse(snapshot.scenarioIsFallback)
    }

    /// The snapshot must read the policy the ENGINE was built with. Reading `BrainPolicy.shared`
    /// instead would make a dump describe an artifact this engine never applied — the most
    /// confusing possible failure for someone debugging a config push.
    func test_snapshotReadsTheEnginesOwnPolicyNotTheSharedOne() {
        _ = fixture.engine.applyPolicy(
            #"{"version": "inspector_v1", "enabled": false, "experiment": {"exploration_floor": 0.17}}"#
        )

        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        XCTAssertEqual(snapshot.policyVersion, "inspector_v1")
        XCTAssertFalse(snapshot.policyEnabled)
        XCTAssertTrue(snapshot.policyReport.contains("inspector_v1"), snapshot.policyReport)
        XCTAssertEqual(snapshot.explorationFloor, 0.17, accuracy: 1e-9)
        XCTAssertNotEqual(snapshot.policyVersion, BrainPolicy.shared.activeVersion())
    }

    func test_policyReportSaysNothingIsLoadedBeforeAnyArtifact() {
        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        XCTAssertEqual(snapshot.policyVersion, "not loaded")
        XCTAssertTrue(snapshot.policyReport.contains("no artifact"), snapshot.policyReport)
    }

    /// A REJECTED artifact must not overwrite the report: the field answers "what is running",
    /// and a refused artifact is not running. The rejection reason travels in the apply result.
    func test_aRejectedArtifactLeavesTheReportOnTheLastGoodOne() {
        _ = fixture.engine.applyPolicy(#"{"version": "good_v1"}"#)
        _ = fixture.engine.applyPolicy("{not valid json")

        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        XCTAssertTrue(snapshot.policyReport.contains("good_v1"), snapshot.policyReport)
    }

    func test_snapshotCarriesTheHoldoutVerdictAndBucket() {
        HoldoutManager.holdoutFraction = 1.0

        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        XCTAssertTrue(snapshot.isHoldout)
        XCTAssertEqual(snapshot.holdoutBucket, HoldoutManager.bucket("p1"))
    }

    /// ═══ THE DUMP MUST NOT CONTRADICT THE DECISIONS PRINTED BELOW IT ═══
    ///
    /// `decide` forces a holdout player onto `.balancedHybrid`; they are the control group and are
    /// never personalized. Android's snapshot calls `scenarioFor` unconditionally, so for a
    /// holdout player whose belief HAS moved it prints the personalized scenario and cap under
    /// "SCENARIO IN FORCE" while the decisions listed lower in the same dump say otherwise.
    ///
    /// The setup is specific on purpose: a FRESH player would resolve to `.balancedHybrid` anyway
    /// and the test would pass with the holdout branch deleted. So the belief is driven until the
    /// personalized scenario is something else, which the assertion below states outright.
    func test_forAHoldoutPlayerTheSnapshotShowsTheScenarioTheDecisionUsed() async {
        HoldoutManager.holdoutFraction = 1.0
        var state = PlayerState(playerId: "p1")
        state.failStreakCurrent = 6
        state.sessionsLifetime = 4
        for _ in 0..<12 {
            XCTAssertTrue(fixture.engine.observe(state))
        }
        XCTAssertNotEqual(
            fixture.engine.scenarioFor("p1"), .balancedHybrid,
            "setup is toothless: the personalized scenario has to differ from the holdout one"
        )

        let response = await fixture.engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .interstitialOpportunity),
            state: state,
            candidates: [noAction(), ActionCandidate(mode: .interstitial, actionId: "i1", isBaseline: true)]
        )
        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: state)

        XCTAssertTrue(snapshot.isHoldout)
        XCTAssertEqual(snapshot.scenario, response.scenario)
        XCTAssertEqual(snapshot.scenario, .balancedHybrid)
        XCTAssertEqual(
            snapshot.effectiveInterstitialCap,
            fixture.guardrails.effectiveInterstitialCap(.balancedHybrid)
        )
        // Imposed, not a low-confidence fallback — this player is confidently segmented and is
        // being held out anyway.
        XCTAssertFalse(snapshot.scenarioIsFallback)
    }

    /// The counterpart, so the holdout branch cannot be "fixed" by hardcoding the neutral
    /// scenario for everybody.
    func test_outsideTheHoldoutTheSnapshotShowsThePersonalizedScenario() {
        var state = PlayerState(playerId: "p1")
        state.failStreakCurrent = 6
        state.sessionsLifetime = 4
        for _ in 0..<12 {
            XCTAssertTrue(fixture.engine.observe(state))
        }

        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: state)

        XCTAssertFalse(snapshot.isHoldout)
        XCTAssertEqual(snapshot.scenario, fixture.engine.scenarioFor("p1"))
        XCTAssertNotEqual(snapshot.scenario, .balancedHybrid)
    }

    func test_formatSaysTheScenarioIsImposedForAHoldoutPlayer() {
        HoldoutManager.holdoutFraction = 1.0
        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        let text = BrainInspector.format(snapshot)

        XCTAssertTrue(text.contains("imposed: holdout control"), text)
        XCTAssertFalse(text.contains("fallback: not confident enough"), text)
    }

    func test_snapshotIncludesTheRecentDecisions() async {
        _ = await fixture.engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noAction()]
        )

        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        XCTAssertEqual(snapshot.recentDecisions.count, 1)
    }

    // MARK: - format

    private func snapshotAfterADecision() async -> BrainSnapshot {
        _ = await fixture.engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noAction()]
        )
        return fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))
    }

    func test_formatNamesThePlayerPolicyAndSegment() async {
        let text = BrainInspector.format(await snapshotAfterADecision())

        XCTAssertTrue(text.contains("player: p1"), text)
        XCTAssertTrue(text.contains("not loaded"), text)
        XCTAssertTrue(text.contains("SHOP_OPEN"), text)
        XCTAssertTrue(text.contains("►"), "the most likely segment is never marked:\n\(text)")
    }

    /// The kill switch is the single most important thing a dump can say — someone looking at
    /// "the brain isn't doing anything" needs to see it in the first three lines.
    func test_formatShoutsWhenTheKillSwitchIsOn() {
        _ = fixture.engine.applyPolicy(#"{"version": "panic", "enabled": false}"#)
        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        XCTAssertTrue(BrainInspector.format(snapshot).contains("KILL SWITCH ON"))
    }

    func test_formatSaysWhenNothingHasBeenLearnedYet() {
        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        XCTAssertTrue(BrainInspector.format(snapshot).contains("NOTHING LEARNED YET"))
    }

    func test_formatMarksAHoldoutPlayerAsControl() {
        HoldoutManager.holdoutFraction = 1.0
        let snapshot = fixture.inspector.snapshot(engine: fixture.engine, state: PlayerState(playerId: "p1"))

        XCTAssertTrue(BrainInspector.format(snapshot).contains("HOLDOUT"))
    }

    /// Guardrail lines go by count descending, and ties by NAME — so the same state renders the
    /// same way every time.
    ///
    /// The failure this guards against is a dump that reorders itself between runs, which people
    /// diff and get false hits from. It cannot be reproduced inside one test process: Swift's
    /// dictionary iteration order is fixed by a per-PROCESS hash seed, so without the tie-break
    /// two renders here would still agree while renders from two different launches would not.
    /// So this asserts the ORDER RULE directly rather than comparing renders and calling that
    /// stability. Even so: removing the tie-break does NOT fail this test — in this process the
    /// dictionary happens to iterate alphabetically anyway. It pins the intended order and
    /// documents the rule, which is the most a single-process test can do here.
    func test_formatOrdersGuardrailsByCountThenName() async {
        var snapshot = await snapshotAfterADecision()
        snapshot = BrainSnapshot(
            playerId: snapshot.playerId,
            posterior: snapshot.posterior,
            segment: snapshot.segment,
            confidence: snapshot.confidence,
            isStillPrior: snapshot.isStillPrior,
            scenario: snapshot.scenario,
            scenarioIsFallback: snapshot.scenarioIsFallback,
            activeSignals: snapshot.activeSignals,
            effectiveInterstitialCap: snapshot.effectiveInterstitialCap,
            guardrailsPrevented: ["A_RULE": 2, "B_RULE": 2, "C_RULE": 2, "D_RULE": 1],
            recentDecisions: snapshot.recentDecisions,
            policyVersion: snapshot.policyVersion,
            policyEnabled: snapshot.policyEnabled,
            policyReport: snapshot.policyReport,
            isHoldout: snapshot.isHoldout,
            holdoutBucket: snapshot.holdoutBucket,
            explorationFloor: snapshot.explorationFloor
        )

        let rendered = BrainInspector.format(snapshot)
        let ruleLines = rendered
            .split(separator: "\n")
            .filter { $0.contains("_RULE:") }
            .map(String.init)

        XCTAssertEqual(ruleLines, ["║ A_RULE: 2×", "║ B_RULE: 2×", "║ C_RULE: 2×", "║ D_RULE: 1×"], rendered)
    }

    /// A debug dump is what someone opens when something is ALREADY wrong, so it is the last
    /// place allowed to kill the app. The posterior bar used to be `Int(p * 20)`, and `Int(Double)`
    /// traps on a NaN — a belief that had gone NaN (the exact condition you would open this dump
    /// to investigate) would crash the host instead of showing itself.
    func test_formatSurvivesANaNInThePosterior() {
        let broken = BrainSnapshot(
            playerId: "p1",
            posterior: [.casualSteady: .nan, .highSkillLowTolerance: 0.5],
            segment: .casualSteady,
            confidence: .nan,
            isStillPrior: false,
            scenario: .balancedHybrid,
            scenarioIsFallback: false,
            activeSignals: [],
            effectiveInterstitialCap: 2,
            guardrailsPrevented: [:],
            recentDecisions: [],
            policyVersion: "v1",
            policyEnabled: true,
            policyReport: "loaded",
            isHoldout: false,
            holdoutBucket: 42,
            explorationFloor: 0.03
        )

        let text = BrainInspector.format(broken)

        XCTAssertTrue(text.contains("player: p1"), text)
        XCTAssertTrue(text.contains("CASUAL_STEADY"), text)
    }

    /// Numbers get formatted, not interpolated — `0.30000000000000004` in a dump is noise that
    /// makes a reader distrust everything next to it.
    func test_formatRoundsProbabilitiesInsteadOfDumpingRawDoubles() async {
        let text = BrainInspector.format(await snapshotAfterADecision())

        XCTAssertFalse(text.contains("0000000"), text)
        XCTAssertTrue(text.contains("p=1.00"), text)
    }
}
