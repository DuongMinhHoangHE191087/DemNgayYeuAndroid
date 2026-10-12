package com.app.plugin.ads.core

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.app.plugin.AppPluginInternalApi
import com.app.plugin.ads.AdsHelper
import com.app.plugin.ads.AdsIDDef
import com.app.plugin.ads.AdsLoadCb
import com.app.plugin.ads.PlacementDefault
import com.app.plugin.ads.RemoveAdsMask.FLAG_FULLSCREEN
import com.app.plugin.ads.adsmob.AdsMobMy
import com.app.plugin.app.AppPluginManager
import com.app.plugin.firbase.RcSectionRegistry
import com.app.plugin.logevent.AdEventLog
import com.app.plugin.until.LogUtil
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SDK tự nạp sẵn quảng cáo toàn màn hình (2.3.0 PRELOAD, D15-230, D23-IM (2)). `AdSdkStartup.startAdNetworks` gọi
 * [schedule] ở cuối, tức chỉ khi consent cho phép, kill switch không chặn và giấy phép VALID; đúng một lần mỗi process.
 * Khi MobileAds khởi tạo xong (hoặc quá 8 s, `AdsMobMy.runWhenSdkReady`) và sau `delay_ms`, mỗi slot của
 * [AdPreloadPlan.decide] được đăng một lời gọi `load*ForThisTurn` trên main (loader tự chạy nền, main không chờ). Chưa
 * có host Activity (mạng cần nó để load) thì chờ `AdsHelper.setMainActivity` ([onHostSet]).
 *
 * Slot đang nạp → bỏ; app gọi `load*ForThisTurn` lúc lượt nạp sẵn đang chạy thì callback của app được gắn vào lượt đó.
 * Mask, kill, `maskAdsStatus`, giấy phép → bỏ slot, một dòng log; interstitial trong cửa sổ `fullStartTime` (≥ 120) hoãn
 * tới khi cửa sổ đóng (D17-230 c). KHÔNG nạp sẵn banner, NtCl, native inline, rect, native full đứng riêng (gắn view).
 * RC [AdPreloadPlan.REMOTE_KEY] qua [RcSectionRegistry] (áp khi RC fetch xong; chưa áp = bật). Log `Log.i` (luật 26):
 * `preload: <format> <placement> load` chỉ khi lượt thật sự mở, `preload: skip <format> (<lý do>)`, `preload: off (rc)`.
 *
 * 2.4.3 PREORDER (Hợp đồng C): `delay_ms` 0 (mặc định) ⇒ sau kiểm host, lượt nạp sẵn nhường splash của app: chờ trong lúc
 * `full_splash` hoặc `nt_splash` đang nạp ([Port.isLoading]); chưa thấy splash nào thì chờ tối đa [SPLASH_GRACE_MS]; trần
 * [SPLASH_CAP_MS]; hỏi lại mỗi [SPLASH_POLL_MS] qua [Port.post]. `delay_ms` > 0 ⇒ RC thắng, như 2.4.2. Log `Log.i`:
 * `preload: wait splash` (một lần mỗi lượt), `preload: splash done after <n> ms (settled|cap|none)`.
 */
internal object AdPreloader {
    /** Mọi thứ ngoài lớp này: test thay [port]. */
    internal interface Port {
        fun whenSdkReady(action: () -> Unit)
        fun post(delayMs: Long, action: () -> Unit)
        fun hostReady(): Boolean
        fun openPlacements(): List<String>
        fun rwiConfigured(): Boolean
        fun removed(flag: Int): Boolean
        fun disabled(flag: Int): Boolean
        fun interstitialWaitMs(): Long
        fun isLoading(placement: String): Boolean
        fun full2FlagShow(): Int
        fun load(slot: PreloadSlot)
    }

    /** Bản thật: [AdsHelper] ([h], test truyền helper riêng), hàng chờ AdMob, main looper. */
    internal class HelperPort(private val h: () -> AdsHelper = { AdsHelper.instance }) : Port {
        override fun whenSdkReady(action: () -> Unit) = AdsMobMy.instance.runWhenSdkReady(action)
        override fun post(delayMs: Long, action: () -> Unit) { Handler(Looper.getMainLooper()).postDelayed(action, delayMs) }
        override fun hostReady(): Boolean = h().hasPlacementConfig() && h().hostActivity() != null   // initAdsHelper + host
        override fun openPlacements(): List<String> =
            listOf(AdsIDDef.AdmobFullOpen, AdsIDDef.MaxFullOpen).flatMap { h().fullNetworks()[it]?.mapAdsPl?.keys.orEmpty() }
        override fun rwiConfigured(): Boolean =
            h().rewardedNetworks()[AdsIDDef.AdmobRwi]?.mapAdsPl?.containsKey(AdPreloadPlan.RWI_DEFAULT) == true
        override fun removed(flag: Int): Boolean = h().isRemoveAds(flag)
        override fun disabled(flag: Int): Boolean = h().isDisableAds(flag)
        override fun interstitialWaitMs(): Long = h().currConfig.let { c -> AppPluginManager.instance.let { a ->
            AdPreloadPlan.startWindowWaitMs(
                System.currentTimeMillis() / 1000, a.getTimeStart(), c.fullStartTime, a.getTimeSesstionStart(), c.fullSesstionTime
            )
        } }
        // Fable C2 (2.4.3): đọc không tạo entry (`getAdsPlacementConfig` tạo `nt_splash` ở app không có, mỗi nhịp 250 ms).
        override fun isLoading(placement: String): Boolean = h().placementsConfigManager.peekConfig(placement)?.isLoading == true
        override fun full2FlagShow(): Int = h().currConfig.full2FlagShow
        override fun load(slot: PreloadSlot) = ended(slot.placement).let { cb ->
            UnitGate.preload(slot.placement, true)   // 2.5.0 PRIO: lượt nạp sẵn là PRELOAD tới khi lượt đầu kết thúc (C1c)
            try { when (slot.format) {
                PreloadFormat.INTERSTITIAL -> h().loadInterstitialForThisTurn(slot.placement, 0, cb, true)
                PreloadFormat.APP_OPEN -> h().loadOpenAdForThisTurn(slot.placement, cb)
                PreloadFormat.REWARDED, PreloadFormat.REWARDED_INTERSTITIAL -> h().loadRewardedForThisTurn(slot.placement, cb)
            } } finally { if (!isLoading(slot.placement)) UnitGate.preload(slot.placement, false) }   // has loaded / từ chối / ném: không mở lượt
        }

        /** 2.4.0 CARRY-ADS #21: lượt nạp sẵn kết thúc không fill → bỏ dấu `trigger=preload` ngay (trước: giữ tới 60 s). */
        private fun ended(pl: String): AdsLoadCb = object : AdsLoadCb {
            override fun onLoaded(placement: String, originPl: String, adId: String) = UnitGate.preload(pl, false)   // `ad_fill` đã bỏ dấu
            override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) =
                AdEventLog.clearPreload(pl).also { UnitGate.preload(pl, false) }
        }
    }

    @Volatile internal var port: Port = HelperPort()

    /** Cấu hình RC đã áp trong process, đọc lúc mạng sẵn sàng. */
    @Volatile internal var config: PreloadConfig = AdPreloadPlan.DEFAULT
        private set

    private val scheduled = AtomicBoolean(false)

    @Volatile private var waitingForHost: PreloadConfig? = null   // lượt chờ host Activity ([onHostSet]); chỉ main ghi

    private const val NT_SPLASH = "nt_splash"   // quy ước SC/fork (INTEGRATION §9 bước 14)
    private const val SPLASH_GRACE_MS = 1_500L
    private const val SPLASH_CAP_MS = 10_000L
    private const val SPLASH_POLL_MS = 250L
    @Volatile private var splashSeen = false     // lượt chờ đã thấy splash nạp; chỉ main ghi
    @Volatile private var splashWaitedMs = 0L    // ms đã chờ (tổng các nhịp hỏi); chỉ main ghi

    /** Đăng ký section RC; `AdSdkStartup` gọi lúc khởi tạo object (trước lần fetch Remote Config của `initPlugin`). */
    @OptIn(AppPluginInternalApi::class)
    fun registerRemote() = RcSectionRegistry.register(AdPreloadPlan.REMOTE_KEY, "AdsPreload") { applyRemote(it) }

    /** Áp chuỗi RC; JSON hỏng/schema khác/trường sai = mặc định + một dòng logE. Trả báo cáo cho dòng log của registry. */
    fun applyRemote(raw: String): String {
        val (c, error) = AdPreloadPlan.parse(raw)
        config = c
        if (error != null) LogUtil.logE("preload: ${AdPreloadPlan.REMOTE_KEY} không hợp lệ ($error), dùng mặc định")
        return if (error != null) "default ($error)" else "$c"
    }

    /** Cuối `AdSdkStartup.startAdNetworks` (main). Lần gọi thứ hai trong process là no-op. */
    fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        val p = port
        p.whenSdkReady {
            val c = config
            if (c.enabled) p.post(c.delayMs) { run(p, c) } else info("preload: off (rc)")
        }
    }

    /** `AdsHelper.setMainActivity` (qua `AppOpenController.onHostSet`): chạy lượt đang chờ host, nếu có. */
    fun onHostSet() {
        val c = waitingForHost ?: return
        waitingForHost = null
        port.let { p -> p.post(0L) { run(p, c) } }   // sau vòng setActivity của mạng
    }

    private fun run(p: Port, c: PreloadConfig) {
        if (!p.hostReady()) {
            waitingForHost = c
            info("preload: waiting for host activity")
            return
        }
        if (c.delayMs > 0L) return loadAll(p, c)   // RC delay_ms thắng (hành vi 2.4.2)
        splashSeen = false
        splashWaitedMs = 0L
        info("preload: wait splash")
        awaitSplash(p, c)
    }

    /** Một nhịp chờ splash: đang nạp ⇒ hỏi lại; xong (đã thấy) / hết ân hạn (chưa thấy) / chạm trần ⇒ nạp sẵn. */
    private fun awaitSplash(p: Port, c: PreloadConfig) {
        val loading = p.isLoading(PlacementDefault.PLFullSplash) || p.isLoading(NT_SPLASH)
        if (loading) splashSeen = true
        val waited = splashWaitedMs
        val reason = when {
            waited >= SPLASH_CAP_MS -> "cap"
            loading -> null
            splashSeen -> "settled"
            waited >= SPLASH_GRACE_MS -> "none"
            else -> null
        }
        if (reason != null) {
            info("preload: splash done after $waited ms ($reason)")
            return loadAll(p, c)
        }
        p.post(SPLASH_POLL_MS) {
            splashWaitedMs = waited + SPLASH_POLL_MS
            awaitSplash(p, c)
        }
    }

    private fun loadAll(p: Port, c: PreloadConfig) {
        for (d in AdPreloadPlan.decide(c, p.openPlacements(), p.rwiConfigured(), p::removed, p::disabled)) {
            if (d.skip != null) {
                info("preload: skip ${d.slot.format.rc} ${d.slot.placement.ifEmpty { "-" }} (${d.skip})")
                continue
            }
            val wait = if (d.slot.format == PreloadFormat.INTERSTITIAL) p.interstitialWaitMs() else 0L
            if (wait <= 0L) load(p, d.slot) else {
                info("preload: defer ${d.slot.format.rc} ${d.slot.placement} $wait ms (fullStartTime)")
                p.post(wait) { load(p, d.slot) }
            }
        }
    }

    /** Chỉ slot rảnh; `trigger=preload` (cả Full2 kéo theo, `InterstitialController.kt:89`, `RewardedController.kt:72`)
     *  chỉ giữ khi lượt thật sự mở (D26-230 I2, m1). */
    private fun load(p: Port, slot: PreloadSlot) {
        val (pl, rc) = slot.placement to slot.format.rc
        if (p.isLoading(pl)) return info("preload: skip $rc $pl (loading)")
        val f2 = p.full2FlagShow()
        val pulls = if (slot.format == PreloadFormat.INTERSTITIAL) f2 == 1 || f2 == 3 else slot.format == PreloadFormat.REWARDED && f2 >= 2
        val full2 = ("full_2_" + pl.substringAfter('_'))
            .takeIf { pulls && !p.removed(FLAG_FULLSCREEN) && !p.disabled(FLAG_FULLSCREEN) && !p.isLoading(it) }
        val marks = listOfNotNull(pl, full2).onEach(AdEventLog::markPreload)
        try {
            p.load(slot)
        } catch (e: Exception) {
            LogUtil.logE("preload: $pl load failed: ${e.message}")
        }
        marks.filterNot(p::isLoading).forEach(AdEventLog::clearPreload)
        info(if (p.isLoading(pl)) "preload: $rc $pl load" else "preload: skip $rc $pl (held or refused)")
    }

    private fun info(msg: String) { Log.i(LogUtil.TAG, msg) }

    /** Như process mới: chưa lên lịch, cấu hình mặc định, không chờ host, [port] thật. Chỉ cho test. */
    @JvmSynthetic
    internal fun resetForTest() {
        scheduled.set(false)
        config = AdPreloadPlan.DEFAULT
        waitingForHost = null
        splashSeen = false
        splashWaitedMs = 0L
        port = HelperPort()
    }
}
