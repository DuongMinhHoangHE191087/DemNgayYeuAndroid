import XCTest
@testable import MonetizationBrainCore

/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// WHAT THESE TESTS PROTECT
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// The dispatcher is the seam where one remote-config payload fans out into eight independent
/// subsystems. The failure mode that matters is not "it crashed" — it is **silent mis-wiring**:
/// two keys pointed at the same manager, a key renamed on the console but not here, or one bad
/// artifact taking the other seven down with it. Every test below is aimed at one of those.
///
/// The central trick is that all eight subsystems expose `activeVersion()`, and each key in the
/// fixture carries a DIFFERENT version string. Asserting eight distinct versions proves each
/// payload reached its own subsystem — an assertion that `applied == true` alone cannot make.
final class RemoteConfigDispatcherTests: XCTestCase {

    // MARK: - Fakes (private to this file, per this package's test convention)

    private final class FakeProvider: RemoteConfigProvider, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String]
        /// Values that only become visible after `fetchAndActivate()` — how a real Remote Config
        /// behaves on a cold start, and the only way to prove the fetch happens BEFORE the read.
        private var pending: [String: String]
        private(set) var fetchCount = 0

        init(values: [String: String] = [:], pending: [String: String] = [:]) {
            self.values = values
            self.pending = pending
        }

        func string(forKey key: String) -> String? {
            lock.lock()
            defer { lock.unlock() }
            return values[key]
        }

        func fetchAndActivate() async {
            lock.lock()
            fetchCount += 1
            values.merge(pending) { _, new in new }
            pending = [:]
            lock.unlock()
        }
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
            lock.lock(); values.removeValue(forKey: key); lock.unlock()
        }
    }

    // MARK: - Fixtures

    /// One artifact per key, each with its own version string so a cross-wired dispatch is
    /// visible as the wrong version landing in the wrong subsystem.
    private let brainPolicyJson = """
    {"version": "brain_ios_v1", "guardrails": {"max_ads_24h": 7}, "experiment": {"exploration_floor": 0.21}}
    """
    private let paywallJson = """
    {"version": "paywall_ios_v1", "placements": {"shop_open": {"decision_point": "SHOP_OPEN",
      "variants": [{"id": "a", "weight": 100, "products": ["gem_pack_1"]}]}}}
    """
    private let routingJson = """
    {"version": "routing_ios_v1", "web_shop": {"enabled": true, "url_template": "https://s.example.com/{product_id}"},
     "rules": [{"product_id": "coin_pack_1", "channel": "web_shop"}]}
    """
    private let subscriptionJson = #"{"version": "sub_ios_v1", "dunning_cadence": [3]}"#
    private let battlePassJson = """
    {"version": "bp_ios_v1", "season_id": "season_3",
     "start_ts_ms": 1700000000000, "end_ts_ms": 1700086400000,
     "premium_product_id": "bp_season_3_premium", "xp_cap": 1000,
     "tiers": [{"tier": 1, "xp_required": 0,
       "rewards_free": [{"id": "r1", "type": "gem", "amount": 10}], "rewards_premium": []}]}
    """
    private let recurringGrantsJson = """
    {"version": "rg_ios_v1", "enabled": true, "grants": [
      {"product_id": "vip_pass", "period_hours": 24, "reward_type": "gem", "reward_amount": 500}]}
    """
    private let offerwallJson = #"{"version": "ow_ios_v1", "enabled": true}"#
    private let crmJson = #"{"version": "crm_ios_v1", "enabled": true}"#

    private func fullPayload() -> [String: String] {
        [
            "brain_policy_ios": brainPolicyJson,
            "paywall_config_ios": paywallJson,
            "purchase_routing_ios": routingJson,
            "subscription_lifecycle_ios": subscriptionJson,
            "battle_pass_ios": battlePassJson,
            "recurring_grants_ios": recurringGrantsJson,
            "offerwall_config_ios": offerwallJson,
            "crm_bridge_ios": crmJson
        ]
    }

    /// Every subsystem the dispatcher writes into, held so the test can assert on it. All are
    /// FRESH instances, never the `.shared` singletons: a dispatcher test must not leave global
    /// state behind for whatever test runs next.
    private struct Subsystems {
        let policy: BrainPolicy
        let guardrails: GuardrailEngine
        let bandit: BanditPolicy
        let engine: DecisionEngine
        let paywall: PaywallManager
        let router: PurchaseRouter
        let lifecycle: SubscriptionLifecycle
        let battlePass: BattlePass
        let grants: RecurringGrants
        let offerwall: OfferwallManager
        let crm: CrmBridge
    }

    private func makeSubsystems() -> Subsystems {
        let policy = BrainPolicy()
        let guardrails = GuardrailEngine()
        let bandit = BanditPolicy()
        return Subsystems(
            policy: policy,
            guardrails: guardrails,
            bandit: bandit,
            engine: DecisionEngine(
                bandit: bandit,
                guardrails: guardrails,
                store: InMemoryStore(),
                policy: policy
            ),
            paywall: PaywallManager(),
            router: PurchaseRouter(),
            lifecycle: SubscriptionLifecycle(),
            battlePass: BattlePass(),
            grants: RecurringGrants(),
            offerwall: OfferwallManager(),
            crm: CrmBridge()
        )
    }

    private func makeDispatcher(_ s: Subsystems) -> RemoteConfigDispatcher {
        RemoteConfigDispatcher(
            decisionEngine: s.engine,
            paywall: s.paywall,
            purchaseRouter: s.router,
            subscriptionLifecycle: s.lifecycle,
            battlePass: s.battlePass,
            recurringGrants: s.grants,
            offerwall: s.offerwall,
            crmBridge: s.crm
        )
    }

    private var subsystems: Subsystems!

    override func setUp() {
        super.setUp()
        subsystems = makeSubsystems()
    }

    override func tearDown() {
        // `BrainPolicy.apply` writes into process-wide singletons (`ClickFraudGuard`,
        // `HoldoutManager`, `RewardModel`, `Entitlements`, `AdsBrain.rolloutFraction`).
        // `resetForTest` puts back what it wrote — without this, a dispatcher test would leak
        // a guardrail change into every later test in the suite.
        subsystems?.policy.resetForTest()
        subsystems = nil
        super.tearDown()
    }

    // MARK: - The key contract

    /// The eight console key names, in Android's dispatch order.
    ///
    /// This test is deliberately a hard-coded literal list rather than anything derived: these
    /// strings are a CONTRACT with the Firebase console. A rename that compiles is a rename that
    /// silently stops loading config in production, and the only thing that can catch it is a
    /// test that spells the names out. The order is Android's `parserCfWithRemoteCf` order, kept
    /// so the two platforms apply an artifact set in the same sequence.
    func test_keysAndOrderMatchTheAndroidDispatchContract() {
        XCTAssertEqual(
            RemoteConfigDispatcher.keys,
            [
                "brain_policy_ios",
                "paywall_config_ios",
                "purchase_routing_ios",
                "subscription_lifecycle_ios",
                "battle_pass_ios",
                "recurring_grants_ios",
                "offerwall_config_ios",
                "crm_bridge_ios"
            ]
        )
    }

    /// Every key carries the `_ios` suffix — never `_android`. Sharing one key across platforms
    /// would mean an iOS-only cap change silently re-tuning the Android fleet.
    func test_everyKeyIsPlatformSuffixed() {
        for key in RemoteConfigDispatcher.keys {
            XCTAssertTrue(key.hasSuffix("_ios"), "\(key) is not iOS-scoped")
        }
    }

    // MARK: - Dispatch

    func test_eachPayloadReachesItsOwnSubsystem() {
        let report = makeDispatcher(subsystems).apply(from: FakeProvider(values: fullPayload()))

        XCTAssertEqual(report.appliedKeys, RemoteConfigDispatcher.keys, report.describe())
        XCTAssertEqual(subsystems.policy.activeVersion(), "brain_ios_v1")
        XCTAssertEqual(subsystems.paywall.activeVersion(), "paywall_ios_v1")
        XCTAssertEqual(subsystems.router.activeVersion(), "routing_ios_v1")
        XCTAssertEqual(subsystems.lifecycle.activeVersion(), "sub_ios_v1")
        XCTAssertEqual(subsystems.battlePass.activeVersion(), "bp_ios_v1")
        XCTAssertEqual(subsystems.grants.activeVersion(), "rg_ios_v1")
        XCTAssertEqual(subsystems.offerwall.activeVersion(), "ow_ios_v1")
        XCTAssertEqual(subsystems.crm.activeVersion(), "crm_ios_v1")
    }

    /// The brain artifact must go through the ENGINE, not straight to `BrainPolicy` — that is
    /// the path that also carries the exploration floor into the bandit, and the guardrail cap
    /// moving is the observable proof the engine's own guardrails were the ones handed over.
    func test_brainPolicyIsAppliedThroughTheEngineSoItsGuardrailsMove() {
        _ = makeDispatcher(subsystems).apply(from: FakeProvider(values: fullPayload()))

        XCTAssertEqual(subsystems.engine.adCapPerDay(), 7)
    }

    /// The other half of "through the engine", and the one a dispatcher-level test has to make
    /// itself: `exploration_floor` lives in the same artifact and reaches a DIFFERENT object.
    /// Asserting only the guardrail cap would leave the dispatcher's own doc comment — "carries
    /// `exploration_floor` to the bandit" — untested at this layer.
    func test_theBrainArtifactsExplorationFloorReachesTheBandit() {
        _ = makeDispatcher(subsystems).apply(from: FakeProvider(values: fullPayload()))

        XCTAssertEqual(subsystems.bandit.explorationFloor, 0.21, accuracy: 1e-9)
    }

    func test_absentKeyIsSkippedAndLeavesItsSubsystemUntouched() {
        var payload = fullPayload()
        payload.removeValue(forKey: "crm_bridge_ios")

        let report = makeDispatcher(subsystems).apply(from: FakeProvider(values: payload))

        XCTAssertEqual(report.absentKeys, ["crm_bridge_ios"])
        XCTAssertFalse(report.appliedKeys.contains("crm_bridge_ios"))
        XCTAssertEqual(subsystems.crm.activeVersion(), "not loaded")
        // The other seven still landed.
        XCTAssertEqual(report.appliedKeys.count, 7)
    }

    /// Android guards every key with `isNotEmpty`. An empty string is Remote Config's "no value
    /// set" — handing it to `apply` would log a parse rejection for a key nobody configured.
    func test_emptyValueIsTreatedAsAbsentNotAsAMalformedArtifact() {
        var payload = fullPayload()
        payload["offerwall_config_ios"] = ""

        let report = makeDispatcher(subsystems).apply(from: FakeProvider(values: payload))

        XCTAssertEqual(report.absentKeys, ["offerwall_config_ios"])
        XCTAssertTrue(report.rejectedKeys.isEmpty, report.describe())
    }

    /// The property that makes eight separate keys worth having: a broken artifact costs you
    /// that subsystem's update and nothing else. One shared key would lose all eight.
    func test_oneMalformedArtifactDoesNotStopTheOtherSeven() {
        var payload = fullPayload()
        payload["battle_pass_ios"] = "{not valid json"

        let report = makeDispatcher(subsystems).apply(from: FakeProvider(values: payload))

        XCTAssertEqual(report.rejectedKeys, ["battle_pass_ios"])
        XCTAssertEqual(report.appliedKeys.count, 7)
        XCTAssertEqual(subsystems.battlePass.activeVersion(), "not loaded")
        XCTAssertEqual(subsystems.crm.activeVersion(), "crm_ios_v1")
    }

    /// `refresh` must fetch first: reading a cold cache and then fetching would apply the
    /// PREVIOUS session's config and hold the new one until the next launch.
    func test_refreshFetchesBeforeReadingSoAColdStartSeesTheNewValues() async {
        let provider = FakeProvider(values: [:], pending: fullPayload())

        let report = await makeDispatcher(subsystems).refresh(from: provider)

        XCTAssertEqual(provider.fetchCount, 1)
        XCTAssertEqual(report.appliedKeys, RemoteConfigDispatcher.keys, report.describe())
    }

    /// A failed fetch is not an error path of its own: Remote Config keeps serving the last
    /// activated values, so the dispatch still runs against whatever is cached.
    func test_applyWithNothingConfiguredIsAnEmptyReportNotAFailure() {
        let report = makeDispatcher(subsystems).apply(from: FakeProvider())

        XCTAssertEqual(report.absentKeys, RemoteConfigDispatcher.keys)
        XCTAssertTrue(report.appliedKeys.isEmpty)
        XCTAssertTrue(report.rejectedKeys.isEmpty)
    }

    // MARK: - Report

    func test_describeNamesEveryKeyWithItsOutcome() {
        var payload = fullPayload()
        payload["battle_pass_ios"] = "{not valid json"
        payload.removeValue(forKey: "crm_bridge_ios")

        let described = makeDispatcher(subsystems).apply(from: FakeProvider(values: payload)).describe()

        for key in RemoteConfigDispatcher.keys {
            XCTAssertTrue(described.contains(key), "describe() never mentions \(key):\n\(described)")
        }
        XCTAssertTrue(described.contains("rejected"), described)
        XCTAssertTrue(described.contains("absent"), described)
        XCTAssertTrue(described.contains("applied"), described)
    }

    /// The per-key report text comes from the subsystem itself, so an operator reading the log
    /// sees WHY an artifact was refused rather than just that it was.
    func test_rejectionCarriesTheSubsystemsOwnExplanation() {
        let report = makeDispatcher(subsystems)
            .apply(from: FakeProvider(values: ["subscription_lifecycle_ios": "{not valid json"]))

        let result = report.results.first { $0.key == "subscription_lifecycle_ios" }
        XCTAssertEqual(result?.status, .rejected)
        XCTAssertFalse(result?.report.isEmpty ?? true)
    }
}
