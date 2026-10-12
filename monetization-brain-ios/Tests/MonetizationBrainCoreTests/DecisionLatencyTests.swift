import XCTest
@testable import MonetizationBrainCore

/// A decision point must never inherit the event sink's latency.
///
/// ═══ WHY THIS IS A TEST AND NOT A COMMENT ═══
///
/// It used to. `AdsBrain.shouldShowInterstitial()` awaits `DecisionEngine.decide`, which awaits
/// `logDecision`, which awaited `EventSink.send`, which awaited the network. Measured against a
/// 2.0 s sink, a single decision blocked for **2.09 s** — and `URLSessionEventSender`'s default
/// timeout is 30 s, so the worst case was a player waiting half a minute at the end of a level
/// for a log line to be acknowledged.
///
/// Nothing about that was visible from reading a call site: every layer just wrote `await`, which
/// is what `async` code is supposed to look like. One re-added `await` in `EventQueue.send` puts
/// it straight back, and no existing test would have noticed — every other test uses a fake sender
/// that returns immediately, so the bug is invisible unless the sink is deliberately slow. Hence
/// this file.
final class DecisionLatencyTests: XCTestCase {
    /// Deliberately slower than any real timeout, so a regression is unmistakable rather than
    /// marginal.
    private static let networkDelay: UInt64 = 2_000_000_000
    /// Generous next to 2 s — the point is the ORDER OF MAGNITUDE, not a benchmark. A tight bound
    /// here would make this flaky on a loaded CI runner and teach people to ignore it.
    private static let budget: TimeInterval = 0.2

    private final class SlowSender: EventSender, @unchecked Sendable {
        func send(_ event: SdkEvent) async throws {
            try? await Task.sleep(nanoseconds: DecisionLatencyTests.networkDelay)
        }
    }

    private func makeQueue() -> EventQueue {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        return EventQueue(directory: directory, sender: SlowSender())
    }

    /// The regression itself, through the real `EventQueue` rather than a stub sink — the await
    /// chain being tested runs through it, so stubbing it out would test nothing.
    func test_aDecisionPointDoesNotWaitForTheNetwork() async {
        let queue = makeQueue()
        await AdsBrain.shared.configure(
            AdsBrain.Configuration(
                remoteConfig: StaticRemoteConfigProvider(),
                eventSink: queue,
                playerId: "latency_\(UUID().uuidString)"
            )
        )
        AdsBrain.shared.enabled = true
        AdsBrain.shared.rolloutFraction = 1.0
        await AdsBrain.shared.onSessionStart()

        let start = Date()
        _ = await AdsBrain.shared.shouldShowInterstitial()
        let elapsed = Date().timeIntervalSince(start)

        XCTAssertLessThan(
            elapsed, Self.budget,
            "a decision point took \(String(format: "%.2f", elapsed))s against a 2.0s network; "
            + "something on the decide -> logDecision -> send path is awaiting delivery again"
        )
    }

    /// The other half, and the reason the fix is safe rather than a trade-off: deferring the
    /// NETWORK does not defer the DISK. If this ever fails, events are being dropped on a kill
    /// instead of merely delivered late, and the latency fix has turned into data loss.
    func test_theEventIsAlreadyDurableWhenTheDecisionReturns() async {
        let queue = makeQueue()
        await AdsBrain.shared.configure(
            AdsBrain.Configuration(
                remoteConfig: StaticRemoteConfigProvider(),
                eventSink: queue,
                playerId: "durability_\(UUID().uuidString)"
            )
        )
        AdsBrain.shared.enabled = true
        AdsBrain.shared.rolloutFraction = 1.0
        await AdsBrain.shared.onSessionStart()
        _ = await AdsBrain.shared.shouldShowInterstitial()

        let queued = await queue.queuedCount()
        XCTAssertGreaterThan(
            queued, 0,
            "the decision returned with nothing on disk; a kill here would lose the event"
        )
    }
}
