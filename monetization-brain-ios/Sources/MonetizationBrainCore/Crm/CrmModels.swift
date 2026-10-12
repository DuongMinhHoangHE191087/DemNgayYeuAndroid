import Foundation

// CRM value types. Flat top-level declarations rather than types nested in `CrmBridge`, matching
// this codebase's existing convention (`PurchaseRoutingModels.swift`, `SubscriptionModels.swift`,
// `RecurringGrantModels.swift`) even though Android nests them inside its `object`.

/// A host-provided destination for engagement signals — a Braze/Airship/CleverTap adapter, a
/// logger, an in-house endpoint, anything.
///
/// ═══ THE SDK CARRIES NO CRM DEPENDENCY ═══
///
/// This protocol is the entire integration surface: the host registers a conformer and the SDK
/// hands it normalized signals. Nothing in this package knows a vendor's name, and no vendor SDK
/// is linked.
///
/// ═══ `deliver` IS HOST CODE, CALLED WITH NO LOCK HELD (§2.2) ═══
///
/// `CrmBridge` snapshots the eligible sinks under its lock and releases it BEFORE calling this,
/// so an implementation may safely call back into the bridge (`activeVersion`, `register`,
/// `unregister`, even `emit`) from inside `deliver`. It should still return promptly: delivery is
/// sequential, so a slow sink delays the sinks after it in the same emit (it does not block other
/// callers of the bridge).
///
/// `deliver` is deliberately NOT `throws`. Android wraps each call in try/catch so one broken
/// sink cannot stop the others; in Swift the equivalent isolation is structural — a non-throwing
/// signature means a sink has no way to interrupt the loop, and `false` is how it reports that it
/// could not take the signal. (A sink that TRAPS — force-unwrapping `nil` — is not recoverable in
/// either language.)
public protocol CrmSink: Sendable {
    /// Stable identity, and the value a config `sinks` allow-list matches against (§3.2).
    /// Registering a second sink with an id already present REPLACES the first.
    var sinkId: String { get }

    /// - Returns: `true` if the sink took the signal. `false` is a report, not an error: the
    ///   bridge carries on to the remaining sinks either way.
    func deliver(_ signal: EngagementSignal) -> Bool
}

/// What kind of engagement moment a signal describes.
///
/// Deliberately a CLOSED, small vocabulary rather than a free-form string: every case here is a
/// moment this SDK can actually detect and a CRM audience can actually segment on. A host with a
/// moment of its own emits it through its own CRM SDK — inventing speculative cases would make
/// this look like a general-purpose analytics channel, which it is not (`SdkEvent`/`EventQueue`
/// is that channel, and it goes somewhere else entirely).
public enum CrmSignalKind: String, Sendable, Equatable, CaseIterable {
    /// The player's most likely latent type changed — `BeliefState`'s argmax actually flipped,
    /// not merely "belief was updated". See `CrmBridge.emitSegmentChange`.
    case segmentChange = "segment_change"
    /// The player's FIRST ever purchase. See `CrmBridge.emitFirstPurchase`.
    case firstPurchase = "first_purchase"
}

/// One normalized outbound signal.
///
/// Carries the pseudonymous `playerId` and nothing else identifying: `properties` is filled by
/// this package's own emitters with segment names and a purchase value, never with anything a
/// host did not already have.
public struct EngagementSignal: Sendable, Equatable {
    public let playerId: String
    public let kind: CrmSignalKind
    /// Signal-specific attributes, already stringified so a sink can forward them without
    /// re-encoding. Keys are stable — a CRM audience is built on them.
    public let properties: [String: String]

    /// Epoch milliseconds, the package's time convention end to end (`SdkEvent.ts`,
    /// `IapPurchase.purchaseTimeMs`).
    ///
    /// `0` (the default) means "not stamped yet", and `CrmBridge.emit` stamps it from the
    /// configured clock at emit time. A caller that supplies its own value keeps it — a host
    /// replaying a signal queued an hour ago must be able to describe when the moment HAPPENED,
    /// not when it was flushed.
    public var timestampMs: Int64

    public init(
        playerId: String,
        kind: CrmSignalKind,
        properties: [String: String] = [:],
        timestampMs: Int64 = 0
    ) {
        self.playerId = playerId
        self.kind = kind
        self.properties = properties
        self.timestampMs = timestampMs
    }
}

/// Outcome of applying a `crm_bridge_ios` payload, matching `RoutingApplyResult`/
/// `PaywallApplyResult`/`SubscriptionApplyResult`/`RecurringGrantsApplyResult`.
///
/// `apply(_:)` never throws: a malformed config means the last-good config keeps running, and
/// `report` says why in a form an operator can read.
public struct CrmApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String

    public init(applied: Bool, report: String) {
        self.applied = applied
        self.report = report
    }
}
