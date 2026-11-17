package com.example.ads

fun isAdsEligible(isVip: Boolean, canRequestAds: Boolean, adUnitId: String, killed: Boolean): Boolean =
    !isVip && canRequestAds && adUnitId.isNotBlank() && !killed
