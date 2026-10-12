import Foundation

/// One completed purchase, in the shape an attribution SDK wants it.
///
/// Separate from `IapPurchase` on purpose: that type carries the store's receipt, token and
/// channel, none of which an attribution network should ever receive. What leaves here is the
/// commercial fact — what was bought, for how much, in what currency.
public struct AttributionPurchase: Sendable, Equatable {
    public let productId: String
    public let orderId: String
    /// Price in millionths of the currency unit, exactly as the store reported it.
    public let revenueMicros: Int64
    /// The STORE's currency code. Never converted — see the type doc on `AttributionBridge`.
    public let currencyCode: String
    public let quantity: Int
    /// Whether this is recurring revenue. Attribution networks model it differently from a
    /// one-off, so this is not a label — it decides which event is sent.
    public let isSubscription: Bool

    /// The same amount as a decimal figure, for SDKs whose API takes one.
    public var revenue: Double {
        Double(revenueMicros) / 1_000_000
    }

    public init(
        productId: String,
        orderId: String,
        revenueMicros: Int64,
        currencyCode: String,
        quantity: Int,
        isSubscription: Bool
    ) {
        self.productId = productId
        self.orderId = orderId
        self.revenueMicros = revenueMicros
        self.currencyCode = currencyCode
        self.quantity = quantity
        self.isSubscription = isSubscription
    }
}

/// Where purchase revenue goes so that user-acquisition campaigns can see it.
///
/// ═══════════════════════════════════════════════════════════════════════════════════════════
/// WHY THIS IS A PROTOCOL HERE AND NOT AN APPSFLYER TARGET
/// ═══════════════════════════════════════════════════════════════════════════════════════════
///
/// Android's `AppsflyerHelper.logIapPurchase` is the counterpart, and the comment above it is the
/// reason this exists at all: before it was written it was an EMPTY function nobody called, so
/// AppsFlyer saw ad revenue and not one cent of IAP. Every ROAS number the UA campaigns bid on
/// was missing a side — the larger side, for a game that sells anything. That class of mistake
/// never reports an error: campaigns keep running, dashboards keep showing numbers, and the
/// auction algorithm optimises against half the truth.
///
/// The iOS port stops one step short of naming a vendor. An attribution SDK is a closed-source
/// binary, and adding one to this package's dependencies would put it in the graph of every
/// consumer — including the ones using a different vendor or none. So the SDK owns the part that
/// is actually hard (knowing a purchase completed, what it was worth, and whether it recurs) and
/// the host writes a conformance, which for AppsFlyer is roughly:
///
/// ```swift
/// struct AppsFlyerAttribution: AttributionBridge {
///     func logPurchase(_ purchase: AttributionPurchase) async {
///         AppsFlyerLib.shared().logEvent(
///             purchase.isSubscription ? AFEventSubscribe : AFEventPurchase,
///             withValues: [
///                 AFEventParamRevenue: purchase.revenue,
///                 AFEventParamCurrency: purchase.currencyCode,
///                 AFEventParamContentId: purchase.productId,
///                 AFEventParamQuantity: purchase.quantity
///             ]
///         )
///     }
/// }
/// ```
///
/// Same reasoning as `RemoteConfigProvider`, and it leaves the door open for a
/// `MonetizationBrainAppsFlyer` target later without changing anything here.
///
/// ═══ TWO RULES A CONFORMANCE MUST KEEP ═══
///
/// **Send the amount and the currency verbatim.** Do not convert to USD first. The network
/// converts using its own same-day rate, which is more accurate than any table shipped inside a
/// client — the same reason `FxRates` refuses to guess rather than guessing badly.
///
/// **Never let reporting break the purchase.** This method cannot throw, and `IapHelper` awaits
/// it as one of several independent reporting steps: a conformance that hangs holds up the
/// others, so keep the work inside it short and swallow the vendor's failures.
public protocol AttributionBridge: Sendable {
    func logPurchase(_ purchase: AttributionPurchase) async
}

/// The default: attribution is opt-in, and a host that never sets one pays nothing for it.
public struct NoOpAttributionBridge: AttributionBridge {
    public init() {}
    public func logPurchase(_ purchase: AttributionPurchase) async {}
}
