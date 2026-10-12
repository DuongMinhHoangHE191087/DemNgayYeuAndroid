package com.app.plugin.ads.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.app.plugin.ads.AdsEcmp
import com.app.plugin.ads.AdsLoadInnerCb
import com.app.plugin.ads.AdsPlBase
import com.app.plugin.until.AppUtil
import com.app.plugin.until.LogUtil

/**
 * 2.4.4 INFLIGHT: một request mỗi ad unit, khoá = **id thật đang xin**, từng id độc lập. Lượt bắt đầu ở id đầu không
 * backoff (thang `;`: id sau xin ngay); id đó slot khác đang bay ⇒ chờ; **mọi** id backoff ⇒ SKIP (cùng slot +
 * placement với lần lỗi) hoặc chờ. Trần chờ [WAIT_CAP_MS] ⇒ gửi như 2.4.3 (cả người chờ [PRELOAD]). Đặt chỗ theo id đang
 * bay ([onRequest]), nhả ở kết quả từng id của lượt đã gửi id đó ([onResult], 2.5.0 TURNKEY) và kết quả đầu cấp slot
 * ([hold]); sống khi slot chủ còn `isLoadingAd` + generation.
 *
 * 2.5.0 PRIO: mức lượt [SHOW] > [APP] > [PRELOAD] đọc lúc quyết ([level]); hàng chờ nhả người mức cao nhất (cùng mức FIFO);
 * lượt mới gặp chủ slot khác mức thấp hơn **chưa gửi** id ⇒ đi luôn, chủ bị đánh dấu nhường ([takeYield]); lượt đã gửi
 * không bao giờ bị giành. `ads_cf_unit_gate_prio_off` = 1 ⇒ không mức, không nhường (2.4.4).
 */
internal object UnitGate {
    private const val BACKOFF_BASE_MS = 2_000L
    private const val BACKOFF_CAP_MS = 16_000L
    private const val WAIT_CAP_MS = 10_000L
    const val PARKED: Int = -1; const val SKIPPED: Int = -2 // kết quả [admit] ngoài chỉ số id
    const val PRELOAD: Int = 0; const val APP: Int = 1; const val SHOW: Int = 2 // mức lượt ([level])
    private class Turn(val slot: Any, val placement: String, val originPl: String, val ids: List<String>, val live: () -> Boolean) {
        val sent = HashSet<String>() // id lượt này đã gửi ([onRequest])
    }
    private class Fail(val n: Int, val atMs: Long, val owner: String)
    private class Waiter(val slot: Any, val placement: String, val sinceMs: Long, val tail: String, val live: () -> Boolean, val run: (Boolean) -> Unit) {
        val timers = ArrayList<Runnable>(2)
    }
    private class TurnCb(val orig: AdsLoadInnerCb?, private val turn: Turn) : AdsLoadInnerCb {
        private var done = false
        private fun end() { if (!done) { done = true; onMain { endTurn(turn) } } }
        override fun onLoaded(placement: String, originPl: String, adId: String, ecmp: AdsEcmp?, adspl: AdsPlBase?) =
            end().also { orig?.onLoaded(placement, originPl, adId, ecmp, adspl) }
        override fun onLoadFailed(placement: String, originPl: String, adId: String, error: String) =
            end().also { orig?.onLoadFailed(placement, originPl, adId, error) }
    }

    private var clock: () -> Long = { SystemClock.elapsedRealtime() }
    private var handler: Handler? = null; private var off: Boolean? = null; private var prioOff: Boolean? = null
    private val turns = ArrayList<Turn>(); private val held = HashMap<String, Turn>(); private val fails = HashMap<String, Fail>()
    private val queues = HashMap<String, ArrayDeque<Waiter>>(); private val parked = HashMap<Any, Waiter>()
    private val demands = HashSet<String>(); private val preloads = HashSet<String>(); private val yielded = HashMap<Any, Turn>()
    private var nativeShow: ((String) -> Boolean)? = null
    private fun main(): Handler = handler ?: Handler(Looper.getMainLooper()).also { handler = it }
    private fun onMain(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main().post(block) }
    private fun info(msg: String) { Log.i(LogUtil.TAG, msg) }

    /**
     * `@integer/ads_cf_unit_gate_off` = 1 ⇒ cổng tắt (2.4.3 nguyên vẹn); `@integer/ads_cf_unit_gate_prio_off` = 1 ⇒ không
     * mức, không nhường (2.4.4). Đọc một lần mỗi process.
     */
    fun isOff(context: Context): Boolean {
        if (prioOff == null) prioOff = flag(context, "ads_cf_unit_gate_prio_off").also { if (it) LogUtil.logD("unit gate: prio off (ads_cf_unit_gate_prio_off=1)") }
        return off ?: flag(context, "ads_cf_unit_gate_off").also { off = it; if (it) LogUtil.logD("unit gate: off (ads_cf_unit_gate_off=1)") }
    }
    private fun flag(context: Context, name: String) = runCatching { AppUtil.getResourceInt(context, name) == 1 }.getOrDefault(false)

    /**
     * Mức lượt của [placement], đọc lúc quyết: [SHOW] khi có show đang chờ (Full2 nối của quảng cáo cha đang hiện, [demand];
     * view native chờ gắn, [showSource]); [PRELOAD] khi `full_2_*` hoặc lượt [AdPreloader] ([preload]); còn lại [APP].
     */
    fun level(placement: String): Int = when {
        prioOff == true -> APP
        placement in demands || nativeShow?.invoke(placement) == true -> SHOW
        placement.startsWith("full_2_") || placement in preloads -> PRELOAD
        else -> APP
    }

    /** `Full2Chain.begin/finishFull2Transition`: Full2 [key] (`full2Name(parent)`) có show đang chờ. Main. */
    fun demand(key: String, on: Boolean) = onMain { if (on) demands += key else demands -= key }

    /** `AdPreloader`: lượt nạp sẵn của [placement] mở ([on]) / lượt đầu kết thúc. Main. */
    fun preload(placement: String, on: Boolean) = onMain { if (on) preloads += placement else preloads -= placement }

    /** `NativeController`: vị từ "placement còn view native chờ gắn" (đọc map view lúc quyết, main). */
    fun showSource(source: ((String) -> Boolean)?) { nativeShow = source }

    /** Dấu nhường của [slot], tiêu một lần: placement lượt đã nhường nếu lượt đó còn sống (hẹn `tryLoad` vào lại cổng). */
    fun takeYield(slot: Any): String? = yielded.remove(slot)?.takeIf { it.live() }?.placement

    /** Cb gốc trong vỏ của [hold] (khử trùng khi gắn callback vào lượt đang nạp). */
    fun original(cb: AdsLoadInnerCb?): AdsLoadInnerCb? = (cb as? TurnCb)?.orig ?: cb

    /** @return chỉ số id để bắt đầu lượt, [PARKED] (slot vào hàng, [run] chạy lại sau) hoặc [SKIPPED]. */
    fun admit(slot: Any, placement: String, originPl: String, ids: List<String>, live: () -> Boolean, run: (Boolean) -> Unit): Int {
        val now = clock()
        val tag = "$placement slot=$originPl unit="
        var minLeft = Long.MAX_VALUE; var minUnit = ""; var own = false
        for ((i, unit) in ids.withIndex()) {
            val left = backoffLeft(unit, now)
            if (left > 0) {
                own = own || fails[unit]?.owner == "$originPl|$placement"
                if (left < minLeft) { minLeft = left; minUnit = unit }
                continue
            }
            if (heldByOther(unit, slot)) {
                if (!takeOver(unit, placement)) {
                    queues.getOrPut(unit) { ArrayDeque() }.addLast(park(slot, placement, now, tag + unit, live, run))
                    info("load merged into in-flight request: $tag$unit")
                    return PARKED
                }
            }
            leave(slot, now, tag + unit)
            return i
        }
        if (own) return SKIPPED.also { leave(slot, now, tag + minUnit); info("load skipped unit backoff $minLeft ms: $tag$minUnit") }
        val w = park(slot, placement, now, tag + minUnit, live, run)
        schedule(w, minLeft) { if (parked[w.slot] === w) { if (w.live()) w.run(false) else drop(w) } }
        info("load waits unit backoff $minLeft ms: $tag$minUnit")
        return PARKED
    }

    /** PRIO nhường: chủ sống của [unit] chưa gửi id đó và mức thấp hơn lượt [placement] ⇒ bỏ đặt chỗ, đánh dấu chủ. */
    private fun takeOver(unit: String, placement: String): Boolean {
        val h = held[unit] ?: return false
        if (prioOff == true || unit in h.sent || level(placement) <= level(h.placement)) return false
        held.remove(unit); yielded[h.slot] = h
        info("load yields unit to $placement: ${h.placement} slot=${h.originPl} unit=$unit")
        return true
    }

    /** Bypass sau trần chờ: id đầu không backoff (mọi id backoff ⇒ 0). */
    fun firstFree(ids: List<String>): Int = clock().let { now -> ids.indexOfFirst { backoffLeft(it, now) == 0L }.coerceAtLeast(0) }

    /** Lượt bắt đầu ở `ids[idx]`: đặt chỗ id đó, trả cb bọc nhả đặt chỗ ở kết quả đầu (idempotent). */
    fun hold(slot: Any, placement: String, originPl: String, ids: List<String>, idx: Int, live: () -> Boolean, cb: AdsLoadInnerCb?): AdsLoadInnerCb {
        val turn = Turn(slot, placement, originPl, ids, live).also { t -> turns.removeAll { !it.live() }; turns += t }
        ids.getOrNull(idx)?.let { held[it] = turn }
        return TurnCb(original(cb), turn)
    }

    /** `AdsTelemetry.onAdLoadStart`: lượt ghi id đã gửi; đặt chỗ đi theo id thật đang bay của lượt (fallthrough `;`). */
    fun onRequest(originPl: String, adId: String) = onMain {
        if (off == true) return@onMain
        val turn = turns.firstOrNull { it.originPl == originPl && adId in it.ids && it.live() } ?: return@onMain
        turn.sent += adId
        if (held[adId]?.let { it === turn || !it.live() } != false) held[adId] = turn
    }

    /**
     * Kết quả thật của request (không gồm timeout watchdog: request có thể còn ở mạng): nhả đặt chỗ id khi lượt đang giữ đã
     * gửi id đó và còn sống (kết quả của lượt khác cùng slot không nhả, TURNKEY); lỗi ⇒ n++, ok ⇒ n = 0.
     */
    fun onResult(originPl: String, placement: String, adId: String, ok: Boolean) = onMain {
        if (off == true) return@onMain
        val h = held[adId]
        if (h != null && h.originPl == originPl && adId in h.sent && h.live()) release(adId)
        if (ok) fails.remove(adId)
        else fails[adId] = Fail((fails[adId]?.n ?: 0) + 1, clock(), "$originPl|$placement")
    }

    private fun endTurn(turn: Turn) = turns.remove(turn).also { held.filterValues { it === turn }.keys.forEach(::release) }
    private fun release(unit: String) { held.remove(unit); main().post { drain(unit) } }
    /** `min(2 000 × 2^(n−1), 16 000)` ms từ mốc lỗi cuối. */
    private fun backoffLeft(unit: String, now: Long): Long = fails[unit]?.let { f ->
        (f.atMs + minOf(BACKOFF_BASE_MS shl (f.n - 1).coerceIn(0, 4), BACKOFF_CAP_MS) - now).coerceAtLeast(0L)
    } ?: 0L
    private fun heldByOther(unit: String, slot: Any): Boolean {
        val h = held[unit] ?: return false
        if (h.slot !== slot && h.live()) return true
        LogUtil.logD("unit gate: dropped stale hold of ${h.originPl} on $unit")
        release(unit)
        return false
    }
    private fun park(slot: Any, placement: String, now: Long, tail: String, live: () -> Boolean, run: (Boolean) -> Unit): Waiter {
        val prev = parked[slot]?.also { detach(it) }
        val w = Waiter(slot, placement, prev?.sinceMs ?: now, tail, live, run)
        parked[slot] = w
        schedule(w, w.sinceMs + WAIT_CAP_MS - now) { capExpired(w) }
        return w
    }
    /** C2: chờ quá [WAIT_CAP_MS] ⇒ bỏ đặt chỗ đang chặn, gửi không xét đặt chỗ (từ id đầu không backoff, [firstFree]). */
    private fun capExpired(w: Waiter) {
        if (parked[w.slot] !== w) return
        if (!w.live()) return drop(w)
        val units = queues.filterValues { w in it }.keys.onEach(::release) // drain chạy sau lượt bypass đã đặt chỗ
        leave(w.slot, clock(), w.tail)
        LogUtil.logD("unit gate: wait cap $WAIT_CAP_MS ms reached, hold on $units dropped, load sent as 2.4.3")
        w.run(true)
    }
    private fun leave(slot: Any, now: Long, tail: String) =
        parked.remove(slot)?.let { detach(it); info("load released after ${now - it.sinceMs} ms: $tail") }
    private fun drop(w: Waiter) {
        if (parked[w.slot] === w) parked.remove(w.slot)
        detach(w).also { LogUtil.logD("unit gate: waiter dropped, turn abandoned") }
    }
    private fun detach(w: Waiter) {
        w.timers.forEach { main().removeCallbacks(it) }
        w.timers.clear()
        queues.values.forEach { it.remove(w) }
    }
    private fun schedule(w: Waiter, delayMs: Long, block: () -> Unit) =
        Runnable(block).also { w.timers += it; main().postDelayed(it, delayMs.coerceAtLeast(0L)) }
    /** Nhả người chờ mức cao nhất (đọc lúc này), cùng mức FIFO; `prio_off` ⇒ FIFO. */
    private fun drain(unit: String) {
        while (true) {
            held[unit]?.let { if (it.live()) return }
            val q = queues[unit] ?: return
            val w = (if (prioOff == true) q.firstOrNull() else q.maxByOrNull { level(it.placement) }) ?: return
            q.remove(w)
            if (parked[w.slot] !== w) continue
            if (w.live()) w.run(false) else drop(w)
        }
    }

    fun resetForTest(now: () -> Long = { SystemClock.elapsedRealtime() }) {
        clock = now; handler = null; off = null; prioOff = null; nativeShow = null
        turns.clear(); held.clear(); fails.clear(); queues.clear(); parked.clear()
        demands.clear(); preloads.clear(); yielded.clear()
    }
}
