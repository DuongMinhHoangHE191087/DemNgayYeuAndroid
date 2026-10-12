package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.app.plugin.ads.core.LoadCallbacks
import com.app.plugin.until.LogUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * 1.6.0 Task 4 (G9 bước rect, heap Task 3): `AdsPlacementConfig.cbLoad` giữ callback load của app — thường là lớp trong
 * của Activity — qua khi host bị huỷ. Heap emulator API 37: `rect_default.cbLoad → MainActivity$DemoLoadCb.this$0` giữ
 * Activity đầu sau hai lần xoay. `clearActivity` nhả `cbShow` rảnh từ 1.4.0 (Task I5) nhưng không nhả `cbLoad`.
 *
 * Hợp đồng mới: `clearActivity` bỏ `cbLoad` của mọi placement **không đang load** (`isLoading = false`), sau hai lượt
 * nhả/clear mạng, cạnh `releaseIdleShowCallbacks`, log `released <s> idle show callbacks, <l> idle load callbacks`.
 * Giữ: placement đang load giữ callback (request đang chờ vẫn nhận đúng một callback kết thúc); `showBanner`/`load…` sau
 * `clearActivity` (host mới, kể cả sau xoay) nhận kết quả như trước — gồm `onLoaded` muộn của vòng banner sau
 * `load timeout` (1.5.0 Task 15, `BannerLoadCycleTest`).
 */
class IdleLoadCallbackReleaseTest {

    /** Mạng interstitial: giữ inner cb mỗi lần `loadAd` (không tự callback). */
    private class IntersNetwork(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullInters, context) {
        override val eventTag: String get() = "test_inters"
        val loads = mutableListOf<AdsLoadInnerCb>()
        /** Chạy trong `clearActivity()` của mạng (lượt 2 của `AdsHelper.clearActivity`), như dismiss phát tới app lúc đó. */
        var onClearActivity: (() -> Unit)? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_inters_data"
        override fun getKeyForResDf(): String = "test_inters_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
        override fun clearActivity() {
            super.clearActivity()
            onClearActivity?.invoke()
        }
    }

    /** Mạng rect: giữ inner cb của `loadAd`; [getAdForShow] trả rect trong cache của mạng (như `LoadCycleGuardTest`). */
    private class RectNetwork(context: Context) : AdsBaseRect(AdsIDDef.AdmobRect, context) {
        override val eventTag: String get() = "test_rect"
        val loads = mutableListOf<AdsLoadInnerCb>()
        var cached: Any? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_rect_data"
        override fun getKeyForResDf(): String = "test_rect_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
        override fun getAdForShow(placement: String, adsShowCb: AdsShowCb): Any? = cached
    }

    /** Mạng banner: giữ inner cb của `loadAd` (mẫu `BannerLoadCycleTest`). */
    private class BannerNetwork(context: Context) : AdsBaseBanner(AdsIDDef.AdmobBanner, context) {
        override val eventTag: String get() = "test_banner"
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_banner_data"
        override fun getKeyForResDf(): String = "test_banner_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
    }

    /** Callback load của app (trong app thật: lớp trong của Activity, giữ Activity qua `this$0`). */
    private class AppLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    private class Fixture(
        val helper: AdsHelper,
        val inters: IntersNetwork,
        val rect: RectNetwork,
        val banner: BannerNetwork,
        val timers: MainHandlerTimers,
    ) {
        fun plcf(pl: String): AdsPlacementConfig = helper.getAdsPlacementConfig(pl, 1)!!

        /** Load Rect → mạng trả rect → Show Rect lấy rect từ cache (đúng hai nút của demo trong kịch bản G9). */
        fun rectLoadedAndShown(cbShowRect: AppLoadCb) {
            val cbLoadRect = AppLoadCb()
            helper.loadRect4Show("default", cbLoadRect)
            rect.loads.single().onLoaded("rect_default", "rect_default", "id-rect", null, null)
            assertEquals(listOf("loaded"), cbLoadRect.events)
            rect.cached = ObjectForShowAdWithViewParent().apply { adLoad = mock(View::class.java) }
            helper.showRect("default", mock(ViewGroup::class.java), cbShowRect)
            assertEquals("Show Rect báo onLoaded từ cache", listOf("loaded"), cbShowRect.events)
            assertFalse(plcf("rect_default").isLoading)
            assertSame(cbShowRect, plcf("rect_default").cbLoad)
        }
    }

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

    /**
     * `AdsHelper` dựng như `Full2HostDestroyTest` (constructor riêng + `placementsConfigManager` thật), ba mạng giả nằm
     * trong `listAdsAll` như app thật (hai lượt nhả/clear của `clearActivity` đi qua chúng). Mạng coi như chưa khởi
     * tạo (`adNetworksStartedAtMs = 0`): watchdog stall 30 s của banner không bỏ vòng (như `BannerLoadCycleTest`).
     */
    private fun withHelper(check: Fixture.() -> Unit) {
        val startedAt = AdSdkStartup::class.java.getDeclaredField("adNetworksStartedAtMs").apply { isAccessible = true }
        val old = startedAt.getLong(null)
        startedAt.setLong(null, 0L)
        try {
            MainHandlerTimers.run(honorRemoveAll = true) { timers ->
                val helper = AdsHelper::class.java.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
                helper.currConfig = AdsConfigOb(mock(Context::class.java)).apply {
                    full2FlagShow = 0
                    fullStepCicle.add(AdsIDDef.AdmobFullInters)
                    rectStepList.add(AdsIDDef.AdmobRect)
                    bannerStepCicle.add(AdsIDDef.AdmobBanner)
                }
                helper.placementsConfigManager = AdsPlacementConfigManager()
                val inters = IntersNetwork(networkContext())
                val rect = RectNetwork(networkContext())
                val banner = BannerNetwork(networkContext())
                setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to inters))
                setField(helper, "listAdsRect", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobRect to rect))
                setField(helper, "listAdsBn", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobBanner to banner))
                setField(helper, "listAdsAll", mutableListOf<AdsBase>(inters, rect, banner))
                Fixture(helper, inters, rect, banner, timers).check()
            }
        } finally {
            startedAt.setLong(null, old)
        }
    }

    private fun shownParent(): ViewGroup = mock(ViewGroup::class.java).also {
        `when`(it.isShown).thenReturn(true)
        `when`(it.isAttachedToWindow).thenReturn(true)
    }

    private fun ecmpWith(adObject: Any?): AdsEcmp = mock(AdsEcmp::class.java).also { `when`(it.adObject).thenReturn(adObject) }

    /** `clearActivity()` với log debug bật; kiểm dòng log đếm callback đã nhả xuất hiện đúng một lần. */
    private fun Fixture.clearActivityExpectingLog(line: String) {
        LogUtil.setFlagLog(3)
        mockStatic(Log::class.java).use { log ->
            helper.clearActivity()
            log.verify({ Log.d(eq(LogUtil.TAG), eq(line)) }, times(1))
        }
    }

    @After
    fun tearDown() {
        LogUtil.setFlagLog(1)
    }

    // ---- Hành vi mới (đỏ trên mã cũ) ----

    /** (1) Kịch bản heap Task 3: `rect_default.cbLoad` (callback của Show Rect) rảnh → nhả khi host bị huỷ. */
    @Test
    fun `rect da load va da hien - clearActivity nha cbLoad ranh, log 1 idle load callbacks`() = withHelper {
        val cbShowRect = AppLoadCb()
        rectLoadedAndShown(cbShowRect)

        clearActivityExpectingLog("ads helper clearActivity: released 0 idle show callbacks, 1 idle load callbacks")

        assertNull("cbLoad rảnh của rect_default phải được nhả (giữ Activity đã huỷ)", plcf("rect_default").cbLoad)
        assertEquals("không callback thêm nào tới app", listOf("loaded"), cbShowRect.events)
    }

    /** (2) Banner đã load xong (vòng kết thúc, `isLoading = false`): `cbLoad` rảnh được nhả. */
    @Test
    fun `banner da load xong - clearActivity nha cbLoad`() = withHelper {
        val cb = AppLoadCb()
        helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = cb, cbShow = null)
        banner.loads.single().onLoaded("bn_home", "bn_home", "id-bn", ecmpWith(mock(View::class.java)), null)
        assertEquals(listOf("loaded"), cb.events)
        assertFalse(plcf("bn_home").isLoading)

        helper.clearActivity()

        assertNull("cbLoad rảnh của banner phải được nhả", plcf("bn_home").cbLoad)
        assertEquals(listOf("loaded"), cb.events)
    }

    /** (3) Interstitial sau `onLoaded` của lượt: không còn lời gọi nào tới callback → nhả. */
    @Test
    fun `interstitial sau onLoaded cua luot - clearActivity nha cbLoad`() = withHelper {
        val cb = AppLoadCb()
        helper.loadInterstitialForThisTurn("home", 0, cb)
        inters.loads.single().onLoaded("full_home", "full_home", "t", null, null)
        assertEquals(listOf("loaded"), cb.events)

        helper.clearActivity()

        assertNull("cbLoad rảnh của interstitial phải được nhả", plcf("full_home").cbLoad)
        assertEquals(listOf("loaded"), cb.events)
    }

    /** (3b) Interstitial sau `onLoadFailed` của lượt (`list empty`): placement rảnh chưa từng load được cũng nhả. */
    @Test
    fun `interstitial sau list empty - clearActivity nha cbLoad`() = withHelper {
        val cb = AppLoadCb()
        helper.loadInterstitialForThisTurn("home", 0, cb)
        inters.loads.single().onLoadFailed("full_home", "full_home", "t", "no fill")
        timers.runPosted()
        assertEquals(listOf("fail:list empty"), cb.events)
        assertFalse(plcf("full_home").isLoaded)

        helper.clearActivity()

        assertNull("cbLoad rảnh (lượt đã lỗi) phải được nhả", plcf("full_home").cbLoad)
        assertEquals(listOf("fail:list empty"), cb.events)
    }

    /**
     * (1c) Thứ tự: callback của app (vd `onAdDismissed` mà native full phát ở lượt 2 của `clearActivity`) gọi `showRect`
     * trên host đang huỷ — rect lấy từ cache, `cbLoad` gán và báo `onLoaded` ngay, placement rảnh. Nhả phải chạy **sau**
     * hai lượt nhả/clear mạng, nếu không callback gán trong lượt 2 còn giữ Activity đã huỷ.
     */
    @Test
    fun `callback gan trong luot 2 cua clearActivity cung duoc nha`() = withHelper {
        rect.cached = ObjectForShowAdWithViewParent().apply { adLoad = mock(View::class.java) }
        val cbInDismiss = AppLoadCb()
        inters.onClearActivity = { helper.showRect("default", mock(ViewGroup::class.java), cbInDismiss) }

        clearActivityExpectingLog("ads helper clearActivity: released 0 idle show callbacks, 1 idle load callbacks")

        assertEquals("showRect trong lượt 2 đã chạy", listOf("loaded"), cbInDismiss.events)
        assertNull("callback gán trong lượt 2 phải được nhả", plcf("rect_default").cbLoad)
    }

    /** (1b) Đếm: rect rảnh bị nhả, interstitial đang load giữ → log đếm đúng 1. */
    @Test
    fun `rect ranh va interstitial dang load - chi rect bi nha, log dem 1`() = withHelper {
        val cbRect = AppLoadCb()
        rectLoadedAndShown(cbRect)
        val cbInters = AppLoadCb()
        helper.loadInterstitialForThisTurn("home", 0, cbInters)
        assertTrue(plcf("full_home").isLoading)

        clearActivityExpectingLog("ads helper clearActivity: released 0 idle show callbacks, 1 idle load callbacks")

        assertNull(plcf("rect_default").cbLoad)
        assertTrue("placement đang load giữ callback (trong wrapper OneShotLoadCb)", LoadCallbacks.holds(plcf("full_home").cbLoad, cbInters))
    }

    /** (1d) Review Task 4 M1: banner đã load xong với `cbLoad = null` không được đếm → log vẫn `1 idle load callbacks`. */
    @Test
    fun `rect ranh, interstitial dang load, banner da load cbLoad null - log dem 1`() = withHelper {
        rectLoadedAndShown(AppLoadCb())
        helper.loadInterstitialForThisTurn("home", 0, AppLoadCb())
        helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = null, cbShow = null)
        banner.loads.single().onLoaded("bn_home", "bn_home", "id-bn", ecmpWith(mock(View::class.java)), null)
        assertFalse(plcf("bn_home").isLoading)
        assertNull(plcf("bn_home").cbLoad)

        clearActivityExpectingLog("ads helper clearActivity: released 0 idle show callbacks, 1 idle load callbacks")

        assertNull(plcf("rect_default").cbLoad)
    }

    /** (1e) Review Task 4 M2: một mạng ném trong `releaseActivity()` → `clearActivity()` ném, nhưng nhả nằm trong `finally`. */
    @Test
    fun `mang nem trong releaseActivity - clearActivity nem ra ngoai nhung cbLoad ranh van duoc nha`() = withHelper {
        rectLoadedAndShown(AppLoadCb())
        val throwingPl = mock(AdsPlBase::class.java)
        doThrow(RuntimeException("release boom")).`when`(throwingPl).releaseActivity()
        inters.mapAdsPl["full_home"] = throwingPl

        val thrown = runCatching { helper.clearActivity() }.exceptionOrNull()

        assertEquals("release boom", thrown?.message)
        assertNull("nhả cbLoad rảnh nằm trong finally", plcf("rect_default").cbLoad)
    }

    // ---- Hành vi giữ (xanh trên mã cũ) ----

    /** (4) Đang load lúc host bị huỷ: callback giữ, lượt đang chờ vẫn nhận đúng một `onLoaded`. */
    @Test
    fun `dang load luc clearActivity - cbLoad con, app nhan dung mot onLoaded`() = withHelper {
        val cb = AppLoadCb()
        helper.loadInterstitialForThisTurn("home", 0, cb)
        assertTrue(plcf("full_home").isLoading)

        helper.clearActivity()

        assertTrue("placement đang load giữ callback (trong wrapper OneShotLoadCb)", LoadCallbacks.holds(plcf("full_home").cbLoad, cb))
        inters.loads.single().onLoaded("full_home", "full_home", "t", null, null)
        assertEquals("đúng một callback kết thúc", listOf("loaded"), cb.events)
    }

    /** (4b) Đang load lúc host bị huỷ, mạng treo: request vẫn nhận `load timeout` đúng một lần (watchdog 90 s). */
    @Test
    fun `dang load luc clearActivity, mang treo - app nhan load timeout dung mot lan`() = withHelper {
        val cb = AppLoadCb()
        helper.loadInterstitialForThisTurn("home", 0, cb)

        helper.clearActivity()
        timers.advanceTo(AdsPlacementConfig.LOAD_TIMEOUT_MS)

        assertEquals(listOf("fail:load timeout"), cb.events)
        inters.loads.single().onLoaded("full_home", "full_home", "t", null, null)
        assertEquals("onLoaded muộn không tới lần hai", listOf("fail:load timeout"), cb.events)
    }

    /**
     * (5) Banner sau `load timeout` → host bị huỷ → host mới `showBanner(cb3)` (cổng delta chặn vòng mới) → view của vòng
     * cũ tới muộn: `cb3` nhận `onLoaded` đúng một lần, `cb1` chỉ có `load timeout` (1.5.0 Task 15 giữ nguyên).
     */
    @Test
    fun `banner load timeout, clearActivity, showBanner cb3 - cb3 nhan onLoaded muon dung mot lan, cb1 khong nhan them`() =
        withHelper {
            helper.currConfig.bannerDeltaTime = 1_000
            val cb1 = AppLoadCb()
            helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = cb1, cbShow = null)
            timers.advanceTo(AdsPlacementConfig.LOAD_TIMEOUT_MS)
            assertEquals(listOf("fail:load timeout"), cb1.events)

            helper.clearActivity()

            helper.bnTimeShow = System.currentTimeMillis() / 1000 // vòng trước vừa kết thúc: delta 1000 s chặn load mới
            val cb3 = AppLoadCb()
            helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = cb3, cbShow = null)
            assertEquals("cổng delta chặn loadBanner", 1, banner.loads.size)
            banner.loads[0].onLoaded("bn_home", "bn_home", "id-bn", ecmpWith(mock(View::class.java)), null)

            assertEquals("cb3 nhận onLoaded muộn của vòng cũ", listOf("loaded"), cb3.events)
            assertEquals("cb1 chỉ một callback kết thúc", listOf("fail:load timeout"), cb1.events)
        }

    /** (6) Sau xoay: host cũ huỷ (callback rảnh), host mới đăng ký lại trong `onCreate` → callback mới đúng một kết thúc. */
    @Test
    fun `clearActivity roi host moi loadInterstitialForThisTurn cbNew - cbNew dung mot ket thuc, cb cu khong nhan them`() =
        withHelper {
            val cbOld = AppLoadCb()
            helper.loadInterstitialForThisTurn("home", 0, cbOld)
            inters.loads[0].onLoaded("full_home", "full_home", "t", null, null)

            helper.clearActivity()

            val cbNew = AppLoadCb()
            helper.loadInterstitialForThisTurn("home", 0, cbNew)
            assertSame(cbNew, plcf("full_home").cbLoad)
            assertEquals(2, inters.loads.size)
            inters.loads[1].onLoaded("full_home", "full_home", "t", null, null)

            assertEquals("cbNew đúng một kết thúc", listOf("loaded"), cbNew.events)
            assertEquals("cb cũ không nhận thêm", listOf("loaded"), cbOld.events)
        }

    /** (6b) Như (6) cho rect: host mới Show Rect lại → callback mới nhận `onLoaded`, callback cũ không nhận thêm. */
    @Test
    fun `clearActivity roi host moi showRect - callback moi nhan onLoaded, cu khong nhan them`() = withHelper {
        val cbOld = AppLoadCb()
        rectLoadedAndShown(cbOld)

        helper.clearActivity()

        val cbNew = AppLoadCb()
        helper.showRect("default", mock(ViewGroup::class.java), cbNew)
        assertEquals(listOf("loaded"), cbNew.events)
        assertSame(cbNew, plcf("rect_default").cbLoad)
        assertEquals(listOf("loaded"), cbOld.events)
    }
}
