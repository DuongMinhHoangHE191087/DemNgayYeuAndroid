import XCTest
@testable import MonetizationBrainCore

final class EventQueueTests: XCTestCase {
    private var directory: URL!

    override func setUp() {
        super.setUp()
        directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    }

    override func tearDown() {
        try? FileManager.default.removeItem(at: directory)
        super.tearDown()
    }

    func test_sendPersistsAndDeliversOnSuccess() async {
        let sender = FakeEventSender()
        let queue = EventQueue(directory: directory, sender: sender)

        await queue.send(SdkEvent(eventName: "debug_ping"))
        await queue.quiesce()   // `send` returns at persist; delivery is what removes the record

        let queuedCount = await queue.queuedCount()
        let sent = await sender.sentEvents
        XCTAssertEqual(queuedCount, 0)
        XCTAssertEqual(sent.map(\.eventName), ["debug_ping"])
    }

    func test_sendKeepsEventQueuedOnSenderFailure() async {
        let sender = FakeEventSender(shouldFail: true)
        let queue = EventQueue(directory: directory, sender: sender)

        await queue.send(SdkEvent(eventName: "debug_ping"))

        let queuedCount = await queue.queuedCount()
        XCTAssertEqual(queuedCount, 1)
    }

    func test_flushRetriesQueuedEventsAndRemovesOnSuccess() async {
        let sender = FakeEventSender(shouldFail: true)
        let queue = EventQueue(directory: directory, sender: sender)
        await queue.send(SdkEvent(eventName: "debug_ping"))
        // Wait for the background attempt to fail before flipping the sender. Without this the
        // detached attempt and the flush can BOTH deliver, and the event arrives twice — which is
        // tolerated in production (at-least-once) but makes this assertion non-deterministic.
        await queue.quiesce()
        let queuedBeforeFlush = await queue.queuedCount()
        XCTAssertEqual(queuedBeforeFlush, 1)

        await sender.setShouldFail(false)
        await queue.flush()

        let queuedCount = await queue.queuedCount()
        let sent = await sender.sentEvents
        XCTAssertEqual(queuedCount, 0)
        XCTAssertEqual(sent.map(\.eventName), ["debug_ping"])
    }

    func test_queueSurvivesAcrossInstancesPointingAtSameDirectory() async {
        let failingSender = FakeEventSender(shouldFail: true)
        let queueBeforeRestart = EventQueue(directory: directory, sender: failingSender)
        await queueBeforeRestart.send(SdkEvent(eventName: "debug_ping"))
        let queuedBeforeRestart = await queueBeforeRestart.queuedCount()
        XCTAssertEqual(queuedBeforeRestart, 1)

        let succeedingSender = FakeEventSender()
        let queueAfterRestart = EventQueue(directory: directory, sender: succeedingSender)
        let queuedAfterRestart = await queueAfterRestart.queuedCount()
        XCTAssertEqual(queuedAfterRestart, 1)

        await queueAfterRestart.flush()

        let queuedAfterFlush = await queueAfterRestart.queuedCount()
        await queueAfterRestart.quiesce()
        let sent = await succeedingSender.sentEvents
        XCTAssertEqual(queuedAfterFlush, 0)
        XCTAssertEqual(sent.map(\.eventName), ["debug_ping"])
    }

    func test_maxRecordsTrimsOldestFirst() async {
        let sender = FakeEventSender(shouldFail: true)
        let queue = EventQueue(directory: directory, maxRecords: 2, sender: sender)

        await queue.send(SdkEvent(eventName: "a"))
        await queue.send(SdkEvent(eventName: "b"))
        await queue.send(SdkEvent(eventName: "c"))

        let names = await queue.queuedEventNamesForTest()
        XCTAssertEqual(names, ["b", "c"])
    }
    // MARK: - Cost of the durable path

    /// ═══ WHY A TEST ASSERTS ON A WRITE COUNT ═══
    ///
    /// This queue used to rewrite the WHOLE file on every enqueue, which is O(n) per event and
    /// quadratic to fill — measured at 1.6 s to enqueue 500 events, on the exact case a durable
    /// queue exists for (a device offline long enough to accumulate them). The fix is Android's:
    /// append, and rewrite only to trim. The observable property is that the number of whole-file
    /// rewrites stops growing with the number of events, so that is what this asserts; a timing
    /// assertion would be flaky and would not say WHY it got fast.
    func test_enqueueAppendsInsteadOfRewritingTheWholeFile() async {
        let sender = FakeEventSender(shouldFail: true)
        let queue = EventQueue(directory: directory, maxRecords: 500, sender: sender)

        for index in 0..<50 {
            await queue.send(SdkEvent(eventName: "e\(index)"))
        }

        let rewrites = await queue.rewriteCountForTest
        let queued = await queue.queuedCount()
        XCTAssertEqual(queued, 50)
        XCTAssertEqual(rewrites, 1, "only the very first write, which has no file to append to")
    }

    /// Trimming is the one enqueue path that still rewrites — and it has to, since dropping the
    /// oldest records means rewriting the file. The cached count is what decides when to do it,
    /// so this also pins that the cache does not go stale and skip a trim.
    func test_trimmingStillRewritesAndDropsTheOldestFirst() async {
        let sender = FakeEventSender(shouldFail: true)
        let queue = EventQueue(directory: directory, maxRecords: 3, sender: sender)

        for index in 0..<5 {
            await queue.send(SdkEvent(eventName: "e\(index)"))
        }

        let names = await queue.queuedEventNamesForTest()
        XCTAssertEqual(names, ["e2", "e3", "e4"], "the newest 3 survive")
    }

    /// The other half: draining used to rewrite the file once per delivered event. Now it
    /// rewrites once per `removalCheckpoint`, which is the bound on how many already-delivered
    /// events a process killed mid-flush can re-send.
    func test_flushRewritesOncePerCheckpointNotOncePerEvent() async {
        let failing = FakeEventSender(shouldFail: true)
        let prefill = EventQueue(directory: directory, maxRecords: 500, sender: failing)
        let count = EventQueue.removalCheckpoint * 2 + 3
        for index in 0..<count {
            await prefill.send(SdkEvent(eventName: "e\(index)"))
        }

        let queue = EventQueue(directory: directory, maxRecords: 500, sender: FakeEventSender())
        await queue.flush()

        let rewrites = await queue.rewriteCountForTest
        let queued = await queue.queuedCount()
        XCTAssertEqual(queued, 0)
        XCTAssertEqual(rewrites, 3, "two checkpoints plus the final partial batch, not \(count)")
    }

    /// ═══ THE REASON `removeLines` RE-READS THE FILE ═══
    ///
    /// `flush` suspends on the network, and the actor is reentrant, so a `send` from the app can
    /// append a new record in the middle of a drain. If the batched removal wrote back a snapshot
    /// taken before that suspension, the new event would be erased — a durable queue losing the
    /// most recent event is the worst thing it can do. Re-reading and removing by content makes
    /// the batch behave exactly like the one-at-a-time removal it replaced.
    func test_anEventEnqueuedDuringAFlushIsNotErasedByTheBatchedRemoval() async {
        let failing = FakeEventSender(shouldFail: true)
        let prefill = EventQueue(directory: directory, maxRecords: 500, sender: failing)
        for index in 0..<3 {
            await prefill.send(SdkEvent(eventName: "old\(index)"))
        }

        let sender = ReentrantSender()
        let queue = EventQueue(directory: directory, maxRecords: 500, sender: sender)
        await sender.attach(queue)

        await queue.flush()

        let names = await queue.queuedEventNamesForTest()
        XCTAssertEqual(names, ["injected"], "the event enqueued mid-flush must survive the drain")
    }

    /// An append killed mid-write leaves a partial trailing line. That is not a new failure mode —
    /// it is the one the queue already had an answer for: a record that cannot be decoded is
    /// dropped rather than retried forever. This pins that the answer still holds now that torn
    /// lines are reachable by design rather than only by a torn atomic replace.
    func test_aTornTrailingLineIsDroppedAndTheGoodRecordsStillGoOut() async {
        let failing = FakeEventSender(shouldFail: true)
        let prefill = EventQueue(directory: directory, maxRecords: 500, sender: failing)
        await prefill.send(SdkEvent(eventName: "good"))

        let fileURL = directory.appendingPathComponent("monetization_brain_event_queue.jsonl")
        let torn = try? FileHandle(forWritingTo: fileURL)
        try? torn?.seekToEnd()
        try? torn?.write(contentsOf: Data(#"{"eventName":"tor"#.utf8))
        try? torn?.close()

        let sender = FakeEventSender()
        let queue = EventQueue(directory: directory, maxRecords: 500, sender: sender)
        await queue.flush()

        let queued = await queue.queuedCount()
        await queue.quiesce()   // `send` returns at persist now; wait for delivery
        let sent = await sender.sentEvents
        XCTAssertEqual(queued, 0, "both the delivered record and the torn one are gone")
        XCTAssertEqual(sent.map(\.eventName), ["good"])
    }

    /// The cached record count is the one piece of state that can drift from the file, and the
    /// drift that matters is DOWNWARD: a count that is too low means the trim never fires and the
    /// queue grows without bound on a long-offline device — the exact failure `maxRecords` exists
    /// to prevent. This runs every write path in one sequence (append, trim, flush, append again)
    /// and checks the file is still bounded afterwards; verified to fail when the append path
    /// stops updating the cache.
    ///
    /// It does NOT catch a count that is too HIGH — that only makes the trim fire early, and
    /// `test_maxRecordsTrimsOldestFirst` is what fails on it. Saying so here so the name is not
    /// read as covering both directions.
    func test_theTrimStillFiresAfterAMixOfAppendsRemovalsAndFlushes() async {
        let failing = FakeEventSender(shouldFail: true)
        let queue = EventQueue(directory: directory, maxRecords: 4, sender: failing)
        for index in 0..<6 {
            await queue.send(SdkEvent(eventName: "e\(index)"))   // appends + a trim
        }
        await queue.flush()                                        // still failing: nothing removed
        await failing.setShouldFail(false)
        await queue.send(SdkEvent(eventName: "delivered"))         // append + batched removal

        let ownCount = await queue.queuedCount()
        let freshReader = EventQueue(directory: directory, maxRecords: 4, sender: FakeEventSender())
        let fileCount = await freshReader.queuedCount()
        XCTAssertEqual(ownCount, fileCount)
        XCTAssertLessThanOrEqual(fileCount, 4, "the trim must still fire")
    }

    /// ═══ A SECOND DRAIN IS NOT A SECOND DRAIN ═══
    ///
    /// `flush` is public, host-driven, and suspends on every send, so two calls interleave freely.
    /// Both of its stated bounds — at most `removalCheckpoint` re-sends after a kill, one rewrite
    /// per `removalCheckpoint` — are per-invocation, and a second concurrent drain doubles them
    /// while re-sending records the first has delivered but not yet removed. The overlap here is
    /// engineered, not hoped for: the sender re-enters `flush` from inside a send, which is the
    /// exact interleaving the actor allows.
    func test_aFlushThatStartsWhileAnotherIsDrainingIsANoOp() async {
        let failing = FakeEventSender(shouldFail: true)
        let prefill = EventQueue(directory: directory, maxRecords: 500, sender: failing)
        for index in 0..<4 {
            await prefill.send(SdkEvent(eventName: "e\(index)"))
        }

        let sender = ReflushingSender()
        let queue = EventQueue(directory: directory, maxRecords: 500, sender: sender)
        await sender.attach(queue)

        await queue.flush()

        await queue.quiesce()   // `send` returns at persist now; wait for delivery
        let sent = await sender.sentNames
        XCTAssertEqual(sent.sorted(), ["e0", "e1", "e2", "e3"], "every record exactly once: \(sent)")
        let queued = await queue.queuedCount()
        XCTAssertEqual(queued, 0)
    }

    /// ═══ WHAT HAPPENS WHEN THE DISK ITSELF SAYS NO ═══
    ///
    /// This test was written expecting the fallback rewrite to rescue the record, and it does not:
    /// with the file read-only, the atomic replace fails too, so the record never reaches disk.
    /// That is not a bug to fix, it is the floor of what a durable queue can promise — a device
    /// with no writable storage (full disk, read-only volume) has nowhere to put the event, and an
    /// in-memory overflow would just be a second queue with strictly worse durability.
    ///
    /// What the queue must still do, and what this pins: send it anyway (persistence failing is no
    /// reason to drop the delivery attempt), never crash, and never CLAIM the record is queued —
    /// a phantom count would make `Report.queuedEvents` lie about data that is already gone.
    func test_whenTheFileCannotBeWrittenTheRecordIsSentAnywayAndNotClaimedAsQueued() async {
        let failing = FakeEventSender(shouldFail: true)
        let queue = EventQueue(directory: directory, maxRecords: 500, sender: failing)
        await queue.send(SdkEvent(eventName: "first"))

        let fileURL = directory.appendingPathComponent("monetization_brain_event_queue.jsonl")
        try? FileManager.default.setAttributes([.posixPermissions: 0o444], ofItemAtPath: fileURL.path)
        defer {
            try? FileManager.default.setAttributes([.posixPermissions: 0o644], ofItemAtPath: fileURL.path)
        }

        let deliverable = FakeEventSender()
        let secondQueue = EventQueue(directory: directory, maxRecords: 500, sender: deliverable)
        await secondQueue.send(SdkEvent(eventName: "second"))
        await secondQueue.quiesce()

        let sent = await deliverable.sentEvents
        XCTAssertEqual(sent.map(\.eventName), ["second"], "delivery must not depend on persistence")
        let names = await secondQueue.queuedEventNamesForTest()
        XCTAssertEqual(names, ["first"], "the unpersisted record must not be counted as queued")
    }

    /// ═══ THE SELF-CORRECTION THE CACHED COUNT GAVE UP, AND HOW IT IS BOUGHT BACK ═══
    ///
    /// Before the append rewrite every operation re-read the file, so two queues over one
    /// directory raced but never silently diverged. A cached count would under-count the moment
    /// another writer appends — and an under-count means the trim never fires and the file grows
    /// past `maxRecords`, with no error and nothing to notice. `appendLine` gets the real file
    /// length for free from its seek, so a foreign write is detected and the count is re-read.
    ///
    /// Verified to bite: without that check the file is left at 5 records with `maxRecords` of 4.
    func test_aForeignWriteIsDetectedSoTheTrimStillFires() async {
        let failing = FakeEventSender(shouldFail: true)
        let mine = EventQueue(directory: directory, maxRecords: 4, sender: failing)
        for index in 0..<3 {
            await mine.send(SdkEvent(eventName: "mine\(index)"))
        }

        // A second LIVE instance over the same directory — not a sequential hand-off.
        let foreign = EventQueue(directory: directory, maxRecords: 4, sender: FakeEventSender(shouldFail: true))
        await foreign.send(SdkEvent(eventName: "foreign"))

        await mine.send(SdkEvent(eventName: "mine3"))

        let queued = await mine.queuedCount()
        XCTAssertLessThanOrEqual(queued, 4, "the trim must still fire after a foreign append")
    }

}

/// Re-enters `flush()` from inside a send, which is the interleaving actor reentrancy allows and
/// the one a host can cause by calling `flush()` twice.
private actor ReflushingSender: EventSender {
    private var queue: EventQueue?
    private var reflushed = false
    private(set) var sentNames: [String] = []

    func attach(_ queue: EventQueue) {
        self.queue = queue
    }

    func send(_ event: SdkEvent) async throws {
        sentNames.append(event.eventName)
        if !reflushed {
            reflushed = true
            await queue?.flush()
        }
    }
}

/// Enqueues one extra event the first time it is asked to send, so a flush is interrupted by the
/// app doing what apps do. The injected event then fails, so it stays queued and is visible.
private actor ReentrantSender: EventSender {
    private var queue: EventQueue?
    private var injected = false

    func attach(_ queue: EventQueue) {
        self.queue = queue
    }

    func send(_ event: SdkEvent) async throws {
        if event.eventName == "injected" {
            throw EventSenderError.transport("stays queued so the test can see it")
        }
        if !injected {
            injected = true
            await queue?.send(SdkEvent(eventName: "injected"))
        }
    }
}

private actor FakeEventSender: EventSender {
    private(set) var sentEvents: [SdkEvent] = []
    private var shouldFail: Bool

    init(shouldFail: Bool = false) {
        self.shouldFail = shouldFail
    }

    func setShouldFail(_ value: Bool) {
        shouldFail = value
    }

    func send(_ event: SdkEvent) async throws {
        if shouldFail {
            throw EventSenderError.transport("forced failure")
        }
        sentEvents.append(event)
    }
}
