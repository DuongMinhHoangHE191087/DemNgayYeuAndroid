import XCTest
@testable import MonetizationBrainCore

/// Design spec §5 (Offerwall half). §2.2 (`enabled` fails open on Android), §2.3 (a vendor
/// `transactionId` carrying a persistence delimiter) and §2.4 (the load path Android structurally
/// cannot test) are the three deviations this file exists to hold in place.
final class OfferwallManagerTests: XCTestCase {
    // MARK: - File-local fakes (this package redeclares them per test file on purpose)

    /// A host offerwall adapter, recording what it was asked to do.
    private final class RecordingProvider: OfferwallProvider, @unchecked Sendable {
        let providerId: String
        private let lock = NSLock()
        private var _available: Bool
        private var _showCount = 0
        private var _availabilityChecks = 0

        init(_ providerId: String, available: Bool = true) {
            self.providerId = providerId
            self._available = available
        }

        func isAvailable() -> Bool {
            lock.lock(); defer { lock.unlock() }
            _availabilityChecks += 1
            return _available
        }

        func show(onWallClosed: @escaping @Sendable () -> Void) {
            lock.lock(); _showCount += 1; lock.unlock()
            onWallClosed()
        }

        func setAvailable(_ value: Bool) { lock.lock(); _available = value; lock.unlock() }
        func showCount() -> Int { lock.lock(); defer { lock.unlock() }; return _showCount }
        func availabilityChecks() -> Int { lock.lock(); defer { lock.unlock() }; return _availabilityChecks }
    }

    /// The provider §2.7/`CrmBridge` §2.2's rule exists for: host code calling straight back into
    /// the manager from inside `isAvailable()`/`show(...)`. `NSLock` is not reentrant, so this
    /// deadlocks unless both host callbacks happen with the lock released.
    private final class ReEnteringProvider: OfferwallProvider, @unchecked Sendable {
        let providerId = "reentrant"
        private let manager: OfferwallManager
        private let lock = NSLock()
        private var reentered = false
        private(set) var observedVersion = ""

        init(manager: OfferwallManager) { self.manager = manager }

        func isAvailable() -> Bool {
            observedVersion = manager.activeVersion()
            return true
        }

        func show(onWallClosed: @escaping @Sendable () -> Void) {
            let shouldReenter: Bool = {
                lock.lock(); defer { lock.unlock() }
                if reentered { return false }
                reentered = true
                return true
            }()
            if shouldReenter {
                manager.register(RecordingProvider("registered_from_inside_show"))
                _ = manager.availableProviders()
                _ = manager.unregister(providerId: "registered_from_inside_show")
            }
            onWallClosed()
        }
    }

    /// Blocks inside `show` until released — proves the manager stays usable by OTHER callers
    /// while a slow provider is mid-presentation.
    private final class BlockingProvider: OfferwallProvider, @unchecked Sendable {
        let providerId = "blocking"
        let entered = DispatchSemaphore(value: 0)
        let release = DispatchSemaphore(value: 0)
        func isAvailable() -> Bool { true }
        func show(onWallClosed: @escaping @Sendable () -> Void) {
            entered.signal()
            release.wait()
            onWallClosed()
        }
    }

    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        init(_ seed: [String: String] = [:]) { self.values = seed }
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

    // MARK: - Helpers

    private let enabledConfig = #"{"version": "ow_v1", "enabled": true}"#

    private func makeManager(
        _ json: String? = nil,
        store: BrainKeyValueStore = InMemoryStore(),
        eventSink: EventSink = FakeEventSink()
    ) -> OfferwallManager {
        let manager = OfferwallManager()
        manager.configure(.init(eventSink: eventSink, playerId: "p1", store: store))
        if let json { _ = manager.apply(json) }
        return manager
    }

    override func setUp() {
        super.setUp()
        ConsentState.shared.resetForTest()
        OfferwallManager.shared.resetForTest()
        // `.unknown` is the post-reset tier and it deliberately does NOT block the offerwall
        // (§3) — unlike `CrmBridge`, whose tests have to state consent explicitly. Left as-is
        // here on purpose: every gate test that cares sets its own tier.
    }

    override func tearDown() {
        ConsentState.shared.resetForTest()
        OfferwallManager.shared.resetForTest()
        super.tearDown()
    }

    // MARK: - Defaults and config parsing (§3)

    func test_beforeAnyApplyNothingOpensAndVersionIsNotLoaded() async {
        let manager = makeManager()
        let provider = RecordingProvider("revu")
        manager.register(provider)
        XCTAssertEqual(manager.activeVersion(), "not loaded")
        let result = await manager.show(providerId: "revu") {}
        XCTAssertFalse(result.shown, result.report)
        XCTAssertEqual(provider.showCount(), 0)
        XCTAssertEqual(manager.availableProviders(), [])
    }

    func test_emptyMalformedOrVersionlessConfigIsRejected() {
        let manager = makeManager()
        for json in [nil, "   ", "{not valid json", #"{"enabled": true}"#, #"{"version": "  "}"#] as [String?] {
            XCTAssertFalse(manager.apply(json).applied, String(describing: json))
        }
        XCTAssertEqual(manager.activeVersion(), "not loaded")
    }

    func test_malformedConfigKeepsTheLastGoodOneRunning() async {
        let manager = makeManager(enabledConfig)
        manager.register(RecordingProvider("revu"))
        let result = manager.apply("{not valid json")
        XCTAssertFalse(result.applied)
        XCTAssertTrue(result.report.contains("ow_v1"), result.report)
        XCTAssertEqual(manager.activeVersion(), "ow_v1")
        let show = await manager.show(providerId: "revu") {}
        XCTAssertTrue(show.shown, show.report)
    }

    // MARK: - §2.2 `enabled` must NOT fail open — the Android bug

    /// Android parses `root["enabled"] != false`, so an ABSENT key is `true` and the offerwall is
    /// live although its documented default is off. Absent means OFF here.
    func test_enabledAbsentMeansOff() async {
        let manager = makeManager(#"{"version": "ow_v1"}"#)
        let provider = RecordingProvider("revu")
        manager.register(provider)
        let result = await manager.show(providerId: "revu") {}
        XCTAssertFalse(result.shown, result.report)
        XCTAssertEqual(provider.showCount(), 0)
    }

    /// The same Android bug from the other direction: `"false"`, `0`, `1` and `[]` are none of
    /// them the Kotlin literal `false`, so all four ENABLE it there. Every one is OFF here, with
    /// a note — an operator who wrote `"enabled": "false"` needs to learn their config does not
    /// say what they think it says.
    func test_wrongTypedEnabledMeansOffWithADiagnosticNote() async {
        for raw in ["\"false\"", "\"true\"", "0", "1", "[]", "{}"] {
            let manager = makeManager()
            let provider = RecordingProvider("revu")
            manager.register(provider)
            let applied = manager.apply(#"{"version": "ow_v1", "enabled": \#(raw)}"#)
            XCTAssertTrue(applied.applied, raw)
            XCTAssertTrue(applied.report.contains("'enabled' is not a boolean"), "enabled: \(raw) -> \(applied.report)")
            XCTAssertTrue(applied.report.contains("defaulting to false"), "enabled: \(raw) -> \(applied.report)")
            let result = await manager.show(providerId: "revu") {}
            XCTAssertFalse(result.shown, "enabled: \(raw) must not open the wall")
        }
    }

    func test_enabledTrueOpensTheWallAndExplicitFalseClosesIt() async {
        let manager = makeManager(enabledConfig)
        let provider = RecordingProvider("revu")
        manager.register(provider)
        var first = await manager.show(providerId: "revu") {}
        XCTAssertTrue(first.shown, first.report)
        _ = manager.apply(#"{"version": "ow_v2", "enabled": false}"#)
        first = await manager.show(providerId: "revu") {}
        XCTAssertFalse(first.shown, first.report)
        XCTAssertEqual(provider.showCount(), 1)
    }

    // MARK: - §3 the `providers` allow-list

    func test_absentOrEmptyProvidersListMakesEveryRegisteredProviderEligible() async {
        for json in [enabledConfig, #"{"version": "ow_v1", "enabled": true, "providers": []}"#] {
            let manager = makeManager(json)
            manager.register(RecordingProvider("revu"))
            manager.register(RecordingProvider("adjoe"))
            XCTAssertEqual(Set(manager.availableProviders()), ["revu", "adjoe"], json)
        }
    }

    func test_providersAllowListFiltersToTheNamedProvidersOnly() async {
        let manager = makeManager(#"{"version": "ow_v1", "enabled": true, "providers": ["revu"]}"#)
        let revu = RecordingProvider("revu")
        let adjoe = RecordingProvider("adjoe")
        manager.register(revu)
        manager.register(adjoe)
        XCTAssertEqual(manager.availableProviders(), ["revu"])
        let blocked = await manager.show(providerId: "adjoe") {}
        XCTAssertFalse(blocked.shown, blocked.report)
        XCTAssertTrue(blocked.report.contains("allow-list"), blocked.report)
        XCTAssertEqual(adjoe.showCount(), 0)
    }

    func test_nonStringProviderEntryIsDroppedWithANote() {
        let manager = makeManager()
        let result = manager.apply(#"{"version": "ow_v1", "enabled": true, "providers": ["revu", 7, null]}"#)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("provider_id"), result.report)
        manager.register(RecordingProvider("revu"))
        manager.register(RecordingProvider("7"))
        XCTAssertEqual(manager.availableProviders(), ["revu"])
    }

    /// An allow-list that is PRESENT and unreadable is the operator explicitly narrowing with a
    /// value nobody can read — treated as naming nobody, never as naming everybody. Same
    /// direction `CrmBridge.parseAllowedSinkIds` takes.
    func test_providersThatIsNotAnArrayBlocksEveryProviderWithANote() {
        let manager = makeManager()
        let result = manager.apply(#"{"version": "ow_v1", "enabled": true, "providers": "revu"}"#)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("is not an array"), result.report)
        XCTAssertTrue(result.report.contains("NO provider"), result.report)
        manager.register(RecordingProvider("revu"))
        XCTAssertEqual(manager.availableProviders(), [])
    }

    /// §2.3 from the CONFIG side: a provider id that cannot survive the dedup key's delimiters is
    /// rejected at parse time, where it is an operator-visible error rather than a silent
    /// un-dedupable payout later.
    func test_allowListProviderIdContainingAPersistenceDelimiterIsDropped() {
        let manager = makeManager()
        let result = manager.apply(#"{"version": "ow_v1", "enabled": true, "providers": ["re,vu", "ad:joe", "ok"]}"#)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("persistence delimiter"), result.report)
        manager.register(RecordingProvider("re,vu"))
        manager.register(RecordingProvider("ok"))
        XCTAssertEqual(manager.availableProviders(), ["ok"])
    }

    // MARK: - Registration

    func test_registerReplacesByProviderIdAndKeepsRegistrationOrder() {
        let manager = makeManager(enabledConfig)
        manager.register(RecordingProvider("revu"))
        manager.register(RecordingProvider("adjoe"))
        let replacement = RecordingProvider("revu", available: false)
        manager.register(replacement)
        XCTAssertEqual(manager.availableProviders(), ["adjoe"], "the replacement reports unavailable")
        replacement.setAvailable(true)
        XCTAssertEqual(manager.availableProviders(), ["revu", "adjoe"], "registration order is preserved")
    }

    func test_unregisterReturnsTrueOnlyWhenSomethingWasActuallyRemoved() {
        let manager = makeManager(enabledConfig)
        manager.register(RecordingProvider("revu"))
        XCTAssertTrue(manager.unregister(providerId: "revu"))
        XCTAssertFalse(manager.unregister(providerId: "revu"))
        XCTAssertFalse(manager.unregister(providerId: "never_registered"))
    }

    func test_availableProvidersSkipsAProviderReportingUnavailable() {
        let manager = makeManager(enabledConfig)
        manager.register(RecordingProvider("revu", available: false))
        manager.register(RecordingProvider("adjoe"))
        XCTAssertEqual(manager.availableProviders(), ["adjoe"])
    }

    func test_availableProvidersIsEmptyWhenDisabledAndDoesNotEvenAskTheProvider() {
        let manager = makeManager(#"{"version": "ow_v1", "enabled": false}"#)
        let provider = RecordingProvider("revu")
        manager.register(provider)
        XCTAssertEqual(manager.availableProviders(), [])
        XCTAssertEqual(provider.availabilityChecks(), 0, "a disabled offerwall must not poll host SDKs")
    }

    // MARK: - §3 gate order

    func test_showRefusesAnUnregisteredProvider() async {
        let manager = makeManager(enabledConfig)
        let result = await manager.show(providerId: "revu") {}
        XCTAssertFalse(result.shown)
        XCTAssertTrue(result.report.contains("not registered"), result.report)
    }

    func test_showIsBlockedForAChildDirectedApp() async {
        let manager = makeManager(enabledConfig)
        let provider = RecordingProvider("revu")
        manager.register(provider)
        ConsentState.shared.setChildDirected(true)
        let result = await manager.show(providerId: "revu") {}
        XCTAssertFalse(result.shown)
        XCTAssertTrue(result.report.contains("child-directed"), result.report)
        XCTAssertEqual(provider.showCount(), 0)
        XCTAssertEqual(manager.availableProviders(), [])
    }

    /// `nil` means the host never declared it, which does NOT block — matching `ConsentState`'s
    /// own rule that the SDK never asserts that legal claim on the app's behalf.
    func test_undeclaredChildDirectedDoesNotBlock() async {
        let manager = makeManager(enabledConfig)
        manager.register(RecordingProvider("revu"))
        ConsentState.shared.setChildDirected(nil)
        let result = await manager.show(providerId: "revu") {}
        XCTAssertTrue(result.shown, result.report)
    }

    func test_deniedConsentBlocksTheOfferwall() async {
        let manager = makeManager(enabledConfig)
        let provider = RecordingProvider("revu")
        manager.register(provider)
        ConsentState.shared.setTier(.denied)
        let result = await manager.show(providerId: "revu") {}
        XCTAssertFalse(result.shown)
        XCTAssertTrue(result.report.contains("consent"), result.report)
        XCTAssertEqual(provider.showCount(), 0)
    }

    /// ═══ THE DELIBERATE CONTRAST WITH `CrmBridge` (§3) ═══
    ///
    /// `CrmBridge` refuses `.unknown` because a signal delivered to an outside processor cannot
    /// be recalled. Opening an offerwall sends nothing anywhere: the player chose to open it.
    func test_unknownAndPartialConsentDoNotBlockTheOfferwall() async {
        for tier in [ConsentTier.unknown, .partial, .granted] {
            let manager = makeManager(enabledConfig)
            manager.register(RecordingProvider("revu"))
            ConsentState.shared.setTier(tier)
            let result = await manager.show(providerId: "revu") {}
            XCTAssertTrue(result.shown, "\(tier): \(result.report)")
        }
    }

    func test_showRefusesAProviderReportingUnavailable() async {
        let manager = makeManager(enabledConfig)
        let provider = RecordingProvider("revu", available: false)
        manager.register(provider)
        let result = await manager.show(providerId: "revu") {}
        XCTAssertFalse(result.shown)
        XCTAssertTrue(result.report.contains("not available"), result.report)
        XCTAssertEqual(provider.showCount(), 0)
    }

    /// Gate ORDER, not just gate presence: a disabled config must short-circuit before the
    /// registration check, so the report names the kill switch rather than the missing provider.
    func test_gateOrderPutsEnabledBeforeRegistration() async {
        let manager = makeManager(#"{"version": "ow_v1", "enabled": false}"#)
        let result = await manager.show(providerId: "never_registered") {}
        XCTAssertFalse(result.shown)
        XCTAssertTrue(result.report.contains("disabled"), result.report)
        XCTAssertFalse(result.report.contains("not registered"), result.report)
    }

    /// ...and registration before COPPA, so an integration mistake is not reported as a consent
    /// problem.
    func test_gateOrderPutsRegistrationBeforeChildDirected() async {
        let manager = makeManager(enabledConfig)
        ConsentState.shared.setChildDirected(true)
        let result = await manager.show(providerId: "never_registered") {}
        XCTAssertTrue(result.report.contains("not registered"), result.report)
    }

    func test_showPassesTheHostCallbackStraightThroughToTheProvider() async {
        let manager = makeManager(enabledConfig)
        manager.register(RecordingProvider("revu"))
        let closed = expectation(description: "onWallClosed")
        let result = await manager.show(providerId: "revu") { closed.fulfill() }
        XCTAssertTrue(result.shown, result.report)
        await fulfillment(of: [closed], timeout: 1.0)
    }

    func test_showEmitsAnImpressionEvent() async {
        let sink = FakeEventSink()
        let manager = makeManager(enabledConfig, eventSink: sink)
        manager.register(RecordingProvider("revu"))
        _ = await manager.show(providerId: "revu") {}
        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 1)
        guard let event = events.first else { return }
        XCTAssertEqual(event.eventName, "offerwall_impression")
        XCTAssertEqual(event.eventData["provider_id"], .string("revu"))
        XCTAssertEqual(event.eventData["offerwall_version"], .string("ow_v1"))
        XCTAssertEqual(event.userProperty.userId, "p1")
    }

    func test_aBlockedShowEmitsNoEvent() async {
        let sink = FakeEventSink()
        let manager = makeManager(#"{"version": "ow_v1", "enabled": false}"#, eventSink: sink)
        manager.register(RecordingProvider("revu"))
        _ = await manager.show(providerId: "revu") {}
        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 0)
    }

    // MARK: - Providers are host code: never called while holding the lock

    func test_aProviderThatReEntersTheManagerDoesNotDeadlock() async {
        let manager = makeManager(enabledConfig)
        let provider = ReEnteringProvider(manager: manager)
        manager.register(provider)
        let done = expectation(description: "show returned")
        Task {
            _ = await manager.show(providerId: "reentrant") {}
            done.fulfill()
        }
        await fulfillment(of: [done], timeout: 5.0)
        XCTAssertEqual(provider.observedVersion, "ow_v1")
    }

    func test_theManagerStaysUsableWhileAProviderIsBlockedInsideShow() async {
        let manager = makeManager(enabledConfig)
        let blocking = BlockingProvider()
        manager.register(blocking)
        Task { _ = await manager.show(providerId: "blocking") {} }
        XCTAssertEqual(blocking.entered.wait(timeout: .now() + 5.0), .success)
        // The lock must be free while the provider is presenting.
        XCTAssertEqual(manager.activeVersion(), "ow_v1")
        manager.register(RecordingProvider("other"))
        XCTAssertTrue(manager.unregister(providerId: "other"))
        blocking.release.signal()
    }

    // MARK: - `reportCompletion` and dedup

    func test_reportCompletionPaysOutAndEmitsACompletedEvent() async {
        let sink = FakeEventSink()
        let manager = makeManager(enabledConfig, eventSink: sink)
        let result = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx1", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertTrue(result.accepted, result.report)
        XCTAssertEqual(result.rewardType, "gem")
        XCTAssertEqual(result.rewardAmount, 500)
        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 1)
        guard let event = events.first else { return }
        XCTAssertEqual(event.eventName, "offerwall_completed")
        XCTAssertEqual(event.eventData["provider_id"], .string("revu"))
        XCTAssertEqual(event.eventData["transaction_id"], .string("tx1"))
        XCTAssertEqual(event.eventData["reward_type"], .string("gem"))
        XCTAssertEqual(event.eventData["reward_amount"], .int(500))
        XCTAssertEqual(event.userProperty.userId, "p1")
    }

    func test_theSameTransactionIsPaidExactlyOnce() async {
        let sink = FakeEventSink()
        let manager = makeManager(enabledConfig, eventSink: sink)
        let first = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx1", rewardType: "gem", rewardAmount: 500
        )
        let second = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx1", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertTrue(first.accepted)
        XCTAssertFalse(second.accepted, second.report)
        XCTAssertEqual(second.rewardAmount, 0, "a refused completion must grant nothing")
        XCTAssertTrue(second.report.contains("already"), second.report)
        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 1, "a duplicate emits no second event")
    }

    /// The dedup key embeds the PROVIDER, so two vendors that happen to mint the same id are two
    /// separate payouts.
    func test_theSameTransactionIdFromTwoProvidersIsPaidTwice() async {
        let manager = makeManager(enabledConfig)
        let revu = await manager.reportCompletion(
            providerId: "revu", transactionId: "42", rewardType: "gem", rewardAmount: 10
        )
        let adjoe = await manager.reportCompletion(
            providerId: "adjoe", transactionId: "42", rewardType: "gem", rewardAmount: 10
        )
        XCTAssertTrue(revu.accepted, revu.report)
        XCTAssertTrue(adjoe.accepted, adjoe.report)
    }

    /// §5: a completion with no `transactionId` is still accepted. It also carries no identity,
    /// so it can never be de-duplicated — every one is paid, and the report says so.
    func test_aCompletionWithNoTransactionIdIsAlwaysPaid() async {
        let manager = makeManager(enabledConfig)
        for transactionId in [nil, "", "   "] as [String?] {
            let first = await manager.reportCompletion(
                providerId: "revu", transactionId: transactionId, rewardType: "gem", rewardAmount: 5
            )
            let second = await manager.reportCompletion(
                providerId: "revu", transactionId: transactionId, rewardType: "gem", rewardAmount: 5
            )
            XCTAssertTrue(first.accepted, String(describing: transactionId))
            XCTAssertTrue(second.accepted, "no id means no dedup: \(second.report)")
            XCTAssertTrue(second.report.contains("cannot be de-duplicated"), second.report)
        }
    }

    // MARK: - §2.3 a vendor `transactionId` carrying a persistence delimiter

    /// The direction the spec commits to: losing a legitimate reward is worse than a duplicate,
    /// so the completion is STILL ACCEPTED and STILL PAID — but it is recorded under a sanitized
    /// key and noted, rather than silently becoming un-dedupable.
    func test_aTransactionIdContainingADelimiterIsStillPaidAndIsNoted() async {
        let manager = makeManager(enabledConfig)
        for transactionId in ["tx,1", "tx:1", "tx\n1"] {
            let result = await manager.reportCompletion(
                providerId: "revu", transactionId: transactionId, rewardType: "gem", rewardAmount: 7
            )
            XCTAssertTrue(result.accepted, "\(transactionId): \(result.report)")
            XCTAssertEqual(result.rewardAmount, 7)
            XCTAssertTrue(result.report.contains("persistence delimiter"), result.report)
        }
    }

    /// ...and the sanitized key is INJECTIVE, so the dedup guarantee survives after all: a
    /// delimiter-carrying id is de-duplicated exactly like a clean one, and two ids that differ
    /// only in which delimiter they contain stay distinct.
    func test_aSanitizedTransactionIdIsStillDeDuplicatedAndDoesNotCollide() async {
        let manager = makeManager(enabledConfig)
        let first = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx,1", rewardType: "gem", rewardAmount: 7
        )
        let repeated = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx,1", rewardType: "gem", rewardAmount: 7
        )
        let sibling = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx:1", rewardType: "gem", rewardAmount: 7
        )
        XCTAssertTrue(first.accepted)
        XCTAssertFalse(repeated.accepted, repeated.report)
        XCTAssertTrue(sibling.accepted, "'tx,1' and 'tx:1' are different transactions")
    }

    /// The composed key must survive the store round trip — the actual defect §2.3 is about.
    func test_aSanitizedTransactionIdSurvivesTheStoreRoundTrip() async {
        let store = InMemoryStore()
        let first = makeManager(enabledConfig, store: store)
        _ = await first.reportCompletion(
            providerId: "revu", transactionId: "tx,1", rewardType: "gem", rewardAmount: 7
        )
        let reloaded = makeManager(enabledConfig, store: store)
        let replay = await reloaded.reportCompletion(
            providerId: "revu", transactionId: "tx,1", rewardType: "gem", rewardAmount: 7
        )
        XCTAssertFalse(replay.accepted, "a relaunch must not pay a delimiter-carrying id again")
    }

    /// An id past the identifier ceiling is paid too, but is deliberately NOT persisted: an
    /// unbounded vendor string rewritten into `UserDefaults` on every completion is the
    /// availability problem `JsonConfigValue.identifier`'s ceiling exists to stop.
    func test_anOverlongTransactionIdIsPaidButNotPersisted() async {
        let manager = makeManager(enabledConfig)
        let huge = String(repeating: "x", count: JsonConfigValue.maxIdentifierLength + 1)
        let first = await manager.reportCompletion(
            providerId: "revu", transactionId: huge, rewardType: "gem", rewardAmount: 3
        )
        let second = await manager.reportCompletion(
            providerId: "revu", transactionId: huge, rewardType: "gem", rewardAmount: 3
        )
        XCTAssertTrue(first.accepted, first.report)
        XCTAssertTrue(second.accepted, "an id too large to persist cannot be de-duplicated")
        XCTAssertTrue(second.report.contains("cannot be de-duplicated"), second.report)
    }

    /// §2.3, provider side: the OTHER key component gets the same treatment, so a host that
    /// registered a delimiter-carrying id still gets de-duplicated payouts.
    func test_aProviderIdContainingADelimiterIsSanitizedIntoTheKeyToo() async {
        let manager = makeManager(enabledConfig)
        let first = await manager.reportCompletion(
            providerId: "re,vu", transactionId: "tx1", rewardType: "gem", rewardAmount: 1
        )
        let repeated = await manager.reportCompletion(
            providerId: "re,vu", transactionId: "tx1", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertTrue(first.accepted, first.report)
        XCTAssertFalse(repeated.accepted, repeated.report)
        XCTAssertTrue(first.report.contains("persistence delimiter"), first.report)
    }

    /// The key is composed, not concatenated ambiguously: `("a:b", "c")` and `("a", "b:c")` are
    /// different payouts even though a naive join produces the same string for both.
    func test_theComposedKeyIsUnambiguousAcrossItsTwoComponents() async {
        let manager = makeManager(enabledConfig)
        let left = await manager.reportCompletion(
            providerId: "a:b", transactionId: "c", rewardType: "gem", rewardAmount: 1
        )
        let right = await manager.reportCompletion(
            providerId: "a", transactionId: "b:c", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertTrue(left.accepted)
        XCTAssertTrue(right.accepted, "these are two different transactions")
    }

    // MARK: - §3 `reportCompletion` deliberately ignores `enabled`

    /// A kill switch stops new opens; it must never withhold payment for work already completed.
    func test_reportCompletionPaysOutWhileTheOfferwallIsDisabled() async {
        let sink = FakeEventSink()
        let manager = makeManager(#"{"version": "ow_v1", "enabled": false}"#, eventSink: sink)
        let result = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx1", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertTrue(result.accepted, result.report)
        XCTAssertEqual(result.rewardAmount, 500)
        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 1)
    }

    /// ...and while no config has ever loaded, and while the allow-list excludes the provider,
    /// and while consent is denied. Work already done gets paid.
    func test_reportCompletionPaysOutRegardlessOfEveryShowGate() async {
        ConsentState.shared.setTier(.denied)
        ConsentState.shared.setChildDirected(true)
        let manager = makeManager(#"{"version": "ow_v1", "enabled": true, "providers": ["adjoe"]}"#)
        let result = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx1", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertTrue(result.accepted, result.report)
    }

    /// The escape must be injective, or a LEGITIMATE completion gets refused as a duplicate.
    ///
    /// Found by security review: escaping only ids that already contained a raw delimiter left
    /// `"tx:1"` (escaped to `"tx%3A1"`) colliding with a different vendor id that literally reads
    /// `"tx%3A1"` and needed no escaping. The second, genuinely distinct transaction was refused
    /// and not paid — the direction §2.3 explicitly says is worse than a duplicate.
    func test_anIdThatLooksLikeAnEscapeSequenceDoesNotCollideWithARealDelimiter() async {
        let manager = makeManager()
        let withRealColon = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx:1", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertTrue(withRealColon.accepted)

        let looksEscaped = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx%3A1", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertTrue(
            looksEscaped.accepted,
            "a distinct vendor id must not be shadowed by another id's escaped form: \(looksEscaped.report)"
        )

        // And each still de-duplicates against ITSELF.
        let replayColon = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx:1", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertFalse(replayColon.accepted, "an id must still shadow its own replay")
    }

    /// Before `configure`, dedup must still hold in memory.
    ///
    /// Found by security review: the record was appended only when a store existed, so the
    /// duplicate check could never fire and the same transaction paid IN FULL on every repeated
    /// call — an unbounded double-payment window during app launch, or permanently for a host that
    /// reports completions before configuring.
    func test_dedupHoldsBeforeConfigureAndSurvivesConfiguringAfterwards() async {
        let manager = OfferwallManager()
        let first = await manager.reportCompletion(
            providerId: "revu", transactionId: "early", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertTrue(first.accepted, "a completion before configure is still paid")

        let replay = await manager.reportCompletion(
            providerId: "revu", transactionId: "early", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertFalse(replay.accepted, "and must not pay twice just because there is nowhere to persist yet")

        // Configuring afterwards must MERGE, not discard what was already paid.
        manager.configure(.init(eventSink: FakeEventSink(), playerId: "p1", store: InMemoryStore()))
        let afterConfigure = await manager.reportCompletion(
            providerId: "revu", transactionId: "early", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertFalse(
            afterConfigure.accepted,
            "configure must merge the in-memory record, not replace it and re-open the payout"
        )
    }

    func test_reportCompletionBeforeConfigureStillPaysButCannotPersist() async {
        let manager = OfferwallManager()
        let result = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx1", rewardType: "gem", rewardAmount: 500
        )
        XCTAssertTrue(result.accepted, result.report)
        XCTAssertTrue(result.report.contains("not configured"), result.report)
    }

    // MARK: - §2.4 the load path, which on iOS CAN be tested

    func test_seenTransactionsRoundTripAcrossAFreshInstanceOverTheSameStore() async {
        let store = InMemoryStore()
        let first = makeManager(enabledConfig, store: store)
        for index in 1...3 {
            let result = await first.reportCompletion(
                providerId: "revu", transactionId: "tx\(index)", rewardType: "gem", rewardAmount: 1
            )
            XCTAssertTrue(result.accepted)
        }
        XCTAssertNotNil(store.string(forKey: OfferwallManager.seenTransactionsKey))

        let reloaded = makeManager(enabledConfig, store: store)
        for index in 1...3 {
            let replay = await reloaded.reportCompletion(
                providerId: "revu", transactionId: "tx\(index)", rewardType: "gem", rewardAmount: 1
            )
            XCTAssertFalse(replay.accepted, "tx\(index) was already paid before the relaunch")
        }
        let fresh = await reloaded.reportCompletion(
            providerId: "revu", transactionId: "tx4", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertTrue(fresh.accepted)
    }

    /// The Android-untestable half: a NON-EMPTY stored string, parsed on load. Android's
    /// `PreferenceHelper` no-ops without a `Context`, so its tests only ever see the empty
    /// default — precisely how a `RecurringGrants`-class defect hides.
    func test_aPreSeededStoreIsHonouredOnTheVeryFirstCall() async {
        let store = InMemoryStore([OfferwallManager.seenTransactionsKey: "revu:tx1,revu:tx2,adjoe:tx1"])
        let manager = makeManager(enabledConfig, store: store)
        for (provider, transaction) in [("revu", "tx1"), ("revu", "tx2"), ("adjoe", "tx1")] {
            let replay = await manager.reportCompletion(
                providerId: provider, transactionId: transaction, rewardType: "gem", rewardAmount: 1
            )
            XCTAssertFalse(replay.accepted, "\(provider):\(transaction) is already in the store")
        }
        let fresh = await manager.reportCompletion(
            providerId: "adjoe", transactionId: "tx2", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertTrue(fresh.accepted)
    }

    func test_emptyAndBlankStoredEntriesAreSkippedRatherThanFailingTheWholeLoad() async {
        let store = InMemoryStore([OfferwallManager.seenTransactionsKey: ",,revu:tx1,,   ,adjoe:tx9,"])
        let manager = makeManager(enabledConfig, store: store)
        let replay = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx1", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertFalse(replay.accepted, replay.report)
        let other = await manager.reportCompletion(
            providerId: "adjoe", transactionId: "tx9", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertFalse(other.accepted, other.report)
    }

    /// §2.4: the 200-entry cap, trimming OLDEST first — the other thing Android never tests.
    func test_theSeenTransactionCapTrimsOldestFirst() async {
        let cap = OfferwallManager.maxSeenTransactions
        let seeded = (1...cap).map { "revu:seed\($0)" }.joined(separator: ",")
        let store = InMemoryStore([OfferwallManager.seenTransactionsKey: seeded])
        let manager = makeManager(enabledConfig, store: store)

        // The store is exactly full; one more completion must evict the OLDEST entry.
        let fresh = await manager.reportCompletion(
            providerId: "revu", transactionId: "newest", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertTrue(fresh.accepted)

        let persisted = store.string(forKey: OfferwallManager.seenTransactionsKey) ?? ""
        let entries = persisted.split(separator: ",").map(String.init)
        XCTAssertEqual(entries.count, cap)
        XCTAssertEqual(entries.first, "revu:seed2", "the oldest entry is the one that goes")
        XCTAssertEqual(entries.last, "revu:newest")

        // The evicted transaction is payable again — the honest consequence of a bounded record,
        // and the direction the spec chooses (a duplicate beats a lost reward).
        let evicted = await manager.reportCompletion(
            providerId: "revu", transactionId: "seed1", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertTrue(evicted.accepted)
        // ...while an entry still inside the window is not.
        let retained = await manager.reportCompletion(
            providerId: "revu", transactionId: "seed3", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertFalse(retained.accepted, retained.report)
    }

    func test_anOverlongStoredRecordIsTrimmedToTheCapOnLoad() async {
        let cap = OfferwallManager.maxSeenTransactions
        let seeded = (1...(cap + 50)).map { "revu:seed\($0)" }.joined(separator: ",")
        let store = InMemoryStore([OfferwallManager.seenTransactionsKey: seeded])
        let manager = makeManager(enabledConfig, store: store)
        let oldest = await manager.reportCompletion(
            providerId: "revu", transactionId: "seed1", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertTrue(oldest.accepted, "the 50 oldest entries were trimmed on load")
        let newest = await manager.reportCompletion(
            providerId: "revu", transactionId: "seed\(cap + 50)", rewardType: "gem", rewardAmount: 1
        )
        XCTAssertFalse(newest.accepted, newest.report)
    }

    func test_resetForTestClearsConfigProvidersAndThePersistedRecord() async {
        let store = InMemoryStore()
        let manager = makeManager(enabledConfig, store: store)
        manager.register(RecordingProvider("revu"))
        _ = await manager.reportCompletion(
            providerId: "revu", transactionId: "tx1", rewardType: "gem", rewardAmount: 1
        )
        manager.resetForTest()
        XCTAssertEqual(manager.activeVersion(), "not loaded")
        XCTAssertEqual(manager.availableProviders(), [])
        XCTAssertNil(store.string(forKey: OfferwallManager.seenTransactionsKey))
    }
}
