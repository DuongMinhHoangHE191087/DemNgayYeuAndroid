package com.app.plugin.ads

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hai lỗi banner lấy từ Invoice Maker (doc 63 mục B).
 *
 * **Vòng load kẹt.** Bản 1.2.0 hạ `isLoading` ngay ở lần gọi `loadBanner()` thứ hai khi vòng đầu
 * còn chạy, nên hai vòng waterfall chạy song song cho cùng chỗ đặt, và vòng cũ thấy cờ đã hạ thì
 * bỏ dở. Compose gọi `showBanner()` hai lần mỗi lần dựng layout, nên chuyện này xảy ra thường
 * xuyên. Nay mỗi vòng mang một số thế hệ: gọi chồng thì bỏ qua, callback của vòng không còn là
 * vòng hiện tại thì bị bỏ. Vòng nào [STALL_TIMEOUT_MS] không có callback nào thì bị bỏ ([abandon])
 * và xếp lịch thử lại. Bản của Invoice đo 30 s cho cả vòng; ở đây đo từng bước, vì waterfall 3–4
 * mạng trả no-fill chậm có thể quá 30 s mà không hề kẹt.
 *
 * **Banner chết sau một vòng lỗi.** Cả waterfall lỗi (hay gặp nhất khi request đầu đi trước lúc
 * mạng khởi tạo xong) thì 1.2.0 dừng hẳn: không thử lại, còn mốc `bnTimeShow` vừa đóng chặn luôn
 * lần `showBanner()` kế tiếp. Nay thử lại tối đa [RETRY_DELAYS_MS].
 */
internal class BannerLoadGuard {
    companion object {
        /** Không có callback nào trong khoảng này thì coi bước đang chạy là kẹt. */
        const val STALL_TIMEOUT_MS = 30_000L

        /** Độ trễ các lần thử lại sau một vòng lỗi trọn vẹn khi chưa có banner nào trên màn hình. */
        val RETRY_DELAYS_MS = longArrayOf(5_000L, 15_000L, 45_000L)

        /**
         * Một bước im lặng có được tính là kẹt chưa. Trước khi mạng khởi tạo ([networksStartedAtMs]
         * = 0, đang chờ consent) thì bước nằm trong hàng chờ của mạng, không kẹt. Sau đó bước được
         * trọn [STALL_TIMEOUT_MS] tính từ lúc mạng khởi tạo, vì mạng còn có tới 8 s để khởi tạo xong
         * rồi mới chạy load đang chờ.
         */
        fun isStallCountable(networksStartedAtMs: Long, nowMs: Long): Boolean =
            networksStartedAtMs > 0L && nowMs - networksStartedAtMs >= STALL_TIMEOUT_MS
    }

    // Theo từng chỗ đặt: chuyển màn hình bắt đầu vòng của chỗ đặt mới trong khi vòng của chỗ cũ
    // còn chạy. Một bộ đếm chung sẽ biến vòng cũ thành "cũ", bỏ callback của nó, và cờ
    // `isLoading` của chỗ đặt đó kẹt `true` mãi.
    private val generations = ConcurrentHashMap<String, Int>()
    private val retryAttempt = AtomicInteger(0)

    /** @return số thế hệ của vòng mới cho [placement]; mọi vòng trước đó của nó thành cũ. */
    fun beginCycle(placement: String): Int = generations.merge(placement, 1, Int::plus) ?: 1

    fun isCurrent(placement: String, cycle: Int): Boolean = generations[placement] == cycle

    /** Bỏ vòng [cycle] nếu nó vẫn là vòng hiện tại của [placement]. @return `true` khi đã bỏ. */
    fun abandon(placement: String, cycle: Int): Boolean = generations.replace(placement, cycle, cycle + 1)

    /** @return độ trễ lần thử lại kế tiếp, `null` khi đã hết lượt. */
    fun nextRetryDelayMs(): Long? = RETRY_DELAYS_MS.getOrNull(retryAttempt.getAndIncrement())

    fun resetRetries() {
        retryAttempt.set(0)
    }
}
