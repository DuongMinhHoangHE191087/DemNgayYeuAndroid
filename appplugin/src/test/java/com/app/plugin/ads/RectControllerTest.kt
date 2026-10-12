package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Test đặc tả vùng rect của [AdsHelper] (plan 1.5.0 Task 3, KB-2): viết và chạy xanh trên mã CŨ trước khi chuyển
 * sang `ads.core.RectController`, làm lưới cho bước chuyển. Mọi lời gọi đi qua hàm public của façade
 * (`showRect`, `loadRect4Show`, `hideRect`, `clearActivity`); map `rectMapView` chỉ quan sát qua hành vi (sau khi
 * gỡ, `showRect` lần nữa gắn view lại thay vì đi nhánh "đã gắn"), không đọc bằng reflection vì field đó rời façade.
 * Mạng rect giả theo mẫu `LoadCycleGuardTest.RectNetwork`, giữ inner callback của `loadAd` để test tự trả kết quả.
 */
class RectControllerTest {

    private class RectNetwork(context: Context, type: Int = AdsIDDef.AdmobRect) : AdsBaseRect(type, context) {
        override val eventTag: String get() = "test_rect"
        var cached: Any? = null
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_rect_data"
        override fun getKeyForResDf(): String = "test_rect_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
        override fun getAdForShow(placement: String, adsShowCb: AdsShowCb): Any? = cached
    }

    private class AppCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    private class Fixture(val helper: AdsHelper, val rect: RectNetwork, val timers: MainHandlerTimers)

    /** `AdsHelper` một mạng rect (AdMob), dựng trong [MainHandlerTimers.run] để watchdog placement không chạy thật. */
    private fun withRect(check: Fixture.() -> Unit) = MainHandlerTimers.run { timers ->
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.rectStepList.add(AdsIDDef.AdmobRect)
        val rect = RectNetwork(networkContext())
        setField(helper, "listAdsRect", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobRect to rect))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>(rect))
        Fixture(helper, rect, timers).check()
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

    private fun cachedRect(adView: View, ecmp: AdsEcmp = mock(AdsEcmp::class.java)) =
        ObjectForShowAdWithViewParent().apply { adLoad = adView; adsEcmp = ecmp }

    @Test fun `showRect co rect trong cache - gan view vao viewParent, onLoaded dung mot lan, khong load mang`() = withRect {
        val parent = mock(ViewGroup::class.java)
        val adView = mock(View::class.java)
        rect.cached = cachedRect(adView)
        val cb = AppCb()

        helper.showRect("home", parent, cb)

        verify(parent, times(1)).addView(adView)
        assertEquals(listOf("loaded"), cb.events)
        assertEquals("không gọi mạng khi có cache", 0, rect.loads.size)
    }

    @Test fun `showRect khong cache - load mang, onLoaded gan view, callback app dung mot lan, plcf isLoaded`() = withRect {
        val parent = mock(ViewGroup::class.java)
        `when`(parent.isAttachedToWindow).thenReturn(true)
        val adView = mock(View::class.java)
        val ecmp = mock(AdsEcmp::class.java)
        `when`(ecmp.adObject).thenReturn(adView)
        val cb = AppCb()

        helper.showRect("home", parent, cb)
        assertEquals("một lượt load mạng", 1, rect.loads.size)
        assertEquals("chưa callback trước khi mạng trả", emptyList<String>(), cb.events)
        rect.loads[0].onLoaded("rect_home", "rect_home", "id-rect", ecmp, null)

        verify(parent, times(1)).addView(adView) // BannerAttach.attach
        assertEquals(listOf("loaded"), cb.events)
        val plcf = helper.getAdsPlacementConfig("rect_home", 1)!!
        assertTrue(plcf.isLoaded)
        assertEquals(false, plcf.isLoading)
    }

    @Test fun `loadRect4Show khi dang loading - khong goi mang lan hai, callback thu hai gan vao luot dang chay`() = withRect {
        val cb1 = AppCb()
        val cb2 = AppCb()

        helper.loadRect4Show("home", cb1)
        helper.loadRect4Show("home", cb2)

        assertEquals("chỉ một lượt load mạng", 1, rect.loads.size)
        assertEquals(emptyList<String>(), cb1.events)
        assertEquals(emptyList<String>(), cb2.events)
        rect.loads[0].onLoaded("rect_home", "rect_home", "id-rect", null, null)
        assertEquals("cb1 (lượt đầu) đúng một lần", listOf("loaded"), cb1.events)
        assertEquals("cb2 gắn vào lượt đang chạy (O7 (a))", listOf("loaded"), cb2.events)
    }

    @Test fun `hideRect isRemove go view va huy ad, map het key - clearActivity detach tung view va xoa map`() = withRect {
        val parent = mock(ViewGroup::class.java)
        val adView = mock(View::class.java)
        val ecmp = mock(AdsEcmp::class.java)
        rect.cached = cachedRect(adView, ecmp)
        helper.showRect("home", parent, AppCb())
        verify(parent, times(1)).addView(adView)

        helper.hideRect(parent, true)
        verify(parent, times(1)).removeView(adView)
        verify(ecmp, times(1)).checkDestroyAd()
        // Map hết key: showRect lần nữa là mục mới (gắn view lại), không phải nhánh "đã gắn" (refreshRect, không addView).
        helper.showRect("home", parent, AppCb())
        verify(parent, times(2)).addView(adView)

        // clearActivity: detach view đang gắn (BannerAttach.detach đọc view.parent) và xoá map; listAdsAll rỗng không ném.
        setField(helper, "listAdsAll", mutableListOf<AdsBase>())
        `when`(adView.parent).thenReturn(parent)
        clearInvocations(parent)
        helper.clearActivity()
        verify(parent, times(1)).removeView(adView)
        verify(parent, never()).addView(adView)
        `when`(adView.parent).thenReturn(null)
        helper.showRect("home", parent, AppCb())
        verify(parent, times(1)).addView(adView)
    }

    // ═══ 1.5.0 Task 15 (review Task 3, m4): hai nhánh chuyển nguyên văn ở Task 3 chưa có test ═══

    /**
     * Nhánh `onLoadFailed` của vòng rect: mạng 1 lỗi khi lượt còn chạy → `Handler.post` sang mạng kế; mạng kế lỗi → hết
     * danh sách → app nhận đúng một `list empty`, hạ `isLoading`. Sau khi lượt đã load xong (`isLoading` hạ), lỗi tới sau
     * trên inner cb của lượt đó (view tự refresh lỗi) đi nhánh "refresh" và báo app `loadRectCircle onLoadFailed refresh`.
     */
    @Test fun `vong rect onLoadFailed - post sang mang ke roi list empty dung mot lan, loi sau khi da load di nhanh refresh`() = withRect {
        val rect2 = RectNetwork(networkContext(), AdsIDDef.MaxRect)
        helper.currConfig.rectStepList.add(AdsIDDef.MaxRect)
        setField(helper, "listAdsRect", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobRect to rect, AdsIDDef.MaxRect to rect2))
        val plcf = helper.getAdsPlacementConfig("rect_home", 1)!!
        val cb = AppCb()

        helper.loadRect4Show("home", cb)
        assertEquals(1, rect.loads.size)
        rect.loads[0].onLoadFailed("rect_home", "rect_home", "id-1", "no fill")
        assertEquals("bước kế đi qua Handler.post", 0, rect2.loads.size)
        timers.runPosted()
        assertEquals("mạng kế loadAd", 1, rect2.loads.size)
        assertTrue(plcf.isLoading)
        assertEquals(emptyList<String>(), cb.events)

        rect2.loads[0].onLoadFailed("rect_home", "rect_home", "id-2", "no fill")
        timers.runPosted()
        assertEquals(listOf("fail:list empty"), cb.events)
        assertFalse(plcf.isLoading)
        assertFalse(plcf.isLoaded)
        assertEquals("không gọi lại mạng", 1, rect.loads.size)

        // Lượt mới load được, rồi view của lượt đó refresh lỗi: nhánh "refresh" (isLoading đã hạ).
        val cb2 = AppCb()
        helper.loadRect4Show("home", cb2)
        rect.loads[1].onLoaded("rect_home", "rect_home", "id-1", null, null)
        assertEquals(listOf("loaded"), cb2.events)
        rect.loads[1].onLoadFailed("rect_home", "rect_home", "id-1", "refresh no fill")
        timers.runPosted()
        assertEquals(listOf("loaded", "fail:loadRectCircle onLoadFailed refresh"), cb2.events)
        assertEquals("nhánh refresh không đi tiếp waterfall", 1, rect2.loads.size)
        assertEquals("lượt trước không nhận thêm", listOf("fail:list empty"), cb.events)
    }

    /**
     * Nhánh destroy khi container bị gỡ khỏi cửa sổ trong lúc load: `onLoaded` báo app, rồi `destroyAd` quảng cáo vừa
     * nhận và đặt lại mục (`adObject = null`, `isLoaded = false`), không gắn view; mục của container không thành "đã
     * gắn", nên `showRect` lần sau trên cùng container load lại.
     */
    @Test fun `showRect container bi go trong luc load - onLoaded bao app, destroyAd, dat lai muc, khong gan view`() = withRect {
        val parent = mock(ViewGroup::class.java) // isAttachedToWindow == false
        val adView = mock(View::class.java)
        val ecmp = mock(AdsEcmp::class.java)
        `when`(ecmp.adObject).thenReturn(adView)
        val adPl = mock(AdsPlBase::class.java)
        val cb = AppCb()

        helper.showRect("home", parent, cb)
        assertEquals(1, rect.loads.size)
        rect.loads[0].onLoaded("rect_home", "rect_home", "id-rect", ecmp, adPl)

        assertEquals("app nhận onLoaded trước khi kiểm container", listOf("loaded"), cb.events)
        verify(adPl, times(1)).destroyAd(adView)
        verify(ecmp, times(1)).adObject = null
        verify(ecmp, times(1)).isLoaded = false
        verify(parent, never()).addView(adView)
        verify(adPl, never()).setGetPlacement(anyString())
        val plcf = helper.getAdsPlacementConfig("rect_home", 1)!!
        assertTrue(plcf.isLoaded)
        assertFalse(plcf.isLoading)

        helper.showRect("home", parent, AppCb())
        assertEquals("mục chưa gắn: showRect lần sau load lại", 2, rect.loads.size)
    }
}
