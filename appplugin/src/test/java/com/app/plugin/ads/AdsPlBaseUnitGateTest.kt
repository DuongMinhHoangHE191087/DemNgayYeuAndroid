package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.app.plugin.ads.core.AdsTelemetry
import com.app.plugin.ads.core.UnitGate
import com.app.plugin.until.LogUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.`when`

/**
 * 2.4.4 INFLIGHT (Hợp đồng A, quyết định (a)) + điều kiện CTRL: cổng ở [AdsPlBase.load] khoá theo **đúng id đang
 * xin**, từng id độc lập. Một request mỗi ad unit (slot khác cùng unit chờ lượt đang bay), backoff 2–16 s sau lỗi
 * (cùng slot + placement thì bỏ qua, lượt khác chờ), id đang backoff trong chuỗi `;` được bỏ qua để xin id sau ngay,
 * fallthrough trong cùng lượt không bao giờ bị chặn, công tắc `ads_cf_unit_gate_off`. Slot giả gọi telemetry như
 * lớp mạng thật (`onAdLoadStart` sau request, `onAdLoadFail` trước fallthrough, `onAdLoaded` sau callback).
 */
class AdsPlBaseUnitGateTest {
    private val unit = "ca-app-pub-3940256099942544/1033173712"
    private val high = "ca-app-pub-3940256099942544/high"
    private val all = "ca-app-pub-3940256099942544/all"
    private val tel = AdsTelemetry(mock(AdsHelper::class.java))

    @After fun tearDown() = UnitGate.resetForTest()

    private class Probe(
        context: Context, pl: String, private val tel: AdsTelemetry, private val tag: String, private val deferred: Boolean,
        private val noRequest: Boolean = false,
    ) : AdsPlBase(context, mock(AdsBase::class.java), 0, pl) {
        val requested = mutableListOf<String>()
        override val eventTag: String get() = tag
        val state get() = adsPlacementEcmp
        private val item get() = state.adEcmps!!.getEcmpForLoad()!!
        override fun onInit() = Unit
        override fun tryLoad() {
            if (noRequest) { // nhánh không request của lớp mạng (vd `idload not correct`): báo fail, không đụng isLoading
                state.adsLoadCb?.onLoadFailed(loadPl, state.originPl, "", "idload not correct")
                return
            }
            state.isLoading = true
            if (deferred) return // giữ chờ mạng khởi tạo: không request, không telemetry, không callback
            val it = item
            requested += it.adsId
            it.isLoading = true
            tel.onAdLoadStart(loadPl, state.originPl, "interstitial", "admob", it.adsId)
        }
        override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int) = false

        /** Như `AdsMobPlInters.onAdFailedToLoad`: telemetry rồi fallthrough sang id kế ngay, hết chuỗi thì báo fail. */
        fun fail() {
            val it = item
            it.isLoading = false
            state.adEcmps!!.idxCurrEcpm++
            tel.onAdLoadFail(loadPl, state.originPl, "interstitial", "admob", it.adsId, "3")
            if (state.adEcmps!!.idxCurrEcpm < state.adEcmps!!.list.size) {
                tryLoad()
            } else {
                state.isLoading = false
                state.adsLoadCb?.onLoadFailed(loadPl, state.originPl, "", "3")
                onFinishLoad()
            }
        }

        fun succeed() {
            val it = item
            it.isLoading = false
            it.isLoaded = true
            it.adObject = Any()
            state.isLoading = false
            state.adsLoadCb?.onLoaded(loadPl, state.originPl, it.adsId, it, this)
            tel.onAdLoaded(loadPl, state.originPl, "interstitial", "admob", it.adsId, "AdMobAdapter")
            onFinishLoad()
        }

        /** Quảng cáo đã hiện: mục trống lại, lần load sau gửi request mới. */
        fun consume() = state.adEcmps!!.list.forEach { it.adObject = null; it.isLoaded = false }
    }

    private class Cb : AdsLoadInnerCb {
        val events = mutableListOf<String>()
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) { events += "loaded" }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
    }

    private fun probe(
        pl: String, ids: String = unit, tag: String = "mob_full", context: Context = mock(Context::class.java),
        deferred: Boolean = false, noRequest: Boolean = false,
    ) = Probe(context, pl, tel, tag, deferred, noRequest).apply { setActivity(mock(Activity::class.java)); parserEcpms(ids) }

    /** Đồng hồ ảo + dòng `Log.i(LogUtil.TAG, …)` (chỉ `i`: hợp đồng log #65 là `Log.i`). */
    private fun <T> gate(block: (MainHandlerTimers, List<String>) -> T): T = MainHandlerTimers.run { timers ->
        val infos = mutableListOf<String>()
        mockStatic(Log::class.java) { inv ->
            if (inv.method.name == "i" && inv.arguments[0] == LogUtil.TAG) infos += inv.arguments[1] as String
            0
        }.use { block(timers, infos) }
    }

    private fun merged(pl: String, slot: String, id: String) = "load merged into in-flight request: $pl slot=$slot unit=$id"
    private fun waits(ms: Long, pl: String, slot: String, id: String) = "load waits unit backoff $ms ms: $pl slot=$slot unit=$id"
    private fun skipped(ms: Long, pl: String, slot: String, id: String) = "load skipped unit backoff $ms ms: $pl slot=$slot unit=$id"
    private fun released(ms: Long, pl: String, slot: String, id: String) = "load released after $ms ms: $pl slot=$slot unit=$id"

    @Test fun `IF1 slot khac cung unit cho request dang bay, thanh cong thi gui ngay`() = gate { timers, infos ->
        val a = probe("full_splash"); val b = probe("full_default")
        a.load("full_splash", Cb())
        val cbB = Cb()
        b.load("full_default", cbB)
        assertEquals("slot thứ hai cùng unit phải chờ request đang bay (0 request)", emptyList<String>(), b.requested)
        assertTrue("slot chờ là đang load", b.isLoadingAd)
        timers.advanceTo(500)
        a.succeed()
        timers.runPosted()
        assertEquals("A thành công ⇒ B gửi request riêng", listOf(unit), b.requested)
        assertEquals(listOf(merged("full_default", "full_default", unit), released(500, "full_default", "full_default", unit)), infos)
        b.succeed()
        assertEquals("quảng cáo về đúng slot của nó", listOf("loaded"), cbB.events)
    }

    @Test fun `IF2 cung slot cung placement thu lai ngay sau loi thi bo qua unit_backoff`() = gate { _, infos ->
        val a = probe("full_splash")
        a.load("full_splash", Cb()); a.fail()
        val cb = Cb()
        a.load("full_splash", cb)
        assertEquals("retry cùng slot trong backoff: 0 request mới, báo unit_backoff", listOf(1, listOf("fail:unit_backoff")), listOf(a.requested.size, cb.events))
        assertEquals("SKIP không bật isLoading", false, a.isLoadingAd)
        assertEquals(listOf(skipped(2_000, "full_splash", "full_splash", unit)), infos)
    }

    @Test fun `IF3 slot khac sau loi thi cho het backoff roi gui`() = gate { timers, infos ->
        val a = probe("full_splash"); val b = probe("full_default")
        a.load("full_splash", Cb()); a.fail()
        b.load("full_default", Cb())
        timers.advanceTo(1_999)
        assertEquals("trong backoff: 0 request", 0, b.requested.size)
        timers.advanceTo(2_000)
        assertEquals("hết backoff: 1 request", listOf(unit), b.requested)
        assertEquals(listOf(waits(2_000, "full_default", "full_default", unit), released(2_000, "full_default", "full_default", unit)), infos)
    }

    @Test fun `IF3b cung slot placement khac sau loi thi cho, khong bo qua`() = gate { timers, infos ->
        val a = probe("nt_splash")
        a.load("nt_splash", Cb()); a.fail()
        timers.advanceTo(500)
        val cb = Cb()
        a.load("nt_home", cb)
        timers.advanceTo(1_999)
        assertEquals(listOf(1, emptyList<String>()), listOf(a.requested.size, cb.events))
        timers.advanceTo(2_000)
        assertEquals(2, a.requested.size)
        assertEquals(waits(1_500, "nt_home", "nt_splash", unit), infos.first())
    }

    @Test fun `IF4 chu khong bao gio callback thi watchdog 60 s cua chu nha, B gui`() = gate { timers, _ ->
        val a = probe("full_splash", deferred = true); val b = probe("full_default")
        val cbA = Cb()
        a.load("full_splash", cbA)
        timers.advanceTo(50_001)
        b.load("full_default", Cb())
        timers.advanceTo(59_999)
        assertEquals(0, b.requested.size)
        timers.advanceTo(60_000)
        assertEquals("watchdog của A báo fail một lần", listOf("fail:load timeout"), cbA.events)
        assertEquals("A hết lượt qua callback timeout (không lỗi request thật, không backoff) ⇒ B gửi", listOf(unit), b.requested)
    }

    /** C2 (Fable): chờ có trần riêng 10 s từ lúc vào hàng; hết trần ⇒ bỏ đặt chỗ, gửi như 2.4.3 (trước watchdog 60 s). */
    @Test fun `IF13 chu khong callback, het tran cho 10 s thi nguoi cho gui`() = gate { timers, infos ->
        val a = probe("full_splash", deferred = true); val b = probe("full_default")
        a.load("full_splash", Cb())
        timers.advanceTo(1)
        b.load("full_default", Cb())
        timers.advanceTo(10_000)
        assertEquals("trước trần: 0 request", 0, b.requested.size)
        timers.advanceTo(10_001)
        assertEquals("hết trần 10 s: người chờ gửi", listOf(unit), b.requested)
        assertEquals(released(10_000, "full_default", "full_default", unit), infos.last())
        val c = probe("full_home")
        c.load("full_home", Cb())
        assertEquals("B gửi xong thì giữ unit: slot mới chờ B (đặt chỗ của A đã bỏ)", emptyList<String>(), c.requested)
        assertEquals(merged("full_home", "full_home", unit), infos.last())
    }

    @Test fun `IF5 dat cho mo coi khong chan, slot moi gui ngay`() = gate { _, _ ->
        val a = probe("full_splash"); val b = probe("full_default"); val c = probe("full_home")
        a.load("full_splash", Cb())
        a.state.isLoading = false // slot chủ hết đang load mà không qua callback
        b.load("full_default", Cb())
        assertEquals("chủ không còn isLoadingAd ⇒ bỏ đặt chỗ, gửi ngay", listOf(unit), b.requested)
        b.state.loadGeneration++ // generation đổi mà không qua callback
        c.load("full_home", Cb())
        assertEquals("generation chủ đổi ⇒ bỏ đặt chỗ, gửi ngay", listOf(unit), c.requested)
    }

    private fun offContext(): Context {
        val res = mock(Resources::class.java)
        `when`(res.getIdentifier(anyString(), anyString(), anyString())).thenReturn(0)
        `when`(res.getIdentifier("ads_cf_unit_gate_off", "integer", "app.test")).thenReturn(42)
        `when`(res.getInteger(42)).thenReturn(1)
        return mock(Context::class.java).also {
            `when`(it.resources).thenReturn(res)
            `when`(it.packageName).thenReturn("app.test")
        }
    }

    @Test fun `IF6 ads_cf_unit_gate_off bang 1 thi hanh vi 243 nguyen ven`() = gate { _, infos ->
        val ctx = offContext()
        val a = probe("full_splash", context = ctx); val b = probe("full_default", context = ctx)
        a.load("full_splash", Cb())
        b.load("full_default", Cb())
        assertEquals("IF1 tắt: B gửi ngay", listOf(unit), b.requested)
        a.fail()
        val cb = Cb()
        a.load("full_splash", cb)
        assertEquals("IF2 tắt: retry gửi ngay, không unit_backoff", listOf(2, emptyList<String>()), listOf(a.requested.size, cb.events))
        a.fail(); b.fail()
        val c = probe("full_home", context = ctx)
        c.load("full_home", Cb())
        assertEquals("IF3 tắt: slot khác gửi ngay sau lỗi", listOf(unit), c.requested)
        assertEquals(emptyList<String>(), infos)
    }

    @Test fun `IF7 lich backoff 2 4 8 16 16 s, thanh cong dat lai`() = gate { timers, infos ->
        val a = probe("full_splash")
        var now = 0L
        repeat(5) {
            a.load("full_splash", Cb()); a.fail()
            a.load("full_splash", Cb()) // retry ngay: SKIP, ghi số ms còn lại
            now += 20_000; timers.advanceTo(now)
        }
        a.load("full_splash", Cb()); a.succeed(); a.consume()
        a.load("full_splash", Cb()); a.fail()
        a.load("full_splash", Cb())
        val ms = infos.map { it.substringAfter("backoff ").substringBefore(" ms").toLong() }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 16_000L, 2_000L), ms)
    }

    @Test fun `IF8 banner rect NtCl va max ngoai cong, hai slot cung unit cung gui`() = gate { _, infos ->
        val ma = probe("full_splash", tag = "max_full"); val mb = probe("full_default", tag = "max_full")
        ma.load("full_splash", Cb()); mb.load("full_default", Cb())
        val ba = BnProbe(tel).apply { setActivity(mock(Activity::class.java)); parserEcpms(unit) }
        val bb = BnProbe(tel).apply { setActivity(mock(Activity::class.java)); parserEcpms(unit) }
        ba.load("bn_home", Cb()); bb.load("bn_home", Cb())
        assertEquals(listOf(1, 1, 1, 1), listOf(ma.requested.size, mb.requested.size, ba.requests, bb.requests))
        val fa = probe("full_splash", tag = "fb_full"); val fbb = probe("full_default", tag = "fb_full")
        fa.load("full_splash", Cb()); fbb.load("full_default", Cb())
        assertEquals("fb_* trong cổng", listOf(1, 0), listOf(fa.requested.size, fbb.requested.size))
        assertEquals(listOf(merged("full_default", "full_default", unit)), infos)
    }

    private class BnProbe(private val tel: AdsTelemetry) : AdsPlBaseBn(mock(Context::class.java), mock(AdsBase::class.java), 0, "bn_home") {
        var requests = 0
        override val eventTag: String get() = "mob_bn"
        override fun onInit() = Unit
        override fun tryLoad() {
            val it = adsPlacementEcmp.adEcmps!!.getEcmpForLoad()!!
            requests++
            adsPlacementEcmp.isLoading = true
            it.isLoading = true
            tel.onAdLoadStart(loadPl, adsPlacementEcmp.originPl, "banner", "admob", it.adsId)
        }
        override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int) = false
    }

    @Test fun `IF9 cung cb gan hai lan vao luot dang nap thi mot ket qua, hai cb khac thi moi cb mot`() = gate { _, _ ->
        val a = probe("full_splash")
        val cb1 = Cb(); val cb2 = Cb()
        a.load("full_splash", cb1)
        a.load("full_splash", cb1)
        a.load("full_splash", cb2)
        a.succeed()
        assertEquals(listOf(listOf("loaded"), listOf("loaded")), listOf(cb1.events, cb2.events))
        assertEquals(1, a.requested.size)
    }

    @Test fun `IF10 bon dong log cong la Log_i nguyen van`() = gate { timers, infos ->
        val a = probe("full_splash"); val b = probe("full_default")
        a.load("full_splash", Cb())
        b.load("full_default", Cb()) // merged
        a.fail() // A lỗi: SKIP khi retry
        a.load("full_splash", Cb())
        timers.runPosted() // B ra khỏi hàng: unit đang backoff ⇒ chờ
        timers.advanceTo(2_000)
        assertEquals(
            listOf(
                merged("full_default", "full_default", unit),
                skipped(2_000, "full_splash", "full_splash", unit),
                waits(2_000, "full_default", "full_default", unit),
                released(2_000, "full_default", "full_default", unit),
            ),
            infos,
        )
    }

    /**
     * CTRL quyết định B: watchdog slot (timeout) không phải lỗi load thật — request có thể vẫn ở mạng — nên không đếm
     * backoff; lượt kế của cùng slot xin lại đúng id đó như 2.4.3 (mục list[0] đang nạp: quảng cáo muộn không được giữ).
     */
    @Test fun `IF14 timeout khong tinh backoff, lan load lai cung slot xin lai dung id dau`() = gate { timers, infos ->
        val a = probe("full_default", "$high;$all")
        val cb1 = Cb()
        a.load("full_home", cb1)
        timers.advanceTo(60_000)
        assertEquals(listOf("fail:load timeout"), cb1.events)
        val cb2 = Cb()
        a.load("full_home", cb2)
        assertEquals("request 2 tới đúng id đầu, không bỏ qua id vừa timeout", listOf(high, high), a.requested)
        assertEquals(listOf(emptyList<String>(), emptyList<String>()), listOf(cb2.events, infos))
        assertTrue("mục list[0] đang nạp lượt mới", a.state.adEcmps!!.list[0].isLoading)
    }

    /** CTRL: chuỗi `id_high;id_all` (sàn eCPM): lỗi của id_high không làm chậm id_all; retry chỉ bị backoff ở đúng id đã lỗi. */
    @Test fun `IF11 hai unit trong mot chuoi, id dau loi thi id sau duoc xin ngay, ca o lan thu lai`() = gate { _, infos ->
        val a = probe("full_splash", "$high;$all")
        a.load("full_splash", Cb())
        a.fail()
        assertEquals("fallthrough trong cùng lượt: id_all xin ngay", listOf(high, all), a.requested)
        a.succeed(); a.consume()
        val cb = Cb()
        a.load("full_splash", cb)
        assertEquals("retry: id_high còn backoff bị bỏ qua, id_all xin ngay (không SKIP cả lượt)", listOf(high, all, all), a.requested)
        assertEquals(emptyList<String>(), cb.events)
        assertEquals(emptyList<String>(), infos)
    }

    /** CTRL: khoá là id đang thật sự xin; slot khác chỉ chờ trên đúng id đó, id đang backoff của slot khác không chặn id sau. */
    @Test fun `IF12 khoa theo id dang xin sau fallthrough, id backoff cua slot khac khong chan id sau`() = gate { timers, infos ->
        val a = probe("full_splash", "$high;$all")
        a.load("full_splash", Cb())
        a.fail() // A đang xin id_all
        val c = probe("full_home", all)
        c.load("full_home", Cb())
        assertEquals("slot chỉ có id_all chờ request id_all đang bay của A", emptyList<String>(), c.requested)
        a.succeed(); a.consume()
        timers.runPosted()
        assertEquals(listOf(all), c.requested)
        c.succeed()
        val b = probe("full_default", "$high;$all")
        b.load("full_default", Cb())
        assertEquals("id_high đang backoff (A lỗi) ⇒ B xin id_all ngay, không chờ 2 s", listOf(all), b.requested)
        assertEquals(listOf(merged("full_home", "full_home", all), released(0, "full_home", "full_home", all)), infos)
    }

    /** R-LA M1: người chờ được nhả vào nhánh tryLoad không request ⇒ slot rảnh như 2.4.3, đúng một callback (không timeout 60 s). */
    @Test fun `RT1b nguoi cho nha vao nhanh tryLoad khong request thi slot ranh, mot callback`() = gate { timers, _ ->
        val a = probe("full_splash"); val b = probe("full_default", noRequest = true)
        a.load("full_splash", Cb())
        val cbB = Cb()
        b.load("full_default", cbB)
        timers.advanceTo(100)
        a.succeed(); timers.runPosted()
        timers.advanceTo(1_000)
        assertEquals("slot B phải rảnh như 2.4.3", false, b.isLoadingAd)
        timers.advanceTo(70_000)
        assertEquals("đúng một callback", listOf("fail:idload not correct"), cbB.events)
    }

    /** R-LA L1: hết trần 10 s, lượt bypass vẫn bỏ qua id đang backoff (bắt đầu ở id đầu không backoff). */
    @Test fun `RT3 bypass sau tran bo qua id dang backoff, xin id sau`() = gate { timers, _ ->
        val x = probe("nt_x", high)
        for (i in 0 until 4) { timers.advanceTo(i * 20_000L); x.load("nt_x", Cb()); x.fail() } // n_high = 4 lúc 60 000
        val a = probe("full_splash", all, deferred = true)
        a.load("full_splash", Cb()) // giữ all, không bao giờ callback
        val b = probe("full_default", "$high;$all")
        b.load("full_default", Cb()) // high backoff (chủ X) ⇒ all bị A giữ ⇒ chờ
        assertEquals(emptyList<String>(), b.requested)
        timers.advanceTo(70_000)
        assertEquals("bypass bỏ qua high (còn backoff 6 s), xin all", listOf(all), b.requested)
    }

    /** R-LA M2: người chờ khi được nhả mà mọi id backoff (có id của chính nó) ⇒ SKIP: slot rảnh, đúng một callback. */
    @Test fun `RT4 nguoi cho bi SKIP thi slot ranh va mot callback`() = gate { timers, _ ->
        val s = probe("full_default", "$high;$all")
        s.load("full_default", Cb()); s.fail() // high lỗi (chủ S|full_default), S xin all
        s.succeed(); s.consume()
        val a = probe("full_splash", all)
        a.load("full_splash", Cb()) // A giữ all
        timers.advanceTo(100)
        val cb = Cb()
        s.load("full_default", cb) // high backoff (của S), all bị A giữ ⇒ chờ
        assertEquals(true, s.isLoadingAd)
        timers.advanceTo(200)
        a.fail(); timers.runPosted() // all backoff (chủ A) ⇒ S: mọi id backoff, có id của S ⇒ SKIP
        assertEquals(listOf("fail:unit_backoff"), cb.events)
        assertEquals("SKIP từ hàng chờ hạ isLoading", false, s.isLoadingAd)
        timers.advanceTo(70_000)
        assertEquals("không có callback timeout thứ hai", listOf("fail:unit_backoff"), cb.events)
    }
}
