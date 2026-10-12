import XCTest
@testable import MonetizationBrainCore

/// `DecisionEngine.applyPolicy` — the public way to hand a policy artifact to the brain.
///
/// It exists as a method on the ENGINE rather than on `BrainPolicy` for two reasons, and both
/// are what these tests pin down: the engine owns the `GuardrailEngine` the artifact writes into
/// (it is private, and deliberately so — see `adCapPerDay`), and the artifact's exploration
/// floor has to reach the engine's `BanditPolicy`, which nothing outside can see either.
final class DecisionEnginePolicyTests: XCTestCase {

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

    private struct Fixture {
        let engine: DecisionEngine
        let bandit: BanditPolicy
        let guardrails: GuardrailEngine
        let policy: BrainPolicy
    }

    private func makeFixture() -> Fixture {
        let bandit = BanditPolicy()
        let guardrails = GuardrailEngine()
        let policy = BrainPolicy()
        return Fixture(
            engine: DecisionEngine(
                bandit: bandit,
                guardrails: guardrails,
                store: InMemoryStore(),
                policy: policy
            ),
            bandit: bandit,
            guardrails: guardrails,
            policy: policy
        )
    }

    private var fixture: Fixture!

    override func setUp() {
        super.setUp()
        fixture = makeFixture()
    }

    override func tearDown() {
        fixture?.policy.resetForTest()
        fixture = nil
        super.tearDown()
    }

    func test_applyPolicyWritesIntoTheEnginesOwnGuardrails() {
        let result = fixture.engine.applyPolicy(#"{"version": "v1", "guardrails": {"max_ads_24h": 7}}"#)

        XCTAssertTrue(result.applied, result.report)
        XCTAssertEqual(fixture.guardrails.maxAds24h, 7)
        XCTAssertEqual(fixture.engine.adCapPerDay(), 7)
    }

    /// The whole reason this method is not just `BrainPolicy.apply`: Android's `applyPolicy`
    /// pushes the parsed floor into the bandit, and without this hop the artifact's
    /// `exploration_floor` is parsed, clamped, stored — and then ignored.
    func test_anArtifactExplorationFloorReachesTheBandit() {
        let result = fixture.engine.applyPolicy(
            #"{"version": "v1", "experiment": {"exploration_floor": 0.11}}"#
        )

        XCTAssertTrue(result.applied, result.report)
        XCTAssertEqual(fixture.bandit.explorationFloor, 0.11, accuracy: 1e-9)
    }

    /// ═══ THE CASE THAT FORCED `pendingExplorationFloor` TO BE OPTIONAL ═══
    ///
    /// Most artifacts never mention `exploration_floor`. If the accessor returned a non-nil
    /// default, every single `applyPolicy` call would overwrite `BanditPolicy`'s own floor with
    /// `BrainPolicy`'s — two different constants, so loading a paywall-only artifact would
    /// silently re-tune exploration and, with it, every logged propensity. Android models this
    /// as `Double? = null` and only pushes on non-null; so does this port.
    func test_anArtifactWithoutAnExplorationFloorLeavesTheBanditAlone() {
        // Driven AWAY from `BanditPolicy`'s compiled default first, and that is the whole point
        // of the line. Starting from the default, "left untouched" and "reset to the default"
        // are the same observation, and the mutation `pendingExplorationFloor ?? 0.03` — which
        // is exactly the bug this method exists to prevent, just with a different constant —
        // would pass. From 0.41, only a genuine no-op survives.
        fixture.bandit.explorationFloor = 0.41

        let result = fixture.engine.applyPolicy(#"{"version": "v1", "guardrails": {"max_ads_24h": 7}}"#)

        XCTAssertTrue(result.applied, result.report)
        XCTAssertEqual(fixture.bandit.explorationFloor, 0.41, accuracy: 1e-9)
        XCTAssertNil(fixture.policy.pendingExplorationFloor)
    }

    /// Concurrent applies: a deadlock and consistency check, and NOT evidence that `applyLock`
    /// works.
    ///
    /// ═══ WHY THAT DISTINCTION IS WRITTEN DOWN ═══
    ///
    /// `applyPolicy` is three lock acquisitions across two objects — `BrainPolicy` stores the
    /// parsed floor, `pendingExplorationFloor` reads it back, the bandit takes the write — so two
    /// overlapping applies could leave the bandit running artifact A's floor while
    /// `activeVersion()` reports artifact B. `applyLock` closes that window, and this test was
    /// written to prove it by failing without the lock. **It does not.** Removing `applyLock` and
    /// running this repeatedly never produced a mismatch: the window is sub-microsecond, and
    /// `BrainPolicy` is `final`, so there is no seam to widen it without putting a test-only hook
    /// into production code.
    ///
    /// So the lock stands on its reasoning, not on this test, and that is said plainly here
    /// rather than left for the next reader to discover — a concurrency test with no engineered
    /// overlap that passes either way is worse than no test, because it looks like coverage.
    ///
    /// What this DOES earn its place for: it fails loudly if `applyPolicy` ever deadlocks (say,
    /// someone calls it from a path already holding a lock it takes), and it pins the
    /// floor/version pairing as an invariant a future refactor must not break.
    func test_concurrentAppliesNeverLeaveTheFloorAndVersionMismatched() {
        let artifacts = [
            (version: "a", floor: 0.1, json: #"{"version": "a", "experiment": {"exploration_floor": 0.1}}"#),
            (version: "b", floor: 0.4, json: #"{"version": "b", "experiment": {"exploration_floor": 0.4}}"#)
        ]

        for _ in 0..<200 {
            DispatchQueue.concurrentPerform(iterations: artifacts.count) { index in
                self.fixture.engine.applyPolicy(artifacts[index].json)
            }

            let winner = fixture.policy.activeVersion()
            guard let expected = artifacts.first(where: { $0.version == winner })?.floor else {
                return XCTFail("unexpected active version \(winner)")
            }
            XCTAssertEqual(
                fixture.bandit.explorationFloor,
                expected,
                accuracy: 1e-9,
                "version '\(winner)' is in force but the bandit is running another artifact's floor"
            )
        }
    }

    /// A rejected artifact must change NOTHING — the guardrails keep the last good values and
    /// the bandit keeps its floor. "Config is broken" and "config says be unrestricted" must
    /// never look the same to the engine.
    func test_aRejectedArtifactLeavesGuardrailsAndBanditUntouched() {
        _ = fixture.engine.applyPolicy(
            #"{"version": "good_v1", "guardrails": {"max_ads_24h": 7}, "experiment": {"exploration_floor": 0.11}}"#
        )

        let result = fixture.engine.applyPolicy("{not valid json")

        XCTAssertFalse(result.applied)
        XCTAssertEqual(fixture.engine.adCapPerDay(), 7)
        XCTAssertEqual(fixture.bandit.explorationFloor, 0.11, accuracy: 1e-9)
        XCTAssertEqual(fixture.policy.activeVersion(), "good_v1")
    }

    func test_nilArtifactIsRejectedRatherThanClearingThePolicy() {
        _ = fixture.engine.applyPolicy(#"{"version": "good_v1", "guardrails": {"max_ads_24h": 7}}"#)

        XCTAssertFalse(fixture.engine.applyPolicy(nil).applied)
        XCTAssertEqual(fixture.engine.adCapPerDay(), 7)
    }
}
