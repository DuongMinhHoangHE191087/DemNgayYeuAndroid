/// Purchase-routing data types — mirrors Android's `PurchaseRouter.Rule`/`WebShopConfig`/
/// `ApplyResult` (Kotlin nested data classes become top-level Swift types, matching this
/// codebase's existing convention for `PaywallVariant`/`Paywall`/`IapProduct`/etc.).

/// One routing rule: this product goes to this channel. The first matching rule in declaration
/// order wins.
public struct PurchaseRoutingRule: Sendable, Equatable {
    public let productId: String
    public let channel: PurchaseChannel

    public init(productId: String, channel: PurchaseChannel) {
        self.productId = productId
        self.channel = channel
    }
}

/// Web-shop channel config. `.webShop` is only ever selectable when every field here clears —
/// see `PurchaseRouter`'s doc comment for the full four-condition gate.
public struct WebShopConfig: Sendable, Equatable {
    public let enabled: Bool
    public let urlTemplate: String
    /// Empty = no country restriction. Non-empty = the caller-supplied country must be a member.
    public let eligibleCountries: Set<String>
    /// `false` when `urlTemplate` is non-empty but not `https://` — when `false`, web-shop can
    /// never be selected regardless of `enabled`. https is not a negotiable/bypassable condition:
    /// steering a paying user's card details to an unencrypted URL is a security defect, not a
    /// tolerable config typo.
    public let urlOk: Bool

    public init(enabled: Bool, urlTemplate: String, eligibleCountries: Set<String>, urlOk: Bool) {
        self.enabled = enabled
        self.urlTemplate = urlTemplate
        self.eligibleCountries = eligibleCountries
        self.urlOk = urlOk
    }

    public static let off = WebShopConfig(enabled: false, urlTemplate: "", eligibleCountries: [], urlOk: false)
}

/// Result of one `PurchaseRouter.apply(_:)` call.
public struct RoutingApplyResult: Sendable, Equatable {
    public let applied: Bool
    public let report: String

    public init(applied: Bool, report: String) {
        self.applied = applied
        self.report = report
    }
}
