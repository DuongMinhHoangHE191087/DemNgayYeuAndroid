package com.app.plugin.nextgen

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.View
import android.view.WindowManager
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.ads.MainPost
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.ads.adsmob.AdsMobPlBanner
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import org.mockito.Answers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.mockito.stubbing.Answer

/**
 * 2.4.0 NG2: bộ đồ test banner/rect Next-Gen dùng chung (NgBannerTest, NgBannerLateTest). `BannerAd.load` qua seam
 * [AdsMobPlBanner.loader] (ghi request + callback đã bọc `MainPost.load`); mỗi `BannerAd` giả trả một `View` giả riêng.
 */
internal class NgBannerKit {
    val requests = ArrayList<Pair<BannerAdRequest, AdLoadCallback<BannerAd>>>()
    val ads = ArrayList<BannerAd>()
    val posted = ArrayDeque<Runnable>()
    private val savedLoader = AdsMobPlBanner.loader

    class Cb : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    fun install() {
        sdkReady(true)
        AdsMobPlBanner.loader = { r, cb -> requests += r to cb }
        MainPost.isMain = { true }
    }

    fun uninstall() {
        AdsMobPlBanner.loader = savedLoader
        sdkReady(false)
        MainPost.resetForTest()
    }

    fun background() {
        MainPost.isMain = { false }
        MainPost.poster = { r -> posted.addLast(r) }
    }

    fun drain() { while (posted.isNotEmpty()) posted.removeFirst().run() }

    /** Trạng thái init của `AdsMobMy.instance` (NG1, private): coi như đã sẵn sàng trong test, rồi trả lại. */
    private fun sdkReady(ready: Boolean) {
        val my = AdsMobMy.instance
        AdsMobMy::class.java.getDeclaredField("state").apply { isAccessible = true }.setInt(my, if (ready) 1 else 0)
        (AdsMobMy::class.java.getDeclaredField("queue").apply { isAccessible = true }.get(my) as MutableCollection<*>).clear()
    }

    /** `BannerAd` giả cho request thứ [i], view riêng; trả lời thành công (callback đã bọc). */
    fun loaded(i: Int): BannerAd = newAd().also { requests[i].second.onAdLoaded(it) }

    fun failed(i: Int) = requests[i].second.onAdFailedToLoad(error())

    fun newAd(): BannerAd {
        val view = mock(View::class.java)
        val ad = mock(BannerAd::class.java, Answer { if (it.method.name == "getView") view else Answers.RETURNS_DEFAULTS.answer(it) })
        ads += ad
        views[ad] = view
        return ad
    }

    private val views = HashMap<BannerAd, View>()

    fun viewOf(ad: BannerAd): View = views.getValue(ad)

    /** Refresh callback (đã bọc `MainPost.bannerRefresh`) mà placement gắn vào [ad]. */
    fun refresh(ad: BannerAd): BannerAdRefreshCallback =
        mockingDetails(ad).invocations.last { it.method.name == "setBannerAdRefreshCallback" }.arguments[0] as BannerAdRefreshCallback

    fun destroyed(ad: BannerAd) = mockingDetails(ad).invocations.count { it.method.name == "destroy" }

    companion object {
        /** `LoadAdError` giả có mã + thông điệp (Kotlin non-null) như SDK gửi. */
        fun error(): LoadAdError = mock(LoadAdError::class.java).also {
            `when`(it.code).thenReturn(LoadAdError.ErrorCode.values()[0])
            `when`(it.message).thenReturn("no fill")
        }

        const val UNIT = "ca-app-pub-3940256099942544/9214589741"
        const val PKG = "com.example.app"

        /** Context có `ads_collapsible_placements` = [collapsible] (`null` = không khai), `admob_banner` = 2 slot; [denied] = TCF không đồng ý. */
        fun context(collapsible: Array<String>? = null, denied: Boolean = false): Context {
            val ctx = mock(Context::class.java)
            val res = mock(Resources::class.java)
            `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
            if (collapsible != null) {
                `when`(res.getIdentifier("ads_collapsible_placements", "array", PKG)).thenReturn(11)
                `when`(res.getStringArray(11)).thenReturn(collapsible)
            }
            `when`(res.getIdentifier("admob_banner", "string", PKG)).thenReturn(12)
            `when`(res.getString(12)).thenReturn("bn_home,$UNIT#bn_settings,$UNIT")
            `when`(ctx.resources).thenReturn(res)
            `when`(ctx.packageName).thenReturn(PKG)
            val prefs = mock(SharedPreferences::class.java)
            if (denied) {
                `when`(prefs.contains("IABTCF_gdprApplies")).thenReturn(true)
                `when`(prefs.getInt("IABTCF_gdprApplies", 0)).thenReturn(1)
                `when`(prefs.getString("IABTCF_PurposeConsents", null)).thenReturn("0111111111")
            }
            `when`(ctx.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
            return ctx
        }

        /** Activity rộng 1080 px, density 3 → 360 dp (đường `defaultDisplay.getMetrics` của Legacy, SDK_INT JVM = 0). */
        fun activity(): Activity {
            val activity = mock(Activity::class.java)
            val display = mock(Display::class.java)
            doAnswer { (it.arguments[0] as DisplayMetrics).widthPixels = 1080; null }.`when`(display).getMetrics(any())
            val wm = mock(WindowManager::class.java)
            `when`(wm.defaultDisplay).thenReturn(display)
            `when`(activity.windowManager).thenReturn(wm)
            val res = mock(Resources::class.java)
            `when`(res.displayMetrics).thenReturn(DisplayMetrics().apply { density = 3f; widthPixels = 1080; heightPixels = 2400 })
            `when`(res.configuration).thenReturn(Configuration().apply { orientation = Configuration.ORIENTATION_PORTRAIT })
            `when`(activity.resources).thenReturn(res)
            `when`(activity.applicationContext).thenReturn(activity)
            return activity
        }

        /** Extras Google của [bundle] giả: `putString` + `putAll` (gộp đệ quy từ Bundle giả nguồn). */
        fun extras(bundle: Bundle?): Map<String, String> {
            bundle ?: return emptyMap()
            val out = LinkedHashMap<String, String>()
            mockingDetails(bundle).invocations.forEach {
                when (it.method.name) {
                    "putString" -> out[it.arguments[0] as String] = it.arguments[1] as String
                    "putAll" -> out.putAll(extras(it.arguments[0] as Bundle?))
                }
            }
            return out
        }
    }
}
