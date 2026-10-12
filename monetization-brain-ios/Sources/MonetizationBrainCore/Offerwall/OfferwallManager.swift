import Foundation

/// "May an offerwall open right now, and has this payout already been made?" — the 4th
/// monetization surface (Android `com.app.plugin.offerwall.OfferwallManager`; design spec §1).
///
/// A vendor-neutral adapter and nothing more: the host wraps each offerwall SDK in an
/// `OfferwallProvider`, this type decides whether one may be presented and de-duplicates
/// completion payouts. It never grants anything — `reportCompletion` returns what the host's own
/// economy code should grant, the same boundary `RecurringGrants`/`PaywallManager` already draw.
///
/// ═══ ⚠️ OFF UNTIL EXPLICITLY ENABLED — ANDROID FAILS OPEN HERE (§2.2) ═══
///
/// Android parses the kill switch as `val enabled = root["enabled"] != false`. A JSON string
/// `"false"`, a number `0`, and an absent key are none of them the Kotlin `Boolean` `false`, so
/// every one of them evaluates to `true`: an operator writing `"enabled": "false"` to turn the
/// offerwall OFF silently turns it on. That is the fifth instance of this class of bug in this
/// port, and — pointedly — `BrainPolicy` in the same Android codebase parses the same field
/// correctly, so it is inconsistency rather than house style.
///
/// Here `enabled` goes through `JsonConfigValue.flag(..., defaultingTo: false, ...)`: absent
/// means off, wrong-typed means off WITH a diagnostic note.
///
/// `BrainPolicy.enabled` defaults the OTHER way, to `true`, and the two are deliberately not
/// unified: that field is a kill switch over tuning the SDK already applies, whose absence means
/// "policy is live". This one decides whether a monetization surface the operator may never have
/// configured starts opening.
///
/// ═══ ⚠️ PROVIDERS ARE NEVER CALLED WHILE HOLDING THE LOCK ═══
///
/// `isAvailable()` and `show(onWallClosed:)` are host code this SDK cannot vet, and `NSLock` is
/// not reentrant. Both are therefore called only after the eligible set has been snapshotted and
/// the lock released — the identical rule `CrmBridge` states for `CrmSink.deliver` (Phase 10
/// §2.2), and for the identical reason: a provider that calls back into this manager from inside
/// either method must not deadlock. It also stops a slow vendor SDK blocking every other caller.
///
/// ═══ `.unknown` CONSENT DOES **NOT** BLOCK — THE CONTRAST WITH `CrmBridge` IS DELIBERATE (§3) ═══
///
/// `CrmBridge.emit` requires `ConsentState.allowsThirdPartySignals()`, i.e. an explicit
/// `.granted`, and refuses `.unknown`. This gate is the looser
/// `ConsentState.allowsPersonalization()` — anything that is not `.denied` — and the difference
/// is the question being asked, not a weaker standard.
///
/// CRM egress sends player data off the device to a processor the player has no relationship
/// with, and a signal already delivered cannot be recalled, so "the app had not asked yet" is not
/// a defence for having sent it. Opening an offerwall is a surface the player actively chooses to
/// open, and **this SDK's own code sends nothing** as a result of this gate: `show` makes no
/// network call and passes no identity to the provider (`show(onWallClosed:)` takes no player
/// argument). A `.denied` player is still excluded, because the wall is a personalized
/// monetization surface; a player the app simply has not asked yet is not.
///
/// **That claim is deliberately narrower than "nothing leaves the device", and the difference is
/// not cosmetic.** A security review pushed back on the wider phrasing and was right to: this gate
/// returning `true` authorizes the host to invoke a live third-party offerwall SDK whose entire
/// purpose is to identify the device in order to serve and attribute offers. Data very plausibly
/// does leave — just not through code this package owns or can see.
///
/// The gate stays as it is, because the decision genuinely belongs to the host: it chose the
/// vendor, embeds their SDK, and signed their data-processing terms, none of which this package
/// can inspect. What changes is that the boundary is stated honestly, so an integrator reads
/// "we do not send anything here" rather than the stronger guarantee this code cannot make on
/// the vendor's behalf.
///
/// (COPPA is separate and stricter: a child-directed app is blocked at every consent tier,
/// because that is not something a consent dialog can waive.)
///
/// ═══ `reportCompletion` DELIBERATELY IGNORES EVERY `show` GATE (§3) ═══
///
/// A kill switch stops new opens; it must not withhold payment for work already completed.
/// Preserved from Android, and extended to the other gates for the same reason: a player who
/// finished an offer before the operator disabled the wall, before consent was withdrawn, or
/// before the allow-list changed, earned that reward under the rules in force at the time.
public final class OfferwallManager: @unchecked Sendable {
    public static let shared = OfferwallManager()

    public struct Configuration: Sendable {
        public let eventSink: EventSink
        public let playerId: String
        /// Where `seenTransactionsKey` lives. Defaults to `UserDefaults`, exactly as
        /// `Entitlements`/`RecurringGrants` do; tests inject an in-memory store — which is the
        /// whole point of §2.4, see this type's persistence section.
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

    /// Internal (not private) so the test file can assert against the exact persisted key name
    /// and the exact cap without duplicating them — matching `RecurringGrants.lastClaimKey`.
    static let seenTransactionsKey = "offerwall_seen_tx"

    /// How many paid transactions are remembered, oldest evicted first.
    ///
    /// Ported as-is from Android. It is a real limit, not a formality: a player who completes
    /// more than this many offers can be paid a second time for the oldest one. That is the
    /// direction this subsystem chooses everywhere (§2.3) — a bounded record that occasionally
    /// over-pays beats an unbounded one that grows in `UserDefaults` forever.
    static let maxSeenTransactions = 200

    private static let notLoadedVersion = "not loaded"

    /// The two persistence delimiters. Entries are joined with `entrySeparator`; each entry is
    /// `providerId` + `pairSeparator` + `transactionId`. Both are forbidden inside either
    /// component — see `sanitizedKeyComponent`.
    private static let entrySeparator: Character = ","
    private static let pairSeparator: Character = ":"

    // MARK: - State

    private let lock = NSLock()
    /// Orders writes to `seenTransactionsKey` without ever holding `lock` across store I/O — see
    /// `SequencedStoreWriter` for the double-grant that closes.
    private let writer = SequencedStoreWriter()
    private var configuration: Configuration?
    private var version = OfferwallManager.notLoadedVersion
    /// Gate 1. A separate flag rather than `version != notLoadedVersion`, so that an operator who
    /// literally names a config version "not loaded" cannot switch the gate off.
    private var configLoaded = false
    /// §2.2. `false`, not `true`. The one line this whole deviation is about.
    private var enabled = false
    /// `nil` means NO allow-list — every registered provider is eligible. An EMPTY set is a
    /// different statement: an allow-list that names nobody, which is what an unreadable
    /// `providers` value degrades to. See `parseAllowedProviderIds`.
    private var allowedProviderIds: Set<String>?
    /// Registration ORDER is preserved (an array, not a dictionary) so `availableProviders()` is
    /// deterministic and a test can assert it. Ids are unique: `register` replaces in place.
    private var providers: [OfferwallProvider] = []
    /// Paid transaction keys, OLDEST FIRST. An array rather than a `Set` because the cap evicts
    /// by age, and age is exactly what a `Set` does not keep.
    private var seenTransactions: [String] = []

    public init() {}

    /// The ONLY place `lock` is acquired — same discipline as `CrmBridge`/`RecurringGrants`:
    /// `NSLock.lock()` is `noasync`, so funnelling every acquisition through one synchronous,
    /// non-escaping helper makes it structurally impossible to hold the lock across an `await`.
    private func withLock<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    // MARK: - Configuration

    /// Wires up the event sink, player id and persistence — and loads the previously paid
    /// transaction record from that store. The store read happens BEFORE the lock is taken; the
    /// lock only covers the field assignment.
    public func configure(_ configuration: Configuration) {
        let loaded = Self.loadSeenTransactions(configuration.store)
        withLock {
            self.configuration = configuration
            // MERGED, not replaced. Completions can arrive before `configure` (a vendor callback
            // racing app launch), and those are held in memory; replacing outright would discard
            // them and let each one pay a second time.
            var merged = loaded
            for key in seenTransactions where !merged.contains(key) {
                merged.append(key)
            }
            if merged.count > Self.maxSeenTransactions {
                merged.removeFirst(merged.count - Self.maxSeenTransactions)
            }
            self.seenTransactions = merged
        }
    }

    public func activeVersion() -> String {
        withLock { version }
    }

    /// Test-only, and INTERNAL on purpose. Android's equivalent is public and wipes the
    /// remote-config-derived state alongside the registered providers, so host code could
    /// silently re-enable an offerwall ops disabled by remote config (or, given Android's
    /// fail-open parse, could not disable one at all). Same treatment as every sibling in this
    /// port: `Entitlements`, `PurchaseRouter`, `PaywallManager`, `SubscriptionLifecycle`,
    /// `RecurringGrants`, `CrmBridge`.
    ///
    /// A host that genuinely needs to stop showing walls unregisters its providers.
    func resetForTest() {
        let pending = withLock { () -> (BrainKeyValueStore, UInt64)? in
            let store = configuration?.store
            configuration = nil
            version = Self.notLoadedVersion
            configLoaded = false
            enabled = false
            allowedProviderIds = nil
            providers = []
            seenTransactions = []
            guard let store else { return nil }
            return (store, writer.nextSequenceNumber())
        }
        guard let pending else { return }
        writer.remove(forKey: Self.seenTransactionsKey, sequence: pending.1, from: pending.0)
    }

    // MARK: - Config

    /// Parses and applies an `offerwall_config_ios` remote-config payload (§3):
    ///
    /// ```json
    /// { "version": "ow_v1", "enabled": true, "providers": ["revu", "adjoe"] }
    /// ```
    ///
    /// NEVER throws out to the caller — a malformed config just means the currently-running
    /// config keeps running, matching every other parser in this package.
    ///
    /// Registered providers are deliberately NOT touched: a config reload is an ops action, and
    /// dropping the host's registrations would silently break the surface until the host happened
    /// to register again. The paid-transaction record is not touched either — a config reload
    /// must never make an already-paid offer payable a second time.
    @discardableResult
    public func apply(_ json: String?) -> OfferwallApplyResult {
        guard let json, !json.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return reject("empty config")
        }
        guard let data = json.data(using: .utf8),
              let root = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else {
            return reject("JSON did not parse to an object")
        }
        guard let ver = (root["version"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
              !ver.isEmpty else {
            return reject("missing 'version' field")
        }

        var notes: [String] = []
        // §2.2: `false`, not `true`.
        let parsedEnabled = JsonConfigValue.flag(root["enabled"], name: "enabled", defaultingTo: false, notes: &notes)
        let parsedAllowList = Self.parseAllowedProviderIds(root["providers"], notes: &notes)

        withLock {
            version = ver
            configLoaded = true
            enabled = parsedEnabled
            allowedProviderIds = parsedAllowList
        }

        let scope: String
        switch parsedAllowList {
        case nil:
            scope = "every registered provider"
        case .some(let ids) where ids.isEmpty:
            scope = "NO provider (the allow-list names nobody)"
        case .some(let ids):
            scope = ids.sorted().joined(separator: ", ")
        }
        var report = "loaded '\(ver)', enabled=\(parsedEnabled), eligible: \(scope)"
        if !notes.isEmpty {
            report += "\n  \u{26A0} " + notes.joined(separator: "\n  \u{26A0} ")
        }
        return OfferwallApplyResult(applied: true, report: report)
    }

    private func reject(_ reason: String) -> OfferwallApplyResult {
        OfferwallApplyResult(
            applied: false,
            report: "REJECTED offerwall config (\(reason)) — keeping '\(activeVersion())'"
        )
    }

    /// The optional `providers` allow-list. Absent or empty means every registered provider is
    /// eligible (§3).
    ///
    /// ═══ A WRONG-TYPED `providers` BLOCKS EVERY PROVIDER, IT DOES NOT FALL BACK TO "ALL" ═══
    ///
    /// The spec leaves this case open; the answer is taken from `CrmBridge.parseAllowedSinkIds`,
    /// which faced the same question. Absent means no allow-list — the operator declining to
    /// narrow. A key that is PRESENT and unreadable (`"providers": "revu"`, a string rather than
    /// an array) is the opposite: the operator was explicitly narrowing and the value could not
    /// be read, so falling back to "everyone" does the exact reverse of what they wrote. An
    /// offerwall that opens nothing is visible in the report and fixed by correcting the config;
    /// a wall opened for a vendor the operator deliberately excluded may be a contractual problem
    /// this SDK cannot see.
    ///
    /// Ids go through `JsonConfigValue.identifier` with the PERSISTENCE DELIMITERS forbidden —
    /// unlike `CrmBridge`, which passes none, because these ids really do end up in a persisted
    /// dedup key (§2.3). Rejecting one here is the cheap, operator-visible half of that defence;
    /// `sanitizedKeyComponent` is the half that handles what arrives at runtime.
    private static func parseAllowedProviderIds(_ raw: Any?, notes: inout [String]) -> Set<String>? {
        guard let raw else { return nil }
        guard let rawProviders = raw as? [Any] else {
            notes.append(
                "'providers' is present but is not an array -> NO provider is eligible. An "
                + "unreadable allow-list is treated as naming nobody, never as naming everybody"
            )
            return []
        }
        // An EMPTY array is the operator declining to narrow, exactly like omitting the key.
        guard !rawProviders.isEmpty else { return nil }

        var result: Set<String> = []
        for element in rawProviders {
            guard let id = JsonConfigValue.identifier(
                element,
                field: "provider_id",
                label: "a 'providers' entry",
                mustNotContain: [entrySeparator, pairSeparator],
                notes: &notes
            ) else { continue }
            result.insert(id)
        }
        if result.isEmpty {
            notes.append(
                "'providers' named \(rawProviders.count) entr\(rawProviders.count == 1 ? "y" : "ies") "
                + "and none was usable -> NO provider is eligible"
            )
        }
        return result
    }

    // MARK: - Providers

    /// Registers (or REPLACES, by `providerId`) an offerwall adapter. Replacing in place keeps
    /// registration order stable, so `availableProviders()` does not depend on when a provider
    /// was last re-registered.
    public func register(_ provider: OfferwallProvider) {
        withLock {
            if let index = providers.firstIndex(where: { $0.providerId == provider.providerId }) {
                providers[index] = provider
            } else {
                providers.append(provider)
            }
        }
    }

    /// - Returns: `true` if a provider with that id was actually removed. The return value exists
    ///   because a host unregistering an id it never registered is a real integration mistake
    ///   worth being able to see.
    @discardableResult
    public func unregister(providerId: String) -> Bool {
        withLock {
            guard let index = providers.firstIndex(where: { $0.providerId == providerId }) else { return false }
            providers.remove(at: index)
            return true
        }
    }

    /// Every registered provider that could be opened RIGHT NOW, in registration order.
    ///
    /// Runs the same gates as `show` and then asks each surviving provider `isAvailable()` — with
    /// the lock RELEASED, because that is host code. A disabled or child-directed configuration
    /// therefore never polls a vendor SDK at all.
    public func availableProviders() -> [String] {
        let eligible = withLock { () -> [OfferwallProvider] in
            guard passesSharedGates() else { return [] }
            return providers.filter { allowedProviderIds?.contains($0.providerId) ?? true }
        }
        return eligible.filter { $0.isAvailable() }.map { $0.providerId }
    }

    /// Gates 1, 2, 4 and 5 of §3 — everything that does not depend on WHICH provider is asked.
    /// Called while holding `lock`, deliberately: reading the consent gates in the same
    /// acquisition that snapshots the providers keeps the decision and the provider list from
    /// describing two different moments. Safe to nest — `ConsentState` takes its own leaf lock
    /// and never calls back into this type, so there is no cycle.
    private func passesSharedGates() -> Bool {
        guard configLoaded, enabled else { return false }
        guard ConsentState.shared.childDirected != true else { return false }
        return ConsentState.shared.allowsPersonalization()
    }

    // MARK: - Show

    /// Opens one offerwall, if every gate allows it.
    ///
    /// ═══ GATE ORDER (§3) ═══
    ///
    /// config loaded -> `enabled` -> provider registered -> NOT child-directed (COPPA) -> consent
    /// not `.denied` -> allow-list -> `isAvailable()`.
    ///
    /// The order is asserted by tests, not merely implemented: a disabled config must report the
    /// kill switch rather than a missing provider, and a missing provider must report an
    /// integration mistake rather than a consent problem. An operator reading the wrong gate name
    /// debugs the wrong thing.
    ///
    /// `isAvailable()` and `show(onWallClosed:)` are both called AFTER the lock is released.
    ///
    /// `async` because it awaits its own `offerwall_impression` event rather than handing it to a
    /// detached `Task` — see `SubscriptionLifecycle.observe`'s doc comment for the drop window
    /// that closes. Every other event producer in this package awaits for the same reason.
    @discardableResult
    public func show(
        providerId: String,
        onWallClosed: @escaping @Sendable () -> Void
    ) async -> OfferwallShowResult {
        let decision = withLock { () -> ShowDecision in
            guard let configuration else { return .refused("OfferwallManager is not configured") }
            guard configLoaded else { return .refused("no offerwall config has loaded yet") }
            guard enabled else { return .refused("the offerwall is disabled by config '\(version)'") }
            guard let provider = providers.first(where: { $0.providerId == providerId }) else {
                return .refused("provider '\(providerId)' is not registered")
            }
            guard ConsentState.shared.childDirected != true else {
                return .refused("the app is declared child-directed (COPPA)")
            }
            guard ConsentState.shared.allowsPersonalization() else {
                return .refused("consent is denied")
            }
            guard allowedProviderIds?.contains(providerId) ?? true else {
                return .refused("provider '\(providerId)' is not on config '\(version)''s allow-list")
            }
            return .allowed(provider, configuration, version)
        }

        guard case .allowed(let provider, let configuration, let configVersion) = decision else {
            guard case .refused(let reason) = decision else {
                return OfferwallShowResult(shown: false, providerId: providerId, report: "refused")
            }
            return OfferwallShowResult(shown: false, providerId: providerId, report: "not shown: \(reason)")
        }

        // Host code from here down — the lock is released.
        guard provider.isAvailable() else {
            return OfferwallShowResult(
                shown: false,
                providerId: providerId,
                report: "not shown: provider '\(providerId)' reports it is not available right now"
            )
        }

        provider.show(onWallClosed: onWallClosed)
        await configuration.eventSink.send(SdkEvent(
            eventName: "offerwall_impression",
            eventData: [
                "provider_id": .string(providerId),
                "offerwall_version": .string(configVersion)
            ],
            userProperty: UserProperty(userId: configuration.playerId)
        ))
        return OfferwallShowResult(
            shown: true,
            providerId: providerId,
            report: "opened '\(providerId)' under config '\(configVersion)'"
        )
    }

    // MARK: - Completions

    /// The vendor says this player finished an offer. Returns what the host should grant.
    ///
    /// ═══ EVERY `show` GATE IS DELIBERATELY IGNORED (§3) ═══
    ///
    /// `enabled`, consent, COPPA and the allow-list all decide whether a NEW wall may open. None
    /// of them may withhold payment for work already completed — the player finished the offer
    /// under whatever rules were in force then. Preserved from Android.
    ///
    /// ═══ DEDUP, AND WHY A DELIMITER IN THE ID DOES NOT COST THE PLAYER THE REWARD (§2.3) ═══
    ///
    /// The record is keyed `providerId` + `:` + `transactionId`, and `transactionId` comes from
    /// the VENDOR — a third-party SDK callback or an S2S payload, in a format that is not ours to
    /// choose. On Android an id containing the `,` that joins entries fractures the record on the
    /// next launch, the reconstructed key matches neither fragment, and the completion is paid
    /// twice.
    ///
    /// Both components therefore go through `sanitizedKeyComponent` before the key is composed. A
    /// completion whose id needed sanitizing is **still accepted and still paid** — losing a
    /// legitimate reward is worse than paying a duplicate, which is Android's own stated trade —
    /// and the `report` says the id was rewritten. Because the escape is injective, dedup keeps
    /// working for such ids rather than silently lapsing; the spec's §4 lists that as a known
    /// limitation, and it is one this implementation does not actually have.
    ///
    /// An id with no identity at all — absent, blank, or past `JsonConfigValue`'s length ceiling
    /// — genuinely cannot be de-duplicated: there is nothing stable to remember, or nothing safe
    /// to persist. Those are paid every time, and the report says so.
    ///
    /// `async` for the `offerwall_completed` event, same reasoning as `show`.
    @discardableResult
    public func reportCompletion(
        providerId: String,
        transactionId: String?,
        rewardType: String,
        rewardAmount: Int
    ) async -> OfferwallCompletionResult {
        var notes: [String] = []
        let provider = Self.sanitizedKeyComponent(providerId, field: "provider_id", notes: &notes)
        let transaction = Self.sanitizedKeyComponent(transactionId, field: "transaction_id", notes: &notes)
        let key: String? = {
            guard let provider, let transaction else { return nil }
            return provider + String(Self.pairSeparator) + transaction
        }()

        let outcome = withLock { () -> (duplicate: Bool, configuration: Configuration?, version: String, pending: PendingWrite?) in
            if let key, seenTransactions.contains(key) {
                return (true, configuration, version, nil)
            }
            var pending: PendingWrite?
            // Recorded whether or not a store exists yet. An earlier version appended only when
            // `configuration` was non-nil, so before `configure()` the `contains` check above could
            // never fire and the SAME transaction paid in full on every repeated call — an
            // unbounded double-payment window during app launch, or forever for a host that calls
            // `reportCompletion` before `configure`. In-memory dedup costs nothing and does not
            // depend on having somewhere to persist to.
            if let key {
                seenTransactions.append(key)
                if seenTransactions.count > Self.maxSeenTransactions {
                    // Oldest first — the record is a rolling window, not a ledger.
                    seenTransactions.removeFirst(seenTransactions.count - Self.maxSeenTransactions)
                }
                // The snapshot and the sequence number that orders it are produced in the SAME
                // acquisition that recorded the payout, which is what makes "higher sequence"
                // mean "encoded from newer state".
                if let configuration {
                    pending = PendingWrite(
                        line: seenTransactions.joined(separator: String(Self.entrySeparator)),
                        sequence: writer.nextSequenceNumber(),
                        store: configuration.store
                    )
                }
            }
            return (false, configuration, version, pending)
        }

        if outcome.duplicate {
            return OfferwallCompletionResult(
                accepted: false,
                rewardType: rewardType,
                rewardAmount: 0,
                report: "refused: '\(providerId)'/'\(transactionId ?? "")' has already been paid"
            )
        }

        // Persist OUTSIDE the lock — never hold it across store I/O or an await.
        if let pending = outcome.pending {
            writer.write(pending.line, forKey: Self.seenTransactionsKey, sequence: pending.sequence, to: pending.store)
        }

        if key == nil {
            notes.append("this completion cannot be de-duplicated, so a repeat of it will be paid again")
        }
        if outcome.configuration == nil {
            notes.append("OfferwallManager is not configured, so nothing was persisted or reported")
        }

        if let configuration = outcome.configuration {
            await configuration.eventSink.send(SdkEvent(
                eventName: "offerwall_completed",
                eventData: [
                    "provider_id": .string(providerId),
                    // The RAW vendor id, not the sanitized key: the backend needs the value the
                    // vendor actually minted in order to reconcile against its own postbacks.
                    "transaction_id": .string(transactionId ?? ""),
                    "reward_type": .string(rewardType),
                    "reward_amount": .int(rewardAmount),
                    "offerwall_version": .string(outcome.version)
                ],
                userProperty: UserProperty(userId: configuration.playerId)
            ))
        }

        var report = "granted \(rewardAmount) '\(rewardType)' for '\(providerId)'"
        if !notes.isEmpty {
            report += "\n  \u{26A0} " + notes.joined(separator: "\n  \u{26A0} ")
        }
        return OfferwallCompletionResult(
            accepted: true,
            rewardType: rewardType,
            rewardAmount: rewardAmount,
            report: report
        )
    }

    /// The outcome of the gate chain, decided inside ONE `lock` acquisition. An enum rather than
    /// a `Result`, because the refusal carries a human-readable gate name rather than an `Error`
    /// — nothing here is thrown, and a gate refusing is not an error condition.
    private enum ShowDecision {
        case allowed(OfferwallProvider, Configuration, String)
        case refused(String)
    }

    /// A snapshot plus the sequence number that orders it. Both are produced inside ONE `lock`
    /// acquisition; the write itself happens after the lock is released.
    private struct PendingWrite {
        let line: String
        let sequence: UInt64
        let store: BrainKeyValueStore
    }

    // MARK: - Key composition (§2.3)

    /// One half of a dedup key, guaranteed safe to embed in a delimiter-joined persistence
    /// record — or `nil` when there is no such guarantee to be had.
    ///
    /// `JsonConfigValue.identifier` does the VALIDATION (its length ceiling, its newline rule,
    /// the two delimiters passed in) and produces the operator-facing note. This function only
    /// decides what to do when that validation fails, which is where the offerwall differs from
    /// every config parser in this package: a config line that fails validation is dropped,
    /// because a dropped line grants nothing and fails closed. A COMPLETION that fails validation
    /// must still be paid, so it needs a fallback rather than a drop.
    ///
    /// - absent, blank, or longer than `JsonConfigValue.maxIdentifierLength` -> `nil`. There is
    ///   either no identity to remember, or an unbounded vendor string that must not be rewritten
    ///   into `UserDefaults` on every completion. The payout still happens; it just cannot be
    ///   de-duplicated.
    /// - otherwise -> percent-escaped, ALWAYS, not only when a delimiter is present. `%` is
    ///   escaped first, which is what makes the mapping injective.
    ///
    /// ═══ WHY "ALWAYS", AND WHAT THE CONDITIONAL VERSION GOT WRONG ═══
    ///
    /// An earlier version escaped only ids that already contained a raw delimiter and returned
    /// everything else verbatim. That is NOT injective, and a security review reproduced the
    /// collision: `"tx:1"` escapes to `"tx%3A1"`, while a different, perfectly legal vendor id
    /// that literally reads `"tx%3A1"` contains no delimiter and so passed through untouched — to
    /// exactly the same key. The second, genuinely distinct completion was then refused as a
    /// duplicate and NOT PAID.
    ///
    /// That is the worse direction. §2.3 accepts a duplicate payout over a lost one, and the
    /// conditional escape produced the loss. Escaping unconditionally removes the overlap by
    /// construction rather than by hoping vendor ids avoid a substring.
    private static func sanitizedKeyComponent(
        _ raw: String?,
        field: String,
        notes: inout [String]
    ) -> String? {
        // `identifier` is still consulted, for its length/blank rules and its diagnostic note —
        // but its RESULT is escaped like any other, never returned raw.
        _ = JsonConfigValue.identifier(
            raw,
            field: field,
            label: "an offerwall completion",
            mustNotContain: [entrySeparator, pairSeparator],
            notes: &notes
        )
        guard let trimmed = raw?.trimmingCharacters(in: .whitespacesAndNewlines),
              !trimmed.isEmpty,
              trimmed.count <= JsonConfigValue.maxIdentifierLength else {
            return nil
        }
        return escapeForKey(trimmed)
    }

    private static func escapeForKey(_ value: String) -> String {
        var escaped = ""
        escaped.reserveCapacity(value.count)
        for character in value {
            switch character {
            case "%": escaped += "%25"
            case entrySeparator: escaped += "%2C"
            case pairSeparator: escaped += "%3A"
            default:
                if character.isNewline { escaped += "%0A" } else { escaped.append(character) }
            }
        }
        return escaped
    }

    // MARK: - Persistence (§2.4)

    /// ═══ THIS IS THE PATH ANDROID CANNOT TEST, AND THEREFORE THE PATH THAT ROTS ═══
    ///
    /// Android's `loadSeenTx` parses a stored string that its unit tests never see:
    /// `PreferenceHelper` no-ops without an Android `Context`, so every test observes the empty
    /// default and the non-empty branch has no coverage at all. That is exactly how the
    /// `RecurringGrants` key-fracturing defect hid. `BrainKeyValueStore` is injectable here, so a
    /// round-trip over a PRE-SEEDED store is mandatory — including the cap trimming oldest-first,
    /// which Android also never tests.
    ///
    /// A blank or empty entry is skipped rather than failing the whole load: one corrupt entry
    /// must not cost a player every other entry's dedup protection. The load is capped too, so a
    /// record that grew past the cap under an older build is trimmed on the way in rather than
    /// being carried forever.
    private static func loadSeenTransactions(_ store: BrainKeyValueStore) -> [String] {
        guard let raw = store.string(forKey: seenTransactionsKey), !raw.isEmpty else { return [] }
        let entries = raw
            .split(separator: entrySeparator)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard entries.count > maxSeenTransactions else { return entries }
        return Array(entries.suffix(maxSeenTransactions))
    }
}
