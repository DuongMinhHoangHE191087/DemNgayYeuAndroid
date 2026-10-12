import Foundation

// Offerwall value types. Flat top-level declarations rather than types nested in
// `OfferwallManager`, matching this codebase's existing convention (`CrmModels.swift`,
// `RecurringGrantModels.swift`, `SubscriptionModels.swift`) even though Android nests them.

/// A host-provided offerwall adapter — one per vendor SDK (Revu, Adjoe, Tapjoy, an in-house
/// wall, anything).
///
/// ═══ THE SDK CARRIES NO OFFERWALL DEPENDENCY ═══
///
/// This protocol is the entire integration surface: the host wraps its vendor SDK in a conformer,
/// registers it, and `OfferwallManager` decides whether one may open. Nothing in this package
/// knows a vendor's name, no vendor SDK is linked, and nothing here talks to a network.
///
/// ═══ ⚠️ BOTH METHODS ARE HOST CODE, CALLED WITH NO LOCK HELD ═══
///
/// `OfferwallManager` snapshots the eligible providers under its lock and RELEASES it before
/// calling either method, exactly as `CrmBridge` does for `CrmSink.deliver` (Phase 10 §2.2).
/// `NSLock` is not reentrant, so an implementation that calls back into the manager — to read
/// `activeVersion()`, to register a second wall, to report a completion from inside
/// `onWallClosed` — would deadlock if it were called under the lock. It is safe here.
///
/// Both should still return promptly: `isAvailable()` is polled for every provider on every
/// `availableProviders()` call, and a blocking implementation delays the providers after it.
public protocol OfferwallProvider: Sendable {
    /// Stable identity. It is the value a config `providers` allow-list matches against, the
    /// argument `show(providerId:)`/`reportCompletion(providerId:)` take, and one half of the
    /// payout dedup key — see `OfferwallManager`'s §2.3 banner. Registering a second provider
    /// with an id already present REPLACES the first.
    var providerId: String { get }

    /// Does this vendor have a wall worth opening right now (offers cached, SDK initialised)?
    ///
    /// The LAST gate in the order, so a `true` here is not permission to show — the manager's
    /// own gates run first and this is never consulted when one of them has already refused.
    func isAvailable() -> Bool

    /// Presents the wall. `onWallClosed` is the host's own callback, passed straight through:
    /// the SDK does not wrap, retain or re-dispatch it.
    ///
    /// Completions are NOT reported through this callback — a wall closing says nothing about
    /// whether an offer was finished, and vendors deliver that separately (an SDK callback or an
    /// S2S postback, often minutes later). The host calls `reportCompletion(...)` when it learns.
    func show(onWallClosed: @escaping @Sendable () -> Void)
}

/// Outcome of applying an `offerwall_config_ios` payload, matching `CrmApplyResult`/
/// `RecurringGrantsApplyResult`/`PaywallApplyResult`.
///
/// `apply(_:)` never throws: a malformed config means the last-good config keeps running, and
/// `report` says why in a form an operator can read.
public struct OfferwallApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String

    public init(applied: Bool, report: String) {
        self.applied = applied
        self.report = report
    }
}

/// Outcome of a `show(providerId:onWallClosed:)` attempt.
///
/// `shown == false` means a gate refused and the provider was never asked to present anything;
/// `report` names the gate. Diagnostic only — never control flow beyond "did the wall open".
public struct OfferwallShowResult: Sendable, Equatable {
    public let shown: Bool
    public let providerId: String
    public let report: String

    public init(shown: Bool, providerId: String, report: String) {
        self.shown = shown
        self.providerId = providerId
        self.report = report
    }
}

/// Outcome of a `reportCompletion(...)` call — what the host app should actually grant.
///
/// `accepted == false` means this completion was recognised as one already paid, and the host
/// must grant NOTHING: `rewardAmount` is `0` on that path so a caller that ignores the flag still
/// cannot double-grant.
public struct OfferwallCompletionResult: Sendable, Equatable {
    public let accepted: Bool
    public let rewardType: String
    public let rewardAmount: Int
    public let report: String

    public init(accepted: Bool, rewardType: String, rewardAmount: Int, report: String) {
        self.accepted = accepted
        self.rewardType = rewardType
        self.rewardAmount = rewardAmount
        self.report = report
    }
}
