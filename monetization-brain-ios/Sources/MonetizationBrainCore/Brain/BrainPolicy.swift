import Foundation

/// "Ops has published new tuning — apply it, but never past what the SDK will allow"
/// (Android `com.app.plugin.brain.BrainPolicy`; design spec §1).
///
/// This type makes NO decisions. It parses a `brain_policy_ios` artifact and writes the tuning
/// knobs the decision path reads — `GuardrailEngine`, `ClickFraudGuard`, `HoldoutManager`,
/// `AdsBrain.rolloutFraction`, `RewardModel`, `FxRates`, `Entitlements`,
/// `PersonalizationScenario.minConfidence` — under ceilings an artifact can never exceed.
///
/// ═══ ⚠️ §2.1 — `Int(Double)` TRAPS IN SWIFT WHERE KOTLIN'S `toInt()` SATURATES ═══
///
/// This is the deviation the whole port turns on. Kotlin's `Double.toInt()` saturates
/// (`1e300.toInt()` is `Int.MAX_VALUE`, no exception), so Android's parser — which runs `num()`
/// over every field and then converts — is crash-proof **by language accident**. Swift's
/// `Int(someDouble)` TRAPS.
///
/// A policy artifact is operator-controlled remote config. Porting `it.toInt()` as `Int(it)`
/// would mean `{"max_ads_24h": 1e300}` takes down every app instance that fetched it — a
/// crash-on-hostile-config bug that does not exist in the original.
///
/// So every numeric conversion here goes through `JsonConfigValue.clamped(_:lowerBound:upperBound:)`,
/// which clamps in `Double` space BEFORE converting. Where a field has a `HARD_*` ceiling, that
/// ceiling IS the upper bound, so the safety cap and the trap-guard are one operation rather than
/// two chances to forget one. `isFinite` is not protection: `1e20` is perfectly finite and still
/// ten times too large for `Int`.
///
/// Fields that stay `Double` (fractions, weights, thresholds) cannot trap, so they clamp with a
/// plain `min`/`max` — after `JsonConfigValue.numeric` has already guaranteed the value is finite,
/// which is the guard that makes that safe.
///
/// ═══ §2.6 — THE NINE `HARD_*` CEILINGS ARE THE SECURITY PROPERTY ═══
///
/// An artifact may make the SDK **more** conservative and never less. Frequency caps clamp on the
/// upper bound; cooldowns clamp on the **lower** one, because a shorter cooldown is the more
/// aggressive direction. `frustrationStreakThreshold` clamps against the CONSTANT rather than the
/// value currently in force — deliberately, since clamping against the current value would
/// ratchet monotonically tighter on repeated fetches and no later artifact could ever relax it.
///
/// ═══ §2.2 — `enabled` DEFAULTS TO `true` HERE, AND TO `false` IN `OfferwallManager` ═══
///
/// Not an inconsistency to unify. This is a kill switch over tuning the SDK already applies:
/// its absence means "the policy is live", matching Android. `OfferwallManager.enabled` decides
/// whether a monetization surface the operator may never have configured starts opening, so its
/// absence means off. Each matches its own subsystem's intent. Both go through
/// `JsonConfigValue.flag`, so a wrong-typed value degrades to the documented default WITH a note
/// — unlike Android's offerwall, which silently reads `"false"` as `true`.
///
/// ═══ §2.7/§4 — NO WHOLE-ARTIFACT ATOMICITY, STATED RATHER THAN IMPLIED ═══
///
/// `apply` performs a dozen writes across eight types. Each one is individually race-free (every
/// knob it touches sits behind `LockedValue` or its owner's own lock), but they are NOT one
/// transaction: a decision path running on another thread can see some knobs updated and others
/// not. Matching Android. Closing it would need one lock spanning five singletons, which is a
/// bigger change than the risk justifies for an artifact that is fetched once per launch.
///
/// The downstream writes therefore happen with this type's OWN lock released. Holding it across
/// them would buy no atomicity (the readers do not take it) and would only add a lock-ordering
/// hazard.
///
/// ═══ NOT PERSISTED, BY DESIGN (§1) ═══
///
/// Every launch starts at the built-in defaults until the next fetch — Android does not persist
/// this either. A tuning artifact that the host has not re-fetched should not be able to keep
/// tightening (or loosening) the SDK on its own.
public final class BrainPolicy: @unchecked Sendable {
    public static let shared = BrainPolicy()

    // MARK: - §2.6 the nine HARD_* ceilings

    /// Daily impression ceiling. The example the spec names outright: an artifact asking for
    /// `max_ads_24h: 9999` must come out at 60.
    public static let hardMaxAds24h = 60
    /// Interstitials in one session. Also the ceiling a per-scenario override is clamped to —
    /// `GuardrailEngine.effectiveInterstitialCap` takes the `min` of the two, so a scenario may
    /// only ever tighten, but the override itself must not be able to exceed the global ceiling
    /// in the first place.
    public static let hardMaxInterstitialPerSession = 10
    /// LOWER bound: a shorter cooldown is the more aggressive direction.
    public static let hardMinMinutesBetweenInterstitial = 1
    /// Clicks inside one `ClickFraudGuard` window before the player is locked out. Raising this
    /// WEAKENS the fraud protection, so it is a ceiling like any other frequency cap — Android's
    /// `HARD_MAX_CLICKS_PER_WINDOW`.
    public static let hardMaxClicksPerWindow = 12
    public static let hardMaxIapOffersPerSession = 6
    /// LOWER bound, same reason as the interstitial cooldown.
    public static let hardMinMinutesBetweenIapOffers = 3
    /// The spend-velocity circuit breaker. An artifact raising this lets the bandit keep selling
    /// to someone in a rapid spending burst, which is the one protection `GuardrailEngine` files
    /// as `.hard` specifically so it can never be bought back.
    public static let hardMaxPurchasesPerSessionBeforeBreak = 5
    /// How deep a losing streak may go before offers stop. Raising it means monetizing someone
    /// who is more stuck — see `GuardrailEngine.frustrationStreakThreshold`'s own doc comment.
    public static let hardMaxFrustrationStreakThreshold = 5
    public static let hardMaxAssistsPerSession = 8

    // There is deliberately NO `hardMaxIapOffers24h`. Android caps nothing on
    // `progression.max_iap_offers_24h` — the knob defaults to `0` (OFF), so every value an
    // artifact can set is stricter than the default and a ceiling would have nothing to protect.
    // The `maxDailyIapOffers` bound below is a §2.1 trap-guard, not a security ceiling.

    // MARK: - Secondary bounds (trap-guards, not security ceilings)

    /// These exist only to satisfy §2.1: a clamp needs an upper bound even where the SAFE
    /// direction is unbounded, because the bound is also what keeps the value away from `Int`'s
    /// edge. They are set far beyond any plausible operator intent, so they never shape behaviour
    /// — unlike the `HARD_*` ceilings above, which are meant to bite.
    private static let maxCooldownMinutes = 1_440 // one day
    private static let maxLevelGate = 100_000
    private static let maxSkipOnReopen = 1_000
    private static let maxDailyIapOffers = 10_000
    /// Android's `window_seconds` floor, expressed in the milliseconds the knob is stored in:
    /// `(it * 1000).toLong().coerceAtLeast(1000L)`.
    private static let minClickFraudWindowMs: Int64 = 1_000
    /// `lockout_seconds` floors at `0`, NOT at one second — `coerceAtLeast(0L)`. A zero lockout
    /// is how an operator turns the lockout off, and clamping it up to a second would quietly
    /// refuse that.
    private static let minClickFraudLockoutMs: Int64 = 0
    private static let maxClickFraudMs: Int64 = 86_400_000 // one day

    /// `[0, 0.5]`, not `[0, 1]`: Android clamps both of these below a half. Holding back — or
    /// exploring on — more than half the population is a configuration error rather than an
    /// experiment, and the clamp says so instead of running it.
    private static let maxHoldoutFraction = 0.5
    private static let maxExplorationFloor = 0.5

    private static let notLoadedVersion = "not loaded"
    private static let noArtifactReport = "no artifact loaded — running the values compiled into this build"

    // MARK: - State

    private let lock = NSLock()
    private var version = BrainPolicy.notLoadedVersion
    /// §2.2: `true`. A kill switch whose absence means "policy is live".
    private var enabled = true
    private var scenarioCaps: [PersonalizationScenario: Int] = [:]
    private var profiles: [LatentPlayerType: LikelihoodProfile] = [:]
    /// `nil` until an artifact names one — `minConfidence()` then reports whatever
    /// `PersonalizationScenario` itself is running, rather than a second, possibly stale copy.
    private var artifactMinConfidence: Double?
    private var applyReport = BrainPolicy.noArtifactReport
    /// `nil` until an artifact names one — see `pendingExplorationFloor` for why the absence
    /// has to stay distinguishable from any particular number.
    private var explorationFloor: Double?
    /// §2.5, see `resetForTest()`.
    private var guardrailSnapshots: [GuardrailSnapshot] = []
    private var globalSnapshot: GlobalKnobs?

    public init() {}

    /// The ONLY place `lock` is acquired — same discipline as `CrmBridge`/`RecurringGrants`/
    /// `OfferwallManager`: `NSLock.lock()` is `noasync`, so funnelling every acquisition through
    /// one synchronous, non-escaping helper makes it structurally impossible to hold the lock
    /// across an `await`.
    private func withLock<T>(_ body: () -> T) -> T {
        lock.lock()
        defer { lock.unlock() }
        return body()
    }

    // MARK: - Queries

    public func activeVersion() -> String {
        withLock { version }
    }

    /// Is the policy layer live? `false` only when an artifact explicitly said `"enabled": false`.
    /// The report of the last artifact that was ACCEPTED — what is running, in words.
    ///
    /// A rejected artifact deliberately does not land here (Android does the same): this answers
    /// "what is in force", and a refused artifact is not in force. The rejection reason is not
    /// lost — it comes back in that call's `PolicyApplyResult` and in
    /// `RemoteConfigApplyReport.describe()`, which is where someone debugging a failed push looks.
    public func lastApplyReport() -> String {
        withLock { applyReport }
    }

    public func isEnabled() -> Bool {
        withLock { enabled }
    }

    /// The interstitial/session cap for one personalization scenario — the artifact's override
    /// where it named one, otherwise the compiled default.
    ///
    /// PULL-based: unlike the guardrail knobs, `PersonalizationScenario.interstitialCapPerSession`
    /// is a computed property with no setter, so there is nothing to write into. The consumer
    /// (`GuardrailEngine.effectiveInterstitialCap`) does not read this yet — wiring it changes
    /// what every decision computes, which is separate work with its own regression surface.
    public func scenarioCap(_ scenario: PersonalizationScenario) -> Int {
        withLock { scenarioCaps[scenario] } ?? scenario.interstitialCapPerSession
    }

    /// The remote-calibrated likelihood profile for one latent type, or `nil` when no COMPLETE
    /// set has been applied — see `LikelihoodProfile`'s banner for why a partial set is dropped
    /// wholesale, and why `LikelihoodEstimator` does not read this yet.
    public func profileFor(_ type: LatentPlayerType) -> LikelihoodProfile? {
        withLock { profiles[type] }
    }

    /// The confidence floor below which belief is too spread out to specialize. Reports the
    /// artifact's value where one was applied, and otherwise whatever
    /// `PersonalizationScenario.minConfidence` is actually running — never a stale private copy.
    public func minConfidence() -> Double {
        withLock { artifactMinConfidence } ?? PersonalizationScenario.minConfidence
    }

    /// Floor on exploration the artifact asks for, or `nil` when it never named one.
    ///
    /// ═══ WHY THIS IS OPTIONAL, AND WHY THAT MATTERS ═══
    ///
    /// `DecisionEngine.applyPolicy` is the consumer: it pushes this value into its
    /// `BanditPolicy.explorationFloor`. Most artifacts — a paywall-only push, a guardrail tweak —
    /// never mention `exploration_floor`, so if this returned a default instead of `nil`, EVERY
    /// apply would overwrite the bandit's own floor with `BrainPolicy`'s. The two constants are
    /// not the same number, so that would quietly re-tune exploration (and with it every logged
    /// propensity, and with that off-policy evaluation) on config pushes about something else
    /// entirely. Android models it as `Double? = null` and only pushes on non-null for exactly
    /// this reason.
    ///
    /// An earlier version of this port made it a non-optional 0.05 and documented that
    /// `BanditPolicy` had no equivalent knob. That was wrong on the facts —
    /// `BanditPolicy.explorationFloor` has existed since Phase 2 — and the value was parsed,
    /// clamped and then dropped.
    public var pendingExplorationFloor: Double? {
        withLock { explorationFloor }
    }

    // MARK: - §2.5 resetForTest

    /// Test-only, and INTERNAL on purpose — same treatment as every sibling in this port.
    ///
    /// ═══ IT REVERTS WHAT IT WROTE, NOT JUST ITS OWN FIELDS (§2.5) ═══
    ///
    /// Android's `clear()` resets only `BrainPolicy`'s own three fields. Every value it wrote
    /// into `GuardrailEngine`, `ClickFraudGuard`, `HoldoutManager`, `AdsBrain.rolloutFraction`,
    /// `RewardModel`, `FxRates` and `Entitlements` survives, and both Android test files work
    /// around that by manually resetting those singletons in `tearDown` — the gap is known and
    /// never asserted. A reset that leaves half the system configured is a test-isolation bug
    /// waiting to be blamed on something else.
    ///
    /// Reverting means restoring the values that were in force BEFORE the first artifact was
    /// applied, captured at that moment — NOT a hard-coded second copy of each downstream type's
    /// defaults. A copy would silently drift the first time someone changed a default in
    /// `GuardrailEngine`, and would also stomp a value the host itself had set.
    ///
    /// `FxRates` is the one exception: it exposes no way to read its table back, so it can only
    /// be cleared. That matches its own `reset()` and is the fail-safe direction anyway — an
    /// unknown rate omits the USD value rather than fabricating one.
    func resetForTest() {
        let (engines, globals) = withLock { () -> ([GuardrailSnapshot], GlobalKnobs?) in
            let engines = guardrailSnapshots
            let globals = globalSnapshot
            version = Self.notLoadedVersion
            enabled = true
            scenarioCaps = [:]
            profiles = [:]
            artifactMinConfidence = nil
            explorationFloor = nil
            applyReport = Self.noArtifactReport
            guardrailSnapshots = []
            globalSnapshot = nil
            return (engines, globals)
        }
        for snapshot in engines {
            guard let engine = snapshot.engine else { continue }
            snapshot.knobs.restore(into: engine)
        }
        globals?.restore()
        // Not snapshottable — see the banner above.
        FxRates.reset()
    }

    // MARK: - Apply

    /// Parses and applies a `brain_policy_ios` artifact.
    ///
    /// ```json
    /// { "version": "policy_v1", "enabled": true,
    ///   "guardrails": {...}, "iap": {...}, "gameplay": {...}, "progression": {...},
    ///   "click_fraud": {...}, "experiment": {...}, "fx_rates": {...},
    ///   "remove_ads_products": [...], "reward": {...}, "scenarios": {...},
    ///   "likelihood_profiles": {...}, "min_confidence": 0.4 }
    /// ```
    ///
    /// NEVER throws out to the caller. Only a missing/blank `version` or unparseable JSON rejects
    /// the whole artifact; every other failure degrades — the field is dropped with a note, its
    /// knob is left exactly as it was, and the rest of the artifact still applies. An unknown
    /// scenario or latent-type name is likewise a note, never a rejection: a newer backend naming
    /// a segment this build does not have must not switch off every other knob in the payload.
    ///
    /// `likelihood_profiles` is the one block that is all-or-nothing — see `LikelihoodProfile`.
    ///
    /// - Parameter guardrails: the engine to write into. Injected rather than reached through a
    ///   singleton because `GuardrailEngine` is not one: `DecisionEngine` owns its instance, and
    ///   a test builds its own.
    @discardableResult
    public func apply(_ json: String?, guardrails: GuardrailEngine) -> PolicyApplyResult {
        guard let json, !json.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return reject("empty artifact")
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
        let parsedEnabled = JsonConfigValue.flag(root["enabled"], name: "enabled", defaultingTo: true, notes: &notes)

        // Capture the pre-artifact state ONCE per engine, and once for the process-wide knobs,
        // so `resetForTest` has something real to restore (§2.5). Done under this type's lock and
        // before any write, so two concurrent applies cannot both record a half-written state.
        withLock {
            if !guardrailSnapshots.contains(where: { $0.engine === guardrails }) {
                guardrailSnapshots.append(GuardrailSnapshot(engine: guardrails, knobs: GuardrailKnobs(guardrails)))
            }
            if globalSnapshot == nil { globalSnapshot = GlobalKnobs() }
        }

        // ═══ Every write below happens with this type's lock RELEASED (§2.7). ═══
        applyGuardrailBlock(root["guardrails"], into: guardrails, notes: &notes)
        applyIapBlock(root["iap"], into: guardrails, notes: &notes)
        applyGameplayBlock(root["gameplay"], into: guardrails, notes: &notes)
        applyProgressionBlock(root["progression"], into: guardrails, notes: &notes)
        applyClickFraudBlock(root["click_fraud"], notes: &notes)
        let parsedFloor = applyExperimentBlock(root["experiment"], notes: &notes)
        applyFxRatesBlock(root["fx_rates"], notes: &notes)
        applyRemoveAdsProducts(root["remove_ads_products"], notes: &notes)
        applyRewardBlock(root["reward"], notes: &notes)
        let parsedCaps = Self.parseScenarioCaps(root["scenarios"], notes: &notes)
        let parsedProfiles = Self.parseLikelihoodProfiles(root["likelihood_profiles"], notes: &notes)
        let parsedConfidence = Self.unitInterval(
            root["min_confidence"], field: "min_confidence", label: "the artifact", notes: &notes
        )
        if let parsedConfidence { PersonalizationScenario.minConfidence = parsedConfidence }

        var report = "loaded '\(ver)', enabled=\(parsedEnabled)"
        if !notes.isEmpty {
            report += "\n  \u{26A0} " + notes.joined(separator: "\n  \u{26A0} ")
        }

        // Report included in the SAME acquisition as the fields it describes. Written afterwards
        // in its own `withLock`, a reader landing between the two would see the new version with
        // the previous artifact's report next to it — a one-line lie in exactly the dump someone
        // opens to find out which artifact is running.
        withLock {
            applyReport = report
            version = ver
            enabled = parsedEnabled
            // REPLACE, never merge: an artifact that stops naming a scenario or a profile set is
            // reverting it to the compiled default, not leaving the old override in place. The
            // same reason `FxRates.setRates` replaces its whole table.
            scenarioCaps = parsedCaps
            profiles = parsedProfiles
            artifactMinConfidence = parsedConfidence
            if let parsedFloor { explorationFloor = parsedFloor }
        }

        return PolicyApplyResult(applied: true, report: report)
    }

    private func reject(_ reason: String) -> PolicyApplyResult {
        PolicyApplyResult(
            applied: false,
            report: "REJECTED brain policy artifact (\(reason)) — keeping '\(activeVersion())'"
        )
    }

    // MARK: - Blocks

    /// ═══ EVERY FREQUENCY CAP HERE FLOORS AT `0`, NOT AT `1` ═══
    ///
    /// Android's `clampUpper` ends in `value.coerceAtLeast(0)`, so `max_ads_24h: 0` is a value it
    /// honours — and it is honoured in the SAFE direction: zero ads, zero offers, zero assists.
    /// Dropping it as "not a positive number" would leave the knob at its compiled default, so an
    /// operator turning a surface off from the artifact would silently get 40 ads a day instead.
    private func applyGuardrailBlock(_ raw: Any?, into engine: GuardrailEngine, notes: inout [String]) {
        guard let block = Self.objectBlock(raw, named: "guardrails", notes: &notes) else { return }
        if let value = JsonConfigValue.clampedInt(
            block["max_interstitial_per_session"], field: "max_interstitial_per_session",
            label: "'guardrails'", lowerBound: 0, upperBound: Self.hardMaxInterstitialPerSession,
            allowingNonPositive: true, notes: &notes
        ) { engine.maxInterstitialPerSession = value }
        // LOWER bound is the HARD_* one — a shorter cooldown is the aggressive direction.
        if let value = JsonConfigValue.clampedInt(
            block["min_minutes_between_interstitial"], field: "min_minutes_between_interstitial",
            label: "'guardrails'", lowerBound: Self.hardMinMinutesBetweenInterstitial,
            upperBound: Self.maxCooldownMinutes, allowingNonPositive: true, notes: &notes
        ) { engine.minMinutesBetweenInterstitial = value }
        if let value = JsonConfigValue.clampedInt(
            block["max_ads_24h"], field: "max_ads_24h",
            label: "'guardrails'", lowerBound: 0, upperBound: Self.hardMaxAds24h,
            allowingNonPositive: true, notes: &notes
        ) { engine.maxAds24h = value }
        // Android's field name is `churn_risk_threshold`; the KNOB it feeds is
        // `churnRiskProtectionThreshold`. The wire name is the short one, and an artifact written
        // against the long one would set nothing at all.
        if let value = Self.unitInterval(
            block["churn_risk_threshold"], field: "churn_risk_threshold",
            label: "'guardrails'", notes: &notes
        ) { engine.churnRiskProtectionThreshold = value }
    }

    private func applyIapBlock(_ raw: Any?, into engine: GuardrailEngine, notes: inout [String]) {
        guard let block = Self.objectBlock(raw, named: "iap", notes: &notes) else { return }
        if let value = JsonConfigValue.clampedInt(
            block["max_offers_per_session"], field: "max_offers_per_session",
            label: "'iap'", lowerBound: 0, upperBound: Self.hardMaxIapOffersPerSession,
            allowingNonPositive: true, notes: &notes
        ) { engine.maxIapOffersPerSession = value }
        if let value = JsonConfigValue.clampedInt(
            block["min_minutes_between_offers"], field: "min_minutes_between_offers",
            label: "'iap'", lowerBound: Self.hardMinMinutesBetweenIapOffers,
            upperBound: Self.maxCooldownMinutes, allowingNonPositive: true, notes: &notes
        ) { engine.minMinutesBetweenIapOffers = value }
        if let value = JsonConfigValue.clampedInt(
            block["max_purchases_per_session_before_break"], field: "max_purchases_per_session_before_break",
            label: "'iap'", lowerBound: 0, upperBound: Self.hardMaxPurchasesPerSessionBeforeBreak,
            allowingNonPositive: true, notes: &notes
        ) { engine.maxPurchasesPerSessionBeforeBreak = value }
        // §2.6: clamped against the CONSTANT, never against `engine.frustrationStreakThreshold`.
        // Using the current value would ratchet monotonically tighter on every fetch.
        if let value = JsonConfigValue.clampedInt(
            block["frustration_streak_threshold"], field: "frustration_streak_threshold",
            label: "'iap'", lowerBound: 0, upperBound: Self.hardMaxFrustrationStreakThreshold,
            allowingNonPositive: true, notes: &notes
        ) { engine.frustrationStreakThreshold = value }
    }

    private func applyGameplayBlock(_ raw: Any?, into engine: GuardrailEngine, notes: inout [String]) {
        guard let block = Self.objectBlock(raw, named: "gameplay", notes: &notes) else { return }
        if let value = JsonConfigValue.clampedInt(
            block["max_assists_per_session"], field: "max_assists_per_session",
            label: "'gameplay'", lowerBound: 0, upperBound: Self.hardMaxAssistsPerSession,
            allowingNonPositive: true, notes: &notes
        ) { engine.maxAssistsPerSession = value }
    }

    /// Every knob here documents `0` as OFF, so all four accept non-positive values and clamp
    /// them to `0` rather than dropping them — an artifact must be able to turn a progression
    /// gate back off.
    private func applyProgressionBlock(_ raw: Any?, into engine: GuardrailEngine, notes: inout [String]) {
        guard let block = Self.objectBlock(raw, named: "progression", notes: &notes) else { return }
        if let value = JsonConfigValue.clampedInt(
            block["min_level_for_interstitial"], field: "min_level_for_interstitial",
            label: "'progression'", lowerBound: 0, upperBound: Self.maxLevelGate,
            allowingNonPositive: true, notes: &notes
        ) { engine.minLevelForInterstitial = value }
        if let value = JsonConfigValue.clampedInt(
            block["min_level_for_interstitial_after_loss"], field: "min_level_for_interstitial_after_loss",
            label: "'progression'", lowerBound: 0, upperBound: Self.maxLevelGate,
            allowingNonPositive: true, notes: &notes
        ) { engine.minLevelForInterstitialAfterLoss = value }
        if let value = JsonConfigValue.clampedInt(
            block["min_minutes_after_rewarded"], field: "min_minutes_after_rewarded",
            label: "'progression'", lowerBound: 0, upperBound: Self.maxCooldownMinutes,
            allowingNonPositive: true, notes: &notes
        ) { engine.minMinutesAfterRewarded = value }
        if let value = JsonConfigValue.clampedInt(
            block["skip_first_interstitials_on_reopen"], field: "skip_first_interstitials_on_reopen",
            label: "'progression'", lowerBound: 0, upperBound: Self.maxSkipOnReopen,
            allowingNonPositive: true, notes: &notes
        ) { engine.skipFirstInterstitialsOnReopen = value }
        // Android reads this under `progression`, NOT under `iap`, and gives it no hard ceiling:
        // the knob is OFF (`0`) by default, so every value is a tightening. The bound below is
        // only §2.1's trap-guard.
        if let value = JsonConfigValue.clampedInt(
            block["max_iap_offers_24h"], field: "max_iap_offers_24h",
            label: "'progression'", lowerBound: 0, upperBound: Self.maxDailyIapOffers,
            allowingNonPositive: true, notes: &notes
        ) { engine.maxIapOffers24h = value }
        // Android's one cross-field sanity check, ported: a loss-branch gate that opens EARLIER
        // than the win-branch one means hitting the player who just lost before the one who just
        // won, which is almost always a transposed pair of numbers rather than an intent. Read
        // back off the engine — the comparison is between the EFFECTIVE values, which may include
        // one this artifact did not name.
        let winGate = engine.minLevelForInterstitial
        let lossGate = engine.minLevelForInterstitialAfterLoss
        if lossGate >= 1 && lossGate < winGate {
            notes.append(
                "'progression': the loss-branch gate (\(lossGate)) is LOWER than the win-branch gate "
                + "(\(winGate)) -> check the artifact"
            )
        }
    }

    /// ═══ THE WIRE CARRIES SECONDS; THE KNOBS HOLD MILLISECONDS ═══
    ///
    /// `window_seconds`/`lockout_seconds` are the ONLY fields in the artifact whose unit differs
    /// from the knob they feed, and Android does the conversion inside the parser:
    /// `(it * 1000).toLong().coerceAtLeast(...)`. Reading them as milliseconds turns the schema's
    /// own reference artifact — `window_seconds: 300` — into a 300-MILLISECOND window, i.e. no
    /// fraud protection at all, with nothing anywhere to say so.
    private func applyClickFraudBlock(_ raw: Any?, notes: inout [String]) {
        guard let block = Self.objectBlock(raw, named: "click_fraud", notes: &notes) else { return }
        if let value = JsonConfigValue.clampedInt(
            block["max_clicks_per_window"], field: "max_clicks_per_window",
            label: "'click_fraud'", lowerBound: 0, upperBound: Self.hardMaxClicksPerWindow,
            allowingNonPositive: true, notes: &notes
        ) { ClickFraudGuard.maxClicksPerWindow = value }
        if let value = Self.millisecondsFromSeconds(
            block["window_seconds"], field: "window_seconds", label: "'click_fraud'",
            lowerBound: Self.minClickFraudWindowMs, notes: &notes
        ) { ClickFraudGuard.windowMs = value }
        if let value = Self.millisecondsFromSeconds(
            block["lockout_seconds"], field: "lockout_seconds", label: "'click_fraud'",
            lowerBound: Self.minClickFraudLockoutMs, notes: &notes
        ) { ClickFraudGuard.lockoutMs = value }
    }

    /// Seconds off the wire, milliseconds into the knob — multiplied in `Double` space and only
    /// then clamped and converted, so §2.1 still holds: `1e300` seconds overflows to `+inf` on
    /// the multiply at worst, and `clamped` returns the ceiling for that rather than trapping.
    ///
    /// The upper bound is §2.1's trap-guard, not a ceiling Android has: `ClickFraudGuard` ADDS
    /// `lockoutMs` to a wall-clock reading, so an unbounded value would overflow the addition as
    /// well as the conversion.
    private static func millisecondsFromSeconds(
        _ raw: Any?,
        field: String,
        label: String,
        lowerBound: Int64,
        notes: inout [String]
    ) -> Int64? {
        guard let seconds = JsonConfigValue.numeric(
            raw, field: field, label: label, allowingNonPositive: true, notes: &notes
        ) else { return nil }
        return JsonConfigValue.clamped(
            seconds * 1_000.0, lowerBound: lowerBound, upperBound: maxClickFraudMs
        )
    }

    /// - Returns: the parsed `exploration_floor`, which this type stores itself rather than
    ///   writing anywhere downstream.
    private func applyExperimentBlock(_ raw: Any?, notes: inout [String]) -> Double? {
        guard let block = Self.objectBlock(raw, named: "experiment", notes: &notes) else { return nil }
        // `[0, 0.5]`, matching Android — NOT the unit interval. A holdout above a half is a
        // configuration error, and clamping it to `1.0` would put the whole population in the
        // control group and measure nothing.
        if let value = Self.unitInterval(
            block["holdout_fraction"], field: "holdout_fraction", label: "'experiment'",
            upperBound: Self.maxHoldoutFraction, notes: &notes
        ) { HoldoutManager.holdoutFraction = value }
        // The salt re-buckets the ENTIRE population when it changes, so an unreadable one is
        // dropped and the running salt survives — mid-cycle re-bucketing on a typo destroys the
        // comparability of data already collected and cannot be undone by fixing the config.
        // No persistence delimiters to forbid: nothing joins this into a key.
        if let value = JsonConfigValue.identifier(
            block["holdout_salt"], field: "holdout_salt", label: "'experiment'", mustNotContain: [], notes: &notes
        ) { HoldoutManager.salt = value }
        // NOT capped at a half: 100% is where a staged rollout is supposed to end up.
        if let value = Self.unitInterval(
            block["rollout_fraction"], field: "rollout_fraction", label: "'experiment'", notes: &notes
        ) {
            AdsBrain.shared.rolloutFraction = value
            if value <= 0 {
                notes.append("'experiment': rollout_fraction=0 -> the brain drives no player at all")
            }
        }
        guard let floor = Self.unitInterval(
            block["exploration_floor"], field: "exploration_floor",
            label: "'experiment'", upperBound: Self.maxExplorationFloor, notes: &notes
        ) else { return nil }
        if floor <= 0 {
            notes.append("'experiment': exploration_floor=0 -> off-policy evaluation will not be usable")
        }
        return floor
    }

    /// `FxRates.setRates` REPLACES the whole table, which is why an unreadable block is skipped
    /// entirely rather than applied as an empty one: a typo must not drop every known rate and
    /// silently blank out the USD column for every non-USD market.
    private func applyFxRatesBlock(_ raw: Any?, notes: inout [String]) {
        guard let raw else { return }
        guard let block = raw as? [String: Any] else {
            notes.append("'fx_rates' is not an object -> the running rate table is kept")
            return
        }
        var rates: [String: Double] = [:]
        for (code, value) in block {
            guard let rate = JsonConfigValue.numeric(
                value, field: code, label: "'fx_rates'", notes: &notes
            ) else { continue }
            rates[code] = rate
        }
        // Android applies the table only when at least one rate survived parsing, and so does
        // this: `setRates` REPLACES, so applying an empty result would blank the USD column for
        // every non-USD market on the strength of one mistyped rate.
        guard !rates.isEmpty else {
            notes.append("'fx_rates' carried no usable rate -> the running rate table is kept")
            return
        }
        FxRates.setRates(rates)
    }

    /// Ids are validated against `Entitlements`' OWN persistence delimiter, because that is where
    /// they are about to be written: `Entitlements` joins this set with `|` into `UserDefaults`,
    /// so an id containing one would not survive its own round trip — the `RecurringGrants`-class
    /// defect, in a different subsystem.
    private func applyRemoveAdsProducts(_ raw: Any?, notes: inout [String]) {
        guard let raw else { return }
        guard let rawProducts = raw as? [Any] else {
            notes.append("'remove_ads_products' is not an array -> the running list is kept")
            return
        }
        var products: Set<String> = []
        for element in rawProducts {
            guard let id = JsonConfigValue.identifier(
                element,
                field: "product_id",
                label: "a 'remove_ads_products' entry",
                mustNotContain: [Character(Entitlements.separator)],
                notes: &notes
            ) else { continue }
            products.insert(id)
        }
        Entitlements.shared.removeAdsProductIds = products
    }

    private func applyRewardBlock(_ raw: Any?, notes: inout [String]) {
        guard let block = Self.objectBlock(raw, named: "reward", notes: &notes) else { return }
        if let value = Self.unitInterval(
            block["weight_immediate_value"], field: "weight_immediate_value", label: "'reward'", notes: &notes
        ) { RewardModel.weightImmediateValue = value }
        if let value = Self.unitInterval(
            block["weight_continuation"], field: "weight_continuation", label: "'reward'", notes: &notes
        ) { RewardModel.weightContinuation = value }
        // Android's property check, ported: these two weights are a GOVERNANCE parameter, not a
        // technical tuning. Pushed far enough toward revenue they invert the ordering the whole
        // reward model exists to encode — "showed an ad and lost the player" must always score
        // BELOW "stayed quiet and kept them". The weights still apply; the report says what they
        // did, so the change is visible in the audit trail rather than only in the behaviour.
        let adThenQuit = RewardModel.estimate(actionHadValue: true, playerContinued: false)
        let silentThenStay = RewardModel.estimate(actionHadValue: false, playerContinued: true)
        if adThenQuit >= silentThenStay {
            notes.append(
                "'reward': these weights LOSE the anti-degeneracy property "
                + "(ad+quit=\(String(format: "%.2f", adThenQuit)) >= "
                + "quiet+stay=\(String(format: "%.2f", silentThenStay))) -> the bandit will learn to be aggressive"
            )
        }
    }

    private static func parseScenarioCaps(_ raw: Any?, notes: inout [String]) -> [PersonalizationScenario: Int] {
        guard let block = objectBlock(raw, named: "scenarios", notes: &notes) else { return [:] }
        var caps: [PersonalizationScenario: Int] = [:]
        for (name, value) in block {
            guard let scenario = PersonalizationScenario(rawValue: name) else {
                notes.append("'scenarios' names '\(name)', which this build has no scenario for -> skipped")
                continue
            }
            guard let entry = value as? [String: Any] else {
                notes.append("'scenarios.\(name)' is not an object -> skipped")
                continue
            }
            guard let cap = JsonConfigValue.clampedInt(
                entry["interstitial_cap"], field: "interstitial_cap",
                label: "'scenarios.\(name)'", lowerBound: 0,
                upperBound: hardMaxInterstitialPerSession, allowingNonPositive: true, notes: &notes
            ) else { continue }
            caps[scenario] = cap
        }
        return caps
    }

    /// ═══ THE ONE ALL-OR-NOTHING BLOCK (§3) ═══
    ///
    /// A profile's four numbers only mean anything relative to the other segments' four, so a
    /// partial set is dropped ENTIRELY rather than mixed with compiled defaults — see
    /// `LikelihoodProfile`'s banner. Every reason for incompleteness lands here: a missing
    /// segment, an unknown segment name, an unreadable field inside one profile.
    private static func parseLikelihoodProfiles(
        _ raw: Any?,
        notes: inout [String]
    ) -> [LatentPlayerType: LikelihoodProfile] {
        guard let block = objectBlock(raw, named: "likelihood_profiles", notes: &notes) else { return [:] }
        var parsed: [LatentPlayerType: LikelihoodProfile] = [:]
        for (name, value) in block {
            guard let type = LatentPlayerType(rawValue: name) else {
                notes.append("'likelihood_profiles' names '\(name)', which this build has no latent type for")
                continue
            }
            guard let entry = value as? [String: Any] else {
                notes.append("'likelihood_profiles.\(name)' is not an object")
                continue
            }
            let label = "'likelihood_profiles.\(name)'"
            guard let failRate = unitInterval(entry["fail_rate"], field: "fail_rate", label: label, notes: &notes),
                  let accept = unitInterval(entry["rewarded_accept"], field: "rewarded_accept", label: label, notes: &notes),
                  let payer = unitInterval(entry["payer_propensity"], field: "payer_propensity", label: label, notes: &notes),
                  let churn = unitInterval(entry["churn_tendency"], field: "churn_tendency", label: label, notes: &notes) else {
                continue
            }
            parsed[type] = LikelihoodProfile(
                failRate: failRate, rewardedAcceptRate: accept, payerPropensity: payer, churnTendency: churn
            )
        }
        guard parsed.count == LatentPlayerType.allCases.count else {
            notes.append(
                "'likelihood_profiles' produced \(parsed.count) of \(LatentPlayerType.allCases.count) "
                + "segments -> the WHOLE set is dropped. Mixing calibrated profiles with compiled "
                + "ones makes the cross-segment comparison meaningless"
            )
            return [:]
        }
        return parsed
    }

    // MARK: - Parsing helpers

    private static func objectBlock(
        _ raw: Any?,
        named name: String,
        notes: inout [String]
    ) -> [String: Any]? {
        guard let raw else { return nil }
        guard let block = raw as? [String: Any] else {
            notes.append("'\(name)' is not an object -> the whole block is skipped")
            return nil
        }
        return block
    }

    /// A `Double` knob confined to `[0, upperBound]`, the unit interval unless the caller says
    /// otherwise (`experiment.holdout_fraction` and `experiment.exploration_floor` stop at `0.5`).
    ///
    /// NOT a private copy of `JsonConfigValue.clamped`: that helper exists to stop `Int.init`
    /// trapping, and there is no conversion here to trap. What this needs instead is the range,
    /// and the only precondition for `min`/`max` being safe is finiteness — which
    /// `JsonConfigValue.numeric` has already guaranteed by dropping `NaN`/`inf`. `1e300` is
    /// finite, so it arrives here and clamps to `1.0` rather than being dropped.
    ///
    /// `allowingNonPositive` is set because `0` is meaningful for every caller: a `0.0` holdout
    /// fraction disables holdout, a `0.0` rollout fraction disables the brain, and a `0.0` reward
    /// weight switches off a term.
    private static func unitInterval(
        _ raw: Any?,
        field: String,
        label: String,
        upperBound: Double = 1.0,
        notes: inout [String]
    ) -> Double? {
        guard let value = JsonConfigValue.numeric(
            raw, field: field, label: label, allowingNonPositive: true, notes: &notes
        ) else { return nil }
        return Swift.min(Swift.max(value, 0.0), upperBound)
    }

    // MARK: - §2.5 snapshots

    /// A `GuardrailEngine` and the knob values it had before this type first wrote to it.
    ///
    /// The reference is WEAK: `BrainPolicy.shared` outlives every engine a test builds, and a
    /// strong reference here would keep each one alive for the life of the process. An engine
    /// that has already been deallocated has nothing left to restore.
    private final class GuardrailSnapshot {
        weak var engine: GuardrailEngine?
        let knobs: GuardrailKnobs
        init(engine: GuardrailEngine, knobs: GuardrailKnobs) {
            self.engine = engine
            self.knobs = knobs
        }
    }

    private struct GuardrailKnobs {
        let maxInterstitialPerSession: Int
        let minMinutesBetweenInterstitial: Int
        let maxAds24h: Int
        let churnRiskProtectionThreshold: Double
        let maxIapOffersPerSession: Int
        let minMinutesBetweenIapOffers: Int
        let maxIapOffers24h: Int
        let maxPurchasesPerSessionBeforeBreak: Int
        let frustrationStreakThreshold: Int
        let maxAssistsPerSession: Int
        let minLevelForInterstitial: Int
        let minLevelForInterstitialAfterLoss: Int
        let minMinutesAfterRewarded: Int
        let skipFirstInterstitialsOnReopen: Int

        init(_ engine: GuardrailEngine) {
            maxInterstitialPerSession = engine.maxInterstitialPerSession
            minMinutesBetweenInterstitial = engine.minMinutesBetweenInterstitial
            maxAds24h = engine.maxAds24h
            churnRiskProtectionThreshold = engine.churnRiskProtectionThreshold
            maxIapOffersPerSession = engine.maxIapOffersPerSession
            minMinutesBetweenIapOffers = engine.minMinutesBetweenIapOffers
            maxIapOffers24h = engine.maxIapOffers24h
            maxPurchasesPerSessionBeforeBreak = engine.maxPurchasesPerSessionBeforeBreak
            frustrationStreakThreshold = engine.frustrationStreakThreshold
            maxAssistsPerSession = engine.maxAssistsPerSession
            minLevelForInterstitial = engine.minLevelForInterstitial
            minLevelForInterstitialAfterLoss = engine.minLevelForInterstitialAfterLoss
            minMinutesAfterRewarded = engine.minMinutesAfterRewarded
            skipFirstInterstitialsOnReopen = engine.skipFirstInterstitialsOnReopen
        }

        func restore(into engine: GuardrailEngine) {
            engine.maxInterstitialPerSession = maxInterstitialPerSession
            engine.minMinutesBetweenInterstitial = minMinutesBetweenInterstitial
            engine.maxAds24h = maxAds24h
            engine.churnRiskProtectionThreshold = churnRiskProtectionThreshold
            engine.maxIapOffersPerSession = maxIapOffersPerSession
            engine.minMinutesBetweenIapOffers = minMinutesBetweenIapOffers
            engine.maxIapOffers24h = maxIapOffers24h
            engine.maxPurchasesPerSessionBeforeBreak = maxPurchasesPerSessionBeforeBreak
            engine.frustrationStreakThreshold = frustrationStreakThreshold
            engine.maxAssistsPerSession = maxAssistsPerSession
            engine.minLevelForInterstitial = minLevelForInterstitial
            engine.minLevelForInterstitialAfterLoss = minLevelForInterstitialAfterLoss
            engine.minMinutesAfterRewarded = minMinutesAfterRewarded
            engine.skipFirstInterstitialsOnReopen = skipFirstInterstitialsOnReopen
        }
    }

    /// The process-wide knobs, captured before the first artifact. `FxRates` is absent because it
    /// cannot be read back — see `resetForTest`.
    private struct GlobalKnobs {
        let maxClicksPerWindow = ClickFraudGuard.maxClicksPerWindow
        let windowMs = ClickFraudGuard.windowMs
        let lockoutMs = ClickFraudGuard.lockoutMs
        let holdoutFraction = HoldoutManager.holdoutFraction
        let salt = HoldoutManager.salt
        let rolloutFraction = AdsBrain.shared.rolloutFraction
        let weightImmediateValue = RewardModel.weightImmediateValue
        let weightContinuation = RewardModel.weightContinuation
        let minConfidence = PersonalizationScenario.minConfidence
        let removeAdsProductIds = Entitlements.shared.removeAdsProductIds

        func restore() {
            ClickFraudGuard.maxClicksPerWindow = maxClicksPerWindow
            ClickFraudGuard.windowMs = windowMs
            ClickFraudGuard.lockoutMs = lockoutMs
            HoldoutManager.holdoutFraction = holdoutFraction
            HoldoutManager.salt = salt
            AdsBrain.shared.rolloutFraction = rolloutFraction
            RewardModel.weightImmediateValue = weightImmediateValue
            RewardModel.weightContinuation = weightContinuation
            PersonalizationScenario.minConfidence = minConfidence
            Entitlements.shared.removeAdsProductIds = removeAdsProductIds
        }
    }
}
