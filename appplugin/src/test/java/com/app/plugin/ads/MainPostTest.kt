package com.app.plugin.ads

import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
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
import org.mockito.Mockito.mock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 C3c (D47-2): callback GMA giao ngoài main đi qua MỘT poster; đang ở main thì chạy ngay, thứ tự giữ. */
class MainPostTest {
    @After
    fun tearDown() {
        MainPost.resetForTest()
    }

    @Test
    fun `P1 moi forwarder ngoai main chay qua poster khong chay thang tren luong goi`() {
        val posted = ArrayList<Runnable>()
        val order = ArrayList<String>()
        MainPost.isMain = { false }
        MainPost.poster = { r -> posted.add(r) }
        val err = mock(LoadAdError::class.java)
        val adErr = mock(AdError::class.java)
        val calls = ArrayList<Pair<String, () -> Unit>>()
        fun add(name: String, call: () -> Unit) { calls.add(name to call) }

        val al = MainPost.adListener(object : AdListener() {
            override fun onAdClosed() { order.add("al.closed") }
            override fun onAdFailedToLoad(e: LoadAdError) { order.add("al.failed") }
            override fun onAdOpened() { order.add("al.opened") }
            override fun onAdLoaded() { order.add("al.loaded") }
            override fun onAdClicked() { order.add("al.clicked") }
            override fun onAdImpression() { order.add("al.impression") }
            override fun onAdSwipeGestureClicked() { order.add("al.swipe") }
        })
        add("al.closed") { al.onAdClosed() }; add("al.failed") { al.onAdFailedToLoad(err) }
        add("al.opened") { al.onAdOpened() }; add("al.loaded") { al.onAdLoaded() }
        add("al.clicked") { al.onAdClicked() }; add("al.impression") { al.onAdImpression() }
        add("al.swipe") { al.onAdSwipeGestureClicked() }

        val fs = MainPost.fullScreen(object : FullScreenContentCallback() {
            override fun onAdFailedToShowFullScreenContent(e: AdError) { order.add("fs.failedShow") }
            override fun onAdShowedFullScreenContent() { order.add("fs.showed") }
            override fun onAdDismissedFullScreenContent() { order.add("fs.dismissed") }
            override fun onAdImpression() { order.add("fs.impression") }
            override fun onAdClicked() { order.add("fs.clicked") }
        })
        add("fs.failedShow") { fs.onAdFailedToShowFullScreenContent(adErr) }; add("fs.showed") { fs.onAdShowedFullScreenContent() }
        add("fs.dismissed") { fs.onAdDismissedFullScreenContent() }; add("fs.impression") { fs.onAdImpression() }
        add("fs.clicked") { fs.onAdClicked() }

        val vd = MainPost.video(object : VideoLifecycleCallbacks() {
            override fun onVideoStart() { order.add("vd.start") }
            override fun onVideoPlay() { order.add("vd.play") }
            override fun onVideoPause() { order.add("vd.pause") }
            override fun onVideoEnd() { order.add("vd.end") }
            override fun onVideoMute(muted: Boolean) { order.add("vd.mute") }
        })
        add("vd.start") { vd.onVideoStart() }; add("vd.play") { vd.onVideoPlay() }; add("vd.pause") { vd.onVideoPause() }
        add("vd.end") { vd.onVideoEnd() }; add("vd.mute") { vd.onVideoMute(true) }

        val il = MainPost.interstitialLoad(object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { order.add("il.loaded") }
            override fun onAdFailedToLoad(e: LoadAdError) { order.add("il.failed") }
        })
        add("il.loaded") { il.onAdLoaded(mock(InterstitialAd::class.java)) }; add("il.failed") { il.onAdFailedToLoad(err) }
        val rl = MainPost.rewardedLoad(object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { order.add("rl.loaded") }
            override fun onAdFailedToLoad(e: LoadAdError) { order.add("rl.failed") }
        })
        add("rl.loaded") { rl.onAdLoaded(mock(RewardedAd::class.java)) }; add("rl.failed") { rl.onAdFailedToLoad(err) }
        val wl = MainPost.rwiLoad(object : RewardedInterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedInterstitialAd) { order.add("wl.loaded") }
            override fun onAdFailedToLoad(e: LoadAdError) { order.add("wl.failed") }
        })
        add("wl.loaded") { wl.onAdLoaded(mock(RewardedInterstitialAd::class.java)) }; add("wl.failed") { wl.onAdFailedToLoad(err) }
        val ol = MainPost.appOpenLoad(object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) { order.add("ol.loaded") }
            override fun onAdFailedToLoad(e: LoadAdError) { order.add("ol.failed") }
        })
        add("ol.loaded") { ol.onAdLoaded(mock(AppOpenAd::class.java)) }; add("ol.failed") { ol.onAdFailedToLoad(err) }

        val paid = MainPost.paid { order.add("paid") }
        val earned = MainPost.earned { order.add("earned") }
        val native = MainPost.nativeLoaded { order.add("native") }
        add("paid") { paid.onPaidEvent(mock(AdValue::class.java)) }
        add("earned") { earned.onUserEarnedReward(mock(RewardItem::class.java)) }
        add("native") { native.onNativeAdLoaded(mock(NativeAd::class.java)) }

        assertEquals("số forwarder", 28, calls.size)
        calls.forEach { it.second() }

        assertTrue("không được chạy trên luồng gọi khi ngoài main: $order", order.isEmpty())
        assertEquals("mỗi forwarder post đúng một lần", 28, posted.size)
        posted.forEach { it.run() }
        assertEquals("phát lại đúng thứ tự gọi", calls.map { it.first }, order)
    }

    @Test
    fun `P2 tren main chay ngay va giu thu tu goi`() {
        var posts = 0
        val log = ArrayList<String>()
        MainPost.isMain = { true }
        MainPost.poster = { posts++ }
        val wrapped = MainPost.adListener(object : AdListener() {
            override fun onAdLoaded() {
                log.add("loaded")
            }

            override fun onAdImpression() {
                log.add("impression")
            }
        })

        wrapped.onAdLoaded()
        MainPost.run { log.add("block") }
        wrapped.onAdImpression()

        assertEquals(listOf("loaded", "block", "impression"), log)
        assertEquals("trên main không được post", 0, posts)
    }
}
