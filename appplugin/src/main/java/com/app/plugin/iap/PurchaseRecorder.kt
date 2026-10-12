package com.app.plugin.iap

import android.content.Context
import com.app.plugin.until.LogUtil

/**
 * Bước ghi sổ của [IapHelper] (2.0.0 B1, tách nguyên văn): token PURCHASED vào [PurchaseLedger] ở đường khôi phục
 * ([recordRestored]) và đường listener ([record]); dấu `reported` về luồng billing ([onReported]). Giá, offer và danh sách
 * khai báo đọc qua lambda của `IapHelper` lúc gọi. Chỉ chạy trên luồng "appplugin-billing" (không khoá; I/O chỉ trong
 * [PurchaseLedger]).
 */
internal class PurchaseRecorder(
    private val productOf: (String) -> IapProduct?,
    private val offerOf: (String) -> PortOffer?,
    private val isDeclared: (String) -> Boolean,
    private val consumableIds: () -> Set<String>,
    private val billing: (() -> Unit) -> Unit
) {
    /** Token sổ chưa biết, sản phẩm đã khai báo mà chưa có giá (R-2 I1). */
    fun needsPrice(p: PortPurchase): Boolean {
        val pid = p.products.firstOrNull() ?: return false
        return isDeclared(pid) && productOf(pid) == null && !PurchaseLedger.isGranted(p.token)
    }

    /** m6: kết quả consume tới trước quyết định ghi của token chưa giá; token chờ consume để ghi (chỉ luồng billing). */
    private val consumeResult = HashMap<String, Boolean>()
    private val parked = HashMap<String, () -> Unit>()

    /**
     * Khôi phục, sau lượt lấy giá (nếu cần). D5: giao dịch trả tiền mà sổ chưa biết (bị kill giữa lượt mua, mua ở máy khác)
     * → `iap_purchase` một lần. R-2 I1: sổ khử trùng theo token (ghi 0 là mãi 0) → chưa có giá thì lấy giá trước; không khai
     * báo → ghi 0. Đã khai mà vẫn chưa giá: xem [decide]. Rồi phát lại token `granted && !reported` (D15).
     */
    fun recordRestored(found: List<Pair<PortPurchase, Boolean>>, context: Context?) {
        for ((p, isSub) in found) decide(p, "restore") { record(p, isSub, partners = !p.acknowledged, paywall = null) }
        PurchaseLedger.reemitPending(context, ::onReported)
    }

    /** Đường listener (R-T11 m2, 2.0.0 B1): cùng luật chưa-giá với khôi phục; phiên paywall chụp lúc mua. */
    fun recordUpdated(p: PortPurchase, isSubscription: Boolean) {
        val paywall = PaywallSession.current()
        decide(p, "listener") { record(p, isSubscription, partners = true, paywall = paywall) }
    }

    /**
     * Có giá (hay không khai báo) → ghi ngay. Đã khai mà chưa giá: R-2 m4 (1.9.0 T11) chưa ghi sổ, Play trả lại token, lần
     * khôi phục sau ghi với giá; consumable (D44) bị consume, Play không trả lại → ghi 0 kèm logE, nhưng chỉ khi consume
     * OK (R-T11 m6, 2.0.0 B1): consume lỗi thì token còn ở Play, vòng sau ghi với giá; chưa có kết quả thì chờ [onConsumed].
     */
    private fun decide(p: PortPurchase, where: String, rec: () -> Unit) {
        val consumed = consumeResult.remove(p.token)
        if (!needsPrice(p)) return rec()
        val head = "IapHelper $where: ${p.products} chưa có giá, "
        if (p.products.firstOrNull() !in consumableIds()) return LogUtil.logE(head + "chưa ghi sổ")
        val atZero = { LogUtil.logE(head + "ghi 0 (consumable)"); rec() }
        when (consumed) {
            true -> atZero()
            false -> LogUtil.logE(head + "consume lỗi, chưa ghi sổ")
            null -> parked[p.token] = atZero
        }
    }

    /** Kết quả `consume` của [p] (luồng billing): token đang chờ thì ghi (OK) hay bỏ; chưa quyết định thì nhớ. */
    fun onConsumed(p: PortPurchase, ok: Boolean) {
        val waiting = parked.remove(p.token)
        when {
            waiting != null && ok -> waiting()
            waiting != null -> LogUtil.logE("IapHelper consume lỗi: ${p.products} chưa có giá, chưa ghi sổ")
            needsPrice(p) -> consumeResult[p.token] = ok
        }
    }

    /** Sổ token (D5/D15, [PurchaseLedger]); [paywall] = phiên của lượt mua (đường listener), khôi phục không gắn phiên. */
    fun record(p: PortPurchase, isSubscription: Boolean, partners: Boolean, paywall: PaywallSession.Snapshot?) {
        val pid = p.products.firstOrNull() ?: return
        PurchaseLedger.recordPurchase(p, productOf(pid), offerOf(pid), isSubscription, partners, paywall, ::onReported)
    }

    /** Luồng của sender (2xx hay REJECTED): đưa dấu `reported` về luồng billing. */
    private fun onReported(token: String) = billing { PurchaseLedger.markReported(token) }
}
