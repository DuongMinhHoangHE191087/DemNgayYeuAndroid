package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Test đặc tả vùng native full của [AdsHelper] (plan 1.5.0 Task 7, KB-2): viết và chạy xanh trên mã CŨ trước khi
 * chuyển sang `ads.core.NativeFullController`, làm lưới cho bước chuyển. Mọi lời gọi đi qua hàm public của façade
 * (`getNativeFullForShow`, `destroyNativeFull`, `loadNativeFullForThisTurn`, `clearActivity`); map `ntFullMapView`
 * chỉ quan sát qua hành vi (`destroyNativeFull` có/không `destroyAd`), không đọc bằng reflection vì field đó rời
 * façade. Mạng native full giả (AdMob, đứng đầu danh sách khi `flagShow == 1`) trả quảng cáo theo placement từ
 * [NtFullNetwork.cached] và giữ inner callback của `loadAd`/`getAdForShow`. `clearActivity` gọi
 * `removeCallbacksAndMessages(null)` nên handler ảo chạy với `honorRemoveAll = true` (review Task 6 m3).
 */
class NativeFullControllerTest {

    private class NtFullNetwork(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullNt, context) {
        override val eventTag: String get() = "test_nt_full"
        val cached = mutableMapOf<String, () -> Any?>()
        val shows = mutableListOf<String>()
        val showCbs = mutableListOf<AdsShowCb>()
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_nt_full_data"
        override fun getKeyForResDf(): String = "test_nt_full_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
        override fun getAdForShow(placement: String, adsShowCb: AdsShowCb): Any? {
            shows += placement
            showCbs += adsShowCb
            return cached[placement]?.invoke()
        }
    }

    private class AppShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "showFail:$placement:$err" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed:$placement:$flag" }
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) { events += "impression:$placement" }
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) { events += "click:$placement" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed:$placement" }
    }

    private class AppLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    /** Một quảng cáo native full sẵn để hiện: view, đối tượng quảng cáo, và `AdsPlBase` để kiểm `destroyAd`. */
    private class Ad {
        val view: View = mock(View::class.java)
        val adObject = Any()
        val pl: AdsPlBase = mock(AdsPlBase::class.java)
        val ecmp: AdsEcmp = mock(AdsEcmp::class.java).also { `when`(it.getAdsPl()).thenReturn(pl) }
        fun forShow() = ObjectForShowAdWithViewParent().apply {
            viewCurrAttach = view; adLoad = adObject; adsEcmp = ecmp
        }
    }

    private class Fixture(val helper: AdsHelper, val net: NtFullNetwork, val timers: MainHandlerTimers) {
        fun plcf() = helper.getAdsPlacementConfig("full_home", 1)!!
    }

    /** `AdsHelper` một mạng native full (AdMob), dựng trong [MainHandlerTimers.run] để `Handler.post` của vòng load chạy tay. */
    private fun withNtFull(check: Fixture.() -> Unit) = MainHandlerTimers.run(honorRemoveAll = true) { timers ->
        val helper = AdsKillSwitchTest.newAdsHelper()
        val net = NtFullNetwork(networkContext())
        setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullNt to net))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>(net))
        Fixture(helper, net, timers).check()
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

    @Test fun `getNativeFullForShow tra viewCurrAttach, chuyen callback nguyen ven - destroyNativeFull destroyAd mot lan, lan hai no-op`() = withNtFull {
        val ad = Ad()
        net.cached["full_home"] = { ad.forShow() }
        val cb = AppShowCb()

        val view = helper.getNativeFullForShow("home", cb)

        assertSame("trả đúng viewCurrAttach của quảng cáo", ad.view, view)
        assertEquals("placement có tiền tố full_", listOf("full_home"), net.shows)
        assertEquals("không load mạng", 0, net.loads.size)
        assertEquals("chưa có callback nào trước khi mạng báo", emptyList<String>(), cb.events)
        // Callback trung gian chuyển nguyên 5 sự kiện, đúng tham số, mỗi cái một lần.
        val inner = net.showCbs.single()
        inner.onAdShowed("full_home", "full_home", "id", 7)
        inner.onAdImpression("full_home", "full_home", "id")
        inner.onAdClick("full_home", "full_home", "id")
        inner.onAdDismissed("full_home", "full_home", "id")
        inner.onAdShowFail("full_home", "full_home", "id", "boom")
        assertEquals(
            listOf("showed:full_home:7", "impression:full_home", "click:full_home", "dismissed:full_home", "showFail:full_home:boom"),
            cb.events,
        )

        helper.destroyNativeFull(null)
        helper.destroyNativeFull(mock(View::class.java))
        verify(ad.pl, never()).destroyAd(ad.adObject)

        helper.destroyNativeFull(view)
        verify(ad.pl, times(1)).destroyAd(ad.adObject)
        helper.destroyNativeFull(view)
        verify(ad.pl, times(1)).destroyAd(ad.adObject)
    }

    @Test fun `khong mang nao co quang cao ma plcf isLoaded - ha co, nap lai dung mot lan, cb khong nhan gi`() = withNtFull {
        val cb = AppShowCb()

        // Cờ chưa bật: không nạp lại.
        assertNull(helper.getNativeFullForShow("home", cb))
        assertEquals(0, net.loads.size)

        // Cờ bật mà mạng không còn quảng cáo: hạ cờ, loadNativeFullForThisTurn gọi mạng đúng một lần.
        plcf().isLoaded = true
        assertNull(helper.getNativeFullForShow("home", cb))
        assertFalse("cờ đã load phải hạ", plcf().isLoaded)
        assertTrue("placement đang load", plcf().isLoading)
        assertEquals("mạng được gọi đúng một lần", 1, net.loads.size)
        assertEquals(listOf("full_home", "full_home"), net.shows)

        // Gọi lại: cờ đã hạ, đang load → không nạp thêm.
        assertNull(helper.getNativeFullForShow("home", cb))
        assertEquals(1, net.loads.size)

        // Mạng lỗi: vòng đi hết danh sách (FB, MAX không có trong listAdsFull) rồi dừng, không gọi lại mạng.
        net.loads[0].onLoadFailed("full_home", "full_home", "id", "no fill")
        timers.runPosted()
        assertFalse(plcf().isLoading)
        assertEquals(1, net.loads.size)
        assertEquals("app không truyền cb show nào vào đường load", emptyList<String>(), cb.events)
    }

    /** Hook `clearActivity` (R4): map view → quảng cáo bị xoá, nên `destroyNativeFull` sau đó không `destroyAd`. */
    @Test fun `clearActivity xoa map native full - destroyNativeFull sau do no-op`() = withNtFull {
        val ad = Ad()
        net.cached["full_home"] = { ad.forShow() }
        val view = helper.getNativeFullForShow("home", AppShowCb())
        assertSame(ad.view, view)

        helper.clearActivity()

        helper.destroyNativeFull(view)
        verify(ad.pl, never()).destroyAd(ad.adObject)
        // Map rỗng không chặn lượt sau: lấy quảng cáo mới rồi huỷ được như thường.
        assertSame(ad.view, helper.getNativeFullForShow("home", AppShowCb()))
        helper.destroyNativeFull(view)
        verify(ad.pl, times(1)).destroyAd(ad.adObject)
    }

    /**
     * Callback load của app (plan Task 15 bước 0 (e), viết sớm ở Task 8 bước A — review Task 7 m1): mỗi lượt của app
     * nhận đúng một callback kết thúc; callback muộn của lượt đã bỏ (`loadCycle` đã tăng) không tới app và không chạm
     * `isLoaded`/`isLoading`. Lưới cho bước Task 15 bỏ getter-null `cbLoad`.
     */
    @Test fun `loadNativeFullForThisTurn - app nhan dung mot callback moi luot, luot cu bi bo khong toi app`() = withNtFull {
        // Lượt 1: mạng báo loaded → app nhận đúng ["loaded"].
        val cb1 = AppLoadCb()
        helper.loadNativeFullForThisTurn("home", cb1)
        timers.runPosted()
        assertEquals("lượt 1 gọi mạng đúng một lần", 1, net.loads.size)
        assertTrue(plcf().isLoading)
        net.loads[0].onLoaded("full_home", "full_home", "id", null, null)
        timers.runPosted()
        assertEquals(listOf("loaded"), cb1.events)
        assertTrue(plcf().isLoaded)
        assertFalse(plcf().isLoading)

        // Lượt 2: mạng lỗi, vòng đi hết danh sách (FB, MAX không có) → app nhận đúng ["fail:list empty"].
        val cb2 = AppLoadCb()
        helper.loadNativeFullForThisTurn("home", cb2)
        timers.runPosted()
        assertEquals("lượt 2 gọi mạng đúng một lần", 2, net.loads.size)
        net.loads[1].onLoadFailed("full_home", "full_home", "id", "no fill")
        timers.runPosted()
        assertEquals(listOf("fail:list empty"), cb2.events)
        assertEquals("lượt 1 không nhận thêm gì", listOf("loaded"), cb1.events)
        assertFalse(plcf().isLoading)
        assertEquals("không gọi lại mạng", 2, net.loads.size)
    }

    @Test fun `loadNativeFullForThisTurn - callback muon cua luot da bo (loadCycle tang) khong toi app, isLoaded khong doi`() = withNtFull {
        val cb = AppLoadCb()
        helper.loadNativeFullForThisTurn("home", cb)
        timers.runPosted()
        assertEquals(1, net.loads.size)
        val staleInner = net.loads[0]
        assertFalse(plcf().isLoaded)

        // Bỏ lượt 1: loadCycle tăng (setter isLoading bật tăng loadCycle, như khi một lượt mới bắt đầu).
        plcf().isLoading = true

        staleInner.onLoaded("full_home", "full_home", "id", null, null)
        timers.runPosted()
        assertEquals("onLoaded muộn không tới app", emptyList<String>(), cb.events)
        assertFalse("onLoaded muộn không bật isLoaded", plcf().isLoaded)
        assertTrue("onLoaded muộn không hạ isLoading của lượt mới", plcf().isLoading)

        staleInner.onLoadFailed("full_home", "full_home", "id", "late fail")
        timers.runPosted()
        assertEquals("onLoadFailed muộn không tới app", emptyList<String>(), cb.events)
        assertFalse(plcf().isLoaded)
        assertTrue(plcf().isLoading)
        assertEquals("onLoadFailed muộn không nạp lại mạng", 1, net.loads.size)

        // Lượt mới cũng đã kết thúc (isLoading hạ): callback muộn của lượt 1 vẫn không tới app, không đổi cờ.
        plcf().isLoading = false
        staleInner.onLoadFailed("full_home", "full_home", "id", "late fail 2")
        staleInner.onLoaded("full_home", "full_home", "id", null, null)
        timers.runPosted()
        assertEquals("callback muộn không tới app khi lượt mới đã kết thúc", emptyList<String>(), cb.events)
        assertFalse(plcf().isLoaded)
        assertFalse(plcf().isLoading)
        assertEquals(1, net.loads.size)
    }
}
