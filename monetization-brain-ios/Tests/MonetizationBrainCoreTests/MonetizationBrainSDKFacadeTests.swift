import XCTest
@testable import MonetizationBrainCore

final class MonetizationBrainSDKFacadeTests: XCTestCase {
    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? { lock.lock(); defer { lock.unlock() }; return values[key] }
        func setString(_ value: String, forKey key: String) { lock.lock(); values[key] = value; lock.unlock() }
        func removeObject(forKey key: String) { lock.lock(); _ = values.removeValue(forKey: key); lock.unlock() }
    }

    private final class FakeStoreBackend: StoreBackend, @unchecked Sendable {
        func products(for identifiers: [String]) async throws -> [IapProduct] { [] }
        func purchase(_ product: IapProduct) async throws -> IapResult { .userCanceled }
        func currentEntitlements() async throws -> [IapPurchase] { [] }
        func subscriptionSnapshots() async throws -> [SubscriptionSnapshot] { [] }
        func transactionUpdates() -> AsyncStream<IapPurchase> { AsyncStream { $0.finish() } }
        func finish(_ purchase: IapPurchase) async {}
    }

    // Per-file fake, redeclared here per this project's convention (no shared test fakes).

private struct NoOpEventSender: EventSender {
    func send(_ event: SdkEvent) async throws {}
}

    private final class FakeClock: BrainClock, @unchecked Sendable {
        private let lock = NSLock()
        private var current: Int64
        init(_ start: Int64) { self.current = start }
        func nowMs() -> Int64 { lock.lock(); defer { lock.unlock() }; return current }
        func advance(ms: Int64) { lock.lock(); current += ms; lock.unlock() }
    }

    override func setUp() {
        super.setUp()
        resetAll()
    }

    override func tearDown() {
        resetAll()
        super.tearDown()
    }

    private func resetAll() {
        MonetizationBrainSDK.resetForTest()
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        Entitlements.shared.resetForTest()
        HoldoutManager.holdoutFraction = 0.05
        UserDefaults.standard.removeObject(forKey: BrainStorage.offerCountKey)
        UserDefaults.standard.removeObject(forKey: BrainStorage.offerDayKey)
    }

    private func config(_ mutate: (inout MonetizationBrainSDK.Config) -> Void = { _ in }) -> MonetizationBrainSDK.Config {
        var c = MonetizationBrainSDK.Config(eventSink: FakeEventSink(), playerId: "p1")
        c.observeAppLifecycle = false   // no UIKit observers in a test process
        c.decisionEngine = DecisionEngine(store: InMemoryStore())
        mutate(&c)
        return c
    }

    // MARK: - The order is the product

    /// Locks the sequence the way Android's own test does. Every step is here because a specific
    /// bug lives on the other side of its ordering — see the facade's banner.
    func test_appliedOrderIsLockedAndComplete() async {
        let report = await MonetizationBrainSDK.configure(config {
            $0.removeAdsProducts = ["remove_ads"]
            $0.inappProducts = ["remove_ads", "gems"]
            $0.subscriptionProducts = ["vip"]
            $0.storeBackend = FakeStoreBackend()
            $0.childDirected = false
            $0.underAgeOfConsent = false
            $0.brainEnabled = true
            $0.observeAppLifecycle = true
        })
        XCTAssertEqual(report.appliedOrder, [
            "entitlements.removeAdsProducts",
            "iap.setProducts",
            "iap.configure",
            "consent.childDirected",
            "consent.underAgeOfConsent",
            "brain.configure",
            "brain.rolloutFraction",
            "brain.enabled",
            "brain.onSessionStart",
            "lifecycle.observe"
        ])
    }

    func test_optionalStepsAreSkippedAndNotReported() async {
        let report = await MonetizationBrainSDK.configure(config())
        XCTAssertEqual(report.appliedOrder, [
            "brain.configure", "brain.rolloutFraction", "brain.enabled", "brain.onSessionStart"
        ])
        XCTAssertFalse(report.billingStarted)
        XCTAssertEqual(report.productCount, 0)
    }

    // MARK: - What it actually did

    func test_configureWiresEntitlementsProductsBrainAndSession() async {
        let report = await MonetizationBrainSDK.configure(config {
            $0.removeAdsProducts = ["remove_ads"]
            $0.inappProducts = ["remove_ads"]
            $0.storeBackend = FakeStoreBackend()
            $0.brainEnabled = true
            $0.brainRolloutFraction = 0.25
        })
        XCTAssertEqual(Entitlements.shared.removeAdsProductIds, ["remove_ads"])
        XCTAssertTrue(IapHelper.shared.isReady())
        XCTAssertTrue(report.billingStarted)
        XCTAssertTrue(AdsBrain.shared.enabled)
        XCTAssertEqual(AdsBrain.shared.rolloutFraction, 0.25)
        XCTAssertEqual(AdsBrain.shared.currentState()?.sessionsLifetime, 1, "the first session was started")
        XCTAssertEqual(report.brainEnabled, true)
        XCTAssertEqual(report.brainRolloutFraction, 0.25)
        XCTAssertEqual(report.holdoutFraction, 0.05)
        XCTAssertEqual(report.consentTier, .unknown)
        XCTAssertEqual(MonetizationBrainSDK.lastReport(), report)
    }

    func test_rolloutFractionIsClampedIntoTheUnitInterval() async {
        let report = await MonetizationBrainSDK.configure(config { $0.brainRolloutFraction = 7.5 })
        XCTAssertEqual(report.brainRolloutFraction, 1.0)
        let low = await MonetizationBrainSDK.configure(config { $0.brainRolloutFraction = -3 })
        XCTAssertEqual(low.brainRolloutFraction, 0.0)
    }

    func test_queuedEventsIsReportedOnlyForAnEventQueue() async {
        let plain = await MonetizationBrainSDK.configure(config())
        XCTAssertNil(plain.queuedEvents, "a plain sink cannot be counted")

        let dir = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let queue = EventQueue(directory: dir, sender: NoOpEventSender())
        var c = config()
        c.eventSink = queue
        let queued = await MonetizationBrainSDK.configure(c)
        XCTAssertNotNil(queued.queuedEvents)
    }

    // MARK: - Warnings: the misconfigurations nothing else reports

    func test_warnsWhenBrainIsEnabledButRolloutIsZero() async {
        let report = await MonetizationBrainSDK.configure(config {
            $0.brainEnabled = true
            $0.brainRolloutFraction = 0
            $0.consentHandledExternally = true
        })
        XCTAssertEqual(report.warnings.count, 1)
        XCTAssertTrue(report.warnings[0].contains("controls no player"), report.warnings[0])
    }

    func test_warnsOnRemoveAdsProductThatIsNotDeclared() async {
        let report = await MonetizationBrainSDK.configure(config {
            $0.inappProducts = ["gems"]
            $0.removeAdsProducts = ["remove_adz"]   // typo
            $0.storeBackend = FakeStoreBackend()
            $0.consentHandledExternally = true
        })
        XCTAssertTrue(report.warnings.contains { $0.contains("'remove_adz'") }, "\(report.warnings)")
    }

    func test_warnsWhenSubscriptionsExistButNothingRemovesAds() async {
        let report = await MonetizationBrainSDK.configure(config {
            $0.subscriptionProducts = ["vip"]
            $0.storeBackend = FakeStoreBackend()
            $0.consentHandledExternally = true
        })
        XCTAssertTrue(report.warnings.contains { $0.contains("subscribers will still see ads") }, "\(report.warnings)")
    }

    /// iOS-specific: Android skips billing silently when there is no Context (a test-only path).
    /// A host that declares products and forgets the backend has no other symptom until the
    /// first purchase fails, so it is a warning here.
    func test_warnsWhenProductsAreDeclaredButThereIsNoStoreBackend() async {
        let report = await MonetizationBrainSDK.configure(config {
            $0.inappProducts = ["gems"]
            $0.consentHandledExternally = true
        })
        XCTAssertFalse(report.billingStarted)
        XCTAssertTrue(report.warnings.contains { $0.contains("storeBackend is nil") }, "\(report.warnings)")
    }

    func test_consentWarningIsSilencedByConsentHandledExternallyAndByAGrantedTier() async {
        let unknown = await MonetizationBrainSDK.configure(config())
        XCTAssertTrue(unknown.warnings.contains { $0.contains("UNKNOWN") }, "\(unknown.warnings)")

        let external = await MonetizationBrainSDK.configure(config { $0.consentHandledExternally = true })
        XCTAssertFalse(external.warnings.contains { $0.contains("UNKNOWN") })

        ConsentState.shared.setTier(.granted)
        let granted = await MonetizationBrainSDK.configure(config())
        XCTAssertFalse(granted.warnings.contains { $0.contains("UNKNOWN") })
        XCTAssertEqual(granted.consentTier, .granted)
    }

    func test_aCleanConfigHasNoWarnings() async {
        ConsentState.shared.setTier(.granted)
        let report = await MonetizationBrainSDK.configure(config {
            $0.inappProducts = ["remove_ads"]
            $0.subscriptionProducts = ["vip"]
            $0.removeAdsProducts = ["remove_ads", "vip"]
            $0.storeBackend = FakeStoreBackend()
            $0.brainEnabled = true
        })
        XCTAssertEqual(report.warnings, [], report.describe())
        XCTAssertTrue(report.describe().contains("✓ no configuration warnings"))
    }

    func test_describeRendersTheReport() async {
        let report = await MonetizationBrainSDK.configure(config { $0.brainEnabled = true })
        let text = report.describe()
        XCTAssertTrue(text.contains("brain      : ON"))
        XCTAssertTrue(text.contains("rollout    : 100% of players"))
        XCTAssertTrue(text.contains("holdout    : 5% (control group)"))
        XCTAssertTrue(text.contains("NO training service"), "the honesty line about no global learning must be there")
        XCTAssertTrue(text.contains("⚠"), "an UNKNOWN consent tier must surface as a warning")
    }

    // MARK: - Session lifecycle decision (pure, no UIKit)

    func test_aForegroundIsANewSessionOnlyAfterTheThirtyMinuteGap() {
        let bg: Int64 = 1_700_000_000_000
        XCTAssertFalse(SessionLifecycle.isNewSession(backgroundedAtMs: 0, nowMs: bg), "never backgrounded")
        XCTAssertFalse(SessionLifecycle.isNewSession(backgroundedAtMs: bg, nowMs: bg + 29 * 60_000), "a quick app switch is not a new session")
        XCTAssertTrue(SessionLifecycle.isNewSession(backgroundedAtMs: bg, nowMs: bg + 30 * 60_000), "exactly 30 minutes counts")
        XCTAssertTrue(SessionLifecycle.isNewSession(backgroundedAtMs: bg, nowMs: bg + 3 * 60 * 60_000))
        XCTAssertFalse(SessionLifecycle.isNewSession(backgroundedAtMs: bg, nowMs: bg - 60_000), "a clock that moved backwards is not 'long enough'")
    }

    func test_foregroundAfterAGapStartsASessionAndAlwaysRestoresWhenIapIsReady() async {
        let clock = FakeClock(1_700_000_000_000)
        var started = 0, restored = 0
        await SessionLifecycle.handleBackground(nowMs: clock.nowMs(), endSession: {})

        // Quick return: no new session, but entitlements are still re-synced.
        clock.advance(ms: 5 * 60_000)
        await SessionLifecycle.handleForeground(
            nowMs: clock.nowMs(), startSession: { started += 1 },
            iapIsReady: { true }, restore: { restored += 1 }
        )
        XCTAssertEqual(started, 0)
        XCTAssertEqual(restored, 1, "a cancelled subscription must be noticed on every foreground, not only after 30 minutes")

        // Long absence: new session.
        await SessionLifecycle.handleBackground(nowMs: clock.nowMs(), endSession: {})
        clock.advance(ms: 31 * 60_000)
        await SessionLifecycle.handleForeground(
            nowMs: clock.nowMs(), startSession: { started += 1 },
            iapIsReady: { false }, restore: { restored += 1 }
        )
        XCTAssertEqual(started, 1)
        XCTAssertEqual(restored, 1, "no restore when billing is not ready")
    }

    func test_backgroundAlwaysEndsTheSession() async {
        var ended = 0
        await SessionLifecycle.handleBackground(nowMs: 1_700_000_000_000, endSession: { ended += 1 })
        XCTAssertEqual(ended, 1, "an app backgrounded right after an ad is the negative signal the reward model needs")
    }
    /// ═══ THE VALUE `min(max(x, 0), 1)` LETS THROUGH ═══
    ///
    /// Every comparison with NaN is false, so the clamp returns the NaN unchanged. Two things
    /// then happen, and the quiet one is worse: `isInRollout` guards on `isFinite`, so a NaN
    /// means NOBODY is in the rollout — the brain is off for the whole population while
    /// `brainEnabled` is true and nothing says so — and `describe()`, the string someone prints
    /// BECAUSE something looks wrong, traps converting it to a percentage and kills the app.
    ///
    /// A NaN here is ordinary: it is what `Double("")` and a missing remote-config field produce.
    func test_aNaNRolloutFractionIsTreatedAsZeroWarnedAboutAndSafeToPrint() async {
        let report = await MonetizationBrainSDK.configure(config {
            $0.brainEnabled = true
            $0.brainRolloutFraction = .nan
        })

        XCTAssertEqual(report.brainRolloutFraction, 0.0)
        XCTAssertTrue(
            report.warnings.contains { $0.contains("not a number") },
            "a NaN rollout must not be silent: \(report.warnings)"
        )
        // The assertion that matters is that this line RUNS at all.
        XCTAssertTrue(report.describe().contains("rollout    : 0%"), report.describe())
    }

    /// `holdoutFraction` is a public `static var`, so a host can leave a NaN in it whatever the
    /// SDK does with its own inputs. `describe()` has to survive that too.
    func test_describeSurvivesANaNHoldoutFraction() async {
        HoldoutManager.holdoutFraction = .nan
        defer { HoldoutManager.holdoutFraction = 0.05 }

        let report = await MonetizationBrainSDK.configure(config {
            $0.brainEnabled = true
            $0.brainRolloutFraction = 0.5
        })

        XCTAssertTrue(report.describe().contains("holdout    : 0%"), report.describe())
    }


}
