package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.os.Handler
import android.view.ViewGroup
import com.app.plugin.ads.core.AdPreloader
import com.app.plugin.ads.core.AdsTelemetry
import com.app.plugin.ads.core.Full2Chain
import com.app.plugin.ads.core.NativeController
import com.app.plugin.ads.core.PreloadFormat
import com.app.plugin.ads.core.PreloadSlot
import com.app.plugin.ads.core.UnitGate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * 2.5.0 PRIO (Hợp đồng A, quyết định (a), Fable C1/C2): mức lượt SHOW > APP > PRELOAD đọc lúc quyết; hàng chờ nhả người
 * mức cao nhất (cùng mức FIFO); lượt mới gặp chủ slot khác mức thấp hơn **chưa gửi** id ⇒ đi luôn, chủ bị đánh dấu nhường
 * và hẹn `tryLoad` của chủ vào lại cổng (hẹn fail của hàng đóng tiêu dấu rồi fail); lượt đã gửi không bị giành; trần chờ
 * 10 s của người chờ PRELOAD vẫn bypass như 2.4.4; `ads_cf_unit_gate_prio_off` = 1 ⇒ 2.4.4.
 */
class UnitGatePrioTest {
    private val unit = "ca-app-pub-3940256099942544/6781010236"
    private val tel = AdsTelemetry(mock(AdsHelper::class.java))

    @After fun tearDown() = UnitGate.resetForTest()

    private fun probe(slot: String, net: GateNet, ctx: Context = mock(Context::class.java), tag: String = "mob_full", queue: SdkReadyQueue? = null) =
        GateProbe(ctx, slot, tel, net, tag, queue).apply { setActivity(mock(Activity::class.java)); parserEcpms(unit) }

    private fun merged(pl: String, slot: String) = "load merged into in-flight request: $pl slot=$slot unit=$unit"
    private fun yields(to: String, pl: String, slot: String) = "load yields unit to $to: $pl slot=$slot unit=$unit"

    private fun prioOffContext(): Context {
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(res.getIdentifier("ads_cf_unit_gate_prio_off", "integer", "app.test")).thenReturn(42)
        `when`(res.getInteger(42)).thenReturn(1)
        return mock(Context::class.java).also { `when`(it.resources).thenReturn(res); `when`(it.packageName).thenReturn("app.test") }
    }

    /** F-Q1 (e244-firstrun.log:2306, :45862): preload Full2 giữ chờ init đặt chỗ unit; nt_splash tới sau phải xin trước. */
    private fun firstRun(ctx: Context, timers: MainHandlerTimers): GateNet {
        val net = GateNet().apply { ready = false }
        val full2 = probe("full_default", net, ctx); val nt = probe("nt_default", net, ctx)
        full2.load("full_2_extra_clean", GateCb())
        nt.load("nt_splash", GateCb())
        net.open(); timers.runPosted()
        return net
    }

    private fun queueOrder(ctx: Context, timers: MainHandlerTimers): GateNet {
        val net = GateNet()
        val home = probe("nt_home", net, ctx); val full2 = probe("full_default", net, ctx); val lang = probe("nt_language", net, ctx)
        home.load("nt_home", GateCb())
        full2.load("full_2_default", GateCb())
        lang.load("nt_language", GateCb())
        home.succeed(); timers.runPosted()
        return net
    }

    @Test fun `P1 nt_splash xin unit truoc preload Full2 dang giu cho init`() = unitGateRun { timers, _ ->
        val net = firstRun(mock(Context::class.java), timers)
        assertEquals("nt_splash (lượt app, native màn) phải xin unit trước preload Full2", listOf("nt_splash"), net.order.take(1))
    }

    @Test fun `P2 nguoi cho muc app vuot preload Full2 trong hang`() = unitGateRun { timers, _ ->
        assertEquals(listOf("nt_home", "nt_language"), queueOrder(mock(Context::class.java), timers).order)
    }

    @Test fun `P5 ads_cf_unit_gate_prio_off bang 1 thi FIFO va khong nhuong nhu 244`() = unitGateRun { timers, infos ->
        assertEquals("P1 tắt: preload giữ trước vẫn xin trước", listOf("full_2_extra_clean"), firstRun(prioOffContext(), timers).order.take(1))
        assertEquals(0, infos.count { it.startsWith("load yields") })
        UnitGate.resetForTest { timers.nowMs }
        assertEquals("P2 tắt: FIFO", listOf("nt_home", "full_2_default"), queueOrder(prioOffContext(), timers).order)
    }

    @Test fun `P6 dung mot dong Log_i load yields khi preload nhuong nt_splash`() = unitGateRun { timers, infos ->
        firstRun(mock(Context::class.java), timers)
        assertEquals(listOf(yields("nt_splash", "full_2_extra_clean", "full_default")), infos.filter { it.startsWith("load yields") })
        assertEquals("chủ nhường vào lại cổng thì chờ lượt mới", listOf(merged("full_2_extra_clean", "full_default")),
            infos.filter { it.startsWith("load merged") })
    }

    /** Chủ đã gửi id (request đang bay) không bao giờ bị giành: GMA không huỷ được request. */
    @Test fun `P9 chu da gui thi khong nhuong, luot moi cho`() = unitGateRun { timers, infos ->
        val net = GateNet()
        val full2 = probe("full_default", net); val nt = probe("nt_default", net)
        full2.load("full_2_extra_clean", GateCb())
        nt.load("nt_splash", GateCb())
        assertEquals(listOf(emptyList<String>(), listOf(merged("nt_splash", "nt_default"))), listOf(nt.requested, infos))
        full2.succeed(); timers.runPosted()
        assertEquals(listOf("full_2_extra_clean", "nt_splash"), net.order)
    }

    /** Fable C1(a): hàng mạng đóng (Meta DENIED) khi chủ đã nhường ⇒ hẹn fail tiêu dấu rồi fail ngay, không vào lại cổng. */
    @Test fun `P7 chu nhuong ma hang mang dong thi mot onLoadFailed, 0 request`() = unitGateRun { timers, _ ->
        val queue = SdkReadyQueue(); val net = GateNet()
        val owner = probe("full_default", net, tag = "fb_full", queue = queue); val nt = probe("nt_default", net, tag = "fb_full")
        val cb = GateCb()
        owner.load("full_2_extra_clean", cb)
        nt.load("nt_splash", GateCb())
        assertEquals("nt_splash gửi ngay (chủ chưa gửi, mức thấp hơn)", listOf(unit), nt.requested)
        queue.close(); timers.runPosted()
        assertEquals(listOf(listOf("fail:network_not_started"), emptyList<String>()), listOf(cb.events, owner.requested))
        assertEquals("slot chủ rảnh", false, owner.isLoadingAd)
        nt.succeed(); timers.advanceTo(70_000)
        assertEquals(listOf(listOf("fail:network_not_started"), emptyList<String>()), listOf(cb.events, owner.requested))
    }

    /** Fable C2: chủ nhường vào lại cổng là người chờ PRELOAD, trần 10 s vẫn bypass như 2.4.4. */
    @Test fun `P8 chu nhuong van chiu tran cho 10 s`() = unitGateRun { timers, infos ->
        val net = GateNet().apply { ready = false }
        val owner = probe("full_default", net); val nt = probe("nt_default", net)
        owner.load("full_2_extra_clean", GateCb())
        nt.load("nt_splash", GateCb())
        timers.advanceTo(100)
        net.open() // chủ vào hàng sau nt_splash; nt_splash gửi và không bao giờ callback
        timers.advanceTo(10_099)
        assertEquals(listOf(listOf(unit), emptyList<String>()), listOf(nt.requested, owner.requested))
        timers.advanceTo(10_100)
        assertEquals("hết trần 10 s: chủ nhường gửi như 2.4.4", listOf(unit), owner.requested)
        assertEquals("load released after 10000 ms: full_2_extra_clean slot=full_default unit=$unit", infos.last())
    }

    /** Chủ nhường mà mạng không bao giờ sẵn: watchdog 60 s báo fail một lần, hẹn cũ không chạy (không kẹt, 0 request). */
    @Test fun `P8b chu nhuong mang khong san thi watchdog 60 s mot lan`() = unitGateRun { timers, _ ->
        val net = GateNet().apply { ready = false }
        val owner = probe("full_default", net); val nt = probe("nt_default", net)
        val cb = GateCb()
        owner.load("full_2_extra_clean", cb)
        nt.load("nt_splash", GateCb())
        timers.advanceTo(60_000)
        assertEquals(listOf("fail:load timeout"), cb.events)
        net.open(); timers.advanceTo(80_000)
        assertEquals(listOf(listOf("fail:load timeout"), emptyList<String>()), listOf(cb.events, owner.requested))
        assertEquals("slot chủ rảnh", false, owner.isLoadingAd)
    }

    @Suppress("UNCHECKED_CAST")
    private fun views(nc: NativeController) = NativeController::class.java.getDeclaredField("nativeMapView")
        .apply { isAccessible = true }.get(nc) as MutableMap<ViewGroup, ObjectForShowAdWithViewParent>

    private fun pending(
        map: MutableMap<ViewGroup, ObjectForShowAdWithViewParent>, pl: String, attached: Boolean = true,
    ): Pair<ViewGroup, ObjectForShowAdWithViewParent> {
        val parent = mock(ViewGroup::class.java).also { `when`(it.isAttachedToWindow).thenReturn(attached) }
        return parent to ObjectForShowAdWithViewParent().apply { placement = pl; parentView = parent; isShow = true }.also { map[parent] = it }
    }

    /** View native chờ gắn ⇒ SHOW (vị từ đọc lúc quyết trên map view), vượt lượt APP vào hàng trước; gắn/ẩn ⇒ hết SHOW. */
    /** Controller native thật trên helper giả; [NativeController.showNative] (ở đây view null, dừng sớm) đăng ký vị từ SHOW. */
    private fun nativeController() = NativeController(mock(AdsHelper::class.java)).apply { showNative("probe", null, null, false) }

    @Test fun `P3 view native cho gan vuot luot app trong hang, gan hoac an thi het SHOW`() = unitGateRun { timers, _ ->
        val nc = nativeController()
        val net = GateNet()
        val x = probe("nt_x", net); val app = probe("full_home", net); val home = probe("nt_home", net)
        x.load("nt_x", GateCb())
        app.load("full_home", GateCb())
        home.load("nt_home", GateCb())
        assertEquals(UnitGate.APP, UnitGate.level("nt_home"))
        val (parent, item) = pending(views(nc), "nt_home")
        assertEquals(UnitGate.SHOW, UnitGate.level("nt_home"))
        x.succeed(); timers.runPosted()
        assertEquals("view chờ gắn vượt lượt app vào hàng trước", listOf("nt_x", "nt_home"), net.order)
        item.isAttach = true
        assertEquals("gắn xong ⇒ hết SHOW", UnitGate.APP, UnitGate.level("nt_home"))
        item.isAttach = false
        nc.hideNative(parent, true)
        assertEquals("ẩn ⇒ hết SHOW", UnitGate.APP, UnitGate.level("nt_home"))
    }

    /** Fable C1(b): hai view cùng placement chờ gắn; gắn view đầu ⇒ vẫn SHOW (còn view chờ). */
    @Test fun `P3b hai parent cung placement, gan cai dau van SHOW`() = unitGateRun { _, _ ->
        val nc = nativeController()
        val map = views(nc)
        val first = pending(map, "nt_home").second; val second = pending(map, "nt_home").second
        first.isAttach = true
        assertEquals("còn view thứ hai chờ gắn", UnitGate.SHOW, UnitGate.level("nt_home"))
        second.isAttach = true
        assertEquals(UnitGate.APP, UnitGate.level("nt_home"))
        nc.onHostCleared()
        assertEquals(UnitGate.APP, UnitGate.level("nt_home"))
    }

    /** R-LA' L1: view chờ gắn mà parent đã rời cửa sổ (Activity kết thúc, không hideNative) không giữ SHOW — như chỗ gắn. */
    @Test fun `P3c view cho gan ma parent khong con trong cua so thi khong SHOW`() = unitGateRun { _, _ ->
        val nc = nativeController()
        val map = views(nc)
        pending(map, "nt_home", attached = false)
        assertEquals("mục mồ côi không giữ SHOW", UnitGate.APP, UnitGate.level("nt_home"))
        pending(map, "nt_home")
        assertEquals("còn một view trong cửa sổ chờ gắn", UnitGate.SHOW, UnitGate.level("nt_home"))
    }

    // R-LA' (RT1–RT4, RT6 của reviewer, chép nguyên ý): cùng mức không nhường, nhường hai tầng, ẩn không xoá, C2, FIFO.
    @Test fun `RT1 cung muc APP chu chua gui thi khong nhuong`() = unitGateRun { timers, infos ->
        val net = GateNet().apply { ready = false }
        val a = probe("full_home", net); val b = probe("full_default", net)
        a.load("full_home", GateCb())
        b.load("full_x", GateCb())
        assertEquals("cùng mức không nhường", 0, infos.count { it.startsWith("load yields") })
        net.open(); timers.runPosted()
        assertEquals(listOf("full_home"), net.order)
        a.succeed(); timers.runPosted()
        assertEquals(listOf("full_home", "full_x"), net.order)
        assertEquals(0, infos.count { it.startsWith("load yields") })
    }

    @Test fun `RT2 nhuong hai tang SHOW roi APP roi PRELOAD`() = unitGateRun { timers, infos ->
        val nc = nativeController()
        val net = GateNet().apply { ready = false }
        val p = probe("full_default", net); val n = probe("full_home", net); val m = probe("nt_home", net)
        p.load("full_2_extra_clean", GateCb())
        n.load("full_home", GateCb())
        pending(views(nc), "nt_home")
        m.load("nt_home", GateCb())
        assertEquals(2, infos.count { it.startsWith("load yields") })
        net.open(); timers.runPosted()
        assertEquals(listOf("nt_home"), net.order)
        m.succeed(); timers.runPosted()
        assertEquals(listOf("nt_home", "full_home"), net.order)
        n.succeed(); timers.runPosted()
        assertEquals(listOf("nt_home", "full_home", "full_2_extra_clean"), net.order)
    }

    /** `hideNative(parent, isRemove = false)` giữ mục trong map với `isShow = false` ⇒ hết SHOW. */
    @Test fun `RT3 hideNative khong xoa view thi het SHOW`() = unitGateRun { _, _ ->
        val nc = nativeController()
        val (parent, _) = pending(views(nc), "nt_home")
        assertEquals(UnitGate.SHOW, UnitGate.level("nt_home"))
        nc.hideNative(parent, false)
        assertEquals(UnitGate.APP, UnitGate.level("nt_home"))
    }

    /** C2: người chờ PRELOAD vào trước vẫn bypass ở mốc 10 s của nó dù trong hàng có APP mức cao hơn. */
    @Test fun `RT4 PRELOAD trong hang van bypass o tran 10 s`() = unitGateRun { timers, _ ->
        val net = GateNet()
        val h = probe("nt_x", net); val f2 = probe("full_default", net); val lang = probe("nt_language", net)
        h.load("nt_x", GateCb()) // gửi, không bao giờ callback
        f2.load("full_2_default", GateCb())
        timers.advanceTo(1_000)
        lang.load("nt_language", GateCb())
        timers.advanceTo(9_999)
        assertEquals(listOf("nt_x"), net.order)
        timers.advanceTo(10_000)
        assertEquals(listOf("nt_x", "full_2_default"), net.order)
        timers.advanceTo(11_000)
        assertEquals(listOf("nt_x", "full_2_default", "nt_language"), net.order)
    }

    /** Cùng mức trong hàng: FIFO (đọc lúc drain). */
    @Test fun `RT6 cung muc trong hang thi FIFO`() = unitGateRun { timers, _ ->
        val net = GateNet()
        val h = probe("nt_x", net); val a = probe("nt_a", net); val b = probe("nt_b", net)
        h.load("nt_x", GateCb()); a.load("nt_a", GateCb()); b.load("nt_b", GateCb())
        h.succeed(); timers.runPosted()
        assertEquals(listOf("nt_x", "nt_a"), net.order)
        a.succeed(); timers.runPosted()
        assertEquals(listOf("nt_x", "nt_a", "nt_b"), net.order)
    }

    private fun full2Chain(): Full2Chain {
        val h = mock(AdsHelper::class.java)
        `when`(h.isPlacementEnabled(anyString())).thenReturn(true)
        `when`(h.full2Handler()).thenReturn(mock(Handler::class.java))
        return Full2Chain(h)
    }

    /** Fable C1(d): Full2 nối của quảng cáo cha đang hiện là SHOW, khoá = `full2Name(parent)`; xong chuyển cảnh ⇒ PRELOAD. */
    @Test fun `P4 Full2 cua luot cha dang hien la SHOW, xong chuyen canh la PRELOAD`() = unitGateRun { _, _ ->
        val chain = full2Chain()
        assertEquals(UnitGate.PRELOAD, UnitGate.level("full_2_extra_clean"))
        chain.beginFull2Transition("gift_extra_clean")
        assertEquals(listOf(UnitGate.SHOW, UnitGate.PRELOAD), listOf(UnitGate.level("full_2_extra_clean"), UnitGate.level("full_2_default")))
        chain.finishFull2Transition()
        assertEquals(UnitGate.PRELOAD, UnitGate.level("full_2_extra_clean"))
        chain.beginFull2Transition("full_default")
        assertEquals(UnitGate.SHOW, UnitGate.level("full_2_default"))
        chain.beginFull2Transition("full_home") // cha mới: khoá cũ bỏ
        assertEquals(listOf(UnitGate.PRELOAD, UnitGate.SHOW), listOf(UnitGate.level("full_2_default"), UnitGate.level("full_2_home")))
        chain.finishFull2Transition()
        assertEquals(UnitGate.PRELOAD, UnitGate.level("full_2_home"))
    }

    /** `HelperPort.load` thật trên helper giả: trả cb của lượt nạp sẵn (null = không mở lượt). */
    private fun preload(loading: Boolean, throws: Boolean = false): AdsLoadCb? {
        val h = mock(AdsHelper::class.java)
        val configs = mock(AdsPlacementConfigManager::class.java)
        val config = AdsPlacementConfig().apply { isLoading = loading }
        `when`(h.placementsConfigManager).thenReturn(configs)
        `when`(configs.peekConfig("full_default")).thenReturn(config)
        var cb: AdsLoadCb? = null
        doAnswer { cb = it.arguments[2] as AdsLoadCb; if (throws) throw IllegalStateException("boom"); null }
            .`when`(h).loadInterstitialForThisTurn(anyString(), anyInt(), any(), anyBoolean())
        val port = AdPreloader.HelperPort { h }
        if (throws) runCatching { port.load(PreloadSlot(PreloadFormat.INTERSTITIAL, "full_default")) }
        else port.load(PreloadSlot(PreloadFormat.INTERSTITIAL, "full_default"))
        return cb
    }

    @Test fun `P10 luot AdPreloader la PRELOAD, dau bo khi luot dau nap xong`() = unitGateRun { _, _ ->
        val cb = preload(loading = true)!!
        assertEquals("lượt nạp sẵn đang chạy", UnitGate.PRELOAD, UnitGate.level("full_default"))
        cb.onLoaded("full_default", "full_default", unit)
        assertEquals(UnitGate.APP, UnitGate.level("full_default"))
    }

    /** Fable C1(c): dấu cũng bỏ ở onLoadFailed và ở lối tắt "has loaded"/bị từ chối (không mở lượt). */
    @Test fun `P10b dau AdPreloader bo o onLoadFailed va loi tat has loaded`() = unitGateRun { _, _ ->
        val cb = preload(loading = true)!!
        assertEquals(UnitGate.PRELOAD, UnitGate.level("full_default"))
        cb.onLoadFailed("full_default", "full_default", "", "no fill")
        assertEquals(UnitGate.APP, UnitGate.level("full_default"))
        preload(loading = false)
        assertEquals("không mở lượt (has loaded) ⇒ không giữ dấu", UnitGate.APP, UnitGate.level("full_default"))
    }

    /** R-LA' L2: `load*ForThisTurn` ném (AdPreloader.load nuốt) ⇒ dấu PRELOAD không rò (slot không nạp). */
    @Test fun `P10c load nem thi dau AdPreloader bo`() = unitGateRun { _, _ ->
        preload(loading = false, throws = true)
        assertEquals(UnitGate.APP, UnitGate.level("full_default"))
    }
}
