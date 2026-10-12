import XCTest
@testable import MonetizationBrainCore

/// Design spec §5. `unregister`, the `sinks` allow-list, the `enabled` default and every gate
/// below are untested on Android; §2.1 and §2.2 are deviations this file exists to hold in place.
final class CrmBridgeTests: XCTestCase {
    /// A host sink, recording what it was handed. File-local by this project's convention — see
    /// `RecurringGrantsTests` for the same shape.
    private final class RecordingSink: CrmSink, @unchecked Sendable {
        let sinkId: String
        private let lock = NSLock()
        private var received: [EngagementSignal] = []
        private let accepts: Bool
        init(_ sinkId: String, accepts: Bool = true) {
            self.sinkId = sinkId
            self.accepts = accepts
        }
        func deliver(_ signal: EngagementSignal) -> Bool {
            lock.lock(); received.append(signal); lock.unlock()
            return accepts
        }
        func signals() -> [EngagementSignal] { lock.lock(); defer { lock.unlock() }; return received }
        func count() -> Int { signals().count }
    }

    /// ═══ THE SINK §2.2 EXISTS FOR ═══
    ///
    /// Host code the SDK cannot vet, calling straight back into the bridge from inside `deliver`.
    /// On Android the `@Synchronized` monitor is reentrant and this survives; `NSLock` is not, so
    /// the same shape deadlocks unless delivery happens with the lock released.
    private final class ReEnteringSink: CrmSink, @unchecked Sendable {
        let sinkId = "reentrant"
        private let bridge: CrmBridge
        private let lock = NSLock()
        private var reentered = false
        private(set) var observedVersion = ""
        init(bridge: CrmBridge) { self.bridge = bridge }
        func deliver(_ signal: EngagementSignal) -> Bool {
            observedVersion = bridge.activeVersion()
            let shouldReenter: Bool = {
                lock.lock(); defer { lock.unlock() }
                if reentered { return false }
                reentered = true
                return true
            }()
            if shouldReenter {
                bridge.register(RecordingSink("registered_from_inside_deliver"))
                _ = bridge.emit(EngagementSignal(playerId: "p1", kind: .firstPurchase))
                _ = bridge.unregister(sinkId: "registered_from_inside_deliver")
            }
            return true
        }
    }

    /// Blocks inside `deliver` until released — used to prove the bridge is usable by OTHER
    /// callers while a slow sink is mid-delivery.
    private final class BlockingSink: CrmSink, @unchecked Sendable {
        let sinkId = "blocking"
        let entered = DispatchSemaphore(value: 0)
        let release = DispatchSemaphore(value: 0)
        func deliver(_ signal: EngagementSignal) -> Bool {
            entered.signal()
            release.wait()
            return true
        }
    }

    /// Registers another sink from inside `deliver` — the in-flight signal must not reach it,
    /// because the eligible set was snapshotted before the lock was released.
    private final class RegisteringSink: CrmSink, @unchecked Sendable {
        let sinkId = "registrar"
        private let bridge: CrmBridge
        let late = RecordingSink("late")
        init(bridge: CrmBridge) { self.bridge = bridge }
        func deliver(_ signal: EngagementSignal) -> Bool {
            bridge.register(late)
            return true
        }
    }

    private final class FakeClock: BrainClock, @unchecked Sendable {
        private let lock = NSLock()
        private var current: Int64
        init(_ start: Int64) { self.current = start }
        func nowMs() -> Int64 { lock.lock(); defer { lock.unlock() }; return current }
    }

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

    private let baseMs: Int64 = 1_700_000_000_000
    private let enabledConfig = #"{"version": "crm_v1", "enabled": true}"#

    private func makeBridge(_ json: String? = nil, clock: BrainClock? = nil) -> CrmBridge {
        let bridge = CrmBridge()
        bridge.configure(.init(clock: clock ?? FakeClock(baseMs)))
        if let json { _ = bridge.apply(json) }
        return bridge
    }

    private func signal(_ kind: CrmSignalKind = .firstPurchase) -> EngagementSignal {
        EngagementSignal(playerId: "p1", kind: kind)
    }

    override func setUp() {
        super.setUp()
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        CrmBridge.shared.resetForTest()
        // `resetForTest` leaves the tier at `.unknown`, which this channel REFUSES — sending player
        // data to an outside processor before the app has asked is the one thing no default should
        // decide. Tests about anything other than the consent gate therefore have to state consent
        // explicitly, which is the point: the gate is not something a test can drift past silently.
        // The gate's own tests set their tier and are unaffected by this line.
        ConsentState.shared.setTier(.granted)
    }

    override func tearDown() {
        AdsBrain.shared.resetForTest()
        ConsentState.shared.resetForTest()
        CrmBridge.shared.resetForTest()
        super.tearDown()
    }

    // MARK: - Defaults and config parsing (§3.2)

    func test_beforeAnyApplyNothingIsDeliveredAndVersionIsNotLoaded() {
        let bridge = makeBridge()
        let sink = RecordingSink("braze")
        bridge.register(sink)
        XCTAssertEqual(bridge.activeVersion(), "not loaded")
        XCTAssertEqual(bridge.emit(signal()), 0, "with CRM, silence is the safe state")
        XCTAssertEqual(sink.count(), 0)
    }

    /// `version` is required and non-blank (§3.2): without one there is nothing to name the
    /// running config in a report, and a config that cannot be identified cannot be rolled back.
    func test_emptyMalformedOrVersionlessConfigIsRejected() {
        let bridge = makeBridge()
        for json in [nil, "   ", #"{"enabled": true}"#, #"{"version": "  ", "enabled": true}"#] as [String?] {
            XCTAssertFalse(bridge.apply(json).applied, String(describing: json))
        }
        XCTAssertEqual(bridge.activeVersion(), "not loaded")
    }

    func test_applyMalformedJsonIsRejectedAndKeepsLastGoodConfig() {
        let bridge = makeBridge(enabledConfig)
        let sink = RecordingSink("braze")
        bridge.register(sink)
        let result = bridge.apply("{not valid json")
        XCTAssertFalse(result.applied)
        XCTAssertTrue(result.report.contains("crm_v1"), result.report)
        XCTAssertEqual(bridge.activeVersion(), "crm_v1")
        XCTAssertEqual(bridge.emit(signal()), 1, "the last-good config must still be running")
    }

    // MARK: - §2.1 `enabled` must NOT fail open — the Android bug

    /// Android parses `root["enabled"] != false`, so an ABSENT key evaluates to `true` and the
    /// bridge silently starts shipping signals to a marketing platform. Absent means OFF here.
    func test_enabledAbsentMeansOff() {
        let bridge = makeBridge(#"{"version": "crm_v1"}"#)
        let sink = RecordingSink("braze")
        bridge.register(sink)
        XCTAssertEqual(bridge.emit(signal()), 0)
        XCTAssertEqual(sink.count(), 0)
    }

    /// Same Android bug from the other direction: `"false"`, `0`, `1` and `[]` are none of them
    /// the Kotlin literal `false`, so all four enable it there. Here every one degrades to OFF —
    /// and says so, because an operator who wrote `"enabled": "false"` needs to learn their
    /// config does not say what they think it says.
    func test_wrongTypedEnabledMeansOffWithADiagnosticNote() {
        for raw in ["\"false\"", "\"true\"", "0", "1", "[]", "{}"] {
            let bridge = makeBridge(#"{"version": "crm_v1", "enabled": \#(raw)}"#)
            let sink = RecordingSink("braze")
            bridge.register(sink)
            XCTAssertEqual(bridge.emit(signal()), 0, "enabled: \(raw) must not enable the bridge")
            let report = bridge.apply(#"{"version": "crm_v2", "enabled": \#(raw)}"#).report
            XCTAssertTrue(report.contains("'enabled' is not a boolean"), "enabled: \(raw) -> \(report)")
            XCTAssertTrue(report.contains("defaulting to false"), "enabled: \(raw) -> \(report)")
        }
    }

    func test_enabledTrueTurnsTheBridgeOnAndExplicitFalseTurnsItOff() {
        let bridge = makeBridge(enabledConfig)
        let sink = RecordingSink("braze")
        bridge.register(sink)
        XCTAssertEqual(bridge.emit(signal()), 1)
        _ = bridge.apply(#"{"version": "crm_v2", "enabled": false}"#)
        XCTAssertEqual(bridge.emit(signal()), 0)
        XCTAssertEqual(sink.count(), 1)
    }

    // MARK: - §3.2 the `sinks` allow-list

    func test_absentOrEmptySinksListMakesEveryRegisteredSinkEligible() {
        for json in [enabledConfig, #"{"version": "crm_v1", "enabled": true, "sinks": []}"#] {
            let bridge = makeBridge(json)
            let braze = RecordingSink("braze")
            let appsflyer = RecordingSink("appsflyer")
            bridge.register(braze)
            bridge.register(appsflyer)
            XCTAssertEqual(bridge.emit(signal()), 2, json)
        }
    }

    func test_sinksAllowListFiltersToTheNamedSinksOnly() {
        let bridge = makeBridge(#"{"version": "crm_v1", "enabled": true, "sinks": ["braze"]}"#)
        let braze = RecordingSink("braze")
        let appsflyer = RecordingSink("appsflyer")
        bridge.register(braze)
        bridge.register(appsflyer)
        XCTAssertEqual(bridge.emit(signal()), 1)
        XCTAssertEqual(braze.count(), 1)
        XCTAssertEqual(appsflyer.count(), 0)
    }

    /// Android drops a non-string element silently. Here it is dropped WITH a note — the
    /// remaining allow-list still applies, so the drop fails closed (a sink nobody named is not
    /// eligible).
    func test_nonStringSinkEntryIsDroppedWithANote() {
        let bridge = CrmBridge()
        bridge.configure(.init(clock: FakeClock(baseMs)))
        let result = bridge.apply(#"{"version": "crm_v1", "enabled": true, "sinks": ["braze", 7, null]}"#)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("sink_id"), result.report)
        let braze = RecordingSink("braze")
        let other = RecordingSink("7")
        bridge.register(braze)
        bridge.register(other)
        XCTAssertEqual(bridge.emit(signal()), 1)
        XCTAssertEqual(other.count(), 0)
    }

    func test_sinksThatIsNotAnArrayBlocksEverySinkWithANote() {
        let bridge = CrmBridge()
        bridge.configure(.init(clock: FakeClock(baseMs)))
        let result = bridge.apply(#"{"version": "crm_v1", "enabled": true, "sinks": "braze"}"#)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("is not an array"), result.report)
        XCTAssertTrue(result.report.contains("NO sink"), result.report)
        let braze = RecordingSink("braze")
        bridge.register(braze)
        XCTAssertEqual(
            bridge.emit(signal()), 0,
            "an unreadable allow-list names nobody — it must never broaden to everybody. The "
            + "operator wrote 'sinks' to NARROW; delivering to a sink they meant to exclude sends "
            + "personal data to a processor they named and rejected."
        )
    }

    /// `first_purchase` must mean first EVER on this device, not first since the process started.
    /// `PlayerState.purchaseCountLifetime` is in-memory, so without a persisted flag every
    /// relaunch-then-purchase sends another one and a marketing platform re-runs its welcome
    /// campaign for a player who is not new.
    ///
    /// Asserted at the storage seam rather than through `AdsBrain` (a strict singleton), because
    /// the flag surviving a fresh `BrainStorage` over the same store IS the relaunch.
    func test_theFirstPurchaseSignalFlagSurvivesARelaunch() {
        let store = InMemoryStore()

        let firstRun = BrainStorage(store: store)
        XCTAssertFalse(firstRun.hasSentFirstPurchaseSignal(playerId: "p1"))
        firstRun.markFirstPurchaseSignalSent(playerId: "p1")
        XCTAssertTrue(firstRun.hasSentFirstPurchaseSignal(playerId: "p1"))

        let afterRelaunch = BrainStorage(store: store)
        XCTAssertTrue(
            afterRelaunch.hasSentFirstPurchaseSignal(playerId: "p1"),
            "a relaunch must not re-send first_purchase — the marketing platform would re-run its "
            + "welcome campaign for an existing payer"
        )
        XCTAssertFalse(
            afterRelaunch.hasSentFirstPurchaseSignal(playerId: "p2"),
            "the flag is per player, not global"
        )
    }

    /// A sink that appends its id to a SHARED log, so inter-sink ordering is observable.
    /// Per-sink counters cannot see order: each one only knows it was called.
    private final class OrderRecordingSink: CrmSink, @unchecked Sendable {
        let sinkId: String
        private let log: SharedLog
        init(_ sinkId: String, log: SharedLog) { self.sinkId = sinkId; self.log = log }
        func deliver(_ signal: EngagementSignal) -> Bool { log.append(sinkId); return true }
    }

    private final class SharedLog: @unchecked Sendable {
        private let lock = NSLock()
        private var entries: [String] = []
        func append(_ id: String) { lock.lock(); entries.append(id); lock.unlock() }
        func read() -> [String] { lock.lock(); defer { lock.unlock() }; return entries }
    }

    /// `CrmBridge`'s doc comment claims registration order is preserved "so delivery order is
    /// deterministic and a test can assert it". Until this test, no test did: every multi-sink
    /// case checked only per-sink counts, so swapping the backing store for a `Set`, or iterating
    /// in reverse, would have left the whole suite green while the documented guarantee quietly
    /// stopped being true.
    func test_sinksAreDeliveredToInRegistrationOrder() {
        let log = SharedLog()
        let bridge = CrmBridge()
        bridge.configure(.init(clock: FakeClock(baseMs)))
        _ = bridge.apply(#"{"version": "crm_v1", "enabled": true}"#)

        // Registered in an order that is NOT alphabetical, so a sort would also be caught.
        for id in ["zulu", "alpha", "mike", "bravo"] {
            bridge.register(OrderRecordingSink(id, log: log))
        }
        XCTAssertEqual(bridge.emit(signal()), 4)
        XCTAssertEqual(
            log.read(), ["zulu", "alpha", "mike", "bravo"],
            "delivery must follow registration order, not insertion into a hash container"
        )
    }

    /// Re-registering an existing id REPLACES in place — it must not move that sink to the end of
    /// the delivery order, which is the difference between "replace" and "remove then append".
    func test_replacingASinkKeepsItsPlaceInTheDeliveryOrder() {
        let log = SharedLog()
        let bridge = CrmBridge()
        bridge.configure(.init(clock: FakeClock(baseMs)))
        _ = bridge.apply(#"{"version": "crm_v1", "enabled": true}"#)

        bridge.register(OrderRecordingSink("first", log: log))
        bridge.register(OrderRecordingSink("second", log: log))
        bridge.register(OrderRecordingSink("first", log: log))   // replace, not append

        XCTAssertEqual(bridge.emit(signal()), 2, "replacing must not duplicate the sink")
        XCTAssertEqual(
            log.read(), ["first", "second"],
            "a replaced sink keeps its original position; appending would give [second, first]"
        )
    }

    // MARK: - §3.3 the gate order

    func test_childDirectedBlocksEvenWhenConsentIsGranted() {
        let bridge = makeBridge(enabledConfig)
        let sink = RecordingSink("braze")
        bridge.register(sink)
        ConsentState.shared.setTier(.granted)
        ConsentState.shared.setChildDirected(true)
        XCTAssertEqual(bridge.emit(signal()), 0, "COPPA: a child-directed app blocks the channel whatever consent says")
        XCTAssertEqual(sink.count(), 0)
    }

    /// `childDirected` is `Bool?` on iOS and `nil` means "the host never declared this" — the SDK
    /// does not assert that legal claim on the app's behalf, matching Android's `== true` check.
    func test_undeclaredOrFalseChildDirectedDoesNotBlock() {
        for value in [nil, false] as [Bool?] {
            let bridge = makeBridge(enabledConfig)
            bridge.register(RecordingSink("braze"))
            ConsentState.shared.setChildDirected(value)
            XCTAssertEqual(bridge.emit(signal()), 1, "childDirected \(String(describing: value)) must not block")
        }
    }

    /// ONLY `.granted` opens this channel — stricter than every other consent predicate here,
    /// because this is the only one that sends player data to a processor outside the studio.
    ///
    /// `.unknown` is refused although `allowsProfiling()` accepts it: `.unknown` means the app has
    /// not asked yet, and while a local profile built too early can be discarded, a signal already
    /// delivered to an outside party cannot be recalled. `.partial` is refused because there is no
    /// per-feature masking, so it cannot tell us whether CRM was among the features consented to.
    ///
    /// This deliberately diverges from Android, which delivers on `.unknown`.
    func test_onlyGrantedConsentOpensTheThirdPartyChannel() {
        let bridge = makeBridge(enabledConfig)
        bridge.register(RecordingSink("braze"))

        for tier in [ConsentTier.denied, .unknown, .partial] {
            ConsentState.shared.setTier(tier)
            XCTAssertEqual(
                bridge.emit(signal()), 0,
                "tier \(tier) must NOT reach a third-party processor — data sent cannot be recalled"
            )
        }

        ConsentState.shared.setTier(.granted)
        XCTAssertEqual(bridge.emit(signal()), 1, "an explicit grant is the only thing that opens it")
    }

    func test_noRegisteredSinkDeliversNothing() {
        let bridge = makeBridge(enabledConfig)
        XCTAssertEqual(bridge.emit(signal()), 0)
    }

    // MARK: - Registration lifecycle (`unregister` is untested on Android)

    func test_unregisterStopsDeliveryAndReportsWhetherItRemovedAnything() {
        let bridge = makeBridge(enabledConfig)
        let sink = RecordingSink("braze")
        bridge.register(sink)
        XCTAssertEqual(bridge.emit(signal()), 1)
        XCTAssertTrue(bridge.unregister(sinkId: "braze"))
        XCTAssertEqual(bridge.emit(signal()), 0)
        XCTAssertEqual(sink.count(), 1)
        XCTAssertFalse(bridge.unregister(sinkId: "braze"), "unregistering twice removes nothing the second time")
        XCTAssertFalse(bridge.unregister(sinkId: "never_registered"))
    }

    func test_registeringTheSameSinkIdTwiceReplacesRatherThanDuplicates() {
        let bridge = makeBridge(enabledConfig)
        let first = RecordingSink("braze")
        let second = RecordingSink("braze")
        bridge.register(first)
        bridge.register(second)
        XCTAssertEqual(bridge.emit(signal()), 1, "one id means one sink — a double register must not double-deliver")
        XCTAssertEqual(first.count(), 0)
        XCTAssertEqual(second.count(), 1)
    }

    func test_resetForTestClearsBothSinksAndConfig() {
        let bridge = makeBridge(enabledConfig)
        bridge.register(RecordingSink("braze"))
        bridge.resetForTest()
        XCTAssertEqual(bridge.activeVersion(), "not loaded")
        XCTAssertEqual(bridge.emit(signal()), 0)
    }

    // MARK: - §2.2 delivery happens with the lock RELEASED

    func test_aSinkThatRefusesDoesNotStopTheOthers() {
        let bridge = makeBridge(enabledConfig)
        let refusing = RecordingSink("refusing", accepts: false)
        let healthy = RecordingSink("healthy")
        bridge.register(refusing)
        bridge.register(healthy)
        XCTAssertEqual(bridge.emit(signal()), 1, "only the accepting sink counts")
        XCTAssertEqual(refusing.count(), 1)
        XCTAssertEqual(healthy.count(), 1, "one failing sink must not stop the rest")
    }

    /// ═══ THE DEADLOCK TEST (§2.2) ═══
    ///
    /// `emit` runs on a background queue so that a bridge holding its lock across `deliver` fails
    /// this as a TIMEOUT rather than hanging the whole test process.
    func test_aSinkThatReEntersTheBridgeDoesNotDeadlock() {
        let bridge = makeBridge(enabledConfig)
        let sink = ReEnteringSink(bridge: bridge)
        bridge.register(sink)

        let finished = expectation(description: "emit returned")
        var delivered = -1
        DispatchQueue.global().async {
            delivered = bridge.emit(self.signal())
            finished.fulfill()
        }
        wait(for: [finished], timeout: 5.0)

        XCTAssertEqual(delivered, 1)
        XCTAssertEqual(sink.observedVersion, "crm_v1", "a re-entrant read must return the live config, not hang")
    }

    func test_aSlowSinkDoesNotBlockOtherBridgeCallers() {
        let bridge = makeBridge(enabledConfig)
        let blocking = BlockingSink()
        bridge.register(blocking)

        let emitted = expectation(description: "emit returned")
        DispatchQueue.global().async {
            _ = bridge.emit(self.signal())
            emitted.fulfill()
        }
        XCTAssertEqual(blocking.entered.wait(timeout: .now() + 5.0), .success, "sink never got the signal")

        // The bridge must still answer while `deliver` is in flight.
        XCTAssertEqual(bridge.activeVersion(), "crm_v1")
        bridge.register(RecordingSink("late_arrival"))
        XCTAssertTrue(bridge.unregister(sinkId: "late_arrival"))

        blocking.release.signal()
        wait(for: [emitted], timeout: 5.0)
    }

    func test_aSinkRegisteredDuringDeliveryDoesNotReceiveTheInFlightSignal() {
        let bridge = makeBridge(enabledConfig)
        let registrar = RegisteringSink(bridge: bridge)
        bridge.register(registrar)

        XCTAssertEqual(bridge.emit(signal()), 1)
        XCTAssertEqual(registrar.late.count(), 0, "the eligible set is snapshotted before delivery starts")
        XCTAssertEqual(bridge.emit(signal()), 2, "...and the new sink is eligible for the NEXT signal")
    }

    // MARK: - Signal shape

    func test_anUnstampedSignalIsStampedWithTheClockAndAHostStampIsPreserved() {
        let bridge = makeBridge(enabledConfig, clock: FakeClock(baseMs))
        let sink = RecordingSink("braze")
        bridge.register(sink)
        _ = bridge.emit(signal())
        _ = bridge.emit(EngagementSignal(playerId: "p1", kind: .firstPurchase, timestampMs: 42))
        XCTAssertEqual(sink.signals().first?.timestampMs, baseMs)
        XCTAssertEqual(
            sink.signals().last?.timestampMs, 42,
            "a replayed signal must keep the time it describes, not the time it was flushed"
        )
    }

    func test_emitSegmentChangeCarriesBothEndsOfTheMove() {
        let bridge = makeBridge(enabledConfig)
        let sink = RecordingSink("braze")
        bridge.register(sink)
        XCTAssertEqual(bridge.emitSegmentChange(playerId: "p1", from: "CASUAL_STEADY", to: "HIGH_SKILL_LOW_TOLERANCE"), 1)
        let signal = sink.signals().first
        XCTAssertEqual(signal?.kind, .segmentChange)
        XCTAssertEqual(signal?.playerId, "p1")
        XCTAssertEqual(signal?.properties["from_segment"], "CASUAL_STEADY")
        XCTAssertEqual(signal?.properties["to_segment"], "HIGH_SKILL_LOW_TOLERANCE")
    }

    func test_emitFirstPurchaseCarriesTheUsdValue() {
        let bridge = makeBridge(enabledConfig)
        let sink = RecordingSink("braze")
        bridge.register(sink)
        XCTAssertEqual(bridge.emitFirstPurchase(playerId: "p1", usd: 4.99), 1)
        let signal = sink.signals().first
        XCTAssertEqual(signal?.kind, .firstPurchase)
        XCTAssertEqual(signal?.properties["value_usd"], "4.99")
    }

    // MARK: - Wiring: `AdsBrain.onPurchase` (FIRST purchase only)

    private func armSharedBridge() -> RecordingSink {
        CrmBridge.shared.configure(.init(clock: FakeClock(baseMs)))
        _ = CrmBridge.shared.apply(enabledConfig)
        let sink = RecordingSink("braze")
        CrmBridge.shared.register(sink)
        return sink
    }

    private func configureBrain(sink: EventSink, playerId: String = "user_1") async {
        await AdsBrain.shared.configure(
            AdsBrain.Configuration(
                remoteConfig: StaticRemoteConfigProvider(),
                eventSink: sink,
                playerId: playerId,
                decisionEngine: DecisionEngine(store: InMemoryStore(), eventSink: sink)
            )
        )
    }

    func test_onPurchaseEmitsFirstPurchaseExactlyOnceEverForThePlayer() async {
        let crmSink = armSharedBridge()
        let eventSink = FakeEventSink()
        await configureBrain(sink: eventSink)
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()

        await AdsBrain.shared.onPurchase(usd: 4.99)
        await AdsBrain.shared.onPurchase(usd: 9.99)
        await AdsBrain.shared.onSessionStart() // a new session, same player
        await AdsBrain.shared.onPurchase(usd: 1.99)

        let firstPurchases = crmSink.signals().filter { $0.kind == .firstPurchase }
        XCTAssertEqual(firstPurchases.count, 1, "the flag must be captured BEFORE purchaseCountLifetime increments")
        XCTAssertEqual(firstPurchases.first?.properties["value_usd"], "4.99")
        XCTAssertEqual(firstPurchases.first?.playerId, "user_1")

        // §4: the CRM channel is NOT the event pipeline. Nothing about these signals may show up
        // as an `SdkEvent` on the way to the studio's own backend.
        let sent = await eventSink.sentEvents
        XCTAssertTrue(sent.allSatisfy { !$0.eventName.contains("crm") && !$0.eventName.contains("purchase") }, sent.map(\.eventName).description)
    }

    // MARK: - Wiring: the belief-argmax FLIP (not merely "an update ran")

    /// Drives real belief updates through `AdsBrain`: two sessions satisfy
    /// `LikelihoodEstimator`'s support gate, and a lost level moves the posterior off the uniform
    /// prior far enough to change the argmax.
    private func driveBrainToASegmentFlip() async {
        await configureBrain(sink: FakeEventSink())
        AdsBrain.shared.enabled = true
        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onLevelFail()
    }

    func test_segmentChangeIsEmittedWhenTheArgmaxActuallyFlips() async {
        let crmSink = armSharedBridge()
        await driveBrainToASegmentFlip()

        let changes = crmSink.signals().filter { $0.kind == .segmentChange }
        XCTAssertEqual(changes.count, 1)
        XCTAssertEqual(changes.first?.properties["from_segment"], LatentPlayerType.highSkillLowTolerance.rawValue)
        XCTAssertEqual(changes.first?.properties["to_segment"], LatentPlayerType.lowSkillHighChurnRisk.rawValue)
        XCTAssertEqual(changes.first?.playerId, "user_1")
    }

    /// ═══ THE TRIGGER IS "THE ARGMAX CHANGED", NOT "AN UPDATE RAN" ═══
    ///
    /// Further observations keep moving the posterior, but while the same type stays on top there
    /// is nothing for a CRM audience to re-segment on. Firing per observation would push a signal
    /// on every level end — for every player, forever.
    func test_noSegmentChangeWhenBeliefUpdatesWithoutChangingTheArgmax() async {
        let crmSink = armSharedBridge()
        await driveBrainToASegmentFlip()
        let afterFlip = crmSink.signals().filter { $0.kind == .segmentChange }.count
        XCTAssertEqual(afterFlip, 1, "precondition")

        for _ in 0..<5 { await AdsBrain.shared.onLevelFail() }

        XCTAssertEqual(
            crmSink.signals().filter { $0.kind == .segmentChange }.count, afterFlip,
            "belief kept updating, but the argmax did not move — no signal belongs on the wire"
        )
    }

    func test_noSegmentChangeWhenTheBrainIsNotActive() async {
        let crmSink = armSharedBridge()
        await configureBrain(sink: FakeEventSink())
        AdsBrain.shared.enabled = false
        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onSessionStart()
        await AdsBrain.shared.onLevelFail()

        XCTAssertTrue(crmSink.signals().filter { $0.kind == .segmentChange }.isEmpty)
    }

    /// The bridge's own gates still apply to a signal the SDK raises internally: an unconfigured
    /// (or disabled) bridge is silent even while the brain is flipping segments.
    func test_brainWiringStillRespectsTheBridgeGates() async {
        let sink = RecordingSink("braze")
        CrmBridge.shared.register(sink) // registered, but no config applied -> gate 1 blocks
        await driveBrainToASegmentFlip()
        await AdsBrain.shared.onPurchase(usd: 4.99)
        XCTAssertTrue(sink.signals().isEmpty)
    }
}
