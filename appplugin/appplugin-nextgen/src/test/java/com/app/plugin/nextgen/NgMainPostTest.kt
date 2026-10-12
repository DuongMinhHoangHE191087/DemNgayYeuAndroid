package com.app.plugin.nextgen

import com.app.plugin.ads.MainPost
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.VideoController
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.rewardedinterstitial.RewardedInterstitialAdEventCallback
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * 2.4.0 NG1 NM1 (Hợp đồng B "Luồng", luật 36): mọi decorator Next-Gen của [MainPost] — `load`, sự kiện từng định dạng
 * (gồm `onAdPaid`), `nativeLoader`, `video`, `earned` — chạy thân qua `poster` khi callback tới trên luồng nền
 * (`isMain = false`, hàng FIFO thủ công), chạy ngay khi đang ở main. Không dùng Looper thật.
 */
class NgMainPostTest {
    private val order = ArrayList<String>()
    private val posted = ArrayDeque<Runnable>()

    @After fun tearDown() = MainPost.resetForTest()

    private fun common(tag: String) = object : AdEventCallback {
        override fun onAdShowedFullScreenContent() { order += "$tag.showed" }
        override fun onAdDismissedFullScreenContent() { order += "$tag.dismissed" }
        override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) { order += "$tag.failedShow" }
        override fun onAdImpression() { order += "$tag.impression" }
        override fun onAdClicked() { order += "$tag.clicked" }
        override fun onAdPaid(value: AdValue) { order += "$tag.paid" }
    }

    /** Gọi đủ 6 sự kiện chung của [cb], mỗi lần ghi tên mong đợi vào [expect]. */
    private fun fireCommon(tag: String, cb: AdEventCallback, expect: MutableList<String>) {
        cb.onAdShowedFullScreenContent(); cb.onAdDismissedFullScreenContent()
        cb.onAdFailedToShowFullScreenContent(mock(FullScreenContentError::class.java)); cb.onAdImpression(); cb.onAdClicked()
        cb.onAdPaid(mock(AdValue::class.java))
        expect += listOf("showed", "dismissed", "failedShow", "impression", "clicked", "paid").map { "$tag.$it" }
    }

    /** Dựng mọi decorator, bắn mọi callback; trả danh sách tên theo thứ tự bắn. */
    private fun fireAll(): List<String> {
        val expect = ArrayList<String>()
        val load = MainPost.load(object : AdLoadCallback<InterstitialAd> {
            override fun onAdLoaded(ad: InterstitialAd) { order += "load.loaded" }
            override fun onAdFailedToLoad(adError: LoadAdError) { order += "load.failed" }
        })
        load.onAdLoaded(mock(InterstitialAd::class.java)); load.onAdFailedToLoad(mock(LoadAdError::class.java))
        expect += listOf("load.loaded", "load.failed")

        val c = common("is")
        val inter = MainPost.interstitialEvents(object : InterstitialAdEventCallback, AdEventCallback by c {
            override fun onAppEvent(name: String, data: String?) { order += "is.appEvent" }
        })
        fireCommon("is", inter, expect); inter.onAppEvent("n", "d"); expect += "is.appEvent"
        val ao = MainPost.appOpenEvents(object : AppOpenAdEventCallback, AdEventCallback by common("ao") {})
        fireCommon("ao", ao, expect)
        val rw = MainPost.rewardedEvents(object : RewardedAdEventCallback, AdEventCallback by common("rw") {
            override fun onAdMetadataChanged() { order += "rw.meta" }
        })
        fireCommon("rw", rw, expect); rw.onAdMetadataChanged(); expect += "rw.meta"
        val rwi = MainPost.rwiEvents(object : RewardedInterstitialAdEventCallback, AdEventCallback by common("rwi") {
            override fun onAdMetadataChanged() { order += "rwi.meta" }
        })
        fireCommon("rwi", rwi, expect); rwi.onAdMetadataChanged(); expect += "rwi.meta"
        val bn = MainPost.bannerEvents(object : BannerAdEventCallback, AdEventCallback by common("bn") {
            override fun onAppEvent(name: String, data: String?) { order += "bn.appEvent" }
        })
        fireCommon("bn", bn, expect); bn.onAppEvent("n", null); expect += "bn.appEvent"
        val nt = MainPost.nativeEvents(object : NativeAdEventCallback, AdEventCallback by common("nt") {
            override fun onAdSwipeGestureClicked() { order += "nt.swipe" }
            override fun onCustomMuteThisAdReported() { order += "nt.mute" }
        })
        fireCommon("nt", nt, expect); nt.onAdSwipeGestureClicked(); nt.onCustomMuteThisAdReported(); expect += listOf("nt.swipe", "nt.mute")

        val nl = MainPost.nativeLoader(object : NativeAdLoaderCallback {
            override fun onNativeAdLoaded(nativeAd: NativeAd) { order += "nl.loaded" }
            override fun onAdFailedToLoad(adError: LoadAdError) { order += "nl.failed" }
            override fun onAdLoadingCompleted() { order += "nl.completed" }
        })
        nl.onNativeAdLoaded(mock(NativeAd::class.java)); nl.onAdFailedToLoad(mock(LoadAdError::class.java)); nl.onAdLoadingCompleted()
        expect += listOf("nl.loaded", "nl.failed", "nl.completed")

        val vd = MainPost.video(object : VideoController.VideoLifecycleCallbacks {
            override fun onVideoStart() { order += "vd.start" }
            override fun onVideoPlay() { order += "vd.play" }
            override fun onVideoPause() { order += "vd.pause" }
            override fun onVideoEnd() { order += "vd.end" }
            override fun onVideoMute(muted: Boolean) { order += "vd.mute" }
        })
        vd.onVideoStart(); vd.onVideoPlay(); vd.onVideoPause(); vd.onVideoEnd(); vd.onVideoMute(true)
        expect += listOf("vd.start", "vd.play", "vd.pause", "vd.end", "vd.mute")

        val earned = MainPost.earned(OnUserEarnedRewardListener { order += "earned" })
        earned.onUserEarnedReward(mock(RewardItem::class.java)); expect += "earned"

        val refresh = MainPost.bannerRefresh(object : BannerAdRefreshCallback {   // banner tự làm mới (fix round I1)
            override fun onAdRefreshed() { order += "bn.refreshed" }
            override fun onAdFailedToRefresh(adError: LoadAdError) { order += "bn.refreshFailed" }
        })
        refresh.onAdRefreshed(); refresh.onAdFailedToRefresh(mock(LoadAdError::class.java)); expect += listOf("bn.refreshed", "bn.refreshFailed")
        return expect
    }

    @Test
    fun `NM1 callback nen di qua poster, thu tu FIFO giu nguyen, 0 chay thang tren luong goi`() {
        MainPost.isMain = { false }
        MainPost.poster = { r -> posted.addLast(r) }
        val expect = fireAll()
        assertEquals("0 thân chạy thẳng trên luồng nền: $order", emptyList<String>(), order)
        assertEquals("mỗi callback đúng một lần post", expect.size, posted.size)
        while (posted.isNotEmpty()) posted.removeFirst().run()
        assertEquals(expect, order)
    }

    @Test
    fun `NM1b dang o main thi chay ngay, khong post`() {
        MainPost.isMain = { true }
        MainPost.poster = { r -> posted.addLast(r) }
        val expect = fireAll()
        assertTrue("0 post khi ở main", posted.isEmpty())
        assertEquals(expect, order)
    }
}
