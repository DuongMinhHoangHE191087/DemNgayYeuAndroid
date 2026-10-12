/// Public IAP data types — deliberately never leak a StoreKit type into this SDK's own public
/// API (mirrors Android's `IapModels.kt`: this is a published artifact, and Google has broken
/// Play Billing's public shape once already; wrapping in an owned model lets the vendor
/// implementation change without breaking every integrator).

/// StoreKit 2's real 4-case product type — NOT Android's coarser `{INAPP, SUBS}` + a separately
/// app-declared consumable set. `Product.ProductType` already tells us consumability directly;
/// Android needed the app to declare it because Play's `ProductDetails` doesn't (see design spec
/// §3.3 for the full rationale).
public enum IapProductType: Sendable, Equatable, CaseIterable {
    case consumable
    case nonConsumable
    case autoRenewable
    case nonRenewing

    public var isSubscription: Bool {
        self == .autoRenewable || self == .nonRenewing
    }
}

/// Payment channel (Android doc 26 §C.2: revenue must be reported PER channel — margins differ
/// significantly by channel). `.webShop` has no execution path yet this phase — kept only so the
/// event schema doesn't need to change when that channel arrives, matching Android's own
/// forward-compat placeholder.
public enum PurchaseChannel: String, Sendable, Equatable, CaseIterable {
    case appStore = "APP_STORE"
    case webShop = "WEB_SHOP"
}

/// Transaction state. `.pending` is a real, distinct StoreKit 2 concept (Ask-to-Buy/SCA flows via
/// `Product.PurchaseResult.pending`) — not invented to mirror Android's PENDING 1:1. See design
/// spec §3.6 for why this phase does NOT replicate Android's INAPP/SUBS grace-period asymmetry.
public enum IapPurchaseState: String, Sendable, Equatable, CaseIterable {
    case purchased = "PURCHASED"
    case pending = "PENDING"
    case unspecified = "UNSPECIFIED"
}

/// A product with price information already fetched from the store.
///
/// `priceMicros`/`currencyCode` keep the store's ORIGINAL unit, never pre-converted — client-side
/// conversion is where rounding error and stale rates creep in; the backend has the real rate
/// table, so conversion belongs there (see `FxRates`).
public struct IapProduct: Sendable, Equatable {
    public let productId: String
    public let type: IapProductType
    public let formattedPrice: String
    public let priceMicros: Int64
    public let currencyCode: String
    public let title: String
    public let description: String

    public init(
        productId: String,
        type: IapProductType,
        formattedPrice: String,
        priceMicros: Int64,
        currencyCode: String,
        title: String = "",
        description: String = ""
    ) {
        self.productId = productId
        self.type = type
        self.formattedPrice = formattedPrice
        self.priceMicros = priceMicros
        self.currencyCode = currencyCode
        self.title = title
        self.description = description
    }
}

/// A transaction that occurred.
///
/// `priceMicros`/`currencyCode` are joined in from the matching cached `IapProduct` at handling
/// time — a raw transaction from the store doesn't carry price by itself; without this join the
/// revenue event has no amount.
public struct IapPurchase: Sendable, Equatable {
    public let productId: String
    public let orderId: String
    public let purchaseToken: String
    public let purchaseTimeMs: Int64
    public let state: IapPurchaseState
    public let isFinished: Bool
    public let isAutoRenewing: Bool
    public let quantity: Int
    public let priceMicros: Int64
    public let currencyCode: String
    public let channel: PurchaseChannel

    public init(
        productId: String,
        orderId: String,
        purchaseToken: String,
        purchaseTimeMs: Int64,
        state: IapPurchaseState,
        isFinished: Bool,
        isAutoRenewing: Bool,
        quantity: Int,
        priceMicros: Int64,
        currencyCode: String,
        channel: PurchaseChannel = .appStore
    ) {
        self.productId = productId
        self.orderId = orderId
        self.purchaseToken = purchaseToken
        self.purchaseTimeMs = purchaseTimeMs
        self.state = state
        self.isFinished = isFinished
        self.isAutoRenewing = isAutoRenewing
        self.quantity = quantity
        self.priceMicros = priceMicros
        self.currencyCode = currencyCode
        self.channel = channel
    }
}

/// Result of one purchase attempt — enough for the caller to show the right message without
/// knowing any store-specific error code.
public enum IapResult: Sendable, Equatable {
    case success(IapPurchase)
    case pending(IapPurchase)
    case userCanceled
    case alreadyOwned
    case failed(code: Int, message: String)
    /// The router chose the web-shop channel (design spec, Purchase Router phase): the SDK built
    /// a checkout URL and is handing it back — the host app opens it however fits its UX
    /// (SFSafariViewController, an in-app WKWebView, the system browser). No entitlement has been
    /// granted yet: that only happens after `PurchaseRouter.grantFromServer(...)`, once the host
    /// app's own backend has verified the vendor's webhook.
    case webCheckoutOpened(productId: String, url: String)
}
