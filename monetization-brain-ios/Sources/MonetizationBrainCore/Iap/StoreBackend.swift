/// The seam between `IapHelper`'s orchestration logic and a real store. `IapHelper` never
/// imports StoreKit directly — every store interaction goes through this protocol, so the
/// orchestration logic is unit-testable with an in-memory fake, the same architectural move this
/// codebase already made for `RemoteConfigProvider` (Phase 1) and `BrainKeyValueStore` (Phase 3).
/// The one real conformance (`StoreKit2Backend`) lives in a separate `MonetizationBrainStoreKit`
/// target — see design spec §2.
public protocol StoreBackend: Sendable {
    /// Fetches price/metadata for the given product identifiers. Products the store doesn't
    /// recognize are simply absent from the result — never a partial failure for the whole call.
    func products(for identifiers: [String]) async throws -> [IapProduct]

    /// Initiates a purchase. Throws only for a genuine call-level failure (e.g. the store
    /// couldn't even be reached) — a user-facing outcome like cancellation is a normal
    /// `IapResult` case, not a thrown error.
    func purchase(_ product: IapProduct) async throws -> IapResult

    /// Snapshot of everything currently entitled, as of right now — used by `restorePurchases()`.
    /// `async throws`, not a stream: `restorePurchases()` must be able to tell "genuinely finished
    /// enumerating everything the store reports as owned" apart from "interrupted partway through"
    /// (a cancelled `Task` — the routine SwiftUI `.task { }` cancellation on view dismissal, not an
    /// attack) — a thrown error (including `CancellationError`) is exactly that signal. Returning a
    /// partial array with no way to know it's partial is what let `restorePurchases()` silently
    /// revoke still-valid entitlements before this fix; see the git history for the full incident.
    func currentEntitlements() async throws -> [IapPurchase]

    /// Every auto-renewable subscription the store currently reports a renewal status for — the
    /// input to `SubscriptionLifecycle.observe(_:complete:)`. Includes subscriptions that are no
    /// longer entitled (`.expired`, `.revoked`, `.inBillingRetryPeriod`), which is exactly what
    /// makes lapse an observed fact rather than an inference (design spec §3.4).
    ///
    /// `async throws`, deliberately NOT a stream, for the same reason `currentEntitlements()`
    /// isn't: the caller must be able to tell "genuinely enumerated every subscription the store
    /// reports" apart from "interrupted partway through" (a cancelled `Task` — the routine SwiftUI
    /// `.task { }` cancellation on view dismissal, not an attack). A thrown error (including
    /// `CancellationError`) is precisely that signal, and a caller MUST skip the whole
    /// classification pass on a throw rather than classify what did arrive. Returning a partial
    /// array with no way to know it's partial is what let `restorePurchases()` silently revoke
    /// still-valid entitlements before Phase 4's fix; the identical failure here would churn a
    /// paying customer into `.expired` and start dunning them.
    ///
    /// A product's absence from the result is NOT churn — see design spec §3.4. Only a stated
    /// `.expired`/`.revoked` renewal state means a lapse.
    func subscriptionSnapshots() async throws -> [SubscriptionSnapshot]

    /// The ongoing stream of transactions arriving outside any in-process `purchase(_:)` call —
    /// restored on another device, completed after being killed mid-purchase, etc. Persistent
    /// for the lifetime of the process; `IapHelper` consumes it via a background task started in
    /// `configure(_:)`.
    func transactionUpdates() -> AsyncStream<IapPurchase>

    /// Finalizes a transaction — StoreKit 2's single `Transaction.finish()` call, unlike Play
    /// Billing's two-call acknowledge/consume split (see design spec §3.2). Called exactly once
    /// per successfully processed purchase, from every trigger path.
    func finish(_ purchase: IapPurchase) async
}
