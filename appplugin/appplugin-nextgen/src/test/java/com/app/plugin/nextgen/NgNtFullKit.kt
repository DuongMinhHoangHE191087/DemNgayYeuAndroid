package com.app.plugin.nextgen

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.DisplayMetrics
import com.app.plugin.ads.AdsBase
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.AdsPlacementEcmpBase
import com.app.plugin.ads.AdsShowCb
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlNtFull
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 2.4.0 NG5b: bộ đồ test Full2 (AdMob) nextgen trên seam nền (luật 36): `MainPost.isMain = { false }` + `poster` hàng FIFO
 * thủ công ([drain]); `NativeAdLoader.load` qua seam [AdsMobPlNtFull.loader]. Dùng chung cho NgNtFullChainTest,
 * NgNtFullOrderTest, NgNtFullHostDestroyTest (bản nextgen của ba test thứ tự Legacy).
 */
internal class NgNtFullKit {
    val posted = ArrayDeque<Runnable>()
    val requests = ArrayList<Pair<NativeAdRequest, NativeAdLoaderCallback>>()
    val events = mutableListOf<String>()
    val host: Activity = mock(Activity::class.java)
    private val savedLoader = AdsMobPlNtFull.loader

    val context: Context = mock(Context::class.java).also { ctx ->
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(res.displayMetrics).thenReturn(DisplayMetrics())
        `when`(ctx.resources).thenReturn(res)
        `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
    }

    val pl = AdsMobPlNtFull(context, mock(AdsBase::class.java), AdsIDDef.AdmobFullNt, "full_2_default")

    /** Callback show của lượt: ghi `showed`/`showFail`/`dismissed` vào [events]. */
    val showCb = object : AdsShowCb {
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed" }
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "showFail" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed" }
    }

    fun install(): NgNtFullKit {
        sdkReady(true)
        AdsMobPlNtFull.loader = { r, cb -> requests += r to cb }
        MainPost.isMain = { false }
        MainPost.poster = { r -> posted.addLast(r) }
        return this
    }

    fun uninstall() {
        AdsMobPlNtFull.loader = savedLoader
        sdkReady(false)
        MainPost.resetForTest()
    }

    fun drain() { while (posted.isNotEmpty()) posted.removeFirst().run() }

    /** Trạng thái init của `AdsMobMy.instance` (NG1, private): coi như đã sẵn sàng trong test, rồi trả lại. */
    private fun sdkReady(ready: Boolean) {
        val my = AdsMobMy.instance
        AdsMobMy::class.java.getDeclaredField("state").apply { isAccessible = true }.setInt(my, if (ready) 1 else 0)
        (AdsMobMy::class.java.getDeclaredField("queue").apply { isAccessible = true }.get(my) as MutableCollection<*>).clear()
    }

    /** Nạp qua đường thật: `load` → seam loader; callback (đã bọc `MainPost.nativeLoader`) gọi "từ luồng nền", chưa xả. */
    fun loadFromBackground(ad: NativeAd = mock(NativeAd::class.java)): NativeAd {
        pl.setActivity(host)
        pl.parserEcpms(UNIT)
        pl.load("full_2_default", null)
        requests.last().second.onNativeAdLoaded(ad)
        return ad
    }

    /** [loadFromBackground] rồi xả hàng main: quảng cáo đã nạp trong mục. */
    fun loaded(): NativeAd = loadFromBackground().also { drain() }

    fun own(name: String) = AdsMobPlNtFull::class.java.getDeclaredField(name).apply { isAccessible = true }
    fun slot() = AdsPlBase::class.java.getDeclaredField("adsPlacementEcmp").apply { isAccessible = true }.get(pl) as AdsPlacementEcmpBase
    fun showWidthDialog(): Any? = AdsMobPlNtFull::class.java.getDeclaredMethod("showWidthDialog").apply { isAccessible = true }.invoke(pl)

    companion object {
        const val UNIT = "ca-app-pub-3940256099942544/2247696110"
        fun field(cls: Class<*>, name: String) = cls.getDeclaredField(name).apply { isAccessible = true }
    }
}
