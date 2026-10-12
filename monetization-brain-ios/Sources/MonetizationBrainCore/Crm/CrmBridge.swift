import Foundation

/// "Something worth re-targeting just happened to this player" — a vendor-neutral outbound signal
/// channel (Android `com.app.plugin.crm.CrmBridge`; design spec §1).
///
/// ═══ THE SDK CARRIES NO CRM DEPENDENCY ═══
///
/// The host registers a `CrmSink` and the SDK hands it normalized `EngagementSignal`s. No vendor
/// SDK is linked, no vendor is named in this package, and nothing here talks to a network.
///
/// ═══ THIS IS NOT THE EVENT PIPELINE (§4) ═══
///
/// `CrmBridge` emits NO `SdkEvent` at all — its output is `deliver` calls to host-registered
/// sinks. `SdkEvent`/`EventQueue` reach the studio's own backend; these signals reach a marketing
/// platform. Two channels, two consent stories, deliberately not merged.
///
/// ═══ ⚠️ OFF UNTIL EXPLICITLY ENABLED — ANDROID FAILS OPEN HERE (§2.1) ═══
///
/// Android parses the flag as `root["enabled"] != false`, so a MISSING key, or a wrong-typed one
/// (`"enabled": "false"`, or `1`), is not the Kotlin literal `false` and therefore evaluates to
/// `true`: the bridge silently starts shipping signals off the device. That contradicts its own
/// documented default (OFF) and its own stated principle that with CRM, silence is the safe
/// state. No Android test covers either case.
///
/// Here `enabled` goes through `JsonConfigValue.flag(..., defaultingTo: false, ...)`: absent means
/// off, wrong-typed means off WITH a diagnostic note. Signals leave the device and reach a
/// marketing platform; enabling that channel by accident is not a recoverable mistake.
///
/// ═══ ⚠️ SINKS ARE NEVER CALLED WHILE HOLDING THE LOCK (§2.2) ═══
///
/// Android's `emit` is `@Synchronized` and calls `deliver` inside the monitor. JVM monitors are
/// REENTRANT, so a sink calling back into the bridge survives there. **`NSLock` is not
/// reentrant** — the same shape deadlocks on iOS, and `deliver` is host code this SDK cannot vet.
///
/// So `emit` snapshots the eligible sinks under the lock, RELEASES it, and only then delivers.
/// That also stops a slow sink blocking every other bridge caller. Per-sink failures stay
/// isolated: one sink returning `false` never stops the others, matching Android.
///
/// `CrmBridgeTests.test_aSinkThatReEntersTheBridgeDoesNotDeadlock` is the test that holds this.
/// It runs `emit` on a background queue on purpose, so the regression surfaces as a failed
/// expectation rather than a hung test process.
///
/// ═══ NO PERSISTENCE, BY DESIGN ═══
///
/// Neither the parsed config nor the sink registrations survive a launch (Android's don't
/// either). Sinks are live host objects that cannot be serialized, and a config the host has not
/// re-fetched should not be able to re-enable an outbound channel on its own.
public final class CrmBridge: @unchecked Sendable {
    public static let shared = CrmBridge()

    /// Just the time seam. There is deliberately no `eventSink` and no `playerId` here: this type
    /// publishes nothing to the event pipeline (§4), and the player a signal is about travels on
    /// the signal itself, because `emitSegmentChange`/`emitFirstPurchase` are called from
    /// `AdsBrain`, which already owns the configured player id.
    public struct Configuration: Sendable {
        /// The time seam (`Support/BrainClock.swift`) — what `emit` stamps an unstamped signal
        /// with, so a test can assert the timestamp without a `ts:` parameter on a public API.
        public let clock: BrainClock

        public init(clock: BrainClock = SystemClock()) {
            self.clock = clock
        }
    }

    private static let notLoadedVersion = "not loaded"

    // MARK: - State

    private let lock = NSLock()
    /// Defaulted rather than optional: the clock is an implementation detail with a correct
    /// default, and making `configure(_:)` a precondition for `emit` would add a second "not
    /// ready" state next to `configLoaded` with no benefit.
    private var clock: BrainClock = SystemClock()
    private var version = CrmBridge.notLoadedVersion
    /// Gate 1 of §3.3. A separate flag rather than `version != notLoadedVersion`, so that an
    /// operator who literally names a config version "not loaded" cannot switch the gate off.
    private var configLoaded = false
    private var enabled = false
    /// Empty means "no allow-list" — every registered sink is eligible (§3.2).
    /// `nil` means NO allow-list — the operator declined to narrow, so every registered sink is
    /// eligible. An EMPTY set is a different statement: an allow-list that names nobody, which is
    /// what an unreadable `sinks` value degrades to. Collapsing the two into one empty set is what
    /// would make a config typo silently broaden delivery instead of stopping it.
    private var allowedSinkIds: Set<String>?
    /// Registration ORDER is preserved (an array, not a dictionary) so delivery order is
    /// deterministic and a test can assert it. Ids are unique: `register` replaces in place.
    private var sinks: [CrmSink] = []

    public init() {}

    /// The ONLY place `lock` is acquired — same discipline as `RecurringGrants`: `NSLock.lock()`
    /// is `noasync`, so funnelling every acquisition through one synchronous, non-escaping helper
    /// makes it structurally impossible to hold the lock across an `await`.
    private func withLock<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    // MARK: - Configuration

    public func configure(_ configuration: Configuration) {
        withLock { clock = configuration.clock }
    }

    public func activeVersion() -> String {
        withLock { version }
    }

    /// Test-only, and INTERNAL on purpose (§2.3). Android's equivalent (`clear()`) is PUBLIC and
    /// wipes the remote-config-derived state alongside the registered sinks — so host code could
    /// silently disable an ops-enabled bridge (or, given Android's fail-open parse, silently
    /// re-enable a disabled one) until the next config fetch. Same treatment as every sibling in
    /// this port: `Entitlements`, `PurchaseRouter`, `PaywallManager`, `SubscriptionLifecycle`,
    /// `RecurringGrants`.
    ///
    /// A host that genuinely needs to stop delivering unregisters its sinks.
    func resetForTest() {
        withLock {
            clock = SystemClock()
            version = Self.notLoadedVersion
            configLoaded = false
            enabled = false
            allowedSinkIds = nil
            sinks = []
        }
    }

    // MARK: - Config

    /// Parses and applies a `crm_bridge_ios` remote-config payload (§3.2):
    ///
    /// ```json
    /// { "version": "crm_v1", "enabled": true, "sinks": ["braze"] }
    /// ```
    ///
    /// NEVER throws out to the caller — a malformed config just means the currently-running
    /// config keeps running, matching `PurchaseRouter`/`PaywallManager`/`RecurringGrants`.
    ///
    /// Registered sinks are deliberately NOT touched: a config reload is an ops action, and
    /// dropping the host's registrations would silently break delivery until the host happened to
    /// register again.
    @discardableResult
    public func apply(_ json: String?) -> CrmApplyResult {
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
        // §2.1: `false`, not `true`. The one line this whole deviation is about.
        let parsedEnabled = JsonConfigValue.flag(root["enabled"], name: "enabled", defaultingTo: false, notes: &notes)
        let parsedAllowList = Self.parseAllowedSinkIds(root["sinks"], notes: &notes)

        withLock {
            version = ver
            configLoaded = true
            enabled = parsedEnabled
            allowedSinkIds = parsedAllowList
        }

        let scope: String
        switch parsedAllowList {
        case nil:
            scope = "every registered sink"
        case .some(let ids) where ids.isEmpty:
            scope = "NO sink (the allow-list names nobody)"
        case .some(let ids):
            scope = ids.sorted().joined(separator: ", ")
        }
        var report = "loaded '\(ver)', enabled=\(parsedEnabled), delivering to \(scope)"
        if !notes.isEmpty {
            report += "\n  \u{26A0} " + notes.joined(separator: "\n  \u{26A0} ")
        }
        return CrmApplyResult(applied: true, report: report)
    }

    private func reject(_ reason: String) -> CrmApplyResult {
        CrmApplyResult(
            applied: false,
            report: "REJECTED CRM config (\(reason)) — keeping '\(activeVersion())'"
        )
    }

    /// The optional `sinks` allow-list. Absent or empty means every registered sink is eligible.
    ///
    /// A non-string element is dropped WITH a note (Android drops it silently) and the remaining
    /// ids still narrow delivery, so that drop fails closed — an id nobody could read is an id
    /// nobody named.
    ///
    /// ═══ A WRONG-TYPED `sinks` BLOCKS EVERY SINK, IT DOES NOT FALL BACK TO "ALL" ═══
    ///
    /// Absent means no allow-list, so everyone registered is eligible — that is the operator
    /// declining to narrow. But a `sinks` key that is PRESENT and unreadable (`"sinks": "braze"`,
    /// a string rather than an array) is the opposite: the operator was explicitly narrowing and
    /// the value could not be read. Falling back to the default there does the exact reverse of
    /// what they wrote.
    ///
    /// The counter-argument is real — the narrow reading kills the channel over one typo, and
    /// every sink an allow-list could exclude was registered by the host app itself, not an
    /// unknown third party. It loses anyway, because the two mistakes are not equally recoverable:
    /// delivering nothing is visible in the report and fixed by correcting the config, while
    /// delivering to a processor the operator deliberately excluded is personal data already gone
    /// to a party they named and rejected — possibly for a contractual or legal reason this SDK
    /// cannot see. Same direction as `enabled` (§2.1): with CRM, silence is the safe state.
    ///
    /// Ids go through `JsonConfigValue.identifier` for its length ceiling and newline rejection;
    /// there are no persistence delimiters to forbid, because nothing here is ever persisted.
    private static func parseAllowedSinkIds(_ raw: Any?, notes: inout [String]) -> Set<String>? {
        guard let raw else { return nil }
        guard let rawSinks = raw as? [Any] else {
            notes.append(
                "'sinks' is present but is not an array -> NO sink is eligible. An unreadable "
                + "allow-list is treated as naming nobody, never as naming everybody: the operator "
                + "was narrowing, and the safe failure is to deliver nothing rather than to deliver "
                + "to a sink they meant to exclude"
            )
            return []
        }
        // An EMPTY array is the operator declining to narrow, exactly like omitting the key —
        // documented that way on both platforms. It is not "an allow-list naming nobody"; only an
        // unreadable value is.
        guard !rawSinks.isEmpty else { return nil }

        var result: Set<String> = []
        for element in rawSinks {
            guard let id = JsonConfigValue.identifier(
                element,
                field: "sink_id",
                label: "a 'sinks' entry",
                mustNotContain: [],
                notes: &notes
            ) else { continue }
            result.insert(id)
        }
        // Named entries that ALL turned out unreadable is the same situation as an unreadable
        // value: an explicit narrowing whose content could not be recovered. Blocking is the safe
        // failure; falling back to "everyone" would broaden delivery on a malformed list.
        if result.isEmpty {
            notes.append(
                "'sinks' named \(rawSinks.count) entr\(rawSinks.count == 1 ? "y" : "ies") and none "
                + "was usable -> NO sink is eligible"
            )
        }
        return result
    }

    // MARK: - Sinks

    /// Registers (or REPLACES, by `sinkId`) a destination. Replacing in place keeps registration
    /// order stable, so delivery order does not depend on when a sink was last re-registered.
    public func register(_ sink: CrmSink) {
        withLock {
            if let index = sinks.firstIndex(where: { $0.sinkId == sink.sinkId }) {
                sinks[index] = sink
            } else {
                sinks.append(sink)
            }
        }
    }

    /// - Returns: `true` if a sink with that id was actually removed. Untested on Android, which
    ///   is why the return value exists at all: a host unregistering an id it never registered is
    ///   a real integration mistake worth being able to see.
    @discardableResult
    public func unregister(sinkId: String) -> Bool {
        withLock {
            guard let index = sinks.firstIndex(where: { $0.sinkId == sinkId }) else { return false }
            sinks.remove(at: index)
            return true
        }
    }

    // MARK: - Emit

    /// Hands one signal to every eligible sink.
    ///
    /// ═══ GATE ORDER (§3.3) ═══
    ///
    /// config loaded -> `enabled` -> NOT child-directed -> personalization consent -> at least one
    /// eligible sink. Child-directed blocks even when consent is granted: COPPA is not something a
    /// consent dialog can waive. `ConsentState.childDirected` is `Bool?` and `nil` means "the host
    /// never declared this", which does NOT block — matching Android's `== true` check, and
    /// matching `ConsentState`'s own rule that the SDK never asserts that legal claim on the app's
    /// behalf.
    ///
    /// The consent reads happen INSIDE this type's lock. That nesting is safe and deliberate:
    /// `ConsentState` takes its own leaf lock and never calls back into this type, so there is no
    /// cycle — and reading both gates in the same acquisition that snapshots the sinks keeps the
    /// decision and the delivery list from describing two different moments.
    ///
    /// Delivery itself happens with the lock RELEASED (§2.2, see the type's banner).
    ///
    /// - Returns: how many sinks ACCEPTED the signal. `0` means a gate blocked it, no sink was
    ///   eligible, or every eligible sink refused — diagnostics only, never control flow.
    @discardableResult
    public func emit(_ signal: EngagementSignal) -> Int {
        let prepared = withLock { () -> (sinks: [CrmSink], signal: EngagementSignal)? in
            guard configLoaded, enabled else { return nil }
            guard ConsentState.shared.childDirected != true else { return nil }
            // `allowsThirdPartySignals`, NOT `allowsPersonalization`: this is the only channel in
            // the SDK that sends player data to a processor outside the studio, so it requires an
            // explicit `.granted` rather than "anything that isn't a refusal". A signal already
            // delivered to an outside party cannot be recalled, which is why "the app had not
            // asked yet" (`.unknown`) is not enough here even though it is enough for building a
            // local profile. See that predicate's banner for the full reasoning.
            guard ConsentState.shared.allowsThirdPartySignals() else { return nil }
            let eligible = sinks.filter { allowedSinkIds?.contains($0.sinkId) ?? true }
            guard !eligible.isEmpty else { return nil }
            var stamped = signal
            if stamped.timestampMs <= 0 { stamped.timestampMs = clock.nowMs() }
            return (eligible, stamped)
        }
        guard let prepared else { return 0 }

        var accepted = 0
        for sink in prepared.sinks {
            if sink.deliver(prepared.signal) { accepted += 1 }
        }
        return accepted
    }

    /// The player's most likely latent type CHANGED — the moment a CRM audience actually needs.
    ///
    /// Segment names are passed as plain strings rather than `LatentPlayerType`, keeping this
    /// file independent of `Brain/` and leaving a host free to emit its own taxonomy. `AdsBrain`
    /// passes `LatentPlayerType.rawValue`.
    @discardableResult
    public func emitSegmentChange(playerId: String, from: String, to: String) -> Int {
        emit(EngagementSignal(
            playerId: playerId,
            kind: .segmentChange,
            properties: ["from_segment": from, "to_segment": to]
        ))
    }

    /// The player's FIRST ever purchase — the highest-value CRM trigger there is, and the one a
    /// caller is most likely to send twice. The "is this the first" decision belongs to the
    /// caller that owns the lifetime counter (`AdsBrain.onPurchase`, which captures the flag
    /// BEFORE incrementing it); this method just normalizes the signal.
    ///
    /// `value_usd` is rendered with two decimals: a currency amount, formatted once here so that
    /// every sink receives the same string rather than each one re-deciding how to print a
    /// `Double`.
    @discardableResult
    public func emitFirstPurchase(playerId: String, usd: Double) -> Int {
        emit(EngagementSignal(
            playerId: playerId,
            kind: .firstPurchase,
            properties: ["value_usd": String(format: "%.2f", usd)]
        ))
    }
}
