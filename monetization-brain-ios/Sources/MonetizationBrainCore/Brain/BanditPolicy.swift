import Foundation

/// Minimal contextual bandit: Thompson Sampling Beta-Bernoulli, keyed by (segmentKey, armId).
/// "Contextual" at the segment-key level — built from decision point + belief-state type — NOT
/// full contextual regression. A deliberate choice per doc 24 §G: start with a segment-keyed
/// MAB, only upgrade to a linear/tree contextual bandit once there's enough traffic per segment.
///
/// No external probability library — a Beta sample is derived from two Gamma draws
/// (Marsaglia-Tsang), Gamma from a Gaussian (Box-Muller), all through an injected
/// `RandomNumberGenerator` rather than a hardcoded global source (a deliberate departure from
/// the Android source, taken for deterministic testability — see the Phase 2 design spec §3).
public final class BanditPolicy: @unchecked Sendable {
    private struct BetaParams {
        var alpha: Double = 1.0
        var beta: Double = 1.0
    }

    private let lock = NSLock()
    private var arms: [String: BetaParams] = [:]

    /// Exploration floor (epsilon floor) — REQUIRED infrastructure, not a tuning knob.
    ///
    /// Reasoning: once Thompson Sampling has converged, it picks the same arm nearly 100% of the
    /// time and the logged propensity approaches 1.0. At that point **every off-policy
    /// evaluation becomes useless** — with no overlap, the importance weight of any other policy
    /// is either 0 or infinite (doc 31 §5, doc 28 §H).
    ///
    /// With probability `explorationFloor`, pick uniformly at random among the valid arms. The
    /// final propensity is therefore always ≥ `explorationFloor / n_arms` > 0, keeping OPE always
    /// feasible.
    ///
    /// 0.02–0.05 is the usual range. Setting 0.0 disables OPE — only do that knowing the
    /// consequence.
    ///
    /// Lock-guarded rather than a plain stored property: `DecisionEngine.applyPolicy` writes it
    /// from whatever thread delivered the remote-config payload while `selectArm` reads it on a
    /// decision path. `selectArm` takes this lock too, but releases it before reading — so this
    /// accessor never nests inside it.
    public var explorationFloor: Double {
        get { lock.lock(); defer { lock.unlock() }; return _explorationFloor }
        set { lock.lock(); defer { lock.unlock() }; _explorationFloor = newValue }
    }

    private var _explorationFloor: Double = 0.03

    public init() {}

    private func key(_ segmentKey: String, _ armId: String) -> String {
        "\(segmentKey)|\(armId)"
    }

    /// WEIGHTED posterior update by belief (doc 33 §9).
    ///
    /// `weight` is b_t(k) — the posterior probability the player belongs to latent type k. Hard-
    /// assigning to `mostLikelyType()` and only updating that segment is wrong: when belief
    /// shifts, history learned in the old segment is wasted, and the new segment restarts from
    /// prior. A soft (EM-style) update distributes evidence across every segment in proportion to
    /// how much it's trusted.
    ///
    /// Beta-Bernoulli naturally accepts fractional updates — alpha/beta are sufficient
    /// statistics, and adding a real number preserves the correct functional form.
    public func update(segmentKey: String, armId: String, reward01: Double, weight: Double = 1.0) {
        lock.lock()
        defer { lock.unlock() }
        updateLocked(segmentKey: segmentKey, armId: armId, reward01: reward01, weight: weight)
    }

    /// Applies a whole feedback batch and serializes it in ONE acquisition.
    ///
    /// A caller that loops `update(...)` and then calls `serialize()` takes this lock once per
    /// segment plus once more — so a concurrent feedback call interleaves, and the string that gets
    /// persisted contains some of its arms and some of theirs. These are Beta posteriors being
    /// accumulated, so a snapshot torn across two batches is not a slightly-stale value; it is a
    /// posterior neither caller computed, written down as if it were.
    ///
    /// `mintSequence` runs inside the same acquisition so the write's sequence orders by the state
    /// it contains rather than by when it was issued — see `BrainStorage.nextWriteSequence`.
    public func updateBatchAndSerialize(
        _ updates: [(segmentKey: String, armId: String, reward01: Double, weight: Double)],
        mintSequence: () -> UInt64
    ) -> (serialized: String, sequence: UInt64) {
        lock.lock()
        defer { lock.unlock() }
        for u in updates {
            updateLocked(segmentKey: u.segmentKey, armId: u.armId, reward01: u.reward01, weight: u.weight)
        }
        return (serializeLocked(), mintSequence())
    }

    /// MUST be called with `lock` held.
    private func updateLocked(segmentKey: String, armId: String, reward01: Double, weight: Double) {
        guard weight > 0.0 else { return }
        var p = arms[key(segmentKey, armId)] ?? BetaParams()
        let r = min(max(reward01, 0.0), 1.0)
        let w = min(max(weight, 0.0), 1.0)
        p.alpha += w * r
        p.beta += w * (1.0 - r)
        arms[key(segmentKey, armId)] = p
    }

    /// Thompson Sampling over the MIXED belief — samples both sources of uncertainty at once:
    /// (1) which latent type the player is, (2) what each arm's true value is.
    ///
    /// Each Monte Carlo round: draw one segment weighted by belief, then draw a Beta for every
    /// arm FROM THAT SAME segment (one segment per round — a player really is only one type, we
    /// just don't know which). Win frequency across rounds gives the selection probability under
    /// the mixture policy — exactly the propensity that belongs in the decision log for OPE
    /// (doc 24 §K, doc 28 §H).
    ///
    /// ═══ WHY THE GENERATOR IS A GENERIC PARAMETER AND NOT `RandomNumberGenerator` ═══
    ///
    /// It used to be the existential. Every `Double.random(in:using:)` through one is a dynamic
    /// dispatch the compiler cannot inline, and this method makes roughly 2 200 of them per
    /// decision (200 samples × 5 arms × two gamma draws each, plus the segment and final draws).
    /// Measured: 0.63 µs PER DRAW, which was most of `DecisionEngine.decide`'s 3.46 ms. With a
    /// concrete generator the generic specialises and the draws inline.
    ///
    /// The cost is a source-breaking signature: a caller holding `var rng: RandomNumberGenerator`
    /// must hold the concrete type instead. That is a better shape for callers anyway — a seeded
    /// generator in a test is always a concrete type — and this API is one `DecisionEngine` owns;
    /// its own doc comment already says nothing outside the package should call `BanditPolicy`.
    ///
    /// - Parameter segmentWeights: segmentKey → belief weight; should sum to roughly 1.0.
    public func selectArm<Generator: RandomNumberGenerator>(
        segmentWeights: [String: Double],
        candidateArmIds: [String],
        mcSamples: Int = 200,
        penalties: [String: Double] = [:],
        randomSource: inout Generator
    ) -> (armId: String, propensity: Double) {
        precondition(!candidateArmIds.isEmpty, "candidateArmIds must not be empty")
        precondition(!segmentWeights.isEmpty, "segmentWeights must not be empty")
        if candidateArmIds.count == 1 {
            return (candidateArmIds[0], 1.0)
        }

        // Order-preserving de-dup. NOT full behavioral parity with Android: Kotlin's equivalent
        // doesn't dedupe either — its per-round loop (`for (armId in candidateArmIds)`) samples
        // a duplicated arm id independently each time it appears, which silently OVER-WEIGHTS
        // that arm's win rate rather than treating duplicates neutrally. Swift's dedup removes
        // that effect entirely (arguably more correct) — the real, load-bearing reason for it is
        // that Dictionary(uniqueKeysWithValues:) below would otherwise crash (a real precondition
        // failure, in release builds too) on a duplicate, so a remote-config-sourced candidate
        // list can't turn silently-biased Android input into a hard iOS crash.
        var seenArmIds = Set<String>()
        let candidateArmIds = candidateArmIds.filter { seenArmIds.insert($0).inserted }
        if candidateArmIds.count == 1 {
            return (candidateArmIds[0], 1.0)
        }

        let segments = Array(segmentWeights.keys)
        var cumulative = [Double](repeating: 0, count: segments.count)
        var running = 0.0
        for i in segments.indices {
            running += max(segmentWeights[segments[i]] ?? 0.0, 0.0)
            cumulative[i] = running
        }
        precondition(running > 0.0, "total segment weight must be positive")

        lock.lock()
        // ═══ THE TABLE IS READ ONCE, NOT ONCE PER SAMPLE ═══
        //
        // This loop used to call `key(segmentKey, armId)` TWICE per arm per round — building a
        // "segment|arm" String and hashing it, to read a value and then write the identical value
        // back. At the default 200 samples over 5 candidates that is 2 000 string allocations per
        // decision, and it measured 3.46 ms per `DecisionEngine.decide` call.
        //
        // Nothing those lookups read can change while the lock is held, so the whole
        // (segment × arm) block is materialised once up front — including the write-back, which
        // is not dead code: it is what puts a default `BetaParams` into `arms` the first time a
        // pair is seen, so `serialize()` persists it. The sampling itself is untouched: the loop
        // structure, the order of `randomSource` draws and the comparison are identical, which is
        // what keeps the seeded-RNG tests meaningful as a check that this changed nothing.
        var paramsBySegment = [[BetaParams]](repeating: [], count: segments.count)
        for (segmentIndex, segmentKey) in segments.enumerated() {
            var row = [BetaParams]()
            row.reserveCapacity(candidateArmIds.count)
            for armId in candidateArmIds {
                let armKey = key(segmentKey, armId)
                let existing = arms[armKey] ?? BetaParams()
                arms[armKey] = existing
                row.append(existing)
            }
            paramsBySegment[segmentIndex] = row
        }
        let penaltyByArm = candidateArmIds.map { penalties[$0] ?? 0.0 }

        var winCounts = [Int](repeating: 0, count: candidateArmIds.count)
        for _ in 0..<mcSamples {
            let u = Double.random(in: 0..<1, using: &randomSource) * running
            let segIdx = cumulative.firstIndex { $0 >= u } ?? (segments.count - 1)
            let params = paramsBySegment[segIdx]

            var bestArm = 0
            var bestSample = -Double.greatestFiniteMagnitude
            for armIndex in candidateArmIds.indices {
                let p = params[armIndex]
                let sample = Self.sampleBeta(alpha: p.alpha, beta: p.beta, randomSource: &randomSource)
                    - penaltyByArm[armIndex]
                if sample > bestSample {
                    bestSample = sample
                    bestArm = armIndex
                }
            }
            winCounts[bestArm] += 1
        }
        lock.unlock()

        let eps = min(max(explorationFloor, 0.0), 1.0)
        let n = Double(candidateArmIds.count)

        // Mix with the uniform distribution: p_final(a) = (1-eps)*p_TS(a) + eps/n.
        // Draw from the MIXED distribution — if the arm were chosen by raw TS and only the
        // logged probability were mixed afterward, the log would misrepresent what was drawn.
        let mixed: [String: Double] = Dictionary(uniqueKeysWithValues: candidateArmIds.enumerated().map {
            (armIndex, armId) in
            let pts = Double(winCounts[armIndex]) / Double(mcSamples)
            return (armId, (1.0 - eps) * pts + eps / n)
        })

        var draw = Double.random(in: 0..<1, using: &randomSource)
        var chosenArm = candidateArmIds.last!
        for armId in candidateArmIds {
            draw -= mixed[armId] ?? 0.0
            if draw <= 0.0 {
                chosenArm = armId
                break
            }
        }
        return (chosenArm, mixed[chosenArm] ?? (eps / n))
    }

    /// Same as above but using the system's default random source — the production entry point.
    /// A separate overload rather than a default value because Swift doesn't allow default
    /// values for `inout` parameters.
    public func selectArm(
        segmentWeights: [String: Double],
        candidateArmIds: [String],
        mcSamples: Int = 200,
        penalties: [String: Double] = [:]
    ) -> (armId: String, propensity: Double) {
        // CONCRETE type, not `RandomNumberGenerator` — see the generic parameter on the overload
        // above. Through an existential, every single `Double.random(in:using:)` is a dynamic
        // dispatch, and this path makes ~2 200 of them per decision.
        var rng = SystemRandomNumberGenerator()
        return selectArm(
            segmentWeights: segmentWeights,
            candidateArmIds: candidateArmIds,
            mcSamples: mcSamples,
            penalties: penalties,
            randomSource: &rng
        )
    }

    /// Serializes the full posterior for persistence: "segmentKey|armId=alpha,beta;..."
    ///
    /// Without persistence the bandit forgets everything on every app restart while belief is
    /// saved — that phase mismatch makes results non-reproducible and hard to diagnose.
    public func serialize() -> String {
        lock.lock()
        defer { lock.unlock() }
        return serializeLocked()
    }

    /// MUST be called with `lock` held.
    private func serializeLocked() -> String {
        arms.map { "\($0.key)=\($0.value.alpha),\($0.value.beta)" }.joined(separator: ";")
    }

    /// Restores the posterior. Corrupt data drops that record rather than throwing (fail-safe).
    public func restore(_ raw: String?) {
        guard let raw, !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        lock.lock()
        defer { lock.unlock() }
        for entry in raw.split(separator: ";") {
            guard let eqIndex = entry.lastIndex(of: "=") else { continue }
            let armKey = String(entry[entry.startIndex..<eqIndex])
            let numsPart = entry[entry.index(after: eqIndex)...]
            let nums = numsPart.split(separator: ",")
            // ═══ `> 0` LETS INFINITY THROUGH, AND THE RESULT IS WORSE THAN A CRASH ═══
            //
            // `inf > 0` is true, so a corrupted record used to install `BetaParams(inf, inf)`.
            // Nothing then fails loudly: `sampleGamma(shape: inf)` returns infinity, `sampleBeta`
            // divides `inf / (inf + inf)` — its own guard only catches a NON-POSITIVE sum — and
            // hands back NaN. In the Monte Carlo loop `if sample > bestSample` is FALSE for NaN
            // forever, so the winner stays whatever arm was first in the list: the bandit silently
            // stops learning AND reports a propensity near 1.0 for that arm, which is exactly the
            // number off-policy evaluation trusts. A crash would at least be noticed.
            guard nums.count == 2,
                  let a = Double(nums[0]), let b = Double(nums[1]),
                  a > 0.0, b > 0.0, a.isFinite, b.isFinite else { continue }
            arms[armKey] = BetaParams(alpha: a, beta: b)
        }
    }

    /// Observations accumulated for one arm (alpha+beta-2, subtracting the Beta(1,1) prior).
    public func observationCount(segmentKey: String, armId: String) -> Double {
        lock.lock()
        defer { lock.unlock() }
        guard let p = arms[key(segmentKey, armId)] else { return 0.0 }
        return max(p.alpha + p.beta - 2.0, 0.0)
    }

    // MARK: - Beta sampling via two Gamma variables

    private static func sampleBeta<Generator: RandomNumberGenerator>(alpha: Double, beta: Double, randomSource: inout Generator) -> Double {
        let x = sampleGamma(shape: alpha, randomSource: &randomSource)
        let y = sampleGamma(shape: beta, randomSource: &randomSource)
        return (x + y) <= 0.0 ? 0.5 : x / (x + y)
    }

    /// Marsaglia-Tsang method, valid for shape > 0.
    private static func sampleGamma<Generator: RandomNumberGenerator>(shape: Double, randomSource: inout Generator) -> Double {
        if shape < 1.0 {
            let u = Double.random(in: 0..<1, using: &randomSource)
            return sampleGamma(shape: 1.0 + shape, randomSource: &randomSource) * pow(u, 1.0 / shape)
        }
        let d = shape - 1.0 / 3.0
        let c = 1.0 / (9.0 * d).squareRoot()
        while true {
            var x: Double
            var v: Double
            repeat {
                x = gaussian(randomSource: &randomSource)
                v = 1.0 + c * x
            } while v <= 0.0
            v = v * v * v
            let u = Double.random(in: 0..<1, using: &randomSource)
            if u < 1.0 - 0.0331 * x * x * x * x { return d * v }
            if log(u) < 0.5 * x * x + d * (1.0 - v + log(v)) { return d * v }
        }
    }

    private static func gaussian<Generator: RandomNumberGenerator>(randomSource: inout Generator) -> Double {
        // Box-Muller
        let u1 = max(Double.random(in: 0..<1, using: &randomSource), 1e-12)
        let u2 = Double.random(in: 0..<1, using: &randomSource)
        return (-2.0 * log(u1)).squareRoot() * cos(2.0 * Double.pi * u2)
    }
}
