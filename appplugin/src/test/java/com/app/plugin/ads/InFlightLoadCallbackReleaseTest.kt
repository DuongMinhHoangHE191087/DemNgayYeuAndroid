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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.contains
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`

/**
 * 1.6.0 Task 5 (review Task 4 I1): `clearActivity` giữ `cbLoad` của placement **đang load** (Task 4: request đang chờ vẫn
 * nhận đúng một callback kết thúc), nhưng sau khi lượt kết thúc callback đó nằm lại trên placement tới `clearActivity` kế
 * tiếp hoặc lần gán mới — giữ Activity đã huỷ. O7 (a) (gắn callback thay vì ghi đè) làm dài thêm: `showRect` của host mới
 * gắn thêm → fan-out giữ callback của host cũ suốt đời host mới.
 *
 * Hợp đồng mới: `clearActivity` bọc callback của mọi placement đang load trong `OneShotLoadCb` — lượt đó chuyển đúng một
 * callback kết thúc rồi bỏ tham chiếu; log `ads helper clearActivity: <n> in-flight load callbacks released after terminal`
 * khi n > 0. Khử trùng của O7 nhìn xuyên wrapper (`LoadCallbacks.holds`).
 */
class InFlightLoadCallbackReleaseTest {

    private class IntersNetwork(context: Context) : AdsBaseFull(AdsIDDef.AdmobFullInters, context) {
        override val eventTag: String get() = "test_inters"
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_inters_data"
        override fun getKeyForResDf(): String = "test_inters_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
    }

    private class RectNetwork(context: Context) : AdsBaseRect(AdsIDDef.AdmobRect, context) {
        override val eventTag: String get() = "test_rect"
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_rect_data"
        override fun getKeyForResDf(): String = "test_rect_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
        override fun getAdForShow(placement: String, adsShowCb: AdsShowCb): Any? = null
    }

    private class BannerNetwork(context: Context) : AdsBaseBanner(AdsIDDef.AdmobBanner, context) {
        override val eventTag: String get() = "test_banner"
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_banner_data"
        override fun getKeyForResDf(): String = "test_banner_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
    }

    private class GiftNetwork(context: Context) : AdsBaseGift(AdsIDDef.AdmobRewarded, context) {
        override val eventTag: String get() = "test_gift"
        val loads = mutableListOf<AdsLoadInnerCb>()
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_gift_data"
        override fun getKeyForResDf(): String = "test_gift_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
    }

    /** Callback load của app (trong app thật: lớp trong của Activity, giữ Activity qua `this$0`). */
    private class AppLoadCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    /** Callback `equals` mọi `EqCb` khác (vd `data class` không trường) — object khác nhau vẫn là request khác nhau. */
    private class EqCb : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
        override fun equals(other: Any?) = other is EqCb
        override fun hashCode() = 1
    }

    private class Fixture(
        val helper: AdsHelper,
        val inters: IntersNetwork,
        val rect: RectNetwork,
        val banner: BannerNetwork,
        val gift: GiftNetwork,
        val timers: MainHandlerTimers,
    ) {
        fun plcf(pl: String): AdsPlacementConfig = helper.getAdsPlacementConfig(pl, 1)!!
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

    /** `AdsHelper` dựng như `IdleLoadCallbackReleaseTest` (bốn mạng giả trong `listAdsAll`, handler ảo). */
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
                    giftStepCicle.clear(); giftStepCicle.add(AdsIDDef.AdmobRewarded); giftStepRe.clear()
                }
                helper.placementsConfigManager = AdsPlacementConfigManager()
                val f = Fixture(
                    helper, IntersNetwork(networkContext()), RectNetwork(networkContext()),
                    BannerNetwork(networkContext()), GiftNetwork(networkContext()), timers,
                )
                setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullInters to f.inters))
                setField(helper, "listAdsRect", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobRect to f.rect))
                setField(helper, "listAdsBn", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobBanner to f.banner))
                setField(helper, "listAdsGift", mutableMapOf<Int, AdsBaseGift>(AdsIDDef.AdmobRewarded to f.gift))
                setField(helper, "listAdsAll", mutableListOf<AdsBase>(f.inters, f.rect, f.banner, f.gift))
                f.check()
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

    @After
    fun tearDown() {
        LogUtil.setFlagLog(1)
    }

    // ═══ KB-2: hành vi giữ (xanh trên mã cũ) ═══

    /** (p) Interstitial và rect `loadRect4Show` đang load → `clearActivity()` → mạng `onLoaded` → mỗi callback đúng một lần. */
    @Test
    fun `p - dang load luc clearActivity - mang onLoaded, callback nhan loaded dung mot lan`() = withHelper {
        val cbInters = AppLoadCb()
        val cbRect = AppLoadCb()
        helper.loadInterstitialForThisTurn("home", 0, cbInters)
        helper.loadRect4Show("default", cbRect)

        helper.clearActivity()
        inters.loads.single().onLoaded("full_home", "full_home", "t", null, null)
        rect.loads.single().onLoaded("rect_default", "rect_default", "id", null, null)

        assertEquals(listOf("loaded"), cbInters.events)
        assertEquals(listOf("loaded"), cbRect.events)
    }

    /** (q) Như (p), mạng treo → watchdog 90 s → `load timeout` đúng một lần; `onLoaded` muộn không tới. */
    @Test
    fun `q - dang load luc clearActivity, mang treo - load timeout dung mot lan, onLoaded muon khong toi`() = withHelper {
        val cbInters = AppLoadCb()
        val cbRect = AppLoadCb()
        helper.loadInterstitialForThisTurn("home", 0, cbInters)
        helper.loadRect4Show("default", cbRect)

        helper.clearActivity()
        timers.advanceTo(AdsPlacementConfig.LOAD_TIMEOUT_MS)
        inters.loads.single().onLoaded("full_home", "full_home", "t", null, null)
        rect.loads.single().onLoaded("rect_default", "rect_default", "id", null, null)

        assertEquals(listOf("fail:load timeout"), cbInters.events)
        assertEquals(listOf("fail:load timeout"), cbRect.events)
        assertFalse("sau load timeout không còn giữ cb (inters)", LoadCallbacks.holds(plcf("full_home").cbLoad, cbInters))
        assertFalse("sau load timeout không còn giữ cb (rect)", LoadCallbacks.holds(plcf("rect_default").cbLoad, cbRect))
    }

    /** (u) Banner đang load → `clearActivity()` → host mới `showBanner(cbB)` (ghi đè có từ trước) → `cbB` nhận, `cb1` không. */
    @Test
    fun `u - banner dang load, clearActivity, host moi showBanner cbB - cbB nhan loaded, cb1 khong`() = withHelper {
        val cb1 = AppLoadCb()
        helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = cb1, cbShow = null)
        assertTrue(plcf("bn_home").isLoading)

        helper.clearActivity()
        val cbB = AppLoadCb()
        helper.showBanner("home", shownParent(), isCollapse = false, cbLoad = cbB, cbShow = null)
        banner.loads.single().onLoaded("bn_home", "bn_home", "id-bn", ecmpWith(mock(View::class.java)), null)

        assertEquals(listOf("loaded"), cbB.events)
        assertEquals(emptyList<String>(), cb1.events)
    }

    // ═══ KB-3: hành vi mới (đỏ trên mã cũ) ═══

    /** (r) Interstitial đang load → `clearActivity()` → `onLoaded`: app nhận đúng một lần, rồi placement không còn giữ cb. */
    @Test
    fun `r - interstitial dang load, clearActivity - mot onLoaded roi placement bo tham chieu callback`() = withHelper {
        val cb = AppLoadCb()
        helper.loadInterstitialForThisTurn("home", 0, cb)

        helper.clearActivity()
        inters.loads.single().onLoaded("full_home", "full_home", "t", null, null)

        assertEquals(listOf("loaded"), cb.events)
        assertFalse(
            "sau kết thúc, placement không được giữ callback của host đã huỷ",
            LoadCallbacks.holds(plcf("full_home").cbLoad, cb),
        )
    }

    /**
     * (s) Review Task 3 §4: `loadRect4Show(cb1)` đang load → `clearActivity()` → host mới `showRect(parent, cb2)` (gắn) →
     * `onLoaded`: cả hai nhận đúng một lần; placement giữ `cb2` (host sống), không giữ `cb1` (host đã huỷ).
     */
    @Test
    fun `s - clearActivity khi dang load giu ca hai callback, sau ket thuc chi giu callback cua host moi`() = withHelper {
        val cb1 = AppLoadCb()
        helper.loadRect4Show("default", cb1)

        helper.clearActivity()
        val cb2 = AppLoadCb()
        helper.showRect("default", shownParent(), cb2)
        rect.loads.single().onLoaded("rect_default", "rect_default", "id", ecmpWith(mock(View::class.java)), null)

        assertEquals("cb1 (host đã huỷ) nhận kết thúc của lượt", listOf("loaded"), cb1.events)
        assertEquals("cb2 (host mới) gắn vào lượt đang chạy", listOf("loaded"), cb2.events)
        assertFalse("không giữ cb1 sau kết thúc", LoadCallbacks.holds(plcf("rect_default").cbLoad, cb1))
        assertTrue("giữ cb2 (host sống)", LoadCallbacks.holds(plcf("rect_default").cbLoad, cb2))
    }

    /** (t) Rewarded đang load → `clearActivity()` → cùng instance (callback cấp Application) gọi lại → đúng một lần. */
    @Test
    fun `t - rewarded dang load, clearActivity, cung instance goi lai - dung mot onLoaded`() = withHelper {
        val cbS = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cbS)

        helper.clearActivity()
        helper.loadRewardedForThisTurn("x", cbS)
        assertEquals("không gọi mạng lần hai", 1, gift.loads.size)
        gift.loads.single().onLoaded("gift_x", "gift_x", "t", null, null)

        assertEquals("cùng instance: khử trùng nhìn xuyên wrapper", listOf("loaded"), cbS.events)
    }

    /**
     * (t2) Như (t) nhưng host mới gắn thêm `cb2` trước khi `cbS` gọi lại: `cbS` nằm trong wrapper bên trong fan-out — khử trùng
     * phải nhìn xuyên cả hai lớp; mỗi callback đúng một lần.
     */
    @Test
    fun `t2 - rewarded dang load, clearActivity, cb2 gan them roi cbS goi lai - moi callback dung mot lan`() = withHelper {
        val cbS = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cbS)

        helper.clearActivity()
        val cb2 = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cb2)
        helper.loadRewardedForThisTurn("x", cbS)
        gift.loads.single().onLoaded("gift_x", "gift_x", "t", null, null)

        assertEquals("cbS không nhân đôi", listOf("loaded"), cbS.events)
        assertEquals(listOf("loaded"), cb2.events)
    }

    /**
     * (R2) Fan-out trong wrapper: `(cb1)`, `(cb2)` → `clearActivity()` → `(cb2)` lần nữa → `onLoaded`: mỗi callback một lần
     * (khử trùng nhìn xuyên `OneShotLoadCb` rồi fan-out), sau kết thúc placement không giữ callback nào của host đã huỷ.
     */
    @Test
    fun `R2 - fan-out trong OneShot, cb2 goi lai sau clearActivity - khong nhan doi, bo tham chieu`() = withHelper {
        val cb1 = AppLoadCb()
        val cb2 = AppLoadCb()
        helper.loadRewardedForThisTurn("x", cb1)
        helper.loadRewardedForThisTurn("x", cb2)

        helper.clearActivity()
        helper.loadRewardedForThisTurn("x", cb2)
        gift.loads.single().onLoaded("gift_x", "gift_x", "t", null, null)

        assertEquals(listOf("loaded"), cb1.events)
        assertEquals("cb2 không nhân đôi", listOf("loaded"), cb2.events)
        assertFalse(LoadCallbacks.holds(plcf("gift_x").cbLoad, cb1))
        assertFalse(LoadCallbacks.holds(plcf("gift_x").cbLoad, cb2))
    }

    /** (R1) Hai object khác nhau nhưng `equals` nhau là hai request: mỗi cái đúng một `onLoaded` (khử trùng theo `===`). */
    @Test
    fun `R1 - hai callback equals nhau nhung khac object - moi cai mot onLoaded`() = withHelper {
        val cb1 = AppLoadCb()
        val a = EqCb()
        val b = EqCb()
        assertTrue(a == b && a !== b)
        helper.loadRewardedForThisTurn("x", cb1)
        helper.loadRewardedForThisTurn("x", a)
        helper.loadRewardedForThisTurn("x", b)
        gift.loads.single().onLoaded("gift_x", "gift_x", "t", null, null)

        assertEquals(listOf("loaded"), cb1.events)
        assertEquals("a", listOf("loaded"), a.events)
        assertEquals("b (equals a, khác object) không được mất kết thúc", listOf("loaded"), b.events)
    }

    /** Hai target ném: mọi target được giao, lỗi đầu tiên lan ra, lỗi sau nằm trong `suppressed` (review Task 5 I3). */
    @Test
    fun `fan-out hai target nem - loi dau lan ra, loi sau suppressed, target con lai van nhan`() = withHelper {
        fun throwing(msg: String) = object : AdsLoadCb {
            override fun onLoaded(placement: String, originPl: String, adId: String) = throw IllegalStateException(msg)
            override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = Unit
        }
        val cb3 = AppLoadCb()
        listOf(throwing("first"), throwing("second"), cb3).forEach { helper.loadRewardedForThisTurn("x", it) }

        val thrown = runCatching { gift.loads.single().onLoaded("gift_x", "gift_x", "t", null, null) }.exceptionOrNull()

        assertEquals(listOf("loaded"), cb3.events)
        assertEquals("first", thrown?.message)
        assertEquals(listOf("second"), thrown?.suppressed?.map { it.message })
    }

    /** (v) Log: một placement đang load có callback → dòng đếm đúng một lần. */
    @Test
    fun `v - log in-flight load callbacks khi placement dang load co callback`() = withHelper {
        helper.loadInterstitialForThisTurn("home", 0, AppLoadCb())
        LogUtil.setFlagLog(3)
        mockStatic(Log::class.java).use { log ->
            helper.clearActivity()
            log.verify(
                { Log.d(eq(LogUtil.TAG), eq("ads helper clearActivity: 1 in-flight load callbacks released after terminal")) },
                times(1),
            )
        }
    }

    /** (v2) Placement đang load với `cbLoad = null` → không có dòng log in-flight, không bọc. */
    @Test
    fun `v2 - placement dang load khong callback - khong log in-flight`() = withHelper {
        helper.loadInterstitialForThisTurn("home", 0, null)
        assertTrue(plcf("full_home").isLoading)
        LogUtil.setFlagLog(3)
        mockStatic(Log::class.java).use { log ->
            helper.clearActivity()
            log.verify({ Log.d(eq(LogUtil.TAG), contains("in-flight")) }, never())
        }
        assertEquals(null, plcf("full_home").cbLoad)
    }

    /** `clearActivity` hai lần trong cùng lượt: không bọc lồng; vẫn đúng một kết thúc. */
    @Test
    fun `clearActivity hai lan trong mot luot - khong boc long, dung mot ket thuc`() = withHelper {
        val cb = AppLoadCb()
        helper.loadInterstitialForThisTurn("home", 0, cb)
        helper.clearActivity()
        LogUtil.setFlagLog(3)
        mockStatic(Log::class.java).use { log ->
            helper.clearActivity()
            log.verify({ Log.d(eq(LogUtil.TAG), contains("in-flight")) }, never())
        }
        inters.loads.single().onLoaded("full_home", "full_home", "t", null, null)
        assertEquals(listOf("loaded"), cb.events)
        assertFalse("sau kết thúc không còn giữ cb", LoadCallbacks.holds(plcf("full_home").cbLoad, cb))
    }
}
