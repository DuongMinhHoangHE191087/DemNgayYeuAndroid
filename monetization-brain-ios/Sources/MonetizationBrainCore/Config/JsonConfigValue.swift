import Foundation

/// Shared primitives for reading `JSONSerialization` output inside this package's remote-config
/// parsers (`PurchaseRouter.apply`, `PaywallManager.apply`, `SubscriptionLifecycle.apply` and
/// `RecurringGrants.apply`).
///
/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// WHY THIS IS SHARED, WHEN TEST FAKES IN THIS PACKAGE ARE DELIBERATELY NOT
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// This package redeclares TEST fakes per file on purpose (there is no shared test-support
/// target). That convention exists to keep one test file's fake from silently changing another
/// file's behavior — it is about test isolation, and it does not extend to production parsing
/// logic, where the opposite risk applies: `isJsonBoolean` below encodes a security-load-bearing
/// subtlety about CoreFoundation bridging that no reader re-derives from first principles, and
/// two copies of it are two chances for one to be "simplified" back to `as? Bool` by someone who
/// does not know why it is written the long way. A fail-open hole closed in one parser and
/// reopened in another is exactly the failure this file prevents.
///
/// The concrete evidence that it needs sharing: the identical hole was found in `PurchaseRouter`
/// *after* `SubscriptionLifecycle` had already closed it privately, and `PaywallManager`'s
/// `parseWeight` turned out to need the same discriminator from the other direction
/// (`true as? Int` == `1`). Three parsers, one subtlety.
///
/// MIGRATION NOTE: `SubscriptionLifecycle` has been switched over — its private copies of
/// `isJsonBoolean`/`parseFlag` are gone, and the numeric clamp it hard-coded now runs through
/// `clamped(_:lowerBound:upperBound:)` below. The remaining acceptance rules it does NOT share
/// are spelled out on `SubscriptionLifecycle.parseOpenCount`: that parser rejects strings and
/// accepts non-positive values, so it cannot use `numeric` without changing what its config
/// accepts.
///
/// `PaywallManager.clampWeight` now delegates its CLAMP here but keeps its own non-finite guard:
/// this helper returns the upper bound for `+inf`, which is right for a quantity and wrong for a
/// share of an A/B split, where the safe reading of an unintelligible weight is "never select this
/// variant". Reachable via a numeric string, since `Double("inf")` is `+infinity`.
///
/// `HoldoutManager.isHoldout`/`isInRollout` are deliberately NOT migrated, and this is not a
/// to-do. Their `Int(fraction * bucketCount)` conversion is already preceded by guards that
/// confine `fraction` to the open interval `(0, 1)`, so the product is provably inside `Int` and
/// there is no trap to close — the duplication is apparent, not real. They also decide experiment
/// bucketing, so a rounding difference would silently re-bucket live players. Nothing to gain,
/// something to lose.
enum JsonConfigValue {
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// `as? Bool` CANNOT TELL `true` FROM `1` — AND GETTING IT WRONG FAILS *OPEN*
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// `JSONSerialization` hands every JSON number back as an `NSNumber`, and Swift's
    /// value-preserving bridging makes `1 as? Bool` succeed (returning `true`) *and*
    /// `true as? Int` succeed (returning `1`). So neither `as? Bool` nor `is Bool` can tell
    /// `{"enabled": true}` from `{"enabled": 1}`, and no numeric parse can tell `{"weight": 10}`
    /// from `{"weight": true}`.
    ///
    /// That matters because the mistake is silent and points the wrong way: a remote config that
    /// ships `1` where a boolean belongs would switch a feature ON with nothing in the apply
    /// report to say the value was never a boolean at all.
    ///
    /// The underlying CoreFoundation type is the only honest discriminator: a JSON boolean
    /// bridges as `__NSCFBoolean` (a `CFBoolean`), a JSON number as `__NSCFNumber` (never one).
    static func isJsonBoolean(_ raw: Any) -> Bool {
        guard let number = raw as? NSNumber else { return false }
        return CFGetTypeID(number) == CFBooleanGetTypeID()
    }

    /// An optional config flag, read strictly, with an explicit fail-safe default:
    ///
    /// - key ABSENT -> `defaultValue`, no note. Leaving an optional flag unset is a legitimate
    ///   operator choice, not a mistake to warn about.
    /// - key present as a REAL JSON boolean -> that value.
    /// - key present as anything else (`1`, `0`, `"true"`, `[]`) -> `defaultValue` AND a
    ///   diagnostic note. Never silently — including when the default happens to match what the
    ///   operator meant, because they still need to learn their config does not say what they
    ///   think it says. This is the rule design spec §3.8 states for this package.
    static func flag(
        _ raw: Any?,
        name: String,
        defaultingTo defaultValue: Bool,
        notes: inout [String]
    ) -> Bool {
        guard let raw else { return defaultValue }
        if isJsonBoolean(raw), let flag = raw as? Bool { return flag }
        notes.append("'\(name)' is not a boolean -> defaulting to \(defaultValue)")
        return defaultValue
    }

    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// EVERY NUMERIC IS CLAMPED IN `Double` SPACE, BEFORE ANY INTEGER CONVERSION
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// `Int(someDouble)`/`Int64(someDouble)` TRAP when the value exceeds the integer's range, and
    /// a remote config is the one input an operator can make say anything: `1e300` and a 20-digit
    /// literal both decode ONLY as `Double` (`as? Int` returns `nil` for each — the second arrives
    /// as an `NSDecimalNumber`), so a "clamp" written after the cast never runs. A host app
    /// crashing on config load is strictly worse than a clamped value, and this exact defect was a
    /// Critical finding in two separate phases of this project.
    ///
    /// `isFinite` is NOT protection: `1e20` is perfectly finite and still 10× too large for `Int`.
    /// The bound has to be applied while the value is still a `Double`.
    ///
    /// This is the one place that rule is implemented, so that a parser needing only the clamp —
    /// and not `numeric`'s acceptance rules — still cannot get the order wrong.
    ///
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// ...AND THE BOUND ITSELF HAS TO BE `Double`-REPRESENTABLE
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// Clamping in `Double` space is only safe if the CEILING survives the trip into `Double`.
    /// Above 2^53 the two types stop lining up, and at the very top they break outright:
    /// `Double(Int64.max)` rounds UP to 2^63, a value `Int64` cannot represent, so
    /// `Int64(Double(Int64.max))` traps. A clamp written as `Int64(min(value, Double(upperBound)))`
    /// therefore traps for EVERY input at or above the bound — `1e300`, or the bound itself — which
    /// is exactly the crash the clamp exists to prevent, moved one line down.
    ///
    /// `Int64.max` is also the most natural "no bound at all" an author reaches for, and further
    /// parsers are expected to migrate onto these helpers. So the ceiling is capped HERE rather
    /// than left to every caller to
    /// remember: callers may pass `Int64.max`/`Int.max` freely. (`BattlePass.maxXp` documents
    /// choosing `Int64.max / 2` partly to dodge this trap; that reason is now obsolete, and the
    /// halved ceiling stands on its own remaining merit — leaving host apps overflow headroom.)
    static func clamped(_ value: Double, lowerBound: Int, upperBound: Int) -> Int {
        // One implementation, so the two overloads cannot drift apart on the subtlety above. The
        // result is inside `lowerBound...upperBound`, both `Int`s, so narrowing back is exact.
        Int(clamped(value, lowerBound: Int64(lowerBound), upperBound: Int64(upperBound)))
    }

    /// `Int64` counterpart of `clamped(_:lowerBound:upperBound:)` — same rule, same reason.
    ///
    /// Out-of-range inputs land EXACTLY on the bound the caller named, `Int64.max` included: the
    /// comparison is done in `Double` space against a ceiling that converts losslessly, and the
    /// bound is then returned as an integer rather than converted back out of a `Double` that may
    /// have rounded past it. (The previous implementation claimed a second integer clamp achieved
    /// this. It could not: the trap fired first, on the conversion.)
    ///
    /// `NaN` returns `lowerBound`. It carries no magnitude, so there is nothing to clamp toward,
    /// and the smallest value the caller said it would accept is the fail-safe direction — the same
    /// direction `numeric` takes when it drops a non-finite value instead of honouring it. `+inf`
    /// clamps to `upperBound` and `-inf` to `lowerBound`, which need no special case.
    ///
    /// Truncation is toward zero, unchanged: `clamped(3.9, 0, 10) == 3`.
    static func clamped(_ value: Double, lowerBound: Int64, upperBound: Int64) -> Int64 {
        guard !value.isNaN else { return lowerBound }
        // The largest `Int64` that is exactly representable as a `Double`: 2^63 - 2^10. Anything
        // larger rounds, and `Int64.max` rounds the wrong way.
        let maxInt64ExactlyRepresentableAsDouble: Int64 = 9_223_372_036_854_774_784
        let convertibleCeiling = Swift.min(upperBound, maxInt64ExactlyRepresentableAsDouble)
        guard value < Double(convertibleCeiling) else { return upperBound }
        guard value > Double(lowerBound) else { return lowerBound }
        return Swift.min(Swift.max(Int64(value), lowerBound), upperBound)
    }

    /// A required, strictly positive numeric config field, read as a `Double` so that the caller
    /// can clamp it before converting (see `clamped(_:lowerBound:upperBound:)`).
    ///
    /// Accepts a JSON number or a numeric STRING (`"24"`), because operators hand-write these
    /// files. Rejects, with a diagnostic note in every case:
    ///
    /// - absent -> `nil`.
    /// - a JSON boolean -> `nil`. `true as? Int` succeeds and returns `1`, so without this guard
    ///   `{"period_hours": true}` would become a real, live one-hour grant line. See
    ///   `isJsonBoolean`.
    /// - non-finite (a `"nan"`/`"inf"` string) or non-positive -> `nil`. Unlike an absurdly large
    ///   number, these carry no intent to honour, so they are DROPPED rather than clamped.
    ///
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// `allowingNonPositive` — FOR FIELDS WHERE `0` IS A REAL OPERATOR CHOICE
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// The strictly-positive rule is right for the fields it was written for: a `period_hours` or
    /// `reward_amount` of `0` is nonsense, and honouring it would create a zero-period grant line.
    /// It is WRONG for a knob whose own documentation says "0 = OFF" — `GuardrailEngine`'s
    /// `maxIapOffers24h`, `minLevelForInterstitial`, `skipFirstInterstitialsOnReopen` and
    /// `maxAssistsPerSession` are all in that family, and `BrainPolicy` must be able to turn them
    /// off from an artifact. Dropping `0` there would silently ignore a legitimate instruction.
    ///
    /// The flag is opt-in so no existing parser's acceptance rules change. With it set, a
    /// negative value is CLAMPED by the caller's lower bound rather than dropped; the
    /// non-finite guard is unaffected, because `NaN`/`inf` still carry no intent to honour.
    /// (`SubscriptionLifecycle.parseOpenCount` still does not use this function — it also accepts
    /// shapes this one rejects; see its own doc comment.)
    static func numeric(
        _ raw: Any?,
        field: String,
        label: String,
        allowingNonPositive: Bool = false,
        notes: inout [String]
    ) -> Double? {
        guard let raw else {
            notes.append("\(label) is missing '\(field)' -> dropped")
            return nil
        }
        // `true as? Int` succeeds and returns `1`, so without this guard `{"period_hours": true}`
        // would become a real, live one-hour grant line. See `isJsonBoolean`.
        if !isJsonBoolean(raw) {
            var value: Double?
            if let intValue = raw as? Int { value = Double(intValue) }
            else if let doubleValue = raw as? Double { value = doubleValue }
            else if let stringValue = raw as? String { value = Double(stringValue) }
            if let value, value.isFinite, allowingNonPositive || value > 0 { return value }
            if value != nil {
                let requirement = allowingNonPositive ? "a finite number" : "a finite positive number"
                notes.append("\(label): '\(field)' is not \(requirement) -> dropped")
                return nil
            }
        }
        notes.append("\(label): '\(field)' is not a number -> dropped")
        return nil
    }

    /// `numeric` plus the clamp — a required, strictly positive config integer, bounded to
    /// `lowerBound...upperBound` without ever handing an out-of-range `Double` to `Int.init`.
    static func clampedInt(
        _ raw: Any?,
        field: String,
        label: String,
        lowerBound: Int,
        upperBound: Int,
        allowingNonPositive: Bool = false,
        notes: inout [String]
    ) -> Int? {
        guard let value = numeric(
            raw, field: field, label: label, allowingNonPositive: allowingNonPositive, notes: &notes
        ) else { return nil }
        return clamped(value, lowerBound: lowerBound, upperBound: upperBound)
    }

    /// `Int64` counterpart of `clampedInt`.
    static func clampedInt64(
        _ raw: Any?,
        field: String,
        label: String,
        lowerBound: Int64,
        upperBound: Int64,
        allowingNonPositive: Bool = false,
        notes: inout [String]
    ) -> Int64? {
        guard let value = numeric(
            raw, field: field, label: label, allowingNonPositive: allowingNonPositive, notes: &notes
        ) else { return nil }
        return clamped(value, lowerBound: lowerBound, upperBound: upperBound)
    }

    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// AN IDENTIFIER THAT GOES INTO A PERSISTENCE KEY MUST NOT CONTAIN THAT KEY'S DELIMITERS
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// A string that survives this is guaranteed safe to embed in a delimiter-joined persistence
    /// key. Skipping the check is not a cosmetic bug: an operator-supplied id containing the
    /// delimiter splits into different components on the way back out, so the restored key never
    /// matches the composed one, every lookup misses, and the subsystem behaves as if it had no
    /// stored state at all after each restart. That is exactly the live defect in Android's
    /// equivalent — a recurring grant that is due again on every relaunch, farmable with no clock
    /// manipulation at all.
    ///
    /// The delimiters are the CALLER's, so the caller passes them: they must be the same
    /// characters that the caller's encoder joins with, and a second delimiter added to an encoder
    /// without being added here reopens the hole. Newlines are ALWAYS rejected, whether or not the
    /// caller lists one — a key that can contain a line break is one encoding change away from the
    /// same class of bug.
    ///
    /// App Store product identifiers are limited to alphanumerics, underscores and periods, so
    /// rejecting delimiters costs nothing real and turns an encoding hazard into an
    /// operator-visible config error.
    ///
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// AND IT NEEDS A CEILING ON ITS LENGTH, FOR EVERY CALLER
    /// ═══════════════════════════════════════════════════════════════════════════════════════
    /// Nothing else bounds the SIZE of an operator-supplied id. A five-million-character
    /// `season_id` or `product_id` parses, is held for the life of the process, is embedded in a
    /// persistence record — and is then rewritten in full by every `addXp` and every `claim`, so
    /// one bad config turns each reward tap into a multi-megabyte `UserDefaults` write. It is
    /// operator-controlled, so this is availability, not a privilege boundary: a self-inflicted
    /// wound rather than an attack. But it is a silent one, and the cost of closing it is a
    /// comparison.
    ///
    /// The bound lives here rather than at each call site for the same reason the delimiter check
    /// does: an id that reaches storage without passing this function is the hole. 128 is a ceiling
    /// no legitimate id approaches — App Store product identifiers are capped at 100 characters,
    /// and every `season_id`/`reward_type` this package has seen is under 32.
    ///
    /// Characters, not UTF-8 bytes: the point is to catch an id that is wrong by orders of
    /// magnitude, and the grapheme count is the number an operator reading the diagnostic can
    /// actually check against what they typed.
    static let maxIdentifierLength = 128

    static func identifier(
        _ raw: Any?,
        field: String,
        label: String,
        mustNotContain delimiters: [Character],
        notes: inout [String]
    ) -> String? {
        guard let value = (raw as? String)?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else {
            notes.append("\(label) is missing a valid '\(field)' -> dropped")
            return nil
        }
        guard value.count <= maxIdentifierLength else {
            // The id itself is NOT echoed into the note: it is the thing that is too big, and a
            // diagnostic that reproduces it in full is the same availability problem wearing a
            // different hat (it ends up in logs and in `apply`'s report string).
            notes.append(
                "\(label)'s '\(field)' is \(value.count) characters, over the \(maxIdentifierLength)-character "
                + "limit -> dropped"
            )
            return nil
        }
        guard !value.contains(where: { delimiters.contains($0) || $0.isNewline }) else {
            let listed = delimiters.map { "'\($0)'" }.joined(separator: ", ")
            notes.append("\(label)'s '\(field)' ('\(value)') contains a persistence delimiter (\(listed) or a newline) -> dropped")
            return nil
        }
        return value
    }
}
