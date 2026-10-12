// Subscription lifecycle value types. Flat top-level structs/enums rather than types nested in
// the manager, matching this codebase's existing convention (see `PurchaseRoutingModels.swift`,
// `PaywallModels.swift`) even though Android nests them inside its `object`.
//
// ═══════════════════════════════════════════════════════════════════════════════════════════
// WHY THESE ARE OWNED TYPES AND NOT STOREKIT'S
// ═══════════════════════════════════════════════════════════════════════════════════════════
// Same rule `IapProduct`/`IapPurchase` already established in Phase 4: a StoreKit type must never
// leak into this SDK's public API, so `MonetizationBrainCore` stays buildable and testable with no
// StoreKit dependency at all. The one real mapping lives in `MonetizationBrainStoreKit`.
//
// There is a second, sharper reason here. StoreKit's `Product.SubscriptionInfo.RenewalState` is a
// `struct` with static members, NOT an enum — Apple can add a value without an API break, and a
// `switch` over it cannot be exhaustive. Mirroring it as a Swift `enum` with an explicit `.unknown`
// is what closes that open-ended set at a single boundary, so the rest of the SDK can reason about
// a fixed set of cases. `.unknown` is load-bearing, not defensive padding. See design spec §3.1.

import Foundation

/// Owned mirror of StoreKit's `Product.SubscriptionInfo.RenewalState` (iOS 15.0).
///
/// `.unknown` covers any `rawValue` this SDK hasn't learned yet. It is handled fail-closed
/// everywhere: it never grants entitlement and never produces a state transition — see
/// `SubscriptionLifecycle.observe(_:complete:)` and design spec §3.7.
public enum SubscriptionRenewalState: String, Sendable, Equatable, CaseIterable {
    /// Entitled. Covers BOTH "renewing normally" and "cancelled but not yet expired" — the two are
    /// told apart by `SubscriptionSnapshot.willAutoRenew`, not by this value. Mistaking the second
    /// for churn is the most common error when porting from Play; see design spec §3.3.
    case subscribed
    /// Not entitled. `SubscriptionSnapshot.expirationReason` says why, when the store stated one.
    case expired
    /// Not entitled, but the App Store is still retrying the charge — recoverable, keep dunning.
    case inBillingRetryPeriod
    /// Entitled despite a failed charge. Requires the studio to have opted into billing grace
    /// periods in App Store Connect; if they haven't, this state simply never occurs.
    case inGracePeriod
    /// Not entitled. A refund, or removal from Family Sharing — indistinguishable from each other
    /// on iOS 15 (`RevocationType` is iOS 26.4). See design spec §7.
    case revoked
    /// A value this SDK does not recognize. Never entitled, never a transition.
    case unknown
}

/// Why a subscription expired. Owned mirror of `RenewalInfo.ExpirationReason` (iOS 15.0), which is
/// likewise a `struct`, hence the `.unknown` case.
///
/// Play reports a single undifferentiated "expired"; this detail has no Android counterpart.
public enum SubscriptionExpirationReason: String, Sendable, Equatable, CaseIterable {
    /// The customer voluntarily cancelled.
    case autoRenewDisabled
    /// The charge failed and retries were exhausted.
    case billingError
    /// A price increase needed consent and didn't get it. Distinguishable in advance:
    /// `PriceIncreaseStatus.pending` is a pre-churn signal.
    case didNotConsentToPriceIncrease
    /// The product wasn't available for purchase at renewal time.
    case productUnavailable
    case unknown
}

/// One subscription's state as the store reports it right now — the input to
/// `SubscriptionLifecycle.observe(_:complete:)`.
///
/// Unlike Android's `SubSnapshot` (which carries only a purchase state and a boolean, because
/// that is all Play Billing answers on-device), this carries StoreKit's own stated renewal state,
/// so classification is a near-total function of platform truth rather than an inference.
/// See design spec §3.1.
public struct SubscriptionSnapshot: Sendable, Equatable {
    public let productId: String
    public let renewalState: SubscriptionRenewalState
    /// `RenewalInfo.willAutoRenew`. The ONLY thing separating an actively-renewing subscription
    /// from a cancelled-but-still-entitled one — both report `.subscribed`.
    public let willAutoRenew: Bool
    /// Epoch milliseconds, matching `IapPurchase.purchaseTimeMs`'s existing convention rather than
    /// introducing a `Date` into a model type. `0` when the store didn't state one.
    public let expirationMs: Int64
    /// Epoch milliseconds. `0` when absent — including whenever the studio hasn't enabled billing
    /// grace periods in App Store Connect.
    public let gracePeriodExpirationMs: Int64
    /// Non-`nil` only when the store stated a reason, which in practice means an expired
    /// subscription.
    public let expirationReason: SubscriptionExpirationReason?

    public init(
        productId: String,
        renewalState: SubscriptionRenewalState,
        willAutoRenew: Bool,
        expirationMs: Int64 = 0,
        gracePeriodExpirationMs: Int64 = 0,
        expirationReason: SubscriptionExpirationReason? = nil
    ) {
        self.productId = productId
        self.renewalState = renewalState
        self.willAutoRenew = willAutoRenew
        self.expirationMs = expirationMs
        self.gracePeriodExpirationMs = gracePeriodExpirationMs
        self.expirationReason = expirationReason
    }
}

/// The classified operational state of one subscription — what the host app acts on.
///
/// Seven states, where Android has five. Two differences carry real behavioral weight, both in
/// design spec §3.3:
///
/// 1. `.inBillingRetry` exists. Apple's own contract is that exiting the grace period CONTINUES in
///    billing retry — entitlement is lost but the charge is still being attempted, and it resolves
///    to either `.active` (billing recovery) or `.expired`. Android cannot see this state at all
///    and infers churn instead. A state machine that treats grace-expiry as terminal stops dunning
///    exactly when dunning can still work.
/// 2. `.revoked` is separate from `.expired`. A refund and a lapse call for opposite host-app
///    responses; Android has no way to tell them apart.
///
/// There is deliberately NO `.paused` case: Play's paused state has no StoreKit analog whatsoever,
/// so an iOS mapper can never produce one.
public enum SubscriptionState: String, Sendable, Equatable, CaseIterable {
    /// Never seen, or the feature is disabled by remote config.
    case none
    /// Subscribed and renewing.
    case active
    /// Subscribed, auto-renew off — **still entitled** until the period ends.
    case cancelled
    /// Charge failed; still entitled while the App Store retries.
    case inGracePeriod
    /// Charge failed; entitlement lost, retries continuing. Still recoverable.
    case inBillingRetry
    /// Lapsed.
    case expired
    /// Refunded, or revoked from Family Sharing.
    case revoked

    /// Whether the customer should receive the service right now.
    ///
    /// `.cancelled` is entitled. This is the single most common mistake when porting a subscription
    /// model off Google Play, where "cancelled" reads as churn — on both stores the customer keeps
    /// access until the paid period actually ends.
    public var isEntitled: Bool {
        self == .active || self == .cancelled || self == .inGracePeriod
    }

    /// Whether a payment problem is actively being retried — i.e. the dunning window.
    ///
    /// Spans grace AND billing retry, because entitlement loss at grace-expiry does not mean the
    /// charge has stopped being attempted. See design spec §3.5.
    public var isRecoverable: Bool {
        self == .inGracePeriod || self == .inBillingRetry
    }
}

/// A state change that just occurred. One `subscription_state` event is emitted per transition.
public struct SubscriptionTransition: Sendable, Equatable {
    public let productId: String
    public let from: SubscriptionState
    public let to: SubscriptionState

    public init(productId: String, from: SubscriptionState, to: SubscriptionState) {
        self.productId = productId
        self.from = from
        self.to = to
    }
}

/// Outcome of applying a remote-config payload, matching `RoutingApplyResult`/`PaywallApplyResult`.
///
/// `apply(_:)` never throws: a malformed config means the last-good config keeps running, and
/// `report` says why in a form an operator can read.
public struct SubscriptionApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String

    public init(applied: Bool, report: String) {
        self.applied = applied
        self.report = report
    }
}
