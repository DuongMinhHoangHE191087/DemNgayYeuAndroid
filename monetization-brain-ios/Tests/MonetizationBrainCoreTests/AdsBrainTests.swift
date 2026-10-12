import XCTest
@testable import MonetizationBrainCore

final class AdsBrainTests: XCTestCase {
    /// Configurations built inline here use `DecisionEngine`'s default store, which is the REAL
    /// `UserDefaults` — and `onIapOfferShown` now persists the daily offer count there. Without
    /// clearing it, one test's bump leaks into the next test's `iapOffers24h == 1` assertion.
    private func clearPersistedOfferCount() {
        UserDefaults.standard.removeObject(forKey: BrainStorage.offerCountKey)
        UserDefaults.standard.removeObject(forKey: BrainStorage.offerDayKey)
    }

    override func setUp() {
        super.setUp()
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.05
        clearPersistedOfferCount()
    }

    override func tearDown() {
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        HoldoutManager.salt = "brain_holdout_v1"
        HoldoutManager.holdoutFraction = 0.05
        clearPersistedOfferCount()
        super.tearDown()
    }

    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? { lock.lock(); defer { lock.unlock() }; return values[key] }
        func setString(_ value: String, forKey key: String) { lock.lock(); values[key] = value; lock.unlock() }
        func removeObject(forKey key: String) { lock.lock(); _ = values.removeValue(forKey: key); lock.unlock() }
    }

    private func makeConfiguration(playerId: String = "user_1") -> AdsBrain.Configuration {
        AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: EventQueue(
                directory: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString),
                sender: NoOpEventSender()
            ),
            playerId: playerId
        )
    }

    func test_enabledDefaultsToFalse() {
        XCTAssertFalse(AdsBrain.shared.enabled)
    }

    func test_isConfiguredFalseBeforeConfigure() {
        XCTAssertFalse(AdsBrain.shared.isConfigured())
    }

    func test_isConfiguredTrueAfterConfigure() async {
        await AdsBrain.shared.configure(makeConfiguration())
        XCTAssertTrue(AdsBrain.shared.isConfigured())
    }

    func test_configureSendsSdkConfiguredEvent() async {
        let sink = FakeEventSink()
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: sink,
            playerId: "user_1"
        )

        await AdsBrain.shared.configure(configuration)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured"])
        XCTAssertEqual(sent.first?.userProperty.userId, "user_1")
    }

    // MARK: - Daily IAP-offer count persistence

    /// A clock the test moves, shared by the engine's storage (day boundary) and AdsBrain
    /// (cooldowns), so "next day" is one call.
    private final class MovableClock: BrainClock, @unchecked Sendable {
        private let lock = NSLock()
        private var current: Int64
        init(_ start: Int64) { self.current = start }
        func nowMs() -> Int64 { lock.lock(); defer { lock.unlock() }; return current }
        func advance(ms: Int64) { lock.lock(); current += ms; lock.unlock() }
    }

    /// The `max_iap_offers_24h` guardrail reads `iapOffers24h`. Before persistence that field reset
    /// on every `onSessionStart`, so a player who closed and reopened the app started the day's
    /// allowance over — the ceiling `BrainPolicy` lets an operator configure held only within one
    /// process. Now: same-day restart keeps the count; the next UTC day starts from zero.
    func test_iapOffers24hSurvivesASessionRestartOnTheSameDayAndResetsTheNextDay() async {
        let clock = MovableClock(1_700_000_000_000 + 43_200_000)   // noon UTC
        let sink = FakeEventSink()
        let engine = DecisionEngine(store: InMemoryStore(), eventSink: sink, clock: clock)
        await AdsBrain.shared.configure(AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink,
            playerId: "user_1", decisionEngine: engine, clock: clock
        ))
        AdsBrain.shared.enabled = true

        await AdsBrain.shared.onSessionStart()
        AdsBrain.shared.onIapOfferShown()
        AdsBrain.shared.onIapOfferShown()
        XCTAssertEqual(AdsBrain.shared.currentState()?.iapOffers24h, 2)

        // A new session on the SAME day — the relaunch case — must not reset the count.
        await AdsBrain.shared.onSessionStart()
        XCTAssertEqual(
            AdsBrain.shared.currentState()?.iapOffers24h, 2,
            "a same-day restart must not hand the day's allowance back"
        )
        XCTAssertEqual(AdsBrain.shared.currentState()?.iapOffersThisSession, 0, "the per-SESSION count does reset")

        // Next UTC day: the allowance genuinely refills.
        clock.advance(ms: 86_400_000)
        await AdsBrain.shared.onSessionStart()
        XCTAssertEqual(AdsBrain.shared.currentState()?.iapOffers24h, 0, "a new day starts from zero")
        AdsBrain.shared.onIapOfferShown()
        XCTAssertEqual(AdsBrain.shared.currentState()?.iapOffers24h, 1)
    }

    // MARK: - comfort()

    func test_comfortIsNilBeforeConfigureAndReadableAfterASessionStarts() async {
        XCTAssertNil(AdsBrain.shared.comfort(), "no player state to score yet")

        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        AdsBrain.shared.onInterstitialShown()

        let result = AdsBrain.shared.comfort()
        XCTAssertNotNil(result)
        XCTAssertGreaterThanOrEqual(result?.value ?? -1, 0)
        XCTAssertLessThanOrEqual(result?.value ?? 2, 1)
        XCTAssertEqual(result?.coverage ?? 0, 0.4, accuracy: 0.0001, "2 of 5 components have data")
        XCTAssertEqual(result?.isTrustworthy, false, "structurally false until a third component lands")
    }

    // MARK: - The clock seam

    /// A clock whose time the test moves, so cooldown arithmetic can be driven rather than waited
    /// out. Before the seam existed this behaviour could only be tested by sleeping.
    private final class FakeClock: BrainClock, @unchecked Sendable {
        private let lock = NSLock()
        private var current: Int64
        init(_ start: Int64) { self.current = start }
        func nowMs() -> Int64 { lock.lock(); defer { lock.unlock() }; return current }
        func advance(minutes: Int64) { lock.lock(); current += minutes * 60_000; lock.unlock() }
    }

    /// `minutesSinceLastIapOffer` is fed to the guardrails' cooldown rules, and it is computed from
    /// the clock. With the seam it is a deterministic assertion; without one the only way to prove
    /// it advances was to sleep for real minutes, which is why it was never tested at all.
    func test_iapOfferCooldownIsMeasuredAgainstTheInjectedClock() async {
        let clock = FakeClock(1_700_000_000_000)
        await AdsBrain.shared.configure(AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(),
            eventSink: FakeEventSink(),
            playerId: "user_1",
            clock: clock
        ))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        // No offer yet -> the documented "never" sentinel, not 0.
        _ = await AdsBrain.shared.shouldOfferIap()
        XCTAssertEqual(AdsBrain.shared.currentState()?.minutesSinceLastIapOffer, -1)

        AdsBrain.shared.onIapOfferShown()
        clock.advance(minutes: 7)
        _ = await AdsBrain.shared.shouldOfferIap()
        XCTAssertEqual(
            AdsBrain.shared.currentState()?.minutesSinceLastIapOffer, 7,
            "the cooldown must be measured against the injected clock, not wall time"
        )
    }

    // MARK: - experiment_exposure

    /// The row must be emitted for EVERY player the experiment covers, and the holdout group is
    /// the case that matters: an experiment you can only see the treated half of cannot be
    /// measured. This is why the gate is `enabled` rather than `isActive()` — `isActive` excludes
    /// exactly the population this event exists to record.
    func test_sessionStartEmitsExperimentExposureIncludingForHoldoutPlayers() async {
        let sink = FakeEventSink()
        // Everyone in the holdout, so `isActive()` is false for this player while `enabled` is true.
        HoldoutManager.holdoutFraction = 1.0
        defer { HoldoutManager.holdoutFraction = 0.05 }

        await AdsBrain.shared.configure(AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1"
        ))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let sent = await sink.sentEvents
        let exposure = sent.first { $0.eventName == "experiment_exposure" }
        XCTAssertNotNil(exposure, "a holdout player's membership IS the data: \(sent.map(\.eventName))")
        XCTAssertEqual(exposure?.eventData["is_holdout"], .int(1))
        XCTAssertEqual(exposure?.eventData["experiment_id"], .string("monetization_brain"))
        XCTAssertEqual(exposure?.eventData["salt_version"], .string(HoldoutManager.salt))
        XCTAssertEqual(exposure?.userProperty.userId, "user_1")

        // `.int(0/1)`, never `.bool` — the backend columns are INT64 and Android sends 0/1.
        if case .int = exposure?.eventData["in_rollout"] {} else {
            XCTFail("in_rollout must be an Int, not a Bool: \(String(describing: exposure?.eventData["in_rollout"]))")
        }
    }

    /// `enabled == false` means no experiment is running, so a membership row would be noise.
    func test_noExperimentExposureWhileTheBrainIsDisabled() async {
        let sink = FakeEventSink()
        await AdsBrain.shared.configure(AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1"
        ))
        AdsBrain.shared.enabled = false
        await AdsBrain.shared.onSessionStart()

        let sent = await sink.sentEvents
        XCTAssertFalse(
            sent.contains { $0.eventName == "experiment_exposure" },
            "no experiment is running, so there is no membership to record"
        )
    }

    func test_chooseCrossModalReturnsBaselineNotHoldoutWhenUnconfigured() async {
        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .interstitial)
        XCTAssertEqual(decision.action, .interstitial)
        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseCrossModalReportsRealHoldoutMembershipWhenConfigured() async {
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_1"))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        // `baseline` MUST be one of chooseCrossModal's real arms (.noAction/.rewardedAd/
        // .iapOffer) now that this is a real decision, not a stub that echoes back whatever was
        // passed — `.interstitial` (the old stub-era baseline) is not a candidate this method
        // can ever build, so under holdout no candidate would match `isBaseline` and the
        // fallback would silently pick `.noAction` instead, defeating this test's own point.
        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .iapOffer)

        // Holdout (user_1 is in-bucket at 0.70, per the known vector already used elsewhere in
        // this file) always gets exactly the `isBaseline` candidate — deterministic.
        XCTAssertEqual(decision.action, .iapOffer)
        XCTAssertTrue(decision.isHoldout)
    }

    func test_chooseCrossModalReportsNotHoldoutForPlayerOutsideBucket() async {
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_2"))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let decision = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .iapOffer)

        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseCrossModalSendsBrainDecisionEventWhenConfigured() async {
        HoldoutManager.holdoutFraction = 0.70 // user_1 is holdout at this fraction (known vector)
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        // See the note on `test_chooseCrossModalReportsRealHoldoutMembershipWhenConfigured` above
        // for why `baseline` must be a real chooseCrossModal arm now.
        _ = await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail, baseline: .iapOffer)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured", "experiment_exposure", "brain_decision"])
        XCTAssertEqual(sent.last?.eventData["decision_point"], .string("LEVEL_FAIL"))
        XCTAssertEqual(sent.last?.eventData["chosen_action_mode"], .string("IAP_OFFER"))
        XCTAssertEqual(sent.last?.eventData["is_holdout"], .int(1))
        XCTAssertEqual(sent.last?.userProperty.userId, "user_1")
    }

    func test_chooseAssistReturnsBaselineNotHoldoutWhenUnconfigured() async {
        let decision = await AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)
        XCTAssertEqual(decision.action, .freeAction)
        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseAssistReportsRealHoldoutMembershipWhenConfigured() async {
        // Known vector: bucketWith("brain_holdout_v1", "user_1") = 6924 < 7000.
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_1"))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let decision = await AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)

        XCTAssertEqual(decision.action, .freeAction)
        XCTAssertTrue(decision.isHoldout)
    }

    func test_chooseAssistReportsNotHoldoutForPlayerOutsideBucket() async {
        // Known vector: bucketWith("brain_holdout_v1", "user_2") = 9781, not < 7000.
        HoldoutManager.holdoutFraction = 0.70
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_2"))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let decision = await AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)

        XCTAssertFalse(decision.isHoldout)
    }

    func test_chooseAssistSendsBrainDecisionEventWhenConfigured() async {
        HoldoutManager.holdoutFraction = 0.70 // user_1 is holdout at this fraction (known vector)
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        _ = await AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured", "experiment_exposure", "brain_decision"])
        XCTAssertEqual(sent.last?.eventData["decision_point"], .string("LEVEL_FAIL_STREAK"))
        XCTAssertEqual(sent.last?.eventData["chosen_action_mode"], .string("FREE_ACTION"))
        XCTAssertEqual(sent.last?.eventData["is_holdout"], .int(1))
        XCTAssertEqual(sent.last?.userProperty.userId, "user_1")
    }

    func test_selfCheckReportListsIssuesWhenUnconfiguredAndDisabled() {
        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertFalse(report.isConfigured)
        XCTAssertFalse(report.isEnabled)
        XCTAssertEqual(report.consentTier, .unknown)
        XCTAssertTrue(report.issues.contains("AdsBrain.configure(_:) has not been called yet."))
        XCTAssertTrue(report.issues.contains("AdsBrain.enabled is false — all decision points return their baseline."))
    }

    func test_selfCheckReportHasNoConfigurationIssueOnceConfiguredAndEnabled() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        ConsentState.shared.setTier(.granted)

        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertTrue(report.isConfigured)
        XCTAssertTrue(report.isEnabled)
        XCTAssertFalse(report.issues.contains("AdsBrain.configure(_:) has not been called yet."))
        XCTAssertFalse(report.issues.contains("AdsBrain.enabled is false — all decision points return their baseline."))
    }

    func test_selfCheckReportFlagsMissingSessionStartWhenConfiguredAndEnabled() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        // Deliberately no onSessionStart() call.

        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertTrue(report.issues.contains("AdsBrain.onSessionStart() has not been called — all decision points return their baseline."))
    }

    func test_selfCheckReportHasNoSessionIssueOnceSessionStarted() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let report = AdsBrain.shared.selfCheckReport()
        XCTAssertFalse(report.issues.contains("AdsBrain.onSessionStart() has not been called — all decision points return their baseline."))
    }

    // MARK: - Task 1: session lifecycle

    func test_onSessionStartResetsSessionScopedFields() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        AdsBrain.shared.onIapOfferShown() // bumps iapOffers24h — asserted PERSISTED below

        await AdsBrain.shared.onSessionStart()
        let state = AdsBrain.shared.currentState()

        XCTAssertNotNil(state)
        XCTAssertEqual(state?.playerId, "user_1")
        XCTAssertEqual(state?.sessionsLifetime, 2)
        XCTAssertEqual(state?.daysSinceLastSession, 0)
        XCTAssertEqual(state?.purchasesThisSession, 0)
        XCTAssertEqual(state?.iapOffersThisSession, 0)
        // Deliberately NOT reset: this is a per-DAY count, and a same-day restart must not hand
        // the allowance back. It used to be `0` here, which defeated `max_iap_offers_24h` on
        // every relaunch.
        XCTAssertEqual(state?.iapOffers24h, 1)
        XCTAssertEqual(state?.minutesSinceLastIapOffer, -1)
        XCTAssertEqual(state?.assistsThisSession, 0)
        XCTAssertEqual(state?.interstitialOpportunitiesThisSession, 0)
        XCTAssertEqual(AdsBrain.shared.adsShownThisSession(), 0)
    }

    func test_onSessionStartTwiceIncrementsSessionsLifetimeWithoutResettingLifetimeFields() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true

        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onSessionStart()

        XCTAssertEqual(AdsBrain.shared.currentState()?.sessionsLifetime, 2)
    }

    func test_reconfiguringWithADifferentPlayerIdStartsAFreshSessionNotThePreviousPlayers() async {
        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_1"))
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onPurchase(usd: 9.99)
        XCTAssertEqual(AdsBrain.shared.currentState()?.purchaseCountLifetime, 1)

        await AdsBrain.shared.configure(makeConfiguration(playerId: "user_2"))
        await AdsBrain.shared.onSessionStart()

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.playerId, "user_2")
        XCTAssertEqual(state?.purchaseCountLifetime, 0)
        XCTAssertEqual(state?.sessionsLifetime, 1)
    }

    func test_currentStateIsNilBeforeAnySessionStarted() {
        XCTAssertNil(AdsBrain.shared.currentState())
    }

    func test_onSessionEndIsHarmlessWithNoPendingDecision() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        await AdsBrain.shared.onSessionEnd() // must not crash / hang with nothing pending
    }

    func test_rolloutFractionDefaultsToOne() {
        XCTAssertEqual(AdsBrain.shared.rolloutFraction, 1.0)
    }

    // MARK: - Task 2: behavior hooks

    func test_onLevelFailIncrementsFailStreakAndUpdatesFailRate() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        await AdsBrain.shared.onLevelFail()
        await AdsBrain.shared.onLevelFail()

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.failStreakCurrent, 2)
        XCTAssertEqual(state?.failRateLast30m, 1.0) // 2 attempts, 2 fails
    }

    func test_onLevelWinResetsFailStreakAndAdvancesLevel() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onLevelFail()

        await AdsBrain.shared.onLevelWin()

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.failStreakCurrent, 0)
        XCTAssertEqual(state?.currentLevel, 1)
        XCTAssertEqual(state?.failRateLast30m, 0.5) // 2 attempts (1 fail, 1 win), 1 fail
    }

    func test_onPurchaseUpdatesPurchaseFields() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        await AdsBrain.shared.onPurchase(usd: 4.99)

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.purchasesThisSession, 1)
        XCTAssertEqual(state?.purchaseCountLifetime, 1)
        XCTAssertEqual(state?.purchaseValueLifetimeUsd, 4.99)
        XCTAssertEqual(state?.daysSinceLastPurchase, 0)
    }

    func test_onRewardedOfferedAcceptedUpdatesFieldsAndAcceptRate() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        await AdsBrain.shared.onRewardedOffered(accepted: true)
        await AdsBrain.shared.onRewardedOffered(accepted: false)

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.rewardedOpportunitiesRecent, 2)
        XCTAssertEqual(state?.rewardedAcceptRateRecent, 0.5)
        XCTAssertEqual(state?.adImpressions24h, 1) // only the accepted one counts
    }

    func test_onInterstitialShownIncrementsSessionAdsAndImpressions() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        AdsBrain.shared.onInterstitialShown()

        XCTAssertEqual(AdsBrain.shared.adsShownThisSession(), 1)
        XCTAssertEqual(AdsBrain.shared.currentState()?.adImpressions24h, 1)
    }

    func test_onIapOfferShownIncrementsSessionAndPersisted24hCounter() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        AdsBrain.shared.onIapOfferShown()

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.iapOffersThisSession, 1)
        XCTAssertEqual(state?.iapOffers24h, 1)
    }

    func test_onIapOfferShownIsNoOpWhenInactive() async {
        await AdsBrain.shared.configure(makeConfiguration())
        await AdsBrain.shared.onSessionStart() // enabled is still false here

        AdsBrain.shared.onIapOfferShown()

        XCTAssertEqual(AdsBrain.shared.currentState()?.iapOffersThisSession, 0)
    }

    func test_onAssistShownIncrementsSessionCounter() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        AdsBrain.shared.onAssistShown()

        XCTAssertEqual(AdsBrain.shared.currentState()?.assistsThisSession, 1)
    }

    // MARK: - Task 3: real binary gates

    func test_shouldShowInterstitialReturnsTrueWhenUnconfigured() async {
        let result = await AdsBrain.shared.shouldShowInterstitial()
        XCTAssertTrue(result)
    }

    func test_shouldShowInterstitialReturnsTrueWhenInactive() async {
        await AdsBrain.shared.configure(makeConfiguration())
        await AdsBrain.shared.onSessionStart() // enabled still false

        let result = await AdsBrain.shared.shouldShowInterstitial()
        XCTAssertTrue(result)
    }

    func test_shouldShowInterstitialMakesNoEngineCallWhenInactive() async {
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration) // enabled defaults false -> inactive
        await AdsBrain.shared.onSessionStart()

        _ = await AdsBrain.shared.shouldShowInterstitial()

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured"]) // no brain_decision
    }

    func test_shouldShowInterstitialWhenActiveAndHoldoutReturnsBaselineDeterministically() async {
        HoldoutManager.holdoutFraction = 1.0 // everyone is holdout
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let result = await AdsBrain.shared.shouldShowInterstitial()

        // Holdout always gets the baseline candidate, which here is `.interstitial` (isBaseline
        // is set on the real-mode arm for every binary gate — see Task 3's implementation).
        XCTAssertTrue(result)
    }

    func test_shouldShowInterstitialIncrementsInterstitialOpportunityCounter() async {
        HoldoutManager.holdoutFraction = 1.0
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        _ = await AdsBrain.shared.shouldShowInterstitial()

        XCTAssertEqual(AdsBrain.shared.currentState()?.interstitialOpportunitiesThisSession, 1)
    }

    func test_shouldOfferRewardedReturnsTrueWhenInactive() async {
        let result = await AdsBrain.shared.shouldOfferRewarded()
        XCTAssertTrue(result)
    }

    func test_shouldOfferRewardedWhenActiveAndHoldoutReturnsBaselineDeterministically() async {
        HoldoutManager.holdoutFraction = 1.0
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        // A fresh player has failStreakCurrent == 0, which the ethical HARD guardrail
        // ETH_MANUFACTURED_FAIL (GuardrailEngine.evaluate) filters `.rewardedAd` out for —
        // "rewarded should only be offered after a real fail." Without a real fail streak, the
        // guardrail removes the only real candidate, .noAction survives as the sole finalCandidate,
        // and holdout picks THAT instead of the isBaseline candidate — an entirely different,
        // still-correct behavior this test is not the one meant to exercise. Establish a real fail
        // first so `.rewardedAd` actually reaches the holdout selection this test is testing.
        await AdsBrain.shared.onLevelFail()

        let result = await AdsBrain.shared.shouldOfferRewarded()

        XCTAssertTrue(result)
    }

    /// Phase 1's original contract, still true: brain off/inactive means "keep offering IAP as
    /// before" — never a silent revenue-surface shutdown.
    func test_shouldOfferIapReturnsTrueWhenBrainInactive() async {
        // Local `let` before each assert, not `XCTAssertTrue(await ...)` directly — XCTAssertTrue's
        // first parameter is a plain (non-async) autoclosure, so an `await` call inside it fails to
        // compile ("'async' call in an autoclosure that does not support concurrency") on this
        // toolchain. Same assertion, just evaluated outside the autoclosure.
        let inactiveShopOpen = await AdsBrain.shared.shouldOfferIap()
        XCTAssertTrue(inactiveShopOpen)
        let inactiveCurrencyLow = await AdsBrain.shared.shouldOfferIap(decisionPoint: .currencyLow)
        XCTAssertTrue(inactiveCurrencyLow)
    }

    func test_shouldOfferIapWhenActiveAndHoldoutReturnsBaselineDeterministically() async {
        HoldoutManager.holdoutFraction = 1.0
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let result = await AdsBrain.shared.shouldOfferIap()

        XCTAssertTrue(result)
    }

    func test_shouldOfferIapRefreshesMinutesSinceLastIapOfferBeforeDeciding() async {
        HoldoutManager.holdoutFraction = 1.0
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        AdsBrain.shared.onIapOfferShown()

        _ = await AdsBrain.shared.shouldOfferIap()

        // lastIapOfferAtMs was just set, so minutesSinceLastIapOffer must now read ~0, not the
        // -1 "never" sentinel `onSessionStart` reset it to.
        XCTAssertEqual(AdsBrain.shared.currentState()?.minutesSinceLastIapOffer, 0)
    }

    func test_shouldShowInterstitialThenResolvePendingOnNextOpportunityCallsFeedback() async {
        HoldoutManager.holdoutFraction = 0.0 // non-holdout, but only one REAL candidate after
                                              // guardrails is not guaranteed here — use holdout=0
                                              // only to reach the bandit path; this test doesn't
                                              // assert WHICH action was chosen, only that feedback
                                              // fires on the next call.
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        _ = await AdsBrain.shared.shouldShowInterstitial()
        AdsBrain.shared.onInterstitialShown()
        _ = await AdsBrain.shared.shouldShowInterstitial() // resolves the first pending decision

        let sent = await sink.sentEvents
        XCTAssertTrue(sent.contains { $0.eventName == "brain_feedback" })
    }

    // MARK: - Task 4: real cross-modal/assist

    func test_chooseCrossModalAllArmsDisallowedReturnsBaselineWithNoEngineCall() async {
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let decision = await AdsBrain.shared.chooseCrossModal(
            decisionPoint: .levelFail, allowRewarded: false, allowIapOffer: false, baseline: .noAction
        )

        XCTAssertEqual(decision.action, .noAction)
        XCTAssertFalse(decision.isHoldout)
        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured", "experiment_exposure"]) // no brain_decision at all
    }

    func test_chooseCrossModalAllowRewardedFalseExcludesRewardedCandidate() async {
        HoldoutManager.holdoutFraction = 1.0 // holdout forces the baseline candidate deterministically
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let decision = await AdsBrain.shared.chooseCrossModal(
            decisionPoint: .levelFail, allowRewarded: false, allowIapOffer: true, baseline: .iapOffer
        )

        XCTAssertEqual(decision.action, .iapOffer) // baseline is the only marked candidate; holdout takes it
    }

    func test_chooseAssistNeverOffersIapOffer() async {
        HoldoutManager.holdoutFraction = 1.0
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        let decision = await AdsBrain.shared.chooseAssist(decisionPoint: .levelFailStreak, baseline: .freeAction)

        // chooseAssist's candidate set is structurally incapable of including .iapOffer — this
        // asserts the RESULT can never be it, guarding against a future edit accidentally adding
        // that arm (the core ethical constraint from design spec / Android's own doc comment).
        XCTAssertNotEqual(decision.action, .iapOffer)
    }

    // MARK: - Task 5: closing gaps

    func test_shouldOfferIapAllInactivePathsMakeNoEngineCall() async {
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration) // enabled defaults false
        await AdsBrain.shared.onSessionStart()

        _ = await AdsBrain.shared.shouldOfferIap()

        let sent = await sink.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["sdk_configured"])
    }

    func test_rolloutFractionZeroMakesBrainInactiveEvenWhenEnabled() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        AdsBrain.shared.rolloutFraction = 0.0
        await AdsBrain.shared.onSessionStart()

        let result = await AdsBrain.shared.shouldOfferRewarded()

        XCTAssertTrue(result) // outside rollout -> same as inactive -> baseline `true`
    }

    func test_onPurchaseDoesNotCreditAnUnrelatedPendingInterstitialDecision() async {
        HoldoutManager.holdoutFraction = 1.0 // holdout: chosen action deterministic (.interstitial)
        let sink = FakeEventSink()
        let engine = DecisionEngine(eventSink: sink)
        let configuration = AdsBrain.Configuration(
            remoteConfig: StaticRemoteConfigProvider(), eventSink: sink, playerId: "user_1",
            decisionEngine: engine
        )
        await AdsBrain.shared.configure(configuration)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        _ = await AdsBrain.shared.shouldShowInterstitial() // queues a pending .interstitial decision

        await AdsBrain.shared.onPurchase(usd: 0.99) // resolves it — must NOT credit it as valuable

        let sent = await sink.sentEvents
        let feedback = sent.first { $0.eventName == "brain_feedback" }
        XCTAssertEqual(feedback?.eventData["reward_value"], .double(RewardModel.estimate(actionHadValue: false, playerContinued: true)))
    }

    func test_onSessionStartDoesNotResetLifetimePurchaseFields() async {
        await AdsBrain.shared.configure(makeConfiguration())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onPurchase(usd: 9.99)

        await AdsBrain.shared.onSessionStart() // new session, same player

        let state = AdsBrain.shared.currentState()
        XCTAssertEqual(state?.purchaseCountLifetime, 1)
        XCTAssertEqual(state?.purchaseValueLifetimeUsd, 9.99)
        XCTAssertEqual(state?.purchasesThisSession, 0) // session-scoped field DOES reset
    }
}

private struct NoOpEventSender: EventSender {
    func send(_ event: SdkEvent) async throws {}
}

private actor FakeEventSink: EventSink {
    private(set) var sentEvents: [SdkEvent] = []

    func send(_ event: SdkEvent) async {
        sentEvents.append(event)
    }
}
