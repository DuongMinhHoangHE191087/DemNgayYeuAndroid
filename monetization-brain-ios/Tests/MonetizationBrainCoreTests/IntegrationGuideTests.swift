import XCTest
// ═══ PLAIN `import`, NOT `@testable` — THAT IS THE POINT OF THIS FILE ═══
//
// `@testable` exposes internal symbols, so a guide "verified" under it could be telling
// integrators to call things they cannot reach. This file compiles against exactly the surface a
// host app sees, which is the only way the verification means anything. It found nothing when the
// switch was made — every call the guide shows is genuinely public — and it will fail the day one
// of them stops being.
import MonetizationBrainCore

/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// THE INTEGRATION GUIDE, COMPILED
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// `docs/INTEGRATION.md` is the one document a host app actually reads, and a sample in it that
/// does not compile costs an integrator an afternoon and costs this SDK their confidence. The
/// first draft of that guide contained a `.with { }` helper that does not exist in this package —
/// written from memory, plausible, wrong.
///
/// So every call the guide shows is made here. This is not a behaviour test: it asserts almost
/// nothing. Its job is to FAIL TO COMPILE the day someone renames a method, changes an argument
/// label or moves a type, so the guide cannot rot quietly while the README says it is current.
///
/// If you change a signature and this file stops compiling, fix the guide in the same commit.
final class IntegrationGuideTests: XCTestCase {

    private final class FakeEventSink: EventSink, @unchecked Sendable {
        func send(_ event: SdkEvent) async {}
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

    /// §3 Start-up, minus the two vendor lines (`FirebaseApp.configure()` and
    /// `StoreKit2Backend()`) that live in targets this test target does not link.
    func test_theStartUpSampleCompilesAndReports() async {
        let remoteConfig = StaticRemoteConfigProvider()

        var config = MonetizationBrainSDK.Config(
            eventSink: FakeEventSink(),
            playerId: "player-42",
            remoteConfig: remoteConfig
        )
        config.brainEnabled = true
        config.brainRolloutFraction = 0.05
        config.inappProducts = ["gem_pack_1", "gem_pack_2"]
        config.subscriptionProducts = ["vip_monthly"]
        config.removeAdsProducts = ["vip_monthly", "remove_ads"]
        config.childDirected = false
        config.observeAppLifecycle = false          // no UIKit observers in a test process
        config.decisionEngine = DecisionEngine(store: InMemoryStore())

        let report = await MonetizationBrainSDK.configure(config)
        XCTAssertFalse(report.describe().isEmpty)

        let applied = await RemoteConfigDispatcher.shared.refresh(from: remoteConfig)
        XCTAssertFalse(applied.describe().isEmpty)

        // The guide promises this warns when products are declared with no store backend.
        XCTAssertTrue(
            report.warnings.contains { $0.contains("storeBackend") },
            "the guide tells integrators to read warnings; this is one of the ones it names"
        )
    }

    /// §4 The five decision points, exactly as written.
    func test_theDecisionPointSamplesCompile() async {
        _ = await AdsBrain.shared.shouldShowInterstitial()
        _ = await AdsBrain.shared.shouldOfferRewarded()
        _ = await AdsBrain.shared.shouldOfferIap(decisionPoint: .shopOpen)

        switch await AdsBrain.shared.chooseCrossModal(decisionPoint: .levelFail).action {
        case .rewardedAd, .iapOffer, .noAction, .freeAction, .interstitial, .gameplayConfig:
            break
        }

        switch await AdsBrain.shared.chooseAssist(decisionPoint: .levelFail).action {
        case .freeAction, .gameplayConfig, .noAction, .rewardedAd, .interstitial, .iapOffer:
            break
        }
    }

    /// §5 Feedback hooks, every one the guide lists.
    func test_theFeedbackSamplesCompile() async {
        AdsBrain.shared.onInterstitialShown()
        AdsBrain.shared.onAdSkipped()
        AdsBrain.shared.onIapOfferShown()
        AdsBrain.shared.onAssistShown()

        await AdsBrain.shared.onRewardedOffered(accepted: true)
        await AdsBrain.shared.onAssistUsed(used: true)
        await AdsBrain.shared.onLevelFail()
        await AdsBrain.shared.onLevelWin()
        await AdsBrain.shared.onPlayerContinued()
        await AdsBrain.shared.onPurchase(usd: 4.99)
        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onSessionEnd()
    }

    /// §6 Selling, and the single place to ask whether ads are off for this player.
    func test_theSellingSamplesCompile() async {
        _ = await IapHelper.shared.queryProducts()
        let result = await IapHelper.shared.purchase("gem_pack_1")
        if case .success = result {}
        _ = Entitlements.shared.hasRemoveAds()
    }

    /// §6 The attribution conformance the guide says is ~15 lines, written out to prove it is.
    private struct GuideAttributionBridge: AttributionBridge {
        func logPurchase(_ purchase: AttributionPurchase) async {
            _ = (purchase.revenue, purchase.currencyCode, purchase.productId,
                 purchase.quantity, purchase.isSubscription)
        }
    }

    func test_theAttributionConformanceCompilesAndPlugsIn() {
        _ = IapHelper.Configuration(
            backend: NoOpStoreBackendForGuide(),
            eventSink: FakeEventSink(),
            playerId: "player-42",
            attribution: GuideAttributionBridge()
        )
    }

    /// §7 The eight console keys, named in the guide and readable from code.
    func test_theRemoteConfigKeysMatchTheGuide() {
        XCTAssertEqual(
            RemoteConfigDispatcher.keys,
            [
                "brain_policy_ios", "paywall_config_ios", "purchase_routing_ios",
                "subscription_lifecycle_ios", "battle_pass_ios", "recurring_grants_ios",
                "offerwall_config_ios", "crm_bridge_ios"
            ],
            "the guide prints these keys; they must be the ones the dispatcher reads"
        )
    }

    /// §8 The two diagnostics, which answer different questions.
    func test_theDiagnosticSamplesCompile() {
        _ = MonetizationBrainSDK.lastReport()?.describe()
        let selfCheck = AdsBrain.shared.selfCheckReport()
        _ = (selfCheck.isConfigured, selfCheck.isEnabled, selfCheck.consentTier, selfCheck.issues)
        if let state = AdsBrain.shared.currentState() {
            _ = BrainInspector.format(
                BrainInspector.shared.snapshot(engine: DecisionEngine.shared, state: state)
            )
        }
    }

    private struct NoOpStoreBackendForGuide: StoreBackend {
        func products(for identifiers: [String]) async throws -> [IapProduct] { [] }
        func purchase(_ product: IapProduct) async throws -> IapResult { .userCanceled }
        func currentEntitlements() async throws -> [IapPurchase] { [] }
        func subscriptionSnapshots() async throws -> [SubscriptionSnapshot] { [] }
        func transactionUpdates() -> AsyncStream<IapPurchase> { AsyncStream { $0.finish() } }
        func finish(_ purchase: IapPurchase) async {}
    }
}
