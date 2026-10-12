import Foundation

/// "Is this player's experience getting worse while the revenue numbers go up?" — the 4th North
/// Star Metric (Android `com.app.plugin.brain.ComfortIndex`; design spec §1).
///
/// ═══ WHY A COUNTERWEIGHT METRIC EXISTS AT ALL ═══
///
/// ARPDAU, revenue and subscription rate are all trivially improvable by making the game worse:
/// more ads, more pressure, more friction. Each of those moves shows up as a win on the other
/// three metrics and as nothing at all until the retention damage lands, months later. This
/// metric is the number that moves in the opposite direction while that is happening.
///
/// ═══ STATELESS, STATIC, PURE (§1) ═══
///
/// No instance, no lock, no persistence, no event. `compute` is a function of its arguments —
/// calling it a thousand times changes nothing and costs nothing but arithmetic. That is what
/// makes it safe to call from a session-end hook, a debug screen and a test in the same process.
///
/// ═══ ⚠️ ONLY 2 OF 5 COMPONENTS HAVE A DATA SOURCE — SEE `isTrustworthy` (§2.4) ═══
///
/// ═══ ⚠️ IMPOSSIBLE INPUTS READ AS BEST CASE, NOT WORST (§2.5) ═══
///
/// A negative `adImpressions24h` clamps to "no ad fatigue at all", and a negative
/// `daysSinceLastSession` lands in the "active today" bucket. Both are the generous direction, so
/// a caller feeding this garbage gets a metric that says everything is fine. The clamping itself
/// is kept (the output range matters and there is no better answer for an impossible input), but
/// the direction of the failure mode is written down here and asserted in tests, because a
/// metric that exists to catch player harm must not fail silently toward "no harm".
///
/// `componentsAvailable` names what actually went into the number, so the gap is visible in the
/// result and in the event payload rather than implied by a low score.
public enum ComfortIndex {
    // MARK: - Components

    /// The two components this SDK can actually measure today.
    ///
    /// Order is the order they enter the formula, and it is load-bearing for the event payload:
    /// `brain_comfort` carries these names verbatim, so a backend that groups on them needs them
    /// stable.
    public static let componentsWithADataSource = ["ad_fatigue", "churn_proxy"]

    /// The three components the metric is DEFINED over but has no data source for (§1, §3.1).
    ///
    /// They stay named rather than silently dropped so that a reader of a `brain_comfort` row can
    /// see WHICH parts of player comfort the number does not describe — a 0.4 coverage figure on
    /// its own says "something is missing", not "sentiment is missing".
    ///
    /// ═══ THESE STRINGS ARE A CROSS-PLATFORM WIRE CONTRACT, NOT LABELS ═══
    ///
    /// They are sent verbatim in `brain_comfort.components_missing` and the backend ingests them as
    /// a string. They therefore match Android's `ComfortIndex.kt` EXACTLY — if the two platforms
    /// name the same absent component differently, one dashboard becomes two, split by a rename
    /// nobody made on purpose.
    ///
    /// Each is absent for a structural reason, which is why the names describe the missing DATA
    /// rather than the missing metric: `organic_purchase_probability` needs a causal model,
    /// `refund_rate` needs refund tracking this SDK does not do, and
    /// `post_purchase_retention_delta` needs post-purchase retention this SDK does not follow.
    /// None of them is a field someone forgot to populate.
    public static let componentsWithoutADataSource = [
        "organic_purchase_probability",
        "refund_rate",
        "post_purchase_retention_delta"
    ]

    /// The denominator of `coverage`. Deliberately NOT `componentsWithADataSource.count` — the
    /// whole point is that the metric is defined over five components and can only fill two.
    public static let totalComponents = 5

    /// Coverage a caller would need before `ComfortResult.isTrustworthy` reports `true`. Ported
    /// from Android unchanged, and structurally unreachable — see `ComfortResult.isTrustworthy`.
    public static let trustworthyCoverageThreshold = 0.6

    // MARK: - Compute

    /// The faithful port of Android's formula (§3.1):
    ///
    /// ```
    /// fatigue    = clamp(adImpressions24h / max(adCapPerDay, 1), 0...1)
    /// churnProxy = daysSinceLastSession >= 7 ? 0.9
    ///            : daysSinceLastSession >= 3 ? 0.6
    ///            : daysSinceLastSession >= 1 ? 0.3 : 0.1
    /// value      = ((1 - fatigue) + (1 - churnProxy)) / 2
    /// ```
    ///
    /// ═══ EQUAL WEIGHTS ARE NOT A CALIBRATED MODEL ═══
    ///
    /// Android's own doc says so, and this port does not improve on it: there is no data behind
    /// the 50/50 split, and inventing a different split here would be equally unjustified while
    /// looking more deliberate. Recorded as a known limitation rather than dressed up as a model.
    ///
    /// ═══ GUARDRAIL BLOCKS ARE DELIBERATELY NOT IN THE VALUE ═══
    ///
    /// How often a guardrail fired measures POLICY health ("is the policy layer straining against
    /// the rails?"), not player experience — a blocked ad is an ad the player never saw, so it
    /// cannot have hurt them. Folding it in would make the metric improve when the policy gets
    /// more aggressive, which is backwards. It is exposed separately through
    /// `policyAggressiveness`, and a test asserts guardrails firing does not move `value`.
    ///
    /// - Parameter adCapPerDay: the daily impression ceiling in force — `GuardrailEngine.maxAds24h`
    ///   at the call site in `AdsBrain`. Values below 1 are read as 1 rather than rejected: this
    ///   is a metric, and a divide-by-zero or a trap here would take down a session-end hook.
    public static func compute(state: PlayerState, adCapPerDay: Int) -> ComfortResult {
        // `Double(Int)` never traps in either direction, so `Int.min`/`Int.max` are safe inputs;
        // the clamp is what bounds the result, exactly as §2.5 describes.
        let cap = Double(max(adCapPerDay, 1))
        let fatigue = min(max(Double(state.adImpressions24h) / cap, 0.0), 1.0)
        let churnProxy = churnProxyFor(daysSinceLastSession: state.daysSinceLastSession)
        let value = ((1.0 - fatigue) + (1.0 - churnProxy)) / 2.0
        return ComfortResult(
            value: value,
            coverage: Double(componentsWithADataSource.count) / Double(totalComponents),
            componentsAvailable: componentsWithADataSource,
            componentsMissing: componentsWithoutADataSource
        )
    }

    /// Bucketed, not continuous — matching Android. The buckets are `>= 7`, `>= 3`, `>= 1` and
    /// "everything else", so a NEGATIVE day count falls through to the best-case bucket (§2.5).
    private static func churnProxyFor(daysSinceLastSession days: Int) -> Double {
        if days >= 7 { return 0.9 }
        if days >= 3 { return 0.6 }
        if days >= 1 { return 0.3 }
        return 0.1
    }

    // MARK: - Policy aggressiveness

    /// How often each guardrail actually blocked something — read straight through to
    /// `GuardrailEngine`'s prevented counts.
    ///
    /// Kept OUT of `ComfortResult.value` on purpose (see `compute`). Reported alongside it because
    /// the two answer complementary questions: `value` says how the player is doing, this says how
    /// hard the policy layer is pressing against its own rails to keep them there.
    public static func policyAggressiveness(engine: GuardrailEngine) -> [String: Int] {
        engine.allPreventedCounts()
    }

    /// `policyAggressiveness` as the `key=value;…` string `brain_comfort` carries (§4).
    ///
    /// SORTED, because `[String: Int]` has no guaranteed iteration order in Swift: an unsorted
    /// render would produce a different string for the same counts on different runs, which is
    /// both untestable and useless to anything downstream trying to diff two rows.
    public static func describe(policyAggressiveness counts: [String: Int]) -> String {
        counts.keys.sorted().map { "\($0)=\(counts[$0] ?? 0)" }.joined(separator: ";")
    }
}

/// The output of `ComfortIndex.compute` — the number, how much of the metric it actually covers,
/// and which components are behind each.
public struct ComfortResult: Sendable, Equatable {
    /// `0...1`, higher is better. `0.95` is the best a player can score (no ad fatigue, active
    /// today) and `0.05` the worst — the endpoints are not 1 and 0 because the churn proxy is
    /// bucketed and its buckets stop at 0.1 and 0.9.
    public let value: Double

    /// Fraction of the metric's five defined components that had a data source: `2 / 5` today.
    public let coverage: Double

    /// Names of the components that actually went into `value`, in formula order.
    public let componentsAvailable: [String]

    /// Names of the components the metric is defined over but could not compute (§3.1).
    public let componentsMissing: [String]

    public init(value: Double, coverage: Double, componentsAvailable: [String], componentsMissing: [String]) {
        self.value = value
        self.coverage = coverage
        self.componentsAvailable = componentsAvailable
        self.componentsMissing = componentsMissing
    }

    /// ═══ ⚠️ THIS IS PERMANENTLY `false`, AND THAT IS THE POINT (§2.4) ═══
    ///
    /// `coverage` is fixed at `2/5 = 0.4` because only two of the five components have a data
    /// source, and the threshold is `0.6`. So this predicate CANNOT return `true` today, and a
    /// caller gating on it — "only act on the comfort index once it is trustworthy" — waits for
    /// a `true` that cannot arrive. Android ships exactly this, silently.
    ///
    /// It is ported rather than deleted, because deleting it would hide the same gap behind a
    /// missing API instead of a dead one, and a future third component is what should revive it.
    /// `ComfortIndexTests.test_isTrustworthyIsStructurallyFalseUntilAThirdComponentLands` fails
    /// the moment that third component lands, which turns a silent behavior change into a
    /// deliberate decision. A dead predicate that announces it is dead is honest; one that looks
    /// alive is a trap.
    public var isTrustworthy: Bool { coverage >= ComfortIndex.trustworthyCoverageThreshold }

    /// Human-readable summary for a debug screen or a log line — never for control flow.
    ///
    /// States the trustworthiness verdict outright rather than leaving the reader to compare
    /// `coverage` against a threshold they would have to go find.
    public func describe() -> String {
        let verdict = isTrustworthy ? "trustworthy" : "NOT trustworthy"
        return String(
            format: "comfort %.2f (coverage %.2f of %d components — %@; missing: %@)",
            value,
            coverage,
            ComfortIndex.totalComponents,
            verdict,
            componentsMissing.joined(separator: ", ")
        )
    }
}
