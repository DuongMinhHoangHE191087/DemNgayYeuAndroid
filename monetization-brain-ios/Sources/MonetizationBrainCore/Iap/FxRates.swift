import Foundation

/// Converts a store price to USD — **only when the rate is actually known**, never a guess.
///
/// ═══ WHY NIL, NOT A GUESS ═══
///
/// The store reports price in local currency. Stuffing that number directly into
/// `PlayerState.purchaseValueLifetimeUsd` would record a 99,000 VND transaction as "99000 USD" —
/// a type error that never crashes, so nobody notices (matches Android's `FxRates.kt` doc
/// comment verbatim).
///
/// Embedding a rate table in code isn't the fix either — rates change daily, this SDK ships
/// monthly at best. So only `USD` is a compile-time constant; every other rate must be loaded
/// remotely (a policy artifact, key `fx_rates` on Android — the same shape applies here) and can
/// update without a release.
///
/// ═══ THE TRUTH STAYS INTACT AT THE EVENT LEVEL ═══
///
/// Failing to convert does NOT lose data: the `iap_purchase` event always sends the original
/// `price_micros`/`currency_code` to the backend, which has the real rate. The only thing this
/// affects is the on-device accumulated USD total, and it's left honestly blank rather than
/// filled with a fabricated number.
public enum FxRates {
    private static let microsPerUnit = 1_000_000.0
    private static let rates = LockedValue([String: Double]())

    /// Loads "1 unit of currency = ? USD". REPLACES the whole table.
    ///
    /// Replace, not merge: merging would keep a stale rate for a currency code the newest
    /// artifact dropped — exactly the kind of stale number this mechanism exists to avoid.
    public static func setRates(_ newRates: [String: Double]) {
        rates.wrapped = newRates
            .filter { $0.value > 0.0 && $0.value.isFinite }
            .reduce(into: [String: Double]()) { result, entry in
                result[entry.key.uppercased()] = entry.value
            }
    }

    public static func reset() {
        rates.wrapped = [:]
    }

    /// Number of non-USD currencies currently known — for surfacing in an artifact-load report.
    public static func knownCurrencyCount() -> Int {
        rates.wrapped.count
    }

    /// - Returns: the USD value, or `nil` if `currencyCode`'s rate isn't known. Callers MUST
    ///   treat `nil` by omitting the value, never by substituting `0.0` — "value unknown" and
    ///   "worth zero" are different facts.
    public static func toUsd(priceMicros: Int64, currencyCode: String) -> Double? {
        guard !currencyCode.isEmpty else { return nil }
        let code = currencyCode.uppercased()
        let amount = Double(priceMicros) / microsPerUnit
        if code == "USD" { return amount }
        guard let rate = rates.wrapped[code] else { return nil }
        return amount * rate
    }
}
