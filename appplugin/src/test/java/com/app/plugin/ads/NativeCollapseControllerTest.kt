package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.FrameLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * Test đặc tả vùng native collapse (NtCl) của [AdsHelper] (plan 1.5.0 Task 4, KB-2): viết và chạy xanh trên mã CŨ
 * trước khi chuyển sang `ads.core.NativeCollapseController`, làm lưới cho bước chuyển. Mọi lời gọi đi qua hàm public
 * của façade (`showNtCl`, `loadNtClForThisTurn`, `createAndAddNtClParentView`, `bringLayoutCl`, `hideNativeCl`,
 * `clearActivity`); `ntClIsShow`/`ntclParentView` chỉ quan sát qua hành vi, không đọc bằng reflection vì hai field đó
 * rời façade. Mạng NtCl giả theo mẫu `AdsDisabledLoadGateTest.LoadedNtCl`, giữ inner callback của `loadAd`.
 */
class NativeCollapseControllerTest {

    private class NtClNetwork(context: Context) : AdsBaseBanner(AdsIDDef.AdmobNtCl, context) {
        override val eventTag: String get() = "test_ntcl"
        /** Kết quả từng lượt `showAd`: true = hiện (báo `onAdShowed`), false = lỗi (báo `onAdShowFail`). */
        val showResults = ArrayDeque<Boolean>()
        var shows = 0
        var hides = 0
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_ntcl_data"
        override fun getKeyForResDf(): String = "test_ntcl_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
        override fun hideAd() { hides++ }
        override fun showAd(placement: String, adsShowCb: AdsShowCb, flagShow: Int): Boolean {
            shows++
            val shown = showResults.removeFirstOrNull() ?: false
            if (shown) adsShowCb.onAdShowed(placement, placement, "id-ntcl", 0)
            else adsShowCb.onAdShowFail(placement, placement, "id-ntcl", "no fill")
            return shown
        }
    }

    private class AppLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    private class AppShowCb : AdsShowCb {
        val events = mutableListOf<String>()
        override fun onAdShowFail(placement: String?, originPl: String?, adId: String?, err: String?) { events += "showFail" }
        override fun onAdShowed(placement: String?, originPl: String?, adId: String?, flag: Int) { events += "showed:$flag" }
        override fun onAdImpression(placement: String?, originPl: String?, adId: String?) { events += "impression" }
        override fun onAdClick(placement: String?, originPl: String?, adId: String?) { events += "click" }
        override fun onAdDismissed(placement: String?, originPl: String?, adId: String?) { events += "dismissed" }
    }

    private class Fixture(val helper: AdsHelper, val ntcl: NtClNetwork, val timers: MainHandlerTimers)

    /** `AdsHelper` một mạng NtCl (AdMob), dựng trong [MainHandlerTimers.run] để `Handler.post` của vòng load chạy tay. */
    private fun withNtCl(stepList: Boolean = true, check: Fixture.() -> Unit) = MainHandlerTimers.run { timers ->
        val helper = AdsKillSwitchTest.newAdsHelper()
        if (stepList) helper.currConfig.clStepList.add(AdsIDDef.AdmobNtCl)
        val ntcl = NtClNetwork(networkContext())
        setField(helper, "listAdsNtCl", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobNtCl to ntcl))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>(ntcl))
        Fixture(helper, ntcl, timers).check()
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

    @Test fun `showNtCl khong co quang cao - goi loadNtClForThisTurn, gan cbShow vao plcf, load xong thi hien lai`() = withNtCl {
        val cbShow = AppShowCb()
        helper.flagAdsFullShowing.set(1)
        ntcl.showResults += false // lượt hiện đầu: mạng không có quảng cáo

        helper.showNtCl("home", cbShow)

        val plcf = helper.getAdsPlacementConfig("cl_home", 1)!!
        assertSame("cbShow gán vào plcf", cbShow, plcf.cbShow)
        assertEquals("một lượt showAd", 1, ntcl.shows)
        assertEquals("onAdShowFail của mạng giảm flagAdsFullShowing (clamp ≥ 0)", 0, helper.flagAdsFullShowing.get())
        assertEquals("không hiện thì loadNtClForThisTurn gọi mạng đúng một lần", 1, ntcl.loads.size)
        assertTrue(plcf.isLoading)
        assertNull("showNtCl gọi loadNtClForThisTurn(placement, null)", plcf.cbLoad)
        assertEquals("mạng báo lỗi hiện không tới app", emptyList<String>(), cbShow.events)

        ntcl.showResults += true
        ntcl.loads[0].onLoaded("cl_home", "cl_home", "id-ntcl", null, null)

        assertTrue(plcf.isLoaded)
        assertFalse(plcf.isLoading)
        assertEquals("ntClIsShow còn bật: load xong thì showNtCl lần nữa", 2, ntcl.shows)
        assertEquals("onAdShowed chuyển tới cbShow của app với flag 1", listOf("showed:1"), cbShow.events)
    }

    @Test fun `loadNtClCircle het danh sach - cbLoad onLoadFailed list empty dung mot lan`() = withNtCl {
        val cb = AppLoadCb()

        helper.loadNtClForThisTurn("home", cb)
        assertEquals("một lượt load mạng", 1, ntcl.loads.size)
        ntcl.loads[0].onLoadFailed("cl_home", "cl_home", "id-ntcl", "no fill")
        assertEquals("chưa báo trước khi Handler.post chạy", emptyList<String>(), cb.events)
        timers.runPosted()

        assertEquals(listOf("fail:list empty"), cb.events)
        val plcf = helper.getAdsPlacementConfig("cl_home", 1)!!
        assertFalse(plcf.isLoading)
        assertEquals("không gọi mạng lần hai", 1, ntcl.loads.size)
    }

    @Test fun `clStepList rong - loadNtClForThisTurn bao list empty dung mot lan, khong goi mang`() = withNtCl(stepList = false) {
        val cb = AppLoadCb()

        helper.loadNtClForThisTurn("home", cb)
        timers.runPosted()

        assertEquals(listOf("fail:list empty"), cb.events)
        assertEquals(0, ntcl.loads.size)
    }

    @Test fun `container NtCl - tao, dua len tren, an, clearActivity go view va bo tham chieu`() = withNtCl {
        val activity = mock(Activity::class.java)
        setField(helper, "mActivity", activity)
        mockConstruction(FrameLayout::class.java).use { frames ->
            val parent = helper.createAndAddNtClParentView()
            assertEquals(1, frames.constructed().size)
            val frame = frames.constructed()[0]
            assertSame(frame, parent)
            verify(activity, times(1)).addContentView(org.mockito.Mockito.eq(frame), any(ViewGroup.LayoutParams::class.java))
            assertSame("gọi lần hai trả cùng container", frame, helper.createAndAddNtClParentView())
            assertEquals(1, frames.constructed().size)

            helper.bringLayoutCl()
            verify(frame, times(1)).bringToFront()

            helper.hideNativeCl()
            verify(frame, times(1)).visibility = View.INVISIBLE
            verify(frame, times(1)).removeAllViews() // 1.9.0 Task 3: ẩn gỡ view của lượt vừa đóng
            assertEquals(1, ntcl.hides)

            setField(helper, "listAdsAll", mutableListOf<AdsBase>())
            helper.clearActivity()
            verify(frame, times(2)).removeAllViews()
            // mActivity = null sau clearActivity: container đã bỏ tham chiếu thì trả null, không trả view của Activity cũ.
            assertNull(helper.createAndAddNtClParentView())
        }
    }

    /**
     * NtCl lượt cũ (plan Task 15, review Task 4 P1; mẫu `LoadCycleGuardTest`): lượt 1 đang chờ mạng, `plcf.loadCycle`
     * tăng như một lượt load mới đã bắt đầu, rồi inner cb của lượt 1 báo `onLoaded`/`onLoadFailed` → không hiện lại
     * (`showAd`), `cb` không nhận callback của lượt cũ, `isLoaded` không đổi, không gọi mạng lần nữa — kể cả khi lượt mới đã
     * kết thúc (nhánh `isLoading=false on fail`).
     */
    @Test fun `loadNtClForThisTurn luot cu (loadCycle tang) - khong showAd, cb khong nhan, isLoaded khong doi`() = withNtCl {
        val cb = AppLoadCb()
        helper.loadNtClForThisTurn("home", cb)
        ntcl.showResults += false
        helper.showNtCl("home", AppShowCb()) // ntClIsShow bật; đang load nên không gọi mạng lần hai, cbLoad giữ cb
        assertEquals(1, ntcl.shows)
        assertEquals(1, ntcl.loads.size)
        val plcf = helper.getAdsPlacementConfig("cl_home", 1)!!
        assertSame(cb, plcf.cbLoad)
        val staleInner = ntcl.loads[0]

        plcf.isLoading = true // lượt mới bắt đầu: setter tăng loadCycle

        ntcl.showResults += true
        staleInner.onLoaded("cl_home", "cl_home", "id-ntcl", null, null)
        timers.runPosted()
        assertEquals("lượt cũ không hiện lại NtCl", 1, ntcl.shows)
        assertEquals("cb không nhận onLoaded của lượt cũ", emptyList<String>(), cb.events)
        assertFalse("isLoaded không đổi bởi lượt cũ", plcf.isLoaded)
        assertTrue("isLoading của lượt mới giữ nguyên", plcf.isLoading)

        staleInner.onLoadFailed("cl_home", "cl_home", "id-ntcl", "late fail")
        timers.runPosted()
        assertEquals("cb không nhận onLoadFailed của lượt cũ", emptyList<String>(), cb.events)
        assertTrue(plcf.isLoading)
        assertEquals("lượt cũ không gọi mạng lần nữa", 1, ntcl.loads.size)

        // Lượt mới cũng đã kết thúc (isLoading hạ): lỗi/onLoaded muộn của lượt 1 vẫn không tới cb, không đổi cờ.
        plcf.isLoading = false
        staleInner.onLoadFailed("cl_home", "cl_home", "id-ntcl", "late fail 2")
        staleInner.onLoaded("cl_home", "cl_home", "id-ntcl", null, null)
        timers.runPosted()
        assertEquals("callback muộn không tới cb khi lượt mới đã kết thúc", emptyList<String>(), cb.events)
        assertFalse(plcf.isLoaded)
        assertEquals(1, ntcl.shows)
        assertEquals(1, ntcl.loads.size)
    }

    // ═══ 1.9.0 Task 3: mỗi host NtCl ≤ 1 view (Hợp đồng D, D4) ═══

    /** NtCl thật ([AdsPlNtClBase.addToParentView]) với context có DisplayMetrics; không load/hiện gì. */
    private class NtClProbe(context: Context) : AdsPlNtClBase(context, mock(AdsBase::class.java), AdsIDDef.AdmobNtCl, "cl_default") {
        override val eventTag: String get() = "test_ntcl_probe"
        override fun onInit() = Unit
        override fun tryLoad() = Unit
        override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int) = false
    }

    /** Container FrameLayout giả đếm view con; `parent` của view giả đọc từ đây (hoặc từ [extraParent]). */
    private class Hosts {
        val children = mutableMapOf<FrameLayout, MutableList<View>>()
        val extraParent = mutableMapOf<View, ViewParent>()
        fun track(frame: FrameLayout) {
            val list = mutableListOf<View>().also { children[frame] = it }
            doAnswer { list += it.getArgument<View>(0); null }.`when`(frame).addView(any(View::class.java))
            doAnswer { list += it.getArgument<View>(0); null }.`when`(frame).addView(any(View::class.java), any(ViewGroup.LayoutParams::class.java))
            doAnswer { list.clear(); null }.`when`(frame).removeAllViews()
            doAnswer { list.remove(it.getArgument<View>(0)); null }.`when`(frame).removeView(any(View::class.java))
            `when`(frame.childCount).thenAnswer { list.size }
        }
        fun view(): View = mock(View::class.java).also { v ->
            `when`(v.parent).thenAnswer { extraParent[v] ?: children.entries.firstOrNull { e -> e.value.any { it === v } }?.key }
        }
    }

    /**
     * `AdsHelper.instance` (addToParentView đọc singleton) có host Activity giả, không mạng NtCl; trả lại `mActivity`,
     * `listAdsNtCl` cũ và bỏ container sau test.
     */
    private fun withSingletonHost(check: (AdsHelper, NtClProbe, Hosts, () -> FrameLayout) -> Unit) {
        val helper = AdsHelper.instance
        val ctx = networkContext()
        `when`(ctx.resources.displayMetrics).thenReturn(DisplayMetrics())
        val hosts = Hosts()
        val field = { name: String -> AdsHelper::class.java.getDeclaredField(name).apply { isAccessible = true } }
        val oldActivity = field("mActivity").get(helper)
        val oldNtCl = field("listAdsNtCl").get(helper)
        setField(helper, "mActivity", mock(Activity::class.java))
        setField(helper, "listAdsNtCl", mutableMapOf<Int, AdsBaseBanner>())
        try {
            mockConstruction(FrameLayout::class.java) { frame, _ -> hosts.track(frame) }.use { frames ->
                check(helper, NtClProbe(ctx), hosts) { frames.constructed().single() }
            }
        } finally {
            val ntcl = AdsHelper::class.java.getDeclaredField("ntcl").apply { isAccessible = true }.get(helper)
            ntcl.javaClass.getDeclaredField("ntclParentView").apply { isAccessible = true }.set(ntcl, null)
            setField(helper, "mActivity", oldActivity)
            setField(helper, "listAdsNtCl", oldNtCl)
        }
    }

    // N1
    @Test fun `NtCl hien - an - hien lai - host giu dung mot view (an go view cu)`() = withSingletonHost { helper, probe, hosts, host ->
        val counts = mutableListOf<Int>()
        probe.addToParentView(hosts.view())
        counts += host().childCount
        helper.hideNativeCl()
        counts += host().childCount
        probe.addToParentView(hosts.view())
        counts += host().childCount
        assertEquals("số view của host sau hiện, ẩn, hiện lại", listOf(1, 0, 1), counts)
    }

    // N2
    @Test fun `NtCl view con cha cu - go khoi cha cu qua BannerAttach roi moi gan`() = withSingletonHost { _, probe, hosts, host ->
        val view = hosts.view()
        val old = mock(ViewGroup::class.java)
        hosts.extraParent[view] = old
        doAnswer { hosts.extraParent.remove(view); null }.`when`(old).removeView(view)

        probe.addToParentView(view)

        verify(old, times(1)).removeView(view)
        assertSame("view nằm trong host", host(), view.parent)
        assertEquals(1, host().childCount)
    }

    // N4 (R-T3 I1): hiện lại khi đang hiện (load xong lúc ntClIsShow) — không qua hide, view cũ vẫn bị gỡ trước khi gắn.
    @Test fun `NtCl hien hai lan khong an - host chi giu view moi`() = withSingletonHost { _, probe, hosts, host ->
        val first = hosts.view()
        val second = hosts.view()
        probe.addToParentView(first)
        probe.addToParentView(second)
        assertEquals(1, host().childCount)
        assertSame("host giữ view mới", host(), second.parent)
        assertNull("view cũ đã rời host", first.parent)
    }

    // N3
    @Test fun `onHostCleared van go het view cua host va bo tham chieu`() = withNtCl {
        setField(helper, "mActivity", mock(Activity::class.java))
        val hosts = Hosts()
        mockConstruction(FrameLayout::class.java) { frame, _ -> hosts.track(frame) }.use { frames ->
            val host = helper.createAndAddNtClParentView()!!
            host.addView(hosts.view())
            host.addView(hosts.view())
            assertEquals(2, host.childCount)
            setField(helper, "listAdsAll", mutableListOf<AdsBase>())
            helper.clearActivity()
            assertEquals("clearActivity gỡ mọi view", 0, host.childCount)
            assertSame(host, frames.constructed().single())
            assertNull(helper.createAndAddNtClParentView())
        }
    }
}
