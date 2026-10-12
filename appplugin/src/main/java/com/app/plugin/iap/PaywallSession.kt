package com.app.plugin.iap

/**
 * Phiên paywall đang mở (1.8.0 Task 7, hợp đồng E): nơi `paywall_choice` và `iap_purchase` lấy `paywall_session_id`,
 * `placement`, `variant_id`. Chỉ giữ trong bộ nhớ (không I/O); hết hạn sau [TTL_MS] để một lượt mua muộn không dính
 * nhầm phiên cũ. `internal`: không đổi mặt API public.
 */
internal object PaywallSession {
    /** Thời hạn của một phiên: 30 phút kể từ lúc mở. */
    const val TTL_MS = 30L * 60L * 1000L

    internal data class Snapshot(val sessionId: String, val placement: String, val variantId: String, val openedAtMs: Long)

    /** Đồng hồ; test thay để không chờ thật. */
    internal var clock: () -> Long = System::currentTimeMillis

    @Volatile private var snapshot: Snapshot? = null

    /** Mở phiên mới (thay phiên cũ nếu còn) và trả bản chụp. */
    fun open(placement: String, variantId: String, sessionId: String): Snapshot {
        val s = Snapshot(sessionId, placement, variantId, clock())
        snapshot = s
        return s
    }

    /** Phiên còn hạn, hoặc `null` (chưa mở, đã đóng, hết hạn — phiên hết hạn bị bỏ luôn). */
    fun current(): Snapshot? {
        val s = snapshot ?: return null
        if (clock() - s.openedAtMs > TTL_MS) {
            snapshot = null
            return null
        }
        return s
    }

    fun close() {
        snapshot = null
    }
}
