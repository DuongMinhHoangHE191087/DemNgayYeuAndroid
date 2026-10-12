import XCTest
@testable import MonetizationBrainCore

final class DecisionEngineTests: XCTestCase {
    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? { values[key] }
        func setString(_ value: String, forKey key: String) { values[key] = value }
        func removeObject(forKey key: String) { values.removeValue(forKey: key) }
    }

    private struct FixedClock: BrainClock {
        let value: Int64
        init(_ value: Int64) { self.value = value }
        func nowMs() -> Int64 { value }
    }

    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

    private func noActionCandidate(id: String = "no_action", isBaseline: Bool = false) -> ActionCandidate {
        ActionCandidate(mode: .noAction, actionId: id, isBaseline: isBaseline)
    }

    override func setUp() {
        super.setUp()
        HoldoutManager.holdoutFraction = 0.0
        ConsentState.shared.resetForTest()
        // Defensive: ClickFraudGuard has static state shared across test classes; without this
        // reset, a leak from ClickFraudGuardTests or GuardrailEngineTests (both using playerId
        // "p1") would cause CLICK_FRAUD_LOCKOUT to fire here before ETH_MANUFACTURED_FAIL,
        // breaking the guardrail-ordering assertions.
        ClickFraudGuard.resetForTest()
    }

    override func tearDown() {
        HoldoutManager.holdoutFraction = 0.05
        ConsentState.shared.resetForTest()
        ClickFraudGuard.resetForTest()
        super.tearDown()
    }

    private func makeEngine(sink: EventSink? = nil) -> DecisionEngine {
        DecisionEngine(guardrails: GuardrailEngine(), store: InMemoryStore(), eventSink: sink)
    }

    // MARK: - decide: basic contract

    func test_decideReturnsNoActionWhenOnlyCandidateAvailable() async {
        let engine = makeEngine()
        let response = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )
        XCTAssertEqual(response.chosenAction.actionId, "no_action")
        XCTAssertEqual(response.eligibleActionIds, ["no_action"])
    }

    func test_decideFallsBackToNoActionWhenGuardrailsRemoveEveryRealCandidate() async {
        let engine = makeEngine()
        var state = PlayerState(playerId: "p1")
        state.failStreakCurrent = 0 // manufactured-fail guardrail blocks rewarded
        let response = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .rewardedOpportunity),
            state: state,
            candidates: [
                noActionCandidate(),
                ActionCandidate(mode: .rewardedAd, actionId: "r1")
            ]
        )
        XCTAssertEqual(response.chosenAction.mode, .noAction)
        XCTAssertTrue(response.guardrailsApplied.contains("ETH_MANUFACTURED_FAIL"))
    }

    // MARK: - decide: holdout path

    func test_holdoutIsDeterministicAndUsesBaselineCandidate() async {
        HoldoutManager.holdoutFraction = 1.0
        let engine = makeEngine()
        let baseline = ActionCandidate(mode: .interstitial, actionId: "baseline_int", isBaseline: true)
        let response = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .interstitialOpportunity),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate(), baseline],
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertTrue(response.isHoldout)
        XCTAssertEqual(response.chosenAction.actionId, "baseline_int")
        XCTAssertEqual(response.chosenActionProbability, 1.0)
        XCTAssertEqual(response.scenario, .balancedHybrid)
    }

    func test_holdoutFallsBackToNoActionWhenNoBaselineMarked() async {
        HoldoutManager.holdoutFraction = 1.0
        let engine = makeEngine()
        let response = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .interstitialOpportunity),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate(), ActionCandidate(mode: .interstitial, actionId: "int1")]
        )
        XCTAssertEqual(response.chosenAction.mode, .noAction)
    }

    func test_holdoutFeedbackDoesNotUpdateBandit() async {
        HoldoutManager.holdoutFraction = 1.0
        let engine = makeEngine()
        let baseline = ActionCandidate(mode: .interstitial, actionId: "baseline_int", isBaseline: true)
        let request = DecisionRequest(playerId: "p1", decisionPoint: .interstitialOpportunity)
        let response = await engine.decide(
            request: request,
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate(), baseline]
        )
        await engine.feedback(request: request, response: response, outcomeReward01: 1.0)
        let count = engine.observationCount(
            decisionPoint: .interstitialOpportunity,
            segment: .casualSteady,
            armId: "baseline_int"
        )
        XCTAssertEqual(count, 0.0)
    }

    // MARK: - decide/feedback: treatment path composability

    func test_decideAndFeedbackRoundTripUpdatesBanditObservationCount() async {
        HoldoutManager.holdoutFraction = 0.0
        let engine = makeEngine()
        let request = DecisionRequest(playerId: "p1", decisionPoint: .interstitialOpportunity)
        let candidates = [noActionCandidate(), ActionCandidate(mode: .interstitial, actionId: "int1")]
        let response = await engine.decide(
            request: request,
            state: PlayerState(playerId: "p1"),
            candidates: candidates,
            adsShownThisSession: 0,
            minutesSinceLastInterstitial: 999
        )
        XCTAssertFalse(response.isHoldout)
        await engine.feedback(request: request, response: response, outcomeReward01: 1.0)

        let segment = response.segment ?? .casualSteady
        let count = engine.observationCount(
            decisionPoint: .interstitialOpportunity,
            segment: segment,
            armId: response.chosenAction.actionId
        )
        XCTAssertGreaterThan(count, 0.0)
    }

    // MARK: - observe / beliefFor

    func test_observeUpdatesBeliefWhenConsentAllowsProfiling() {
        let engine = makeEngine()
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 10
        state.failRateLast30m = 0.65
        state.daysSinceLastSession = 9
        state.purchaseCountLifetime = 0
        let updated = engine.observe(state)
        XCTAssertTrue(updated)
        XCTAssertFalse(engine.beliefFor("p1").isStillPrior())
    }

    func test_observeReturnsFalseWhenProfilingConsentDenied() {
        ConsentState.shared.setTier(.denied)
        let engine = makeEngine()
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 10
        state.failRateLast30m = 0.65
        let updated = engine.observe(state)
        XCTAssertFalse(updated)
    }

    // MARK: - resetPlayer

    func test_resetPlayerClearsBothInMemoryAndStorage() {
        let engine = makeEngine()
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 10
        state.failRateLast30m = 0.65
        _ = engine.observe(state)
        XCTAssertFalse(engine.beliefFor("p1").isStillPrior())

        engine.resetPlayer("p1")

        XCTAssertTrue(engine.beliefFor("p1").isStillPrior())
    }

    // MARK: - startNewSession / budgetFor

    func test_startNewSessionResetsBudgetLambda() {
        let engine = makeEngine()
        let budget = engine.budgetFor("p1")
        budget.observe(chosenMode: .interstitial, budgetPerDecision: 0.0)
        XCTAssertNotEqual(budget.lambdaAdPressure, 0.1)
        engine.startNewSession("p1")
        XCTAssertEqual(engine.budgetFor("p1").lambdaAdPressure, 0.1, accuracy: 1e-12)
    }

    // MARK: - effectiveInterstitialCap

    func test_effectiveInterstitialCapReflectsScenario() {
        let engine = makeEngine()
        var state = PlayerState(playerId: "p1")
        state.sessionsLifetime = 20
        state.failRateLast30m = 0.05
        state.rewardedAcceptRateRecent = 0.9
        state.rewardedOpportunitiesRecent = 10
        state.purchaseCountLifetime = 2
        _ = engine.observe(state)
        let cap = engine.effectiveInterstitialCap("p1")
        XCTAssertLessThanOrEqual(cap, 6)
        XCTAssertGreaterThan(cap, 0)
    }

    // MARK: - event emission

    func test_decideEmitsBrainDecisionEventWithRealBackendFieldNames() async {
        let sink = FakeEventSink()
        let engine = makeEngine(sink: sink)
        _ = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )
        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 1)
        let event = events[0]
        XCTAssertEqual(event.eventName, "brain_decision")
        XCTAssertEqual(
            Set(event.eventData.keys),
            ["decision_id", "decision_point", "chosen_action_id", "chosen_action_mode",
             "chosen_action_probability", "eligible_actions", "guardrails_applied",
             "segment", "segment_confidence", "scenario", "is_holdout", "policy_version"]
        )
        XCTAssertEqual(event.eventData["decision_point"], .string("SHOP_OPEN"))
        XCTAssertEqual(event.eventData["chosen_action_mode"], .string("NO_ACTION"))
        XCTAssertNotNil(event.eventData["decision_id"])
        XCTAssertNotNil(event.eventData["chosen_action_probability"])
        XCTAssertEqual(event.eventData["is_holdout"], .int(0))
        XCTAssertEqual(event.userProperty.userId, "p1")
    }

    func test_feedbackEmitsBrainFeedbackEventWithRewardValue() async {
        let sink = FakeEventSink()
        let engine = makeEngine(sink: sink)
        let request = DecisionRequest(playerId: "p1", decisionPoint: .shopOpen)
        let response = await engine.decide(
            request: request,
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )
        await engine.feedback(request: request, response: response, outcomeReward01: 0.5)
        let events = await sink.sentEvents
        let feedbackEvent = events.first { $0.eventName == "brain_feedback" }
        XCTAssertEqual(feedbackEvent?.eventData["decision_id"], .string(response.decisionId))
        XCTAssertEqual(feedbackEvent?.eventData["reward_value"], .double(0.5))
        XCTAssertEqual(feedbackEvent?.eventData["outcome_event"], .string("outcome_reward"))
    }

    /// `outcome_timestamp_ms` is read by the backend, and it used to come from a bare `Date()` —
    /// so nothing could assert it, and a wrong unit (seconds instead of milliseconds, say) would
    /// have reached production looking plausible. It now comes from the engine's injected clock.
    func test_feedbackTimestampComesFromTheInjectedClock() async {
        let sink = FakeEventSink()
        let engine = DecisionEngine(
            guardrails: GuardrailEngine(),
            store: InMemoryStore(),
            eventSink: sink,
            clock: FixedClock(1_700_000_000_123)
        )
        let request = DecisionRequest(playerId: "p1", decisionPoint: .shopOpen)
        let response = await engine.decide(
            request: request,
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )

        await engine.feedback(request: request, response: response, outcomeReward01: 0.5)

        let events = await sink.sentEvents
        let feedbackEvent = events.first { $0.eventName == "brain_feedback" }
        XCTAssertEqual(feedbackEvent?.eventData["outcome_timestamp_ms"], .int(1_700_000_000_123))
    }

    func test_decisionIdsAreUniqueAcrossCalls() async {
        let engine = makeEngine()
        let r1 = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )
        let r2 = await engine.decide(
            request: DecisionRequest(playerId: "p1", decisionPoint: .shopOpen),
            state: PlayerState(playerId: "p1"),
            candidates: [noActionCandidate()]
        )
        XCTAssertNotEqual(r1.decisionId, r2.decisionId)
    }

    // MARK: - bandit restore at init

    func test_initRestoresBanditStatsFromStorage() {
        let store = InMemoryStore()
        let seedBandit = BanditPolicy()
        seedBandit.update(segmentKey: "SHOP_OPEN_CASUAL_STEADY", armId: "arm1", reward01: 1.0)
        store.setString(seedBandit.serialize(), forKey: "brain_bandit_stats")

        let engine = DecisionEngine(guardrails: GuardrailEngine(), store: store)
        let count = engine.observationCount(decisionPoint: .shopOpen, segment: .casualSteady, armId: "arm1")
        XCTAssertGreaterThan(count, 0.0)
    }

    func test_brainDecisionOmitsSegmentWhenPersonalizationConsentDenied() async {
        HoldoutManager.holdoutFraction = 0.0
        ConsentState.shared.setTier(.denied)
        let sink = FakeEventSink()
        let engine = DecisionEngine(
            bandit: BanditPolicy(),
            guardrails: GuardrailEngine(),
            store: InMemoryStore(),
            eventSink: sink
        )
        let request = DecisionRequest(playerId: "p1", decisionPoint: .levelFail)
        let candidates = [ActionCandidate(mode: .noAction, actionId: "no_action")]

        _ = await engine.decide(request: request, state: PlayerState(playerId: "p1"), candidates: candidates)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.first?.eventData["segment"], .string("UNKNOWN"))
        XCTAssertEqual(sent.first?.eventData["segment_confidence"], .double(0.0))
    }

    func test_brainDecisionIncludesRealSegmentWhenPersonalizationConsentGranted() async {
        HoldoutManager.holdoutFraction = 0.0
        ConsentState.shared.setTier(.granted)
        let sink = FakeEventSink()
        let engine = DecisionEngine(
            bandit: BanditPolicy(),
            guardrails: GuardrailEngine(),
            store: InMemoryStore(),
            eventSink: sink
        )
        let request = DecisionRequest(playerId: "p1", decisionPoint: .levelFail)
        let candidates = [ActionCandidate(mode: .noAction, actionId: "no_action")]

        let response = await engine.decide(request: request, state: PlayerState(playerId: "p1"), candidates: candidates)

        // Confirms the fix's guard is genuinely conditional, not a hardcoded "UNKNOWN" — the
        // logged value must be `mostLikelyType()`'s real (always-non-nil) argmax, matching what
        // `decide()` itself computed for this response.
        let sent = await sink.sentEvents
        XCTAssertEqual(sent.first?.eventData["segment"], .string(response.segment?.rawValue ?? "UNKNOWN"))
        XCTAssertNotEqual(sent.first?.eventData["segment"], .string("UNKNOWN"))
        XCTAssertEqual(sent.first?.eventData["segment_confidence"], .double(response.segmentConfidence))
    }
}
