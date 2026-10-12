package com.app.plugin.iap

import com.app.plugin.brain.AdsBrain
import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.consent.TcfConsent
import com.app.plugin.logevent.EventNames
import com.app.plugin.logevent.EventOutcome
import com.app.plugin.logevent.IapEventLog
import com.app.plugin.logevent.LogEventTransport
import com.app.plugin.logevent.TelemetryConsentGate
import com.app.plugin.until.LogUtil
import com.app.plugin.until.PreferenceHelper
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Sổ token của giao dịch đã trả tiền (1.8.0 Task 5, D5, D13, D15): `iap_purchase` đúng một lần theo purchase token, sống
 * qua kill. Chỉ chạm trên luồng "appplugin-billing" (không khoá, không I/O trên main).
 *
 * Thứ tự (D13/D15, đã quyết): quyền lợi cấp → [record] ghi dấu `granted` xuống prefs ([PREFS_KEY], `commit`) → gửi
 * `iap_purchase` qua `LogEventTransport` (tập bền `DurableEvents`, D7) với `ts` = giờ giao dịch → `reported` khi server
 * trả 2xx (`EventOutcome.ACCEPTED`) hay từ chối hẳn (`REJECTED`: 4xx trừ 408/429, 1.9.0 T11). Mỗi lần khôi phục, token
 * `granted && !reported` chưa phát trong process này được phát lại **chỉ khi** tầng consent cho qua cổng
 * (GRANTED/PARTIAL/ngoài GDPR): `DENIED` → không bao giờ gửi, token ở `!reported`, phát lại ở một process sau có tầng
 * cho qua (R-P1). Server khử trùng theo token (at-least-once an toàn).
 * Tối đa [MAX_TOKENS] token, bỏ cũ nhất.
 */
internal object PurchaseLedger {
    const val PREFS_KEY = "mem_iap_ledger"
    const val MAX_TOKENS = 200

    /** Pha thực thu của một lượt mua (F4/D5): `phase` ∈ `IapEventLog.PHASE_*`, [micros] = số tiền thật sự trừ. */
    data class Charged(val phase: String, val micros: Long, val offerTag: String)

    /** Một token đã cấp quyền lợi, đủ trường để phát lại `iap_purchase` y như lần đầu. */
    data class Entry(
        val token: String, val productId: String, val orderId: String, val priceMicros: Long, val currency: String,
        val quantity: Int, val isSubscription: Boolean, val phase: String, val offerTag: String, val tsMs: Long,
        val paywallSessionId: String, val placement: String, val variantId: String, val reported: Boolean = false
    )

    private var entries: LinkedHashMap<String, Entry>? = null
    private val emittedThisProcess = HashSet<String>()

    /**
     * Pha thực thu của [offerToken] trong [offer] (F4): phase đầu 0 đ = trial (thu 0), phase đầu có giá mà còn phase sau
     * = intro, còn lại = base; INAPP = giá một lần. Không có offer (chưa có giá) → `""`/0.
     */
    fun phaseOf(offer: PortOffer?, offerToken: String): Charged {
        if (offer == null) return Charged("", 0L, "")
        if (offer.type == IapProductType.INAPP) return Charged(IapEventLog.PHASE_INAPP, offer.basePriceMicros, "")
        val o = offer.offers.firstOrNull { it.token == offerToken } ?: offer.offers.firstOrNull()
            ?: return Charged(IapEventLog.PHASE_BASE, offer.basePriceMicros, "")
        val first = o.phases.firstOrNull() ?: return Charged(IapEventLog.PHASE_BASE, offer.basePriceMicros, o.tag)
        return when {
            first.priceMicros == 0L -> Charged(IapEventLog.PHASE_TRIAL, 0L, o.tag)
            o.phases.size > 1 -> Charged(IapEventLog.PHASE_INTRO, first.priceMicros, o.tag)
            else -> Charged(IapEventLog.PHASE_BASE, first.priceMicros, o.tag)
        }
    }

    fun isGranted(token: String): Boolean = token in load()

    internal fun isReported(token: String): Boolean = load()[token]?.reported == true

    /**
     * Token mới: ghi `granted` (đĩa) TRƯỚC, rồi phát `iap_purchase`; [partners] = báo cả AppsFlyer + brain (lượt mua thật
     * của máy này; không báo cho token đã acknowledge mà sổ chưa biết — mua ở máy khác/trước 1.8.0). Token đã có → không làm gì.
     * [onAccepted] chạy trên luồng của sender khi server nhận (2xx) hay từ chối hẳn (REJECTED): người gọi đưa [markReported]
     * về luồng billing.
     */
    fun record(entry: Entry, partners: Boolean, onAccepted: (String) -> Unit) {
        val map = load()
        if (entry.token in map) return
        map[entry.token] = entry
        while (map.size > MAX_TOKENS) map.remove(map.keys.first())
        save(map)
        emit(entry, onAccepted)
        if (partners) reportPartners(entry)
    }

    /**
     * Dựng [Entry] cho giao dịch PURCHASED [p]: giá/tiền tệ theo pha thực thu của offer đã mua ([product] `offerToken`,
     * F4); chưa có giá → 0/"" (vẫn một lần theo token).
     */
    fun recordPurchase(
        p: PortPurchase, product: IapProduct?, offer: PortOffer?, isSubscription: Boolean, partners: Boolean,
        paywall: PaywallSession.Snapshot?, onAccepted: (String) -> Unit
    ) {
        val productId = p.products.firstOrNull() ?: return
        val charged = phaseOf(offer.takeIf { product != null }, product?.offerToken ?: "")
        val entry = Entry(
            p.token, productId, p.orderId, charged.micros, product?.currencyCode ?: "", p.quantity, isSubscription,
            charged.phase, charged.offerTag, p.timeMs, paywall?.sessionId ?: "", paywall?.placement ?: "", paywall?.variantId ?: ""
        )
        record(entry, partners, onAccepted)
    }

    /** `iap_result` cho mọi kết cục của một lượt mua (T7, hợp đồng E). */
    fun logResult(result: IapResult, productId: String) {
        val (outcome, code) = when (result) {
            is IapResult.Success -> IapEventLog.OUTCOME_SUCCESS to BillingPort.OK
            is IapResult.Pending -> IapEventLog.OUTCOME_PENDING to BillingPort.OK
            IapResult.UserCanceled -> IapEventLog.OUTCOME_CANCEL to BillingPort.USER_CANCELED
            IapResult.AlreadyOwned -> IapEventLog.OUTCOME_ALREADY_OWNED to BillingPort.ITEM_ALREADY_OWNED
            is IapResult.Failed -> IapEventLog.OUTCOME_FAILED to result.code
            is IapResult.WebCheckoutOpened -> return
        }
        try { IapEventLog.iapResult(outcome, code, productId) } catch (e: Exception) { LogUtil.logE("IapHelper iap_result: ${e.message}") }
    }

    fun markReported(token: String) {
        val map = load()
        val e = map[token] ?: return
        if (e.reported) return
        map[token] = e.copy(reported = true)
        save(map)
    }

    /** Phát lại token `granted && !reported` chưa phát trong process này, nếu tầng consent cho qua (D15). */
    fun reemitPending(context: android.content.Context?, onAccepted: (String) -> Unit) {
        val pending = load().values.filter { !it.reported && it.token !in emittedThisProcess }
        if (pending.isEmpty() || !tierPasses(context)) return
        LogUtil.logD("IapHelper ledger: phát lại ${pending.size} iap_purchase chưa báo")
        pending.forEach { emit(it, onAccepted) }
    }

    private fun tierPasses(context: android.content.Context?): Boolean {
        val tier = ConsentState.tier()
        val gdpr = if (tier == ConsentTier.UNKNOWN && context != null) runCatching { TcfConsent.gdprApplies(context) }.getOrNull() else null
        return TelemetryConsentGate.decide(tier, gdpr) == TelemetryConsentGate.Decision.PASS
    }

    private fun emit(e: Entry, onAccepted: (String) -> Unit) {
        emittedThisProcess += e.token
        val paywall = if (e.paywallSessionId.isEmpty()) null else PaywallSession.Snapshot(e.paywallSessionId, e.placement, e.variantId, e.tsMs)
        val data = IapEventLog.iapPurchaseData(
            e.productId, e.orderId, e.priceMicros, e.currency, e.quantity, PurchaseChannel.PLAY_STORE.name, e.isSubscription,
            e.token, e.phase, e.offerTag, paywall, com.app.plugin.logevent.ScreenName.name
        )
        try {
            // F1a m2 (1.9.0 T11): REJECTED = 4xx vĩnh viễn (trừ 408/429) hay body hỏng — gửi lại vô ích, như 2xx thôi phát lại.
            // 2.0.0 B1: 401 tới đây là DROPPED (tập bền `DurableEvents`, mã tạm thời) → chưa `reported`, process sau phát lại.
            val final: (EventOutcome) -> Unit = { o -> if (o == EventOutcome.ACCEPTED || o == EventOutcome.REJECTED) onAccepted(e.token) }
            LogEventTransport.sendEventAt(EventNames.IAP_PURCHASE, data, final, null, e.tsMs)
        } catch (ex: Exception) {
            LogUtil.logE("IapHelper log purchase failed: ${ex.message}")
        }
    }

    /** Doanh thu IAP về AppsFlyer và tín hiệu payer cho brain (trước 1.8.0: `IapHelper.reportPurchase`). */
    private fun reportPartners(e: Entry) {
        try {
            // Thiếu bước này thì ROAS của mọi chiến dịch UA chỉ có vế quảng cáo. AppsFlyer cố ý KHÔNG nhận purchase token.
            com.app.plugin.appsflyer.AppsflyerHelper.instance.logIapPurchase(
                productId = e.productId,
                revenue = e.priceMicros / 1_000_000.0,
                currency = e.currency,
                quantity = e.quantity,
                isSubscription = e.isSubscription
            )
        } catch (ex: Exception) {
            LogUtil.logE("IapHelper appsflyer report failed: ${ex.message}")
        }
        try {
            // Không biết tỉ giá thì vẫn báo ĐÃ MUA (0 USD): `LikelihoodEstimator` chỉ đọc số lượt; thà trống còn hơn số bịa.
            AdsBrain.onPurchase(FxRates.toUsd(e.priceMicros, e.currency) ?: 0.0)
        } catch (ex: Exception) {
            LogUtil.logE("IapHelper notify brain failed: ${ex.message}")
        }
    }

    private fun load(): LinkedHashMap<String, Entry> = entries ?: read().also { entries = it }

    private fun read(): LinkedHashMap<String, Entry> {
        val out = LinkedHashMap<String, Entry>()
        val raw = runCatching { PreferenceHelper.getInstance().preferences?.getString(PREFS_KEY, null) }.getOrNull() ?: return out
        try {
            JsonParser.parseString(raw).asJsonArray.forEach { el -> fromJson(el.asJsonObject).let { out[it.token] = it } }
        } catch (e: Exception) {
            LogUtil.logE("IapHelper ledger unreadable, starting empty: ${e.message}")
        }
        return out
    }

    /** `commit` (không `apply`): dấu `granted` phải nằm trên đĩa trước khi event đi (D15). Chỉ trên luồng billing. */
    private fun save(map: Map<String, Entry>) {
        val arr = JsonArray().also { a -> map.values.forEach { a.add(toJson(it)) } }
        val prefs = PreferenceHelper.getInstance().preferences ?: return
        if (!prefs.edit().putString(PREFS_KEY, arr.toString()).commit()) LogUtil.logE("IapHelper ledger not saved")
    }

    private fun toJson(e: Entry) = JsonObject().apply {
        addProperty("t", e.token); addProperty("p", e.productId); addProperty("o", e.orderId); addProperty("m", e.priceMicros)
        addProperty("c", e.currency); addProperty("q", e.quantity); addProperty("s", e.isSubscription); addProperty("ph", e.phase)
        addProperty("ot", e.offerTag); addProperty("ts", e.tsMs); addProperty("ps", e.paywallSessionId)
        addProperty("pl", e.placement); addProperty("v", e.variantId); addProperty("r", e.reported)
    }

    private fun fromJson(j: JsonObject): Entry {
        fun s(k: String) = j.get(k)?.asString ?: ""
        return Entry(
            s("t"), s("p"), s("o"), j.get("m")?.asLong ?: 0L, s("c"), j.get("q")?.asInt ?: 1, j.get("s")?.asBoolean == true,
            s("ph"), s("ot"), j.get("ts")?.asLong ?: 0L, s("ps"), s("pl"), s("v"), j.get("r")?.asBoolean == true
        )
    }

    /**
     * 2.4.2 CLEAR (`LocalDataReset`; trên luồng billing như mọi lần chạm sổ): bỏ sổ trong bộ nhớ và trên đĩa. Lần
     * [record]/[markReported] sau đọc lại sổ rỗng, không ghi lại token hay order id cũ (I2).
     */
    internal fun forget() {
        entries = null
        PreferenceHelper.getInstance().preferences?.edit()?.remove(PREFS_KEY)?.commit()
    }

    /** Test: quên mọi thứ (như cài mới). */
    internal fun resetForTest() {
        entries = null
        emittedThisProcess.clear()
        PreferenceHelper.getInstance().preferences?.edit()?.remove(PREFS_KEY)?.commit()
    }

    /** Test: như process mới — đọc lại sổ từ prefs, chưa phát gì trong process này. */
    internal fun newProcessForTest() {
        entries = null
        emittedThisProcess.clear()
    }
}
