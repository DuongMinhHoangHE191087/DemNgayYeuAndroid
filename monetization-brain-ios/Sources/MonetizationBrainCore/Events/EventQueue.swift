import Foundation

/// Durable event queue: every event is written to disk before a send is attempted, and only
/// removed once delivery is confirmed. Survives a killed process and a lost network.
///
/// Design carried over from Android's `DecisionLogQueue`/`DecisionLogger` on purpose:
/// - Persist-then-send: a process killed right after `send(_:)` returns still has the event
///   queued on disk.
/// - Remove-by-content-match, not by index: new events can be enqueued between a send attempt
///   and its removal, so removal must match the exact record rather than trust a stale index.
/// - Write-to-temp-then-atomic-replace **for every whole-file rewrite** (trimming, batched
///   removal): a process killed mid-rewrite leaves the last good file intact — a duplicate send is
///   recoverable (the backend contract tolerates duplicates), a lost queue is not. The hot path,
///   `enqueueLine`, does NOT go through that: it appends to the live file, which is what makes it
///   O(1). See the trade-off section below for what a kill mid-append leaves behind.
/// - Bounded by `maxRecords`, trimming the OLDEST records first: new data reflects the current
///   policy, and an unbounded queue on a long-offline device would grow forever.
///
/// ═══ THE READ-MODIFY-WRITE THIS USED TO DO, AND WHAT IT COST ═══
///
/// Every `send` and every removal used to rewrite the WHOLE file. The class doc said to revisit
/// it "only if event volume ever makes this a measured bottleneck", so it was measured, on the
/// case that matters — a device that has been offline long enough to need a durable queue at all:
///
///     200 queued events: enqueue 440 ms total, flush 434 ms
///     500 queued events: enqueue 1.63 s total, flush 1.75 s
///
/// Quadratic in both directions, and the enqueue half is the ordinary path every event takes, not
/// a recovery path. The queue was slowest at exactly the moment it was most needed.
///
/// Now, matching Android's `DecisionLogQueue`:
/// - `enqueue` APPENDS (O(1)) and rewrites only to trim, which needs the record count — hence the
///   cached count, the one piece of state that has to stay in step with every write path.
/// - `flush` batches removals and rewrites once per `removalCheckpoint` records instead of once
///   per record.
///
/// ═══ WHAT THAT TRADES AWAY, STATED PLAINLY ═══
///
/// Delivery here is at-least-once by design (the backend contract tolerates duplicates; there is
/// no client-side dedup key). Removing one record at a time meant a process killed mid-flush
/// re-sent at most ONE already-delivered event. Batching widens that window to at most
/// `removalCheckpoint`. That is the whole cost, it is bounded, and it buys back the numbers above.
///
/// An append can also be torn by a kill mid-write, leaving a partial trailing line. That is not a
/// new failure mode — it is the one `decodeLine` already handles: `flush` drops a record it cannot
/// decode rather than retrying it forever. A write that throws *after* putting some bytes down is
/// the same shape with one extra consequence: the fallback rewrite below re-adds the record, so
/// the torn fragment is joined by a live duplicate of the line it was trying to be. Both are
/// covered by the two invariants this queue already lives by — undecodable records are dropped,
/// duplicates are tolerated.
///
/// ═══ THE FLOOR: A DISK THAT WILL NOT TAKE THE WRITE AT ALL ═══
///
/// If neither the append nor the fallback rewrite can write (a full disk, a read-only volume),
/// the record does not reach disk and "persist, then send" degrades to send-and-hope: the event is
/// still sent, and if that send fails it is gone. There is nowhere else to put it — an in-memory
/// overflow would be a second queue with strictly worse durability than the one that just failed.
/// What the queue does guarantee in that state is that it does not crash and does not COUNT the
/// record as queued, because a `Report.queuedEvents` that reports data already lost is worse than
/// one that reports the loss.
public actor EventQueue: EventSink {
    private let directory: URL
    private let fileName: String
    private let maxRecords: Int
    private let sender: EventSender
    private let sequenceCounter: EventSequenceCounter
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    /// Records removed between whole-file rewrites during `flush`. See the trade-off in the type
    /// banner: this is the upper bound on how many already-delivered events a process killed
    /// mid-flush can re-send.
    static let removalCheckpoint = 25

    /// Size the file is expected to have, from this instance's own writes. `appendLine` learns the
    /// REAL size for free (the seek to the end returns it), so a mismatch is a cheap, exact signal
    /// that something else wrote to this file.
    ///
    /// ═══ WHY THIS EXISTS AT ALL ═══
    ///
    /// Before the append rewrite, every operation re-read the file, so two `EventQueue` instances
    /// over one directory raced but could never silently diverge — each was self-correcting. A
    /// cached count gives that up: an instance that trusts its own arithmetic forever will
    /// UNDER-count once another writer appends, and an under-count means the trim never fires and
    /// the queue grows past `maxRecords` — the unbounded-file failure `maxRecords` exists to
    /// prevent, with no error and nothing to notice it. Rather than write "one instance owns this
    /// directory" into the docs and hope, the mismatch drops the cache and the next count is read
    /// from the file.
    private var expectedFileBytes: UInt64?

    /// Whether a `flush()` is already draining. See `flush` for why a second one returns instead
    /// of joining in.
    private var isDraining = false

    /// Records on disk, cached so an append does not have to read the file back just to learn
    /// whether trimming is due. `nil` means "not known, go and count". EVERY write path has to
    /// keep this in step — that is the price of not reading the file on the hot path.
    private var cachedCount: Int?

    /// Test-only: how many times the whole file has been rewritten. The point of this phase is
    /// that this number stops growing with the number of events, so it is the thing to assert on.
    private(set) var rewriteCountForTest = 0

    /// - Parameter sequenceCounter: source of `SdkEvent.sequenceId`. Defaults to a counter over
    ///   the host app's `UserDefaults`, matching the precedent `Entitlements` set; inject an
    ///   in-memory store in tests so runs don't share one real counter.
    /// When `send(_:)` is allowed to return.
    ///
    /// ═══ WHY THIS EXISTS, MEASURED ═══
    ///
    /// `send(_:)` used to await the network attempt, and every decision point awaits `send`:
    /// `AdsBrain.shouldShowInterstitial()` -> `DecisionEngine.decide` -> `logDecision` -> here.
    /// So a decision inherited the sink's latency 1:1 — measured at **2.09 s against a 2.0 s
    /// sink**, and `URLSessionEventSender`'s default timeout is 30 s. That is a player waiting at
    /// the end of a level for a log line to be acknowledged.
    ///
    /// Awaiting it bought the caller NOTHING, which is what makes this safe rather than a
    /// trade-off: `attemptSend` catches every error and returns `Void`, so the caller cannot
    /// learn whether delivery succeeded no matter how long it waits. Durability does not come
    /// from the wait either — it comes from `enqueueLine`, which has already run. A failed or
    /// abandoned attempt leaves the record on disk for the next `flush()`, exactly as before.
    public enum DeliveryMode: Sendable {
        /// `send(_:)` returns once the event is ON DISK; the network attempt continues in the
        /// background. The default, and the only one a host on a decision path should use.
        case afterPersist
        /// `send(_:)` also awaits the network attempt. For tests that want to assert on the
        /// sender immediately after `send` without calling `quiesce()`.
        case afterNetworkAttempt
    }

    private let deliveryMode: DeliveryMode

    /// The tail of the background delivery chain, or `nil` when nothing is in flight.
    ///
    /// A chain rather than one task per event, deliberately. Spawning per event would let a burst
    /// — a session start emitting several events at once — open that many concurrent POSTs at a
    /// backend that has no batch endpoint. Each task awaits its predecessor, so delivery stays
    /// SEQUENTIAL and at most one request is open at a time, which is the same property `flush()`
    /// already commits to ("sequential by design"). It also makes `quiesce()` a single `await`:
    /// the newest task transitively covers every older one.
    private var deliveryChain: Task<Void, Never>?
    /// Bumped on every enqueue. `Task` is a struct with no identity, so this is how `quiesce()`
    /// tells "the chain I awaited is still the newest" from "another send landed meanwhile".
    private var deliveryGeneration = 0

    /// - Parameter deliveryMode: see `DeliveryMode`. Defaults to `.afterPersist`, so a decision
    ///   point never waits on the network.
    public init(
        directory: URL,
        fileName: String = "monetization_brain_event_queue.jsonl",
        maxRecords: Int = 5000,
        sender: EventSender,
        sequenceCounter: EventSequenceCounter = EventSequenceCounter(),
        deliveryMode: DeliveryMode = .afterPersist
    ) {
        self.directory = directory
        self.fileName = fileName
        self.maxRecords = maxRecords
        self.sender = sender
        self.sequenceCounter = sequenceCounter
        self.deliveryMode = deliveryMode
    }

    /// Awaits every background delivery started so far.
    ///
    /// For tests and for a host that wants to drain before backgrounding. Awaiting the newest
    /// task is enough because each one awaits its predecessor; the loop re-checks in case a send
    /// landed while we were suspended.
    public func quiesce() async {
        while let tail = deliveryChain {
            let generation = deliveryGeneration
            await tail.value
            if deliveryGeneration == generation {
                deliveryChain = nil
                return
            }
        }
    }

    private var fileURL: URL { directory.appendingPathComponent(fileName) }
    private var tmpURL: URL { directory.appendingPathComponent(fileName + ".tmp") }

    /// Stamps `event` with its `sequenceId`, enqueues it (persists to disk immediately), then
    /// attempts to send it right away. A send failure leaves it queued for the next `flush()`.
    ///
    /// Under the default `.afterPersist` this RETURNS once the event is on disk, without waiting
    /// for the network — see `DeliveryMode` for the measurement that motivated it. Call
    /// `quiesce()` to await the attempts themselves.
    ///
    /// ═══ WHY THE NUMBER IS ASSIGNED HERE ═══
    ///
    /// Not at `SdkEvent.init`: events are constructed at call sites that may then decide not to
    /// send them (the consent gate in `DecisionEngine`, an early `return`), and every such
    /// discarded event would burn a number and open a gap the backend would read as lost data.
    /// Construction is also synchronous and unsynchronised, and every existing test builds
    /// `SdkEvent`s directly — numbering there would make fixtures non-deterministic.
    ///
    /// Not at send time, per attempt, either — and this is the one place iOS deliberately
    /// diverges from Android. Android re-derives a fresh number on every replay
    /// (`DecisionLogger.flushQueue` at `brain/DecisionLogger.kt:57-85` calls back into
    /// `LogEventServer.logEventToServer`, which assigns again at `logevent/LogEventServer.kt:95`),
    /// because Android's durable queue stores decoded FIELDS and the original number simply
    /// isn't in the record. This queue stores the full wire envelope, so the number survives —
    /// and keeping it stable is strictly better for the only thing the backend uses the column
    /// for: a gap then means "an event never arrived" and a repeat means "the same event arrived
    /// twice", instead of a retry storm silently inflating the sequence space. It also preserves
    /// this queue's remove-by-content-match invariant, which a per-attempt renumber would break
    /// by making the sent payload differ from the persisted line.
    ///
    /// So: assigned once, at enqueue, inside the actor. Actor isolation serialises `send(_:)`,
    /// and `EventSequenceCounter` is independently lock-guarded, so concurrent emitters cannot
    /// interleave a read-modify-write and produce a duplicate.
    ///
    /// A caller-supplied `sequenceId` is overwritten: this queue is the wire boundary and the
    /// sole authority on the numbering.
    public func send(_ event: SdkEvent) async {
        var numbered = event
        numbered.sequenceId = sequenceCounter.next()
        guard let line = encodeLine(numbered) else { return }
        enqueueLine(line)
        switch deliveryMode {
        case .afterNetworkAttempt:
            await attemptSend(event: numbered, line: line)
        case .afterPersist:
            let previous = deliveryChain
            deliveryGeneration += 1
            deliveryChain = Task { [weak self] in
                await previous?.value
                await self?.attemptSend(event: numbered, line: line)
            }
        }
    }

    /// Retries every event still in the queue. Sequential by design — the goal is durability,
    /// not throughput; a batched ingestion endpoint doesn't exist yet.
    ///
    /// Retries never renumber: the persisted line already carries the `sequenceId` assigned when
    /// the event was first enqueued (see `send(_:)`), and re-sending the decoded envelope as-is
    /// is what makes a duplicate delivery an exact duplicate. Records written by an SDK version
    /// that predates numbering still carry `0` and are replayed with `0` — rewriting history to
    /// look numbered would be worse than an honestly unnumbered record.
    public func flush() async {
        // ═══ A SECOND CONCURRENT DRAIN IS A NO-OP, NOT A SECOND DRAIN ═══
        //
        // `flush` is public and host-driven, and it suspends on every send, so two calls
        // interleave freely. Nothing corrupts if they do — `removeLines` re-reads — but both of
        // this method's stated bounds are per-invocation: "at most `removalCheckpoint`
        // already-delivered events re-sent after a kill" and "one rewrite per
        // `removalCheckpoint`". Two overlapping drains quietly double both, and would also send
        // the same record twice in the window between one drain's send and the other's removal.
        // The caller's intent is "drain the queue", and a drain is already running.
        guard !isDraining else { return }
        isDraining = true
        defer { isDraining = false }

        var delivered: [String] = []
        for line in peekAllLines() {
            guard let event = decodeLine(line) else {
                // Corrupt record — cannot be retried, drop it rather than block the queue.
                delivered.append(line)
                continue
            }
            do {
                try await sender.send(event)
                delivered.append(line)
            } catch {
                // Left queued for the next flush(). A send failure must never propagate to the
                // caller — logging is never allowed to interrupt the app's own flow.
            }
            if delivered.count >= Self.removalCheckpoint {
                removeLines(delivered)
                delivered = []
            }
        }
        removeLines(delivered)
    }

    /// Number of events currently queued (persisted, not yet confirmed delivered).
    public func queuedCount() -> Int {
        peekAllLines().count
    }

    /// Test-only: names of events currently queued, in queue order.
    func queuedEventNamesForTest() -> [String] {
        peekAllLines().compactMap { decodeLine($0)?.eventName }
    }

    /// Test-only: `sequenceId`s of events currently queued, in queue order.
    func queuedSequenceIdsForTest() -> [Int] {
        peekAllLines().compactMap { decodeLine($0)?.sequenceId }
    }

    // MARK: - Send

    private func attemptSend(event: SdkEvent, line: String) async {
        do {
            try await sender.send(event)
            removeLine(line)
        } catch {
            // Left queued for the next flush(). A send failure must never propagate to the
            // caller — logging is never allowed to interrupt the app's own flow.
        }
    }

    // MARK: - Encoding

    private func encodeLine(_ event: SdkEvent) -> String? {
        guard let data = try? encoder.encode(event),
              let json = String(data: data, encoding: .utf8) else { return nil }
        // Compact JSONEncoder output never contains a raw newline, but guard anyway since this
        // is a one-record-per-line file format.
        return json.replacingOccurrences(of: "\n", with: "\\n")
    }

    private func decodeLine(_ line: String) -> SdkEvent? {
        guard let data = line.data(using: .utf8) else { return nil }
        return try? decoder.decode(SdkEvent.self, from: data)
    }

    // MARK: - Disk I/O

    private func peekAllLines() -> [String] {
        guard let data = try? Data(contentsOf: fileURL),
              let contents = String(data: data, encoding: .utf8) else { return [] }
        return contents.split(separator: "\n", omittingEmptySubsequences: true).map(String.init)
    }

    private func removeLine(_ line: String) {
        removeLines([line])
    }

    /// Removes the FIRST occurrence of each line, matched by CONTENT, not index — the file is
    /// re-read here rather than carried in from the caller precisely because a `send` can have
    /// appended to it while `flush` was suspended on the network.
    private func removeLines(_ lines: [String]) {
        guard !lines.isEmpty else { return }
        var remaining = peekAllLines()
        for line in lines {
            if let index = remaining.firstIndex(of: line) {
                remaining.remove(at: index)
            }
        }
        writeAll(remaining)
    }

    /// Appends in O(1), falling back to the rewrite path when there is no file to append to yet.
    ///
    /// The trim is the one branch that still rewrites, which is why the count is cached: asking
    /// the file how long it is on every append would put the O(n) read straight back.
    private func enqueueLine(_ line: String) {
        guard let fileWasOurs = appendLine(line) else {
            // No file, no directory, or an I/O error: the rewrite path creates all of it.
            var lines = peekAllLines()
            lines.append(line)
            writeAll(trimmed(lines))
            return
        }
        if !fileWasOurs {
            // Another writer got here first; anything this instance remembers about the file's
            // length is about a file that no longer exists.
            cachedCount = nil
        }
        // `currentCount()` after the append reads a file that already contains this line, so it is
        // only incremented when the cache survived.
        let count = cachedCount.map { $0 + 1 } ?? currentCount()
        cachedCount = count
        guard count > maxRecords else { return }
        let lines = peekAllLines()
        guard lines.count > maxRecords else { return }
        writeAll(trimmed(lines))
    }

    /// Trim the OLDEST first — new data reflects the current policy. One implementation, because
    /// "which end do we drop" is this type's contract and two copies of it is one copy too many.
    private func trimmed(_ lines: [String]) -> [String] {
        lines.count > maxRecords ? Array(lines.suffix(maxRecords)) : lines
    }

    private func currentCount() -> Int {
        if let cachedCount { return cachedCount }
        let count = peekAllLines().count
        cachedCount = count
        return count
    }

    /// Appends, and reports whether the file it appended to was the one this instance last wrote.
    ///
    /// `nil` means the append did not happen at all (no file yet, no directory, an I/O error) and
    /// the caller must fall back to a rewrite. `false` means it DID happen but the file had been
    /// changed by someone else first — see `expectedFileBytes`.
    private func appendLine(_ line: String) -> Bool? {
        guard let data = (line + "\n").data(using: .utf8),
              let handle = try? FileHandle(forWritingTo: fileURL) else { return nil }
        defer { try? handle.close() }
        do {
            // The seek returns the current length, so the check costs nothing extra.
            let lengthBefore = try handle.seekToEnd()
            let fileWasOurs = expectedFileBytes == lengthBefore
            try handle.write(contentsOf: data)
            expectedFileBytes = lengthBefore + UInt64(data.count)
            return fileWasOurs
        } catch {
            // Bytes may or may not have landed. Neither the length nor the count is known now.
            expectedFileBytes = nil
            cachedCount = nil
            return nil
        }
    }

    /// The only path that rewrites the whole file: trimming, and batched removal.
    ///
    /// It is one of THREE places `cachedCount` is written, and the audit surface is all three, not
    /// this one — an earlier version of this comment claimed otherwise, which is exactly the kind
    /// of thing that sends the next reader looking in one place for a drift that came from
    /// another. The three, and why each is safe: here (set to what was just written to disk, and
    /// cleared when the write fails), `currentCount()` (a lazy read of the file itself), and
    /// `enqueueLine` (increments after an append that `appendLine` confirmed landed on the file
    /// the count describes — see `expectedFileBytes`).
    private func writeAll(_ lines: [String]) {
        rewriteCountForTest += 1
        do {
            if !FileManager.default.fileExists(atPath: directory.path) {
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            }
            let content = lines.isEmpty ? "" : lines.joined(separator: "\n") + "\n"
            try Data(content.utf8).write(to: tmpURL, options: .atomic)
            if FileManager.default.fileExists(atPath: fileURL.path) {
                _ = try FileManager.default.replaceItemAt(fileURL, withItemAt: tmpURL)
            } else {
                try FileManager.default.moveItem(at: tmpURL, to: fileURL)
            }
            // Set only once the file on disk actually says this. Setting it before the write —
            // which is what this did first — means a failed write leaves the cache describing a
            // file that was never written, and the trim then fires against a length the queue
            // only imagined.
            cachedCount = lines.count
            expectedFileBytes = UInt64(Data(content.utf8).count)
        } catch {
            // Best-effort: on failure the previous file is left as-is rather than risking a
            // half-written queue. Removal failing here means a duplicate send later, never
            // lost data — the backend contract tolerates duplicates.
            try? FileManager.default.removeItem(at: tmpURL)
            // The file is unchanged and its length is now anyone's guess: drop the cache rather
            // than keep a number that was never true. The next append pays one read to recount.
            cachedCount = nil
            expectedFileBytes = nil
        }
    }
}
