import Foundation

/// The package's time seam (design spec §3.3).
///
/// ═══ WHY A SEAM AT ALL ═══
///
/// Before this, every site in the package called `Date()` directly, and the only precedent for
/// making time testable was `ClickFraudGuard`'s per-method `nowMs: Int64? = nil` parameter. A
/// subsystem whose entire behavior is "has the period elapsed" cannot be tested deterministically
/// without a seam, and spreading `nowMs:` across every entry point would spread the problem rather
/// than solve it — worse, a PUBLIC timestamp parameter is itself an injection point for exactly
/// the clock manipulation `RecurringGrants`' doc comment describes. So the seam is an injected
/// dependency with a real default, modelled on `BrainKeyValueStore`, and it is never a parameter
/// on a public API.
///
/// ═══ MILLISECONDS, NEVER A `Date` ═══
///
/// Epoch milliseconds match this package's existing time convention end to end
/// (`IapPurchase.purchaseTimeMs`, `SubscriptionSnapshot.expirationMs`, `SdkEvent.ts`), and keep
/// the seam trivially fakeable — a `Date`-shaped seam would invite `Date` into model types that
/// deliberately don't carry one.
///
/// ═══ SCOPE ═══
///
/// Consumers: `RecurringGrants`, `BattlePass`, `CrmBridge`, `OfferwallManager` and `AdsBrain`'s
/// cooldown arithmetic.
///
/// ═══ THE `Date()` CALL SITES DELIBERATELY LEFT ALONE ═══
///
/// Retrofitting every remaining `Date()` was considered and scoped down, because uniformity is not
/// the point — testability is, and the rest either already have a seam or gain nothing:
///
/// - `ClickFraudGuard.defaultNowMs` — already overridable through its `nowMs: Int64?` parameter,
///   which its tests use. A second seam would be churn.
/// - `DecisionEngine`'s `runId` — a one-shot uniqueness suffix at init. Nothing asserts on it and
///   nothing branches on it, so a clock would buy nothing.
/// - `SdkEvent`'s `ts` default parameter — already overridable per event, and threading a clock
///   into every construction site across the package is a large change for a value no decision
///   reads.
/// - `StoreKit2Backend` — those instants come from the real App Store; a fake clock there would
///   describe a transaction that did not happen when it says it did.
///
/// Stated so the remaining call sites read as decisions rather than as work left half-done.
///
/// Named `BrainClock`, not `Clock`, matching `BrainKeyValueStore` — this package's other injected
/// seam. The Swift standard library declares its own `Clock` protocol (SE-0329, an entirely
/// different shape built around `Instant`/`Duration`), so the bare name is ambiguous in any file
/// importing both, and forces every consumer to alias around it.
///
/// This package already carries one such shadowing trap — `SubscriptionRenewalState` shadows a
/// StoreKit typealias, documented in the Phase 7 spec §7 because by then it could not be renamed
/// cheaply. Taking a second one, at the one moment when nothing depends on the name yet, would be
/// choosing the trap deliberately.
public protocol BrainClock: Sendable {
    /// Milliseconds since the Unix epoch.
    func nowMs() -> Int64
}

/// The real clock: device wall-clock time. The default everywhere, so no production call site
/// has to know the seam exists.
///
/// ⚠️ Wall-clock time is user-settable and not monotonic. Anything that must not be farmable by
/// moving a device's clock needs server-authoritative time, not this — see `RecurringGrants`'
/// stated limits.
public struct SystemClock: BrainClock {
    public init() {}

    /// Deliberately NOT clamped before the `Int64` conversion, unlike every conversion in
    /// `RecurringGrants`' config parser — and the difference is the input, not the arithmetic.
    /// Those clamps exist because remote config is operator-controlled and `Int(someDouble)` traps
    /// above `Int.max` (a Critical finding twice in this port's history). This value comes from
    /// `Date()`, which no caller supplies and which cannot reach that magnitude on a real device.
    ///
    /// Said out loud so the asymmetry reads as a decision rather than an oversight — and so nobody
    /// copies this line into a place where the input IS externally controlled.
    public func nowMs() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
}
