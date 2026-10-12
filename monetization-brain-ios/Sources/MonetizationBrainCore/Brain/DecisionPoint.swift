/// Minimal set of decision points (Android doc 06 §2). Extend only when a matching
/// instrumentation event exists — don't add a decision point speculatively.
public enum DecisionPoint: String, Sendable, CaseIterable {
    case sessionStart = "SESSION_START"
    case levelFail = "LEVEL_FAIL"
    case levelFailStreak = "LEVEL_FAIL_STREAK"
    case levelComplete = "LEVEL_COMPLETE"
    case currencyLow = "CURRENCY_LOW"
    case shopOpen = "SHOP_OPEN"
    case rewardedOpportunity = "REWARDED_OPPORTUNITY"
    case interstitialOpportunity = "INTERSTITIAL_OPPORTUNITY"
    case returnAfterInactivity = "RETURN_AFTER_INACTIVITY"
}
