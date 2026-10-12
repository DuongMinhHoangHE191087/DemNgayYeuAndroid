package com.app.plugin.ads

internal object AdsIDDef {
    //Banner
    const val AdmobBanner = 0
    const val FBBanner = 1
    const val MaxBanner = 6

    //Rect
    const val AdmobRect = 0
    const val FBRect = 1
    const val MaxRect = 6

    //Collapse
    const val AdmobNtCl = 0
    const val FBNtCl = 1
    const val MaxNtCl = 6

    //Native
    const val AdmobNative = 0
    const val FBNative = 1
    const val MaxNative = 6

    //Full
    const val AdmobFullInters = 0
    const val AdmobFullLow = 10
    const val AdmobFullNt = 20
    const val AdmobFullOpen = 100
    const val FBFullInters = 1
    const val FBFullNt = 21
    const val MaxFullInters = 6
    const val MaxFullNt = 60
    const val MaxFullOpen = 600

    //Gift
    const val AdmobRewarded = 0
    const val FBRewarded = 1
    const val MaxRewarded = 6

    // Rewarded interstitial (1.9.0 Task 2): chỉ AdMob, chỉ `RewardedInterstitialController` dùng (placement `rwi_`);
    // nằm trong map gift để kế thừa vòng đời Activity, `RewardedController` bỏ qua type này. `val` internal (không
    // `const`): getter mangle `$appplugin`, khối dump của lớp có từ 1.5.0 không đổi (cổng façade).
    internal val AdmobRwi: Int = 30
}