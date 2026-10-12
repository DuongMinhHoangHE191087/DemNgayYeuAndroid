package com.app.plugin.logevent

import android.content.Context
import android.util.Log
import com.app.plugin.consent.ConsentRules
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.TcfConsent
import com.app.plugin.until.PreferenceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Cổng consent của telemetry server (O3 (a), DOC/65 §7 #17): event `LogEventServer` đã dựng (JSON, `sequenceId`, `ts` lúc
 * phát) được **giữ** trong bộ nhớ khi chưa có tầng consent dùng được, thay vì đi ngay trong `Application.onCreate`.
 *
 * - Luật như `ConsentRules.tierAllows`: `GRANTED`/`PARTIAL` hoặc ngoài GDPR (`IABTCF_gdprApplies = 0`) → gửi;
 *   `DENIED` → bỏ event **đang giữ** (`onOutcome(DROPPED)`, decision log giữ bản đĩa như kill switch); còn lại
 *   (`UNKNOWN` trong GDPR, hoặc `gdprApplies` vắng) → giữ, trần [HOLD_CAP], tràn thì bỏ event cũ nhất **không phải**
 *   `first_open`/`session_start` (hai event của funnel không bao giờ bị đẩy ra).
 * - Sau `DENIED` không gửi gì (N1, quyết định Q2 1.6.4): event phát lúc tầng là `DENIED` vào vòng RAM `deniedHeld`
 *   (trần [DENIED_CAP], tràn bỏ cũ nhất = `DROPPED`), không ghi đĩa, chỉ xả khi tầng đổi sang `GRANTED`/`PARTIAL` trong
 *   cùng process, mất khi process kết thúc. `brain_decision` (sống hay replay) → `DROPPED` ngay, không vào vòng: bản đĩa
 *   của `DecisionLogger` giữ và replay ở ranh giới phiên kế (sign-off 1.6.4 R1).
 * - Chưa [install] (test, app không kế thừa `AppPluginBase`) = gửi ngay, hành vi cũ.
 * - Tầng đổi ([ConsentState.onChange]) chỉ hẹn việc lên [ioScope]; [submitOrHold] chạy trên IO của `LogEventServer`.
 *   Không I/O đĩa trên main ngoài lần đọc ở [install] (prefs đã warm-up, như `TIME_OPEN`).
 * - [KEY_PENDING] (prefs của `PreferenceHelper`) = số **event** (mọi loại, không phải số người) sẽ mất nếu process chết lúc
 *   này: đang giữ + đã bị đẩy ra + của process trước chưa báo; `brain_decision` không tính (còn bản trên đĩa, replay lại).
 *   Lần mở kế đọc nó lúc [install] → `session_start.pre_consent_lost`; chỉ người quay lại và đồng ý báo (`DENIED` xoá).
 *   Vòng `deniedHeld` không tính (Q2: không ghi đĩa; mất khi process chết, không báo).
 *
 * Log thẳng `Log.d`/`Log.e` tag `AppPlugin` (như `AnalyticsConsent.apply`): chạy trong `onCreate`, trước cờ của `LogUtil`.
 */
internal object TelemetryConsentGate {
    enum class Decision { PASS, HOLD, DROP }

    /** Trước consent thường ≤ 10 event (`first_open`, `session_start`, `fir_rm_fet_*`, replay…); 50 là trần an toàn. */
    const val HOLD_CAP = 50
    /** Trần vòng RAM sau `DENIED` (Q2 "existing cap 500" = `LogEventQueue.maxPending`). */
    private const val DENIED_CAP = 500
    /** Số event chưa gửi nếu process chết lúc này (xem KDoc của object). Chỉ ghi trên IO. */
    const val KEY_PENDING = "telemetry_pre_consent_pending"

    private const val TAG = "AppPlugin"

    /** Event của funnel: tràn trần không đẩy ra. */
    private val PINNED = setOf(EventNames.FIRST_OPEN, EventNames.SESSION_START, EventNames.CONSENT_FORM_SHOWN, EventNames.CONSENT_CHOICE)
    private const val PREFIX = "telemetry consent gate:"

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val onTier: (ConsentTier) -> Unit = { reevaluate() }

    private val held = ArrayDeque<Pair<LogEventQueue.Item, (LogEventQueue.Item) -> Unit>>()
    /** Event phát khi tầng là `DENIED` (N1/Q2): chỉ RAM, không vào [KEY_PENDING]. Trong khoá của object. */
    private val deniedHeld = ArrayDeque<Pair<LogEventQueue.Item, (LogEventQueue.Item) -> Unit>>()
    private var deniedEvictLogged = false

    @Volatile private var appContext: Context? = null

    /** [KEY_PENDING] của process trước, đọc ở [install]; [consumePendingLostForSessionStart] lấy một lần. */
    private var lostFromPrevious = 0

    /** Số đã chuyển cho `session_start`, còn tính vào [KEY_PENDING] tới khi chính item đó được gửi (hay bỏ vì `DENIED`). */
    private var carriedLost = 0

    /** Event bị đẩy ra vì trần trong process này (trừ `brain_decision`): mất, báo ở `session_start` lần mở kế. */
    private var evictedLost = 0
    private var lastWrittenPending = 0
    private var evictLogged = false

    private var flushed = 0L
    private var dropped = 0L
    private var evicted = 0L

    val heldCount: Int get() = synchronized(this) { held.size }
    val flushedCount: Long get() = synchronized(this) { flushed }
    val droppedCount: Long get() = synchronized(this) { dropped }
    val evictedCount: Long get() = synchronized(this) { evicted }

    /** Tầng dùng được chưa: cùng luật `ConsentRules.tierAllows`; `DENIED` thắng `gdprApplies = 0`. */
    fun decide(tier: ConsentTier, gdprApplies: Boolean?): Decision = when {
        ConsentRules.tierAllows(tier, gdprApplies) -> Decision.PASS
        tier == ConsentTier.DENIED -> Decision.DROP
        else -> Decision.HOLD
    }

    /** Gọi một lần trong `AppPluginBase.initPlugin`, trước `LogEventServer.init` (replay, `first_open`). Main thread. */
    fun install(context: Context) {
        val previous = PreferenceHelper.getInstance().get(KEY_PENDING, 0).coerceAtLeast(0)
        synchronized(this) {
            if (appContext != null) return
            appContext = context.applicationContext ?: context
            lostFromPrevious = previous
            lastWrittenPending = previous
        }
        if (!ConsentState.hasOnChange(onTier)) ConsentState.onChange(onTier)
    }

    /** Event đã dựng: gửi, giữ, hay (tầng `DENIED`) bỏ các event đang giữ rồi đưa event này vào vòng `DENIED`. Luồng IO. */
    fun submitOrHold(item: LogEventQueue.Item, submit: (LogEventQueue.Item) -> Unit) {
        if (appContext == null) return submit(item)
        evaluate(item to submit)
    }

    /** Tầng đổi: xả hoặc bỏ bộ đệm trên IO. Gọi từ listener tầng (main hoặc luồng UMP), không chặn luồng gọi. */
    fun reevaluate() {
        if (appContext == null) return
        try {
            ioScope.launch { evaluate(null) }
        } catch (e: Exception) {
            Log.e(TAG, "$PREFIX reevaluate not scheduled: ${e.message}")
        }
    }

    /** `pre_consent_lost` cho `session_start` của process này (0 = không có khoá). Main thread, không đọc đĩa. */
    fun consumePendingLostForSessionStart(): Int = synchronized(this) {
        carriedLost = lostFromPrevious
        lostFromPrevious = 0
        carriedLost
    }

    /**
     * Áp tầng hiện tại lên bộ đệm và [incoming] (nếu có). Luồng IO. Tầng đọc **trong** khoá (review Task 6 I1): event đọc
     * `UNKNOWN` rồi thua lượt xả của `GRANTED` không bị kẹt trong bộ đệm. `gdprApplies` (prefs của CMP) đọc ngoài khoá — khoá
     * mà `consumePendingLostForSessionStart` (main) chờ — và chỉ dùng khi tầng trong khoá vẫn `UNKNOWN` (khác: `null`).
     */
    private fun evaluate(incoming: Pair<LogEventQueue.Item, (LogEventQueue.Item) -> Unit>?) {
        val context = appContext
        val preRead = if (context != null && ConsentState.tier() == ConsentTier.UNKNOWN) TcfConsent.gdprApplies(context) else null
        val lost = synchronized(this) {
            val tier = ConsentState.tier()
            val gdprApplies = if (tier == ConsentTier.UNKNOWN) preRead else null
            val decision = decide(tier, gdprApplies)
            val out = when (decision) {
                Decision.PASS -> flushLocked(tier)
                Decision.DROP -> dropAllLocked() + (incoming?.let(::holdDeniedLocked) ?: emptyList())
                Decision.HOLD -> incoming?.let { holdLocked(it, ConsentRules.scopeLabel(tier, gdprApplies)) } ?: emptyList()
            }
            if (decision == Decision.PASS) incoming?.let { (item, submit) ->
                submit(item)
                clearCarriedIfSessionStart(item)
            }
            writePendingLocked()
            out
        }
        reportDropped(lost)
    }

    /**
     * Gửi mọi event đang giữ theo thứ tự phát: vòng `DENIED` trước (lượt `DENIED` nào cũng bỏ [held], nên [held] chỉ còn
     * event phát sau vòng), rồi [held]. Trả event không gửi được (submit ném — sender vốn không ném).
     */
    private fun flushLocked(tier: ConsentTier): List<LogEventQueue.Item> {
        val all = deniedHeld + held
        if (all.isEmpty()) return emptyList()
        val failed = all.mapNotNull { (item, submit) -> item.takeIf { runCatching { submit(item) }.isFailure } }
        all.forEach { clearCarriedIfSessionStart(it.first) }
        flushed += all.size
        val note = if (failed.isEmpty()) "" else ", ${failed.size} failed"
        if (deniedHeld.isNotEmpty()) Log.d(TAG, "$PREFIX flushed ${deniedHeld.size} held after DENIED (tier=$tier)$note")
        if (held.isNotEmpty()) Log.d(TAG, "$PREFIX flushed ${held.size}$note")
        deniedHeld.clear()
        held.clear()
        return failed
    }

    /** `DENIED`: bỏ bộ đệm. Người từ chối không nằm trong funnel: số đã đẩy ra của process này cũng xoá. */
    private fun dropAllLocked(): List<LogEventQueue.Item> {
        if (held.isEmpty()) return emptyList()
        val out = held.map { it.first }
        out.forEach(::clearCarriedIfSessionStart)
        evictedLost = 0
        held.clear()
        dropped += out.size
        Log.d(TAG, "$PREFIX dropped ${out.size} (tier=DENIED)")
        return out
    }

    /**
     * Tầng `DENIED` (N1/Q2): `brain_decision` → `DROPPED` ngay (bản đĩa của `DecisionLogger` giữ, lô replay không treo);
     * event khác vào vòng RAM, tràn [DENIED_CAP] thì bỏ cũ nhất (`DROPPED`). `session_start` không còn giữ số của process
     * trước (như `DENIED` bỏ nó ở [dropAllLocked]): số đó đi theo item nếu tầng đổi, không vào [KEY_PENDING].
     */
    private fun holdDeniedLocked(entry: Pair<LogEventQueue.Item, (LogEventQueue.Item) -> Unit>): List<LogEventQueue.Item> {
        val item = entry.first
        if (item.name == EventNames.BRAIN_DECISION) {
            dropped++
            return listOf(item)
        }
        clearCarriedIfSessionStart(item)
        deniedHeld.addLast(entry)
        val out = mutableListOf<LogEventQueue.Item>()
        while (deniedHeld.size > DENIED_CAP) {
            out += deniedHeld.removeFirst().first
            dropped++
            if (!deniedEvictLogged) Log.e(TAG, "$PREFIX denied ring full (cap $DENIED_CAP): dropped ${out.last().name}, logged once")
            deniedEvictLogged = true
        }
        Log.d(TAG, "$PREFIX hold-denied ${item.name} (ring=${deniedHeld.size})")
        return out
    }

    private fun holdLocked(entry: Pair<LogEventQueue.Item, (LogEventQueue.Item) -> Unit>, scope: String): List<LogEventQueue.Item> {
        held.addLast(entry)
        val out = mutableListOf<LogEventQueue.Item>()
        while (held.size > HOLD_CAP) {
            val index = held.indexOfFirst { it.first.name !in PINNED }
            if (index < 0) break
            out += held.removeAt(index).first
            if (out.last().name != EventNames.BRAIN_DECISION) evictedLost++
            evicted++
            if (!evictLogged) Log.e(TAG, "$PREFIX evicted 1 (cap $HOLD_CAP): ${out.last().name}, logged once")
            evictLogged = true
        }
        Log.d(TAG, "$PREFIX hold ${entry.first.name} (held=${held.size}, $scope)")
        return out
    }

    /** `session_start` mang [carriedLost] đã được gửi hay bị bỏ vì `DENIED`: số đó không còn treo. */
    private fun clearCarriedIfSessionStart(item: LogEventQueue.Item) {
        if (item.name == EventNames.SESSION_START) carriedLost = 0
    }

    /**
     * [KEY_PENDING] = event đang giữ (trừ `brain_decision`) + đã đẩy ra + số của process trước chưa báo (chưa chuyển, hoặc
     * đã chuyển cho `session_start` chưa gửi). Process chết ở đâu thì lần mở kế vẫn báo đủ. Chỉ ghi khi đổi. Luồng IO.
     */
    private fun writePendingLocked() {
        val value = lostFromPrevious + carriedLost + evictedLost + held.count { it.first.name != EventNames.BRAIN_DECISION }
        if (value == lastWrittenPending) return
        lastWrittenPending = value
        PreferenceHelper.getInstance().setValue(KEY_PENDING, value)
    }

    private fun reportDropped(items: List<LogEventQueue.Item>) {
        for (item in items) {
            try {
                item.onOutcome?.invoke(EventOutcome.DROPPED)
            } catch (e: Exception) {
                Log.e(TAG, "$PREFIX onOutcome failed (${item.name}): ${e.message}")
            }
        }
    }

    @JvmSynthetic
    internal fun resetForTest() {
        ConsentState.removeOnChange(onTier)
        synchronized(this) {
            held.clear()
            deniedHeld.clear()
            deniedEvictLogged = false
            appContext = null
            lostFromPrevious = 0
            carriedLost = 0
            evictedLost = 0
            lastWrittenPending = 0
            evictLogged = false
            flushed = 0
            dropped = 0
            evicted = 0
        }
    }
}
