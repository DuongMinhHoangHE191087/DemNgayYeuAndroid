package com.example.ads

import com.example.shared.monetization.AdPlacementPolicy
import com.example.shared.monetization.AdPlacementType
import com.example.shared.monetization.AdSessionState

fun isAdsEligible(isVip: Boolean, canRequestAds: Boolean, adUnitId: String, killed: Boolean): Boolean =
    !isVip && canRequestAds && adUnitId.isNotBlank() && !killed

fun isAdsEligibleWithPolicy(
    state: AdSessionState,
    adUnitId: String,
    killed: Boolean,
    type: AdPlacementType = AdPlacementType.BANNER,
    nowMs: Long = 0L,
): Boolean {
    if (adUnitId.isBlank() || killed) return false
    return AdPlacementPolicy.evaluate(type, state, nowMs).allowed
}
