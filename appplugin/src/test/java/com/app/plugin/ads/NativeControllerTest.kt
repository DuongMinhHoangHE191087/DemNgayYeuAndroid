package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import com.app.plugin.until.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Test đặc tả vùng native của [AdsHelper] (plan 1.5.0 Task 5, KB-2): viết và chạy xanh trên mã CŨ trước khi chuyển
 * sang `ads.core.NativeController`, làm lưới cho bước chuyển. Mọi lời gọi đi qua hàm public của façade (`showNative`,
 * `loadNative4Show`, `hideNative`, `clearActivity`); map `nativeMapView` chỉ quan sát qua hành vi (sau khi gỡ,
 * `showNative` lần nữa gắn view lại thay vì đi nhánh "đã gắn"), không đọc bằng reflection vì field đó rời façade.
 * Mạng native giả trả quảng cáo theo placement từ [NativeNetwork.cached] và giữ inner callback của `loadAd`.
 */
class NativeControllerTest {

    private class NativeNetwork(context: Context) : AdsBaseNative(AdsIDDef.AdmobNative, context) {
        override val eventTag: String get() = "test_native"
        val cached = mutableMapOf<String, () -> Any?>()
        val shows = mutableListOf<String>()
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_native_data"
        override fun getKeyForResDf(): String = "test_native_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
        override fun getAdForShow(placement: String, adsShowCb: AdsShowCb): Any? {
            shows += placement
            return cached[placement]?.invoke()
        }
    }

    private class AppLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    private class AppShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "showFail:$err" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed" }
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) { events += "impression" }
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) { events += "click" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed" }
    }

    /** Một quảng cáo native sẵn để hiện: view, đối tượng quảng cáo, và `AdsPlBase` để kiểm `destroyAd`. */
    private class Ad {
        val view: View = mock(View::class.java)
        val adObject = Any()
        val pl: AdsPlBase = mock(AdsPlBase::class.java)
        val ecmp: AdsEcmp = mock(AdsEcmp::class.java).also { `when`(it.getAdsPl()).thenReturn(pl) }
        fun forShow() = ObjectForShowAdWithViewParent().apply {
            viewCurrAttach = view; adLoad = adObject; adsEcmp = ecmp
        }
    }

    private class Fixture(val helper: AdsHelper, val native: NativeNetwork, val timers: MainHandlerTimers)

    /** `AdsHelper` một mạng native (AdMob), dựng trong [MainHandlerTimers.run] để `Handler.post` của vòng load chạy tay. */
    private fun withNative(stepList: Boolean = true, check: Fixture.() -> Unit) = MainHandlerTimers.run { timers ->
        val helper = AdsKillSwitchTest.newAdsHelper()
        if (stepList) helper.currConfig.nativeStepList.add(AdsIDDef.AdmobNative)
        val native = NativeNetwork(networkContext())
        setField(helper, "listAdsNative", mutableMapOf<Int, AdsBaseNative>(AdsIDDef.AdmobNative to native))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>(native))
        Fixture(helper, native, timers).check()
    }

    /** Context đủ cho `AdsBase` dựng (resources + SharedPreferences), như `LoadCycleGuardTest.setUp`. */
    private fun networkContext(): Context {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(resources.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(context.resources).thenReturn(resources)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(mock(SharedPreferences::class.java))
        return context
    }

    private fun setField(helper: AdsHelper, name: String, value: Any?) {
        AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true }.set(helper, value)
    }

    @Test fun `showNative lan hai cung viewParent cung placement da gan - chi doi callback, khong goi mang`() = withNative {
        val parent = mock(ViewGroup::class.java)
        val ad = Ad()
        native.cached["nt_home"] = { ad.forShow() }
        val cb1 = AppShowCb()
        val cb2 = AppShowCb()

        helper.showNative("home", parent, cb1)
        verify(parent, times(1)).addView(ad.view)
        assertEquals(listOf("showed"), cb1.events)
        assertEquals(listOf("nt_home"), native.shows)

        helper.showNative("home", parent, cb2)

        assertEquals("không lấy quảng cáo lần hai", listOf("nt_home"), native.shows)
        assertEquals("không load mạng", 0, native.loads.size)
        verify(parent, times(1)).addView(ad.view)
        verify(parent, times(1)).removeAllViews()
        verify(ad.pl, never()).destroyAd(ad.adObject)
        assertSame("plcf.cbShow là callback mới", cb2, helper.getAdsPlacementConfig("nt_home", 1)!!.cbShow)
        assertEquals(listOf("showed"), cb1.events)
        assertEquals(emptyList<String>(), cb2.events)
    }

    @Test fun `loadNative4Show het danh sach - onLoadFailed list empty dung mot lan`() = withNative {
        val cb = AppLoadCb()

        helper.loadNative4Show("home", cb)
        assertEquals("một lượt load mạng", 1, native.loads.size)
        native.loads[0].onLoadFailed("nt_home", "nt_home", "id-nt", "no fill")
        assertEquals("chưa báo trước khi Handler.post chạy", emptyList<String>(), cb.events)
        timers.runPosted()

        assertEquals(listOf("fail:list empty"), cb.events)
        assertFalse(helper.getAdsPlacementConfig("nt_home", 1)!!.isLoading)
        assertEquals("không gọi mạng lần hai", 1, native.loads.size)
    }

    @Test fun `nativeStepList rong - loadNative4Show bao list empty dung mot lan, khong goi mang`() = withNative(stepList = false) {
        val cb = AppLoadCb()

        helper.loadNative4Show("home", cb)
        timers.runPosted()

        assertEquals(listOf("fail:list empty"), cb.events)
        assertEquals(0, native.loads.size)
    }

    @Test fun `showNative khong co quang cao - load, het danh sach thi cbShow onAdShowFail list empty dung mot lan`() = withNative {
        val parent = mock(ViewGroup::class.java)
        val cb = AppShowCb()

        helper.showNative("home", parent, cb)
        assertEquals("một lượt load mạng", 1, native.loads.size)
        native.loads[0].onLoadFailed("nt_home", "nt_home", "id-nt", "no fill")
        timers.runPosted()

        assertEquals(listOf("showFail:list empty"), cb.events)
        verify(parent, never()).addView(any(View::class.java))
    }

    @Test fun `native reuse - dung quang cao cua nguon khi nguon khong dang hien, bo qua khi nguon dang hien`() = withNative {
        val donor = mock(AdsPlBase::class.java)
        native.mapAdsPl = mutableMapOf("nt_language" to donor)
        `when`(donor.hasFreshAdForReuse()).thenReturn(true)
        val reused = Ad()
        `when`(donor.getAdForShow(eq("nt_home") ?: "nt_home", any(AdsShowCb::class.java)))
            .thenAnswer { reused.forShow() }
        helper.optimizationPolicy.setNativeReuseSources("nt_home", listOf("nt_language"))

        // Nguồn nt_language không hiện: nt_home lấy quảng cáo của nguồn, không load.
        val homeParent = mock(ViewGroup::class.java)
        helper.showNative("home", homeParent, AppShowCb())
        verify(homeParent, times(1)).addView(reused.view)
        assertEquals(0, native.loads.size)

        // Nguồn nt_language đang hiện ở container khác: nt_home (container mới) không lấy của nguồn mà load.
        val language = Ad()
        native.cached["nt_language"] = { language.forShow() }
        val languageParent = mock(ViewGroup::class.java)
        helper.showNative("language", languageParent, AppShowCb())
        verify(languageParent, times(1)).addView(language.view)
        val otherParent = mock(ViewGroup::class.java)
        helper.showNative("home", otherParent, AppShowCb())
        verify(otherParent, never()).addView(any(View::class.java))
        verify(donor, times(1)).getAdForShow(eq("nt_home") ?: "nt_home", any(AdsShowCb::class.java))
        assertEquals("nguồn đang hiện: load mạng", 1, native.loads.size)
    }

    /**
     * Chặn crash 1.4.0 `e67b3da` (review Task 5 m1): quảng cáo trả về không có view để gắn thì báo đúng một
     * `onAdShowFail(VIEW_ATTACH_NULL_IN_CALL_BACK)`, không `addView(null)`, và mục không bị đánh dấu "đã gắn".
     */
    @Test fun `showNative quang cao khong co view - showFail view_attach_null mot lan, khong addView, lan sau lay lai`() = withNative {
        val parent = mock(ViewGroup::class.java)
        val ad = Ad()
        native.cached["nt_home"] = { ad.forShow().apply { viewCurrAttach = null } }
        val cb = AppShowCb()

        helper.showNative("home", parent, cb)

        assertEquals(listOf("showFail:${Constants.AdShowFailReason.VIEW_ATTACH_NULL_IN_CALL_BACK}"), cb.events)
        verify(parent, never()).addView(any(View::class.java))
        verify(parent, never()).addView(isNull())
        assertEquals("không load mạng", 0, native.loads.size)

        // isAttach được trả về false: showNative lần nữa cùng parent/placement lấy quảng cáo lại, không đi nhánh "đã gắn".
        helper.showNative("home", parent, AppShowCb())
        assertEquals(listOf("nt_home", "nt_home"), native.shows)
        verify(parent, never()).addView(any(View::class.java))
    }

    @Test fun `hideNative isRemove go view va destroyAd, map het key - clearActivity destroyAd moi muc va xoa map`() = withNative {
        val p1 = mock(ViewGroup::class.java)
        val p2 = mock(ViewGroup::class.java)
        val ad1 = Ad()
        val ad2 = Ad()
        val queue = ArrayDeque(listOf(ad1, ad2, ad1, ad2)) // thứ tự lấy: p1, p2, p1 (sau hide), p2 (sau clear)
        native.cached["nt_home"] = { queue.removeFirst().forShow() }

        helper.showNative("home", p1, AppShowCb())
        helper.showNative("home", p2, AppShowCb())
        verify(p1, times(1)).addView(ad1.view)
        verify(p2, times(1)).addView(ad2.view)

        helper.hideNative(p1, true)
        verify(p1, times(1)).removeView(ad1.view)
        verify(ad1.pl, times(1)).destroyAd(ad1.adObject)
        // Map hết key p1: showNative lần nữa là mục mới (gắn view lại), không phải nhánh "đã gắn".
        helper.showNative("home", p1, AppShowCb())
        verify(p1, times(2)).addView(ad1.view)

        // clearActivity: destroyAd mỗi mục đúng một lần; map rỗng nên showNative p2 gắn lại thay vì chỉ đổi callback.
        setField(helper, "listAdsAll", mutableListOf<AdsBase>())
        helper.clearActivity()
        verify(ad1.pl, times(2)).destroyAd(ad1.adObject)
        verify(ad2.pl, times(1)).destroyAd(ad2.adObject)
        helper.showNative("home", p2, AppShowCb())
        verify(p2, times(2)).addView(ad2.view)
    }
}
