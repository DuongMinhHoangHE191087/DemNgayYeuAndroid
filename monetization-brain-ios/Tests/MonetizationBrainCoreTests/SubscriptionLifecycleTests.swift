import XCTest
@testable import MonetizationBrainCore

final class SubscriptionLifecycleTests: XCTestCase {
    /// In-memory `BrainKeyValueStore` — redeclared per-file, matching this project's established
    /// convention of NOT sharing test fakes across files (see `EntitlementsTests`' own copy).
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

    /// Same shape as `PurchaseRouterTests`' own file-local fake.
    private actor FakeEventSink: EventSink {
        private(set) var sentEvents: [SdkEvent] = []
        func send(_ event: SdkEvent) async { sentEvents.append(event) }
    }

    /// Engineered contention for the STORE. The lost-write this exists to catch lives entirely
    /// between two `setString` calls, so the delay has to be there: `armDelay()` makes the NEXT
    /// write to arrive sleep while a later one runs past it.
    private final class DelayingStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        private var delayNext = false
        private var writeOrder: [String] = []
        private let delay: TimeInterval
        init(delay: TimeInterval = 0.4) { self.delay = delay }
        func armDelay() { lock.lock(); delayNext = true; lock.unlock() }
        func string(forKey key: String) -> String? {
            lock.lock(); defer { lock.unlock() }
            return values[key]
        }
        func setString(_ value: String, forKey key: String) {
            lock.lock()
            let shouldDelay = delayNext
            delayNext = false
            lock.unlock()
            if shouldDelay { Thread.sleep(forTimeInterval: delay) }
            lock.lock(); values[key] = value; writeOrder.append("\(key)=\(value)"); lock.unlock()
        }
        func removeObject(forKey key: String) {
            lock.lock(); _ = values.removeValue(forKey: key); lock.unlock()
        }
        func landed() -> [String] { lock.lock(); defer { lock.unlock() }; return writeOrder }
    }

    private let productId = "sub_monthly"

    // MARK: - Helpers

    private func makeLifecycle(
        store: BrainKeyValueStore = InMemoryStore(),
        eventSink: EventSink = FakeEventSink()
    ) -> SubscriptionLifecycle {
        let lifecycle = SubscriptionLifecycle()
        lifecycle.configure(.init(eventSink: eventSink, playerId: "p1", store: store))
        return lifecycle
    }

    private func snapshot(
        _ renewalState: SubscriptionRenewalState,
        willAutoRenew: Bool = true,
        productId: String? = nil,
        expirationReason: SubscriptionExpirationReason? = nil
    ) -> SubscriptionSnapshot {
        SubscriptionSnapshot(
            productId: productId ?? self.productId,
            renewalState: renewalState,
            willAutoRenew: willAutoRenew,
            expirationMs: 1_700_000_000_000,
            gracePeriodExpirationMs: 0,
            expirationReason: expirationReason
        )
    }

    /// Polls with a bounded retry rather than one fixed sleep, matching `IapHelperTests`' own
    /// async-listener convention.
    ///
    /// NOTE ON WHAT THIS HELPER CANNOT PROVE: `observe(_:)` awaits its event sends directly, so
    /// every event is already delivered by the time it returns and no polling is strictly needed.
    /// A bounded poll would pass just as happily against a regression to a detached
    /// `Task { await sink.send(event) }` — which is exactly the drop window `observe` was made
    /// `async` to close. `test_observeDeliversItsEventsBeforeReturning` is the test that actually
    /// holds that guarantee; this helper is kept only for the genuinely concurrent cases.
    private func waitForEvents(_ sink: FakeEventSink, count: Int) async -> [SdkEvent] {
        for _ in 0..<50 {
            let events = await sink.sentEvents
            if events.count >= count { return events }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        return await sink.sentEvents
    }

    /// For the negative case there is nothing to poll FOR — give any erroneously-dispatched send
    /// a generous window to land before asserting it didn't.
    private func settledEvents(_ sink: FakeEventSink) async -> [SdkEvent] {
        try? await Task.sleep(nanoseconds: 100_000_000)
        return await sink.sentEvents
    }

    override func setUp() {
        super.setUp()
        SubscriptionLifecycle.shared.resetForTest()
    }

    override func tearDown() {
        SubscriptionLifecycle.shared.resetForTest()
        super.tearDown()
    }

    // MARK: - Default state

    func test_beforeAnyObserveEveryProductIsNoneAndVersionIsNotLoaded() async {
        let lifecycle = makeLifecycle()
        XCTAssertEqual(lifecycle.stateFor(productId: productId), SubscriptionState.none)
        XCTAssertEqual(lifecycle.activeVersion(), "not loaded")
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: productId))
        XCTAssertTrue(lifecycle.winBackEligibleProductIds().isEmpty)
    }

    func test_classificationWorksWithNoConfigLoadedUsingTheDefaultCadence() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .inGracePeriod)
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertTrue(lifecycle.dunningNoticeDue(productId: productId), "default cadence [1, 2, 4] must be live before any apply()")
    }

    // MARK: - Config: reject paths keep the last-good config

    func test_applyNilOrBlankConfigIsRejected() async {
        let lifecycle = makeLifecycle()
        XCTAssertFalse(lifecycle.apply(nil).applied)
        XCTAssertFalse(lifecycle.apply("   ").applied)
        XCTAssertEqual(lifecycle.activeVersion(), "not loaded")
    }

    func test_applyMissingOrBlankVersionIsRejected() async {
        let lifecycle = makeLifecycle()
        XCTAssertFalse(lifecycle.apply(#"{"enabled": true}"#).applied)
        XCTAssertFalse(lifecycle.apply(#"{"version": "  "}"#).applied)
    }

    func test_applyMalformedJsonIsRejectedAndKeepsLastGoodConfig() async {
        let lifecycle = makeLifecycle()
        XCTAssertTrue(lifecycle.apply(#"{"version": "sub_v1", "dunning_cadence": [3]}"#).applied)
        let result = lifecycle.apply("{not valid json")
        XCTAssertFalse(result.applied)
        XCTAssertTrue(result.report.contains("sub_v1"), "report was: \(result.report)")
        XCTAssertEqual(lifecycle.activeVersion(), "sub_v1")
    }

    func test_rejectedConfigLeavesTheLastGoodCadenceRunning() async {
        let lifecycle = makeLifecycle()
        _ = lifecycle.apply(#"{"version": "sub_v1", "dunning_cadence": [3]}"#)
        _ = lifecycle.apply(#"{"dunning_cadence": [1]}"#) // rejected: no version
        _ = await lifecycle.observe([snapshot(.inBillingRetryPeriod, willAutoRenew: false)])
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertFalse(lifecycle.dunningNoticeDue(productId: productId), "cadence [3] from the last-good config must still govern")
        lifecycle.noteForegroundOpen(productId: productId)
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertTrue(lifecycle.dunningNoticeDue(productId: productId))
    }

    // MARK: - Config: wrong-typed fields degrade with a note, never silently (§3.8)

    func test_enabledWrongTypeDegradesToTheDefaultWithANote() async {
        let lifecycle = makeLifecycle()
        let result = lifecycle.apply(#"{"version": "sub_v2", "enabled": "false"}"#)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("enabled"), "report was: \(result.report)")
        _ = await lifecycle.observe([snapshot(.subscribed)])
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .active, "a wrong-typed 'enabled' must default to true, not disable the layer")
    }

    func test_winBackEnabledWrongTypeDegradesToTheDefaultWithANote() async {
        let lifecycle = makeLifecycle()
        let result = lifecycle.apply(#"{"version": "sub_v3", "win_back_enabled": 1}"#)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("win_back_enabled"), "report was: \(result.report)")
        _ = await lifecycle.observe([snapshot(.expired, willAutoRenew: false)])
        XCTAssertTrue(lifecycle.isWinBackEligible(productId: productId))
    }

    // MARK: - Config: dunning_cadence

    func test_dunningCadenceThatIsNotAnArrayIsRejected() async {
        let lifecycle = makeLifecycle()
        _ = lifecycle.apply(#"{"version": "sub_v4"}"#)
        let result = lifecycle.apply(#"{"version": "sub_v5", "dunning_cadence": "1,2,4"}"#)
        XCTAssertFalse(result.applied)
        XCTAssertEqual(lifecycle.activeVersion(), "sub_v4")
    }

    func test_dunningCadenceWithANonNumericElementIsRejected() async {
        let lifecycle = makeLifecycle()
        let result = lifecycle.apply(#"{"version": "sub_v6", "dunning_cadence": [1, "two", 4]}"#)
        XCTAssertFalse(result.applied)
        XCTAssertEqual(lifecycle.activeVersion(), "not loaded")
    }

    /// `parseOpenCount` deliberately does NOT route through `JsonConfigValue.numeric`, and its
    /// banner names three divergences. Two were already locked by tests; this is the third, which
    /// was cited against `test_dunningCadenceWithANonNumericElementIsRejected` — a test using
    /// `"two"`, which `numeric` would reject too, so it could never tell the two apart.
    ///
    /// A NUMERIC string is the case that distinguishes them: `numeric` accepts `"3"` as 3, this
    /// parser rejects it. Without this test, "simplifying" parseOpenCount onto the shared helper
    /// would silently start accepting string cadences and no test would object.
    func test_dunningCadenceWithANumericStringElementIsRejected() async {
        let lifecycle = makeLifecycle()
        let result = lifecycle.apply(#"{"version": "sub_v6b", "dunning_cadence": [1, "3", 4]}"#)
        XCTAssertFalse(result.applied,
                       "a numeric STRING is still not a number here, unlike JsonConfigValue.numeric")
        XCTAssertEqual(lifecycle.activeVersion(), "not loaded")
    }

    func test_dunningCadenceWithABooleanElementIsRejected() async {
        // `true` bridges out of JSONSerialization as an NSNumber that casts happily to 1 — it is
        // NOT a cadence value, and must be rejected rather than read as "notify on open 1".
        let lifecycle = makeLifecycle()
        XCTAssertFalse(lifecycle.apply(#"{"version": "sub_v7", "dunning_cadence": [true, 2]}"#).applied)
    }

    func test_dunningCadenceElementsBelowOneAreDroppedWithANote() async {
        let lifecycle = makeLifecycle()
        let result = lifecycle.apply(#"{"version": "sub_v8", "dunning_cadence": [0, -3, 2]}"#)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.isEmpty)
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertFalse(lifecycle.dunningNoticeDue(productId: productId))
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertTrue(lifecycle.dunningNoticeDue(productId: productId))
    }

    func test_dunningCadenceIsSortedAndDeduplicated() async {
        let lifecycle = makeLifecycle()
        let result = lifecycle.apply(#"{"version": "sub_v9", "dunning_cadence": [4, 1, 4, 2, 1]}"#)
        XCTAssertTrue(result.applied)
        XCTAssertTrue(result.report.contains("[1, 2, 4]"), "report was: \(result.report)")
    }

    func test_absurdlyLargeDunningCadenceElementIsClampedWithANote() async {
        let lifecycle = makeLifecycle()
        let result = lifecycle.apply(#"{"version": "sub_v10", "dunning_cadence": [999999999]}"#)
        XCTAssertTrue(result.applied)
        XCTAssertFalse(result.report.isEmpty)
        XCTAssertTrue(result.report.contains("1000"), "report was: \(result.report)")
    }

    /// Regression: `Int(someDouble)` TRAPS above `Int.max`, and these values reach that conversion
    /// while `999999999` above does not — it casts cleanly as `Int` and never enters the `Double`
    /// branch at all, so the test above provably could not have caught this.
    ///
    /// `JSONSerialization` returns any number too large for `Int64` as an `NSDecimalNumber` or
    /// `__NSCFNumber` wrapping a `Double`. Both payloads below are plain operator mistakes — one
    /// is an integer with a few too many digits — and before the fix each one crashed the host app
    /// on config fetch. Same bug class as Phase 5's Critical `parseWeight` finding.
    func test_dunningCadenceValuesBeyondIntMaxAreClampedRatherThanTrapping() async {
        for payload in [
            #"{"version": "sub_v10b", "dunning_cadence": [99999999999999999999]}"#,
            #"{"version": "sub_v10c", "dunning_cadence": [1e300]}"#,
            #"{"version": "sub_v10d", "dunning_cadence": [-1e300]}"#
        ] {
            let lifecycle = makeLifecycle()
            let result = lifecycle.apply(payload)
            XCTAssertTrue(result.applied, "payload was: \(payload)")
            XCTAssertFalse(result.report.isEmpty, "payload was: \(payload)")
        }
    }

    /// The positive case still classifies: a clamped-to-1000 cadence is a usable cadence, not a
    /// rejected config, so the subsystem keeps working after an operator typo.
    func test_aClampedCadenceStillProducesAWorkingLifecycle() async {
        let lifecycle = makeLifecycle()
        XCTAssertTrue(lifecycle.apply(#"{"version": "sub_v10e", "dunning_cadence": [1e300, 2]}"#).applied)
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        lifecycle.noteForegroundOpen(productId: productId)
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertTrue(lifecycle.dunningNoticeDue(productId: productId), "open 2 should still match")
    }

    /// Holds the guarantee `observe`'s `async` conversion exists for: the event is ALREADY sent
    /// when `observe` returns, with no polling and no suspension in between.
    ///
    /// Regressing to a detached `Task { await sink.send(event) }` reopens the drop window Phase 6's
    /// security review found in `grantFromServer` — state classified, persisted and returned while
    /// its event never reaches the queue because the process ended first. Every other event
    /// assertion in this file polls, and would pass against that regression; this one would not.
    func test_observeDeliversItsEventsBeforeReturning() async {
        let sink = FakeEventSink()
        let lifecycle = makeLifecycle(eventSink: sink)
        _ = await lifecycle.observe([snapshot(.subscribed)])
        // Read immediately — no poll, no sleep, no yield.
        let events = await sink.sentEvents
        XCTAssertEqual(events.count, 1, "event must be delivered before observe returns, not after")
        XCTAssertEqual(events.first?.eventName, "subscription_state")
    }

    func test_emptyDunningCadenceIsValidAndMeansNeverNotify() async {
        let lifecycle = makeLifecycle()
        XCTAssertTrue(lifecycle.apply(#"{"version": "sub_v11", "dunning_cadence": []}"#).applied)
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        for _ in 0..<5 {
            lifecycle.noteForegroundOpen(productId: productId)
            XCTAssertFalse(lifecycle.dunningNoticeDue(productId: productId))
        }
    }

    // MARK: - Classification (§3.3)

    func test_subscribedWithAutoRenewIsActiveAndEntitled() async {
        let lifecycle = makeLifecycle()
        let transitions = await lifecycle.observe([snapshot(.subscribed, willAutoRenew: true)])
        XCTAssertEqual(transitions, [SubscriptionTransition(productId: productId, from: .none, to: .active)])
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .active)
        XCTAssertTrue(lifecycle.stateFor(productId: productId).isEntitled)
    }

    func test_subscribedWithoutAutoRenewIsCancelledAndStillEntitled() async {
        // The single most common porting mistake off Play: cancelled is NOT churn.
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.subscribed, willAutoRenew: false)])
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .cancelled)
        XCTAssertTrue(lifecycle.stateFor(productId: productId).isEntitled)
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: productId))
    }

    func test_inGracePeriodIsEntitledAndRecoverable() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .inGracePeriod)
        XCTAssertTrue(lifecycle.stateFor(productId: productId).isEntitled)
        XCTAssertTrue(lifecycle.stateFor(productId: productId).isRecoverable)
    }

    func test_inBillingRetryIsNotEntitledButStillRecoverable() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.inBillingRetryPeriod, willAutoRenew: false)])
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .inBillingRetry)
        XCTAssertFalse(lifecycle.stateFor(productId: productId).isEntitled)
        XCTAssertTrue(lifecycle.stateFor(productId: productId).isRecoverable)
    }

    func test_expiredIsNeitherEntitledNorRecoverable() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.expired, willAutoRenew: false)])
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .expired)
        XCTAssertFalse(lifecycle.stateFor(productId: productId).isEntitled)
        XCTAssertFalse(lifecycle.stateFor(productId: productId).isRecoverable)
    }

    func test_revokedIsKeptDistinctFromExpired() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.revoked, willAutoRenew: false)])
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .revoked)
        XCTAssertFalse(lifecycle.stateFor(productId: productId).isEntitled)
    }

    func test_graceExpiryIntoBillingRetryIsNotTerminalAndStaysRecoverable() async {
        // Apple's GRACE_PERIOD_EXPIRED: entitlement is lost, but the charge is still being
        // retried. Treating this as churn is what Android does, and it hard-churns a user who is
        // still recoverable (§3.3).
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        let transitions = await lifecycle.observe([snapshot(.inBillingRetryPeriod, willAutoRenew: false)])
        XCTAssertEqual(transitions, [SubscriptionTransition(productId: productId, from: .inGracePeriod, to: .inBillingRetry)])
        XCTAssertTrue(lifecycle.stateFor(productId: productId).isRecoverable)
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: productId), "still being charged — must not be marked churned")
    }

    func test_billingRetryCanRecoverBackToActive() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.inBillingRetryPeriod, willAutoRenew: false)])
        let transitions = await lifecycle.observe([snapshot(.subscribed, willAutoRenew: true)])
        XCTAssertEqual(transitions, [SubscriptionTransition(productId: productId, from: .inBillingRetry, to: .active)])
    }

    func test_reObservingTheSameStateProducesNoTransition() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.subscribed)])
        let transitions = await lifecycle.observe([snapshot(.subscribed)])
        XCTAssertTrue(transitions.isEmpty)
    }

    func test_snapshotWithABlankProductIdIsIgnored() async {
        let lifecycle = makeLifecycle()
        let transitions = await lifecycle.observe([snapshot(.subscribed, productId: "")])
        XCTAssertTrue(transitions.isEmpty)
    }

    // MARK: - `.unknown` (§3.7) and absence (§3.4)

    func test_unknownOnANeverSeenProductGrantsNothingAndEmitsNoTransition() async {
        let lifecycle = makeLifecycle()
        let transitions = await lifecycle.observe([snapshot(.unknown)])
        XCTAssertTrue(transitions.isEmpty)
        XCTAssertEqual(lifecycle.stateFor(productId: productId), SubscriptionState.none)
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: productId))
    }

    func test_unknownRetainsTheProductsPriorActiveStateInsteadOfChurningIt() async {
        // The case Android leaves untested (§3.7): an already-`.active` product that starts
        // reporting a rawValue this SDK doesn't know must NOT silently lapse.
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.subscribed)])
        let transitions = await lifecycle.observe([snapshot(.unknown)])
        XCTAssertTrue(transitions.isEmpty)
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .active)
        XCTAssertTrue(lifecycle.stateFor(productId: productId).isEntitled)
    }

    func test_absenceFromACompleteSetRetainsStateAndIsNotInferredAsALapse() async {
        // Android infers LAPSED from absence; this port deliberately does not (§3.4).
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.subscribed)])
        let transitions = await lifecycle.observe([], complete: true)
        XCTAssertTrue(transitions.isEmpty)
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .active)
    }

    func test_incompleteObserveUpdatesOnlyTheProductItCarries() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.expired, willAutoRenew: false, productId: "sub_a"), snapshot(.subscribed, productId: "sub_b")])
        let transitions = await lifecycle.observe([snapshot(.subscribed, productId: "sub_a")], complete: false)
        XCTAssertEqual(transitions, [SubscriptionTransition(productId: "sub_a", from: .expired, to: .active)])
        XCTAssertEqual(lifecycle.stateFor(productId: "sub_b"), .active)
    }

    // MARK: - `subscription_state` event (§5)

    func test_transitionEmitsExactlyOneSubscriptionStateEventWithTheLockedWireShape() async {
        let sink = FakeEventSink()
        let lifecycle = makeLifecycle(eventSink: sink)
        _ = lifecycle.apply(#"{"version": "sub_v12"}"#)
        _ = await lifecycle.observe([snapshot(.subscribed)])

        let events = await waitForEvents(sink, count: 1)
        XCTAssertEqual(events.count, 1)
        let event = events[0]
        XCTAssertEqual(event.eventName, "subscription_state")
        XCTAssertEqual(event.eventData["product_id"], .string(productId))
        XCTAssertEqual(event.eventData["from_state"], .string("none"))
        XCTAssertEqual(event.eventData["to_state"], .string("active"))
        XCTAssertEqual(event.eventData["lifecycle_version"], .string("sub_v12"))
        XCTAssertNil(event.eventData["expiration_reason"])
        XCTAssertEqual(event.userProperty.userId, "p1")
    }

    func test_noEventIsEmittedOnANoOpReObserve() async {
        let sink = FakeEventSink()
        let lifecycle = makeLifecycle(eventSink: sink)
        _ = await lifecycle.observe([snapshot(.subscribed)])
        _ = await waitForEvents(sink, count: 1)
        _ = await lifecycle.observe([snapshot(.subscribed)])
        let events = await settledEvents(sink)
        XCTAssertEqual(events.count, 1, "a re-observe of the same state must not re-emit")
    }

    func test_expiredTransitionCarriesTheStatedExpirationReason() async {
        let sink = FakeEventSink()
        let lifecycle = makeLifecycle(eventSink: sink)
        _ = await lifecycle.observe([snapshot(.expired, willAutoRenew: false, expirationReason: .billingError)])
        let events = await waitForEvents(sink, count: 1)
        XCTAssertEqual(events.first?.eventData["expiration_reason"], .string("billingError"))
    }

    func test_expirationReasonIsOmittedWhenTheStoreStatedNone() async {
        let sink = FakeEventSink()
        let lifecycle = makeLifecycle(eventSink: sink)
        _ = await lifecycle.observe([snapshot(.expired, willAutoRenew: false, expirationReason: nil)])
        let events = await waitForEvents(sink, count: 1)
        XCTAssertEqual(events.first?.eventData["to_state"], .string("expired"))
        XCTAssertNil(events.first?.eventData["expiration_reason"])
    }

    func test_oneEventIsEmittedPerTransitionInAMultiProductObserve() async {
        let sink = FakeEventSink()
        let lifecycle = makeLifecycle(eventSink: sink)
        _ = await lifecycle.observe([
            snapshot(.subscribed, productId: "sub_a"),
            snapshot(.revoked, willAutoRenew: false, productId: "sub_b")
        ])
        let events = await waitForEvents(sink, count: 2)
        XCTAssertEqual(events.count, 2)
        let productIds = events.compactMap { event -> String? in
            guard case .string(let value)? = event.eventData["product_id"] else { return nil }
            return value
        }
        XCTAssertEqual(Set(productIds), ["sub_a", "sub_b"])
    }

    // MARK: - Dunning: command/query split (§3.5)

    func test_noticeIsDueOnTheFirstSecondAndFourthForegroundOpens() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        XCTAssertFalse(lifecycle.dunningNoticeDue(productId: productId), "nothing is due before the first foreground open")
        let due: [Bool] = (1...5).map { _ in
            lifecycle.noteForegroundOpen(productId: productId)
            return lifecycle.dunningNoticeDue(productId: productId)
        }
        XCTAssertEqual(due, [true, true, false, true, false])
    }

    func test_dunningNoticeDueIsPureAndRepeatedCallsNeverAdvanceTheCadence() async {
        // Android's single mutate-on-read method burns a notice every time it's called; rendering
        // two surfaces silently skipped one.
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        lifecycle.noteForegroundOpen(productId: productId)
        for _ in 0..<10 {
            XCTAssertTrue(lifecycle.dunningNoticeDue(productId: productId))
        }
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertTrue(lifecycle.dunningNoticeDue(productId: productId), "open 2 is still in the cadence")
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertFalse(lifecycle.dunningNoticeDue(productId: productId), "open 3 is not")
    }

    func test_dunningCountingContinuesAcrossGraceIntoBillingRetry() async {
        // Both are recoverable payment-problem states, so the episode does not restart (§3.5).
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        lifecycle.noteForegroundOpen(productId: productId)
        lifecycle.noteForegroundOpen(productId: productId)
        _ = await lifecycle.observe([snapshot(.inBillingRetryPeriod, willAutoRenew: false)])
        XCTAssertTrue(lifecycle.dunningNoticeDue(productId: productId), "open 2 stays due across the grace -> retry hop")
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertFalse(lifecycle.dunningNoticeDue(productId: productId), "open 3")
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertTrue(lifecycle.dunningNoticeDue(productId: productId), "open 4")
    }

    func test_leavingTheDunningWindowResetsTheEpisodeSoTheNextOneRestartsAtOne() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        lifecycle.noteForegroundOpen(productId: productId)
        lifecycle.noteForegroundOpen(productId: productId)
        lifecycle.noteForegroundOpen(productId: productId)
        _ = await lifecycle.observe([snapshot(.subscribed)]) // recovered — episode over
        XCTAssertFalse(lifecycle.dunningNoticeDue(productId: productId))
        _ = await lifecycle.observe([snapshot(.inGracePeriod)]) // a brand-new episode
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertTrue(lifecycle.dunningNoticeDue(productId: productId), "the next episode must restart at open 1")
    }

    func test_noticeIsNeverDueOutsideTheDunningWindow() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.subscribed)])
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertFalse(lifecycle.dunningNoticeDue(productId: productId))
        _ = await lifecycle.observe([snapshot(.expired, willAutoRenew: false)])
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertFalse(lifecycle.dunningNoticeDue(productId: productId))
    }

    // MARK: - Win-back eligibility

    func test_aLapsedSubscriberIsWinBackEligible() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.expired, willAutoRenew: false)])
        XCTAssertTrue(lifecycle.isWinBackEligible(productId: productId))
        XCTAssertEqual(lifecycle.winBackEligibleProductIds(), [productId])
    }

    /// `.revoked` is a refund OR a Family Sharing removal, indistinguishable on iOS 15. Offering a
    /// discount to the refunder half funds a purchase-refund-discount loop, so the blind tie breaks
    /// against offering — see `winBackEligibleLocked`'s banner for the full reasoning and the cost.
    func test_aRefundedSubscriberIsNotWinBackEligible() async {
        let refunded = makeLifecycle()
        _ = await refunded.observe([snapshot(.revoked, willAutoRenew: false)])
        XCTAssertEqual(refunded.stateFor(productId: productId), .revoked)
        XCTAssertFalse(refunded.isWinBackEligible(productId: productId))
        XCTAssertTrue(refunded.winBackEligibleProductIds().isEmpty)
    }

    func test_anEntitledSubscriberIsNeverWinBackEligible() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.subscribed)])
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: productId))
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: productId))
    }

    func test_aProductNeverSeenIsNotWinBackEligible() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.expired, willAutoRenew: false, productId: "sub_a")])
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: "never_owned"))
    }

    func test_winBackDisabledByConfigYieldsNoEligibleProducts() async {
        let lifecycle = makeLifecycle()
        XCTAssertTrue(lifecycle.apply(#"{"version": "sub_v13", "win_back_enabled": false}"#).applied)
        _ = await lifecycle.observe([snapshot(.expired, willAutoRenew: false)])
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: productId))
        XCTAssertTrue(lifecycle.winBackEligibleProductIds().isEmpty)
    }

    func test_resubscribingRemovesWinBackEligibilityImmediately() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.expired, willAutoRenew: false)])
        _ = await lifecycle.observe([snapshot(.subscribed)], complete: false)
        XCTAssertFalse(lifecycle.isWinBackEligible(productId: productId))
    }

    /// Only `.expired` qualifies. `.revoked` is withheld (refund abuse), `.subscribed` is still
    /// paying, and `.inBillingRetry` is someone Apple may yet charge successfully — discounting
    /// them mid-recovery converts a full-price renewal into a cheaper one.
    func test_winBackEligibleProductIdsListsLapsedProductsOnly() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([
            snapshot(.expired, willAutoRenew: false, productId: "sub_a"),
            snapshot(.revoked, willAutoRenew: false, productId: "sub_b"),
            snapshot(.subscribed, productId: "sub_c"),
            snapshot(.inBillingRetryPeriod, willAutoRenew: false, productId: "sub_d")
        ])
        XCTAssertEqual(lifecycle.winBackEligibleProductIds(), ["sub_a"])
    }

    // MARK: - Management URL

    func test_managementUrlIsTheAppStoreLinkAndNilOnceTheFeatureIsDisabled() async {
        let lifecycle = makeLifecycle()
        XCTAssertEqual(lifecycle.managementUrl(), "https://apps.apple.com/account/subscriptions")
        _ = lifecycle.apply(#"{"version": "sub_v14", "enabled": false}"#)
        XCTAssertNil(lifecycle.managementUrl())
    }

    // MARK: - Disabled by remote config

    func test_disabledConfigMakesObserveANoOpAndEveryStateNone() async {
        let sink = FakeEventSink()
        let lifecycle = makeLifecycle(eventSink: sink)
        _ = lifecycle.apply(#"{"version": "sub_v15", "enabled": false}"#)
        let transitions = await lifecycle.observe([snapshot(.subscribed)])
        XCTAssertTrue(transitions.isEmpty)
        XCTAssertEqual(lifecycle.stateFor(productId: productId), SubscriptionState.none)
        let events = await settledEvents(sink)
        XCTAssertTrue(events.isEmpty)
    }

    func test_disablingHidesButDoesNotDestroyStateSoReEnablingRestoresIt() async {
        let lifecycle = makeLifecycle()
        _ = await lifecycle.observe([snapshot(.subscribed)])
        _ = lifecycle.apply(#"{"version": "sub_v16", "enabled": false}"#)
        XCTAssertEqual(lifecycle.stateFor(productId: productId), SubscriptionState.none)
        _ = lifecycle.apply(#"{"version": "sub_v17", "enabled": true}"#)
        XCTAssertEqual(lifecycle.stateFor(productId: productId), .active)
    }

    // MARK: - Persistence

    func test_stateHistoryAndDunningSurviveAcrossInstancesSharingAStore() async {
        let store = InMemoryStore()
        let first = makeLifecycle(store: store)
        _ = await first.observe([snapshot(.inGracePeriod), snapshot(.expired, willAutoRenew: false, productId: "sub_old")])
        first.noteForegroundOpen(productId: productId)
        first.noteForegroundOpen(productId: productId)

        let second = makeLifecycle(store: store)
        XCTAssertEqual(second.stateFor(productId: productId), .inGracePeriod)
        XCTAssertEqual(second.stateFor(productId: "sub_old"), .expired)
        XCTAssertTrue(second.isWinBackEligible(productId: "sub_old"))
        XCTAssertTrue(second.dunningNoticeDue(productId: productId), "the dunning counter (2 opens) must survive a relaunch")
    }

    func test_persistedStateIsWrittenUnderTheDocumentedKeys() async {
        let store = InMemoryStore()
        let lifecycle = makeLifecycle(store: store)
        _ = await lifecycle.observe([snapshot(.subscribed)])
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertEqual(store.string(forKey: "sub_lifecycle_states"), "\(productId):active")
        XCTAssertEqual(store.string(forKey: "sub_lifecycle_history"), productId)
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        lifecycle.noteForegroundOpen(productId: productId)
        XCTAssertEqual(store.string(forKey: "sub_lifecycle_dunning"), "\(productId):1")
    }

    func test_corruptPersistedStateIsIgnoredRatherThanCrashing() async {
        let store = InMemoryStore()
        store.setString("garbage|\(productId):not_a_state|sub_ok:active", forKey: "sub_lifecycle_states")
        store.setString("\(productId):not_a_number", forKey: "sub_lifecycle_dunning")
        let lifecycle = makeLifecycle(store: store)
        XCTAssertEqual(lifecycle.stateFor(productId: productId), SubscriptionState.none)
        XCTAssertEqual(lifecycle.stateFor(productId: "sub_ok"), .active)
    }

    func test_resetForTestClearsBothMemoryAndTheStore() async {
        let store = InMemoryStore()
        let lifecycle = makeLifecycle(store: store)
        _ = lifecycle.apply(#"{"version": "sub_v18", "dunning_cadence": [9]}"#)
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        lifecycle.noteForegroundOpen(productId: productId)

        lifecycle.resetForTest()

        XCTAssertEqual(lifecycle.stateFor(productId: productId), SubscriptionState.none)
        XCTAssertEqual(lifecycle.activeVersion(), "not loaded")
        XCTAssertTrue(lifecycle.winBackEligibleProductIds().isEmpty)
        XCTAssertNil(store.string(forKey: "sub_lifecycle_states"))
        XCTAssertNil(store.string(forKey: "sub_lifecycle_history"))
        XCTAssertNil(store.string(forKey: "sub_lifecycle_dunning"))
    }

    // MARK: - Shared singleton

    func test_sharedSingletonIsUsableAndResettable() async {
        SubscriptionLifecycle.shared.configure(.init(eventSink: FakeEventSink(), playerId: "p1", store: InMemoryStore()))
        _ = await SubscriptionLifecycle.shared.observe([snapshot(.subscribed)])
        XCTAssertEqual(SubscriptionLifecycle.shared.stateFor(productId: productId), .active)
        SubscriptionLifecycle.shared.resetForTest()
        XCTAssertEqual(SubscriptionLifecycle.shared.stateFor(productId: productId), SubscriptionState.none)
    }

    // MARK: - Concurrency: persisted state must not regress (H1)

    /// ═══ THE LOST TRANSITION THIS CLOSES ═══
    ///
    /// `observe` mutates under the lock and then persists OUTSIDE it, and nothing ordered two of
    /// those writes. The first call encoded a states line naming one product; the second encoded a
    /// line naming BOTH. With the writes unordered, the older line could land LAST and storage
    /// would forget a transition that had already been classified, persisted as far as the caller
    /// was concerned, and reported to the host. In memory nothing looked wrong; after a restart the
    /// second product is back at `.none` — it loses `isEntitled`, so `RecurringGrants` stops
    /// granting to a paying subscriber, and its transition is re-emitted as if it had just
    /// happened.
    ///
    /// The ordering is engineered, not hoped for: the first write is armed to stall inside the
    /// store and the second `observe` runs 100ms into a 400ms stall.
    func test_aStragglingObserveWriteCannotEraseALaterTransitionFromStorage() async {
        let store = DelayingStore()
        let lifecycle = makeLifecycle(store: store)
        store.armDelay()

        let first = Task { await lifecycle.observe([self.snapshot(.subscribed)]).count }
        // Long enough for the first `observe` to leave the lock and be inside the stalling store call.
        try? await Task.sleep(nanoseconds: 100_000_000)
        let second = await lifecycle.observe([snapshot(.subscribed, productId: "sub_annual")])
        let firstCount = await first.value

        XCTAssertEqual(firstCount, 1)
        XCTAssertEqual(second.count, 1)
        let states = store.string(forKey: SubscriptionLifecycle.statesKey) ?? ""
        XCTAssertTrue(states.contains("sub_monthly:active"), "landed writes were: \(store.landed())")
        XCTAssertTrue(
            states.contains("sub_annual:active"),
            "the newest snapshot must be the one that stands; landed writes were: \(store.landed())"
        )

        // What the regression actually costs, made concrete: a process restart over the same store.
        let restarted = makeLifecycle(store: store)
        XCTAssertEqual(restarted.stateFor(productId: "sub_monthly"), .active)
        XCTAssertEqual(restarted.stateFor(productId: "sub_annual"), .active,
                       "a classified transition must not be forgotten by a restart")
    }

    /// The other writer of `sub_lifecycle_dunning`: `noteForegroundOpen`. Its write and `observe`'s
    /// go to the same key from two different call paths, so they are exactly the pair that has to
    /// be ordered. Here the stalling write is the counter bump; the `observe` that follows clears
    /// the episode, and it is the CLEARED line that must survive.
    func test_aStragglingDunningBumpCannotResurrectAClearedEpisode() async {
        let store = DelayingStore()
        let lifecycle = makeLifecycle(store: store)
        _ = await lifecycle.observe([snapshot(.inGracePeriod)])
        store.armDelay()

        let bump = Task { lifecycle.noteForegroundOpen(productId: self.productId) }
        try? await Task.sleep(nanoseconds: 100_000_000)
        // Leaving the recoverable window resets the episode, which rewrites the dunning line empty.
        _ = await lifecycle.observe([snapshot(.expired)])
        await bump.value

        XCTAssertEqual(
            store.string(forKey: SubscriptionLifecycle.dunningKey), "",
            "the cleared episode must stand; landed writes were: \(store.landed())"
        )
    }
}
