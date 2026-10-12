package com.app.plugin.ads

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.util.Log
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * 1.6.0 Task 5 (O7 (a)): bốn hàm load từng im lặng ở cổng `cf not` (`flagShow < 0`) và "đang load" (CHANGELOG 1.4.0/1.5.0
 * "Giới hạn đã biết"); `showRect` khi đang load ghi đè callback của lượt đang chạy. Hợp đồng mới: mỗi request nhận đúng một
 * callback kết thúc — "đang load" gắn vào lượt đang chạy (fan-out), `cf not` báo `onLoadFailed(…, "cf not")` ngay; load lại
 * trong `onLoadFailed` không đệ quy đồng bộ (lời gọi lồng post sang vòng kế). Bảng [gates] theo mẫu `AdsDisabledLoadGateTest`.
 */
class LoadGateCallbackTest {

    private class GiftNet(c: Context) : AdsBaseGift(AdsIDDef.AdmobRewarded, c) {
        override val eventTag: String get() = "test_gift"
        val loads = mutableListOf<AdsLoadInnerCb>(); override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_gift_data"; override fun getKeyForResDf(): String = "test_gift_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
    }

    private class NtFullNet(c: Context) : AdsBaseFull(AdsIDDef.AdmobFullNt, c) {
        override val eventTag: String get() = "test_nt_full"
        val loads = mutableListOf<AdsLoadInnerCb>(); override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_nt_full_data"; override fun getKeyForResDf(): String = "test_nt_full_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
    }

    private class NtClNet(c: Context) : AdsBaseBanner(AdsIDDef.AdmobNtCl, c) {
        override val eventTag: String get() = "test_ntcl"
        val loads = mutableListOf<AdsLoadInnerCb>(); override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_ntcl_data"; override fun getKeyForResDf(): String = "test_ntcl_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
    }

    private class RectNet(c: Context) : AdsBaseRect(AdsIDDef.AdmobRect, c) {
        override val eventTag: String get() = "test_rect"
        val loads = mutableListOf<AdsLoadInnerCb>()
        var cached: Any? = null
        override fun getNewAdsPl(placement: String): AdsPlBase? = null
        override fun getKeyForDataSave(): String = "test_rect_data"; override fun getKeyForResDf(): String = "test_rect_res"
        override fun loadAd(placement: String, adsLoadCb: AdsLoadInnerCb) { loads += adsLoadCb }
        override fun getAdForShow(placement: String, adsShowCb: AdsShowCb): Any? = cached
    }

    /** Callback của app; [onFail] chạy trong `onLoadFailed` (app load lại ngay trong callback). */
    private class AppCb(private val onFail: (AppCb) -> Unit = {}) : AdsLoadCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
            events += "fail:$error"
            onFail(this)
        }
    }

    private class ThrowingCb : AdsLoadCb {
        override fun onLoaded(placement: String, originPl: String, adId: String) = throw RuntimeException("boom")
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) = throw RuntimeException("boom")
    }

    /** Một cổng load: tên hàm, placement thật, cách gọi `load<X>("home", cb)`. */
    private class Gate(val name: String, val realPl: String, val load: (AdsHelper, AdsLoadCb?) -> Unit)

    private val gates = listOf(
        Gate("loadRewardedForThisTurn", "gift_home") { h, cb -> h.loadRewardedForThisTurn("home", cb) },
        Gate("loadNativeFullForThisTurn", "full_home") { h, cb -> h.loadNativeFullForThisTurn("home", cb) },
        Gate("loadNtClForThisTurn", "cl_home") { h, cb -> h.loadNtClForThisTurn("home", cb) },
        Gate("loadRect4Show", "rect_home") { h, cb -> h.loadRect4Show("home", cb) },
    )

    private class Fixture(
        val helper: AdsHelper,
        val gift: GiftNet,
        val ntFull: NtFullNet,
        val ntcl: NtClNet,
        val rect: RectNet,
        val timers: MainHandlerTimers,
    ) {
        fun plcf(pl: String): AdsPlacementConfig = helper.getAdsPlacementConfig(pl, 1)!!
        fun loads(gate: Gate): List<AdsLoadInnerCb> = when (gate.realPl) {
            "gift_home" -> gift.loads
            "full_home" -> ntFull.loads
            "cl_home" -> ntcl.loads
            else -> rect.loads
        }
        fun loaded(gate: Gate, i: Int = 0) = loads(gate)[i].onLoaded(gate.realPl, gate.realPl, "id", null, null)
    }

    /** `AdsHelper` dựng TRONG [MainHandlerTimers.run]: mọi `Handler` (watchdog 90 s, `post` của vòng, `cf not` lồng) ảo. */
    private fun withHelper(check: Fixture.() -> Unit) = MainHandlerTimers.run { timers ->
        val helper = AdsKillSwitchTest.newAdsHelper()
        helper.currConfig.apply {
            full2FlagShow = 0
            giftStepCicle.clear(); giftStepCicle.add(AdsIDDef.AdmobRewarded); giftStepRe.clear()
            clStepList.add(AdsIDDef.AdmobNtCl)
            rectStepList.add(AdsIDDef.AdmobRect)
        }
        val f = Fixture(helper, GiftNet(ctx()), NtFullNet(ctx()), NtClNet(ctx()), RectNet(ctx()), timers)
        setField(helper, "listAdsGift", mutableMapOf<Int, AdsBaseGift>(AdsIDDef.AdmobRewarded to f.gift))
        setField(helper, "listAdsFull", mutableMapOf<Int, AdsBaseFull>(AdsIDDef.AdmobFullNt to f.ntFull))
        setField(helper, "listAdsNtCl", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobNtCl to f.ntcl))
        setField(helper, "listAdsRect", mutableMapOf<Int, AdsBaseBanner>(AdsIDDef.AdmobRect to f.rect))
        setField(helper, "listAdsAll", mutableListOf<AdsBase>(f.gift, f.ntFull, f.ntcl, f.rect))
        f.check()
    }

    /** Mỗi cổng một fixture mới; chạy hết bốn cổng rồi mới báo, để log đỏ ghi đủ cổng nào hỏng (tên cổng trong message). */
    private fun eachGate(check: Fixture.(Gate) -> Unit) {
        val failures = gates.mapNotNull { g ->
            runCatching { withHelper { check(g) } }.exceptionOrNull()?.let { e -> "${g.name}: $e" }
        }
        if (failures.isNotEmpty()) throw AssertionError(failures.joinToString("\n"))
    }

    private fun ctx(): Context {
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

    private fun shownParent(): ViewGroup = mock(ViewGroup::class.java).also { `when`(it.isAttachedToWindow).thenReturn(true) }

    private fun ecmpWith(adObject: Any?): AdsEcmp = mock(AdsEcmp::class.java).also { `when`(it.adObject).thenReturn(adObject) }

    // ═══ KB-2: hành vi giữ (xanh trên mã cũ) ═══

    @Test fun `luot dau - mang onLoaded - callback nhan loaded dung mot lan (4 ham)`() = eachGate { g ->
        val cb1 = AppCb()
        g.load(helper, cb1)
        assertEquals("${g.name}: một lượt mạng", 1, loads(g).size)
        assertTrue("${g.name}: đang load", plcf(g.realPl).isLoading)
        loaded(g)
        timers.runPosted()
        assertEquals("${g.name}: đúng một onLoaded", listOf("loaded"), cb1.events)
        assertFalse(plcf(g.realPl).isLoading)
    }

    // ═══ KB-3: hành vi mới (đỏ trên mã cũ) ═══

    /** (a) `cf not` → `onLoadFailed(…, "cf not")` đồng bộ, đúng một lần, không gọi mạng, không bật `isLoading`. */
    @Test fun `a - cf not bao onLoadFailed cf not ngay, dung mot lan, khong goi mang`() = eachGate { g ->
        plcf(g.realPl).flagShow = -1
        val cb = AppCb()
        g.load(helper, cb)
        assertEquals("${g.name}: cf not phải báo ngay trên luồng gọi (trước runPosted)", listOf("fail:cf not"), cb.events)
        timers.runPosted()
        assertEquals("${g.name}: đúng một lần", listOf("fail:cf not"), cb.events)
        assertEquals("${g.name}: không gọi mạng", 0, loads(g).size)
        assertFalse(plcf(g.realPl).isLoading)
    }

    /** (b) Đang load: callback thứ hai gắn vào lượt đang chạy; mạng gọi một lần; cả hai nhận `onLoaded` đúng một lần. */
    @Test fun `b - dang load, callback thu hai gan vao luot dang chay - ca hai nhan loaded mot lan`() = eachGate { g ->
        val cb1 = AppCb()
        val cb2 = AppCb()
        g.load(helper, cb1)
        g.load(helper, cb2)
        assertEquals("${g.name}: mạng gọi một lần", 1, loads(g).size)
        assertEquals(emptyList<String>(), cb1.events + cb2.events)
        loaded(g)
        timers.runPosted()
        assertEquals("${g.name}: cb1 (lượt đầu)", listOf("loaded"), cb1.events)
        assertEquals("${g.name}: cb2 (gọi lúc đang load) phải nhận kết quả của lượt đang chạy", listOf("loaded"), cb2.events)
    }

    /** (c) Như (b), mạng lỗi hết danh sách → cả hai `list empty` đúng một lần. */
    @Test fun `c - dang load, mang loi het - ca hai nhan list empty mot lan`() = eachGate { g ->
        val cb1 = AppCb()
        val cb2 = AppCb()
        g.load(helper, cb1)
        g.load(helper, cb2)
        loads(g)[0].onLoadFailed(g.realPl, g.realPl, "id", "no fill")
        timers.runPosted()
        assertEquals("${g.name}: cb1", listOf("fail:list empty"), cb1.events)
        assertEquals("${g.name}: cb2 phải nhận list empty của lượt đang chạy", listOf("fail:list empty"), cb2.events)
        assertEquals(1, loads(g).size)
    }

    /** (d) Cùng một instance gọi hai lần lúc đang load → một callback (fan-out khử trùng). */
    @Test fun `d - cung instance goi hai lan - dung mot callback`() = eachGate { g ->
        val cb1 = AppCb()
        g.load(helper, cb1)
        g.load(helper, cb1)
        assertSame("${g.name}: cbLoad không đổi (trùng)", cb1, plcf(g.realPl).cbLoad)
        loaded(g)
        timers.runPosted()
        assertEquals("${g.name}: không nhân đôi", listOf("loaded"), cb1.events)
    }

    /** (d2) `cb1`, `cb2`, `cb1` lúc đang load → fan-out khử trùng xuyên danh sách: mỗi callback đúng một lần. */
    @Test fun `d2 - cb1 cb2 cb1 khi dang load - moi callback dung mot lan`() = eachGate { g ->
        val cb1 = AppCb()
        val cb2 = AppCb()
        g.load(helper, cb1)
        g.load(helper, cb2)
        g.load(helper, cb1)
        loaded(g)
        timers.runPosted()
        assertEquals("${g.name}: cb1 không nhân đôi", listOf("loaded"), cb1.events)
        assertEquals("${g.name}: cb2", listOf("loaded"), cb2.events)
    }

    /** (e) Đang load, lời gọi không callback (rewarded tự nạp lại sau dismiss) → `cbLoad` không đổi. */
    @Test fun `e - dang load, goi voi callback null - cbLoad khong doi`() = eachGate { g ->
        val cb1 = AppCb()
        g.load(helper, cb1)
        g.load(helper, null)
        assertSame("${g.name}", cb1, plcf(g.realPl).cbLoad)
        loaded(g)
        assertEquals(listOf("loaded"), cb1.events)
    }

    /** (f) Mạng treo → watchdog 90 s: cả hai callback nhận `load timeout` đúng một lần; `onLoaded` muộn không tới. */
    @Test fun `f - load timeout - ca hai nhan load timeout mot lan, onLoaded muon khong toi`() = eachGate { g ->
        val cb1 = AppCb()
        val cb2 = AppCb()
        g.load(helper, cb1)
        g.load(helper, cb2)
        timers.advanceTo(AdsPlacementConfig.LOAD_TIMEOUT_MS)
        assertEquals("${g.name}: cb1", listOf("fail:load timeout"), cb1.events)
        assertEquals("${g.name}: cb2 phải nhận load timeout của lượt đang chạy", listOf("fail:load timeout"), cb2.events)
        loaded(g)
        timers.runPosted()
        assertEquals("${g.name}: onLoaded muộn bị guard loadCycle chặn", listOf("fail:load timeout"), cb1.events)
        assertEquals(listOf("fail:load timeout"), cb2.events)
    }

    /**
     * (g) Rect: `loadRect4Show(cb1)` rồi `showRect(parent, cb2)` lúc đang load, không cache → cả hai nhận `onLoaded` của
     * lượt đang chạy (mã cũ: `showRect` ghi đè `cbLoad` → `cb1` không bao giờ nhận kết thúc). Lượt do `loadRect4Show` mở
     * không mang container (`itemShow = null`, có từ trước): view không được gắn bởi lượt này — app gọi `showRect` lại.
     */
    @Test fun `g - loadRect4Show cb1 roi showRect cb2 khi dang load khong cache - ca hai nhan loaded`() = withHelper {
        val cb1 = AppCb()
        val cb2 = AppCb()
        helper.loadRect4Show("home", cb1)
        helper.showRect("home", shownParent(), cb2)
        assertEquals("không gọi mạng lần hai", 1, rect.loads.size)
        rect.loads[0].onLoaded("rect_home", "rect_home", "id", ecmpWith(mock(View::class.java)), null)
        assertEquals("cb1 (loadRect4Show) không được mất kết thúc", listOf("loaded"), cb1.events)
        assertEquals("cb2 (showRect lúc đang load)", listOf("loaded"), cb2.events)
    }

    /** (g2) `showRect` hai lần cùng container lúc đang load (lượt mang container): view gắn một lần, cả hai `onLoaded`. */
    @Test fun `g2 - showRect hai lan cung container khi dang load - view gan mot lan, ca hai nhan loaded`() = withHelper {
        val parent = shownParent()
        val adView = mock(View::class.java)
        val cb1 = AppCb()
        val cb2 = AppCb()
        helper.showRect("home", parent, cb1)
        helper.showRect("home", parent, cb2)
        assertEquals(1, rect.loads.size)
        rect.loads[0].onLoaded("rect_home", "rect_home", "id", ecmpWith(adView), null)
        verify(parent, times(1)).addView(adView)
        assertEquals(listOf("loaded"), cb1.events)
        assertEquals("cb2 gắn vào lượt đang chạy", listOf("loaded"), cb2.events)
    }

    /** (h) Đang load, có cache: `showRect(cb2)` nhận `onLoaded` ngay từ cache; `cb1` nhận kết thúc của lượt; không nhân đôi. */
    @Test fun `h - dang load co cache - showRect cb2 nhan loaded ngay, cb1 nhan ket qua cua luot`() = withHelper {
        val cb1 = AppCb()
        val cb2 = AppCb()
        val parent = shownParent()
        val adView = mock(View::class.java)
        helper.loadRect4Show("home", cb1)
        rect.cached = ObjectForShowAdWithViewParent().apply { adLoad = adView; adsEcmp = mock(AdsEcmp::class.java) }
        helper.showRect("home", parent, cb2)
        verify(parent, times(1)).addView(adView)
        assertEquals("cb2 nhận từ cache ngay", listOf("loaded"), cb2.events)
        assertEquals("cb1 chưa nhận (lượt còn chạy)", emptyList<String>(), cb1.events)
        rect.loads[0].onLoaded("rect_home", "rect_home", "id", null, null)
        assertEquals("cb1 nhận kết quả lượt của mình", listOf("loaded"), cb1.events)
        assertEquals("cb2 không nhận thêm kết quả của lượt", listOf("loaded"), cb2.events)
    }

    /** (h2) `showRect` trên container đã gắn view lúc đang load: gắn vào lượt đang chạy, không ghi đè (D2). */
    @Test fun `h2 - showRect container da gan khi dang load - gan callback, ca hai nhan loaded`() = withHelper {
        val parent = shownParent()
        rect.cached = ObjectForShowAdWithViewParent().apply { adLoad = mock(View::class.java); adsEcmp = mock(AdsEcmp::class.java) }
        helper.showRect("home", parent, AppCb())
        rect.cached = null
        val cb1 = AppCb()
        val cb2 = AppCb()
        helper.loadRect4Show("home", cb1)
        helper.showRect("home", parent, cb2)
        rect.loads.single().onLoaded("rect_home", "rect_home", "id", null, null)
        assertEquals("cb1 không được mất kết thúc", listOf("loaded"), cb1.events)
        assertEquals("cb2 gắn vào lượt đang chạy", listOf("loaded"), cb2.events)
    }

    /** (i) Load lại ngay trong `onLoadFailed("cf not")`: không ném, không đệ quy đồng bộ — lời gọi lồng post sang vòng kế. */
    @Test fun `i - load lai trong onLoadFailed cf not - loi goi long post sang vong ke, khong de quy`() = eachGate { g ->
        plcf(g.realPl).flagShow = -1
        val cb = AppCb(onFail = { self -> if (self.events.size == 1) g.load(helper, self) })
        g.load(helper, cb)
        assertEquals("${g.name}: lời gọi lồng không được báo đồng bộ", 1, cb.events.size)
        timers.runPosted()
        assertEquals("${g.name}: lời gọi lồng nhận đúng một cf not ở vòng kế", listOf("fail:cf not", "fail:cf not"), cb.events)
    }

    /** (i2) App load lại vô hạn trong `onLoadFailed`: không StackOverflowError, một callback trên luồng gọi, một `post`. */
    @Test fun `i2 - load lai vo han trong onLoadFailed cf not - khong StackOverflow, mot callback, mot post`() = eachGate { g ->
        plcf(g.realPl).flagShow = -1
        val cb = AppCb(onFail = { self -> g.load(helper, self) })
        g.load(helper, cb)
        assertEquals("${g.name}", 1, cb.events.size)
        assertEquals("${g.name}: đúng một post chờ vòng kế", 1, timers.pendingCount)
    }

    /**
     * (i3) App load lại N lần trong `onLoadFailed`: mỗi lần tới ở một vòng looper riêng — callback không bao giờ được gọi
     * lồng trong chính nó (kể cả lời gọi đã post: nó đi lại qua cổng chặn đệ quy).
     */
    @Test fun `i3 - load lai nhieu lan trong onLoadFailed cf not - khong goi long, du N callback`() = eachGate { g ->
        plcf(g.realPl).flagShow = -1
        var depth = 0
        var maxDepth = 0
        val events = mutableListOf<String>()
        val cb = object : AdsLoadCb {
            override fun onLoaded(placement: String, originPl: String, adId: String) { events += "loaded" }
            override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) {
                depth++
                maxDepth = maxOf(maxDepth, depth)
                events += "fail:$error"
                if (events.size < 5) g.load(helper, this)
                depth--
            }
        }
        g.load(helper, cb)
        timers.runPosted()
        assertEquals("${g.name}: đủ 5 callback", List(5) { "fail:cf not" }, events)
        assertEquals("${g.name}: không lần nào gọi lồng", 1, maxDepth)
    }

    /** (i4) Callback ném trong `onLoadFailed("cf not")`: lỗi về app; lời gọi sau (callback khác) vẫn báo đồng bộ. */
    @Test fun `i4 - callback nem trong cf not - loi goi sau van bao dong bo`() = eachGate { g ->
        plcf(g.realPl).flagShow = -1
        val thrown = runCatching { g.load(helper, ThrowingCb()) }.exceptionOrNull()
        assertEquals("${g.name}: lỗi của callback app về luồng gọi như trước", "boom", thrown?.message)
        val cb2 = AppCb()
        g.load(helper, cb2)
        assertEquals("${g.name}: cổng chặn đệ quy đã mở lại (finally)", listOf("fail:cf not"), cb2.events)
    }

    /** (j) Một target ném: target kia vẫn nhận; lỗi của app lan ra như khi chỉ có một callback; không log nuốt lỗi. */
    @Test fun `j - mot target nem trong fan-out - target kia van nhan, loi lan ra`() = eachGate { g ->
        val cb2 = AppCb()
        g.load(helper, ThrowingCb())
        g.load(helper, cb2)
        mockStatic(Log::class.java).use { log ->
            val thrown = runCatching { loaded(g) }.exceptionOrNull()
            assertEquals("${g.name}: cb2 phải nhận dù cb1 ném", listOf("loaded"), cb2.events)
            assertEquals("${g.name}: lỗi của callback app lan ra (như một callback)", "boom", thrown?.message)
            log.verify({ Log.e(anyString(), anyString()) }, never())
        }
    }

    /** (R7) Như (j) ở nhánh `onLoadFailed` (`load timeout`). */
    @Test fun `R7 - mot target nem o load timeout - target kia van nhan, loi lan ra`() = eachGate { g ->
        val cb2 = AppCb()
        g.load(helper, ThrowingCb())
        g.load(helper, cb2)
        val thrown = runCatching { timers.advanceTo(AdsPlacementConfig.LOAD_TIMEOUT_MS) }.exceptionOrNull()
        assertEquals("${g.name}: cb2 phải nhận dù cb1 ném", listOf("fail:load timeout"), cb2.events)
        assertEquals("${g.name}: lỗi của callback app lan ra", "boom", thrown?.message)
    }

    /** `cf not` không có callback: không post, không ném. */
    @Test fun `cf not voi callback null - khong nem, khong post`() = eachGate { g ->
        plcf(g.realPl).flagShow = -1
        g.load(helper, null)
        assertEquals("${g.name}", 0, timers.pendingCount)
        assertEquals(0, loads(g).size)
    }
}
