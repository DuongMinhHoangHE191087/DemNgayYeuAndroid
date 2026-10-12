import XCTest
@testable import MonetizationBrainCore

/// Covers `SdkEvent.sequenceId` end to end: the counter itself, and `EventQueue` as the one
/// place that stamps it. Kept in its own file rather than folded into `EventQueueTests` so the
/// numbering contract reads as one story, and so this doesn't collide with concurrent edits to
/// the existing event tests.
final class EventSequenceIdTests: XCTestCase {
    /// In-memory `BrainKeyValueStore` — redeclared per-file, matching this project's established
    /// test-fixture convention. Locked because the concurrency test hits it from many tasks.
    private final class InMemoryStore: BrainKeyValueStore, @unchecked Sendable {
        private let lock = NSLock()
        private var values: [String: String] = [:]
        func string(forKey key: String) -> String? {
            lock.lock(); defer { lock.unlock() }
            return values[key]
        }
        func setString(_ value: String, forKey key: String) {
            lock.lock(); defer { lock.unlock() }
            values[key] = value
        }
        func removeObject(forKey key: String) {
            lock.lock(); defer { lock.unlock() }
            values.removeValue(forKey: key)
        }
    }

    private var directory: URL!

    override func setUp() {
        super.setUp()
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    private func makeQueue(
        store: BrainKeyValueStore,
        sender: EventSender,
        maxRecords: Int = 5000
    ) -> EventQueue {
        EventQueue(
            directory: directory,
            maxRecords: maxRecords,
            sender: sender,
            sequenceCounter: EventSequenceCounter(store: store)
        )
    }

    // MARK: - Counter

    /// Android reads `get("SEQUENCE_ID", 0)` then pre-increments, so a fresh install's first
    /// event is 1 and 0 is reserved for "never numbered".
    func test_firstNumberOnAFreshInstallIsOneNotZero() {
        let counter = EventSequenceCounter(store: InMemoryStore())
        XCTAssertEqual(counter.next(), 1)
    }

    func test_counterIssuesConsecutiveNumbers() {
        let counter = EventSequenceCounter(store: InMemoryStore())
        XCTAssertEqual([counter.next(), counter.next(), counter.next()], [1, 2, 3])
    }

    func test_counterPersistsEveryBumpImmediately() {
        let store = InMemoryStore()
        let counter = EventSequenceCounter(store: store)
        _ = counter.next()
        _ = counter.next()
        XCTAssertEqual(store.string(forKey: EventSequenceCounter.storageKey), "2")
    }

    /// A corrupted preference must never trap the host app; restarting the numbering is the
    /// documented fallback (the backend dedupes on `dedup_key`, not on this column).
    func test_counterRestartsFromOneWhenStoredValueIsCorrupt() {
        let store = InMemoryStore()
        store.setString("not-a-number", forKey: EventSequenceCounter.storageKey)
        XCTAssertEqual(EventSequenceCounter(store: store).next(), 1)

        store.setString("-5", forKey: EventSequenceCounter.storageKey)
        XCTAssertEqual(EventSequenceCounter(store: store).next(), 1)
    }

    // MARK: - EventQueue stamping

    func test_queueStampsMonotonicNumbersOnSentEvents() async {
        let sender = FakeSender()
        let queue = makeQueue(store: InMemoryStore(), sender: sender)

        await queue.send(SdkEvent(eventName: "a"))
        await queue.send(SdkEvent(eventName: "b"))
        await queue.send(SdkEvent(eventName: "c"))

        await queue.quiesce()   // `send` returns at persist now; wait for delivery
        let sent = await sender.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["a", "b", "c"])
        XCTAssertEqual(sent.map(\.sequenceId), [1, 2, 3])
    }

    /// The queue is the sole authority on the numbering — a call site that sets this by hand
    /// must not be able to punch a hole in it.
    func test_queueOverwritesACallerSuppliedSequenceId() async {
        let sender = FakeSender()
        let queue = makeQueue(store: InMemoryStore(), sender: sender)

        await queue.send(SdkEvent(eventName: "a", sequenceId: 9_999))

        await queue.quiesce()   // `send` returns at persist now; wait for delivery
        let sent = await sender.sentEvents
        XCTAssertEqual(sent.map(\.sequenceId), [1])
    }

    /// The number is on the PERSISTED record, not just the in-flight copy — that is what lets a
    /// retry after a crash reuse it.
    func test_persistedRecordCarriesTheNumber() async {
        let queue = makeQueue(store: InMemoryStore(), sender: FakeSender(shouldFail: true))

        await queue.send(SdkEvent(eventName: "a"))
        await queue.send(SdkEvent(eventName: "b"))

        let queued = await queue.queuedSequenceIdsForTest()
        XCTAssertEqual(queued, [1, 2])
    }

    // MARK: - Persistence across a relaunch

    /// Simulated relaunch: a brand-new `EventQueue` and a brand-new `EventSequenceCounter` over
    /// the SAME store must continue the numbering, not restart it. This is the whole reason the
    /// counter is store-backed rather than an in-memory `Int`.
    func test_numberingContinuesAcrossASimulatedRelaunch() async {
        let store = InMemoryStore()

        let firstLaunchSender = FakeSender()
        let firstLaunch = makeQueue(store: store, sender: firstLaunchSender)
        await firstLaunch.send(SdkEvent(eventName: "a"))
        await firstLaunch.send(SdkEvent(eventName: "b"))

        let secondLaunchSender = FakeSender()
        let secondLaunch = makeQueue(store: store, sender: secondLaunchSender)
        await secondLaunch.send(SdkEvent(eventName: "c"))

        await firstLaunch.quiesce()
        await secondLaunch.quiesce()
        let first = await firstLaunchSender.sentEvents
        let second = await secondLaunchSender.sentEvents
        XCTAssertEqual(first.map(\.sequenceId), [1, 2])
        XCTAssertEqual(second.map(\.sequenceId), [3])
    }

    // MARK: - Retry path

    /// A retry re-sends the SAME number. Android renumbers on replay because its durable queue
    /// stores decoded fields and the original number isn't recoverable; this queue stores the
    /// full envelope, so a duplicate delivery stays an exact duplicate and a gap in the ingested
    /// numbers keeps meaning "an event never arrived".
    func test_retryReusesTheOriginalNumberAndDoesNotBurnCounterValues() async {
        let store = InMemoryStore()
        let sender = FakeSender(shouldFail: true)
        let queue = makeQueue(store: store, sender: sender)

        await queue.send(SdkEvent(eventName: "a"))
        await queue.flush()
        await queue.flush()

        await sender.setShouldFail(false)
        await queue.flush()

        await queue.quiesce()   // `send` returns at persist now; wait for delivery
        let sent = await sender.sentEvents
        XCTAssertEqual(sent.count, 4, "one initial attempt plus three flush attempts")
        XCTAssertEqual(Set(sent.map(\.sequenceId)), [1], "every attempt carries the original number")

        let queuedCount = await queue.queuedCount()
        XCTAssertEqual(queuedCount, 0)

        // Three failed attempts consumed no numbers, so the next NEW event is 2, not 5.
        let followUpSender = FakeSender()
        let followUpQueue = makeQueue(store: store, sender: followUpSender)
        await followUpQueue.send(SdkEvent(eventName: "b"))
        await followUpQueue.quiesce()
        let followUp = await followUpSender.sentEvents
        XCTAssertEqual(followUp.map(\.sequenceId), [2])
    }

    /// A record written before numbering existed decodes with 0 and is replayed with 0 — it is
    /// not retroactively renumbered into the live sequence.
    func test_flushDoesNotRenumberRecordsPersistedWithoutASequenceId() async {
        let store = InMemoryStore()
        let legacy = SdkEvent(eventName: "legacy_event")
        XCTAssertEqual(legacy.sequenceId, 0)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let line = String(data: try! JSONEncoder().encode(legacy), encoding: .utf8)!
        try! Data((line + "\n").utf8)
            .write(to: directory.appendingPathComponent("monetization_brain_event_queue.jsonl"))

        let sender = FakeSender()
        let queue = makeQueue(store: store, sender: sender)
        await queue.flush()

        await queue.quiesce()   // `send` returns at persist now; wait for delivery
        let sent = await sender.sentEvents
        XCTAssertEqual(sent.map(\.sequenceId), [0])
    }

    // MARK: - Concurrency

    /// Events are emitted from many `async` call sites at once. `EventQueue` is an actor and the
    /// counter is lock-guarded, so the read-modify-write can never interleave: N concurrent
    /// sends must produce exactly 1...N, with no duplicate and no gap.
    func test_concurrentSendsProduceNoDuplicatesAndNoGaps() async {
        let count = 64
        let sender = FakeSender()
        let queue = makeQueue(store: InMemoryStore(), sender: sender)

        await withTaskGroup(of: Void.self) { group in
            for index in 0..<count {
                group.addTask { await queue.send(SdkEvent(eventName: "event_\(index)")) }
            }
        }

        await queue.quiesce()   // `send` returns at persist now; wait for delivery
        let sent = await sender.sentEvents
        let ids = sent.map(\.sequenceId).sorted()
        XCTAssertEqual(ids.count, count)
        XCTAssertEqual(Set(ids).count, count, "no duplicate sequence ids")
        XCTAssertEqual(ids, Array(1...count), "contiguous, starting at 1")
    }

    /// Same check one level down: many tasks racing on the counter alone.
    func test_concurrentCounterAccessIssuesEachNumberExactlyOnce() async {
        let count = 200
        let counter = EventSequenceCounter(store: InMemoryStore())

        let ids = await withTaskGroup(of: Int.self) { group -> [Int] in
            for _ in 0..<count {
                group.addTask { counter.next() }
            }
            var collected: [Int] = []
            for await id in group { collected.append(id) }
            return collected
        }

        XCTAssertEqual(Set(ids).count, count)
        XCTAssertEqual(ids.sorted(), Array(1...count))
        XCTAssertEqual(counter.lastIssuedValue(), count)
    }
}

private actor FakeSender: EventSender {
    private(set) var sentEvents: [SdkEvent] = []
    private var shouldFail: Bool

    init(shouldFail: Bool = false) {
        self.shouldFail = shouldFail
    }

    func setShouldFail(_ value: Bool) {
        shouldFail = value
    }

    func send(_ event: SdkEvent) async throws {
        sentEvents.append(event)
        if shouldFail { throw EventSenderError.transport("fake failure") }
    }
}
