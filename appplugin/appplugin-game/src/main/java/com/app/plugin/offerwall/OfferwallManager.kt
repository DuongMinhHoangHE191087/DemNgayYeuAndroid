package com.app.plugin.offerwall

import com.app.plugin.consent.ConsentState
import com.app.plugin.consent.ConsentTier
import com.app.plugin.logevent.LogEventServer
import com.app.plugin.until.PreferenceHelper

/**
 * Lớp adapter offerwall — bề mặt monetization thứ tư sau IAP, interstitial, rewarded
 * (doc 23 mục C.4, doc 20).
 *
 * ═══ VÌ SAO LỚP NÀY TỒN TẠI ═══
 *
 * Offerwall phục hưng hậu-ATT vì attribution chạy server-to-server (không cần IDFA), eCPM
 * tier-1 $350–$2,000+ so với ~$19 của rewarded, 93% doanh thu là additive và người engage
 * có D30 retention gấp ~7 lần (doc 20 mục A). Nhưng mỗi vendor (Tapjoy/AppLovin, DT Exchange,
 * adjoe, Torox, RevU, Mistplay) một SDK, một API, một luồng trả thưởng — và khuyến nghị của
 * doc 20 là LUÔN chạy đa offerwall vì fill rate thấp (5–15%). Lớp này là abstraction để cắm
 * 2–3 provider cùng lúc qua một API thống nhất: cổng vào có gate, event thống nhất, chống
 * trả thưởng trùng. Nó KHÔNG build nội dung offerwall và không phụ thuộc SDK vendor nào —
 * doc 20 mục F.1: "adapter + reward delivery, không build content".
 *
 * ═══ BA QUYẾT ĐỊNH ═══
 *
 * 1. **Provider là interface do game implement**, không phải dependency của SDK. Mỗi vendor
 *    SDK đổi API theo phiên bản; nhúng thẳng vào đây biến thư viện này thành nô lệ của
 *    lịch release vendor (bài học ironSource đóng direct demand 30/4/2026 — doc 20 A.4).
 *    Game bọc vendor SDK trong một [OfferwallProvider] mỏng và đăng ký vào.
 * 2. **Đóng tường ≠ hoàn thành offer.** `show()` chỉ ghi nhận lượt mở tường; thưởng về qua
 *    [reportCompletion] — tách riêng vì nhiều vendor xác nhận hoàn thành bằng callback
 *    server-to-server đến SAU khi người chơi đã đóng tường. Gộp hai thứ sẽ mất thưởng của
 *    người đã làm thật.
 * 3. **SDK không trao thưởng.** `reportCompletion` chống trùng theo
 *    (provider, transaction id) rồi trả dữ liệu — **trao currency là việc của game**, cùng
 *    ranh giới với battle pass claim và recurring grant. Xác thực chống gian lận thật sự
 *    nằm ở luồng S2S giữa vendor và server của studio — SDK không thể và không cố làm thay
 *    (ghi rõ trong giới hạn).
 *
 * ═══ GATE CONSENT ═══
 *
 * Offerwall không cần IDFA cho attribution, nhưng SDK vendor thu thập dữ liệu thiết bị cho
 * fraud prevention/analytics — dữ liệu hành vi cần consent theo GDPR (doc 20 E.3). Vì vậy:
 * consent DENIED → chặn mở tường. UNKNOWN không phải DENIED (doc 13: chưa hỏi khác với từ
 * chối). App hướng tới trẻ em (`childDirected == true`) → chặn luôn: COPPA, và đa số vendor
 * tự loại khỏi app trẻ em anyway.
 *
 * ═══ GIỚI HẠN PHẢI NÓI RÕ ═══
 *
 * Lịch sử transaction sống trên máy (prefs, trần 200 bản ghi): xoá app là mất khả năng chống
 * trùng cho các completion cũ. Không có xác thực S2S phía SDK — đó là việc của backend
 * studio với vendor. SDK không vẽ UI của tường — vendor SDK tự vẽ.
 */
public object OfferwallManager {

    private const val KEY_TX = "offerwall_seen_tx"
    private const val SEP = ","
    private const val TX_CAP = 200

    public data class ApplyResult(val applied: Boolean, val report: String)
    public data class ShowResult(val shown: Boolean, val report: String)
    public data class CompletionResult(val accepted: Boolean, val currencyEarned: Long, val report: String)

    /** Adapter do game implement, bọc MỘT vendor SDK (Tapjoy, adjoe, Torox, RevU, DT...). */
    public interface OfferwallProvider {
        public val providerId: String
        public fun isAvailable(): Boolean
        /** Mở tường của vendor. [onWallClosed] gọi khi người chơi đóng tường. */
        public fun show(onWallClosed: () -> Unit)
    }

    public data class OfferwallConfig(
        val version: String,
        val enabled: Boolean,
        /** Rỗng = cho phép mọi provider đã đăng ký. */
        val providers: Set<String>
    )

    @Volatile
    private var config: OfferwallConfig? = null

    private val providers = LinkedHashMap<String, OfferwallProvider>()

    @Volatile
    private var seenTx: MutableSet<String> = loadSeenTx()

    // ═══ Cấu hình ═══

    /**
     * Nạp cấu hình từ Remote Config (khoá `offerwall_config_android`).
     *
     * ```json
     * {
     *   "version": "ow_v1",
     *   "enabled": true,
     *   "providers": ["revu", "adjoe"]
     * }
     * ```
     *
     * `providers` vắng mặt hoặc rỗng nghĩa là cho phép mọi provider đã đăng ký — SDK phải
     * chạy được ngay khi studio chưa kịp cấu hình danh sách. JSON hỏng → giữ bản đang chạy.
     */
    public fun apply(json: String?): ApplyResult {
        if (json.isNullOrBlank()) return reject("cấu hình rỗng")
        return try {
            val root = com.google.gson.Gson().fromJson(json, Map::class.java) ?: return reject("JSON null")
            val ver = root["version"] as? String
            if (ver.isNullOrBlank()) return reject("thiếu trường version")
            val enabled = root["enabled"] != false
            val allow = mutableSetOf<String>()
            (root["providers"] as? List<*>)?.forEach { p ->
                (p as? String)?.takeIf { it.isNotBlank() }?.let { allow.add(it.trim()) }
            }
            config = OfferwallConfig(ver, enabled, allow)
            ApplyResult(
                true,
                "áp $ver: ${if (enabled) "bật" else "TẮT"}, " +
                    if (allow.isEmpty()) "cho phép mọi provider đã đăng ký" else "chỉ [${allow.joinToString(", ")}]"
            )
        } catch (e: Exception) {
            reject("JSON hỏng: ${e.message}")
        }
    }

    private fun reject(reason: String): ApplyResult {
        val cur = config
        return ApplyResult(false, "từ chối ($reason) — giữ nguyên bản ${cur?.version ?: "chưa nạp"}")
    }

    public fun activeVersion(): String = config?.version ?: "chưa nạp"

    // ═══ Đăng ký provider ═══

    /** Đăng ký (hoặc THAY — cùng `providerId` thì adapter mới thế chỗ cũ). Id rỗng bị từ chối. */
    @Synchronized
    public fun register(provider: OfferwallProvider): Boolean {
        if (provider.providerId.isBlank()) return false
        providers[provider.providerId.trim()] = provider
        return true
    }

    @Synchronized
    public fun unregister(providerId: String) {
        providers.remove(providerId.trim())
    }

    /**
     * Provider mở được tường NGAY BÂY GIỜ — đã qua mọi gate (tính năng bật, allow-list,
     * consent, COPPA, khả dụng). Game gọi để quyết định có vẽ nút offerwall hay không.
     */
    @Synchronized
    public fun availableProviders(): List<OfferwallProvider> {
        val cfg = config ?: return emptyList()
        if (!cfg.enabled) return emptyList()
        return providers.values.filter { p -> gateFor(cfg, p) == null && p.isAvailable() }
    }

    // ═══ Mở tường ═══

    /**
     * Mở tường của [providerId] nếu qua mọi gate. Trả lý do từ chối cụ thể khi bị chặn —
     * im lặng không hiện là cách nhanh nhất để studio tưởng tính năng hỏng.
     */
    @Synchronized
    public fun show(providerId: String, onWallClosed: () -> Unit = {}): ShowResult {
        val cfg = config ?: return ShowResult(false, "chưa nạp cấu hình")
        if (!cfg.enabled) return ShowResult(false, "tính năng đang tắt")
        val p = providers[providerId.trim()]
            ?: return ShowResult(false, "chưa đăng ký provider ${providerId.trim()}")
        gateFor(cfg, p)?.let { return ShowResult(false, it) }
        if (!p.isAvailable()) return ShowResult(false, "provider ${providerId.trim()} không khả dụng")
        try {
            LogEventServer.logOfferwallImpression(p.providerId, cfg.version)
        } catch (_: Exception) { /* LogEventServer chưa khởi tạo — bỏ qua, không chặn tường */ }
        p.show(onWallClosed)
        return ShowResult(true, "đã mở tường ${p.providerId}")
    }

    /** Lý do chặn, hoặc null nếu được mở. Thứ tự: consent trước, allow-list sau. */
    private fun gateFor(cfg: OfferwallConfig, p: OfferwallProvider): String? {
        if (ConsentState.childDirected == true)
            return "app hướng tới trẻ em — offerwall bị chặn (COPPA)"
        if (ConsentState.tier() == ConsentTier.DENIED)
            return "consent bị từ chối — không mở offerwall"
        if (cfg.providers.isNotEmpty() && p.providerId !in cfg.providers)
            return "provider ${p.providerId} không nằm trong danh sách cho phép"
        return null
    }

    // ═══ Hoàn thành offer ═══

    /**
     * Báo một offer hoàn thành (vendor callback client hoặc luồng S2S của game báo về).
     *
     * Chống trùng theo (`providerId`, `transactionId`) — cả trong phiên lẫn xuyên phiên
     * (prefs). KHÔNG có transaction_id thì vẫn nhận (mất thưởng thật tệ hơn trùng thưởng)
     * nhưng ghi rõ trong report là không chống trùng được.
     *
     * Cố ý KHÔNG kiểm `enabled`: người chơi đã làm xong việc trước khi tính năng bị tắt —
     * từ chối ghi nhận công họ đã làm là bất công với người chơi; công tắc tắt là để chặn
     * lượt MỞ mới, không để quỵt thưởng.
     *
     * Trả [CompletionResult] với số currency — **trao thưởng là việc của game**.
     */
    @Synchronized
    public fun reportCompletion(
        providerId: String,
        currencyEarned: Long,
        transactionId: String?
    ): CompletionResult {
        val pid = providerId.trim()
        if (pid.isEmpty()) return CompletionResult(false, 0, "provider rỗng")
        if (currencyEarned < 0) return CompletionResult(false, 0, "currency âm")
        val tx = transactionId?.trim()?.takeIf { it.isNotEmpty() }
        if (tx == null) {
            logCompleted(pid, currencyEarned, "")
            return CompletionResult(true, currencyEarned, "đã nhận $currencyEarned — không có transaction_id, không chống trùng được")
        }
        val key = "$pid:$tx"
        if (key in seenTx) return CompletionResult(false, 0, "trùng transaction $tx của $pid")
        seenTx.add(key)
        persistSeenTx()
        logCompleted(pid, currencyEarned, tx)
        return CompletionResult(true, currencyEarned, "đã nhận $currencyEarned từ $pid ($tx)")
    }

    private fun logCompleted(providerId: String, currency: Long, tx: String) {
        try {
            LogEventServer.logOfferwallCompleted(providerId, currency, tx, config?.version ?: "chưa nạp")
        } catch (_: Exception) { /* chưa khởi tạo — sự kiện mất, thưởng không được mất */ }
    }

    // ═══ Persist ═══

    private fun loadSeenTx(): MutableSet<String> {
        val raw = PreferenceHelper.getInstance().get(KEY_TX, "")
        return raw.split(SEP).filter { it.isNotEmpty() }.toMutableSet()
    }

    private fun persistSeenTx() {
        // Trần TX_CAP: chống phình prefs; bản ghi cũ nhất rơi ra trước.
        val all = seenTx.toList()
        val keep = if (all.size > TX_CAP) all.takeLast(TX_CAP) else all
        PreferenceHelper.getInstance().setValue(KEY_TX, keep.joinToString(SEP))
    }

    // ═══ Reset ═══

    @Synchronized
    public fun clear() {
        config = null
        providers.clear()
        seenTx.clear()
        PreferenceHelper.getInstance().remove(KEY_TX)
    }
}
