package com.app.plugin.ads

import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.VideoController.VideoLifecycleCallbacks
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback

/**
 * Poster duy nhất đưa callback GMA về main (2.0.0 C3c, D47-2). Callback giao ngoài main đi qua [poster];
 * đang ở main thì chạy ngay, không đổi thứ tự hôm nay. Bọc ở chỗ ĐĂNG KÝ listener bằng decorator bên dưới
 * (một dòng mỗi chỗ), không bọc từng thân `onAd…`. Hàm nào cũng phải ghi rõ visibility + kiểu trả về.
 */
internal object MainPost {
    /** Seam test: mặc định so [Looper.myLooper] với main looper. */
    @Volatile internal var isMain: () -> Boolean = { Looper.myLooper() === Looper.getMainLooper() }

    /** Seam test: mặc định post lên main looper. */
    @Volatile internal var poster: (Runnable) -> Unit = { r -> Handler(Looper.getMainLooper()).post(r) }

    internal fun resetForTest(): Unit {
        isMain = { Looper.myLooper() === Looper.getMainLooper() }
        poster = { r -> Handler(Looper.getMainLooper()).post(r) }
    }

    internal fun run(block: () -> Unit): Unit {
        if (isMain()) block() else poster(Runnable { block() })
    }

    internal fun paid(block: (AdValue) -> Unit): OnPaidEventListener =
        OnPaidEventListener { v -> MainPost.run { block(v) } }

    internal fun earned(block: () -> Unit): OnUserEarnedRewardListener =
        OnUserEarnedRewardListener { _: RewardItem -> MainPost.run(block) }

    internal fun nativeLoaded(block: (NativeAd) -> Unit): NativeAd.OnNativeAdLoadedListener =
        NativeAd.OnNativeAdLoadedListener { ad -> MainPost.run { block(ad) } }

    internal fun adListener(d: AdListener): AdListener = object : AdListener() {
        override fun onAdClosed(): Unit = MainPost.run { d.onAdClosed() }
        override fun onAdFailedToLoad(e: LoadAdError): Unit = MainPost.run { d.onAdFailedToLoad(e) }
        override fun onAdOpened(): Unit = MainPost.run { d.onAdOpened() }
        override fun onAdLoaded(): Unit = MainPost.run { d.onAdLoaded() }
        override fun onAdClicked(): Unit = MainPost.run { d.onAdClicked() }
        override fun onAdImpression(): Unit = MainPost.run { d.onAdImpression() }
        override fun onAdSwipeGestureClicked(): Unit = MainPost.run { d.onAdSwipeGestureClicked() }
    }

    internal fun fullScreen(d: FullScreenContentCallback): FullScreenContentCallback = object : FullScreenContentCallback() {
        override fun onAdFailedToShowFullScreenContent(e: AdError): Unit = MainPost.run { d.onAdFailedToShowFullScreenContent(e) }
        override fun onAdShowedFullScreenContent(): Unit = MainPost.run { d.onAdShowedFullScreenContent() }
        override fun onAdDismissedFullScreenContent(): Unit = MainPost.run { d.onAdDismissedFullScreenContent() }
        override fun onAdImpression(): Unit = MainPost.run { d.onAdImpression() }
        override fun onAdClicked(): Unit = MainPost.run { d.onAdClicked() }
    }

    internal fun video(d: VideoLifecycleCallbacks): VideoLifecycleCallbacks = object : VideoLifecycleCallbacks() {
        override fun onVideoStart(): Unit = MainPost.run { d.onVideoStart() }
        override fun onVideoPlay(): Unit = MainPost.run { d.onVideoPlay() }
        override fun onVideoPause(): Unit = MainPost.run { d.onVideoPause() }
        override fun onVideoEnd(): Unit = MainPost.run { d.onVideoEnd() }
        override fun onVideoMute(muted: Boolean): Unit = MainPost.run { d.onVideoMute(muted) }
    }

    internal fun interstitialLoad(d: InterstitialAdLoadCallback): InterstitialAdLoadCallback = object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd): Unit = MainPost.run { d.onAdLoaded(ad) }
        override fun onAdFailedToLoad(e: LoadAdError): Unit = MainPost.run { d.onAdFailedToLoad(e) }
    }

    internal fun rewardedLoad(d: RewardedAdLoadCallback): RewardedAdLoadCallback = object : RewardedAdLoadCallback() {
        override fun onAdLoaded(ad: RewardedAd): Unit = MainPost.run { d.onAdLoaded(ad) }
        override fun onAdFailedToLoad(e: LoadAdError): Unit = MainPost.run { d.onAdFailedToLoad(e) }
    }

    internal fun rwiLoad(d: RewardedInterstitialAdLoadCallback): RewardedInterstitialAdLoadCallback =
        object : RewardedInterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedInterstitialAd): Unit = MainPost.run { d.onAdLoaded(ad) }
            override fun onAdFailedToLoad(e: LoadAdError): Unit = MainPost.run { d.onAdFailedToLoad(e) }
        }

    internal fun appOpenLoad(d: AppOpenAd.AppOpenAdLoadCallback): AppOpenAd.AppOpenAdLoadCallback =
        object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd): Unit = MainPost.run { d.onAdLoaded(ad) }
            override fun onAdFailedToLoad(e: LoadAdError): Unit = MainPost.run { d.onAdFailedToLoad(e) }
        }
}
