package com.app.plugin.ads

import android.os.Handler
import android.os.Looper
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.VideoController
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue as NgAdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError as NgLoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd as NgNativeAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener as NgEarned

/**
 * Cửa duy nhất đưa callback quảng cáo về main (2.0.0 C3c; 2.4.0 NG1, luật 36, Hợp đồng B "Luồng"). GMA Next-Gen chạy MỌI
 * callback load/event trên luồng nền [S5] → decorator Next-Gen bên dưới (`load`, `*Events`, `nativeLoader`, `video`,
 * `earned`, `bannerRefresh` — banner tự làm mới, R-NG1 I1) bọc ở chỗ đăng ký; đang ở main thì chạy ngay. 2.4.0 NG5b:
 * decorator Legacy đã xoá (nextgen 0 lớp `com.google.android.gms.ads`); [post] luôn xếp cuối hàng main (chuỗi Full2).
 */
internal object MainPost {
    /** Seam test: mặc định so [Looper.myLooper] với main looper; [poster] mặc định post lên main looper. */
    @Volatile internal var isMain: () -> Boolean = { Looper.myLooper() === Looper.getMainLooper() }
    @Volatile internal var poster: (Runnable) -> Unit = { r -> Handler(Looper.getMainLooper()).post(r) }

    internal fun resetForTest(): Unit {
        isMain = { Looper.myLooper() === Looper.getMainLooper() }
        poster = { r -> Handler(Looper.getMainLooper()).post(r) }
    }

    internal fun run(block: () -> Unit): Unit {
        if (isMain()) block() else poster(Runnable { block() })
    }

    /** Luôn xếp [block] cuối hàng main, kể cả khi đang ở main: chạy SAU mọi callback nền đã xếp trước (FIFO, NG5b). */
    internal fun post(block: () -> Unit): Unit = poster(Runnable { block() })

    // ═══ Next-Gen ═══
    internal fun <T> load(d: AdLoadCallback<T>): AdLoadCallback<T> = object : AdLoadCallback<T> {
        override fun onAdLoaded(ad: T): Unit = MainPost.run { d.onAdLoaded(ad) }
        override fun onAdFailedToLoad(adError: NgLoadAdError): Unit = MainPost.run { d.onAdFailedToLoad(adError) }
    }

    /** Sáu sự kiện chung của mọi định dạng (gồm `onAdPaid` [S24]); lớp con thêm sự kiện riêng. */
    private abstract class Posted(private val d: AdEventCallback) : AdEventCallback {
        override fun onAdShowedFullScreenContent(): Unit = MainPost.run { d.onAdShowedFullScreenContent() }
        override fun onAdDismissedFullScreenContent(): Unit = MainPost.run { d.onAdDismissedFullScreenContent() }
        override fun onAdFailedToShowFullScreenContent(e: FullScreenContentError): Unit = MainPost.run { d.onAdFailedToShowFullScreenContent(e) }
        override fun onAdImpression(): Unit = MainPost.run { d.onAdImpression() }
        override fun onAdClicked(): Unit = MainPost.run { d.onAdClicked() }
        override fun onAdPaid(value: NgAdValue): Unit = MainPost.run { d.onAdPaid(value) }
    }
    internal fun interstitialEvents(d: InterstitialAdEventCallback): InterstitialAdEventCallback =
        object : Posted(d), InterstitialAdEventCallback { override fun onAppEvent(n: String, v: String?): Unit = MainPost.run { d.onAppEvent(n, v) } }
    internal fun appOpenEvents(d: AppOpenAdEventCallback): AppOpenAdEventCallback = object : Posted(d), AppOpenAdEventCallback {}
    internal fun rewardedEvents(d: RewardedAdEventCallback): RewardedAdEventCallback =
        object : Posted(d), RewardedAdEventCallback { override fun onAdMetadataChanged(): Unit = MainPost.run { d.onAdMetadataChanged() } }
    internal fun rwiEvents(d: RewardedInterstitialAdEventCallback): RewardedInterstitialAdEventCallback =
        object : Posted(d), RewardedInterstitialAdEventCallback { override fun onAdMetadataChanged(): Unit = MainPost.run { d.onAdMetadataChanged() } }
    internal fun bannerEvents(d: BannerAdEventCallback): BannerAdEventCallback =
        object : Posted(d), BannerAdEventCallback { override fun onAppEvent(n: String, v: String?): Unit = MainPost.run { d.onAppEvent(n, v) } }
    internal fun nativeEvents(d: NativeAdEventCallback): NativeAdEventCallback = object : Posted(d), NativeAdEventCallback {
        override fun onAdSwipeGestureClicked(): Unit = MainPost.run { d.onAdSwipeGestureClicked() }
        override fun onCustomMuteThisAdReported(): Unit = MainPost.run { d.onCustomMuteThisAdReported() }
    }
    internal fun nativeLoader(d: NativeAdLoaderCallback): NativeAdLoaderCallback = object : NativeAdLoaderCallback {
        override fun onNativeAdLoaded(nativeAd: NgNativeAd): Unit = MainPost.run { d.onNativeAdLoaded(nativeAd) }
        override fun onAdFailedToLoad(adError: NgLoadAdError): Unit = MainPost.run { d.onAdFailedToLoad(adError) }
        override fun onAdLoadingCompleted(): Unit = MainPost.run { d.onAdLoadingCompleted() }
    }
    internal fun video(d: VideoController.VideoLifecycleCallbacks): VideoController.VideoLifecycleCallbacks =
        object : VideoController.VideoLifecycleCallbacks {
            override fun onVideoStart(): Unit = MainPost.run { d.onVideoStart() }
            override fun onVideoPlay(): Unit = MainPost.run { d.onVideoPlay() }
            override fun onVideoPause(): Unit = MainPost.run { d.onVideoPause() }
            override fun onVideoEnd(): Unit = MainPost.run { d.onVideoEnd() }
            override fun onVideoMute(muted: Boolean): Unit = MainPost.run { d.onVideoMute(muted) }
        }
    internal fun earned(d: NgEarned): NgEarned = NgEarned { item -> MainPost.run { d.onUserEarnedReward(item) } }
    internal fun bannerRefresh(d: BannerAdRefreshCallback): BannerAdRefreshCallback = object : BannerAdRefreshCallback {
        override fun onAdRefreshed(): Unit = MainPost.run { d.onAdRefreshed() }
        override fun onAdFailedToRefresh(adError: NgLoadAdError): Unit = MainPost.run { d.onAdFailedToRefresh(adError) }
    }
}
