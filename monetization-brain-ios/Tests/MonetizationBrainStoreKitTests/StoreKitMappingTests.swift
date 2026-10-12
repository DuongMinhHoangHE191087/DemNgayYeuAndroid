import XCTest
import StoreKit
import MonetizationBrainCore
@testable import MonetizationBrainStoreKit

/// The first tests this target has ever had.
///
/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// WHY THIS FILE EXISTS NOW
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// `StoreKit2Backend` was carried as "verified by type-checking only" because `Transaction` and
/// `Product` cannot be minted in a unit test. That is true of the half that talks to the store.
/// It is NOT true of the half that decides what the store's answer MEANS — and that half is where
/// a mistake costs a customer their entitlement or hands a refunder a discount. All of it is
/// value-in, value-out, and the three StoreKit types involved are `RawRepresentable` structs a
/// test can build, including with raw values Apple has not shipped yet.
final class StoreKitMappingTests: XCTestCase {

    /// ═══ THE SHADOWING TRAP, BITING EXACTLY WHERE PHASE 7 SAID IT WOULD ═══
    ///
    /// `SubscriptionRenewalState` is also a StoreKit typealias, so in a file importing both, the
    /// bare name resolves to Apple's and every `.subscribed` below fails to type-check. The Phase
    /// 7 spec §7 recorded this shadowing as a known trap that could no longer be renamed cheaply;
    /// this is the first file to actually pay for it. One alias, stated once.
    private typealias CoreRenewalState = MonetizationBrainCore.SubscriptionRenewalState

    private func snapshot(
        _ state: CoreRenewalState,
        willAutoRenew: Bool = false,
        expirationMs: Int64 = 0
    ) -> SubscriptionSnapshot {
        SubscriptionSnapshot(
            productId: "sub_monthly",
            renewalState: state,
            willAutoRenew: willAutoRenew,
            expirationMs: expirationMs
        )
    }

    // MARK: - Family Sharing reduction

    /// The bug this reduction exists to prevent: a customer whose OWN purchase lapsed but who is
    /// still covered by a family plan. The store lists both statuses, and taking `.first` would
    /// revoke a live entitlement whenever the expired one happened to be listed first.
    func test_aLiveFamilyEntitlementSurvivesAnExpiredOwnPurchaseInEitherOrder() {
        let expired = snapshot(.expired)
        let subscribed = snapshot(.subscribed)

        XCTAssertEqual(StoreKit2Backend.moreFavorable(expired, subscribed).renewalState, .subscribed)
        XCTAssertEqual(StoreKit2Backend.moreFavorable(subscribed, expired).renewalState, .subscribed)
    }

    /// ═══ THE RANKING LINE THAT LOOKS BACKWARDS AND IS NOT ═══
    ///
    /// `.revoked` outranks `.expired`. Neither is entitled, so the ordering changes nothing about
    /// access — it changes what `SubscriptionLifecycle.isWinBackEligible` sees. A refund is a
    /// deliberate act; a lapse is the absence of one. A customer holding both has demonstrably
    /// refunded, and ranking `.expired` higher would let the stale lapse mask the refund and hand
    /// them a win-back discount. Nothing else downstream distinguishes the two.
    func test_arefundOutranksALapseSoWinBackStaysWithheld() {
        XCTAssertEqual(
            StoreKit2Backend.moreFavorable(snapshot(.expired), snapshot(.revoked)).renewalState,
            .revoked
        )
        XCTAssertEqual(
            StoreKit2Backend.moreFavorable(snapshot(.revoked), snapshot(.expired)).renewalState,
            .revoked
        )
    }

    /// Full ordering, stated once: subscribed > inGracePeriod > inBillingRetryPeriod > revoked >
    /// expired > unknown. Asserted pairwise rather than by reading `signalRank` back, so a
    /// renumbering that keeps the ranks consistent but reorders them still fails.
    func test_theStrongestSignalWinsAcrossTheWholeOrdering() {
        let ordered: [CoreRenewalState] = [
            .unknown, .expired, .revoked, .inBillingRetryPeriod, .inGracePeriod, .subscribed
        ]

        for (weakIndex, weaker) in ordered.enumerated() {
            for stronger in ordered[(weakIndex + 1)...] {
                XCTAssertEqual(
                    StoreKit2Backend.moreFavorable(snapshot(weaker), snapshot(stronger)).renewalState,
                    stronger,
                    "\(stronger) should outrank \(weaker)"
                )
                XCTAssertEqual(
                    StoreKit2Backend.moreFavorable(snapshot(stronger), snapshot(weaker)).renewalState,
                    stronger,
                    "\(stronger) should outrank \(weaker) regardless of order"
                )
            }
        }
    }

    /// A status the SDK does not recognise must never erase one it does — otherwise a single
    /// future StoreKit state could silently take away a live subscription.
    func test_anUnknownStatusNeverErasesARealOne() {
        XCTAssertEqual(
            StoreKit2Backend.moreFavorable(snapshot(.subscribed), snapshot(.unknown)).renewalState,
            .subscribed
        )
    }

    /// Two equally-ranked statuses resolve by expiry, then by auto-renew — so the reduction does
    /// not depend on the order the store happened to list them in. Without the tiebreaks the
    /// reported expiry flips between calls for a customer with two live entitlements.
    func test_equalRanksAreBrokenByLaterExpiryThenByAutoRenew() {
        let early = snapshot(.subscribed, willAutoRenew: true, expirationMs: 1_000)
        let late = snapshot(.subscribed, willAutoRenew: false, expirationMs: 2_000)
        XCTAssertEqual(StoreKit2Backend.moreFavorable(early, late).expirationMs, 2_000)
        XCTAssertEqual(StoreKit2Backend.moreFavorable(late, early).expirationMs, 2_000)

        let renewing = snapshot(.subscribed, willAutoRenew: true, expirationMs: 5_000)
        let lapsing = snapshot(.subscribed, willAutoRenew: false, expirationMs: 5_000)
        // Tied on rank AND expiry: the auto-renewing one wins from either side, so the reduction
        // does not depend on which status the store listed first.
        XCTAssertTrue(StoreKit2Backend.moreFavorable(lapsing, renewing).willAutoRenew)
        XCTAssertTrue(StoreKit2Backend.moreFavorable(renewing, lapsing).willAutoRenew)
    }

    func test_theFirstStatusIsTakenWhenThereIsNothingToCompareItTo() {
        XCTAssertEqual(StoreKit2Backend.moreFavorable(nil, snapshot(.expired)).renewalState, .expired)
    }

    // MARK: - StoreKit enum mapping (including the load-bearing `default:`)

    func test_everyKnownRenewalStateMaps() {
        XCTAssertEqual(StoreKit2Backend.toRenewalState(.subscribed), .subscribed)
        XCTAssertEqual(StoreKit2Backend.toRenewalState(.expired), .expired)
        XCTAssertEqual(StoreKit2Backend.toRenewalState(.inBillingRetryPeriod), .inBillingRetryPeriod)
        XCTAssertEqual(StoreKit2Backend.toRenewalState(.inGracePeriod), .inGracePeriod)
        XCTAssertEqual(StoreKit2Backend.toRenewalState(.revoked), .revoked)
    }

    /// `RenewalState` is a struct, not an enum, so the `switch` cannot be exhaustive and the
    /// `default:` is the only thing standing between a future StoreKit state and a crash or a
    /// wrong mapping. A raw value Apple has not shipped is the only way to reach it.
    func test_anUnrecognisedRenewalStateBecomesUnknownRatherThanSomethingWrong() {
        XCTAssertEqual(
            StoreKit2Backend.toRenewalState(Product.SubscriptionInfo.RenewalState(rawValue: 9_999)),
            .unknown
        )
    }

    func test_everyKnownExpirationReasonMaps() {
        XCTAssertEqual(StoreKit2Backend.toExpirationReason(.autoRenewDisabled), .autoRenewDisabled)
        XCTAssertEqual(StoreKit2Backend.toExpirationReason(.billingError), .billingError)
        XCTAssertEqual(
            StoreKit2Backend.toExpirationReason(.didNotConsentToPriceIncrease),
            .didNotConsentToPriceIncrease
        )
        XCTAssertEqual(StoreKit2Backend.toExpirationReason(.productUnavailable), .productUnavailable)
        XCTAssertEqual(
            StoreKit2Backend.toExpirationReason(
                Product.SubscriptionInfo.RenewalInfo.ExpirationReason(rawValue: 9_999)
            ),
            .unknown
        )
    }

    /// The `default:` here maps to `.nonConsumable`, NOT to a consumable — an unrecognised product
    /// type must never be treated as something the player can buy again and again.
    func test_productTypesMapAndAnUnknownOneIsNotTreatedAsConsumable() {
        XCTAssertEqual(StoreKit2Backend.toProductType(.consumable), .consumable)
        XCTAssertEqual(StoreKit2Backend.toProductType(.nonConsumable), .nonConsumable)
        XCTAssertEqual(StoreKit2Backend.toProductType(.autoRenewable), .autoRenewable)
        XCTAssertEqual(StoreKit2Backend.toProductType(.nonRenewable), .nonRenewing)
        XCTAssertEqual(StoreKit2Backend.toProductType(Product.ProductType(rawValue: "future")), .nonConsumable)
    }

    // MARK: - Time

    func test_absentDatesAreZeroNotASentinelThatReadsAsARealInstant() {
        XCTAssertEqual(StoreKit2Backend.toEpochMs(nil), 0)
    }

    func test_aDateConvertsToEpochMilliseconds() {
        XCTAssertEqual(
            StoreKit2Backend.toEpochMs(Date(timeIntervalSince1970: 1_700_000_000.5)),
            1_700_000_000_500
        )
    }

    /// `Int64(someDouble)` TRAPS above `Int64.max` — it does not saturate. These dates arrive in a
    /// cryptographically verified payload so this is defence in depth, but it is on a payment path
    /// and the same pattern has been a real crash elsewhere in this port.
    ///
    /// Writing this test found that the defence did not defend: the guard was on `seconds * 1000`,
    /// which overflows to infinity for a finite but huge input, so the function returned 0 — the
    /// EPOCH — for a far-future date. Callers order by this value, and 0 is the oldest instant
    /// there is, so the meaning inverted. A far-future expiry read as long-expired.
    func test_anAbsurdlyFarDateSaturatesInsteadOfReadingAsTheEpoch() {
        XCTAssertGreaterThan(StoreKit2Backend.clampToEpochMs(.greatestFiniteMagnitude), 0)
        XCTAssertLessThan(StoreKit2Backend.clampToEpochMs(-.greatestFiniteMagnitude), 0)
        // Saturation is monotone: a bigger input never comes back smaller.
        XCTAssertGreaterThanOrEqual(
            StoreKit2Backend.clampToEpochMs(.greatestFiniteMagnitude),
            StoreKit2Backend.clampToEpochMs(1e300)
        )
        XCTAssertGreaterThanOrEqual(
            StoreKit2Backend.clampToEpochMs(1e300),
            StoreKit2Backend.clampToEpochMs(1_700_000_000)
        )
    }

    /// NaN and infinity are not magnitudes to saturate — they are garbage, and they read as 0,
    /// the same as an absent date.
    func test_nonFiniteInputsReadAsAbsentRatherThanAsAnInstant() {
        XCTAssertEqual(StoreKit2Backend.clampToEpochMs(.infinity), 0)
        XCTAssertEqual(StoreKit2Backend.clampToEpochMs(-.infinity), 0)
        XCTAssertEqual(StoreKit2Backend.clampToEpochMs(.nan), 0)
    }

    // MARK: - Price

    /// Prices go through `Decimal`, never `Double`. `4.99 * 1_000_000` in binary floating point is
    /// 4989999.999999999, and truncating that bills 4_989_999 micros — one micro short, on every
    /// price ending in .99, forever.
    func test_pricesConvertExactlyThroughDecimal() {
        XCTAssertEqual(StoreKit2Backend.toPriceMicros(Decimal(string: "4.99")!), 4_990_000)
        XCTAssertEqual(StoreKit2Backend.toPriceMicros(Decimal(string: "0.99")!), 990_000)
        XCTAssertEqual(StoreKit2Backend.toPriceMicros(Decimal(string: "19.99")!), 19_990_000)
        XCTAssertEqual(StoreKit2Backend.toPriceMicros(Decimal(string: "0")!), 0)
        // A currency with no minor unit, at a magnitude that is ordinary in it.
        XCTAssertEqual(StoreKit2Backend.toPriceMicros(Decimal(string: "129000")!), 129_000_000_000)
    }
}
