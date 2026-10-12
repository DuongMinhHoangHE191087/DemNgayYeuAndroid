import XCTest
@testable import MonetizationBrainCore

final class BrainStorageTests: XCTestCase {
    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? { values[key] }
        func setString(_ value: String, forKey key: String) { values[key] = value }
        func removeObject(forKey key: String) { values.removeValue(forKey: key) }
    }

    /// Makes one write stall so a later one can overtake it — the store-side analogue of the
    /// `StallingClock` used elsewhere in this suite. A race test with no engineered overlap has
    /// already been measured in this codebase as detecting nothing.
    private final class DelayingStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        private var stallNext = false
        private(set) var landedOrder: [String] = []

        func armStall() { lock.lock(); stallNext = true; lock.unlock() }

        func string(forKey key: String) -> String? {
            lock.lock(); defer { lock.unlock() }; return values[key]
        }

        func setString(_ value: String, forKey key: String) {
            lock.lock()
            let shouldStall = stallNext
            if shouldStall { stallNext = false }
            lock.unlock()
            if shouldStall { Thread.sleep(forTimeInterval: 0.4) }
            lock.lock()
            values[key] = value
            landedOrder.append(value)
            lock.unlock()
        }

        func removeObject(forKey key: String) {
            lock.lock(); _ = values.removeValue(forKey: key); lock.unlock()
        }
    }

    /// A straggling write must not revert belief to an older snapshot.
    ///
    /// Both writes are issued in sequence order, but the first stalls inside the store long enough
    /// for the second to land first. Without ordering, the stale snapshot lands last and stands —
    /// invisible until the next process reads it back.
    func test_aStragglingBeliefWriteCannotRevertToAnOlderSnapshot() {
        let store = DelayingStore()
        let storage = BrainStorage(store: store)

        let oldSequence = storage.nextWriteSequence()
        let newSequence = storage.nextWriteSequence()
        XCTAssertLessThan(oldSequence, newSequence)

        store.armStall()
        let stalled = Thread.detachNewThread {
            storage.saveBelief(playerId: "p1", serialized: "OLD", sequence: oldSequence)
        }
        _ = stalled
        Thread.sleep(forTimeInterval: 0.1)   // let the stalled write get inside the store
        storage.saveBelief(playerId: "p1", serialized: "NEW", sequence: newSequence)
        Thread.sleep(forTimeInterval: 0.6)   // let the straggler finish

        XCTAssertEqual(storage.loadBelief(playerId: "p1"), "NEW",
                       "the newer snapshot must stand; landed order was \(store.landedOrder)")
    }

    /// A straggling save must not resurrect a belief a later clear removed.
    func test_aStragglingBeliefWriteCannotResurrectAClearedBelief() {
        let store = DelayingStore()
        let storage = BrainStorage(store: store)
        let saveSequence = storage.nextWriteSequence()
        let clearSequence = storage.nextWriteSequence()

        store.armStall()
        Thread.detachNewThread {
            storage.saveBelief(playerId: "p1", serialized: "STALE", sequence: saveSequence)
        }
        Thread.sleep(forTimeInterval: 0.1)
        storage.clearBelief(playerId: "p1", sequence: clearSequence)
        Thread.sleep(forTimeInterval: 0.6)

        XCTAssertNil(storage.loadBelief(playerId: "p1"),
                     "a straggling save must not undo a later clear")
    }

    private final class FakeClock: BrainClock, @unchecked Sendable {
        private let lock = NSLock()
        private var current: Int64
        init(_ start: Int64) { self.current = start }
        func nowMs() -> Int64 { lock.lock(); defer { lock.unlock() }; return current }
        func advance(ms: Int64) { lock.lock(); current += ms; lock.unlock() }
    }

    private static let dayMs: Int64 = 86_400_000
    private static let noon: Int64 = 1_700_000_000_000 + dayMs / 2

    // MARK: - Daily IAP-offer count

    func test_offerCountIsZeroWithNoRecord() {
        let storage = BrainStorage(store: InMemoryStore(), clock: FakeClock(Self.noon))
        XCTAssertEqual(storage.loadOfferCount24h(), 0)
    }

    func test_bumpIncrementsAndReturnsTheNewTotal() {
        let storage = BrainStorage(store: InMemoryStore(), clock: FakeClock(Self.noon))
        XCTAssertEqual(storage.bumpOfferCount24h(), 1)
        XCTAssertEqual(storage.bumpOfferCount24h(), 2)
        XCTAssertEqual(storage.loadOfferCount24h(), 2)
    }

    /// The whole reason this exists: a relaunch on the same day must NOT hand the allowance back.
    /// A fresh `BrainStorage` over the same store IS the relaunch.
    func test_offerCountSurvivesARelaunchOnTheSameDay() {
        let store = InMemoryStore()
        let clock = FakeClock(Self.noon)
        let firstRun = BrainStorage(store: store, clock: clock)
        _ = firstRun.bumpOfferCount24h()
        _ = firstRun.bumpOfferCount24h()
        _ = firstRun.bumpOfferCount24h()

        let afterRelaunch = BrainStorage(store: store, clock: clock)
        XCTAssertEqual(
            afterRelaunch.loadOfferCount24h(), 3,
            "closing and reopening the app must not reset the day's offer count"
        )
        XCTAssertEqual(afterRelaunch.bumpOfferCount24h(), 4, "and it keeps counting from where it was")
    }

    /// UTC calendar day, not a sliding window — matching Android and its stated trade: the
    /// allowance refills at midnight, which is fine for an anti-annoyance ceiling.
    func test_offerCountResetsWhenTheUtcDayChanges() {
        let store = InMemoryStore()
        let clock = FakeClock(Self.noon)
        let storage = BrainStorage(store: store, clock: clock)
        _ = storage.bumpOfferCount24h()
        _ = storage.bumpOfferCount24h()

        clock.advance(ms: Self.dayMs)     // same time, next day
        XCTAssertEqual(storage.loadOfferCount24h(), 0, "yesterday's count is not read, let alone carried")
        XCTAssertEqual(storage.bumpOfferCount24h(), 1, "the new day starts from one")

        // And a relaunch on the NEW day sees the new day's count, not yesterday's stale one.
        let afterRelaunch = BrainStorage(store: store, clock: clock)
        XCTAssertEqual(afterRelaunch.loadOfferCount24h(), 1)
    }

    /// Two concurrent bumps must not both read N and both write N+1. Forced contention rather
    /// than hoped-for overlap: 64 tasks, exactly 64 counted.
    func test_concurrentBumpsNeverLoseAnIncrement() async {
        let storage = BrainStorage(store: InMemoryStore(), clock: FakeClock(Self.noon))
        await withTaskGroup(of: Void.self) { group in
            for _ in 0..<64 {
                group.addTask { _ = storage.bumpOfferCount24h() }
            }
        }
        XCTAssertEqual(storage.loadOfferCount24h(), 64)
    }

    func test_loadBeliefReturnsNilWhenNeverSaved() {
        let storage = BrainStorage(store: InMemoryStore())
        XCTAssertNil(storage.loadBelief(playerId: "p1"))
    }

    func test_saveThenLoadBeliefRoundTrips() {
        let storage = BrainStorage(store: InMemoryStore())
        storage.saveBelief(playerId: "p1", serialized: "some-serialized-belief")
        XCTAssertEqual(storage.loadBelief(playerId: "p1"), "some-serialized-belief")
    }

    func test_beliefIsIsolatedPerPlayer() {
        let storage = BrainStorage(store: InMemoryStore())
        storage.saveBelief(playerId: "p1", serialized: "belief-p1")
        storage.saveBelief(playerId: "p2", serialized: "belief-p2")
        XCTAssertEqual(storage.loadBelief(playerId: "p1"), "belief-p1")
        XCTAssertEqual(storage.loadBelief(playerId: "p2"), "belief-p2")
    }

    func test_clearBeliefRemovesOnlyThatPlayer() {
        let storage = BrainStorage(store: InMemoryStore())
        storage.saveBelief(playerId: "p1", serialized: "belief-p1")
        storage.saveBelief(playerId: "p2", serialized: "belief-p2")
        storage.clearBelief(playerId: "p1")
        XCTAssertNil(storage.loadBelief(playerId: "p1"))
        XCTAssertEqual(storage.loadBelief(playerId: "p2"), "belief-p2")
    }

    func test_blankBeliefTreatedAsAbsent() {
        let store = InMemoryStore()
        store.setString("   ", forKey: "brain_belief_p1")
        let storage = BrainStorage(store: store)
        XCTAssertNil(storage.loadBelief(playerId: "p1"))
    }

    func test_loadBanditStatsReturnsNilWhenNeverSaved() {
        let storage = BrainStorage(store: InMemoryStore())
        XCTAssertNil(storage.loadBanditStats())
    }

    func test_saveThenLoadBanditStatsRoundTrips() {
        let storage = BrainStorage(store: InMemoryStore())
        storage.saveBanditStats("seg|arm=2.0,3.0")
        XCTAssertEqual(storage.loadBanditStats(), "seg|arm=2.0,3.0")
    }

    func test_userDefaultsBrainStoreRoundTripsThroughRealUserDefaults() {
        let suiteName = "BrainStorageTests.\(UUID().uuidString)"
        guard let defaults = UserDefaults(suiteName: suiteName) else {
            return XCTFail("could not create isolated UserDefaults suite")
        }
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let store = UserDefaultsBrainStore(defaults: defaults)
        store.setString("value", forKey: "key")
        XCTAssertEqual(store.string(forKey: "key"), "value")
        store.removeObject(forKey: "key")
        XCTAssertNil(store.string(forKey: "key"))
    }
}
