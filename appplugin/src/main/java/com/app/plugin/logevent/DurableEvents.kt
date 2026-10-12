package com.app.plugin.logevent

import android.content.Context
import android.util.Log
import com.app.plugin.brain.DecisionLogQueue
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.TcfConsent
import com.google.gson.JsonParser
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Event tiền + phễu sống qua kill (1.7.0 Task 6, CEO Q-G11 (a), sign-off R1 (a)–(d), phán quyết R-9).
 *
 * - Tập [DURABLE]: body JSON đã dựng (giữ `ts`, `sequenceId`, `event_id`) ghi vào [DecisionLogQueue] riêng
 *   (`files/appplugin_events`, trần [MAX_RECORDS], bỏ cũ nhất) **chỉ** khi cổng consent cho gửi — lambda `submit` của
 *   `TelemetryConsentGate` gọi [storeThenSubmit]. Event đang giữ (UNKNOWN) hay sau `DENIED` không bao giờ chạm đĩa (Q2).
 * - R1 (a): lambda đó chạy trong monitor của cổng, cái main chờ ở `consumePendingLostForSessionStart`. [storeThenSubmit]
 *   chỉ giao việc cho [ioExecutor]; tác vụ IO ghi đĩa **rồi** `submit`, ngoài monitor.
 * - Xoá khi server nhận (2xx) hoặc từ chối vĩnh viễn — chỉ 400/413 (R-9, đếm [permanentRejectedCount]; 401 là lỗi tạm, bản đĩa ở lại — 2.0.0 B1). 429, 5xx,
 *   4xx khác, lỗi mạng, `DROPPED`: bản đĩa ở lại, replay lượt sau. Xoá theo lô (`removeAll`).
 * - R1 (b): replay chỉ khi tầng qua cổng (GRANTED/PARTIAL/ngoài GDPR), lúc [onTransportReady] hoặc từ `ConsentState.onChange`;
 *   UNKNOWN → không làm gì (không vào `held`, không đổi `KEY_PENDING`); `DENIED` → [clearNow] xoá file.
 * - R1 (c): mỗi lượt ≤ `LogEventTransport.eventQueueHeadroom()`, phần còn lại ở đĩa; lượt kế chỉ khi lượt trước xoá được bản.
 * - R1 (d): replay gửi đúng body đã lưu; thứ tự do `sequenceId`/`ts` trong body quyết định, không phải thứ tự gửi.
 * - Đọc/ghi file chỉ trên [ioExecutor] (một luồng: ghi, xoá, `clear` theo thứ tự hẹn), không trên main.
 */
internal object DurableEvents {
    val DURABLE: Set<String> = setOf(
        EventNames.AD_REVENUE, EventNames.IAP_PURCHASE, EventNames.FIRST_OPEN, EventNames.SESSION_START,
        EventNames.SESSION_END, EventNames.CONSENT_FORM_SHOWN, EventNames.CONSENT_CHOICE,
        EventNames.IAP_RESULT, EventNames.PAYWALL_IMPRESSION, EventNames.PAYWALL_HOLDOUT_SKIP, EventNames.PAYWALL_SKIP,
        EventNames.PAYWALL_CHOICE, EventNames.SUBSCRIPTION_STATE // 1.8.0 T7 (D7): tiền + phễu paywall, không mất
    )
    const val MAX_RECORDS = 2000
    private const val DIR_NAME = "appplugin_events"
    private const val SEP = '\u001F'
    private const val MAX_ROUNDS = 10
    private const val TAG = "AppPlugin"

    /** Phán quyết R-9: chỉ các mã này là vĩnh viễn (bỏ + đếm); 429 (theo `Retry-After`) và 5xx thử lại. 2.0.0 B1: 401 ra khỏi tập. */
    private val PERMANENT_HTTP = setOf(400, 413)

    /** 2.0.0 B1 (T11 m3): 401 = lỗi tạm thời phía server (xoay khoá, xác thực) — bản đĩa ở lại, người gọi nhận DROPPED. */
    private const val TRANSIENT_HTTP = 401

    @Volatile private var queue: DecisionLogQueue? = null
    @Volatile private var tcfContext: Context? = null

    /** Luồng đĩa của tập bền. Test thay bằng executor tự điều khiển. */
    internal var ioExecutor: Executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "appplugin-durable-events").apply { isDaemon = true }
    }

    /** Dòng (tên + body) đã giao cho sender mà chưa có kết quả: lượt replay bỏ qua, không gửi trùng. */
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val toRemove = ConcurrentLinkedQueue<String>()
    private val outstanding = AtomicInteger(0)
    private val removedInPass = AtomicInteger(0)

    val storedCount = AtomicLong(0)
    val replayedCount = AtomicLong(0)
    val permanentRejectedCount = AtomicLong(0)

    private val onTier: (ConsentTier) -> Unit = { tier -> onTierChanged(tier) }

    /** `AppPluginManager.initPlugin` (main): chỉ đọc field `dataDir`, không chạm đĩa (như `attachQueueForApp`). */
    fun attach(context: Context) = attachDir(File(context.applicationInfo.dataDir, "files"), context.applicationContext ?: context)

    internal fun attachDir(filesDir: File, context: Context?) = attach(DecisionLogQueue(File(filesDir, DIR_NAME), MAX_RECORDS), context)

    internal fun attach(q: DecisionLogQueue, context: Context?) {
        queue = q
        tcfContext = context
        if (!ConsentState.hasOnChange(onTier)) ConsentState.onChange(onTier)
        if (LogEventTransport.isInitialized()) onTransportReady()
    }

    /** `LogEventTransport.init` xong: `DENIED` → xoá file; tầng qua cổng → replay (R1 b). Chỉ hẹn IO. */
    fun onTransportReady() = onTierChanged(ConsentState.tier())

    private fun onTierChanged(tier: ConsentTier) {
        if (queue == null) return
        runOnIo("tier") { if (tier == ConsentTier.DENIED) clearNow() else replayRound(1) }
    }

    /**
     * Gọi trong lambda `submit` của cổng (nhánh PASS, trong monitor của `TelemetryConsentGate`): **không I/O ở đây** (R1 a).
     * Tác vụ IO ghi đĩa rồi mới [submit]; chưa gắn hàng đợi thì gửi ngay như cũ.
     */
    fun storeThenSubmit(item: LogEventQueue.Item, submit: (LogEventQueue.Item) -> Unit) {
        val q = queue ?: return submit(item)
        val record = item.name + SEP + item.body
        inFlight += record
        val tracked = item.copy(onOutcome = Tracked(record, item.onOutcome, replay = false))
        val scheduled = runOnIo("store") {
            // Q2: event phát sau DENIED (vòng RAM, `consent_tier` của envelope = DENIED) được xả khi GRANTED nhưng không
            // bao giờ xuống đĩa; tầng đổi sang DENIED sau khi cổng cho qua: event vẫn đi, không để lại bản đĩa.
            val body = runCatching { JsonParser.parseString(item.body).asJsonObject }.getOrNull()
            val raisedDenied = body?.get("consent_tier")?.asString == ConsentTier.DENIED.name
            if (!raisedDenied && ConsentState.tier() != ConsentTier.DENIED) store(q, item.name, record, body)
            submit(tracked)
        }
        if (!scheduled) submit(tracked)
    }

    private fun store(q: DecisionLogQueue, name: String, record: String, body: com.google.gson.JsonObject?) {
        if (!q.enqueue(record)) {
            Log.e(TAG, "logevent durable store failed: $name kept in memory only")
            return
        }
        storedCount.incrementAndGet()
        val eventId = runCatching { body?.get("event_id")?.asString }.getOrNull() ?: "-"
        Log.i(TAG, "logevent durable stored $name $eventId")
    }

    /** Một lượt replay; chỉ trên [ioExecutor]. */
    private fun replayRound(round: Int) {
        val q = queue ?: return
        if (!tierPasses() || !LogEventControl.sendAllowed() || !LogEventTransport.isInitialized() || outstanding.get() > 0) return
        val lines = q.peekAll()
        if (lines == null) {
            Log.e(TAG, "logevent durable replay skipped: file unreadable, records kept")
            return
        }
        val candidates = lines.filter { it !in inFlight && it.indexOf(SEP) > 0 }
        if (candidates.isEmpty()) return
        val budget = LogEventTransport.eventQueueHeadroom()
        if (budget <= 0) {
            Log.d(TAG, "logevent durable replay deferred: event queue full, ${candidates.size} record(s) stay on disk")
            return
        }
        val picked = candidates.take(budget)
        outstanding.set(picked.size)
        removedInPass.set(0)
        replayedCount.addAndGet(picked.size.toLong())
        Log.i(TAG, "logevent durable replayed ${picked.size} (round $round, ${lines.size - picked.size} stay on disk)")
        for (record in picked) {
            inFlight += record
            val item = LogEventQueue.Item(record.substringBefore(SEP), record.substringAfter(SEP), Tracked(record, null, replay = true, round = round))
            TelemetryConsentGate.submitOrHold(item) { LogEventTransport.submitNow(it) }
        }
    }

    /** Tầng qua cổng: cùng luật `TelemetryConsentGate.decide`; `gdprApplies` (prefs của CMP) chỉ đọc khi tầng UNKNOWN. */
    private fun tierPasses(): Boolean {
        val tier = ConsentState.tier()
        val gdpr = if (tier == ConsentTier.UNKNOWN) tcfContext?.let { runCatching { TcfConsent.gdprApplies(it) }.getOrNull() } else null
        return TelemetryConsentGate.decide(tier, gdpr) == TelemetryConsentGate.Decision.PASS
    }

    /** `onOutcome` của item bền: nhớ mã HTTP của lần bị từ chối hẳn (sender báo trước `REJECTED`). */
    private class Tracked(
        val record: String,
        val downstream: ((EventOutcome) -> Unit)?,
        val replay: Boolean,
        val round: Int = 0
    ) : (EventOutcome) -> Unit, LogEventSender.RejectionCodeSink {
        @Volatile private var code = 0

        override fun onRejectedHttp(code: Int) {
            this.code = code
        }

        override fun invoke(outcome: EventOutcome) {
            onOutcome(this, outcome, code)
            // Sổ token IAP (`PurchaseLedger`) thấy 401 là DROPPED → không `reported`, phát lại ở process sau.
            downstream?.invoke(if (outcome == EventOutcome.REJECTED && code == TRANSIENT_HTTP) EventOutcome.DROPPED else outcome)
        }
    }

    private fun onOutcome(t: Tracked, outcome: EventOutcome, code: Int) {
        val remove = outcome == EventOutcome.ACCEPTED || (outcome == EventOutcome.REJECTED && code in PERMANENT_HTTP)
        if (remove) {
            if (outcome == EventOutcome.REJECTED) {
                permanentRejectedCount.incrementAndGet()
                Log.e(TAG, "logevent durable dropped: http $code (${t.record.substringBefore(SEP)})")
            }
            toRemove += t.record
            if (t.replay) removedInPass.incrementAndGet()
            if (!runOnIo("remove") { drainRemovals() }) inFlight -= t.record
        } else {
            inFlight -= t.record   // 429/5xx/4xx khác/mạng/DROPPED: nằm lại trên đĩa, lượt sau gửi lại
        }
        if (t.replay && outstanding.decrementAndGet() == 0) {
            runOnIo("replay next") { if (removedInPass.get() > 0 && t.round < MAX_ROUNDS) replayRound(t.round + 1) }
        }
    }

    /** Xoá theo lô mọi dòng đã có kết quả cuối; chỉ trên [ioExecutor]. */
    private fun drainRemovals() {
        val batch = generateSequence { toRemove.poll() }.toList()
        if (batch.isEmpty()) return
        queue?.removeAll(batch)
        inFlight -= batch.toSet()
    }

    private fun clearNow() {
        queue?.clear()
        toRemove.clear()
        Log.i(TAG, "logevent durable cleared (tier=DENIED)")
    }

    /** 2.4.2 CLEAR (`LocalDataReset`): làm rỗng tập bền trên [ioExecutor] (sau mọi ghi đã hẹn); `queue` đọc một lần (m10). */
    internal fun clearAll(filesDir: File): Boolean =
        (queue ?: DecisionLogQueue(File(filesDir, DIR_NAME), MAX_RECORDS)).let { q -> runOnIo("clear") { q.clear() } }

    private fun runOnIo(what: String, task: () -> Unit): Boolean = try {
        ioExecutor.execute {
            try {
                task()
            } catch (e: Exception) {
                Log.e(TAG, "logevent durable $what failed: ${e.message}")
            }
        }
        true
    } catch (e: RejectedExecutionException) {
        Log.e(TAG, "logevent durable $what not scheduled: ${e.message}")
        false
    }

    @JvmSynthetic
    internal fun resetForTest() {
        ConsentState.removeOnChange(onTier)
        queue = null
        tcfContext = null
        inFlight.clear()
        toRemove.clear()
        outstanding.set(0)
        removedInPass.set(0)
        storedCount.set(0)
        replayedCount.set(0)
        permanentRejectedCount.set(0)
    }
}
