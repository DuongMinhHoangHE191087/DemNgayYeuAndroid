// Recurring-grant value types. Flat top-level structs rather than types nested in the manager,
// matching this codebase's existing convention (`PurchaseRoutingModels.swift`,
// `SubscriptionModels.swift`) even though Android nests them inside its `object`.

import Foundation

/// One configured grant line: "while this player owns `productId`, every `periodHours` they are
/// owed `rewardAmount` of `rewardType`."
///
/// The identity of a line is the WHOLE triple `(productId, periodHours, rewardType)` — the same
/// product can carry several lines on different cadences, and each keeps its own claim schedule.
///
/// `rewardType`/`rewardAmount` are **opaque to this SDK**. It decides eligibility and timing and
/// hands the pair back; the host app's economy code is what grants anything (the same boundary
/// `PaywallManager` and `IapHelper.purchase` already draw).
public struct RecurringGrant: Sendable, Equatable {
    public let productId: String
    /// Rolling period in hours, clamped to `[1, 8760]` at parse time. Rolling from the last claim
    /// — there is deliberately no calendar or timezone semantics, so there is no "resets at
    /// midnight."
    public let periodHours: Int
    public let rewardType: String
    public let rewardAmount: Int64

    public init(productId: String, periodHours: Int, rewardType: String, rewardAmount: Int64) {
        self.productId = productId
        self.periodHours = periodHours
        self.rewardType = rewardType
        self.rewardAmount = rewardAmount
    }
}

/// Outcome of `RecurringGrants.claim(productId:rewardType:)`.
///
/// `claimed` is the ONLY field control flow may branch on. On a rejected claim `rewardAmount` is
/// `0` and `rewardType` echoes what was asked for, so a caller that ignores `claimed` and grants
/// `rewardAmount` anyway still grants nothing — fail-closed by construction.
public struct RecurringGrantClaimResult: Sendable, Equatable {
    public let claimed: Bool
    public let rewardType: String
    public let rewardAmount: Int64
    /// Human-readable reason, for logs and debug UI — never for control flow.
    public let report: String

    public init(claimed: Bool, rewardType: String, rewardAmount: Int64, report: String) {
        self.claimed = claimed
        self.rewardType = rewardType
        self.rewardAmount = rewardAmount
        self.report = report
    }
}

/// Outcome of applying a remote-config payload, matching `RoutingApplyResult`/
/// `PaywallApplyResult`/`SubscriptionApplyResult`.
///
/// `apply(_:)` never throws: a malformed config means the last-good config keeps running, and
/// `report` says why in a form an operator can read.
public struct RecurringGrantsApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String

    public init(applied: Bool, report: String) {
        self.applied = applied
        self.report = report
    }
}
