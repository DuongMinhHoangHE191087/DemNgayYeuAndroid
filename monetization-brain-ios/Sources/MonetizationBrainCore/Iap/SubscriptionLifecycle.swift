import Foundation

/// "What is the operational state of this subscription, and what should the app do about it?"
/// (Android `com.app.plugin.iap.SubscriptionLifecycle`; design spec §1.)
///
/// ═══ A PURE CLASSIFICATION LAYER — IT NEVER TALKS TO A STORE ═══
///
/// This type owns no StoreKit dependency and performs no I/O beyond its own key-value
/// persistence. `IapHelper` feeds it `SubscriptionSnapshot`s at explicit sync points
/// (`restorePurchases()`, post-purchase) and it classifies them — the same seam
/// `StoreBackend`/`BrainKeyValueStore` already established, and the reason the whole state
/// machine is unit-testable with zero real I/O.
///
/// ═══ CLASSIFY FROM STOREKIT'S STATED RENEWAL STATE, NEVER FROM AN INFERENCE ═══
///
/// Android's class exists to compensate for what Play does NOT answer on-device: it derives five
/// states from two booleans, so "pending" has to stand in for grace, and *absence from the
/// snapshot set* has to stand in for a lapse. iOS states all of it outright
/// (`RenewalState` + `willAutoRenew` + `ExpirationReason`), so this port classifies rather than
/// guesses. Two consequences that look like small details and are not:
///
/// 1. **`.cancelled` is ENTITLED.** `.subscribed && !willAutoRenew` is a customer who turned off
///    auto-renew and keeps access until the period ends — not churn. Treating it as churn is the
///    single most common mistake when porting a subscription model off Play (§3.3).
/// 2. **Grace-expiry is NOT terminal.** A subscription leaving the grace period continues in
///    billing retry: entitlement is lost, the charge is still being attempted, and it resolves to
///    either `.active` (billing recovery) or `.expired`. Android has no state for this and infers
///    churn — which stops dunning exactly when dunning still has a chance to work, and marks a
///    customer win-back-eligible while Apple is still charging their card (§3.3).
///
/// ═══ ABSENCE IS NOT A LAPSE (§3.4) ═══
///
/// A product missing from a `complete: true` snapshot set produces NO transition and keeps its
/// last known state. Android's absence-inference is deliberately not ported: `.expired`/`.revoked`
/// arrive as stated values here, so the inference buys nothing and costs correctness (it is what
/// makes Android's unknown-state handling silently freeze a product — §3.7). The `history` set
/// survives for exactly ONE narrower job: win-back eligibility needs "was this player ever
/// subscribed?", which no single current snapshot answers.
///
/// ═══ `.unknown` RETAINS, IT NEVER CHURNS (§3.7) ═══
///
/// `SubscriptionRenewalState.unknown` is what an Apple-side addition this SDK hasn't learned yet
/// maps to. It produces no transition, grants no entitlement, and LEAVES THE PRIOR STATE INTACT.
/// The alternative (reading unknown as churn) would revoke a paying customer's state because
/// Apple shipped a new enum value. Android reaches the same outcome by accident and never tests
/// it; here it is deliberate and covered by `test_unknownRetainsTheProductsPriorActiveState...`.
///
/// ═══ DUNNING IS A COMMAND AND A QUERY, NOT ONE MUTATING READ (§3.5) ═══
///
/// Android's `dunningNoticeDue(productId)` increments and persists a counter on every call — a
/// query with a hidden side effect, so rendering two surfaces silently burns a notice and a debug
/// inspector corrupts the cadence. Here `noteForegroundOpen(productId:)` mutates (call it once per
/// foreground) and `dunningNoticeDue(productId:)` is pure (call it as often as you like). The
/// cadence semantics — `[1, 2, 4]` meaning the 1st, 2nd and 4th foreground opens of an episode —
/// are preserved exactly, and the window now covers `.inBillingRetry` as well as `.inGracePeriod`.
///
/// ═══ CONCURRENCY ═══
///
/// One `NSLock`, every acquisition through the synchronous `withLock` helper (`NSLock` is
/// `noasync`), never held across store I/O or an `await`: mutate under the lock, snapshot, unlock,
/// then persist and emit. Any decision that reads more than one locked field takes them in a
/// SINGLE acquisition — `dunningNoticeDue` reads `enabled`, the product's state, the cadence and
/// the counter as one atomic read, because a concurrent `apply()`/`observe()` between two separate
/// reads is precisely the TOCTOU shape Phase 6's security review found in `PurchaseRouter`. Unlike
/// Android — whose `apply()` and `dunningNoticeDue()` are unsynchronized while `observe()`/
/// `clear()` are, so its dunning map is mutated from two different locking regimes — every
/// mutating path here is synchronized.
///
/// ═══ WHAT THIS LAYER CANNOT KNOW ═══
///
/// Everything that happens while the app is closed is invisible to it (§7): expiry, renewal,
/// billing failure, grace entry/exit and refund approval all happen server-side. It is correct
/// about "is this customer entitled right now, while they are looking at the app," and it cannot
/// be correct about churn analytics or refund reaction — that needs App Store Server Notifications
/// V2 on a backend, which is out of scope by construction, not a gap to close in Swift.
public final class SubscriptionLifecycle: @unchecked Sendable {
    public static let shared = SubscriptionLifecycle()

    public struct Configuration: Sendable {
        public let eventSink: EventSink
        public let playerId: String
        /// Where the three pipe-delimited keys below live. Defaults to `UserDefaults`, exactly as
        /// `Entitlements`/`IapHelper` do; tests inject an in-memory store.
        public let store: BrainKeyValueStore

        public init(
            eventSink: EventSink,
            playerId: String,
            store: BrainKeyValueStore = UserDefaultsBrainStore()
        ) {
            self.eventSink = eventSink
            self.playerId = playerId
            self.store = store
        }
    }

    // MARK: - Constants

    // Internal (not private) so the test file can assert against the exact persisted key names
    // without duplicating the string literals.
    static let statesKey = "sub_lifecycle_states"
    static let historyKey = "sub_lifecycle_history"
    static let dunningKey = "sub_lifecycle_dunning"

    private static let entrySeparator: Character = "|"
    private static let pairSeparator: Character = ":"
    private static let notLoadedVersion = "not loaded"

    /// Android's own default, preserved: notify on the 1st, 2nd and 4th foreground open of a
    /// dunning episode. Live BEFORE any `apply(_:)` — a studio that never ships a
    /// `subscription_lifecycle_ios` config still gets working dunning, not silence.
    private static let defaultDunningCadence = [1, 2, 4]
    /// Cadence entries below this are operator error (open counts start at 1) and are dropped.
    private static let minDunningOpen = 1
    /// Never trust config numerics: an open count beyond this is meaningless (nobody opens an app
    /// 1000 times inside one failed-billing episode) and clamping bounds the counter too.
    private static let maxDunningOpen = 1_000

    /// Apple's ONE subscription-management destination. Unlike Play's deep link there is no
    /// per-product or per-package parameterization to build, which is why `managementUrl()` takes
    /// no arguments. `nil` when the feature is disabled — see that method.
    private static let appStoreManagementUrl = "https://apps.apple.com/account/subscriptions"

    // MARK: - State

    private let lock = NSLock()
    /// Orders this type's writes to all three keys without ever holding `lock` across store I/O —
    /// see `SequencedStoreWriter` for the lost-write this closes and the alternatives weighed. One
    /// counter serves all three keys: `lastWritten` is per-key, so `statesKey` never has to compete
    /// with `dunningKey` for a number, and the ordering that matters is per-key anyway.
    private let writer = SequencedStoreWriter()
    private var configuration: Configuration?
    private var version = SubscriptionLifecycle.notLoadedVersion
    private var enabled = true
    private var winBackEnabled = true
    private var dunningCadence = SubscriptionLifecycle.defaultDunningCadence
    private var states: [String: SubscriptionState] = [:]
    private var history: Set<String> = []
    private var dunningOpens: [String: Int] = [:]

    public init() {}

    /// The ONLY place `lock` is acquired. `NSLock.lock()` is `noasync`, so funnelling every
    /// acquisition through one synchronous, non-escaping helper makes it structurally impossible
    /// to take the lock inside an `async` body or hold it across an `await`.
    private func withLock<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    // MARK: - Configuration

    /// Wires up the event sink, player id and persistence — and loads previously persisted state
    /// from that store. Store reads happen BEFORE the lock is taken; the lock only covers the
    /// field assignment.
    public func configure(_ configuration: Configuration) {
        let store = configuration.store
        let loadedStates = Self.loadStates(store)
        let loadedHistory = Self.loadHistory(store)
        let loadedDunning = Self.loadDunning(store)
        withLock {
            self.configuration = configuration
            self.states = loadedStates
            self.history = loadedHistory
            self.dunningOpens = loadedDunning
        }
    }

    public func activeVersion() -> String {
        withLock { version }
    }

    /// Test-only, and INTERNAL on purpose. Android's equivalent (`clear()`) is public and
    /// unguarded, documented only as "Reset (test)" — any host code can call it in production,
    /// wiping persisted state AND resetting `enabled` back to `true`, silently re-enabling a
    /// feature ops had disabled via remote config. Matching `Entitlements`/`ConsentState`/
    /// `AdsBrain`, this stays inside the module (§3.8).
    func resetForTest() {
        let pending = withLock { () -> (BrainKeyValueStore, UInt64)? in
            let store = configuration?.store
            configuration = nil
            version = Self.notLoadedVersion
            enabled = true
            winBackEnabled = true
            dunningCadence = Self.defaultDunningCadence
            states = [:]
            history = []
            dunningOpens = [:]
            guard let store else { return nil }
            return (store, writer.nextSequenceNumber())
        }
        guard let pending else { return }
        for key in [Self.statesKey, Self.historyKey, Self.dunningKey] {
            writer.remove(forKey: key, sequence: pending.1, from: pending.0)
        }
    }

    // MARK: - Config

    /// Parses and applies a `subscription_lifecycle_ios` remote-config payload. NEVER throws out
    /// to the caller — a malformed config just means the currently-running config keeps running,
    /// matching `PurchaseRouter`/`PaywallManager`.
    ///
    /// Strictness is deliberately UNIFORM here, which Android's is not: Android rejects the whole
    /// config over a malformed `dunning_cadence` element but silently defaults `enabled`/
    /// `win_back_enabled` to `true` when they are present with the wrong type (`"enabled": "false"`
    /// as a string reads as `true` — a studio can believe they turned the feature off). Every
    /// degraded field below appends a diagnostic note instead, the rule Phase 6's security review
    /// forced onto `eligible_countries` after it was found failing open silently (§3.8).
    ///
    /// Config fields are assigned ONLY on full success, under one lock acquisition.
    @discardableResult
    public func apply(_ json: String?) -> SubscriptionApplyResult {
        guard let json, !json.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return reject("empty config")
        }
        guard let data = json.data(using: .utf8),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return reject("JSON did not parse to an object")
        }
        guard let rawVersion = root["version"] as? String else {
            return reject("missing 'version' field")
        }
        let ver = rawVersion.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !ver.isEmpty else {
            return reject("blank 'version' field")
        }

        var notes: [String] = []
        let parsedEnabled = JsonConfigValue.flag(root["enabled"], name: "enabled", defaultingTo: true, notes: &notes)
        let parsedWinBack = JsonConfigValue.flag(
            root["win_back_enabled"],
            name: "win_back_enabled",
            defaultingTo: true,
            notes: &notes
        )

        var parsedCadence = Self.defaultDunningCadence
        if let rawCadence = root["dunning_cadence"] {
            guard let elements = rawCadence as? [Any] else {
                return reject("'dunning_cadence' is not an array")
            }
            var values: Set<Int> = []
            for element in elements {
                // `true`/`false` bridge out of JSONSerialization as NSNumbers that cast happily
                // to 1/0 — a boolean is NOT an open count, and reading one as "notify on open 1"
                // is the kind of silent misconfiguration this parser exists to refuse.
                guard !JsonConfigValue.isJsonBoolean(element), let raw = Self.parseOpenCount(element) else {
                    return reject("'dunning_cadence' contains a non-numeric element")
                }
                // A fractional open count is numeric, so it is not a rejection — but it IS an
                // operator mistake, and every other soft degradation in this parser says so.
                // Truncating in silence would be the one exception.
                if let fractional = element as? Double, fractional != fractional.rounded(.towardZero) {
                    notes.append("'dunning_cadence' element \(fractional) is not a whole number -> truncated to \(raw)")
                }
                guard raw >= Self.minDunningOpen else {
                    notes.append("'dunning_cadence' element \(raw) is below \(Self.minDunningOpen) -> dropped")
                    continue
                }
                if raw > Self.maxDunningOpen {
                    notes.append("'dunning_cadence' element \(raw) exceeds \(Self.maxDunningOpen) -> clamped to \(Self.maxDunningOpen)")
                }
                values.insert(min(raw, Self.maxDunningOpen))
            }
            // An EMPTY cadence is valid and means "never send a dunning notice" (matching
            // Android) — it is a legitimate operator choice, not a parse failure.
            parsedCadence = values.sorted()
        }

        withLock {
            version = ver
            enabled = parsedEnabled
            winBackEnabled = parsedWinBack
            dunningCadence = parsedCadence
        }

        var report = "loaded '\(ver)', enabled=\(parsedEnabled), win_back=\(parsedWinBack), dunning_cadence=\(parsedCadence)"
        if !notes.isEmpty {
            report += "\n  \u{26A0} " + notes.joined(separator: "\n  \u{26A0} ")
        }
        return SubscriptionApplyResult(applied: true, report: report)
    }

    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// WHY THE CLAMP HAPPENS BEFORE THE CONVERSION, NOT AFTER
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// `Int(someDouble)` TRAPS — a hard crash, not an optional — when the value exceeds
    /// `Int.max`, and `isFinite` does not protect against it: `1e20` is perfectly finite and
    /// still 10× too large. The caller's `min(raw, maxDunningOpen)` clamp cannot help either,
    /// because the trap happens before it ever sees the value.
    ///
    /// This is reachable from remote config with no ill intent whatsoever. `JSONSerialization`
    /// hands back any number too large for `Int64` as an `NSDecimalNumber` or `__NSCFNumber`
    /// wrapping a `Double`, so `raw as? Int` fails and the `Double` branch takes it:
    ///
    ///     {"dunning_cadence": [99999999999999999999]}   // one extra digit from an ops typo
    ///     {"dunning_cadence": [1e300]}
    ///
    /// Either one crashes every app instance that fetches the config, on the purchase path.
    /// The same bug class was a Critical finding in Phase 5's `parseWeight`. The rule itself now
    /// lives in `JsonConfigValue.clamped(_:lowerBound:upperBound:)` — bound the `Double` in
    /// `Double` space, and only then convert — and this function supplies the bounds.
    ///
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// WHY THIS IS NOT `JsonConfigValue.clampedInt`
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// It shares the clamp, but NOT the acceptance rules, and swapping them would change what
    /// this config accepts — three ways, each covered by a test in `SubscriptionLifecycleTests`:
    ///
    /// - `JsonConfigValue.numeric` accepts a numeric STRING (`"3"`); a cadence element that is a
    ///   string is a rejected config here (`test_dunningCadenceWithANonNumericElementIsRejected`).
    /// - `numeric` DROPS non-positive values; a cadence of `[0, -3, 2]` must stay applied, with
    ///   the out-of-range entries noted and dropped by the loop above
    ///   (`test_dunningCadenceElementsBelowOneAreDroppedWithANote`, and `[-1e300]` in
    ///   `test_dunningCadenceValuesBeyondIntMaxAreClampedRatherThanTrapping`).
    /// - `clampedInt` clamps to the CALLER's bounds; this returns a value still outside them so
    ///   the loop can tell "below 1 -> dropped" from "exceeds 1000 -> clamped" and name the
    ///   offending number in its note.
    ///
    /// The `Int32` range below is deliberately far wider than `minDunningOpen...maxDunningOpen`
    /// for that last reason: it exists only to make the conversion safe, not to bound the value.
    private static func parseOpenCount(_ raw: Any) -> Int? {
        if let value = raw as? Int { return value }
        guard let value = raw as? Double, value.isFinite else { return nil }
        // Deliberately clamped, not rejected: an out-of-range cadence entry is an operator
        // mistake, and the caller's own bounds check then drops or clamps it with a diagnostic
        // note like any other bad value.
        return JsonConfigValue.clamped(value, lowerBound: Int(Int32.min), upperBound: Int(Int32.max))
    }

    private func reject(_ reason: String) -> SubscriptionApplyResult {
        SubscriptionApplyResult(
            applied: false,
            report: "REJECTED subscription lifecycle config (\(reason)) — keeping '\(activeVersion())'"
        )
    }

    // MARK: - Classification

    /// Feeds the classifier a set of snapshots and returns every REAL transition (`from != to`).
    ///
    /// - Parameter complete: whether `snapshots` is the store's full picture (a `restorePurchases`
    ///   sync) or just the products one operation touched (the post-purchase path). It is
    ///   deliberately NOT load-bearing today: absence from a complete set means "no news," never
    ///   "lapsed" (§3.4), so both values behave identically. It is part of the signature so call
    ///   sites state their intent, and so re-introducing absence-inference can never happen as a
    ///   quiet edit — it would have to change this parameter's documented contract first.
    ///
    /// A `.unknown` renewal state is skipped entirely: no transition, no entitlement, prior state
    /// retained (§3.7).
    ///
    /// `async` because it awaits its own event sends rather than handing them to a detached `Task`.
    /// Fire-and-forget would open exactly the drop window Phase 6's security review found in
    /// `grantFromServer` — a transition classified, persisted, and reported to the caller while its
    /// `subscription_state` event never reaches the queue because the process ended before the
    /// detached task ran. Every other event producer in this package (`PurchaseRouter.purchase`,
    /// `PaywallManager.onPaywallShown`) awaits for the same reason, and both call sites (§6) are
    /// already `async`. Events are still BUILT under the same lock that produced the transitions,
    /// so their `lifecycle_version` cannot drift from the config that classified them.
    @discardableResult
    public func observe(_ snapshots: [SubscriptionSnapshot], complete: Bool = true) async -> [SubscriptionTransition] {
        let outcome = withLock { () -> ObserveOutcome in
            guard enabled else { return ObserveOutcome() }
            var outcome = ObserveOutcome()
            var statesChanged = false
            var historyChanged = false
            var dunningChanged = false

            for snapshot in snapshots where !snapshot.productId.isEmpty {
                guard let to = Self.classify(snapshot) else { continue }
                // Every recognized snapshot proves the store knows a subscription record for this
                // product — that, and only that, is what `history` answers for win-back.
                if history.insert(snapshot.productId).inserted { historyChanged = true }

                let from = states[snapshot.productId] ?? SubscriptionState.none
                guard from != to else { continue }
                states[snapshot.productId] = to
                statesChanged = true

                // Episode reset: leaving the dunning window clears the counter so the NEXT
                // failed-billing episode restarts at open 1. Grace -> billing retry is not
                // leaving it (§3.5) — both states are recoverable.
                if from.isRecoverable, !to.isRecoverable, dunningOpens.removeValue(forKey: snapshot.productId) != nil {
                    dunningChanged = true
                }

                outcome.transitions.append(SubscriptionTransition(productId: snapshot.productId, from: from, to: to))
                outcome.events.append(Self.stateEvent(
                    productId: snapshot.productId,
                    from: from,
                    to: to,
                    expirationReason: snapshot.expirationReason,
                    version: version,
                    playerId: configuration?.playerId ?? ""
                ))
            }

            outcome.configuration = configuration
            if statesChanged { outcome.statesLine = Self.encode(states) }
            if historyChanged { outcome.historyLine = history.sorted().joined(separator: String(Self.entrySeparator)) }
            if dunningChanged { outcome.dunningLine = Self.encode(dunningOpens) }
            // Stamped in the SAME acquisition that encoded those three lines — that pairing is what
            // makes "higher sequence" mean "encoded from newer state". All three share one number
            // because they were encoded from one consistent state; `SequencedStoreWriter` compares
            // per key, so they never shadow each other.
            outcome.sequence = writer.nextSequenceNumber()
            return outcome
        }

        // Persist and emit OUTSIDE the lock — never hold it across store I/O or an await. Each
        // write carries the sequence number stamped inside that same acquisition, so a snapshot
        // encoded before this one can never overwrite it (`SequencedStoreWriter`).
        if let store = outcome.configuration?.store {
            let sequence = outcome.sequence
            if let line = outcome.statesLine {
                writer.write(line, forKey: Self.statesKey, sequence: sequence, to: store)
            }
            if let line = outcome.historyLine {
                writer.write(line, forKey: Self.historyKey, sequence: sequence, to: store)
            }
            if let line = outcome.dunningLine {
                writer.write(line, forKey: Self.dunningKey, sequence: sequence, to: store)
            }
        }
        if let sink = outcome.configuration?.eventSink {
            for event in outcome.events { await sink.send(event) }
        }
        return outcome.transitions
    }

    /// The whole state table (§3.3), in one total function of values the platform stated.
    /// `nil` means "unclassifiable" — see `observe`.
    private static func classify(_ snapshot: SubscriptionSnapshot) -> SubscriptionState? {
        switch snapshot.renewalState {
        case .subscribed:
            // The ONLY branch that needs a second fact. Cancelled-but-entitled and
            // renewing-normally both report `.subscribed`.
            return snapshot.willAutoRenew ? .active : .cancelled
        case .inGracePeriod: return .inGracePeriod
        case .inBillingRetryPeriod: return .inBillingRetry
        case .expired: return .expired
        case .revoked: return .revoked
        case .unknown: return nil
        }
    }

    private static func stateEvent(
        productId: String,
        from: SubscriptionState,
        to: SubscriptionState,
        expirationReason: SubscriptionExpirationReason?,
        version: String,
        playerId: String
    ) -> SdkEvent {
        var data: [String: SdkEventValue] = [
            "product_id": .string(productId),
            "from_state": .string(from.rawValue),
            "to_state": .string(to.rawValue),
            "lifecycle_version": .string(version)
        ]
        // Only on `.expired`, and only when the store actually stated a reason (§5). Android's
        // payload carries `screen_name` here instead, read from a global mutable `ScreenName.name`
        // — no such ambient global exists in this port and introducing one to fill a wire field
        // would re-import a pattern this package has been removing (§3.9).
        if to == .expired, let expirationReason {
            data["expiration_reason"] = .string(expirationReason.rawValue)
        }
        return SdkEvent(
            eventName: "subscription_state",
            eventData: data,
            userProperty: UserProperty(userId: playerId)
        )
    }

    private struct ObserveOutcome {
        var transitions: [SubscriptionTransition] = []
        var events: [SdkEvent] = []
        var statesLine: String?
        var historyLine: String?
        var dunningLine: String?
        var configuration: Configuration?
        /// Stays `0` only on the `enabled == false` early return, which encodes no line and so
        /// never reaches `SequencedStoreWriter.write`. Every other path stamps it inside the lock.
        var sequence: UInt64 = 0
    }

    // MARK: - Queries

    /// The product's classified state, or `.none` when it has never been seen — or when the
    /// feature is disabled by remote config, which hides state without destroying it, so flipping
    /// `enabled` back on restores exactly what was already known.
    public func stateFor(productId: String) -> SubscriptionState {
        withLock { enabled ? (states[productId] ?? SubscriptionState.none) : SubscriptionState.none }
    }

    /// A device-local heuristic, NOT StoreKit's native win-back offer eligibility — that is
    /// `RenewalInfo.eligibleWinBackOfferIDs`, iOS 18+, above this package's iOS 15 floor (§1).
    ///
    /// Eligible means: this device has seen the player hold this subscription at some point, and
    /// they are neither entitled now NOR in a recoverable payment-problem state. That second
    /// clause is the point of `.inBillingRetry` existing: winning back someone whose card Apple is
    /// still successfully retrying is a discount handed to a customer who was about to keep paying
    /// (§3.3).
    ///
    /// Known limitation (§7): `history` is device-local, so a reinstall makes a genuinely churned
    /// player look like they were never subscribed. Fixing that needs account-level truth, i.e. a
    /// server.
    public func isWinBackEligible(productId: String) -> Bool {
        withLock { winBackEligibleLocked(productId) }
    }

    public func winBackEligibleProductIds() -> Set<String> {
        withLock { Set(history.filter { winBackEligibleLocked($0) }) }
    }

    /// MUST be called with `lock` held — every public caller reads `enabled`, `winBackEnabled`,
    /// `history` and `states` together in ONE acquisition, never as separate locked reads.
    ///
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// WHY `.revoked` IS EXCLUDED, AND WHAT IT COSTS
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// `.revoked` means a refund OR removal from Family Sharing, and iOS 15 cannot tell the two
    /// apart (`RevocationType` is iOS 26.4 — design spec §7). Only one of those two people should
    /// receive a discounted win-back offer, so the tie has to be broken blind.
    ///
    /// It is broken AGAINST offering. Marketing a discount to someone who just got their money
    /// back reads as tone-deaf at best, and at worst funds a loop — purchase, refund, collect the
    /// win-back discount, repeat — where the SDK itself supplies the incentive. The cost is the
    /// family-share member who genuinely lost access and gets no nudge; they can still resubscribe
    /// unaided, and they are the smaller cohort. Losing one win-back opportunity beats paying a
    /// refunder to churn again, which is the same fail-safe direction `PurchaseRouter` takes on
    /// its country gate.
    ///
    /// `.inBillingRetry` is excluded for a different reason: Apple is still trying to charge that
    /// customer successfully, and discounting someone mid-recovery converts a full-price renewal
    /// into a cheaper one (§3.3).
    private func winBackEligibleLocked(_ productId: String) -> Bool {
        guard enabled, winBackEnabled, history.contains(productId) else { return false }
        let state = states[productId] ?? SubscriptionState.none
        return state == .expired
    }

    /// Where to send someone who wants to manage or resubscribe. Apple exposes ONE account-level
    /// destination with no per-product parameterization (Play's deep link takes a sku and package
    /// name), so this takes no arguments and is a constant.
    ///
    /// `nil` when the feature is disabled by remote config — a studio that turned this layer off
    /// gets no lifecycle-driven UI from it at all, management link included.
    public func managementUrl() -> String? {
        withLock { enabled ? Self.appStoreManagementUrl : nil }
    }

    // MARK: - Dunning

    /// THE COMMAND half of the split (§3.5). Call once per app foreground, per product the host
    /// cares about. Counts only inside a dunning window (`.inGracePeriod`/`.inBillingRetry`);
    /// anywhere else it is a no-op, so the counter always means "opens within THIS episode."
    public func noteForegroundOpen(productId: String) {
        let pending = withLock { () -> (BrainKeyValueStore, String, UInt64)? in
            guard enabled, !productId.isEmpty else { return nil }
            guard (states[productId] ?? SubscriptionState.none).isRecoverable else { return nil }
            // Saturate one past the largest cadence value a config can hold: the counter stays
            // bounded no matter how long an episode runs, and a saturated counter can never
            // accidentally match a cadence entry.
            dunningOpens[productId] = min((dunningOpens[productId] ?? 0) + 1, Self.maxDunningOpen + 1)
            guard let store = configuration?.store else { return nil }
            return (store, Self.encode(dunningOpens), writer.nextSequenceNumber())
        }
        guard let pending else { return }
        writer.write(pending.1, forKey: Self.dunningKey, sequence: pending.2, to: pending.0)
    }

    /// THE QUERY half (§3.5): PURE. No counter is advanced, nothing is persisted, and calling it
    /// ten times to render three surfaces answers the same thing ten times. Android's single
    /// mutating method silently burned a notice on every extra call.
    ///
    /// Reads `enabled`, the product's state, the cadence and the counter in ONE lock acquisition —
    /// four fields a concurrent `apply()`/`observe()` can change between two separate reads.
    public func dunningNoticeDue(productId: String) -> Bool {
        withLock {
            guard enabled else { return false }
            guard (states[productId] ?? SubscriptionState.none).isRecoverable else { return false }
            guard let opens = dunningOpens[productId] else { return false }
            return dunningCadence.contains(opens)
        }
    }

    // MARK: - Persistence

    // Pipe-delimited strings in a `BrainKeyValueStore`, exactly as `Entitlements` does it (§4).
    // Parsing is defensive in both directions: a corrupt or hand-edited value drops the entries it
    // cannot read rather than throwing away the whole file or crashing the host app at launch.

    private static func encode(_ states: [String: SubscriptionState]) -> String {
        states.keys.sorted()
            .map { "\($0)\(pairSeparator)\(states[$0]!.rawValue)" }
            .joined(separator: String(entrySeparator))
    }

    private static func encode(_ counts: [String: Int]) -> String {
        counts.keys.sorted()
            .map { "\($0)\(pairSeparator)\(counts[$0]!)" }
            .joined(separator: String(entrySeparator))
    }

    /// Splits at the LAST `:` so a product id containing one still round-trips.
    private static func splitPair(_ entry: Substring) -> (key: String, value: String)? {
        guard let index = entry.lastIndex(of: pairSeparator) else { return nil }
        let key = String(entry[entry.startIndex..<index])
        let value = String(entry[entry.index(after: index)...])
        guard !key.isEmpty, !value.isEmpty else { return nil }
        return (key, value)
    }

    private static func entries(_ store: BrainKeyValueStore, _ key: String) -> [Substring] {
        guard let raw = store.string(forKey: key), !raw.isEmpty else { return [] }
        return raw.split(separator: entrySeparator)
    }

    private static func loadStates(_ store: BrainKeyValueStore) -> [String: SubscriptionState] {
        var result: [String: SubscriptionState] = [:]
        for entry in entries(store, statesKey) {
            guard let pair = splitPair(entry), let state = SubscriptionState(rawValue: pair.value) else { continue }
            result[pair.key] = state
        }
        return result
    }

    private static func loadHistory(_ store: BrainKeyValueStore) -> Set<String> {
        Set(entries(store, historyKey).map(String.init))
    }

    private static func loadDunning(_ store: BrainKeyValueStore) -> [String: Int] {
        var result: [String: Int] = [:]
        for entry in entries(store, dunningKey) {
            guard let pair = splitPair(entry), let count = Int(pair.value), count > 0 else { continue }
            result[pair.key] = min(count, maxDunningOpen + 1)
        }
        return result
    }
}
