import Foundation

/// Deterministic, hash-based bucketing shared by holdout (control group) and staged rollout.
///
/// The same `playerId` always lands in the same bucket across sessions and reinstalls — no
/// storage needed, and no drift versus random-per-call bucketing, which would put a player in
/// treatment on one session and control on the next and corrupt the measurement.
///
/// Uses FNV-1a 32-bit rather than Swift's `String.hashValue`, which is randomized per process
/// launch and therefore cannot agree with the server-side bucket-reconciliation job or with the
/// Android SDK. `bucketWith(_:_:)` is verified byte-for-byte against Android's
/// `HoldoutManager.bucketWith` and the backend's Python reimplementation — see
/// `HoldoutManagerParityTests`.
public enum HoldoutManager {
    private static let bucketCount = 10_000
    private static let fnvOffsetBasis: Int32 = -0x7ee3623b // 2166136261 as signed 32-bit
    private static let fnvPrime: Int32 = 0x0100_0193 // 16777619

    /// Fraction of players held back as control. 0.05 = 5%. 0.0 disables holdout entirely.
    ///
    /// Lock-backed: this is read from `AdsBrain`'s `async` decision paths on arbitrary executors
    /// while a host may tune it from its own thread. The README carried that as a known data race
    /// from Phase 1 until `LockedValue` landed.
    private static let _holdoutFraction = LockedValue(0.05)
    public static var holdoutFraction: Double {
        get { _holdoutFraction.wrapped }
        set { _holdoutFraction.wrapped = newValue }
    }

    /// Hash salt. Changing this re-buckets the entire population — only do it at the start of
    /// a new measurement cycle; changing it mid-cycle breaks comparability of data already
    /// collected, since some players jump groups.
    private static let _salt = LockedValue("brain_holdout_v1")
    public static var salt: String {
        get { _salt.wrapped }
        set { _salt.wrapped = newValue }
    }

    /// Stable bucket in `[0, 10000)` for `playerId` under the current `salt`.
    public static func bucket(_ playerId: String) -> Int {
        bucketWith(salt, playerId)
    }

    /// Bucket under an explicit `namespace`. Two different namespaces produce two
    /// STATISTICALLY INDEPENDENT groupings over the same player set.
    ///
    /// Do not derive one namespace's bucket from another's by phase-shifting
    /// (`(bucket + n) % 10000`) — that's a deterministic function of the same bucket, so the two
    /// groupings stay fully correlated. With a 10% holdout and a 50% rollout, that bug would put
    /// every holdout player outside the rollout, making it impossible to run both experiments at
    /// once. This is why `isInRollout` below uses `"\(salt)|rollout"` as its own namespace.
    public static func bucketWith(_ namespace: String, _ playerId: String) -> Int {
        var hash = fnvOffsetBasis
        let input = "\(namespace)|\(playerId)"
        for unit in input.utf16 {
            hash ^= Int32(unit)
            hash = hash &* fnvPrime
        }
        let unsigned = UInt32(bitPattern: hash)
        return Int(unsigned % UInt32(bucketCount))
    }

    /// Is this player in the control (holdout) group?
    ///
    /// `NaN` guard: `Int(Double.nan)` traps in Swift, and both range checks below are false for
    /// NaN, so a malformed fraction (e.g. sourced from an unparseable remote-config string)
    /// would otherwise reach that conversion and crash the host app. Treat it as "no holdout"
    /// rather than crash — the same fail-safe default as `holdoutFraction <= 0.0`.
    /// Not migrated to `JsonConfigValue.clamped`, on purpose: the three guards below confine
    /// `holdoutFraction` to `(0, 1)`, so `fraction * bucketCount` is provably inside `Int` and
    /// there is no clamp-before-convert hazard to share. This also decides experiment bucketing,
    /// where a rounding difference would silently re-bucket live players for no gain.
    /// A complete snapshot of one player's experiment membership — the input to the
    /// `experiment_exposure` event.
    ///
    /// Taking it changes nothing: bucketing is a pure function of `(salt, playerId)`, so this
    /// records a fact rather than deciding one. `saltVersion` travels with it because changing the
    /// salt re-buckets the whole population, and a row that does not say which salt produced it
    /// cannot be compared against rows from before the change.
    public struct ExposureRecord: Sendable, Equatable {
        public let experimentId: String
        public let bucket: Int
        public let isHoldout: Bool
        public let inRollout: Bool
        public let saltVersion: String
    }

    public static func exposureRecord(
        playerId: String,
        rolloutFraction: Double,
        experimentId: String = "monetization_brain"
    ) -> ExposureRecord {
        ExposureRecord(
            experimentId: experimentId,
            bucket: bucket(playerId),
            isHoldout: isHoldout(playerId),
            inRollout: isInRollout(playerId, fraction: rolloutFraction),
            saltVersion: salt
        )
    }

    public static func isHoldout(_ playerId: String) -> Bool {
        guard holdoutFraction.isFinite else { return false }
        if holdoutFraction <= 0.0 { return false }
        if holdoutFraction >= 1.0 { return true }
        return bucket(playerId) < cutoff(holdoutFraction)
    }

    /// Fraction of the population, as a bucket cut-off.
    ///
    /// ═══ WHY THIS GOES THROUGH `JsonConfigValue.clamped` ═══
    ///
    /// The conversion used to be a bare `Int(fraction * Double(bucketCount))`, and `Int(Double)`
    /// TRAPS — it does not saturate — on anything outside `Int`'s range. That was safe only
    /// because of the three guards above it: finite, `> 0`, `< 1`. Safe-by-argument is a
    /// different thing from safe, and this file's guards are the kind that get reordered by
    /// someone fixing an unrelated edge case; the same trap has now been found in five separate
    /// places in this port. Clamping in `Double` space before converting makes the conversion
    /// correct on its own, so the guards stay for what they say (`>= 1` means EVERYBODY, not
    /// 9999/10000) rather than for what they prevent.
    private static func cutoff(_ fraction: Double) -> Int {
        JsonConfigValue.clamped(fraction * Double(bucketCount), lowerBound: 0, upperBound: bucketCount)
    }

    /// Staged rollout support: is this player in the `fraction` of the population currently
    /// getting the brain enabled? Uses a separate namespace from holdout — see the warning on
    /// `bucketWith` above for why.
    ///
    /// `NaN` guard: same reasoning as `isHoldout` above — a malformed `fraction` must not crash
    /// the host app.
    public static func isInRollout(_ playerId: String, fraction: Double) -> Bool {
        guard fraction.isFinite else { return false }
        if fraction >= 1.0 { return true }
        if fraction <= 0.0 { return false }
        return bucketWith("\(salt)|rollout", playerId) < cutoff(fraction)
    }
}
