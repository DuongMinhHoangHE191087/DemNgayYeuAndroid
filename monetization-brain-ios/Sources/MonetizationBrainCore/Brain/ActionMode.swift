/// Minimal action space (Android doc 01 §1). `.gameplayConfig` is reserved for dynamic
/// difficulty adjustment (doc 24 §C), not implemented until a later phase.
public enum ActionMode: String, Sendable, CaseIterable {
    case noAction = "NO_ACTION"
    case freeAction = "FREE_ACTION"
    case rewardedAd = "REWARDED_AD"
    case interstitial = "INTERSTITIAL"
    case iapOffer = "IAP_OFFER"
    case gameplayConfig = "GAMEPLAY_CONFIG"
}
