package com.app.plugin.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.app.plugin.ads.core.AdsTelemetry
import com.app.plugin.ads.core.UnitGate
import com.app.plugin.until.LogUtil
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/** Mạng giả: `ready = false` ⇒ lượt load bị giữ qua `unlessLoadAbandoned { tryLoad() }` như `AdsMobPl*.tryLoad`. */
internal class GateNet {
    var ready = true
    val held = mutableListOf<() -> Unit>()
    val order = mutableListOf<String>()
    fun open() { ready = true; val h = held.toList(); held.clear(); h.forEach { it() } }
}

/**
 * Slot giả trong cổng unit (như `Probe` của [AdsPlBaseUnitGateTest]): telemetry như lớp mạng thật (`onAdLoadStart` sau
 * request, `onAdLoadFail` trước fallthrough, `onAdLoaded` sau callback); mạng chưa sẵn ⇒ giữ qua [unlessLoadAbandoned]
 * (AdMob) hoặc [holdUntilReady] khi có [queue] (Meta/MAX, hàng đóng ⇒ fail một lần).
 */
internal class GateProbe(
    context: Context, pl: String, private val tel: AdsTelemetry, private val net: GateNet,
    private val tag: String = "mob_full", private val queue: SdkReadyQueue? = null,
) : AdsPlBase(context, mock(AdsBase::class.java), 0, pl) {
    val requested = mutableListOf<String>()
    override val eventTag: String get() = tag
    val state get() = adsPlacementEcmp
    private val item get() = state.adEcmps!!.getEcmpForLoad()!!
    override fun onInit() = Unit
    override fun tryLoad() {
        if (queue != null && holdUntilReady(queue)) return
        if (!net.ready) { state.isLoading = true; net.held += unlessLoadAbandoned { tryLoad() }; return }
        loadPl = state.getPl
        state.isLoading = true
        val it = item
        requested += it.adsId; net.order += loadPl
        it.isLoading = true
        tel.onAdLoadStart(loadPl, state.originPl, "native_custom", "admob", it.adsId)
    }
    override fun show(placement: String, adsShowCb: AdsShowCb?, flagShow: Int) = false

    fun succeed() {
        val it = item
        it.isLoading = false; it.isLoaded = true; it.adObject = Any()
        state.isLoading = false
        state.adsLoadCb?.onLoaded(loadPl, state.originPl, it.adsId, it, this)
        tel.onAdLoaded(loadPl, state.originPl, "native_custom", "admob", it.adsId, "AdMobAdapter")
        onFinishLoad()
    }

    fun fail() {
        val it = item
        it.isLoading = false
        state.adEcmps!!.idxCurrEcpm++
        tel.onAdLoadFail(loadPl, state.originPl, "native_custom", "admob", it.adsId, "3")
        if (state.adEcmps!!.idxCurrEcpm < state.adEcmps!!.list.size) tryLoad() else {
            state.isLoading = false
            state.adsLoadCb?.onLoadFailed(loadPl, state.originPl, "", "3")
            onFinishLoad()
        }
    }

    /** Quảng cáo đã hiện: mục trống lại. */
    fun consume() = state.adEcmps!!.list.forEach { it.adObject = null; it.isLoaded = false }
}

internal class GateCb : AdsLoadInnerCb {
    val events = mutableListOf<String>()
    override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) { events += "loaded" }
    override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) { events += "fail:$error" }
}

/** Đồng hồ ảo + dòng `Log.i(LogUtil.TAG, …)` (hợp đồng log #65). */
internal fun <T> unitGateRun(block: (MainHandlerTimers, List<String>) -> T): T = MainHandlerTimers.run { timers ->
    val infos = mutableListOf<String>()
    org.mockito.Mockito.mockStatic(Log::class.java) { inv ->
        if (inv.method.name == "i" && inv.arguments[0] == LogUtil.TAG) infos += inv.arguments[1] as String
        0
    }.use { block(timers, infos) }
}

/**
 * 2.5.0 TURNKEY (Hợp đồng A, quyết định (b)): đặt chỗ unit nhả ở kết quả của id chỉ khi lượt đang giữ id đó **đã gửi**
 * id đó (ghi ở `onRequest`) và còn sống; kết quả mang `originPl` của slot nhưng thuộc lượt khác (lượt cũ, hoặc lượt đang
 * giữ chưa gửi) không nhả. `endTurn` (callback đầu của lượt) giữ nguyên.
 */
class UnitGateTurnKeyTest {
    private val unit = "ca-app-pub-3940256099942544/6781010236"
    private val high = "ca-app-pub-3940256099942544/high"
    private val all = "ca-app-pub-3940256099942544/all"
    private val tel = AdsTelemetry(mock(AdsHelper::class.java))

    @After fun tearDown() = UnitGate.resetForTest()

    private fun probe(slot: String, net: GateNet, ids: String = unit) =
        GateProbe(mock(Context::class.java), slot, tel, net).apply { setActivity(mock(Activity::class.java)); parserEcpms(ids) }

    /** F1b 2.4.4 §2: kết quả của lượt 1 (đã hết bằng watchdog) không được nhả đặt chỗ lượt 2 cùng slot chưa gửi. */
    @Test fun `TK1 ket qua cua luot cu khong nha luot moi cung slot chua gui`() = unitGateRun { timers, _ ->
        val net = GateNet()
        val a = probe("full_splash", net); val b = probe("full_default", net)
        a.load("full_splash", GateCb())
        timers.advanceTo(60_000) // watchdog: lượt 1 của A hết, TurnCb nhả
        net.ready = false
        a.load("full_splash", GateCb()) // lượt 2 của A: đặt chỗ unit, chưa gửi
        b.load("full_default", GateCb()) // B chờ lượt 2 của A
        tel.onAdLoaded("full_splash", "full_splash", "native_custom", "admob", unit, "AdMobAdapter") // kết quả của lượt 1
        timers.runPosted()
        net.open(); timers.runPosted() // mạng sẵn sàng: lượt 2 của A gửi
        assertEquals("A gửi lượt 2", 2, a.requested.size)
        assertEquals("B phải còn chờ lượt 2 của A (kết quả muộn không thuộc lượt đang giữ)", emptyList<String>(), b.requested)
    }

    /** Fallthrough `;`: kết quả của id lượt đã gửi nhả đúng id đó ngay, không chờ hết lượt (đặt chỗ đã gửi ghi ở onRequest). */
    @Test fun `TK3 loi id da gui nha id do giua luot, slot khac xin sau backoff`() = unitGateRun { timers, _ ->
        val net = GateNet()
        val a = probe("full_splash", net, "$high;$all")
        a.load("full_splash", GateCb())
        val d = probe("full_home", net, high)
        d.load("full_home", GateCb())
        assertEquals("D chờ request high đang bay của A", emptyList<String>(), d.requested)
        a.fail() // high lỗi ⇒ nhả high; A xin all
        timers.runPosted()
        timers.advanceTo(2_000)
        assertEquals("A còn bay ở all", listOf(high, all), a.requested)
        assertEquals("high đã nhả ở kết quả của chính lượt gửi nó: D xin sau backoff 2 s", listOf(high), d.requested)
    }

    /** INFLIGHT: kết quả muộn sau timeout không tới telemetry ở 16 lớp mạng trong cổng (guard đứng trước `onAdLoad*`). */
    @Test fun `TK2 lop mang trong cong goi telemetry ket qua sau guard isStaleLoadCallback`() {
        val root = listOf(File("."), File("..")).first { File(it, "src/main/java/com/app/plugin/ads/AdsPlBase.kt").isFile }
        val legacy = "src/main/java/com/app/plugin/ads"
        val ng = "appplugin-nextgen/src/main/java/com/app/plugin/ads"
        val files = listOf("Inters", "Nt", "NtFull", "Open", "Rwi", "rewarded").map { "$legacy/adsmob/AdsMobPl$it.kt" } +
            listOf("Inters", "Nt", "NtFull", "rewarded").map { "$legacy/adsfb/AdsFbPl$it.kt" } +
            listOf("Inters", "Nt", "NtFull", "Open", "Rwi", "rewarded").map { "$ng/adsmob/AdsMobPl$it.kt" }
        val call = Regex("""AdsHelper\.instance\.onAdLoad(Fail|ed)\(""")
        val listener = Regex("""override fun |MainPost\.nativeLoaded \{""")
        var calls = 0
        for (f in files) {
            val src = File(root, f).readText()
            for (m in call.findAll(src)) {
                calls++
                val start = listener.findAll(src.substring(0, m.range.first)).last().range.first
                assertTrue("$f: ${m.value} phải đứng sau isStaleLoadCallback trong cùng listener",
                    src.substring(start, m.range.first).contains("isStaleLoadCallback("))
            }
        }
        assertEquals("16 lớp × (onAdLoadFail + onAdLoaded)", 32, calls)
    }
}
