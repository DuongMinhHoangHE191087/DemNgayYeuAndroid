import XCTest
@testable import MonetizationBrainCore

/// Design spec §5. Android's own ComfortIndex test asserts only that the output lands in
/// `0...1`; every arithmetic and boundary assertion below is new here, because a metric whose
/// only test is "in range" cannot tell a correct formula from a broken one that happens to be
/// bounded.
final class ComfortIndexTests: XCTestCase {
    /// Same shape as `AdsBrainTests`' own file-local fake — this project redeclares test fakes
    /// per file on purpose (see `JsonConfigValue`'s banner for where that convention stops).
    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

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
            lock.lock(); _ = values.removeValue(forKey: key); lock.unlock()
        }
    }

    private func state(
        adImpressions24h: Int = 0,
        daysSinceLastSession: Int = 0
    ) -> PlayerState {
        var s = PlayerState(playerId: "p1")
        s.adImpressions24h = adImpressions24h
        s.daysSinceLastSession = daysSinceLastSession
        return s
    }

    override func setUp() {
        super.setUp()
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        CrmBridge.shared.resetForTest()
    }

    override func tearDown() {
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        CrmBridge.shared.resetForTest()
        super.tearDown()
    }

    // MARK: - §3.1 exact arithmetic

    func test_bestCaseIsExactlyPointNineFive() {
        // fatigue 0, churnProxy 0.1 -> ((1 - 0) + (1 - 0.1)) / 2
        let result = ComfortIndex.compute(state: state(), adCapPerDay: 40)
        XCTAssertEqual(result.value, 0.95, accuracy: 1e-12)
    }

    func test_worstCaseIsExactlyPointZeroFive() {
        // fatigue 1 (saturated), churnProxy 0.9 -> ((1 - 1) + (1 - 0.9)) / 2
        let result = ComfortIndex.compute(
            state: state(adImpressions24h: 40, daysSinceLastSession: 7), adCapPerDay: 40
        )
        XCTAssertEqual(result.value, 0.05, accuracy: 1e-12)
    }

    func test_halfCapAndThreeDaysAwayIsExactlyPointFourFive() {
        // fatigue 0.5, churnProxy 0.6 -> (0.5 + 0.4) / 2
        let result = ComfortIndex.compute(
            state: state(adImpressions24h: 20, daysSinceLastSession: 3), adCapPerDay: 40
        )
        XCTAssertEqual(result.value, 0.45, accuracy: 1e-12)
    }

    func test_fatigueIsTheRatioOfImpressionsToTheDailyCap() {
        // fatigue 0.25, churnProxy 0.1 -> (0.75 + 0.9) / 2
        let result = ComfortIndex.compute(state: state(adImpressions24h: 10), adCapPerDay: 40)
        XCTAssertEqual(result.value, 0.825, accuracy: 1e-12)
    }

    /// The two components carry EQUAL weight (§3.1) — not a calibrated choice, and deliberately
    /// recorded as such. This is the test that would fail if someone "tuned" the weights without
    /// saying so.
    func test_theTwoComponentsCarryEqualWeight() {
        let fatigueOnly = ComfortIndex.compute(
            state: state(adImpressions24h: 40, daysSinceLastSession: 0), adCapPerDay: 40
        )
        // fatigue 1, churn 0.1 -> (0 + 0.9) / 2 = 0.45
        XCTAssertEqual(fatigueOnly.value, 0.45, accuracy: 1e-12)
        let churnOnly = ComfortIndex.compute(
            state: state(adImpressions24h: 0, daysSinceLastSession: 7), adCapPerDay: 40
        )
        // fatigue 0, churn 0.9 -> (1 + 0.1) / 2 = 0.55
        XCTAssertEqual(churnOnly.value, 0.55, accuracy: 1e-12)
        // A one-unit move in either component moves `value` by the same amount.
        let best = ComfortIndex.compute(state: state(), adCapPerDay: 40).value
        XCTAssertEqual(best - fatigueOnly.value, 0.5, accuracy: 1e-12)
        XCTAssertEqual(best - churnOnly.value, 0.4, accuracy: 1e-12)
    }

    // MARK: - §5 churn-proxy bucket boundaries (untested on Android)

    private func churnProxy(daysSinceLastSession: Int) -> Double {
        // With zero ad impressions, value == ((1 - 0) + (1 - churnProxy)) / 2, so
        // churnProxy == 2 - 2 * value.
        2.0 - 2.0 * ComfortIndex.compute(
            state: state(daysSinceLastSession: daysSinceLastSession), adCapPerDay: 40
        ).value
    }

    /// Every bucket edge, from BOTH sides — the day before the boundary and the boundary itself.
    /// A one-sided assertion cannot tell `>= 7` from `> 7`, and Android tests neither side.
    func test_churnProxyBucketBoundariesAtExactlyOneThreeAndSevenDays() {
        let expected: [(days: Int, proxy: Double)] = [
            (0, 0.1), (1, 0.3), (2, 0.3), (3, 0.6), (6, 0.6), (7, 0.9)
        ]
        for case let (days, proxy) in expected {
            XCTAssertEqual(churnProxy(daysSinceLastSession: days), proxy, accuracy: 1e-12, "day \(days)")
        }
    }

    func test_churnProxySaturatesBeyondSevenDays() {
        XCTAssertEqual(churnProxy(daysSinceLastSession: 365), 0.9, accuracy: 1e-12)
        XCTAssertEqual(churnProxy(daysSinceLastSession: Int.max), 0.9, accuracy: 1e-12)
    }

    // MARK: - §2.5 impossible inputs are clamped, and read as BEST case

    func test_negativeImpressionCountReadsAsZeroFatigueNotNegative() {
        let negative = ComfortIndex.compute(state: state(adImpressions24h: -5), adCapPerDay: 40)
        let zero = ComfortIndex.compute(state: state(adImpressions24h: 0), adCapPerDay: 40)
        XCTAssertEqual(negative.value, zero.value, accuracy: 1e-12)
        XCTAssertEqual(negative.value, 0.95, accuracy: 1e-12, "a negative impression count must not read BETTER than zero")
    }

    func test_negativeDaysSinceLastSessionReadsAsActiveToday() {
        XCTAssertEqual(churnProxy(daysSinceLastSession: -3), 0.1, accuracy: 1e-12)
    }

    func test_nonPositiveAdCapIsTreatedAsOne() {
        // max(adCapPerDay, 1) == 1, so a single impression saturates fatigue.
        for cap in [0, -1, Int.min] {
            let result = ComfortIndex.compute(state: state(adImpressions24h: 1), adCapPerDay: cap)
            XCTAssertEqual(result.value, 0.45, accuracy: 1e-12, "cap \(cap)")
        }
    }

    func test_absurdImpressionCountClampsToFullFatigueWithoutTrapping() {
        let result = ComfortIndex.compute(
            state: state(adImpressions24h: Int.max, daysSinceLastSession: 7), adCapPerDay: 1
        )
        XCTAssertEqual(result.value, 0.05, accuracy: 1e-12)
    }

    func test_valueNeverLeavesTheUnitIntervalForAnyInput() {
        let impressions = [Int.min, -5, 0, 1, 39, 40, 41, 10_000, Int.max]
        let days = [Int.min, -3, 0, 1, 3, 7, 365, Int.max]
        let caps = [Int.min, -1, 0, 1, 40, Int.max]
        for i in impressions {
            for d in days {
                for c in caps {
                    let value = ComfortIndex.compute(
                        state: state(adImpressions24h: i, daysSinceLastSession: d), adCapPerDay: c
                    ).value
                    XCTAssertTrue((0.0...1.0).contains(value), "value \(value) out of range for (\(i), \(d), \(c))")
                }
            }
        }
    }

    // MARK: - §2.4 coverage and the structurally-false `isTrustworthy`

    func test_coverageIsExactlyTwoOfFive() {
        let result = ComfortIndex.compute(state: state(), adCapPerDay: 40)
        XCTAssertEqual(result.coverage, 0.4, accuracy: 1e-12)
        XCTAssertEqual(result.componentsAvailable.count, 2)
        XCTAssertEqual(ComfortIndex.totalComponents, 5)
    }

    /// ═══ THE TEST §2.4 EXISTS FOR ═══
    ///
    /// `isTrustworthy` cannot be `true` today: coverage is fixed at 0.4 and the threshold is 0.6.
    /// Android ships that silently. When a third component ever lands, THIS test fails and forces
    /// a conscious decision about what `isTrustworthy` should mean — rather than the predicate
    /// quietly coming alive and changing a caller's behavior.
    func test_isTrustworthyIsStructurallyFalseUntilAThirdComponentLands() {
        XCTAssertEqual(ComfortIndex.trustworthyCoverageThreshold, 0.6, accuracy: 1e-12)
        XCTAssertLessThan(
            Double(ComfortIndex.componentsWithADataSource.count) / Double(ComfortIndex.totalComponents),
            ComfortIndex.trustworthyCoverageThreshold,
            "a third component has landed — decide what isTrustworthy should mean, do not just delete this test"
        )
        // ...and no input can reach it either, which is the part a caller would gate on.
        for i in [Int.min, 0, 40, Int.max] {
            for d in [Int.min, 0, 7, Int.max] {
                let result = ComfortIndex.compute(
                    state: state(adImpressions24h: i, daysSinceLastSession: d), adCapPerDay: 40
                )
                XCTAssertFalse(result.isTrustworthy, "(\(i), \(d))")
            }
        }
    }

    func test_componentsAvailableAndMissingNameEveryComponentExactlyOnce() {
        let result = ComfortIndex.compute(state: state(), adCapPerDay: 40)
        XCTAssertEqual(result.componentsAvailable, ["ad_fatigue", "churn_proxy"])
        XCTAssertEqual(result.componentsMissing, ComfortIndex.componentsWithoutADataSource)
        XCTAssertEqual(result.componentsMissing.count, 3)
        XCTAssertEqual(
            result.componentsAvailable.count + result.componentsMissing.count,
            ComfortIndex.totalComponents,
            "coverage is only honest if the named components add up to the denominator"
        )
        XCTAssertTrue(Set(result.componentsAvailable).isDisjoint(with: Set(result.componentsMissing)))
    }

    func test_describeReportsTheValueCoverageAndTheMissingComponents() {
        let result = ComfortIndex.compute(
            state: state(adImpressions24h: 20, daysSinceLastSession: 3), adCapPerDay: 40
        )
        let described = result.describe()
        XCTAssertTrue(described.contains("0.45"), described)
        XCTAssertTrue(described.contains("0.40"), described)
        XCTAssertTrue(described.contains("post_purchase_retention_delta"), described)
        XCTAssertTrue(
            described.lowercased().contains("not trustworthy"),
            "describe() must say the coverage gate is not met, not leave the reader to compute it: \(described)"
        )
    }

    // MARK: - §3.1 guardrails measure POLICY health, never player comfort

    func test_policyAggressivenessIsEmptyForAFreshEngine() {
        XCTAssertTrue(ComfortIndex.policyAggressiveness(engine: GuardrailEngine()).isEmpty)
    }

    func test_policyAggressivenessReadsThroughToPreventedCounts() {
        let engine = GuardrailEngine()
        var blocked = PlayerState(playerId: "p1")
        blocked.failStreakCurrent = 0
        // A rewarded candidate with no real fail is the cheapest hard block to provoke.
        for _ in 0..<3 {
            _ = engine.evaluate(
                candidate: ActionCandidate(mode: .rewardedAd, actionId: "rewarded"),
                state: blocked,
                adsShownThisSession: 0,
                minutesSinceLastInterstitial: 100
            )
        }
        XCTAssertEqual(ComfortIndex.policyAggressiveness(engine: engine)["ETH_MANUFACTURED_FAIL"], 3)
    }

    func test_guardrailsFiringDoesNotMoveTheComfortValue() {
        let engine = GuardrailEngine()
        let subject = state(adImpressions24h: 10, daysSinceLastSession: 1)
        let before = ComfortIndex.compute(state: subject, adCapPerDay: engine.maxAds24h)

        var blocked = PlayerState(playerId: "p1")
        blocked.failStreakCurrent = 0
        for _ in 0..<25 {
            _ = engine.evaluate(
                candidate: ActionCandidate(mode: .rewardedAd, actionId: "rewarded"),
                state: blocked,
                adsShownThisSession: 0,
                minutesSinceLastInterstitial: 100
            )
        }

        let after = ComfortIndex.compute(state: subject, adCapPerDay: engine.maxAds24h)
        XCTAssertEqual(before.value, after.value, accuracy: 1e-12, "guardrail blocks measure POLICY health, not player experience")
        XCTAssertEqual(before.coverage, after.coverage, accuracy: 1e-12)
        XCTAssertFalse(ComfortIndex.policyAggressiveness(engine: engine).isEmpty, "the counts themselves must have moved")
    }

    func test_policyAggressivenessRendersSortedSoTheEventPayloadIsDeterministic() {
        let rendered = ComfortIndex.describe(policyAggressiveness: ["B_CODE": 2, "A_CODE": 1, "C_CODE": 3])
        XCTAssertEqual(rendered, "A_CODE=1;B_CODE=2;C_CODE=3")
        XCTAssertEqual(ComfortIndex.describe(policyAggressiveness: [:]), "")
    }

    // MARK: - Wiring: `AdsBrain.onSessionEnd` emits `brain_comfort` (§1, §4)

    private func configureBrain(sink: EventSink, playerId: String = "user_1") async {
        let engine = DecisionEngine(store: InMemoryStore(), eventSink: sink)
        await AdsBrain.shared.configure(
            AdsBrain.Configuration(
                remoteConfig: StaticRemoteConfigProvider(),
                eventSink: sink,
                playerId: playerId,
                decisionEngine: engine
            )
        )
    }

    func test_onSessionEndEmitsBrainComfort() async {
        let sink = FakeEventSink()
        await configureBrain(sink: sink)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        for _ in 0..<4 { AdsBrain.shared.onInterstitialShown() }

        await AdsBrain.shared.onSessionEnd()

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured", "experiment_exposure", "brain_comfort"])
        // 4 impressions against the default 40/day cap -> fatigue 0.1; the session just started,
        // so churnProxy is 0.1 -> ((1 - 0.1) + (1 - 0.1)) / 2 == 0.9.
        XCTAssertEqual(sent.last?.eventData["comfort_value"], .double(0.9))
        XCTAssertEqual(sent.last?.eventData["comfort_coverage"], .double(0.4))
        XCTAssertEqual(sent.last?.userProperty.userId, "user_1")
    }

    func test_brainComfortJoinsTheMissingComponentsWithSemicolons() async {
        let sink = FakeEventSink()
        await configureBrain(sink: sink)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        await AdsBrain.shared.onSessionEnd()

        let sent = await sink.sentEvents
        XCTAssertEqual(
            sent.last?.eventData["components_missing"],
            .string(ComfortIndex.componentsWithoutADataSource.joined(separator: ";"))
        )
    }

    func test_brainComfortCarriesThePreventedCountsAsKeyValuePairs() async {
        let sink = FakeEventSink()
        let engine = DecisionEngine(store: InMemoryStore(), eventSink: sink)
        await AdsBrain.shared.configure(
            AdsBrain.Configuration(
                remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
                decisionEngine: engine
            )
        )
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        // A rewarded opportunity with no fail streak is blocked by ETH_MANUFACTURED_FAIL.
        _ = await AdsBrain.shared.shouldOfferRewarded()

        await AdsBrain.shared.onSessionEnd()

        let sent = await sink.sentEvents
        guard case .string(let rendered)? = sent.last?.eventData["policy_aggressiveness"] else {
            return XCTFail("brain_comfort carried no policy_aggressiveness string")
        }
        XCTAssertTrue(rendered.contains("ETH_MANUFACTURED_FAIL=1"), rendered)
    }

    /// `brain_comfort` is NOT consent-gated (§4): it is an aggregate quality metric carrying no
    /// profile and no identifier beyond the pseudonymous player id every event already carries.
    func test_brainComfortIsEmittedEvenWhenConsentIsDenied() async {
        let sink = FakeEventSink()
        await configureBrain(sink: sink)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        ConsentState.shared.setTier(.denied)

        await AdsBrain.shared.onSessionEnd()

        let sent = await sink.sentEvents
        XCTAssertTrue(sent.map(\.eventName).contains("brain_comfort"))
    }

    func test_noComfortEventWhenTheBrainIsDisabled() async {
        let sink = FakeEventSink()
        await configureBrain(sink: sink)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        AdsBrain.shared.enabled = false

        await AdsBrain.shared.onSessionEnd()

        let sent = await sink.sentEvents
        XCTAssertFalse(sent.map(\.eventName).contains("brain_comfort"), "off must mean exactly the pre-SDK behavior")
    }

    func test_noComfortEventWithoutASession() async {
        let sink = FakeEventSink()
        await configureBrain(sink: sink)
        AdsBrain.shared.enabled = true

        await AdsBrain.shared.onSessionEnd()

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured"])
    }
}
