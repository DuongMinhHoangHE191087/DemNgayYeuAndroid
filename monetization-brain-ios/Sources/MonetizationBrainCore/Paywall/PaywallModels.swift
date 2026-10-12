/// Paywall data types — mirrors Android's `PaywallManager.Variant`/`Paywall`/`ApplyResult`
/// (Kotlin nested data classes become top-level Swift types, matching this codebase's existing
/// convention for `IapProduct`/`IapPurchase`/etc.).

/// One A/B variant for a placement: which products to offer, in what order, and which weight
/// this variant gets in the deterministic bucket pick.
public struct PaywallVariant: Sendable, Equatable {
    public let id: String
    public let weight: Int
    public let products: [String]
    public let highlight: String?

    public init(id: String, weight: Int, products: [String], highlight: String? = nil) {
        self.id = id
        self.weight = weight
        self.products = products
        self.highlight = highlight
    }
}

/// The decision returned to the caller — what to sell, in what order, with which variant.
/// `products` is already in the chosen variant's declared order.
public struct Paywall: Sendable, Equatable {
    public let placement: String
    public let variantId: String
    public let products: [String]
    public let highlight: String?
    public let version: String

    public init(placement: String, variantId: String, products: [String], highlight: String?, version: String) {
        self.placement = placement
        self.variantId = variantId
        self.products = products
        self.highlight = highlight
        self.version = version
    }
}

/// Result of one `PaywallManager.apply(_:)` call.
public struct PaywallApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String

    public init(applied: Bool, report: String) {
        self.applied = applied
        self.report = report
    }
}
